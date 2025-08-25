package neqsim.util.unit;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.util.exception.InvalidInputException;

/**
 * <p>
 * PressureUnit class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PressureUnit extends neqsim.util.unit.BaseUnit {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for PressureUnit.
   * </p>
   *
   * @param value Pressure value
   * @param unit Engineering unit of value
   */
  public PressureUnit(double value, String unit) {
    super(value, unit);
  }

  /**
   * <p>
   * getConversionFactor.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a double
   */
  public double getConversionFactor(String name) {
    double conversionFactor = 1.0;
    switch (name) {
      case "bara":
        conversionFactor = 1.0;
        break;
      case "bar":
        conversionFactor = 1.0;
        break;
      case "barg":
        conversionFactor = 1.0;
        break;
      case "psi":
        conversionFactor = 0.0689475729317831;
        break;
      case "psia":
        conversionFactor = 0.0689475729317831;
        break;
      case "psig":
        conversionFactor = 0.0689475729317831;
        break;
      case "Pa":
        conversionFactor = 1.0e-5;
        break;
      case "kPa":
        conversionFactor = 1.0e-2;
        break;
      case "MPa":
        conversionFactor = 10.0;
        break;
      case "atm":
        conversionFactor = ThermodynamicConstantsInterface.referencePressure;
        break;
      default:
        throw new RuntimeException(
            new InvalidInputException(this, "getConversionFactor", name, "unit not supproted"));
    }

    return conversionFactor;
  }

  private double toAbsoluteBar(double value, String unit) {
    switch (unit) {
      case "bara":
      case "bar":
        return value;
      case "barg":
        return value + ThermodynamicConstantsInterface.referencePressure;
      case "psi":
      case "psia":
        return value * getConversionFactor("psi");
      case "psig":
        return value * getConversionFactor("psi")
            + ThermodynamicConstantsInterface.referencePressure;
      case "atm":
        return value * ThermodynamicConstantsInterface.referencePressure;
      default:
        return value * getConversionFactor(unit);
    }
  }

  private double fromAbsoluteBar(double value, String unit) {
    switch (unit) {
      case "bara":
      case "bar":
        return value;
      case "barg":
        return value - ThermodynamicConstantsInterface.referencePressure;
      case "psi":
      case "psia":
        return value / getConversionFactor("psi");
      case "psig":
        return value / getConversionFactor("psi")
            - ThermodynamicConstantsInterface.referencePressure / getConversionFactor("psi");
      case "atm":
        return value / ThermodynamicConstantsInterface.referencePressure;
      default:
        return value / getConversionFactor(unit);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(double val, String fromunit, String tounit) {
    double absBar = toAbsoluteBar(val, fromunit);
    return fromAbsoluteBar(absBar, tounit);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String tounit) {
    double absBar = toAbsoluteBar(invalue, inunit);
    return fromAbsoluteBar(absBar, tounit);
  }
}
