/*
 * Unit.java
 *
 * Created on 25. januar 2002, 20:20
 */

package neqsim.util.unit;

/**
 * Unit interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface Unit {
  /**
   * Validate a unit string input.
   *
   * @param unit unit string to validate
   * @param parameterName parameter name used in error messages
   * @throws IllegalArgumentException if the unit string is null or blank
   */
  static void validateUnitInput(String unit, String parameterName) {
    if (unit == null) {
      throw new IllegalArgumentException("Unit parameter '" + parameterName + "' cannot be null");
    }
    if (unit.trim().isEmpty()) {
      throw new IllegalArgumentException("Unit parameter '" + parameterName + "' cannot be blank");
    }
  }

  /**
   * <p>
   * Get the value in SI units.
   * </p>
   *
   * @return a double
   */
  double getSIvalue();

  /**
   * <p>
   * Get the SI unit symbol.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  String getSIUnit();

  /**
   * Get process value in specified unit.
   *
   * @param toUnit Unit to get process value in.
   * @return Value converted to the specified unit.
   */
  double getValue(String toUnit);

  /**
   * Convert process value between specified units.
   *
   * @param fromUnit Unit to convert from.
   * @param toUnit Unit to convert to.
   * @param value Value to convert.
   * @return Value converted to the specified unit.
   */
  double getValue(double value, String fromUnit, String toUnit);
}
