// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.controllers.impl;

import com.zextras.carbonio.docs_connector.Constants.Context;
import com.zextras.carbonio.docs_connector.controllers.WopiController;
import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import com.zextras.carbonio.docs_connector.exceptions.ServiceDependencyException;
import com.zextras.carbonio.docs_connector.services.WopiService;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WopiControllerImpl implements WopiController {

  private static final Logger logger = LoggerFactory.getLogger(WopiController.class);

  private final WopiService wopiService;

  @Inject
  public WopiControllerImpl(WopiService wopiService) {
    this.wopiService = wopiService;
  }

  public Response docsEditorAttributes(
      String accessToken, UUID nodeId, Integer version, Integer offsetFromUtc, HttpServletRequest httpRequest) {
    logger.info("Get docs-editor attributes for: " + nodeId);

    OpenDocumentToken openDocumentToken =
        (OpenDocumentToken) httpRequest.getAttribute(Context.OPEN_DOCUMENT_TOKEN);

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
      Integer offsetFromUtc,
      InputStream blob,
      HttpServletRequest httpRequest) {
    logger.info("Save blob for: " + nodeId);

    OpenDocumentToken openDocumentToken =
        (OpenDocumentToken) httpRequest.getAttribute(Context.OPEN_DOCUMENT_TOKEN);

    if (openDocumentToken.getDocumentId().equals(nodeId)) {
      try {
        return wopiService
            .saveBlob(openDocumentToken.getRequesterCookie(), nodeId, Optional.ofNullable(offsetFromUtc), blob, contentLength, coolIsAutosave)
            .map(nodeUpdatedTimestamp -> Response.ok().entity(nodeUpdatedTimestamp).build())
            .orElse(Response.status(424).build());
      } catch (ServiceDependencyException exception) {
        logger.error(exception.getMessage());
        return Response.status(424).build();
      }
    }

    logger.error("Invalid token: " + accessToken + ", nodeId: " + nodeId);
    return Response.status(Status.UNAUTHORIZED).build();
  }
}
