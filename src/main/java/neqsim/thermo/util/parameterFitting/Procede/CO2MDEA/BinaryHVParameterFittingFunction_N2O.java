package neqsim.thermo.util.parameterFitting.Procede.CO2MDEA;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.mixingRule.HVmixingRuleInterface;
import neqsim.thermo.phase.PhaseEosInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class BinaryHVParameterFittingFunction_N2O extends LevenbergMarquardtFunction {
    private static final long serialVersionUID = 1000;
    int type = 0;
    int phase = 0;
    static Logger logger = LogManager.getLogger(BinaryHVParameterFittingFunction_N2O.class);

    public BinaryHVParameterFittingFunction_N2O() {}

    public BinaryHVParameterFittingFunction_N2O(int phase, int type) {
        this.phase = phase;
        this.type = type;
    }

    @Override
    public double calcValue(double[] dependentValues) {
        try {
            thermoOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return (system.getPressure() * system.getPhases()[0].getComponent(0).getx()
                * system.getPhase(0).getComponent(0).getFugasityCoeffisient());
    }

    @Override
    public double calcTrueValue(double val) {
        return (val);
    }

    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;

        if (i == 0) {
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[0]).getMixingRule())
                    .setHVDijParameter(0, 2, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[1]).getMixingRule())
                    .setHVDijParameter(0, 2, value);
        }
        if (i == 1) {
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[0]).getMixingRule())
                    .setHVDijParameter(2, 0, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[1]).getMixingRule())
                    .setHVDijParameter(2, 0, value);
        }

        if (i == 4) {
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[0]).getMixingRule())
                    .setHValphaParameter(0, 2, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[1]).getMixingRule())
                    .setHValphaParameter(0, 2, value);
        }

        if (i == 2) {
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[0]).getMixingRule())
                    .setHVDijTParameter(0, 2, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[1]).getMixingRule())
                    .setHVDijTParameter(0, 2, value);
        }
        if (i == 3) {
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[0]).getMixingRule())
                    .setHVDijTParameter(2, 0, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[1]).getMixingRule())
                    .setHVDijTParameter(2, 0, value);
        }

        /*
         * ((PhaseEosInterface)system.getPhases()[0]).getMixingRule().
         * setBinaryInteractionParameter(0,2, value);
         * ((PhaseEosInterface)system.getPhases()[1]).getMixingRule().
         * setBinaryInteractionParameter(0,2, value);
         */
    }
}
