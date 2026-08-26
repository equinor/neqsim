package neqsim.process.processmodel.dexpi;

import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.diagram.ProcessDiagramGraphAdapter;

/**
 * Writes a deterministic multi-area NeqSim package containing native DEXPI 2.0 Process exchanges.
 *
 * <p>
 * DEXPI Process files remain independently schema/profile assessed, one per {@link ProcessSystem} area. A NeqSim
 * manifest preserves the plant-wide canonical graph fingerprint, stable area identities and cross-area connection
 * identities. The package is not a new DEXPI profile and does not claim that manifest-only connections are present in a
 * native whole-plant DEXPI model.
 * </p>
 */
public final class Dexpi20ProcessModelPackageWriter {
  private static final String MANIFEST_ENTRY = "manifest.json";
  private static final String SCHEMA_VERSION = "neqsim_dexpi_2_0_process_model_package.v1";

  private Dexpi20ProcessModelPackageWriter() {
  }

  /** Severity of one package-level diagnostic. */
  public enum Severity {
    /** Informational evidence about the bounded package representation. */
    INFO,
    /** Explicit retained limitation or manifest-only relationship. */
    WARNING,
    /** Invalid canonical or per-area exchange evidence. */
    ERROR
  }

  /** Immutable package diagnostic. */
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

  /** One native per-area DEXPI Process exchange in the package. */
  public static final class AreaExchange implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String areaName;
    private final String areaId;
    private final String processSystemName;
    private final String entryName;
    private final String fileSha256;
    private final Dexpi20ProcessTopologyAssessment.Report assessment;

    AreaExchange(String areaName, String areaId, String processSystemName, String entryName, String fileSha256,
        Dexpi20ProcessTopologyAssessment.Report assessment) {
      this.areaName = areaName;
      this.areaId = areaId;
      this.processSystemName = processSystemName;
      this.entryName = entryName;
      this.fileSha256 = fileSha256;
      this.assessment = assessment;
    }

    public String getAreaName() {
      return areaName;
    }

    public String getAreaId() {
      return areaId;
    }

    public String getProcessSystemName() {
      return processSystemName;
    }

    public String getEntryName() {
      return entryName;
    }

    public String getFileSha256() {
      return fileSha256;
    }

    public Dexpi20ProcessTopologyAssessment.Report getAssessment() {
      return assessment;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("areaName", areaName);
      result.put("areaId", areaId);
      result.put("processSystemName", processSystemName);
      result.put("entryName", entryName);
      result.put("fileSha256", fileSha256);
      result.put("schemaProfileAndSupportedTopologyValid",
          Boolean.valueOf(assessment.isSchemaProfileAndSupportedTopologyValid()));
      result.put("assessment", assessment.toMap());
      return result;
    }
  }

  /** One canonical plant-wide material, energy or information connection. */
  public static final class ConnectionRecord implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String connectionId;
    private final String externalKey;
    private final String connectionType;
    private final String sourceArea;
    private final String targetArea;
    private final String sourceEquipment;
    private final String targetEquipment;
    private final String sourcePort;
    private final String targetPort;
    private final String carriedObjectName;
    private final boolean crossArea;
    private final boolean recycle;
    private final String exchangeStatus;

    ConnectionRecord(EngineeringNode node) {
      connectionId = node.getId();
      externalKey = node.getExternalKey();
      connectionType = property(node, "connectionType");
      sourceArea = property(node, "sourceArea");
      targetArea = property(node, "targetArea");
      sourceEquipment = property(node, "sourceEquipment");
      targetEquipment = property(node, "targetEquipment");
      sourcePort = property(node, "sourcePort");
      targetPort = property(node, "targetPort");
      carriedObjectName = property(node, "carriedObjectName");
      crossArea = booleanProperty(node, "crossArea");
      recycle = booleanProperty(node, "recycle");
      exchangeStatus = "MATERIAL".equals(connectionType)
          ? crossArea ? "MANIFEST_ONLY_CROSS_AREA" : "ASSESSED_AREA_DEXPI"
          : "MANIFEST_ONLY_NOT_MAPPED_TO_NATIVE_PROCESS";
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

    public boolean isCrossArea() {
      return crossArea;
    }

    public String getExchangeStatus() {
      return exchangeStatus;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("connectionId", connectionId);
      result.put("externalKey", externalKey);
      result.put("connectionType", connectionType);
      result.put("sourceArea", sourceArea);
      result.put("targetArea", targetArea);
      result.put("sourceEquipment", sourceEquipment);
      result.put("targetEquipment", targetEquipment);
      result.put("sourcePort", sourcePort);
      result.put("targetPort", targetPort);
      result.put("carriedObjectName", carriedObjectName);
      result.put("crossArea", Boolean.valueOf(crossArea));
      result.put("recycle", Boolean.valueOf(recycle));
      result.put("exchangeStatus", exchangeStatus);
      return result;
    }
  }

  /** Immutable evidence for one deterministic multi-area package. */
  public static final class Report implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String plantId;
    private final String revision;
    private final String operatingCaseId;
    private final String canonicalFingerprint;
    private final List<AreaExchange> areaExchanges;
    private final List<ConnectionRecord> connections;
    private final List<Diagnostic> diagnostics;
    private final String packageFileSha256;
    private final String manifestSha256;

    Report(String plantId, String revision, String operatingCaseId, String canonicalFingerprint,
        List<AreaExchange> areaExchanges, List<ConnectionRecord> connections, List<Diagnostic> diagnostics,
        String packageFileSha256, String manifestSha256) {
      this.plantId = plantId;
      this.revision = revision;
      this.operatingCaseId = operatingCaseId;
      this.canonicalFingerprint = canonicalFingerprint;
      this.areaExchanges = Collections.unmodifiableList(new ArrayList<AreaExchange>(areaExchanges));
      this.connections = Collections.unmodifiableList(new ArrayList<ConnectionRecord>(connections));
      this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
      this.packageFileSha256 = packageFileSha256;
      this.manifestSha256 = manifestSha256;
    }

    /** @return canonical plant-wide engineering-graph SHA-256 fingerprint */
    public String getCanonicalFingerprint() {
      return canonicalFingerprint;
    }

    /** @return controlled plant or project identifier */
    public String getPlantId() {
      return plantId;
    }

    /** @return controlled source-model revision */
    public String getRevision() {
      return revision;
    }

    /** @return operating-case identifier, or {@code null} for topology-only output */
    public String getOperatingCaseId() {
      return operatingCaseId;
    }

    public List<AreaExchange> getAreaExchanges() {
      return areaExchanges;
    }

    public List<ConnectionRecord> getConnections() {
      return connections;
    }

    public List<Diagnostic> getDiagnostics() {
      return diagnostics;
    }

    public String getPackageFileSha256() {
      return packageFileSha256;
    }

    public String getManifestSha256() {
      return manifestSha256;
    }

    /**
     * Returns whether every area exchange and the canonical package evidence passed its bounded gates.
     *
     * @return true when no package error exists and every area assessment passes
     */
    public boolean isComplete() {
      for (AreaExchange area : areaExchanges) {
        if (!area.getAssessment().isSchemaProfileAndSupportedTopologyValid()) {
          return false;
        }
      }
      for (Diagnostic diagnostic : diagnostics) {
        if (diagnostic.getSeverity() == Severity.ERROR) {
          return false;
        }
      }
      return true;
    }

    /**
     * Returns false because the ZIP manifest is a NeqSim container, not an official DEXPI whole-plant profile.
     *
     * @return always false
     */
    public boolean isNativeWholePlantDexpiExchange() {
      return false;
    }

    /** Returns the deterministic embedded manifest without the enclosing archive fingerprints. */
    public String toManifestJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(manifestMap());
    }

    /** Returns deterministic report JSON including the exact archive and manifest fingerprints. */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = manifestMap();
      result.put("manifestSha256", manifestSha256);
      result.put("packageFileSha256", packageFileSha256);
      return result;
    }

    private Map<String, Object> manifestMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", SCHEMA_VERSION);
      result.put("packageFormat", "NEQSIM_DETERMINISTIC_ZIP_WITH_NATIVE_DEXPI_AREA_FILES");
      result.put("dexpiVersion", "2.0.0");
      result.put("dexpiModel", "Process");
      result.put("manifestEntry", MANIFEST_ENTRY);
      result.put("plantId", plantId);
      result.put("revision", revision);
      if (operatingCaseId != null) {
        result.put("operatingCaseId", operatingCaseId);
      }
      result.put("canonicalFingerprint", canonicalFingerprint);
      result.put("engineeringState", "CALCULATED");
      result.put("approvalStatus", "REVIEW_REQUIRED");
      result.put("fitnessForConstruction", Boolean.FALSE);
      result.put("nativeWholePlantDexpiExchange", Boolean.FALSE);
      List<Map<String, Object>> areaMaps = new ArrayList<Map<String, Object>>();
      for (AreaExchange area : areaExchanges) {
        areaMaps.add(area.toMap());
      }
      result.put("areaExchanges", areaMaps);
      List<Map<String, Object>> connectionMaps = new ArrayList<Map<String, Object>>();
      for (ConnectionRecord connection : connections) {
        connectionMaps.add(connection.toMap());
      }
      result.put("connections", connectionMaps);
      List<Map<String, Object>> diagnosticMaps = new ArrayList<Map<String, Object>>();
      for (Diagnostic diagnostic : diagnostics) {
        diagnosticMaps.add(diagnostic.toMap());
      }
      result.put("diagnostics", diagnosticMaps);
      result.put("completeWithinDeclaredScope", Boolean.valueOf(isComplete()));
      return result;
    }
  }

  /**
   * Writes topology-only area exchanges and a plant-wide manifest.
   *
   * @param processModel source multi-area model
   * @param packageFile destination ZIP file
   * @param plantId controlled plant or project identifier
   * @param revision controlled source-model revision
   * @return deterministic package evidence
   * @throws IOException if an area exchange or archive cannot be written
   */
  public static Report writeAndAssess(ProcessModel processModel, File packageFile, String plantId, String revision)
      throws IOException {
    return writeAndAssess(processModel, packageFile, plantId, revision, null);
  }

  /**
   * Writes area exchanges with selected canonical operating values and a plant-wide manifest.
   *
   * @param processModel source multi-area model with successfully run areas
   * @param packageFile destination ZIP file
   * @param plantId controlled plant or project identifier
   * @param revision controlled source-model revision
   * @param operatingCaseId stable plant-wide operating-case identifier
   * @return deterministic package evidence
   * @throws IOException if an area exchange or archive cannot be written
   */
  public static Report writeAndAssess(ProcessModel processModel, File packageFile, String plantId, String revision,
      String operatingCaseId) throws IOException {
    if (processModel == null || packageFile == null) {
      throw new IllegalArgumentException("processModel and packageFile must not be null");
    }
    String controlledPlantId = requireText(plantId, "plantId");
    String controlledRevision = requireText(revision, "revision");
    String controlledCaseId = operatingCaseId == null ? null : requireText(operatingCaseId, "operatingCaseId");
    ProcessDiagramGraphAdapter.Result canonical = controlledCaseId == null
        ? ProcessDiagramGraphAdapter.fromProcessModel(processModel, controlledPlantId, controlledRevision)
        : ProcessDiagramGraphAdapter.fromProcessModel(processModel, controlledPlantId, controlledRevision,
            controlledCaseId);

    List<String> areaNames = new ArrayList<String>(processModel.getProcessSystemNames());
    Collections.sort(areaNames);
    if (areaNames.isEmpty()) {
      throw new IllegalArgumentException("DEXPI ProcessModel package requires at least one process area");
    }

    Map<String, byte[]> entries = new TreeMap<String, byte[]>();
    List<AreaExchange> areas = new ArrayList<AreaExchange>();
    List<Diagnostic> diagnostics = adapterDiagnostics(canonical);
    for (String areaName : areaNames) {
      ProcessSystem area = processModel.get(areaName);
      if (area == null) {
        diagnostics
            .add(error("DEXPI_PROCESS_PACKAGE_AREA_MISSING", "ProcessModel area could not be resolved", areaName));
        continue;
      }
      String entryName = areaEntryName(controlledPlantId, areaName);
      Path temporaryFile = Files.createTempFile("neqsim-dexpi-process-area-", ".xml");
      try {
        Dexpi20ProcessTopologyAssessment.Report assessment = controlledCaseId == null
            ? Dexpi20ProcessModelWriter.writeAndAssessTopology(area, temporaryFile.toFile(),
                controlledPlantId + "/" + areaName, controlledRevision)
            : Dexpi20ProcessModelWriter.writeAndAssessTopology(area, temporaryFile.toFile(),
                controlledPlantId + "/" + areaName, controlledRevision, controlledCaseId);
        byte[] xml = Files.readAllBytes(temporaryFile);
        entries.put(entryName, xml);
        String stableAreaId = areaId(canonical.getGraph(), areaName);
        areas.add(new AreaExchange(areaName, stableAreaId, area.getName(), entryName, sha256(xml), assessment));
        if (stableAreaId.isEmpty()) {
          diagnostics.add(error("DEXPI_PROCESS_PACKAGE_AREA_ID_MISSING",
              "Canonical ProcessModel snapshot did not expose a stable area identity", areaName));
        }
        if (!assessment.isSchemaProfileAndSupportedTopologyValid()) {
          diagnostics.add(error("DEXPI_PROCESS_PACKAGE_AREA_ASSESSMENT_FAILED",
              "Area DEXPI Process exchange failed schema/profile or supported-topology assessment", areaName));
        }
      } finally {
        Files.deleteIfExists(temporaryFile);
      }
    }

    List<ConnectionRecord> connections = connectionRecords(canonical.getGraph(), diagnostics);
    diagnostics.add(new Diagnostic(Severity.INFO, "DEXPI_PROCESS_PACKAGE_NOT_NATIVE_WHOLE_PLANT_PROFILE",
        "The ZIP and manifest are a NeqSim container around independently assessed native DEXPI area files",
        controlledPlantId));
    diagnostics.add(new Diagnostic(Severity.WARNING, "DEXPI_PROCESS_PACKAGE_DOCUMENT_SEMANTICS_NOT_INCLUDED",
        "Controlled drawing/document/sheet semantics remain a separate projection", controlledPlantId));
    diagnostics.add(new Diagnostic(Severity.WARNING, "DEXPI_PROCESS_PACKAGE_GRAPHICS_NOT_INCLUDED",
        "Graphical projection remains a separate DEXPI Core or native SVG/PDF artifact", controlledPlantId));
    sortDiagnostics(diagnostics);

    Report manifestReport = new Report(controlledPlantId, controlledRevision, controlledCaseId,
        canonical.getFingerprint(), areas, connections, diagnostics, "", "");
    byte[] manifest = manifestReport.toManifestJson().getBytes(StandardCharsets.UTF_8);
    entries.put(MANIFEST_ENTRY, manifest);
    writeArchive(packageFile.toPath(), entries);
    return new Report(controlledPlantId, controlledRevision, controlledCaseId, canonical.getFingerprint(), areas,
        connections, diagnostics, sha256(Files.readAllBytes(packageFile.toPath())), sha256(manifest));
  }

  private static List<Diagnostic> adapterDiagnostics(ProcessDiagramGraphAdapter.Result canonical) {
    List<Diagnostic> result = new ArrayList<Diagnostic>();
    for (ProcessDiagramGraphAdapter.Diagnostic diagnostic : canonical.getDiagnostics()) {
      Severity severity = diagnostic.getSeverity() == ProcessDiagramGraphAdapter.Severity.ERROR ? Severity.ERROR
          : diagnostic.getSeverity() == ProcessDiagramGraphAdapter.Severity.WARNING ? Severity.WARNING : Severity.INFO;
      String subject = diagnostic.getArea();
      if (!diagnostic.getSubject().isEmpty()) {
        subject = subject.isEmpty() ? diagnostic.getSubject() : subject + "/" + diagnostic.getSubject();
      }
      result.add(new Diagnostic(severity, diagnostic.getCode(), diagnostic.getMessage(), subject));
    }
    return result;
  }

  private static List<ConnectionRecord> connectionRecords(EngineeringGraph graph, List<Diagnostic> diagnostics) {
    List<ConnectionRecord> result = new ArrayList<ConnectionRecord>();
    for (EngineeringNode node : graph.getNodes().values()) {
      if (node.getKind() != EngineeringNode.Kind.PIPE_SEGMENT
          && node.getKind() != EngineeringNode.Kind.ENERGY_CONNECTION
          && node.getKind() != EngineeringNode.Kind.SIGNAL_CONNECTION) {
        continue;
      }
      ConnectionRecord record = new ConnectionRecord(node);
      result.add(record);
      if (record.isCrossArea() && "MATERIAL".equals(record.getConnectionType())) {
        diagnostics.add(new Diagnostic(Severity.WARNING, "DEXPI_PROCESS_PACKAGE_CROSS_AREA_CONNECTION_MANIFEST_ONLY",
            "Cross-area material identity is preserved in the package manifest, not as one native DEXPI connection",
            record.getConnectionId()));
      } else if ("ENERGY".equals(record.getConnectionType())) {
        diagnostics.add(new Diagnostic(Severity.WARNING, "DEXPI_PROCESS_PACKAGE_ENERGY_CONNECTION_MANIFEST_ONLY",
            "Energy connection is preserved in the package manifest but is not mapped to native DEXPI Process",
            record.getConnectionId()));
      } else if ("SIGNAL".equals(record.getConnectionType())) {
        diagnostics.add(new Diagnostic(Severity.WARNING, "DEXPI_PROCESS_PACKAGE_SIGNAL_CONNECTION_MANIFEST_ONLY",
            "Information connection is preserved in the package manifest but is not mapped to native DEXPI Process",
            record.getConnectionId()));
      }
    }
    Collections.sort(result, new Comparator<ConnectionRecord>() {
      @Override
      public int compare(ConnectionRecord first, ConnectionRecord second) {
        return first.getConnectionId().compareTo(second.getConnectionId());
      }
    });
    return result;
  }

  private static String areaId(EngineeringGraph graph, String areaName) {
    for (EngineeringNode node : graph.getNodes().values()) {
      if (node.getKind() == EngineeringNode.Kind.AREA && areaName.equals(property(node, "areaName"))) {
        return node.getId();
      }
    }
    return "";
  }

  private static String areaEntryName(String plantId, String areaName) {
    return "areas/" + safeSegment(areaName) + "-" + shortHash(plantId + "/" + areaName) + ".process.dexpi.xml";
  }

  private static String safeSegment(String value) {
    String result = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    return result.isEmpty() ? "area" : result;
  }

  private static String shortHash(String value) {
    return sha256(value.getBytes(StandardCharsets.UTF_8)).substring(0, 12);
  }

  private static void writeArchive(Path file, Map<String, byte[]> entries) throws IOException {
    Path absolute = file.toAbsolutePath();
    Path parent = absolute.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    FileOutputStream output = new FileOutputStream(file.toFile());
    try {
      ZipOutputStream archive = new ZipOutputStream(output, StandardCharsets.UTF_8);
      try {
        for (Map.Entry<String, byte[]> item : entries.entrySet()) {
          CRC32 crc = new CRC32();
          crc.update(item.getValue());
          ZipEntry entry = new ZipEntry(item.getKey());
          entry.setMethod(ZipEntry.STORED);
          entry.setSize(item.getValue().length);
          entry.setCompressedSize(item.getValue().length);
          entry.setCrc(crc.getValue());
          entry.setTime(0L);
          archive.putNextEntry(entry);
          archive.write(item.getValue());
          archive.closeEntry();
        }
      } finally {
        archive.close();
      }
    } finally {
      output.close();
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

  private static String property(EngineeringNode node, String name) {
    Object value = node.getProperties().get(name);
    return value == null ? "" : String.valueOf(value);
  }

  private static boolean booleanProperty(EngineeringNode node, String name) {
    Object value = node.getProperties().get(name);
    return value instanceof Boolean ? ((Boolean) value).booleanValue() : Boolean.parseBoolean(String.valueOf(value));
  }

  private static String sha256(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder result = new StringBuilder();
      for (byte value : digest) {
        result.append(String.format(Locale.ROOT, "%02x", Integer.valueOf(value & 255)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by every supported Java runtime", exception);
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  private static Diagnostic error(String code, String message, String subject) {
    return new Diagnostic(Severity.ERROR, code, message, subject);
  }
}
