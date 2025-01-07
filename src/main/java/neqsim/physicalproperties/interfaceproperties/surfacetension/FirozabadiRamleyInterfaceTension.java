package neqsim.physicalproperties.interfaceproperties.surfacetension;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * FirozabadiRamleyInterfaceTension class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class FirozabadiRamleyInterfaceTension extends SurfaceTension {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for FirozabadiRamleyInterfaceTension.
   * </p>
   */
  public FirozabadiRamleyInterfaceTension() {}

  /**
   * <p>
   * Constructor for FirozabadiRamleyInterfaceTension.
   * </p>
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
    return 1.0e-3 * Math
        .pow(system.getPhases()[0].getComponents()[componentNumber].getParachorParameter() * 1.0e-6
            * (system.getPhases()[1].getPhysicalProperties().getDensity()
                / system.getPhases()[1].getMolarMass()
                * system.getPhases()[1].getComponents()[componentNumber].getx()
                - system.getPhases()[0].getPhysicalProperties().getDensity()
                    / system.getPhases()[0].getMolarMass()
                    * system.getPhases()[0].getComponents()[componentNumber].getx()),
            4.0);
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
    if (system.getNumberOfPhases() < 2) {
      return 0.0;
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
    return Math.pow(temp1, 4.0) / 1000.0;
  }
}
