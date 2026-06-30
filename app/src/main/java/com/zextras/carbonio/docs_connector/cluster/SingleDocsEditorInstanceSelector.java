// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.cluster;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;

/**
 * Default CE implementation of {@link DocsEditorInstanceSelector}.
 * Returns {@link Optional#empty()} so that the static WOPI host/port from the config is used.
 * Advanced overrides this bean with a real instance selector via {@code @Alternative @Priority(1)}.
 */
@ApplicationScoped
public class SingleDocsEditorInstanceSelector implements DocsEditorInstanceSelector {

  @Override
  public Optional<UUID> selectInstance(UUID documentId) {
    return Optional.empty();
  }
}
