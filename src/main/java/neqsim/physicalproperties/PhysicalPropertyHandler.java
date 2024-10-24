package neqsim.physicalproperties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.physicalpropertymethods.PhysicalPropertyType;
import neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface;
import neqsim.physicalproperties.physicalpropertysystem.commonphasephysicalproperties.DefaultPhysicalProperties;
import neqsim.physicalproperties.physicalpropertysystem.gasphysicalproperties.GasPhysicalProperties;
import neqsim.physicalproperties.physicalpropertysystem.liquidphysicalproperties.AminePhysicalProperties;
import neqsim.physicalproperties.physicalpropertysystem.liquidphysicalproperties.CO2waterPhysicalProperties;
import neqsim.physicalproperties.physicalpropertysystem.liquidphysicalproperties.GlycolPhysicalProperties;
import neqsim.physicalproperties.physicalpropertysystem.liquidphysicalproperties.LiquidPhysicalProperties;
import neqsim.physicalproperties.physicalpropertysystem.liquidphysicalproperties.WaterPhysicalProperties;
import neqsim.physicalproperties.physicalpropertysystem.solidphysicalproperties.SolidPhysicalProperties;
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
   * @param ppt PhysicalPropertyType enum object
   */
  public void setPhysicalProperties(PhaseInterface phase, PhysicalPropertyType ppt) {
    switch (ppt) {
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
        setPhysicalProperties(phase, PhysicalPropertyType.DEFAULT);
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
   * getPhysicalProperty.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface}
   *         object
   */
  public PhysicalPropertiesInterface getPhysicalProperty(PhaseInterface phase) {
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
