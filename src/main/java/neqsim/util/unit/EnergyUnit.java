package neqsim.util.unit;

import neqsim.util.exception.InvalidInputException;

/**
 * <p>
 * EnergyUnit class for converting between different energy units.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class EnergyUnit extends neqsim.util.unit.BaseUnit {
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

  /**
   * Get conversion factor to Joules (SI unit).
   *
   * @param name unit name
   * @return conversion factor to Joules
   */
  public double getConversionFactor(String name) {
    switch (name) {
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
        throw new RuntimeException(
            new InvalidInputException(this, "getConversionFactor", name, "unit not supported"));
    }
  }

  @Override
  public double getValue(double val, String fromunit, String tounit) {
    invalue = val;
    return getConversionFactor(fromunit) / getConversionFactor(tounit) * invalue;
  }

  @Override
  public double getValue(String tounit) {
    return getConversionFactor(inunit) / getConversionFactor(tounit) * invalue;
  }
}
