// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Layer 2 integration tests for carbonio-docs-connector-ce.
 *
 * <p>Uses {@code @QuarkusIntegrationTest}: the app runs out-of-process, as a packaged artifact (the
 * same {@code carbonio-docs-connector-ce-runner} the Jenkinsfile builds natively), not in Quarkus'
 * in-JVM test mode. {@code @Inject}/{@code @InjectMock} are therefore NOT available; all
 * dependencies are either real containers or WireMock, wired purely through externalized config
 * (see {@link CeStackTestResource}).
 *
 * <p>Dependency handling, per the "direct dependencies are real, indirect ones are mocked" policy:
 *
 * <ul>
 *   <li><b>carbonio-user-management</b> (direct dependency) — a real {@code
 *       registry.dev.zextras.com/dev/carbonio-user-management:devel} container. Auth scenarios
 *       (valid/invalid/empty cookie, GUEST user, inactive user) are produced by real
 *       user-management state, itself backed by a WireMock mailbox mock (mailbox is
 *       user-management's dependency, not ours — see {@link CeStackTestResource}).
 *   <li><b>carbonio-files</b> (direct dependency) — a real {@code
 *       registry.dev.zextras.com/dev/carbonio-files:devel} container with its own real postgres
 *       database. Every scenario that needs a specific node (owner, mime type, byte size,
 *       permission) drives it by ACTUALLY creating that state via files' own real HTTP contract —
 *       {@link CeStackTestResource#rawUploadToFiles} / {@link CeStackTestResource#rawCreateShare}
 *       for cases docs-connector's own {@code /files/create} (fixed blank templates only) can't
 *       reach — never by stubbing an arbitrary GraphQL response. Only carbonio-storages (files' OWN
 *       blob backend, one level further down) stays mocked; see {@link CeStackTestResource}'s class
 *       javadoc for why downloaded content is a fixed fixture rather than a genuine echo of what
 *       was uploaded.
 * </ul>
 *
 * <p><b>Coverage note:</b> the previous {@code @QuarkusTest} version of this class also asserted
 * two scenarios that a real, correctly-functioning user-management container can never produce:
 * user-management responding with a 5xx, and user-management being unreachable
 * (connection-refused). Reading {@code UserResource}/{@code UserService} in
 * carbonio-user-management confirms {@code GET /internal/users/myself} only ever answers 200 or 401
 * — any mailbox-side failure (timeout, 5xx, malformed response) is normalized internally to a plain
 * 401, by design. There is no way to make a live instance of that service return a 5xx or drop the
 * connection without literally killing/misconfiguring it for the whole test class, which would
 * break every other scenario in this suite. Those two scenarios are NOT dropped: they remain
 * covered, unchanged, as plain-Mockito unit tests in {@code CookieAuthenticationFilterTest} ({@code
 * givenUserManagement5xxTheFilterShouldReturn503} and {@code
 * givenUserManagementUnreachableTheFilterShouldReturn503}), which exercise {@link
 * com.zextras.carbonio.docs_connector.auth.CookieAuthenticationFilter}'s error-mapping logic
 * directly against a mocked {@code UserResourceApi} that throws the exact {@code ApiException}
 * shapes a broken/unreachable dependency would produce.
 */
class DocsConnectorCeIT extends AbstractDocsConnectorCeIT {

  // A node id that is never created by any test in this suite -- used to genuinely exercise
  // files' real "node not found" behavior (as opposed to stubbing an arbitrary response).
  private static final String NEVER_CREATED_NODE_ID = "58032253-ed56-4eca-9017-3ae26cc2d9f1";

  private static final String REQUESTER_ID = CeStackTestResource.TEST_USER_ID;

  // ----- /files/create -----

  @Test
  @DisplayName("POST /files/create without cookie should return 401")
  void givenNoCookieCreateFileShouldReturn401() {
    given()
        .contentType(ContentType.JSON)
        .body(
            "{\"filename\":\"test\",\"destinationFolderId\":\"LOCAL_ROOT\",\"type\":\"LIBRE_DOCUMENT\"}")
        .when()
        .post("/files/create")
        .then()
        .statusCode(401);
  }

  @Test
  @DisplayName("POST /files/create with invalid cookie should return 401")
  void givenInvalidCookieCreateFileShouldReturn401() {
    // "invalid-token" matches no mailbox-mock stub, so real user-management falls through to its
    // catch-all 401, exactly as it would for a genuinely unrecognized session.
    given()
        .contentType(ContentType.JSON)
        .cookie("ZM_AUTH_TOKEN", "invalid-token")
        .body(
            "{\"filename\":\"test\",\"destinationFolderId\":\"LOCAL_ROOT\",\"type\":\"LIBRE_DOCUMENT\"}")
        .when()
        .post("/files/create")
        .then()
        .statusCode(401);
  }

  @Test
  @DisplayName(
      "POST /files/create with valid cookie should upload template and return 200 with nodeId")
  void givenValidCookieCreateFileShouldAttemptUpload() {
    // Real end-to-end: docs-connector uploads a blank ODT template to the REAL files container.
    given()
        .contentType(ContentType.JSON)
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .body(
            "{\"filename\":\"New"
                + " Doc\",\"destinationFolderId\":\"LOCAL_ROOT\",\"type\":\"LIBRE_DOCUMENT\"}")
        .when()
        .post("/files/create")
        .then()
        .statusCode(200)
        .body("nodeId", org.hamcrest.Matchers.notNullValue());
  }

  // ----- /files/open/{nodeId} -----

  @Test
  @DisplayName("GET /files/open/{nodeId} without cookie should return 401")
  void givenNoCookieOpenFileShouldReturn401() {
    given().when().get("/files/open/" + NEVER_CREATED_NODE_ID).then().statusCode(401);
  }

  @Test
  @DisplayName("GET /files/open/{nodeId} with invalid cookie should return 401")
  void givenInvalidCookieOpenFileShouldReturn401() {
    given()
        .cookie("ZM_AUTH_TOKEN", "bad-token")
        .when()
        .get("/files/open/" + NEVER_CREATED_NODE_ID)
        .then()
        .statusCode(401);
  }

  @Test
  @DisplayName("GET /files/open/{nodeId} with valid cookie but Files returns 404 should return 404")
  void givenValidCookieButFilesReturns404OpenFileShouldReturn404() {
    // Genuine real behavior: a nodeId that was never created anywhere in this suite. Real files'
    // getNode resolver returns null for it (see NodeDataFetcher#getNodeFetcher javadoc: "if the
    // node does not exist it returns null"), which docs-connector maps to 404.
    given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when()
        .get("/files/open/" + NEVER_CREATED_NODE_ID)
        .then()
        .statusCode(404);
  }

  // ----- /wopi/{nodeId} access-token boundary cases -----
  // These never reach files at all: AccessTokenValidationFilter aborts with 401 BEFORE the
  // request reaches WopiResource/WopiService whenever access_token is absent, or is a
  // well-formed-but-unknown UUID (OpenDocumentTokenRepository#getToken returns empty). So the
  // nodeId used here does not need to be real.

  @Test
  @DisplayName("GET /wopi/{nodeId} without access_token query param should return 401")
  void givenNoAccessTokenGetWopiAttributesShouldReturn401() {
    given().when().get("/wopi/" + NEVER_CREATED_NODE_ID).then().statusCode(401);
  }

  @Test
  @DisplayName("GET /wopi/{nodeId} with expired/unknown access_token should return 401")
  void givenUnknownAccessTokenGetWopiAttributesShouldReturn401() {
    given()
        .queryParam("access_token", "00000000-0000-0000-0000-000000000000")
        .queryParam("access_token_ttl", String.valueOf(System.currentTimeMillis() + 10000))
        .when()
        .get("/wopi/" + NEVER_CREATED_NODE_ID)
        .then()
        .statusCode(401);
  }

  @Test
  @DisplayName("POST /wopi/{nodeId}/contents without access_token should return 401")
  void givenNoAccessTokenSaveBlobShouldReturn401() {
    given()
        .contentType(ContentType.BINARY)
        .body("file-content".getBytes(StandardCharsets.UTF_8))
        .when()
        .post("/wopi/" + NEVER_CREATED_NODE_ID + "/contents")
        .then()
        .statusCode(401);
  }

  // ----- Full happy-path WOPI flow -----

  /** Uploads a small real ODT file as the given user, returning its real nodeId. */
  private String uploadRealOdt(String authToken, String filename) throws Exception {
    byte[] content = ("real-content-" + filename).getBytes(StandardCharsets.UTF_8);
    return CeStackTestResource.rawUploadToFiles(
        authToken, "LOCAL_ROOT", filename, "application/vnd.oasis.opendocument.text", content);
  }

  @Test
  @DisplayName("Full WOPI flow: openFile → getDocsEditorAttributes → getBlob → saveBlob")
  void givenValidCookieFullWopiFlowShouldSucceed() throws Exception {
    String nodeId = uploadRealOdt(CeStackTestResource.AUTH_TOKEN, "wopi-flow.odt");

    // Step 1: GET /files/open/{nodeId} — should return 200 with redirect URL containing
    // access_token
    Response openResponse =
        given()
            .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
            .when()
            .get("/files/open/" + nodeId)
            .then()
            .statusCode(200)
            .extract()
            .response();

    // Extract access_token from the redirect URL in the response body
    // DocsEditorRedirect record serialises as {"fileOpenUrl":"..."}
    String responseBody = openResponse.asString();
    ObjectMapper mapper = new ObjectMapper();
    JsonNode json = mapper.readTree(responseBody);
    String redirectUrl = json.get("fileOpenUrl").asText();

    assertThat(redirectUrl).contains("access_token=");
    assertThat(redirectUrl).contains("access_token_ttl=");

    String accessToken = extractAccessToken(redirectUrl);
    assertThat(accessToken).isNotBlank();

    long futureTtl = System.currentTimeMillis() + 43_200_000L;

    // Step 2: GET /wopi/{nodeId}?access_token={token} — should return 200 with DocsEditorAttributes
    // (WopiService's userResourceApi.internalUsersIdUserIdGet(REQUESTER_ID) call is answered by
    // real user-management, backed by the mailbox mock's /internal/accounts/{id}/info stub.)
    given()
        .queryParam("access_token", accessToken)
        .queryParam("access_token_ttl", futureTtl)
        .when()
        .get("/wopi/" + nodeId)
        .then()
        .statusCode(200);

    // Step 3: GET /wopi/{nodeId}/contents?access_token={token} — should return file content
    // (from the mocked storages backend -- see CeStackTestResource class javadoc).
    given()
        .queryParam("access_token", accessToken)
        .queryParam("access_token_ttl", futureTtl)
        .when()
        .get("/wopi/" + nodeId + "/contents")
        .then()
        .statusCode(200);

    // Step 4: POST /wopi/{nodeId}/contents?access_token={token} — should return 200 (real
    // uploadFileVersion against real files, backed by the mocked storages upload stub).
    byte[] newContent = "updated-content".getBytes(StandardCharsets.UTF_8);
    given()
        .contentType(ContentType.BINARY)
        .queryParam("access_token", accessToken)
        .queryParam("access_token_ttl", futureTtl)
        .body(newContent)
        .when()
        .post("/wopi/" + nodeId + "/contents")
        .then()
        .statusCode(200);
  }

  private String extractAccessToken(String redirectUrl) {
    String accessTokenParam = "access_token=";
    int tokenStart = redirectUrl.indexOf(accessTokenParam) + accessTokenParam.length();
    int tokenEnd = redirectUrl.indexOf("&", tokenStart);
    return tokenEnd > 0
        ? redirectUrl.substring(tokenStart, tokenEnd)
        : redirectUrl.substring(tokenStart);
  }

  // ----- Auth edge cases -----

  @Test
  @DisplayName("GET /files/open/{nodeId} with empty-string cookie value should return 401")
  void givenEmptyCookieValueOpenFileShouldReturn401() {
    // An empty ZM_AUTH_TOKEN never even reaches the mailbox mock: user-management's own REST
    // layer rejects a blank token with 401 before calling mailbox (see UserResource#getMyself).
    given()
        .cookie("ZM_AUTH_TOKEN", "")
        .when()
        .get("/files/open/" + NEVER_CREATED_NODE_ID)
        .then()
        .statusCode(401);
  }

  // NOTE: "user-management returns a 5xx" and "user-management is unreachable" are intentionally
  // NOT reproduced here. See the class-level javadoc: a real, correctly-functioning
  // user-management instance can only ever answer 200 or 401 on this endpoint, by design. Both
  // scenarios remain covered as plain-Mockito unit tests in CookieAuthenticationFilterTest
  // (givenUserManagement5xxTheFilterShouldReturn503,
  // givenUserManagementUnreachableTheFilterShouldReturn503).

  // ----- AccessTokenValidationFilter edge cases (IT) -----

  @Test
  @DisplayName("GET /wopi/{nodeId} with malformed (non-UUID) access_token should return 401")
  void givenMalformedAccessTokenGetWopiAttributesShouldReturn401() {
    given()
        .queryParam("access_token", "not-a-uuid-at-all")
        .queryParam("access_token_ttl", System.currentTimeMillis() + 10000)
        .when()
        .get("/wopi/" + NEVER_CREATED_NODE_ID)
        .then()
        .statusCode(401);
  }

  @Test
  @DisplayName("GET /wopi/{nodeId} with empty-string access_token should return 401")
  void givenEmptyStringAccessTokenGetWopiAttributesShouldReturn401() {
    given()
        .queryParam("access_token", "")
        .queryParam("access_token_ttl", System.currentTimeMillis() + 10000)
        .when()
        .get("/wopi/" + NEVER_CREATED_NODE_ID)
        .then()
        .statusCode(401);
  }

  // ----- File size limit edge cases (IT) -----

  @Test
  @DisplayName("GET /files/open/{nodeId} for spreadsheet exceeding 10 MB limit should return 403")
  void givenSpreadsheetExceedingSizeLimitOpenFileShouldReturn403() throws Exception {
    // Genuinely upload an 11 MB blob with a spreadsheet mime type/extension to the REAL files
    // container; the mocked storages backend echoes back the real Content-Length as the node's
    // recorded size (see CeStackTestResource#setupStoragesStubs), so this exceeds the real
    // 10 MB spreadsheet limit for real, not via a canned stub value.
    byte[] oversized = new byte[11 * 1024 * 1024];
    String nodeId =
        CeStackTestResource.rawUploadToFiles(
            CeStackTestResource.AUTH_TOKEN,
            "LOCAL_ROOT",
            "budget.ods",
            "application/vnd.oasis.opendocument.spreadsheet",
            oversized);

    given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when()
        .get("/files/open/" + nodeId)
        .then()
        .statusCode(403);
  }

  @Test
  @DisplayName("GET /files/open/{nodeId} for presentation exceeding 100 MB limit should return 403")
  void givenPresentationExceedingSizeLimitOpenFileShouldReturn403() throws Exception {
    byte[] oversized = new byte[101 * 1024 * 1024];
    String nodeId =
        CeStackTestResource.rawUploadToFiles(
            CeStackTestResource.AUTH_TOKEN,
            "LOCAL_ROOT",
            "slides.odp",
            "application/vnd.oasis.opendocument.presentation",
            oversized);

    given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when()
        .get("/files/open/" + nodeId)
        .then()
        .statusCode(403);
  }

  // ----- 8 new IT cases -----

  @Test
  @DisplayName("GET /files/open/{nodeId} for .docx (OOXML) returns 200")
  void givenValidCookieAndDocxFile_whenOpenFile_thenReturn200() throws Exception {
    String nodeId =
        CeStackTestResource.rawUploadToFiles(
            CeStackTestResource.AUTH_TOKEN,
            "LOCAL_ROOT",
            "report.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "docx-content".getBytes(StandardCharsets.UTF_8));

    given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .when()
        .get("/files/open/" + nodeId)
        .then()
        .statusCode(200);
  }

  @Test
  @DisplayName("GET /files/open/{nodeId} with GUEST user type should return 401")
  void givenValidCookieAndExternalUser_whenOpenFile_thenReturn401() {
    // GUEST_AUTH_TOKEN is mapped, by the mailbox mock, to an isExternal=true account -- real
    // user-management reports it as type=GUEST, which CookieAuthenticationFilter rejects. The
    // request never reaches files, so the nodeId does not need to exist.
    given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.GUEST_AUTH_TOKEN)
        .when()
        .get("/files/open/" + NEVER_CREATED_NODE_ID)
        .then()
        .statusCode(401);
  }

  @Test
  @DisplayName("GET /files/open/{nodeId} with non-active user should return 401")
  void givenValidCookieAndInactiveUser_whenOpenFile_thenReturn401() {
    // INACTIVE_AUTH_TOKEN is mapped, by the mailbox mock, to a status=locked account. The request
    // never reaches files, so the nodeId does not need to exist.
    given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.INACTIVE_AUTH_TOKEN)
        .when()
        .get("/files/open/" + NEVER_CREATED_NODE_ID)
        .then()
        .statusCode(401);
  }

  @Test
  @DisplayName("GET /files/open/{nodeId} for read-only file injects permission=readonly")
  void givenValidCookieAndReadOnlyFile_whenOpenFile_thenRedirectUrlContainsPermissionReadonly()
      throws Exception {
    // Genuine real permission state: SECOND_USER owns the node and shares it READ_ONLY with the
    // main test user via files' real GraphQL createShare mutation -- files' permission model is
    // real DB state now that files is a real container, so this cannot be stubbed.
    String nodeId =
        CeStackTestResource.rawUploadToFiles(
            CeStackTestResource.SECOND_AUTH_TOKEN,
            "LOCAL_ROOT",
            "readonly-doc.odt",
            "application/vnd.oasis.opendocument.text",
            "owned-by-second-user".getBytes(StandardCharsets.UTF_8));
    CeStackTestResource.rawCreateShare(
        CeStackTestResource.SECOND_AUTH_TOKEN,
        nodeId,
        CeStackTestResource.TEST_USER_ID,
        "READ_ONLY");

    Response r =
        given()
            .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
            .when()
            .get("/files/open/" + nodeId)
            .then()
            .statusCode(200)
            .extract()
            .response();

    JsonNode json = new ObjectMapper().readTree(r.asString());
    String url = json.get("fileOpenUrl").asText();
    assertThat(url).contains("permission=readonly");
  }

  @Test
  @DisplayName("GET /files/open/{nodeId}?redirect=true returns 307")
  void givenValidCookie_whenOpenFileWithRedirectTrue_thenReturn307() throws Exception {
    String nodeId = uploadRealOdt(CeStackTestResource.AUTH_TOKEN, "redirect-flow.odt");

    given()
        .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
        .redirects()
        .follow(false)
        .queryParam("redirect", true)
        .when()
        .get("/files/open/" + nodeId)
        .then()
        .statusCode(307);
  }

  @Test
  @DisplayName("Open then GET /wopi/{nodeId} returns DocsEditorAttributes with user info")
  void givenValidCookieAndOpenedFile_whenGetWopiAttributes_thenReturn200WithCorrectFields()
      throws Exception {
    String nodeId = uploadRealOdt(CeStackTestResource.AUTH_TOKEN, "wopi-attrs.odt");

    Response openR =
        given()
            .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
            .when()
            .get("/files/open/" + nodeId)
            .then()
            .statusCode(200)
            .extract()
            .response();
    String url = new ObjectMapper().readTree(openR.asString()).get("fileOpenUrl").asText();
    String accessToken = extractAccessToken(url);

    // WopiService's internalUsersIdUserIdGet(REQUESTER_ID) call is answered by real
    // user-management, backed by the mailbox mock's /internal/accounts/{id}/info stub.
    given()
        .queryParam("access_token", accessToken)
        .queryParam("access_token_ttl", System.currentTimeMillis() + 43_200_000L)
        .when()
        .get("/wopi/" + nodeId)
        .then()
        .statusCode(200)
        .body("$", org.hamcrest.Matchers.notNullValue());
  }

  @Test
  @DisplayName("Open then GET /wopi/{nodeId}/contents returns file bytes")
  void givenValidCookieAndOpenedFile_whenGetFileContents_thenReturnFileBytes() throws Exception {
    String nodeId = uploadRealOdt(CeStackTestResource.AUTH_TOKEN, "wopi-contents.odt");

    Response openR =
        given()
            .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
            .when()
            .get("/files/open/" + nodeId)
            .then()
            .statusCode(200)
            .extract()
            .response();
    String url = new ObjectMapper().readTree(openR.asString()).get("fileOpenUrl").asText();
    String accessToken = extractAccessToken(url);

    byte[] returned =
        given()
            .queryParam("access_token", accessToken)
            .queryParam("access_token_ttl", System.currentTimeMillis() + 43_200_000L)
            .when()
            .get("/wopi/" + nodeId + "/contents")
            .then()
            .statusCode(200)
            .extract()
            .asByteArray();

    // storages is mocked (see CeStackTestResource class javadoc): the real files container never
    // actually persists what was uploaded to it, so the download genuinely returns the mock's
    // fixed fixture content, not an echo of whatever bytes were uploaded above.
    assertThat(returned)
        .isEqualTo(
            CeStackTestResource.MOCKED_STORAGE_FILE_CONTENT.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("WOPI token bound to nodeId A returns 401 when used against nodeId B")
  void givenWopiAccessToken_whenAccessedAcrossDifferentNodeId_thenReturn401() throws Exception {
    String nodeId = uploadRealOdt(CeStackTestResource.AUTH_TOKEN, "cross-node.odt");

    Response openR =
        given()
            .cookie("ZM_AUTH_TOKEN", CeStackTestResource.AUTH_TOKEN)
            .when()
            .get("/files/open/" + nodeId)
            .then()
            .statusCode(200)
            .extract()
            .response();
    String url = new ObjectMapper().readTree(openR.asString()).get("fileOpenUrl").asText();
    String accessToken = extractAccessToken(url);

    // WopiResource compares the token's bound documentId against the path nodeId BEFORE ever
    // calling files, so "otherNode" does not need to be a real, existing node for this check.
    String otherNode = "12345678-1234-1234-1234-123456789012";

    given()
        .queryParam("access_token", accessToken)
        .queryParam("access_token_ttl", System.currentTimeMillis() + 43_200_000L)
        .when()
        .get("/wopi/" + otherNode)
        .then()
        .statusCode(401);
  }
}
