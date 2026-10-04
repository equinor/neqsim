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

  private static final String[] ALLOWED_UNITS = {"K", "C", "F", "R"};

  /**
   * Constructor for TemperatureUnit.
   *
   * @param value a double
   * @param unit a {@link java.lang.String} object
   * @throws IllegalArgumentException if unit is not supported
   */
  public TemperatureUnit(double value, String unit) {
    super(value, unit);
    // Preserve the protected legacy snapshot for existing subclasses.
    SIvalue = toSIvalue(value, unit);
  }

  /** {@inheritDoc} */
  @Override
  public String[] getAllowedUnits() {
    return ALLOWED_UNITS.clone();
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
    Unit.validateUnitInput(unit, "unit");
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

  /** {@inheritDoc} */
  @Override
  public double fromSIvalue(double siValue, String unit) {
    Unit.validateUnitInput(unit, "unit");
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

  /** {@inheritDoc} */
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

  /**
   * Convert a signed temperature difference without Celsius or Fahrenheit offsets.
   *
   * @param value temperature difference
   * @param unit source temperature unit
   * @param toUnit target temperature unit
   * @return difference in the target unit
   * @throws IllegalArgumentException if either unit is null, blank or unsupported
   */
  public static double convertDifference(double value, String unit, String toUnit) {
    return value * differenceScale(unit) / differenceScale(toUnit);
  }

  /**
   * Return the temperature-difference multiplier to Kelvin.
   *
   * @param unit temperature unit
   * @return scale multiplier to Kelvin
   * @throws IllegalArgumentException if the unit is null, blank or unsupported
   */
  private static double differenceScale(String unit) {
    Unit.validateUnitInput(unit, "unit");
    switch (unit) {
    case "K":
    case "C":
      return 1.0;
    case "F":
    case "R":
      return 5.0 / 9.0;
    default:
      throw new IllegalArgumentException("Unsupported unit: " + unit);
    }
  }

  /**
   * Return the legacy temperature scale to Kelvin, excluding offsets.
   *
   * @param unit temperature unit
   * @return scale multiplier to Kelvin
   * @throws IllegalArgumentException if the unit is null, blank or unsupported
   * @deprecated Use {@link #convert(double, String, String)} for temperatures or
   * {@link #convertDifference(double, String, String)} for differences.
   */
  @Deprecated
  public double getConversionFactor(String unit) {
    return differenceScale(unit);
  }
}
