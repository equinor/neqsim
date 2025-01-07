/*
 * TimeUnit.java
 *
 * Created on 25. januar 2002, 20:23
 */

package neqsim.util.unit;

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
}
