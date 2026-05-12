// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.services;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Default (CE) no-op implementation of {@link QuotaChecker}.
 * Always returns {@code false} (not over quota) because CE has no quota concept.
 * Advanced overrides this bean via {@code @Alternative @Priority(1)}.
 */
@ApplicationScoped
public class NoOpQuotaChecker implements QuotaChecker {

  @Override
  public boolean isOverQuota(String accountId, String cookie) {
    return false;
  }
}
