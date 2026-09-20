package neqsim.util.unit;

/**
 * TemperatureUnit class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class TemperatureUnit extends neqsim.util.unit.BaseUnit {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for TemperatureUnit.
   *
   * @param value a double
   * @param name temperature unit: K, C, F or R
   * @throws IllegalArgumentException if the input unit is unsupported, null or blank
   */
  public TemperatureUnit(double value, String name) {
    super(value, name);
    Unit.validateUnitInput(name, "name");
    // Preserve the input temperature in Kelvin after removal of the three-argument API.
    switch (name) {
    case "K":
      SIvalue = value;
      break;
    case "C":
      SIvalue = value + 273.15;
      break;
    case "F":
      SIvalue = (value - 32.0) * 5.0 / 9.0 + 273.15;
      break;
    case "R":
      SIvalue = value * 5.0 / 9.0;
      break;
    default:
      throw new IllegalArgumentException("Unsupported unit: " + name);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getSIUnit() {
    return "K";
  }

  /**
   * Get conversion factor for temperature unit conversions to Kelvin. Note: This is primarily for understanding scale,
   * not for direct conversions including offsets.
   *
   * @param name a {@link java.lang.String} object representing the temperature unit
   * @return a double representing the conversion factor relative to Kelvin
   */
  public double getConversionFactor(String name) {
    switch (name) {
    case "K":
      return 1.0;
    case "C":
      return 1.0; // Same scale as Kelvin
    case "F":
      return 5.0 / 9.0; // Scale factor for Fahrenheit to Kelvin
    case "R":
      return 5.0 / 9.0; // Scale factor for Rankine to Kelvin
    default:
      throw new IllegalArgumentException("Unknown unit: " + name);
    }
  }

  /**
   * {@inheritDoc}
   *
   * Convert a given temperature value from Kelvin to a specified unit.
   */
  @Override
  public double getValue(String toUnit) {
    // convert the original value to Kelvin and reuse for subsequent conversions
    double tempInKelvin = SIvalue;

    switch (toUnit) {
    case "K":
      return tempInKelvin;
    case "C":
      return tempInKelvin - 273.15;
    case "F":
      return (tempInKelvin - 273.15) * 9.0 / 5.0 + 32;
    case "R":
      return tempInKelvin * 9.0 / 5.0;
    default:
      throw new IllegalArgumentException("Unsupported conversion unit: " + toUnit);
    }
  }
}
