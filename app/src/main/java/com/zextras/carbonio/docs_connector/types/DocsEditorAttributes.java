// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.UUID;

public class DocsEditorAttributes implements Serializable {

  private UUID ownerId;
  private UUID userId;
  private String userFriendlyName;
  private Boolean userCanWrite;
  private String baseFileName;
  private Integer version;
  private Long size;
  private String lastModifiedTime;
  private Boolean enableOwnerTermination;
  private Boolean disableCopy;
  private Boolean disableExport;
  private Boolean disablePrint;
  private Boolean disableInactiveMessages;
  private Boolean hideExportOption;
  private Boolean hideSaveOption;
  private Boolean hidePrintOption;
  private Boolean hideChangeTrackingControls;
  private Boolean userCanNotWriteRelative;
  private Boolean userCanRename;
  private Boolean supportsLocks;

  @JsonProperty("OwnerId")
  public UUID getOwnerId() {
    return ownerId;
  }

  @JsonProperty("UserId")
  public UUID getUserId() {
    return userId;
  }

  @JsonProperty("UserFriendlyName")
  public String getUserFriendlyName() {
    return userFriendlyName;
  }

  @JsonProperty("UserCanWrite")
  public Boolean getUserCanWrite() {
    return userCanWrite;
  }

  @JsonProperty("BaseFileName")
  public String getBaseFileName() {
    return baseFileName;
  }

  @JsonProperty("Version")
  public Integer getVersion() {
    return version;
  }

  @JsonProperty("Size")
  public Long getSize() {
    return size;
  }

  @JsonProperty("LastModifiedTime")
  public String getLastModifiedTime() {
    return lastModifiedTime;
  }

  @JsonProperty("EnableOwnerTermination")
  public Boolean getEnableOwnerTermination() {
    return enableOwnerTermination;
  }

  @JsonProperty("DisableCopy")
  public Boolean getDisableCopy() {
    return disableCopy;
  }

  @JsonProperty("DisableExport")
  public Boolean getDisableExport() {
    return disableExport;
  }

  @JsonProperty("DisablePrint")
  public Boolean getDisablePrint() {
    return disablePrint;
  }

  @JsonProperty("DisableInactiveMessages")
  public Boolean getDisableInactiveMessages() {
    return disableInactiveMessages;
  }

  @JsonProperty("HideExportOption")
  public Boolean getHideExportOption() {
    return hideExportOption;
  }

  @JsonProperty("HideSaveOption")
  public Boolean getHideSaveOption() {
    return hideSaveOption;
  }

  @JsonProperty("HidePrintOption")
  public Boolean getHidePrintOption() {
    return hidePrintOption;
  }

  @JsonProperty("HideChangeTrackingControls")
  public Boolean getHideChangeTrackingControls() {
    return hideChangeTrackingControls;
  }

  @JsonProperty("UserCanNotWriteRelative")
  public Boolean getUserCanNotWriteRelative() {
    return userCanNotWriteRelative;
  }

  @JsonProperty("UserCanRename")
  public Boolean getUserCanRename() {
    return userCanRename;
  }

  @JsonProperty("SupportsLocks")
  public Boolean getSupportsLocks() {
    return supportsLocks;
  }

  public DocsEditorAttributes setOwnerId(UUID ownerId) {
    this.ownerId = ownerId;
    return this;
  }

  public DocsEditorAttributes setUserId(UUID userId) {
    this.userId = userId;
    return this;
  }

  public DocsEditorAttributes setUserFriendlyName(String userFriendlyName) {
    this.userFriendlyName = userFriendlyName;
    return this;
  }

  public DocsEditorAttributes setUserCanWrite(Boolean userCanWrite) {
    this.userCanWrite = userCanWrite;
    return this;
  }

  public DocsEditorAttributes setBaseFileName(String baseFileName) {
    this.baseFileName = baseFileName;
    return this;
  }

  public DocsEditorAttributes setVersion(Integer version) {
    this.version = version;
    return this;
  }

  public DocsEditorAttributes setSize(Long size) {
    this.size = size;
    return this;
  }

  public DocsEditorAttributes setLastModifiedTime(String lastModifiedTime) {
    this.lastModifiedTime = lastModifiedTime;
    return this;
  }

  public DocsEditorAttributes setEnableOwnerTermination(Boolean enableOwnerTermination) {
    this.enableOwnerTermination = enableOwnerTermination;
    return this;
  }

  public DocsEditorAttributes setDisableCopy(Boolean disableCopy) {
    this.disableCopy = disableCopy;
    return this;
  }

  public DocsEditorAttributes setDisableExport(Boolean disableExport) {
    this.disableExport = disableExport;
    return this;
  }

  public DocsEditorAttributes setDisablePrint(Boolean disablePrint) {
    this.disablePrint = disablePrint;
    return this;
  }

  public DocsEditorAttributes setDisableInactiveMessages(Boolean disableInactiveMessages) {
    this.disableInactiveMessages = disableInactiveMessages;
    return this;
  }

  public DocsEditorAttributes setHideExportOption(Boolean hideExportOption) {
    this.hideExportOption = hideExportOption;
    return this;
  }

  public DocsEditorAttributes setHideSaveOption(Boolean hideSaveOption) {
    this.hideSaveOption = hideSaveOption;
    return this;
  }

  public DocsEditorAttributes setHidePrintOption(Boolean hidePrintOption) {
    this.hidePrintOption = hidePrintOption;
    return this;
  }

  public DocsEditorAttributes setHideChangeTrackingControls(Boolean hideChangeTrackingControls) {
    this.hideChangeTrackingControls = hideChangeTrackingControls;
    return this;
  }

  public DocsEditorAttributes setUserCanNotWriteRelative(Boolean userCanNotWriteRelative) {
    this.userCanNotWriteRelative = userCanNotWriteRelative;
    return this;
  }

  public DocsEditorAttributes setUserCanRename(Boolean userCanRename) {
    this.userCanRename = userCanRename;
    return this;
  }

  public DocsEditorAttributes setSupportsLocks(Boolean supportsLocks) {
    this.supportsLocks = supportsLocks;
    return this;
  }
}
