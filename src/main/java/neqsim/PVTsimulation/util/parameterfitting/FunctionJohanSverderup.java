/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */
package neqsim.PVTsimulation.util.parameterfitting;

import neqsim.PVTsimulation.simulation.SaturationPressure;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class FunctionJohanSverderup extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;

    double molarMass = 0.0;

    /**
     * Creates new Test
     */
    public FunctionJohanSverderup() {
        params = new double[1];
    }

    public double calcValue(double[] dependentValues) {
        system.addComponent("methane", -system.getPhase(0).getComponent("methane").getNumberOfmoles());
        system.addComponent("methane", params[0]);
        system.init_x_y();
        system.init(1);
        system.setPressure(system.getPressure() - 25.0);
        SaturationPressure satCalc = new SaturationPressure(system);
        double satPres = satCalc.calcSaturationPressure();

        return satPres;
    }

    public void setFittingParams(int i, double value) {
        params[i] = value;
    }
}