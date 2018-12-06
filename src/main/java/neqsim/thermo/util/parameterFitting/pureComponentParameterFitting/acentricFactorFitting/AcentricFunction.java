/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.acentricFactorFitting;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class AcentricFunction extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;
    
    /** Creates new Test */
    public AcentricFunction() {
        params = new double[1];
    }
    
    public double calcValue(double[] dependentValues){
        system.setTemperature(dependentValues[0]);
        system.init(0);
        system.init(1);
        try{
            thermoOps.bubblePointPressureFlash(false);
        }
        catch(Exception e){
            System.out.println(e.toString());
        }
        return Math.log(system.getPressure());
    }
    
    public void setFittingParams(int i, double value){
        params[i] = value;
        system.getPhases()[0].getComponents()[i].setAcentricFactor(value);
        system.getPhases()[1].getComponents()[i].setAcentricFactor(value);
    }
}