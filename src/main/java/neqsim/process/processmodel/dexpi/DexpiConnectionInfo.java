package neqsim.process.processmodel.dexpi;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable source-evidence record for one Proteus-compatible DEXPI material connection.
 *
 * <p>
 * This record preserves source order, direction, ownership, and endpoint-resolution evidence. It
 * does not reconstruct or imply live {@code ProcessSystem} topology.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiConnectionInfo implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String id;
  private final String sourceId;
  private final String segmentId;
  private final String fromId;
  private final String toId;
  private final String fromElementName;
  private final String toElementName;
  private final boolean fromResolved;
  private final boolean toResolved;

  /**
   * Creates an immutable connection evidence record.
   *
   * @param id stable evidence identity
   * @param sourceId source connection identity, or empty when absent
   * @param segmentId owning piping-network segment identity, or empty when absent
   * @param fromId source endpoint identity
   * @param toId target endpoint identity
   * @param fromElementName resolved source XML element name
   * @param toElementName resolved target XML element name
   * @param fromResolved whether the source endpoint resolves in the source document
   * @param toResolved whether the target endpoint resolves in the source document
   */
  public DexpiConnectionInfo(String id, String sourceId, String segmentId, String fromId,
      String toId, String fromElementName, String toElementName, boolean fromResolved,
      boolean toResolved) {
    this.id = normalize(id);
    this.sourceId = normalize(sourceId);
    this.segmentId = normalize(segmentId);
    this.fromId = normalize(fromId);
    this.toId = normalize(toId);
    this.fromElementName = normalize(fromElementName);
    this.toElementName = normalize(toElementName);
    this.fromResolved = fromResolved;
    this.toResolved = toResolved;
  }

  /** @return stable evidence identity */
  public String getId() {
    return id;
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

  /** @return source endpoint identity */
  public String getFromId() {
    return fromId;
  }

  /** @return target endpoint identity */
  public String getToId() {
    return toId;
  }

  /** @return resolved source endpoint XML element name, or empty when unresolved */
  public String getFromElementName() {
    return fromElementName;
  }

  /** @return resolved target endpoint XML element name, or empty when unresolved */
  public String getToElementName() {
    return toElementName;
  }

  /** @return whether the source endpoint resolves in the source document */
  public boolean isFromResolved() {
    return fromResolved;
  }

  /** @return whether the target endpoint resolves in the source document */
  public boolean isToResolved() {
    return toResolved;
  }

  /** @return whether both endpoint references resolve */
  public boolean isResolved() {
    return fromResolved && toResolved;
  }

  /** @return whether both non-empty endpoint identities are equal */
  public boolean isSelfReference() {
    return !fromId.isEmpty() && fromId.equals(toId);
  }

  Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("sourceId", sourceId);
    result.put("segmentId", segmentId);
    result.put("fromId", fromId);
    result.put("toId", toId);
    result.put("fromElementName", fromElementName);
    result.put("toElementName", toElementName);
    result.put("fromResolved", Boolean.valueOf(fromResolved));
    result.put("toResolved", Boolean.valueOf(toResolved));
    return result;
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
