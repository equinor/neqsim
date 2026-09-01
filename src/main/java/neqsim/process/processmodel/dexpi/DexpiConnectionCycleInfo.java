package neqsim.process.processmodel.dexpi;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable evidence for one cyclic strongly connected group of explicit DEXPI material-connection
 * endpoint references.
 *
 * <p>
 * This record describes source-document graph evidence only. It does not establish hydraulic
 * continuity, a physical recycle, process intent, or live {@code ProcessSystem} topology.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiConnectionCycleInfo implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String id;
  private final String connectionComponentId;
  private final List<String> endpointIds;
  private final List<String> connectionIds;
  private final List<String> unresolvedEndpointIds;
  private final boolean selfReference;

  /**
   * Creates immutable directed-cycle source-reference evidence.
   *
   * @param id deterministic cycle-group evidence identity
   * @param connectionComponentId owning weak connection-component evidence identity
   * @param endpointIds endpoint identities in first-reference order
   * @param connectionIds internal connection-evidence identities in source order
   * @param unresolvedEndpointIds endpoint identities that do not resolve in the source document
   * @param selfReference whether the group contains an explicit self-reference connection
   */
  public DexpiConnectionCycleInfo(String id, String connectionComponentId,
      List<String> endpointIds, List<String> connectionIds, List<String> unresolvedEndpointIds,
      boolean selfReference) {
    this.id = normalize(id);
    this.connectionComponentId = normalize(connectionComponentId);
    this.endpointIds = immutableCopy(endpointIds);
    this.connectionIds = immutableCopy(connectionIds);
    this.unresolvedEndpointIds = immutableCopy(unresolvedEndpointIds);
    this.selfReference = selfReference;
  }

  /** @return deterministic cycle-group evidence identity */
  public String getId() {
    return id;
  }

  /** @return owning weak connection-component evidence identity */
  public String getConnectionComponentId() {
    return connectionComponentId;
  }

  /** @return immutable endpoint identities in first-reference order */
  public List<String> getEndpointIds() {
    return endpointIds;
  }

  /** @return immutable internal connection-evidence identities in source order */
  public List<String> getConnectionIds() {
    return connectionIds;
  }

  /** @return immutable unresolved endpoint identities */
  public List<String> getUnresolvedEndpointIds() {
    return unresolvedEndpointIds;
  }

  /** @return number of endpoints in this cyclic source-reference group */
  public int getEndpointCount() {
    return endpointIds.size();
  }

  /** @return number of internal source connection occurrences */
  public int getConnectionCount() {
    return connectionIds.size();
  }

  /** @return whether the group contains an explicit self-reference connection */
  public boolean hasSelfReference() {
    return selfReference;
  }

  /** @return whether any endpoint reference in this group is unresolved */
  public boolean hasUnresolvedEndpoints() {
    return !unresolvedEndpointIds.isEmpty();
  }

  Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("connectionComponentId", connectionComponentId);
    result.put("endpointCount", Integer.valueOf(getEndpointCount()));
    result.put("connectionCount", Integer.valueOf(getConnectionCount()));
    result.put("selfReference", Boolean.valueOf(selfReference));
    result.put("hasUnresolvedEndpoints", Boolean.valueOf(hasUnresolvedEndpoints()));
    result.put("endpointIds", endpointIds);
    result.put("connectionIds", connectionIds);
    result.put("unresolvedEndpointIds", unresolvedEndpointIds);
    return result;
  }

  private static List<String> immutableCopy(List<String> values) {
    return Collections.unmodifiableList(new ArrayList<String>(values));
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
