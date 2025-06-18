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

    public static final String DEFAULT_HOST = "127.78.0.13";
    public static final int DEFAULT_PORT = 10_000;
    public static final String HOST_PROPERTY = "carbonio.docs-connector.host";
    public static final String PORT_PROPERTY = "carbonio.docs-connector.port";
    public static final String SERVICE_NAME = "carbonio-docs-connector";
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

    public static final Map<GenericFileType, Long> DEFAULT_MAX_FILE_SIZE_IN_MB_PER_TYPE =
      Map.of(
        GenericFileType.DOCUMENT, 50L,
        GenericFileType.PRESENTATION, 100L,
        GenericFileType.SPREADSHEET, 10L
      );

    public static final class Key {

      private Key() {}

      public static final String MAX_FILE_SIZE_IN_MB = "max-file-size-in-mb";
    }

    public static final class Files {

      private Files() {}

      public static final String DEFAULT_PROTOCOL = "http";
      public static final String DEFAULT_HOST = "127.78.0.13";
      public static final String HOST_PROPERTY = "carbonio.files.host";
      public static final String PORT_PROPERTY = "carbonio.files.port";
      public static final int DEFAULT_PORT = 20000;
    }

    public static final class UserManagement {

      private UserManagement() {}

      public static final String DEFAULT_PROTOCOL = "http";
      public static final String DEFAULT_HOST = "127.78.0.13";
      public static final String HOST_PROPERTY = "carbonio.user-management.host";
      public static final String PORT_PROPERTY = "carbonio.user-management.port";
      public static final int DEFAULT_PORT = 20001;
      public static final String ZM_AUTH_TOKEN = "ZM_AUTH_TOKEN=";
    }
  }
}
