package neqsim.statistics.parameterFitting.util.example;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestFunction extends LevenbergMarquardtFunction {
    public TestFunction() {}

    @Override
    public double calcValue(double[] dependentValues) {
        return 3.0 * params[0] * params[1] - 2.0 * params[0] * dependentValues[0]
                - dependentValues[0];
    }

    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
    }
}
