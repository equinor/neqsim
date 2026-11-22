package neqsim.process.automation;

import java.util.List;
import java.util.Locale;

/**
 * Implemented by process units that publish their own variable addresses to {@link ProcessAutomation}, for equipment
 * that is not a stream-based unit operation (a compressor shaft controller, a platform power system).
 *
 * <p>
 * The unit lists its variables with {@link #getAutomationVariables(String)}; an address has the form
 * {@code unitName.property}, where the property may itself contain dots (for example
 * {@code "HP shaft.member.K-201.speed"} has the property {@code member.K-201.speed}). Reading and writing go through
 * {@link #getAutomationValue(String, String)} and {@link #setAutomationValue(String, double, String)}; a setter that
 * rejects a value (out of range, unknown member, wrong flag) throws {@link IllegalArgumentException}, which
 * {@link ProcessAutomation#setVariableValueSafe(String, double, String)} turns into a diagnostic JSON result.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public interface AutomationAddressable {

  /**
   * List the variables of this unit.
   *
   * @param unitAddress address prefix of the unit, including an area prefix when the model has areas; every returned
   * address must start with this prefix followed by a dot
   * @return the variable descriptors
   */
  List<SimulationVariable> getAutomationVariables(String unitAddress);

  /**
   * Read a variable.
   *
   * @param property the property part of the address (everything after {@code unitName.})
   * @param unitOfMeasure the requested unit, or null or empty for the default unit of the variable
   * @return the value
   * @throws IllegalArgumentException if the property is unknown or the unit is not supported
   */
  double getAutomationValue(String property, String unitOfMeasure);

  /**
   * Write an input variable with the guards of the unit.
   *
   * @param property the property part of the address
   * @param value the new value
   * @param unitOfMeasure the unit of the value, or null or empty for the default unit of the variable
   * @throws IllegalArgumentException if the property is unknown or read-only, the unit is not supported or the value is
   * rejected
   */
  void setAutomationValue(String property, double value, String unitOfMeasure);

  /**
   * Convert a power in W to the requested unit.
   *
   * @param watts power in W
   * @param unit "W", "kW" or "MW"; null or empty means kW
   * @return the power in the unit
   * @throws IllegalArgumentException if the unit is not supported
   */
  static double powerFromWatts(double watts, String unit) {
    return watts / powerFactor(unit);
  }

  /**
   * Convert a power in the given unit to W.
   *
   * @param value power in {@code unit}
   * @param unit "W", "kW" or "MW"; null or empty means kW
   * @return the power in W
   * @throws IllegalArgumentException if the unit is not supported
   */
  static double powerToWatts(double value, String unit) {
    return value * powerFactor(unit);
  }

  /**
   * Convert a temperature in K to the requested unit.
   *
   * @param kelvin temperature in K
   * @param unit "K" or "C"; null or empty means C
   * @return the temperature in the unit
   * @throws IllegalArgumentException if the unit is not supported
   */
  static double temperatureFromKelvin(double kelvin, String unit) {
    return isKelvin(unit) ? kelvin : kelvin - 273.15;
  }

  /**
   * Convert a temperature in the given unit to K.
   *
   * @param value temperature in {@code unit}
   * @param unit "K" or "C"; null or empty means C
   * @return the temperature in K
   * @throws IllegalArgumentException if the unit is not supported
   */
  static double temperatureToKelvin(double value, String unit) {
    return isKelvin(unit) ? value : value + 273.15;
  }

  /**
   * Whether a temperature unit is Kelvin.
   *
   * @param unit "K", "C" or null or empty (C)
   * @return true for K
   * @throws IllegalArgumentException if the unit is neither K nor C
   */
  static boolean isKelvin(String unit) {
    if (unit == null || unit.trim().isEmpty() || "C".equalsIgnoreCase(unit.trim())) {
      return false;
    }
    if ("K".equalsIgnoreCase(unit.trim())) {
      return true;
    }
    throw new IllegalArgumentException("unsupported temperature unit '" + unit + "' (use C or K).");
  }

  /**
   * Watts per power unit.
   *
   * @param unit "W", "kW" or "MW"; null or empty means kW
   * @return watts per unit
   * @throws IllegalArgumentException if the unit is not supported
   */
  static double powerFactor(String unit) {
    if (unit == null || unit.trim().isEmpty()) {
      return 1.0e3;
    }
    String u = unit.trim().toLowerCase(Locale.ROOT);
    if ("w".equals(u)) {
      return 1.0;
    } else if ("kw".equals(u)) {
      return 1.0e3;
    } else if ("mw".equals(u)) {
      return 1.0e6;
    }
    throw new IllegalArgumentException("unsupported power unit '" + unit + "' (use W, kW or MW).");
  }
}
