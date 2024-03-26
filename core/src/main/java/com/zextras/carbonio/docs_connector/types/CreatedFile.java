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
