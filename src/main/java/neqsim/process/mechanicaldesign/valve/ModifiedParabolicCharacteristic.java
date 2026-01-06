package neqsim.process.mechanicaldesign.valve;

/**
 * Represents a valve with a modified parabolic (or parabolic) flow characteristic.
 * 
 * <p>
 * Modified parabolic valves provide a characteristic that falls between linear and equal
 * percentage. At low openings, it behaves more like equal percentage, and at high openings, it
 * approaches linear behavior.
 * </p>
 * 
 * <p>
 * The characteristic follows the equation:
 * </p>
 * 
 * <pre>
 * Cv = Cv_max * x ^ n
 * </pre>
 * 
 * <p>
 * where:
 * </p>
 * <ul>
 * <li>x = fractional valve opening (0 to 1)</li>
 * <li>n = exponent (default 2.0 for parabolic, can be adjusted)</li>
 * </ul>
 * 
 * <p>
 * Modified parabolic valves are used when:
 * </p>
 * <ul>
 * <li>A compromise between linear and equal percentage is needed</li>
 * <li>The process has moderate pressure drop variations</li>
 * <li>Butterfly valves (which have inherent parabolic characteristics)</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 * @see ValveCharacteristic
 * @see LinearCharacteristic
 */
public class ModifiedParabolicCharacteristic implements ValveCharacteristic {

  private static final long serialVersionUID = 1000L;

  /**
   * The exponent for the parabolic curve. Default is 2.0 for standard parabolic. Values between 1.5
   * and 2.5 are common for modified parabolic characteristics.
   */
  private double exponent = 2.0;

  /**
   * Default constructor with standard parabolic exponent of 2.0.
   */
  public ModifiedParabolicCharacteristic() {
    this.exponent = 2.0;
  }

  /**
   * Constructor with custom exponent.
   *
   * @param exponent the exponent for the parabolic curve (typically 1.5 to 2.5)
   */
  public ModifiedParabolicCharacteristic(double exponent) {
    if (exponent <= 0.0) {
      throw new IllegalArgumentException("Exponent must be positive");
    }
    this.exponent = exponent;
  }

  /**
   * {@inheritDoc}
   * 
   * <p>
   * Calculates the actual Kv based on modified parabolic characteristic.
   * </p>
   */
  @Override
  public double getActualKv(double Kv, double percentOpening) {
    return Kv * getOpeningFactor(percentOpening);
  }

  /**
   * {@inheritDoc}
   * 
   * <p>
   * Returns the opening factor for modified parabolic characteristic using the formula:
   * </p>
   * 
   * <pre>
   * factor = x ^ n
   * </pre>
   * 
   * <p>
   * where x is the fractional opening (0 to 1) and n is the exponent.
   * </p>
   */
  @Override
  public double getOpeningFactor(double percentOpening) {
    // Clamp to valid range
    double opening = Math.max(0.0, Math.min(100.0, percentOpening));

    // Convert to fractional opening (0 to 1)
    double x = opening / 100.0;

    // Modified parabolic formula: x^n
    return Math.pow(x, exponent);
  }

  /**
   * Gets the exponent of the parabolic curve.
   *
   * @return the exponent value
   */
  public double getExponent() {
    return exponent;
  }

  /**
   * Sets the exponent of the parabolic curve.
   *
   * @param exponent the exponent value (must be positive)
   */
  public void setExponent(double exponent) {
    if (exponent <= 0.0) {
      throw new IllegalArgumentException("Exponent must be positive");
    }
    this.exponent = exponent;
  }
}
