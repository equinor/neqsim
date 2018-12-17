/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

import org.apache.log4j.Logger;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class FreezeSolidFunction extends HuronVidalFunction {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(FreezeSolidFunction.class);
    
    /** Creates new Test */
    public FreezeSolidFunction() {
    }
    
    public double calcValue(double[] dependentValues){
        system.init(0);
        try{
            thermoOps.freezingPointTemperatureFlash();
        }
        catch(Exception e){
            logger.error(e.toString());
        }
        return system.getTemperature();
    }
    
}