package com.zextras.carbonio.docs_connector.dal.repositories.impl;

import com.google.inject.Inject;
import com.zextras.carbonio.docs_connector.cache.CacheManager;
import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import com.zextras.carbonio.docs_connector.dal.repositories.interfaces.OpenDocumentTokenRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

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
    OpenDocumentToken token = new OpenDocumentToken(
      UUID.randomUUID(),
      documentId,
      requesterId,
      requesterCookie,
      clock.millis() + cacheManager.getTokenDurationInMs()
    );

    cacheManager.getTokenCache().put(token.getTokenId().toString(), token);

    return token;
  }

  @Override
  public Optional<OpenDocumentToken> getToken(UUID tokenId) {
    return Optional.ofNullable(cacheManager.getTokenCache().getIfPresent(tokenId.toString()));
  }
}
