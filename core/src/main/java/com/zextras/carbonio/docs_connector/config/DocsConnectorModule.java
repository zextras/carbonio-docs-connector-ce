// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.config;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.zextras.carbonio.docs_connector.Constants;
import com.zextras.carbonio.docs_connector.Constants.Config.Files;
import com.zextras.carbonio.docs_connector.Constants.Config.UserManagement;
import com.zextras.carbonio.docs_connector.auth.AccessTokenValidationFilter;
import com.zextras.carbonio.docs_connector.auth.CookieAuthenticationFilter;
import com.zextras.carbonio.docs_connector.controllers.FilesController;
import com.zextras.carbonio.docs_connector.controllers.HealthController;
import com.zextras.carbonio.docs_connector.controllers.WopiController;
import com.zextras.carbonio.docs_connector.controllers.impl.FilesControllerImpl;
import com.zextras.carbonio.docs_connector.controllers.impl.HealthControllerImpl;
import com.zextras.carbonio.docs_connector.controllers.impl.WopiControllerImpl;
import com.zextras.carbonio.docs_connector.dal.repositories.impl.OpenDocumentTokenRepositoryInMemory;
import com.zextras.carbonio.docs_connector.dal.repositories.interfaces.OpenDocumentTokenRepository;
import com.zextras.carbonio.files.FilesClient;
import com.zextras.carbonio.usermanagement.UserManagementClient;
import java.time.Clock;
import org.jboss.resteasy.plugins.guice.ext.RequestScopeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocsConnectorModule extends RequestScopeModule {

  private static final Logger logger = LoggerFactory.getLogger(DocsConnectorModule.class);

  @Override
  public void configure() {
    bind(HealthController.class).to(HealthControllerImpl.class);
    bind(FilesController.class).to(FilesControllerImpl.class);
    bind(WopiController.class).to(WopiControllerImpl.class);

    bind(Clock.class).toInstance(Clock.systemUTC());
    bind(OpenDocumentTokenRepository.class).to(OpenDocumentTokenRepositoryInMemory.class);

    bind(AccessTokenValidationFilter.class);
    bind(CookieAuthenticationFilter.class);
  }

  @Provides
  @Singleton
  public DocsConnectorConfig provideConfig() throws Exception {
      final DocsConnectorConfig config = new DocsConnectorConfig();
      config.loadConfig();
      return config;
  }

  @Provides
  @Singleton
  public UserManagementClient provideUserManagementClient(DocsConnectorConfig config) {
      final String userManagementUrl = String.format(
          "%s://%s:%s",
          Constants.Config.UserManagement.DEFAULT_PROTOCOL,
          config.getUserManagementHost(),
          config.getUserManagementPort());

      logger.info("Creating UserManagementClient with URL: {}", userManagementUrl);
      return UserManagementClient.atURL(userManagementUrl);
  }

  @Provides
  @Singleton
  public FilesClient provideFilesClient(DocsConnectorConfig config) {
      final String filesUrl = String.format(
          "%s://%s:%s",
          Constants.Config.Files.DEFAULT_PROTOCOL,
          config.getFilesHost(),
          config.getFilesPort());

      logger.info("Creating FilesClient with URL: {}", filesUrl);
      return FilesClient.atURL(filesUrl);
  }
}
