// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.config;

import com.zextras.carbonio.docs_connector.types.CreatedFile;
import com.zextras.carbonio.docs_connector.types.DocsEditorAttributes;
import com.zextras.carbonio.docs_connector.types.DocsEditorRedirect;
import com.zextras.carbonio.docs_connector.types.FileType;
import com.zextras.carbonio.docs_connector.types.InsertFile;
import com.zextras.carbonio.docs_connector.types.NodeUpdatedTimestamp;
import com.zextras.carbonio.files.sdk.rest.model.CreateFolderRequest;
import com.zextras.carbonio.files.sdk.rest.model.CreatePublicLinkRequest;
import com.zextras.carbonio.files.sdk.rest.model.DeleteAllRequest;
import com.zextras.carbonio.files.sdk.rest.model.DeleteAllResponse;
import com.zextras.carbonio.files.sdk.rest.model.InternalNodeDto;
import com.zextras.carbonio.files.sdk.rest.model.InternalNodeIdDto;
import com.zextras.carbonio.files.sdk.rest.model.OwnerDto;
import com.zextras.carbonio.files.sdk.rest.model.ParentDto;
import com.zextras.carbonio.files.sdk.rest.model.PermissionsDto;
import com.zextras.carbonio.files.sdk.rest.model.PublicLinkDto;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Registers, for the GraalVM native image, the POJOs that Jackson (de)serializes at runtime but
 * that Quarkus does not auto-register, so without reflection metadata they fail in native mode.
 *
 * <p><b>JAX-RS JSON DTOs returned/consumed through an opaque {@code
 * jakarta.ws.rs.core.Response}</b> — because the resource methods declare {@code Response}
 * (not the concrete type), Quarkus cannot infer the entity type at build time and does not
 * register it, so native Jackson serialization fails with {@code "No serializer found ... no
 * properties discovered to create BeanSerializer"}:
 *
 * <ul>
 *   <li>{@link DocsEditorRedirect} — {@code FilesResource#openFile} response;
 *   <li>{@link CreatedFile}, {@link InsertFile} — {@code FilesResource#createFile} response /
 *       request body;
 *   <li>{@link DocsEditorAttributes} — {@code WopiResource} CheckFileInfo response;
 *   <li>{@link NodeUpdatedTimestamp} — {@code WopiResource#saveBlob} response;
 *   <li>{@link FileType} — enum nested in the DTOs above.
 * </ul>
 *
 * <p><b>carbonio-files-ce-rest-sdk DTOs</b> — the SDK is a plain jar, not a Quarkus extension,
 * so it contributes no native reflection metadata. Every DTO the generated {@code ApiClient}
 * deserializes (Jackson POJO mapping, not tree-model) must be registered here explicitly:
 *
 * <ul>
 *   <li>{@link InternalNodeDto} — returned by {@code getNode()} (used by both {@code FilesService}
 *       and {@code WopiService} at runtime);
 *   <li>{@link OwnerDto}, {@link ParentDto}, {@link PermissionsDto} — nested fields of {@link
 *       InternalNodeDto};
 *   <li>{@link InternalNodeIdDto}, {@link PublicLinkDto}, {@link DeleteAllResponse} — other
 *       deserialized response DTOs (defensive; not called by docs-connector today but included so
 *       the registration stays complete if the SDK usage grows);
 *   <li>{@link CreateFolderRequest}, {@link CreatePublicLinkRequest}, {@link DeleteAllRequest} —
 *       request bodies serialized by the generated client (defensive).
 * </ul>
 *
 * <p>Upload/download responses ({@code uploadFile}/{@code uploadFileVersion}/{@code downloadFile})
 * are parsed via Jackson tree-model ({@code JsonNode}) directly from the raw HTTP body, not via
 * POJO deserialization, so they need no registration.
 */
@RegisterForReflection(
    targets = {
      // JAX-RS JSON DTOs returned via an opaque Response (open / create / WOPI)
      DocsEditorRedirect.class,
      CreatedFile.class,
      DocsEditorAttributes.class,
      NodeUpdatedTimestamp.class,
      InsertFile.class,
      FileType.class,
      // carbonio-files-ce-rest-sdk model DTOs (plain jar, no native metadata)
      InternalNodeDto.class,
      OwnerDto.class,
      ParentDto.class,
      PermissionsDto.class,
      InternalNodeIdDto.class,
      PublicLinkDto.class,
      DeleteAllResponse.class,
      CreateFolderRequest.class,
      CreatePublicLinkRequest.class,
      DeleteAllRequest.class,
    })
public final class FilesJacksonReflectionConfig {
  private FilesJacksonReflectionConfig() {}
}
