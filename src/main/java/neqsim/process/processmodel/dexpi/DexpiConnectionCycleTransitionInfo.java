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
    this.connection = Objects.requireNonNull(connection, "connection");
    this.fromEndpoint = Objects.requireNonNull(fromEndpoint, "fromEndpoint");
    this.toEndpoint = Objects.requireNonNull(toEndpoint, "toEndpoint");
    this.fromCycleId = normalize(fromCycleId);
    this.toCycleId = normalize(toCycleId);
    if (this.fromCycleId.isEmpty() && this.toCycleId.isEmpty()) {
      throw new IllegalArgumentException("At least one endpoint must belong to a directed cycle");
    }
    if (!this.fromCycleId.isEmpty() && this.fromCycleId.equals(this.toCycleId)) {
      throw new IllegalArgumentException("A transition must cross a directed-cycle boundary");
    }
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

  /** @return target directed-cycle identity, or empty when outside every cyclic group */
  public String getToCycleId() {
    return toCycleId;
  }

  /** @return whether the target endpoint belongs to a directed-cycle group */
  public boolean hasToCycle() {
    return !toCycleId.isEmpty();
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
    result.put("kind", kind.name());
    result.put("connection", connection.toMap());
    result.put("fromEndpoint", fromEndpoint.toMap());
    result.put("toEndpoint", toEndpoint.toMap());
    return result;
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
