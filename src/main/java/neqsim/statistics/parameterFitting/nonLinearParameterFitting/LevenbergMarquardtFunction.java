package neqsim.statistics.parameterFitting.nonLinearParameterFitting;

import neqsim.statistics.parameterFitting.BaseFunction;

/**
 * <p>LevenbergMarquardtFunction class.</p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class LevenbergMarquardtFunction extends BaseFunction {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for LevenbergMarquardtFunction.</p>
     */
    public LevenbergMarquardtFunction() {}

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

    /** {@inheritDoc} */
    @Override
    public double getFittingParams(int i) {
        return params[i];
    }

    /** {@inheritDoc} */
    @Override
    public double[] getFittingParams() {
        return params;
    }

    /** {@inheritDoc} */
    @Override
    public int getNumberOfFittingParams() {
        return params.length;
    }

    /**
     * <p>setFittingParams.</p>
     *
     * @param value an array of {@link double} objects
     */
    public void setFittingParams(double[] value) {
        System.arraycopy(value, 0, params, 0, value.length);
    }

    /**
     * <p>setFittingParam.</p>
     *
     * @param parameterNumber a int
     * @param parameterVal a double
     */
    public void setFittingParam(int parameterNumber, double parameterVal) {
        params[parameterNumber] = parameterVal;
    }
}
