package neqsim.physicalproperties.system.liquidphysicalproperties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.liquidphysicalproperties.conductivity.Conductivity;
import neqsim.physicalproperties.methods.liquidphysicalproperties.density.Density;
import neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity.CO2water;
import neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity.Viscosity;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * CO2waterPhysicalProperties class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class CO2waterPhysicalProperties extends PhysicalProperties {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CO2waterPhysicalProperties.class);

  /**
   * <p>
   * Constructor for CO2waterPhysicalProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param binaryDiffusionCoefficientMethod a int
   * @param multicomponentDiffusionMethod a int
   */
  public CO2waterPhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
    conductivityCalc = new Conductivity(this);
    viscosityCalc = new Viscosity(this);
    diffusivityCalc = new CO2water(this);
    densityCalc = new Density(this);
  }

  /** {@inheritDoc} */
  @Override
  public CO2waterPhysicalProperties clone() {
    CO2waterPhysicalProperties properties = null;

    try {
      properties = (CO2waterPhysicalProperties) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return properties;
  }
}
