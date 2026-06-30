// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.docs_connector.Constants;
import com.zextras.carbonio.docs_connector.clients.UserManagementClient;
import com.zextras.carbonio.user_management.sdk.grpc.GetUserMyselfRequest;
import com.zextras.carbonio.user_management.sdk.grpc.UserInfoProto;
import com.zextras.carbonio.user_management.sdk.grpc.UserManagementServiceGrpc.UserManagementServiceBlockingStub;
import com.zextras.carbonio.user_management.sdk.grpc.UserMyselfProto;
import com.zextras.carbonio.user_management.sdk.grpc.UserMyselfResponse;
import com.zextras.carbonio.user_management.sdk.grpc.UserTypeProto;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MultivaluedMap;
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

  private UserManagementClient userManagementClient;
  private UserManagementServiceBlockingStub blockingStub;
  private CookieAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    userManagementClient = mock(UserManagementClient.class);
    blockingStub = mock(UserManagementServiceBlockingStub.class);

    when(userManagementClient.getBlockingStub()).thenReturn(blockingStub);

    // Ensure the TEST-ONLY override system property is unset by default.
    System.clearProperty(CookieAuthenticationFilter.REQUESTER_DOMAIN_OVERRIDE_PROPERTY);

    filter = new CookieAuthenticationFilter(userManagementClient);
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

  private UserMyselfResponse buildUserMyselfResponse(
      String userId, UserTypeProto type, String status, String locale) {
    UserInfoProto info = UserInfoProto.newBuilder()
        .setUserId(userId)
        .setType(type)
        .setStatus(status)
        .setDomain("example.com")
        .setFullName("Test User")
        .setEmail("test@example.com")
        .build();
    UserMyselfProto myself = UserMyselfProto.newBuilder()
        .setInfo(info)
        .setLocale(locale)
        .build();
    return UserMyselfResponse.newBuilder().setUser(myself).build();
  }

  @Test
  @DisplayName("Given a valid cookie for an active internal user the filter should set requester properties")
  void givenAValidCookieForAnActiveInternalUserTheFilterShouldSetRequesterProperties() {
    // Given
    String token = "valid-token";
    ContainerRequestContext ctx = buildFilesRequestContext(token);

    UserMyselfResponse response = buildUserMyselfResponse(
        "user-uuid-1234", UserTypeProto.INTERNAL, "active", "en_US");

    GetUserMyselfRequest expectedRequest = GetUserMyselfRequest.newBuilder()
        .setToken(token)
        .setBypassCache(true)
        .build();
    when(blockingStub.getUserMyself(expectedRequest)).thenReturn(response);

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
  void givenMissingCookieTheFilterShouldReturn401() {
    // Given
    ContainerRequestContext ctx = buildFilesRequestContext(null);

    // When
    filter.filter(ctx);

    // Then
    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(ctx).abortWith(responseCaptor.capture());
    Assertions.assertThat(responseCaptor.getValue().getStatus())
        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
    verify(blockingStub, never()).getUserMyself(any());
  }

  @Test
  @DisplayName("Given an invalid token the filter should return 401")
  void givenAnInvalidTokenTheFilterShouldReturn401() {
    // Given
    String token = "invalid-token";
    ContainerRequestContext ctx = buildFilesRequestContext(token);

    GetUserMyselfRequest expectedRequest = GetUserMyselfRequest.newBuilder()
        .setToken(token)
        .setBypassCache(true)
        .build();
    when(blockingStub.getUserMyself(expectedRequest))
        .thenThrow(new StatusRuntimeException(Status.UNAUTHENTICATED));

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
  void givenAnInactiveUserTheFilterShouldReturn401() {
    // Given
    String token = "inactive-token";
    ContainerRequestContext ctx = buildFilesRequestContext(token);

    UserMyselfResponse response = buildUserMyselfResponse(
        "inactive-user", UserTypeProto.INTERNAL, "locked", "en");
    GetUserMyselfRequest expectedRequest = GetUserMyselfRequest.newBuilder()
        .setToken(token)
        .setBypassCache(true)
        .build();
    when(blockingStub.getUserMyself(expectedRequest)).thenReturn(response);

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
  void givenAGuestUserTheFilterShouldReturn401() {
    // Given
    String token = "guest-token";
    ContainerRequestContext ctx = buildFilesRequestContext(token);

    UserMyselfResponse response = buildUserMyselfResponse(
        "guest-user", UserTypeProto.GUEST, "active", "en");
    GetUserMyselfRequest expectedRequest = GetUserMyselfRequest.newBuilder()
        .setToken(token)
        .setBypassCache(true)
        .build();
    when(blockingStub.getUserMyself(expectedRequest)).thenReturn(response);

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
  void givenANonFilesEndpointTheFilterShouldSkipAuthentication() {
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
    verify(blockingStub, never()).getUserMyself(any());
  }

  @Test
  @DisplayName("Given the test-only override system property the filter should use the override domain")
  void givenADomainOverrideSystemPropertyTheFilterShouldUseOverrideDomain() {
    // Given
    String token = "valid-token";
    ContainerRequestContext ctx = buildFilesRequestContext(token);
    String overrideDomain = "override.example.com";

    // The override is a TEST-ONLY system property, NOT a Consul KV / application-config key.
    System.setProperty(
        CookieAuthenticationFilter.REQUESTER_DOMAIN_OVERRIDE_PROPERTY, overrideDomain);

    UserMyselfResponse response = buildUserMyselfResponse(
        "user-uuid-1234", UserTypeProto.INTERNAL, "active", "pt_BR");
    GetUserMyselfRequest expectedRequest = GetUserMyselfRequest.newBuilder()
        .setToken(token)
        .setBypassCache(true)
        .build();
    when(blockingStub.getUserMyself(expectedRequest)).thenReturn(response);

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
