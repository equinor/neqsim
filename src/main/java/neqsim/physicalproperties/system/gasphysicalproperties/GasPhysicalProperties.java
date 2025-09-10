/*
 * GasPhysicalProperties.java
 *
 * Created on 29. oktober 2000, 16:18
 */

package neqsim.physicalproperties.system.gasphysicalproperties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity.CO2ConductivityMethod;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity.PFCTConductivityMethodMod86;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.CO2ViscosityMethod;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.PFCTViscosityMethodHeavyOil;
import neqsim.thermo.phase.PhaseSpanWagnerEos;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * GasPhysicalProperties class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class GasPhysicalProperties extends PhysicalProperties {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
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
    if (phase instanceof PhaseSpanWagnerEos) {
      conductivityCalc = new CO2ConductivityMethod(this);
      viscosityCalc = new CO2ViscosityMethod(this);
    } else {
      // conductivityCalc = new ChungConductivityMethod(this);
      conductivityCalc = new PFCTConductivityMethodMod86(this);
      // viscosityCalc = new ChungViscosityMethod(this);
      // viscosityCalc = new FrictionTheoryViscosityMethod(this);
      // viscosityCalc = new PFCTViscosityMethodMod86(this);
      viscosityCalc = new PFCTViscosityMethodHeavyOil(this);
    }

    // viscosityCalc = new LBCViscosityMethod(this);
    diffusivityCalc =
        new neqsim.physicalproperties.methods.gasphysicalproperties.diffusivity.Diffusivity(this);
    // diffusivityCalc = new WilkeLeeDiffusivity(this);
    densityCalc = new neqsim.physicalproperties.methods.gasphysicalproperties.density.Density(this);
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
