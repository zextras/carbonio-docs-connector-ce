// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.docs_connector.Constants;
import com.zextras.carbonio.user_management.sdk.rest.ApiException;
import com.zextras.carbonio.user_management.sdk.rest.api.UserResourceApi;
import com.zextras.carbonio.user_management.sdk.rest.model.MyselfDto;
import com.zextras.carbonio.user_management.sdk.rest.model.UserInfoDto;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link CookieAuthenticationFilter}. No CDI container is started — all
 * dependencies are provided via constructor injection.
 */
class CookieAuthenticationFilterTest {

  private UserResourceApi userResourceApi;
  private CookieAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    userResourceApi = mock(UserResourceApi.class);

    // Ensure the TEST-ONLY override system property is unset by default.
    System.clearProperty(CookieAuthenticationFilter.REQUESTER_DOMAIN_OVERRIDE_PROPERTY);

    filter = new CookieAuthenticationFilter(userResourceApi);
  }

  @AfterEach
  void tearDown() {
    // Clean up the test-only override so it never leaks into other tests.
    System.clearProperty(CookieAuthenticationFilter.REQUESTER_DOMAIN_OVERRIDE_PROPERTY);
  }

  private ContainerRequestContext buildFilesRequestContext(String cookieValue) {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    UriInfo uriInfo = mock(UriInfo.class);
    jakarta.ws.rs.core.PathSegment segment = mock(jakarta.ws.rs.core.PathSegment.class);

    when(ctx.getUriInfo()).thenReturn(uriInfo);
    when(segment.getPath()).thenReturn(Constants.DocsConnector.API.Endpoints.FILES);
    when(uriInfo.getPathSegments()).thenReturn(List.of(segment));

    if (cookieValue != null) {
      Cookie cookie = new Cookie.Builder(Constants.Config.ACCEPTED_COOKIE_TYPE)
          .value(cookieValue)
          .build();
      when(ctx.getCookies()).thenReturn(Map.of(Constants.Config.ACCEPTED_COOKIE_TYPE, cookie));
    } else {
      when(ctx.getCookies()).thenReturn(Map.of());
    }

    return ctx;
  }

  private MyselfDto buildUserMyself(String userId, String type, String status, String locale) {
    UserInfoDto info = new UserInfoDto()
        .userId(userId)
        .type(type)
        .status(status)
        .domain("example.com")
        .fullName("Test User")
        .email("test@example.com");
    return new MyselfDto().info(info).locale(locale);
  }

  @Test
  @DisplayName("Given a valid cookie for an active internal user the filter should set requester properties")
  void givenAValidCookieForAnActiveInternalUserTheFilterShouldSetRequesterProperties()
      throws Exception {
    // Given
    String token = "valid-token";
    ContainerRequestContext ctx = buildFilesRequestContext(token);

    MyselfDto response = buildUserMyself("user-uuid-1234", "INTERNAL", "active", "en_US");
    when(userResourceApi.internalUsersMyselfGet(token)).thenReturn(response);

    // When
    filter.filter(ctx);

    // Then — filter sets REQUESTER_COOKIE, REQUESTER_ID, REQUESTER_DOMAIN, REQUESTER_LOCALE
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
    verify(ctx, org.mockito.Mockito.atLeastOnce()).setProperty(keyCaptor.capture(), valueCaptor.capture());
    verify(ctx, never()).abortWith(any());

    // Verify all required properties are set
    Assertions.assertThat(keyCaptor.getAllValues()).contains(Constants.Context.REQUESTER_ID);
    Assertions.assertThat(keyCaptor.getAllValues()).contains(Constants.Context.REQUESTER_COOKIE);
    Assertions.assertThat(keyCaptor.getAllValues()).contains(Constants.Context.REQUESTER_DOMAIN);
    Assertions.assertThat(keyCaptor.getAllValues()).contains(Constants.Context.REQUESTER_LOCALE);
  }

  @Test
  @DisplayName("Given a missing cookie the filter should return 401")
  void givenMissingCookieTheFilterShouldReturn401() throws Exception {
    // Given
    ContainerRequestContext ctx = buildFilesRequestContext(null);

    // When
    filter.filter(ctx);

    // Then
    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(ctx).abortWith(responseCaptor.capture());
    Assertions.assertThat(responseCaptor.getValue().getStatus())
        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
    verify(userResourceApi, never()).internalUsersMyselfGet(any());
  }

  @Test
  @DisplayName("Given an invalid token the filter should return 401")
  void givenAnInvalidTokenTheFilterShouldReturn401() throws Exception {
    // Given
    String token = "invalid-token";
    ContainerRequestContext ctx = buildFilesRequestContext(token);

    when(userResourceApi.internalUsersMyselfGet(token))
        .thenThrow(new ApiException(401, "Unauthorized"));

    // When
    filter.filter(ctx);

    // Then
    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(ctx).abortWith(responseCaptor.capture());
    Assertions.assertThat(responseCaptor.getValue().getStatus())
        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  @DisplayName("Given user-management is unreachable (ApiException getCode()==0) the filter should return 503")
  void givenUserManagementUnreachableTheFilterShouldReturn503() throws Exception {
    // Given — ApiException(Throwable) never sets a code (connection refused / timeout / a body
    // that failed to deserialize), so getCode() == 0. This must NOT be reported as an invalid
    // cookie (401): it would cause a spurious client-side logout / re-auth loop.
    String token = "any-token";
    ContainerRequestContext ctx = buildFilesRequestContext(token);

    when(userResourceApi.internalUsersMyselfGet(token))
        .thenThrow(new ApiException(new java.io.IOException("connection refused")));

    // When
    filter.filter(ctx);

    // Then
    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(ctx).abortWith(responseCaptor.capture());
    Assertions.assertThat(responseCaptor.getValue().getStatus())
        .isEqualTo(Response.Status.SERVICE_UNAVAILABLE.getStatusCode());
  }

  @Test
  @DisplayName("Given user-management returns a 5xx the filter should return 503")
  void givenUserManagement5xxTheFilterShouldReturn503() throws Exception {
    // Given
    String token = "any-token";
    ContainerRequestContext ctx = buildFilesRequestContext(token);

    when(userResourceApi.internalUsersMyselfGet(token))
        .thenThrow(new ApiException(502, "Bad Gateway"));

    // When
    filter.filter(ctx);

    // Then
    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(ctx).abortWith(responseCaptor.capture());
    Assertions.assertThat(responseCaptor.getValue().getStatus())
        .isEqualTo(Response.Status.SERVICE_UNAVAILABLE.getStatusCode());
  }

  @Test
  @DisplayName("Given user-management returns a null info (blank 2xx body) the filter should return 401, not 500")
  void givenNullInfoTheFilterShouldReturn401() throws Exception {
    // Given — the generated client returns null outright for a 2xx response with a blank body;
    // under gRPC this shape was impossible (proto3 defaults a missing sub-message to a non-null,
    // empty instance), so a degenerate response here must yield the same clean 401 outcome, not
    // an NPE surfacing as a 500.
    String token = "valid-token";
    ContainerRequestContext ctx = buildFilesRequestContext(token);

    MyselfDto response = new MyselfDto().info(null).locale("en");
    when(userResourceApi.internalUsersMyselfGet(token)).thenReturn(response);

    // When
    filter.filter(ctx);

    // Then
    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(ctx).abortWith(responseCaptor.capture());
    Assertions.assertThat(responseCaptor.getValue().getStatus())
        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  @DisplayName("Given user-management returns a null MyselfDto (blank 2xx body) the filter should return 401, not 500")
  void givenNullMyselfTheFilterShouldReturn401() throws Exception {
    // Given
    String token = "valid-token";
    ContainerRequestContext ctx = buildFilesRequestContext(token);

    when(userResourceApi.internalUsersMyselfGet(token)).thenReturn(null);

    // When
    filter.filter(ctx);

    // Then
    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(ctx).abortWith(responseCaptor.capture());
    Assertions.assertThat(responseCaptor.getValue().getStatus())
        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  @DisplayName("Given an inactive user the filter should return 401")
  void givenAnInactiveUserTheFilterShouldReturn401() throws Exception {
    // Given
    String token = "inactive-token";
    ContainerRequestContext ctx = buildFilesRequestContext(token);

    MyselfDto response = buildUserMyself("inactive-user", "INTERNAL", "locked", "en");
    when(userResourceApi.internalUsersMyselfGet(token)).thenReturn(response);

    // When
    filter.filter(ctx);

    // Then
    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(ctx).abortWith(responseCaptor.capture());
    Assertions.assertThat(responseCaptor.getValue().getStatus())
        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  @DisplayName("Given a guest (external) user the filter should return 401")
  void givenAGuestUserTheFilterShouldReturn401() throws Exception {
    // Given
    String token = "guest-token";
    ContainerRequestContext ctx = buildFilesRequestContext(token);

    MyselfDto response = buildUserMyself("guest-user", "GUEST", "active", "en");
    when(userResourceApi.internalUsersMyselfGet(token)).thenReturn(response);

    // When
    filter.filter(ctx);

    // Then
    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(ctx).abortWith(responseCaptor.capture());
    Assertions.assertThat(responseCaptor.getValue().getStatus())
        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  @DisplayName("Given a non-files endpoint the filter should skip authentication entirely")
  void givenANonFilesEndpointTheFilterShouldSkipAuthentication() throws Exception {
    // Given
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    UriInfo uriInfo = mock(UriInfo.class);
    jakarta.ws.rs.core.PathSegment segment = mock(jakarta.ws.rs.core.PathSegment.class);

    when(ctx.getUriInfo()).thenReturn(uriInfo);
    when(segment.getPath()).thenReturn(Constants.DocsConnector.API.Endpoints.WOPI);
    when(uriInfo.getPathSegments()).thenReturn(List.of(segment));

    // When
    filter.filter(ctx);

    // Then — no abort, no interaction with user-management
    verify(ctx, never()).abortWith(any());
    verify(userResourceApi, never()).internalUsersMyselfGet(any());
  }

  @Test
  @DisplayName("Given the test-only override system property the filter should use the override domain")
  void givenADomainOverrideSystemPropertyTheFilterShouldUseOverrideDomain() throws Exception {
    // Given
    String token = "valid-token";
    ContainerRequestContext ctx = buildFilesRequestContext(token);
    String overrideDomain = "override.example.com";

    // The override is a TEST-ONLY system property, NOT a Consul KV / application-config key.
    System.setProperty(
        CookieAuthenticationFilter.REQUESTER_DOMAIN_OVERRIDE_PROPERTY, overrideDomain);

    MyselfDto response = buildUserMyself("user-uuid-1234", "INTERNAL", "active", "pt_BR");
    when(userResourceApi.internalUsersMyselfGet(token)).thenReturn(response);

    // When
    filter.filter(ctx);

    // Then — abortWith should NOT be called, domain override should be set
    verify(ctx, never()).abortWith(any());

    // Capture all setProperty calls and verify domain override is used
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
    verify(ctx, org.mockito.Mockito.atLeastOnce()).setProperty(keyCaptor.capture(), valueCaptor.capture());

    int domainIdx = keyCaptor.getAllValues().indexOf(Constants.Context.REQUESTER_DOMAIN);
    Assertions.assertThat(domainIdx).isGreaterThanOrEqualTo(0);
    Assertions.assertThat(valueCaptor.getAllValues().get(domainIdx)).isEqualTo(overrideDomain);
  }
}
