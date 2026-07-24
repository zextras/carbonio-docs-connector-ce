// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.producers;

import com.zextras.carbonio.docs_connector.config.DocsConnectorServiceConfig;
import com.zextras.carbonio.quarkus.extensions.bootstrap.NetworkingConfigService;
import com.zextras.carbonio.user_management.sdk.rest.ApiClient;
import com.zextras.carbonio.user_management.sdk.rest.api.UserResourceApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.net.http.HttpClient;

/**
 * CDI producer for the {@link UserResourceApi} REST SDK bean (carbonio-user-management-rest-sdk).
 * Replaces the old {@code @GrpcClient("user-management")} stub: host/port come from {@link
 * NetworkingConfigService} ({@code networking-config.carbonio.user-management.*}), the same
 * values the gRPC client used.
 *
 * <p>The {@link HttpClient} is explicitly pinned to HTTP/1.1: the JDK client's default (HTTP/2
 * with an HTTP/1.1 upgrade attempt) trips plaintext servers that only speak HTTP/1.1 (e.g.
 * WireMock/Jetty in the ITs) into a protocol error/hang. carbonio-user-management is plain
 * HTTP/1.1, same as the carbonio-files SDK client wired via {@link FilesClientProducer}.
 */
@ApplicationScoped
public class UserManagementClientProducer {

  private final NetworkingConfigService networkingConfig;

  @Inject
  public UserManagementClientProducer(NetworkingConfigService networkingConfig) {
    this.networkingConfig = networkingConfig;
  }

  @Produces
  @ApplicationScoped
  public UserResourceApi userResourceApi() {
    String host = networkingConfig
        .get(DocsConnectorServiceConfig.NetworkingConfig.USER_MANAGEMENT_HOST)
        .orElseThrow();
    int port = Integer.parseInt(
        networkingConfig
            .get(DocsConnectorServiceConfig.NetworkingConfig.USER_MANAGEMENT_PORT)
            .orElseThrow()
    );

    HttpClient.Builder httpClientBuilder =
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1);
    ApiClient apiClient = new ApiClient(
        httpClientBuilder,
        ApiClient.createDefaultObjectMapper(),
        "http://" + host + ":" + port);
    return new UserResourceApi(apiClient);
  }
}
