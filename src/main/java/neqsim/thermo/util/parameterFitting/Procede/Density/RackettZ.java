
// To find the Rackett constant for Water and MDEA
package neqsim.thermo.util.parameterFitting.Procede.Density;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class RackettZ extends LevenbergMarquardtFunction {
    private static final long serialVersionUID = 1000;


    public RackettZ() {
        params = new double[1];
    }

    @Override
    public double calcValue(double[] dependentValues) {
        system.initPhysicalProperties();
        return system.getPhase(1).getPhysicalProperties().getDensity();
    }

    @Override
    public double calcTrueValue(double val) {
        return val;
    }

    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        if (i == 0) {
            system.getPhases()[0].getComponents()[0].setRacketZ(value);
            system.getPhases()[1].getComponents()[0].setRacketZ(value);
        }
    }
}
