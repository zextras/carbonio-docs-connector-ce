// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.apis;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import com.zextras.carbonio.docs_connector.Constants.Config.Files;
import com.zextras.carbonio.docs_connector.config.DocsConnectorModule;
import com.zextras.carbonio.user_management.sdk.grpc.GetUserByEmailRequest;
import com.zextras.carbonio.user_management.sdk.grpc.GetUserByIdRequest;
import com.zextras.carbonio.user_management.sdk.grpc.GetUserMyselfRequest;
import com.zextras.carbonio.user_management.sdk.grpc.UserInfoProto;
import com.zextras.carbonio.user_management.sdk.grpc.UserInfoResponse;
import com.zextras.carbonio.user_management.sdk.grpc.UserManagementServiceGrpc;
import com.zextras.carbonio.user_management.sdk.grpc.UserManagementServiceGrpc.UserManagementServiceBlockingStub;
import com.zextras.carbonio.user_management.sdk.grpc.UserManagementServiceGrpc.UserManagementServiceImplBase;
import com.zextras.carbonio.user_management.sdk.grpc.UserMyselfProto;
import com.zextras.carbonio.user_management.sdk.grpc.UserMyselfResponse;
import com.zextras.carbonio.user_management.sdk.grpc.UserTypeProto;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.ws.rs.HttpMethod;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.LocalConnector;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.ExpectationId;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

public class Simulator implements AutoCloseable {

  private static final String UM_INPROCESS_NAME = "um-docs-connector-ce-test";

  private Injector injector;

  private ClientAndServer clientAndServer;
  private MockServerClient serviceDiscoverMock;
  private MockServerClient filesServiceMock;
  private org.eclipse.jetty.server.Server jettyServer;
  private LocalConnector httpLocalConnector;

  // gRPC in-process UM mock
  private MockUserManagementService mockUmService;
  private ManagedChannel umChannel;
  private Server umGrpcServer;

  private Simulator() {}

  private void createInjector() {
    if (umChannel == null) {
      umChannel = InProcessChannelBuilder.forName(UM_INPROCESS_NAME).directExecutor().build();
    }
    if (mockUmService == null) {
      mockUmService = new MockUserManagementService();
    }

    AbstractModule umOverride = new AbstractModule() {
      @Provides
      @Singleton
      public ManagedChannel provideUserManagementChannel() {
        return umChannel;
      }

      @Provides
      @Singleton
      public UserManagementServiceBlockingStub provideUserManagementStub() {
        return UserManagementServiceGrpc.newBlockingStub(umChannel);
      }
    };

    injector = Guice.createInjector(
        Modules.override(new DocsConnectorModule()).with(umOverride)
    );
  }

  private Simulator startServiceDiscover() {

    if (serviceDiscoverMock != null && serviceDiscoverMock.hasStarted()) {
      return this;
    }

    startMockServer();
    serviceDiscoverMock = new MockServerClient("localhost", 8500);

    serviceDiscoverMock
      .when(
        HttpRequest.request()
          .withMethod(HttpMethod.GET)
          .withPath("/v1/status/leader"))
      .respond(
        HttpResponse.response()
          .withStatusCode(200));

    return this;
  }

  private void stopServiceDiscover() {
    if (serviceDiscoverMock != null && serviceDiscoverMock.hasStarted()) {
      serviceDiscoverMock.stop();
    }
  }

  private void startMockServer() {
    if (clientAndServer == null) {
      clientAndServer = ClientAndServer.startClientAndServer(
        8500,
        Files.DEFAULT_PORT
      );
    }
  }

  private void stopMockServer() {
    if (clientAndServer != null && clientAndServer.hasStarted()) {
      clientAndServer.stop();
    }
  }

  private void startUserManagement() {
    mockUmService = new MockUserManagementService();
    umChannel = InProcessChannelBuilder.forName(UM_INPROCESS_NAME).directExecutor().build();

    try {
      umGrpcServer =
          InProcessServerBuilder.forName(UM_INPROCESS_NAME)
              .directExecutor()
              .addService(mockUmService)
              .build()
              .start();
    } catch (IOException e) {
      throw new RuntimeException("Failed to start gRPC InProcessServer for UM", e);
    }
  }

  private void stopUserManagement() {
    if (umGrpcServer != null) {
      umGrpcServer.shutdownNow();
      umGrpcServer = null;
    }
    if (umChannel != null) {
      umChannel.shutdownNow();
      umChannel = null;
    }
  }

  /**
   * Shuts down the UM gRPC InProcess server to simulate UM being unreachable.
   * The channel will transition to SHUTDOWN state, causing health checks to
   * report UM as unhealthy.
   */
  public void shutdownUserManagementServer() {
    if (umGrpcServer != null) {
      umGrpcServer.shutdownNow();
      try {
        umGrpcServer.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      umGrpcServer = null;
    }
    if (umChannel != null) {
      umChannel.shutdownNow();
    }
  }

  private void startFilesService() {
    startMockServer();
    filesServiceMock = new MockServerClient("localhost", Files.DEFAULT_PORT);
    System.setProperty(Files.HOST_PROPERTY, "localhost");
  }

  private void stopFilesService() {
    if (filesServiceMock != null && filesServiceMock.hasStarted()) {
      filesServiceMock.stop();
    }
  }

  private void startJettyServer() {
    try {
      jettyServer = new org.eclipse.jetty.server.Server();
      httpLocalConnector = new LocalConnector(jettyServer);
      jettyServer.addConnector(httpLocalConnector);

      ServletContextHandler servletContextHandler = new ServletContextHandler("/",
        ServletContextHandler.SESSIONS);
      ServletHolder servletHolder = new ServletHolder(HttpServletDispatcher.class);

      servletContextHandler.addEventListener(injector.getInstance(
        GuiceResteasyBootstrapServletContextListener.class)
      );
      servletContextHandler.addServlet(servletHolder, "/*");
      jettyServer.setHandler(servletContextHandler);

      jettyServer.start();
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  private void stopJettyServer() {
    if (jettyServer != null) {
      try {
        jettyServer.stop();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  public Simulator start() {
    startJettyServer();
    return this;
  }

  public Injector getInjector() {
    return injector;
  }

  public MockUserManagementService getUserManagementService() {
    return mockUmService;
  }

  public MockServerClient getFilesService() {
    return filesServiceMock;
  }

  public LocalConnector getHttpLocalConnector() {
    return httpLocalConnector;
  }

  public String mockValidateUser(String token, String userId) {
    mockUmService.registerToken(token, userId);
    return userId;
  }

  public String mockGetMyself(String cookie, String userId, String locale) {
    mockUmService.registerToken(cookie, userId);
    return userId;
  }

  public String mockServiceDiscoverConfig(String key, String value) {
    String encodedValue = new String(java.util.Base64.getEncoder().encode(value.getBytes()));
    String bodyPayload = """
        [
          {
            "Key":"carbonio-docs-connector/%s",
            "Value":"%s",
            "CreateIndex": 0,
            "ModifyIndex": 0,
            "LockIndex": 0,
            "Flags": 0
          }
        ]
      """.formatted(key, encodedValue);

    return serviceDiscoverMock
      .when(
        HttpRequest.request()
          .withMethod(HttpMethod.GET)
          .withPath("/v1/kv/carbonio-docs-connector%2F" + key.replaceAll("/", "%2F"))
          .withHeader("X-Consul-Token", ""))
      .respond(
        HttpResponse.response()
          .withStatusCode(200)
          .withBody(bodyPayload)
      )[0].getId();
  }

  public void resetAllExpectations(List<String> expectationIds) {
    if (clientAndServer != null && clientAndServer.hasStarted()) {
      expectationIds.forEach(id -> clientAndServer.clear(ExpectationId.expectationId(id)));
    }
  }

  public void stopAll() {
    stopJettyServer();
    stopServiceDiscover();
    stopUserManagement();
    stopFilesService();
    stopMockServer();
  }

  @Override
  public void close() {
    stopAll();
  }

  /**
   * In-memory gRPC service implementation for UserManagement.
   */
  public static class MockUserManagementService extends UserManagementServiceImplBase {

    private final Map<String, UserMyselfResponse> tokenToMyself = new ConcurrentHashMap<>();
    private final Map<String, UserInfoProto> userIdToInfo = new ConcurrentHashMap<>();

    void registerToken(String token, String userId) {
      if (!tokenToMyself.containsKey(token)) {
        UserInfoProto info = UserInfoProto.newBuilder()
            .setUserId(userId)
            .setEmail("fake-email@example.com")
            .setFullName("Fake User")
            .setDomain("example.com")
            .setStatus("active")
            .setType(UserTypeProto.INTERNAL)
            .build();
        UserMyselfProto myself = UserMyselfProto.newBuilder()
            .setInfo(info)
            .setLocale("en")
            .addFeatures("carbonioFeatureDocsEnabled")
            .build();
        tokenToMyself.put(token, UserMyselfResponse.newBuilder().setUser(myself).build());
        userIdToInfo.put(userId, info);
      }
    }

    public void registerUserById(String userId, String email, String fullName,
        String domain, String status) {
      UserInfoProto info = UserInfoProto.newBuilder()
          .setUserId(userId)
          .setEmail(email)
          .setFullName(fullName)
          .setDomain(domain)
          .setStatus(status)
          .setType(UserTypeProto.INTERNAL)
          .build();
      userIdToInfo.put(userId, info);
    }

    @Override
    public void getUserMyself(GetUserMyselfRequest request,
        StreamObserver<UserMyselfResponse> responseObserver) {
      String token = request.getToken();
      UserMyselfResponse response = tokenToMyself.get(token);
      if (response != null) {
        responseObserver.onNext(response);
        responseObserver.onCompleted();
      } else {
        responseObserver.onError(
            Status.UNAUTHENTICATED.withDescription("Invalid token").asRuntimeException());
      }
    }

    @Override
    public void getUserById(GetUserByIdRequest request,
        StreamObserver<UserInfoResponse> responseObserver) {
      String userId = request.getUserId();
      UserInfoProto info = userIdToInfo.get(userId);
      if (info != null) {
        responseObserver.onNext(UserInfoResponse.newBuilder().setUser(info).build());
        responseObserver.onCompleted();
      } else {
        responseObserver.onError(
            Status.NOT_FOUND.withDescription("User not found").asRuntimeException());
      }
    }

    @Override
    public void getUserByEmail(GetUserByEmailRequest request,
        StreamObserver<UserInfoResponse> responseObserver) {
      String email = request.getUserEmail();
      for (UserInfoProto info : userIdToInfo.values()) {
        if (info.getEmail().equals(email)) {
          responseObserver.onNext(UserInfoResponse.newBuilder().setUser(info).build());
          responseObserver.onCompleted();
          return;
        }
      }
      responseObserver.onError(
          Status.NOT_FOUND.withDescription("User not found by email").asRuntimeException());
    }
  }

  public static class SimulatorBuilder {

    private Simulator simulator;

    public static SimulatorBuilder aSimulator() {
      return new SimulatorBuilder();
    }

    public SimulatorBuilder init() {
      simulator = new Simulator();
      return this;
    }

    public SimulatorBuilder withServiceDiscover() {
      simulator.startServiceDiscover();
      return this;
    }

    public SimulatorBuilder withUserManagement() {
      simulator.startUserManagement();
      return this;
    }

    public SimulatorBuilder withFiles() {
      simulator.startFilesService();
      return this;
    }

    public Simulator build() {
      simulator.createInjector();
      return simulator;
    }
  }
}
