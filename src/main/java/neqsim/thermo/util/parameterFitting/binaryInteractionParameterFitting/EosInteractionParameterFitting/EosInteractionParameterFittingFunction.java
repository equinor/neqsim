package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.EosInteractionParameterFitting;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.phase.PhaseEosInterface;

/**
 * <p>
 * EosInteractionParameterFittingFunction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class EosInteractionParameterFittingFunction extends LevenbergMarquardtFunction {
    /**
     * <p>
     * Constructor for EosInteractionParameterFittingFunction.
     * </p>
     */
    public EosInteractionParameterFittingFunction() {
        params = new double[1];
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        double calcK = 0;
        double expK = 0;
        expK = dependentValues[1] / dependentValues[0];

        system.init(0);
        system.getPhases()[1].getComponents()[0].setx(dependentValues[0]);
        system.getPhases()[1].getComponents()[1].setx(1.0 - dependentValues[0]);
        system.getPhases()[0].getComponents()[0].setx(dependentValues[1]);
        system.getPhases()[0].getComponents()[1].setx(1.0 - dependentValues[1]);
        system.init(1);
        system.getPhases()[0].getComponents()[0].setK(Math.exp(
                Math.log(system.getPhases()[1].getComponents()[0].getFugasityCoeffisient()) - Math
                        .log(system.getPhases()[0].getComponents()[0].getFugasityCoeffisient())));
        system.getPhases()[1].getComponents()[0].setK(Math.exp(
                Math.log(system.getPhases()[1].getComponents()[0].getFugasityCoeffisient()) - Math
                        .log(system.getPhases()[0].getComponents()[0].getFugasityCoeffisient())));
        calcK = system.getPhases()[0].getComponents()[0].getK();

        double diff = expK - calcK;
        // System.out.println("diff: " + diff);
        return diff;
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        ((PhaseEosInterface) system.getPhases()[0]).getMixingRule().setBinaryInteractionParameter(0,
                1, value);
        ((PhaseEosInterface) system.getPhases()[1]).getMixingRule().setBinaryInteractionParameter(0,
                1, value);
    }
}
