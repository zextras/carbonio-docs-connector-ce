// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.resources;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.docs_connector.Constants;
import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import com.zextras.carbonio.docs_connector.exceptions.AccountOverQuotaException;
import com.zextras.carbonio.docs_connector.exceptions.ServiceDependencyException;
import com.zextras.carbonio.docs_connector.services.WopiService;
import com.zextras.carbonio.docs_connector.types.DocsEditorAttributes;
import com.zextras.carbonio.docs_connector.types.NodeUpdatedTimestamp;
import com.zextras.carbonio.files.entities.FilesBlob;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WopiResource}. No CDI container — WopiService is mocked.
 */
class WopiResourceTest {

  private WopiService wopiService;
  private WopiResource wopiResource;

  private static final UUID NODE_ID = UUID.fromString("58032253-ed56-4eca-9017-3ae26cc2d9f1");
  private static final String REQUESTER_ID = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";
  private static final String COOKIE = "ZM_AUTH_TOKEN=test-token";
  private static final String ACCESS_TOKEN_STR = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

  @BeforeEach
  void setUp() {
    wopiService = mock(WopiService.class);
    wopiResource = new WopiResource(wopiService);
  }

  private OpenDocumentToken buildValidToken(UUID tokenId, UUID documentId) {
    return new OpenDocumentToken(
        tokenId,
        documentId,
        REQUESTER_ID,
        COOKIE,
        Instant.now().plusSeconds(43200)
    );
  }

  private ContainerRequestContext buildContextWithToken(OpenDocumentToken token) {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    when(ctx.getProperty(Constants.Context.OPEN_DOCUMENT_TOKEN)).thenReturn(token);
    return ctx;
  }

  // ----- docsEditorAttributes (GET /wopi/{nodeId}) -----

  @Test
  @DisplayName("docsEditorAttributes should return 200 when token matches node and attributes are found")
  void givenMatchingTokenDocsEditorAttributesShouldReturn200() {
    // Given
    UUID tokenId = UUID.fromString(ACCESS_TOKEN_STR);
    OpenDocumentToken token = buildValidToken(tokenId, NODE_ID);
    ContainerRequestContext ctx = buildContextWithToken(token);

    DocsEditorAttributes attrs = new DocsEditorAttributes()
        .setBaseFileName("doc.odt")
        .setVersion(1)
        .setUserCanWrite(true)
        .setUserFriendlyName("Test User")
        .setSize(1024L);

    when(wopiService.getDocsEditorAttributes(
        eq(REQUESTER_ID), eq(COOKIE), eq(NODE_ID), eq(Optional.empty()), eq(Optional.empty())))
        .thenReturn(Optional.of(attrs));

    // When
    Response response = wopiResource.docsEditorAttributes(ACCESS_TOKEN_STR, NODE_ID, null, null, ctx);

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    Assertions.assertThat(response.getEntity()).isInstanceOf(DocsEditorAttributes.class);
  }

  @Test
  @DisplayName("docsEditorAttributes should return 401 when token documentId does not match nodeId")
  void givenTokenDocumentIdMismatchDocsEditorAttributesShouldReturn401() {
    // Given
    UUID differentNodeId = UUID.randomUUID();
    OpenDocumentToken token = buildValidToken(UUID.fromString(ACCESS_TOKEN_STR), differentNodeId);
    ContainerRequestContext ctx = buildContextWithToken(token);

    // When
    Response response = wopiResource.docsEditorAttributes(ACCESS_TOKEN_STR, NODE_ID, null, null, ctx);

    // Then
    Assertions.assertThat(response.getStatus())
        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  @DisplayName("docsEditorAttributes should return 500 when service returns empty Optional")
  void givenServiceReturnsEmptyDocsEditorAttributesShouldReturn500() {
    // Given
    UUID tokenId = UUID.fromString(ACCESS_TOKEN_STR);
    OpenDocumentToken token = buildValidToken(tokenId, NODE_ID);
    ContainerRequestContext ctx = buildContextWithToken(token);

    when(wopiService.getDocsEditorAttributes(any(), anyString(), any(), any(), any()))
        .thenReturn(Optional.empty());

    // When
    Response response = wopiResource.docsEditorAttributes(ACCESS_TOKEN_STR, NODE_ID, null, null, ctx);

    // Then
    Assertions.assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  // ----- getBlob (GET /wopi/{nodeId}/contents) -----

  @Test
  @DisplayName("getBlob should return 200 with binary content when blob is found")
  void givenValidTokenGetBlobShouldReturn200WithBinaryContent() {
    // Given
    UUID tokenId = UUID.fromString(ACCESS_TOKEN_STR);
    OpenDocumentToken token = buildValidToken(tokenId, NODE_ID);
    ContainerRequestContext ctx = buildContextWithToken(token);

    byte[] blobContent = "file-content".getBytes(StandardCharsets.UTF_8);
    FilesBlob filesBlob = mock(FilesBlob.class);
    when(filesBlob.getContent()).thenReturn(new ByteArrayInputStream(blobContent));
    when(filesBlob.getSize()).thenReturn((long) blobContent.length);

    when(wopiService.getBlob(eq(COOKIE), eq(NODE_ID), eq(Optional.empty())))
        .thenReturn(Optional.of(filesBlob));

    // When
    Response response = wopiResource.getBlob(ACCESS_TOKEN_STR, NODE_ID, null, ctx);

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  @DisplayName("getBlob should return 401 when token documentId does not match nodeId")
  void givenTokenDocumentIdMismatchGetBlobShouldReturn401() {
    // Given
    UUID differentNodeId = UUID.randomUUID();
    OpenDocumentToken token = buildValidToken(UUID.fromString(ACCESS_TOKEN_STR), differentNodeId);
    ContainerRequestContext ctx = buildContextWithToken(token);

    // When
    Response response = wopiResource.getBlob(ACCESS_TOKEN_STR, NODE_ID, null, ctx);

    // Then
    Assertions.assertThat(response.getStatus())
        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  @DisplayName("getBlob should return 500 when service returns empty Optional")
  void givenServiceReturnsEmptyGetBlobShouldReturn500() {
    // Given
    UUID tokenId = UUID.fromString(ACCESS_TOKEN_STR);
    OpenDocumentToken token = buildValidToken(tokenId, NODE_ID);
    ContainerRequestContext ctx = buildContextWithToken(token);

    when(wopiService.getBlob(anyString(), any(), any())).thenReturn(Optional.empty());

    // When
    Response response = wopiResource.getBlob(ACCESS_TOKEN_STR, NODE_ID, null, ctx);

    // Then
    Assertions.assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  // ----- saveBlob (POST /wopi/{nodeId}/contents) -----

  @Test
  @DisplayName("saveBlob should return 200 with NodeUpdatedTimestamp when save succeeds")
  void givenValidInputsSaveBlobShouldReturn200WithTimestamp() throws Exception {
    // Given
    UUID tokenId = UUID.fromString(ACCESS_TOKEN_STR);
    OpenDocumentToken token = buildValidToken(tokenId, NODE_ID);
    ContainerRequestContext ctx = buildContextWithToken(token);

    NodeUpdatedTimestamp timestamp = new NodeUpdatedTimestamp();
    timestamp.setLastModifiedTime("2026-01-01T00:00:00");

    when(wopiService.saveBlob(eq(COOKIE), eq(NODE_ID), eq(Optional.empty()),
        any(InputStream.class), anyLong(), anyBoolean()))
        .thenReturn(Optional.of(timestamp));

    InputStream body = new ByteArrayInputStream("file-content".getBytes(StandardCharsets.UTF_8));

    // When
    Response response = wopiResource.saveBlob(
        ACCESS_TOKEN_STR, NODE_ID, true, false, 12L, null, body, ctx);

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    Assertions.assertThat(response.getEntity()).isInstanceOf(NodeUpdatedTimestamp.class);
  }

  @Test
  @DisplayName("saveBlob should return 424 when service throws ServiceDependencyException")
  void givenServiceDependencyExceptionSaveBlobShouldReturn424() throws Exception {
    // Given
    UUID tokenId = UUID.fromString(ACCESS_TOKEN_STR);
    OpenDocumentToken token = buildValidToken(tokenId, NODE_ID);
    ContainerRequestContext ctx = buildContextWithToken(token);

    when(wopiService.saveBlob(anyString(), any(), any(), any(), anyLong(), anyBoolean()))
        .thenThrow(new ServiceDependencyException("Files unavailable"));

    InputStream body = new ByteArrayInputStream("file-content".getBytes(StandardCharsets.UTF_8));

    // When
    Response response = wopiResource.saveBlob(
        ACCESS_TOKEN_STR, NODE_ID, true, false, 12L, null, body, ctx);

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(424);
  }

  @Test
  @DisplayName("saveBlob should return 401 when token documentId does not match nodeId")
  void givenTokenDocumentIdMismatchSaveBlobShouldReturn401() throws Exception {
    // Given
    UUID differentNodeId = UUID.randomUUID();
    OpenDocumentToken token = buildValidToken(UUID.fromString(ACCESS_TOKEN_STR), differentNodeId);
    ContainerRequestContext ctx = buildContextWithToken(token);

    InputStream body = new ByteArrayInputStream("file-content".getBytes(StandardCharsets.UTF_8));

    // When
    Response response = wopiResource.saveBlob(
        ACCESS_TOKEN_STR, NODE_ID, true, false, 12L, null, body, ctx);

    // Then
    Assertions.assertThat(response.getStatus())
        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  @DisplayName("saveBlob should return 424 when service returns empty Optional")
  void givenServiceReturnsEmptySaveBlobShouldReturn424() throws Exception {
    // Given
    UUID tokenId = UUID.fromString(ACCESS_TOKEN_STR);
    OpenDocumentToken token = buildValidToken(tokenId, NODE_ID);
    ContainerRequestContext ctx = buildContextWithToken(token);

    when(wopiService.saveBlob(anyString(), any(), any(), any(), anyLong(), anyBoolean()))
        .thenReturn(Optional.empty());

    InputStream body = new ByteArrayInputStream("file-content".getBytes(StandardCharsets.UTF_8));

    // When
    Response response = wopiResource.saveBlob(
        ACCESS_TOKEN_STR, NODE_ID, true, false, 12L, null, body, ctx);

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(424);
  }

  // ----- Over-quota saveBlob test (task 5 — TDD additions) -----

  @Test
  @DisplayName("saveBlob should return 413 when service throws AccountOverQuotaException")
  void givenAccountOverQuotaSaveBlobShouldReturn413() throws Exception {
    // Given
    UUID tokenId = UUID.fromString(ACCESS_TOKEN_STR);
    OpenDocumentToken token = buildValidToken(tokenId, NODE_ID);
    ContainerRequestContext ctx = buildContextWithToken(token);

    when(wopiService.saveBlob(anyString(), any(), any(), any(), anyLong(), anyBoolean()))
        .thenThrow(new AccountOverQuotaException("Account is over quota"));

    InputStream body = new ByteArrayInputStream("file-content".getBytes(StandardCharsets.UTF_8));

    // When
    Response response = wopiResource.saveBlob(
        ACCESS_TOKEN_STR, NODE_ID, true, false, 12L, null, body, ctx);

    // Then — over-quota save maps to 413 (Payload Too Large)
    Assertions.assertThat(response.getStatus()).isEqualTo(413);
  }
}
