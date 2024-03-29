package com.zextras.carbonio.docs_connector.types;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NodeUpdatedTimestamp {

  private String lastModifiedTime;

  public NodeUpdatedTimestamp setLastModifiedTime(String lastModifiedTime) {
    this.lastModifiedTime = lastModifiedTime;
    return this;
  }

  @JsonProperty("LastModifiedTime")
  public String getLastModifiedTime() {
    return lastModifiedTime;
  }
}
