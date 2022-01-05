package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.CharacterisationParameters;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 * <p>
 * CharacterisationFunction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class CharacterisationFunction extends LevenbergMarquardtFunction {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(CharacterisationFunction.class);

    /**
     * <p>
     * Constructor for CharacterisationFunction.
     * </p>
     */
    public CharacterisationFunction() {
        params = new double[1];
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        system.setTemperature(dependentValues[0]);
        system.init(0);
        system.init(1);
        try {
            thermoOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return Math.log(system.getPressure());
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;

        // system.getCharacterization().getTBPModel();
        system.getPhases()[0].getComponents()[i].setAcentricFactor(value);
        system.getPhases()[1].getComponents()[i].setAcentricFactor(value);
    }
}
