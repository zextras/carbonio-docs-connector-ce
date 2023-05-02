package com.zextras.carbonio.docs_connector.controllers;

import com.google.inject.Inject;
import com.zextras.carbonio.docs_connector.Constants.Context;
import com.zextras.carbonio.docs_connector.generated.FilesApiService;
import com.zextras.carbonio.docs_connector.generated.model.InsertFile;
import com.zextras.carbonio.docs_connector.services.FilesService;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import javax.enterprise.context.RequestScoped;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@RequestScoped
public class FilesController implements FilesApiService {

  private final FilesService filesService;

  @Inject
  public FilesController(FilesService filesService) {this.filesService = filesService;}

  public Response createFile(
    String cookie,
    InsertFile insertFile,
    SecurityContext securityContext,
    HttpServletRequest httpRequest
  ) {
    return filesService.uploadTemplate(cookie, insertFile)
      .map(createdFile -> Response.ok().entity(createdFile).build())
      .orElse(Response.serverError().build());
  }

  public Response openFile(
    String cookie,
    UUID nodeId,
    Integer version,
    SecurityContext securityContext,
    HttpServletRequest httpRequest
  ) {
    String requesterId = (String) httpRequest.getAttribute(Context.REQUESTER_ID);

    Optional<String> optDocsEditorRedirect = filesService.openFile(
      requesterId,
      cookie,
      nodeId.toString(),
      Optional.ofNullable(version)
    );

    return optDocsEditorRedirect.isPresent()
      ? Response.temporaryRedirect(URI.create(optDocsEditorRedirect.get())).build()
      : Response.serverError().build();
  }
}
