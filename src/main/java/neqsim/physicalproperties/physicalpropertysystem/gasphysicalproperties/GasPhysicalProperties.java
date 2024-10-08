/*
 * GasPhysicalProperties.java
 *
 * Created on 29. oktober 2000, 16:18
 */

package neqsim.physicalproperties.physicalpropertysystem.gasphysicalproperties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.physicalpropertymethods.commonphasephysicalproperties.conductivity.PFCTConductivityMethodMod86;
import neqsim.physicalproperties.physicalpropertymethods.commonphasephysicalproperties.viscosity.PFCTViscosityMethodHeavyOil;
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
    extends neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(GasPhysicalProperties.class);

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
    // conductivityCalc = new ChungConductivityMethod(this);
    conductivityCalc = new PFCTConductivityMethodMod86(this);
    // viscosityCalc = new ChungViscosityMethod(this);
    // viscosityCalc = new FrictionTheoryViscosityMethod(this);
    // viscosityCalc = new PFCTViscosityMethodMod86(this);
    viscosityCalc = new PFCTViscosityMethodHeavyOil(this);

    // viscosityCalc = new LBCViscosityMethod(this);
    diffusivityCalc =
        new neqsim.physicalproperties.physicalpropertymethods.gasphysicalproperties.diffusivity.Diffusivity(
            this);
    // diffusivityCalc = new WilkeLeeDiffusivity(this);
    densityCalc =
        new neqsim.physicalproperties.physicalpropertymethods.gasphysicalproperties.density.Density(
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
