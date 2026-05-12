// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.services;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

/**
 * Default CE no-op implementation of {@link SaveBlobCallback}.
 * CE has no {@code open_document} table, so nothing to update after save.
 * Advanced overrides this via {@code @Alternative @Priority(1)}.
 */
@ApplicationScoped
public class NoOpSaveBlobCallback implements SaveBlobCallback {

  @Override
  public void onBlobSaved(UUID nodeId) {
    // No-op in CE
  }
}
