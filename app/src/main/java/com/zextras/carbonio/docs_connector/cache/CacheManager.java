// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class CacheManager {

  private final long tokenDurationInMs = Duration.ofHours(12).toMillis();

  private final Cache<String, OpenDocumentToken> tokenCache;

  @Inject
  public CacheManager() {
    tokenCache = Caffeine
        .newBuilder()
        .expireAfterWrite(tokenDurationInMs, TimeUnit.MILLISECONDS)
        .build();
  }

  public Cache<String, OpenDocumentToken> getTokenCache() {
    return tokenCache;
  }

  public long getTokenDurationInMs() {
    return tokenDurationInMs;
  }
}
