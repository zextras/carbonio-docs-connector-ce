// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.auth;

import com.zextras.carbonio.docs_connector.Constants.Config;
import com.zextras.carbonio.docs_connector.Constants.Context;
import com.zextras.carbonio.docs_connector.Constants.DocsConnector.API.Endpoints;
import com.zextras.carbonio.user_management.sdk.rest.ApiException;
import com.zextras.carbonio.user_management.sdk.rest.api.UserResourceApi;
import com.zextras.carbonio.user_management.sdk.rest.model.MyselfDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@ApplicationScoped
public class CookieAuthenticationFilter implements ContainerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(CookieAuthenticationFilter.class);

  /**
   * TEST-ONLY override for the requester domain used in docs-editor redirects. It is read directly
   * from the system property {@value Context#OVERRIDE_REQUESTER_DOMAIN_PROPERTY} (or, equivalently,
   * from an env var of the same logical name) and is intentionally NOT a Consul KV /
   * application-config key, so it never appears in the generated configs.md and is excluded from the
   * config-migration surface. It exists purely to let developers/tests force redirects onto a
   * different domain. Mirrors the legacy system property name for continuity.
   */
  static final String REQUESTER_DOMAIN_OVERRIDE_PROPERTY =
      Context.OVERRIDE_REQUESTER_DOMAIN_PROPERTY;

  private final UserResourceApi userResourceApi;

  @Inject
  public CookieAuthenticationFilter(UserResourceApi userResourceApi) {
    this.userResourceApi = userResourceApi;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {

    String endpoint = requestContext.getUriInfo().getPathSegments().get(0).getPath();
    logger.debug("Request received for '{}' endpoint", endpoint);

    if (Endpoints.FILES.equals(endpoint)) {

      Optional<Cookie> optZmCookie = requestContext
          .getCookies()
          .values()
          .stream()
          .filter(cookie -> Config.ACCEPTED_COOKIE_TYPE.equals(cookie.getName()))
          .findFirst();

      if (optZmCookie.isEmpty()) {
        logger.error("The request is unauthorized: the cookie is missing");
        requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
        return;
      }

      String token = optZmCookie.get().getValue();

      try {
        // bypassCache=true: force user-management to re-validate this token against mailbox on
        // every request instead of serving its cached MyselfDto. UserMyselfCache's TTL defaults to
        // the entire remaining lifetime of the session token, and nothing invalidates it early, so
        // without the bypass a revoked session (password change, admin "end all sessions") would
        // keep authenticating successfully here for as long as the token itself remains valid -
        // potentially days. The cached value is not safe to use for an authorization decision.
        MyselfDto myself = userResourceApi.internalUsersMyselfGet(true, token);

        // A 2xx response with a blank body deserializes to a null MyselfDto (or a MyselfDto with
        // a null `info`) instead of throwing. Under the old gRPC client this shape was impossible
        // (proto3 defaults a missing sub-message to an empty, non-null instance whose status/type
        // fields are "" and fail the checks below anyway), so treat it the same way here: a
        // degenerate response is just another way of not having a valid active internal user.
        if (myself == null || myself.getInfo() == null) {
          logger.error("The request is unauthorized: user-management returned no user info");
          requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
          return;
        }

        if (!"active".equalsIgnoreCase(myself.getInfo().getStatus())) {
          logger.error("The request is unauthorized: the user is not active");
          requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
          return;
        }

        if (!"INTERNAL".equalsIgnoreCase(myself.getInfo().getType())) {
          logger.error("The request is unauthorized: the user type is not internal");
          requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
          return;
        }

        requestContext.setProperty(Context.REQUESTER_COOKIE, token);
        requestContext.setProperty(Context.REQUESTER_ID, myself.getInfo().getUserId());

        // TEST-ONLY override: read directly from the system property (falling back to an env var of
        // the same name), NOT from Consul KV / application-config. Absent in normal deployments.
        Optional<String> requesterDomainOverride = requesterDomainOverride();
        if (requesterDomainOverride.isPresent()) {
          requestContext.setProperty(Context.REQUESTER_DOMAIN, requesterDomainOverride.get());
        } else {
          requestContext.setProperty(Context.REQUESTER_DOMAIN, myself.getInfo().getDomain());
        }

        String localeStr = myself.getLocale();
        requestContext.setProperty(
            Context.REQUESTER_LOCALE,
            localeStr != null && !localeStr.isEmpty()
                ? Locale.forLanguageTag(localeStr.replace('_', '-'))
                : Locale.ENGLISH);

      } catch (ApiException e) {
        // getCode() == 0 means no HTTP response was ever received (connection refused, timeout,
        // a body that failed to deserialize, ...) -- see ApiException(Throwable) in the generated
        // client. That, and any 5xx, means user-management itself is unavailable/broken, not that
        // the cookie is invalid: reporting it as a 401 causes a spurious client-side logout /
        // re-auth loop. Only a genuine 401 from user-management means the cookie is invalid.
        if (e.getCode() == Status.UNAUTHORIZED.getStatusCode()) {
          logger.error("The request is unauthorized: the cookie is invalid");
          requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
        } else if (e.getCode() == 0 || e.getCode() >= 500) {
          logger.error(
              "The request could not be authenticated: user-management is unavailable (code {})",
              e.getCode(), e);
          requestContext.abortWith(Response.status(Status.SERVICE_UNAVAILABLE).build());
        } else {
          logger.error("The request is unauthorized: REST error {}", e.getCode(), e);
          requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
        }
      }
    }
  }

  /**
   * Reads the TEST-ONLY requester-domain override. Looks first at the system property
   * {@link #REQUESTER_DOMAIN_OVERRIDE_PROPERTY}, then at an environment variable of the same logical
   * name (dots normalized to underscores: {@code CARBONIO_DOCS_CONNECTOR_REQUESTER_DOMAIN_OVERRIDE}).
   * Blank values are treated as unset. This is intentionally NOT a Consul KV / application-config
   * key.
   */
  private static Optional<String> requesterDomainOverride() {
    String value = System.getProperty(REQUESTER_DOMAIN_OVERRIDE_PROPERTY);
    if (value == null || value.isBlank()) {
      value = System.getenv(
          REQUESTER_DOMAIN_OVERRIDE_PROPERTY.replace('.', '_').replace('-', '_').toUpperCase());
    }
    return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
  }
}
