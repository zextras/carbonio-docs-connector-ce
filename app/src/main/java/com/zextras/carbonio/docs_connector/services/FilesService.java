// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.services;

import com.zextras.carbonio.docs_connector.Constants;
import com.zextras.carbonio.docs_connector.cluster.DocsEditorInstanceSelector;
import com.zextras.carbonio.docs_connector.config.DocsConnectorServiceConfig;
import com.zextras.carbonio.docs_connector.dal.dao.OpenDocumentToken;
import com.zextras.carbonio.docs_connector.dal.repositories.interfaces.OpenDocumentTokenRepository;
import com.zextras.carbonio.docs_connector.entities.files.graphql.NodeAttributes;
import com.zextras.carbonio.docs_connector.exceptions.AccountOverQuotaException;
import com.zextras.carbonio.docs_connector.exceptions.FileSizeTooLargeException;
import com.zextras.carbonio.docs_connector.exceptions.ServiceDependencyException;
import com.zextras.carbonio.docs_connector.services.utilities.TemplateUtils;
import com.zextras.carbonio.docs_connector.types.CreatedFile;
import com.zextras.carbonio.docs_connector.types.FileType.GenericFileType;
import com.zextras.carbonio.docs_connector.types.InsertFile;
import com.zextras.carbonio.files.FilesClient;
import com.zextras.carbonio.files.exceptions.AccountInOverQuota;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ApplicationConfigService;
import com.zextras.carbonio.quarkus.extensions.bootstrap.NetworkingConfigService;
import io.vavr.control.Try;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class FilesService {

  private static final Logger logger = LoggerFactory.getLogger(FilesService.class);
  private static final long MEGA_BYTE = 1024 * 1024;

  private final OpenDocumentTokenRepository openDocumentTokenRepository;
  private final ApplicationConfigService applicationConfig;
  private final NetworkingConfigService networkingConfig;
  private final FilesClient filesClient;
  private final QuotaChecker quotaChecker;
  private final DocsEditorInstanceSelector docsEditorInstanceSelector;

  @Inject
  public FilesService(
      OpenDocumentTokenRepository openDocumentTokenRepository,
      ApplicationConfigService applicationConfig,
      NetworkingConfigService networkingConfig,
      FilesClient filesClient,
      QuotaChecker quotaChecker,
      DocsEditorInstanceSelector docsEditorInstanceSelector) {
    this.openDocumentTokenRepository = openDocumentTokenRepository;
    this.applicationConfig = applicationConfig;
    this.networkingConfig = networkingConfig;
    this.filesClient = filesClient;
    this.quotaChecker = quotaChecker;
    this.docsEditorInstanceSelector = docsEditorInstanceSelector;
  }

  public String openFile(
      String requesterId,
      Locale requesterLocale,
      String cookie,
      String nodeId,
      Optional<Integer> optVersion,
      Optional<Integer> optOffsetFromUtc
  ) throws ServiceDependencyException, FileSizeTooLargeException {

    NodeAttributes nodeAttributes = filesClient
        .genericGraphQLRequest(cookie, NodeAttributes.getNodeGraphQLRequest(nodeId, optVersion))
        .flatMap(graphQLResponse -> Try.of(() -> NodeAttributes.mapFromJSON(graphQLResponse)))
        .getOrElseThrow(ServiceDependencyException::new);

    GenericFileType fileType = GenericFileType.fromMimeType(nodeAttributes.getMime_type());
    long maxFileSizeInMb = getMaxSizeLimitForFileType(fileType);
    if (nodeAttributes.getSize() > maxFileSizeInMb * MEGA_BYTE) {
      String message = "File %s with mime type %s and size %d is too large to open".formatted(
          nodeId,
          nodeAttributes.getMime_type(),
          nodeAttributes.getSize()
      );

      logger.info(message);
      throw new FileSizeTooLargeException(message, maxFileSizeInMb);
    }

    OpenDocumentToken openDocumentToken = openDocumentTokenRepository
        .createToken(UUID.fromString(nodeId), requesterId, cookie);

    // Instance selection (Advanced: sticky routing via Consul + DB; CE: empty = static config)
    Optional<UUID> selectedInstanceId =
        docsEditorInstanceSelector.selectInstance(UUID.fromString(nodeId));

    // WopiSRC
    String wopiProtocol = Constants.Config.Wopi.DEFAULT_PROTOCOL;
    String wopiHost = networkingConfig
        .get(DocsConnectorServiceConfig.NetworkingConfig.WOPI_HOST)
        .orElseThrow();
    String wopiPort = networkingConfig
        .get(DocsConnectorServiceConfig.NetworkingConfig.WOPI_PORT)
        .orElseThrow();

    StringBuilder wopiEndpointBuilder = new StringBuilder()
        .append(wopiProtocol).append("://").append(wopiHost).append(":").append(wopiPort)
        .append("/wopi/")
        .append(nodeId);

    // Query params order: version -> service_id -> offset_from_utc.
    // Collect present params then join with "&" so there is never a trailing "&".
    List<String> wopiParams = new ArrayList<>();
    optVersion.ifPresent(version -> wopiParams.add("version=" + version));
    selectedInstanceId.ifPresent(
        instanceId -> wopiParams.add("service_id=" + instanceId));
    optOffsetFromUtc.ifPresent(
        offsetFromUtc -> wopiParams.add("offset_from_utc=" + offsetFromUtc));
    if (!wopiParams.isEmpty()) {
      wopiEndpointBuilder.append("?").append(String.join("&", wopiParams));
    }

    // Public URL, it contains the redirect=true to allow the controller to return 307. Optionally,
    // it can contain the version:
    // - services/docs/files/open/<node_id>?redirect=true
    // - services/docs/files/open/<node_id>?version=<version>&redirect=true
    StringBuilder publicURLBuilder = new StringBuilder()
        .append("services/docs/files/open/")
        .append(nodeId)
        .append("?");

    optVersion
        .map(version -> publicURLBuilder.append("version=").append(version).append("&"));

    publicURLBuilder.append("redirect=true");

    // Cool html resource + token parameter
    StringBuilder docsPathAndParametersBuilder = new StringBuilder()
        .append("services/docs/editor/browser/dist/cool.html")
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
    boolean overQuota = quotaChecker.isOverQuota(
        nodeAttributes.getOwner().getId(), cookie);
    if (!nodeAttributes.getPermissions().getCan_write_file()
        || optVersion.isPresent()
        || overQuota) {
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

    selectedInstanceId.ifPresent(instanceId ->
        docsPathAndParametersBuilder.append("&service_id=").append(instanceId));

    logger.info(docsPathAndParametersBuilder.toString());
    return docsPathAndParametersBuilder.toString();
  }

  public Optional<CreatedFile> uploadTemplate(
      String cookie,
      InsertFile docsFile
  ) throws AccountOverQuotaException {
    Optional<byte[]> optTemplateRaw = TemplateUtils.getTemplateRaw(docsFile.getType());
    if (optTemplateRaw.isEmpty()) {
      return Optional.empty();
    }

    byte[] templateRaw = optTemplateRaw.get();
    Try<CreatedFile> result = filesClient
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
        });

    if (result.isFailure()) {
      Throwable cause = result.getCause();
      if (cause instanceof AccountInOverQuota) {
        throw new AccountOverQuotaException("Account is over quota", cause);
      }
      logger.error("Failed to upload the template: " + cause.getMessage(), cause);
      return Optional.empty();
    }

    return Optional.ofNullable(result.get());
  }

  /**
   * Resolves the max file size limit (in MB) for the given file type from Consul KV via
   * ApplicationConfigService. Defaults are guaranteed by {@code @ConfigKey(ifNotPresent = ...)}
   * on the config constants.
   */
  private long getMaxSizeLimitForFileType(GenericFileType fileType) {
    String configKey = switch (fileType) {
      case DOCUMENT -> DocsConnectorServiceConfig.ApplicationConfig.MAX_FILE_SIZE_MB_DOCUMENT;
      case PRESENTATION -> DocsConnectorServiceConfig.ApplicationConfig.MAX_FILE_SIZE_MB_PRESENTATION;
      case SPREADSHEET -> DocsConnectorServiceConfig.ApplicationConfig.MAX_FILE_SIZE_MB_SPREADSHEET;
    };
    return applicationConfig
        .get(configKey)
        .map(Long::parseLong)
        .orElseThrow();
  }
}
