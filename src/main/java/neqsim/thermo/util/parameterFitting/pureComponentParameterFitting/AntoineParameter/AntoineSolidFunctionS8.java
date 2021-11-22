package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.AntoineParameter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class AntoineSolidFunctionS8 extends LevenbergMarquardtFunction {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(AntoineSolidFunctionS8.class);


    public AntoineSolidFunctionS8() {
        params = new double[2];
    }

    @Override
    public double calcValue(double[] dependentValues) {
        system.init(0);
        try {
            system.getPhase(0).getComponent(0).getSolidVaporPressure(dependentValues[0]);
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return system.getPhase(0).getComponent(0).getSolidVaporPressure(dependentValues[0]);
    }

    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        if (i == 0) {
            system.getPhases()[0].getComponents()[0].setAntoineASolid(value);
        }
        if (i == 1) {
            system.getPhases()[0].getComponents()[0].setAntoineBSolid(value);
        }
        if (i == 2) {
            system.getPhases()[0].getComponents()[0].setAntoineCSolid(value);
        }
    }
}
