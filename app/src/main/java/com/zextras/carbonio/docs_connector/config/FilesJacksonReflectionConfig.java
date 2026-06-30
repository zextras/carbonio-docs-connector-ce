// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.config;

import com.zextras.carbonio.docs_connector.entities.files.graphql.NodeAttributes;
import com.zextras.carbonio.docs_connector.entities.files.graphql.Permissions;
import com.zextras.carbonio.docs_connector.entities.files.graphql.User;
import com.zextras.carbonio.docs_connector.types.CreatedFile;
import com.zextras.carbonio.docs_connector.types.DocsEditorAttributes;
import com.zextras.carbonio.docs_connector.types.DocsEditorRedirect;
import com.zextras.carbonio.docs_connector.types.FileType;
import com.zextras.carbonio.docs_connector.types.InsertFile;
import com.zextras.carbonio.docs_connector.types.NodeUpdatedTimestamp;
import com.zextras.carbonio.files.entities.NodeId;
import com.zextras.carbonio.files.entities.NodeIdVersion;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Registers, for the GraalVM native image, the POJOs that Jackson (de)serializes at runtime but that
 * Quarkus does not auto-register, so without reflection metadata they fail in native mode.
 *
 * <p>Two categories:
 *
 * <ul>
 *   <li><b>Manually parsed payloads</b> ({@code new ObjectMapper().readValue(...)}, not JAX-RS
 *       bodies):
 *       <ul>
 *         <li>{@link NodeAttributes}, {@link User}, {@link Permissions} — parsed by {@code
 *             NodeAttributes.mapFromJSON} on the files GraphQL response (open file / WOPI). Missing
 *             registration surfaced as a generic 404 on {@code /files/open}.</li>
 *         <li>{@link NodeId}, {@link NodeIdVersion} — parsed by the carbonio-files-sdk {@code
 *             FilesClient.uploadFile} response. Missing registration surfaced as a 404 on {@code
 *             /files/create}.</li>
 *       </ul>
 *   </li>
 *   <li><b>JAX-RS JSON DTOs returned/consumed through an opaque {@code jakarta.ws.rs.core.Response}</b>
 *       — because the resource methods declare {@code Response} (not the concrete type), Quarkus
 *       cannot infer the entity type at build time and does not register it, so native Jackson
 *       serialization fails with {@code "No serializer found ... no properties discovered to create
 *       BeanSerializer"}:
 *       <ul>
 *         <li>{@link DocsEditorRedirect} — {@code FilesResource#openFile} response;</li>
 *         <li>{@link CreatedFile}, {@link InsertFile} — {@code FilesResource#createFile} response /
 *             request body;</li>
 *         <li>{@link DocsEditorAttributes} — {@code WopiResource} CheckFileInfo response;</li>
 *         <li>{@link NodeUpdatedTimestamp} — {@code WopiResource#saveBlob} response;</li>
 *         <li>{@link FileType} — enum nested in the DTOs above.</li>
 *       </ul>
 *   </li>
 * </ul>
 */
@RegisterForReflection(
    targets = {
      // Manually parsed files GraphQL response (open / WOPI)
      NodeAttributes.class,
      User.class,
      Permissions.class,
      // carbonio-files-sdk uploadFile response (create)
      NodeId.class,
      NodeIdVersion.class,
      // JAX-RS JSON DTOs returned via an opaque Response (open / create / WOPI)
      DocsEditorRedirect.class,
      CreatedFile.class,
      DocsEditorAttributes.class,
      NodeUpdatedTimestamp.class,
      InsertFile.class,
      FileType.class
    })
public final class FilesJacksonReflectionConfig {
  private FilesJacksonReflectionConfig() {}
}
