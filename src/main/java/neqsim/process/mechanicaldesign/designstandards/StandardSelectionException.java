package neqsim.process.mechanicaldesign.designstandards;

/** Exception raised when a strict standard selection cannot be honored. */
public final class StandardSelectionException extends IllegalArgumentException {
  private static final long serialVersionUID = 1000L;

  /** Machine-readable rejection reason. */
  public enum Reason {
    /** No selection was supplied. */
    MISSING_SELECTION,
    /** No equipment context was supplied. */
    MISSING_EQUIPMENT,
    /** The standard is discovery metadata without an implemented calculation. */
    CATALOG_ONLY,
    /** A calculation exists elsewhere but is not connected to the registry. */
    NOT_REGISTRY_CONNECTED,
    /** A kernel exists, but not for the requested edition basis. */
    EDITION_NOT_IMPLEMENTED,
    /** The standard does not apply to the supplied equipment type. */
    NOT_APPLICABLE
  }

  private final Reason reason;
  private final StandardType standardType;
  private final String equipmentType;

  StandardSelectionException(Reason reason, StandardType standardType, String equipmentType, String detail) {
    super(buildMessage(reason, standardType, equipmentType, detail));
    this.reason = reason;
    this.standardType = standardType;
    this.equipmentType = equipmentType;
  }

  /** @return machine-readable rejection reason */
  public Reason getReason() {
    return reason;
  }

  /** @return rejected standard type, or {@code null} if no selection was supplied */
  public StandardType getStandardType() {
    return standardType;
  }

  /** @return equipment type, or {@code null} if unavailable */
  public String getEquipmentType() {
    return equipmentType;
  }

  private static String buildMessage(Reason reason, StandardType standardType, String equipmentType, String detail) {
    StringBuilder message = new StringBuilder("Standard selection rejected: ").append(reason);
    if (standardType != null) {
      message.append(" (standard=").append(standardType.getCode()).append(')');
    }
    if (equipmentType != null) {
      message.append(" (equipment=").append(equipmentType).append(')');
    }
    if (detail != null && !detail.isEmpty()) {
      message.append(". ").append(detail);
    }
    return message.toString();
  }
}
