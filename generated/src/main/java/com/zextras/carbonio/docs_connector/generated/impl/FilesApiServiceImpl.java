package com.zextras.carbonio.docs_connector.generated.impl;

import com.zextras.carbonio.docs_connector.generated.*;
import com.zextras.carbonio.docs_connector.generated.model.*;


import com.zextras.carbonio.docs_connector.generated.model.CreatedFile;
import com.zextras.carbonio.docs_connector.generated.model.InsertFile;
import java.util.UUID;

import java.util.List;
import com.zextras.carbonio.docs_connector.generated.NotFoundException;

import java.io.InputStream;

import javax.enterprise.context.RequestScoped;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@RequestScoped
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen", date = "2023-04-22T12:43:31.729605+02:00[Europe/Rome]")
public class FilesApiServiceImpl implements FilesApiService {
      public Response createFile(String cookie,InsertFile insertFile,SecurityContext securityContext, HttpServletRequest httpRequest)
      throws NotFoundException {
      // do some magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "magic!")).build();
  }
      public Response openFile(String cookie,UUID nodeId,Integer version,SecurityContext securityContext, HttpServletRequest httpRequest)
      throws NotFoundException {
      // do some magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "magic!")).build();
  }
}
