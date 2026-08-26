package neqsim.process.processmodel.dexpi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Deterministic revision-impact evidence between two valid assessed multi-area DEXPI Process packages.
 *
 * <p>
 * The comparison operates only on immutable snapshots returned by {@link Dexpi20ProcessModelPackageReader}. It
 * distinguishes exact package-byte changes, per-area native DEXPI Process document changes, stable area-identity
 * changes, and plant-wide material, energy, or signal connection changes. It does not reconstruct or execute a process
 * model, infer native whole-plant DEXPI relationships, approve a drawing, or determine fitness for construction.
 * </p>
 */
public final class Dexpi20ProcessModelPackageRevisionImpact implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = new Gson();
  public static final String SCHEMA_VERSION = "neqsim_dexpi_2_0_process_model_package_revision_impact.v1";

  /** Overall comparison status. */
  public enum Status {
    /** Package content and assessed evidence are unchanged. */
    UNCHANGED,
    /** At least one area document, identity, or connection changed. */
    CHANGED
  }

  /** Per-identity comparison outcome. */
  public enum ChangeType {
    /** Identity exists only in the revised package. */
    ADDED,
    /** Identity exists only in the baseline package. */
    REMOVED,
    /** Identity exists in both packages but assessed content changed. */
    MODIFIED,
    /** Identity and assessed content are unchanged. */
    UNCHANGED
  }

  /** Immutable state of one assessed area document. */
  public static final class AreaState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String areaId;
    private final String areaName;
    private final String entryName;
    private final String fileSha256;
    private final int byteLength;

    AreaState(Dexpi20ProcessModelPackageReader.AreaDocument area) {
      areaId = area.getAreaId();
      areaName = area.getAreaName();
      entryName = area.getEntryName();
      fileSha256 = area.getFileSha256();
      byteLength = area.getByteLength();
    }

    public String getAreaId() {
      return areaId;
    }

    public String getAreaName() {
      return areaName;
    }

    public String getEntryName() {
      return entryName;
    }

    public String getFileSha256() {
      return fileSha256;
    }

    public int getByteLength() {
      return byteLength;
    }

    /** @return deterministic machine-readable area state */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("areaId", areaId);
      result.put("areaName", areaName);
      result.put("entryName", entryName);
      result.put("fileSha256", fileSha256);
      result.put("byteLength", Integer.valueOf(byteLength));
      return result;
    }

    boolean sameAs(AreaState other) {
      return other != null && sameText(areaId, other.areaId) && sameText(areaName, other.areaName)
          && sameText(entryName, other.entryName) && sameText(fileSha256, other.fileSha256)
          && byteLength == other.byteLength;
    }
  }

  /** Immutable state of one assessed plant-wide connection. */
  public static final class ConnectionState implements Serializable {
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

    ConnectionState(Dexpi20ProcessModelPackageAssessment.ConnectionEvidence connection) {
      connectionId = connection.getConnectionId();
      connectionType = connection.getConnectionType();
      sourceArea = connection.getSourceArea();
      targetArea = connection.getTargetArea();
      sourceEquipment = connection.getSourceEquipment();
      targetEquipment = connection.getTargetEquipment();
      sourcePort = connection.getSourcePort();
      targetPort = connection.getTargetPort();
      crossArea = connection.isCrossArea();
      recycle = connection.isRecycle();
      exchangeStatus = connection.getExchangeStatus();
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

    /** @return deterministic machine-readable connection state */
    public Map<String, Object> toMap() {
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

    boolean sameAs(ConnectionState other) {
      return other != null && sameText(connectionId, other.connectionId)
          && sameText(connectionType, other.connectionType) && sameText(sourceArea, other.sourceArea)
          && sameText(targetArea, other.targetArea) && sameText(sourceEquipment, other.sourceEquipment)
          && sameText(targetEquipment, other.targetEquipment) && sameText(sourcePort, other.sourcePort)
          && sameText(targetPort, other.targetPort) && crossArea == other.crossArea && recycle == other.recycle
          && sameText(exchangeStatus, other.exchangeStatus);
    }
  }

  /** Immutable revision delta for one stable area identity. */
  public static final class AreaChange implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String areaId;
    private final ChangeType changeType;
    private final AreaState baseline;
    private final AreaState revised;

    AreaChange(String areaId, ChangeType changeType, AreaState baseline, AreaState revised) {
      this.areaId = areaId;
      this.changeType = changeType;
      this.baseline = baseline;
      this.revised = revised;
    }

    public String getAreaId() {
      return areaId;
    }

    public ChangeType getChangeType() {
      return changeType;
    }

    public AreaState getBaseline() {
      return baseline;
    }

    public AreaState getRevised() {
      return revised;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("areaId", areaId);
      result.put("changeType", changeType.name());
      result.put("baseline", baseline == null ? null : baseline.toMap());
      result.put("revised", revised == null ? null : revised.toMap());
      return result;
    }
  }

  /** Immutable revision delta for one stable plant-wide connection identity. */
  public static final class ConnectionChange implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String connectionId;
    private final ChangeType changeType;
    private final ConnectionState baseline;
    private final ConnectionState revised;

    ConnectionChange(String connectionId, ChangeType changeType, ConnectionState baseline, ConnectionState revised) {
      this.connectionId = connectionId;
      this.changeType = changeType;
      this.baseline = baseline;
      this.revised = revised;
    }

    public String getConnectionId() {
      return connectionId;
    }

    public ChangeType getChangeType() {
      return changeType;
    }

    public ConnectionState getBaseline() {
      return baseline;
    }

    public ConnectionState getRevised() {
      return revised;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("connectionId", connectionId);
      result.put("changeType", changeType.name());
      result.put("baseline", baseline == null ? null : baseline.toMap());
      result.put("revised", revised == null ? null : revised.toMap());
      return result;
    }
  }

  private final String plantId;
  private final String baselineRevision;
  private final String revisedRevision;
  private final String baselineOperatingCaseId;
  private final String revisedOperatingCaseId;
  private final String baselinePackageFileSha256;
  private final String revisedPackageFileSha256;
  private final String baselineManifestSha256;
  private final String revisedManifestSha256;
  private final String baselineCanonicalFingerprint;
  private final String revisedCanonicalFingerprint;
  private final List<AreaChange> areaChanges;
  private final List<ConnectionChange> connectionChanges;
  private final Status status;

  private Dexpi20ProcessModelPackageRevisionImpact(Dexpi20ProcessModelPackageReader.Snapshot baseline,
      Dexpi20ProcessModelPackageReader.Snapshot revised, List<AreaChange> areaChanges,
      List<ConnectionChange> connectionChanges) {
    plantId = baseline.getPlantId();
    baselineRevision = baseline.getRevision();
    revisedRevision = revised.getRevision();
    baselineOperatingCaseId = baseline.getOperatingCaseId();
    revisedOperatingCaseId = revised.getOperatingCaseId();
    baselinePackageFileSha256 = baseline.getPackageFileSha256();
    revisedPackageFileSha256 = revised.getPackageFileSha256();
    baselineManifestSha256 = baseline.getManifestSha256();
    revisedManifestSha256 = revised.getManifestSha256();
    baselineCanonicalFingerprint = baseline.getCanonicalFingerprint();
    revisedCanonicalFingerprint = revised.getCanonicalFingerprint();
    this.areaChanges = immutableAreaChanges(areaChanges);
    this.connectionChanges = immutableConnectionChanges(connectionChanges);
    status = countChangedAreas(areaChanges) == 0 && countChangedConnections(connectionChanges) == 0
        && sameText(baselinePackageFileSha256, revisedPackageFileSha256) ? Status.UNCHANGED : Status.CHANGED;
  }

  /**
   * Compares two valid assessed snapshots for the same controlled plant identity.
   *
   * @param baseline immutable baseline package snapshot
   * @param revised immutable revised package snapshot
   * @return deterministic package revision impact
   */
  public static Dexpi20ProcessModelPackageRevisionImpact compare(Dexpi20ProcessModelPackageReader.Snapshot baseline,
      Dexpi20ProcessModelPackageReader.Snapshot revised) {
    if (baseline == null || revised == null) {
      throw new IllegalArgumentException("baseline and revised must not be null");
    }
    if (!sameText(baseline.getPlantId(), revised.getPlantId())) {
      throw new IllegalArgumentException("Package revision impact requires the same plant identity");
    }

    Map<String, AreaState> baselineAreas = areasById(baseline);
    Map<String, AreaState> revisedAreas = areasById(revised);
    Set<String> areaIds = new TreeSet<String>();
    areaIds.addAll(baselineAreas.keySet());
    areaIds.addAll(revisedAreas.keySet());
    List<AreaChange> areas = new ArrayList<AreaChange>();
    for (String areaId : areaIds) {
      AreaState before = baselineAreas.get(areaId);
      AreaState after = revisedAreas.get(areaId);
      areas.add(new AreaChange(areaId, changeType(before, after), before, after));
    }

    Map<String, ConnectionState> baselineConnections = connectionsById(baseline);
    Map<String, ConnectionState> revisedConnections = connectionsById(revised);
    Set<String> connectionIds = new TreeSet<String>();
    connectionIds.addAll(baselineConnections.keySet());
    connectionIds.addAll(revisedConnections.keySet());
    List<ConnectionChange> connections = new ArrayList<ConnectionChange>();
    for (String connectionId : connectionIds) {
      ConnectionState before = baselineConnections.get(connectionId);
      ConnectionState after = revisedConnections.get(connectionId);
      connections.add(new ConnectionChange(connectionId, changeType(before, after), before, after));
    }
    return new Dexpi20ProcessModelPackageRevisionImpact(baseline, revised, areas, connections);
  }

  public String getPlantId() {
    return plantId;
  }

  public String getBaselineRevision() {
    return baselineRevision;
  }

  public String getRevisedRevision() {
    return revisedRevision;
  }

  public String getBaselineOperatingCaseId() {
    return baselineOperatingCaseId;
  }

  public String getRevisedOperatingCaseId() {
    return revisedOperatingCaseId;
  }

  public String getBaselinePackageFileSha256() {
    return baselinePackageFileSha256;
  }

  public String getRevisedPackageFileSha256() {
    return revisedPackageFileSha256;
  }

  public String getBaselineManifestSha256() {
    return baselineManifestSha256;
  }

  public String getRevisedManifestSha256() {
    return revisedManifestSha256;
  }

  public String getBaselineCanonicalFingerprint() {
    return baselineCanonicalFingerprint;
  }

  public String getRevisedCanonicalFingerprint() {
    return revisedCanonicalFingerprint;
  }

  public Status getStatus() {
    return status;
  }

  public List<AreaChange> getAreaChanges() {
    return immutableAreaChanges(areaChanges);
  }

  public List<ConnectionChange> getConnectionChanges() {
    return immutableConnectionChanges(connectionChanges);
  }

  public int getChangedAreaCount() {
    return countChangedAreas(areaChanges);
  }

  public int getChangedConnectionCount() {
    return countChangedConnections(connectionChanges);
  }

  public boolean isExactPackageMatch() {
    return sameText(baselinePackageFileSha256, revisedPackageFileSha256);
  }

  /**
   * Reports whether the stable area identity set is unchanged.
   *
   * <p>
   * Area XML may still differ when this method returns true.
   * </p>
   *
   * @return true when no area identity was added or removed
   */
  public boolean isAreaIdentitySetEquivalent() {
    for (AreaChange change : areaChanges) {
      if (change.getChangeType() == ChangeType.ADDED || change.getChangeType() == ChangeType.REMOVED) {
        return false;
      }
    }
    return true;
  }

  /**
   * Reports whether all assessed material, energy, and signal connection evidence is unchanged.
   *
   * @return true when no connection was added, removed, or modified
   */
  public boolean isConnectionTopologyEquivalent() {
    return countChangedConnections(connectionChanges) == 0;
  }

  /** @return always {@code REVIEW_REQUIRED} */
  public String getApprovalStatus() {
    return "REVIEW_REQUIRED";
  }

  /** @return always true; software evidence cannot approve an engineering revision */
  public boolean isEngineeringReviewRequired() {
    return true;
  }

  /** @return always false */
  public boolean isFitnessForConstruction() {
    return false;
  }

  /** @return always false */
  public boolean isNativeWholePlantDexpiExchange() {
    return false;
  }

  /** @return deterministic machine-readable revision-impact representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", SCHEMA_VERSION);
    result.put("plantId", plantId);
    result.put("baselineRevision", baselineRevision);
    result.put("revisedRevision", revisedRevision);
    if (baselineOperatingCaseId != null) {
      result.put("baselineOperatingCaseId", baselineOperatingCaseId);
    }
    if (revisedOperatingCaseId != null) {
      result.put("revisedOperatingCaseId", revisedOperatingCaseId);
    }
    result.put("baselinePackageFileSha256", baselinePackageFileSha256);
    result.put("revisedPackageFileSha256", revisedPackageFileSha256);
    result.put("baselineManifestSha256", baselineManifestSha256);
    result.put("revisedManifestSha256", revisedManifestSha256);
    result.put("baselineCanonicalFingerprint", baselineCanonicalFingerprint);
    result.put("revisedCanonicalFingerprint", revisedCanonicalFingerprint);
    result.put("status", status.name());
    result.put("exactPackageMatch", Boolean.valueOf(isExactPackageMatch()));
    result.put("areaIdentitySetEquivalent", Boolean.valueOf(isAreaIdentitySetEquivalent()));
    result.put("connectionTopologyEquivalent", Boolean.valueOf(isConnectionTopologyEquivalent()));
    result.put("changedAreaCount", Integer.valueOf(getChangedAreaCount()));
    result.put("changedConnectionCount", Integer.valueOf(getChangedConnectionCount()));
    List<Map<String, Object>> areas = new ArrayList<Map<String, Object>>();
    for (AreaChange change : areaChanges) {
      areas.add(change.toMap());
    }
    result.put("areaChanges", areas);
    List<Map<String, Object>> connections = new ArrayList<Map<String, Object>>();
    for (ConnectionChange change : connectionChanges) {
      connections.add(change.toMap());
    }
    result.put("connectionChanges", connections);
    result.put("approvalStatus", getApprovalStatus());
    result.put("engineeringReviewRequired", Boolean.TRUE);
    result.put("fitnessForConstruction", Boolean.FALSE);
    result.put("nativeWholePlantDexpiExchange", Boolean.FALSE);
    result.put("scope", "Exact assessed package, per-area native DEXPI Process document, stable area identity, "
        + "and manifest connection revision evidence");
    result.put("fingerprint", fingerprint(result));
    return result;
  }

  /** @return pretty-printed deterministic JSON */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  private static Map<String, AreaState> areasById(Dexpi20ProcessModelPackageReader.Snapshot snapshot) {
    Map<String, AreaState> result = new TreeMap<String, AreaState>();
    for (Dexpi20ProcessModelPackageReader.AreaDocument area : snapshot.getAreaDocuments()) {
      result.put(area.getAreaId(), new AreaState(area));
    }
    return result;
  }

  private static Map<String, ConnectionState> connectionsById(Dexpi20ProcessModelPackageReader.Snapshot snapshot) {
    Map<String, ConnectionState> result = new TreeMap<String, ConnectionState>();
    for (Dexpi20ProcessModelPackageAssessment.ConnectionEvidence connection : snapshot.getConnectionEvidence()) {
      result.put(connection.getConnectionId(), new ConnectionState(connection));
    }
    return result;
  }

  private static ChangeType changeType(AreaState baseline, AreaState revised) {
    if (baseline == null) {
      return ChangeType.ADDED;
    }
    if (revised == null) {
      return ChangeType.REMOVED;
    }
    return baseline.sameAs(revised) ? ChangeType.UNCHANGED : ChangeType.MODIFIED;
  }

  private static ChangeType changeType(ConnectionState baseline, ConnectionState revised) {
    if (baseline == null) {
      return ChangeType.ADDED;
    }
    if (revised == null) {
      return ChangeType.REMOVED;
    }
    return baseline.sameAs(revised) ? ChangeType.UNCHANGED : ChangeType.MODIFIED;
  }

  private static int countChangedAreas(List<AreaChange> changes) {
    int count = 0;
    for (AreaChange change : changes) {
      if (change.getChangeType() != ChangeType.UNCHANGED) {
        count++;
      }
    }
    return count;
  }

  private static int countChangedConnections(List<ConnectionChange> changes) {
    int count = 0;
    for (ConnectionChange change : changes) {
      if (change.getChangeType() != ChangeType.UNCHANGED) {
        count++;
      }
    }
    return count;
  }

  private static List<AreaChange> immutableAreaChanges(List<AreaChange> changes) {
    return Collections.unmodifiableList(new ArrayList<AreaChange>(changes));
  }

  private static List<ConnectionChange> immutableConnectionChanges(List<ConnectionChange> changes) {
    return Collections.unmodifiableList(new ArrayList<ConnectionChange>(changes));
  }

  private static boolean sameText(String first, String second) {
    return first == null ? second == null : first.equals(second);
  }

  private static String fingerprint(Map<String, Object> map) {
    Map<String, Object> content = new LinkedHashMap<String, Object>(map);
    content.remove("fingerprint");
    byte[] bytes = GSON.toJson(content).getBytes(StandardCharsets.UTF_8);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(bytes);
      StringBuilder result = new StringBuilder();
      for (byte value : hash) {
        result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}

