package neqsim.standards.oilquality;

/**
 * Literature-qualified reference-point conversion between ASTM D86 and atmospheric true boiling point (TBP)
 * temperatures.
 *
 * <p>
 * The correlation is {@code T_TBP = a * T_D86^b}, with both temperatures in kelvin. Coefficients and applicability
 * ranges are defined at seven discrete liquid-volume recovery points. This class intentionally rejects intermediate
 * recovery percentages rather than inventing an interpolation rule that is not part of the qualified reference-point
 * contract.
 * </p>
 *
 * <p>
 * Source: M. R. Riazi and T. E. Daubert, "Analytical correlations interconvert distillation-curve types," Oil &amp; Gas
 * Journal, vol. 84, no. 34, pp. 50-54, 57, August 25, 1986. Public bibliographic record:
 * https://www.osti.gov/biblio/5212509.
 * </p>
 *
 * <p>
 * This empirical conversion is not an implementation of the ASTM D86 test procedure and does not establish standards
 * compliance.
 * </p>
 */
public final class RiaziDaubertDistillationConversion {
  private static final double CELSIUS_TO_KELVIN = 273.15;
  private static final double POINT_TOLERANCE = 1.0e-9;

  /**
   * Rows are recovery vol%, a, b, minimum D86 temperature in C, maximum D86 temperature in C, worked-example D86
   * temperature in C, and worked-example TBP temperature in C.
   */
  private static final double[][] REFERENCE_DATA = { { 0.0, 0.9177, 1.0019, 20.0, 320.0, 36.5, 14.1 },
      { 10.0, 0.5564, 1.0900, 35.0, 305.0, 54.1, 33.4 }, { 30.0, 0.7617, 1.0425, 50.0, 315.0, 76.9, 68.9 },
      { 50.0, 0.9013, 1.0176, 55.0, 320.0, 101.5, 101.6 }, { 70.0, 0.8821, 1.0226, 65.0, 330.0, 131.0, 135.1 },
      { 90.0, 0.9552, 1.0110, 75.0, 345.0, 171.0, 180.5 }, { 95.0, 0.8177, 1.0355, 75.0, 400.0, 186.5, 194.1 } };

  private RiaziDaubertDistillationConversion() {
  }

  /**
   * Converts a D86 temperature to TBP at a published recovery point.
   *
   * @param d86TemperatureC D86 temperature in degrees Celsius
   * @param recoveryVolumePercent liquid-volume percent recovered
   * @return TBP temperature in degrees Celsius
   * @throws IllegalArgumentException if the recovery point or temperature is outside the published reference domain
   */
  public static double convertD86ToTbpC(double d86TemperatureC, double recoveryVolumePercent) {
    int index = requireReferencePoint(recoveryVolumePercent);
    requireFinite(d86TemperatureC, "D86 temperature");
    requireD86TemperatureInRange(d86TemperatureC, index);

    double d86TemperatureK = d86TemperatureC + CELSIUS_TO_KELVIN;
    if (d86TemperatureK <= 0.0) {
      throw new IllegalArgumentException("D86 temperature must be above absolute zero");
    }
    double tbpTemperatureK = REFERENCE_DATA[index][1] * Math.pow(d86TemperatureK, REFERENCE_DATA[index][2]);
    if (!isFinite(tbpTemperatureK) || tbpTemperatureK <= 0.0) {
      throw new IllegalArgumentException("D86 to TBP conversion produced an invalid temperature");
    }
    return tbpTemperatureK - CELSIUS_TO_KELVIN;
  }

  /**
   * Converts a TBP temperature to D86 at a published recovery point.
   *
   * @param tbpTemperatureC TBP temperature in degrees Celsius
   * @param recoveryVolumePercent liquid-volume percent recovered
   * @return D86 temperature in degrees Celsius
   * @throws IllegalArgumentException if the recovery point is unsupported or the inverse result is outside the
   * published D86-temperature domain
   */
  public static double convertTbpToD86C(double tbpTemperatureC, double recoveryVolumePercent) {
    int index = requireReferencePoint(recoveryVolumePercent);
    requireFinite(tbpTemperatureC, "TBP temperature");

    double tbpTemperatureK = tbpTemperatureC + CELSIUS_TO_KELVIN;
    if (tbpTemperatureK <= 0.0) {
      throw new IllegalArgumentException("TBP temperature must be above absolute zero");
    }
    double d86TemperatureK = Math.pow(tbpTemperatureK / REFERENCE_DATA[index][1], 1.0 / REFERENCE_DATA[index][2]);
    if (!isFinite(d86TemperatureK) || d86TemperatureK <= 0.0) {
      throw new IllegalArgumentException("TBP to D86 conversion produced an invalid temperature");
    }
    double d86TemperatureC = d86TemperatureK - CELSIUS_TO_KELVIN;
    requireD86TemperatureInRange(d86TemperatureC, index);
    return d86TemperatureC;
  }

  /**
   * Returns whether a recovery percentage is one of the seven published reference points.
   *
   * @param recoveryVolumePercent liquid-volume percent recovered
   * @return true for 0, 10, 30, 50, 70, 90, or 95 vol%
   */
  public static boolean isSupportedRecoveryPoint(double recoveryVolumePercent) {
    if (!isFinite(recoveryVolumePercent)) {
      return false;
    }
    for (int i = 0; i < REFERENCE_DATA.length; i++) {
      if (Math.abs(recoveryVolumePercent - REFERENCE_DATA[i][0]) <= POINT_TOLERANCE) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a defensive copy of the qualified reference data.
   *
   * @return rows containing recovery vol%, a, b, minimum D86 C, maximum D86 C, example D86 C, and example TBP C
   */
  public static double[][] getReferenceData() {
    double[][] copy = new double[REFERENCE_DATA.length][];
    for (int i = 0; i < REFERENCE_DATA.length; i++) {
      copy[i] = REFERENCE_DATA[i].clone();
    }
    return copy;
  }

  /**
   * Returns the public bibliographic record for the source correlation.
   *
   * @return source URI
   */
  public static String getSourceUri() {
    return "https://www.osti.gov/biblio/5212509";
  }

  private static int requireReferencePoint(double recoveryVolumePercent) {
    if (!isFinite(recoveryVolumePercent)) {
      throw new IllegalArgumentException("Recovery volume percent must be finite");
    }
    for (int i = 0; i < REFERENCE_DATA.length; i++) {
      if (Math.abs(recoveryVolumePercent - REFERENCE_DATA[i][0]) <= POINT_TOLERANCE) {
        return i;
      }
    }
    throw new IllegalArgumentException("Unsupported recovery volume percent; use 0, 10, 30, 50, 70, 90, or 95");
  }

  private static void requireD86TemperatureInRange(double d86TemperatureC, int index) {
    double minimumC = REFERENCE_DATA[index][3];
    double maximumC = REFERENCE_DATA[index][4];
    if (d86TemperatureC < minimumC || d86TemperatureC > maximumC) {
      throw new IllegalArgumentException("D86 temperature is outside the published range at this recovery point");
    }
  }

  private static void requireFinite(double value, String name) {
    if (!isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  private static boolean isFinite(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }
}

