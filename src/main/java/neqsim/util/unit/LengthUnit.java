/*
 * LengthUnit.java
 *
 * Created on 25. januar 2002, 20:23
 */

package neqsim.util.unit;

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
}
