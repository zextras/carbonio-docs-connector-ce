// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.exceptions;

public class ServiceDependencyException extends Exception {

  public ServiceDependencyException(Throwable throwable) {
    super(throwable.getMessage(), throwable);
  }
}
