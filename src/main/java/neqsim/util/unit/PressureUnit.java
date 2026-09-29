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

  private static final String[] ALLOWED_UNITS = {"bara", "bar", "barg", "psi", "psia", "psig", "Pa", "kPa", "MPa",
      "atm"};

  private static final double PSI_TO_BAR = 0.0689475729317831;

  /**
   * Constructor for PressureUnit.
   *
   * @param value Pressure value
   * @param unit Engineering unit of value
   */
  public PressureUnit(double value, String unit) {
    super(value, unit);
  }

  /** {@inheritDoc} */
  @Override
  public String[] getAllowedUnits() {
    return ALLOWED_UNITS.clone();
  }

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
    Unit.validateUnitInput(unit, "unit");
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
      throw new IllegalArgumentException(new InvalidInputException(this, "toSIvalue", unit, "unit not supported"));
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
    Unit.validateUnitInput(unit, "unit");
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
      throw new IllegalArgumentException(new InvalidInputException(this, "fromSIvalue", unit, "unit not supported"));
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

  /**
   * Convert a signed pressure difference without applying atmospheric offsets.
   *
   * Gauge aliases use the corresponding absolute scale. Zero remains exactly zero; negative differences are retained.
   * This method does not clamp pressure drops or validate absolute-pressure feasibility.
   *
   * @param value pressure difference
   * @param unit source pressure unit
   * @param toUnit target pressure unit
   * @return difference in the target unit
   * @throws IllegalArgumentException if either unit is null, blank or unsupported
   */
  public static double convertDifference(double value, String unit, String toUnit) {
    String source = "barg".equals(unit) ? "bar" : "psig".equals(unit) ? "psi" : unit;
    String target = "barg".equals(toUnit) ? "bar" : "psig".equals(toUnit) ? "psi" : toUnit;
    return convert(value, source, target);
  }

  /**
   * Return the legacy pressure scale to bar, ignoring gauge offsets.
   *
   * @param unit source pressure unit
   * @return multiplier to bar, not to SI Pascals
   * @throws IllegalArgumentException if the unit is null, blank or unsupported
   * @deprecated Use {@link #convert(double, String, String)} for pressures or
   * {@link #convertDifference(double, String, String)} for differences.
   */
  @Deprecated
  public double getConversionFactor(String unit) {
    return convertDifference(1.0, unit, "bar");
  }
}
