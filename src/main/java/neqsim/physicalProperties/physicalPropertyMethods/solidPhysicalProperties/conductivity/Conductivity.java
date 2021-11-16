/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */
package neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.conductivity;

import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class Conductivity extends
        neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.SolidPhysicalPropertyMethod
        implements
        neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ConductivityInterface {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Conductivity.class);

    double conductivity = 0;

    /**
     * Creates new Conductivity
     */
    public Conductivity() {}

    public Conductivity(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhase) {
        super(solidPhase);
    }

    @Override
    public Object clone() {
        Conductivity properties = null;

        try {
            properties = (Conductivity) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return properties;
    }

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
