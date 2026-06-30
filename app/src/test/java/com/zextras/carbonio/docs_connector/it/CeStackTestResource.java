// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.it;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Integration test infrastructure for carbonio-docs-connector-ce.
 *
 * <p><b>Design rationale (TRAP 25):</b> In {@code @QuarkusTest} mode, Quarkus forces all
 * {@code @GrpcClient} channels to connect to an in-process server on port 9001, which conflicts
 * with any real or stub gRPC server we would start here. Therefore, {@link
 * com.zextras.carbonio.docs_connector.clients.UserManagementClient} is mocked via
 * {@code @InjectMock} in the test class itself — this resource does NOT start a gRPC server.
 *
 * <p>What this resource provides:
 * <ul>
 *   <li>A real Consul Testcontainer for service-discovery / KV lookups at startup.</li>
 *   <li>A WireMock server that stubs the carbonio-files HTTP SDK endpoints.</li>
 *   <li>Pre-populated Consul KV entries for {@code max-file-size-in-mb/*} keys.</li>
 * </ul>
 *
 * <p>The {@code gRPC user-management} dependency is handled exclusively by {@code @InjectMock}
 * in {@link DocsConnectorCeIT}.
 */
public class CeStackTestResource implements QuarkusTestResourceLifecycleManager {

  /** Fixed ZM_AUTH_TOKEN used across all IT tests. */
  public static final String AUTH_TOKEN = "test_auth_token_docs_ce";

  /** WireMock server instance (exposed to tests for stub registration). */
  public static volatile WireMockServer FILES_MOCK;

  private static volatile boolean started = false;
  private static Map<String, String> cachedConfig;

  @SuppressWarnings("rawtypes")
  private static GenericContainer consul;

  @Override
  public Map<String, String> start() {
    if (started) {
      return cachedConfig;
    }

    // Start Consul container
    consul = new GenericContainer<>("hashicorp/consul:1.21")
        .withExposedPorts(8500)
        .waitingFor(Wait.forHttp("/v1/status/leader").forPort(8500));
    consul.start();

    String consulHost = consul.getHost();
    int consulPort = (Integer) consul.getMappedPort(8500);

    // Pre-populate Consul KV with docs-connector application config
    try {
      populateConsulKv(consulHost, consulPort);
    } catch (Exception e) {
      throw new RuntimeException("Failed to populate Consul KV", e);
    }

    // Start WireMock for carbonio-files HTTP SDK
    FILES_MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    FILES_MOCK.start();

    cachedConfig = Map.ofEntries(
        // Point Consul service-discover at our container
        Map.entry("networking-config.carbonio.service-discover.host", consulHost),
        Map.entry("networking-config.carbonio.service-discover.port", String.valueOf(consulPort)),
        // Point files SDK at WireMock
        Map.entry("networking-config.carbonio.files.host", "localhost"),
        Map.entry("networking-config.carbonio.files.port", String.valueOf(FILES_MOCK.port())),
        // Point WOPI at localhost (no real server needed for CE unit-level ITs)
        Map.entry("networking-config.carbonio.wopi.host", "localhost"),
        Map.entry("networking-config.carbonio.wopi.port", "20000"),
        // Service host (for health endpoints)
        Map.entry("networking-config.carbonio.service.host", "localhost")
    );

    started = true;
    return cachedConfig;
  }

  @Override
  public void stop() {
    // Containers are static singletons — Testcontainers' JVM shutdown hook cleans up.
    if (FILES_MOCK != null && FILES_MOCK.isRunning()) {
      FILES_MOCK.stop();
    }
  }

  /**
   * Populates the Consul KV store with docs-connector application config keys.
   *
   * <p>According to memory note {@code project_consul_kv_recursive_stub.md}, the bootstrap
   * extension performs a single recursive GET — but here we are using a real Consul container,
   * so we PUT individual keys directly via the Consul HTTP API.
   */
  private static void populateConsulKv(String host, int port) throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    String baseUrl = "http://" + host + ":" + port;

    putConsulKv(client, baseUrl, "carbonio-docs-connector/max-file-size-in-mb/document", "50");
    putConsulKv(client, baseUrl, "carbonio-docs-connector/max-file-size-in-mb/presentation", "100");
    putConsulKv(client, baseUrl, "carbonio-docs-connector/max-file-size-in-mb/spreadsheet", "10");
  }

  private static void putConsulKv(HttpClient client, String baseUrl, String key, String value)
      throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/v1/kv/" + key))
        .header("Content-Type", "text/plain")
        .PUT(HttpRequest.BodyPublishers.ofString(value))
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new RuntimeException(
          "Failed to PUT Consul KV key=" + key + " (HTTP " + response.statusCode() + "): "
              + response.body());
    }
  }

  /**
   * Builds a Consul-format recursive KV response JSON for the given entries.
   *
   * <p>Used by tests that need to verify the KV response format matches what
   * {@code ApplicationConfigService} expects when reading from Consul.
   */
  public static String buildConsulKvArrayJson(String[][] entries) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < entries.length; i++) {
      String key = entries[i][0];
      String value = Base64.getEncoder()
          .encodeToString(entries[i][1].getBytes(StandardCharsets.UTF_8));
      if (i > 0) sb.append(",");
      sb.append("{\"Key\":\"").append(key).append("\",\"Value\":\"").append(value)
          .append("\",\"CreateIndex\":1,\"ModifyIndex\":1,\"LockIndex\":0,\"Flags\":0}");
    }
    sb.append("]");
    return sb.toString();
  }
}
