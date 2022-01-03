
// To find the Rackett constant for Water and MDEA
package neqsim.thermo.util.parameterFitting.Procede.Density;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 * <p>RackettZ class.</p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class RackettZ extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;


    /**
     * <p>Constructor for RackettZ.</p>
     */
    public RackettZ() {
        params = new double[1];
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        system.initPhysicalProperties();
        return system.getPhase(1).getPhysicalProperties().getDensity();
    }

    /** {@inheritDoc} */
    @Override
    public double calcTrueValue(double val) {
        return val;
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        if (i == 0) {
            system.getPhases()[0].getComponents()[0].setRacketZ(value);
            system.getPhases()[1].getComponents()[0].setRacketZ(value);
        }
    }

}
