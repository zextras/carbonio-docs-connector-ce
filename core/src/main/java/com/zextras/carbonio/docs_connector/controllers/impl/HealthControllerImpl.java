// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.controllers.impl;

import com.google.inject.Inject;
import com.zextras.carbonio.docs_connector.controllers.HealthController;
import com.zextras.carbonio.docs_connector.services.HealthService;
import com.zextras.carbonio.docs_connector.types.health.HealthStatus;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthControllerImpl implements HealthController {

  private static final Logger logger = LoggerFactory.getLogger(HealthControllerImpl.class);

  private final HealthService healthService;

  @Inject
  public HealthControllerImpl(HealthService healthService) {
    this.healthService = healthService;
  }

  public Response health() {
    HealthStatus serviceHealthStatus = healthService.getServiceHealthStatus();

    return serviceHealthStatus.isReady()
        ? Response.ok().entity(serviceHealthStatus).build()
        : Response.status(Status.BAD_GATEWAY).entity(serviceHealthStatus).build();
  }

  /**
   * @return a {@link Response#noContent()} representing the liveness of the service
   */
  public Response healthLive() {
    logger.debug("carbonio-docs-connector is live");

    return Response.noContent().build();
  }

  public Response healthReady() {
    boolean dependenciesAreReady = healthService.areServiceDependenciesReady();

    return dependenciesAreReady
        ? Response.noContent().build()
        : Response.status(Status.BAD_GATEWAY).build();
  }
}
