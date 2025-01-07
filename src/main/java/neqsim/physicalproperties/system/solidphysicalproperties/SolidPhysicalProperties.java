package neqsim.physicalproperties.system.solidphysicalproperties;

import neqsim.physicalproperties.system.PhysicalProperties;
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
  /** Serialization version UID. */
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
        new neqsim.physicalproperties.methods.solidphysicalproperties.conductivity.Conductivity(
            this);
    viscosityCalc =
        new neqsim.physicalproperties.methods.solidphysicalproperties.viscosity.Viscosity(this);
    diffusivityCalc =
        new neqsim.physicalproperties.methods.solidphysicalproperties.diffusivity.Diffusivity(this);
    densityCalc =
        new neqsim.physicalproperties.methods.solidphysicalproperties.density.Density(this);
    this.init(phase);
  }
}
