package neqsim.util.unit;

import neqsim.util.exception.InvalidInputException;

/**
 * EnergyUnit class for converting between different energy units.
 *
 * @author esol
 * @version $Id: $Id
 */
public class EnergyUnit extends neqsim.util.unit.BaseUnit implements LinearScaleUnit {
  private static final long serialVersionUID = 1000L;

  /**
   * Constructor for EnergyUnit.
   *
   * @param value energy value
   * @param unit engineering unit of value
   */
  public EnergyUnit(double value, String unit) {
    super(value, unit);
  }

  /** {@inheritDoc} */
  @Override
  public String[] getAllowedUnits() {
    return new String[] {"J", "kJ", "MJ", "Wh", "kWh", "MWh", "BTU", "kcal"};
  }

  /** {@inheritDoc} */
  @Override
  public String getSIUnit() {
    return "J";
  }

  /** {@inheritDoc} */
  @Override
  public double getConversionFactor(String unit) {
    switch (unit) {
    case "J":
      return 1.0;
    case "kJ":
      return 1000.0;
    case "MJ":
      return 1.0e6;
    case "Wh":
      return 3600.0;
    case "kWh":
      return 3.6e6;
    case "MWh":
      return 3.6e9;
    case "BTU":
      return 1055.05585;
    case "kcal":
      return 4184.0;
    default:
      throw new RuntimeException(new InvalidInputException(this, "getConversionFactor", unit, "unit not supported"));
    }
  }

  /**
   * Get the SI value (Joules) of the current energy.
   *
   * <p>
   * Converts the stored value and unit to SI (Joules). Supported input units: J, kJ, MJ, Wh, kWh, MWh, BTU, kcal.
   * </p>
   */
  @Override
  public double getSIvalue() {
    return invalue * getConversionFactor(inunit);
  }

  /**
   * Convert an energy value between supported units.
   *
   * <p>
   * Static convenience method for converting between any two supported units. Supported units: J, kJ, MJ, Wh, kWh, MWh,
   * BTU, kcal. Examples:
   * <ul>
   * <li>EnergyUnit.convert(1, "MWh", "kWh") = 1000</li>
   * <li>EnergyUnit.convert(3600, "Wh", "MJ") = 12.96</li>
   * <li>EnergyUnit.convert(4184, "kcal", "J") = 17513056</li>
   * </ul>
   *
   * @param value value to convert
   * @param unit source unit name
   * @param toUnit target unit name
   * @return converted value
   * @throws RuntimeException if either unit is not supported
   */
  public static double convert(double value, String unit, String toUnit) {
    return new EnergyUnit(value, unit).getValue(toUnit);
  }
}
