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

import com.zextras.carbonio.docs_connector.config.DocsConnectorServiceConfig;
import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import com.zextras.carbonio.docs_connector.dal.repositories.interfaces.OpenDocumentTokenRepository;
import com.zextras.carbonio.docs_connector.exceptions.FileSizeTooLargeException;
import com.zextras.carbonio.docs_connector.exceptions.ServiceDependencyException;
import com.zextras.carbonio.docs_connector.types.CreatedFile;
import com.zextras.carbonio.docs_connector.types.FileType;
import com.zextras.carbonio.docs_connector.types.InsertFile;
import com.zextras.carbonio.files.FilesClient;
import com.zextras.carbonio.files.entities.NodeId;
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

    // Defaults: no domain override, use defaults for wopi host/port, use default max sizes
    when(applicationConfig.get(DocsConnectorServiceConfig.ApplicationConfig.REQUESTER_DOMAIN_OVERRIDE))
        .thenReturn(Optional.empty());
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

    filesService = new FilesService(tokenRepository, applicationConfig, networkingConfig, filesClient);
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
  void givenFilesClientUploadFailureUploadTemplateShouldReturnEmpty() {
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
  void givenSuccessfulUploadUploadTemplateShouldReturnCreatedFile() {
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
}
