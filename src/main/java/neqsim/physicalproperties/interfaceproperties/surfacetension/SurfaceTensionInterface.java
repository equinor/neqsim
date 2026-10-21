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
   * Calculates the surface tension between two phases. Density-profile models log numerical failure before using
   * Parachor for a gas interface or Firozabadi-Ramley for a liquid-liquid interface. A failed fallback raises an
   * exception.
   *
   * @param interface1 Phase index 1
   * @param interface2 Phase index 2
   * @return finite, nonnegative surface tension in N/m; zero when the system has no interface
   * @throws IllegalArgumentException if two active phase indices are invalid or identical
   * @throws IllegalStateException if the bulk state is invalid or neither the model nor its fallback can complete
   */
  public double calcSurfaceTension(int interface1, int interface2);
}
