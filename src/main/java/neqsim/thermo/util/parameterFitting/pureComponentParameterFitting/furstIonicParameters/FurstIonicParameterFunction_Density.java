/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.furstIonicParameters;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class FurstIonicParameterFunction_Density extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;

    /** Creates new Test */
    public FurstIonicParameterFunction_Density() {
        // params = new double[3];
    }

    @Override
	public double calcValue(double[] dependentValues) {
        system.init(0);
        system.init(1);
        system.initPhysicalProperties();
        // return system.getPhase(1).getOsmoticCoefficientOfWater();
        return system.getPhase(1).getPhysicalProperties().getDensity();
    }

    @Override
	public double calcTrueValue(double val) {
        return val;
    }

    @Override
	public void setFittingParams(int i, double value) {
        params[i] = value;
        neqsim.thermo.util.constants.FurstElectrolyteConstants.setFurstParam(i, value);
        ((PhaseModifiedFurstElectrolyteEos) system.getPhase(0)).reInitFurstParam();
        ((PhaseModifiedFurstElectrolyteEos) system.getPhase(1)).reInitFurstParam();
    }
}