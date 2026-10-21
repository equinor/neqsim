package neqsim.physicalproperties.interfaceproperties.surfacetension;

import neqsim.thermo.system.SystemInterface;

/**
 * FirozabadiRamleyInterfaceTension class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class FirozabadiRamleyInterfaceTension extends SurfaceTension {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for FirozabadiRamleyInterfaceTension.
   */
  public FirozabadiRamleyInterfaceTension() {
  }

  /**
   * Constructor for FirozabadiRamleyInterfaceTension.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public FirozabadiRamleyInterfaceTension(SystemInterface system) {
    super(system);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * using the Macleod/Sugden method
   * </p>
   */
  @Override
  public double calcPureComponentSurfaceTension(int componentNumber) {
    return new ParachorSurfaceTension(system).calcPureComponentSurfaceTension(componentNumber);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Using the Firozabadi Ramley (1988) method for mixtures Units: N/m
   * </p>
   */
  @Override
  public double calcSurfaceTension(int interface1, int interface2) {
    if (!validateInterface(interface1, interface2)) {
      return 0.0;
    }
    for (int index : new int[] {interface1, interface2}) {
      double density = system.getPhase(index).getPhysicalProperties().getDensity();
      if (!Double.isFinite(density) || density <= 0.0) {
        throw new IllegalStateException("Initialize valid physical density before calculating surface tension");
      }
    }
    double deltaDens = Math.abs(system.getPhase(interface2).getPhysicalProperties().getDensity()
        - system.getPhase(interface1).getPhysicalProperties().getDensity());
    double Tr = system.getPhase(interface1).getTemperature()
        / system.getPhase(interface1).getPseudoCriticalTemperature();
    // System.out.println("deltaDens " + deltaDens + " Tr " + Tr + " pt " +
    // system.getPhase(interface1).getType());
    double a1 = 0.0;
    double b1 = 0.0;
    if (deltaDens / 1000.0 < 0.2) {
      a1 = 2.2062;
      b1 = -0.94716;
    } else if (deltaDens / 1000.0 < 0.5) {
      a1 = 2.915;
      b1 = -0.76852;
    } else {
      a1 = 3.3858;
      b1 = -0.62590;
    }

    double temp1 = a1 * Math.pow(deltaDens / 1000.0, b1 + 1.0) / Math.pow(Tr, 0.3125);
    return validateResult(Math.pow(temp1, 4.0) / 1000.0);
  }
}
