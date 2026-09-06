package neqsim.process.processmodel.dexpi;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable source-evidence record for one Proteus-compatible DEXPI material connection.
 *
 * <p>
 * This record preserves source order, direction, ownership, and endpoint-resolution evidence. It does not reconstruct
 * or imply live {@code ProcessSystem} topology.
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
  private final String segmentComponentClass;
  private final String segmentComponentName;
  private final String segmentTagName;
  private final String fromId;
  private final String toId;
  private final String fromElementName;
  private final String toElementName;
  private final String fromComponentClass;
  private final String fromComponentName;
  private final String fromTagName;
  private final String toComponentClass;
  private final String toComponentName;
  private final String toTagName;
  private final String fromOwnerId;
  private final String toOwnerId;
  private final String fromOwnerElementName;
  private final String toOwnerElementName;
  private final String fromOwnerComponentClass;
  private final String fromOwnerComponentName;
  private final String fromOwnerTagName;
  private final String toOwnerComponentClass;
  private final String toOwnerComponentName;
  private final String toOwnerTagName;
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
  public DexpiConnectionInfo(String id, String sourceId, String segmentId, String fromId, String toId,
      String fromElementName, String toElementName, boolean fromResolved, boolean toResolved) {
    this(id, sourceId, segmentId, fromId, toId, fromElementName, toElementName, "", "", "", "", fromResolved,
        toResolved);
  }

  /**
   * Creates an immutable connection evidence record with explicit endpoint ownership.
   *
   * @param id stable evidence identity
   * @param sourceId source connection identity, or empty when absent
   * @param segmentId owning piping-network segment identity, or empty when absent
   * @param fromId source endpoint identity
   * @param toId target endpoint identity
   * @param fromElementName resolved source XML element name
   * @param toElementName resolved target XML element name
   * @param fromOwnerId explicit source owner identity, or empty when absent
   * @param toOwnerId explicit target owner identity, or empty when absent
   * @param fromOwnerElementName source owner XML element name, or empty when absent
   * @param toOwnerElementName target owner XML element name, or empty when absent
   * @param fromResolved whether the source endpoint resolves in the source document
   * @param toResolved whether the target endpoint resolves in the source document
   */
  public DexpiConnectionInfo(String id, String sourceId, String segmentId, String fromId, String toId,
      String fromElementName, String toElementName, String fromOwnerId, String toOwnerId, String fromOwnerElementName,
      String toOwnerElementName, boolean fromResolved, boolean toResolved) {
    this(id, sourceId, segmentId, fromId, toId, fromElementName, toElementName, fromOwnerId, toOwnerId,
        fromOwnerElementName, toOwnerElementName, "", "", "", "", "", "", fromResolved, toResolved);
  }

  /**
   * Creates immutable connection evidence with explicit endpoint-owner provenance.
   *
   * @param id stable evidence identity
   * @param sourceId source connection identity, or empty when absent
   * @param segmentId owning piping-network segment identity, or empty when absent
   * @param fromId source endpoint identity
   * @param toId target endpoint identity
   * @param fromElementName resolved source XML element name
   * @param toElementName resolved target XML element name
   * @param fromOwnerId explicit source owner identity, or empty when absent
   * @param toOwnerId explicit target owner identity, or empty when absent
   * @param fromOwnerElementName source owner XML element name, or empty when absent
   * @param toOwnerElementName target owner XML element name, or empty when absent
   * @param fromOwnerComponentClass explicit source-owner ComponentClass, or empty when absent
   * @param fromOwnerComponentName explicit source-owner ComponentName, or empty when absent
   * @param fromOwnerTagName explicit source-owner TagName, or empty when absent
   * @param toOwnerComponentClass explicit target-owner ComponentClass, or empty when absent
   * @param toOwnerComponentName explicit target-owner ComponentName, or empty when absent
   * @param toOwnerTagName explicit target-owner TagName, or empty when absent
   * @param fromResolved whether the source endpoint resolves in the source document
   * @param toResolved whether the target endpoint resolves in the source document
   */
  public DexpiConnectionInfo(String id, String sourceId, String segmentId, String fromId, String toId,
      String fromElementName, String toElementName, String fromOwnerId, String toOwnerId, String fromOwnerElementName,
      String toOwnerElementName, String fromOwnerComponentClass, String fromOwnerComponentName, String fromOwnerTagName,
      String toOwnerComponentClass, String toOwnerComponentName, String toOwnerTagName, boolean fromResolved,
      boolean toResolved) {
    this(id, sourceId, segmentId, "", "", "", fromId, toId, fromElementName, toElementName, fromOwnerId, toOwnerId,
        fromOwnerElementName, toOwnerElementName, fromOwnerComponentClass, fromOwnerComponentName, fromOwnerTagName,
        toOwnerComponentClass, toOwnerComponentName, toOwnerTagName, fromResolved, toResolved);
  }

  /**
   * Creates immutable connection evidence with explicit segment and endpoint-owner provenance.
   *
   * @param id stable evidence identity
   * @param sourceId source connection identity, or empty when absent
   * @param segmentId owning piping-network segment identity, or empty when absent
   * @param segmentComponentClass explicit segment ComponentClass, or empty when absent
   * @param segmentComponentName explicit segment ComponentName, or empty when absent
   * @param segmentTagName explicit segment TagName, or empty when absent
   * @param fromId source endpoint identity
   * @param toId target endpoint identity
   * @param fromElementName resolved source XML element name
   * @param toElementName resolved target XML element name
   * @param fromOwnerId explicit source owner identity, or empty when absent
   * @param toOwnerId explicit target owner identity, or empty when absent
   * @param fromOwnerElementName source owner XML element name, or empty when absent
   * @param toOwnerElementName target owner XML element name, or empty when absent
   * @param fromOwnerComponentClass explicit source-owner ComponentClass, or empty when absent
   * @param fromOwnerComponentName explicit source-owner ComponentName, or empty when absent
   * @param fromOwnerTagName explicit source-owner TagName, or empty when absent
   * @param toOwnerComponentClass explicit target-owner ComponentClass, or empty when absent
   * @param toOwnerComponentName explicit target-owner ComponentName, or empty when absent
   * @param toOwnerTagName explicit target-owner TagName, or empty when absent
   * @param fromResolved whether the source endpoint resolves in the source document
   * @param toResolved whether the target endpoint resolves in the source document
   */
  public DexpiConnectionInfo(String id, String sourceId, String segmentId, String segmentComponentClass,
      String segmentComponentName, String segmentTagName, String fromId, String toId, String fromElementName,
      String toElementName, String fromOwnerId, String toOwnerId, String fromOwnerElementName,
      String toOwnerElementName, String fromOwnerComponentClass, String fromOwnerComponentName, String fromOwnerTagName,
      String toOwnerComponentClass, String toOwnerComponentName, String toOwnerTagName, boolean fromResolved,
      boolean toResolved) {
    this(id, sourceId, segmentId, segmentComponentClass, segmentComponentName, segmentTagName, fromId, toId,
        fromElementName, toElementName, "", "", "", "", "", "", fromOwnerId, toOwnerId, fromOwnerElementName,
        toOwnerElementName, fromOwnerComponentClass, fromOwnerComponentName, fromOwnerTagName, toOwnerComponentClass,
        toOwnerComponentName, toOwnerTagName, fromResolved, toResolved);
  }

  /**
   * Creates immutable connection evidence with explicit segment, endpoint, and owner provenance.
   *
   * @param id stable evidence identity
   * @param sourceId source connection identity, or empty when absent
   * @param segmentId owning piping-network segment identity, or empty when absent
   * @param segmentComponentClass explicit segment ComponentClass, or empty when absent
   * @param segmentComponentName explicit segment ComponentName, or empty when absent
   * @param segmentTagName explicit segment TagName, or empty when absent
   * @param fromId source endpoint identity
   * @param toId target endpoint identity
   * @param fromElementName resolved source XML element name
   * @param toElementName resolved target XML element name
   * @param fromComponentClass explicit source-endpoint ComponentClass, or empty when absent
   * @param fromComponentName explicit source-endpoint ComponentName, or empty when absent
   * @param fromTagName explicit source-endpoint TagName, or empty when absent
   * @param toComponentClass explicit target-endpoint ComponentClass, or empty when absent
   * @param toComponentName explicit target-endpoint ComponentName, or empty when absent
   * @param toTagName explicit target-endpoint TagName, or empty when absent
   * @param fromOwnerId explicit source owner identity, or empty when absent
   * @param toOwnerId explicit target owner identity, or empty when absent
   * @param fromOwnerElementName source owner XML element name, or empty when absent
   * @param toOwnerElementName target owner XML element name, or empty when absent
   * @param fromOwnerComponentClass explicit source-owner ComponentClass, or empty when absent
   * @param fromOwnerComponentName explicit source-owner ComponentName, or empty when absent
   * @param fromOwnerTagName explicit source-owner TagName, or empty when absent
   * @param toOwnerComponentClass explicit target-owner ComponentClass, or empty when absent
   * @param toOwnerComponentName explicit target-owner ComponentName, or empty when absent
   * @param toOwnerTagName explicit target-owner TagName, or empty when absent
   * @param fromResolved whether the source endpoint resolves in the source document
   * @param toResolved whether the target endpoint resolves in the source document
   */
  public DexpiConnectionInfo(String id, String sourceId, String segmentId, String segmentComponentClass,
      String segmentComponentName, String segmentTagName, String fromId, String toId, String fromElementName,
      String toElementName, String fromComponentClass, String fromComponentName, String fromTagName,
      String toComponentClass, String toComponentName, String toTagName, String fromOwnerId, String toOwnerId,
      String fromOwnerElementName, String toOwnerElementName, String fromOwnerComponentClass,
      String fromOwnerComponentName, String fromOwnerTagName, String toOwnerComponentClass,
      String toOwnerComponentName, String toOwnerTagName, boolean fromResolved, boolean toResolved) {
    this.id = normalize(id);
    this.sourceId = normalize(sourceId);
    this.segmentId = normalize(segmentId);
    this.segmentComponentClass = normalize(segmentComponentClass);
    this.segmentComponentName = normalize(segmentComponentName);
    this.segmentTagName = normalize(segmentTagName);
    this.fromId = normalize(fromId);
    this.toId = normalize(toId);
    this.fromElementName = normalize(fromElementName);
    this.toElementName = normalize(toElementName);
    this.fromComponentClass = normalize(fromComponentClass);
    this.fromComponentName = normalize(fromComponentName);
    this.fromTagName = normalize(fromTagName);
    this.toComponentClass = normalize(toComponentClass);
    this.toComponentName = normalize(toComponentName);
    this.toTagName = normalize(toTagName);
    this.fromOwnerId = normalize(fromOwnerId);
    this.toOwnerId = normalize(toOwnerId);
    this.fromOwnerElementName = normalize(fromOwnerElementName);
    this.toOwnerElementName = normalize(toOwnerElementName);
    this.fromOwnerComponentClass = normalize(fromOwnerComponentClass);
    this.fromOwnerComponentName = normalize(fromOwnerComponentName);
    this.fromOwnerTagName = normalize(fromOwnerTagName);
    this.toOwnerComponentClass = normalize(toOwnerComponentClass);
    this.toOwnerComponentName = normalize(toOwnerComponentName);
    this.toOwnerTagName = normalize(toOwnerTagName);
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

  /** @return explicit segment ComponentClass, or empty when absent */
  public String getSegmentComponentClass() {
    return segmentComponentClass;
  }

  /** @return explicit segment ComponentName, or empty when absent */
  public String getSegmentComponentName() {
    return segmentComponentName;
  }

  /** @return explicit segment TagName, or empty when absent */
  public String getSegmentTagName() {
    return segmentTagName;
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

  /** @return explicit source-endpoint ComponentClass, or empty when absent */
  public String getFromComponentClass() {
    return fromComponentClass;
  }

  /** @return explicit source-endpoint ComponentName, or empty when absent */
  public String getFromComponentName() {
    return fromComponentName;
  }

  /** @return explicit source-endpoint TagName, or empty when absent */
  public String getFromTagName() {
    return fromTagName;
  }

  /** @return explicit target-endpoint ComponentClass, or empty when absent */
  public String getToComponentClass() {
    return toComponentClass;
  }

  /** @return explicit target-endpoint ComponentName, or empty when absent */
  public String getToComponentName() {
    return toComponentName;
  }

  /** @return explicit target-endpoint TagName, or empty when absent */
  public String getToTagName() {
    return toTagName;
  }

  /** @return explicit source endpoint owner identity, or empty when absent */
  public String getFromOwnerId() {
    return fromOwnerId;
  }

  /** @return explicit target endpoint owner identity, or empty when absent */
  public String getToOwnerId() {
    return toOwnerId;
  }

  /** @return source owner XML element name, or empty when absent */
  public String getFromOwnerElementName() {
    return fromOwnerElementName;
  }

  /** @return target owner XML element name, or empty when absent */
  public String getToOwnerElementName() {
    return toOwnerElementName;
  }

  /** @return explicit source-owner ComponentClass, or empty when absent */
  public String getFromOwnerComponentClass() {
    return fromOwnerComponentClass;
  }

  /** @return explicit source-owner ComponentName, or empty when absent */
  public String getFromOwnerComponentName() {
    return fromOwnerComponentName;
  }

  /** @return explicit source-owner TagName, or empty when absent */
  public String getFromOwnerTagName() {
    return fromOwnerTagName;
  }

  /** @return explicit target-owner ComponentClass, or empty when absent */
  public String getToOwnerComponentClass() {
    return toOwnerComponentClass;
  }

  /** @return explicit target-owner ComponentName, or empty when absent */
  public String getToOwnerComponentName() {
    return toOwnerComponentName;
  }

  /** @return explicit target-owner TagName, or empty when absent */
  public String getToOwnerTagName() {
    return toOwnerTagName;
  }

  /** @return whether both endpoint owners have explicit identities */
  public boolean isOwnershipResolved() {
    return !fromOwnerId.isEmpty() && !toOwnerId.isEmpty();
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
    result.put("segmentComponentClass", segmentComponentClass);
    result.put("segmentComponentName", segmentComponentName);
    result.put("segmentTagName", segmentTagName);
    result.put("fromId", fromId);
    result.put("toId", toId);
    result.put("fromElementName", fromElementName);
    result.put("toElementName", toElementName);
    result.put("fromComponentClass", fromComponentClass);
    result.put("fromComponentName", fromComponentName);
    result.put("fromTagName", fromTagName);
    result.put("toComponentClass", toComponentClass);
    result.put("toComponentName", toComponentName);
    result.put("toTagName", toTagName);
    result.put("fromOwnerId", fromOwnerId);
    result.put("toOwnerId", toOwnerId);
    result.put("fromOwnerElementName", fromOwnerElementName);
    result.put("toOwnerElementName", toOwnerElementName);
    result.put("fromOwnerComponentClass", fromOwnerComponentClass);
    result.put("fromOwnerComponentName", fromOwnerComponentName);
    result.put("fromOwnerTagName", fromOwnerTagName);
    result.put("toOwnerComponentClass", toOwnerComponentClass);
    result.put("toOwnerComponentName", toOwnerComponentName);
    result.put("toOwnerTagName", toOwnerTagName);
    result.put("fromResolved", Boolean.valueOf(fromResolved));
    result.put("toResolved", Boolean.valueOf(toResolved));
    return result;
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
