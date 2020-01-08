/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.ionicInteractionCoefficientFitting;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.mixingRule.HVmixingRuleInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;
import org.apache.logging.log4j.*;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class IonicInteractionParameterFittingFunctionCH4 extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(IonicInteractionParameterFittingFunctionCH4.class);
    
    /** Creates new Test */
    public IonicInteractionParameterFittingFunctionCH4() {
    }
    
    public double calcValue(double[] dependentValues){
        try{
            thermoOps.bubblePointPressureFlash(false);
            //logger.info("pres " + system.getPressure()*system.getPhases()[0].getComponent(0).getx());
        }
        catch(Exception e){
            logger.error(e.toString());
        }
        return system.getPressure();
    }
    
    public double calcTrueValue(double val){
        return val;
    }
    
    public void setFittingParams(int i, double value){
        params[i] = value;
        int MDEAplusNumb=0, MDEANumb=0, methanenumb=0, CO2Numb=0, HCO3numb=0, Waternumb=0, methane=0;
        int j=0;
        do{
            MDEAplusNumb = j;
            j++;
        }
        while(!system.getPhases()[0].getComponents()[j-1].getComponentName().equals("MDEA+"));
        j=0;
        
        do{
            MDEANumb = j;
            j++;
        }
        while(!system.getPhases()[0].getComponents()[j-1].getComponentName().equals("MDEA"));
        j=0;
        do{
            CO2Numb = j;
            j++;
        }
        while(!system.getPhases()[0].getComponents()[j-1].getComponentName().equals("CO2"));
        j=0;
        
        do{
            HCO3numb = j;
            j++;
        }
        while(!system.getPhases()[0].getComponents()[j-1].getComponentName().equals("HCO3-"));
        j=0;
        do{
            Waternumb = j;
            j++;
        }
        while(!system.getPhases()[0].getComponents()[j-1].getComponentName().equals("water"));
        j=0;
        do{
            methanenumb = j;
            j++;
        }
        while(!system.getPhases()[0].getComponents()[j-1].getComponentName().equals("methane"));
        if(i==10){
            ((PhaseEosInterface)system.getPhases()[0]).getMixingRule().setBinaryInteractionParameter(methane,CO2Numb, value);
            ((PhaseEosInterface)system.getPhases()[1]).getMixingRule().setBinaryInteractionParameter(methane,CO2Numb, value);
        }
        if(i==1){
            ((PhaseEosInterface)system.getPhases()[0]).getMixingRule().setBinaryInteractionParameter(methane,MDEANumb, value*1e3);
            ((PhaseEosInterface)system.getPhases()[1]).getMixingRule().setBinaryInteractionParameter(methane,MDEANumb, value*1e3);
        }
        if(i==2){
            ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule().setWijT1Parameter(MDEAplusNumb,methane, value);
            ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule().setWijT1Parameter(MDEAplusNumb,methane, value);
        }
        if(i==0){
            ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,methane, value);
            ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,methane, value);
        }
        if(i==11){
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).setHVDijParameter(methane,MDEANumb, value*1e4);
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).setHVDijParameter(methane,MDEANumb, value*1e4);
        }
        if(i==21){
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).setHVDijParameter(MDEANumb, methane, value*1e4);
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).setHVDijParameter(MDEANumb, methane, value*1e4);
        }
        
        if(i==61){
            ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,CO2Numb, value);
            ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,CO2Numb, value);
        }
        if(i==22){
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).setHVDijParameter(CO2Numb,MDEANumb, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).setHVDijParameter(CO2Numb,MDEANumb, value);
        }
        if(i==32){
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).setHVDijParameter(MDEANumb, CO2Numb, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).setHVDijParameter(MDEANumb, CO2Numb, value);
        }
    }
    
    
}