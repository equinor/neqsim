package neqsim.process.engineering;

import java.io.Serializable;

/** Controlled definition of a process, relief, drain, vent, recycle or utility document boundary. */
public final class EngineeringBoundary implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Boundary service and expected flow direction relative to the current DEXPI document. */
  public enum Type {
    PROCESS_INLET, PROCESS_OUTLET, FLARE_HEADER, VENT_HEADER, CLOSED_DRAIN, UTILITY_INLET, UTILITY_OUTLET,
    RECYCLE_TIE_IN
  }

  private final String id;
  private final String equipmentTag;
  private final Type type;
  private String connectedDocumentReference = "UNRESOLVED";
  private String evidenceReference = "";

  /** Creates an unresolved controlled engineering boundary. */
  public EngineeringBoundary(String id, String equipmentTag, Type type) {
    if (id == null || id.trim().isEmpty() || equipmentTag == null || equipmentTag.trim().isEmpty() || type == null) {
      throw new IllegalArgumentException("id, equipmentTag and type are required");
    }
    this.id = id;
    this.equipmentTag = equipmentTag;
    this.type = type;
  }

  /** Records the destination/source document and evidence used to resolve this boundary. */
  public EngineeringBoundary resolve(String documentReference, String evidenceReference) {
    if (documentReference == null || documentReference.trim().isEmpty() || evidenceReference == null
        || evidenceReference.trim().isEmpty()) {
      throw new IllegalArgumentException("documentReference and evidenceReference are required");
    }
    this.connectedDocumentReference = documentReference;
    this.evidenceReference = evidenceReference;
    return this;
  }

  public String getId() {
    return id;
  }

  public String getEquipmentTag() {
    return equipmentTag;
  }

  public Type getType() {
    return type;
  }

  public String getConnectedDocumentReference() {
    return connectedDocumentReference;
  }

  public String getEvidenceReference() {
    return evidenceReference;
  }

  public boolean isResolved() {
    return !"UNRESOLVED".equals(connectedDocumentReference);
  }
}
