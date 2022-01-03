/*
 * ViscosityFunction.java
 *
 * Created on 24. januar 2001, 23:30
 */
package neqsim.physicalProperties.util.parameterFitting.pureComponentParameterFitting.pureCompViscosity.linearLiquidModel;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
/**
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ViscosityFunction extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new ViscosityFunction
     */
    public ViscosityFunction() {
    }

	/** {@inheritDoc} */
    @Override
	public double calcValue(double[] dependentValues) {
        system.init(1);
        system.initPhysicalProperties();
        return system.getPhases()[1].getPhysicalProperties().getViscosity() * 1e3;
    }

	/** {@inheritDoc} */
    @Override
	public void setFittingParams(int i, double value) {
        params[i] = value;
        system.getPhases()[0].getComponents()[0].setLiquidViscosityParameter(value, i);
        system.getPhases()[1].getComponents()[0].setLiquidViscosityParameter(value, i);
    }
}
