// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.files.exceptions.UnAuthorized;

import com.zextras.carbonio.docs_connector.clients.UserManagementClient;
import com.zextras.carbonio.docs_connector.exceptions.ServiceDependencyException;
import com.zextras.carbonio.docs_connector.types.DocsEditorAttributes;
import com.zextras.carbonio.docs_connector.types.NodeUpdatedTimestamp;
import com.zextras.carbonio.files.FilesClient;
import com.zextras.carbonio.files.entities.FilesBlob;
import com.zextras.carbonio.files.entities.NodeIdVersion;
import com.zextras.carbonio.user_management.sdk.grpc.GetUserByIdRequest;
import com.zextras.carbonio.user_management.sdk.grpc.UserInfoProto;
import com.zextras.carbonio.user_management.sdk.grpc.UserInfoResponse;
import com.zextras.carbonio.user_management.sdk.grpc.UserManagementServiceGrpc.UserManagementServiceBlockingStub;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.vavr.control.Try;
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

/**
 * Unit tests for {@link WopiService}. All dependencies are mocked — no CDI container.
 */
class WopiServiceTest {

  private UserManagementClient userManagementClient;
  private UserManagementServiceBlockingStub blockingStub;
  private FilesClient filesClient;
  private WopiService wopiService;

  private static final UUID NODE_ID = UUID.fromString("58032253-ed56-4eca-9017-3ae26cc2d9f1");
  private static final String REQUESTER_ID = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";
  private static final String COOKIE = "ZM_AUTH_TOKEN=test-token";

  private String buildGetNodeResponse(UUID nodeId, String ownerId, String name, String ext,
      String mimeType, long updatedAt, long size, int version, boolean canWrite) {
    return """
        {
          "data": {
            "getNode": {
              "permissions": { "can_write_file": %b },
              "owner": { "id": "%s", "full_name": "Owner" },
              "parent": { "id": "LOCAL_ROOT" },
              "id": "%s",
              "name": "%s",
              "updated_at": %d,
              "extension": "%s",
              "mime_type": "%s",
              "size": %d,
              "version": %d
            }
          }
        }
        """.formatted(canWrite, ownerId, nodeId, name, updatedAt, ext, mimeType, size, version);
  }

  @BeforeEach
  void setUp() {
    userManagementClient = mock(UserManagementClient.class);
    blockingStub = mock(UserManagementServiceBlockingStub.class);
    filesClient = mock(FilesClient.class);

    when(userManagementClient.getBlockingStub()).thenReturn(blockingStub);

    SaveBlobCallback saveBlobCallback = mock(SaveBlobCallback.class);
    wopiService = new WopiService(userManagementClient, filesClient, saveBlobCallback);
  }

  @Test
  @DisplayName("getDocsEditorAttributes should return attributes when user and node are found")
  void givenValidRequesterAndNodeGetDocsEditorAttributesShouldReturnAttributes() {
    // Given
    UserInfoProto userInfo = UserInfoProto.newBuilder()
        .setUserId(REQUESTER_ID)
        .setFullName("Test User")
        .setEmail("test@example.com")
        .build();
    UserInfoResponse userInfoResponse = UserInfoResponse.newBuilder().setUser(userInfo).build();

    GetUserByIdRequest userByIdRequest = GetUserByIdRequest.newBuilder()
        .setUserId(REQUESTER_ID).build();
    when(blockingStub.getUserById(userByIdRequest)).thenReturn(userInfoResponse);

    String graphQLResponse = buildGetNodeResponse(NODE_ID, REQUESTER_ID, "test-doc", "odt",
        "application/vnd.oasis.opendocument.text", 100000L, 1024L * 1024, 1, true);
    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponse));

    // When
    Optional<DocsEditorAttributes> result = wopiService.getDocsEditorAttributes(
        REQUESTER_ID, COOKIE, NODE_ID, Optional.empty(), Optional.empty());

    // Then
    Assertions.assertThat(result).isPresent();
    DocsEditorAttributes attrs = result.get();
    Assertions.assertThat(attrs.getBaseFileName()).isEqualTo("test-doc.odt");
    Assertions.assertThat(attrs.getUserCanWrite()).isTrue();
    Assertions.assertThat(attrs.getUserFriendlyName()).isEqualTo("Test User");
    Assertions.assertThat(attrs.getVersion()).isEqualTo(1);
  }

  @Test
  @DisplayName("getDocsEditorAttributes should throw NoSuchElementException when user-management gRPC fails")
  void givenUserManagementFailureGetDocsEditorAttributesShouldThrow() {
    // Given
    GetUserByIdRequest userByIdRequest = GetUserByIdRequest.newBuilder()
        .setUserId(REQUESTER_ID).build();
    when(blockingStub.getUserById(userByIdRequest))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    // When / Then
    Assertions.assertThatThrownBy(() ->
            wopiService.getDocsEditorAttributes(REQUESTER_ID, COOKIE, NODE_ID,
                Optional.empty(), Optional.empty()))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName("getDocsEditorAttributes should return empty Optional when files graphQL fails")
  void givenFilesGraphQLFailureGetDocsEditorAttributesShouldReturnEmpty() {
    // Given
    UserInfoProto userInfo = UserInfoProto.newBuilder()
        .setUserId(REQUESTER_ID).setFullName("Test User").build();
    UserInfoResponse userInfoResponse = UserInfoResponse.newBuilder().setUser(userInfo).build();

    GetUserByIdRequest userByIdRequest = GetUserByIdRequest.newBuilder()
        .setUserId(REQUESTER_ID).build();
    when(blockingStub.getUserById(userByIdRequest)).thenReturn(userInfoResponse);

    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.failure(new RuntimeException("Files unavailable")));

    // When
    Optional<DocsEditorAttributes> result = wopiService.getDocsEditorAttributes(
        REQUESTER_ID, COOKIE, NODE_ID, Optional.empty(), Optional.empty());

    // Then
    Assertions.assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("getBlob should return Optional with FilesBlob when download succeeds")
  void givenValidInputsGetBlobShouldReturnFilesBlob() {
    // Given
    byte[] blobBytes = "file content".getBytes(StandardCharsets.UTF_8);
    FilesBlob filesBlob = mock(FilesBlob.class);
    when(filesBlob.getContent()).thenReturn(new ByteArrayInputStream(blobBytes));
    when(filesBlob.getSize()).thenReturn((long) blobBytes.length);

    when(filesClient.downloadFile(eq(COOKIE), eq(NODE_ID.toString()), eq(Optional.empty())))
        .thenReturn(Try.success(filesBlob));

    // When
    Optional<FilesBlob> result = wopiService.getBlob(COOKIE, NODE_ID, Optional.empty());

    // Then
    Assertions.assertThat(result).isPresent();
    Assertions.assertThat(result.get()).isSameAs(filesBlob);
  }

  @Test
  @DisplayName("getBlob should return empty Optional when download fails")
  void givenDownloadFailureGetBlobShouldReturnEmpty() {
    // Given
    when(filesClient.downloadFile(eq(COOKIE), eq(NODE_ID.toString()), any()))
        .thenReturn(Try.failure(new RuntimeException("Download failed")));

    // When
    Optional<FilesBlob> result = wopiService.getBlob(COOKIE, NODE_ID, Optional.empty());

    // Then
    Assertions.assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("saveBlob should return NodeUpdatedTimestamp when everything succeeds")
  void givenValidInputsSaveBlobShouldReturnUpdatedTimestamp() throws Exception {
    // Given
    String graphQLResponseBefore = buildGetNodeResponse(NODE_ID, REQUESTER_ID, "doc", "odt",
        "application/vnd.oasis.opendocument.text", 100L, 1024L, 4, true);
    String graphQLResponseAfter = buildGetNodeResponse(NODE_ID, REQUESTER_ID, "doc", "odt",
        "application/vnd.oasis.opendocument.text", 59000L, 1024L, 5, true);

    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponseBefore))
        .thenReturn(Try.success(graphQLResponseAfter));

    NodeIdVersion uploadedVersion = new NodeIdVersion(NODE_ID.toString(), 5);
    when(filesClient.uploadFileVersion(eq(COOKIE), eq(NODE_ID.toString()), anyString(),
        anyString(), any(InputStream.class), anyLong(), eq(true)))
        .thenReturn(Try.success(uploadedVersion));

    InputStream blob = new ByteArrayInputStream("file-content".getBytes(StandardCharsets.UTF_8));

    // When
    Optional<NodeUpdatedTimestamp> result = wopiService.saveBlob(
        COOKIE, NODE_ID, Optional.empty(), blob, 12L, true);

    // Then
    Assertions.assertThat(result).isPresent();
    Assertions.assertThat(result.get().getLastModifiedTime()).isNotNull();
  }

  @Test
  @DisplayName("saveBlob should throw ServiceDependencyException when initial graphQL fetch fails")
  void givenInitialGraphQLFailureSaveBlobShouldThrow() {
    // Given
    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.failure(new RuntimeException("Files unavailable")));

    InputStream blob = new ByteArrayInputStream("file-content".getBytes(StandardCharsets.UTF_8));

    // When / Then
    Assertions.assertThatThrownBy(() ->
            wopiService.saveBlob(COOKIE, NODE_ID, Optional.empty(), blob, 12L, false))
        .isInstanceOf(ServiceDependencyException.class);
  }

  @Test
  @DisplayName("saveBlob should throw ServiceDependencyException when uploadFileVersion returns UnAuthorized")
  void givenUploadReturnsUnAuthorizedSaveBlobShouldThrowServiceDependencyException() {
    // Given
    String graphQLResponse = buildGetNodeResponse(NODE_ID, REQUESTER_ID, "doc", "odt",
        "application/vnd.oasis.opendocument.text", 100L, 1024L, 4, true);

    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponse));

    when(filesClient.uploadFileVersion(eq(COOKIE), eq(NODE_ID.toString()), anyString(),
        anyString(), any(InputStream.class), anyLong(), eq(false)))
        .thenReturn(Try.failure(new UnAuthorized()));

    InputStream blob = new ByteArrayInputStream("file-content".getBytes(StandardCharsets.UTF_8));

    // When / Then
    Assertions.assertThatThrownBy(() ->
            wopiService.saveBlob(COOKIE, NODE_ID, Optional.empty(), blob, 12L, false))
        .isInstanceOf(ServiceDependencyException.class);
  }

  @Test
  @DisplayName("getDocsEditorAttributes when filename exceeds 64 chars should abbreviate to 50 chars of name + extension")
  void givenFilenameExceeding64CharsGetDocsEditorAttributesShouldAbbreviate() {
    // Given
    String longName = "a".repeat(60); // 60-char name + ".odt" = 64 chars → abbreviate to 50 + ".odt" = 54
    UserInfoProto userInfo = UserInfoProto.newBuilder()
        .setUserId(REQUESTER_ID)
        .setFullName("Test User")
        .setEmail("test@example.com")
        .build();
    UserInfoResponse userInfoResponse = UserInfoResponse.newBuilder().setUser(userInfo).build();

    GetUserByIdRequest userByIdRequest = GetUserByIdRequest.newBuilder()
        .setUserId(REQUESTER_ID).build();
    when(blockingStub.getUserById(userByIdRequest)).thenReturn(userInfoResponse);

    String graphQLResponse = buildGetNodeResponse(NODE_ID, REQUESTER_ID, longName, "odt",
        "application/vnd.oasis.opendocument.text", 100000L, 1024L * 1024, 1, true);
    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponse));

    // When
    Optional<DocsEditorAttributes> result = wopiService.getDocsEditorAttributes(
        REQUESTER_ID, COOKIE, NODE_ID, Optional.empty(), Optional.empty());

    // Then — baseFileName should be abbreviated: first 50 chars of name + ".odt"
    Assertions.assertThat(result).isPresent();
    String baseFileName = result.get().getBaseFileName();
    Assertions.assertThat(baseFileName.length()).isLessThanOrEqualTo(64);
    Assertions.assertThat(baseFileName).endsWith(".odt");
    Assertions.assertThat(baseFileName).startsWith("a".repeat(50));
  }

  @Test
  @DisplayName("getDocsEditorAttributes with null extension should not throw and return filename without extension")
  void givenNullExtensionGetDocsEditorAttributesShouldHandleGracefully() {
    // Given — null extension in JSON
    String graphQLResponseNullExt = """
        {
          "data": {
            "getNode": {
              "permissions": { "can_write_file": true },
              "owner": { "id": "%s", "full_name": "Owner" },
              "parent": { "id": "LOCAL_ROOT" },
              "id": "%s",
              "name": "nodoc",
              "updated_at": 1000,
              "extension": null,
              "mime_type": "application/vnd.oasis.opendocument.text",
              "size": 1024,
              "version": 1
            }
          }
        }
        """.formatted(REQUESTER_ID, NODE_ID);

    UserInfoProto userInfo = UserInfoProto.newBuilder()
        .setUserId(REQUESTER_ID)
        .setFullName("Test User")
        .setEmail("test@example.com")
        .build();
    UserInfoResponse userInfoResponse = UserInfoResponse.newBuilder().setUser(userInfo).build();

    GetUserByIdRequest userByIdRequest = GetUserByIdRequest.newBuilder()
        .setUserId(REQUESTER_ID).build();
    when(blockingStub.getUserById(userByIdRequest)).thenReturn(userInfoResponse);

    when(filesClient.genericGraphQLRequest(eq(COOKIE), anyString()))
        .thenReturn(Try.success(graphQLResponseNullExt));

    // When / Then — must not throw, baseFileName is just the name
    Assertions.assertThatCode(() -> {
      Optional<DocsEditorAttributes> result = wopiService.getDocsEditorAttributes(
          REQUESTER_ID, COOKIE, NODE_ID, Optional.empty(), Optional.empty());
      Assertions.assertThat(result).isPresent();
      Assertions.assertThat(result.get().getBaseFileName()).isEqualTo("nodoc");
    }).doesNotThrowAnyException();
  }
}
