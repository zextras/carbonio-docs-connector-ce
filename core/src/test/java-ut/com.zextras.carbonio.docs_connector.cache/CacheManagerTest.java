package com.zextras.carbonio.docs_connector.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.zextras.carbonio.docs_connector.services.utilities.OpenDocumentToken;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class CacheManagerTest {

  @Test
  void givenACacheManagerTheGetTokenCacheShouldReturnAnEmptyCacheWithTheRightAttributes() {
    // Given & When
    CacheManager cacheManager = new CacheManager();

    // Then
    Cache<String, OpenDocumentToken> tokenCache = cacheManager.getTokenCache();
    Assertions.assertThat(tokenCache).isNotNull();
    Assertions.assertThat(tokenCache.estimatedSize()).isEqualTo(0);
    Assertions.assertThat(tokenCache.policy().expireAfterWrite()).isPresent();
    Assertions.assertThat(tokenCache.policy().expireAfterAccess()).isEmpty();
    Assertions.assertThat(tokenCache.policy().expireVariably()).isEmpty();
    Assertions.assertThat(tokenCache.policy().expireAfterWrite().get().getExpiresAfter())
      .hasMillis(Duration.ofHours(12).toMillis());
  }

  @Test
  void givenACacheManagerTheGetTokenDurationInMsShouldReturnTheRightDurationOfTheTokenInTheCache() {
    // Given && When
    CacheManager cacheManager = new CacheManager();

    // Then
    Assertions.assertThat(cacheManager.getTokenDurationInMs())
      .isEqualTo(Duration.ofHours(12).toMillis());
  }
}
