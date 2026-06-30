// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.producers;

import com.zextras.carbonio.docs_connector.config.DocsConnectorServiceConfig;
import com.zextras.carbonio.files.FilesClient;
import com.zextras.carbonio.quarkus.extensions.bootstrap.NetworkingConfigService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * CDI producer for {@link FilesClient}. Constructs the client from the networking config service,
 * which reads host/port from Consul networking-config or {@code /etc/carbonio/docs-connector/config.properties}.
 */
@ApplicationScoped
public class FilesClientProducer {

  private final NetworkingConfigService networkingConfig;

  @Inject
  public FilesClientProducer(NetworkingConfigService networkingConfig) {
    this.networkingConfig = networkingConfig;
  }

  @Produces
  @ApplicationScoped
  public FilesClient filesClient() {
    String host = networkingConfig
        .get(DocsConnectorServiceConfig.NetworkingConfig.FILES_HOST)
        .orElseThrow();
    int port = Integer.parseInt(
        networkingConfig
            .get(DocsConnectorServiceConfig.NetworkingConfig.FILES_PORT)
            .orElseThrow()
    );
    return FilesClient.atURL("http://" + host + ":" + port);
  }
}
