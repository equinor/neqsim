/*
 * RacketFunction.java
 *
 * Created on 24. januar 2001, 21:15
 */
package neqsim.physicalProperties.util.parameterFitting.pureComponentParameterFitting.pureCompDensity.pureComponentRacketVolumeCorrectionParameterFitting;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 * <p>
 * RacketFunction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class RacketFunction extends LevenbergMarquardtFunction {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for RacketFunction.
     * </p>
     */
    public RacketFunction() {
        params = new double[1];
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        system.setTemperature(dependentValues[0]);
        system.init(1);
        system.initPhysicalProperties();
        return system.getPhases()[1].getPhysicalProperties().getDensity();
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        system.getPhases()[0].getComponents()[i].setRacketZ(value);
        system.getPhases()[1].getComponents()[i].setRacketZ(value);
    }
}
