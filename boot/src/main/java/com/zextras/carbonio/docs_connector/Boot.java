// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.zextras.carbonio.docs_connector.Constants.DocsConnector;
import com.zextras.carbonio.docs_connector.config.DocsConnectorConfig;
import com.zextras.carbonio.docs_connector.config.DocsConnectorModule;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.slf4j.LoggerFactory;

public class Boot {

  private static final Logger   rootLogger = (Logger) LoggerFactory.getLogger("ROOT");
  private static final Logger   logger     = (Logger) LoggerFactory.getLogger(Boot.class);
  private static       Injector injector;

  private Server server;


  public static void main(String[] args) throws Exception {
    injector = Guice.createInjector(new DocsConnectorModule());
    injector.getInstance(Boot.class).boot();
  }

  public void boot() throws Exception {

    // Set configuration level
    String logLevel = System.getProperty("LOG_LEVEL");
    rootLogger.setLevel(Level.toLevel(Optional.ofNullable(logLevel).orElse("WARN")));

    try {
      DocsConnectorConfig config = injector.getInstance(DocsConnectorConfig.class);
      server = new Server(InetSocketAddress.createUnresolved(config.getDocsConnectorHost(), Integer.parseInt(config.getDocsConnectorPort())));
      ServletContextHandler servletHandler = new ServletContextHandler("/", ServletContextHandler.SESSIONS);
      servletHandler.addEventListener(injector.getInstance(
        GuiceResteasyBootstrapServletContextListener.class)
      );
      ServletHolder servletHolder = new ServletHolder(HttpServletDispatcher.class);

      servletHandler.addServlet(servletHolder, "/*");
      server.setHandler(servletHandler);

      server.start();
      server.join();
    } catch (Exception exception) {
      logger.error("Service stopped unexpectedly: " + exception.getMessage(), exception);
    } finally {
      server.stop();
    }
  }
}
