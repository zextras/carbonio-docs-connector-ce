// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.docs_connector.exceptions.AccountOverQuotaException;
import com.zextras.carbonio.docs_connector.exceptions.ServiceDependencyException;
import com.zextras.carbonio.docs_connector.types.DocsEditorAttributes;
import com.zextras.carbonio.docs_connector.types.NodeUpdatedTimestamp;
import com.zextras.carbonio.files.sdk.FilesInternalClient;
import com.zextras.carbonio.files.sdk.FilesInternalClientException;
import com.zextras.carbonio.files.sdk.rest.model.InternalNodeDto;
import com.zextras.carbonio.files.sdk.rest.model.OwnerDto;
import com.zextras.carbonio.files.sdk.rest.model.PermissionsDto;
import com.zextras.carbonio.user_management.sdk.rest.ApiException;
import com.zextras.carbonio.user_management.sdk.rest.api.UserResourceApi;
import com.zextras.carbonio.user_management.sdk.rest.model.UserInfoDto;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link WopiService}. All dependencies are mocked — no CDI container. */
class WopiServiceTest {

  private UserResourceApi userResourceApi;
  private FilesInternalClient filesClient;
  private WopiService wopiService;

  private static final UUID NODE_ID = UUID.fromString("58032253-ed56-4eca-9017-3ae26cc2d9f1");
  private static final String REQUESTER_ID = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";

  private InternalNodeDto buildNodeDto(
      UUID nodeId,
      String ownerId,
      String name,
      String ext,
      String mimeType,
      long updatedAt,
      long size,
      int version,
      boolean canWrite) {
    return new InternalNodeDto()
        .id(nodeId.toString())
        .name(name)
        .extension(ext)
        .mimeType(mimeType)
        .size(size)
        .version(version)
        .updatedAt(updatedAt)
        .owner(new OwnerDto().id(ownerId))
        .permissions(new PermissionsDto().canWriteFile(canWrite));
  }

  @BeforeEach
  void setUp() {
    userResourceApi = mock(UserResourceApi.class);
    filesClient = mock(FilesInternalClient.class);

    SaveBlobCallback saveBlobCallback = mock(SaveBlobCallback.class);
    wopiService = new WopiService(userResourceApi, filesClient, saveBlobCallback);
  }

  @Test
  @DisplayName("getDocsEditorAttributes should return attributes when user and node are found")
  void givenValidRequesterAndNodeGetDocsEditorAttributesShouldReturnAttributes() throws Exception {
    // Given
    UserInfoDto userInfo =
        new UserInfoDto().userId(REQUESTER_ID).fullName("Test User").email("test@example.com");
    when(userResourceApi.internalUsersIdUserIdGet(REQUESTER_ID)).thenReturn(userInfo);

    when(filesClient.getNode(eq(REQUESTER_ID), eq(NODE_ID.toString())))
        .thenReturn(
            buildNodeDto(
                NODE_ID,
                REQUESTER_ID,
                "test-doc",
                "odt",
                "application/vnd.oasis.opendocument.text",
                100000L,
                1024L * 1024,
                1,
                true));

    // When
    Optional<DocsEditorAttributes> result =
        wopiService.getDocsEditorAttributes(
            REQUESTER_ID, NODE_ID, Optional.empty(), Optional.empty());

    // Then
    Assertions.assertThat(result).isPresent();
    DocsEditorAttributes attrs = result.get();
    Assertions.assertThat(attrs.getBaseFileName()).isEqualTo("test-doc.odt");
    Assertions.assertThat(attrs.getUserCanWrite()).isTrue();
    Assertions.assertThat(attrs.getUserFriendlyName()).isEqualTo("Test User");
    Assertions.assertThat(attrs.getVersion()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "getDocsEditorAttributes with a specific version should return version-scoped metadata"
          + " (historical-version parity)")
  void givenAVersionGetDocsEditorAttributesShouldUseVersionAwareGetNode() throws Exception {
    // Given -- opening version 2 must return version 2's size/updatedAt/version, not the current
    // version's. Only the 3-arg getNode is stubbed: a call to the 2-arg overload would return a
    // null node and NPE, proving the version is actually forwarded.
    UserInfoDto userInfo =
        new UserInfoDto().userId(REQUESTER_ID).fullName("Test User").email("test@example.com");
    when(userResourceApi.internalUsersIdUserIdGet(REQUESTER_ID)).thenReturn(userInfo);

    long historicalSize = 4242L;
    when(filesClient.getNode(eq(REQUESTER_ID), eq(NODE_ID.toString()), eq(2)))
        .thenReturn(
            buildNodeDto(
                NODE_ID,
                REQUESTER_ID,
                "test-doc",
                "odt",
                "application/vnd.oasis.opendocument.text",
                200000L,
                historicalSize,
                2,
                true));

    // When
    Optional<DocsEditorAttributes> result =
        wopiService.getDocsEditorAttributes(
            REQUESTER_ID, NODE_ID, Optional.of(2), Optional.empty());

    // Then -- metadata reflects the requested version
    Assertions.assertThat(result).isPresent();
    Assertions.assertThat(result.get().getVersion()).isEqualTo(2);
    Assertions.assertThat(result.get().getSize()).isEqualTo(historicalSize);
  }

  @Test
  @DisplayName(
      "getDocsEditorAttributes should throw NoSuchElementException when user-management reports 404"
          + " (user not found)")
  void givenUserManagement404GetDocsEditorAttributesShouldThrowNoSuchElement() throws Exception {
    // Given
    when(userResourceApi.internalUsersIdUserIdGet(REQUESTER_ID))
        .thenThrow(new ApiException(404, "Not Found"));

    // When / Then
    Assertions.assertThatThrownBy(
            () ->
                wopiService.getDocsEditorAttributes(
                    REQUESTER_ID, NODE_ID, Optional.empty(), Optional.empty()))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName(
      "getDocsEditorAttributes should throw ServiceDependencyException when user-management reports"
          + " a 5xx")
  void givenUserManagement5xxGetDocsEditorAttributesShouldThrowServiceDependencyException()
      throws Exception {
    // Given
    when(userResourceApi.internalUsersIdUserIdGet(REQUESTER_ID))
        .thenThrow(new ApiException(503, "Service Unavailable"));

    // When / Then
    Assertions.assertThatThrownBy(
            () ->
                wopiService.getDocsEditorAttributes(
                    REQUESTER_ID, NODE_ID, Optional.empty(), Optional.empty()))
        .isInstanceOf(ServiceDependencyException.class);
  }

  @Test
  @DisplayName(
      "getDocsEditorAttributes should throw ServiceDependencyException when user-management call"
          + " fails with getCode()==0 (network failure)")
  void
      givenUserManagementNetworkFailureGetDocsEditorAttributesShouldThrowServiceDependencyException()
          throws Exception {
    // Given — ApiException(Throwable) never sets a code, so getCode() == 0 (connection
    // refused / timeout / a body that failed to deserialize)
    when(userResourceApi.internalUsersIdUserIdGet(REQUESTER_ID))
        .thenThrow(new ApiException(new java.io.IOException("connection refused")));

    // When / Then
    Assertions.assertThatThrownBy(
            () ->
                wopiService.getDocsEditorAttributes(
                    REQUESTER_ID, NODE_ID, Optional.empty(), Optional.empty()))
        .isInstanceOf(ServiceDependencyException.class);
  }

  @Test
  @DisplayName(
      "getDocsEditorAttributes should throw NoSuchElementException when user-management returns a"
          + " null UserInfoDto (blank 2xx body)")
  void givenNullUserInfoGetDocsEditorAttributesShouldThrowNoSuchElement() throws Exception {
    // Given — the generated client returns null outright for a 2xx response with a blank body
    when(userResourceApi.internalUsersIdUserIdGet(REQUESTER_ID)).thenReturn(null);

    // When / Then
    Assertions.assertThatThrownBy(
            () ->
                wopiService.getDocsEditorAttributes(
                    REQUESTER_ID, NODE_ID, Optional.empty(), Optional.empty()))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName(
      "getDocsEditorAttributes should throw NoSuchElementException when userId is null (nullable"
          + " field, no gRPC empty-string guarantee)")
  void givenNullUserIdGetDocsEditorAttributesShouldThrowNoSuchElement() throws Exception {
    // Given — under gRPC a missing userId was guaranteed "" (IllegalArgumentException on
    // UUID.fromString); the REST DTO field is @Nullable, so it can be null instead.
    UserInfoDto userInfo = new UserInfoDto().fullName("Test User").email("test@example.com");
    when(userResourceApi.internalUsersIdUserIdGet(REQUESTER_ID)).thenReturn(userInfo);

    // When / Then
    Assertions.assertThatThrownBy(
            () ->
                wopiService.getDocsEditorAttributes(
                    REQUESTER_ID, NODE_ID, Optional.empty(), Optional.empty()))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName(
      "getDocsEditorAttributes should throw ServiceDependencyException when files getNode fails")
  void givenFilesGraphQLFailureGetDocsEditorAttributesShouldThrowServiceDependencyException()
      throws Exception {
    // Given — a genuinely failed/unreachable files call is a dependency failure, distinct from a
    // 404 (see givenNodeNotFoundGetDocsEditorAttributesShouldThrowNoSuchElement below).
    UserInfoDto userInfo = new UserInfoDto().userId(REQUESTER_ID).fullName("Test User");
    when(userResourceApi.internalUsersIdUserIdGet(REQUESTER_ID)).thenReturn(userInfo);

    when(filesClient.getNode(anyString(), anyString()))
        .thenThrow(
            new FilesInternalClientException("Files unavailable", -1, new RuntimeException()));

    // When / Then
    Assertions.assertThatThrownBy(
            () ->
                wopiService.getDocsEditorAttributes(
                    REQUESTER_ID, NODE_ID, Optional.empty(), Optional.empty()))
        .isInstanceOf(ServiceDependencyException.class);
  }

  @Test
  @DisplayName(
      "getDocsEditorAttributes should throw NoSuchElementException when files reports the node does"
          + " not exist")
  void givenNodeNotFoundGetDocsEditorAttributesShouldThrowNoSuchElement() throws Exception {
    // Given -- HTTP 404 from getNode means the node does not exist or is inaccessible.
    UserInfoDto userInfo = new UserInfoDto().userId(REQUESTER_ID).fullName("Test User");
    when(userResourceApi.internalUsersIdUserIdGet(REQUESTER_ID)).thenReturn(userInfo);

    when(filesClient.getNode(anyString(), anyString()))
        .thenThrow(new FilesInternalClientException("not found", 404, new RuntimeException()));

    // When / Then
    Assertions.assertThatThrownBy(
            () ->
                wopiService.getDocsEditorAttributes(
                    REQUESTER_ID, NODE_ID, Optional.empty(), Optional.empty()))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName("getBlob should return the blob content and node size when download succeeds")
  void givenValidInputsGetBlobShouldReturnBlobWithSize() {
    // Given
    byte[] blobBytes = "file content".getBytes(StandardCharsets.UTF_8);
    InputStream blobStream = new ByteArrayInputStream(blobBytes);

    when(filesClient.getNode(eq(REQUESTER_ID), eq(NODE_ID.toString())))
        .thenReturn(
            buildNodeDto(
                NODE_ID,
                REQUESTER_ID,
                "doc",
                "odt",
                "application/vnd.oasis.opendocument.text",
                1000L,
                blobBytes.length,
                1,
                true));
    when(filesClient.downloadFile(eq(REQUESTER_ID), eq(NODE_ID.toString()), eq(Optional.empty())))
        .thenReturn(blobStream);

    // When
    Optional<WopiService.WopiBlob> result =
        wopiService.getBlob(REQUESTER_ID, NODE_ID, Optional.empty());

    // Then
    Assertions.assertThat(result).isPresent();
    Assertions.assertThat(result.get().content()).isSameAs(blobStream);
    Assertions.assertThat(result.get().size()).isEqualTo((long) blobBytes.length);
  }

  @Test
  @DisplayName("getBlob with a specific version should size the blob from that version (parity)")
  void givenAVersionGetBlobShouldSizeFromVersionAwareGetNode() {
    // Given -- serving version 3 must report version 3's size, not the current version's
    byte[] blobBytes = "historical content".getBytes(StandardCharsets.UTF_8);
    InputStream blobStream = new ByteArrayInputStream(blobBytes);
    long historicalSize = 987L;

    when(filesClient.getNode(eq(REQUESTER_ID), eq(NODE_ID.toString()), eq(3)))
        .thenReturn(
            buildNodeDto(
                NODE_ID,
                REQUESTER_ID,
                "doc",
                "odt",
                "application/vnd.oasis.opendocument.text",
                1000L,
                historicalSize,
                3,
                true));
    when(filesClient.downloadFile(eq(REQUESTER_ID), eq(NODE_ID.toString()), eq(Optional.of(3))))
        .thenReturn(blobStream);

    // When
    Optional<WopiService.WopiBlob> result =
        wopiService.getBlob(REQUESTER_ID, NODE_ID, Optional.of(3));

    // Then
    Assertions.assertThat(result).isPresent();
    Assertions.assertThat(result.get().size()).isEqualTo(historicalSize);
  }

  @Test
  @DisplayName("getBlob should return empty Optional when download fails")
  void givenDownloadFailureGetBlobShouldReturnEmpty() {
    // Given
    when(filesClient.getNode(eq(REQUESTER_ID), eq(NODE_ID.toString())))
        .thenReturn(
            buildNodeDto(
                NODE_ID,
                REQUESTER_ID,
                "doc",
                "odt",
                "application/vnd.oasis.opendocument.text",
                1000L,
                1024L,
                1,
                true));
    when(filesClient.downloadFile(eq(REQUESTER_ID), eq(NODE_ID.toString()), any()))
        .thenThrow(
            new FilesInternalClientException("Download failed", 500, new RuntimeException()));

    // When
    Optional<WopiService.WopiBlob> result =
        wopiService.getBlob(REQUESTER_ID, NODE_ID, Optional.empty());

    // Then
    Assertions.assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("saveBlob should return NodeUpdatedTimestamp when everything succeeds")
  void givenValidInputsSaveBlobShouldReturnUpdatedTimestamp() throws Exception {
    // Given
    InternalNodeDto nodeBefore =
        buildNodeDto(
            NODE_ID,
            REQUESTER_ID,
            "doc",
            "odt",
            "application/vnd.oasis.opendocument.text",
            100L,
            1024L,
            4,
            true);
    InternalNodeDto nodeAfter =
        buildNodeDto(
            NODE_ID,
            REQUESTER_ID,
            "doc",
            "odt",
            "application/vnd.oasis.opendocument.text",
            59000L,
            1024L,
            5,
            true);

    when(filesClient.getNode(eq(REQUESTER_ID), eq(NODE_ID.toString())))
        .thenReturn(nodeBefore)
        .thenReturn(nodeAfter);

    when(filesClient.uploadFileVersion(
            eq(REQUESTER_ID),
            eq(NODE_ID.toString()),
            anyString(),
            anyString(),
            any(),
            anyLong(),
            eq(true)))
        .thenReturn(5);

    InputStream blob = new ByteArrayInputStream("file-content".getBytes(StandardCharsets.UTF_8));

    // When
    Optional<NodeUpdatedTimestamp> result =
        wopiService.saveBlob(REQUESTER_ID, NODE_ID, Optional.empty(), blob, 12L, true);

    // Then
    Assertions.assertThat(result).isPresent();
    Assertions.assertThat(result.get().getLastModifiedTime()).isNotNull();
  }

  @Test
  @DisplayName("saveBlob should throw ServiceDependencyException when initial getNode fails")
  void givenInitialGraphQLFailureSaveBlobShouldThrow() {
    // Given
    when(filesClient.getNode(anyString(), anyString()))
        .thenThrow(
            new FilesInternalClientException("Files unavailable", -1, new RuntimeException()));

    InputStream blob = new ByteArrayInputStream("file-content".getBytes(StandardCharsets.UTF_8));

    // When / Then
    Assertions.assertThatThrownBy(
            () -> wopiService.saveBlob(REQUESTER_ID, NODE_ID, Optional.empty(), blob, 12L, false))
        .isInstanceOf(ServiceDependencyException.class);
  }

  @Test
  @DisplayName(
      "saveBlob should throw NoSuchElementException when files reports the node does not exist")
  void givenNodeNotFoundSaveBlobShouldThrowNoSuchElement() {
    // Given -- HTTP 404 from getNode means node does not exist or is inaccessible.
    when(filesClient.getNode(anyString(), anyString()))
        .thenThrow(new FilesInternalClientException("not found", 404, new RuntimeException()));

    InputStream blob = new ByteArrayInputStream("file-content".getBytes(StandardCharsets.UTF_8));

    // When / Then
    Assertions.assertThatThrownBy(
            () -> wopiService.saveBlob(REQUESTER_ID, NODE_ID, Optional.empty(), blob, 12L, false))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName(
      "saveBlob should throw ServiceDependencyException when uploadFileVersion returns"
          + " a non-quota error")
  void givenUploadReturnsErrorSaveBlobShouldThrowServiceDependencyException() {
    // Given
    InternalNodeDto node =
        buildNodeDto(
            NODE_ID,
            REQUESTER_ID,
            "doc",
            "odt",
            "application/vnd.oasis.opendocument.text",
            100L,
            1024L,
            4,
            true);

    when(filesClient.getNode(eq(REQUESTER_ID), eq(NODE_ID.toString()))).thenReturn(node);

    when(filesClient.uploadFileVersion(
            eq(REQUESTER_ID),
            eq(NODE_ID.toString()),
            anyString(),
            anyString(),
            any(),
            anyLong(),
            eq(false)))
        .thenThrow(new FilesInternalClientException("unauthorized", 403, new RuntimeException()));

    InputStream blob = new ByteArrayInputStream("file-content".getBytes(StandardCharsets.UTF_8));

    // When / Then
    Assertions.assertThatThrownBy(
            () -> wopiService.saveBlob(REQUESTER_ID, NODE_ID, Optional.empty(), blob, 12L, false))
        .isInstanceOf(ServiceDependencyException.class);
  }

  // ----- Over-quota saveBlob tests (task 5 — TDD additions) -----

  @Test
  @DisplayName(
      "saveBlob should throw AccountOverQuotaException when Files returns 422 (over quota)")
  void givenAccountInOverQuotaSaveBlobShouldThrowAccountOverQuotaException() {
    // Given
    InternalNodeDto node =
        buildNodeDto(
            NODE_ID,
            REQUESTER_ID,
            "doc",
            "odt",
            "application/vnd.oasis.opendocument.text",
            100L,
            1024L,
            4,
            true);

    when(filesClient.getNode(eq(REQUESTER_ID), eq(NODE_ID.toString()))).thenReturn(node);

    when(filesClient.uploadFileVersion(
            eq(REQUESTER_ID),
            eq(NODE_ID.toString()),
            anyString(),
            anyString(),
            any(),
            anyLong(),
            eq(false)))
        .thenThrow(
            new FilesInternalClientException("account is over quota", 422, new RuntimeException()));

    InputStream blob = new ByteArrayInputStream("file-content".getBytes(StandardCharsets.UTF_8));

    // When / Then — WopiService must propagate AccountOverQuotaException (mapped from 422)
    Assertions.assertThatThrownBy(
            () -> wopiService.saveBlob(REQUESTER_ID, NODE_ID, Optional.empty(), blob, 12L, false))
        .isInstanceOf(AccountOverQuotaException.class);
  }

  @Test
  @DisplayName(
      "getDocsEditorAttributes when filename exceeds 64 chars should abbreviate to 50 chars of name"
          + " + extension")
  void givenFilenameExceeding64CharsGetDocsEditorAttributesShouldAbbreviate() throws Exception {
    // Given
    String longName =
        "a".repeat(60); // 60-char name + ".odt" = 64 chars → abbreviate to 50 + ".odt" = 54
    UserInfoDto userInfo =
        new UserInfoDto().userId(REQUESTER_ID).fullName("Test User").email("test@example.com");
    when(userResourceApi.internalUsersIdUserIdGet(REQUESTER_ID)).thenReturn(userInfo);

    when(filesClient.getNode(eq(REQUESTER_ID), eq(NODE_ID.toString())))
        .thenReturn(
            buildNodeDto(
                NODE_ID,
                REQUESTER_ID,
                longName,
                "odt",
                "application/vnd.oasis.opendocument.text",
                100000L,
                1024L * 1024,
                1,
                true));

    // When
    Optional<DocsEditorAttributes> result =
        wopiService.getDocsEditorAttributes(
            REQUESTER_ID, NODE_ID, Optional.empty(), Optional.empty());

    // Then — baseFileName should be abbreviated: first 50 chars of name + ".odt"
    Assertions.assertThat(result).isPresent();
    String baseFileName = result.get().getBaseFileName();
    Assertions.assertThat(baseFileName.length()).isLessThanOrEqualTo(64);
    Assertions.assertThat(baseFileName).endsWith(".odt");
    Assertions.assertThat(baseFileName).startsWith("a".repeat(50));
  }

  @Test
  @DisplayName(
      "getDocsEditorAttributes with null extension should not throw and return filename without"
          + " extension")
  void givenNullExtensionGetDocsEditorAttributesShouldHandleGracefully() throws Exception {
    // Given — null extension
    UserInfoDto userInfo =
        new UserInfoDto().userId(REQUESTER_ID).fullName("Test User").email("test@example.com");
    when(userResourceApi.internalUsersIdUserIdGet(REQUESTER_ID)).thenReturn(userInfo);

    when(filesClient.getNode(eq(REQUESTER_ID), eq(NODE_ID.toString())))
        .thenReturn(
            new InternalNodeDto()
                .id(NODE_ID.toString())
                .name("nodoc")
                .extension(null)
                .mimeType("application/vnd.oasis.opendocument.text")
                .size(1024L)
                .version(1)
                .updatedAt(1000L)
                .owner(new OwnerDto().id(REQUESTER_ID))
                .permissions(new PermissionsDto().canWriteFile(true)));

    // When / Then — must not throw, baseFileName is just the name
    Assertions.assertThatCode(
            () -> {
              Optional<DocsEditorAttributes> result =
                  wopiService.getDocsEditorAttributes(
                      REQUESTER_ID, NODE_ID, Optional.empty(), Optional.empty());
              Assertions.assertThat(result).isPresent();
              Assertions.assertThat(result.get().getBaseFileName()).isEqualTo("nodoc");
            })
        .doesNotThrowAnyException();
  }
}
