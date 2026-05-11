// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.dal.repositories.impl;

import com.zextras.carbonio.docs_connector.cache.CacheManager;
import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OpenDocumentTokenRepositoryInMemory}. Uses real {@link CacheManager} (no
 * mocking needed — Caffeine is an in-process dependency with no external side effects).
 */
class OpenDocumentTokenRepositoryInMemoryTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-01-01T10:00:00Z");

  private OpenDocumentTokenRepositoryInMemory repository;
  private Clock fixedClock;
  private CacheManager cacheManager;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    cacheManager = new CacheManager();
    repository = new OpenDocumentTokenRepositoryInMemory(cacheManager, fixedClock);
  }

  @Test
  @DisplayName("Creating a token should store it and allow retrieval by token id")
  void givenValidInputsCreateTokenShouldStoreAndReturnRetrievableToken() {
    // Given
    UUID documentId = UUID.randomUUID();
    String requesterId = "requester-user-id";
    String cookie = "ZM_AUTH_TOKEN=abc123";

    // When
    OpenDocumentToken created = repository.createToken(documentId, requesterId, cookie);

    // Then
    Assertions.assertThat(created).isNotNull();
    Assertions.assertThat(created.getTokenId()).isNotNull();
    Assertions.assertThat(created.getDocumentId()).isEqualTo(documentId);
    Assertions.assertThat(created.getRequesterId()).isEqualTo(requesterId);
    Assertions.assertThat(created.getRequesterCookie()).contains("ZM_AUTH_TOKEN=abc123");

    // Expiration should be clock.millis() + 12 hours (as configured in CacheManager)
    long expectedExpirationMs = FIXED_NOW.toEpochMilli() + cacheManager.getTokenDurationInMs();
    Assertions.assertThat(created.getExpirationTimestamp().toEpochMilli())
        .isEqualTo(expectedExpirationMs);
  }

  @Test
  @DisplayName("getToken should return the token if it exists in cache")
  void givenACreatedTokenGetTokenShouldReturnIt() {
    // Given
    UUID documentId = UUID.randomUUID();
    OpenDocumentToken created = repository.createToken(documentId, "user-1", "ZM_AUTH_TOKEN=tok1");

    // When
    Optional<OpenDocumentToken> found = repository.getToken(created.getTokenId());

    // Then
    Assertions.assertThat(found).isPresent();
    Assertions.assertThat(found.get()).isEqualTo(created);
  }

  @Test
  @DisplayName("getToken should return empty Optional for an unknown token id")
  void givenAnUnknownTokenIdGetTokenShouldReturnEmpty() {
    // Given
    UUID unknownId = UUID.randomUUID();

    // When
    Optional<OpenDocumentToken> found = repository.getToken(unknownId);

    // Then
    Assertions.assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("Two createToken calls should produce tokens with distinct token ids")
  void givenTwoCreateTokenCallsShouldProduceDistinctTokenIds() {
    // Given
    UUID documentId = UUID.randomUUID();

    // When
    OpenDocumentToken first = repository.createToken(documentId, "user-1", "ZM_AUTH_TOKEN=tok1");
    OpenDocumentToken second = repository.createToken(documentId, "user-2", "ZM_AUTH_TOKEN=tok2");

    // Then
    Assertions.assertThat(first.getTokenId()).isNotEqualTo(second.getTokenId());
  }

  @Test
  @DisplayName("createToken should throw IllegalArgumentException when cookie lacks ZM_AUTH_TOKEN")
  void givenACookieWithoutZmAuthTokenCreateTokenShouldThrow() {
    // Given
    UUID documentId = UUID.randomUUID();
    String badCookie = "SESSION=somevalue";

    // When / Then
    Assertions.assertThatThrownBy(
            () -> repository.createToken(documentId, "user-id", badCookie))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ZM_AUTH_TOKEN");
  }

  @Test
  @DisplayName("createToken with null cookie should not throw and requesterCookie should be null")
  void givenNullCookieCreateTokenShouldSetNullRequesterCookie() {
    // Given
    UUID documentId = UUID.randomUUID();

    // When
    OpenDocumentToken created = repository.createToken(documentId, "user-id", null);

    // Then
    Assertions.assertThat(created.getRequesterCookie()).isNull();
  }
}
