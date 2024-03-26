package com.zextras.carbonio.docs_connector.controllers;

import java.util.UUID;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/wopi/{nodeId}")
public interface WopiController {
  @GET
  @Produces({ "application/json" })
  Response docsEditorAttributes(@QueryParam("access_token") String accessToken, @PathParam("nodeId") UUID nodeId,
      @QueryParam("version") Integer version, @Context HttpServletRequest httpRequest);

  @GET
  @Path("/contents")
  @Produces({MediaType.APPLICATION_OCTET_STREAM})
  Response getBlob(@QueryParam("access_token") String accessToken, @PathParam("nodeId") UUID nodeId,
      @QueryParam("version") Integer version, @Context HttpServletRequest httpRequest);

  @POST
  @Path("/contents")
  @Consumes({MediaType.APPLICATION_OCTET_STREAM})
  @Produces({ "application/json" })
  Response saveBlob(@QueryParam("access_token") String accessToken, @PathParam("nodeId") UUID nodeId,
      @HeaderParam("X-COOL-WOPI-IsAutosave") Boolean xCOOLWOPIIsAutosave,
      @HeaderParam("X-COOL-WOPI-IsExitSave") Boolean xCOOLWOPIIsExitSave,
      @HeaderParam("Content-Length") Long contentLength, InputStream body, @Context HttpServletRequest httpRequest);
}
