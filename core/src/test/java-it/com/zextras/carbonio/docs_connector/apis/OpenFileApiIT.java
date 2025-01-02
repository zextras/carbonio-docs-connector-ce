// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.apis;

import com.zextras.carbonio.docs_connector.apis.Simulator.SimulatorBuilder;
import com.zextras.carbonio.docs_connector.entities.files.graphql.NodeAttributes;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.http.HttpTester.Response;
import org.eclipse.jetty.server.LocalConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.model.Cookie;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

public class OpenFileApiIT {

  static Simulator simulator;
  static List<String> expectationIds;
  static String filesGetNodeResponseFormat = """
              {
              "data": {
                  "getNode": {
                      "permissions": {
                          "can_write_file": true
                      },
                      "owner": {
                          "id": "%s",
                          "full_name": "Fake user"
                      },
                      "parent": {
                          "id": "LOCAL_ROOT"
                      },
                      "id": "%s",
                      "name": "test-file",
                      "updated_at": 100,
                      "extension": "%s",
                      "mime_type": "%s",
                      "size": %d,
                      "version": 1
                  }
              }
          }
    """;

  @BeforeAll
  static void init() {
    simulator = SimulatorBuilder
      .aSimulator()
      .init()
      .withServiceDiscover()
      .withUserManagement()
      .withFiles()
      .build()
      .start();

    expectationIds = new ArrayList<>();
  }

  @AfterEach
  void cleanUp() {
    simulator.resetAllExpectations(expectationIds);
  }

  @AfterAll
  static void cleanUpAll() {
    simulator.stopAll();
  }

  static Stream<Arguments> maxFileSizeProvider() {
    long megaBytes = 1024 * 1024;
    long elevenMbInBytes = 11L * megaBytes;
    long ninetyOneMbInBytes = 91L * megaBytes;
    long threeMbInBytes = 3L * megaBytes;

    return Stream.of(
      Arguments.of(
        "document",
        10L,
        "application/vnd.oasis.opendocument.text", "odt",
        elevenMbInBytes
      ),
      Arguments.of(
        "presentation",
        90L,
        "application/vnd.ms-powerpoint",
        "ppt",
        ninetyOneMbInBytes
      ),
      Arguments.of(
        "spreadsheet",
        2L,
        "application/vnd.oasis.opendocument.spreadsheet",
        "ods",
        threeMbInBytes
      )
    );
  }

  @DisplayName("Given a valid user cookie and an accessible node id the openFile API should"
    + "return a 200 and a payload containing the URL of the document to open with Docs")
  @Test
  void givenAValidUserCookieAndAnAccessibleNodeIdTheOpenFileApiShouldReturnA200ContainingTheUrlToOpenTheDocumentWithDocs()
    throws Exception {
    // Given
    String userCookie = "ZM_AUTH_TOKEN=9e2cffc4";
    String requesterId = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";
    String nodeId = "58032253-ed56-4eca-9017-3ae26cc2d9f1";

    expectationIds.add(simulator.mockValidateUser("9e2cffc4", requesterId));
    expectationIds.add(simulator.mockGetMyself(userCookie, requesterId, "pt_BR"));

    expectationIds.add(simulator.getFilesService()
      .when(HttpRequest
        .request("/graphql/")
        .withCookie(Cookie.cookie("ZM_AUTH_TOKEN", "9e2cffc4"))
        .withBody(NodeAttributes.getNodeGraphQLRequest(nodeId, Optional.empty()))
      )
      .respond(HttpResponse
        .response()
        .withStatusCode(HttpStatus.OK_200)
        .withBody(filesGetNodeResponseFormat.formatted(
          requesterId,
          nodeId,
          "odt",
          "application/vnd.oasis.opendocument.text",
          52428800
        ))
      )[0].getId()
    );

    LocalConnector httpLocalConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeaders.HOST.toString(), "test");
    request.setHeader(HttpHeaders.COOKIE.toString(), userCookie);
    request.setURI("/files/open/%s".formatted(nodeId));

    // When
    Response response = HttpTester.parseResponse(
      httpLocalConnector.getResponse(request.generate())
    );

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);
    Assertions
      .assertThat(response.getContent())
      .contains("/services/docs/editor/browser/dist/cool.html")
      .contains("?access_token=")
      .contains("&access_token_ttl=")
      .contains("&title=test-file")
      .contains(
        "&ui_defaults=UIMode=classic;UIMode=classic;TextSidebar=false;PresentationSidebar=false;SpreadsheetSidebar=false")
      .contains("&WOPISrc")
      .contains(nodeId)
      .contains("&public_url=services%2Fdocs%2Ffiles%2Fopen%2F58032253-ed56-4eca-9017-3ae26cc2d9f1%3Fredirect%3Dtrue")
      .contains("&lang=pt-BR");
  }

  @DisplayName("Given a valid user cookie and an accessible node id the openFile API with the "
    + "redirect query params to true should return a redirect containing the URL of the document "
    + "to open with Docs")
  @Test
  void givenAValidUserCookieAndAnAccessibleNodeIdTheOpenFileApiShouldReturnARedirectContainingTheUrlToOpenTheDocumentWithDocs()
    throws Exception {
    // Given
    String userCookie = "ZM_AUTH_TOKEN=9e2cffc4";
    String requesterId = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";
    String nodeId = "58032253-ed56-4eca-9017-3ae26cc2d9f1";

    expectationIds.add(simulator.mockValidateUser("9e2cffc4", requesterId));
    expectationIds.add(simulator.mockGetMyself(userCookie, requesterId, "pt_BR"));

    expectationIds.add(simulator.getFilesService()
      .when(HttpRequest
        .request("/graphql/")
        .withCookie(Cookie.cookie("ZM_AUTH_TOKEN", "9e2cffc4"))
        .withBody(NodeAttributes.getNodeGraphQLRequest(nodeId, Optional.empty()))
      )
      .respond(HttpResponse
        .response()
        .withStatusCode(HttpStatus.OK_200)
        .withBody(filesGetNodeResponseFormat.formatted(
          requesterId,
          nodeId,
          "odt",
          "application/vnd.oasis.opendocument.text",
          20
        ))
      )[0].getId()
    );

    LocalConnector httpLocalConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeaders.HOST.toString(), "test");
    request.setHeader(HttpHeaders.COOKIE.toString(), userCookie);
    request.setURI("/files/open/%s?redirect=true".formatted(nodeId));

    // When
    Response response = HttpTester.parseResponse(
      httpLocalConnector.getResponse(request.generate())
    );

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT_307);
    Assertions
      .assertThat(response.get(HttpHeaders.LOCATION))
      .contains("/services/docs/editor/browser/dist/cool.html");
  }

  @DisplayName("Given a valid cookie of a user and an invalid node id, "
    + "the openFile API should return a 404 status code")
  @Test
  void givenAValidUserCookieAndAnInvalidNodeIdTheOpenFileApiShouldReturnANotFoundStatusCode()
    throws Exception {
    // Given
    String userCookie = "ZM_AUTH_TOKEN=9e2cffc4";
    String requesterId = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";
    String nodeId = "58032253-ed56-4eca-9017-3ae26cc2d9f1";

    expectationIds.add(simulator.mockValidateUser("9e2cffc4", requesterId));
    expectationIds.add(simulator.mockGetMyself(userCookie, requesterId, "pt_BR"));
    expectationIds.add(simulator.getFilesService()
      .when(HttpRequest
        .request("/graphql/")
        .withCookie(Cookie.cookie("ZM_AUTH_TOKEN", "9e2cffc4"))
        .withBody(NodeAttributes.getNodeGraphQLRequest(nodeId, Optional.empty()))
      )
      .respond(HttpResponse
        .response()
        .withStatusCode(HttpStatus.NOT_FOUND_404)
      )[0].getId()
    );

    LocalConnector httpLocalConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeaders.HOST.toString(), "test");
    request.setHeader(HttpHeaders.COOKIE.toString(), userCookie);
    request.setURI("/files/open/%s".formatted(nodeId));

    // When
    Response response = HttpTester.parseResponse(
      httpLocalConnector.getResponse(request.generate())
    );

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);
  }

  @DisplayName("Given a valid cookie of a user and node id without permissions, "
    + "the openFile API should return a 404 status code. This test also covers the scenario where "
    + "Files service returns a malformed json response that jackson cannot deserialize")
  @Test
  void givenAUserAndANodeIdWithoutPermissionsTheOpenFileApiShouldReturnA404StatusCode()
    throws Exception {
    // Given
    String userCookie = "ZM_AUTH_TOKEN=9e2cffc4";
    String requesterId = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";
    String nodeId = "58032253-ed56-4eca-9017-3ae26cc2d9f1";

    expectationIds.add(simulator.mockValidateUser("9e2cffc4", requesterId));
    expectationIds.add(simulator.mockGetMyself(userCookie, requesterId, "pt_BR"));
    expectationIds.add(simulator.getFilesService().when(HttpRequest
        .request("/graphql/")
        .withCookie(Cookie.cookie("ZM_AUTH_TOKEN", "9e2cffc4"))
        .withBody(NodeAttributes.getNodeGraphQLRequest(nodeId, Optional.empty()))
      )
      .respond(HttpResponse
        .response()
        .withStatusCode(HttpStatus.OK_200)
        .withBody("""
          {
            "errors": [],
            "data": null
          }
          """)
      )[0].getId());

    LocalConnector httpLocalConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET);
    request.setHeader(HttpHeaders.HOST, "test");
    request.setHeader(HttpHeaders.COOKIE, userCookie);
    request.setURI("/files/open/%s".formatted(nodeId));

    // When
    Response response = HttpTester.parseResponse(
      httpLocalConnector.getResponse(request.generate()));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);
  }

  @DisplayName("Given a valid cookie of a user and node id with a size exceeding the limit set in "
    + "service discover, the openFile API should return a 403 status code")
  @ParameterizedTest
  @MethodSource("maxFileSizeProvider")
  void givenAUserAndANodeIdWithASizeTooLargeTheOpenFileApiShouldReturnA403StatusCode(
    String genericFileType,
    long maxFileTypeLimit,
    String fileMimeType,
    String fileExtension,
    long fileSize
  ) throws Exception {
    // Given
    String userCookie = "ZM_AUTH_TOKEN=9e2cffc4";
    String requesterId = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";
    String nodeId = "58032253-ed56-4eca-9017-3ae26cc2d9f1";

    expectationIds.add(simulator.mockValidateUser("9e2cffc4", requesterId));
    expectationIds.add(simulator.mockGetMyself(userCookie, requesterId, "pt_BR"));
    expectationIds.add(simulator.getFilesService()
      .when(HttpRequest
        .request("/graphql/")
        .withCookie(Cookie.cookie("ZM_AUTH_TOKEN", "9e2cffc4"))
        .withBody(NodeAttributes.getNodeGraphQLRequest(nodeId, Optional.empty()))
      )
      .respond(HttpResponse
        .response()
        .withStatusCode(HttpStatus.OK_200)
        .withBody(filesGetNodeResponseFormat.formatted(
          requesterId,
          nodeId,
          fileExtension,
          fileMimeType,
          fileSize
        ))
      )[0].getId());

    expectationIds.add(simulator.mockServiceDiscoverConfig(
      "max-file-size-in-mb/" + genericFileType,
      String.valueOf(maxFileTypeLimit))
    );

    LocalConnector httpLocalConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET);
    request.setHeader(HttpHeaders.HOST, "test");
    request.setHeader(HttpHeaders.COOKIE, userCookie);
    request.setURI("/files/open/%s".formatted(nodeId));

    // When
    Response response = HttpTester.parseResponse(
      httpLocalConnector.getResponse(request.generate()));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN_403);
    Assertions.assertThat(response.getContent()).contains(String.valueOf(maxFileTypeLimit));
  }
}
