/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.EosInteractionParameterFitting;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.mixingRule.HVmixingRuleInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import org.apache.log4j.Logger;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class CPAParameterFittingToDewPointData extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(CPAParameterFittingToDewPointData.class);
    
    /** Creates new Test */
    public CPAParameterFittingToDewPointData() {
        params = new double[1];
    }
    
    
    public double calcValue(double[] dependentValues){
        try{
        thermoOps.waterPrecipitationTemperature();
        }
        catch(Exception e){
            logger.error("error",e);
            
        }
        //System.out.println("x " + system.getPhases()[1].getComponents()[0].getx());
        return system.getTemperature(); // lucia data
       //   return system.getPhases()[1].getComponents()[0].getx(); // for MEG
    }
    
//    public double calcTrueValue(double val){
//        return val;
//    }
//    
     public void setFittingParams(int i, double value){
        params[i] = value;
         if(i==0){
//        ((PhaseEosInterface)system.getPhases()[0]).getMixingRule().setBinaryInteractionParameter(0,1, value);
//        ((PhaseEosInterface)system.getPhases()[1]).getMixingRule().setBinaryInteractionParameter(0,1, value);
         }
        if(i==2){
            system.getPhases()[0].getComponents()[1].seta(value*1e4);
            system.getPhases()[1].getComponents()[1].seta(value*1e4);
        }
        if(i==1){
            system.getPhases()[0].getComponents()[1].setb(value);
            system.getPhases()[1].getComponents()[1].setb(value);
        }
        if(i==3){
            system.getPhase(0).getComponent(1).getAtractiveTerm().setm(value);
            system.getPhases()[1].getComponents()[1].getAtractiveTerm().setm(value);
        }
        if(i==5){
            system.getPhase(0).getComponent(1).setAssociationEnergy(value);
            system.getPhase(1).getComponent(1).setAssociationEnergy(value);
        }
        if(i==4){
            system.getPhase(0).getComponent(1).setAssociationVolume(value);
            system.getPhase(1).getComponent(1).setAssociationVolume(value);
        }
    }
     
      public void setFittingParams2(int i, double value){
        params[i] = value;
        
        if(i==0){
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).setHVDijParameter(0,1, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).setHVDijParameter(0,1, value);
        }
        if(i==1){
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).setHVDijParameter(1,0, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).setHVDijParameter(1,0, value);
        }
        if(i==2){
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).setHVDijTParameter(0,1, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).setHVDijTParameter(0,1, value);
        }
        if(i==3){
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).setHVDijTParameter(1,0, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).setHVDijTParameter(1,0, value);
        }
         if(i==4){
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[0]).getMixingRule()).setHValphaParameter(1,0, value);
            ((HVmixingRuleInterface) ((PhaseEosInterface)system.getPhases()[1]).getMixingRule()).setHValphaParameter(1,0, value);
        }
      }
}