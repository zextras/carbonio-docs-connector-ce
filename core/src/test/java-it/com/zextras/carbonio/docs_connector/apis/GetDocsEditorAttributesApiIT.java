package com.zextras.carbonio.docs_connector.apis;

import jakarta.ws.rs.HttpMethod;
import java.io.IOException;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

class GetDocsEditorAttributesApiIT {

  private ClientAndServer filesServiceMock;

  @Test
  void placeHolderOfIntTestToVerifyThatAllDependenciesAreOk() throws IOException {
    // Given
    filesServiceMock = ClientAndServer.startClientAndServer("localhost", 20000);

    // When
    filesServiceMock.when(HttpRequest.request().withPath("/test").withMethod(HttpMethod.GET.toString())).respond(HttpResponse.response().withStatusCode(
      HttpStatus.SC_OK));

    // When
    try (CloseableHttpClient httpClient = HttpClients.createMinimal()) {
      HttpGet request = new HttpGet("http://127.0.0.1:" + filesServiceMock.getLocalPort() + "/test");
      CloseableHttpResponse response = httpClient.execute(request);

      Assertions.assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
    }

    filesServiceMock.stop();
  }
}
