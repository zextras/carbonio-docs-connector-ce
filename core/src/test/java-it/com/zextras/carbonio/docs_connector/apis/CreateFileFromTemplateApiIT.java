// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.apis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zextras.carbonio.docs_connector.apis.Simulator.SimulatorBuilder;
import com.zextras.carbonio.docs_connector.types.CreatedFile;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.HttpHeaders;
import org.assertj.core.api.Assertions;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.server.LocalConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

public class CreateFileFromTemplateApiIT {

  private static Simulator simulator;

  @BeforeAll
  static void init() {
    simulator = SimulatorBuilder
      .aSimulator()
      .init()
      .withUserManagement()
      .withFiles()
      .build()
      .start();
  }

  @AfterAll
  static void cleanUpAll() {
    simulator.stopAll();
  }

  @Test
  void givenAValidUserAndADocumentTypeTheCreateFileApiShouldReturnUploadTheRelatedTemplateAndReturnTheUploadedNodeId()
    throws Exception {
    // Given
    simulator
      .getFilesService()
      .when(HttpRequest.request().withMethod(HttpMethod.POST).withPath("/upload/"))
      .respond(HttpResponse
        .response()
        .withStatusCode(200)
        .withBody("{ \"nodeId\": \"11111111-1111-1111-1111-111111111111\"}")
      );

    String userCookie = "ZM_AUTH_TOKEN=9e2cffc4";
    String requesterId = "9e2cffc4-5860-4095-aedb-7b48d6ff889a";
    simulator.mockValidateUser("9e2cffc4", requesterId);
    simulator.mockGetMyself(userCookie, requesterId, "pt_BR");

    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.POST);
    request.setURI(("/files/create/"));
    String jsonBody = "{"
      + "\"filename\": \"New spreadsheet\","
      + "\"destinationFolderId\": \"LOCAL_ROOT\","
      + "\"type\": \"LIBRE_DOCUMENT\""
      + "}";
    request.setContent(jsonBody);
    request.setHeader("Content-Type", "application/json");
    request.setHeader(HttpHeaders.COOKIE, userCookie);

    // When
    LocalConnector localConnector = simulator.getHttpLocalConnector();
    HttpTester.Response response =
      HttpTester.parseResponse(HttpTester.from(localConnector.getResponse(request.generate())));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(200);

    CreatedFile responseContent =
      new ObjectMapper().readValue(response.getContent(), CreatedFile.class);

    Assertions
      .assertThat(responseContent.getNodeId().toString())
      .isEqualTo("11111111-1111-1111-1111-111111111111");
  }
}
