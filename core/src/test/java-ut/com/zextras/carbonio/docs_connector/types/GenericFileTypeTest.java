// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.types;

import com.zextras.carbonio.docs_connector.types.FileType.GenericFileType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class GenericFileTypeTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "text/rtf",
    "text/plain",
    "application/msword",
    "application/vnd.oasis.opendocument.text",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.oasis.opendocument.text",
  })
  void givenAValidDocumentMimeTypeTheGenericFileTypeFromMimeTypeShouldReturnDocumentType(String mimeType) {
    // Given & When
    GenericFileType genericFIleType = GenericFileType.fromMimeType(mimeType);

    // Then
    Assertions.assertThat(genericFIleType).isEqualTo(GenericFileType.DOCUMENT);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "application/vnd.ms-powerpoint",
    "application/vnd.oasis.opendocument.presentation",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.sun.xml.impress",
  })
  void givenAValidPresentationMimeTypeTheGenericFileTypeFromMimeTypeShouldReturnPresentationType(String mimeType) {
    // Given & When
    GenericFileType genericFIleType = GenericFileType.fromMimeType(mimeType);

    // Then
    Assertions.assertThat(genericFIleType).isEqualTo(GenericFileType.PRESENTATION);
  }


  @ParameterizedTest
  @ValueSource(strings = {
    "application/vnd.ms-excel",
    "application/vnd.oasis.opendocument.spreadsheet",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.sun.xml.calc",
  })
  void givenAValidSpreadsheetMimeTypeTheGenericFileTypeFromMimeTypeShouldReturnSpreadsheetType(String mimeType) {
    // Given & When
    GenericFileType genericFIleType = GenericFileType.fromMimeType(mimeType);

    // Then
    Assertions.assertThat(genericFIleType).isEqualTo(GenericFileType.SPREADSHEET);
  }

  @Test
  void givenAInvalidMimeTypeTheGenericFileTypeFromMimeTypeShouldReturnDocumentType() {
    // Given & When
    GenericFileType genericFIleType = GenericFileType.fromMimeType("application/invalid-mime-type");

    // Then
    Assertions.assertThat(genericFIleType).isEqualTo(GenericFileType.DOCUMENT);
  }
}
