// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.entities.files.graphql;

public class User {

  private String id;
  private String full_name;

  public User() {}

  public String getId() {
    return id;
  }

  public String getFull_name() {
    return full_name;
  }
}
