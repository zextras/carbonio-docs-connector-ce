package com.zextras.carbonio.docs_connector.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.zextras.carbonio.docs_connector.entities.files.graphql.NodeAttributes;
import com.zextras.carbonio.docs_connector.generated.model.DocsEditorAttributes;
import com.zextras.carbonio.docs_connector.services.utilities.OpenDocumentToken;
import com.zextras.carbonio.files.FilesClient;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WopiService {

  private static final Logger logger = LoggerFactory.getLogger(WopiService.class);

  public Optional<DocsEditorAttributes> getDocsEditorAttributes(
    OpenDocumentToken token,
    UUID nodeId,
    Optional<Integer> optVersion
  ) {

    return Optional.ofNullable(
      FilesClient
        .atURL("http://127.78.0.11:20000")
        .genericGraphQLRequest(
          token.getRequesterCookies(),
          NodeAttributes.getGraphQLRequest(nodeId.toString(), optVersion)
        ).map(graphQLResponse -> {

          try {
            NodeAttributes nodeAttributes = NodeAttributes.mapFromJSON(graphQLResponse);

            String lastModifiedTimeFormatted = formatDateToIso8601(
              new Date(nodeAttributes.getUpdated_at())
            );

            String abbreviateFilename = abbreviateFilename(
              nodeAttributes.getName(),
              nodeAttributes.getExtension()
            );

            UUID nodeOwnerId = UUID.fromString(nodeAttributes.getOwner().getId());

            DocsEditorAttributes docsEditorAttributes = new DocsEditorAttributes();
            docsEditorAttributes.setOwnerId(nodeOwnerId);
            docsEditorAttributes.setUserId(nodeOwnerId);
            //docsEditorAttributes.setUserFriendlyName(nodeAttributes.getOwner().getFull_name());
            docsEditorAttributes.setUserCanWrite(nodeAttributes.getPermissions().getCan_write_file());
            docsEditorAttributes.setBaseFileName(abbreviateFilename);
            docsEditorAttributes.setVersion(nodeAttributes.getVersion());
            docsEditorAttributes.setSize(nodeAttributes.getSize());
            docsEditorAttributes.setLastModifiedTime(lastModifiedTimeFormatted);
            docsEditorAttributes.setEnableOwnerTermination(false);
            docsEditorAttributes.setDisableCopy(false);
            docsEditorAttributes.setDisableExport(false);
            docsEditorAttributes.setDisablePrint(false);
            docsEditorAttributes.setDisableInactiveMessages(true);
            docsEditorAttributes.setHideExportOption(false);
            docsEditorAttributes.setHideSaveOption(
              !nodeAttributes.getPermissions().getCan_write_file()
            );
            docsEditorAttributes.setHidePrintOption(false);
            docsEditorAttributes.setHideChangeTrackingControls(false);
            docsEditorAttributes.setUserCanNotWriteRelative(true);
            docsEditorAttributes.setUserCanRename(false);
            docsEditorAttributes.setSupportsLocks(false);

            return docsEditorAttributes;

          } catch (JsonProcessingException exception) {
            logger.error(exception.getMessage());
            return null;
          }
        })
        .onFailure(failure -> logger.error(failure.getMessage()))
        .getOrNull()
    );
  }

  private String formatDateToIso8601(Date modifiedTime) {
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    return dateFormat.format(
      modifiedTime
    );
  }

  private String abbreviateFilename(
    String name,
    String extensions
  ) {
    return name + Optional.ofNullable(extensions).map(ext -> "." + ext).orElse("");
  }
}
