package com.zextras.carbonio.docs_connector.controllers.impl;

import com.google.inject.Inject;
import com.zextras.carbonio.docs_connector.Constants.Context;
import com.zextras.carbonio.docs_connector.controllers.WopiController;
import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import com.zextras.carbonio.docs_connector.services.WopiService;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import javax.enterprise.context.RequestScoped;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequestScoped
public class WopiControllerImpl implements WopiController {

  private static final Logger logger = LoggerFactory.getLogger(WopiController.class);

  private final WopiService wopiService;

  @Inject
  public WopiControllerImpl(WopiService wopiService) {
    this.wopiService = wopiService;
  }

  public Response docsEditorAttributes(
      String accessToken, UUID nodeId, Integer version, HttpServletRequest httpRequest) {
    logger.info("Get docs-editor attributes for: " + nodeId);

    OpenDocumentToken openDocumentToken =
        (OpenDocumentToken) httpRequest.getAttribute(Context.OPEN_DOCUMENT_TOKEN);

    if (openDocumentToken.getDocumentId().equals(nodeId)) {
      return wopiService
          .getDocsEditorAttributes(
              openDocumentToken.getRequesterId(),
              openDocumentToken.getRequesterCookie(),
              nodeId,
              Optional.ofNullable(version))
          .map(docsEditorAttributes -> Response.ok().entity(docsEditorAttributes).build())
          .orElse(Response.serverError().build());
    }

    logger.error("Invalid token: " + accessToken + ", nodeId: " + nodeId);
    return Response.status(Status.UNAUTHORIZED).build();
  }

  public Response getBlob(
      String accessToken, UUID nodeId, Integer version, HttpServletRequest httpRequest) {
    logger.info("Get blob for: " + nodeId);

    OpenDocumentToken openDocumentToken =
        (OpenDocumentToken) httpRequest.getAttribute(Context.OPEN_DOCUMENT_TOKEN);

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

  public Response saveBlob(
      String accessToken,
      UUID nodeId,
      Boolean coolIsAutosave,
      Boolean coolIsExitSave,
      Long contentLength,
      InputStream blob,
      HttpServletRequest httpRequest) {
    logger.info("Save blob for: " + nodeId);

    OpenDocumentToken openDocumentToken =
        (OpenDocumentToken) httpRequest.getAttribute(Context.OPEN_DOCUMENT_TOKEN);

    if (openDocumentToken.getDocumentId().equals(nodeId)) {

      return wopiService
          .saveBlob(
              openDocumentToken.getRequesterCookie(), nodeId, blob, contentLength, coolIsAutosave)
          .map(nodeUpdatedTimestamp -> Response.ok().entity(nodeUpdatedTimestamp).build())
          .orElse(Response.serverError().build());
    }

    logger.error("Invalid token: " + accessToken + ", nodeId: " + nodeId);
    return Response.status(Status.UNAUTHORIZED).build();
  }
}
