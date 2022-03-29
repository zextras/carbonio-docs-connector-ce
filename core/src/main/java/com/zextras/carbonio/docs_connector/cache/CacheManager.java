package com.zextras.carbonio.docs_connector.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zextras.carbonio.docs_connector.services.utilities.OpenDocumentToken;
import java.util.concurrent.TimeUnit;

@Singleton
public class CacheManager {

  private final long tokenDurationInMs = 1000L * 60L * 60L * 12;

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
