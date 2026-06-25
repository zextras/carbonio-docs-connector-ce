// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.docs_connector.cluster.DocsEditorInstanceSelector;
import com.zextras.carbonio.docs_connector.config.DocsConnectorServiceConfig;
import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import com.zextras.carbonio.docs_connector.dal.repositories.interfaces.OpenDocumentTokenRepository;
import com.zextras.carbonio.docs_connector.exceptions.AccountOverQuotaException;
import com.zextras.carbonio.docs_connector.exceptions.FileSizeTooLargeException;
import com.zextras.carbonio.docs_connector.exceptions.ServiceDependencyException;
import com.zextras.carbonio.docs_connector.types.CreatedFile;
import com.zextras.carbonio.docs_connector.types.FileType;
import com.zextras.carbonio.docs_connector.types.InsertFile;
import com.zextras.carbonio.files.FilesClient;
import com.zextras.carbonio.files.entities.NodeId;
import com.zextras.carbonio.files.exceptions.AccountInOverQuota;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ApplicationConfigService;
import com.zextras.carbonio.quarkus.extensions.bootstrap.NetworkingConfigService;
import io.vavr.control.Try;
import java.io.InputStream;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FilesService}. All dependencies are mocked — no CDI container.
 */
class FilesServiceTest {

  private OpenDocumentTokenRepository tokenRepository;
  private ApplicationConfigService applicationConfig;
  private NetworkingConfigService networkingConfig;
  private FilesClient filesClient;
  private QuotaChecker quotaChecker;
  private DocsEditorInstanceSelector instanceSelector;
  private FilesService filesService;

  private static final String NODE_ID = "58032253-ed56-4eca-9017-3ae26cc2d9f1";
  private static final String REQUESTER_ID = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";
  private static final String COOKIE = "ZM_AUTH_TOKEN=test-token";

  /** Minimal graphQL JSON returned by files when node is found. */
  private String buildGetNodeResponse(
      String nodeId, String ownerId, String name, String ext,
      String mimeType, long size, boolean canWriteFile) {
    return """
        {
          "data": {
            "getNode": {
              "permissions": { "can_write_file": %b },
              "owner": { "id": "%s", "full_name": "Owner" },
              "parent": { "id": "LOCAL_ROOT" },
              "id": "%s",
              "name": "%s",
              "updated_at": 1000,
              "extension": "%s",
              "mime_type": "%s",
              "size": %d,
              "version": 1
            }
          }
        }
        """.formatted(canWriteFile, ownerId, nodeId, name, ext, mimeType, size);
  }

  @BeforeEach
  void setUp() {
    tokenRepository = mock(OpenDocumentTokenRepository.class);
    applicationConfig = mock(ApplicationConfigService.class);
    networkingConfig = mock(NetworkingConfigService.class);
    filesClient = mock(FilesClient.class);
    quotaChecker = mock(QuotaChecker.class);
    instanceSelector = mock(DocsEditorInstanceSelector.class);

    // Defaults: use defaults for wopi host/port, use default max sizes
    when(applicationConfig.get(DocsConnectorServiceConfig.ApplicationConfig.MAX_FILE_SIZE_MB_DOCUMENT))
        .thenReturn(Optional.of("50"));
    when(applicationConfig.get(DocsConnectorServiceConfig.ApplicationConfig.MAX_FILE_SIZE_MB_PRESENTATION))
        .thenReturn(Optional.of("100"));
    when(applicationConfig.get(DocsConnectorServiceConfig.ApplicationConfig.MAX_FILE_SIZE_MB_SPREADSHEET))
        .thenReturn(Optional.of("10"));
    when(networkingConfig.get(DocsConnectorServiceConfig.NetworkingConfig.WOPI_HOST))
        .thenReturn(Optional.of("127.78.0.12"));
    when(networkingConfig.get(DocsConnectorServiceConfig.NetworkingConfig.WOPI_PORT))
        .thenReturn(Optional.of("20000"));
    when(quotaChecker.isOverQuota(anyString(), anyString())).thenReturn(false);
    when(instanceSelector.selectInstance(any())).thenReturn(Optional.empty());

    filesService = new FilesService(
        tokenRepository, applicationConfig, networkingConfig, filesClient,
        quotaChecker, instanceSelector);
  }

  @Test
  @DisplayName("openFile should return a URL containing docs editor path when node is valid and within size limit")
  void givenAValidNodeWithinSizeLimitOpenFileShouldReturnDocsEditorUrl()
      throws ServiceDependencyException, FileSizeTooLargeException {
    // Given
    long fileSizeBytes = 10 * 1024 * 1024L; // 10 MB, under 50 MB limit
    String graphQLResponse = buildGetNodeResponse(
        NODE_ID, REQUESTER_ID, "test-doc", "odt",
        "application/vnd.oasis.opendocument.text", fileSizeBytes, true);

    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponse));

    OpenDocumentToken token = new OpenDocumentToken(
        UUID.randomUUID(), UUID.fromString(NODE_ID), REQUESTER_ID, COOKIE,
        Instant.now().plusSeconds(43200));
    when(tokenRepository.createToken(UUID.fromString(NODE_ID), REQUESTER_ID, COOKIE))
        .thenReturn(token);

    // When
    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID, Optional.empty(), Optional.empty());

    // Then
    Assertions.assertThat(url).contains("services/docs/editor/browser/dist/cool.html");
    Assertions.assertThat(url).contains("access_token=");
    Assertions.assertThat(url).contains("WOPISrc");
    Assertions.assertThat(url).contains(NODE_ID);
  }

  @Test
  @DisplayName("openFile should throw FileSizeTooLargeException when node size exceeds document limit")
  void givenANodeExceedingDocumentSizeLimitOpenFileShouldThrowFileSizeTooLargeException() {
    // Given — 51 MB, exceeds 50 MB limit
    long oversizedBytes = 51L * 1024 * 1024;
    String graphQLResponse = buildGetNodeResponse(
        NODE_ID, REQUESTER_ID, "test-doc", "odt",
        "application/vnd.oasis.opendocument.text", oversizedBytes, true);

    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponse));

    // When / Then
    Assertions.assertThatThrownBy(() ->
            filesService.openFile(REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID,
                Optional.empty(), Optional.empty()))
        .isInstanceOf(FileSizeTooLargeException.class);
  }

  @Test
  @DisplayName("openFile should throw ServiceDependencyException when files client fails")
  void givenFilesClientFailureOpenFileShouldThrowServiceDependencyException() {
    // Given
    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.failure(new RuntimeException("Network error")));

    // When / Then
    Assertions.assertThatThrownBy(() ->
            filesService.openFile(REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID,
                Optional.empty(), Optional.empty()))
        .isInstanceOf(ServiceDependencyException.class);
  }

  @Test
  @DisplayName("openFile with redirect=true should include public_url with redirect=true parameter")
  void givenOpenFileRequestTheUrlShouldIncludePublicUrlWithRedirectParam()
      throws ServiceDependencyException, FileSizeTooLargeException {
    // Given
    long fileSizeBytes = 5 * 1024 * 1024L;
    String graphQLResponse = buildGetNodeResponse(
        NODE_ID, REQUESTER_ID, "doc", "odt",
        "application/vnd.oasis.opendocument.text", fileSizeBytes, true);

    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponse));

    OpenDocumentToken token = new OpenDocumentToken(
        UUID.randomUUID(), UUID.fromString(NODE_ID), REQUESTER_ID, COOKIE,
        Instant.now().plusSeconds(43200));
    when(tokenRepository.createToken(any(), anyString(), anyString())).thenReturn(token);

    // When
    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID, Optional.empty(), Optional.empty());

    // Then
    Assertions.assertThat(url).contains("public_url=");
    Assertions.assertThat(url).contains("redirect%3Dtrue");
  }

  @Test
  @DisplayName("openFile with version parameter should add permission=readonly to URL")
  void givenAVersionParameterOpenFileShouldAddReadonlyPermission()
      throws ServiceDependencyException, FileSizeTooLargeException {
    // Given
    long fileSizeBytes = 5 * 1024 * 1024L;
    String graphQLResponse = buildGetNodeResponse(
        NODE_ID, REQUESTER_ID, "doc", "odt",
        "application/vnd.oasis.opendocument.text", fileSizeBytes, true);

    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponse));

    OpenDocumentToken token = new OpenDocumentToken(
        UUID.randomUUID(), UUID.fromString(NODE_ID), REQUESTER_ID, COOKIE,
        Instant.now().plusSeconds(43200));
    when(tokenRepository.createToken(any(), anyString(), anyString())).thenReturn(token);

    // When
    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID, Optional.of(2), Optional.empty());

    // Then — when a specific version is requested, the file is opened read-only
    Assertions.assertThat(url).contains("permission=readonly");
  }

  @Test
  @DisplayName("openFile with can_write_file=false should add permission=readonly to URL")
  void givenANodeWithoutWritePermissionOpenFileShouldAddReadonlyPermission()
      throws ServiceDependencyException, FileSizeTooLargeException {
    // Given
    long fileSizeBytes = 5 * 1024 * 1024L;
    String graphQLResponse = buildGetNodeResponse(
        NODE_ID, REQUESTER_ID, "doc", "odt",
        "application/vnd.oasis.opendocument.text", fileSizeBytes, false); // no write permission

    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponse));

    OpenDocumentToken token = new OpenDocumentToken(
        UUID.randomUUID(), UUID.fromString(NODE_ID), REQUESTER_ID, COOKIE,
        Instant.now().plusSeconds(43200));
    when(tokenRepository.createToken(any(), anyString(), anyString())).thenReturn(token);

    // When
    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID, Optional.empty(), Optional.empty());

    // Then
    Assertions.assertThat(url).contains("permission=readonly");
  }

  @Test
  @DisplayName("uploadTemplate should return empty Optional when FilesClient upload fails")
  void givenFilesClientUploadFailureUploadTemplateShouldReturnEmpty()
      throws AccountOverQuotaException {
    // Given
    InsertFile insertFile = new InsertFile();
    insertFile.setType(FileType.LIBRE_DOCUMENT);
    insertFile.setFilename("New Doc");
    insertFile.setDestinationFolderId("LOCAL_ROOT");

    // FilesClient.uploadFile returns failure
    when(filesClient.uploadFile(anyString(), anyString(), anyString(), anyString(), any(InputStream.class), anyLong()))
        .thenReturn(Try.failure(new RuntimeException("upload failed")));

    // When
    Optional<CreatedFile> result = filesService.uploadTemplate(COOKIE, insertFile);

    // Then — template exists for LIBRE_DOCUMENT, upload is attempted, failure → getOrNull → null → empty
    Assertions.assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("uploadTemplate should return a CreatedFile with the node id when FilesClient upload succeeds")
  void givenSuccessfulUploadUploadTemplateShouldReturnCreatedFile()
      throws AccountOverQuotaException {
    // Given
    InsertFile insertFile = new InsertFile();
    insertFile.setType(FileType.LIBRE_SPREADSHEET);
    insertFile.setFilename("New Spreadsheet");
    insertFile.setDestinationFolderId("LOCAL_ROOT");

    String expectedNodeId = "11111111-1111-1111-1111-111111111111";
    NodeId nodeId = new NodeId(expectedNodeId);

    when(filesClient.uploadFile(anyString(), anyString(), anyString(), anyString(), any(InputStream.class), anyLong()))
        .thenReturn(Try.success(nodeId));

    // When
    Optional<CreatedFile> result = filesService.uploadTemplate(COOKIE, insertFile);

    // Then
    Assertions.assertThat(result).isPresent();
    Assertions.assertThat(result.get().getNodeId().toString()).isEqualTo(expectedNodeId);
  }

  @Test
  @DisplayName("openFile with spreadsheet MIME type should apply the 10 MB limit")
  void givenSpreadsheetMimeTypeOpenFileShouldApply10MbLimit() {
    // Given — exactly at the limit: 10 MB is not strictly greater than 10 MB
    long exactLimitBytes = 10L * 1024 * 1024;
    String graphQLResponse = buildGetNodeResponse(
        NODE_ID, REQUESTER_ID, "budget", "ods",
        "application/vnd.oasis.opendocument.spreadsheet", exactLimitBytes, true);

    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponse));

    OpenDocumentToken token = new OpenDocumentToken(
        UUID.randomUUID(), UUID.fromString(NODE_ID), REQUESTER_ID, COOKIE,
        Instant.now().plusSeconds(43200));
    when(tokenRepository.createToken(any(), anyString(), anyString())).thenReturn(token);

    // When — should NOT throw because size == limit (not strictly greater)
    Assertions.assertThatCode(() ->
            filesService.openFile(REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID,
                Optional.empty(), Optional.empty()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("openFile with spreadsheet MIME type exceeding 10 MB should throw FileSizeTooLargeException")
  void givenSpreadsheetExceeding10MbOpenFileShouldThrowFileSizeTooLargeException() {
    // Given — 1 byte over the limit
    long oversizedBytes = 10L * 1024 * 1024 + 1;
    String graphQLResponse = buildGetNodeResponse(
        NODE_ID, REQUESTER_ID, "budget", "ods",
        "application/vnd.oasis.opendocument.spreadsheet", oversizedBytes, true);

    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponse));

    // When / Then
    Assertions.assertThatThrownBy(() ->
            filesService.openFile(REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID,
                Optional.empty(), Optional.empty()))
        .isInstanceOf(com.zextras.carbonio.docs_connector.exceptions.FileSizeTooLargeException.class)
        .satisfies(e -> Assertions.assertThat(
            ((com.zextras.carbonio.docs_connector.exceptions.FileSizeTooLargeException) e).getMaxSizeLimitInMB()
        ).isEqualTo(10L));
  }

  @Test
  @DisplayName("openFile with presentation MIME type exceeding 100 MB should throw FileSizeTooLargeException")
  void givenPresentationExceeding100MbOpenFileShouldThrowFileSizeTooLargeException() {
    // Given — 101 MB
    long oversizedBytes = 101L * 1024 * 1024;
    String graphQLResponse = buildGetNodeResponse(
        NODE_ID, REQUESTER_ID, "slides", "odp",
        "application/vnd.oasis.opendocument.presentation", oversizedBytes, true);

    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponse));

    // When / Then
    Assertions.assertThatThrownBy(() ->
            filesService.openFile(REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID,
                Optional.empty(), Optional.empty()))
        .isInstanceOf(com.zextras.carbonio.docs_connector.exceptions.FileSizeTooLargeException.class)
        .satisfies(e -> Assertions.assertThat(
            ((com.zextras.carbonio.docs_connector.exceptions.FileSizeTooLargeException) e).getMaxSizeLimitInMB()
        ).isEqualTo(100L));
  }

  @Test
  @DisplayName("openFile when applicationConfig returns empty for max-file-size key should throw (no hardcoded fallback)")
  void givenEmptyMaxFileSizeConfigOpenFileShouldThrow() {
    // Given — override config to return empty (simulate broken extension)
    when(applicationConfig.get(DocsConnectorServiceConfig.ApplicationConfig.MAX_FILE_SIZE_MB_DOCUMENT))
        .thenReturn(Optional.empty());

    long fileSizeBytes = 40L * 1024 * 1024;
    String graphQLResponse = buildGetNodeResponse(
        NODE_ID, REQUESTER_ID, "doc", "odt",
        "application/vnd.oasis.opendocument.text", fileSizeBytes, true);

    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponse));

    // When/Then — should fail loudly instead of silently using a hardcoded default
    Assertions.assertThatThrownBy(() ->
        filesService.openFile(
            REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID, Optional.empty(), Optional.empty()))
        .isInstanceOf(java.util.NoSuchElementException.class);
  }

  @Test
  @DisplayName("openFile with both version and offsetFromUtc should include version in wopi endpoint query string")
  void givenVersionAndOffsetFromUtcOpenFileShouldIncludeVersionInWopiSrc()
      throws ServiceDependencyException, FileSizeTooLargeException {
    // Given
    long fileSizeBytes = 5L * 1024 * 1024;
    String graphQLResponse = buildGetNodeResponse(
        NODE_ID, REQUESTER_ID, "doc", "odt",
        "application/vnd.oasis.opendocument.text", fileSizeBytes, true);

    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponse));

    OpenDocumentToken token = new OpenDocumentToken(
        UUID.randomUUID(), UUID.fromString(NODE_ID), REQUESTER_ID, COOKIE,
        Instant.now().plusSeconds(43200));
    when(tokenRepository.createToken(any(), anyString(), anyString())).thenReturn(token);

    // When
    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID,
        Optional.of(3), Optional.of(60));

    // Then — version param in WOPISrc and also in public_url
    Assertions.assertThat(url).contains("version%3D3");  // URL-encoded version=3 in WOPISrc
    Assertions.assertThat(url).contains("permission=readonly"); // version requested → read-only
  }

  @Test
  @DisplayName("uploadTemplate for LIBRE_PRESENTATION type should return a CreatedFile on success")
  void givenLibrePresentationTypeUploadTemplateShouldReturnCreatedFile()
      throws AccountOverQuotaException {
    // Given
    InsertFile insertFile = new InsertFile();
    insertFile.setType(FileType.LIBRE_PRESENTATION);
    insertFile.setFilename("New Presentation");
    insertFile.setDestinationFolderId("LOCAL_ROOT");

    String expectedNodeId = "22222222-2222-2222-2222-222222222222";
    NodeId nodeId = new NodeId(expectedNodeId);

    when(filesClient.uploadFile(anyString(), anyString(), anyString(), anyString(), any(InputStream.class), anyLong()))
        .thenReturn(Try.success(nodeId));

    // When
    Optional<CreatedFile> result = filesService.uploadTemplate(COOKIE, insertFile);

    // Then
    Assertions.assertThat(result).isPresent();
    Assertions.assertThat(result.get().getNodeId().toString()).isEqualTo(expectedNodeId);
  }

  // ----- Over-quota behavior tests (task 5 — TDD additions) -----

  @Test
  @DisplayName("uploadTemplate should throw AccountOverQuotaException when FilesClient throws AccountInOverQuota")
  void givenAccountInOverQuotaUploadTemplateShouldThrowAccountOverQuotaException() {
    // Given
    InsertFile insertFile = new InsertFile();
    insertFile.setType(FileType.LIBRE_DOCUMENT);
    insertFile.setFilename("New Doc");
    insertFile.setDestinationFolderId("LOCAL_ROOT");

    when(filesClient.uploadFile(anyString(), anyString(), anyString(), anyString(),
        any(InputStream.class), anyLong()))
        .thenReturn(Try.failure(new AccountInOverQuota("account is over quota")));

    // When / Then
    Assertions.assertThatThrownBy(() -> filesService.uploadTemplate(COOKIE, insertFile))
        .isInstanceOf(AccountOverQuotaException.class);
  }

  @Test
  @DisplayName("openFile when quotaChecker reports over-quota should add permission=readonly to URL")
  void givenOverQuotaAccountOpenFileShouldAddReadonlyPermission()
      throws ServiceDependencyException, FileSizeTooLargeException {
    // Given
    long fileSizeBytes = 5L * 1024 * 1024;
    String graphQLResponse = buildGetNodeResponse(
        NODE_ID, REQUESTER_ID, "doc", "odt",
        "application/vnd.oasis.opendocument.text", fileSizeBytes, true);

    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponse));

    OpenDocumentToken token = new OpenDocumentToken(
        UUID.randomUUID(), UUID.fromString(NODE_ID), REQUESTER_ID, COOKIE,
        Instant.now().plusSeconds(43200));
    when(tokenRepository.createToken(any(), anyString(), anyString())).thenReturn(token);

    // Override default: account is over quota
    when(quotaChecker.isOverQuota(anyString(), anyString())).thenReturn(true);

    // When
    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID, Optional.empty(), Optional.empty());

    // Then — over-quota forces read-only even if node allows writes
    Assertions.assertThat(url).contains("permission=readonly");
  }

  // ----- WOPISrc exact query-string tests -----

  /** Decodes the WOPISrc parameter value from the cool.html URL. */
  private static String extractDecodedWopiSrc(String url) {
    String prefix = "WOPISrc=";
    int start = url.indexOf(prefix);
    if (start < 0) {
      throw new AssertionError("WOPISrc not found in URL: " + url);
    }
    int valueStart = start + prefix.length();
    int end = url.indexOf("&", valueStart);
    String encoded = end > 0 ? url.substring(valueStart, end) : url.substring(valueStart);
    return java.net.URLDecoder.decode(encoded, java.nio.charset.StandardCharsets.UTF_8);
  }

  private OpenDocumentToken stubValidNode(long fileSizeBytes) {
    String graphQLResponse = buildGetNodeResponse(
        NODE_ID, REQUESTER_ID, "doc", "odt",
        "application/vnd.oasis.opendocument.text", fileSizeBytes, true);
    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponse));
    OpenDocumentToken token = new OpenDocumentToken(
        UUID.randomUUID(), UUID.fromString(NODE_ID), REQUESTER_ID, COOKIE,
        Instant.now().plusSeconds(43200));
    when(tokenRepository.createToken(any(), anyString(), anyString())).thenReturn(token);
    return token;
  }

  private static final UUID INSTANCE_ID =
      UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
  private static final String WOPI_BASE =
      "http://127.78.0.12:20000/wopi/" + NODE_ID;

  @Test
  @DisplayName("WOPISrc: Advanced, no version, no offset → ends with ?service_id=<id>, no trailing &")
  void wopiSrc_advancedNoVersionNoOffset_exactServiceId()
      throws ServiceDependencyException, FileSizeTooLargeException {
    when(instanceSelector.selectInstance(any())).thenReturn(Optional.of(INSTANCE_ID));
    stubValidNode(5L * 1024 * 1024);

    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID,
        Optional.empty(), Optional.empty());

    String wopiSrc = extractDecodedWopiSrc(url);
    Assertions.assertThat(wopiSrc)
        .isEqualTo(WOPI_BASE + "?service_id=" + INSTANCE_ID);
  }

  @Test
  @DisplayName("WOPISrc: Advanced, version present, no offset → ?version=V&service_id=<id>, no trailing & or &&")
  void wopiSrc_advancedVersionNoOffset_exactVersionAndServiceId()
      throws ServiceDependencyException, FileSizeTooLargeException {
    when(instanceSelector.selectInstance(any())).thenReturn(Optional.of(INSTANCE_ID));
    stubValidNode(5L * 1024 * 1024);

    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID,
        Optional.of(3), Optional.empty());

    String wopiSrc = extractDecodedWopiSrc(url);
    Assertions.assertThat(wopiSrc)
        .isEqualTo(WOPI_BASE + "?version=3&service_id=" + INSTANCE_ID);
  }

  @Test
  @DisplayName("WOPISrc: CE (no instance), version present → ?version=V, no trailing &")
  void wopiSrc_ceVersionNoInstance_exactVersion()
      throws ServiceDependencyException, FileSizeTooLargeException {
    when(instanceSelector.selectInstance(any())).thenReturn(Optional.empty());
    stubValidNode(5L * 1024 * 1024);

    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID,
        Optional.of(2), Optional.empty());

    String wopiSrc = extractDecodedWopiSrc(url);
    Assertions.assertThat(wopiSrc)
        .isEqualTo(WOPI_BASE + "?version=2");
  }

  @Test
  @DisplayName("WOPISrc: CE (no instance), no version, no offset → bare path, no query string")
  void wopiSrc_ceNoVersionNoInstance_noQueryString()
      throws ServiceDependencyException, FileSizeTooLargeException {
    when(instanceSelector.selectInstance(any())).thenReturn(Optional.empty());
    stubValidNode(5L * 1024 * 1024);

    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID,
        Optional.empty(), Optional.empty());

    String wopiSrc = extractDecodedWopiSrc(url);
    Assertions.assertThat(wopiSrc).isEqualTo(WOPI_BASE);
  }

  @Test
  @DisplayName("WOPISrc: Advanced, version + offset → ?version=V&service_id=<id>&offset_from_utc=O")
  void wopiSrc_advancedVersionAndOffset_exactAllParams()
      throws ServiceDependencyException, FileSizeTooLargeException {
    when(instanceSelector.selectInstance(any())).thenReturn(Optional.of(INSTANCE_ID));
    stubValidNode(5L * 1024 * 1024);

    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID,
        Optional.of(5), Optional.of(60));

    String wopiSrc = extractDecodedWopiSrc(url);
    Assertions.assertThat(wopiSrc)
        .isEqualTo(
            WOPI_BASE + "?version=5&service_id=" + INSTANCE_ID + "&offset_from_utc=60");
  }

  @Test
  @DisplayName("WOPISrc: Advanced, no version, offset present → ?service_id=<id>&offset_from_utc=O")
  void wopiSrc_advancedNoVersionWithOffset_exactServiceIdAndOffset()
      throws ServiceDependencyException, FileSizeTooLargeException {
    when(instanceSelector.selectInstance(any())).thenReturn(Optional.of(INSTANCE_ID));
    stubValidNode(5L * 1024 * 1024);

    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID,
        Optional.empty(), Optional.of(120));

    String wopiSrc = extractDecodedWopiSrc(url);
    Assertions.assertThat(wopiSrc)
        .isEqualTo(WOPI_BASE + "?service_id=" + INSTANCE_ID + "&offset_from_utc=120");
  }

  @Test
  @DisplayName("WOPISrc: CE (no instance), no version, offset present → ?offset_from_utc=O")
  void wopiSrc_ceNoVersionWithOffset_exactOffset()
      throws ServiceDependencyException, FileSizeTooLargeException {
    when(instanceSelector.selectInstance(any())).thenReturn(Optional.empty());
    stubValidNode(5L * 1024 * 1024);

    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID,
        Optional.empty(), Optional.of(180));

    String wopiSrc = extractDecodedWopiSrc(url);
    Assertions.assertThat(wopiSrc)
        .isEqualTo(WOPI_BASE + "?offset_from_utc=180");
  }

  @Test
  @DisplayName("WOPISrc: CE (no instance), version + offset → ?version=V&offset_from_utc=O")
  void wopiSrc_ceVersionAndOffset_exactVersionAndOffset()
      throws ServiceDependencyException, FileSizeTooLargeException {
    when(instanceSelector.selectInstance(any())).thenReturn(Optional.empty());
    stubValidNode(5L * 1024 * 1024);

    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID,
        Optional.of(7), Optional.of(240));

    String wopiSrc = extractDecodedWopiSrc(url);
    Assertions.assertThat(wopiSrc)
        .isEqualTo(WOPI_BASE + "?version=7&offset_from_utc=240");
  }

  @Test
  @DisplayName("openFile when selectInstance returns a UUID should embed service_id in both WOPISrc and cool.html URL")
  void givenSelectedInstanceOpenFileShouldEmbedServiceIdInWopiSrcAndCoolHtml()
      throws ServiceDependencyException, FileSizeTooLargeException {
    when(instanceSelector.selectInstance(any())).thenReturn(Optional.of(INSTANCE_ID));
    stubValidNode(5L * 1024 * 1024);

    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID, Optional.empty(), Optional.empty());

    String wopiSrc = extractDecodedWopiSrc(url);
    Assertions.assertThat(wopiSrc)
        .as("service_id must be present inside the decoded WOPISrc URL")
        .contains("service_id=" + INSTANCE_ID);

    // service_id must also appear at the top-level cool.html URL (outside WOPISrc)
    String wopiSrcPrefix = "WOPISrc=";
    int wopiSrcStart = url.indexOf(wopiSrcPrefix);
    int valueStart = wopiSrcStart + wopiSrcPrefix.length();
    int end = url.indexOf("&", valueStart);
    String encoded = end > 0 ? url.substring(valueStart, end) : url.substring(valueStart);
    String afterWopiSrc = url.substring(valueStart + encoded.length());
    Assertions.assertThat(afterWopiSrc)
        .as("service_id must appear in cool.html parameters after WOPISrc")
        .contains("service_id=" + INSTANCE_ID);
  }

  @Test
  @DisplayName("openFile with version AND instance should produce version=V&service_id=ID (single &, correct order) in WOPISrc")
  void givenVersionAndSelectedInstanceOpenFileShouldProduceSingleAmpersandInWopiSrc()
      throws ServiceDependencyException, FileSizeTooLargeException {
    when(instanceSelector.selectInstance(any())).thenReturn(Optional.of(INSTANCE_ID));
    stubValidNode(5L * 1024 * 1024);

    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID, Optional.of(3), Optional.empty());

    String wopiSrcDecoded = extractDecodedWopiSrc(url);

    Assertions.assertThat(wopiSrcDecoded)
        .isEqualTo(WOPI_BASE + "?version=3&service_id=" + INSTANCE_ID);
    Assertions.assertThat(wopiSrcDecoded)
        .as("WOPISrc must not contain double ampersand (&&)")
        .doesNotContain("&&");
  }

  @Test
  @DisplayName("openFile when selectInstance returns empty should NOT embed service_id in WOPISrc")
  void givenNoSelectedInstanceOpenFileShouldNotEmbedServiceIdInWopiSrc()
      throws ServiceDependencyException, FileSizeTooLargeException {
    when(instanceSelector.selectInstance(any())).thenReturn(Optional.empty());
    stubValidNode(5L * 1024 * 1024);

    String url = filesService.openFile(
        REQUESTER_ID, Locale.ENGLISH, COOKIE, NODE_ID, Optional.empty(), Optional.empty());

    Assertions.assertThat(url).doesNotContain("service_id");
  }
}
