/*
 * ViscosityFunction.java
 *
 * Created on 24. januar 2001, 23:30
 */

package neqsim.physicalProperties.util.parameterFitting.pureComponentParameterFitting.pureCompConductivity.linearLiquidModel;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 * /**
 *
 * @author Even Solbraa
 * @version
 */
public class ConductivityFunction extends LevenbergMarquardtFunction {
    private static final long serialVersionUID = 1000;

    /** Creates new ViscosityFunction */
    public ConductivityFunction() {
        params = new double[3];
    }

    @Override
    public double calcValue(double[] dependentValues) {
        system.setTemperature(dependentValues[0]);
        system.init(1);
        system.initPhysicalProperties();
        return system.getPhases()[1].getPhysicalProperties().getConductivity();
    }

    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        system.getPhases()[0].getComponents()[0].setLiquidConductivityParameter(value, i);
        system.getPhases()[1].getComponents()[0].setLiquidConductivityParameter(value, i);
    }
}
