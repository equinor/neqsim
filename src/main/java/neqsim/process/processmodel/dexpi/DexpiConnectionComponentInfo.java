package neqsim.process.processmodel.dexpi;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable evidence for one weakly connected group of explicit DEXPI material-connection endpoint references.
 *
 * <p>
 * A component groups source reference identities only. It does not establish hydraulic continuity, physical branch or
 * fitting identity, process intent, or live {@code ProcessSystem} topology.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiConnectionComponentInfo implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String id;
  private final List<String> endpointIds;
  private final List<DexpiConnectionEndpointInfo> endpoints;
  private final List<String> connectionIds;
  private final List<DexpiConnectionInfo> connections;
  private final List<String> sourceEndpointIds;
  private final List<String> sinkEndpointIds;
  private final List<String> potentialMultiConnectionEndpointIds;
  private final List<String> unresolvedEndpointIds;
  private final boolean completeEvidence;

  /**
   * Creates immutable source-reference component evidence.
   *
   * <p>
   * This legacy constructor preserves scalar and identity evidence only. {@link #hasCompleteEvidence()} returns
   * {@code false}; use the complete-evidence constructor when endpoint and connection records are available.
   * </p>
   *
   * @param id deterministic component evidence identity
   * @param endpointIds explicit endpoint identities in first-reference order
   * @param connectionIds connection-evidence identities in source order
   * @param sourceEndpointIds endpoints classified as source evidence
   * @param sinkEndpointIds endpoints classified as sink evidence
   * @param potentialMultiConnectionEndpointIds endpoints with multiple incoming or outgoing occurrences
   * @param unresolvedEndpointIds endpoint identities that do not resolve in the source document
   */
  public DexpiConnectionComponentInfo(String id, List<String> endpointIds, List<String> connectionIds,
      List<String> sourceEndpointIds, List<String> sinkEndpointIds, List<String> potentialMultiConnectionEndpointIds,
      List<String> unresolvedEndpointIds) {
    this(id, endpointIds, Collections.<DexpiConnectionEndpointInfo>emptyList(), connectionIds,
        Collections.<DexpiConnectionInfo>emptyList(), sourceEndpointIds, sinkEndpointIds,
        potentialMultiConnectionEndpointIds, unresolvedEndpointIds, false);
  }

  /**
   * Creates immutable source-reference component evidence with complete endpoint and connection occurrences.
   *
   * @param id deterministic component evidence identity
   * @param endpointIds explicit endpoint identities in first-reference order
   * @param endpoints endpoint evidence records in first-reference order
   * @param connectionIds connection-evidence identities in source order
   * @param connections connection occurrences in source order
   * @param sourceEndpointIds endpoints classified as source evidence
   * @param sinkEndpointIds endpoints classified as sink evidence
   * @param potentialMultiConnectionEndpointIds endpoints with multiple incoming or outgoing occurrences
   * @param unresolvedEndpointIds endpoint identities that do not resolve in the source document
   */
  public DexpiConnectionComponentInfo(String id, List<String> endpointIds,
      List<DexpiConnectionEndpointInfo> endpoints, List<String> connectionIds, List<DexpiConnectionInfo> connections,
      List<String> sourceEndpointIds, List<String> sinkEndpointIds, List<String> potentialMultiConnectionEndpointIds,
      List<String> unresolvedEndpointIds) {
    this(id, endpointIds, endpoints, connectionIds, connections, sourceEndpointIds, sinkEndpointIds,
        potentialMultiConnectionEndpointIds, unresolvedEndpointIds, true);
  }

  private DexpiConnectionComponentInfo(String id, List<String> endpointIds,
      List<DexpiConnectionEndpointInfo> endpoints, List<String> connectionIds, List<DexpiConnectionInfo> connections,
      List<String> sourceEndpointIds, List<String> sinkEndpointIds, List<String> potentialMultiConnectionEndpointIds,
      List<String> unresolvedEndpointIds, boolean completeEvidence) {
    this.id = normalize(id);
    this.endpointIds = immutableCopy(endpointIds);
    this.endpoints = Collections.unmodifiableList(new ArrayList<DexpiConnectionEndpointInfo>(endpoints));
    this.connectionIds = immutableCopy(connectionIds);
    this.connections = Collections.unmodifiableList(new ArrayList<DexpiConnectionInfo>(connections));
    this.sourceEndpointIds = immutableCopy(sourceEndpointIds);
    this.sinkEndpointIds = immutableCopy(sinkEndpointIds);
    this.potentialMultiConnectionEndpointIds = immutableCopy(potentialMultiConnectionEndpointIds);
    this.unresolvedEndpointIds = immutableCopy(unresolvedEndpointIds);
    this.completeEvidence = completeEvidence;
  }

  /** @return deterministic component evidence identity */
  public String getId() {
    return id;
  }

  /** @return immutable endpoint identities in first-reference order */
  public List<String> getEndpointIds() {
    return endpointIds;
  }

  /** @return immutable endpoint evidence records in first-reference order */
  public List<DexpiConnectionEndpointInfo> getEndpoints() {
    return endpoints;
  }

  /** @return immutable connection-evidence identities in source order */
  public List<String> getConnectionIds() {
    return connectionIds;
  }

  /** @return immutable connection occurrences in source order */
  public List<DexpiConnectionInfo> getConnections() {
    return connections;
  }

  /** @return immutable endpoint identities classified as source evidence */
  public List<String> getSourceEndpointIds() {
    return sourceEndpointIds;
  }

  /** @return immutable endpoint identities classified as sink evidence */
  public List<String> getSinkEndpointIds() {
    return sinkEndpointIds;
  }

  /**
   * Returns endpoints with multiple incoming or outgoing source occurrences.
   *
   * @return immutable potential multi-connection endpoint identities
   */
  public List<String> getPotentialMultiConnectionEndpointIds() {
    return potentialMultiConnectionEndpointIds;
  }

  /** @return immutable unresolved endpoint identities */
  public List<String> getUnresolvedEndpointIds() {
    return unresolvedEndpointIds;
  }

  /** @return number of explicit endpoint identities in this source-reference component */
  public int getEndpointCount() {
    return endpointIds.size();
  }

  /** @return number of source connection occurrences in this source-reference component */
  public int getConnectionCount() {
    return connectionIds.size();
  }

  /** @return whether complete endpoint and connection records were supplied */
  public boolean hasCompleteEvidence() {
    return completeEvidence;
  }

  /** @return whether any endpoint reference in this component is unresolved */
  public boolean hasUnresolvedEndpoints() {
    return !unresolvedEndpointIds.isEmpty();
  }

  /** @return whether any endpoint has multiple incoming or outgoing source occurrences */
  public boolean hasPotentialMultiConnectionNodes() {
    return !potentialMultiConnectionEndpointIds.isEmpty();
  }

  Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("endpointCount", Integer.valueOf(getEndpointCount()));
    result.put("connectionCount", Integer.valueOf(getConnectionCount()));
    result.put("completeEvidence", Boolean.valueOf(hasCompleteEvidence()));
    result.put("hasUnresolvedEndpoints", Boolean.valueOf(hasUnresolvedEndpoints()));
    result.put("hasPotentialMultiConnectionNodes", Boolean.valueOf(hasPotentialMultiConnectionNodes()));
    result.put("endpointIds", endpointIds);
    List<Map<String, Object>> endpointMaps = new ArrayList<Map<String, Object>>();
    for (DexpiConnectionEndpointInfo endpoint : endpoints) {
      endpointMaps.add(endpoint.toMap());
    }
    result.put("endpoints", endpointMaps);
    result.put("connectionIds", connectionIds);
    List<Map<String, Object>> connectionMaps = new ArrayList<Map<String, Object>>();
    for (DexpiConnectionInfo connection : connections) {
      connectionMaps.add(connection.toMap());
    }
    result.put("connections", connectionMaps);
    result.put("sourceEndpointIds", sourceEndpointIds);
    result.put("sinkEndpointIds", sinkEndpointIds);
    result.put("potentialMultiConnectionEndpointIds", potentialMultiConnectionEndpointIds);
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
