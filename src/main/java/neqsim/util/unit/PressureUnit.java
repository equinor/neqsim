package neqsim.util.unit;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.util.exception.InvalidInputException;

/**
 * PressureUnit class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class PressureUnit extends neqsim.util.unit.BaseUnit implements BiasAdjustedUnit {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private static final String[] ALLOWED_UNITS = { "bara", "bar", "barg", "psi", "psia", "psig", "Pa", "kPa", "MPa",
      "atm" };

  /**
   * Constructor for PressureUnit.
   *
   * @param value Pressure value
   * @param unit Engineering unit of value
   */
  public PressureUnit(double value, String unit) {
    super(value, unit);
  }

  private static final double PSI_TO_BAR = 0.0689475729317831;

  /**
   * Convert a pressure value to SI unit (Pascals).
   *
   * @param value pressure value
   * @param unit source unit (bara, barg, psi, psia, psig, Pa, kPa, MPa, atm)
   * @return value in Pascals
   * @throws RuntimeException if unit is not supported
   */
  @Override
  public double toSIvalue(double value, String unit) {
    switch (unit) {
    case "bara":
    case "bar":
      return value * 1.0e5;
    case "barg":
      return (value + ThermodynamicConstantsInterface.referencePressure) * 1.0e5;
    case "psi":
    case "psia":
      return value * PSI_TO_BAR * 1.0e5;
    case "psig":
      return (value * PSI_TO_BAR + ThermodynamicConstantsInterface.referencePressure) * 1.0e5;
    case "Pa":
      return value;
    case "kPa":
      return value * 1.0e3;
    case "MPa":
      return value * 1.0e6;
    case "atm":
      return value * ThermodynamicConstantsInterface.referencePressure * 1.0e5;
    default:
      throw new RuntimeException(new InvalidInputException(this, "toSIvalue", unit, "unit not supported"));
    }
  }

  /**
   * Convert a pressure value from SI unit (Pascals) to specified unit.
   *
   * @param siValue pressure value in Pascals
   * @param unit target unit (bara, barg, psi, psia, psig, Pa, kPa, MPa, atm)
   * @return value in specified unit
   * @throws RuntimeException if unit is not supported
   */
  @Override
  public double fromSIvalue(double siValue, String unit) {
    switch (unit) {
    case "bara":
    case "bar":
      return siValue / 1.0e5;
    case "barg":
      return siValue / 1.0e5 - ThermodynamicConstantsInterface.referencePressure;
    case "psi":
    case "psia":
      return siValue / 1.0e5 / PSI_TO_BAR;
    case "psig":
      return siValue / 1.0e5 / PSI_TO_BAR - ThermodynamicConstantsInterface.referencePressure / PSI_TO_BAR;
    case "Pa":
      return siValue;
    case "kPa":
      return siValue / 1.0e3;
    case "MPa":
      return siValue / 1.0e6;
    case "atm":
      return siValue / 1.0e5 / ThermodynamicConstantsInterface.referencePressure;
    default:
      throw new RuntimeException(new InvalidInputException(this, "fromSIvalue", unit, "unit not supported"));
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getSIUnit() {
    return "Pa";
  }

  /** {@inheritDoc} */
  @Override
  public double getSIvalue() {
    return toSIvalue(invalue, inunit);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String toUnit) {
    return fromSIvalue(getSIvalue(), toUnit);
  }

  /**
   * Convert a pressure value between supported units.
   *
   * @param value value to convert
   * @param unit source unit
   * @param toUnit target unit
   * @return converted value
   */
  public static double convert(double value, String unit, String toUnit) {
    return new PressureUnit(value, unit).getValue(toUnit);
  }
}
