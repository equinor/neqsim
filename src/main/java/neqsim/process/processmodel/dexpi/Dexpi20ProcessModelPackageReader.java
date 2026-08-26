package neqsim.process.processmodel.dexpi;

import com.google.gson.GsonBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads independently assessed NeqSim multi-area DEXPI Process packages without extracting archive paths.
 *
 * <p>
 * The reader first applies {@link Dexpi20ProcessModelPackageAssessment}, captures the exact assessed archive bytes, and
 * refuses to expose content unless both reads have the same SHA-256 fingerprint. The returned snapshot contains
 * defensive copies of the native per-area DEXPI 2.0 Process XML and the independently validated plant-wide connection
 * evidence. It does not reconstruct or execute a {@code ProcessModel}, invent native whole-plant DEXPI relationships,
 * or weaken the mandatory engineering-review boundary.
 * </p>
 */
public final class Dexpi20ProcessModelPackageReader {
  private static final long MAX_PACKAGE_FILE_BYTES = 256L * 1024L * 1024L;
  private static final long MAX_AREA_XML_BYTES = 64L * 1024L * 1024L;

  private Dexpi20ProcessModelPackageReader() {
  }

  /** Checked failure carrying deterministic assessment evidence for an invalid package. */
  public static final class InvalidPackageException extends IOException {
    private static final long serialVersionUID = 1000L;
    private final Dexpi20ProcessModelPackageAssessment.Report assessmentReport;

    InvalidPackageException(Dexpi20ProcessModelPackageAssessment.Report assessmentReport) {
      super("DEXPI ProcessModel package failed independent assessment");
      this.assessmentReport = assessmentReport;
    }

    /** @return immutable fail-closed assessment report */
    public Dexpi20ProcessModelPackageAssessment.Report getAssessmentReport() {
      return assessmentReport;
    }
  }

  /** Immutable native DEXPI Process document for one assessed area. */
  public static final class AreaDocument implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String areaName;
    private final String areaId;
    private final String entryName;
    private final String fileSha256;
    private final byte[] xmlBytes;

    AreaDocument(Dexpi20ProcessModelPackageAssessment.AreaEvidence evidence, byte[] xmlBytes) {
      this.areaName = evidence.getAreaName();
      this.areaId = evidence.getAreaId();
      this.entryName = evidence.getEntryName();
      this.fileSha256 = evidence.getActualFileSha256();
      this.xmlBytes = xmlBytes.clone();
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

    public String getFileSha256() {
      return fileSha256;
    }

    /** @return a defensive copy of the exact assessed XML bytes */
    public byte[] getXmlBytes() {
      return xmlBytes.clone();
    }

    /** @return the exact assessed XML decoded as UTF-8 */
    public String getXmlUtf8() {
      return new String(xmlBytes, StandardCharsets.UTF_8);
    }

    public int getByteLength() {
      return xmlBytes.length;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("areaName", areaName);
      result.put("areaId", areaId);
      result.put("entryName", entryName);
      result.put("fileSha256", fileSha256);
      result.put("byteLength", Integer.valueOf(xmlBytes.length));
      return result;
    }
  }

  /** Immutable restart snapshot of one valid assessed package. */
  public static final class Snapshot implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Dexpi20ProcessModelPackageAssessment.Report assessmentReport;
    private final List<AreaDocument> areaDocuments;

    Snapshot(Dexpi20ProcessModelPackageAssessment.Report assessmentReport, List<AreaDocument> areaDocuments) {
      this.assessmentReport = assessmentReport;
      this.areaDocuments = Collections.unmodifiableList(new ArrayList<AreaDocument>(areaDocuments));
    }

    public Dexpi20ProcessModelPackageAssessment.Report getAssessmentReport() {
      return assessmentReport;
    }

    public String getPackageFileSha256() {
      return assessmentReport.getPackageFileSha256();
    }

    public String getManifestSha256() {
      return assessmentReport.getManifestSha256();
    }

    public String getPlantId() {
      return assessmentReport.getPlantId();
    }

    public String getRevision() {
      return assessmentReport.getRevision();
    }

    public String getOperatingCaseId() {
      return assessmentReport.getOperatingCaseId();
    }

    public String getCanonicalFingerprint() {
      return assessmentReport.getCanonicalFingerprint();
    }

    public List<AreaDocument> getAreaDocuments() {
      return areaDocuments;
    }

    public List<Dexpi20ProcessModelPackageAssessment.ConnectionEvidence> getConnectionEvidence() {
      return assessmentReport.getConnectionEvidence();
    }

    /** @return always {@code REVIEW_REQUIRED} */
    public String getApprovalStatus() {
      return "REVIEW_REQUIRED";
    }

    /** @return always false */
    public boolean isFitnessForConstruction() {
      return false;
    }

    /** @return always false */
    public boolean isNativeWholePlantDexpiExchange() {
      return false;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "neqsim_dexpi_2_0_process_model_package_snapshot.v1");
      result.put("packageFileSha256", getPackageFileSha256());
      result.put("manifestSha256", getManifestSha256());
      result.put("plantId", getPlantId());
      result.put("revision", getRevision());
      if (getOperatingCaseId() != null) {
        result.put("operatingCaseId", getOperatingCaseId());
      }
      result.put("canonicalFingerprint", getCanonicalFingerprint());
      result.put("approvalStatus", getApprovalStatus());
      result.put("fitnessForConstruction", Boolean.FALSE);
      result.put("nativeWholePlantDexpiExchange", Boolean.FALSE);
      List<Map<String, Object>> areas = new ArrayList<Map<String, Object>>();
      for (AreaDocument area : areaDocuments) {
        areas.add(area.toMap());
      }
      result.put("areaDocuments", areas);
      List<Map<String, Object>> connections = new ArrayList<Map<String, Object>>();
      for (Dexpi20ProcessModelPackageAssessment.ConnectionEvidence connection : getConnectionEvidence()) {
        connections.add(connection.toMap());
      }
      result.put("connectionEvidence", connections);
      result.put("contentScope", "Exact assessed native per-area DEXPI Process XML and validated manifest connections");
      result.put("executionStatus", "NOT_RECONSTRUCTED_OR_EXECUTED");
      return result;
    }

    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }
  }

  /**
   * Reads one package into an immutable assessed snapshot.
   *
   * @param packageFile package written by {@link Dexpi20ProcessModelPackageWriter}
   * @return immutable exact-content snapshot
   * @throws InvalidPackageException when independent assessment finds an invalid package
   * @throws IOException when the file changes during intake or cannot be read within the bounded size
   */
  public static Snapshot read(File packageFile) throws IOException {
    if (packageFile == null) {
      throw new IllegalArgumentException("packageFile must not be null");
    }
    Path path = packageFile.toPath();
    if (!Files.isRegularFile(path)) {
      throw new IOException("DEXPI ProcessModel package is not a regular file: " + path);
    }
    long fileSize = Files.size(path);
    if (fileSize > MAX_PACKAGE_FILE_BYTES) {
      throw new IOException("DEXPI ProcessModel package exceeds the bounded file-size limit");
    }

    Dexpi20ProcessModelPackageAssessment.Report assessment =
        Dexpi20ProcessModelPackageAssessment.assess(packageFile);
    if (!assessment.isValid()) {
      throw new InvalidPackageException(assessment);
    }

    byte[] archiveBytes = Files.readAllBytes(path);
    if (!assessment.getPackageFileSha256().equals(sha256(archiveBytes))) {
      throw new IOException("DEXPI ProcessModel package changed during assessed intake");
    }
    return new Snapshot(assessment, readAreaDocuments(archiveBytes, assessment));
  }

  private static List<AreaDocument> readAreaDocuments(byte[] archiveBytes,
      Dexpi20ProcessModelPackageAssessment.Report assessment) throws IOException {
    Map<String, Dexpi20ProcessModelPackageAssessment.AreaEvidence> expected =
        new LinkedHashMap<String, Dexpi20ProcessModelPackageAssessment.AreaEvidence>();
    for (Dexpi20ProcessModelPackageAssessment.AreaEvidence area : assessment.getAreaEvidence()) {
      expected.put(area.getEntryName(), area);
    }
    Map<String, byte[]> found = new LinkedHashMap<String, byte[]>();
    ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archiveBytes), StandardCharsets.UTF_8);
    try {
      ZipEntry entry;
      byte[] buffer = new byte[8192];
      while ((entry = input.getNextEntry()) != null) {
        Dexpi20ProcessModelPackageAssessment.AreaEvidence area = expected.get(entry.getName());
        if (area == null) {
          while (input.read(buffer) != -1) {
            // Consume assessed non-area entries without exposing them.
          }
          continue;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long countForEntry = 0L;
        int count;
        while ((count = input.read(buffer)) != -1) {
          countForEntry += count;
          if (countForEntry > MAX_AREA_XML_BYTES) {
            throw new IOException("Assessed area XML exceeds the bounded reader limit: " + entry.getName());
          }
          output.write(buffer, 0, count);
        }
        found.put(entry.getName(), output.toByteArray());
      }
    } finally {
      input.close();
    }

    List<AreaDocument> result = new ArrayList<AreaDocument>();
    for (Dexpi20ProcessModelPackageAssessment.AreaEvidence area : assessment.getAreaEvidence()) {
      byte[] xml = found.get(area.getEntryName());
      if (xml == null || !area.getActualFileSha256().equals(sha256(xml))) {
        throw new IOException("Assessed area XML changed or is unavailable: " + area.getEntryName());
      }
      result.add(new AreaDocument(area, xml));
    }
    return result;
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
      throw new IllegalStateException("SHA-256 is required by every supported Java runtime", exception);
    }
  }
}
