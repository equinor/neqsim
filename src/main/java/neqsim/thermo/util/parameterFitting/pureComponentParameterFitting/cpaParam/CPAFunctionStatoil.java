/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.cpaParam;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class CPAFunctionStatoil extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(CPAFunctionStatoil.class);

    /** Creates new Test */
    public CPAFunctionStatoil() {
    }

    public double calcValue(double[] dependentValues) {
        system.setTemperature(dependentValues[0]);
        // system.init(0);
        // system.setPressure(system.getPhases()[0].getComponents()[0].getAntoineVaporPressure(dependentValues[0]));
        // System.out.println("pres from antoine: " + system.getPressure());
        try {
            thermoOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            logger.error(e.toString());
        }
        // System.out.println("pres: " + system.getPressure());
        return system.getPressure();
    }

    public double calcTrueValue(double val) {
        return val;
    }

    public void setFittingParams(int i, double value) {
        params[i] = value;
        if (i == 0) {
            system.getPhase(0).getComponent(0).getAtractiveTerm().setm(value);
            system.getPhases()[1].getComponents()[0].getAtractiveTerm().setm(value);
        }
        system.getPhases()[0].getComponents()[0].setMatiascopemanParams(i, value);
        system.getPhases()[1].getComponents()[0].setMatiascopemanParams(i, value);
        system.getPhases()[0].getComponents()[0].getAtractiveTerm().setParameters(i, value);
        system.getPhases()[1].getComponents()[0].getAtractiveTerm().setParameters(i, value);

//        value = 0.0;
//        for(int j=1;j<3;j++){
//            system.getPhases()[0].getComponents()[0].setSchwartzentruberParams(j, value);
//            system.getPhases()[1].getComponents()[0].setSchwartzentruberParams(j, value);
//            system.getPhases()[0].getComponents()[0].getAtractiveTerm().setParameters(j, value);
//            system.getPhases()[1].getComponents()[0].getAtractiveTerm().setParameters(j, value);
//        }
    }
}
