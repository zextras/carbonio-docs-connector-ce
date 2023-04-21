package com.zextras.carbonio.docs_connector.dal.repositories.interfaces;

import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import java.util.Optional;
import java.util.UUID;

public interface OpenDocumentTokenRepository {

  OpenDocumentToken createToken(
    UUID documentId,
    String requesterId,
    String requesterCookie
  );

  Optional<OpenDocumentToken> getToken(UUID tokenId);
}
