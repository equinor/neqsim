package neqsim.process.processmodel.dexpi;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable source-evidence summary for one endpoint referenced by Proteus-compatible DEXPI material connections.
 *
 * <p>
 * Incoming and outgoing occurrences retain connection-evidence IDs in source order. This record does not classify
 * branches or reconstruct live {@code ProcessSystem} topology.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiConnectionEndpointInfo implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String endpointId;
  private final String elementName;
  private final String ownerId;
  private final String ownerElementName;
  private final boolean resolved;
  private final List<String> incomingConnectionIds;
  private final List<String> outgoingConnectionIds;

  /**
   * Creates immutable endpoint-incidence evidence.
   *
   * @param endpointId explicit source endpoint identity
   * @param elementName resolved endpoint XML element name, or empty when unresolved
   * @param ownerId explicit endpoint owner identity, or empty when absent
   * @param ownerElementName endpoint owner XML element name, or empty when absent
   * @param resolved whether the endpoint resolves in the source document
   * @param incomingConnectionIds incoming connection-evidence IDs in source order
   * @param outgoingConnectionIds outgoing connection-evidence IDs in source order
   */
  public DexpiConnectionEndpointInfo(String endpointId, String elementName, String ownerId, String ownerElementName,
      boolean resolved, List<String> incomingConnectionIds, List<String> outgoingConnectionIds) {
    this.endpointId = normalize(endpointId);
    this.elementName = normalize(elementName);
    this.ownerId = normalize(ownerId);
    this.ownerElementName = normalize(ownerElementName);
    this.resolved = resolved;
    this.incomingConnectionIds = Collections.unmodifiableList(new ArrayList<String>(incomingConnectionIds));
    this.outgoingConnectionIds = Collections.unmodifiableList(new ArrayList<String>(outgoingConnectionIds));
  }

  /** @return explicit source endpoint identity */
  public String getEndpointId() {
    return endpointId;
  }

  /** @return resolved endpoint XML element name, or empty when unresolved */
  public String getElementName() {
    return elementName;
  }

  /** @return explicit endpoint owner identity, or empty when absent */
  public String getOwnerId() {
    return ownerId;
  }

  /** @return endpoint owner XML element name, or empty when absent */
  public String getOwnerElementName() {
    return ownerElementName;
  }

  /** @return whether the endpoint resolves in the source document */
  public boolean isResolved() {
    return resolved;
  }

  /** @return immutable incoming connection-evidence IDs in source order */
  public List<String> getIncomingConnectionIds() {
    return incomingConnectionIds;
  }

  /** @return immutable outgoing connection-evidence IDs in source order */
  public List<String> getOutgoingConnectionIds() {
    return outgoingConnectionIds;
  }

  /** @return number of incoming source connection occurrences */
  public int getIncomingConnectionCount() {
    return incomingConnectionIds.size();
  }

  /** @return number of outgoing source connection occurrences */
  public int getOutgoingConnectionCount() {
    return outgoingConnectionIds.size();
  }

  /** @return total number of source connection occurrences */
  public int getConnectionCount() {
    return incomingConnectionIds.size() + outgoingConnectionIds.size();
  }

  /** @return whether the endpoint occurs in more than one source connection role */
  public boolean isReferencedMultipleTimes() {
    return getConnectionCount() > 1;
  }

  Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("endpointId", endpointId);
    result.put("elementName", elementName);
    result.put("ownerId", ownerId);
    result.put("ownerElementName", ownerElementName);
    result.put("resolved", Boolean.valueOf(resolved));
    result.put("incomingConnectionCount", Integer.valueOf(getIncomingConnectionCount()));
    result.put("outgoingConnectionCount", Integer.valueOf(getOutgoingConnectionCount()));
    result.put("incomingConnectionIds", incomingConnectionIds);
    result.put("outgoingConnectionIds", outgoingConnectionIds);
    return result;
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
