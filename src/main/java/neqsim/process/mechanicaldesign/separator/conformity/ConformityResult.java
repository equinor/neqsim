package neqsim.process.mechanicaldesign.separator.conformity;

import java.io.Serializable;

/**
 * Result of a single conformity check against a design standard.
 *
 * <p>
 * Each check evaluates an actual operating value against a limit from a named
 * standard (e.g.,
 * TR3500). The result includes the check name, the internal it applies to (if
 * any), the actual
 * value, the limit, and a pass/warning/fail status.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ConformityResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Status of a conformity check.
   */
  public enum Status {
    /** Actual value is within the acceptable range. */
    PASS,
    /** Actual value is close to the limit (within warning threshold). */
    WARNING,
    /** Actual value exceeds the limit. */
    FAIL,
    /** Check could not be evaluated (missing data or not applicable). */
    NOT_APPLICABLE
  }

  /**
   * Direction of the limit check.
   */
  public enum LimitDirection {
    /** Actual value must be BELOW the limit (e.g., K-factor, momentum). */
    MAXIMUM,
    /**
     * Actual value must be ABOVE the limit (e.g., drainage head, retention time).
     */
    MINIMUM
  }

  private final String checkName;
  private final String standard;
  private final String internalType;
  private final double actualValue;
  private final double limitValue;
  private final String unit;
  private final Status status;
  private final LimitDirection direction;
  private final String description;

  /**
   * Constructs a ConformityResult.
   *
   * @param checkName    short identifier for the check (e.g., "k-factor",
   *                     "inlet-momentum")
   * @param standard     the conformity standard (e.g., "TR3500", "API-12J")
   * @param internalType the internal this check applies to (e.g., "mesh-pad",
   *                     "demisting-cyclones")
   *                     or empty string for vessel-level checks
   * @param actualValue  the calculated actual value
   * @param limitValue   the acceptance limit from the standard
   * @param unit         the engineering unit (e.g., "m/s", "Pa", "mm")
   * @param direction    whether the limit is a maximum or minimum
   * @param description  human-readable description of the check
   */
  public ConformityResult(String checkName, String standard, String internalType,
      double actualValue, double limitValue, String unit, LimitDirection direction,
      String description) {
    this.checkName = checkName;
    this.standard = standard;
    this.internalType = internalType;
    this.actualValue = actualValue;
    this.limitValue = limitValue;
    this.unit = unit;
    this.direction = direction;
    this.description = description;
    this.status = evaluateStatus(actualValue, limitValue, direction);
  }

  /**
   * Creates a NOT_APPLICABLE result when a check cannot be evaluated.
   *
   * @param checkName short identifier for the check
   * @param standard  the conformity standard
   * @param reason    why the check is not applicable
   * @return a ConformityResult with NOT_APPLICABLE status
   */
  public static ConformityResult notApplicable(String checkName, String standard, String reason) {
    ConformityResult result = new ConformityResult(checkName, standard, "", Double.NaN, Double.NaN,
        "", LimitDirection.MAXIMUM, reason);
    return new ConformityResult(checkName, standard, "", Double.NaN, Double.NaN, "",
        LimitDirection.MAXIMUM, reason) {
      private static final long serialVersionUID = 1L;

      @Override
      public Status getStatus() {
        return Status.NOT_APPLICABLE;
      }
    };
  }

  /**
   * Evaluates the status based on actual value, limit, and direction.
   *
   * @param actual the actual value
   * @param limit  the limit value
   * @param dir    the direction (MAXIMUM or MINIMUM)
   * @return the evaluated status
   */
  private static Status evaluateStatus(double actual, double limit, LimitDirection dir) {
    if (Double.isNaN(actual) || Double.isNaN(limit)) {
      return Status.NOT_APPLICABLE;
    }
    double warningThreshold = 0.9;
    if (dir == LimitDirection.MAXIMUM) {
      if (actual > limit) {
        return Status.FAIL;
      } else if (actual > limit * warningThreshold) {
        return Status.WARNING;
      }
      return Status.PASS;
    } else {
      // MINIMUM: actual must be >= limit
      if (actual < limit) {
        return Status.FAIL;
      } else if (actual < limit * (1.0 + (1.0 - warningThreshold))) {
        return Status.WARNING;
      }
      return Status.PASS;
    }
  }

  /**
   * Gets the check name.
   *
   * @return the check name
   */
  public String getCheckName() {
    return checkName;
  }

  /**
   * Gets the standard name.
   *
   * @return the standard name
   */
  public String getStandard() {
    return standard;
  }

  /**
   * Gets the internal type this check applies to.
   *
   * @return the internal type, or empty string for vessel-level checks
   */
  public String getInternalType() {
    return internalType;
  }

  /**
   * Gets the actual calculated value.
   *
   * @return the actual value
   */
  public double getActualValue() {
    return actualValue;
  }

  /**
   * Gets the limit value from the standard.
   *
   * @return the limit value
   */
  public double getLimitValue() {
    return limitValue;
  }

  /**
   * Gets the engineering unit.
   *
   * @return the unit string
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Gets the conformity status.
   *
   * @return PASS, WARNING, FAIL, or NOT_APPLICABLE
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Gets the limit direction.
   *
   * @return MAXIMUM or MINIMUM
   */
  public LimitDirection getDirection() {
    return direction;
  }

  /**
   * Gets the human-readable description.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Returns true if the check passed (PASS or WARNING).
   *
   * @return true if status is PASS or WARNING
   */
  public boolean isPassed() {
    return status == Status.PASS || status == Status.WARNING;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return String.format("%-25s %10.4f %10.4f %-6s %-4s  %s",
        checkName, actualValue, limitValue, unit, status, description);
  }
}
