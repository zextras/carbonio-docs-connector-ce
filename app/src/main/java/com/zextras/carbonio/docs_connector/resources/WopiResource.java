// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.resources;

import com.zextras.carbonio.docs_connector.Constants;
import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import com.zextras.carbonio.docs_connector.exceptions.AccountOverQuotaException;
import com.zextras.carbonio.docs_connector.exceptions.ServiceDependencyException;
import com.zextras.carbonio.docs_connector.services.WopiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JAX-RS resource for the /wopi/{nodeId} endpoints. Merges the old WopiController interface and
 * WopiControllerImpl into a single CDI bean. The {@link OpenDocumentToken} is set in the request
 * context by {@link com.zextras.carbonio.docs_connector.auth.AccessTokenValidationFilter} and read
 * here via {@link ContainerRequestContext#getProperty(String)}.
 */
@Path("/wopi/{nodeId}")
@ApplicationScoped
public class WopiResource {

  private static final Logger logger = LoggerFactory.getLogger(WopiResource.class);

  private final WopiService wopiService;

  @Inject
  public WopiResource(WopiService wopiService) {
    this.wopiService = wopiService;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response docsEditorAttributes(
      @QueryParam("access_token") String accessToken,
      @PathParam("nodeId") UUID nodeId,
      @QueryParam("version") Integer version,
      @QueryParam("offset_from_utc") Integer offsetFromUtc,
      @jakarta.ws.rs.core.Context ContainerRequestContext requestContext) {

    logger.info("Get docs-editor attributes for: " + nodeId);

    OpenDocumentToken openDocumentToken =
        (OpenDocumentToken) requestContext.getProperty(Constants.Context.OPEN_DOCUMENT_TOKEN);

    if (openDocumentToken.getDocumentId().equals(nodeId)) {
      return wopiService
          .getDocsEditorAttributes(
              openDocumentToken.getRequesterId(),
              openDocumentToken.getRequesterCookie(),
              nodeId,
              Optional.ofNullable(version),
              Optional.ofNullable(offsetFromUtc))
          .map(docsEditorAttributes -> Response.ok().entity(docsEditorAttributes).build())
          .orElse(Response.serverError().build());
    }

    logger.error("Invalid token: " + accessToken + ", nodeId: " + nodeId);
    return Response.status(Status.UNAUTHORIZED).build();
  }

  @GET
  @Path("/contents")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response getBlob(
      @QueryParam("access_token") String accessToken,
      @PathParam("nodeId") UUID nodeId,
      @QueryParam("version") Integer version,
      @jakarta.ws.rs.core.Context ContainerRequestContext requestContext) {

    logger.info("Get blob for: " + nodeId);

    OpenDocumentToken openDocumentToken =
        (OpenDocumentToken) requestContext.getProperty(Constants.Context.OPEN_DOCUMENT_TOKEN);

    if (openDocumentToken.getDocumentId().equals(nodeId)) {
      return wopiService
          .getBlob(openDocumentToken.getRequesterCookie(), nodeId, Optional.ofNullable(version))
          .map(
              filesBlob ->
                  Response.ok()
                      .type(MediaType.APPLICATION_OCTET_STREAM)
                      .entity(filesBlob.getContent())
                      .header(HttpHeaders.CONTENT_LENGTH, filesBlob.getSize())
                      .build())
          .orElse(Response.serverError().build());
    }

    logger.error("Invalid token: " + accessToken + ", nodeId: " + nodeId);
    return Response.status(Status.UNAUTHORIZED).build();
  }

  @POST
  @Path("/contents")
  @Consumes(MediaType.APPLICATION_OCTET_STREAM)
  @Produces(MediaType.APPLICATION_JSON)
  public Response saveBlob(
      @QueryParam("access_token") String accessToken,
      @PathParam("nodeId") UUID nodeId,
      @HeaderParam("X-COOL-WOPI-IsAutosave") Boolean xCOOLWOPIIsAutosave,
      @HeaderParam("X-COOL-WOPI-IsExitSave") Boolean xCOOLWOPIIsExitSave,
      @HeaderParam("Content-Length") Long contentLength,
      @QueryParam("offset_from_utc") Integer offsetFromUtc,
      InputStream body,
      @jakarta.ws.rs.core.Context ContainerRequestContext requestContext) {

    logger.info("Save blob for: " + nodeId);

    OpenDocumentToken openDocumentToken =
        (OpenDocumentToken) requestContext.getProperty(Constants.Context.OPEN_DOCUMENT_TOKEN);

    if (openDocumentToken.getDocumentId().equals(nodeId)) {
      try {
        return wopiService
            .saveBlob(
                openDocumentToken.getRequesterCookie(),
                nodeId,
                Optional.ofNullable(offsetFromUtc),
                body,
                contentLength,
                xCOOLWOPIIsAutosave != null && xCOOLWOPIIsAutosave)
            .map(nodeUpdatedTimestamp -> Response.ok().entity(nodeUpdatedTimestamp).build())
            .orElse(Response.status(424).build());
      } catch (AccountOverQuotaException exception) {
        logger.error(exception.getMessage());
        return Response.status(413).build();
      } catch (ServiceDependencyException exception) {
        logger.error(exception.getMessage());
        return Response.status(424).build();
      }
    }

    logger.error("Invalid token: " + accessToken + ", nodeId: " + nodeId);
    return Response.status(Status.UNAUTHORIZED).build();
  }
}
