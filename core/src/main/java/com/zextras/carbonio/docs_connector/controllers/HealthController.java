// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.controllers;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/health")
public interface HealthController {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  Response health();

  @GET
  @Path("ready")
  Response healthReady();

  @GET
  @Path("live")
  Response healthLive();
}
