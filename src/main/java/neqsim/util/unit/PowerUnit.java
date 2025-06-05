package neqsim.util.unit;

import neqsim.util.exception.InvalidInputException;

/**
 * <p>
 * PowerUnit class for converting between different power units.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PowerUnit extends neqsim.util.unit.BaseUnit {
  private static final long serialVersionUID = 1000L;

  /**
   * Constructor for PowerUnit.
   *
   * @param value power value
   * @param unit engineering unit of value
   */
  public PowerUnit(double value, String unit) {
    super(value, unit);
  }

  /**
   * Get conversion factor to Watts (SI unit).
   *
   * @param name unit name
   * @return conversion factor to Watts
   */
  public double getConversionFactor(String name) {
    switch (name) {
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
