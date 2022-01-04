package neqsim.statistics.monteCarloSimulation.util.example;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 * <p>TestFunction class.</p>
 *
 * @author Even Solbraa
 * @since 2.2.3
 * @version $Id: $Id
 */
public class TestFunction extends LevenbergMarquardtFunction {
    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        return 3.0 * params[0] * params[1] - 2.0 * params[0] * dependentValues[0]
                - dependentValues[0];
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
    }
}
