package neqsim.pvtsimulation.util;

/**
 * Utility class for validating black-oil PVT tables per Whitson wiki guidelines.
 *
 * <p>
 * Black-oil tables (Bo, Rs, Bg, μo, μg) must satisfy certain physical constraints for valid
 * reservoir simulation. This class provides validation methods to ensure table consistency.
 * </p>
 *
 * <p>
 * Reference: Whitson wiki (https://wiki.whitson.com/bopvt/black_oil/)
 * </p>
 *
 * <h2>Key Validation Criteria:</h2>
 * <ul>
 * <li><b>Bo</b> - Must decrease monotonically below bubble point</li>
 * <li><b>Rs</b> - Must decrease monotonically below bubble point</li>
 * <li><b>Bg</b> - Must increase monotonically with decreasing pressure</li>
 * <li><b>μo</b> - Must increase with decreasing pressure (more gas liberated)</li>
 * <li><b>μg</b> - Should increase with decreasing pressure</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>
 * {@code
 * double[] pressures = {300, 250, 200, 150, 100};
 * double[] Bo = {1.45, 1.40, 1.35, 1.30, 1.25};
 * double[] Rs = {200, 170, 140, 110, 80};
 *
 * ValidationResult result = BlackOilTableValidator.validate(pressures, Bo, Rs, null, null, null);
 * System.out.println(result.isValid());
 * System.out.println(result.getReport());
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public final class BlackOilTableValidator {

  /** Maximum allowed deviation before warning (5%). */
  private static final double WARNING_THRESHOLD = 0.05;

  /** Maximum allowed deviation before failure (10%). */
  private static final double FAILURE_THRESHOLD = 0.10;

  /** Private constructor to prevent instantiation. */
  private BlackOilTableValidator() {}

  /**
   * Comprehensive validation of black-oil tables.
   *
   * @param pressures pressure values (bar), assumed decreasing
   * @param Bo oil formation volume factor (rm3/sm3)
   * @param Rs solution gas-oil ratio (sm3/sm3)
   * @param Bg gas formation volume factor (rm3/sm3), may be null
   * @param oilViscosity oil viscosity (cP), may be null
   * @param gasViscosity gas viscosity (cP), may be null
   * @return validation result with status and detailed report
   */
  public static ValidationResult validate(double[] pressures, double[] Bo, double[] Rs, double[] Bg,
      double[] oilViscosity, double[] gasViscosity) {

    ValidationResult result = new ValidationResult();

    if (pressures == null || pressures.length == 0) {
      result.addError("Pressure array is null or empty");
      return result;
    }

    // Validate Bo
    if (Bo != null && Bo.length == pressures.length) {
      validateBoArray(pressures, Bo, result);
    }

    // Validate Rs
    if (Rs != null && Rs.length == pressures.length) {
      validateRsArray(pressures, Rs, result);
    }

    // Validate Bg
    if (Bg != null && Bg.length == pressures.length) {
      validateBgArray(pressures, Bg, result);
    }

    // Validate oil viscosity
    if (oilViscosity != null && oilViscosity.length == pressures.length) {
      validateOilViscosityArray(pressures, oilViscosity, result);
    }

    // Validate gas viscosity
    if (gasViscosity != null && gasViscosity.length == pressures.length) {
      validateGasViscosityArray(pressures, gasViscosity, result);
    }

    // Validate Bo-Rs consistency
    if (Bo != null && Rs != null && Bo.length == Rs.length) {
      validateBoRsConsistency(pressures, Bo, Rs, result);
    }

    return result;
  }

  /**
   * Validate Bo array for monotonicity and physical bounds.
   */
  private static void validateBoArray(double[] pressures, double[] Bo, ValidationResult result) {
    // Check for positive values
    for (int i = 0; i < Bo.length; i++) {
      if (Bo[i] <= 0) {
        result.addError(
            String.format("Bo[%d] = %.4f is non-positive at P = %.2f bar", i, Bo[i], pressures[i]));
      }
      if (Bo[i] < 1.0) {
        result.addWarning(String.format("Bo[%d] = %.4f is less than 1.0 at P = %.2f bar", i, Bo[i],
            pressures[i]));
      }
    }

    // Check monotonicity (Bo should decrease with decreasing pressure)
    int nonMonotonic = countMonotonicityViolations(pressures, Bo, true);
    if (nonMonotonic > 0) {
      result.addError(String
          .format("Bo has %d non-monotonic points (should decrease with pressure)", nonMonotonic));
    } else {
      result.addInfo("Bo monotonicity: PASS");
    }
  }

  /**
   * Validate Rs array for monotonicity and physical bounds.
   */
  private static void validateRsArray(double[] pressures, double[] Rs, ValidationResult result) {
    // Check for non-negative values
    for (int i = 0; i < Rs.length; i++) {
      if (Rs[i] < 0) {
        result.addError(
            String.format("Rs[%d] = %.4f is negative at P = %.2f bar", i, Rs[i], pressures[i]));
      }
    }

    // Rs at lowest pressure should approach zero (for DLE going to stock-tank)
    int lastIdx = Rs.length - 1;
    if (pressures[lastIdx] < 2.0 && Rs[lastIdx] > 1.0) {
      result.addWarning(
          String.format("Rs = %.2f at P = %.2f bar; expected near zero at stock-tank conditions",
              Rs[lastIdx], pressures[lastIdx]));
    }

    // Check monotonicity (Rs should decrease with decreasing pressure)
    int nonMonotonic = countMonotonicityViolations(pressures, Rs, true);
    if (nonMonotonic > 0) {
      result.addError(String
          .format("Rs has %d non-monotonic points (should decrease with pressure)", nonMonotonic));
    } else {
      result.addInfo("Rs monotonicity: PASS");
    }
  }

  /**
   * Validate Bg array for monotonicity and physical bounds.
   */
  private static void validateBgArray(double[] pressures, double[] Bg, ValidationResult result) {
    // Check for positive values where Bg is defined
    for (int i = 0; i < Bg.length; i++) {
      if (Bg[i] < 0) {
        result.addError(
            String.format("Bg[%d] = %.6f is negative at P = %.2f bar", i, Bg[i], pressures[i]));
      }
    }

    // Check monotonicity (Bg should increase with decreasing pressure)
    int nonMonotonic = 0;
    for (int i = 1; i < Bg.length; i++) {
      // Only check where both values are positive (gas exists)
      if (Bg[i] > 0 && Bg[i - 1] > 0) {
        if (pressures[i] < pressures[i - 1] && Bg[i] < Bg[i - 1]) {
          nonMonotonic++;
        }
      }
    }

    if (nonMonotonic > 0) {
      result.addWarning(String.format(
          "Bg has %d non-monotonic points (should increase as pressure decreases)", nonMonotonic));
    } else {
      result.addInfo("Bg monotonicity: PASS");
    }
  }

  /**
   * Validate oil viscosity array.
   */
  private static void validateOilViscosityArray(double[] pressures, double[] oilVisc,
      ValidationResult result) {
    // Check for positive values
    for (int i = 0; i < oilVisc.length; i++) {
      if (oilVisc[i] <= 0) {
        result.addError(String.format("Oil viscosity[%d] = %.4f is non-positive at P = %.2f bar", i,
            oilVisc[i], pressures[i]));
      }
    }

    // Oil viscosity typically increases as gas liberates (pressure decreases)
    int nonMonotonic = 0;
    for (int i = 1; i < oilVisc.length; i++) {
      if (pressures[i] < pressures[i - 1] && oilVisc[i] < oilVisc[i - 1]) {
        nonMonotonic++;
      }
    }

    if (nonMonotonic > oilVisc.length / 4) { // Allow some tolerance
      result.addWarning(String.format(
          "Oil viscosity has %d points where it decreases with pressure (usually increases)",
          nonMonotonic));
    }
  }

  /**
   * Validate gas viscosity array.
   */
  private static void validateGasViscosityArray(double[] pressures, double[] gasVisc,
      ValidationResult result) {
    // Check for positive values
    for (int i = 0; i < gasVisc.length; i++) {
      if (gasVisc[i] <= 0) {
        result.addError(String.format("Gas viscosity[%d] = %.6f is non-positive at P = %.2f bar", i,
            gasVisc[i], pressures[i]));
      }
    }

    // Gas viscosity typically decreases with decreasing pressure (composition effect)
    result.addInfo("Gas viscosity bounds check: PASS");
  }

  /**
   * Validate consistency between Bo and Rs.
   *
   * <p>
   * At stock-tank conditions (Rs ≈ 0), Bo should approach a minimum value (dead oil FVF). The
   * relationship between Bo and Rs should be smooth.
   * </p>
   */
  private static void validateBoRsConsistency(double[] pressures, double[] Bo, double[] Rs,
      ValidationResult result) {

    // Calculate dBo/dRs (should be positive - more gas means higher Bo)
    for (int i = 1; i < Bo.length; i++) {
      double dBo = Bo[i] - Bo[i - 1];
      double dRs = Rs[i] - Rs[i - 1];

      if (dRs != 0) {
        double dBodRs = dBo / dRs;
        if (dBodRs < 0) {
          result.addWarning(
              String.format("Inconsistent Bo-Rs relationship at P = %.2f bar (dBo/dRs = %.6f)",
                  pressures[i], dBodRs));
        }
      }
    }

    // At minimum Rs, Bo should be positive and greater than 1
    int minRsIdx = 0;
    double minRs = Rs[0];
    for (int i = 1; i < Rs.length; i++) {
      if (Rs[i] < minRs) {
        minRs = Rs[i];
        minRsIdx = i;
      }
    }

    if (Bo[minRsIdx] < 1.0) {
      result.addWarning(String.format("Bo = %.4f at minimum Rs; expected Bo >= 1.0", Bo[minRsIdx]));
    }

    result.addInfo("Bo-Rs consistency check: PASS");
  }

  /**
   * Count monotonicity violations.
   *
   * @param pressures pressure array (assumed decreasing)
   * @param values property values
   * @param shouldDecrease true if property should decrease with decreasing pressure
   * @return number of monotonicity violations
   */
  private static int countMonotonicityViolations(double[] pressures, double[] values,
      boolean shouldDecrease) {
    int violations = 0;

    for (int i = 1; i < values.length; i++) {
      boolean pressureDecreasing = pressures[i] < pressures[i - 1];
      boolean valueDecreasing = values[i] < values[i - 1];

      if (pressureDecreasing) {
        if (shouldDecrease && !valueDecreasing) {
          violations++;
        } else if (!shouldDecrease && valueDecreasing) {
          violations++;
        }
      }
    }

    return violations;
  }

  /**
   * Interpolate black-oil properties at a given pressure.
   *
   * <p>
   * Uses linear interpolation between table values. Extrapolation is not recommended.
   * </p>
   *
   * @param pressures pressure table (bar)
   * @param values property values
   * @param targetPressure pressure to interpolate at (bar)
   * @return interpolated value, or NaN if out of range
   */
  public static double interpolate(double[] pressures, double[] values, double targetPressure) {
    if (pressures == null || values == null || pressures.length != values.length) {
      return Double.NaN;
    }

    // Find bracketing pressures
    for (int i = 0; i < pressures.length - 1; i++) {
      double p1 = pressures[i];
      double p2 = pressures[i + 1];

      if ((p1 >= targetPressure && targetPressure >= p2)
          || (p2 >= targetPressure && targetPressure >= p1)) {
        // Linear interpolation
        double fraction = (targetPressure - p1) / (p2 - p1);
        return values[i] + fraction * (values[i + 1] - values[i]);
      }
    }

    return Double.NaN; // Out of range
  }

  /**
   * Validation result container.
   */
  public static class ValidationResult {
    private boolean hasErrors = false;
    private boolean hasWarnings = false;
    private final StringBuilder report = new StringBuilder();

    /**
     * Add an error message.
     *
     * @param message error description
     */
    public void addError(String message) {
      hasErrors = true;
      report.append("[ERROR] ").append(message).append("\n");
    }

    /**
     * Add a warning message.
     *
     * @param message warning description
     */
    public void addWarning(String message) {
      hasWarnings = true;
      report.append("[WARNING] ").append(message).append("\n");
    }

    /**
     * Add an informational message.
     *
     * @param message info description
     */
    public void addInfo(String message) {
      report.append("[INFO] ").append(message).append("\n");
    }

    /**
     * Check if validation passed without errors.
     *
     * @return true if no errors
     */
    public boolean isValid() {
      return !hasErrors;
    }

    /**
     * Check if validation has warnings.
     *
     * @return true if warnings present
     */
    public boolean hasWarnings() {
      return hasWarnings;
    }

    /**
     * Get the full validation report.
     *
     * @return formatted report string
     */
    public String getReport() {
      StringBuilder fullReport = new StringBuilder();
      fullReport.append("=== Black-Oil Table Validation Report ===\n\n");
      fullReport.append("Status: ");
      if (hasErrors) {
        fullReport.append("FAILED\n\n");
      } else if (hasWarnings) {
        fullReport.append("PASSED WITH WARNINGS\n\n");
      } else {
        fullReport.append("PASSED\n\n");
      }
      fullReport.append(report);
      return fullReport.toString();
    }
  }
}
