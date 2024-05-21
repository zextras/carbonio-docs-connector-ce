// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.types.health;

import java.io.Serializable;
import java.util.List;

public class HealthStatus implements Serializable {

  private boolean ready;
  private List<ServiceHealth> dependencies;

  public boolean isReady() {
    return ready;
  }

  public HealthStatus setReady(boolean ready) {
    this.ready = ready;
    return this;
  }

  public List<ServiceHealth> getDependencies() {
    return dependencies;
  }

  public HealthStatus setDependencies(List<ServiceHealth> dependencies) {
    this.dependencies = dependencies;
    return this;
  }
}
