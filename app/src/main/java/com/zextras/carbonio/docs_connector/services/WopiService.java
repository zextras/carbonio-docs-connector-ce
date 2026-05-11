// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.services;

import static io.vavr.API.$;
import static io.vavr.API.Case;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.zextras.carbonio.docs_connector.clients.UserManagementClient;
import com.zextras.carbonio.docs_connector.entities.files.graphql.NodeAttributes;
import com.zextras.carbonio.docs_connector.exceptions.ServiceDependencyException;
import com.zextras.carbonio.docs_connector.types.DocsEditorAttributes;
import com.zextras.carbonio.docs_connector.types.NodeUpdatedTimestamp;
import com.zextras.carbonio.files.FilesClient;
import com.zextras.carbonio.files.entities.FilesBlob;
import com.zextras.carbonio.files.entities.NodeIdVersion;
import com.zextras.carbonio.files.exceptions.InternalServerError;
import com.zextras.carbonio.files.exceptions.UnAuthorized;
import com.zextras.carbonio.user_management.sdk.grpc.GetUserByIdRequest;
import com.zextras.carbonio.user_management.sdk.grpc.UserInfoProto;
import io.grpc.StatusRuntimeException;
import io.vavr.Predicates;
import io.vavr.control.Try;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
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
      Optional<Integer> optVersion,
      Optional<Integer> optOffsetFromUtc
  ) {
    UserInfoProto userInfo;
    try {
      GetUserByIdRequest request =
          GetUserByIdRequest.newBuilder().setUserId(requesterId).build();
      userInfo = userManagementClient.getBlockingStub().getUserById(request).getUser();
    } catch (StatusRuntimeException e) {
      logger.error("Unable to retrieve user info of user id {}", requesterId, e);
      throw new NoSuchElementException();
    }

    return Optional.ofNullable(
        filesClient
            .genericGraphQLRequest(
                requesterCookie,
                NodeAttributes.getNodeGraphQLRequest(nodeId.toString(), optVersion)
            ).map(graphQLResponse -> {

              try {
                NodeAttributes nodeAttributes = NodeAttributes.mapFromJSON(graphQLResponse);

                String lastModifiedTimeFormatted = formatDateToIso8601WithOffset(
                    new Date(nodeAttributes.getUpdated_at()),
                    optOffsetFromUtc
                );

                logger.info("Getting blob with instant: {}", formatDateToIso8601WithOffset(
                    new Date(nodeAttributes.getUpdated_at()),
                    optOffsetFromUtc));

                String abbreviateFilename = abbreviateFilename(
                    nodeAttributes.getName(),
                    nodeAttributes.getExtension()
                );

                UUID nodeOwnerId = UUID.fromString(nodeAttributes.getOwner().getId());

                DocsEditorAttributes docsEditorAttributes = new DocsEditorAttributes();
                docsEditorAttributes.setOwnerId(nodeOwnerId);
                docsEditorAttributes.setUserId(UUID.fromString(userInfo.getUserId()));
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
      Optional<Integer> optOffsetFromUtc,
      InputStream blob,
      long contentLength,
      boolean coolIsAutosave
  ) throws ServiceDependencyException {
    NodeAttributes nodeAttributes = filesClient
        .genericGraphQLRequest(
            cookie,
            NodeAttributes.getNodeGraphQLRequest(nodeId.toString(), Optional.empty())
        )
        .flatMap(graphQLResponse -> Try.of(() -> NodeAttributes.mapFromJSON(graphQLResponse)))
        .getOrElseThrow(ServiceDependencyException::new);

    NodeIdVersion uploadedNodeIdVersion = filesClient
        .uploadFileVersion(
            cookie,
            nodeId.toString(),
            createFullFilename(nodeAttributes.getName(), nodeAttributes.getExtension()),
            nodeAttributes.getMime_type(),
            blob,
            contentLength,
            coolIsAutosave
        ).mapFailure(
            Case(
                $(Predicates.instanceOf(UnAuthorized.class)),
                new ServiceDependencyException("Unable to save blob %s to Files (424)".formatted(nodeId))),
            Case(
                $(Predicates.instanceOf(InternalServerError.class)),
                new ServiceDependencyException("Unable to save blob %s to Files (500)".formatted(nodeId)))
        ).get();

    Optional<Integer> uploadedNodeVersion = uploadedNodeIdVersion != null
        ? Optional.ofNullable(uploadedNodeIdVersion.getVersion())
        : Optional.empty();

    /*
     * Retrieve the last update timestamp of the saved file
     */
    return Optional.ofNullable(
        filesClient
            .genericGraphQLRequest(
                cookie,
                NodeAttributes.getNodeGraphQLRequest(nodeId.toString(), uploadedNodeVersion)
            )
            .map(graphQLResponse -> {
              try {
                NodeAttributes updatedModeAttributes = NodeAttributes.mapFromJSON(graphQLResponse);

                NodeUpdatedTimestamp updatedTimestamp = new NodeUpdatedTimestamp();
                updatedTimestamp.setLastModifiedTime(
                    formatDateToIso8601WithOffset(
                        new Date(updatedModeAttributes.getUpdated_at()), optOffsetFromUtc)
                );

                logger.info("Saving blob with instant: {}", formatDateToIso8601WithOffset(new Date(), optOffsetFromUtc));

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

  private String formatDateToIso8601WithOffset(Date modifiedTime, Optional<Integer> optOffsetMinutes) {
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    // TODO marked for removal, ignore it completely and only deal with UTC
    // we don't actually need the offset to be passed from Files' frontend, we can just format
    // the "lastSaved" in docs-editor frontend;
    // using a forwarded offset works but if user refreshes the docs page the offset is lost and
    // lastSaved will be broken (calculated from UTC)
    optOffsetMinutes = Optional.empty();

    if (optOffsetMinutes.isPresent()) {
      int totalOffsetMillis = optOffsetMinutes.get() * 60 * 1000;
      TimeZone customTz = new SimpleTimeZone(totalOffsetMillis, "Custom Offset");
      dateFormat.setTimeZone(customTz);
    } else {
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    return dateFormat.format(modifiedTime);
  }

  private String createFullFilename(String name, String extension) {
    return (extension == null) ? name : name + "." + extension;
  }

  private String abbreviateFilename(String name, String extension) {
    String fullFilename = createFullFilename(name, extension);
    return (fullFilename.length() > 64)
        ? createFullFilename(name.substring(0, 50), extension)
        : fullFilename;
  }
}
