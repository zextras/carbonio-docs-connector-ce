package com.zextras.carbonio.docs_connector;

public final class Constants {

  private Constants() {}

  public static final class Service {

    private Service() {}

    public static final String IP = "127.78.0.13";
    public static final int PORT = 10_000;
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
    public static final String REQUESTER_LOCALE = "requester-locale";
  }

  public static final class Config {

    private Config() {}

    public static final String ACCEPTED_COOKIE_TYPE = "ZM_AUTH_TOKEN";

    public static final class FilesService {

      private FilesService() {}

      public static final String PROTOCOL = "http";
      public static final String URL = "127.78.0.13";
      public static final int PORT = 20000;
    }

    public static final class UserService {

      private UserService() {}

      public static final String PROTOCOL = "http";
      public static final String URL = "127.78.0.13";
      public static final int PORT = 20001;
      public static final String ZM_AUTH_TOKEN = "ZM_AUTH_TOKEN=";
    }
  }
}
