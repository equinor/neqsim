/*
 * LengthUnit.java
 *
 * Created on 25. januar 2002, 20:23
 */

package neqsim.util.unit;

import neqsim.util.exception.InvalidInputException;

/**
 * LengthUnit class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class LengthUnit extends neqsim.util.unit.BaseUnit implements LinearScaleUnit {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private static final String[] ALLOWED_UNITS = {"m", "meter", "metre", "cm", "mm", "km", "in", "inch", "ft", "feet"};

  /**
   * Constructor for LengthUnit.
   *
   * @param value Numeric value
   * @param unit Name of unit
   */
  public LengthUnit(double value, String unit) {
    super(value, unit);
  }

  /** {@inheritDoc} */
  @Override
  public String getSIUnit() {
    return "m";
  }

  /** {@inheritDoc} */
  @Override
  public double getConversionFactor(String unit) {
    if ("m".equals(unit) || "meter".equals(unit) || "metre".equals(unit)) {
      return 1.0;
    } else if ("cm".equals(unit)) {
      return 1.0e-2;
    } else if ("mm".equals(unit)) {
      return 1.0e-3;
    } else if ("km".equals(unit)) {
      return 1.0e3;
    } else if ("in".equals(unit) || "inch".equals(unit)) {
      return 0.0254;
    } else if ("ft".equals(unit) || "feet".equals(unit)) {
      return 0.3048;
    }

    throw new RuntimeException(new InvalidInputException(this, "getConversionFactor", unit, "unit not supported"));
  }

  /**
   * Get the SI value (meters) of the current length.
   *
   * <p>
   * Converts the stored value and unit to SI (meters). Supported input units: m, meter, metre, cm, mm, km, in, inch,
   * ft, feet.
   * </p>
   */
  @Override
  public double getSIvalue() {
    return invalue * getConversionFactor(inunit);
  }

  /**
   * Convert the current length to the specified unit.
   *
   * <p>
   * Converts the stored value from its original unit to the target unit. Supported units: m, meter, metre, cm, mm, km,
   * in, inch, ft, feet. Examples:
   * <ul>
   * <li>LengthUnit(100, "cm").getValue("m") = 1.0</li>
   * <li>LengthUnit(5, "ft").getValue("m") = 1.524</li>
   * <li>LengthUnit(1, "km").getValue("ft") = 3280.84</li>
   * </ul>
   *
   * @param toUnit target unit name (one of the supported units)
   * @return converted value in the target unit
   * @throws RuntimeException if the target unit is not supported
   */
  @Override
  public double getValue(String toUnit) {
    return getSIvalue() / getConversionFactor(toUnit);
  }

  /**
   * Convert a length value between supported units.
   *
   * <p>
   * Static convenience method for converting between any two supported units. Supported units: m, meter, metre, cm, mm,
   * km, in, inch, ft, feet. Examples:
   * <ul>
   * <li>LengthUnit.convert(100, "cm", "m") = 1.0</li>
   * <li>LengthUnit.convert(5280, "ft", "km") = 1.60934</li>
   * </ul>
   *
   * @param value value to convert
   * @param unit source unit name
   * @param toUnit target unit name
   * @return converted value
   * @throws RuntimeException if either unit is not supported
   */
  public static double convert(double value, String unit, String toUnit) {
    return new LengthUnit(value, unit).getValue(toUnit);
  }
}
