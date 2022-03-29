package com.zextras.carbonio.docs_connector.generated;

import com.zextras.carbonio.docs_connector.generated.model.*;
import com.zextras.carbonio.docs_connector.generated.WopiApiService;

import io.swagger.annotations.ApiParam;
import io.swagger.jaxrs.*;

import com.zextras.carbonio.docs_connector.generated.model.DocsEditorAttributes;
import java.io.File;
import java.util.UUID;

import java.util.Map;
import java.util.List;
import com.zextras.carbonio.docs_connector.generated.NotFoundException;

import java.io.InputStream;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.*;
import javax.inject.Inject;

import javax.validation.constraints.*;
import javax.validation.Valid;

@Path("/wopi/{nodeId}")


@io.swagger.annotations.Api(description = "the wopi API")
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen", date = "2022-03-29T11:16:15.397757+02:00[Europe/Rome]")
public class WopiApi  {

    @Inject WopiApiService service;

    @GET
    
    
    @Produces({ "application/json" })
    @io.swagger.annotations.ApiOperation(value = "", notes = "Retrieve docs-editor attributes", response = DocsEditorAttributes.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "All the attributes necessary to carbonio-docs-editor in order to open a file correctly", response = DocsEditorAttributes.class) })
    public Response docsEditorAttributes( @NotNull  @QueryParam("access_token") String accessToken, @PathParam("nodeId") UUID nodeId,  @QueryParam("version") Integer version,@Context SecurityContext securityContext)
    throws NotFoundException {
        return service.docsEditorAttributes(accessToken,nodeId,version,securityContext);
    }
    @GET
    @Path("/contents")
    
    @Produces({ "application/octet-stream" })
    @io.swagger.annotations.ApiOperation(value = "", notes = "Retrieve blob of a specific Files node", response = File.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "Return the requested blob", response = File.class) })
    public Response getBlob( @NotNull  @QueryParam("access_token") String accessToken, @PathParam("nodeId") UUID nodeId,  @QueryParam("version") Integer version,@Context SecurityContext securityContext)
    throws NotFoundException {
        return service.getBlob(accessToken,nodeId,version,securityContext);
    }
    @POST
    @Path("/contents")
    @Consumes({ "application/octet-stream" })
    @Produces({ "application/json" })
    @io.swagger.annotations.ApiOperation(value = "", notes = "Save the updated blob of a specific Files node", response = DocsEditorAttributes.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "All the attributes necessary to carbonio-docs-editor in order to open a file correctly", response = DocsEditorAttributes.class) })
    public Response saveBlob( @NotNull  @QueryParam("access_token") String accessToken, @NotNull  @ApiParam(value = "" ,required=true) @HeaderParam("Content-Length") Long contentLength, @PathParam("nodeId") UUID nodeId,@ApiParam(value = "Save the blob" ) @Valid InputStream body,@Context SecurityContext securityContext)
    throws NotFoundException {
        return service.saveBlob(accessToken,contentLength,nodeId,body,securityContext);
    }
}
