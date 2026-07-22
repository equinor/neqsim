/*
 * SurfaceTensionInterface.java
 *
 * Created on 13. august 2001, 13:14
 */

package neqsim.physicalproperties.interfaceproperties.surfacetension;

/**
 * SurfaceTensionInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface SurfaceTensionInterface {
  /**
   * Calculates the surface tension between two phases.
   *
   * @param interface1 Phase index 1
   * @param interface2 Phase index 2
   * @return Surface tension in N/m
   */
  public double calcSurfaceTension(int interface1, int interface2);
}
