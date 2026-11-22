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
  private final String sourceComponentClass;
  private final String sourceComponentName;
  private final String sourceTagName;
  private final String targetId;
  private final boolean targetResolved;
  private final String targetElementName;
  private final String targetComponentClass;
  private final String targetComponentName;
  private final String targetTagName;
  private final String attachmentId;
  private final boolean attachmentResolved;
  private final String attachmentElementName;
  private final String attachmentComponentClass;
  private final String attachmentComponentName;
  private final String attachmentTagName;
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
  public DexpiInformationFlowInfo(String id, Kind kind, String componentClass, String sourceId, boolean sourceResolved,
      String sourceElementName, String targetId, boolean targetResolved, String targetElementName, String attachmentId,
      boolean attachmentResolved, String attachmentElementName, String signalConveyingType) {
    this(id, kind, componentClass, sourceId, sourceResolved, sourceElementName, "", "", "", targetId, targetResolved,
        targetElementName, "", "", "", attachmentId, attachmentResolved, attachmentElementName, "", "", "",
        signalConveyingType);
  }

  /**
   * Creates immutable evidence with explicit metadata from resolved referenced elements.
   *
   * @param id source XML identity, or an empty string when absent
   * @param kind supported information-flow kind
   * @param componentClass source component class
   * @param sourceId logical source identity, or an empty string when absent
   * @param sourceResolved whether the logical source resolves to a source element
   * @param sourceElementName resolved logical-source XML element name, or an empty string
   * @param sourceComponentClass explicit logical-source component class, or an empty string
   * @param sourceComponentName explicit logical-source component name, or an empty string
   * @param sourceTagName explicit logical-source tag name, or an empty string
   * @param targetId logical target identity, or an empty string when absent
   * @param targetResolved whether the logical target resolves to a source element
   * @param targetElementName resolved logical-target XML element name, or an empty string
   * @param targetComponentClass explicit logical-target component class, or an empty string
   * @param targetComponentName explicit logical-target component name, or an empty string
   * @param targetTagName explicit logical-target tag name, or an empty string
   * @param attachmentId process-attachment identity, or an empty string when absent
   * @param attachmentResolved whether the process attachment resolves to a source element
   * @param attachmentElementName resolved attachment XML element name, or an empty string
   * @param attachmentComponentClass explicit attachment component class, or an empty string
   * @param attachmentComponentName explicit attachment component name, or an empty string
   * @param attachmentTagName explicit attachment tag name, or an empty string
   * @param signalConveyingType signal medium/type, or an empty string when absent or not applicable
   */
  public DexpiInformationFlowInfo(String id, Kind kind, String componentClass, String sourceId, boolean sourceResolved,
      String sourceElementName, String sourceComponentClass, String sourceComponentName, String sourceTagName,
      String targetId, boolean targetResolved, String targetElementName, String targetComponentClass,
      String targetComponentName, String targetTagName, String attachmentId, boolean attachmentResolved,
      String attachmentElementName, String attachmentComponentClass, String attachmentComponentName,
      String attachmentTagName, String signalConveyingType) {
    this.id = normalize(id);
    this.kind = Objects.requireNonNull(kind, "kind");
    this.componentClass = normalize(componentClass);
    this.sourceId = normalize(sourceId);
    this.sourceResolved = sourceResolved;
    this.sourceElementName = normalize(sourceElementName);
    this.sourceComponentClass = normalize(sourceComponentClass);
    this.sourceComponentName = normalize(sourceComponentName);
    this.sourceTagName = normalize(sourceTagName);
    this.targetId = normalize(targetId);
    this.targetResolved = targetResolved;
    this.targetElementName = normalize(targetElementName);
    this.targetComponentClass = normalize(targetComponentClass);
    this.targetComponentName = normalize(targetComponentName);
    this.targetTagName = normalize(targetTagName);
    this.attachmentId = normalize(attachmentId);
    this.attachmentResolved = attachmentResolved;
    this.attachmentElementName = normalize(attachmentElementName);
    this.attachmentComponentClass = normalize(attachmentComponentClass);
    this.attachmentComponentName = normalize(attachmentComponentName);
    this.attachmentTagName = normalize(attachmentTagName);
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

  /** @return explicit logical-source component class, or an empty string */
  public String getSourceComponentClass() {
    return sourceComponentClass;
  }

  /** @return explicit logical-source component name, or an empty string */
  public String getSourceComponentName() {
    return sourceComponentName;
  }

  /** @return explicit logical-source tag name, or an empty string */
  public String getSourceTagName() {
    return sourceTagName;
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

  /** @return explicit logical-target component class, or an empty string */
  public String getTargetComponentClass() {
    return targetComponentClass;
  }

  /** @return explicit logical-target component name, or an empty string */
  public String getTargetComponentName() {
    return targetComponentName;
  }

  /** @return explicit logical-target tag name, or an empty string */
  public String getTargetTagName() {
    return targetTagName;
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

  /** @return explicit process-attachment component class, or an empty string */
  public String getAttachmentComponentClass() {
    return attachmentComponentClass;
  }

  /** @return explicit process-attachment component name, or an empty string */
  public String getAttachmentComponentName() {
    return attachmentComponentName;
  }

  /** @return explicit process-attachment tag name, or an empty string */
  public String getAttachmentTagName() {
    return attachmentTagName;
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
    result.put("sourceComponentClass", sourceComponentClass);
    result.put("sourceComponentName", sourceComponentName);
    result.put("sourceTagName", sourceTagName);
    result.put("targetId", targetId);
    result.put("targetResolved", Boolean.valueOf(targetResolved));
    result.put("targetElementName", targetElementName);
    result.put("targetComponentClass", targetComponentClass);
    result.put("targetComponentName", targetComponentName);
    result.put("targetTagName", targetTagName);
    result.put("attachmentId", attachmentId);
    result.put("attachmentResolved", Boolean.valueOf(attachmentResolved));
    result.put("attachmentElementName", attachmentElementName);
    result.put("attachmentComponentClass", attachmentComponentClass);
    result.put("attachmentComponentName", attachmentComponentName);
    result.put("attachmentTagName", attachmentTagName);
    result.put("signalConveyingType", signalConveyingType);
    return result;
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
