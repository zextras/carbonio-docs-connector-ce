package com.zextras.carbonio.docs_connector.config;

import com.zextras.carbonio.docs_connector.controllers.FilesController;
import com.zextras.carbonio.docs_connector.generated.FilesApi;
import com.zextras.carbonio.docs_connector.generated.FilesApiService;
import org.jboss.resteasy.plugins.guice.ext.RequestScopeModule;

public class DocsConnectorModule extends RequestScopeModule {

  @Override
  public void configure() {
    bind(FilesApi.class);
    bind(FilesApiService.class).to(FilesController.class);
  }
}
