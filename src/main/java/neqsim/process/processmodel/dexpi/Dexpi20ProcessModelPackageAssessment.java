package neqsim.process.processmodel.dexpi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Assesses a deterministic multi-area NeqSim DEXPI Process package without trusting its manifest.
 *
 * <p>
 * The assessment verifies archive safety, the controlled package contract, exact entry hashes, stable area and
 * connection identities, and the mandatory engineering-review boundary. Every embedded area XML is independently
 * reassessed against the bundled DEXPI 2.0 schema, official Process imports, and NeqSim supported semantic profile. The
 * package remains a NeqSim container; successful assessment is not native whole-plant DEXPI conformance or drawing
 * approval.
 * </p>
 */
public final class Dexpi20ProcessModelPackageAssessment {
  private static final String MANIFEST_ENTRY = "manifest.json";
  private static final String SCHEMA_VERSION = "neqsim_dexpi_2_0_process_model_package.v1";
  private static final String PACKAGE_FORMAT = "NEQSIM_DETERMINISTIC_ZIP_WITH_NATIVE_DEXPI_AREA_FILES";
  private static final long MAX_ENTRY_BYTES = 64L * 1024L * 1024L;
  private static final long MAX_PACKAGE_CONTENT_BYTES = 256L * 1024L * 1024L;
  private static final int MAX_ENTRIES = 1024;

  private Dexpi20ProcessModelPackageAssessment() {
  }

  /** Severity of one independently generated package-assessment diagnostic. */
  public enum Severity {
    /** Informational assessment evidence. */
    INFO,
    /** Retained limitation that does not invalidate package integrity. */
    WARNING,
    /** Invalid or unsafe package evidence. */
    ERROR
  }

  /** Immutable package-assessment diagnostic. */
  public static final class Diagnostic implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Severity severity;
    private final String code;
    private final String message;
    private final String subject;

    Diagnostic(Severity severity, String code, String message, String subject) {
      this.severity = severity;
      this.code = code;
      this.message = message;
      this.subject = subject == null ? "" : subject;
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

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("severity", severity.name());
      result.put("code", code);
      result.put("message", message);
      result.put("subject", subject);
      return result;
    }
  }

  /** Independently verified evidence for one declared area exchange. */
  public static final class AreaEvidence implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String areaName;
    private final String areaId;
    private final String entryName;
    private final String declaredFileSha256;
    private final String actualFileSha256;
    private final Dexpi20ConformanceAssessment.Report conformanceReport;

    AreaEvidence(String areaName, String areaId, String entryName, String declaredFileSha256, String actualFileSha256,
        Dexpi20ConformanceAssessment.Report conformanceReport) {
      this.areaName = areaName;
      this.areaId = areaId;
      this.entryName = entryName;
      this.declaredFileSha256 = declaredFileSha256;
      this.actualFileSha256 = actualFileSha256;
      this.conformanceReport = conformanceReport;
    }

    public String getAreaName() {
      return areaName;
    }

    public String getAreaId() {
      return areaId;
    }

    public String getEntryName() {
      return entryName;
    }

    public String getDeclaredFileSha256() {
      return declaredFileSha256;
    }

    public String getActualFileSha256() {
      return actualFileSha256;
    }

    public Dexpi20ConformanceAssessment.Report getConformanceReport() {
      return conformanceReport;
    }

    public boolean isFileHashValid() {
      return declaredFileSha256.equals(actualFileSha256);
    }

    public boolean isSchemaAndProfileConformant() {
      return conformanceReport != null && conformanceReport.isSchemaAndProfileConformant();
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("areaName", areaName);
      result.put("areaId", areaId);
      result.put("entryName", entryName);
      result.put("declaredFileSha256", declaredFileSha256);
      result.put("actualFileSha256", actualFileSha256);
      result.put("fileHashValid", Boolean.valueOf(isFileHashValid()));
      result.put("schemaAndProfileConformant", Boolean.valueOf(isSchemaAndProfileConformant()));
      if (conformanceReport != null) {
        result.put("conformance", conformanceReport.toMap());
      }
      return result;
    }
  }

  /** Independently verified evidence for one declared plant-wide connection. */
  public static final class ConnectionEvidence implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String connectionId;
    private final String connectionType;
    private final String sourceArea;
    private final String targetArea;
    private final String sourceEquipment;
    private final String targetEquipment;
    private final String sourcePort;
    private final String targetPort;
    private final boolean crossArea;
    private final boolean recycle;
    private final String exchangeStatus;

    ConnectionEvidence(String connectionId, String connectionType, String sourceArea, String targetArea,
        String sourceEquipment, String targetEquipment, String sourcePort, String targetPort, boolean crossArea,
        boolean recycle, String exchangeStatus) {
      this.connectionId = connectionId;
      this.connectionType = connectionType;
      this.sourceArea = sourceArea;
      this.targetArea = targetArea;
      this.sourceEquipment = sourceEquipment;
      this.targetEquipment = targetEquipment;
      this.sourcePort = sourcePort;
      this.targetPort = targetPort;
      this.crossArea = crossArea;
      this.recycle = recycle;
      this.exchangeStatus = exchangeStatus;
    }

    public String getConnectionId() {
      return connectionId;
    }

    public String getConnectionType() {
      return connectionType;
    }

    public String getSourceArea() {
      return sourceArea;
    }

    public String getTargetArea() {
      return targetArea;
    }

    public String getSourceEquipment() {
      return sourceEquipment;
    }

    public String getTargetEquipment() {
      return targetEquipment;
    }

    public String getSourcePort() {
      return sourcePort;
    }

    public String getTargetPort() {
      return targetPort;
    }

    public boolean isCrossArea() {
      return crossArea;
    }

    public boolean isRecycle() {
      return recycle;
    }

    public String getExchangeStatus() {
      return exchangeStatus;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("connectionId", connectionId);
      result.put("connectionType", connectionType);
      result.put("sourceArea", sourceArea);
      result.put("targetArea", targetArea);
      result.put("sourceEquipment", sourceEquipment);
      result.put("targetEquipment", targetEquipment);
      result.put("sourcePort", sourcePort);
      result.put("targetPort", targetPort);
      result.put("crossArea", Boolean.valueOf(crossArea));
      result.put("recycle", Boolean.valueOf(recycle));
      result.put("exchangeStatus", exchangeStatus);
      return result;
    }
  }

  /** Immutable independent assessment of one package file. */
  public static final class Report implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String packageFileSha256;
    private final String manifestSha256;
    private final String plantId;
    private final String revision;
    private final String operatingCaseId;
    private final String canonicalFingerprint;
    private final List<AreaEvidence> areaEvidence;
    private final List<ConnectionEvidence> connectionEvidence;
    private final List<Diagnostic> diagnostics;

    Report(String packageFileSha256, String manifestSha256, String plantId, String revision, String operatingCaseId,
        String canonicalFingerprint, List<AreaEvidence> areaEvidence, List<ConnectionEvidence> connectionEvidence,
        List<Diagnostic> diagnostics) {
      this.packageFileSha256 = packageFileSha256;
      this.manifestSha256 = manifestSha256;
      this.plantId = plantId;
      this.revision = revision;
      this.operatingCaseId = operatingCaseId;
      this.canonicalFingerprint = canonicalFingerprint;
      this.areaEvidence = Collections.unmodifiableList(new ArrayList<AreaEvidence>(areaEvidence));
      this.connectionEvidence =
          Collections.unmodifiableList(new ArrayList<ConnectionEvidence>(connectionEvidence));
      this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
    }

    public String getPackageFileSha256() {
      return packageFileSha256;
    }

    public String getManifestSha256() {
      return manifestSha256;
    }

    public String getPlantId() {
      return plantId;
    }

    public String getRevision() {
      return revision;
    }

    public String getOperatingCaseId() {
      return operatingCaseId;
    }

    public String getCanonicalFingerprint() {
      return canonicalFingerprint;
    }

    public List<AreaEvidence> getAreaEvidence() {
      return areaEvidence;
    }

    public List<ConnectionEvidence> getConnectionEvidence() {
      return connectionEvidence;
    }

    public List<Diagnostic> getDiagnostics() {
      return diagnostics;
    }

    /** @return true when the independently assessed archive and declared contents have no error */
    public boolean isValid() {
      for (Diagnostic diagnostic : diagnostics) {
        if (diagnostic.getSeverity() == Severity.ERROR) {
          return false;
        }
      }
      return true;
    }

    /**
     * Returns false because this assessment covers a NeqSim package around native area exchanges.
     *
     * @return always false
     */
    public boolean isNativeWholePlantDexpiExchange() {
      return false;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "neqsim_dexpi_2_0_process_model_package_assessment.v1");
      result.put("packageFileSha256", packageFileSha256);
      result.put("manifestSha256", manifestSha256);
      result.put("plantId", plantId);
      result.put("revision", revision);
      if (operatingCaseId != null) {
        result.put("operatingCaseId", operatingCaseId);
      }
      result.put("canonicalFingerprint", canonicalFingerprint);
      result.put("valid", Boolean.valueOf(isValid()));
      result.put("nativeWholePlantDexpiExchange", Boolean.FALSE);
      result.put("approvalStatus", "REVIEW_REQUIRED");
      result.put("fitnessForConstruction", Boolean.FALSE);
      List<Map<String, Object>> areas = new ArrayList<Map<String, Object>>();
      for (AreaEvidence area : areaEvidence) {
        areas.add(area.toMap());
      }
      result.put("areaEvidence", areas);
      List<Map<String, Object>> connections = new ArrayList<Map<String, Object>>();
      for (ConnectionEvidence connection : connectionEvidence) {
        connections.add(connection.toMap());
      }
      result.put("connectionEvidence", connections);
      List<Map<String, Object>> findings = new ArrayList<Map<String, Object>>();
      for (Diagnostic diagnostic : diagnostics) {
        findings.add(diagnostic.toMap());
      }
      result.put("diagnostics", findings);
      result.put("assessmentScope",
          "Archive safety, manifest contract, exact hashes, stable identities, connection status, "
              + "engineering-review boundary, and independent per-area DEXPI 2.0 Process conformance");
      return result;
    }

    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }
  }

  /**
   * Independently assesses one deterministic multi-area package.
   *
   * @param packageFile package written by {@link Dexpi20ProcessModelPackageWriter}
   * @return immutable independent assessment
   * @throws IOException when the package file cannot be read
   */
  public static Report assess(File packageFile) throws IOException {
    if (packageFile == null) {
      throw new IllegalArgumentException("packageFile must not be null");
    }
    Path packagePath = packageFile.toPath();
    if (!Files.isRegularFile(packagePath)) {
      throw new IOException("DEXPI ProcessModel package is not a regular file: " + packagePath);
    }
    String packageSha = sha256(Files.newInputStream(packagePath));
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    Map<String, byte[]> entries = readArchive(packagePath, diagnostics);
    byte[] manifestBytes = entries.get(MANIFEST_ENTRY);
    if (manifestBytes == null) {
      diagnostics.add(
          error("DEXPI_PROCESS_PACKAGE_MANIFEST_MISSING", "Package does not contain manifest.json", MANIFEST_ENTRY));
      sortDiagnostics(diagnostics);
      return new Report(packageSha, "", "", "", null, "", Collections.<AreaEvidence>emptyList(),
          Collections.<ConnectionEvidence>emptyList(), diagnostics);
    }

    String manifestSha = sha256(new ByteArrayInputStream(manifestBytes));
    JsonObject manifest;
    try {
      manifest = new Gson().fromJson(new String(manifestBytes, StandardCharsets.UTF_8), JsonObject.class);
      if (manifest == null) {
        throw new JsonParseException("manifest root is null");
      }
    } catch (JsonParseException exception) {
      diagnostics.add(error("DEXPI_PROCESS_PACKAGE_MANIFEST_INVALID_JSON",
          "Package manifest is not valid JSON: " + exception.getMessage(), MANIFEST_ENTRY));
      sortDiagnostics(diagnostics);
      return new Report(packageSha, manifestSha, "", "", null, "", Collections.<AreaEvidence>emptyList(),
          Collections.<ConnectionEvidence>emptyList(), diagnostics);
    }

    String plantId = requiredText(manifest, "plantId", diagnostics);
    String revision = requiredText(manifest, "revision", diagnostics);
    String operatingCaseId = optionalText(manifest, "operatingCaseId", diagnostics);
    String canonicalFingerprint = requiredText(manifest, "canonicalFingerprint", diagnostics);
    verifyManifestContract(manifest, canonicalFingerprint, diagnostics);

    Set<String> declaredAreaEntries = new LinkedHashSet<String>();
    Set<String> areaNames = new LinkedHashSet<String>();
    Set<String> areaIds = new LinkedHashSet<String>();
    List<AreaEvidence> areas = assessAreas(manifest, entries, declaredAreaEntries, areaNames, areaIds, diagnostics);
    List<ConnectionEvidence> connections = verifyConnections(manifest, areaNames, diagnostics);
    verifyDeclaredEntries(entries.keySet(), declaredAreaEntries, diagnostics);
    sortDiagnostics(diagnostics);
    return new Report(packageSha, manifestSha, plantId, revision, operatingCaseId, canonicalFingerprint, areas,
        connections, diagnostics);
  }

  private static Map<String, byte[]> readArchive(Path packagePath, List<Diagnostic> diagnostics) throws IOException {
    Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
    ZipInputStream input = new ZipInputStream(Files.newInputStream(packagePath), StandardCharsets.UTF_8);
    long totalBytes = 0L;
    int entryCount = 0;
    try {
      ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        entryCount++;
        String name = entry.getName();
        if (entryCount > MAX_ENTRIES) {
          diagnostics.add(error("DEXPI_PROCESS_PACKAGE_ENTRY_LIMIT_EXCEEDED",
              "Package contains more than " + MAX_ENTRIES + " entries", name));
          break;
        }
        if (!isSafeEntryName(name)) {
          diagnostics.add(error("DEXPI_PROCESS_PACKAGE_UNSAFE_ENTRY_NAME",
              "Archive entry is absolute, parent-relative, or uses a backslash", name));
        }
        if (entry.isDirectory()) {
          diagnostics.add(error("DEXPI_PROCESS_PACKAGE_DIRECTORY_ENTRY_UNEXPECTED",
              "Deterministic package must contain files only", name));
          continue;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long entryBytes = 0L;
        int count;
        while ((count = input.read(buffer)) != -1) {
          entryBytes += count;
          totalBytes += count;
          if (entryBytes > MAX_ENTRY_BYTES || totalBytes > MAX_PACKAGE_CONTENT_BYTES) {
            diagnostics.add(error("DEXPI_PROCESS_PACKAGE_UNCOMPRESSED_SIZE_LIMIT_EXCEEDED",
                "Archive content exceeds the bounded assessment limit", name));
            throw new IOException("DEXPI package exceeds bounded uncompressed size limit");
          }
          output.write(buffer, 0, count);
        }
        if (entries.put(name, output.toByteArray()) != null) {
          diagnostics
              .add(error("DEXPI_PROCESS_PACKAGE_DUPLICATE_ENTRY", "Archive contains a duplicate entry name", name));
        }
      }
    } finally {
      input.close();
    }
    return entries;
  }

  private static boolean isSafeEntryName(String name) {
    if (name == null || name.isEmpty() || name.startsWith("/") || name.indexOf('\\') >= 0) {
      return false;
    }
    for (String part : name.split("/")) {
      if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
        return false;
      }
    }
    return true;
  }

  private static void verifyManifestContract(JsonObject manifest, String canonicalFingerprint,
      List<Diagnostic> diagnostics) {
    requireValue(manifest, "schemaVersion", SCHEMA_VERSION, diagnostics);
    requireValue(manifest, "packageFormat", PACKAGE_FORMAT, diagnostics);
    requireValue(manifest, "dexpiVersion", "2.0.0", diagnostics);
    requireValue(manifest, "dexpiModel", "Process", diagnostics);
    requireValue(manifest, "manifestEntry", MANIFEST_ENTRY, diagnostics);
    requireValue(manifest, "engineeringState", "CALCULATED", diagnostics);
    requireValue(manifest, "approvalStatus", "REVIEW_REQUIRED", diagnostics);
    requireFalse(manifest, "fitnessForConstruction", diagnostics);
    requireFalse(manifest, "nativeWholePlantDexpiExchange", diagnostics);
    requireTrue(manifest, "completeWithinDeclaredScope", diagnostics);
    if (!isSha256(canonicalFingerprint)) {
      diagnostics.add(error("DEXPI_PROCESS_PACKAGE_CANONICAL_FINGERPRINT_INVALID",
          "canonicalFingerprint must be a lowercase SHA-256 value", "canonicalFingerprint"));
    }
  }

  private static List<AreaEvidence> assessAreas(JsonObject manifest, Map<String, byte[]> entries,
      Set<String> declaredEntries, Set<String> areaNames, Set<String> areaIds, List<Diagnostic> diagnostics)
      throws IOException {
    List<AreaEvidence> result = new ArrayList<AreaEvidence>();
    JsonArray areas = requiredArray(manifest, "areaExchanges", diagnostics);
    if (areas == null || areas.size() == 0) {
      diagnostics.add(error("DEXPI_PROCESS_PACKAGE_AREA_EXCHANGES_MISSING",
          "Package must declare at least one assessed area exchange", "areaExchanges"));
      return result;
    }
    for (int index = 0; index < areas.size(); index++) {
      JsonElement element = areas.get(index);
      String subject = "areaExchanges[" + index + "]";
      if (!element.isJsonObject()) {
        diagnostics.add(error("DEXPI_PROCESS_PACKAGE_AREA_DECLARATION_INVALID",
            "Area exchange declaration must be a JSON object", subject));
        continue;
      }
      JsonObject area = element.getAsJsonObject();
      String areaName = requiredText(area, "areaName", diagnostics, subject);
      String areaId = requiredText(area, "areaId", diagnostics, subject);
      requiredText(area, "processSystemName", diagnostics, subject);
      String entryName = requiredText(area, "entryName", diagnostics, subject);
      String declaredSha = requiredText(area, "fileSha256", diagnostics, subject);
      requireTrue(area, "schemaProfileAndSupportedTopologyValid", diagnostics, subject);
      addUnique(areaNames, areaName, "DEXPI_PROCESS_PACKAGE_DUPLICATE_AREA_NAME", diagnostics);
      addUnique(areaIds, areaId, "DEXPI_PROCESS_PACKAGE_DUPLICATE_AREA_ID", diagnostics);
      addUnique(declaredEntries, entryName, "DEXPI_PROCESS_PACKAGE_DUPLICATE_AREA_ENTRY", diagnostics);
      if (!entryName.startsWith("areas/") || !entryName.endsWith(".xml") || !isSafeEntryName(entryName)) {
        diagnostics.add(error("DEXPI_PROCESS_PACKAGE_AREA_ENTRY_NAME_INVALID",
            "Area entry must be a safe areas/*.xml path", entryName));
      }
      if (!areaId.matches("area:[a-z0-9][a-z0-9-]*")) {
        diagnostics.add(
            error("DEXPI_PROCESS_PACKAGE_AREA_ID_INVALID", "areaId must be a stable canonical area identity", areaId));
      }
      if (!isSha256(declaredSha)) {
        diagnostics.add(error("DEXPI_PROCESS_PACKAGE_AREA_HASH_INVALID", "fileSha256 must be a lowercase SHA-256 value",
            entryName));
      }
      byte[] xml = entries.get(entryName);
      if (xml == null) {
        diagnostics.add(error("DEXPI_PROCESS_PACKAGE_AREA_ENTRY_MISSING",
            "Declared area XML entry is absent from the archive", entryName));
        result.add(new AreaEvidence(areaName, areaId, entryName, declaredSha, "", null));
        continue;
      }
      String actualSha = sha256(new ByteArrayInputStream(xml));
      if (!declaredSha.equals(actualSha)) {
        diagnostics.add(error("DEXPI_PROCESS_PACKAGE_AREA_HASH_MISMATCH",
            "Declared area XML SHA-256 does not match archive content", entryName));
      }
      verifyNestedAreaHash(area, declaredSha, entryName, diagnostics);
      Dexpi20ConformanceAssessment.Report conformance = assessAreaXml(xml);
      if (!conformance.isSchemaAndProfileConformant()) {
        diagnostics.add(error("DEXPI_PROCESS_PACKAGE_AREA_CONFORMANCE_FAILED",
            "Embedded area XML failed independent DEXPI 2.0 Process assessment: " + conformance.getErrors(),
            entryName));
      }
      result.add(new AreaEvidence(areaName, areaId, entryName, declaredSha, actualSha, conformance));
    }
    Collections.sort(result, new Comparator<AreaEvidence>() {
      @Override
      public int compare(AreaEvidence first, AreaEvidence second) {
        return first.getEntryName().compareTo(second.getEntryName());
      }
    });
    return result;
  }

  private static Dexpi20ConformanceAssessment.Report assessAreaXml(byte[] xml) throws IOException {
    Path temporary = Files.createTempFile("neqsim-dexpi-process-package-assessment-", ".xml");
    try {
      Files.write(temporary, xml);
      return Dexpi20ConformanceAssessment.assess(temporary, Dexpi20ConformanceAssessment.Profile.PROCESS_PFD_BFD);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void verifyNestedAreaHash(JsonObject area, String declaredSha, String entryName,
      List<Diagnostic> diagnostics) {
    JsonElement assessmentElement = area.get("assessment");
    if (assessmentElement == null || !assessmentElement.isJsonObject()) {
      diagnostics.add(error("DEXPI_PROCESS_PACKAGE_AREA_ASSESSMENT_MISSING",
          "Area declaration has no embedded writer assessment", entryName));
      return;
    }
    JsonElement conformanceElement = assessmentElement.getAsJsonObject().get("conformance");
    if (conformanceElement == null || !conformanceElement.isJsonObject()) {
      diagnostics.add(error("DEXPI_PROCESS_PACKAGE_AREA_CONFORMANCE_EVIDENCE_MISSING",
          "Embedded writer assessment has no conformance evidence", entryName));
      return;
    }
    String nestedSha = optionalText(conformanceElement.getAsJsonObject(), "fileSha256", diagnostics, entryName);
    if (nestedSha == null || !declaredSha.equals(nestedSha)) {
      diagnostics.add(error("DEXPI_PROCESS_PACKAGE_AREA_ASSESSMENT_HASH_MISMATCH",
          "Embedded writer assessment hash does not match the area declaration", entryName));
    }
  }

  private static List<ConnectionEvidence> verifyConnections(JsonObject manifest, Set<String> areaNames,
      List<Diagnostic> diagnostics) {
    List<ConnectionEvidence> result = new ArrayList<ConnectionEvidence>();
    JsonArray connections = requiredArray(manifest, "connections", diagnostics);
    if (connections == null) {
      return result;
    }
    Set<String> ids = new LinkedHashSet<String>();
    Set<String> manifestDiagnosticKeys = manifestDiagnosticKeys(manifest);
    for (int index = 0; index < connections.size(); index++) {
      JsonElement element = connections.get(index);
      String subject = "connections[" + index + "]";
      if (!element.isJsonObject()) {
        diagnostics.add(error("DEXPI_PROCESS_PACKAGE_CONNECTION_DECLARATION_INVALID",
            "Connection declaration must be a JSON object", subject));
        continue;
      }
      JsonObject connection = element.getAsJsonObject();
      String id = requiredText(connection, "connectionId", diagnostics, subject);
      String type = requiredText(connection, "connectionType", diagnostics, id);
      String sourceArea = requiredText(connection, "sourceArea", diagnostics, id);
      String targetArea = requiredText(connection, "targetArea", diagnostics, id);
      String sourceEquipment = requiredText(connection, "sourceEquipment", diagnostics, id);
      String targetEquipment = requiredText(connection, "targetEquipment", diagnostics, id);
      String sourcePort = requiredText(connection, "sourcePort", diagnostics, id);
      String targetPort = requiredText(connection, "targetPort", diagnostics, id);
      String status = requiredText(connection, "exchangeStatus", diagnostics, id);
      Boolean crossArea = requiredBoolean(connection, "crossArea", diagnostics, id);
      Boolean recycle = requiredBoolean(connection, "recycle", diagnostics, id);
      addUnique(ids, id, "DEXPI_PROCESS_PACKAGE_DUPLICATE_CONNECTION_ID", diagnostics);
      if (!areaNames.contains(sourceArea) || !areaNames.contains(targetArea)) {
        diagnostics.add(error("DEXPI_PROCESS_PACKAGE_CONNECTION_AREA_UNKNOWN",
            "Connection sourceArea and targetArea must reference declared areas", id));
      }
      String expectedStatus = expectedConnectionStatus(type, Boolean.TRUE.equals(crossArea));
      if (expectedStatus == null) {
        diagnostics.add(error("DEXPI_PROCESS_PACKAGE_CONNECTION_TYPE_UNSUPPORTED",
            "connectionType must be MATERIAL, ENERGY, or SIGNAL", id));
      } else if (!expectedStatus.equals(status)) {
        diagnostics.add(error("DEXPI_PROCESS_PACKAGE_CONNECTION_STATUS_INVALID",
            "exchangeStatus does not match connection type and cross-area state", id));
      }
      String diagnosticCode = expectedDiagnosticCode(expectedStatus, type);
      if (diagnosticCode != null && !manifestDiagnosticKeys.contains(diagnosticCode + "\n" + id)) {
        diagnostics.add(error("DEXPI_PROCESS_PACKAGE_CONNECTION_LOSS_DIAGNOSTIC_MISSING",
            "Manifest-only connection has no matching structured loss diagnostic", id));
      }
      result.add(new ConnectionEvidence(id, type, sourceArea, targetArea, sourceEquipment, targetEquipment, sourcePort,
          targetPort, Boolean.TRUE.equals(crossArea), Boolean.TRUE.equals(recycle), status));
    }
    Collections.sort(result, new Comparator<ConnectionEvidence>() {
      @Override
      public int compare(ConnectionEvidence first, ConnectionEvidence second) {
        return first.getConnectionId().compareTo(second.getConnectionId());
      }
    });
    return result;
  }

  private static String expectedConnectionStatus(String type, boolean crossArea) {
    if ("MATERIAL".equals(type)) {
      return crossArea ? "MANIFEST_ONLY_CROSS_AREA" : "ASSESSED_AREA_DEXPI";
    }
    if ("ENERGY".equals(type) || "SIGNAL".equals(type)) {
      return "MANIFEST_ONLY_NOT_MAPPED_TO_NATIVE_PROCESS";
    }
    return null;
  }

  private static String expectedDiagnosticCode(String status, String type) {
    if ("MANIFEST_ONLY_CROSS_AREA".equals(status)) {
      return "DEXPI_PROCESS_PACKAGE_CROSS_AREA_CONNECTION_MANIFEST_ONLY";
    }
    if (!"MANIFEST_ONLY_NOT_MAPPED_TO_NATIVE_PROCESS".equals(status)) {
      return null;
    }
    return "ENERGY".equals(type) ? "DEXPI_PROCESS_PACKAGE_ENERGY_CONNECTION_MANIFEST_ONLY"
        : "SIGNAL".equals(type) ? "DEXPI_PROCESS_PACKAGE_SIGNAL_CONNECTION_MANIFEST_ONLY" : null;
  }

  private static Set<String> manifestDiagnosticKeys(JsonObject manifest) {
    Set<String> result = new LinkedHashSet<String>();
    JsonElement element = manifest.get("diagnostics");
    if (element == null || !element.isJsonArray()) {
      return result;
    }
    for (JsonElement item : element.getAsJsonArray()) {
      if (!item.isJsonObject()) {
        continue;
      }
      JsonObject diagnostic = item.getAsJsonObject();
      String code = scalarText(diagnostic.get("code"));
      String subject = scalarText(diagnostic.get("subject"));
      if (code != null && subject != null) {
        result.add(code + "\n" + subject);
      }
    }
    return result;
  }

  private static void verifyDeclaredEntries(Set<String> actualEntries, Set<String> declaredAreaEntries,
      List<Diagnostic> diagnostics) {
    Set<String> expected = new LinkedHashSet<String>(declaredAreaEntries);
    expected.add(MANIFEST_ENTRY);
    for (String entry : actualEntries) {
      if (!expected.contains(entry)) {
        diagnostics.add(error("DEXPI_PROCESS_PACKAGE_UNDECLARED_ENTRY",
            "Archive contains an entry not declared by the package manifest", entry));
      }
    }
  }

  private static void addUnique(Set<String> values, String value, String code, List<Diagnostic> diagnostics) {
    if (!values.add(value)) {
      diagnostics.add(error(code, "Package identity must be unique", value));
    }
  }

  private static JsonArray requiredArray(JsonObject object, String name, List<Diagnostic> diagnostics) {
    JsonElement value = object.get(name);
    if (value == null || !value.isJsonArray()) {
      diagnostics.add(error("DEXPI_PROCESS_PACKAGE_MANIFEST_FIELD_INVALID", name + " must be a JSON array", name));
      return null;
    }
    return value.getAsJsonArray();
  }

  private static String requiredText(JsonObject object, String name, List<Diagnostic> diagnostics) {
    return requiredText(object, name, diagnostics, name);
  }

  private static String requiredText(JsonObject object, String name, List<Diagnostic> diagnostics, String subject) {
    String value = optionalText(object, name, diagnostics, subject);
    if (value == null || value.trim().isEmpty()) {
      diagnostics
          .add(error("DEXPI_PROCESS_PACKAGE_MANIFEST_FIELD_MISSING", name + " must be a non-empty string", subject));
      return "";
    }
    return value;
  }

  private static String optionalText(JsonObject object, String name, List<Diagnostic> diagnostics) {
    return optionalText(object, name, diagnostics, name);
  }

  private static String optionalText(JsonObject object, String name, List<Diagnostic> diagnostics, String subject) {
    JsonElement value = object.get(name);
    if (value == null || value.isJsonNull()) {
      return null;
    }
    String text = scalarText(value);
    if (text == null) {
      diagnostics.add(error("DEXPI_PROCESS_PACKAGE_MANIFEST_FIELD_INVALID", name + " must be a JSON string", subject));
    }
    return text;
  }

  private static String scalarText(JsonElement value) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      return null;
    }
    return value.getAsString();
  }

  private static Boolean requiredBoolean(JsonObject object, String name, List<Diagnostic> diagnostics, String subject) {
    JsonElement value = object.get(name);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
      diagnostics.add(error("DEXPI_PROCESS_PACKAGE_MANIFEST_FIELD_INVALID", name + " must be a JSON boolean", subject));
      return null;
    }
    return Boolean.valueOf(value.getAsBoolean());
  }

  private static void requireValue(JsonObject object, String name, String expected, List<Diagnostic> diagnostics) {
    String actual = optionalText(object, name, diagnostics);
    if (!expected.equals(actual)) {
      diagnostics
          .add(error("DEXPI_PROCESS_PACKAGE_MANIFEST_CONTRACT_MISMATCH", name + " must equal " + expected, name));
    }
  }

  private static void requireFalse(JsonObject object, String name, List<Diagnostic> diagnostics) {
    requireBooleanValue(object, name, false, diagnostics, name);
  }

  private static void requireTrue(JsonObject object, String name, List<Diagnostic> diagnostics) {
    requireBooleanValue(object, name, true, diagnostics, name);
  }

  private static void requireTrue(JsonObject object, String name, List<Diagnostic> diagnostics, String subject) {
    requireBooleanValue(object, name, true, diagnostics, subject);
  }

  private static void requireBooleanValue(JsonObject object, String name, boolean expected,
      List<Diagnostic> diagnostics, String subject) {
    Boolean actual = requiredBoolean(object, name, diagnostics, subject);
    if (actual == null || actual.booleanValue() != expected) {
      diagnostics
          .add(error("DEXPI_PROCESS_PACKAGE_ENGINEERING_BOUNDARY_INVALID", name + " must remain " + expected, subject));
    }
  }

  private static boolean isSha256(String value) {
    return value != null && value.matches("[0-9a-f]{64}");
  }

  private static String sha256(InputStream input) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      try {
        int count;
        while ((count = input.read(buffer)) != -1) {
          digest.update(buffer, 0, count);
        }
      } finally {
        input.close();
      }
      StringBuilder result = new StringBuilder();
      for (byte value : digest.digest()) {
        result.append(String.format(Locale.ROOT, "%02x", Integer.valueOf(value & 255)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by every supported Java runtime", exception);
    }
  }

  private static void sortDiagnostics(List<Diagnostic> diagnostics) {
    Collections.sort(diagnostics, new Comparator<Diagnostic>() {
      @Override
      public int compare(Diagnostic first, Diagnostic second) {
        int bySeverity = first.getSeverity().compareTo(second.getSeverity());
        if (bySeverity != 0) {
          return bySeverity;
        }
        int byCode = first.getCode().compareTo(second.getCode());
        return byCode == 0 ? first.getSubject().compareTo(second.getSubject()) : byCode;
      }
    });
  }

  private static Diagnostic error(String code, String message, String subject) {
    return new Diagnostic(Severity.ERROR, code, message, subject);
  }
}
