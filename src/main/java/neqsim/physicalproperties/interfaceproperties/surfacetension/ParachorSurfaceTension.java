package neqsim.physicalproperties.interfaceproperties.surfacetension;

import neqsim.thermo.system.SystemInterface;

/**
 * ParachorSurfaceTension class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class ParachorSurfaceTension extends SurfaceTension {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for ParachorSurfaceTension.
   */
  public ParachorSurfaceTension() {
  }

  /**
   * Constructor for ParachorSurfaceTension.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ParachorSurfaceTension(SystemInterface system) {
    super(system);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Calculates the pure component surfacetension using the Macleod/Sugden method
   * </p>
   */
  @Override
  public double calcPureComponentSurfaceTension(int componentNumber) {
    return 1.0e-3 * Math.pow(system.getPhases()[0].getComponents()[componentNumber].getParachorParameter() * 1.0e-6
        * (system.getPhases()[1].getPhysicalProperties().getDensity() / system.getPhases()[1].getMolarMass()
            * system.getPhases()[1].getComponents()[componentNumber].getx()
            - system.getPhases()[0].getPhysicalProperties().getDensity() / system.getPhases()[0].getMolarMass()
                * system.getPhases()[0].getComponents()[componentNumber].getx()),
        4.0);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Using the Macleod/Sugden method for mixtures Units: N/m
   * </p>
   */
  @Override
  public double calcSurfaceTension(int interface1, int interface2) {
    if (system.getNumberOfPhases() < 2) {
      return 0.0;
    }
    if (interface1 < 0 || interface2 < 0 || interface1 >= system.getNumberOfPhases()
        || interface2 >= system.getNumberOfPhases()) {
      throw new IllegalArgumentException("Surface-tension phase index is outside the current phase set");
    }
    if (interface1 == interface2) {
      throw new IllegalArgumentException("Surface tension requires two distinct phases");
    }

    double temp = 0.0;
    for (int i = 0; i < system.getPhase(interface1).getNumberOfComponents(); i++) {
      temp += system.getPhase(interface1).getComponent(i).getParachorParameter() * 1.0e-6
          * (system.getPhase(interface2).getPhysicalProperties().getDensity()
              / system.getPhase(interface2).getMolarMass() * system.getPhase(interface2).getComponent(i).getx()
              - system.getPhase(interface1).getPhysicalProperties().getDensity()
                  / system.getPhase(interface1).getMolarMass() * system.getPhase(interface1).getComponent(i).getx());
    }
    return Math.pow(temp, 4.0) / 1000.0;
  }
}
