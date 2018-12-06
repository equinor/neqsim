/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class FreezeSolidFunction extends HuronVidalFunction {

    private static final long serialVersionUID = 1000;
    
    /** Creates new Test */
    public FreezeSolidFunction() {
    }
    
    public double calcValue(double[] dependentValues){
        system.init(0);
        try{
            thermoOps.freezingPointTemperatureFlash();
        }
        catch(Exception e){
            System.out.println(e.toString());
        }
        return system.getTemperature();
    }
    
}