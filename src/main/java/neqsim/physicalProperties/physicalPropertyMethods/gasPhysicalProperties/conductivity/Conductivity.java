/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */
package neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.conductivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Even Solbraa
 */
abstract class Conductivity extends
        neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.GasPhysicalPropertyMethod
        implements
        neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ConductivityInterface {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Conductivity.class);

    double conductivity = 0;

    /**
     * <p>
     * Constructor for Conductivity.
     * </p>
     */
    public Conductivity() {}

    /**
     * <p>
     * Constructor for Conductivity.
     * </p>
     *
     * @param gasPhase a
     *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
     *        object
     */
    public Conductivity(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface gasPhase) {
        super(gasPhase);
    }

    /** {@inheritDoc} */
    @Override
    public Conductivity clone() {
        Conductivity properties = null;

        try {
            properties = (Conductivity) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return properties;
    }
}
