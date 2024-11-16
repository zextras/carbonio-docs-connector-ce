package com.zextras.carbonio.docs_connector.types;

import jakarta.validation.constraints.NotNull;

public class InsertFile {

  private String filename;
  private String destinationFolderId;
  private FileType type;

  /** Filename of the file to be created (without the extension) */
  public String getFilename() {
    return filename;
  }

  public InsertFile setFilename(String filename) {
    this.filename = filename;
    return this;
  }

  /** Folder id where the file is created. The Root id is <code>LOCAL_ROOT</code> */
  @NotNull
  public String getDestinationFolderId() {
    return destinationFolderId;
  }

  public void setDestinationFolderId(String destinationFolderId) {
    this.destinationFolderId = destinationFolderId;
  }

  @NotNull
  public FileType getType() {
    return type;
  }

  public void setType(FileType type) {
    this.type = type;
  }
}
