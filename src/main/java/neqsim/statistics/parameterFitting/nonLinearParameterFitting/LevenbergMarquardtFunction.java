package neqsim.statistics.parameterFitting.nonLinearParameterFitting;

import neqsim.statistics.parameterFitting.BaseFunction;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class LevenbergMarquardtFunction extends BaseFunction {

    private static final long serialVersionUID = 1000;

    public LevenbergMarquardtFunction() {}

    @Override
    public double calcValue(double[] dependentValues) {
        return 3.0 * params[0] * params[1] - 2.0 * params[0] * dependentValues[0]
                - dependentValues[0];
    }

    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
    }

    @Override
    public double getFittingParams(int i) {
        return params[i];
    }

    @Override
    public double[] getFittingParams() {
        return params;
    }

    @Override
    public int getNumberOfFittingParams() {
        return params.length;
    }

    public void setFittingParams(double[] value) {
        System.arraycopy(value, 0, params, 0, value.length);
    }

    public void setFittingParam(int parameterNumber, double parameterVal) {
        params[parameterNumber] = parameterVal;
    }
}
