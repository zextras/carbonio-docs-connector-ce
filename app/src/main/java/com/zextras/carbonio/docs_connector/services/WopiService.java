// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.services;

import com.zextras.carbonio.docs_connector.exceptions.AccountOverQuotaException;
import com.zextras.carbonio.docs_connector.exceptions.ServiceDependencyException;
import com.zextras.carbonio.docs_connector.types.DocsEditorAttributes;
import com.zextras.carbonio.docs_connector.types.NodeUpdatedTimestamp;
import com.zextras.carbonio.files.sdk.FilesInternalClient;
import com.zextras.carbonio.files.sdk.FilesInternalClientException;
import com.zextras.carbonio.files.sdk.rest.model.InternalNodeDto;
import com.zextras.carbonio.user_management.sdk.rest.ApiException;
import com.zextras.carbonio.user_management.sdk.rest.api.UserResourceApi;
import com.zextras.carbonio.user_management.sdk.rest.model.UserInfoDto;
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

  private final UserResourceApi userResourceApi;
  private final FilesInternalClient filesClient;
  private final SaveBlobCallback saveBlobCallback;

  @Inject
  public WopiService(
      UserResourceApi userResourceApi,
      FilesInternalClient filesClient,
      SaveBlobCallback saveBlobCallback) {
    this.userResourceApi = userResourceApi;
    this.filesClient = filesClient;
    this.saveBlobCallback = saveBlobCallback;
  }

  public Optional<DocsEditorAttributes> getDocsEditorAttributes(
      String requesterId,
      UUID nodeId,
      Optional<Integer> optVersion,
      Optional<Integer> optOffsetFromUtc)
      throws ServiceDependencyException {
    UserInfoDto userInfo;
    try {
      userInfo = userResourceApi.internalUsersIdUserIdGet(requesterId);
    } catch (ApiException e) {
      if (e.getCode() == 404) {
        logger.error("Unable to retrieve user info of user id {}: not found", requesterId, e);
        throw new NoSuchElementException();
      } else if (e.getCode() == 0 || e.getCode() >= 500) {
        // getCode() == 0 means no HTTP response was ever received (connection refused, timeout,
        // a body that failed to deserialize, ...); that and any 5xx mean user-management itself
        // is unavailable, which is a dependency failure, not "this user doesn't exist".
        logger.error(
            "Unable to retrieve user info of user id {}: user-management is unavailable (code {})",
            requesterId,
            e.getCode(),
            e);
        throw new ServiceDependencyException(e);
      } else {
        // This endpoint is documented to only ever return 200 or 404; any other code is
        // unexpected, so fall back to the conservative "not found" outcome.
        logger.error("Unable to retrieve user info of user id {}", requesterId, e);
        throw new NoSuchElementException();
      }
    }

    // A 2xx response with a blank body deserializes to a null UserInfoDto (or one with a null
    // userId) instead of throwing. Under the old gRPC client this was impossible (proto3 defaults
    // a missing field to "", never null); a degenerate response here is functionally the same as
    // not having found the user, so it gets the same clean outcome instead of an NPE surfacing
    // as a 500 further down.
    if (userInfo == null || userInfo.getUserId() == null) {
      logger.error("Unable to retrieve user info of user id {}: empty response", requesterId);
      throw new NoSuchElementException();
    }

    InternalNodeDto node;
    try {
      node = filesClient.getNode(requesterId, nodeId.toString());
    } catch (FilesInternalClientException e) {
      if (e.isNotFound() || e.isForbidden()) {
        logger.error("Unable to retrieve node {}: not found", nodeId);
        throw new NoSuchElementException("Node " + nodeId + " not found");
      }
      throw new ServiceDependencyException(e);
    }

    long updatedAt = node.getUpdatedAt() != null ? node.getUpdatedAt() : 0L;
    String lastModifiedTimeFormatted =
        formatDateToIso8601WithOffset(new Date(updatedAt), optOffsetFromUtc);

    logger.info("Getting blob with instant: {}", lastModifiedTimeFormatted);

    String abbreviateFilename =
        abbreviateFilename(node.getName(), node.getExtension());

    UUID nodeOwnerId =
        node.getOwner() != null && node.getOwner().getId() != null
            ? UUID.fromString(node.getOwner().getId())
            : null;

    DocsEditorAttributes docsEditorAttributes = new DocsEditorAttributes();
    docsEditorAttributes.setOwnerId(nodeOwnerId);
    docsEditorAttributes.setUserId(UUID.fromString(userInfo.getUserId()));
    docsEditorAttributes.setUserFriendlyName(userInfo.getFullName());
    docsEditorAttributes.setUserCanWrite(
        Boolean.TRUE.equals(node.getPermissions().getCanWriteFile()));
    docsEditorAttributes.setBaseFileName(abbreviateFilename);
    docsEditorAttributes.setVersion(node.getVersion());
    docsEditorAttributes.setSize(node.getSize());
    docsEditorAttributes.setLastModifiedTime(lastModifiedTimeFormatted);
    docsEditorAttributes.setEnableOwnerTermination(false);
    docsEditorAttributes.setDisableCopy(false);
    docsEditorAttributes.setDisableExport(false);
    docsEditorAttributes.setDisablePrint(false);
    docsEditorAttributes.setDisableInactiveMessages(true);
    docsEditorAttributes.setHideExportOption(false);
    docsEditorAttributes.setHideSaveOption(
        !Boolean.TRUE.equals(node.getPermissions().getCanWriteFile()));
    docsEditorAttributes.setHidePrintOption(false);
    docsEditorAttributes.setHideChangeTrackingControls(false);
    docsEditorAttributes.setUserCanNotWriteRelative(true);
    docsEditorAttributes.setUserCanRename(false);
    docsEditorAttributes.setSupportsLocks(false);

    return Optional.of(docsEditorAttributes);
  }

  public Optional<InputStream> getBlob(
      String userId, UUID nodeId, Optional<Integer> optVersion) {
    try {
      return Optional.of(filesClient.downloadFile(userId, nodeId.toString(), optVersion));
    } catch (FilesInternalClientException e) {
      logger.error(e.getMessage(), e);
      return Optional.empty();
    }
  }

  public Optional<NodeUpdatedTimestamp> saveBlob(
      String userId,
      UUID nodeId,
      Optional<Integer> optOffsetFromUtc,
      InputStream blob,
      long contentLength,
      boolean coolIsAutosave)
      throws ServiceDependencyException, AccountOverQuotaException {

    InternalNodeDto node;
    try {
      node = filesClient.getNode(userId, nodeId.toString());
    } catch (FilesInternalClientException e) {
      if (e.isNotFound() || e.isForbidden()) {
        // Same "successful response, no matching node" distinction as FilesService#openFile /
        // #getDocsEditorAttributes above -- a genuine not-found, not a dependency failure.
        throw new NoSuchElementException("Node " + nodeId + " not found");
      }
      throw new ServiceDependencyException(e);
    }

    try {
      filesClient.uploadFileVersion(
          userId,
          nodeId.toString(),
          createFullFilename(node.getName(), node.getExtension()),
          node.getMimeType(),
          () -> blob,
          contentLength,
          coolIsAutosave);
    } catch (FilesInternalClientException e) {
      // HTTP 422 = over-quota (Advanced files enforces per-account quota on upload)
      if (e.getStatusCode() == 422) {
        throw new AccountOverQuotaException(
            "Unable to save blob %s to Files (owner is over quota)".formatted(nodeId));
      }
      throw new ServiceDependencyException(
          "Unable to save blob %s to Files (%d)".formatted(nodeId, e.getStatusCode()));
    }

    // Notify callback (Advanced: updates savedAt on open_document record)
    saveBlobCallback.onBlobSaved(nodeId);

    /*
     * Retrieve the last update timestamp of the saved file
     */
    try {
      InternalNodeDto updatedNode = filesClient.getNode(userId, nodeId.toString());
      long updatedAt = updatedNode.getUpdatedAt() != null ? updatedNode.getUpdatedAt() : 0L;

      NodeUpdatedTimestamp updatedTimestamp = new NodeUpdatedTimestamp();
      updatedTimestamp.setLastModifiedTime(
          formatDateToIso8601WithOffset(new Date(updatedAt), optOffsetFromUtc));

      logger.info(
          "Saving blob with instant: {}", formatDateToIso8601WithOffset(new Date(), optOffsetFromUtc));

      return Optional.of(updatedTimestamp);
    } catch (FilesInternalClientException e) {
      logger.error(e.getMessage(), e);
      return Optional.empty();
    }
  }

  private String formatDateToIso8601WithOffset(
      Date modifiedTime, Optional<Integer> optOffsetMinutes) {
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
