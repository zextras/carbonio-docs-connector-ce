package com.zextras.carbonio.docs_connector.apis;

import com.zextras.carbonio.docs_connector.apis.Simulator.SimulatorBuilder;
import com.zextras.carbonio.docs_connector.entities.files.graphql.NodeAttributes;
import com.zextras.carbonio.usermanagement.entities.Locale;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.http.HttpTester.Response;
import org.eclipse.jetty.server.LocalConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.Cookie;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

public class OpenFileApiIT {

  static Simulator simulator;
  static MockServerClient filesServiceMock;

  @BeforeAll
  static void init() {
    simulator = SimulatorBuilder
      .aSimulator()
      .init()
      .withUserManagement()
      .withFiles()
      .build()
      .start();

    filesServiceMock = simulator.getFilesService();
  }

  @AfterEach
  void cleanUp() {
    simulator.resetAll();
  }

  @AfterAll
  static void cleanUpAll() {
    simulator.stopAll();
  }

  @Test
  void givenAValidUserCookieAndAnAccessibleNodeIdTheOpenFileApiShouldReturnARedirectContainingTheUrlToOpenTheDocumentWithDocs()
    throws Exception {
    // Given
    String userCookie = "ZM_AUTH_TOKEN=9e2cffc4";
    String nodeId = "58032253-ed56-4eca-9017-3ae26cc2d9f1";

    simulator.mockValidateUser("9e2cffc4", "9e2cffc4-5860-4095-aedb-7b48d6ff889a");
    simulator.mockGetMyself(userCookie, "9e2cffc4-5860-4095-aedb-7b48d6ff889a", Locale.EN.name());

    filesServiceMock
      .when(HttpRequest
        .request("/graphql/")
        .withCookie(Cookie.cookie("ZM_AUTH_TOKEN", "9e2cffc4"))
        .withBody(NodeAttributes.getNodeGraphQLRequest(nodeId, Optional.empty()))
      )
      .respond(HttpResponse
        .response()
        .withStatusCode(HttpStatus.OK_200)
        .withBody("""
          {
              "data": {
                  "getNode": {
                      "permissions": {
                          "can_write_file": true
                      },
                      "owner": {
                          "id": "9e2cffc4-5860-4095-aedb-7b48d6ff889a",
                          "full_name": "Fake user"
                      },
                      "parent": {
                          "id": "LOCAL_ROOT"
                      },
                      "id": "58032253-ed56-4eca-9017-3ae26cc2d9f1",
                      "name": "test-file",
                      "updated_at": 100,
                      "extension": "odt",
                      "mime_type": "application/vnd.oasis.opendocument.text",
                      "size": 10.0,
                      "version": 1
                  }
              }
          }
          """)
      );

    LocalConnector httpLocalConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeader.HOST.toString(), "test");
    request.setHeader(HttpHeader.COOKIE.toString(), userCookie);
    request.setURI("/files/open/%s".formatted(nodeId));

    // When
    Response response = HttpTester.parseResponse(
      httpLocalConnector.getResponse(request.generate())
    );

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT_307);
    Assertions
      .assertThat(response.get(HttpHeader.LOCATION))
      .contains("/editor/browser/dist/cool.html")
      .contains("?access_token=")
      .contains("&access_token_ttl=")
      .contains("&title=test-file")
      .contains("&ui_defaults=UIMode=classic;UIMode=classic;TextSidebar=false;PresentationSidebar=false;SpreadsheetSidebar=false")
      .contains("&WOPISrc")
      .contains(nodeId)
      .contains("&public_url=docs%2Feditor%2F58032253-ed56-4eca-9017-3ae26cc2d9f1")
      .contains("&lang=EN");
  }
}
