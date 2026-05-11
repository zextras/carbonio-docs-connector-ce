// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.docs_connector.Constants;
import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import com.zextras.carbonio.docs_connector.dal.repositories.interfaces.OpenDocumentTokenRepository;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link AccessTokenValidationFilter}. No CDI container — all dependencies injected
 * via constructor.
 */
class AccessTokenValidationFilterTest {

  private OpenDocumentTokenRepository tokenRepository;
  private Clock clock;
  private AccessTokenValidationFilter filter;

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant FUTURE = NOW.plusSeconds(3600);
  private static final Instant PAST = NOW.minusSeconds(3600);

  @BeforeEach
  void setUp() {
    tokenRepository = mock(OpenDocumentTokenRepository.class);
    clock = Clock.fixed(NOW, ZoneOffset.UTC);
    filter = new AccessTokenValidationFilter(tokenRepository, clock);
  }

  private ContainerRequestContext buildWopiRequestContext(String accessToken) {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    UriInfo uriInfo = mock(UriInfo.class);
    PathSegment segment = mock(PathSegment.class);

    when(ctx.getUriInfo()).thenReturn(uriInfo);
    when(segment.getPath()).thenReturn(Constants.DocsConnector.API.Endpoints.WOPI);
    when(uriInfo.getPathSegments()).thenReturn(List.of(segment));

    MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
    if (accessToken != null) {
      queryParams.putSingle(Constants.DocsConnector.API.Wopi.ACCESS_TOKEN_QUERY_PARAM, accessToken);
    }
    when(uriInfo.getQueryParameters()).thenReturn(queryParams);

    return ctx;
  }

  private OpenDocumentToken buildToken(UUID tokenId, Instant expiration) {
    return new OpenDocumentToken(
        tokenId,
        UUID.randomUUID(),
        "requester-id",
        "ZM_AUTH_TOKEN=abc123",
        expiration
    );
  }

  @Test
  @DisplayName("Given a valid non-expired access token the filter should set the token in context")
  void givenAValidNonExpiredTokenTheFilterShouldSetTokenInContext() {
    // Given
    UUID tokenId = UUID.randomUUID();
    OpenDocumentToken token = buildToken(tokenId, FUTURE);

    when(tokenRepository.getToken(tokenId)).thenReturn(Optional.of(token));

    ContainerRequestContext ctx = buildWopiRequestContext(tokenId.toString());

    // When
    filter.filter(ctx);

    // Then
    verify(ctx, never()).abortWith(any());
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
    verify(ctx).setProperty(keyCaptor.capture(), valueCaptor.capture());

    Assertions.assertThat(keyCaptor.getValue()).isEqualTo(Constants.Context.OPEN_DOCUMENT_TOKEN);
    Assertions.assertThat(valueCaptor.getValue()).isSameAs(token);
  }

  @Test
  @DisplayName("Given an expired access token the filter should return 401")
  void givenAnExpiredAccessTokenTheFilterShouldReturn401() {
    // Given
    UUID tokenId = UUID.randomUUID();
    OpenDocumentToken expiredToken = buildToken(tokenId, PAST);

    when(tokenRepository.getToken(tokenId)).thenReturn(Optional.of(expiredToken));

    ContainerRequestContext ctx = buildWopiRequestContext(tokenId.toString());

    // When
    filter.filter(ctx);

    // Then
    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(ctx).abortWith(responseCaptor.capture());
    Assertions.assertThat(responseCaptor.getValue().getStatus())
        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
    verify(ctx, never()).setProperty(any(), any());
  }

  @Test
  @DisplayName("Given an access token not found in repository the filter should return 401")
  void givenATokenNotFoundInRepositoryTheFilterShouldReturn401() {
    // Given
    UUID tokenId = UUID.randomUUID();
    when(tokenRepository.getToken(tokenId)).thenReturn(Optional.empty());

    ContainerRequestContext ctx = buildWopiRequestContext(tokenId.toString());

    // When
    filter.filter(ctx);

    // Then
    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(ctx).abortWith(responseCaptor.capture());
    Assertions.assertThat(responseCaptor.getValue().getStatus())
        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  @DisplayName("Given no access token query parameter the filter should return 401")
  void givenNoAccessTokenQueryParameterTheFilterShouldReturn401() {
    // Given
    ContainerRequestContext ctx = buildWopiRequestContext(null);

    // When
    filter.filter(ctx);

    // Then
    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(ctx).abortWith(responseCaptor.capture());
    Assertions.assertThat(responseCaptor.getValue().getStatus())
        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
    verify(tokenRepository, never()).getToken(any());
  }

  @Test
  @DisplayName("Given a files endpoint the filter should skip access token validation")
  void givenAFilesEndpointTheFilterShouldSkipValidation() {
    // Given
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    UriInfo uriInfo = mock(UriInfo.class);
    PathSegment segment = mock(PathSegment.class);

    when(ctx.getUriInfo()).thenReturn(uriInfo);
    when(segment.getPath()).thenReturn(Constants.DocsConnector.API.Endpoints.FILES);
    when(uriInfo.getPathSegments()).thenReturn(List.of(segment));

    // When
    filter.filter(ctx);

    // Then — no abort, no repository interaction
    verify(ctx, never()).abortWith(any());
    verify(tokenRepository, never()).getToken(any());
  }

  @Test
  @DisplayName("Given a token that expires exactly at the clock instant the filter should return 401")
  void givenATokenExpiringExactlyNowTheFilterShouldReturn401() {
    // Given — token expiration == clock time → NOT strictly greater → expired
    UUID tokenId = UUID.randomUUID();
    OpenDocumentToken tokenAtExactNow = buildToken(tokenId, NOW);

    when(tokenRepository.getToken(tokenId)).thenReturn(Optional.of(tokenAtExactNow));

    ContainerRequestContext ctx = buildWopiRequestContext(tokenId.toString());

    // When
    filter.filter(ctx);

    // Then
    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(ctx).abortWith(responseCaptor.capture());
    Assertions.assertThat(responseCaptor.getValue().getStatus())
        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }
}
