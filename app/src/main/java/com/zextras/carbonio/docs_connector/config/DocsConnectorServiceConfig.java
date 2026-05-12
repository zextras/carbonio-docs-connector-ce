// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.config;

import com.zextras.carbonio.quarkus.extensions.bootstrap.CarbonioServiceConfig;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ConfigKey;

public final class DocsConnectorServiceConfig implements CarbonioServiceConfig {

  private DocsConnectorServiceConfig() {}

  public static final class NetworkingConfig {
    private NetworkingConfig() {}

    @ConfigKey(description = "Host of the carbonio-user-management gRPC service")
    public static final String USER_MANAGEMENT_HOST = "carbonio.user-management.host";

    @ConfigKey(description = "Port of the carbonio-user-management gRPC service")
    public static final String USER_MANAGEMENT_PORT = "carbonio.user-management.port";

    @ConfigKey(description = "Host of the carbonio-files REST service")
    public static final String FILES_HOST = "carbonio.files.host";

    @ConfigKey(description = "Port of the carbonio-files REST service")
    public static final String FILES_PORT = "carbonio.files.port";

    @ConfigKey(description = "Host of the WOPI/docs-editor service")
    public static final String WOPI_HOST = "carbonio.wopi.host";

    @ConfigKey(description = "Port of the WOPI/docs-editor service")
    public static final String WOPI_PORT = "carbonio.wopi.port";
  }

  public static final class ApplicationConfig {
    private ApplicationConfig() {}

    /** Optional override for requester domain (legacy: carbonio.docs-connector.requester-domain-override).
     *  Consul KV: carbonio-docs-connector/requester-domain-override */
    @ConfigKey(description = "Override domain used for docs-editor redirects (testing only)")
    public static final String REQUESTER_DOMAIN_OVERRIDE = "requester-domain-override";

    /** Consul KV: carbonio-docs-connector/max-file-size-in-mb/{document|presentation|spreadsheet} */
    @ConfigKey(description = "Max file size in MB for documents", ifNotPresent = "50")
    public static final String MAX_FILE_SIZE_MB_DOCUMENT = "max-file-size-in-mb/document";

    @ConfigKey(description = "Max file size in MB for presentations", ifNotPresent = "100")
    public static final String MAX_FILE_SIZE_MB_PRESENTATION = "max-file-size-in-mb/presentation";

    @ConfigKey(description = "Max file size in MB for spreadsheets", ifNotPresent = "10")
    public static final String MAX_FILE_SIZE_MB_SPREADSHEET = "max-file-size-in-mb/spreadsheet";
  }
}
