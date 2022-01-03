package neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.viscosity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Even Solbraa
 * @version
 */
abstract class Viscosity extends
        neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.GasPhysicalPropertyMethod
        implements
        neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ViscosityInterface {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Viscosity.class);

    /**
     * Creates new Conductivity
     */
    public Viscosity() {}

    /**
     * <p>Constructor for Viscosity.</p>
     *
     * @param gasPhase a {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface} object
     */
    public Viscosity(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface gasPhase) {
        super(gasPhase);
    }

    /** {@inheritDoc} */
    @Override
    public Viscosity clone() {
        Viscosity properties = null;

        try {
            properties = (Viscosity) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return properties;
    }
}
