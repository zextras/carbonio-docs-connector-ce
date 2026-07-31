// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.it;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;

/**
 * Integration test infrastructure for carbonio-docs-connector-ce.
 *
 * <p><b>Testing philosophy (narrow integration tests):</b>
 *
 * <ul>
 *   <li>{@code carbonio-user-management} is a direct dependency of docs-connector-ce, so it runs as
 *       a real {@code registry.dev.zextras.com/dev/carbonio-user-management:devel} Docker container
 *       — not a stub. The {@code UserResourceApi} REST SDK bean therefore talks to a genuine,
 *       running service over HTTP, exactly as it does in production.
 *   <li>{@code carbonio-files} is ALSO a direct dependency (docs-connector-ce declares {@code
 *       carbonio-files-sdk} and calls it directly from {@code FilesService}/{@code WopiService}),
 *       so per the same policy it runs as a real {@code
 *       registry.dev.zextras.com/dev/carbonio-files-ce:devel} container (NOT the sibling {@code
 *       carbonio-files:devel} Advanced image -- separate repo, separate registry tag, separate
 *       storage backend), with its own real {@code postgres:16} database. Since carbonio-files-ce
 *       commit {@code 491f99f2} (PR #302) that image validates auth via the REST user-management
 *       SDK, so it is pointed at the SAME real user-management container docs-connector uses — a
 *       stubbed/unreachable user-management here makes files answer a bare, misleading 401 with no
 *       message.
 *   <li>{@code carbonio-mailbox} is a dependency of user-management, not of docs-connector-ce
 *       directly, so it is replaced by a lightweight WireMock container acting as the mailbox
 *       internal REST API (matching the pattern already used by carbonio-tasks-ce's {@code
 *       StackTestResource}).
 *   <li>Consul is NOT a real container: every sibling repo stubs the Consul KV HTTP API via
 *       WireMock rather than paying for a real {@code hashicorp/consul} image, so this module does
 *       the same. The SAME WireMock container doubles as the mailbox mock (one more network alias)
 *       — {@code carbonio-quarkus-extensions}' {@code CarbonioBootstrapFactory} performs a single
 *       {@code GET /v1/kv/?recurse} at startup and fails fast if Consul is unreachable, so the
 *       whole KV prefix is stubbed recursively (see {@code setupConsulStubs}), not per-key.
 *   <li>{@code carbonio-storages} (the blob backend files-ce uploads/downloads through, via the
 *       {@code storages-ce-sdk} client -- NOT PowerStore, which is Advanced's storage backend) is
 *       one of files' own hard dependencies (an object store), so — per the same WireMock container
 *       growing one more alias — it is kept mocked with a permissive stub, mirroring
 *       carbonio-videorecorder's {@code FilesContainerSupport}. The upload stub uses WireMock
 *       response templating to echo back the real {@code Content-Length} of whatever was uploaded
 *       as the reported blob size, so files' own DB genuinely reflects the byte count callers send
 *       — required for the file-size-limit ITs to mean anything with a real files backend.
 * </ul>
 */
public class CeStackTestResource implements QuarkusTestResourceLifecycleManager {

  /** Fixed ZM_AUTH_TOKEN used across all IT tests for a valid, active, INTERNAL user. */
  public static final String AUTH_TOKEN = "test_auth_token_docs_ce";

  /** Fixed ZM_AUTH_TOKEN mapped by the mailbox mock to a GUEST (external) user. */
  public static final String GUEST_AUTH_TOKEN = "test_guest_token_docs_ce";

  /** Fixed ZM_AUTH_TOKEN mapped by the mailbox mock to a locked (non-active) INTERNAL user. */
  public static final String INACTIVE_AUTH_TOKEN = "test_inactive_token_docs_ce";

  /**
   * Fixed ZM_AUTH_TOKEN mapped by the mailbox mock to a SECOND valid, active, INTERNAL user —
   * distinct from {@link #AUTH_TOKEN}'s user. Used as the OWNER identity for ITs that need a real
   * files node the main test user does not own (e.g. a read-only share), since files' own
   * permission model can only be driven by genuinely uploading/sharing as a different real user,
   * not by stubbing GraphQL responses (files is now a real container, not a mock).
   */
  public static final String SECOND_AUTH_TOKEN = "test_second_auth_token_docs_ce";

  /** Account id the mailbox mock returns for {@link #AUTH_TOKEN}. */
  public static final String TEST_USER_ID = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";

  /** Account id the mailbox mock returns for {@link #SECOND_AUTH_TOKEN}. */
  public static final String SECOND_USER_ID = "10000000-0000-0000-0000-000000000004";

  private static final String GUEST_USER_ID = "10000000-0000-0000-0000-000000000002";
  private static final String INACTIVE_USER_ID = "10000000-0000-0000-0000-000000000003";

  /** carbonio-files' own database (real postgres:16 container — files is a direct dependency). */
  private static final String FILES_DB_NAME = "carbonio-files-db";

  private static final String FILES_DB_USER = "postgres";
  private static final String FILES_DB_PASSWORD = "postgres";

  /**
   * Host of the real carbonio-files container, reachable from the JVM test process (host network),
   * exposed so ITs can talk to files DIRECTLY (bypassing docs-connector) when a scenario needs
   * control docs-connector's own REST surface cannot give them (uploading a specific byte
   * size/extension as a specific owner, or creating a share) — see {@link #rawUploadToFiles} /
   * {@link #rawCreateShare}.
   */
  public static volatile String FILES_HOST;

  /** Mapped host port of the real carbonio-files container. See {@link #FILES_HOST}. */
  public static volatile int FILES_PORT;

  private static volatile boolean started = false;
  private static Map<String, String> cachedConfig;

  private static Network network;
  private static GenericContainer<?> wireMock;
  private static PostgreSQLContainer<?> postgres;
  private static GenericContainer<?> userManagement;
  private static GenericContainer<?> files;

  @Override
  public Map<String, String> start() {
    if (started) {
      return cachedConfig;
    }

    network = Network.newNetwork();

    // Single WireMock container plays three roles via three network aliases:
    //   "consul"               -- Consul KV/agent HTTP API stub (see setupConsulStubs)
    //   "carbonio-mailbox-mock" -- carbonio-mailbox internal REST API stub (mailbox is
    //                              user-management's dependency, not ours)
    //   "carbonio-storages"    -- carbonio-storages blob backend stub (files-ce's own
    //                              hard dependency, kept mocked per task scope)
    // --global-response-templating lets the storages upload stub echo back the real
    // Content-Length of the uploaded blob as the reported "size", so files' DB genuinely reflects
    // what was uploaded instead of a canned constant.
    wireMock =
        new GenericContainer<>("wiremock/wiremock:3.9.2")
            .withNetwork(network)
            .withNetworkAliases("carbonio-mailbox-mock", "consul", "carbonio-storages")
            .withCommand("--global-response-templating")
            .withExposedPorts(8080)
            .waitingFor(
                Wait.forHttp("/__admin/health")
                    .forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    // carbonio-files' own database. CE docs-connector itself has NO database (see
    // application.properties: "CE ships NO migration classes, it has no DB") -- this postgres
    // container exists solely for files.
    postgres =
        new PostgreSQLContainer<>("postgres:16")
            .withNetwork(network)
            .withNetworkAliases("carbonio-postgres")
            .withDatabaseName(FILES_DB_NAME)
            .withUsername(FILES_DB_USER)
            .withPassword(FILES_DB_PASSWORD);

    // WireMock and postgres have no inter-dependencies -- start in parallel.
    Startables.deepStart(wireMock, postgres).join();

    String wireMockAdminUrl = "http://" + wireMock.getHost() + ":" + wireMock.getMappedPort(8080);

    // Configure ALL WireMock stubs BEFORE starting user-management/files: both perform
    // startup-time calls (Consul KV recurse, mailbox token validation) that must already be
    // answered correctly the first time they're reached.
    try {
      setupMailboxWireMockStubs(wireMockAdminUrl);
      setupConsulStubs(wireMockAdminUrl);
      setupStoragesStubs(wireMockAdminUrl);
    } catch (Exception e) {
      throw new RuntimeException("Failed to configure WireMock stubs", e);
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
            .withEnv("NETWORKING_CONFIG_CARBONIO_SERVICE_DISCOVER_PORT", "8080")
            // Point user-management at the WireMock mailbox mock instead of a real mailbox.
            .withEnv("NETWORKING_CONFIG_CARBONIO_MAILBOX_INTERNAL_HOST", "carbonio-mailbox-mock")
            .withEnv("NETWORKING_CONFIG_CARBONIO_MAILBOX_INTERNAL_PORT", "8080")
            .dependsOn(wireMock)
            .waitingFor(
                Wait.forHttp("/q/health/live")
                    .forPort(10000)
                    .withStartupTimeout(Duration.ofMinutes(5)));

    userManagement.start();

    // Real carbonio-files container: direct dependency (carbonio-files-sdk), real image, per
    // policy. Its own DB is the postgres container above; its storages/UM dependencies point at
    // the containers already running in this same network.
    files =
        new GenericContainer<>("registry.dev.zextras.com/dev/carbonio-files-ce:devel")
            .withNetwork(network)
            .withNetworkAliases("carbonio-files")
            .withExposedPorts(10000)
            .withEnv("CARBONIO_FILES_HOST", "0.0.0.0")
            .withEnv("CARBONIO_FILES_PORT", "10000")
            .withEnv("CARBONIO_POSTGRESQL_HOST", "carbonio-postgres")
            .withEnv("CARBONIO_POSTGRESQL_PORT", "5432")
            .withEnv("CARBONIO_STORAGES_HOST", "carbonio-storages")
            .withEnv("CARBONIO_STORAGES_PORT", "8080")
            // Critical (see class javadoc): files:devel validates auth via the REST
            // user-management SDK as of 2026-07-27 -- point it at the REAL container, not a stub,
            // or every authenticated call answers a bare, unhelpful 401.
            .withEnv("CARBONIO_USER_MANAGEMENT_HOST", "carbonio-user-management")
            .withEnv("CARBONIO_USER_MANAGEMENT_PORT", "10000")
            .withEnv("CARBONIO_SERVICE_DISCOVER_HOST", "consul")
            .withEnv("CARBONIO_SERVICE_DISCOVER_PORT", "8080")
            .withEnv("CARBONIO_MAILBOX_HOST", "carbonio-mailbox-mock")
            .withEnv("CARBONIO_MAILBOX_PORT", "8080")
            // Workaround for a real bug in carbonio-files-ce:devel itself (independent of this
            // test harness): commit 256fee2e ("adopt carbonio-systemd-notify for native sd_notify
            // readiness", #250) added SystemdNotify.ready(...) to NettyServer.start(), and that
            // call requires --enable-preview (class file version 65.65535). docker/entrypoint.sh's
            // `exec java ...` line was never updated to pass that flag, so the shipped image
            // crashes with "UnsupportedClassVersionError: Preview features are not enabled" the
            // instant it reaches NettyServer.start() (i.e. as soon as DB connectivity succeeds) --
            // confirmed by running the image directly with `docker run`. JAVA_TOOL_OPTIONS is
            // picked up automatically by the JVM launcher inside entrypoint.sh's `exec java ...`
            // without needing to touch the image or its entrypoint script.
            .withEnv("JAVA_TOOL_OPTIONS", "--enable-preview")
            .dependsOn(postgres, wireMock, userManagement)
            .waitingFor(
                // files' HealthController answers /health/live with 204 No Content (not 200) --
                // HttpWaitStrategy's default matcher only accepts 200, so without this explicit
                // forStatusCode(204) the wait strategy times out after 5 minutes even though the
                // container is genuinely healthy and answering the whole time (confirmed by
                // polling the endpoint directly during a real run: consistent HTTP 204 from the
                // first check onward).
                Wait.forHttp("/health/live")
                    .forPort(10000)
                    .forStatusCode(204)
                    .withStartupTimeout(Duration.ofMinutes(5)));

    files.start();

    FILES_HOST = files.getHost();
    FILES_PORT = files.getMappedPort(10000);

    cachedConfig =
        Map.ofEntries(
            // Point Consul service-discover at the WireMock stub
            Map.entry("networking-config.carbonio.service-discover.host", wireMock.getHost()),
            Map.entry(
                "networking-config.carbonio.service-discover.port",
                String.valueOf(wireMock.getMappedPort(8080))),
            // Point the user-management REST client (UserResourceApi) at the real container
            Map.entry("networking-config.carbonio.user-management.host", "localhost"),
            Map.entry(
                "networking-config.carbonio.user-management.port",
                String.valueOf(userManagement.getMappedPort(10000))),
            // Point the files SDK at the real files container
            Map.entry("networking-config.carbonio.files.host", "localhost"),
            Map.entry("networking-config.carbonio.files.port", String.valueOf(FILES_PORT)),
            // Point WOPI at localhost (no real server needed for CE unit-level ITs)
            Map.entry("networking-config.carbonio.wopi.host", "localhost"),
            Map.entry("networking-config.carbonio.wopi.port", "20000"),
            // Service host (for health endpoints)
            Map.entry("networking-config.carbonio.service.host", "localhost"));

    started = true;
    return cachedConfig;
  }

  @Override
  public void stop() {
    // Containers are static singletons -- Testcontainers' JVM shutdown hook cleans up.
  }

  /**
   * Registers WireMock stubs that impersonate the mailbox internal REST API consumed by
   * user-management's {@code MailboxInternalApiClient}.
   *
   * <p>Two endpoints matter here:
   *
   * <ul>
   *   <li>{@code GET /internal/accounts/myself} — user-management's {@code UserService} calls this
   *       (via {@code internalClient.getMyAccountInfo(token)}) with a {@code Cookie} header whose
   *       value contains {@code ZM_AUTH_TOKEN=<token>}. One high-priority stub per fixed test token
   *       returns a distinct {@code AccountInfo}; any other/missing token falls through to a
   *       low-priority catch-all returning 401 — identical contract to the real mailbox for an
   *       unrecognized session. An empty-string {@code ZM_AUTH_TOKEN} never reaches this endpoint
   *       at all: user-management's own REST layer rejects a blank token with 401 before ever
   *       calling mailbox.
   *   <li>{@code GET /internal/accounts/{accountId}/info} — user-management's {@code UserService}
   *       calls this (via {@code internalClient.getAccountInfo(userId)}) for {@code
   *       WopiService.getDocsEditorAttributes}. Every IT in this module derives {@code requesterId}
   *       as {@link #TEST_USER_ID}, so a wildcard match answering with the fixed test-user record
   *       is sufficient.
   * </ul>
   */
  private static void setupMailboxWireMockStubs(String wireMockAdminUrl) throws Exception {
    HttpClient client = HttpClient.newHttpClient();

    postAccountInfoStub(client, wireMockAdminUrl, AUTH_TOKEN, TEST_USER_ID, "active", false);
    postAccountInfoStub(client, wireMockAdminUrl, GUEST_AUTH_TOKEN, GUEST_USER_ID, "active", true);
    postAccountInfoStub(
        client, wireMockAdminUrl, INACTIVE_AUTH_TOKEN, INACTIVE_USER_ID, "locked", false);
    postAccountInfoStub(
        client, wireMockAdminUrl, SECOND_AUTH_TOKEN, SECOND_USER_ID, "active", false);

    // Catch-all: any other/missing/invalid token -> 401 (priority 10 = lowest).
    postStub(
        client,
        wireMockAdminUrl,
        "{\"priority\":10,"
            + "\"request\":{\"method\":\"GET\",\"urlPath\":\"/internal/accounts/myself\"},"
            + "\"response\":{\"status\":401}}");

    // GET /internal/accounts/{accountId}/info -- always answers with the fixed test-user record
    // (see javadoc above for why a wildcard match is sufficient for this suite).
    postStub(
        client,
        wireMockAdminUrl,
        "{\"priority\":1,"
            + "\"request\":{\"method\":\"GET\",\"urlPathPattern\":\"/internal/accounts/[^/]+/info\"},"
            + "\"response\":{\"status\":200,\"headers\":{\"Content-Type\":\"application/json\"},"
            + "\"jsonBody\":{\"id\":\""
            + TEST_USER_ID
            + "\","
            + "\"name\":\"test@example.com\","
            + "\"displayName\":\"Test User\","
            + "\"domain\":\"example.com\","
            + "\"status\":\"active\","
            + "\"isGlobalAdmin\":false,"
            + "\"isExternal\":false,"
            + "\"isExternalVirtualAccount\":false,"
            + "\"locale\":\"en_US\","
            + "\"features\":{\"carbonioFeatureFilesEnabled\":true},"
            + "\"capabilities\":{},"
            + "\"sessionLifetimeMs\":86400000"
            + "}}}");
  }

  /**
   * Registers a single WireMock stub matching {@code GET /internal/accounts/myself} for the given
   * {@code ZM_AUTH_TOKEN} (carried inside the {@code Cookie} header), returning a mailbox {@code
   * AccountInfo} JSON body for the given user id / status / external flag.
   */
  private static void postAccountInfoStub(
      HttpClient client,
      String wireMockAdminUrl,
      String token,
      String userId,
      String status,
      boolean isExternalVirtualAccount)
      throws Exception {
    String stubJson =
        "{\"priority\":1,"
            + "\"request\":{"
            + "\"method\":\"GET\","
            + "\"urlPath\":\"/internal/accounts/myself\","
            + "\"headers\":{\"Cookie\":{\"contains\":\"ZM_AUTH_TOKEN="
            + token
            + "\"}}"
            + "},"
            + "\"response\":{"
            + "\"status\":200,"
            + "\"headers\":{\"Content-Type\":\"application/json; charset=utf-8\"},"
            + "\"jsonBody\":{"
            + "\"id\":\""
            + userId
            + "\","
            + "\"name\":\"test-"
            + userId
            + "@carbonio.test\","
            + "\"displayName\":\"Test User\","
            + "\"status\":\""
            + status
            + "\","
            + "\"isGlobalAdmin\":false,"
            // The two booleans are NOT synonyms and for a guest they are OPPOSITE, so the mock must
            // emit what mailbox really emits or user-management is fed impossible input:
            //   isExternal               -- derived, mailbox's Account#isAccountExternal(): true
            // only
            //                               when zimbraMailTransport does not match the server
            // named by
            //                               zimbraMailHost (foreign/relayed MTA routing). A guest
            // is
            //                               provisioned with zimbraMailHost = the local server, so
            // a
            //                               real guest is FALSE here.
            //   isExternalVirtualAccount -- the LDAP zimbraIsExternalVirtualAccount boolean: TRUE
            // for a
            //                               guest / external-share virtual account.
            // UserService#mapAccountInfoToUserMyself classifies GUEST-vs-INTERNAL off
            // isExternalVirtualAccount() only. Emitting the same value into both (as an earlier
            // version
            // of this stub did) would still pass even if that mapping regressed back to
            // isExternal(),
            // which is exactly the defect CO-3822 fixed -- so keep them decoupled.
            + "\"isExternal\":false,"
            + "\"isExternalVirtualAccount\":"
            + isExternalVirtualAccount
            + ","
            + "\"locale\":\"en_US\","
            // carbonioFeatureFilesEnabled=true: carbonio-files' AuthenticationHandler refuses
            // access ("Files feature is not enabled for user") unless the requester's
            // UserMyself#getCarbonioAttributes() map has this key set to "TRUE". That map is
            // built by UserMyself's List<String> constructor from user-management's MyselfDto
            // features list, which UserService#mapAccountInfoToUserMyself derives from exactly
            // this mailbox AccountInfo#features() map (Map<String,Boolean>) -- only true-valued
            // keys survive into the list. Real accounts have this COS/feature flag enabled by
            // default; the mock must say so explicitly or every real-files call gets a 403.
            + "\"features\":{\"carbonioFeatureFilesEnabled\":true},"
            + "\"capabilities\":{},"
            + "\"sessionLifetimeMs\":86400000"
            + "}"
            + "}"
            + "}";

    postStub(client, wireMockAdminUrl, stubJson);
  }

  /**
   * Registers WireMock stubs that impersonate the Consul HTTP API for BOTH docs-connector-ce's own
   * KV namespace and carbonio-files' KV namespace.
   *
   * <p>{@code carbonio-quarkus-extensions}' {@code CarbonioBootstrapFactory} (used by
   * docs-connector-ce and user-management) issues a SINGLE ROOT recursive GET at boot: {@code GET
   * /v1/kv/?recurse} — so the root recurse stub below carries docs-connector's own {@code
   * carbonio-docs-connector/*} keys.
   *
   * <p>carbonio-files is the OLD pre-Quarkus Guice/Ebean service (its own Quarkus rewrite is still
   * in progress on a separate branch): its {@code FilesConfig} reads DB credentials via {@code
   * ServiceDiscoverHttpClient} with INDIVIDUAL per-key GETs — {@code GET
   * /v1/kv/carbonio-files/db-name}, {@code .../db-username}, {@code .../db-password} (see {@code
   * carbonio-files-ce core/src/main/java/.../clients/ServiceDiscoverHttpClient.java}) — so those
   * three keys are ALSO stubbed individually (a recursive stub alone would never be matched by
   * files' actual requests).
   */
  private static void setupConsulStubs(String wireMockAdminUrl) throws Exception {
    HttpClient client = HttpClient.newHttpClient();

    // docs-connector-ce's own config (root recurse; prefix == "").
    postConsulKvRecursiveStub(
        client,
        wireMockAdminUrl,
        "",
        new String[][] {
          {"carbonio-docs-connector/max-file-size-in-mb/document", "50"},
          {"carbonio-docs-connector/max-file-size-in-mb/presentation", "100"},
          {"carbonio-docs-connector/max-file-size-in-mb/spreadsheet", "10"},
        });

    // carbonio-files DB credentials -- individual-key GETs (see javadoc above).
    postConsulKvIndividualStub(client, wireMockAdminUrl, "carbonio-files/db-name", FILES_DB_NAME);
    postConsulKvIndividualStub(
        client, wireMockAdminUrl, "carbonio-files/db-username", FILES_DB_USER);
    postConsulKvIndividualStub(
        client, wireMockAdminUrl, "carbonio-files/db-password", FILES_DB_PASSWORD);
    // Recursive stub too (defensive: covers a future Quarkus migration of the files image, which
    // would issue a root/prefix recurse instead of individual-key GETs).
    postConsulKvRecursiveStub(
        client,
        wireMockAdminUrl,
        "carbonio-files/",
        new String[][] {
          {"carbonio-files/db-name", FILES_DB_NAME},
          {"carbonio-files/db-username", FILES_DB_USER},
          {"carbonio-files/db-password", FILES_DB_PASSWORD},
        });

    // Catch-all for unknown KV keys -> 404 (priority 10 = lowest; urlPathPattern ignores query).
    // Harmless for files' own page-token-secret-key lookup/creation dance (FilesConfig#
    // initializeSecretKey swallows any failure and falls back to a default signing key).
    postStub(
        client,
        wireMockAdminUrl,
        "{\"priority\":10,"
            + "\"request\":{\"method\":\"GET\",\"urlPathPattern\":\"/v1/kv/.*\"},"
            + "\"response\":{\"status\":404}}");
    postStub(
        client,
        wireMockAdminUrl,
        "{\"priority\":10,"
            + "\"request\":{\"method\":\"PUT\",\"urlPathPattern\":\"/v1/kv/.*\"},"
            + "\"response\":{\"status\":200,\"body\":\"true\"}}");

    // Service registration / deregistration -> 200
    for (String pattern :
        new String[] {
          "/v1/agent/service/register.*",
          "/v1/agent/service/deregister/.*",
          "/v1/agent/check/register.*",
          "/v1/agent/check/deregister/.*"
        }) {
      postStub(
          client,
          wireMockAdminUrl,
          "{\"request\":{\"method\":\"PUT\",\"urlPathPattern\":\""
              + pattern
              + "\"},"
              + "\"response\":{\"status\":200}}");
    }

    // Service discovery -> empty array
    for (String pattern : new String[] {"/v1/health/service/.*", "/v1/catalog/service/.*"}) {
      postStub(
          client,
          wireMockAdminUrl,
          "{\"request\":{\"method\":\"GET\",\"urlPathPattern\":\""
              + pattern
              + "\"},"
              + "\"response\":{\"status\":200,"
              + "\"headers\":{\"Content-Type\":\"application/json\"},\"body\":\"[]\"}}");
    }

    // Agent self / status (urlPath = path-only exact match, ignores query string)
    postStub(
        client,
        wireMockAdminUrl,
        "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/v1/agent/self\"},"
            + "\"response\":{\"status\":200,"
            + "\"headers\":{\"Content-Type\":\"application/json\"},"
            + "\"jsonBody\":{\"Config\":{\"Datacenter\":\"dc1\",\"NodeName\":\"mock-consul\"}}}}");
    postStub(
        client,
        wireMockAdminUrl,
        "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/v1/status/leader\"},"
            + "\"response\":{\"status\":200,"
            + "\"headers\":{\"Content-Type\":\"application/json\"},"
            + "\"body\":\"\\\"127.0.0.1:8300\\\"\"}}");
  }

  /**
   * Registers a single WireMock stub that matches the Consul recursive KV fetch: {@code GET
   * /v1/kv/{prefix}?recurse} (urlPath ignores the query string).
   *
   * <p>The response is a JSON array with one object per key-value pair, values base64-encoded,
   * exactly what the real Consul API returns for {@code ?recurse}.
   */
  private static void postConsulKvRecursiveStub(
      HttpClient client, String baseUrl, String prefix, String[][] kvEntries) throws Exception {
    StringBuilder arrayBody = new StringBuilder("[");
    for (int i = 0; i < kvEntries.length; i++) {
      String key = kvEntries[i][0];
      String value = kvEntries[i][1];
      String b64 = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
      if (i > 0) arrayBody.append(",");
      arrayBody
          .append("{\"LockIndex\":0,\"Key\":\"")
          .append(key)
          .append("\",\"Flags\":0,")
          .append("\"Value\":\"")
          .append(b64)
          .append("\",\"CreateIndex\":1,\"ModifyIndex\":1}");
    }
    arrayBody.append("]");

    String escapedBody = arrayBody.toString().replace("\\", "\\\\").replace("\"", "\\\"");

    postStub(
        client,
        baseUrl,
        "{\"priority\":1,"
            + "\"request\":{\"method\":\"GET\",\"urlPath\":\"/v1/kv/"
            + prefix
            + "\"},"
            + "\"response\":{\"status\":200,"
            + "\"headers\":{\"Content-Type\":\"application/json\"},"
            + "\"body\":\""
            + escapedBody
            + "\"}}");
  }

  /**
   * Registers a WireMock stub matching an INDIVIDUAL-key Consul KV GET — {@code GET
   * /v1/kv/<full-key>} — used by carbonio-files' {@code ServiceDiscoverHttpClient}, which reads
   * each DB credential key with a separate request (unlike the bootstrap-extension's single root
   * recurse). The response is a single-element JSON array in Consul's standard format, value
   * base64-encoded.
   */
  private static void postConsulKvIndividualStub(
      HttpClient client, String baseUrl, String key, String value) throws Exception {
    String b64 = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    String body =
        "[{\"LockIndex\":0,\"Key\":\""
            + key
            + "\",\"Flags\":0,"
            + "\"Value\":\""
            + b64
            + "\",\"CreateIndex\":1,\"ModifyIndex\":1}]";
    String escapedBody = body.replace("\\", "\\\\").replace("\"", "\\\"");
    postStub(
        client,
        baseUrl,
        "{\"priority\":1,"
            + "\"request\":{\"method\":\"GET\",\"urlPath\":\"/v1/kv/"
            + key
            + "\"},"
            + "\"response\":{\"status\":200,"
            + "\"headers\":{\"Content-Type\":\"application/json\"},"
            + "\"body\":\""
            + escapedBody
            + "\"}}");
  }

  /**
   * Registers WireMock stubs impersonating carbonio-storages (the blob backend files-ce uploads/
   * downloads through via the {@code storages-ce-sdk} client). Kept mocked per task scope — it is
   * one of files' own hard dependencies, not something worth standing up for real here.
   *
   * <p><b>Upload: two-tier "size" echo, split by request size.</b> Files' download response sets
   * its {@code Content-Length} header from the node's RECORDED size (the "size" the upload stub
   * answered at upload time, persisted via {@code BlobService#uploadFileOrFileVersion}, {@code
   * node.setSize(uploadResponse.getSize())}) — completely independent of how many bytes the
   * download stub below will actually stream. Since the download stub always streams the same fixed
   * {@value #MOCKED_STORAGE_FILE_CONTENT} fixture (storages is mocked, so it never really persists
   * what was uploaded), any node whose recorded size differs from that fixture's byte length makes
   * files promise a {@code Content-Length} it then doesn't deliver on — a genuine "declared 204
   * bytes, sent 27" truncated-response bug that leaves strict HTTP clients (Apache HttpClient, used
   * by both docs-connector's own {@code FilesClient} and this IT's RestAssured client) blocked on a
   * read that will never complete, until their own socket-read timeout fires (confirmed directly: a
   * raw {@code GET /download/{nodeId}} against real files-ce, bypassing docs-connector entirely,
   * reproduces {@code IOException: fixed content-length: 204, bytes received: 27} in under 50ms
   * once the WireMock request journal is inspected). This is exactly what happened here: the old
   * in-JVM WireMock stub for files ALSO stubbed downloads, so its "size" and its download body were
   * always trivially consistent by construction; now that files is a real container computing this
   * independently, they can silently diverge.
   *
   * <p>So: uploads with a genuinely large multipart {@code Content-Length} (>= 7 digits, i.e. >=
   * 1,000,000 bytes — comfortably below the smallest real oversized fixture this suite uploads, 11
   * MB, and comfortably above the multipart-wrapped size of every other, tiny, test fixture upload
   * in this suite, ~200-400 bytes) keep the REAL echoed Content-Length, which is what the
   * file-size-limit ITs need (they assert on files exceeding the real 10 MB / 100 MB config
   * limits). Every other (small) upload gets the FIXED size of the download fixture itself, so that
   * any later download of that same node has a correct, honest Content-Length.
   *
   * <p><b>Path matching MUST be exact (`urlPath`), never a `.*upload.*`/`.*download.*` substring
   * pattern.</b> Files itself also reads Consul KV keys named {@code
   * carbonio-files/max-uploadable-size-in-mb} and {@code
   * carbonio-files/max-downloadable-size-in-mb} (see {@code
   * FilesConfig#getMaxUploadableFileSizeInMb}/{@code getMaxDownloadableFileSizeInMb}) — both paths
   * legitimately CONTAIN the substrings "upload"/"download", so a loose {@code urlPathPattern} here
   * silently steals those Consul KV lookups too (confirmed via {@code GET /__admin/requests}:
   * {@code GET /v1/kv/carbonio-files/max-uploadable-size-in-mb} was being answered by the storages
   * upload stub instead of falling through to the Consul 404 catch-all). With the OLD single
   * templated stub this was accidentally harmless: {@code {{request.headers.[Content-Length]}}} has
   * nothing to substitute on a body-less GET (no Content-Length header), so WireMock emits
   * malformed JSON, {@code ServiceDiscoverHttpClient #getConfig} fails to parse it (a checked
   * {@code IOException}, caught, treated as "config absent") and files quietly falls back to "no
   * limit". Switching the small-upload stub to a static, validly-parsing JSON body (needed for the
   * fix above) turns that accident into a real bug: valid-but-wrong-shaped JSON parses fine, then
   * {@code readTree(body).get(0).get("Value")} NPEs on the object node's absent index 0 — an
   * UNCHECKED exception that is NOT caught, surfacing as a real upload-time 500 (confirmed
   * directly: {@code java.lang.NullPointerException: ... ServiceDiscoverHttpClient.getConfig} in
   * the real files-ce container's own logs). The actual storages endpoints are exactly {@code
   * /upload} and {@code /download} (see the {@code storages-ce-sdk} retrofit interface:
   * {@code @PUT/@POST("upload")}, {@code @GET("download")}) — Consul KV lookups always live under
   * {@code /v1/kv/...} — so an exact {@code urlPath} match (which compares the path only, ignoring
   * the query string) is both simpler and closes this whole class of accidental collision for good.
   */
  private static void setupStoragesStubs(String wireMockAdminUrl) throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    int fixedDownloadSize = MOCKED_STORAGE_FILE_CONTENT.getBytes(StandardCharsets.UTF_8).length;

    // Large uploads (file-size-limit ITs, 11 MB / 101 MB oversized fixtures): keep the real,
    // genuinely-echoed Content-Length -- HIGHER priority (1) so it is matched first.
    postStub(
        client,
        wireMockAdminUrl,
        "{\"priority\":1,"
            + "\"request\":{\"method\":\"ANY\",\"urlPath\":\"/upload\","
            + "\"headers\":{\"Content-Length\":{\"matches\":\"^[0-9]{7,}$\"}}},"
            + "\"response\":{\"status\":200,"
            + "\"headers\":{\"Content-Type\":\"application/json\"},"
            + "\"transformers\":[\"response-template\"],"
            + "\"body\":\"{\\\"digest\\\":\\\"00000000-0000-0000-0000-000000000001\\\","
            + "\\\"size\\\":{{request.headers.[Content-Length]}},"
            + "\\\"digest_algorithm\\\":\\\"MD5\\\"}\"}}");

    // Every other (small) upload: FIXED size matching the download fixture's real byte length --
    // see class/method javadoc above for why this must NOT be a real Content-Length echo.
    postStub(
        client,
        wireMockAdminUrl,
        "{\"priority\":2,"
            + "\"request\":{\"method\":\"ANY\",\"urlPath\":\"/upload\"},"
            + "\"response\":{\"status\":200,"
            + "\"headers\":{\"Content-Type\":\"application/json\"},"
            + "\"body\":\"{\\\"digest\\\":\\\"00000000-0000-0000-0000-000000000001\\\","
            + "\\\"size\\\":"
            + fixedDownloadSize
            + ","
            + "\\\"digest_algorithm\\\":\\\"MD5\\\"}\"}}");

    // Download: fixed canned bytes (see javadoc above for why this can't be a genuine echo).
    postStub(
        client,
        wireMockAdminUrl,
        "{\"priority\":1,"
            + "\"request\":{\"method\":\"GET\",\"urlPath\":\"/download\"},"
            + "\"response\":{\"status\":200,"
            + "\"headers\":{\"Content-Type\":\"application/octet-stream\"},"
            + "\"base64Body\":\""
            + Base64.getEncoder()
                .encodeToString(MOCKED_STORAGE_FILE_CONTENT.getBytes(StandardCharsets.UTF_8))
            + "\"}}");

    // Health checks
    postStub(
        client,
        wireMockAdminUrl,
        "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/health\"},"
            + "\"response\":{\"status\":200}}");

    // bulk-delete: full success, empty failed-ids list.
    postStub(
        client,
        wireMockAdminUrl,
        "{\"priority\":1,\"request\":{\"method\":\"POST\",\"urlPath\":\"/bulk-delete\"},"
            + "\"response\":{\"status\":200,"
            + "\"headers\":{\"Content-Type\":\"application/json\"},\"body\":\"{\\\"ids\\\":[]}\"}}");

    // Catch-all: accept ANY other storage op, return a minimal upload-shaped response.
    postStub(
        client,
        wireMockAdminUrl,
        "{\"priority\":100,"
            + "\"request\":{\"method\":\"ANY\",\"urlPattern\":\"/.*\"},"
            + "\"response\":{\"status\":200,"
            + "\"headers\":{\"Content-Type\":\"application/json\"},"
            + "\"body\":\"{\\\"digest\\\":\\\"00000000-0000-0000-0000-000000000001\\\","
            + "\\\"size\\\":1024,"
            + "\\\"digest_algorithm\\\":\\\"MD5\\\"}\"}}");
  }

  /**
   * Fixed byte content the mocked storages backend always returns for a download, regardless of
   * node/version or what bytes were actually uploaded (storages is mocked, so it never really
   * stores anything). ITs that assert on downloaded content assert against THIS constant.
   */
  public static final String MOCKED_STORAGE_FILE_CONTENT = "mocked-storage-blob-content";

  /** Posts a single WireMock stub JSON to the admin mappings endpoint. */
  private static void postStub(HttpClient client, String baseUrl, String stubJson)
      throws Exception {
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/__admin/mappings"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(stubJson))
            .build();
    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 201) {
      throw new RuntimeException(
          "Failed to register WireMock stub (HTTP " + resp.statusCode() + "): " + resp.body());
    }
  }

  /**
   * Uploads a file DIRECTLY to the real carbonio-files container, bypassing docs-connector's own
   * {@code /files/create} (which only ever uploads fixed, small blank templates). Replicates
   * exactly the HTTP contract {@code carbonio-files-sdk}'s {@code FilesClient#uploadFile} uses
   * against files' {@code POST /upload/}: a {@code Cookie} header carrying {@code
   * ZM_AUTH_TOKEN=<token>}, a base64-encoded {@code Filename} header, and a raw {@code ParentId}
   * header — needed here so ITs can control the exact byte size / extension / owner of a real files
   * node (file-size-limit tests, the read-only-share test), which docs-connector's template upload
   * cannot do.
   *
   * @return the real node id created by files
   */
  public static String rawUploadToFiles(
      String authToken, String parentId, String filename, String mimeType, byte[] content)
      throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    String filenameB64 =
        Base64.getEncoder().encodeToString(filename.getBytes(StandardCharsets.UTF_8));
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://" + FILES_HOST + ":" + FILES_PORT + "/upload/"))
            .header("Cookie", "ZM_AUTH_TOKEN=" + authToken)
            .header("Filename", filenameB64)
            .header("ParentId", parentId)
            .header("Content-Type", mimeType)
            .timeout(Duration.ofSeconds(120))
            .POST(BodyPublishers.ofByteArray(content))
            .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200 && response.statusCode() != 201) {
      throw new RuntimeException(
          "Raw upload to files failed (HTTP " + response.statusCode() + "): " + response.body());
    }
    // Response body: {"nodeId":"<uuid>"}
    String body = response.body();
    int idx = body.indexOf("\"nodeId\"");
    int colon = body.indexOf(':', idx);
    int firstQuote = body.indexOf('"', colon + 1);
    int secondQuote = body.indexOf('"', firstQuote + 1);
    return body.substring(firstQuote + 1, secondQuote);
  }

  /**
   * Creates a share on a real files node DIRECTLY against the real files container's GraphQL
   * endpoint, as the node's owner. Used to drive genuine read-only-permission scenarios: files'
   * permission model is real DB state now that files is a real container, so a share must actually
   * be created via its own API rather than stubbed.
   */
  public static void rawCreateShare(
      String ownerAuthToken, String nodeId, String shareTargetId, String permission)
      throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    // Selection set must stay within carbonio-files-CE's schema: its `Share` type has no `id`
    // field (fields are created_at / node / share_target / permission / expires_at -- see
    // carbonio-files-ce core/src/main/resources/api/schema.graphql). Selecting `id` works against
    // the Advanced image but fails CE validation with "Field 'id' in type 'Share' is undefined".
    String query =
        "{\"query\":\"mutation { createShare(node_id: \\\""
            + nodeId
            + "\\\", "
            + "share_target_id: \\\""
            + shareTargetId
            + "\\\", permission: "
            + permission
            + ") "
            + "{ permission node { id } } }\"}";
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://" + FILES_HOST + ":" + FILES_PORT + "/graphql/"))
            .header("Cookie", "ZM_AUTH_TOKEN=" + ownerAuthToken)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(query))
            .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new RuntimeException(
          "createShare failed (HTTP " + response.statusCode() + "): " + response.body());
    }
    if (response.body().contains("\"errors\":[{")) {
      throw new RuntimeException("createShare returned GraphQL errors: " + response.body());
    }
  }
}
