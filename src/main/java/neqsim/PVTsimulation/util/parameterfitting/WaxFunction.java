package neqsim.PVTsimulation.util.parameterfitting;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 * <p>
 * WaxFunction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class WaxFunction extends LevenbergMarquardtFunction {
    private static final long serialVersionUID = 1000;

    double molarMass = 0.0;

    /**
     * <p>
     * Constructor for WaxFunction.
     * </p>
     */
    public WaxFunction() {
        params = new double[3];
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        system.setTemperature(dependentValues[0]);
        thermoOps.TPflash();
        double waxFraction = 0.0;
        if (system.hasPhaseType("wax")) {
            waxFraction = system.getWtFraction(system.getPhaseNumberOfPhase("wax"));
        }
        // system.display();
        return waxFraction * 100.0; // %wax
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        if (i < 3) {
            system.getWaxModel().setWaxParameter(i, params[i]);
        } else if (i == 3) {
            system.getWaxModel().setParameterWaxHeatOfFusion(i - 3, value);
        } else {
            system.getWaxModel().setParameterWaxTriplePointTemperature(i - 4, value);
        }
        system.getWaxModel().removeWax();
        system.getWaxModel().addTBPWax();
    }
}
