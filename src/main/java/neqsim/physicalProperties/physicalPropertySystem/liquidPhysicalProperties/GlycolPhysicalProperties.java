/*
 * GlycolPhysicalProperties.java
 *
 * Created on 13. august 2001, 10:31
 */

package neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties;

import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.conductivity.Conductivity;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.density.Density;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.diffusivity.SiddiqiLucasMethod;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.viscosity.Viscosity;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * GlycolPhysicalProperties class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class GlycolPhysicalProperties extends LiquidPhysicalProperties {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for GlycolPhysicalProperties.
   * </p>
   */
  public GlycolPhysicalProperties() {}

  /**
   * <p>
   * Constructor for GlycolPhysicalProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param binaryDiffusionCoefficientMethod a int
   * @param multicomponentDiffusionMethod a int
   */
  public GlycolPhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
    conductivityCalc = new Conductivity(this);
    viscosityCalc = new Viscosity(this);
    diffusivityCalc = new SiddiqiLucasMethod(this);
    densityCalc = new Density(this);
  }
}
