/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */
//To find the Rackett constant for Water and MDEA
package neqsim.thermo.util.parameterFitting.Procede.Density;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class RackettZ extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;

    /** Creates new Test */
    public RackettZ() {
        params = new double[1];
    }

    public double calcValue(double[] dependentValues) {
        system.initPhysicalProperties();
        return system.getPhase(1).getPhysicalProperties().getDensity();
    }

    public double calcTrueValue(double val) {
        return val;
    }

    public void setFittingParams(int i, double value) {
        params[i] = value;
        if (i == 0) {
            system.getPhases()[0].getComponents()[0].setRacketZ(value);
            system.getPhases()[1].getComponents()[0].setRacketZ(value);
        }
    }

}