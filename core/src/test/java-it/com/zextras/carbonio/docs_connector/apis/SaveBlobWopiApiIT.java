// SPDX-FileCopyrightText: 2025 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.apis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zextras.carbonio.docs_connector.apis.Simulator.SimulatorBuilder;
import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import com.zextras.carbonio.docs_connector.dal.repositories.interfaces.OpenDocumentTokenRepository;
import com.zextras.carbonio.docs_connector.entities.files.graphql.NodeAttributes;
import com.zextras.carbonio.docs_connector.types.NodeUpdatedTimestamp;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.HttpHeaders;
import org.apache.commons.codec.binary.Base64;
import org.assertj.core.api.Assertions;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.http.HttpTester.Response;
import org.eclipse.jetty.server.LocalConnector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.Cookie;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class SaveBlobWopiApiIT {

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
                      "updated_at": %d,
                      "extension": "%s",
                      "mime_type": "%s",
                      "size": %d,
                      "version": %d
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

  @DisplayName("Given a valid user cookie, an accessible node id, a valid OpenDocument token and a blob to save, the "
    + "saveBlob WOPI API should return a 200 and a payload containing the updated timestamp of the related Node")
  @Test
  void givenAllMandatoryValidInputsTheSaveBlobApiShouldReturnA200ContainingTheUpdatedTimestampOfTheRelatedNode()
    throws Exception {
    // Given
    String userCookie = "ZM_AUTH_TOKEN=9e2cffc4";
    String requesterId = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";
    String nodeId = "58032253-ed56-4eca-9017-3ae26cc2d9f1";
    byte[] nodeBlob = "file-content".getBytes(StandardCharsets.UTF_8);

    OpenDocumentToken openDocumentToken = simulator
      .getInjector()
      .getInstance(OpenDocumentTokenRepository.class)
      .createToken(UUID.fromString(nodeId), requesterId, userCookie);

    expectationIds.add(simulator.mockValidateUser("9e2cffc4", requesterId));
    expectationIds.add(simulator.mockGetMyself(userCookie, requesterId, "en_US"));

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
          100,
          "odt",
          "application/vnd.oasis.opendocument.text",
          52428800,
          4
        ))
      )[0].getId()
    );

    expectationIds.add(simulator.getFilesService()
      .when(HttpRequest
        .request("/upload-version/")
        .withCookie(Cookie.cookie("ZM_AUTH_TOKEN", "9e2cffc4"))
        .withHeader(
          "Filename",
          Base64.encodeBase64String("test-file.odt".getBytes(StandardCharsets.UTF_8))
        )
        .withHeader("NodeId", nodeId)
        .withHeader("OverwriteVersion", String.valueOf(true))
        .withBody(new BinaryBody(nodeBlob))
      )
      .respond(HttpResponse
        .response()
        .withStatusCode(HttpStatus.OK_200)
        .withBody("""
          {
            "nodeId": "%s",
            "version": 5
          }
          """.formatted(nodeId))
      )[0].getId()
    );

    expectationIds.add(simulator.getFilesService()
      .when(HttpRequest
        .request("/graphql/")
        .withCookie(Cookie.cookie("ZM_AUTH_TOKEN", "9e2cffc4"))
        .withBody(NodeAttributes.getNodeGraphQLRequest(nodeId, Optional.of(5)))
      )
      .respond(HttpResponse
        .response()
        .withStatusCode(HttpStatus.OK_200)
        .withBody(filesGetNodeResponseFormat.formatted(
          requesterId,
          nodeId,
          59000,
          "odt",
          "application/vnd.oasis.opendocument.text",
          52428800,
          5
        ))
      )[0].getId()
    );

    LocalConnector httpLocalConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.POST.toString());
    request.setHeader(HttpHeaders.HOST, "test");
    request.setHeader(HttpHeaders.COOKIE, userCookie);
    request.setHeader("X-COOL-WOPI-IsAutosave", String.valueOf(true));
    request.setHeader("X-COOL-WOPI-IsExitSave", String.valueOf(false));
    request.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(nodeBlob.length));
    request.setURI("/wopi/%s/contents?access_token=%s&access_token_ttl=%s".formatted(
      nodeId,
      openDocumentToken.getTokenId(),
      System.currentTimeMillis() + 10000000)
    );
    request.setContent(nodeBlob);

    // When
    Response response = HttpTester.parseResponse(httpLocalConnector.getResponse(request.generate()));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);

    NodeUpdatedTimestamp updatedTimestamp = new ObjectMapper()
      .readValue(response.getContent(), NodeUpdatedTimestamp.class);
    Assertions.assertThat(updatedTimestamp.getLastModifiedTime()).isEqualTo("1970-01-01T00:00:59");
  }


  @DisplayName("Scenario: Files is not reachable. "
    + "Given a valid user cookie, an accessible node id, a valid OpenDocument token and a blob to save, the saveBlob "
    + "WOPI API should return a failed dependency status code")
  @Test
  void givenAllMandatoryValidInputsButFilesIsNotReachableTheSaveBlobApiShouldReturnAFailedDependencyStatusCode()
    throws Exception {
    // Given
    String userCookie = "ZM_AUTH_TOKEN=9e2cffc4";
    String requesterId = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";
    String nodeId = "58032253-ed56-4eca-9017-3ae26cc2d9f1";
    byte[] nodeBlob = "file-content".getBytes(StandardCharsets.UTF_8);

    OpenDocumentToken openDocumentToken = simulator
      .getInjector()
      .getInstance(OpenDocumentTokenRepository.class)
      .createToken(UUID.fromString(nodeId), requesterId, userCookie);

    expectationIds.add(simulator.mockValidateUser("9e2cffc4", requesterId));
    expectationIds.add(simulator.mockGetMyself(userCookie, requesterId, "en_US"));

    expectationIds.add(simulator.getFilesService()
      .when(HttpRequest
        .request("/graphql/")
        .withCookie(Cookie.cookie("ZM_AUTH_TOKEN", "9e2cffc4"))
        .withBody(NodeAttributes.getNodeGraphQLRequest(nodeId, Optional.empty()))
      )
      .respond(HttpResponse
        .response()
        .withStatusCode(HttpStatus.SERVICE_UNAVAILABLE_503)
      )[0].getId()
    );

    LocalConnector httpLocalConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.POST.toString());
    request.setHeader(HttpHeaders.HOST, "test");
    request.setHeader(HttpHeaders.COOKIE, userCookie);
    request.setHeader("X-COOL-WOPI-IsAutosave", String.valueOf(false));
    request.setHeader("X-COOL-WOPI-IsExitSave", String.valueOf(false));
    request.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(nodeBlob.length));
    request.setURI("/wopi/%s/contents?access_token=%s&access_token_ttl=%s".formatted(
      nodeId,
      openDocumentToken.getTokenId(),
      System.currentTimeMillis() + 10000000)
    );
    request.setContent(nodeBlob);

    // When
    Response response = HttpTester.parseResponse(httpLocalConnector.getResponse(request.generate()));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(424);
  }

  @DisplayName("Given a valid user cookie, an accessible node id, a valid OpenDocument token and a blob to save, the "
    + "saveBlob WOPI API should not save the blob and returns an error status code")
  @ParameterizedTest
  @CsvSource({
    "424, 424", // Scenario: something went wrong on Files during upload (unauthorized, storages failed ...)
    "500, 424", // Scenario: Files exploded during upload
  })
  void givenAllMandatoryValidInputsButAccountIsInOverQuotaTheSaveBlobApiShouldReturnAnError(int filesStatusCode, int docsStatusCode)
    throws Exception {
    // Given
    String userCookie = "ZM_AUTH_TOKEN=9e2cffc4";
    String requesterId = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";
    String nodeId = "58032253-ed56-4eca-9017-3ae26cc2d9f1";
    byte[] nodeBlob = "file-content".getBytes(StandardCharsets.UTF_8);

    OpenDocumentToken openDocumentToken = simulator
      .getInjector()
      .getInstance(OpenDocumentTokenRepository.class)
      .createToken(UUID.fromString(nodeId), requesterId, userCookie);

    expectationIds.add(simulator.mockValidateUser("9e2cffc4", requesterId));
    expectationIds.add(simulator.mockGetMyself(userCookie, requesterId, "en_US"));

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
          100,
          "odt",
          "application/vnd.oasis.opendocument.text",
          52428800,
          4
        ))
      )[0].getId()
    );

    expectationIds.add(simulator.getFilesService()
      .when(HttpRequest
        .request("/upload-version/")
        .withCookie(Cookie.cookie("ZM_AUTH_TOKEN", "9e2cffc4"))
        .withHeader(
          "Filename",
          Base64.encodeBase64String("test-file.odt".getBytes(StandardCharsets.UTF_8))
        )
        .withHeader("NodeId", nodeId)
        .withHeader("OverwriteVersion", String.valueOf(true))
        .withBody(new BinaryBody(nodeBlob))
      )
      .respond(HttpResponse
        .response()
        .withStatusCode(filesStatusCode)
      )[0].getId()
    );

    LocalConnector httpLocalConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.POST.toString());
    request.setHeader(HttpHeaders.HOST, "test");
    request.setHeader(HttpHeaders.COOKIE, userCookie);
    request.setHeader("X-COOL-WOPI-IsAutosave", String.valueOf(true));
    request.setHeader("X-COOL-WOPI-IsExitSave", String.valueOf(false));
    request.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(nodeBlob.length));
    request.setURI("/wopi/%s/contents?access_token=%s&access_token_ttl=%s".formatted(
      nodeId,
      openDocumentToken.getTokenId(),
      System.currentTimeMillis() + 10000000)
    );
    request.setContent(nodeBlob);

    // When
    Response response = HttpTester.parseResponse(httpLocalConnector.getResponse(request.generate()));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(docsStatusCode);
  }


  @DisplayName("Scenario: Files fails during the fetch of the updated node metadata. "
    + "Given a valid user cookie, an accessible node id, a valid OpenDocument token and a blob to save, the saveBlob "
    + "WOPI API should return a failed dependency status code")
  @Test
  void givenAllMandatoryValidInputsButFilesFailsToUploadTheSaveBlobApiShouldReturnAFailedDependencyStatusCode()
    throws Exception {
    // Given
    String userCookie = "ZM_AUTH_TOKEN=9e2cffc4";
    String requesterId = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";
    String nodeId = "58032253-ed56-4eca-9017-3ae26cc2d9f1";
    byte[] nodeBlob = "file-content".getBytes(StandardCharsets.UTF_8);

    OpenDocumentToken openDocumentToken = simulator
      .getInjector()
      .getInstance(OpenDocumentTokenRepository.class)
      .createToken(UUID.fromString(nodeId), requesterId, userCookie);

    expectationIds.add(simulator.mockValidateUser("9e2cffc4", requesterId));
    expectationIds.add(simulator.mockGetMyself(userCookie, requesterId, "en_US"));

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
          100,
          "odt",
          "application/vnd.oasis.opendocument.text",
          52428800,
          4
        ))
      )[0].getId()
    );

    expectationIds.add(simulator.getFilesService()
      .when(HttpRequest
        .request("/upload-version/")
        .withCookie(Cookie.cookie("ZM_AUTH_TOKEN", "9e2cffc4"))
        .withHeader(
          "Filename",
          Base64.encodeBase64String("test-file.odt".getBytes(StandardCharsets.UTF_8))
        )
        .withHeader("NodeId", nodeId)
        .withHeader("OverwriteVersion", String.valueOf(false))
        .withBody(new BinaryBody(nodeBlob))
      )
      .respond(HttpResponse
        .response()
        .withStatusCode(HttpStatus.OK_200)
        .withBody("""
          {
            "nodeId": "%s",
            "version": 5
          }
          """.formatted(nodeId))
      )[0].getId()
    );

    expectationIds.add(simulator.getFilesService()
      .when(HttpRequest
        .request("/graphql/")
        .withCookie(Cookie.cookie("ZM_AUTH_TOKEN", "9e2cffc4"))
        .withBody(NodeAttributes.getNodeGraphQLRequest(nodeId, Optional.of(5)))
      )
      .respond(HttpResponse
        .response()
        .withStatusCode(HttpStatus.UNAUTHORIZED_401)
      )[0].getId()
    );

    LocalConnector httpLocalConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.POST.toString());
    request.setHeader(HttpHeaders.HOST, "test");
    request.setHeader(HttpHeaders.COOKIE, userCookie);
    request.setHeader("X-COOL-WOPI-IsAutosave", String.valueOf(false));
    request.setHeader("X-COOL-WOPI-IsExitSave", String.valueOf(false));
    request.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(nodeBlob.length));
    request.setURI("/wopi/%s/contents?access_token=%s&access_token_ttl=%s".formatted(
      nodeId,
      openDocumentToken.getTokenId(),
      System.currentTimeMillis() + 10000000)
    );
    request.setContent(nodeBlob);

    // When
    Response response = HttpTester.parseResponse(httpLocalConnector.getResponse(request.generate()));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(424);
  }
}
