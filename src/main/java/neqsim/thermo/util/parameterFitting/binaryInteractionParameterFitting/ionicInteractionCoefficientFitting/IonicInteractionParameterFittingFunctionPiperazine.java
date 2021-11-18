package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.ionicInteractionCoefficientFitting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class IonicInteractionParameterFittingFunctionPiperazine extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;
    static Logger logger =
            LogManager.getLogger(IonicInteractionParameterFittingFunctionPiperazine.class);


    public IonicInteractionParameterFittingFunctionPiperazine() {}

    @Override
    public double calcValue(double[] dependentValues) {
        try {
            thermoOps.bubblePointPressureFlash(false);
            // System.out.println("pres " +
            // system.getPressure()*system.getPhases()[0].getComponent(0).getx());
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return system.getPressure() * system.getPhases()[0].getComponent(0).getx();
    }

    @Override
    public double calcTrueValue(double val) {
        return val;
    }

    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        int PiperazineplusNumb = 0, CO2Numb = 0, HCO3numb = 0, Waternumb = 0, PiperazineNumb = 0;
        int j = 0;
        do {
            PiperazineNumb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName()
                .equals("Piperazine"));

        j = 0;
        do {
            PiperazineplusNumb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName()
                .equals("Piperazine+"));

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
        // System.out.println("water numb " + Waternumb);
        // System.out.println("co2 numb " + CO2Numb);
        // System.out.println("MDEA numb " + MDEANumb);
        // System.out.println("HCO3numb numb " + HCO3numb);
        // System.out.println("MDEAplusNumb numb " + MDEAplusNumb);

        if (i == 0) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(PiperazineplusNumb, CO2Numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(PiperazineplusNumb, CO2Numb, value);
        }
        if (i == 1) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(PiperazineplusNumb, PiperazineNumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(PiperazineplusNumb, PiperazineNumb, value);
        }
        // if(i==1){
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule()).setWijParameter(PiperazineplusNumb,
        // Waternumb, value);
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule()).setWijParameter(PiperazineplusNumb,
        // Waternumb, value);
        //
        // if((ElectrolyteMixingRulesInterface)((PhaseModifiedFurstElectrolyteEos)((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getRefPhase(PiperazineplusNumb)).getElectrolyteMixingRule()!=null){
        // //((ElectrolyteMixingRulesInterface)((PhaseModifiedFurstElectrolyteEos)((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getRefPhase(PiperazineplusNumb)).getElectrolyteMixingRule()).setWijParameter(0,
        // 1, value);
        // ((ElectrolyteMixingRulesInterface)((PhaseModifiedFurstElectrolyteEos)((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getRefPhase(PiperazineplusNumb)).getElectrolyteMixingRule()).setWijParameter(0,
        // 1, value);
        // }
        // }
        // if(i==2){
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule()).setWijParameter(PiperazineplusNumb,HCO3numb,
        // value);
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule()).setWijParameter(PiperazineplusNumb,HCO3numb,
        // value);
        // }
        // if(i==42){
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule()).setWijParameter(H3OplusNumb,
        // MDEANumb, value);
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule()).setWijParameter(H3OplusNumb,
        // MDEANumb, value);
        //
        // }
        //
        //
        //
        //
        // if(i==4){
        // ((PhaseEosInterface)system.getPhases()[0]).getMixingRule().setBinaryInteractionParameter(CO2Numb,MDEANumb,
        // value*1e3);
        // ((PhaseEosInterface)system.getPhases()[1]).getMixingRule().setBinaryInteractionParameter(CO2Numb,MDEANumb,
        // value*1e3);
        // }
        //
        // if(i==20){
        // system.getPhase(0).getComponent(MDEAplusNumb).setStokesCationicDiameter(value);
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).reInitFurstParam();
        // system.getPhase(1).getComponent(MDEAplusNumb).setStokesCationicDiameter(value);
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).reInitFurstParam();
        // }
        // if(i==40){
        // system.getChemicalReactionOperations().getReactionList().getReaction(0).setK(0,value);
        // }
        // if(i==50){
        // system.getChemicalReactionOperations().getReactionList().getReaction(0).setK(1,value);
        // }
        // if(i==60){
        // system.getChemicalReactionOperations().getReactionList().getReaction(0).setK(2,value);
        // }
        //
        //
        //
        // if(i==10){
        // ((HVmixingRuleInterface)
        // ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).setHVDijParameter(CO2Numb,MDEANumb,
        // value);
        // ((HVmixingRuleInterface)
        // ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).setHVDijParameter(CO2Numb,MDEANumb,
        // value);
        // }
        // if(i==40){
        // ((HVmixingRuleInterface)
        // ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).setHVDijParameter(MDEANumb,
        // CO2Numb, value*1e8);
        // ((HVmixingRuleInterface)
        // ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).setHVDijParameter(MDEANumb,
        // CO2Numb, value*1e8);
        // }
        // if(i==10){
        // ((HVmixingRuleInterface)
        // ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).setHVDijTParameter(CO2Numb,MDEANumb,
        // value);
        // ((HVmixingRuleInterface)
        // ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).setHVDijTParameter(CO2Numb,MDEANumb,
        // value);
        // }
        // if(i==50){
        // ((HVmixingRuleInterface)
        // ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).setHVDijTParameter(MDEANumb,
        // CO2Numb, value*1e8);
        // ((HVmixingRuleInterface)
        // ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).setHVDijTParameter(MDEANumb,
        // CO2Numb, value*1e8);
        // }
        //
        // // Temp der 1
        // if(i==50){
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule()).setWijT1Parameter(MDEAplusNumb,MDEANumb,
        // value);
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule()).setWijT1Parameter(MDEAplusNumb,MDEANumb,
        // value);
        // }
        // if(i==40){
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule()).setWijT1Parameter(MDEAplusNumb,CO2Numb,
        // value);
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule()).setWijT1Parameter(MDEAplusNumb,CO2Numb,
        // value);
        // }
        // if(i==60){
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule()).setWijT1Parameter(MDEAplusNumb,HCO3numb,
        // value);
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule()).setWijT1Parameter(MDEAplusNumb,HCO3numb,
        // value);
        // }
        // if(i==7){
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule()).setWijT1Parameter(MDEAplusNumb,
        // Waternumb, value);
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule()).setWijT1Parameter(MDEAplusNumb,
        // Waternumb, value);
        // }
        //
        //
        // // Temp der 2
        // if(i==66){
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule()).setWijT2Parameter(MDEAplusNumb,MDEANumb,
        // value);
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule()).setWijT2Parameter(MDEAplusNumb,MDEANumb,
        // value);
        // }
        // if(i==20){
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule()).setWijT2Parameter(MDEAplusNumb,CO2Numb,
        // value);
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule()).setWijT2Parameter(MDEAplusNumb,CO2Numb,
        // value);
        // }
        // if(i==76){
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule()).setWijT2Parameter(MDEAplusNumb,HCO3numb,
        // value);
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule()).setWijT2Parameter(MDEAplusNumb,HCO3numb,
        // value);
        // }
        // if(i==86){
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule()).setWijT2Parameter(MDEAplusNumb,
        // Waternumb, value);
        // ((ElectrolyteMixingRulesInterface)
        // ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule()).setWijT2Parameter(MDEAplusNumb,
        // Waternumb, value);
        // }
    }

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
        if (i == 1) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, CO2Numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT1Parameter(MDEAplusNumb, CO2Numb, value);
        }
        if (i == 10) {
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
