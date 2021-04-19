/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.cpaParam;

import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class CPAFunctionDens extends CPAFunction {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(CPAFunctionDens.class);

    int phasetype = 1;

    /** Creates new Test */
    public CPAFunctionDens() {
    }

    public CPAFunctionDens(int phase) {
        phasetype = phase;
    }

    @Override
	public double calcTrueValue(double val) {
        return val;
    }

    // public double calcValue(double[] dependentValues){
    // system.setTemperature(dependentValues[0]);
    // system.init(0);
    // system.init(1);
    // system.initPhysicalProperties();
    // return system.getPhase(phasetype).getPhysicalProperties().getDensity();
    // }
    public double calcValue2(double[] dependentValues) {
        system.setTemperature(dependentValues[0]);
        system.setPressure(1.0);// system.getPhases()[0].getComponents()[0].getAntoineVaporPressure(dependentValues[0]));
        try {
            thermoOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            logger.error(e.toString());
        }
        system.initPhysicalProperties();
        // System.out.println("pres: " + system.getPressure());
        return system.getPhase(phasetype).getPhysicalProperties().getDensity();
    }

    @Override
	public double calcValue(double[] dependentValues) {
        system.setTemperature(dependentValues[0]);
        // system.setPressure(system.getPhases()[0].getComponents()[0].getAntoineVaporPressure(dependentValues[0]));

        system.init(0);
        system.init(1);
        system.initPhysicalProperties();
        // System.out.println("pres: " + system.getPressure());
        return system.getPhase(phasetype).getPhysicalProperties().getDensity();
    }
}