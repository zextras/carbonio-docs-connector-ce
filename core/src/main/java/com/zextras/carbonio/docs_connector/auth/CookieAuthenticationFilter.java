// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zextras.carbonio.docs_connector.Constants.Config;
import com.zextras.carbonio.docs_connector.Constants.Config.UserManagement;
import com.zextras.carbonio.docs_connector.Constants.Context;
import com.zextras.carbonio.docs_connector.Constants.DocsConnector.API.Endpoints;
import com.zextras.carbonio.docs_connector.config.DocsConnectorConfig;
import com.zextras.carbonio.usermanagement.UserManagementClient;
import com.zextras.carbonio.usermanagement.enumerations.UserStatus;
import com.zextras.carbonio.usermanagement.enumerations.UserType;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Provider
@Singleton
public class CookieAuthenticationFilter implements ContainerRequestFilter {

  private final static Logger logger = LoggerFactory.getLogger(CookieAuthenticationFilter.class);

  private final DocsConnectorConfig config;
  private final UserManagementClient userManagementClient;

  @Inject
  public CookieAuthenticationFilter(DocsConnectorConfig config, UserManagementClient userManagementClient) {
    this.config = config;
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

      userManagementClient
          .getUserMyself(UserManagement.ZM_AUTH_TOKEN.concat(optZmCookie.get().getValue()))
          .onSuccess(
              myself -> {

                if(!myself.getStatus().equals(UserStatus.ACTIVE)){
                  logger.error("The request is unauthorized: the user is not active");
                  requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
                  return;
                }

                if(!myself.getType().equals(UserType.INTERNAL)){
                  logger.error("The request is unauthorized: the user type is not internal");
                  requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
                  return;
                }

                requestContext.setProperty(Context.REQUESTER_COOKIE, optZmCookie.get().getValue());
                requestContext.setProperty(Context.REQUESTER_ID, myself.getId().getUserId());
                Optional<String> requesterDomainOverride = config.getRequesterDomainOverride();
                if (requesterDomainOverride.isPresent()) {
                  requestContext.setProperty(Context.REQUESTER_DOMAIN, requesterDomainOverride.get());
                } else {
                  requestContext.setProperty(Context.REQUESTER_DOMAIN, myself.getDomain());
                }
                requestContext.setProperty(Context.REQUESTER_LOCALE, myself.getLocale()
                );
              })
          .onFailure(throwable -> {
            logger.error("The request is unauthorized: the cookie is invalid");
            requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
          });
    }
  }
}
