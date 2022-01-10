package neqsim.physicalProperties.util.parameterFitting.pureComponentParameterFitting.pureCompViscosity.chungMethod;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 * <p>
 * ChungFunction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ChungFunction extends LevenbergMarquardtFunction {
    /**
     * <p>
     * Constructor for ChungFunction.
     * </p>
     */
    public ChungFunction() {
        params = new double[1];
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        system.setTemperature(dependentValues[0]);
        system.init(1);
        system.initPhysicalProperties();
        return system.getPhases()[1].getPhysicalProperties().getViscosity();
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        system.getPhases()[0].getComponents()[i].setViscosityAssociationFactor(value);
        system.getPhases()[1].getComponents()[i].setViscosityAssociationFactor(value);
    }
}
