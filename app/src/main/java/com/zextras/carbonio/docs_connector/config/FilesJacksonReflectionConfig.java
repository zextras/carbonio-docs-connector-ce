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
 * <ul>
 *   <li>{@link DocsEditorRedirect} — {@code FilesResource#openFile} response;
 *   <li>{@link CreatedFile}, {@link InsertFile} — {@code FilesResource#createFile} response /
 *       request body;
 *   <li>{@link DocsEditorAttributes} — {@code WopiResource} CheckFileInfo response;
 *   <li>{@link NodeUpdatedTimestamp} — {@code WopiResource#saveBlob} response;
 *   <li>{@link FileType} — enum nested in the DTOs above.
 * </ul>
 *
 * <p>The carbonio-files-ce-rest-sdk DTOs ({@code InternalNodeDto} and friends) are handled by the
 * SDK's own Jackson configuration and do not need manual registration here.
 */
@RegisterForReflection(
    targets = {
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
