// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.config;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.zextras.carbonio.docs_connector.Constants.Config.FilesService;
import com.zextras.carbonio.docs_connector.Constants.Config.UserService;
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

public class DocsConnectorModule extends RequestScopeModule {

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
  public UserManagementClient getUserServiceClient() {
    return UserManagementClient.atURL(UserService.PROTOCOL, UserService.URL, UserService.PORT);
  }

  @Provides
  @Singleton
  public FilesClient getFilesServiceClient() {
    return FilesClient.atURL(FilesService.PROTOCOL, FilesService.URL, FilesService.PORT);
  }
}
