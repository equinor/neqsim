package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.EosInteractionParameterFitting;

import neqsim.thermo.phase.PhaseEosInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class BinaryCPAparameterFitToActivityCoefficientFunction
        extends EosInteractionParameterFittingFunction {

    private static final long serialVersionUID = 1000;

    public BinaryCPAparameterFitToActivityCoefficientFunction() {}

    @Override
    public double calcValue(double[] dependentValues) {
        system.init(0);
        system.init(1);

        // double fug =
        // system.getPhases()[1].getComponents()[0].getFugasityCoeffisient();
        // double pureFug = system.getPhases()[1].getPureComponentFugacity(0);
        double val = system.getPhases()[1].getActivityCoefficient(1);
        // System.out.println("activity: " + val);
        return val;
    }

    @Override
    public double calcTrueValue(double val) {
        return val;
    }

    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        if (i == 10) {
            ((PhaseEosInterface) system.getPhases()[0]).getMixingRule()
                    .setBinaryInteractionParameter(0, 1, value);
            ((PhaseEosInterface) system.getPhases()[1]).getMixingRule()
                    .setBinaryInteractionParameter(0, 1, value);
        }
        if (i == 2) {
            ((PhaseEosInterface) system.getPhases()[0]).getMixingRule()
                    .setBinaryInteractionParameterT1(0, 1, value);
            ((PhaseEosInterface) system.getPhases()[1]).getMixingRule()
                    .setBinaryInteractionParameterT1(0, 1, value);
        }

        if (i == 0) {
            ((PhaseEosInterface) system.getPhases()[0]).getMixingRule()
                    .setBinaryInteractionParameterij(0, 1, value);
            ((PhaseEosInterface) system.getPhases()[1]).getMixingRule()
                    .setBinaryInteractionParameterij(0, 1, value);
        }

        if (i == 1) {
            ((PhaseEosInterface) system.getPhases()[0]).getMixingRule()
                    .setBinaryInteractionParameterji(0, 1, value);
            ((PhaseEosInterface) system.getPhases()[1]).getMixingRule()
                    .setBinaryInteractionParameterji(0, 1, value);
        }
    }
}
