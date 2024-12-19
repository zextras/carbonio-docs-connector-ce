// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.controllers;

import java.io.InputStream;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/wopi/{nodeId}")
public interface WopiController {

  @GET
  @Produces({"application/json"})
  Response docsEditorAttributes(
      @QueryParam("access_token") String accessToken,
      @PathParam("nodeId") UUID nodeId,
      @QueryParam("version") Integer version,
      @Context HttpServletRequest httpRequest);

  @GET
  @Path("/contents")
  @Produces({MediaType.APPLICATION_OCTET_STREAM})
  Response getBlob(
      @QueryParam("access_token") String accessToken,
      @PathParam("nodeId") UUID nodeId,
      @QueryParam("version") Integer version,
      @Context HttpServletRequest httpRequest);

  @POST
  @Path("/contents")
  @Consumes({MediaType.APPLICATION_OCTET_STREAM})
  @Produces({"application/json"})
  Response saveBlob(
      @QueryParam("access_token") String accessToken,
      @PathParam("nodeId") UUID nodeId,
      @HeaderParam("X-COOL-WOPI-IsAutosave") Boolean xCOOLWOPIIsAutosave,
      @HeaderParam("X-COOL-WOPI-IsExitSave") Boolean xCOOLWOPIIsExitSave,
      @HeaderParam("Content-Length") Long contentLength,
      InputStream body,
      @Context HttpServletRequest httpRequest);
}
