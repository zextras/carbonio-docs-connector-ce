// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zextras.carbonio.docs_connector.Constants.Config;
import com.zextras.carbonio.docs_connector.Constants.Context;
import com.zextras.carbonio.docs_connector.Constants.DocsConnector.API.Endpoints;
import com.zextras.carbonio.docs_connector.config.DocsConnectorConfig;
import com.zextras.carbonio.user_management.sdk.grpc.GetUserMyselfRequest;
import com.zextras.carbonio.user_management.sdk.grpc.UserManagementServiceGrpc.UserManagementServiceBlockingStub;
import com.zextras.carbonio.user_management.sdk.grpc.UserMyselfProto;
import com.zextras.carbonio.user_management.sdk.grpc.UserTypeProto;
import io.grpc.StatusRuntimeException;
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
@Singleton
public class CookieAuthenticationFilter implements ContainerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(CookieAuthenticationFilter.class);

  private final DocsConnectorConfig config;
  private final UserManagementServiceBlockingStub userManagementStub;

  @Inject
  public CookieAuthenticationFilter(
      DocsConnectorConfig config, UserManagementServiceBlockingStub userManagementStub) {
    this.config = config;
    this.userManagementStub = userManagementStub;
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
        UserMyselfProto myself = userManagementStub.getUserMyself(request).getUser();

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
        Optional<String> requesterDomainOverride = config.getRequesterDomainOverride();
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
}
