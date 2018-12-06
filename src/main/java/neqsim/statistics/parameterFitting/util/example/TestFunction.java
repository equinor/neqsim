/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.statistics.parameterFitting.util.example;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class TestFunction extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;
    
    /** Creates new Test */
    public TestFunction() {
    }
   
    public double calcValue(double[] dependentValues){
        return 3.0*params[0]*params[1]-2.0*params[0]*dependentValues[0]-dependentValues[0];
    }
    
    public void setFittingParams(int i, double value){
        params[i] = value;
    }
}