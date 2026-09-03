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
  private final String sourceId;
  private final String segmentId;
  private final Direction direction;
  private final String internalEndpointId;
  private final String internalEndpointElementName;
  private final String internalOwnerId;
  private final String internalOwnerElementName;
  private final String externalEndpointId;
  private final String externalEndpointElementName;
  private final String externalOwnerId;
  private final String externalOwnerElementName;
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
    this(connectionId, "", "", direction, internalEndpointId, "", "", "", externalEndpointId, "", "", "",
        internalEndpointResolved, externalEndpointResolved);
  }

  /**
   * Creates immutable cycle-boundary source-reference evidence with endpoint ownership.
   *
   * @param connectionId connection-evidence identity
   * @param direction connection direction relative to the cyclic group
   * @param internalEndpointId endpoint identity inside the cyclic group
   * @param internalEndpointElementName resolved internal endpoint XML element name, or empty
   * @param internalOwnerId explicit internal endpoint owner identity, or empty
   * @param internalOwnerElementName internal endpoint owner XML element name, or empty
   * @param externalEndpointId endpoint identity outside the cyclic group
   * @param externalEndpointElementName resolved external endpoint XML element name, or empty
   * @param externalOwnerId explicit external endpoint owner identity, or empty
   * @param externalOwnerElementName external endpoint owner XML element name, or empty
   * @param internalEndpointResolved whether the internal endpoint resolves in the source document
   * @param externalEndpointResolved whether the external endpoint resolves in the source document
   */
  public DexpiConnectionCycleBoundaryInfo(String connectionId, Direction direction, String internalEndpointId,
      String internalEndpointElementName, String internalOwnerId, String internalOwnerElementName,
      String externalEndpointId, String externalEndpointElementName, String externalOwnerId,
      String externalOwnerElementName, boolean internalEndpointResolved, boolean externalEndpointResolved) {
    this(connectionId, "", "", direction, internalEndpointId, internalEndpointElementName, internalOwnerId,
        internalOwnerElementName, externalEndpointId, externalEndpointElementName, externalOwnerId,
        externalOwnerElementName, internalEndpointResolved, externalEndpointResolved);
  }

  /**
   * Creates immutable cycle-boundary source-reference evidence with connection and endpoint provenance.
   *
   * @param connectionId connection-evidence identity
   * @param sourceId original source connection identity, or empty when absent
   * @param segmentId owning piping-network segment identity, or empty when absent
   * @param direction connection direction relative to the cyclic group
   * @param internalEndpointId endpoint identity inside the cyclic group
   * @param internalEndpointElementName resolved internal endpoint XML element name, or empty
   * @param internalOwnerId explicit internal endpoint owner identity, or empty
   * @param internalOwnerElementName internal endpoint owner XML element name, or empty
   * @param externalEndpointId endpoint identity outside the cyclic group
   * @param externalEndpointElementName resolved external endpoint XML element name, or empty
   * @param externalOwnerId explicit external endpoint owner identity, or empty
   * @param externalOwnerElementName external endpoint owner XML element name, or empty
   * @param internalEndpointResolved whether the internal endpoint resolves in the source document
   * @param externalEndpointResolved whether the external endpoint resolves in the source document
   */
  public DexpiConnectionCycleBoundaryInfo(String connectionId, String sourceId, String segmentId,
      Direction direction, String internalEndpointId, String internalEndpointElementName, String internalOwnerId,
      String internalOwnerElementName, String externalEndpointId, String externalEndpointElementName,
      String externalOwnerId, String externalOwnerElementName, boolean internalEndpointResolved,
      boolean externalEndpointResolved) {
    this.connectionId = normalize(connectionId);
    this.sourceId = normalize(sourceId);
    this.segmentId = normalize(segmentId);
    this.direction = direction;
    this.internalEndpointId = normalize(internalEndpointId);
    this.internalEndpointElementName = normalize(internalEndpointElementName);
    this.internalOwnerId = normalize(internalOwnerId);
    this.internalOwnerElementName = normalize(internalOwnerElementName);
    this.externalEndpointId = normalize(externalEndpointId);
    this.externalEndpointElementName = normalize(externalEndpointElementName);
    this.externalOwnerId = normalize(externalOwnerId);
    this.externalOwnerElementName = normalize(externalOwnerElementName);
    this.internalEndpointResolved = internalEndpointResolved;
    this.externalEndpointResolved = externalEndpointResolved;
  }

  /** @return connection-evidence identity */
  public String getConnectionId() {
    return connectionId;
  }

  /** @return original source connection identity, or empty when absent */
  public String getSourceId() {
    return sourceId;
  }

  /** @return whether the source supplied a connection identity */
  public boolean hasSourceId() {
    return !sourceId.isEmpty();
  }

  /** @return owning piping-network segment identity, or empty when absent */
  public String getSegmentId() {
    return segmentId;
  }

  /** @return connection direction relative to the cyclic group */
  public Direction getDirection() {
    return direction;
  }

  /** @return endpoint identity inside the cyclic group */
  public String getInternalEndpointId() {
    return internalEndpointId;
  }

  /** @return resolved internal endpoint XML element name, or empty */
  public String getInternalEndpointElementName() {
    return internalEndpointElementName;
  }

  /** @return explicit internal endpoint owner identity, or empty */
  public String getInternalOwnerId() {
    return internalOwnerId;
  }

  /** @return internal endpoint owner XML element name, or empty */
  public String getInternalOwnerElementName() {
    return internalOwnerElementName;
  }

  /** @return endpoint identity outside the cyclic group */
  public String getExternalEndpointId() {
    return externalEndpointId;
  }

  /** @return resolved external endpoint XML element name, or empty */
  public String getExternalEndpointElementName() {
    return externalEndpointElementName;
  }

  /** @return explicit external endpoint owner identity, or empty */
  public String getExternalOwnerId() {
    return externalOwnerId;
  }

  /** @return external endpoint owner XML element name, or empty */
  public String getExternalOwnerElementName() {
    return externalOwnerElementName;
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
    result.put("sourceId", sourceId);
    result.put("segmentId", segmentId);
    result.put("direction", direction.name());
    result.put("internalEndpointId", internalEndpointId);
    result.put("internalEndpointElementName", internalEndpointElementName);
    result.put("internalOwnerId", internalOwnerId);
    result.put("internalOwnerElementName", internalOwnerElementName);
    result.put("externalEndpointId", externalEndpointId);
    result.put("externalEndpointElementName", externalEndpointElementName);
    result.put("externalOwnerId", externalOwnerId);
    result.put("externalOwnerElementName", externalOwnerElementName);
    result.put("internalEndpointResolved", Boolean.valueOf(internalEndpointResolved));
    result.put("externalEndpointResolved", Boolean.valueOf(externalEndpointResolved));
    return result;
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
