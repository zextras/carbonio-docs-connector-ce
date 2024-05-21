// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.types.health;

import java.io.Serializable;

public class ServiceHealth implements Serializable {

  private String name;
  private boolean ready;
  private boolean live;
  private DependencyType type;

  public String getName() {
    return name;
  }

  public ServiceHealth setName(String name) {
    this.name = name;
    return this;
  }

  public boolean isReady() {
    return ready;
  }

  public ServiceHealth setReady(boolean ready) {
    this.ready = ready;
    return this;
  }

  public boolean isLive() {
    return live;
  }

  public ServiceHealth setLive(boolean live) {
    this.live = live;
    return this;
  }

  public DependencyType getType() {
    return type;
  }

  public ServiceHealth setType(DependencyType type) {
    this.type = type;
    return this;
  }
}
