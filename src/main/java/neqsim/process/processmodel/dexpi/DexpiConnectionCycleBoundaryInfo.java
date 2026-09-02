package neqsim.process.processmodel.dexpi;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable source evidence for one connection occurrence crossing a directed-cycle boundary.
 *
 * <p>
 * The internal and external endpoints are oriented relative to one cyclic strongly connected group. This record does
 * not establish hydraulic continuity, a physical recycle, process intent, or live {@code ProcessSystem} topology.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiConnectionCycleBoundaryInfo implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Direction of one explicit connection occurrence relative to the cyclic group. */
  public enum Direction {
    /** The connection source is outside and its target is inside the cyclic group. */
    INCOMING,
    /** The connection source is inside and its target is outside the cyclic group. */
    OUTGOING
  }

  private final String connectionId;
  private final Direction direction;
  private final String internalEndpointId;
  private final String externalEndpointId;
  private final boolean internalEndpointResolved;
  private final boolean externalEndpointResolved;

  /**
   * Creates immutable cycle-boundary source-reference evidence.
   *
   * @param connectionId connection-evidence identity
   * @param direction connection direction relative to the cyclic group
   * @param internalEndpointId endpoint identity inside the cyclic group
   * @param externalEndpointId endpoint identity outside the cyclic group
   * @param internalEndpointResolved whether the internal endpoint resolves in the source document
   * @param externalEndpointResolved whether the external endpoint resolves in the source document
   */
  public DexpiConnectionCycleBoundaryInfo(String connectionId, Direction direction, String internalEndpointId,
      String externalEndpointId, boolean internalEndpointResolved, boolean externalEndpointResolved) {
    this.connectionId = normalize(connectionId);
    this.direction = direction;
    this.internalEndpointId = normalize(internalEndpointId);
    this.externalEndpointId = normalize(externalEndpointId);
    this.internalEndpointResolved = internalEndpointResolved;
    this.externalEndpointResolved = externalEndpointResolved;
  }

  /** @return connection-evidence identity */
  public String getConnectionId() {
    return connectionId;
  }

  /** @return connection direction relative to the cyclic group */
  public Direction getDirection() {
    return direction;
  }

  /** @return endpoint identity inside the cyclic group */
  public String getInternalEndpointId() {
    return internalEndpointId;
  }

  /** @return endpoint identity outside the cyclic group */
  public String getExternalEndpointId() {
    return externalEndpointId;
  }

  /** @return whether the internal endpoint resolves in the source document */
  public boolean isInternalEndpointResolved() {
    return internalEndpointResolved;
  }

  /** @return whether the external endpoint resolves in the source document */
  public boolean isExternalEndpointResolved() {
    return externalEndpointResolved;
  }

  Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("connectionId", connectionId);
    result.put("direction", direction.name());
    result.put("internalEndpointId", internalEndpointId);
    result.put("externalEndpointId", externalEndpointId);
    result.put("internalEndpointResolved", Boolean.valueOf(internalEndpointResolved));
    result.put("externalEndpointResolved", Boolean.valueOf(externalEndpointResolved));
    return result;
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
