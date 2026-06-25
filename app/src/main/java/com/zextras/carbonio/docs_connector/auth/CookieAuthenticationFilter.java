// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.auth;

import com.zextras.carbonio.docs_connector.Constants.Config;
import com.zextras.carbonio.docs_connector.Constants.Context;
import com.zextras.carbonio.docs_connector.Constants.DocsConnector.API.Endpoints;
import com.zextras.carbonio.docs_connector.clients.UserManagementClient;
import com.zextras.carbonio.user_management.sdk.grpc.GetUserMyselfRequest;
import com.zextras.carbonio.user_management.sdk.grpc.UserMyselfProto;
import com.zextras.carbonio.user_management.sdk.grpc.UserTypeProto;
import io.grpc.StatusRuntimeException;
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

  private final UserManagementClient userManagementClient;

  @Inject
  public CookieAuthenticationFilter(UserManagementClient userManagementClient) {
    this.userManagementClient = userManagementClient;
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
        GetUserMyselfRequest request =
            GetUserMyselfRequest.newBuilder()
                .setToken(token)
                .setBypassCache(true)
                .build();
        UserMyselfProto myself = userManagementClient.getBlockingStub().getUserMyself(request).getUser();

        if (!myself.getInfo().getStatus().equalsIgnoreCase("active")) {
          logger.error("The request is unauthorized: the user is not active");
          requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
          return;
        }

        if (myself.getInfo().getType() != UserTypeProto.INTERNAL) {
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

      } catch (StatusRuntimeException e) {
        if (e.getStatus().getCode() == io.grpc.Status.Code.UNAUTHENTICATED) {
          logger.error("The request is unauthorized: the cookie is invalid");
        } else {
          logger.error("The request is unauthorized: gRPC error {}", e.getStatus(), e);
        }
        requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
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
