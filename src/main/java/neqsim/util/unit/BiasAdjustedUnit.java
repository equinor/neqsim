/*
 * BiasAdjustedUnit.java
 *
 * Interface for units with bias/offset terms in conversions.
 */

package neqsim.util.unit;

/**
 * <p>
 * BiasAdjustedUnit interface.
 * </p>
 *
 * <p>
 * This interface defines units that use both a scale factor (gain) and a constant bias/offset term in conversions.
 * Examples: Temperature (K = °C + 273.15), Pressure with gauge distinction (absolute vs gauge). Unlike LinearScaleUnit
 * which uses a single conversion factor, this interface separates the conversion into two-step process: value → SI
 * (toSIvalue) and SI → target unit (fromSIvalue).
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface BiasAdjustedUnit extends Unit {
  /**
   * Convert a value from the specified unit to SI unit.
   *
   * <p>
   * This handles the first leg of the conversion: named unit → SI. For temperature: °C → K. For pressure: bar (abs or
   * gauge) → Pa.
   * </p>
   *
   * @param value value in the specified unit
   * @param unit source unit name (e.g., "C", "bar", "barg")
   * @return value in SI unit (Kelvin for temperature, Pascals for pressure)
   * @throws RuntimeException if the unit name is not supported
   */
  double toSIvalue(double value, String unit);

  /**
   * Convert a value from SI unit back to the specified unit.
   *
   * <p>
   * This handles the second leg of the conversion: SI → named unit. For temperature: K → °C. For pressure: Pa → bar
   * (abs or gauge).
   * </p>
   *
   * @param siValue value in SI unit (Kelvin for temperature, Pascals for pressure)
   * @param unit target unit name (e.g., "C", "bar", "barg")
   * @return value in the specified unit
   * @throws RuntimeException if the unit name is not supported
   */
  double fromSIvalue(double siValue, String unit);

  /**
   * <p>
   * Convert a value from one unit to another using two-step SI intermediate.
   * </p>
   *
   * <p>
   * Default implementation: fromSIvalue(toSIvalue(value, fromUnit), toUnit)
   * </p>
   *
   * @param value value to convert
   * @param fromUnit source unit name
   * @param toUnit target unit name
   * @deprecated Use the static convert method on the concrete unit class instead (e.g., TemperatureUnit.convert,
   * @return converted value
   */
  @Deprecated
  @Override
  default double getValue(double value, String fromUnit, String toUnit) {
    return fromSIvalue(toSIvalue(value, fromUnit), toUnit);
  }
}
