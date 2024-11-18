package com.zextras.carbonio.docs_connector.controllers;

import com.zextras.carbonio.docs_connector.types.InsertFile;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.annotations.jaxrs.QueryParam;

@Path("/files")
public interface FilesController {

  @POST
  @Path("/create")
  @Consumes({"application/json"})
  @Produces(MediaType.APPLICATION_JSON)
  Response createFile(
      @HeaderParam("Cookie") String cookie,
      InsertFile insertFile,
      @Context HttpServletRequest httpRequest);

  @GET
  @Path("/open/{nodeId}")
  @Produces(MediaType.APPLICATION_JSON)
  Response openFile(
      @HeaderParam("Cookie") String cookie,
      @PathParam("nodeId") UUID nodeId,
      @QueryParam("version") Integer version,
      @Context HttpServletRequest httpRequest);
}
