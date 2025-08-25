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
  /** Serialization version UID. */
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
    // store the temperature in Kelvin for reuse
    this.SIvalue = getValue(value, name, "K");
  }

  /**
   * Get conversion factor for temperature unit conversions to Kelvin. Note: This is primarily for
   * understanding scale, not for direct conversions including offsets.
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

  /** {@inheritDoc} */
  @Override
  public double getValue(double value, String fromUnit, String toUnit) {
    if (fromUnit.equals(toUnit)) {
      return value;
    }

    // Convert input to Kelvin first. Unknown units should not default to Kelvin.
    double tempInKelvin;
    switch (fromUnit) {
      case "K":
        tempInKelvin = value;
        break;
      case "C":
        tempInKelvin = value + 273.15;
        break;
      case "F":
        tempInKelvin = (value - 32) * 5.0 / 9.0 + 273.15;
        break;
      case "R":
        tempInKelvin = value * 5.0 / 9.0;
        break;
      default:
        throw new IllegalArgumentException("Unsupported fromUnit: " + fromUnit);
    }

    // Convert from Kelvin to target unit
    if (toUnit.equals("K")) {
      return tempInKelvin;
    } else if (toUnit.equals("C")) {
      return tempInKelvin - 273.15;
    } else if (toUnit.equals("F")) {
      return (tempInKelvin - 273.15) * 9.0 / 5.0 + 32;
    } else if (toUnit.equals("R")) {
      return tempInKelvin * 9.0 / 5.0;
    }

    throw new IllegalArgumentException("Unsupported unit: " + toUnit);
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
