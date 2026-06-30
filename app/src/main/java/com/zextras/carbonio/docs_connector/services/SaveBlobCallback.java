// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.services;

import java.util.UUID;

/**
 * Callback invoked after a blob is successfully saved via WOPI.
 * CE provides a no-op default. Advanced overrides to update the {@code savedAt}
 * timestamp on the {@code open_document} record.
 */
public interface SaveBlobCallback {

  /**
   * Called after a blob is saved for the given document.
   *
   * @param nodeId the document UUID that was saved
   */
  void onBlobSaved(UUID nodeId);
}
