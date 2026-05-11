// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.auth;

import com.zextras.carbonio.docs_connector.Constants.Context;
import com.zextras.carbonio.docs_connector.Constants.DocsConnector.API;
import com.zextras.carbonio.docs_connector.Constants.DocsConnector.API.Endpoints;
import com.zextras.carbonio.docs_connector.Constants.DocsConnector.API.Wopi;
import com.zextras.carbonio.docs_connector.dal.repositories.interfaces.OpenDocumentTokenRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Provider
@ApplicationScoped
public class AccessTokenValidationFilter implements ContainerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(AccessTokenValidationFilter.class);

  private final OpenDocumentTokenRepository openDocumentTokenRepository;
  private final Clock clock;

  @Inject
  public AccessTokenValidationFilter(
      OpenDocumentTokenRepository openDocumentTokenRepository,
      Clock clock) {
    this.openDocumentTokenRepository = openDocumentTokenRepository;
    this.clock = clock;
  }

  /*
  Since Collabora Online's code now makes a preliminary checkinfo operation with a hardcoded access token ttl,
  instead of checking that ttl we just check the one saved in the repository against system clock.
   */
  @Override
  public void filter(ContainerRequestContext requestContext) {

    String endpoint = requestContext.getUriInfo().getPathSegments().get(0).getPath();
    logger.debug("Request received for '{}' endpoint", endpoint);

    if (Endpoints.WOPI.equals(endpoint)) {
      MultivaluedMap<String, String> queryParameters = requestContext
          .getUriInfo()
          .getQueryParameters();

      if (queryParameters.containsKey(API.Wopi.ACCESS_TOKEN_QUERY_PARAM)) {
        List<String> accessTokens = queryParameters.get(Wopi.ACCESS_TOKEN_QUERY_PARAM);
        if (!accessTokens.isEmpty()) {
          openDocumentTokenRepository
              .getToken(UUID.fromString(accessTokens.get(0)))
              .ifPresentOrElse(
                  token -> {
                    if (token.getExpirationTimestamp().toEpochMilli() > clock.millis()) {
                      requestContext.setProperty(Context.OPEN_DOCUMENT_TOKEN, token);
                    } else {
                      logger.warn("Token {} is expired", accessTokens.get(0));
                      requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
                    }
                  },
                  () -> requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build())
              );

          return;
        }
      }
      requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
    }
  }
}
