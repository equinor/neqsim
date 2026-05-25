/*
 * TimeUnit.java
 *
 * Created on 25. januar 2002, 20:23
 */

package neqsim.util.unit;

import neqsim.util.exception.InvalidInputException;

/**
 * <p>
 * TimeUnit class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class TimeUnit extends neqsim.util.unit.BaseUnit {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for TimeUnit.
   * </p>
   *
   * @param value Numeric value
   * @param name Name of unit
   */
  public TimeUnit(double value, String name) {
    super(value, name);
  }

  /**
   * Get conversion factor to seconds (SI unit).
   *
   * @param name unit name
   * @return conversion factor to seconds
   */
  public double getConversionFactor(String name) {
    if ("s".equals(name) || "sec".equals(name) || "second".equals(name)) {
      return 1.0;
    } else if ("min".equals(name) || "minute".equals(name)) {
      return 60.0;
    } else if ("h".equals(name) || "hr".equals(name) || "hour".equals(name)) {
      return 3600.0;
    } else if ("d".equals(name) || "day".equals(name)) {
      return 86400.0;
    }

    throw new RuntimeException(
        new InvalidInputException(this, "getConversionFactor", name, "unit not supported"));
  }

  /** {@inheritDoc} */
  @Override
  public double getSIvalue() {
    return getValue("sec");
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(double val, String fromunit, String tounit) {
    return getConversionFactor(fromunit) / getConversionFactor(tounit) * val;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String tounit) {
    return getValue(invalue, inunit, tounit);
  }
}
