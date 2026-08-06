/*
 * LinearScaleUnit.java
 *
 * Interface for units that scale linearly with a gain factor only.
 */

package neqsim.util.unit;

/**
 * <p>
 * LinearScaleUnit interface.
 * </p>
 *
 * <p>
 * This interface defines units that use only a linear scale factor (gain) for conversion, with no bias or offset term.
 * Examples: Length, Energy, Power, Time. Non-examples: Temperature (has offset), Pressure with gauge distinction (has
 * bias).
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface LinearScaleUnit extends Unit {
  /**
   * Get the conversion factor from a named unit to SI unit.
   *
   * <p>
   * Returns the conversion factor used to convert between units. The conversion formulas are:
   * <ul>
   * <li>To SI unit: <code>si_value = value * getConversionFactor(unit)</code></li>
   * <li>From SI unit: <code>value = si_value / getConversionFactor(unit)</code></li>
   * <li>Between any two units:
   * <code>value_target = value_source * getConversionFactor(source) / getConversionFactor(target)</code></li>
   * </ul>
   *
   * <p>
   * For example, if this implementation is LengthUnit with getSIUnit() = "m" (meters):
   * <ul>
   * <li>getConversionFactor("m") returns 1.0 → To SI: 5m * 1.0 = 5 m SI; From SI: 5 m / 1.0 = 5 m</li>
   * <li>getConversionFactor("cm") returns 0.01 → To SI: 500 cm * 0.01 = 5 m SI; From SI: 5 m / 0.01 = 500 cm</li>
   * <li>getConversionFactor("km") returns 1000.0 → To SI: 0.005 km * 1000.0 = 5 m SI; From SI: 5 m / 1000.0 = 0.005
   * km</li>
   * <li>cm to km: 500 cm * 0.01 / 1000.0 = 0.005 km</li>
   * </ul>
   *
   * @param unit unit name (the exact unit names supported depend on the implementing class)
   * @return conversion factor from the named unit to SI unit (always positive)
   * @throws RuntimeException if the unit name is not supported by this implementation
   */
  double getConversionFactor(String unit);

  /**
   * <p>
   * Convert a value from one unit to another using linear scale factors.
   * </p>
   *
   * <p>
   * Default implementation: value * getConversionFactor(fromUnit) / getConversionFactor(toUnit)
   * </p>
   *
   * @param value value to convert
   * @param fromUnit source unit
   * @param toUnit target unit
   * @return converted value
   */
  @Override
  default double getValue(double value, String fromUnit, String toUnit) {
    return value * getConversionFactor(fromUnit) / getConversionFactor(toUnit);
  }
}
