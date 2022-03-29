package com.zextras.carbonio.docs_connector.controllers;

import com.google.inject.Inject;
import com.zextras.carbonio.docs_connector.generated.FilesApiService;
import com.zextras.carbonio.docs_connector.generated.model.InsertFile;
import com.zextras.carbonio.docs_connector.services.FilesService;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import javax.enterprise.context.RequestScoped;
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
    SecurityContext securityContext
  ) {
    return Response.ok().build();
  }

  public Response openFile(
    String cookie,
    UUID nodeId,
    Integer version,
    SecurityContext securityContext
  ) {

    Optional<String> optDocsEditorRedirect = filesService.openFile(
      nodeId.toString(),
      Optional.ofNullable(version),
      cookie
    );

    return optDocsEditorRedirect.isPresent()
      ? Response.temporaryRedirect(URI.create(optDocsEditorRedirect.get())).build()
      : Response.serverError().build();
  }
}
