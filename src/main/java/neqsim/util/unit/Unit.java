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
   * <p>
   * Convert value from a specified unit to a specified unit. This default method uses reflection to invoke the concrete
   * unit class's static convert method.
   * </p>
   *
   * @param value a double
   * @param unit a {@link java.lang.String} object
   * @param toUnit a {@link java.lang.String} object
   * @return a double
   */
  default double getValue(double value, String unit, String toUnit) {
    try {
      java.lang.reflect.Method convertMethod = this.getClass().getMethod("convert", double.class, String.class,
          String.class);
      return (double) convertMethod.invoke(null, value, unit, toUnit);
    } catch (java.lang.reflect.InvocationTargetException e) {
      // Unwrap the underlying exception from invoke()
      Throwable cause = e.getCause();
      if (cause instanceof IllegalArgumentException) {
        throw (IllegalArgumentException) cause;
      } else if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      } else {
        throw new RuntimeException("Failed to invoke convert method on " + this.getClass().getName(), cause);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to invoke convert method on " + this.getClass().getName(), e);
    }
  }
}
