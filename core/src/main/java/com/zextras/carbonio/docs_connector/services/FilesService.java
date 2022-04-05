package com.zextras.carbonio.docs_connector.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import com.zextras.carbonio.docs_connector.cache.CacheManager;
import com.zextras.carbonio.docs_connector.entities.files.graphql.NodeAttributes;
import com.zextras.carbonio.docs_connector.generated.model.CreatedFile;
import com.zextras.carbonio.docs_connector.generated.model.InsertFile;
import com.zextras.carbonio.docs_connector.services.utilities.OpenDocumentToken;
import com.zextras.carbonio.docs_connector.services.utilities.TemplateUtils;
import com.zextras.carbonio.files.FilesClient;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilesService {

  private static final Logger logger          = LoggerFactory.getLogger(FilesService.class);
  private static final String filesServiceURL = "http://127.78.0.13:20000";

  private final CacheManager cacheManager;

  @Inject
  public FilesService(CacheManager cacheManager) {this.cacheManager = cacheManager;}

  public Optional<String> openFile(
    String nodeId,
    Optional<Integer> optVersion,
    String cookies
  ) {

    return Optional.ofNullable(
      FilesClient
        .atURL(filesServiceURL)
        .genericGraphQLRequest(cookies, NodeAttributes.getNodeGraphQLRequest(nodeId, optVersion))
        .map(graphQLResponse -> {
          try {
            NodeAttributes nodeAttributes = NodeAttributes.mapFromJSON(graphQLResponse);

            OpenDocumentToken token = new OpenDocumentToken(
              UUID.randomUUID(),
              UUID.fromString(nodeId),
              cookies,
              System.currentTimeMillis() + cacheManager.getTokenDurationInMs()
            );

            cacheManager.getTokenCache().put(token.getTokenId().toString(), token);

            // WopiSRC
            StringBuilder wopiEndpointBuilder = new StringBuilder()
              .append("http://127.78.0.13:10000/wopi/")
              .append(nodeId);

            optVersion
              .map(version -> wopiEndpointBuilder.append("?version=").append(version).append("&"))
              .orElseGet(() -> wopiEndpointBuilder.append("?"));

            // The & or ? connector is appended before
            wopiEndpointBuilder
              .append("access_token=")
              .append(token.getTokenId());

            // Public URL
            StringBuilder publicURLBuilder = new StringBuilder()
              .append("docs/editor/")
              .append(nodeId);

            // Cool html resource + token parameter
            StringBuilder docsPathAndParametersBuilder = new StringBuilder()
              .append("editor/browser/dist/cool.html")
              .append("?access_token=")
              .append(token.getTokenId())
              .append("&access_token_ttl=")
              .append(token.getExpirationTimestamp());

            /*
             * If the version is specified then the document should be opened in read only.
             * This is a conservative choice to avoid corner case when the last version is already
             * opened and another user tries to edit a specific version causing conflicts.
             *
             * This is a temporary solution and if the client requests a specific version then it
             * will be opened in read only even if it should be editable
             */
            if (!nodeAttributes.getPermissions().getCan_write_file() || optVersion.isPresent()) {
              docsPathAndParametersBuilder.append("&permission=readonly");
            }

            // Document title parameter
            docsPathAndParametersBuilder
              .append("&title=")
              .append(URLEncoder.encode(
                nodeAttributes.getName().replaceAll(" ", "_"),
                StandardCharsets.UTF_8
              ));

            // UI parameters
            docsPathAndParametersBuilder
              .append("&ui_defaults=UIMode=classic;")
              .append("UIMode=classic;")
              .append("TextSidebar=false;")
              .append("PresentationSidebar=false;")
              .append("SpreadsheetSidebar=false");

            docsPathAndParametersBuilder
              .append("&WOPISrc=")
              .append(URLEncoder.encode(wopiEndpointBuilder.toString(), StandardCharsets.UTF_8));

            docsPathAndParametersBuilder
              .append("&public_url=")
              .append(URLEncoder.encode(publicURLBuilder.toString(), StandardCharsets.UTF_8));

            logger.info(docsPathAndParametersBuilder.toString());
            return docsPathAndParametersBuilder.toString();

          } catch (JsonProcessingException exception) {
            logger.error(exception.getMessage());
            return null;
          }
        })
        .onFailure(failure -> logger.error(failure.getMessage()))
        .getOrNull()
    );
  }

  public Optional<CreatedFile> uploadTemplate(
    String cookies,
    InsertFile docsFile
  ) {

    return TemplateUtils.getTemplateRaw(docsFile.getType())
      .map(templateRaw ->
        FilesClient
          .atURL(filesServiceURL)
          .uploadFile(
            cookies,
            docsFile.getDestinationFolderId(),
            TemplateUtils.appendExtensionByType(docsFile.getType(), docsFile.getFilename()),
            TemplateUtils.detectMimeTypeFrom(docsFile.getType()),
            new ByteArrayInputStream(templateRaw),
            templateRaw.length
          )
          .map(nodeId -> {
            CreatedFile createdFile = new CreatedFile();
            createdFile.setNodeId(UUID.fromString(nodeId.getNodeId()));
            return createdFile;
          })
          .onFailure(failure ->
            logger.error("Failed to upload the template: " + failure.getMessage())
          )
          .getOrNull()
      );
  }
}
