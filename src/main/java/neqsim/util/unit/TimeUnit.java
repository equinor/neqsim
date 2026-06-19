/*
 * TimeUnit.java
 *
 * Created on 25. januar 2002, 20:23
 */

package neqsim.util.unit;

import neqsim.util.exception.InvalidInputException;

/**
 * TimeUnit class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class TimeUnit extends neqsim.util.unit.BaseUnit implements LinearScaleUnit {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private static final String[] ALLOWED_UNITS = { "s", "sec", "second", "min", "minute", "h", "hr", "hour", "d",
      "day" };

  /**
   * Constructor for TimeUnit.
   *
   * @param value Numeric value
   * @param unit Name of unit
   */
  public TimeUnit(double value, String unit) {
    super(value, unit);
  }

  /** {@inheritDoc} */
  @Override
  public String getSIUnit() {
    return "s";
  }

  /** {@inheritDoc} */
  @Override
  public double getConversionFactor(String unit) {
    if ("s".equals(unit) || "sec".equals(unit) || "second".equals(unit)) {
      return 1.0;
    } else if ("min".equals(unit) || "minute".equals(unit)) {
      return 60.0;
    } else if ("h".equals(unit) || "hr".equals(unit) || "hour".equals(unit)) {
      return 3600.0;
    } else if ("d".equals(unit) || "day".equals(unit)) {
      return 86400.0;
    }

    throw new RuntimeException(new InvalidInputException(this, "getConversionFactor", unit, "unit not supported"));
  }

  /**
   * Get the SI value (seconds) of the current time.
   *
   * <p>
   * Converts the stored value and unit to SI (seconds). Supported input units: s, sec, second, min, minute, h, hr,
   * hour, d, day.
   * </p>
   */
  @Override
  public double getSIvalue() {
    return invalue * getConversionFactor(inunit);
  }

  /**
   * Convert the current time to the specified unit.
   *
   * <p>
   * Converts the stored value from its original unit to the target unit. Supported units: s, sec, second, min, minute,
   * h, hr, hour, d, day. Examples:
   * <ul>
   * <li>TimeUnit(60, "min").getValue("s") = 3600</li>
   * <li>TimeUnit(24, "h").getValue("d") = 1.0</li>
   * <li>TimeUnit(1, "d").getValue("s") = 86400</li>
   * </ul>
   * </p>
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
   * Convert a time value between supported units.
   *
   * <p>
   * Static convenience method for converting between any two supported units. Supported units: s, sec, second, min,
   * minute, h, hr, hour, d, day. Examples:
   * <ul>
   * <li>TimeUnit.convert(60, "min", "h") = 1.0</li>
   * <li>TimeUnit.convert(2, "h", "min") = 120</li>
   * <li>TimeUnit.convert(1, "d", "s") = 86400</li>
   * </ul>
   * </p>
   *
   * @param value value to convert
   * @param unit source unit name
   * @param toUnit target unit name
   * @return converted value
   * @throws RuntimeException if either unit is not supported
   */
  public static double convert(double value, String unit, String toUnit) {
    return new TimeUnit(value, unit).getValue(toUnit);
  }

}
