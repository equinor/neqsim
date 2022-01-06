package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.ionicInteractionCoefficientFitting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.mixingRule.HVmixingRuleInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;

/**
 * <p>
 * IonicInteractionParameterFittingFunction_CO2 class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class IonicInteractionParameterFittingFunction_CO2 extends LevenbergMarquardtFunction {
    static Logger logger = LogManager.getLogger(IonicInteractionParameterFittingFunction_CO2.class);

    /**
     * <p>
     * Constructor for IonicInteractionParameterFittingFunction_CO2.
     * </p>
     */
    public IonicInteractionParameterFittingFunction_CO2() {}

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        try {
            thermoOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return system.getPressure() * system.getPhases()[0].getComponent(0).getx();
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
        int MDEAplusNumb = 0, MDEANumb = 0, CO2Numb = 0, HCO3numb = 0, Waternumb = 0;
        int j = 0;
        do {
            MDEAplusNumb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName().equals("MDEA+"));
        j = 0;

        do {
            MDEANumb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName().equals("MDEA"));
        j = 0;
        do {
            CO2Numb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName().equals("CO2"));
        j = 0;

        do {
            HCO3numb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName().equals("HCO3-"));
        j = 0;
        do {
            Waternumb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName().equals("water"));

        if (i == 1) {
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[0]).getMixingRule())
                    .setHVDijParameter(CO2Numb, MDEANumb, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[1]).getMixingRule())
                    .setHVDijParameter(CO2Numb, MDEANumb, value);
        }
        if (i == 0) {
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[0]).getMixingRule())
                    .setHVDijParameter(MDEANumb, CO2Numb, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[1]).getMixingRule())
                    .setHVDijParameter(MDEANumb, CO2Numb, value);
        }
        if (i == 2) {
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[0]).getMixingRule())
                    .setHVDijTParameter(CO2Numb, MDEANumb, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[1]).getMixingRule())
                    .setHVDijTParameter(CO2Numb, MDEANumb, value);
        }
        if (i == 3) {
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[0]).getMixingRule())
                    .setHVDijTParameter(MDEANumb, CO2Numb, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[1]).getMixingRule())
                    .setHVDijTParameter(MDEANumb, CO2Numb, value);
        }
        if (i == 4) {
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[0]).getMixingRule())
                    .setHValphaParameter(CO2Numb, MDEANumb, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[1]).getMixingRule())
                    .setHValphaParameter(CO2Numb, MDEANumb, value);
        }
        if (i == 5) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, CO2Numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, CO2Numb, value);
        }
        // if(i==2){
        // ((HVmixingRuleInterface)
        // ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).setHVDijTParameter(CO2Numb,MDEANumb,
        // value);
        // ((HVmixingRuleInterface)
        // ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).setHVDijTParameter(CO2Numb,MDEANumb,
        // value);
        // }
        // if(i==3){
        // ((HVmixingRuleInterface)
        // ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).setHVDijTParameter(MDEANumb,
        // CO2Numb, value);
        // ((HVmixingRuleInterface)
        // ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).setHVDijTParameter(MDEANumb,
        // CO2Numb, value);
        // }

        if (i == 30) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, MDEANumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, MDEANumb, value);
        }
        if (i == 20) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, CO2Numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, CO2Numb, value);
        }
        if (i == 66) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, HCO3numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, HCO3numb, value);
        }
        if (i == 56) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, Waternumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, Waternumb, value);
        }

        // Temp der 1
        if (i == 30) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, MDEANumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, MDEANumb, value);
        }
        if (i == 20) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, CO2Numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, CO2Numb, value);
        }
        if (i == 40) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, HCO3numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, HCO3numb, value);
        }
        if (i == 50) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, Waternumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, Waternumb, value);
        }

        // Temp der 2
        if (i == 60) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, MDEANumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, MDEANumb, value);
        }
        if (i == 20) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, CO2Numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, CO2Numb, value);
        }
        if (i == 7) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, HCO3numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, HCO3numb, value);
        }
        if (i == 8) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, Waternumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, Waternumb, value);
        }
    }

    /**
     * <p>
     * setFittingParams5.
     * </p>
     *
     * @param i a int
     * @param value a double
     */
    public void setFittingParams5(int i, double value) {
        params[i] = value;
        int MDEAplusNumb = 0, MDEANumb = 0, CO2Numb = 0, HCO3numb = 0, Waternumb = 0;
        int j = 0;
        do {
            MDEAplusNumb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName()
                .equals("MDEAplus"));
        j = 0;

        do {
            MDEANumb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName().equals("MDEA"));
        j = 0;
        do {
            CO2Numb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName().equals("CO2"));
        j = 0;

        do {
            HCO3numb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName()
                .equals("HCO3minus"));
        j = 0;
        do {
            Waternumb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName().equals("water"));

        // Temp der 1
        if (i == 0) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, MDEANumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, MDEANumb, value);
        }
        if (i == 3) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, CO2Numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, CO2Numb, value);
        }
        if (i == 1) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, HCO3numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, HCO3numb, value);
        }
        if (i == 2) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, Waternumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, Waternumb, value);
        }

        // Temp der 2
        if (i == 23) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, MDEANumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, MDEANumb, value);
        }
        if (i == 20) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, CO2Numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, CO2Numb, value);
        }
        if (i == 4) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, HCO3numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, HCO3numb, value);
        }
        if (i == 5) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, Waternumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT2Parameter(MDEAplusNumb, Waternumb, value);
        }
    }

    /**
     * <p>
     * setFittingParams3.
     * </p>
     *
     * @param i a int
     * @param value a double
     */
    public void setFittingParams3(int i, double value) {
        params[i] = value;
        int MDEAplusNumb = 0, MDEANumb = 0, CO2Numb = 0, HCO3numb = 0, Waternumb = 0;
        int j = 0;
        do {
            MDEAplusNumb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName()
                .equals("MDEAplus"));
        j = 0;

        do {
            MDEANumb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName().equals("MDEA"));
        j = 0;
        do {
            CO2Numb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName().equals("CO2"));
        j = 0;

        do {
            HCO3numb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName()
                .equals("HCO3minus"));
        j = 0;
        do {
            Waternumb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName().equals("water"));

        if (i == 0) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, CO2Numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, CO2Numb, value);
        }
    }
}
