// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.services;

/**
 * Abstraction for quota checks. CE provides a no-op default (quota is always OK).
 * Advanced overrides this with a real implementation that delegates to
 * {@code QuotaService}.
 */
public interface QuotaChecker {

  /**
   * Checks whether the given account is over quota.
   *
   * @param accountId the account identifier
   * @param cookie    the requester's auth cookie
   * @return {@code true} if the account is over quota and should be restricted
   */
  boolean isOverQuota(String accountId, String cookie);
}
