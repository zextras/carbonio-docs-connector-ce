// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.config;

import com.orbitz.consul.Consul;
import com.zextras.carbonio.docs_connector.Constants.Config;
import com.zextras.carbonio.docs_connector.Constants.Config.Key;
import com.zextras.carbonio.docs_connector.Constants.Service;
import com.zextras.carbonio.docs_connector.types.FileType.GenericFileType;

public class DocsConnectorConfig {

  private final String consulHttpTokenEnvironmentKey = "CONSUL_HTTP_TOKEN";

  /**
   * Fetches the maximum file size values from the service-discover key/value if it is set,
   * otherwise it returns the default size
   *
   * @param fileType is a {@link GenericFileType} to fetch the maximum file size for.
   * @return a {@code long} representing the maximum file size of the passed file type.
   */
  public long getMaxSizeLimitForFileType(GenericFileType fileType) {
    return Consul
      .builder()
      .withTokenAuth(System.getenv(consulHttpTokenEnvironmentKey))
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
