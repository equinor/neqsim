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
    if (!validateInterface(0, 1)) {
      return 0.0;
    }
    double firstMolarDensity = physicalDensity(0) / system.getPhase(0).getMolarMass();
    double secondMolarDensity = physicalDensity(1) / system.getPhase(1).getMolarMass();
    return validateResult(1.0e-3 * Math.pow(system.getPhase(0).getComponent(componentNumber).getParachorParameter()
        * 1.0e-6 * (secondMolarDensity * system.getPhase(1).getComponent(componentNumber).getx()
            - firstMolarDensity * system.getPhase(0).getComponent(componentNumber).getx()),
        4.0));
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
    if (!validateInterface(interface1, interface2)) {
      return 0.0;
    }
    double firstMolarDensity = physicalDensity(interface1) / system.getPhase(interface1).getMolarMass();
    double secondMolarDensity = physicalDensity(interface2) / system.getPhase(interface2).getMolarMass();

    double temp = 0.0;
    for (int i = 0; i < system.getPhase(interface1).getNumberOfComponents(); i++) {
      temp += system.getPhase(interface1).getComponent(i).getParachorParameter() * 1.0e-6
          * (secondMolarDensity * system.getPhase(interface2).getComponent(i).getx()
              - firstMolarDensity * system.getPhase(interface1).getComponent(i).getx());
    }
    return validateResult(Math.pow(temp, 4.0) / 1000.0);
  }

}
