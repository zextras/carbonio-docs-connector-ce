// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector;

import com.zextras.carbonio.docs_connector.types.FileType.GenericFileType;
import java.util.List;
import java.util.Map;

public final class Constants {

  private Constants() {}

  public static final class DocsConnector {

    private DocsConnector() {}

    public static final class API {

      private API() {}

      public static final class Endpoints {

        private Endpoints() {}

        public static final String WOPI = "wopi";
        public static final String FILES = "files";

      }

      public static final class Wopi {

        private Wopi() {}

        public static final String ACCESS_TOKEN_QUERY_PARAM = "access_token";
        public static final String ACCESS_TOKEN_TTL_QUERY_PARAM = "access_token_ttl";
      }
    }
  }

  public static final class Context {

    private Context() {}

    public static final String OPEN_DOCUMENT_TOKEN = "open-document-token";
    public static final String REQUESTER_COOKIE = "requester-cookie";
    public static final String REQUESTER_ID = "requester-id";
    public static final String REQUESTER_DOMAIN = "requester-domain";
    /** @deprecated Use {@link com.zextras.carbonio.docs_connector.config.DocsConnectorServiceConfig.ApplicationConfig#REQUESTER_DOMAIN_OVERRIDE} instead. */
    @Deprecated(forRemoval = true)
    public static final String OVERRIDE_REQUESTER_DOMAIN_PROPERTY = "carbonio.docs-connector.requester-domain-override";
    public static final String REQUESTER_LOCALE = "requester-locale";
  }

  public static final class Config {

    private Config() {}

    public static final String ACCEPTED_COOKIE_TYPE = "ZM_AUTH_TOKEN";

    public static final List<String> DOCUMENT_MIME_TYPES = List.of(
      "text/rtf",
      "text/plain",
      "application/msword",
      "application/rtf",
      "application/vnd.lotus-wordpro",
      "application/vnd.ms-word.document.macroEnabled.12",
      "application/vnd.ms-word.template.macroEnabled.12",
      "application/vnd.oasis.opendocument.text",
      "application/vnd.oasis.opendocument.text-flat-xml",
      "application/vnd.oasis.opendocument.text-master",
      "application/vnd.oasis.opendocument.text-master-template",
      "application/vnd.oasis.opendocument.text-template",
      "application/vnd.oasis.opendocument.text-web",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
      "application/vnd.sun.xml.writer",
      "application/vnd.sun.xml.writer.global",
      "application/vnd.sun.xml.writer.template"
    );
    public static final List<String> PRESENTATION_MIME_TYPES = List.of(
      "application/vnd.ms-powerpoint",
      "application/vnd.ms-powerpoint.presentation.macroEnabled.12",
      "application/vnd.ms-powerpoint.template.macroEnabled.12",
      "application/vnd.oasis.opendocument.presentation",
      "application/vnd.oasis.opendocument.presentation-flat-xml",
      "application/vnd.openxmlformats-officedocument.presentationml.presentation",
      "application/vnd.openxmlformats-officedocument.presentationml.template",
      "application/vnd.sun.xml.impress",
      "application/vnd.sun.xml.impress.template"
    );
    public static final List<String> SPREADSHEET_MIME_TYPES = List.of(
      "application/vnd.ms-excel",
      "application/vnd.ms-excel.sheet.binary.macroEnabled.12",
      "application/vnd.ms-excel.sheet.macroEnabled.12",
      "application/vnd.ms-excel.template.macroEnabled.12",
      "application/vnd.oasis.opendocument.spreadsheet",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
      "application/vnd.sun.xml.calc",
      "application/vnd.sun.xml.calc.template"
    );
    public static final Map<GenericFileType, List<String>> GENERIC_FILE_TYPE_MIME_TYPES_MAP =
      Map.of(
        GenericFileType.DOCUMENT, DOCUMENT_MIME_TYPES,
        GenericFileType.PRESENTATION, PRESENTATION_MIME_TYPES,
        GenericFileType.SPREADSHEET, SPREADSHEET_MIME_TYPES
      );

    public static final class Wopi {

      private Wopi() {}

      public static final String DEFAULT_PROTOCOL = "http";
    }
  }
}
