package neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.conductivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Even Solbraa
 * @version
 */
abstract class Conductivity extends
        neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.CommonPhysicalPropertyMethod
        implements
        neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ConductivityInterface {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Conductivity.class);

    double conductivity = 0;

    /** Creates new Conductivity */
    public Conductivity() {}

    public Conductivity(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {
        super(phase);
    }

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
