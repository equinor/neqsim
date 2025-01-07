/*
 * WaterPhysicalProperties.java
 *
 * Created on 13. august 2001, 10:34
 */

package neqsim.physicalproperties.system.liquidphysicalproperties;

import neqsim.physicalproperties.methods.liquidphysicalproperties.conductivity.Conductivity;
import neqsim.physicalproperties.methods.liquidphysicalproperties.density.Density;
import neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity.SiddiqiLucasMethod;
import neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity.Viscosity;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * WaterPhysicalProperties class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class WaterPhysicalProperties extends LiquidPhysicalProperties {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for WaterPhysicalProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param binaryDiffusionCoefficientMethod a int
   * @param multicomponentDiffusionMethod a int
   */
  public WaterPhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
    conductivityCalc = new Conductivity(this);
    viscosityCalc = new Viscosity(this);
    diffusivityCalc = new SiddiqiLucasMethod(this);
    densityCalc = new Density(this);
  }
}
