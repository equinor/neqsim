/*
 * SurfaceTension.java
 *
 * Created on 13. august 2001, 13:14
 */

package neqsim.physicalproperties.interfaceproperties.surfacetension;

import neqsim.physicalproperties.interfaceproperties.InterfaceProperties;
import neqsim.thermo.system.SystemInterface;

/**
 * SurfaceTension class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class SurfaceTension extends InterfaceProperties implements SurfaceTensionInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected SystemInterface system;

  /**
   * Constructor for SurfaceTension.
   */
  public SurfaceTension() {
  }

  /**
   * Constructor for SurfaceTension.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SurfaceTension(SystemInterface system) {
    this.system = system;
  }

  /**
   * Calculates the pure component surfacetension.
   *
   * @param componentNumber Number of component in phase's componentarray.
   * @return pure component surface tension.
   */
  public double calcPureComponentSurfaceTension(int componentNumber) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcSurfaceTension(int interface1, int interface2) {
    return 0.0;
  }

  /**
   * getComponentWithHighestBoilingpoint.
   *
   * @return a int
   */
  public int getComponentWithHighestBoilingpoint() {
    int compNumb = 0;
    double boilPoint = 0;
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).getNormalBoilingPoint() > boilPoint) {
        compNumb = i;
        boilPoint = system.getPhase(0).getComponent(i).getNormalBoilingPoint();
      }
    }
    return compNumb;
  }
}
