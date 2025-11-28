// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.apis;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.zextras.carbonio.docs_connector.Constants.Config.Files;
import com.zextras.carbonio.docs_connector.Constants.Config.UserManagement;
import com.zextras.carbonio.docs_connector.config.DocsConnectorModule;
import com.zextras.carbonio.usermanagement.entities.UserId;
import com.zextras.carbonio.usermanagement.entities.UserMyself;
import com.zextras.carbonio.usermanagement.enumerations.UserStatus;
import com.zextras.carbonio.usermanagement.enumerations.UserType;
import jakarta.ws.rs.HttpMethod;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.Server;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.ExpectationId;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;

public class Simulator implements AutoCloseable {

  private final Injector injector;

  private ClientAndServer clientAndServer;
  private MockServerClient serviceDiscoverMock;
  private MockServerClient userManagementServiceMock;
  private MockServerClient filesServiceMock;
  private Server jettyServer;
  private LocalConnector httpLocalConnector;

  private Simulator() {
    this.injector = Guice.createInjector(new DocsConnectorModule());
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
        UserManagement.DEFAULT_PORT,
        Files.DEFAULT_PORT
      );
    }
  }

  private void stopMockServer() {
    if (clientAndServer != null && clientAndServer.hasStarted()) {
      clientAndServer.stop();
    }
  }

  private void startUserManagementService() {
    startMockServer();
    userManagementServiceMock = new MockServerClient("localhost", UserManagement.DEFAULT_PORT);
    System.setProperty(UserManagement.HOST_PROPERTY, "localhost");
  }

  private void stopUserManagementService() {
    if (userManagementServiceMock != null && userManagementServiceMock.hasStarted()) {
      userManagementServiceMock.stop();
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
      jettyServer = new Server();
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

  public MockServerClient getUserManagementService() {
    return userManagementServiceMock;
  }

  public MockServerClient getFilesService() {
    return filesServiceMock;
  }

  public LocalConnector getHttpLocalConnector() {
    return httpLocalConnector;
  }

  public String mockValidateUser(String cookie, String userId) {
    return userManagementServiceMock
      .when(
        HttpRequest.request()
          .withMethod(HttpMethod.GET)
          .withPath("/auth/token/" + cookie))
      .respond(
        HttpResponse.response()
          .withStatusCode(200)
          .withBody("""
            {"userId": "%s"})
            """.formatted(userId))
      )[0].getId();
  }

  public String mockGetMyself(String cookie, String userId, String locale) {
    final UserMyself userInfo =
        new UserMyself(
            new UserId(userId),
            "fake-email@example.com",
            "Fake User",
            "example.com",
            UserStatus.ACTIVE,
            Locale.ENGLISH,
            UserType.INTERNAL,
            Map.of());

    return userManagementServiceMock
      .when(
        HttpRequest.request()
          .withMethod(HttpMethod.GET.toString())
          .withHeader("Cookie", cookie)
          .withPath("/users/myself/"))
      .respond(
        HttpResponse.response()
          .withStatusCode(200)
          .withBody(JsonBody.json(userInfo))
      )[0].getId();
  }

  public String mockServiceDiscoverConfig(String key, String value) {
    String encodedValue = new String(Base64.getEncoder().encode(value.getBytes()));
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
    stopUserManagementService();
    stopFilesService();
    stopMockServer();
  }

  @Override
  public void close() {
    stopAll();
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
      simulator.startUserManagementService();
      return this;
    }

    public SimulatorBuilder withFiles() {
      simulator.startFilesService();
      return this;
    }

    public Simulator build() {
      return simulator;
    }
  }
}
