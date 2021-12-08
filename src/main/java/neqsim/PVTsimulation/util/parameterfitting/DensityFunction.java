package neqsim.PVTsimulation.util.parameterfitting;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class DensityFunction extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;

    double molarMass = 0.0;

    public DensityFunction() {
        params = new double[1];
    }

    @Override
    public double calcValue(double[] dependentValues) {
        system.setTemperature(dependentValues[0]);
        thermoOps.TPflash();
        system.initPhysicalProperties();

        // system.display();
        return system.getPhase(0).getPhysicalProperties().getDensity();
    }

    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
    }
}
