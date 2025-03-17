// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.dal.repositories.impl;

import com.google.inject.Inject;
import com.zextras.carbonio.docs_connector.cache.CacheManager;
import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import com.zextras.carbonio.docs_connector.dal.repositories.interfaces.OpenDocumentTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenDocumentTokenRepositoryInMemory implements OpenDocumentTokenRepository {

  private final CacheManager cacheManager;
  private final Clock clock;

  @Inject
  public OpenDocumentTokenRepositoryInMemory(CacheManager cacheManager, Clock clock) {
    this.cacheManager = cacheManager;
    this.clock = clock;
  }

  @Override
  public OpenDocumentToken createToken(
    UUID documentId,
    String requesterId,
    String requesterCookie
  ) {
    // Extract only the relevant token we need
    String zmAuthTokenCookie = null;
    if (requesterCookie != null) {
        Matcher matcher = Pattern
            .compile("ZM_AUTH_TOKEN=([a-zA-Z0-9_]+)(;|$)")
            .matcher(requesterCookie);

        if (matcher.find()) {
            zmAuthTokenCookie = matcher.group();
        } else {
            throw new IllegalArgumentException("ZM_AUTH_TOKEN not found in requester cookie");
        }
    }

    OpenDocumentToken token = new OpenDocumentToken(
      UUID.randomUUID(),
      documentId,
      requesterId,
      zmAuthTokenCookie,
      Instant.ofEpochMilli(clock.millis() + cacheManager.getTokenDurationInMs())
    );

    cacheManager.getTokenCache().put(token.getTokenId().toString(), token);

    return token;
  }

  @Override
  public Optional<OpenDocumentToken> getToken(UUID tokenId) {
    return Optional.ofNullable(cacheManager.getTokenCache().getIfPresent(tokenId.toString()));
  }
}
