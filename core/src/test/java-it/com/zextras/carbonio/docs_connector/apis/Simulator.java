package com.zextras.carbonio.docs_connector.apis;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.zextras.carbonio.docs_connector.Constants.Config.FilesService;
import com.zextras.carbonio.docs_connector.Constants.Config.UserService;
import com.zextras.carbonio.docs_connector.config.DocsConnectorModule;
import jakarta.ws.rs.HttpMethod;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

public class Simulator implements AutoCloseable {

  private final Injector injector;

  private ClientAndServer clientAndServer;
  private MockServerClient userManagementServiceMock;
  private MockServerClient filesServiceMock;
  private Server jettyServer;
  private LocalConnector httpLocalConnector;

  private Simulator() {
    this.injector = Guice.createInjector(new DocsConnectorModule());
  }

  private void startMockServer() {
    if (clientAndServer == null) {
      clientAndServer = ClientAndServer.startClientAndServer(UserService.PORT, FilesService.PORT);
    }
  }

  private void stopMockServer() {
    if (clientAndServer != null && clientAndServer.hasStarted()) {
      clientAndServer.stop();
    }
  }

  private void startUserManagementService() {
    startMockServer();
    userManagementServiceMock = new MockServerClient(UserService.URL, UserService.PORT);
  }

  private void stopUserManagementService() {
    if (userManagementServiceMock != null && userManagementServiceMock.hasStarted()) {
      userManagementServiceMock.stop();
    }
  }

  private void startFilesService() {
    startMockServer();
    filesServiceMock = new MockServerClient(FilesService.URL, FilesService.PORT);
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

      ServletContextHandler servletContextHandler = new ServletContextHandler("/", ServletContextHandler.SESSIONS);
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

  public void mockValidateUser(String cookie, String userId) {
    userManagementServiceMock
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
      );
  }

  public void mockGetMyself(String cookie, String userId, String locale) {
    userManagementServiceMock
      .when(
        HttpRequest.request()
          .withMethod(HttpMethod.GET.toString())
          .withHeader("Cookie", cookie)
          .withPath("/users/myself/"))
      .respond(
        HttpResponse.response()
          .withStatusCode(200)
          .withBody("""
            {
                "id": {
                    "userId": "%s"
                },
                "email": "fake@example.com",
                "fullName": "Fake User",
                "domain": "https://example.com",
                "locale": "%s"
            }
            """.formatted(userId, locale))
      );

  }

  public void resetAll() {
    userManagementServiceMock.reset();
  }

  public void stopAll() {
    stopJettyServer();
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
