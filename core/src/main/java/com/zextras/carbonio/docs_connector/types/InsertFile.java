package com.zextras.carbonio.docs_connector.types;

import jakarta.validation.constraints.NotNull;

public class InsertFile {

  private String filename;
  private String destinationFolderId;

  /**
   * Represents a document file type. It can be an open document format:
   *
   * <ul>
   *   <li>LIBRE_DOCUMENT
   *   <li>LIBRE_SPREADSHEET
   *   <li>LIBRE_PRESENTATION
   * </ul>
   *
   * Otherwise, it can be a Microsoft Office document format:
   *
   * <ul>
   *   <li>MS_DOCUMENT
   *   <li>MS_SPREADSHEET
   *   <li>MS_PRESENTATION
   * </ul>
   */
  public enum TypeEnum {
    LIBRE_DOCUMENT("LIBRE_DOCUMENT"),

    LIBRE_SPREADSHEET("LIBRE_SPREADSHEET"),

    LIBRE_PRESENTATION("LIBRE_PRESENTATION"),

    MS_DOCUMENT("MS_DOCUMENT"),

    MS_SPREADSHEET("MS_SPREADSHEET"),

    MS_PRESENTATION("MS_PRESENTATION");
    private String value;

    TypeEnum(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  private TypeEnum type;

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
  public TypeEnum getType() {
    return type;
  }

  public void setType(TypeEnum type) {
    this.type = type;
  }
}
