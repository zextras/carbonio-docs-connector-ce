package com.zextras.carbonio.docs_connector.controllers;

import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.resteasy.annotations.jaxrs.QueryParam;

import com.zextras.carbonio.docs_connector.types.InsertFile;

@Path("/files")
public interface FilesController {
  @POST
  @Path("/create")
  @Consumes({ "application/json" })
  @Produces(MediaType.APPLICATION_JSON)
  Response createFile(@HeaderParam("Cookie") String cookie, InsertFile insertFile, @Context HttpServletRequest httpRequest);

  @GET
  @Path("/open/{nodeId}")
  Response openFile(@HeaderParam("Cookie") String cookie, @PathParam("nodeId") UUID nodeId, @QueryParam("version") Integer version, @Context HttpServletRequest httpRequest);
}
