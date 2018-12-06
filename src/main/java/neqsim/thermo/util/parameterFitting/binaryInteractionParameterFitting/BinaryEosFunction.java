/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.phase.PhaseEosInterface;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class BinaryEosFunction extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;
    
    /** Creates new Test */
    public BinaryEosFunction() {
        params = new double[1];
        params[0] = -0.34;
    }
    
    public double calcValue(double[] dependentValues){
        system.setTemperature(dependentValues[0]);
        system.setPressure(dependentValues[1]);
        thermoOps.TPflash();
        //System.out.println("pres CO2: " + system.getPressure()*system.getPhases()[0].getComponents()[0].getx());
        return system.getPressure()*system.getPhases()[0].getComponents()[0].getx();
    }
    
    public void setFittingParams(int i, double value){
        params[i] = value;
        ((PhaseEosInterface)system.getPhases()[0]).getMixingRule().setBinaryInteractionParameter(0,1, value);
        ((PhaseEosInterface)system.getPhases()[1]).getMixingRule().setBinaryInteractionParameter(0,1, value);
    }
    
    public double getFittingParams(int i){
        return params[i];
    }
    
    public double[] getFittingParams(){
        return params;
    }
    
    public int getNumberOfFittingParams(){
        return params.length;
    }
    
    public void setFittingParams(double[] value){
        for(int i=0;i<value.length;i++){
            params[i] = value[i];
            ((PhaseEosInterface)system.getPhases()[0]).getMixingRule().setBinaryInteractionParameter(0,1, value[i]);
            ((PhaseEosInterface)system.getPhases()[1]).getMixingRule().setBinaryInteractionParameter(0,1, value[i]);
        }
    }
    
}
