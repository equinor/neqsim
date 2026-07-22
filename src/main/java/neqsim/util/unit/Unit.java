/*
 * Unit.java
 *
 * Created on 25. januar 2002, 20:20
 */

package neqsim.util.unit;

/**
 * Unit interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface Unit {
  /**
   * getSIvalue.
   *
   * @return a double
   */
  double getSIvalue();

  /**
   * Convert value from a specified unit to a specified unit.
   *
   * @param val a double
   * @param fromunit a {@link java.lang.String} object
   * @param tounit a {@link java.lang.String} object
   * @return a double
   */
  double getValue(double val, String fromunit, String tounit);

  /**
   * Get process value in specified unit.
   *
   * @param tounit Unit to get process value in.
   * @return Value converted to the specified unit.
   */
  double getValue(String tounit);
}
