// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.apis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zextras.carbonio.docs_connector.apis.Simulator.SimulatorBuilder;
import com.zextras.carbonio.docs_connector.types.health.DependencyType;
import com.zextras.carbonio.docs_connector.types.health.HealthStatus;
import com.zextras.carbonio.docs_connector.types.health.ServiceHealth;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.http.HttpTester.Response;
import org.eclipse.jetty.server.LocalConnector;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

public class HealthApiIT {

  @Test
  void givenAllDependenciesHealthyTheHealthShouldReturn200CodeWithTheHealthStatusOfEachDependency()
    throws Exception {
    // Given
    SimulatorBuilder simulatorBuilder =
      SimulatorBuilder.aSimulator()
        .init()
        .withUserManagement()
        .withFiles();

    try (Simulator simulator = simulatorBuilder.build().start()) {

      LocalConnector localConnector = simulator.getHttpLocalConnector();
      MockServerClient userManagementServiceMock = simulator.getUserManagementService();

      userManagementServiceMock
        .when(HttpRequest.request().withMethod(HttpMethod.GET).withPath("/health/"))
        .respond(HttpResponse.response().withStatusCode(HttpStatus.OK_200));

      MockServerClient filesServiceMock = simulator.getFilesService();

      filesServiceMock
        .when(HttpRequest.request().withMethod(HttpMethod.GET).withPath("/health/"))
        .respond(HttpResponse.response().withStatusCode(HttpStatus.OK_200));

      HttpTester.Request request = HttpTester.newRequest();
      request.setMethod(HttpMethod.GET);
      request.setHeader(HttpHeaders.HOST, "test");
      request.setURI(("/health/"));

      // When
      Response httpFields =
        HttpTester.parseResponse(HttpTester.from(localConnector.getResponse(request.generate())));

      // Then
      Assertions.assertThat(httpFields.getStatus()).isEqualTo(HttpStatus.OK_200);

      HealthStatus healthStatus =
        new ObjectMapper().readValue(httpFields.getContent(), HealthStatus.class);

      Assertions.assertThat(healthStatus.isReady()).isTrue();
      List<ServiceHealth> dependenciesHealth = healthStatus.getDependencies();
      Assertions.assertThat(dependenciesHealth).hasSize(2);

      Assertions.assertThat(dependenciesHealth.get(0).getName())
        .isEqualTo("carbonio-user-management");
      Assertions.assertThat(dependenciesHealth.get(0).isLive()).isTrue();
      Assertions.assertThat(dependenciesHealth.get(0).isReady()).isTrue();
      Assertions.assertThat(dependenciesHealth.get(0).getType()).isEqualTo(DependencyType.REQUIRED);

      Assertions.assertThat(dependenciesHealth.get(1).getName())
        .isEqualTo("carbonio-files");
      Assertions.assertThat(dependenciesHealth.get(1).isLive()).isTrue();
      Assertions.assertThat(dependenciesHealth.get(1).isReady()).isTrue();
      Assertions.assertThat(dependenciesHealth.get(1).getType()).isEqualTo(DependencyType.REQUIRED);
    }
  }

  @Test
  void
  givenUserManagementUnreachableAndFilesUnreachableTheHealthShouldReturn502CodeWithTheHealthStatusOfEachDependency()
    throws Exception {
    // Given
    // Notice tha absence of the UserManagement and Files initialization
    try (Simulator simulator = SimulatorBuilder.aSimulator().init().build().start()) {
      LocalConnector localConnector = simulator.getHttpLocalConnector();

      HttpTester.Request request = HttpTester.newRequest();
      request.setMethod(HttpMethod.GET);
      request.setHeader(HttpHeaders.HOST, "test");
      request.setURI(("/health/"));

      // When
      Response httpFields =
        HttpTester.parseResponse(HttpTester.from(localConnector.getResponse(request.generate())));

      // Then
      Assertions.assertThat(httpFields.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY_502);

      HealthStatus healthStatus =
        new ObjectMapper().readValue(httpFields.getContent(), HealthStatus.class);

      Assertions.assertThat(healthStatus.isReady()).isFalse();
      List<ServiceHealth> dependenciesHealth = healthStatus.getDependencies();
      Assertions.assertThat(dependenciesHealth).hasSize(2);

      Assertions.assertThat(dependenciesHealth.get(0).getName()).isEqualTo("carbonio-user-management");
      Assertions.assertThat(dependenciesHealth.get(0).isLive()).isFalse();
      Assertions.assertThat(dependenciesHealth.get(0).isReady()).isFalse();
      Assertions.assertThat(dependenciesHealth.get(0).getType()).isEqualTo(DependencyType.REQUIRED);

      Assertions.assertThat(dependenciesHealth.get(1).getName())
        .isEqualTo("carbonio-files");
      Assertions.assertThat(dependenciesHealth.get(1).isLive()).isFalse();
      Assertions.assertThat(dependenciesHealth.get(1).isReady()).isFalse();
      Assertions.assertThat(dependenciesHealth.get(1).getType()).isEqualTo(DependencyType.REQUIRED);
    }
  }

  @Test
  void givenAnHealthServiceTheHealthLiveShouldReturn204StatusCode() throws Exception {
    // Given
    try (Simulator simulator = SimulatorBuilder.aSimulator().init().build().start()) {
      LocalConnector localConnector = simulator.getHttpLocalConnector();

      HttpTester.Request request = HttpTester.newRequest();
      request.setMethod(HttpMethod.GET);
      request.setHeader(HttpHeaders.HOST, "test");
      request.setURI(("/health/live/"));

      // When
      Response httpFields =
        HttpTester.parseResponse(HttpTester.from(localConnector.getResponse(request.generate())));

      // Then
      Assertions.assertThat(httpFields.getStatus()).isEqualTo(HttpStatus.NO_CONTENT_204);
      Assertions.assertThat(httpFields.getContent()).isEmpty();
    }
  }

  @Test
  void givenAllDependenciesHealthyTheHealthReadyShouldReturn204StatusCode() throws Exception {
    // Given
    SimulatorBuilder simulatorBuilder =
      SimulatorBuilder.aSimulator()
        .init()
        .withUserManagement()
        .withFiles();

    try (Simulator simulator = simulatorBuilder.build().start()) {

      LocalConnector localConnector = simulator.getHttpLocalConnector();
      MockServerClient userManagementServiceMock = simulator.getUserManagementService();

      userManagementServiceMock
        .when(HttpRequest.request().withMethod(HttpMethod.GET).withPath("/health/"))
        .respond(HttpResponse.response().withStatusCode(HttpStatus.OK_200));

      MockServerClient filesServiceMock = simulator.getFilesService();

      filesServiceMock
        .when(HttpRequest.request().withMethod(HttpMethod.GET).withPath("/health/"))
        .respond(HttpResponse.response().withStatusCode(HttpStatus.OK_200));

      HttpTester.Request request = HttpTester.newRequest();
      request.setMethod(HttpMethod.GET);
      request.setHeader(HttpHeaders.HOST, "test");
      request.setURI(("/health/ready/"));

      // When
      Response httpFields =
        HttpTester.parseResponse(HttpTester.from(localConnector.getResponse(request.generate())));

      // Then
      Assertions.assertThat(httpFields.getStatus()).isEqualTo(HttpStatus.NO_CONTENT_204);
      Assertions.assertThat(httpFields.getContent()).isEmpty();
    }
  }


  @Test
  void givenUserManagementUnreachableAndFilesUnreachableTheHealthReadyShouldReturn502StatusCode() throws Exception {
    // Given
    // Notice tha absence of the UserManagement and Files initialization
    try (Simulator simulator = SimulatorBuilder.aSimulator().init().build().start()) {
      LocalConnector localConnector = simulator.getHttpLocalConnector();

      HttpTester.Request request = HttpTester.newRequest();
      request.setMethod(HttpMethod.GET);
      request.setHeader(HttpHeaders.HOST, "test");
      request.setURI(("/health/ready/"));

      // When
      Response httpFields =
        HttpTester.parseResponse(HttpTester.from(localConnector.getResponse(request.generate())));

      // Then
      Assertions.assertThat(httpFields.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY_502);
      Assertions.assertThat(httpFields.getContent()).isEmpty();
    }
  }
}
