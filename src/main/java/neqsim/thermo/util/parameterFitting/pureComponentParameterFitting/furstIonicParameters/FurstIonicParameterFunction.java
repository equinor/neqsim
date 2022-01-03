package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.furstIonicParameters;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;

/**
 * <p>FurstIonicParameterFunction class.</p>
 *
 * @author Even Solbraa
 */
public class FurstIonicParameterFunction extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;


    /**
     * <p>Constructor for FurstIonicParameterFunction.</p>
     */
    public FurstIonicParameterFunction() {
        // params = new double[3];
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        // system.init(0);
        system.init(1);
        return system.getPhase(1).getOsmoticCoefficient(
                system.getPhase(1).getComponent("water").getComponentNumber());
        // return system.getPhase(1).getOsmoticCoefficientOfWater();
        // return system.getPhase(1).getMeanIonicActivity(1,2);
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
        neqsim.thermo.util.constants.FurstElectrolyteConstants.setFurstParam(i, value);
        ((PhaseModifiedFurstElectrolyteEos) system.getPhase(0)).reInitFurstParam();
        ((PhaseModifiedFurstElectrolyteEos) system.getPhase(1)).reInitFurstParam();
    }
}
