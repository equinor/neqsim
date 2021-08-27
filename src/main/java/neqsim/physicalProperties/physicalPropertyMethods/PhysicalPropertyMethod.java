/*
 * PhysicalPropertyMethod.java
 *
 * Created on 3. august 2001, 22:49
 */

package neqsim.physicalProperties.physicalPropertyMethods;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author  esol
 * @version
 */
public class PhysicalPropertyMethod implements PhysicalPropertyMethodInterface {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(PhysicalPropertyMethod.class);

    /** Creates new PhysicalPropertyMethod */
    public PhysicalPropertyMethod() {
    }

    @Override
    public Object clone() {
        PhysicalPropertyMethod properties = null;

        try {
            properties = (PhysicalPropertyMethod) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return properties;
    }

    @Override
    public void setPhase(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {
    }

    @Override
    public void tuneModel(double val, double temperature, double pressure) {
        logger.error("model tuning not implemented!");
    }
    // should contain phase objects ++ get diffusivity methods .. more ?
}
