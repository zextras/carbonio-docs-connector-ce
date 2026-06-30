// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.exceptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({"cause", "stackTrace", "suppressed", "localizedMessage"})
public class FileSizeTooLargeException extends Exception {

  private final String message;
  private final long maxSizeLimitInMB;

  public FileSizeTooLargeException(String message, long maxSizeLimitInMB) {
    this.message = message;
    this.maxSizeLimitInMB = maxSizeLimitInMB;
  }

  public String getMessage() {
    return message;
  }

  public long getMaxSizeLimitInMB() {
    return maxSizeLimitInMB;
  }
}
