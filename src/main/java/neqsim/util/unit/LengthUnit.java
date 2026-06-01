/*
 * LengthUnit.java
 *
 * Created on 25. januar 2002, 20:23
 */

package neqsim.util.unit;

import neqsim.util.exception.InvalidInputException;

/**
 * <p>
 * LengthUnit class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class LengthUnit extends neqsim.util.unit.BaseUnit {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for LengthUnit.
   * </p>
   *
   * @param value Numeric value
   * @param name Name of unit
   */
  public LengthUnit(double value, String name) {
    super(value, name);
  }

  /**
   * Get conversion factor to meters (SI unit).
   *
   * @param name unit name
   * @return conversion factor to meters
   */
  public double getConversionFactor(String name) {
    if ("m".equals(name) || "meter".equals(name) || "metre".equals(name)) {
      return 1.0;
    } else if ("cm".equals(name)) {
      return 1.0e-2;
    } else if ("mm".equals(name)) {
      return 1.0e-3;
    } else if ("km".equals(name)) {
      return 1.0e3;
    } else if ("in".equals(name) || "inch".equals(name)) {
      return 0.0254;
    } else if ("ft".equals(name) || "feet".equals(name)) {
      return 0.3048;
    }

    throw new RuntimeException(
        new InvalidInputException(this, "getConversionFactor", name, "unit not supported"));
  }

  /** {@inheritDoc} */
  @Override
  public double getSIvalue() {
    return getValue("m");
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
