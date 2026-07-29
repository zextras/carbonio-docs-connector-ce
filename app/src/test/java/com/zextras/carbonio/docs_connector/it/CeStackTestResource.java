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
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;

/**
 * Integration test infrastructure for carbonio-docs-connector-ce.
 *
 * <p><b>Testing philosophy (narrow integration tests):</b>
 *
 * <ul>
 *   <li>{@code carbonio-user-management} is a direct dependency of docs-connector-ce, so it runs
 *       as a real {@code registry.dev.zextras.com/dev/carbonio-user-management:devel} Docker
 *       container — not a stub. The {@code UserResourceApi} REST SDK bean therefore talks to a
 *       genuine, running service over HTTP, exactly as it does in production.
 *   <li>{@code carbonio-mailbox} is a dependency of user-management, not of docs-connector-ce
 *       directly, so it is replaced by a lightweight WireMock container acting as the mailbox
 *       internal REST API (matching the pattern already used by carbonio-tasks-ce's {@code
 *       StackTestResource}). Real mailbox's own LDAP/MariaDB/Postfix integration is covered by
 *       user-management's and mailbox's own integration test suites, not ours.
 *   <li>Consul is a real container: user-management and docs-connector-ce share it over the same
 *       Docker network for service-discovery / KV lookups at startup.
 *   <li>carbonio-files stays mocked via an in-JVM {@link WireMockServer} (unchanged from before
 *       this refactor): it is reached by the docs-connector-ce process over {@code localhost},
 *       which is sufficient because only the out-of-process app under test (not another
 *       container) needs to reach it. Only the mailbox mock had to become a container, because it
 *       must be reachable from *inside* the user-management container via a Docker network alias.
 * </ul>
 */
public class CeStackTestResource implements QuarkusTestResourceLifecycleManager {

  /** Fixed ZM_AUTH_TOKEN used across all IT tests for a valid, active, INTERNAL user. */
  public static final String AUTH_TOKEN = "test_auth_token_docs_ce";

  /** Fixed ZM_AUTH_TOKEN mapped by the mailbox mock to a GUEST (external) user. */
  public static final String GUEST_AUTH_TOKEN = "test_guest_token_docs_ce";

  /** Fixed ZM_AUTH_TOKEN mapped by the mailbox mock to a locked (non-active) INTERNAL user. */
  public static final String INACTIVE_AUTH_TOKEN = "test_inactive_token_docs_ce";

  /** Account id the mailbox mock returns for {@link #AUTH_TOKEN}. */
  public static final String TEST_USER_ID = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";

  private static final String GUEST_USER_ID = "10000000-0000-0000-0000-000000000002";
  private static final String INACTIVE_USER_ID = "10000000-0000-0000-0000-000000000003";

  /** WireMock server instance for the carbonio-files SDK (exposed to tests for stub registration). */
  public static volatile WireMockServer FILES_MOCK;

  private static volatile boolean started = false;
  private static Map<String, String> cachedConfig;

  private static Network network;
  private static GenericContainer<?> consul;
  private static GenericContainer<?> mailboxMock;
  private static GenericContainer<?> userManagement;

  @Override
  public Map<String, String> start() {
    if (started) {
      return cachedConfig;
    }

    network = Network.newNetwork();

    // Real Consul, on the shared network so user-management can reach it via the "consul" alias.
    consul = new GenericContainer<>("hashicorp/consul:1.21")
        .withNetwork(network)
        .withNetworkAliases("consul")
        .withExposedPorts(8500)
        .waitingFor(Wait.forHttp("/v1/status/leader").forPort(8500));

    // WireMock standing in for carbonio-mailbox's internal REST API, reachable by
    // user-management via the "carbonio-mailbox-mock" alias.
    mailboxMock = new GenericContainer<>("wiremock/wiremock:3.9.2")
        .withNetwork(network)
        .withNetworkAliases("carbonio-mailbox-mock")
        .withExposedPorts(8080)
        .waitingFor(
            Wait.forHttp("/__admin/health")
                .forPort(8080)
                .withStartupTimeout(Duration.ofMinutes(2)));

    // Consul and the mailbox mock have no inter-dependencies -- start in parallel.
    Startables.deepStart(consul, mailboxMock).join();

    String consulHost = consul.getHost();
    int consulPort = consul.getMappedPort(8500);

    // Pre-populate Consul KV with docs-connector-ce's own application config.
    try {
      populateConsulKv(consulHost, consulPort);
    } catch (Exception e) {
      throw new RuntimeException("Failed to populate Consul KV", e);
    }

    // Configure the mailbox mock BEFORE starting user-management: it must already answer
    // correctly the first time user-management's token-validation calls reach it.
    String mailboxMockAdminUrl =
        "http://" + mailboxMock.getHost() + ":" + mailboxMock.getMappedPort(8080);
    try {
      setupMailboxWireMockStubs(mailboxMockAdminUrl);
    } catch (Exception e) {
      throw new RuntimeException("Failed to configure mailbox WireMock stubs", e);
    }

    // Real carbonio-user-management container: direct dependency, real image, per policy.
    userManagement =
        new GenericContainer<>("registry.dev.zextras.com/dev/carbonio-user-management:devel")
            .withNetwork(network)
            .withNetworkAliases("carbonio-user-management")
            .withExposedPorts(10000)
            .withEnv("NETWORKING_CONFIG_CARBONIO_SERVICE_HOST", "0.0.0.0")
            .withEnv("NETWORKING_CONFIG_CARBONIO_SERVICE_PORT", "10000")
            .withEnv("NETWORKING_CONFIG_CARBONIO_SERVICE_DISCOVER_HOST", "consul")
            .withEnv("NETWORKING_CONFIG_CARBONIO_SERVICE_DISCOVER_PORT", "8500")
            // Point user-management at the WireMock mailbox mock instead of a real mailbox.
            .withEnv("NETWORKING_CONFIG_CARBONIO_MAILBOX_INTERNAL_HOST", "carbonio-mailbox-mock")
            .withEnv("NETWORKING_CONFIG_CARBONIO_MAILBOX_INTERNAL_PORT", "8080")
            .dependsOn(consul, mailboxMock)
            .waitingFor(
                Wait.forHttp("/q/health/live")
                    .forPort(10000)
                    .withStartupTimeout(Duration.ofMinutes(5)));

    userManagement.start();

    // Start WireMock for carbonio-files HTTP SDK (in-JVM: only the out-of-process
    // docs-connector-ce app needs to reach it, over localhost -- no container required).
    FILES_MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    FILES_MOCK.start();

    cachedConfig = Map.ofEntries(
        // Point Consul service-discover at our container
        Map.entry("networking-config.carbonio.service-discover.host", consulHost),
        Map.entry("networking-config.carbonio.service-discover.port", String.valueOf(consulPort)),
        // Point the user-management REST client (UserResourceApi) at the real container
        Map.entry("networking-config.carbonio.user-management.host", "localhost"),
        Map.entry(
            "networking-config.carbonio.user-management.port",
            String.valueOf(userManagement.getMappedPort(10000))),
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
    // Containers are static singletons -- Testcontainers' JVM shutdown hook cleans up.
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
   * Registers WireMock stubs that impersonate the mailbox internal REST API consumed by
   * user-management's {@code MailboxInternalApiClient}.
   *
   * <p>Two endpoints matter here:
   *
   * <ul>
   *   <li>{@code GET /internal/accounts/myself} — user-management's {@code UserService} calls
   *       this (via {@code internalClient.getMyAccountInfo(token)}) with a {@code Cookie} header
   *       whose value contains {@code ZM_AUTH_TOKEN=<token>}. One high-priority stub per fixed
   *       test token returns a distinct {@code AccountInfo}; any other/missing token falls
   *       through to a low-priority catch-all returning 401 — identical contract to the real
   *       mailbox for an unrecognized session. An empty-string {@code ZM_AUTH_TOKEN} never
   *       reaches this endpoint at all: user-management's own REST layer rejects a blank token
   *       with 401 before ever calling mailbox.
   *   <li>{@code GET /internal/accounts/{accountId}/info} — user-management's {@code UserService}
   *       calls this (via {@code internalClient.getAccountInfo(userId)}) for {@code
   *       WopiService.getDocsEditorAttributes}, after cookie authentication has already passed.
   *       Every IT in this module derives {@code requesterId} as {@link #TEST_USER_ID} (see the
   *       Files graphQL stub's {@code owner.id}), so a wildcard match answering with the fixed
   *       test-user record is sufficient — the same approach as the Advanced sibling's {@code
   *       AdvancedStackTestResource#setupUserManagementStubs}.
   * </ul>
   */
  private static void setupMailboxWireMockStubs(String wireMockAdminUrl) throws Exception {
    HttpClient client = HttpClient.newHttpClient();

    postAccountInfoStub(client, wireMockAdminUrl, AUTH_TOKEN, TEST_USER_ID, "active", false);
    postAccountInfoStub(client, wireMockAdminUrl, GUEST_AUTH_TOKEN, GUEST_USER_ID, "active", true);
    postAccountInfoStub(
        client, wireMockAdminUrl, INACTIVE_AUTH_TOKEN, INACTIVE_USER_ID, "locked", false);

    // Catch-all: any other/missing/invalid token -> 401 (priority 10 = lowest).
    postStub(client, wireMockAdminUrl,
        "{\"priority\":10,"
        + "\"request\":{\"method\":\"GET\",\"urlPath\":\"/internal/accounts/myself\"},"
        + "\"response\":{\"status\":401}}");

    // GET /internal/accounts/{accountId}/info -- always answers with the fixed test-user record
    // (see javadoc above for why a wildcard match is sufficient for this suite).
    postStub(client, wireMockAdminUrl,
        "{\"priority\":1,"
        + "\"request\":{\"method\":\"GET\",\"urlPathPattern\":\"/internal/accounts/[^/]+/info\"},"
        + "\"response\":{\"status\":200,"
        + "\"headers\":{\"Content-Type\":\"application/json\"},"
        + "\"jsonBody\":{"
        + "\"id\":\"" + TEST_USER_ID + "\","
        + "\"name\":\"test@example.com\","
        + "\"displayName\":\"Test User\","
        + "\"domain\":\"example.com\","
        + "\"status\":\"active\","
        + "\"isGlobalAdmin\":false,"
        + "\"isExternal\":false,"
        + "\"isExternalVirtualAccount\":false,"
        + "\"locale\":\"en_US\","
        + "\"features\":{},"
        + "\"capabilities\":{},"
        + "\"sessionLifetimeMs\":86400000"
        + "}}}");
  }

  /**
   * Registers a single WireMock stub matching {@code GET /internal/accounts/myself} for the
   * given {@code ZM_AUTH_TOKEN} (carried inside the {@code Cookie} header), returning a mailbox
   * {@code AccountInfo} JSON body for the given user id / status / external flag.
   */
  private static void postAccountInfoStub(
      HttpClient client,
      String wireMockAdminUrl,
      String token,
      String userId,
      String status,
      boolean isExternal) throws Exception {
    String stubJson =
        "{\"priority\":1,"
        + "\"request\":{"
        + "\"method\":\"GET\","
        + "\"urlPath\":\"/internal/accounts/myself\","
        + "\"headers\":{\"Cookie\":{\"contains\":\"ZM_AUTH_TOKEN=" + token + "\"}}"
        + "},"
        + "\"response\":{"
        + "\"status\":200,"
        + "\"headers\":{\"Content-Type\":\"application/json; charset=utf-8\"},"
        + "\"jsonBody\":{"
        + "\"id\":\"" + userId + "\","
        + "\"name\":\"test-" + userId + "@carbonio.test\","
        + "\"displayName\":\"Test User\","
        + "\"status\":\"" + status + "\","
        + "\"isGlobalAdmin\":false,"
        + "\"isExternal\":" + isExternal + ","
        // UserService#mapAccountInfoToUserMyself classifies GUEST-vs-INTERNAL off
        // isExternalVirtualAccount(), NOT isExternal() -- both fields exist on the mailbox-sdk
        // AccountInfo record and only the former drives the type the docs-connector filter sees.
        + "\"isExternalVirtualAccount\":" + isExternal + ","
        + "\"locale\":\"en_US\","
        + "\"features\":{},"
        + "\"capabilities\":{},"
        + "\"sessionLifetimeMs\":86400000"
        + "}"
        + "}"
        + "}";

    postStub(client, wireMockAdminUrl, stubJson);
  }

  /** Posts a single WireMock stub JSON to the admin mappings endpoint. */
  private static void postStub(HttpClient client, String baseUrl, String stubJson)
      throws Exception {
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/__admin/mappings"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(stubJson))
        .build();
    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 201) {
      throw new RuntimeException(
          "Failed to register mailbox WireMock stub (HTTP " + resp.statusCode() + "): "
              + resp.body());
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
