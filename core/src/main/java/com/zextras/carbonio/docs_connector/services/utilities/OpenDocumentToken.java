package com.zextras.carbonio.docs_connector.services.utilities;

import java.util.Objects;
import java.util.UUID;

public class OpenDocumentToken {

  private final UUID tokenId;
  private final UUID nodeId;
  private final String requesterCookies;
  private final Long   expiresAt;

  public OpenDocumentToken(
    UUID tokenId,
    UUID nodeId,
    String requesterCookies,
    Long expirationTimestamp
  ) {
    this.tokenId = tokenId;
    this.nodeId = nodeId;
    this.requesterCookies = requesterCookies;
    this.expiresAt = expirationTimestamp;
  }

  public UUID getTokenId() {
    return tokenId;
  }

  public UUID getNodeId() {
    return nodeId;
  }

  public String getRequesterCookies() {
    return requesterCookies;
  }

  public Long getExpirationTimestamp() {
    return expiresAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OpenDocumentToken that = (OpenDocumentToken) o;
    return Objects.equals(tokenId, that.tokenId)
      && Objects.equals(nodeId, that.nodeId)
      && Objects.equals(requesterCookies, that.requesterCookies)
      && Objects.equals(expiresAt, that.expiresAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tokenId, nodeId, requesterCookies, expiresAt);
  }
}
