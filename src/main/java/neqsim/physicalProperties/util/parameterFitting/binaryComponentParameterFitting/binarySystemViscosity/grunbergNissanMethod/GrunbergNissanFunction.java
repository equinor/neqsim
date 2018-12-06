/*
 * RacketFunction.java
 *
 * Created on 24. januar 2001, 21:15
 */

package neqsim.physicalProperties.util.parameterFitting.binaryComponentParameterFitting.binarySystemViscosity.grunbergNissanMethod;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class GrunbergNissanFunction extends LevenbergMarquardtFunction{

    private static final long serialVersionUID = 1000;
    
    
    public GrunbergNissanFunction() {
    }
    
    public double calcValue(double[] dependentValues){
        system.init(1);
        system.initPhysicalProperties();
        return system.getPhases()[1].getPhysicalProperties().getViscosity()*1e3;
    }
    
    public void setFittingParams(int i, double value){
         params[i] = value;
//        system.getPhases()[0].getPhysicalProperties().getMixingRule().setViscosityGij(value, 0, 1);
//        system.getPhases()[0].getPhysicalProperties().getMixingRule().setViscosityGij(value, 1, 0);
        system.getPhases()[1].getPhysicalProperties().getMixingRule().setViscosityGij(value, 1, 0);
        system.getPhase(1).getPhysicalProperties().getMixingRule().setViscosityGij(value, 0, 1);
    }
}