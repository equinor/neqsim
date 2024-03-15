package neqsim.util.unit;

/**
 * <p>
 * TemperatureUnit class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class TemperatureUnit extends neqsim.util.unit.BaseUnit {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for TemperatureUnit.
   * </p>
   *
   * @param value a double
   * @param name a {@link java.lang.String} object
   */
  public TemperatureUnit(double value, String name) {
    super(value, name);
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
    if (tounit.equals("C")) {
      return getConversionFactor(inunit) / getConversionFactor("K") * invalue - 273.15;
    }
    return getConversionFactor(inunit) / getConversionFactor(tounit) * invalue;
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
      case "K":
        conversionFactor = 1.0;
        break;
      case "R":
        conversionFactor = 5.0 / 9.0;
        break;
    }
    return conversionFactor;
  }
}
