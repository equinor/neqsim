/*
 * WaterPhysicalProperties.java
 *
 * Created on 13. august 2001, 10:34
 */

package neqsim.physicalproperties.system.liquidphysicalproperties;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * WaterPhysicalProperties class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SaltWaterPhysicalProperties extends WaterPhysicalProperties {
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
  public SaltWaterPhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
    viscosityCalc =
        new neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity.Water(this);
    densityCalc =
        new neqsim.physicalproperties.methods.liquidphysicalproperties.density.Water(this);
  }
}
