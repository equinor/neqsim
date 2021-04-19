/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */

package neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.viscosity;

//import physicalProperties.*;

import org.apache.logging.log4j.*;

//import physicalProperties.gasPhysicalProperties.*;
/**
 *
 * @author Even Solbraa
 * @version
 */
abstract class Viscosity
        extends neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.GasPhysicalPropertyMethod
        implements neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ViscosityInterface {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Viscosity.class);

    /** Creates new Conductivity */
    public Viscosity() {
    }

    public Viscosity(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface gasPhase) {
        super(gasPhase);
    }

    @Override
	public Object clone() {
        Viscosity properties = null;

        try {
            properties = (Viscosity) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return properties;
    }
}
