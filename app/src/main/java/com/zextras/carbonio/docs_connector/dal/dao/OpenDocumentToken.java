// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.dal.dao;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class OpenDocumentToken {

  private final UUID   tokenId;
  private final UUID   documentId;
  private final String requesterId;
  private final String requesterCookie;
  private final Instant expiresAt;

  public OpenDocumentToken(
    UUID tokenId,
    UUID documentId,
    String requesterId,
    String requesterCookie,
    Instant expirationTimestamp
  ) {
    this.tokenId = tokenId;
    this.documentId = documentId;
    this.requesterId = requesterId;
    this.requesterCookie = requesterCookie;
    this.expiresAt = expirationTimestamp;
  }

  public UUID getTokenId() {
    return tokenId;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public String getRequesterId() {
    return requesterId;
  }

  public String getRequesterCookie() {
    return requesterCookie;
  }

  public Instant getExpirationTimestamp() {
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
      && Objects.equals(requesterId, that.requesterId)
      && Objects.equals(requesterCookie, that.requesterCookie)
      && Objects.equals(expiresAt, that.expiresAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tokenId, documentId, requesterId, requesterCookie, expiresAt);
  }

  @Override
  public String toString() {
    return String.format(
      "tokenId: %s, documentId: %s, requesterId: %s, expires at %d",
      tokenId,
      documentId,
      requesterId,
      expiresAt
    );
  }
}
