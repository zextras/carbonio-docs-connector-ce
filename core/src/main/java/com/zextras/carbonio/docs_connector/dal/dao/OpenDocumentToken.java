package com.zextras.carbonio.docs_connector.dal.dao;

import java.util.Objects;
import java.util.UUID;

public class OpenDocumentToken {

  private final UUID   tokenId;
  private final UUID   documentId;
  private final String requesterCookie;
  private final Long   expiresAt;

  public OpenDocumentToken(
    UUID tokenId,
    UUID documentId,
    String requesterCookie,
    Long expirationTimestamp
  ) {
    this.tokenId = tokenId;
    this.documentId = documentId;
    this.requesterCookie = requesterCookie;
    this.expiresAt = expirationTimestamp;
  }

  public UUID getTokenId() {
    return tokenId;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public String getRequesterCookie() {
    return requesterCookie;
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
      && Objects.equals(documentId, that.documentId)
      && Objects.equals(requesterCookie, that.requesterCookie)
      && Objects.equals(expiresAt, that.expiresAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tokenId, documentId, requesterCookie, expiresAt);
  }

  @Override
  public String toString() {
    return String.format(
      "tokenId: %s, documentId: %s, expires at %d",
      tokenId,
      documentId,
      expiresAt
    );
  }
}
