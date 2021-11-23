package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.AntoineParameter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class AntoineSolidFunction extends LevenbergMarquardtFunction {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(AntoineSolidFunction.class);

    public AntoineSolidFunction() {
        params = new double[2];
    }

    @Override
    public double calcValue(double[] dependentValues) {
        system.init(0);
        try {
            thermoOps.freezingPointTemperatureFlash();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return system.getTemperature();
    }

    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        if (i == 1) {
            system.getPhases()[0].getComponents()[0].setAntoineASolid(value);
            system.getPhases()[1].getComponents()[0].setAntoineASolid(value);
            system.getPhases()[2].getComponents()[0].setAntoineASolid(value);
            system.getPhases()[3].getComponents()[0].setAntoineASolid(value);
        }
        if (i == 0) {
            system.getPhases()[0].getComponents()[0].setAntoineBSolid(value);
            system.getPhases()[1].getComponents()[0].setAntoineBSolid(value);
            system.getPhases()[2].getComponents()[0].setAntoineBSolid(value);
            system.getPhases()[3].getComponents()[0].setAntoineBSolid(value);
        }
    }
}
