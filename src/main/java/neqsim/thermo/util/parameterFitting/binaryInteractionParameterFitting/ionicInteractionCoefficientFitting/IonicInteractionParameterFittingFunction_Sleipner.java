package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.ionicInteractionCoefficientFitting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.mixingRule.HVmixingRuleInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;

/**
 * <p>
 * IonicInteractionParameterFittingFunction_Sleipner class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class IonicInteractionParameterFittingFunction_Sleipner extends LevenbergMarquardtFunction
        implements ThermodynamicConstantsInterface {
    private static final long serialVersionUID = 1000;
    static Logger logger =
            LogManager.getLogger(IonicInteractionParameterFittingFunction_Sleipner.class);

    /**
     * <p>
     * Constructor for IonicInteractionParameterFittingFunction_Sleipner.
     * </p>
     */
    public IonicInteractionParameterFittingFunction_Sleipner() {}

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        try {
            thermoOps.bubblePointPressureFlash(false);
            // logger.info("pres " +
            // system.getPressure()*system.getPhases()[0].getComponent(0).getx());
        } catch (Exception e) {
            logger.error(e.toString());
        }

        // logger.info("pressure "+system.getPressure());
        return system.getPressure();
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
        int MDEAplusNumb = 0, MDEANumb = 0, CO2Numb = 0, HCO3Numb = 0, WaterNumb = 0, AcidNumb = 0,
                AcidnegNumb = 0;
        int j = 0;
        do {
            MDEAplusNumb = j;
            j++;
        } while (!system.getPhases()[1].getComponents()[j - 1].getComponentName().equals("MDEA+"));

        j = 0;
        do {
            MDEANumb = j;
            j++;
        } while (!system.getPhases()[1].getComponents()[j - 1].getComponentName().equals("MDEA"));
        j = 0;
        do {
            CO2Numb = j;
            j++;
        } while (!system.getPhases()[1].getComponents()[j - 1].getComponentName().equals("CO2"));

        j = 0;
        do {
            HCO3Numb = j;
            j++;
        } while (!system.getPhases()[1].getComponents()[j - 1].getComponentName().equals("HCO3-"));

        j = 0;
        do {
            WaterNumb = j;
            j++;
        } while (!system.getPhases()[1].getComponents()[j - 1].getComponentName().equals("water"));

        j = 0;
        do {
            AcidNumb = j;
            j++;
        } while (!system.getPhases()[1].getComponents()[j - 1].getComponentName()
                .equals("AceticAcid"));

        j = 0;
        do {
            AcidnegNumb = j;
            j++;
        } while (!system.getPhases()[1].getComponents()[j - 1].getComponentName().equals("Ac-"));
        // logger.info("Acetate
        // "+system.getPhase(1).getComponent(AcidnegNumb).getNumberOfmoles());
        // logger.info("HCO3- " + system.getPhase(1).getComponent(HCO3Numb).getx());
        // logger.info("Ac- " + system.getPhase(1).getComponent(AcidnegNumb).getx());
        // logger.info("HAc " + system.getPhase(1).getComponent(AcidNumb).getx());

        if (i == 1) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(AcidnegNumb, MDEAplusNumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(AcidnegNumb, MDEAplusNumb, value);
        }
        if (i == 0) {
            // ((ElectrolyteMixingRulesInterface)
            // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule()).setWijParameter(AcidnegNumb,
            // MDEAplusNumb,-3.12174e-4);
            // ((ElectrolyteMixingRulesInterface)
            // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule()).setWijParameter(AcidnegNumb,
            // MDEAplusNumb,-3.12174e-4);

            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, AcidNumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, AcidNumb, value);

            /*
             * double a1 = ((ComponentEosInterface)system.getPhases()[1].getComponent(AcidNumb)).
             * geta(); double a2 =
             * ((ComponentEosInterface)system.getPhases()[1].getComponent(MDEANumb)). geta(); double
             * b1 = ((ComponentEosInterface)system.getPhases()[1].getComponent(AcidNumb)). getb();
             * double b2 = ((ComponentEosInterface)system.getPhases()[1].getComponent(MDEANumb)).
             * getb(); double g11 = -a1/b1*Math.log(2); double g22 = -a2/b2*Math.log(2); double g12
             * = -2*Math.sqrt(b1*b2)/(b1+b2)*Math.sqrt(g11*g22)*(1-(0.2)); double para0 =
             * (g12-g22)/R; double para1 = (g12-g11)/R;
             * 
             * ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).
             * setHValphaParameter(AcidNumb,MDEANumb,0); ((HVmixingRuleInterface)
             * ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).
             * setHValphaParameter(AcidNumb,MDEANumb,0); ((HVmixingRuleInterface)
             * ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()). setHVDijParameter
             * (AcidNumb,MDEANumb,para0); ((HVmixingRuleInterface)
             * ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()). setHVDijParameter
             * (AcidNumb,MDEANumb,para0); ((HVmixingRuleInterface)
             * ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()). setHVDijParameter
             * (MDEANumb,AcidNumb,para1); ((HVmixingRuleInterface)
             * ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()). setHVDijParameter
             * (MDEANumb,AcidNumb,para1); ((HVmixingRuleInterface)
             * ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).
             * setHVDijTParameter(AcidNumb,MDEANumb,0); ((HVmixingRuleInterface)
             * ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).
             * setHVDijTParameter(AcidNumb,MDEANumb,0); ((HVmixingRuleInterface)
             * ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).
             * setHVDijTParameter(MDEANumb,AcidNumb,0); ((HVmixingRuleInterface)
             * ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).
             * setHVDijTParameter(MDEANumb,AcidNumb,0);
             */}
        if (i == 2) {
            double a1 =
                    ((ComponentEosInterface) system.getPhases()[1].getComponent(AcidNumb)).geta();
            double a2 =
                    ((ComponentEosInterface) system.getPhases()[1].getComponent(MDEANumb)).geta();
            double b1 =
                    ((ComponentEosInterface) system.getPhases()[1].getComponent(AcidNumb)).getb();
            double b2 =
                    ((ComponentEosInterface) system.getPhases()[1].getComponent(MDEANumb)).getb();
            double g11 = -a1 / b1 * Math.log(2);
            double g22 = -a2 / b2 * Math.log(2);
            double g12 = -2 * Math.sqrt(b1 * b2) / (b1 + b2) * Math.sqrt(g11 * g22) * (1 - value);
            double para0 = (g12 - g22) / R;
            double para1 = (g12 - g11) / R;

            // logger.info("para0 "+ para0);
            // logger.info("para1 "+ para1);

            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[0]).getMixingRule())
                    .setHValphaParameter(AcidNumb, MDEANumb, 0.0);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[1]).getMixingRule())
                    .setHValphaParameter(AcidNumb, MDEANumb, 0.0);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[0]).getMixingRule())
                    .setHVDijParameter(AcidNumb, MDEANumb, para0);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[1]).getMixingRule())
                    .setHVDijParameter(AcidNumb, MDEANumb, para0);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[0]).getMixingRule())
                    .setHVDijParameter(MDEANumb, AcidNumb, para1);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[1]).getMixingRule())
                    .setHVDijParameter(MDEANumb, AcidNumb, para1);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[0]).getMixingRule())
                    .setHVDijTParameter(AcidNumb, MDEANumb, 0.0);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[1]).getMixingRule())
                    .setHVDijTParameter(AcidNumb, MDEANumb, 0.0);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[0]).getMixingRule())
                    .setHVDijTParameter(MDEANumb, AcidNumb, 0.0);
            ((HVmixingRuleInterface) ((PhaseEosInterface) system.getPhases()[1]).getMixingRule())
                    .setHVDijTParameter(MDEANumb, AcidNumb, 0.0);
        }
    }
}
