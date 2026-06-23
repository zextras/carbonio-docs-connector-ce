// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zextras.carbonio.docs_connector.clients.UserManagementClient;
import com.zextras.carbonio.user_management.sdk.grpc.GetUserMyselfRequest;
import com.zextras.carbonio.user_management.sdk.grpc.UserInfoProto;
import com.zextras.carbonio.user_management.sdk.grpc.UserManagementServiceGrpc.UserManagementServiceBlockingStub;
import com.zextras.carbonio.user_management.sdk.grpc.UserMyselfProto;
import com.zextras.carbonio.user_management.sdk.grpc.UserMyselfResponse;
import com.zextras.carbonio.user_management.sdk.grpc.UserTypeProto;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Layer 2 integration tests for carbonio-docs-connector-ce.
 *
 * <p>Uses {@code @QuarkusTest} (not {@code @QuarkusIntegrationTest}) so that the full CDI
 * container is started but we can still use {@code @InjectMock} to replace the gRPC
 * {@link UserManagementClient}.
 *
 * <p>TRAP 25: In {@code @QuarkusTest}, {@code @GrpcClient} channels are forced to port 9001
 * (in-process), so we cannot reach a real or stub gRPC server. The only correct workaround
 * is to mock the entire {@code UserManagementClient} CDI bean via {@code @InjectMock}.
 * The mock returns controlled responses to simulate successful and failed authentication.
 *
 * <p>The files SDK is stubbed via WireMock (provided by {@link CeStackTestResource}).
 * Consul is a real container (also from {@link CeStackTestResource}).
 */
@QuarkusTest
@WithTestResource(CeStackTestResource.class)
class DocsConnectorCeIT {

  @InjectMock
  UserManagementClient userManagementClient;

  private UserManagementServiceBlockingStub mockStub;

  private static final String NODE_ID = "58032253-ed56-4eca-9017-3ae26cc2d9f1";
  private static final String REQUESTER_ID = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";

  @BeforeEach
  void setUp() {
    mockStub = Mockito.mock(UserManagementServiceBlockingStub.class);
    when(userManagementClient.getBlockingStub()).thenReturn(mockStub);
  }

  /**
   * Configures the mock gRPC stub to accept the given token as a valid active internal user.
   */
  private void mockValidUser(String token, String userId, String locale) {
    UserInfoProto info = UserInfoProto.newBuilder()
        .setUserId(userId)
        .setType(UserTypeProto.INTERNAL)
        .setStatus("active")
        .setDomain("example.com")
        .setFullName("Test User")
        .setEmail("test@example.com")
        .build();
    UserMyselfProto myself = UserMyselfProto.newBuilder()
        .setInfo(info)
        .setLocale(locale)
        .build();
    UserMyselfResponse response = UserMyselfResponse.newBuilder().setUser(myself).build();

    GetUserMyselfRequest request = GetUserMyselfRequest.newBuilder()
        .setToken(token)
        .setBypassCache(true)
        .build();
    when(mockStub.getUserMyself(request)).thenReturn(response);
  }

  /**
   * Configures the mock gRPC stub to reject the given token with UNAUTHENTICATED.
   */
  private void mockInvalidUser(String token) {
    GetUserMyselfRequest request = GetUserMyselfRequest.newBuilder()
        .setToken(token)
        .setBypassCache(true)
        .build();
    when(mockStub.getUserMyself(request))
        .thenThrow(new StatusRuntimeException(Status.UNAUTHENTICATED));
  }

  // ----- /services/docs/files/create -----

  @Test
  @DisplayName("POST /files/create without cookie should return 401")
  void givenNoCookieCreateFileShouldReturn401() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"filename\":\"test\",\"destinationFolderId\":\"LOCAL_ROOT\",\"type\":\"LIBRE_DOCUMENT\"}")
        .when().post("/services/docs/files/create")
        .then().statusCode(401);
  }

  @Test
  @DisplayName("POST /files/create with invalid cookie should return 401")
  void givenInvalidCookieCreateFileShouldReturn401() {
    mockInvalidUser("invalid-token");

    given()
        .contentType(ContentType.JSON)
        .cookie("ZM_AUTH_TOKEN", "invalid-token")
        .body("{\"filename\":\"test\",\"destinationFolderId\":\"LOCAL_ROOT\",\"type\":\"LIBRE_DOCUMENT\"}")
        .when().post("/services/docs/files/create")
        .then().statusCode(401);
  }

  @Test
  @DisplayName("POST /files/create with valid cookie should upload template and return 200 with nodeId")
  void givenValidCookieCreateFileShouldAttemptUpload() {
    mockValidUser(CeStackTestResource.AUTH_TOKEN, REQUESTER_ID, "en_US");

    // Stub WireMock: the Files SDK POSTs to /upload/ with multipart content
    CeStackTestResource.FILES_MOCK.stubFor(
        post(urlPathMatching("/upload/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"nodeId\":\"11111111-1111-1111-1111-111111111111\"}")));

    given()
        .contentType(ContentType.JSON)
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .body("{\"filename\":\"New Doc\",\"destinationFolderId\":\"LOCAL_ROOT\",\"type\":\"LIBRE_DOCUMENT\"}")
        .when().post("/services/docs/files/create")
        .then()
        .statusCode(200);
  }

  // ----- /services/docs/files/open/{nodeId} -----

  @Test
  @DisplayName("GET /files/open/{nodeId} without cookie should return 401")
  void givenNoCookieOpenFileShouldReturn401() {
    given()
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(401);
  }

  @Test
  @DisplayName("GET /files/open/{nodeId} with invalid cookie should return 401")
  void givenInvalidCookieOpenFileShouldReturn401() {
    mockInvalidUser("bad-token");

    given()
        .cookie("ZM_AUTH_TOKEN", "bad-token")
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(401);
  }

  @Test
  @DisplayName("GET /files/open/{nodeId} with valid cookie but Files returns 404 should return 404")
  void givenValidCookieButFilesReturns404OpenFileShouldReturn404() {
    mockValidUser(CeStackTestResource.AUTH_TOKEN, REQUESTER_ID, "en_US");

    // Stub WireMock: graphQL returns null data (node not found)
    CeStackTestResource.FILES_MOCK.stubFor(
        post(urlPathEqualTo("/graphql/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"errors\":[],\"data\":null}")));

    given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(404);
  }

  // ----- /services/docs/wopi/{nodeId} -----

  @Test
  @DisplayName("GET /wopi/{nodeId} without access_token query param should return 401")
  void givenNoAccessTokenGetWopiAttributesShouldReturn401() {
    given()
        .when().get("/services/docs/wopi/" + NODE_ID)
        .then().statusCode(401);
  }

  @Test
  @DisplayName("GET /wopi/{nodeId} with expired/unknown access_token should return 401")
  void givenUnknownAccessTokenGetWopiAttributesShouldReturn401() {
    given()
        .queryParam("access_token", "00000000-0000-0000-0000-000000000000")
        .queryParam("access_token_ttl", String.valueOf(System.currentTimeMillis() + 10000))
        .when().get("/services/docs/wopi/" + NODE_ID)
        .then().statusCode(401);
  }

  // ----- /services/docs/wopi/{nodeId}/contents -----

  @Test
  @DisplayName("POST /wopi/{nodeId}/contents without access_token should return 401")
  void givenNoAccessTokenSaveBlobShouldReturn401() {
    given()
        .contentType(ContentType.BINARY)
        .body("file-content".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .when().post("/services/docs/wopi/" + NODE_ID + "/contents")
        .then().statusCode(401);
  }

  // ----- Full happy-path WOPI flow -----

  /**
   * Stubs the Files graphQL endpoint (POST /graphql/) to return a valid node.
   * The response is used both by openFile (FilesService) and getDocsEditorAttributes/saveBlob (WopiService).
   */
  private void stubFilesGraphQL(String nodeId, String mimeType, long sizeBytes) {
    String body = """
        {
          "data": {
            "getNode": {
              "permissions": { "can_write_file": true },
              "owner": { "id": "%s", "full_name": "Owner" },
              "parent": { "id": "LOCAL_ROOT" },
              "id": "%s",
              "name": "test-doc",
              "updated_at": 1700000000000,
              "extension": "odt",
              "mime_type": "%s",
              "size": %d,
              "version": 1
            }
          }
        }
        """.formatted(REQUESTER_ID, nodeId, mimeType, sizeBytes);

    CeStackTestResource.FILES_MOCK.stubFor(
        post(urlPathEqualTo("/graphql/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
  }

  @Test
  @DisplayName("Full WOPI flow: openFile → getDocsEditorAttributes → getBlob → saveBlob")
  void givenValidCookieFullWopiFlowShouldSucceed() throws Exception {
    mockValidUser(CeStackTestResource.AUTH_TOKEN, REQUESTER_ID, "en_US");

    // 1. Stub Files graphQL for all three calls (openFile + getDocsEditorAttributes + saveBlob pre/post)
    long sizeBytes = 5L * 1024 * 1024; // 5 MB — within all limits
    stubFilesGraphQL(NODE_ID, "application/vnd.oasis.opendocument.text", sizeBytes);

    // 2. Stub download endpoint for getBlob
    byte[] fileContent = "document-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    CeStackTestResource.FILES_MOCK.stubFor(
        get(urlPathMatching("/download/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/octet-stream")
                .withHeader("Content-Length", String.valueOf(fileContent.length))
                .withBody(fileContent)));

    // 3. Stub upload-version endpoint for saveBlob
    CeStackTestResource.FILES_MOCK.stubFor(
        post(urlPathEqualTo("/upload-version/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"nodeId\":\"" + NODE_ID + "\",\"version\":2}")));

    // Step 1: GET /files/open/{nodeId} — should return 200 with redirect URL containing access_token
    Response openResponse = given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(200)
        .extract().response();

    // Extract access_token from the redirect URL in the response body
    // DocsEditorRedirect record serialises as {"fileOpenUrl":"..."}
    String responseBody = openResponse.asString();
    ObjectMapper mapper = new ObjectMapper();
    JsonNode json = mapper.readTree(responseBody);
    String redirectUrl = json.get("fileOpenUrl").asText();

    assertThat(redirectUrl).contains("access_token=");
    assertThat(redirectUrl).contains("access_token_ttl=");

    // Parse access_token from the URL query parameters
    String accessTokenParam = "access_token=";
    int tokenStart = redirectUrl.indexOf(accessTokenParam) + accessTokenParam.length();
    int tokenEnd = redirectUrl.indexOf("&", tokenStart);
    String accessToken = tokenEnd > 0
        ? redirectUrl.substring(tokenStart, tokenEnd)
        : redirectUrl.substring(tokenStart);

    assertThat(accessToken).isNotBlank();

    long futureTtl = System.currentTimeMillis() + 43_200_000L;

    // Step 2: GET /wopi/{nodeId}?access_token={token} — should return 200 with DocsEditorAttributes
    // Mock the getUserById call that WopiService makes internally
    com.zextras.carbonio.user_management.sdk.grpc.GetUserByIdRequest byIdRequest =
        com.zextras.carbonio.user_management.sdk.grpc.GetUserByIdRequest.newBuilder()
            .setUserId(REQUESTER_ID)
            .build();
    com.zextras.carbonio.user_management.sdk.grpc.UserInfoProto userInfo =
        com.zextras.carbonio.user_management.sdk.grpc.UserInfoProto.newBuilder()
            .setUserId(REQUESTER_ID)
            .setFullName("Test User")
            .setEmail("test@example.com")
            .build();
    com.zextras.carbonio.user_management.sdk.grpc.UserInfoResponse userInfoResponse =
        com.zextras.carbonio.user_management.sdk.grpc.UserInfoResponse.newBuilder()
            .setUser(userInfo)
            .build();
    when(mockStub.getUserById(byIdRequest)).thenReturn(userInfoResponse);

    given()
        .queryParam("access_token", accessToken)
        .queryParam("access_token_ttl", futureTtl)
        .when().get("/services/docs/wopi/" + NODE_ID)
        .then().statusCode(200);

    // Step 3: GET /wopi/{nodeId}/contents?access_token={token} — should return file content
    given()
        .queryParam("access_token", accessToken)
        .queryParam("access_token_ttl", futureTtl)
        .when().get("/services/docs/wopi/" + NODE_ID + "/contents")
        .then().statusCode(200);

    // Step 4: POST /wopi/{nodeId}/contents?access_token={token} — should return 200
    // Stub the second graphQL call that saveBlob makes after upload (to get updated timestamp)
    CeStackTestResource.FILES_MOCK.stubFor(
        post(urlPathEqualTo("/graphql/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "data": {
                        "getNode": {
                          "permissions": { "can_write_file": true },
                          "owner": { "id": "%s", "full_name": "Owner" },
                          "parent": { "id": "LOCAL_ROOT" },
                          "id": "%s",
                          "name": "test-doc",
                          "updated_at": 1700000001000,
                          "extension": "odt",
                          "mime_type": "application/vnd.oasis.opendocument.text",
                          "size": %d,
                          "version": 2
                        }
                      }
                    }
                    """.formatted(REQUESTER_ID, NODE_ID, fileContent.length))));

    given()
        .contentType(ContentType.BINARY)
        .queryParam("access_token", accessToken)
        .queryParam("access_token_ttl", futureTtl)
        .body(fileContent)
        .when().post("/services/docs/wopi/" + NODE_ID + "/contents")
        .then().statusCode(200);
  }

  // ----- Auth edge cases -----

  @Test
  @DisplayName("GET /files/open/{nodeId} with empty-string cookie value should return 401")
  void givenEmptyCookieValueOpenFileShouldReturn401() {
    mockInvalidUser("");

    given()
        .cookie("ZM_AUTH_TOKEN", "")
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(401);
  }

  @Test
  @DisplayName("GET /files/open/{nodeId} when gRPC throws UNAVAILABLE should return 401")
  void givenGrpcUnavailableOpenFileShouldReturn401() {
    GetUserMyselfRequest request = GetUserMyselfRequest.newBuilder()
        .setToken(CeStackTestResource.AUTH_TOKEN)
        .setBypassCache(true)
        .build();
    when(mockStub.getUserMyself(request))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(401);
  }

  // ----- AccessTokenValidationFilter edge cases (IT) -----

  @Test
  @DisplayName("GET /wopi/{nodeId} with malformed (non-UUID) access_token should return 401")
  void givenMalformedAccessTokenGetWopiAttributesShouldReturn401() {
    given()
        .queryParam("access_token", "not-a-uuid-at-all")
        .queryParam("access_token_ttl", System.currentTimeMillis() + 10000)
        .when().get("/services/docs/wopi/" + NODE_ID)
        .then().statusCode(401);
  }

  @Test
  @DisplayName("GET /wopi/{nodeId} with empty-string access_token should return 401")
  void givenEmptyStringAccessTokenGetWopiAttributesShouldReturn401() {
    given()
        .queryParam("access_token", "")
        .queryParam("access_token_ttl", System.currentTimeMillis() + 10000)
        .when().get("/services/docs/wopi/" + NODE_ID)
        .then().statusCode(401);
  }

  // ----- File size limit edge cases (IT) -----

  @Test
  @DisplayName("GET /files/open/{nodeId} for spreadsheet exceeding 10 MB limit should return 403")
  void givenSpreadsheetExceedingSizeLimitOpenFileShouldReturn403() {
    mockValidUser(CeStackTestResource.AUTH_TOKEN, REQUESTER_ID, "en_US");

    long oversizedBytes = 11L * 1024 * 1024; // 11 MB — exceeds 10 MB spreadsheet limit
    String graphQLBody = """
        {
          "data": {
            "getNode": {
              "permissions": { "can_write_file": true },
              "owner": { "id": "%s", "full_name": "Owner" },
              "parent": { "id": "LOCAL_ROOT" },
              "id": "%s",
              "name": "budget",
              "updated_at": 1700000000000,
              "extension": "ods",
              "mime_type": "application/vnd.oasis.opendocument.spreadsheet",
              "size": %d,
              "version": 1
            }
          }
        }
        """.formatted(REQUESTER_ID, NODE_ID, oversizedBytes);

    CeStackTestResource.FILES_MOCK.stubFor(
        post(urlPathEqualTo("/graphql/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(graphQLBody)));

    given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(403);
  }

  @Test
  @DisplayName("GET /files/open/{nodeId} for presentation exceeding 100 MB limit should return 403")
  void givenPresentationExceedingSizeLimitOpenFileShouldReturn403() {
    mockValidUser(CeStackTestResource.AUTH_TOKEN, REQUESTER_ID, "en_US");

    long oversizedBytes = 101L * 1024 * 1024; // 101 MB — exceeds 100 MB presentation limit
    String graphQLBody = """
        {
          "data": {
            "getNode": {
              "permissions": { "can_write_file": true },
              "owner": { "id": "%s", "full_name": "Owner" },
              "parent": { "id": "LOCAL_ROOT" },
              "id": "%s",
              "name": "slides",
              "updated_at": 1700000000000,
              "extension": "odp",
              "mime_type": "application/vnd.oasis.opendocument.presentation",
              "size": %d,
              "version": 1
            }
          }
        }
        """.formatted(REQUESTER_ID, NODE_ID, oversizedBytes);

    CeStackTestResource.FILES_MOCK.stubFor(
        post(urlPathEqualTo("/graphql/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(graphQLBody)));

    given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(403);
  }

  // ----- 8 new IT cases -----

  @Test
  @DisplayName("GET /files/open/{nodeId} for .docx (OOXML) returns 200")
  void givenValidCookieAndDocxFile_whenOpenFile_thenReturn200() {
    mockValidUser(CeStackTestResource.AUTH_TOKEN, REQUESTER_ID, "en_US");
    long sizeBytes = 2L * 1024 * 1024;
    String body = """
        {
          "data": {
            "getNode": {
              "permissions": { "can_write_file": true },
              "owner": { "id": "%s", "full_name": "Owner" },
              "parent": { "id": "LOCAL_ROOT" },
              "id": "%s",
              "name": "report",
              "updated_at": 1700000000000,
              "extension": "docx",
              "mime_type": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
              "size": %d,
              "version": 1
            }
          }
        }
        """.formatted(REQUESTER_ID, NODE_ID, sizeBytes);
    CeStackTestResource.FILES_MOCK.stubFor(
        post(urlPathEqualTo("/graphql/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));

    given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(200);
  }

  @Test
  @DisplayName("GET /files/open/{nodeId} with GUEST user type should return 401")
  void givenValidCookieAndExternalUser_whenOpenFile_thenReturn401() {
    UserInfoProto info = UserInfoProto.newBuilder()
        .setUserId(REQUESTER_ID)
        .setType(UserTypeProto.GUEST)
        .setStatus("active")
        .setDomain("example.com")
        .setFullName("Ext User")
        .setEmail("ext@example.com")
        .build();
    UserMyselfProto myself = UserMyselfProto.newBuilder().setInfo(info).setLocale("en_US").build();
    UserMyselfResponse response = UserMyselfResponse.newBuilder().setUser(myself).build();
    GetUserMyselfRequest request = GetUserMyselfRequest.newBuilder()
        .setToken(CeStackTestResource.AUTH_TOKEN)
        .setBypassCache(true)
        .build();
    when(mockStub.getUserMyself(request)).thenReturn(response);

    given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(401);
  }

  @Test
  @DisplayName("GET /files/open/{nodeId} with non-active user should return 401")
  void givenValidCookieAndInactiveUser_whenOpenFile_thenReturn401() {
    UserInfoProto info = UserInfoProto.newBuilder()
        .setUserId(REQUESTER_ID)
        .setType(UserTypeProto.INTERNAL)
        .setStatus("locked")
        .setDomain("example.com")
        .setFullName("Locked User")
        .setEmail("locked@example.com")
        .build();
    UserMyselfProto myself = UserMyselfProto.newBuilder().setInfo(info).setLocale("en_US").build();
    UserMyselfResponse response = UserMyselfResponse.newBuilder().setUser(myself).build();
    GetUserMyselfRequest request = GetUserMyselfRequest.newBuilder()
        .setToken(CeStackTestResource.AUTH_TOKEN)
        .setBypassCache(true)
        .build();
    when(mockStub.getUserMyself(request)).thenReturn(response);

    given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(401);
  }

  @Test
  @DisplayName("GET /files/open/{nodeId} for read-only file injects permission=readonly")
  void givenValidCookieAndReadOnlyFile_whenOpenFile_thenRedirectUrlContainsPermissionReadonly()
      throws Exception {
    mockValidUser(CeStackTestResource.AUTH_TOKEN, REQUESTER_ID, "en_US");
    long sizeBytes = 1L * 1024 * 1024;
    String body = """
        {
          "data": {
            "getNode": {
              "permissions": { "can_write_file": false },
              "owner": { "id": "%s", "full_name": "Owner" },
              "parent": { "id": "LOCAL_ROOT" },
              "id": "%s",
              "name": "readonly-doc",
              "updated_at": 1700000000000,
              "extension": "odt",
              "mime_type": "application/vnd.oasis.opendocument.text",
              "size": %d,
              "version": 1
            }
          }
        }
        """.formatted(REQUESTER_ID, NODE_ID, sizeBytes);
    CeStackTestResource.FILES_MOCK.stubFor(
        post(urlPathEqualTo("/graphql/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));

    Response r = given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(200)
        .extract().response();

    JsonNode json = new ObjectMapper().readTree(r.asString());
    String url = json.get("fileOpenUrl").asText();
    assertThat(url).contains("permission=readonly");
  }

  @Test
  @DisplayName("GET /files/open/{nodeId}?redirect=true returns 307")
  void givenValidCookie_whenOpenFileWithRedirectTrue_thenReturn307() {
    mockValidUser(CeStackTestResource.AUTH_TOKEN, REQUESTER_ID, "en_US");
    stubFilesGraphQL(NODE_ID, "application/vnd.oasis.opendocument.text", 1024L);

    given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .redirects().follow(false)
        .queryParam("redirect", true)
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(307);
  }

  @Test
  @DisplayName("Open then GET /wopi/{nodeId} returns DocsEditorAttributes with user info")
  void givenValidCookieAndOpenedFile_whenGetWopiAttributes_thenReturn200WithCorrectFields()
      throws Exception {
    mockValidUser(CeStackTestResource.AUTH_TOKEN, REQUESTER_ID, "en_US");
    stubFilesGraphQL(NODE_ID, "application/vnd.oasis.opendocument.text", 1024L);

    Response openR = given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(200)
        .extract().response();
    String url = new ObjectMapper().readTree(openR.asString()).get("fileOpenUrl").asText();
    String accessToken = url.substring(url.indexOf("access_token=") + "access_token=".length());
    if (accessToken.contains("&")) accessToken = accessToken.substring(0, accessToken.indexOf("&"));

    com.zextras.carbonio.user_management.sdk.grpc.GetUserByIdRequest byIdRequest =
        com.zextras.carbonio.user_management.sdk.grpc.GetUserByIdRequest.newBuilder()
            .setUserId(REQUESTER_ID).build();
    com.zextras.carbonio.user_management.sdk.grpc.UserInfoProto info =
        com.zextras.carbonio.user_management.sdk.grpc.UserInfoProto.newBuilder()
            .setUserId(REQUESTER_ID)
            .setFullName("Test User")
            .setEmail("test@example.com")
            .build();
    com.zextras.carbonio.user_management.sdk.grpc.UserInfoResponse infoResp =
        com.zextras.carbonio.user_management.sdk.grpc.UserInfoResponse.newBuilder()
            .setUser(info).build();
    when(mockStub.getUserById(byIdRequest)).thenReturn(infoResp);

    given()
        .queryParam("access_token", accessToken)
        .queryParam("access_token_ttl", System.currentTimeMillis() + 43_200_000L)
        .when().get("/services/docs/wopi/" + NODE_ID)
        .then().statusCode(200)
        .body("$", org.hamcrest.Matchers.notNullValue());
  }

  @Test
  @DisplayName("Open then GET /wopi/{nodeId}/contents returns file bytes")
  void givenValidCookieAndOpenedFile_whenGetFileContents_thenReturnFileBytes() throws Exception {
    mockValidUser(CeStackTestResource.AUTH_TOKEN, REQUESTER_ID, "en_US");
    stubFilesGraphQL(NODE_ID, "application/vnd.oasis.opendocument.text", 1024L);

    byte[] fileContent = "hello-docs-connector".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    CeStackTestResource.FILES_MOCK.stubFor(
        get(urlPathMatching("/download/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/octet-stream")
                .withHeader("Content-Length", String.valueOf(fileContent.length))
                .withBody(fileContent)));

    Response openR = given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(200).extract().response();
    String url = new ObjectMapper().readTree(openR.asString()).get("fileOpenUrl").asText();
    String accessToken = url.substring(url.indexOf("access_token=") + "access_token=".length());
    if (accessToken.contains("&")) accessToken = accessToken.substring(0, accessToken.indexOf("&"));

    byte[] returned = given()
        .queryParam("access_token", accessToken)
        .queryParam("access_token_ttl", System.currentTimeMillis() + 43_200_000L)
        .when().get("/services/docs/wopi/" + NODE_ID + "/contents")
        .then().statusCode(200)
        .extract().asByteArray();

    assertThat(returned).isEqualTo(fileContent);
  }

  @Test
  @DisplayName("WOPI token bound to nodeId A returns 401 when used against nodeId B")
  void givenWopiAccessToken_whenAccessedAcrossDifferentNodeId_thenReturn401() throws Exception {
    mockValidUser(CeStackTestResource.AUTH_TOKEN, REQUESTER_ID, "en_US");
    stubFilesGraphQL(NODE_ID, "application/vnd.oasis.opendocument.text", 1024L);

    Response openR = given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when().get("/services/docs/files/open/" + NODE_ID)
        .then().statusCode(200).extract().response();
    String url = new ObjectMapper().readTree(openR.asString()).get("fileOpenUrl").asText();
    String accessToken = url.substring(url.indexOf("access_token=") + "access_token=".length());
    if (accessToken.contains("&")) accessToken = accessToken.substring(0, accessToken.indexOf("&"));

    String otherNode = "12345678-1234-1234-1234-123456789012";

    given()
        .queryParam("access_token", accessToken)
        .queryParam("access_token_ttl", System.currentTimeMillis() + 43_200_000L)
        .when().get("/services/docs/wopi/" + otherNode)
        .then().statusCode(401);
  }
}
