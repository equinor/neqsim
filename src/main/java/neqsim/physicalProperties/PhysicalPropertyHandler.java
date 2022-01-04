package neqsim.physicalProperties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    private neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface gasPhysicalProperties =
            null;
    private neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface oilPhysicalProperties =
            null;
    private neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface aqueousPhysicalProperties =
            null;
    private neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhysicalProperties =
            null;
    private neqsim.physicalProperties.mixingRule.PhysicalPropertyMixingRule mixingRule = null;
    static Logger logger = LogManager.getLogger(PhysicalPropertyHandler.class);
    private static final long serialVersionUID = 1000;

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
     * @param type a int
     */
    public void setPhysicalProperties(PhaseInterface phase, int type) {
        switch (type) {
            case 0:
                gasPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(
                                phase, 0, 0);
                oilPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.LiquidPhysicalProperties(
                                phase, 0, 0);
                aqueousPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.WaterPhysicalProperties(
                                phase, 0, 0);
                break;
            case 1:
                gasPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(
                                phase, 0, 0);
                oilPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.LiquidPhysicalProperties(
                                phase, 0, 0);
                aqueousPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.WaterPhysicalProperties(
                                phase, 0, 0);
                break;
            case 2:
                gasPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(
                                phase, 0, 0);
                oilPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.LiquidPhysicalProperties(
                                phase, 0, 0);
                aqueousPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.GlycolPhysicalProperties(
                                phase, 0, 0);
                break;
            case 3:
                gasPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(
                                phase, 0, 0);
                oilPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.LiquidPhysicalProperties(
                                phase, 0, 0);
                aqueousPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.AminePhysicalProperties(
                                phase, 0, 0);
                break;
            case 4:
                gasPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(
                                phase, 0, 0);
                oilPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.LiquidPhysicalProperties(
                                phase, 0, 0);
                aqueousPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.CO2waterPhysicalProperties(
                                phase, 0, 0);
                break;
            case 6:
                gasPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.commonPhasePhysicalProperties.DefaultPhysicalProperties(
                                phase, 0, 0);
                oilPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.commonPhasePhysicalProperties.DefaultPhysicalProperties(
                                phase, 0, 0);
                aqueousPhysicalProperties =
                        new neqsim.physicalProperties.physicalPropertySystem.commonPhasePhysicalProperties.DefaultPhysicalProperties(
                                phase, 0, 0);
                break;
            default:
                logger.error(
                        "error selecting physical properties model.\n Continue using default model...");
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
     * @return a
     *         {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
     *         object
     */
    public neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface getPhysicalProperty(
            PhaseInterface phase) {
        switch (phase.getPhaseTypeName()) {
            case "gas":
                return gasPhysicalProperties;
            case "oil":
                return oilPhysicalProperties;
            case "aqueous":
                return aqueousPhysicalProperties;
            case "solid":
                return solidPhysicalProperties;
            case "wax":
                return solidPhysicalProperties;
            case "hydrate":
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
        } catch (Exception e) {
            // e.printStackTrace(System.err);
            logger.error(e.getMessage());
        }
        try {
            if (gasPhysicalProperties != null) {
                clonedHandler.gasPhysicalProperties =
                        (neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface) gasPhysicalProperties
                                .clone();
            }
            if (oilPhysicalProperties != null) {
                clonedHandler.oilPhysicalProperties =
                        (neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface) oilPhysicalProperties
                                .clone();
            }
            if (aqueousPhysicalProperties != null) {
                clonedHandler.aqueousPhysicalProperties =
                        (neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface) aqueousPhysicalProperties
                                .clone();
            }
            if (solidPhysicalProperties != null) {
                clonedHandler.solidPhysicalProperties =
                        (neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface) solidPhysicalProperties
                                .clone();
            }
            if (mixingRule != null) {
                clonedHandler.mixingRule =
                        (neqsim.physicalProperties.mixingRule.PhysicalPropertyMixingRule) mixingRule
                                .clone();
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedHandler;
    }
}
