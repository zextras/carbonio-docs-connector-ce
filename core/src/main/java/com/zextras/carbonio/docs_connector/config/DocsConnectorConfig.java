package com.zextras.carbonio.docs_connector.config;

import com.orbitz.consul.Consul;
import com.zextras.carbonio.docs_connector.Constants.Config;
import com.zextras.carbonio.docs_connector.Constants.Config.Key;
import com.zextras.carbonio.docs_connector.Constants.Service;
import com.zextras.carbonio.docs_connector.types.FileType.GenericFileType;

public class DocsConnectorConfig {

  private final String consulHttpTokenEnvironmentKey = "CONSUL_HTTP_TOKEN";

  public long getMaxSizeLimitForDocumentType(GenericFileType fileType) {
    return Consul
      .builder()
      .withTokenAuth(consulHttpTokenEnvironmentKey)
      .build()
      .keyValueClient()
      .getValueAsString("%s/%s/%s".formatted(
        Service.SERVICE_NAME,
        Key.MAX_FILE_SIZE_IN_MB,
        fileType.toString().toLowerCase()
      ))
      .map(Long::parseLong)
      .orElse(Config.DEFAULT_MAX_FILE_SIZE_IN_MB_PER_TYPE.get(fileType));
  }
}
