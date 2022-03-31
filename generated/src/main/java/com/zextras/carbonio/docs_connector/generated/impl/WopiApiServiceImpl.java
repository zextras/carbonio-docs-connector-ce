package com.zextras.carbonio.docs_connector.generated.impl;

import com.zextras.carbonio.docs_connector.generated.*;
import com.zextras.carbonio.docs_connector.generated.model.*;


import com.zextras.carbonio.docs_connector.generated.model.DocsEditorAttributes;
import java.io.File;
import com.zextras.carbonio.docs_connector.generated.model.NodeUpdatedTimestamp;
import java.util.UUID;

import java.util.List;
import com.zextras.carbonio.docs_connector.generated.NotFoundException;

import java.io.InputStream;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@RequestScoped
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen", date = "2022-03-31T15:35:04.410508+02:00[Europe/Rome]")
public class WopiApiServiceImpl implements WopiApiService {
      public Response docsEditorAttributes(String accessToken,UUID nodeId,Integer version,SecurityContext securityContext)
      throws NotFoundException {
      // do some magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "magic!")).build();
  }
      public Response getBlob(String accessToken,UUID nodeId,Integer version,SecurityContext securityContext)
      throws NotFoundException {
      // do some magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "magic!")).build();
  }
      public Response saveBlob(UUID nodeId,String accessToken,Boolean xCOOLWOPIIsAutosave,Boolean xCOOLWOPIIsExitSave,Long contentLength,InputStream body,SecurityContext securityContext)
      throws NotFoundException {
      // do some magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "magic!")).build();
  }
}
