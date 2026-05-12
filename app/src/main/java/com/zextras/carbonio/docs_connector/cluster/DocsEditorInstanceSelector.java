// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.cluster;

import java.util.Optional;
import java.util.UUID;

/**
 * Strategy interface for selecting a docs-editor instance for a given document.
 *
 * <p>CE provides a default no-op implementation ({@link SingleDocsEditorInstanceSelector}) that
 * returns {@link Optional#empty()} (static WOPI host/port from config is used).
 *
 * <p>Advanced overrides with an implementation that performs Consul health checks and sticky
 * routing via the {@code open_document} table.
 */
public interface DocsEditorInstanceSelector {

  /**
   * Selects a docs-editor instance for the given document.
   *
   * @param documentId the document UUID
   * @return the selected instance UUID, or empty if static config should be used
   */
  Optional<UUID> selectInstance(UUID documentId);
}
