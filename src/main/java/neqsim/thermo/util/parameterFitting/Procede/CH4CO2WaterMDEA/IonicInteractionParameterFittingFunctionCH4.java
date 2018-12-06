/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.thermo.util.parameterFitting.Procede.CH4CO2WaterMDEA;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.mixingRule.ElectrolyteMixingRulesInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;

/**
 *
 * @author  Neeraj
 * @version
 */
public class IonicInteractionParameterFittingFunctionCH4 extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;
    int type = 0;
    int phase = 0;
    
    /** Creates new Test */
    public IonicInteractionParameterFittingFunctionCH4() {
    }
    
    public IonicInteractionParameterFittingFunctionCH4(int phase, int type) {
        this.phase = phase;
        this.type = type;
    }
    
    public double calcValue(double[] dependentValues){
        try{
            thermoOps.bubblePointPressureFlash(false);
        } catch(Exception e){
            System.out.println(e.toString());
        }
        if (type == 0)
        {
            //System.out.println("Pressure "+system.getPressure());
            return system.getPressure();
        }
        else {
            return (system.getPressure()*system.getPhases()[0].getComponent(1).getx()/(system.getPhases()[0].getComponent(0).getx() + system.getPhases()[0].getComponent(1).getx()));
        } 
    }
    
    public double calcTrueValue(double val){
        return (val);
    }
    
    
    public void setFittingParams(int i, double value){
        params[i] = value;
        int MDEAplusNumb=0, MDEANumb=0, CO2Numb=0, HCO3numb=0, Waternumb=0, CO3numb=0, OHnumb=0, CH4Numb=0;
        
        int j=0;
        do{
            MDEAplusNumb = j;
            j++;
        }
        while(!system.getPhases()[1].getComponents()[j-1].getComponentName().equals("MDEA+"));
        
        j=0;
        do{
            MDEANumb = j;
            j++;
        }
        while(!system.getPhases()[1].getComponents()[j-1].getComponentName().equals("MDEA"));
        
        j=0;
        do{
            CO2Numb = j;
            j++;
        }
        while(!system.getPhases()[1].getComponents()[j-1].getComponentName().equals("CO2"));
        
        if(CO2Numb !=1) {
            System.out.println("-------------ERROR in CO2 number------------");
        }
        
        j=0;
        do{
            CH4Numb = j;
            j++;
        }
        while(!system.getPhases()[1].getComponents()[j-1].getComponentName().equals("methane"));
        
        j=0;
        do{
            HCO3numb = j;
            j++;
        }
        while(!system.getPhases()[1].getComponents()[j-1].getComponentName().equals("HCO3-"));
        
        j=0;
        do{
            CO3numb = j;
            j++;
        }
        while(!system.getPhases()[0].getComponents()[j-1].getComponentName().equals("CO3--"));
        
        j=0;
        do{
            OHnumb = j;
            j++;
        }
        while(!system.getPhases()[1].getComponents()[j-1].getComponentName().equals("OH-"));
        
        
        j=0;
        do{
            Waternumb = j;
            j++;
        }
        while(!system.getPhases()[1].getComponents()[j-1].getComponentName().equals("water"));
        
        
        ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb, Waternumb, 0.0004092282);
        ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb, Waternumb, 0.0004092282);
        
        if((ElectrolyteMixingRulesInterface)((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1].getRefPhase(MDEAplusNumb)).getElectrolyteMixingRule()!=null){
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0].getRefPhase(MDEAplusNumb)).getElectrolyteMixingRule().setWijParameter(0,1,0.0004092282);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0].getRefPhase(MDEAplusNumb)).getElectrolyteMixingRule().setWijParameter(1,0,0.0004092282);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1].getRefPhase(MDEAplusNumb)).getElectrolyteMixingRule().setWijParameter(0,1,0.0004092282);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1].getRefPhase(MDEAplusNumb)).getElectrolyteMixingRule().setWijParameter(1,0,0.0004092282);
        }
        
        ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,HCO3numb, -0.0001293147);
        ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,HCO3numb, -0.0001293147);
        
        ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,MDEANumb, 0.0019465801);
        ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,MDEANumb, 0.0019465801);
        
        ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,CO2Numb, 0.0002481365);
        ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,CO2Numb, 0.0002481365);
        
        ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,CO3numb, -0.0003581646);
        ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,CO3numb, -0.0003581646);
        
        ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,OHnumb, 1e-10);
        ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,OHnumb, 1e-10);
        
        if(i==0){
            ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,CH4Numb, value);
            ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).getElectrolyteMixingRule().setWijParameter(MDEAplusNumb,CH4Numb, value);
        }
        
        if(i==1){
            ((PhaseEosInterface)system.getPhases()[0]).getMixingRule().setBinaryInteractionParameter(CO2Numb,CH4Numb, value);
            ((PhaseEosInterface)system.getPhases()[1]).getMixingRule().setBinaryInteractionParameter(CO2Numb,CH4Numb, value);
        }
              
    }
    

}