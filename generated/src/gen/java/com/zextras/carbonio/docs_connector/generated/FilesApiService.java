package com.zextras.carbonio.docs_connector.generated;

import com.zextras.carbonio.docs_connector.generated.*;
import com.zextras.carbonio.docs_connector.generated.model.*;


import com.zextras.carbonio.docs_connector.generated.model.CreatedFile;
import com.zextras.carbonio.docs_connector.generated.model.InsertFile;
import java.util.UUID;

import java.util.List;
import com.zextras.carbonio.docs_connector.generated.NotFoundException;

import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen", date = "2023-04-21T12:17:09.016050+02:00[Europe/Rome]")
public interface FilesApiService {
      Response createFile(String cookie,InsertFile insertFile,SecurityContext securityContext,HttpServletRequest httpRequest)
      throws NotFoundException;
      Response openFile(String cookie,UUID nodeId,Integer version,SecurityContext securityContext,HttpServletRequest httpRequest)
      throws NotFoundException;
}
