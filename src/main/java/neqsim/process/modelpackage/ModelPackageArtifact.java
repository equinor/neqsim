package neqsim.process.modelpackage;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable integrity and role metadata for one file in a model package. */
public final class ModelPackageArtifact implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String path;
  private final String role;
  private final String mediaType;
  private final long sizeBytes;
  private final String sha256;

  public ModelPackageArtifact(String path, String role, String mediaType, long sizeBytes, String sha256) {
    this.path = requireText(path, "path");
    this.role = requireText(role, "role");
    this.mediaType = requireText(mediaType, "mediaType");
    if (sizeBytes < 0L) {
      throw new IllegalArgumentException("sizeBytes must not be negative");
    }
    this.sizeBytes = sizeBytes;
    this.sha256 = requireHash(sha256);
  }

  public String getPath() {
    return path;
  }

  public String getRole() {
    return role;
  }

  public String getMediaType() {
    return mediaType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getSha256() {
    return sha256;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("path", path);
    result.put("role", role);
    result.put("mediaType", mediaType);
    result.put("sizeBytes", Long.valueOf(sizeBytes));
    result.put("sha256", sha256);
    return result;
  }

  static ModelPackageArtifact fromMap(Map<String, Object> value) {
    Object size = value.get("sizeBytes");
    long sizeBytes = size instanceof Number ? ((Number) size).longValue() : Long.parseLong(String.valueOf(size));
    return new ModelPackageArtifact(String.valueOf(value.get("path")), String.valueOf(value.get("role")),
        String.valueOf(value.get("mediaType")), sizeBytes, String.valueOf(value.get("sha256")));
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static String requireHash(String value) {
    String hash = requireText(value, "sha256").toLowerCase();
    if (!hash.matches("[a-f0-9]{64}")) {
      throw new IllegalArgumentException("sha256 must contain 64 lowercase hexadecimal characters");
    }
    return hash;
  }
}
