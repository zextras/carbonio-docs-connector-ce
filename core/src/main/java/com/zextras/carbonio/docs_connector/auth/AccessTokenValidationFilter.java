// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zextras.carbonio.docs_connector.Constants.Context;
import com.zextras.carbonio.docs_connector.Constants.Service.API;
import com.zextras.carbonio.docs_connector.Constants.Service.API.Endpoints;
import com.zextras.carbonio.docs_connector.Constants.Service.API.Wopi;
import com.zextras.carbonio.docs_connector.dal.repositories.interfaces.OpenDocumentTokenRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@Singleton
public class AccessTokenValidationFilter implements ContainerRequestFilter {

  private final static Logger logger = LoggerFactory.getLogger(AccessTokenValidationFilter.class);

  private final OpenDocumentTokenRepository openDocumentTokenRepository;
  private final Clock clock;

  @Inject
  public AccessTokenValidationFilter(OpenDocumentTokenRepository openDocumentTokenRepository,
    Clock clock) {
    this.openDocumentTokenRepository = openDocumentTokenRepository;
    this.clock = clock;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {

    String endpoint = requestContext.getUriInfo().getPathSegments().get(0).getPath();
    logger.debug("Request received for '{}' endpoint", endpoint);

    if (Endpoints.WOPI.equals(endpoint)) {
      MultivaluedMap<String, String> queryParameters = requestContext
        .getUriInfo()
        .getQueryParameters();

      if (queryParameters.containsKey(API.Wopi.ACCESS_TOKEN_QUERY_PARAM)
        && queryParameters.containsKey(Wopi.ACCESS_TOKEN_TTL_QUERY_PARAM)
      ) {
        List<String> accessTokens = queryParameters.get(Wopi.ACCESS_TOKEN_QUERY_PARAM);
        List<String> tokenExpirationTimestamps = queryParameters.get(
          Wopi.ACCESS_TOKEN_TTL_QUERY_PARAM);

        if (accessTokens.size() > 0
          && tokenExpirationTimestamps.size() > 0
          && Long.parseLong(tokenExpirationTimestamps.get(0)) > clock.millis()
        ) {

          openDocumentTokenRepository
            .getToken(UUID.fromString(accessTokens.get(0)))
            .ifPresentOrElse(
              token -> requestContext.setProperty(Context.OPEN_DOCUMENT_TOKEN, token),
              () -> requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build())
            );

          return;
        }
      }
      requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
    }
  }
}
