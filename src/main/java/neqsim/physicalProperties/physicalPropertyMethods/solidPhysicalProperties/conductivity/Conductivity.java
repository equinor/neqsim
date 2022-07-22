/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */

package neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.conductivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * Conductivity class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Conductivity extends
        neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.SolidPhysicalPropertyMethod
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
     * @param solidPhase a
     *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
     *        object
     */
    public Conductivity(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhase) {
        super(solidPhase);
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

    /** {@inheritDoc} */
    @Override
    public double calcConductivity() {
        // using default value of parafin wax
        if (solidPhase.getPhase().getPhaseTypeName().equals("wax")) {
            conductivity = 0.25;
        } else {
            conductivity = 2.18;
        }

        return conductivity;
    }
}
