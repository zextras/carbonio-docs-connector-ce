package com.zextras.carbonio.docs_connector.controllers;

import com.google.inject.Inject;
import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import com.zextras.carbonio.docs_connector.dal.repositories.interfaces.OpenDocumentTokenRepository;
import com.zextras.carbonio.docs_connector.generated.WopiApiService;
import com.zextras.carbonio.docs_connector.services.WopiService;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import javax.enterprise.context.RequestScoped;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequestScoped
public class WopiController implements WopiApiService {

  private final static Logger logger = LoggerFactory.getLogger(WopiController.class);

  private final OpenDocumentTokenRepository openDocumentTokenRepository;
  private final WopiService  wopiService;

  @Inject
  public WopiController(
    OpenDocumentTokenRepository openDocumentTokenRepository,
    WopiService wopiService
  ) {
    this.openDocumentTokenRepository = openDocumentTokenRepository;
    this.wopiService = wopiService;
  }

  public Response docsEditorAttributes(
    String accessToken,
    UUID nodeId,
    Integer version,
    SecurityContext securityContext
  ) {
    logger.info("Get docs-editor attributes for: " + nodeId);

    Optional<OpenDocumentToken> optToken = Optional
      .ofNullable(accessToken)
      .map(token -> openDocumentTokenRepository.getToken(UUID.fromString(token)))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .filter(openDocumentToken -> openDocumentToken.getDocumentId().equals(nodeId));

    if (optToken.isPresent()) {
      return wopiService
        .getDocsEditorAttributes(optToken.get(), nodeId, Optional.ofNullable(version))
        .map(docsEditorAttributes -> Response.ok().entity(docsEditorAttributes).build())
        .orElse(Response.serverError().build());
    }

    logger.error("Invalid token: " + accessToken + ", nodeId: " + nodeId);
    return Response.status(Status.UNAUTHORIZED).build();
  }

  public Response getBlob(
    String accessToken,
    UUID nodeId,
    Integer version,
    SecurityContext securityContext
  ) {
    logger.info("Get blob for: " + nodeId);

    Optional<OpenDocumentToken> optToken = Optional
      .ofNullable(accessToken)
      .map(token -> openDocumentTokenRepository.getToken(UUID.fromString(token)))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .filter(openDocumentToken -> openDocumentToken.getDocumentId().equals(nodeId));

    if (optToken.isPresent()) {
      return wopiService
        .getBlob(optToken.get().getRequesterCookie(), nodeId, Optional.ofNullable(version))
        .map(filesBlob ->
          Response
            .ok()
            .entity(filesBlob.getContent())
            .header(HttpHeaders.CONTENT_LENGTH, filesBlob.getSize())
            .build()
        )
        .orElse(Response.serverError().build());
    }

    logger.error("Invalid token: " + accessToken + ", nodeId: " + nodeId);
    return Response.status(Status.UNAUTHORIZED).build();
  }

  public Response saveBlob(
    UUID nodeId,
    String accessToken,
    Boolean coolIsAutosave,
    Boolean coolIsExitSave,
    Long contentLength,
    InputStream blob,
    SecurityContext securityContext
  ) {
    logger.info("Save blob for: " + nodeId);

    Optional<OpenDocumentToken> optToken = Optional
      .ofNullable(accessToken)
      .map(token -> openDocumentTokenRepository.getToken(UUID.fromString(token)))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .filter(openDocumentToken -> openDocumentToken.getDocumentId().equals(nodeId));

    if (optToken.isPresent()) {
      return wopiService
        .saveBlob(optToken.get().getRequesterCookie(), nodeId, blob, contentLength, coolIsAutosave)
        .map(nodeUpdatedTimestamp -> Response.ok().entity(nodeUpdatedTimestamp).build())
        .orElse(Response.serverError().build());
    }

    logger.error("Invalid token: " + accessToken + ", nodeId: " + nodeId);
    return Response.status(Status.UNAUTHORIZED).build();
  }
}
