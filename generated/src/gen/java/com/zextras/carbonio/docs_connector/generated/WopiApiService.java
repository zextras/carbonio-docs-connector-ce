package com.zextras.carbonio.docs_connector.generated;

import com.zextras.carbonio.docs_connector.generated.*;
import com.zextras.carbonio.docs_connector.generated.model.*;


import com.zextras.carbonio.docs_connector.generated.model.DocsEditorAttributes;
import java.io.File;
import com.zextras.carbonio.docs_connector.generated.model.NodeUpdatedTimestamp;
import java.util.UUID;

import java.util.List;
import com.zextras.carbonio.docs_connector.generated.NotFoundException;

import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen", date = "2022-03-31T15:35:04.410508+02:00[Europe/Rome]")
public interface WopiApiService {
      Response docsEditorAttributes(String accessToken,UUID nodeId,Integer version,SecurityContext securityContext,HttpServletRequest httpRequest)
      throws NotFoundException;
      Response getBlob(String accessToken,UUID nodeId,Integer version,SecurityContext securityContext,HttpServletRequest httpRequest)
      throws NotFoundException;
      Response saveBlob(UUID nodeId,String accessToken,Boolean xCOOLWOPIIsAutosave,Boolean xCOOLWOPIIsExitSave,Long contentLength,InputStream body,SecurityContext securityContext,HttpServletRequest httpRequest)
      throws NotFoundException;
}
