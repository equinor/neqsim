package neqsim.process.mechanicaldesign.designstandards;

import java.io.Serializable;
import java.util.Objects;

/** Immutable result of evaluating whether a standard applies to equipment. */
public final class StandardApplicability implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Applicability result. */
  public enum Status {
    /** The equipment type is listed for the standard. */
    APPLICABLE,
    /** The equipment type is known but is not listed for the standard. */
    NOT_APPLICABLE,
    /** Applicability cannot be determined from the available equipment context. */
    UNKNOWN
  }

  private final StandardType standardType;
  private final String equipmentType;
  private final Status status;
  private final String reason;

  private StandardApplicability(StandardType standardType, String equipmentType, Status status, String reason) {
    this.standardType = Objects.requireNonNull(standardType, "standardType");
    this.equipmentType = equipmentType;
    this.status = Objects.requireNonNull(status, "status");
    this.reason = reason == null ? "" : reason;
  }

  static StandardApplicability applicable(StandardType standardType, String equipmentType) {
    return new StandardApplicability(standardType, equipmentType, Status.APPLICABLE,
        standardType.getCode() + " lists " + equipmentType + " as applicable equipment.");
  }

  static StandardApplicability notApplicable(StandardType standardType, String equipmentType) {
    return new StandardApplicability(standardType, equipmentType, Status.NOT_APPLICABLE,
        standardType.getCode() + " does not list " + equipmentType + " as applicable equipment.");
  }

  static StandardApplicability unknown(StandardType standardType, String reason) {
    return new StandardApplicability(standardType, null, Status.UNKNOWN, reason);
  }

  /** @return standard type */
  public StandardType getStandardType() {
    return standardType;
  }

  /** @return simple equipment class name, or {@code null} when unavailable */
  public String getEquipmentType() {
    return equipmentType;
  }

  /** @return applicability status */
  public Status getStatus() {
    return status;
  }

  /** @return human-readable reason */
  public String getReason() {
    return reason;
  }

  /** @return whether the standard is explicitly applicable */
  public boolean isApplicable() {
    return status == Status.APPLICABLE;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof StandardApplicability)) {
      return false;
    }
    StandardApplicability other = (StandardApplicability) object;
    return standardType == other.standardType && Objects.equals(equipmentType, other.equipmentType)
        && status == other.status && reason.equals(other.reason);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(standardType, equipmentType, status, reason);
  }
}
