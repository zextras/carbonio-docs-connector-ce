// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.services;

import com.google.inject.Inject;
import com.zextras.carbonio.docs_connector.types.health.DependencyType;
import com.zextras.carbonio.docs_connector.types.health.HealthStatus;
import com.zextras.carbonio.docs_connector.types.health.ServiceHealth;
import com.zextras.carbonio.files.FilesClient;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import java.util.ArrayList;
import java.util.List;

public class HealthService {

  private final ManagedChannel userManagementChannel;
  private final FilesClient filesClient;

  @Inject
  public HealthService(ManagedChannel userManagementChannel, FilesClient filesClient) {
    this.userManagementChannel = userManagementChannel;
    this.filesClient = filesClient;
  }

  public boolean areServiceDependenciesReady() {
    return isUserManagementAlive();
  }

  public HealthStatus getServiceHealthStatus() {
    List<ServiceHealth> dependencies = new ArrayList<>();
    dependencies.add(getUserManagementHealth());
    dependencies.add(getFilesHealth());

    boolean docsConnectorIsReady =
        dependencies.stream()
            .filter(dependency -> DependencyType.REQUIRED.equals(dependency.getType()))
            .allMatch(ServiceHealth::isReady);

    return new HealthStatus().setReady(docsConnectorIsReady).setDependencies(dependencies);
  }

  public ServiceHealth getUserManagementHealth() {
    boolean userManagementIsLive = isUserManagementAlive();

    return new ServiceHealth()
        .setName("carbonio-user-management")
        .setType(DependencyType.REQUIRED)
        .setLive(userManagementIsLive)
        .setReady(userManagementIsLive);
  }

  private boolean isUserManagementAlive() {
    ConnectivityState state = userManagementChannel.getState(true);
    return state == ConnectivityState.READY || state == ConnectivityState.IDLE;
  }

  public ServiceHealth getFilesHealth() {
    boolean filesIsLive = filesClient.healthCheck();

    return new ServiceHealth()
      .setName("carbonio-files")
      .setType(DependencyType.REQUIRED)
      .setLive(filesIsLive)
      .setReady(filesIsLive);
  }
}
