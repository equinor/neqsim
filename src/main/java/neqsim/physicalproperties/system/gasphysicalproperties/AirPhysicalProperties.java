/*
 * AirPhysicalProperties.java
 *
 * Created on 13. august 2001, 10:34
 */

package neqsim.physicalproperties.system.gasphysicalproperties;

import neqsim.physicalproperties.methods.gasphysicalproperties.conductivity.ChungConductivityMethod;
import neqsim.physicalproperties.methods.gasphysicalproperties.viscosity.ChungViscosityMethod;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * AirPhysicalProperties class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AirPhysicalProperties extends GasPhysicalProperties {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for AirPhysicalProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param binaryDiffusionCoefficientMethod a int
   * @param multicomponentDiffusionMethod a int
   */
  public AirPhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
    conductivityCalc = new ChungConductivityMethod(this);
    viscosityCalc = new ChungViscosityMethod(this);
    diffusivityCalc =
        new neqsim.physicalproperties.methods.gasphysicalproperties.diffusivity.Diffusivity(this);
    // diffusivityCalc = new WilkeLeeDiffusivity(this);

    densityCalc = new neqsim.physicalproperties.methods.gasphysicalproperties.density.Density(this);
    this.init(phase);
  }
}
