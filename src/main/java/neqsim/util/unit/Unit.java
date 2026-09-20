/*
 * Unit.java
 *
 * Created on 25. januar 2002, 20:20
 */

package neqsim.util.unit;

import neqsim.util.exception.InvalidInputException;

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
   * Units accepted by this implementation, or {@code null} to accept any unit.
   *
   * @return array of allowed unit names, or {@code null} for no restriction
   */
  public String[] getAllowedUnits() throws UnsupportedOperationException;

  /**
   * Validate that a unit name is one of the units supported by the calling implementation.
   *
   * @param unit the unit name to validate
   * @throws IllegalArgumentException if the unit is not supported
   */
  public default void validateAllowedUnit(String unit) {
    String[] allowedUnits = getAllowedUnits();
    if (allowedUnits == null) {
      return;
    }
    for (String allowed : allowedUnits) {
      if (allowed.equals(unit)) {
        return;
      }
    }
    throw new IllegalArgumentException(
        new InvalidInputException(this, "validateAllowedUnit", unit, "unit not supported"));
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
}
