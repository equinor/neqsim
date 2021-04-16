/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.ionicInteractionCoefficientFitting;

import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class IonicInteractionParameterFittingFunction_1 extends IonicInteractionParameterFittingFunction {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(IonicInteractionParameterFittingFunction_1.class);

    /** Creates new Test */
    public IonicInteractionParameterFittingFunction_1() {
    }

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