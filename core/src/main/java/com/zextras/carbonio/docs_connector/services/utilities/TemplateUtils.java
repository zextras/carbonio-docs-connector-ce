package com.zextras.carbonio.docs_connector.services.utilities;

import com.zextras.carbonio.docs_connector.generated.model.InsertFile.TypeEnum;
import com.zextras.carbonio.docs_connector.services.FilesService;
import java.io.InputStream;
import java.util.Optional;

public class TemplateUtils {

  /**
   * @param docsFileType is a {@link TypeEnum} representing the file type whose template is to be
   * loaded.
   *
   * @return an {@link InputStream} containing the related template according to the {@link
   * TypeEnum} type. If the template does not exist or the {@link TypeEnum} does not match those
   * specified, then it returns an {@link Optional#empty}.
   */
  public static Optional<InputStream> getTemplate(TypeEnum docsFileType) {
    switch (docsFileType) {
      case DOCUMENT:
        return Optional.ofNullable(
          FilesService.class.getClassLoader().getResourceAsStream("templates/empty.odt"));
      case SPREADSHEET:
        return Optional.ofNullable(
          FilesService.class.getClassLoader().getResourceAsStream("templates/empty.ods"));
      case PRESENTATION:
        return Optional.ofNullable(
          FilesService.class.getClassLoader().getResourceAsStream("templates/empty.odp"));
      default:
        return Optional.empty();
    }
  }

  /**
   * @param docsFileType is a {@link TypeEnum} representing the file type to find the mime-type
   * for.
   *
   * @return a {@link String} representing the related mime-type of the {@link TypeEnum} type. If
   * the {@link TypeEnum} does not match those specified, then it returns an empty {@link String}.
   */
  public static String detectMimeTypeFrom(TypeEnum docsFileType) {
    String mimeType = "";

    switch (docsFileType) {
      case SPREADSHEET:
        mimeType = "application/vnd.oasis.opendocument.spreadsheet";
        break;
      case PRESENTATION:
        mimeType = "application/vnd.oasis.opendocument.presentation";
        break;
      case DOCUMENT:
        mimeType = "application/vnd.oasis.opendocument.text";
        break;
    }

    return mimeType;
  }

  /**
   * @param docsFileType is a {@link TypeEnum} representing the file type necessary to choose the
   * right extension to append.
   * @param filename is a {@link String} representing the filename to modify.
   *
   * @return a {@link String} representing the filename with the extension according to the {@link
   * TypeEnum} type passed. If the {@link TypeEnum} does not match those specified, then it returns
   * only the filename unchanged.
   */
  public static String appendExtensionByType(
    TypeEnum docsFileType,
    String filename
  ) {
    switch (docsFileType) {
      case DOCUMENT:
        return filename + ".odt";
      case SPREADSHEET:
        return filename + ".ods";
      case PRESENTATION:
        return filename + ".odp";
      default:
        return filename;
    }
  }
}
