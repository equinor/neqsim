package neqsim.statistics.parameterfitting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Downloads experimental data files into a local cache for reproducible fitting studies.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ExperimentalDataDownloader {
  /** Buffer size used for URL downloads. */
  private static final int BUFFER_SIZE = 8192;

  /**
   * Utility class constructor.
   */
  private ExperimentalDataDownloader() {}

  /**
   * Downloads a URL to a deterministic file inside a cache directory if it is not already present.
   *
   * @param source URL to download
   * @param cacheDirectory cache directory
   * @return cached file path
   * @throws IOException if the source cannot be downloaded or cached
   */
  public static File downloadIfNeeded(URL source, File cacheDirectory) throws IOException {
    return downloadIfNeeded(source, cacheDirectory, fileNameFromUrl(source));
  }

  /**
   * Downloads a URL to a named cache file if it is not already present.
   *
   * @param source URL to download
   * @param cacheDirectory cache directory
   * @param fileName target cache file name
   * @return cached file path
   * @throws IOException if the source cannot be downloaded or cached
   */
  public static File downloadIfNeeded(URL source, File cacheDirectory, String fileName)
      throws IOException {
    validate(source, cacheDirectory, fileName);
    if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
      throw new IOException("Could not create cache directory: " + cacheDirectory);
    }
    File cachedFile = new File(cacheDirectory, fileName);
    if (cachedFile.exists() && cachedFile.length() > 0L) {
      return cachedFile;
    }
    download(source, cachedFile);
    return cachedFile;
  }

  /**
   * Downloads a URL to a target file, replacing any existing file.
   *
   * @param source URL to download
   * @param targetFile target file
   * @throws IOException if download or writing fails
   */
  public static void download(URL source, File targetFile) throws IOException {
    if (source == null) {
      throw new IllegalArgumentException("source cannot be null");
    }
    if (targetFile == null) {
      throw new IllegalArgumentException("targetFile cannot be null");
    }
    File parent = targetFile.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new IOException("Could not create target directory: " + parent);
    }
    InputStream input = source.openStream();
    FileOutputStream output = new FileOutputStream(targetFile);
    try {
      byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        output.write(buffer, 0, read);
      }
    } finally {
      output.close();
      input.close();
    }
  }

  /**
   * Resolves a safe file name from a URL.
   *
   * @param source source URL
   * @return file name
   */
  private static String fileNameFromUrl(URL source) {
    if (source == null) {
      throw new IllegalArgumentException("source cannot be null");
    }
    String path = source.getPath();
    int slash = path == null ? -1 : path.lastIndexOf('/');
    String name = slash >= 0 ? path.substring(slash + 1) : path;
    if (name == null || name.trim().isEmpty()) {
      return "experimental-data.dat";
    }
    return name.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  /**
   * Validates download request inputs.
   *
   * @param source source URL
   * @param cacheDirectory cache directory
   * @param fileName target file name
   */
  private static void validate(URL source, File cacheDirectory, String fileName) {
    if (source == null) {
      throw new IllegalArgumentException("source cannot be null");
    }
    if (cacheDirectory == null) {
      throw new IllegalArgumentException("cacheDirectory cannot be null");
    }
    if (fileName == null || fileName.trim().isEmpty()) {
      throw new IllegalArgumentException("fileName cannot be empty");
    }
  }
}
