/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */
package neqsim.PVTsimulation.util.parameterfitting;

import neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.FrictionTheoryViscosityMethod;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ViscosityFunction extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;

    double molarMass = 0.0;
    boolean includeWaxEmulsionViscosity = true;

    /**
     * Creates new Test
     */
    public ViscosityFunction() {
        params = new double[1];
    }

    /**
     * Creates new Test
     */
    public ViscosityFunction(boolean includeWax) {
        params = new double[1];
        includeWaxEmulsionViscosity = includeWax;
    }

    @Override
	public double calcValue(double[] dependentValues) {
        thermoOps.TPflash();
        system.initPhysicalProperties();
        double waxFraction = 0.0;
        if (system.hasPhaseType("wax") && includeWaxEmulsionViscosity) {
            waxFraction = system.getWtFraction(system.getPhaseNumberOfPhase("wax"));
            return system.getPhase(0).getPhysicalProperties().getViscosityOfWaxyOil(waxFraction, dependentValues[0]); // %wax
        }
        // system.display();
        return system.getPhase(0).getPhysicalProperties().getViscosity(); // %wax
    }

    @Override
	public void setFittingParams(int i, double value) {
        params[i] = value;

        ((FrictionTheoryViscosityMethod) system.getPhase(0).getPhysicalProperties().getViscosityModel())
                .setTBPviscosityCorrection(value);

    }
}
