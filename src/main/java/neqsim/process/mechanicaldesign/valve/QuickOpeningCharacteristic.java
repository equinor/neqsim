package neqsim.process.mechanicaldesign.valve;

/**
 * Represents a valve with a quick opening flow characteristic.
 * 
 * <p>
 * Quick opening valves provide a large change in flow for a small initial valve opening, with the
 * flow rate approaching maximum at relatively low travel. This provides a high gain at low openings
 * and low gain at high openings.
 * </p>
 * 
 * <p>
 * The characteristic follows the equation:
 * </p>
 * 
 * <pre>
 * Cv = Cv_max * sqrt(x)
 * </pre>
 * 
 * <p>
 * where x is the fractional valve opening (0 to 1).
 * </p>
 * 
 * <p>
 * Quick opening valves are typically used for:
 * </p>
 * <ul>
 * <li>On/off control applications</li>
 * <li>Safety and relief systems</li>
 * <li>Applications requiring maximum flow quickly</li>
 * <li>Surge control systems</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 * @see ValveCharacteristic
 * @see LinearCharacteristic
 */
public class QuickOpeningCharacteristic implements ValveCharacteristic {

  private static final long serialVersionUID = 1000L;

  /**
   * Default constructor.
   */
  public QuickOpeningCharacteristic() {}

  /**
   * {@inheritDoc}
   * 
   * <p>
   * Calculates the actual Kv based on quick opening characteristic.
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
   * Returns the opening factor for quick opening characteristic using the formula:
   * </p>
   * 
   * <pre>
   * factor = sqrt(x)
   * </pre>
   * 
   * <p>
   * where x is the fractional opening (0 to 1).
   * </p>
   */
  @Override
  public double getOpeningFactor(double percentOpening) {
    // Clamp to valid range
    double opening = Math.max(0.0, Math.min(100.0, percentOpening));

    // Convert to fractional opening (0 to 1)
    double x = opening / 100.0;

    // Quick opening formula: sqrt(x)
    return Math.sqrt(x);
  }
}
