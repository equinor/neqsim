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
    validateDensities();
    return validateResult(
        1.0e-3 * Math.pow(system.getPhases()[0].getComponents()[componentNumber].getParachorParameter() * 1.0e-6
            * (system.getPhases()[1].getPhysicalProperties().getDensity() / system.getPhases()[1].getMolarMass()
                * system.getPhases()[1].getComponents()[componentNumber].getx()
                - system.getPhases()[0].getPhysicalProperties().getDensity() / system.getPhases()[0].getMolarMass()
                    * system.getPhases()[0].getComponents()[componentNumber].getx()),
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
    validateDensity(interface1);
    validateDensity(interface2);

    double temp = 0.0;
    for (int i = 0; i < system.getPhase(interface1).getNumberOfComponents(); i++) {
      temp += system.getPhase(interface1).getComponent(i).getParachorParameter() * 1.0e-6
          * (system.getPhase(interface2).getPhysicalProperties().getDensity()
              / system.getPhase(interface2).getMolarMass() * system.getPhase(interface2).getComponent(i).getx()
              - system.getPhase(interface1).getPhysicalProperties().getDensity()
                  / system.getPhase(interface1).getMolarMass() * system.getPhase(interface1).getComponent(i).getx());
    }
    return validateResult(Math.pow(temp, 4.0) / 1000.0);
  }

  /** Validates the physical densities used by the pure-component correlation. */
  private void validateDensities() {
    validateDensity(0);
    validateDensity(1);
  }

  /**
   * Rejects uninitialized or invalid cached physical density.
   *
   * @param phase phase index
   * @throws IllegalStateException if the density is not finite and positive
   */
  private void validateDensity(int phase) {
    double density = system.getPhase(phase).getPhysicalProperties().getDensity();
    if (!Double.isFinite(density) || density <= 0.0) {
      throw new IllegalStateException("Initialize valid physical density before calculating surface tension");
    }
  }
}
