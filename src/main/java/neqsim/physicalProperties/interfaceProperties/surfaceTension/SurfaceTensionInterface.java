/*
 * SurfaceTensionInterface.java
 *
 * Created on 13. august 2001, 13:14
 */

package neqsim.physicalProperties.interfaceProperties.surfaceTension;

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
   * calcSurfaceTension. Calculates the surfacetension.
   * </p>
   *
   * @param interface1 Phase index 1
   * @param interface2 Phase index 2
   * @return Surface tension in N/m
   */
  public double calcSurfaceTension(int interface1, int interface2);
}
