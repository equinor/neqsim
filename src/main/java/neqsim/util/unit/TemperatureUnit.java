package neqsim.util.unit;

/**
 * TemperatureUnit class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class TemperatureUnit extends neqsim.util.unit.BaseUnit implements BiasAdjustedUnit {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private static final String[] ALLOWED_UNITS = { "K", "C", "F", "R" };

  /**
   * Constructor for TemperatureUnit.
   *
   * @param value a double
   * @param unit a {@link java.lang.String} object
   * @throws IllegalArgumentException if unit is not supported
   */
  public TemperatureUnit(double value, String unit) {
    super(value, unit);
  }

  /** {@inheritDoc} */
  @Override
  public String getSIUnit() {
    return "K";
  }

  /**
   * Convert a temperature value to SI unit (Kelvin).
   *
   * @param value temperature value
   * @param unit source unit (K, C, F, R)
   * @return value in Kelvin
   * @throws IllegalArgumentException if unit is not supported
   */
  @Override
  public double toSIvalue(double value, String unit) {
    switch (unit) {
    case "K":
      return value;
    case "C":
      return value + 273.15;
    case "F":
      return (value - 32) * 5.0 / 9.0 + 273.15;
    case "R":
      return value * 5.0 / 9.0;
    default:
      throw new IllegalArgumentException("Unsupported unit: " + unit);
    }
  }

  /**
   * Convert a temperature value from SI unit (Kelvin) to specified unit.
   *
   * @param siValue temperature value in Kelvin
   * @param unit target unit (K, C, F, R)
   * @return value in specified unit
   * @throws IllegalArgumentException if unit is not supported
   */
  @Override
  public double fromSIvalue(double siValue, String unit) {
    switch (unit) {
    case "K":
      return siValue;
    case "C":
      return siValue - 273.15;
    case "F":
      return (siValue - 273.15) * 9.0 / 5.0 + 32;
    case "R":
      return siValue * 9.0 / 5.0;
    default:
      throw new IllegalArgumentException("Unsupported unit: " + unit);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getSIvalue() {
    return toSIvalue(invalue, inunit);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Convert the stored value to the specified unit.
   */
  @Override
  public double getValue(String toUnit) {
    return fromSIvalue(getSIvalue(), toUnit);
  }

  /**
   * Convert a temperature value between supported units.
   *
   * @param value value to convert
   * @param unit source unit
   * @param toUnit target unit
   * @return converted value
   */
  public static double convert(double value, String unit, String toUnit) {
    return new TemperatureUnit(value, unit).getValue(toUnit);
  }
}
