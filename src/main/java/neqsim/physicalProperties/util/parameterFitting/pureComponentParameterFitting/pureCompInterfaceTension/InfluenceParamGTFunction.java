/*
 * RacketFunction.java
 *
 * Created on 24. januar 2001, 21:15
 */
package neqsim.physicalProperties.util.parameterFitting.pureComponentParameterFitting.pureCompInterfaceTension;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 *
 * @author  Even Solbraa
 * @version 
 */
public class InfluenceParamGTFunction extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;

    public InfluenceParamGTFunction() {
        params = new double[1];
    }

    public double calcValue(double[] dependentValues) {
        system.init(3);
        try{
        thermoOps.bubblePointPressureFlash(false);
        }
        catch(Exception e){
            e.printStackTrace();
        }
        system.initPhysicalProperties();
        return system.getInterphaseProperties().getSurfaceTension(0,1)*1e3;
    }

    public void setFittingParams(int i, double value) {
        params[i] = value;
        system.getPhases()[0].getComponent(0).setSurfTensInfluenceParam(i, value);
        system.getPhases()[1].getComponent(0).setSurfTensInfluenceParam(i, value);
    }
}