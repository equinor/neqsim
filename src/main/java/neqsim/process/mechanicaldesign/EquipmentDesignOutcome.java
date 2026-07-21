package neqsim.process.mechanicaldesign;

import java.io.Serializable;

/**
 * Immutable outcome for one equipment item in a system mechanical design calculation.
 *
 * <p>
 * Failure details deliberately contain only the exception type and message. Stack traces remain in the application log
 * and are not copied into serialized engineering results.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class EquipmentDesignOutcome implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Status of an equipment design attempt. */
  public enum Status {
    /** The equipment design calculation completed and was included in system totals. */
    CALCULATED,

    /** The equipment design calculation failed and was excluded from system totals. */
    FAILED,

    /** The equipment was not attempted because fail-fast execution had stopped. */
    SKIPPED
  }

  private final String equipmentName;
  private final String equipmentType;
  private final Status status;
  private final String errorType;
  private final String message;

  private EquipmentDesignOutcome(String equipmentName, String equipmentType, Status status, String errorType,
      String message) {
    this.equipmentName = equipmentName;
    this.equipmentType = equipmentType;
    this.status = status;
    this.errorType = errorType;
    this.message = message;
  }

  /**
   * Create a successful outcome.
   *
   * @param equipmentName equipment name
   * @param equipmentType equipment Java type
   * @return calculated outcome
   */
  public static EquipmentDesignOutcome calculated(String equipmentName, String equipmentType) {
    return new EquipmentDesignOutcome(equipmentName, equipmentType, Status.CALCULATED, null, null);
  }

  /**
   * Create a failed outcome.
   *
   * @param equipmentName equipment name
   * @param equipmentType equipment Java type, or an empty string when the equipment could not be resolved
   * @param exception calculation exception
   * @return failed outcome
   */
  public static EquipmentDesignOutcome failed(String equipmentName, String equipmentType, Exception exception) {
    String failureMessage = exception.getMessage();
    if (failureMessage == null || failureMessage.trim().isEmpty()) {
      failureMessage = "No error message was provided";
    }
    return new EquipmentDesignOutcome(equipmentName, equipmentType, Status.FAILED, exception.getClass().getName(),
        failureMessage);
  }

  /**
   * Create a skipped outcome.
   *
   * @param equipmentName equipment name
   * @return skipped outcome
   */
  public static EquipmentDesignOutcome skipped(String equipmentName) {
    return new EquipmentDesignOutcome(equipmentName, "", Status.SKIPPED, null,
        "Not attempted because fail-fast execution stopped after an earlier failure");
  }

  /** @return equipment name */
  public String getEquipmentName() {
    return equipmentName;
  }

  /** @return equipment Java type, or an empty string when unavailable */
  public String getEquipmentType() {
    return equipmentType;
  }

  /** @return outcome status */
  public Status getStatus() {
    return status;
  }

  /** @return exception class name for a failed outcome, otherwise {@code null} */
  public String getErrorType() {
    return errorType;
  }

  /** @return failure or skip message, otherwise {@code null} */
  public String getMessage() {
    return message;
  }

  /** @return {@code true} only when the calculation completed successfully */
  public boolean isCalculated() {
    return status == Status.CALCULATED;
  }
}
