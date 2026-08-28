package neqsim.process.processmodel.diagram;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Independently assesses a stored or transferred {@link EngineeringDiagramDelivery} directory.
 *
 * <p>
 * The assessment is bounded and fail-closed. It validates the delivery manifest schema and
 * fingerprint, engineering-review boundaries, relative artifact paths, media types, sizes and
 * SHA-256 hashes. It also rejects symbolic links, paths outside the delivery root, duplicate or
 * unlisted files, missing required projections, and an information-model artifact inconsistent
 * with the declared source scope.
 * </p>
 *
 * <p>
 * A successful assessment proves exact package integrity only. It does not reconstruct or execute
 * a process model, repeat accountable engineering review, qualify DEXPI interoperability, approve
 * a drawing, or claim standards conformance.
 * </p>
 */
public final class EngineeringDiagramDeliveryAssessment {
  private static final String DELIVERY_SCHEMA = "neqsim_engineering_diagram_delivery.v1";
  private static final String ASSESSMENT_SCHEMA =
      "neqsim_engineering_diagram_delivery_assessment.v1";
  private static final String MANIFEST_FILE = "delivery-manifest.json";
  private static final long MAX_MANIFEST_BYTES = 16L * 1024L * 1024L;
  private static final long MAX_ARTIFACT_BYTES = 256L * 1024L * 1024L;
  private static final long MAX_TOTAL_ARTIFACT_BYTES = 512L * 1024L * 1024L;
  private static final int MAX_ARTIFACT_COUNT = 4096;

  private EngineeringDiagramDeliveryAssessment() {}

  /** Assessment diagnostic severity. */
  public enum Severity {
    INFO,
    WARNING,
    ERROR
  }

  /** Immutable structured assessment diagnostic. */
  public static final class Diagnostic implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Severity severity;
    private final String code;
    private final String message;
    private final String subject;

    private Diagnostic(Severity severity, String code, String message, String subject) {
      this.severity = severity;
      this.code = code;
      this.message = message;
      this.subject = subject;
    }

    public Severity getSeverity() {
      return severity;
    }

    public String getCode() {
      return code;
    }

    public String getMessage() {
      return message;
    }

    public String getSubject() {
      return subject;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("severity", severity.name());
      result.put("code", code);
      result.put("message", message);
      if (subject != null) {
        result.put("subject", subject);
      }
      return result;
    }
  }

  /** Immutable verified evidence for one declared delivery artifact. */
  public static final class ArtifactEvidence implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String relativePath;
    private final String mediaType;
    private final long sizeBytes;
    private final String sha256;

    private ArtifactEvidence(String relativePath, String mediaType, long sizeBytes,
        String sha256) {
      this.relativePath = relativePath;
      this.mediaType = mediaType;
      this.sizeBytes = sizeBytes;
      this.sha256 = sha256;
    }

    public String getRelativePath() {
      return relativePath;
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

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("relativePath", relativePath);
      result.put("mediaType", mediaType);
      result.put("sizeBytes", Long.valueOf(sizeBytes));
      result.put("sha256", sha256);
      return result;
    }
  }

  /** Immutable serializable assessment result. */
  public static final class Report implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String directory;
    private final String manifestSha256;
    private final String manifestFingerprint;
    private final String sourceScope;
    private final String plantId;
    private final String revision;
    private final Map<String, ArtifactEvidence> artifacts;
    private final List<Diagnostic> diagnostics;
    private final boolean complete;
    private final String fingerprint;

    private Report(Path directory, String manifestSha256, String manifestFingerprint,
        String sourceScope, String plantId, String revision,
        Map<String, ArtifactEvidence> artifacts, List<Diagnostic> diagnostics,
        boolean manifestDeclaredComplete) {
      this.directory = directory.toString();
      this.manifestSha256 = manifestSha256;
      this.manifestFingerprint = manifestFingerprint;
      this.sourceScope = sourceScope;
      this.plantId = plantId;
      this.revision = revision;
      this.artifacts = Collections.unmodifiableMap(
          new LinkedHashMap<String, ArtifactEvidence>(artifacts));
      this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
      this.complete = manifestDeclaredComplete && !hasErrors(diagnostics);
      this.fingerprint = sha256(new GsonBuilder().create().toJson(toMapWithoutFingerprint())
          .getBytes(StandardCharsets.UTF_8));
    }

    public Path getDirectory() {
      return Paths.get(directory);
    }

    public String getManifestSha256() {
      return manifestSha256;
    }

    public String getManifestFingerprint() {
      return manifestFingerprint;
    }

    public String getSourceScope() {
      return sourceScope;
    }

    public String getPlantId() {
      return plantId;
    }

    public String getRevision() {
      return revision;
    }

    public Map<String, ArtifactEvidence> getArtifacts() {
      return artifacts;
    }

    public List<Diagnostic> getDiagnostics() {
      return diagnostics;
    }

    public boolean isComplete() {
      return complete;
    }

    public String getFingerprint() {
      return fingerprint;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = toMapWithoutFingerprint();
      result.put("fingerprint", fingerprint);
      return result;
    }

    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }

    private Map<String, Object> toMapWithoutFingerprint() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", ASSESSMENT_SCHEMA);
      result.put("manifestSha256", manifestSha256);
      result.put("manifestFingerprint", manifestFingerprint);
      result.put("sourceScope", sourceScope);
      result.put("plantId", plantId);
      result.put("revision", revision);
      result.put("approvalStatus", "REVIEW_REQUIRED");
      result.put("fitnessForConstruction", Boolean.FALSE);
      result.put("iso10628ConformanceClaimed", Boolean.FALSE);
      result.put("completeWithinDeclaredScope", Boolean.valueOf(complete));
      List<Map<String, Object>> artifactMaps = new ArrayList<Map<String, Object>>();
      for (ArtifactEvidence artifact : artifacts.values()) {
        artifactMaps.add(artifact.toMap());
      }
      result.put("artifacts", artifactMaps);
      List<Map<String, Object>> diagnosticMaps = new ArrayList<Map<String, Object>>();
      for (Diagnostic diagnostic : diagnostics) {
        diagnosticMaps.add(diagnostic.toMap());
      }
      result.put("diagnostics", diagnosticMaps);
      return result;
    }
  }

  /**
   * Assesses one existing delivery without modifying or extracting its content.
   *
   * @param directory delivery root containing {@code delivery-manifest.json}
   * @return deterministic integrity and qualification-boundary evidence
   * @throws IOException when the directory cannot be read within the bounded intake limits
   */
  public static Report assess(Path directory) throws IOException {
    if (directory == null) {
      throw new IllegalArgumentException("directory must not be null");
    }
    Path normalized = directory.toAbsolutePath().normalize();
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    Map<String, ArtifactEvidence> artifacts = new TreeMap<String, ArtifactEvidence>();
    if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
      diagnostics.add(error("DELIVERY_DIRECTORY_INVALID",
          "Delivery root must be an existing non-symbolic-link directory", normalized.toString()));
      return new Report(normalized, null, null, null, null, null, artifacts, diagnostics, false);
    }

    Path root = normalized.toRealPath();
    Path manifestPath = root.resolve(MANIFEST_FILE);
    if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
      diagnostics.add(error("DELIVERY_MANIFEST_MISSING",
          "Delivery manifest is missing or is not a regular file", MANIFEST_FILE));
      return new Report(root, null, null, null, null, null, artifacts, diagnostics, false);
    }

    byte[] manifestBytes = readBounded(manifestPath, MAX_MANIFEST_BYTES,
        "Delivery manifest exceeds the bounded intake limit");
    String manifestSha256 = sha256(manifestBytes);
    JsonObject manifest;
    try {
      JsonElement parsed = new JsonParser().parse(new String(manifestBytes, StandardCharsets.UTF_8));
      if (!parsed.isJsonObject()) {
        diagnostics.add(error("DELIVERY_MANIFEST_NOT_OBJECT",
            "Delivery manifest root must be a JSON object", MANIFEST_FILE));
        return new Report(root, manifestSha256, null, null, null, null, artifacts,
            diagnostics, false);
      }
      manifest = parsed.getAsJsonObject();
    } catch (RuntimeException exception) {
      diagnostics.add(error("DELIVERY_MANIFEST_INVALID_JSON",
          "Delivery manifest is not valid JSON", MANIFEST_FILE));
      return new Report(root, manifestSha256, null, null, null, null, artifacts,
          diagnostics, false);
    }

    String schemaVersion = text(manifest, "schemaVersion", diagnostics);
    String sourceScope = text(manifest, "sourceScope", diagnostics);
    String plantId = text(manifest, "plantId", diagnostics);
    String revision = text(manifest, "revision", diagnostics);
    String manifestFingerprint = text(manifest, "fingerprint", diagnostics);
    boolean manifestDeclaredComplete = booleanValue(manifest, "completeWithinDeclaredScope",
        diagnostics);

    if (!DELIVERY_SCHEMA.equals(schemaVersion)) {
      diagnostics.add(error("DELIVERY_SCHEMA_UNSUPPORTED",
          "Unsupported engineering-diagram delivery schema", schemaVersion));
    }
    requireBoundary(manifest, "approvalStatus", "REVIEW_REQUIRED", diagnostics);
    requireFalse(manifest, "fitnessForConstruction", diagnostics);
    requireFalse(manifest, "iso10628ConformanceClaimed", diagnostics);
    if (!manifestDeclaredComplete) {
      diagnostics.add(error("DELIVERY_MANIFEST_DECLARED_INCOMPLETE",
          "Delivery manifest is not complete within its declared scope", MANIFEST_FILE));
    }
    verifyManifestFingerprint(manifest, manifestFingerprint, diagnostics);

    long totalBytes = readArtifactEvidence(root, manifest, artifacts, diagnostics);
    if (totalBytes > MAX_TOTAL_ARTIFACT_BYTES) {
      diagnostics.add(error("DELIVERY_TOTAL_SIZE_LIMIT_EXCEEDED",
          "Declared delivery artifacts exceed the bounded total-size limit", null));
    }
    verifyActualFileSet(root, artifacts, diagnostics);
    verifyRequiredArtifacts(sourceScope, artifacts, diagnostics);

    if (!hasErrors(diagnostics)) {
      diagnostics.add(new Diagnostic(Severity.INFO, "DELIVERY_INTEGRITY_VERIFIED",
          "Manifest fingerprint, artifact bytes, paths, and declared review boundaries are valid",
          plantId));
    }
    return new Report(root, manifestSha256, manifestFingerprint, sourceScope, plantId, revision,
        artifacts, diagnostics, manifestDeclaredComplete);
  }

  private static long readArtifactEvidence(Path root, JsonObject manifest,
      Map<String, ArtifactEvidence> artifacts, List<Diagnostic> diagnostics) throws IOException {
    JsonElement value = manifest.get("artifacts");
    if (value == null || !value.isJsonArray()) {
      diagnostics.add(error("DELIVERY_ARTIFACT_LIST_MISSING",
          "Manifest artifacts must be a JSON array", "artifacts"));
      return 0L;
    }
    JsonArray array = value.getAsJsonArray();
    if (array.size() > MAX_ARTIFACT_COUNT) {
      diagnostics.add(error("DELIVERY_ARTIFACT_COUNT_LIMIT_EXCEEDED",
          "Manifest exceeds the bounded artifact-count limit", "artifacts"));
      return 0L;
    }

    long totalBytes = 0L;
    for (JsonElement element : array) {
      if (!element.isJsonObject()) {
        diagnostics.add(error("DELIVERY_ARTIFACT_INVALID",
            "Every artifact entry must be a JSON object", "artifacts"));
        continue;
      }
      JsonObject item = element.getAsJsonObject();
      String relativePath = text(item, "relativePath", diagnostics);
      String mediaType = text(item, "mediaType", diagnostics);
      String expectedSha256 = text(item, "sha256", diagnostics);
      Long expectedSize = longValue(item, "sizeBytes", diagnostics);
      if (relativePath == null || mediaType == null || expectedSha256 == null
          || expectedSize == null) {
        continue;
      }
      if (artifacts.containsKey(relativePath)) {
        diagnostics.add(error("DELIVERY_ARTIFACT_DUPLICATE",
            "Manifest contains a duplicate artifact path", relativePath));
        continue;
      }
      if (!isSafeRelativePath(relativePath) || MANIFEST_FILE.equals(relativePath)) {
        diagnostics.add(error("DELIVERY_ARTIFACT_PATH_UNSAFE",
            "Artifact path must be a portable delivery-root-relative path", relativePath));
        continue;
      }
      if (!expectedSha256.matches("[0-9a-f]{64}")) {
        diagnostics.add(error("DELIVERY_ARTIFACT_SHA256_INVALID",
            "Artifact SHA-256 must contain 64 lowercase hexadecimal characters", relativePath));
        continue;
      }
      if (expectedSize.longValue() < 0L || expectedSize.longValue() > MAX_ARTIFACT_BYTES) {
        diagnostics.add(error("DELIVERY_ARTIFACT_SIZE_LIMIT_EXCEEDED",
            "Artifact size is negative or exceeds the bounded per-artifact limit", relativePath));
        continue;
      }
      if (expectedSize.longValue() > MAX_TOTAL_ARTIFACT_BYTES - totalBytes) {
        diagnostics.add(error("DELIVERY_TOTAL_SIZE_LIMIT_EXCEEDED",
            "Declared delivery artifacts exceed the bounded total-size limit", relativePath));
        continue;
      }
      verifyMediaType(relativePath, mediaType, diagnostics);

      Path target = root.resolve(relativePath).normalize();
      if (!target.startsWith(root) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
        diagnostics.add(error("DELIVERY_ARTIFACT_MISSING",
            "Declared artifact is missing or is not a regular file", relativePath));
        continue;
      }
      Path realTarget = target.toRealPath();
      if (!realTarget.startsWith(root)) {
        diagnostics.add(error("DELIVERY_ARTIFACT_ESCAPES_ROOT",
            "Artifact resolves outside the delivery root", relativePath));
        continue;
      }
      byte[] bytes = readBounded(realTarget, MAX_ARTIFACT_BYTES,
          "Delivery artifact exceeds the bounded intake limit: " + relativePath);
      totalBytes += bytes.length;
      String actualSha256 = sha256(bytes);
      if (expectedSize.longValue() != bytes.length) {
        diagnostics.add(error("DELIVERY_ARTIFACT_SIZE_MISMATCH",
            "Artifact byte length does not match its manifest evidence", relativePath));
      }
      if (!expectedSha256.equals(actualSha256)) {
        diagnostics.add(error("DELIVERY_ARTIFACT_SHA256_MISMATCH",
            "Artifact SHA-256 does not match its manifest evidence", relativePath));
      }
      artifacts.put(relativePath,
          new ArtifactEvidence(relativePath, mediaType, bytes.length, actualSha256));
    }
    return totalBytes;
  }

  private static void verifyActualFileSet(Path root, Map<String, ArtifactEvidence> artifacts,
      List<Diagnostic> diagnostics) throws IOException {
    List<String> actualFiles = new ArrayList<String>();
    collectFiles(root, root, actualFiles, diagnostics);
    Collections.sort(actualFiles);
    for (String relativePath : actualFiles) {
      if (!MANIFEST_FILE.equals(relativePath) && !artifacts.containsKey(relativePath)) {
        diagnostics.add(error("DELIVERY_UNLISTED_FILE",
            "Delivery contains a file that is not declared in the manifest", relativePath));
      }
    }
  }

  private static void collectFiles(Path root, Path directory, List<String> files,
      List<Diagnostic> diagnostics) throws IOException {
    DirectoryStream<Path> children = Files.newDirectoryStream(directory);
    try {
      for (Path child : children) {
        String relativePath = root.relativize(child).toString().replace('\\', '/');
        if (Files.isSymbolicLink(child)) {
          diagnostics.add(error("DELIVERY_SYMBOLIC_LINK_REJECTED",
              "Delivery content must not contain symbolic links", relativePath));
        } else if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
          collectFiles(root, child, files, diagnostics);
        } else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
          files.add(relativePath);
        } else {
          diagnostics.add(error("DELIVERY_UNSUPPORTED_FILE_TYPE",
              "Delivery contains an unsupported filesystem object", relativePath));
        }
      }
    } finally {
      children.close();
    }
  }

  private static void verifyRequiredArtifacts(String sourceScope,
      Map<String, ArtifactEvidence> artifacts, List<Diagnostic> diagnostics) {
    requireArtifact(artifacts, "document-set.json", diagnostics);
    requireArtifact(artifacts, "drawing-set.pdf", diagnostics);
    boolean hasSvg = false;
    for (String path : artifacts.keySet()) {
      hasSvg = hasSvg || path.startsWith("svg/") && path.endsWith(".svg");
    }
    if (!hasSvg) {
      diagnostics.add(error("DELIVERY_SVG_MISSING",
          "Delivery must contain at least one declared native SVG sheet", "svg/"));
    }
    if ("PROCESS_SYSTEM".equals(sourceScope)) {
      requireArtifact(artifacts, "dexpi-process.xml", diagnostics);
      rejectArtifact(artifacts, "dexpi-process-model.zip", diagnostics);
    } else if ("PROCESS_MODEL".equals(sourceScope)) {
      requireArtifact(artifacts, "dexpi-process-model.zip", diagnostics);
      rejectArtifact(artifacts, "dexpi-process.xml", diagnostics);
    } else {
      diagnostics.add(error("DELIVERY_SOURCE_SCOPE_INVALID",
          "sourceScope must be PROCESS_SYSTEM or PROCESS_MODEL", sourceScope));
    }
  }

  private static void requireArtifact(Map<String, ArtifactEvidence> artifacts, String path,
      List<Diagnostic> diagnostics) {
    if (!artifacts.containsKey(path)) {
      diagnostics.add(error("DELIVERY_REQUIRED_ARTIFACT_MISSING",
          "Delivery does not contain a required declared artifact", path));
    }
  }

  private static void rejectArtifact(Map<String, ArtifactEvidence> artifacts, String path,
      List<Diagnostic> diagnostics) {
    if (artifacts.containsKey(path)) {
      diagnostics.add(error("DELIVERY_SOURCE_SCOPE_ARTIFACT_CONFLICT",
          "Delivery contains a DEXPI artifact inconsistent with its source scope", path));
    }
  }

  private static void verifyMediaType(String path, String mediaType,
      List<Diagnostic> diagnostics) {
    String expected = null;
    if (path.endsWith(".json")) {
      expected = "application/json";
    } else if (path.endsWith(".pdf")) {
      expected = "application/pdf";
    } else if (path.endsWith(".svg")) {
      expected = "image/svg+xml";
    } else if (path.endsWith(".xml")) {
      expected = "application/xml";
    } else if (path.endsWith(".zip")) {
      expected = "application/zip";
    }
    if (expected == null || !expected.equals(mediaType)) {
      diagnostics.add(error("DELIVERY_ARTIFACT_MEDIA_TYPE_INVALID",
          "Artifact media type does not match the supported delivery contract", path));
    }
  }

  private static boolean isSafeRelativePath(String value) {
    if (value.isEmpty() || value.indexOf('\\') >= 0 || value.startsWith("/")
        || value.matches("^[A-Za-z]:.*")) {
      return false;
    }
    Path path;
    try {
      path = Paths.get(value);
    } catch (RuntimeException exception) {
      return false;
    }
    if (path.isAbsolute() || !path.normalize().toString().replace('\\', '/').equals(value)) {
      return false;
    }
    for (Path part : path) {
      if ("..".equals(part.toString()) || ".".equals(part.toString())) {
        return false;
      }
    }
    return true;
  }

  private static void verifyManifestFingerprint(JsonObject manifest, String expected,
      List<Diagnostic> diagnostics) {
    if (expected == null || !expected.matches("[0-9a-f]{64}")) {
      diagnostics.add(error("DELIVERY_MANIFEST_FINGERPRINT_INVALID",
          "Manifest fingerprint must contain 64 lowercase hexadecimal characters", "fingerprint"));
      return;
    }
    JsonObject withoutFingerprint = new JsonObject();
    for (Map.Entry<String, JsonElement> entry : manifest.entrySet()) {
      if (!"fingerprint".equals(entry.getKey())) {
        withoutFingerprint.add(entry.getKey(), entry.getValue());
      }
    }
    Gson gson = new GsonBuilder().create();
    String actual = sha256(gson.toJson(withoutFingerprint).getBytes(StandardCharsets.UTF_8));
    if (!expected.equals(actual)) {
      diagnostics.add(error("DELIVERY_MANIFEST_FINGERPRINT_MISMATCH",
          "Manifest content does not match its deterministic fingerprint", "fingerprint"));
    }
  }

  private static String text(JsonObject object, String name, List<Diagnostic> diagnostics) {
    JsonElement value = object.get(name);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
        || value.getAsString().trim().isEmpty()) {
      diagnostics.add(error("DELIVERY_MANIFEST_FIELD_INVALID",
          "Manifest field must be a non-blank string", name));
      return null;
    }
    return value.getAsString();
  }

  private static Long longValue(JsonObject object, String name, List<Diagnostic> diagnostics) {
    JsonElement value = object.get(name);
    try {
      if (value == null || !value.isJsonPrimitive()
          || !value.getAsJsonPrimitive().isNumber()
          || !value.getAsString().matches("-?[0-9]+")) {
        throw new NumberFormatException(name);
      }
      return Long.valueOf(value.getAsLong());
    } catch (RuntimeException exception) {
      diagnostics.add(error("DELIVERY_MANIFEST_FIELD_INVALID",
          "Manifest field must be an integer", name));
      return null;
    }
  }

  private static boolean booleanValue(JsonObject object, String name,
      List<Diagnostic> diagnostics) {
    JsonElement value = object.get(name);
    if (value == null || !value.isJsonPrimitive()
        || !value.getAsJsonPrimitive().isBoolean()) {
      diagnostics.add(error("DELIVERY_MANIFEST_FIELD_INVALID",
          "Manifest field must be a boolean", name));
      return false;
    }
    return value.getAsBoolean();
  }

  private static void requireBoundary(JsonObject object, String name, String expected,
      List<Diagnostic> diagnostics) {
    String value = text(object, name, diagnostics);
    if (value != null && !expected.equals(value)) {
      diagnostics.add(error("DELIVERY_QUALIFICATION_BOUNDARY_INVALID",
          "Delivery qualification boundary has an unsupported value", name));
    }
  }

  private static void requireFalse(JsonObject object, String name,
      List<Diagnostic> diagnostics) {
    JsonElement value = object.get(name);
    if (value == null || !value.isJsonPrimitive()
        || !value.getAsJsonPrimitive().isBoolean() || value.getAsBoolean()) {
      diagnostics.add(error("DELIVERY_QUALIFICATION_BOUNDARY_INVALID",
          "Delivery qualification boundary must remain false", name));
    }
  }

  private static byte[] readBounded(Path path, long limit, String message) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    InputStream input = Files.newInputStream(path);
    try {
      byte[] buffer = new byte[8192];
      long total = 0L;
      int count;
      while ((count = input.read(buffer)) != -1) {
        total += count;
        if (total > limit) {
          throw new IOException(message);
        }
        output.write(buffer, 0, count);
      }
    } finally {
      input.close();
    }
    return output.toByteArray();
  }

  private static Diagnostic error(String code, String message, String subject) {
    return new Diagnostic(Severity.ERROR, code, message, subject);
  }

  private static boolean hasErrors(List<Diagnostic> diagnostics) {
    for (Diagnostic diagnostic : diagnostics) {
      if (diagnostic.getSeverity() == Severity.ERROR) {
        return true;
      }
    }
    return false;
  }

  private static String sha256(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(bytes);
      StringBuilder result = new StringBuilder();
      for (byte value : digest.digest()) {
        result.append(String.format(Locale.ROOT, "%02x", Integer.valueOf(value & 255)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by every supported Java runtime",
          exception);
    }
  }
}
