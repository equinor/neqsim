package neqsim.physicalproperties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.physicalproperties.system.commonphasephysicalproperties.DefaultPhysicalProperties;
import neqsim.physicalproperties.system.gasphysicalproperties.GasPhysicalProperties;
import neqsim.physicalproperties.system.liquidphysicalproperties.AminePhysicalProperties;
import neqsim.physicalproperties.system.liquidphysicalproperties.CO2waterPhysicalProperties;
import neqsim.physicalproperties.system.liquidphysicalproperties.GlycolPhysicalProperties;
import neqsim.physicalproperties.system.liquidphysicalproperties.LiquidPhysicalProperties;
import neqsim.physicalproperties.system.liquidphysicalproperties.SaltWaterPhysicalProperties;
import neqsim.physicalproperties.system.liquidphysicalproperties.WaterPhysicalProperties;
import neqsim.physicalproperties.system.solidphysicalproperties.SolidPhysicalProperties;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * PhysicalPropertyHandler class. Containing the physical property functions for all kinds of
 * phasetypes. WAX, HYDRATE, SOLID and SOLIDCOMPLEX are all considered as solid phases.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PhysicalPropertyHandler implements Cloneable, java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhysicalPropertyHandler.class);

  private PhysicalProperties gasPhysicalProperties = null;
  private PhysicalProperties oilPhysicalProperties = null;
  private PhysicalProperties aqueousPhysicalProperties = null;
  private PhysicalProperties solidPhysicalProperties = null;
  private neqsim.physicalproperties.mixingrule.PhysicalPropertyMixingRule mixingRule = null;

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
   * @param ppm PhysicalPropertyModel enum object
   */
  public void setPhysicalProperties(PhaseInterface phase, PhysicalPropertyModel ppm) {
    switch (ppm) {
      case DEFAULT: // Default
        gasPhysicalProperties = new GasPhysicalProperties(phase, 0, 0);
        oilPhysicalProperties = new LiquidPhysicalProperties(phase, 0, 0);
        aqueousPhysicalProperties = new WaterPhysicalProperties(phase, 0, 0);
        break;
      case WATER: // Water
        gasPhysicalProperties = new GasPhysicalProperties(phase, 0, 0);
        oilPhysicalProperties = new LiquidPhysicalProperties(phase, 0, 0);
        aqueousPhysicalProperties = new WaterPhysicalProperties(phase, 0, 0);
        break;
      case SALT_WATER: // Water
        gasPhysicalProperties = new GasPhysicalProperties(phase, 0, 0);
        oilPhysicalProperties = new LiquidPhysicalProperties(phase, 0, 0);
        aqueousPhysicalProperties = new SaltWaterPhysicalProperties(phase, 0, 0);
        break;
      case GLYCOL: // Glycol
        gasPhysicalProperties = new GasPhysicalProperties(phase, 0, 0);
        oilPhysicalProperties = new LiquidPhysicalProperties(phase, 0, 0);
        aqueousPhysicalProperties = new GlycolPhysicalProperties(phase, 0, 0);
        break;
      case AMINE: // Amine
        gasPhysicalProperties = new GasPhysicalProperties(phase, 0, 0);
        oilPhysicalProperties = new LiquidPhysicalProperties(phase, 0, 0);
        aqueousPhysicalProperties = new AminePhysicalProperties(phase, 0, 0);
        break;
      case CO2WATER:
        gasPhysicalProperties = new GasPhysicalProperties(phase, 0, 0);
        oilPhysicalProperties = new LiquidPhysicalProperties(phase, 0, 0);
        aqueousPhysicalProperties = new CO2waterPhysicalProperties(phase, 0, 0);
        break;
      case BASIC:
        gasPhysicalProperties = new DefaultPhysicalProperties(phase, 0, 0);
        oilPhysicalProperties = new DefaultPhysicalProperties(phase, 0, 0);
        aqueousPhysicalProperties = new DefaultPhysicalProperties(phase, 0, 0);
        break;
      default:
        logger
            .error("error selecting physical properties model.\n Continue using default model...");
        setPhysicalProperties(phase, PhysicalPropertyModel.DEFAULT);
        break;
    }
    solidPhysicalProperties = new SolidPhysicalProperties(phase);
    mixingRule = new neqsim.physicalproperties.mixingrule.PhysicalPropertyMixingRule();
    mixingRule.initMixingRules(phase);
    gasPhysicalProperties.setMixingRule(mixingRule);
    oilPhysicalProperties.setMixingRule(mixingRule);
    aqueousPhysicalProperties.setMixingRule(mixingRule);
  }

  /**
   * <p>
   * Get PhysicalProperties for a specific phase type.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public PhysicalProperties getPhysicalProperties(PhaseInterface phase) {
    switch (phase.getType()) {
      case GAS:
        return gasPhysicalProperties;
      case LIQUID:
        // TODO: check if it should be AQUEOUS?
      case OIL:
        return oilPhysicalProperties;
      case AQUEOUS:
        return aqueousPhysicalProperties;
      case WAX:
      case HYDRATE:
      case SOLID:
      case SOLIDCOMPLEX:
      case ASPHALTENE:
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
