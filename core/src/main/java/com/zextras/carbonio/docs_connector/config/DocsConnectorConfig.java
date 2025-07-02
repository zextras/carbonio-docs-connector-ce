// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.config;

import com.orbitz.consul.Consul;
import com.zextras.carbonio.docs_connector.Constants;
import com.zextras.carbonio.docs_connector.Constants.Config;
import com.zextras.carbonio.docs_connector.Constants.Config.Key;
import com.zextras.carbonio.docs_connector.Constants.DocsConnector;
import com.zextras.carbonio.docs_connector.types.FileType.GenericFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

public class DocsConnectorConfig {

  private final String consulHttpTokenEnvironmentKey = "CONSUL_HTTP_TOKEN";
  private static final Logger logger = LoggerFactory.getLogger(DocsConnectorConfig.class);
  private final Properties properties;

  public DocsConnectorConfig() {
      properties = new Properties();
  }

  // Load config from files or system properties.
  public void loadConfig() throws IOException {
      loadFromEtc() // the official way
          .ifPresent(config -> {
              try {
                  properties.load(config);
              } catch (IOException e) {
                  logger.warn("Error loading configuration file: {}", e.getMessage());
              }
          });

      properties.putAll(System.getProperties()); // the dev way, overriding existing properties
  }

  private Optional<InputStream> loadFromEtc() {
      return loadFile("/etc/carbonio/docs-connector/config.properties");
  }

  private Optional<InputStream> loadFile(String path) {
      try {
          return Optional.of(new FileInputStream(path));
      } catch (FileNotFoundException e) {
          return Optional.empty();
      }
  }

  public String getDocsConnectorHost() {
      return properties.getProperty(
          Constants.DocsConnector.HOST_PROPERTY,
          Constants.DocsConnector.DEFAULT_HOST);
  }

  public String getDocsConnectorPort() {
      return properties.getProperty(
          Constants.DocsConnector.PORT_PROPERTY,
          String.valueOf(Constants.DocsConnector.DEFAULT_PORT));
  }

  public String getUserManagementHost() {
      return properties.getProperty(
          Constants.Config.UserManagement.HOST_PROPERTY,
          Constants.Config.UserManagement.DEFAULT_HOST);
  }

  public String getUserManagementPort() {
      return properties.getProperty(
          Constants.Config.UserManagement.PORT_PROPERTY,
          String.valueOf(Constants.Config.UserManagement.DEFAULT_PORT));
  }

  public String getFilesHost() {
      return properties.getProperty(
          Constants.Config.Files.HOST_PROPERTY,
          Constants.Config.Files.DEFAULT_HOST);
  }

  public String getFilesPort() {
      return properties.getProperty(
          Constants.Config.Files.PORT_PROPERTY,
          String.valueOf(Constants.Config.Files.DEFAULT_PORT));
  }

  public String getWopiHost() {
      return properties.getProperty(
          Constants.Config.Wopi.HOST_PROPERTY,
          Constants.Config.Wopi.DEFAULT_HOST);
  }

  public String getWopiPort() {
      return properties.getProperty(
          Constants.Config.Wopi.PORT_PROPERTY,
          String.valueOf(Constants.Config.Wopi.DEFAULT_PORT));
  }

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
        DocsConnector.SERVICE_NAME,
        Key.MAX_FILE_SIZE_IN_MB,
        fileType.toString().toLowerCase()
      ))
      .map(Long::parseLong)
      .orElse(Config.DEFAULT_MAX_FILE_SIZE_IN_MB_PER_TYPE.get(fileType));
  }
}
