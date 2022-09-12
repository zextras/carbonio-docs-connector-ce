package com.zextras.carbonio.docs_connector.services.utilities;

import com.zextras.carbonio.docs_connector.generated.model.InsertFile.TypeEnum;
import com.zextras.carbonio.docs_connector.services.FilesService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TemplateUtils {

  private static Logger logger = LoggerFactory.getLogger(TemplateUtils.class);

  /**
   * @param docsFileType is a {@link TypeEnum} representing the file type whose template is to be
   * loaded.
   *
   * @return an {@link Optional} of <code>byte[]</code> containing the template according to the
   * {@link TypeEnum} type. If the template does not exist or the {@link TypeEnum} does not match
   * those specified, then it returns an {@link Optional#empty}.
   */
  public static Optional<byte[]> getTemplateRaw(TypeEnum docsFileType) {
    Optional<InputStream> optTemplate;
    switch (docsFileType) {
      case LIBRE_DOCUMENT:
        optTemplate = Optional.ofNullable(
          FilesService.class.getClassLoader().getResourceAsStream("templates/empty.odt")
        );
        break;
      case LIBRE_SPREADSHEET:
        optTemplate = Optional.ofNullable(
          FilesService.class.getClassLoader().getResourceAsStream("templates/empty.ods")
        );
        break;
      case LIBRE_PRESENTATION:
        optTemplate = Optional.ofNullable(
          FilesService.class.getClassLoader().getResourceAsStream("templates/empty.odp")
        );
        break;
      case MS_DOCUMENT:
        optTemplate = Optional.ofNullable(
          FilesService.class.getClassLoader().getResourceAsStream("templates/empty.docx")
        );
        break;
      case MS_SPREADSHEET:
        optTemplate = Optional.ofNullable(
          FilesService.class.getClassLoader().getResourceAsStream("templates/empty.xlsx")
        );
        break;
      case MS_PRESENTATION:
        optTemplate = Optional.ofNullable(
          FilesService.class.getClassLoader().getResourceAsStream("templates/empty.pptx")
        );
        break;
      default:
        optTemplate = Optional.empty();
    }

    return optTemplate.map(template -> {
      try {
        return template.readAllBytes();
      } catch (IOException exception) {
        logger.error("Failed to load template: " + docsFileType);
        return null;
      } finally {
        IOUtils.closeQuietly(template);
      }
    });
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
      case LIBRE_SPREADSHEET:
        mimeType = "application/vnd.oasis.opendocument.spreadsheet";
        break;
      case LIBRE_PRESENTATION:
        mimeType = "application/vnd.oasis.opendocument.presentation";
        break;
      case LIBRE_DOCUMENT:
        mimeType = "application/vnd.oasis.opendocument.text";
        break;
      case MS_DOCUMENT:
        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        break;
      case MS_SPREADSHEET:
        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        break;
      case MS_PRESENTATION:
        mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
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
      case LIBRE_DOCUMENT:
        return filename + ".odt";
      case LIBRE_SPREADSHEET:
        return filename + ".ods";
      case LIBRE_PRESENTATION:
        return filename + ".odp";
      case MS_DOCUMENT:
        return filename + ".docx";
      case MS_SPREADSHEET:
        return filename + ".xlsx";
      case MS_PRESENTATION:
        return filename + ".pptx";
      default:
        return filename;
    }
  }
}
