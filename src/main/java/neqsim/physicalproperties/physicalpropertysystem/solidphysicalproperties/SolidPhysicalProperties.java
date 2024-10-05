package neqsim.physicalproperties.physicalpropertysystem.solidphysicalproperties;

import neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * SolidPhysicalProperties class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SolidPhysicalProperties extends PhysicalProperties {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SolidPhysicalProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public SolidPhysicalProperties(PhaseInterface phase) {
    super(phase);
    conductivityCalc =
        new neqsim.physicalproperties.physicalpropertymethods.solidphysicalproperties.conductivity.Conductivity(
            this);
    viscosityCalc =
        new neqsim.physicalproperties.physicalpropertymethods.solidphysicalproperties.viscosity.Viscosity(
            this);
    diffusivityCalc =
        new neqsim.physicalproperties.physicalpropertymethods.solidphysicalproperties.diffusivity.Diffusivity(
            this);
    densityCalc =
        new neqsim.physicalproperties.physicalpropertymethods.solidphysicalproperties.density.Density(
            this);
    this.init(phase);
  }
}
