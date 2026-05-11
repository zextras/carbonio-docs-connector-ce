// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.resources;

import com.zextras.carbonio.docs_connector.Constants;
import com.zextras.carbonio.docs_connector.exceptions.FileSizeTooLargeException;
import com.zextras.carbonio.docs_connector.exceptions.ServiceDependencyException;
import com.zextras.carbonio.docs_connector.services.FilesService;
import com.zextras.carbonio.docs_connector.types.DocsEditorRedirect;
import com.zextras.carbonio.docs_connector.types.InsertFile;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * JAX-RS resource for the /files endpoints. Merges the old FilesController interface and
 * FilesControllerImpl into a single CDI bean. Request context attributes (requesterId, domain,
 * locale) are set by {@link com.zextras.carbonio.docs_connector.auth.CookieAuthenticationFilter}
 * and read here via {@link ContainerRequestContext#getProperty(String)}.
 */
@Path("/files")
@ApplicationScoped
public class FilesResource {

  private final FilesService filesService;

  @Inject
  public FilesResource(FilesService filesService) {
    this.filesService = filesService;
  }

  @POST
  @Path("/create")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response createFile(
      @HeaderParam("Cookie") String cookie,
      InsertFile insertFile,
      @jakarta.ws.rs.core.Context ContainerRequestContext requestContext) {
    return filesService
        .uploadTemplate(cookie, insertFile)
        .map(createdFile -> Response.ok().entity(createdFile).build())
        .orElse(Response.serverError().build());
  }

  @GET
  @Path("/open/{nodeId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response openFile(
      @HeaderParam("Cookie") String cookie,
      @PathParam("nodeId") UUID nodeId,
      @QueryParam("version") Integer version,
      @QueryParam("redirect") Boolean redirect,
      @QueryParam("offset_from_utc") Integer offsetFromUtc,
      @jakarta.ws.rs.core.Context ContainerRequestContext requestContext) {

    String requesterId = (String) requestContext.getProperty(Constants.Context.REQUESTER_ID);
    String requesterDomain = (String) requestContext.getProperty(Constants.Context.REQUESTER_DOMAIN);
    Locale requesterLocale = (Locale) requestContext.getProperty(Constants.Context.REQUESTER_LOCALE);

    try {
      String docsEditorURL = filesService.openFile(
          requesterId,
          requesterLocale,
          cookie,
          nodeId.toString(),
          Optional.ofNullable(version),
          Optional.ofNullable(offsetFromUtc)
      );
      String docsRedirectURL = "%s/%s".formatted(requesterDomain, docsEditorURL);

      return (redirect != null && redirect)
          ? Response.temporaryRedirect(URI.create(docsRedirectURL)).build()
          : Response.ok().entity(new DocsEditorRedirect(docsRedirectURL)).build();

    } catch (FileSizeTooLargeException exception) {
      return Response.status(Status.FORBIDDEN).entity(exception).build();
    } catch (ServiceDependencyException exception) {
      return Response.status(Status.NOT_FOUND).build();
    }
  }
}
