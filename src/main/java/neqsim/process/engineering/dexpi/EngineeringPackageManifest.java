package neqsim.process.engineering.dexpi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/** Writes a deterministic file inventory and SHA-256 integrity record for an engineering package. */
final class EngineeringPackageManifest {
  private EngineeringPackageManifest() {
  }

  static Path write(Path packageDirectory, Path outputFile) throws IOException {
    List<Path> files = new ArrayList<Path>();
    collectFiles(packageDirectory, outputFile, files);
    Collections.sort(files, new Comparator<Path>() {
      @Override
      public int compare(Path left, Path right) {
        return relative(packageDirectory, left).compareTo(relative(packageDirectory, right));
      }
    });

    List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();
    for (Path file : files) {
      Map<String, Object> entry = new LinkedHashMap<String, Object>();
      entry.put("path", relative(packageDirectory, file));
      entry.put("sizeBytes", Long.valueOf(Files.size(file)));
      entry.put("sha256", sha256(file));
      entries.add(entry);
    }
    Map<String, Object> root = new LinkedHashMap<String, Object>();
    root.put("schemaVersion", "neqsim_engineering_package_manifest.v1");
    root.put("hashAlgorithm", "SHA-256");
    root.put("fileCount", Integer.valueOf(entries.size()));
    root.put("files", entries);
    root.put("governanceNote",
        "Integrity hashes identify package content; they do not constitute engineering approval.");
    Files.write(outputFile,
        new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8));
    return outputFile;
  }

  private static void collectFiles(Path directory, Path outputFile, List<Path> result) throws IOException {
    try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
      for (Path entry : stream) {
        if (Files.isDirectory(entry)) {
          collectFiles(entry, outputFile, result);
        } else if (!entry.equals(outputFile)) {
          result.add(entry);
        }
      }
    }
  }

  private static String relative(Path directory, Path file) {
    return directory.relativize(file).toString().replace('\\', '/');
  }

  private static String sha256(Path file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      try (InputStream input = Files.newInputStream(file)) {
        int count;
        while ((count = input.read(buffer)) >= 0) {
          if (count > 0) {
            digest.update(buffer, 0, count);
          }
        }
      }
      StringBuilder hex = new StringBuilder();
      for (byte value : digest.digest()) {
        hex.append(String.format("%02x", Integer.valueOf(value & 0xff)));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }
}
