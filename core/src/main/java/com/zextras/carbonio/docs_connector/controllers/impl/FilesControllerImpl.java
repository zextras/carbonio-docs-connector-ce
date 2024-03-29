package com.zextras.carbonio.docs_connector.controllers.impl;

import com.zextras.carbonio.docs_connector.Constants.Context;
import com.zextras.carbonio.docs_connector.controllers.FilesController;
import com.zextras.carbonio.docs_connector.services.FilesService;
import com.zextras.carbonio.docs_connector.types.InsertFile;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class FilesControllerImpl implements FilesController {

  private final FilesService filesService;

  @Inject
  public FilesControllerImpl(FilesService filesService) {
    this.filesService = filesService;
  }

  public Response createFile(String cookie, InsertFile insertFile, HttpServletRequest httpRequest) {
    return filesService
        .uploadTemplate(cookie, insertFile)
        .map(createdFile -> Response.ok().entity(createdFile).build())
        .orElse(Response.serverError().build());
  }

  public Response openFile(
      String cookie, UUID nodeId, Integer version, HttpServletRequest httpRequest) {
    String requesterId = (String) httpRequest.getAttribute(Context.REQUESTER_ID);
    Locale requesterLocale = (Locale) httpRequest.getAttribute(Context.REQUESTER_LOCALE);

    Optional<String> optDocsEditorRedirect =
        filesService.openFile(
            requesterId, requesterLocale, cookie, nodeId.toString(), Optional.ofNullable(version));

    return optDocsEditorRedirect.isPresent()
        ? Response.temporaryRedirect(URI.create(optDocsEditorRedirect.get())).build()
        : Response.serverError().build();
  }
}
