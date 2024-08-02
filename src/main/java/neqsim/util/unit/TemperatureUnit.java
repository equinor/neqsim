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

    // Convert input to Kelvin first
    double tempInKelvin = value;
    if (fromUnit.equals("C")) {
      tempInKelvin += 273.15;
    } else if (fromUnit.equals("F")) {
      tempInKelvin = (value - 32) * 5.0 / 9.0 + 273.15;
    } else if (fromUnit.equals("R")) {
      tempInKelvin = value * 5.0 / 9.0;
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
    switch (toUnit) {
      case "K":
        // Convert from Kelvin to Kelvin
        return invalue;
      case "C":
        // Convert from Kelvin to Celsius
        return invalue - 273.15;
      case "F":
        // Convert from Kelvin to Fahrenheit
        return invalue * 9.0 / 5.0 - 459.67;
      case "R":
        // Convert from Kelvin to Rankine
        return invalue * 9.0 / 5.0;
      default:
        // Handle unsupported units
        throw new IllegalArgumentException("Unsupported conversion unit: " + toUnit);
    }
  }
}
