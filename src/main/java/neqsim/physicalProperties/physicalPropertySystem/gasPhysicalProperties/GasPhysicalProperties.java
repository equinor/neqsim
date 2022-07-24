/*
 * GasPhysicalProperties.java
 *
 * Created on 29. oktober 2000, 16:18
 */

package neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * GasPhysicalProperties class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class GasPhysicalProperties
    extends neqsim.physicalProperties.physicalPropertySystem.PhysicalProperties {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(GasPhysicalProperties.class);

  /**
   * <p>
   * Constructor for GasPhysicalProperties.
   * </p>
   */
  public GasPhysicalProperties() {}

  /**
   * <p>
   * Constructor for GasPhysicalProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param binaryDiffusionCoefficientMethod a int
   * @param multicomponentDiffusionMethod a int
   */
  public GasPhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
    // conductivityCalc = new
    // neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.conductivity.ChungConductivityMethod(this);
    conductivityCalc =
        new neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.conductivity.PFCTConductivityMethodMod86(
            this);
    // viscosityCalc = new
    // physicalProperties.physicalPropertyMethods.gasPhysicalProperties.viscosity.ChungViscosityMethod(this);
    // viscosityCalc = new
    // neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.FrictionTheoryViscosityMethod(this);
    // viscosityCalc = new
    // neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.PFCTViscosityMethodMod86(this);
    viscosityCalc =
        new neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.PFCTViscosityMethodHeavyOil(
            this);

    /// viscosityCalc = new
    /// neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.LBCViscosityMethod(this);
    diffusivityCalc =
        new neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.diffusivity.Diffusivity(
            this);
    // diffusivityCalc = new
    // physicalProperties.physicalPropertyMethods.gasPhysicalProperties.diffusivity.WilkeLeeDiffusivity(this);
    densityCalc =
        new neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.density.Density(
            this);
    // this.init(phase);
  }

  /** {@inheritDoc} */
  @Override
  public GasPhysicalProperties clone() {
    GasPhysicalProperties properties = null;

    try {
      properties = (GasPhysicalProperties) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return properties;
  }
}
