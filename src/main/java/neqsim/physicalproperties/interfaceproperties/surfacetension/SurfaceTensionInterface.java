/*
 * SurfaceTensionInterface.java
 *
 * Created on 13. august 2001, 13:14
 */

package neqsim.physicalproperties.interfaceproperties.surfacetension;

/**
 * <p>
 * SurfaceTensionInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface SurfaceTensionInterface {
  /**
   * <p>
   * Calculates the surface tension between two phases.
   * </p>
   *
   * @param interface1 Phase index 1
   * @param interface2 Phase index 2
   * @return Surface tension in N/m
   */
  public double calcSurfaceTension(int interface1, int interface2);
}
