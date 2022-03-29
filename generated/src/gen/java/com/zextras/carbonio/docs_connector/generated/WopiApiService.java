package com.zextras.carbonio.docs_connector.generated;

import com.zextras.carbonio.docs_connector.generated.*;
import com.zextras.carbonio.docs_connector.generated.model.*;


import com.zextras.carbonio.docs_connector.generated.model.DocsEditorAttributes;
import java.io.File;
import java.util.UUID;

import java.util.List;
import com.zextras.carbonio.docs_connector.generated.NotFoundException;

import java.io.InputStream;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen", date = "2022-03-29T11:16:15.397757+02:00[Europe/Rome]")
public interface WopiApiService {
      Response docsEditorAttributes(String accessToken,UUID nodeId,Integer version,SecurityContext securityContext)
      throws NotFoundException;
      Response getBlob(String accessToken,UUID nodeId,Integer version,SecurityContext securityContext)
      throws NotFoundException;
      Response saveBlob(String accessToken,Long contentLength,UUID nodeId,InputStream body,SecurityContext securityContext)
      throws NotFoundException;
}
