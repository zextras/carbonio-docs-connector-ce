// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.resources;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.docs_connector.Constants;
import com.zextras.carbonio.docs_connector.exceptions.FileSizeTooLargeException;
import com.zextras.carbonio.docs_connector.exceptions.ServiceDependencyException;
import com.zextras.carbonio.docs_connector.services.FilesService;
import com.zextras.carbonio.docs_connector.types.CreatedFile;
import com.zextras.carbonio.docs_connector.types.FileType;
import com.zextras.carbonio.docs_connector.types.InsertFile;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FilesResource}. No CDI container — FilesService is mocked.
 */
class FilesResourceTest {

  private FilesService filesService;
  private FilesResource filesResource;

  private static final String NODE_ID_STR = "58032253-ed56-4eca-9017-3ae26cc2d9f1";
  private static final UUID NODE_ID = UUID.fromString(NODE_ID_STR);
  private static final String REQUESTER_ID = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";
  private static final String COOKIE = "ZM_AUTH_TOKEN=test-token";
  private static final String REQUESTER_DOMAIN = "example.com";

  @BeforeEach
  void setUp() {
    filesService = mock(FilesService.class);
    filesResource = new FilesResource(filesService);
  }

  private ContainerRequestContext buildRequestContext() {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    when(ctx.getProperty(Constants.Context.REQUESTER_ID)).thenReturn(REQUESTER_ID);
    when(ctx.getProperty(Constants.Context.REQUESTER_DOMAIN)).thenReturn(REQUESTER_DOMAIN);
    when(ctx.getProperty(Constants.Context.REQUESTER_LOCALE)).thenReturn(Locale.ENGLISH);
    return ctx;
  }

  @Test
  @DisplayName("createFile should return 200 with CreatedFile when upload succeeds")
  void givenValidInsertFileCreateFileShouldReturn200WithCreatedFile() throws Exception {
    // Given
    InsertFile insertFile = new InsertFile();
    insertFile.setType(FileType.LIBRE_DOCUMENT);
    insertFile.setFilename("New Document");
    insertFile.setDestinationFolderId("LOCAL_ROOT");

    CreatedFile createdFile = new CreatedFile();
    createdFile.setNodeId(NODE_ID);

    when(filesService.uploadTemplate(eq(COOKIE), any(InsertFile.class)))
        .thenReturn(Optional.of(createdFile));

    ContainerRequestContext ctx = buildRequestContext();

    // When
    Response response = filesResource.createFile(COOKIE, insertFile, ctx);

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    Assertions.assertThat(response.getEntity()).isInstanceOf(CreatedFile.class);
    CreatedFile entity = (CreatedFile) response.getEntity();
    Assertions.assertThat(entity.getNodeId()).isEqualTo(NODE_ID);
  }

  @Test
  @DisplayName("createFile should return 500 when upload returns empty Optional")
  void givenFailedUploadCreateFileShouldReturn500() throws Exception {
    // Given
    InsertFile insertFile = new InsertFile();
    insertFile.setType(FileType.LIBRE_DOCUMENT);
    insertFile.setFilename("New Document");
    insertFile.setDestinationFolderId("LOCAL_ROOT");

    when(filesService.uploadTemplate(anyString(), any(InsertFile.class)))
        .thenReturn(Optional.empty());

    ContainerRequestContext ctx = buildRequestContext();

    // When
    Response response = filesResource.createFile(COOKIE, insertFile, ctx);

    // Then
    Assertions.assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  @DisplayName("openFile should return 200 with DocsEditorRedirect URL when node is accessible")
  void givenAccessibleNodeOpenFileShouldReturn200WithRedirectUrl()
      throws ServiceDependencyException, FileSizeTooLargeException {
    // Given
    String editorPath = "services/docs/editor/browser/dist/cool.html?access_token=abc&WOPISrc=xyz";

    when(filesService.openFile(
        eq(REQUESTER_ID),
        eq(Locale.ENGLISH),
        eq(COOKIE),
        eq(NODE_ID_STR),
        eq(Optional.empty()),
        eq(Optional.empty())
    )).thenReturn(editorPath);

    ContainerRequestContext ctx = buildRequestContext();

    // When
    Response response = filesResource.openFile(COOKIE, NODE_ID, null, null, null, ctx);

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    Assertions.assertThat(response.getEntity()).isNotNull();
  }

  @Test
  @DisplayName("openFile with redirect=true should return 307 with Location header")
  void givenRedirectTrueOpenFileShouldReturn307()
      throws ServiceDependencyException, FileSizeTooLargeException {
    // Given
    String editorPath = "services/docs/editor/browser/dist/cool.html?access_token=abc&WOPISrc=xyz";

    when(filesService.openFile(
        eq(REQUESTER_ID),
        eq(Locale.ENGLISH),
        eq(COOKIE),
        eq(NODE_ID_STR),
        eq(Optional.empty()),
        eq(Optional.empty())
    )).thenReturn(editorPath);

    ContainerRequestContext ctx = buildRequestContext();

    // When
    Response response = filesResource.openFile(COOKIE, NODE_ID, null, true, null, ctx);

    // Then
    Assertions.assertThat(response.getStatus())
        .isEqualTo(Response.Status.TEMPORARY_REDIRECT.getStatusCode());
    Assertions.assertThat(response.getLocation()).isNotNull();
  }

  @Test
  @DisplayName("openFile should return 403 when file size is too large")
  void givenFileSizeTooLargeOpenFileShouldReturn403()
      throws ServiceDependencyException, FileSizeTooLargeException {
    // Given
    when(filesService.openFile(any(), any(), anyString(), anyString(), any(), any()))
        .thenThrow(new FileSizeTooLargeException("File too large", 50L));

    ContainerRequestContext ctx = buildRequestContext();

    // When
    Response response = filesResource.openFile(COOKIE, NODE_ID, null, null, null, ctx);

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  @DisplayName("openFile should return 404 when files service dependency fails")
  void givenServiceDependencyExceptionOpenFileShouldReturn404()
      throws ServiceDependencyException, FileSizeTooLargeException {
    // Given
    when(filesService.openFile(any(), any(), anyString(), anyString(), any(), any()))
        .thenThrow(new ServiceDependencyException("Files unavailable"));

    ContainerRequestContext ctx = buildRequestContext();

    // When
    Response response = filesResource.openFile(COOKIE, NODE_ID, null, null, null, ctx);

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }
}
