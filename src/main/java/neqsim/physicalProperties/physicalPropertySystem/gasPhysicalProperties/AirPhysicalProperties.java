/*
 * AirPhysicalProperties.java
 *
 * Created on 13. august 2001, 10:34
 */

package neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties;

import neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.conductivity.ChungConductivityMethod;
import neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.viscosity.ChungViscosityMethod;
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
        new neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.diffusivity.Diffusivity(
            this);
    // diffusivityCalc = new WilkeLeeDiffusivity(this);

    densityCalc =
        new neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.density.Density(
            this);
    this.init(phase);
  }
}
