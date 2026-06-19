package neqsim.util.unit;

import neqsim.util.exception.InvalidInputException;

/**
 * PowerUnit class for converting between different power units.
 *
 * @author esol
 * @version $Id: $Id
 */
public class PowerUnit extends neqsim.util.unit.BaseUnit implements LinearScaleUnit {
  private static final long serialVersionUID = 1000L;

  private static final String[] ALLOWED_UNITS = { "W", "kW", "MW", "hp", "BTU/hr" };

  /**
   * Constructor for PowerUnit.
   *
   * @param value power value
   * @param unit engineering unit of value
   */
  public PowerUnit(double value, String unit) {
    super(value, unit);
  }

  /** {@inheritDoc} */
  @Override
  public double getConversionFactor(String unit) {
    switch (unit) {
    case "W":
      return 1.0;
    case "kW":
      return 1000.0;
    case "MW":
      return 1.0e6;
    case "hp":
      return 745.699872;
    case "BTU/hr":
      return 0.29307107;
    default:
      throw new RuntimeException(new InvalidInputException(this, "getConversionFactor", unit, "unit not supported"));
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getSIUnit() {
    return "W";
  }

  /**
   * Get the SI value (Watts) of the current power.
   *
   * <p>
   * Converts the stored value and unit to SI (Watts). Supported input units: W, kW, MW, hp, BTU/hr.
   * </p>
   */
  @Override
  public double getSIvalue() {
    return invalue * getConversionFactor(inunit);
  }

  /**
   * Convert the current power to the specified unit.
   *
   * <p>
   * Converts the stored value from its original unit to the target unit. Supported units: W, kW, MW, hp, BTU/hr.
   * Examples:
   * <ul>
   * <li>PowerUnit(1000, "kW").getValue("W") = 1000000</li>
   * <li>PowerUnit(1, "MW").getValue("kW") = 1000</li>
   * <li>PowerUnit(745.7, "hp").getValue("W") ≈ 556000</li>
   * </ul>
   * </p>
   *
   * @param toUnit target unit name (one of the supported units)
   * @return converted value in the target unit
   * @throws RuntimeException if the target unit is not supported
   */
  @Override
  public double getValue(String toUnit) {
    return getSIvalue() / getConversionFactor(toUnit);
  }

  /**
   * Convert a power value between supported units.
   *
   * <p>
   * Static convenience method for converting between any two supported units. Supported units: W, kW, MW, hp, BTU/hr.
   * Examples:
   * <ul>
   * <li>PowerUnit.convert(1, "MW", "kW") = 1000</li>
   * <li>PowerUnit.convert(1000, "W", "kW") = 1</li>
   * <li>PowerUnit.convert(746, "hp", "W") ≈ 557000</li>
   * </ul>
   * </p>
   *
   * @param value value to convert
   * @param unit source unit name
   * @param toUnit target unit name
   * @return converted value
   * @throws RuntimeException if either unit is not supported
   */
  public static double convert(double value, String unit, String toUnit) {
    return new PowerUnit(value, unit).getValue(toUnit);
  }
}
