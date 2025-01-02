// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.types;

import java.io.Serializable;
import java.util.UUID;

public class CreatedFile implements Serializable {

  private UUID nodeId;

  public UUID getNodeId() {
    return nodeId;
  }

  public CreatedFile setNodeId(UUID nodeId) {
    this.nodeId = nodeId;
    return this;
  }
}
