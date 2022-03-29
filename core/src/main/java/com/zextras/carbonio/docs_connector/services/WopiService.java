package com.zextras.carbonio.docs_connector.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.zextras.carbonio.docs_connector.entities.files.graphql.NodeAttributes;
import com.zextras.carbonio.docs_connector.generated.model.DocsEditorAttributes;
import com.zextras.carbonio.docs_connector.services.utilities.OpenDocumentToken;
import com.zextras.carbonio.files.FilesClient;
import com.zextras.carbonio.files.entities.FilesBlob;
import com.zextras.carbonio.files.entities.NodeId;
import io.vavr.control.Try;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WopiService {

  private static final Logger logger          = LoggerFactory.getLogger(WopiService.class);
  private static final String filesServiceURL = "http://127.78.0.11:20000";

  public Optional<DocsEditorAttributes> getDocsEditorAttributes(
    OpenDocumentToken token,
    UUID nodeId,
    Optional<Integer> optVersion
  ) {

    return Optional.ofNullable(
      FilesClient
        .atURL(filesServiceURL)
        .genericGraphQLRequest(
          token.getRequesterCookies(),
          NodeAttributes.getNodeGraphQLRequest(nodeId.toString(), optVersion)
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

  public Optional<FilesBlob> getBlob(
    String cookie,
    UUID nodeId,
    Optional<Integer> optVersion
  ) {
    return Optional.ofNullable(
      FilesClient
        .atURL(filesServiceURL)
        .downloadFile(cookie, nodeId.toString(), optVersion)
        .onFailure(failure -> logger.error(failure.getMessage()))
        .getOrNull()
    );
  }

  public Optional<DocsEditorAttributes> saveBlob(
    String cookie,
    UUID nodeId,
    InputStream blob
  ) {

    Try<NodeId> uploadedNodeId = FilesClient
      .atURL(filesServiceURL)
      .genericGraphQLRequest(
        cookie,
        NodeAttributes.getNodeGraphQLRequest(nodeId.toString(), Optional.empty())
      )
      .map(graphQLResponse -> {
        try {
          NodeAttributes nodeAttributes = NodeAttributes.mapFromJSON(graphQLResponse);

          return FilesClient
            .atURL(filesServiceURL)
            .uploadFileVersion(
              cookie,
              nodeId.toString(),
              createFullFilename(nodeAttributes.getName(), nodeAttributes.getExtension()),
              nodeAttributes.getMime_type(),
              blob,
              false
            )
            .onFailure(failure -> logger.error("Saving blob failed: " + failure));

        } catch (JsonProcessingException exception) {
          logger.error(exception.getMessage());
          return null;
        }
      })
      .onFailure(failure -> logger.error(failure.getMessage()))
      .getOrNull();

    if (uploadedNodeId.isSuccess()) {
      /*
       * Retrieve the last update timestamp of the saved file
       */
      return Optional.ofNullable(
        FilesClient
          .atURL(filesServiceURL)
          .genericGraphQLRequest(
            cookie,
            NodeAttributes.getNodeGraphQLRequest(nodeId.toString(), Optional.empty())
          )
          .map(graphQLResponse -> {
            try {
              NodeAttributes nodeAttributes = NodeAttributes.mapFromJSON(graphQLResponse);

              DocsEditorAttributes docsEditorAttributes = new DocsEditorAttributes();
              docsEditorAttributes.setLastModifiedTime(
                formatDateToIso8601(new Date(nodeAttributes.getUpdated_at()))
              );

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

    return Optional.empty();
  }

  private String formatDateToIso8601(Date modifiedTime) {
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

    return dateFormat.format(modifiedTime);
  }

  private String createFullFilename(
    String name,
    String extension
  ) {
    return (extension == null)
      ? name
      : name + "." + extension;
  }

  private String abbreviateFilename(
    String name,
    String extension
  ) {
    String fullFilename = createFullFilename(name, extension);

    return (fullFilename.length() > 64)
      ? createFullFilename(name.substring(0, 50), extension)
      : fullFilename;
  }
}
