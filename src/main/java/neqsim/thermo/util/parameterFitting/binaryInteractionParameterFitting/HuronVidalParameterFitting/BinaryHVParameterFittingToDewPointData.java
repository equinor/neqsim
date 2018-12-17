/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

import neqsim.thermo.phase.PhaseEosInterface;
import org.apache.log4j.Logger;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class BinaryHVParameterFittingToDewPointData extends HuronVidalFunction{

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(BinaryHVParameterFittingToDewPointData.class);
    
    int phase = 1;
    int type = 1;
    /** Creates new Test */
    public BinaryHVParameterFittingToDewPointData() {
    }
    
    public BinaryHVParameterFittingToDewPointData(int phase, int type) {
        this.phase = phase;
        this.type = type;
    }
    
    public double calcValue(double[] dependentValues){
        try{
            if(system.getTemperature()>3.0){
                thermoOps.dewPointTemperatureFlash();
            }
            else{
                thermoOps.freezingPointTemperatureFlash();
            }
            
        }
        catch(Exception e){
            logger.error("err dew pont");
        }
        return system.getTemperature();
    }
    
    public double calcTrueValue(double val){
        return val;
    }
    
    public void setFittingParams(int i, double value){
        params[i] = value;
        ((PhaseEosInterface)system.getPhases()[0]).getMixingRule().setBinaryInteractionParameter(0,1, value);
        ((PhaseEosInterface)system.getPhases()[1]).getMixingRule().setBinaryInteractionParameter(0,1, value);
    }
}