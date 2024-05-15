// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.services;

import com.google.inject.Inject;
import com.zextras.carbonio.docs_connector.types.health.DependencyType;
import com.zextras.carbonio.docs_connector.types.health.HealthStatus;
import com.zextras.carbonio.docs_connector.types.health.ServiceHealth;
import com.zextras.carbonio.files.FilesClient;
import com.zextras.carbonio.usermanagement.UserManagementClient;
import java.util.ArrayList;
import java.util.List;

public class HealthService {

  private final UserManagementClient userManagementClient;
  private final FilesClient filesClient;

  @Inject
  public HealthService(UserManagementClient userManagementClient, FilesClient filesClient) {
    this.userManagementClient = userManagementClient;
    this.filesClient = filesClient;
  }

  public boolean areServiceDependenciesReady() {
    return userManagementClient.healthCheck();
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
    boolean userManagementIsLive = userManagementClient.healthCheck();

    return new ServiceHealth()
        .setName("carbonio-user-management")
        .setType(DependencyType.REQUIRED)
        .setLive(userManagementIsLive)
        .setReady(userManagementIsLive);
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
