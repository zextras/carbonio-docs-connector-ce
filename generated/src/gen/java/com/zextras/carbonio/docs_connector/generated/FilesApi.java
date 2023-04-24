package com.zextras.carbonio.docs_connector.generated;

import com.zextras.carbonio.docs_connector.generated.model.*;
import com.zextras.carbonio.docs_connector.generated.FilesApiService;

import io.swagger.annotations.ApiParam;
import io.swagger.jaxrs.*;

import com.zextras.carbonio.docs_connector.generated.model.CreatedFile;
import com.zextras.carbonio.docs_connector.generated.model.InsertFile;
import java.util.UUID;

import java.util.Map;
import java.util.List;
import com.zextras.carbonio.docs_connector.generated.NotFoundException;

import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.*;
import javax.inject.Inject;

import javax.validation.constraints.*;
import javax.validation.Valid;

@Path("/files")


@io.swagger.annotations.Api(description = "the files API")
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen", date = "2023-04-21T12:17:09.016050+02:00[Europe/Rome]")
public class FilesApi  {

    @Inject FilesApiService service;

    @POST
    @Path("/create")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @io.swagger.annotations.ApiOperation(value = "", notes = "Create a new empty Docs file", response = CreatedFile.class, tags={ "files", })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "The metadata of the new created Docs file", response = CreatedFile.class) })
    public Response createFile( @NotNull  @ApiParam(value = "" ,required=true) @HeaderParam("Cookie") String cookie,@ApiParam(value = "New Docs file metadata" ) @Valid InsertFile insertFile,@Context SecurityContext securityContext,@Context HttpServletRequest httpRequest)
    throws NotFoundException {
        return service.createFile(cookie,insertFile,securityContext,httpRequest);
    }
    @GET
    @Path("/open/{nodeId}")
    
    
    @io.swagger.annotations.ApiOperation(value = "", notes = "Open an existing file with Docs. When the version is not specified, it will open the last one", response = Void.class, tags={ "files", })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 307, message = "URL necessary to redirect to an url that talks with carbonio-docs-editor", response = Void.class) })
    public Response openFile( @NotNull  @ApiParam(value = "" ,required=true) @HeaderParam("Cookie") String cookie, @PathParam("nodeId") UUID nodeId,  @QueryParam("version") Integer version,@Context SecurityContext securityContext,@Context HttpServletRequest httpRequest)
    throws NotFoundException {
        return service.openFile(cookie,nodeId,version,securityContext,httpRequest);
    }
}
