package com.zextras.carbonio.docs_connector.services;

import com.google.inject.Inject;
import com.zextras.carbonio.docs_connector.config.DocsConnectorConfig;
import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import com.zextras.carbonio.docs_connector.dal.repositories.interfaces.OpenDocumentTokenRepository;
import com.zextras.carbonio.docs_connector.entities.files.graphql.NodeAttributes;
import com.zextras.carbonio.docs_connector.exceptions.FileSizeTooLargeException;
import com.zextras.carbonio.docs_connector.exceptions.ServiceDependencyException;
import com.zextras.carbonio.docs_connector.services.utilities.TemplateUtils;
import com.zextras.carbonio.docs_connector.types.CreatedFile;
import com.zextras.carbonio.docs_connector.types.FileType.GenericFileType;
import com.zextras.carbonio.docs_connector.types.InsertFile;
import com.zextras.carbonio.files.FilesClient;
import io.vavr.control.Try;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilesService {

  private static final Logger logger = LoggerFactory.getLogger(FilesService.class);

  private final OpenDocumentTokenRepository openDocumentTokenRepository;
  private final DocsConnectorConfig config;
  private final FilesClient filesClient;

  @Inject
  public FilesService(
    OpenDocumentTokenRepository openDocumentTokenRepository,
    DocsConnectorConfig config,
    FilesClient filesClient
  ) {
    this.openDocumentTokenRepository = openDocumentTokenRepository;
    this.config = config;
    this.filesClient = filesClient;
  }

  public String openFile(
    String requesterId,
    Locale requesterLocale,
    String cookie,
    String nodeId,
    Optional<Integer> optVersion
  ) throws ServiceDependencyException, FileSizeTooLargeException {

    NodeAttributes nodeAttributes = filesClient
      .genericGraphQLRequest(cookie, NodeAttributes.getNodeGraphQLRequest(nodeId, optVersion))
      .flatMap(graphQLResponse -> Try.of(() -> NodeAttributes.mapFromJSON(graphQLResponse)))
      .getOrElseThrow(ServiceDependencyException::new);

    GenericFileType fileType = GenericFileType.fromMimeType(nodeAttributes.getMime_type());
    long maxFileSizeInMb = config.getMaxSizeLimitForDocumentType(fileType);
    if (nodeAttributes.getSize() >  maxFileSizeInMb) {
      throw new FileSizeTooLargeException(
        "File %s with mime type %s and size %d is too large to open".formatted(
            nodeId,
            nodeAttributes.getMime_type(),
            nodeAttributes.getSize()),
        maxFileSizeInMb
      );
    }

    OpenDocumentToken openDocumentToken = openDocumentTokenRepository
      .createToken(UUID.fromString(nodeId), requesterId, cookie);

    // WopiSRC
    StringBuilder wopiEndpointBuilder = new StringBuilder()
      .append("http://127.78.0.12:20000/wopi/")
      .append(nodeId);

    optVersion
      .map(version -> wopiEndpointBuilder.append("?version=").append(version));

    // Public URL
    StringBuilder publicURLBuilder = new StringBuilder()
      .append("docs/editor/")
      .append(nodeId);

    // Cool html resource + token parameter
    StringBuilder docsPathAndParametersBuilder = new StringBuilder()
      .append("editor/browser/dist/cool.html")
      .append("?access_token=")
      .append(openDocumentToken.getTokenId())
      .append("&access_token_ttl=")
      .append(openDocumentToken.getExpirationTimestamp().toEpochMilli());

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

    docsPathAndParametersBuilder.append("&lang=").append(requesterLocale.toLanguageTag());

    logger.info(docsPathAndParametersBuilder.toString());
    return docsPathAndParametersBuilder.toString();
  }

  public Optional<CreatedFile> uploadTemplate(
    String cookie,
    InsertFile docsFile
  ) {

    return TemplateUtils.getTemplateRaw(docsFile.getType())
      .map(templateRaw ->
        filesClient
          .uploadFile(
            cookie,
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
            logger.error("Failed to upload the template: " + failure.getMessage(), failure)
          )
          .getOrNull()
      );
  }
}
