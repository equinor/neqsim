package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.ionicInteractionCoefficientFitting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * IonicInteractionParameterFittingFunction_1 class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class IonicInteractionParameterFittingFunction_1
        extends IonicInteractionParameterFittingFunction {
    static Logger logger = LogManager.getLogger(IonicInteractionParameterFittingFunction_1.class);

    /**
     * <p>
     * Constructor for IonicInteractionParameterFittingFunction_1.
     * </p>
     */
    public IonicInteractionParameterFittingFunction_1() {}

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        try {
            thermoOps.bubblePointPressureFlash(false);
            // System.out.println("pres " +
            // system.getPressure()*system.getPhases()[0].getComponent(0).getx());
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return system.getPressure();
    }
}
