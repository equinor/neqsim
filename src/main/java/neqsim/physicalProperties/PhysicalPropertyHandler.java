package neqsim.physicalProperties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface;
import neqsim.physicalProperties.physicalPropertySystem.commonPhasePhysicalProperties.DefaultPhysicalProperties;
import neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties;
import neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.AminePhysicalProperties;
import neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.CO2waterPhysicalProperties;
import neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.GlycolPhysicalProperties;
import neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.LiquidPhysicalProperties;
import neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.WaterPhysicalProperties;
import neqsim.physicalProperties.physicalPropertySystem.solidPhysicalProperties.SolidPhysicalProperties;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * PhysicalPropertyHandler class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PhysicalPropertyHandler implements Cloneable, java.io.Serializable {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(PhysicalPropertyHandler.class);

  private PhysicalPropertiesInterface gasPhysicalProperties = null;
  private PhysicalPropertiesInterface oilPhysicalProperties = null;
  private PhysicalPropertiesInterface aqueousPhysicalProperties = null;
  private PhysicalPropertiesInterface solidPhysicalProperties = null;
  private neqsim.physicalProperties.mixingRule.PhysicalPropertyMixingRule mixingRule = null;

  /**
   * <p>
   * Constructor for PhysicalPropertyHandler.
   * </p>
   */
  public PhysicalPropertyHandler() {}

  /**
   * <p>
   * setPhysicalProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param type 0 Orginal/default 1 Water 2 Glycol 3 Amine 4 CO2Water 6 Basic
   */
  public void setPhysicalProperties(PhaseInterface phase, int type) {
    switch (type) {
      case 0: // Default
        gasPhysicalProperties = new GasPhysicalProperties(phase, 0, 0);
        oilPhysicalProperties = new LiquidPhysicalProperties(phase, 0, 0);
        aqueousPhysicalProperties = new WaterPhysicalProperties(phase, 0, 0);
        break;
      case 1: // Water
        gasPhysicalProperties = new GasPhysicalProperties(phase, 0, 0);
        oilPhysicalProperties = new LiquidPhysicalProperties(phase, 0, 0);
        aqueousPhysicalProperties = new WaterPhysicalProperties(phase, 0, 0);
        break;
      case 2: // Glycol
        gasPhysicalProperties = new GasPhysicalProperties(phase, 0, 0);
        oilPhysicalProperties = new LiquidPhysicalProperties(phase, 0, 0);
        aqueousPhysicalProperties = new GlycolPhysicalProperties(phase, 0, 0);
        break;
      case 3: // Amine
        gasPhysicalProperties = new GasPhysicalProperties(phase, 0, 0);
        oilPhysicalProperties = new LiquidPhysicalProperties(phase, 0, 0);
        aqueousPhysicalProperties = new AminePhysicalProperties(phase, 0, 0);
        break;
      case 4: // CO2water
        gasPhysicalProperties = new GasPhysicalProperties(phase, 0, 0);
        oilPhysicalProperties = new LiquidPhysicalProperties(phase, 0, 0);
        aqueousPhysicalProperties = new CO2waterPhysicalProperties(phase, 0, 0);
        break;
      case 6: // Basic?
        gasPhysicalProperties = new DefaultPhysicalProperties(phase, 0, 0);
        oilPhysicalProperties = new DefaultPhysicalProperties(phase, 0, 0);
        aqueousPhysicalProperties = new DefaultPhysicalProperties(phase, 0, 0);
        break;
      default:
        logger
            .error("error selecting physical properties model.\n Continue using default model...");
        setPhysicalProperties(phase, 0);
        break;
    }
    solidPhysicalProperties = new SolidPhysicalProperties(phase);
    mixingRule = new neqsim.physicalProperties.mixingRule.PhysicalPropertyMixingRule();
    mixingRule.initMixingRules(phase);
    gasPhysicalProperties.setMixingRule(mixingRule);
    oilPhysicalProperties.setMixingRule(mixingRule);
    aqueousPhysicalProperties.setMixingRule(mixingRule);
  }

  /**
   * <p>
   * getPhysicalProperty.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
   *         object
   */
  public neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface getPhysicalProperty(
      PhaseInterface phase) {
    switch (phase.getType()) {
      case GAS:
        return gasPhysicalProperties;
      case OIL:
        return oilPhysicalProperties;
      case AQUEOUS:
        return aqueousPhysicalProperties;
      case SOLID:
        return solidPhysicalProperties;
      case SOLIDCOMPLEX:
        return solidPhysicalProperties;
      case WAX:
        return solidPhysicalProperties;
      case HYDRATE:
        return solidPhysicalProperties;
      default:
        return gasPhysicalProperties;
    }
  }

  /** {@inheritDoc} */
  @Override
  public PhysicalPropertyHandler clone() {
    PhysicalPropertyHandler clonedHandler = null;

    try {
      clonedHandler = (PhysicalPropertyHandler) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    try {
      if (gasPhysicalProperties != null) {
        clonedHandler.gasPhysicalProperties = gasPhysicalProperties.clone();
      }
      if (oilPhysicalProperties != null) {
        clonedHandler.oilPhysicalProperties = oilPhysicalProperties.clone();
      }
      if (aqueousPhysicalProperties != null) {
        clonedHandler.aqueousPhysicalProperties = aqueousPhysicalProperties.clone();
      }
      if (solidPhysicalProperties != null) {
        clonedHandler.solidPhysicalProperties = solidPhysicalProperties.clone();
      }
      if (mixingRule != null) {
        clonedHandler.mixingRule = mixingRule.clone();
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return clonedHandler;
  }
}
