package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.acentricFactorFitting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class AcentricFunctionScwartzentruber extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(AcentricFunctionScwartzentruber.class);


    public AcentricFunctionScwartzentruber() {
        params = new double[3];
    }

    @Override
    public double calcValue(double[] dependentValues) {
        // System.out.println("dep " + dependentValues[0]);
        system.setTemperature(dependentValues[0]);
        system.setPressure(system.getPhases()[0].getComponents()[0]
                .getAntoineVaporPressure(dependentValues[0]));
        // System.out.println("antoine pres: " + system.getPressure());
        system.init(0);
        system.init(1);
        try {
            thermoOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            logger.error(e.toString());
        }
        // System.out.println("pres: " + system.getPressure());
        return Math.log(system.getPressure());
    }

    @Override
    public double calcTrueValue(double val) {
        return Math.exp(val);
    }

    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        system.getPhases()[0].getComponents()[0].setSchwartzentruberParams(i, value);
        system.getPhases()[1].getComponents()[0].setSchwartzentruberParams(i, value);
        system.getPhases()[0].getComponents()[0].getAtractiveTerm().setParameters(i, value);
        system.getPhases()[1].getComponents()[0].getAtractiveTerm().setParameters(i, value);
    }
}
