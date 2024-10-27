/*
 * LiquidPhysicalProperties.java
 *
 * Created on 29. oktober 2000, 16:17
 */

package neqsim.physicalproperties.physicalpropertysystem.liquidphysicalproperties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.physicalpropertymethods.commonphasephysicalproperties.conductivity.PFCTConductivityMethodMod86;
import neqsim.physicalproperties.physicalpropertymethods.commonphasephysicalproperties.viscosity.PFCTViscosityMethodHeavyOil;
import neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.density.Density;
import neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.diffusivity.SiddiqiLucasMethod;
import neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * LiquidPhysicalProperties class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class LiquidPhysicalProperties extends PhysicalProperties {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(LiquidPhysicalProperties.class);

  /**
   * <p>
   * Constructor for LiquidPhysicalProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param binaryDiffusionCoefficientMethod a int
   * @param multicomponentDiffusionMethod a int
   */
  public LiquidPhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
    // conductivityCalc = new Conductivity(this);
    conductivityCalc = new PFCTConductivityMethodMod86(this);
    // viscosityCalc = new Viscosity(this);
    // viscosityCalc = new FrictionTheoryViscosityMethod(this);
    // viscosityCalc = new PFCTViscosityMethodMod86(this);
    // viscosityCalc = new LBCViscosityMethod(this);
    viscosityCalc = new PFCTViscosityMethodHeavyOil(this);
    diffusivityCalc = new SiddiqiLucasMethod(this);
    densityCalc = new Density(this);
  }

  /** {@inheritDoc} */
  @Override
  public LiquidPhysicalProperties clone() {
    LiquidPhysicalProperties properties = null;

    try {
      properties = (LiquidPhysicalProperties) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return properties;
  }
}
