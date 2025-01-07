package neqsim.physicalproperties.system.commonphasephysicalproperties;

import neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity.PFCTConductivityMethodMod86;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.diffusivity.CorrespondingStatesDiffusivity;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.FrictionTheoryViscosityMethod;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * DefaultPhysicalProperties class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class DefaultPhysicalProperties extends PhysicalProperties {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for DefaultPhysicalProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param binaryDiffusionCoefficientMethod a int
   * @param multicomponentDiffusionMethod a int
   */
  public DefaultPhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
    conductivityCalc = new PFCTConductivityMethodMod86(this);

    // viscosityCalc = new PFCTViscosityMethod(this);
    // viscosityCalc = new PFCTViscosityMethodMod86(this);
    // viscosityCalc = new LBCViscosityMethod(this);
    viscosityCalc = new FrictionTheoryViscosityMethod(this);
    diffusivityCalc = new CorrespondingStatesDiffusivity(this);
    densityCalc = new neqsim.physicalproperties.methods.gasphysicalproperties.density.Density(this);
    this.init(phase);
  }
}
