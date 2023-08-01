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

  /** {@inheritDoc} */
  @Override
  public double getValue(double val, String fromunit, String tounit) {
    invalue = val;
    return getConversionFactor(fromunit) / getConversionFactor(tounit) * invalue;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String tounit) {
    if (tounit.equals("barg")) {
      return (getConversionFactor(inunit) / getConversionFactor("bara")) * invalue
          - ThermodynamicConstantsInterface.referencePressure;
    }
    if (inunit.equals("barg")) {
      return (getConversionFactor(inunit) / getConversionFactor("bara")) * invalue
          + ThermodynamicConstantsInterface.referencePressure;
    } else {
      return getConversionFactor(inunit) / getConversionFactor(tounit) * invalue;
    }
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
        conversionFactor = 0.06894757;
        break;
      case "Pa":
        conversionFactor = 1.0e-5;
        break;
      case "MPa":
        conversionFactor = 10.0;
        break;
      default:
        throw new RuntimeException(
            new InvalidInputException(this, "getConversionFactor", name, "unit not supproted"));
    }

    return conversionFactor;
  }
}
