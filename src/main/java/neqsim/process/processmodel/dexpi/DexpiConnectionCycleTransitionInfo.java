package neqsim.process.processmodel.dexpi;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable source evidence for one connection occurrence crossing directed-cycle boundaries.
 *
 * <p>
 * A transition is retained once in source order even when it leaves one cyclic strongly connected group and enters
 * another. This record does not establish hydraulic continuity, a physical recycle, process intent, or live
 * {@code ProcessSystem} topology.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiConnectionCycleTransitionInfo implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Classification of one explicit connection occurrence relative to cyclic groups. */
  public enum Kind {
    /** The connection source is outside every cyclic group and its target is inside one. */
    ENTERING,
    /** The connection source is inside a cyclic group and its target is outside every cyclic group. */
    LEAVING,
    /** The connection leaves one cyclic group and enters a distinct cyclic group. */
    BETWEEN_CYCLES
  }

  private final String fromCycleId;
  private final String toCycleId;
  private final DexpiConnectionCycleInfo fromCycle;
  private final DexpiConnectionCycleInfo toCycle;
  private final DexpiConnectionCycleBoundaryInfo fromCycleBoundary;
  private final DexpiConnectionCycleBoundaryInfo toCycleBoundary;
  private final Kind kind;
  private final DexpiConnectionInfo connection;
  private final DexpiConnectionEndpointInfo fromEndpoint;
  private final DexpiConnectionEndpointInfo toEndpoint;

  /**
   * Creates complete immutable evidence for one source connection crossing a cycle boundary.
   *
   * @param connection complete source connection occurrence
   * @param fromEndpoint complete source-endpoint evidence
   * @param toEndpoint complete target-endpoint evidence
   * @param fromCycleId source directed-cycle identity, or empty when outside every cycle
   * @param toCycleId target directed-cycle identity, or empty when outside every cycle
   * @throws NullPointerException if connection or endpoint evidence is null
   * @throws IllegalArgumentException if neither endpoint belongs to a cycle, or both identify the same cycle
   */
  public DexpiConnectionCycleTransitionInfo(DexpiConnectionInfo connection, DexpiConnectionEndpointInfo fromEndpoint,
      DexpiConnectionEndpointInfo toEndpoint, String fromCycleId, String toCycleId) {
    this(connection, fromEndpoint, toEndpoint, fromCycleId, toCycleId, null, null);
  }

  /**
   * Creates complete immutable evidence with the source and target cycle records available to the reader.
   *
   * @param connection complete source connection occurrence
   * @param fromEndpoint complete source-endpoint evidence
   * @param toEndpoint complete target-endpoint evidence
   * @param fromCycleId source directed-cycle identity, or empty when outside every cycle
   * @param toCycleId target directed-cycle identity, or empty when outside every cycle
   * @param fromCycle complete source directed-cycle evidence, or {@code null} when outside or unavailable
   * @param toCycle complete target directed-cycle evidence, or {@code null} when outside or unavailable
   * @throws NullPointerException if connection or endpoint evidence is null
   * @throws IllegalArgumentException if cycle identities do not describe a boundary crossing or disagree with the cycle
   * records
   */
  public DexpiConnectionCycleTransitionInfo(DexpiConnectionInfo connection, DexpiConnectionEndpointInfo fromEndpoint,
      DexpiConnectionEndpointInfo toEndpoint, String fromCycleId, String toCycleId, DexpiConnectionCycleInfo fromCycle,
      DexpiConnectionCycleInfo toCycle) {
    this(connection, fromEndpoint, toEndpoint, fromCycleId, toCycleId, fromCycle, toCycle, null, null);
  }

  /**
   * Creates complete immutable evidence with cycle and boundary records available to the reader.
   *
   * @param connection complete source connection occurrence
   * @param fromEndpoint complete source-endpoint evidence
   * @param toEndpoint complete target-endpoint evidence
   * @param fromCycleId source directed-cycle identity, or empty when outside every cycle
   * @param toCycleId target directed-cycle identity, or empty when outside every cycle
   * @param fromCycle complete source directed-cycle evidence, or {@code null} when outside or unavailable
   * @param toCycle complete target directed-cycle evidence, or {@code null} when outside or unavailable
   * @param fromCycleBoundary source cycle's outgoing boundary evidence, or {@code null} when outside or unavailable
   * @param toCycleBoundary target cycle's incoming boundary evidence, or {@code null} when outside or unavailable
   * @throws NullPointerException if connection or endpoint evidence is null
   * @throws IllegalArgumentException if cycle identities do not describe a boundary crossing or evidence disagrees
   */
  public DexpiConnectionCycleTransitionInfo(DexpiConnectionInfo connection, DexpiConnectionEndpointInfo fromEndpoint,
      DexpiConnectionEndpointInfo toEndpoint, String fromCycleId, String toCycleId, DexpiConnectionCycleInfo fromCycle,
      DexpiConnectionCycleInfo toCycle, DexpiConnectionCycleBoundaryInfo fromCycleBoundary,
      DexpiConnectionCycleBoundaryInfo toCycleBoundary) {
    this.connection = Objects.requireNonNull(connection, "connection");
    this.fromEndpoint = Objects.requireNonNull(fromEndpoint, "fromEndpoint");
    this.toEndpoint = Objects.requireNonNull(toEndpoint, "toEndpoint");
    this.fromCycleId = normalize(fromCycleId);
    this.toCycleId = normalize(toCycleId);
    this.fromCycle = fromCycle;
    this.toCycle = toCycle;
    this.fromCycleBoundary = fromCycleBoundary;
    this.toCycleBoundary = toCycleBoundary;
    if (this.fromCycleId.isEmpty() && this.toCycleId.isEmpty()) {
      throw new IllegalArgumentException("At least one endpoint must belong to a directed cycle");
    }
    if (!this.fromCycleId.isEmpty() && this.fromCycleId.equals(this.toCycleId)) {
      throw new IllegalArgumentException("A transition must cross a directed-cycle boundary");
    }
    if (fromCycle != null && !this.fromCycleId.equals(fromCycle.getId())) {
      throw new IllegalArgumentException("Source cycle evidence must match fromCycleId");
    }
    if (toCycle != null && !this.toCycleId.equals(toCycle.getId())) {
      throw new IllegalArgumentException("Target cycle evidence must match toCycleId");
    }
    validateBoundaryEvidence(fromCycleBoundary, this.fromCycleId, DexpiConnectionCycleBoundaryInfo.Direction.OUTGOING,
        "Source");
    validateBoundaryEvidence(toCycleBoundary, this.toCycleId, DexpiConnectionCycleBoundaryInfo.Direction.INCOMING,
        "Target");
    if (this.fromCycleId.isEmpty()) {
      kind = Kind.ENTERING;
    } else if (this.toCycleId.isEmpty()) {
      kind = Kind.LEAVING;
    } else {
      kind = Kind.BETWEEN_CYCLES;
    }
  }

  /** @return connection-evidence identity */
  public String getConnectionId() {
    return connection.getId();
  }

  /** @return source directed-cycle identity, or empty when outside every cyclic group */
  public String getFromCycleId() {
    return fromCycleId;
  }

  /** @return whether the source endpoint belongs to a directed-cycle group */
  public boolean hasFromCycle() {
    return !fromCycleId.isEmpty();
  }

  /** @return complete source directed-cycle evidence, or {@code null} when outside or unavailable */
  public DexpiConnectionCycleInfo getFromCycle() {
    return fromCycle;
  }

  /** @return whether complete source directed-cycle evidence is available */
  public boolean hasFromCycleEvidence() {
    return fromCycle != null;
  }

  /** @return source cycle's outgoing boundary evidence, or {@code null} when outside or unavailable */
  public DexpiConnectionCycleBoundaryInfo getFromCycleBoundary() {
    return fromCycleBoundary;
  }

  /** @return whether complete source cycle-boundary evidence is available */
  public boolean hasFromCycleBoundaryEvidence() {
    return fromCycleBoundary != null;
  }

  /** @return target directed-cycle identity, or empty when outside every cyclic group */
  public String getToCycleId() {
    return toCycleId;
  }

  /** @return whether the target endpoint belongs to a directed-cycle group */
  public boolean hasToCycle() {
    return !toCycleId.isEmpty();
  }

  /** @return complete target directed-cycle evidence, or {@code null} when outside or unavailable */
  public DexpiConnectionCycleInfo getToCycle() {
    return toCycle;
  }

  /** @return whether complete target directed-cycle evidence is available */
  public boolean hasToCycleEvidence() {
    return toCycle != null;
  }

  /** @return target cycle's incoming boundary evidence, or {@code null} when outside or unavailable */
  public DexpiConnectionCycleBoundaryInfo getToCycleBoundary() {
    return toCycleBoundary;
  }

  /** @return whether complete target cycle-boundary evidence is available */
  public boolean hasToCycleBoundaryEvidence() {
    return toCycleBoundary != null;
  }

  /** @return transition classification relative to the cyclic groups */
  public Kind getKind() {
    return kind;
  }

  /** @return complete source connection evidence */
  public DexpiConnectionInfo getConnection() {
    return connection;
  }

  /** @return complete source-endpoint evidence */
  public DexpiConnectionEndpointInfo getFromEndpoint() {
    return fromEndpoint;
  }

  /** @return complete target-endpoint evidence */
  public DexpiConnectionEndpointInfo getToEndpoint() {
    return toEndpoint;
  }

  Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("connectionId", connection.getId());
    result.put("fromCycleId", fromCycleId);
    result.put("toCycleId", toCycleId);
    result.put("hasFromCycleEvidence", Boolean.valueOf(hasFromCycleEvidence()));
    result.put("hasToCycleEvidence", Boolean.valueOf(hasToCycleEvidence()));
    result.put("hasFromCycleBoundaryEvidence", Boolean.valueOf(hasFromCycleBoundaryEvidence()));
    result.put("hasToCycleBoundaryEvidence", Boolean.valueOf(hasToCycleBoundaryEvidence()));
    result.put("kind", kind.name());
    result.put("fromCycle", fromCycle == null ? null : fromCycle.toMap());
    result.put("toCycle", toCycle == null ? null : toCycle.toMap());
    result.put("fromCycleBoundary", fromCycleBoundary == null ? null : fromCycleBoundary.toMap());
    result.put("toCycleBoundary", toCycleBoundary == null ? null : toCycleBoundary.toMap());
    result.put("connection", connection.toMap());
    result.put("fromEndpoint", fromEndpoint.toMap());
    result.put("toEndpoint", toEndpoint.toMap());
    return result;
  }

  private void validateBoundaryEvidence(DexpiConnectionCycleBoundaryInfo boundary, String cycleId,
      DexpiConnectionCycleBoundaryInfo.Direction expectedDirection, String label) {
    if (boundary == null) {
      return;
    }
    if (cycleId.isEmpty()) {
      throw new IllegalArgumentException(label + " boundary evidence requires a cycle identity");
    }
    if (!Objects.equals(connection.getId(), boundary.getConnectionId())) {
      throw new IllegalArgumentException(label + " boundary evidence must match the connection identity");
    }
    if (boundary.getDirection() != expectedDirection) {
      throw new IllegalArgumentException(label + " boundary evidence has the wrong direction");
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
