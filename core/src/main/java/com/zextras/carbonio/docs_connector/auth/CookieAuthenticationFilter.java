package com.zextras.carbonio.docs_connector.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zextras.carbonio.docs_connector.Constants.Config;
import com.zextras.carbonio.docs_connector.Constants.Context;
import com.zextras.carbonio.docs_connector.Constants.Service.API.Endpoints;
import com.zextras.carbonio.usermanagement.UserManagementClient;
import java.util.Optional;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@Singleton
public class CookieAuthenticationFilter implements ContainerRequestFilter {

  private final static Logger logger = LoggerFactory.getLogger(CookieAuthenticationFilter.class);

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

      userManagementClient
        .validateUserToken(optZmCookie.get().getValue())
        .onSuccess(userId -> {
          requestContext.setProperty(Context.REQUESTER_COOKIE, optZmCookie.get().getValue());
          requestContext.setProperty(Context.REQUESTER_ID, userId.getUserId());
        })
        .onFailure(failure -> {
          logger.error("The request is unauthorized: the cookie is invalid");
          requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
        });
    }
  }
}
