package com.zextras.carbonio.docs_connector.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import com.zextras.carbonio.docs_connector.entities.files.graphql.NodeAttributes;
import com.zextras.carbonio.docs_connector.generated.model.DocsEditorAttributes;
import com.zextras.carbonio.docs_connector.generated.model.NodeUpdatedTimestamp;
import com.zextras.carbonio.files.FilesClient;
import com.zextras.carbonio.files.entities.FilesBlob;
import com.zextras.carbonio.files.entities.NodeIdVersion;
import com.zextras.carbonio.usermanagement.UserManagementClient;
import com.zextras.carbonio.usermanagement.entities.UserInfo;
import io.vavr.control.Try;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WopiService {

  private static final Logger logger = LoggerFactory.getLogger(WopiService.class);

  private final UserManagementClient userManagementClient;
  private final FilesClient filesClient;

  @Inject
  public WopiService(UserManagementClient userManagementClient, FilesClient filesClient) {
    this.userManagementClient = userManagementClient;
    this.filesClient = filesClient;
  }

  public Optional<DocsEditorAttributes> getDocsEditorAttributes(
    String requesterId,
    String requesterCookie,
    UUID nodeId,
    Optional<Integer> optVersion
  ) {
    UserInfo userInfo = userManagementClient
      .getUserById(requesterCookie, requesterId)
      .onFailure(
        failure -> logger.error("Unable to retrieve user info of user id {}", requesterId, failure))
      .getOrElseThrow(() -> new NoSuchElementException()); // Think more about it

    return Optional.ofNullable(
      filesClient
        .genericGraphQLRequest(
          requesterCookie,
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
            docsEditorAttributes.setUserId(UUID.fromString(userInfo.getId().getUserId()));
            docsEditorAttributes.setUserFriendlyName(userInfo.getFullName());
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
            logger.error(exception.getMessage(), exception);
            return null;
          }
        })
        .onFailure(failure -> logger.error(failure.getMessage(), failure))
        .getOrNull()
    );
  }

  public Optional<FilesBlob> getBlob(
    String cookie,
    UUID nodeId,
    Optional<Integer> optVersion
  ) {

    return Optional.ofNullable(
      filesClient
        .downloadFile(cookie, nodeId.toString(), optVersion)
        .onFailure(failure -> logger.error(failure.getMessage(), failure))
        .getOrNull()
    );
  }

  public Optional<NodeUpdatedTimestamp> saveBlob(
    String cookie,
    UUID nodeId,
    InputStream blob,
    long contentLength,
    boolean coolIsAutosave
  ) {

    Try<NodeIdVersion> uploadedNode = filesClient
      .genericGraphQLRequest(
        cookie,
        NodeAttributes.getNodeGraphQLRequest(nodeId.toString(), Optional.empty())
      )
      .map(graphQLResponse -> {
        try {
          NodeAttributes nodeAttributes = NodeAttributes.mapFromJSON(graphQLResponse);

          return filesClient
            .uploadFileVersion(
              cookie,
              nodeId.toString(),
              createFullFilename(nodeAttributes.getName(), nodeAttributes.getExtension()),
              nodeAttributes.getMime_type(),
              blob,
              contentLength,
              coolIsAutosave
            )
            .onFailure(failure -> logger.error("Saving blob failed: " + failure));

        } catch (JsonProcessingException exception) {
          logger.error(exception.getMessage(), exception);
          return null;
        }
      })
      .onFailure(failure -> logger.error(failure.getMessage(), failure))
      .get();

    if (uploadedNode.isSuccess()) {
      /*
       * Retrieve the last update timestamp of the saved file
       */
      return Optional.ofNullable(
        filesClient
          .genericGraphQLRequest(
            cookie,
            NodeAttributes.getNodeGraphQLRequest(nodeId.toString(), Optional.empty())
          )
          .map(graphQLResponse -> {
            try {
              NodeAttributes nodeAttributes = NodeAttributes.mapFromJSON(graphQLResponse);

              NodeUpdatedTimestamp updatedTimestamp = new NodeUpdatedTimestamp();
              updatedTimestamp.setLastModifiedTime(
                formatDateToIso8601(new Date(nodeAttributes.getUpdated_at()))
              );

              return updatedTimestamp;

            } catch (JsonProcessingException exception) {
              logger.error(exception.getMessage(), exception);
              return null;
            }
          })
          .onFailure(failure -> logger.error(failure.getMessage(), failure))
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
