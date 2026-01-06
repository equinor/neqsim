package neqsim.process.mechanicaldesign.valve;

/**
 * Represents a valve with an equal percentage flow characteristic.
 * 
 * <p>
 * For equal percentage valves, equal increments of valve travel produce equal percentage changes in
 * the existing flow coefficient. This provides a logarithmic relationship between stem position and
 * flow, making it ideal for processes where the pressure drop varies significantly with flow rate.
 * </p>
 * 
 * <p>
 * The characteristic follows the equation:
 * </p>
 * 
 * <pre>
 * Cv = Cv_max * R ^ (x - 1)
 * </pre>
 * 
 * <p>
 * where:
 * </p>
 * <ul>
 * <li>R = rangeability (typically 50:1, meaning Cv_max/Cv_min = 50)</li>
 * <li>x = fractional valve opening (0 to 1)</li>
 * </ul>
 * 
 * <p>
 * Equal percentage valves are the most commonly used characteristic for process control because
 * they provide consistent control loop gain across a wide range of operating conditions.
 * </p>
 *
 * @author esol
 * @version 1.0
 * @see ValveCharacteristic
 * @see LinearCharacteristic
 */
public class EqualPercentageCharacteristic implements ValveCharacteristic {

  private static final long serialVersionUID = 1000L;

  /**
   * The rangeability of the valve, defined as Cv_max / Cv_min. Typical values are 50:1 for standard
   * valves and up to 100:1 for high-performance valves.
   */
  private double rangeability = 50.0;

  /**
   * Default constructor with standard rangeability of 50:1.
   */
  public EqualPercentageCharacteristic() {
    this.rangeability = 50.0;
  }

  /**
   * Constructor with custom rangeability.
   *
   * @param rangeability the rangeability ratio (Cv_max/Cv_min), typically 30 to 100
   */
  public EqualPercentageCharacteristic(double rangeability) {
    if (rangeability <= 1.0) {
      throw new IllegalArgumentException("Rangeability must be greater than 1.0");
    }
    this.rangeability = rangeability;
  }

  /**
   * {@inheritDoc}
   * 
   * <p>
   * Calculates the actual Kv based on equal percentage characteristic.
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
   * Returns the opening factor for equal percentage characteristic using the formula:
   * </p>
   * 
   * <pre>
   * factor = R ^ (x - 1)
   * </pre>
   * 
   * <p>
   * where R is the rangeability and x is the fractional opening (0 to 1).
   * </p>
   */
  @Override
  public double getOpeningFactor(double percentOpening) {
    // Clamp to valid range
    double opening = Math.max(0.0, Math.min(100.0, percentOpening));

    // Handle edge cases
    if (opening <= 0.0) {
      return 1.0 / rangeability; // Minimum flow at closed position
    }
    if (opening >= 100.0) {
      return 1.0; // Maximum flow at fully open
    }

    // Convert to fractional opening (0 to 1)
    double x = opening / 100.0;

    // Equal percentage formula: R^(x-1)
    return Math.pow(rangeability, x - 1.0);
  }

  /**
   * Gets the rangeability of the valve.
   *
   * @return the rangeability ratio
   */
  public double getRangeability() {
    return rangeability;
  }

  /**
   * Sets the rangeability of the valve.
   *
   * @param rangeability the rangeability ratio (must be greater than 1.0)
   */
  public void setRangeability(double rangeability) {
    if (rangeability <= 1.0) {
      throw new IllegalArgumentException("Rangeability must be greater than 1.0");
    }
    this.rangeability = rangeability;
  }
}
