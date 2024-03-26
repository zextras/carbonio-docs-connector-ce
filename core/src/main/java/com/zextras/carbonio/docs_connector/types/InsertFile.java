package com.zextras.carbonio.docs_connector.types;

import javax.validation.constraints.NotNull;

public class InsertFile   {

  private String filename;
  private String destinationFolderId;

  /**
   * Docs file type. It can be:  - LIBRE_DOCUMENT, LIBRE_SPREADSHEET or LIBRE_PRESENTATION to upload open document format  - MS_DOCUMENT, MS_SPREADSHEET or MS_PRESENTATION to upload Microsoft Office document format
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

  /**
   * Filename of the file to be created (without the extension)
   **/
  public String getFilename() {
    return filename;
  }

  public InsertFile setFilename(String filename) {
    this.filename = filename;
    return this;
  }

  /**
   * Folder id where the file is created. The Root id is LOCAL_ROOT
   **/

  @NotNull
  public String getDestinationFolderId() {
    return destinationFolderId;
  }
  public void setDestinationFolderId(String destinationFolderId) {
    this.destinationFolderId = destinationFolderId;
  }

  /**
   * Docs file type. It can be:  - LIBRE_DOCUMENT, LIBRE_SPREADSHEET or LIBRE_PRESENTATION to upload open document format  - MS_DOCUMENT, MS_SPREADSHEET or MS_PRESENTATION to upload Microsoft Office document format
   **/

  @NotNull
  public TypeEnum getType() {
    return type;
  }
  public void setType(TypeEnum type) {
    this.type = type;
  }
}
