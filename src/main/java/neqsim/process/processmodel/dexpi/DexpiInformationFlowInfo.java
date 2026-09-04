package neqsim.process.processmodel.dexpi;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable source evidence for one supported DEXPI instrumentation information-flow occurrence.
 *
 * <p>
 * The record preserves explicit source-document relationships without constructing live transmitters, controllers,
 * safeguards, or executable control topology.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiInformationFlowInfo implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Supported source information-flow kinds. */
  public enum Kind {
    /** A logical signal connection between instrumentation functions. */
    SIGNAL_LINE,
    /** A measuring relationship from a sensing function to an instrument and process attachment. */
    MEASURING_LINE
  }

  private final String id;
  private final Kind kind;
  private final String componentClass;
  private final String sourceId;
  private final boolean sourceResolved;
  private final String sourceElementName;
  private final String targetId;
  private final boolean targetResolved;
  private final String targetElementName;
  private final String attachmentId;
  private final boolean attachmentResolved;
  private final String attachmentElementName;
  private final String signalConveyingType;

  /**
   * Creates immutable evidence for one information-flow occurrence.
   *
   * @param id source XML identity, or an empty string when absent
   * @param kind supported information-flow kind
   * @param componentClass source component class
   * @param sourceId logical source identity, or an empty string when absent
   * @param sourceResolved whether the logical source resolves to a source element
   * @param sourceElementName resolved logical-source XML element name, or an empty string
   * @param targetId logical target identity, or an empty string when absent
   * @param targetResolved whether the logical target resolves to a source element
   * @param targetElementName resolved logical-target XML element name, or an empty string
   * @param attachmentId process-attachment identity, or an empty string when absent
   * @param attachmentResolved whether the process attachment resolves to a source element
   * @param attachmentElementName resolved attachment XML element name, or an empty string
   * @param signalConveyingType signal medium/type, or an empty string when absent or not applicable
   */
  public DexpiInformationFlowInfo(String id, Kind kind, String componentClass, String sourceId,
      boolean sourceResolved, String sourceElementName, String targetId, boolean targetResolved,
      String targetElementName, String attachmentId, boolean attachmentResolved, String attachmentElementName,
      String signalConveyingType) {
    this.id = normalize(id);
    this.kind = Objects.requireNonNull(kind, "kind");
    this.componentClass = normalize(componentClass);
    this.sourceId = normalize(sourceId);
    this.sourceResolved = sourceResolved;
    this.sourceElementName = normalize(sourceElementName);
    this.targetId = normalize(targetId);
    this.targetResolved = targetResolved;
    this.targetElementName = normalize(targetElementName);
    this.attachmentId = normalize(attachmentId);
    this.attachmentResolved = attachmentResolved;
    this.attachmentElementName = normalize(attachmentElementName);
    this.signalConveyingType = normalize(signalConveyingType);
  }

  /** @return source XML identity, or an empty string when absent */
  public String getId() {
    return id;
  }

  /** @return supported information-flow kind */
  public Kind getKind() {
    return kind;
  }

  /** @return source component class */
  public String getComponentClass() {
    return componentClass;
  }

  /** @return logical source identity, or an empty string when absent */
  public String getSourceId() {
    return sourceId;
  }

  /** @return whether the logical source resolves to a source element */
  public boolean isSourceResolved() {
    return sourceResolved;
  }

  /** @return resolved logical-source XML element name, or an empty string */
  public String getSourceElementName() {
    return sourceElementName;
  }

  /** @return logical target identity, or an empty string when absent */
  public String getTargetId() {
    return targetId;
  }

  /** @return whether the logical target resolves to a source element */
  public boolean isTargetResolved() {
    return targetResolved;
  }

  /** @return resolved logical-target XML element name, or an empty string */
  public String getTargetElementName() {
    return targetElementName;
  }

  /** @return process-attachment identity, or an empty string when absent */
  public String getAttachmentId() {
    return attachmentId;
  }

  /** @return whether the source contains a process-attachment reference */
  public boolean hasAttachment() {
    return !attachmentId.isEmpty();
  }

  /** @return whether the process attachment resolves to a source element */
  public boolean isAttachmentResolved() {
    return attachmentResolved;
  }

  /** @return resolved process-attachment XML element name, or an empty string */
  public String getAttachmentElementName() {
    return attachmentElementName;
  }

  /** @return signal medium/type, or an empty string when absent or not applicable */
  public String getSignalConveyingType() {
    return signalConveyingType;
  }

  Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("kind", kind.name());
    result.put("componentClass", componentClass);
    result.put("sourceId", sourceId);
    result.put("sourceResolved", Boolean.valueOf(sourceResolved));
    result.put("sourceElementName", sourceElementName);
    result.put("targetId", targetId);
    result.put("targetResolved", Boolean.valueOf(targetResolved));
    result.put("targetElementName", targetElementName);
    result.put("attachmentId", attachmentId);
    result.put("attachmentResolved", Boolean.valueOf(attachmentResolved));
    result.put("attachmentElementName", attachmentElementName);
    result.put("signalConveyingType", signalConveyingType);
    return result;
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
