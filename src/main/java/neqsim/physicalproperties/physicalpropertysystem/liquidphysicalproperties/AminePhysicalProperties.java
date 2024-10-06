/*
 * AminePhysicalProperties.java
 *
 * Created on 13. august 2001, 10:31
 */

package neqsim.physicalproperties.physicalpropertysystem.liquidphysicalproperties;

import neqsim.physicalproperties.physicalpropertymethods.gasphysicalproperties.density.Density;
import neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.conductivity.Conductivity;
import neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.diffusivity.AmineDiffusivity;
import neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.viscosity.AmineViscosity;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * AminePhysicalProperties class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AminePhysicalProperties extends LiquidPhysicalProperties {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for AminePhysicalProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param binaryDiffusionCoefficientMethod a int
   * @param multicomponentDiffusionMethod a int
   */
  public AminePhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
    conductivityCalc = new Conductivity(this);
    viscosityCalc = new AmineViscosity(this);
    diffusivityCalc = new AmineDiffusivity(this);
    densityCalc = new Density(this);
  }
}
