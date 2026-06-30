// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.exceptions;

/**
 * Thrown when a file operation is rejected because the account is over quota.
 * Maps to HTTP 422 (Unprocessable Entity) for template upload and HTTP 413
 * (Payload Too Large) for WOPI save operations.
 */
public class AccountOverQuotaException extends Exception {

  public AccountOverQuotaException(String message) {
    super(message);
  }

  public AccountOverQuotaException(String message, Throwable cause) {
    super(message, cause);
  }
}
