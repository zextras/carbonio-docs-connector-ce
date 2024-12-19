// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.types;

import com.zextras.carbonio.docs_connector.Constants.Config;
import java.util.Map.Entry;

/**
 * Represents a document file type. It can be an open document format:
 *
 * <ul>
 *   <li>LIBRE_DOCUMENT
 *   <li>LIBRE_PRESENTATION
 *   <li>LIBRE_SPREADSHEET
 * </ul>
 * <p>
 * Otherwise, it can be a Microsoft Office document format:
 *
 * <ul>
 *   <li>MS_DOCUMENT
 *   <li>MS_PRESENTATION
 *   <li>MS_SPREADSHEET
 * </ul>
 */
public enum FileType {

  LIBRE_DOCUMENT("LIBRE_DOCUMENT"),
  LIBRE_PRESENTATION("LIBRE_PRESENTATION"),
  LIBRE_SPREADSHEET("LIBRE_SPREADSHEET"),

  MS_DOCUMENT("MS_DOCUMENT"),
  MS_PRESENTATION("MS_PRESENTATION"),
  MS_SPREADSHEET("MS_SPREADSHEET");

  private final String value;

  FileType(String fileType) {
    value = fileType;
  }

  public String toString() {
    return String.valueOf(value);
  }

  public enum GenericFileType {
    DOCUMENT,
    PRESENTATION,
    SPREADSHEET;

    public static GenericFileType fromMimeType(String fileMimeType) {
      return Config.GENERIC_FILE_TYPE_MIME_TYPES_MAP
        .entrySet()
        .stream()
        .filter(entry -> entry.getValue().contains(fileMimeType))
        .map(Entry::getKey)
        .findFirst()
        .orElse(GenericFileType.DOCUMENT);
    }
  }
}
