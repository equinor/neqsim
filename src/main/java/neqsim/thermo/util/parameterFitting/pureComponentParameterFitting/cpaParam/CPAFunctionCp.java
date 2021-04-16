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
public class CPAFunctionCp extends CPAFunction {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(CPAFunctionCp.class);

    int phasetype = 1;

    /** Creates new Test */
    public CPAFunctionCp() {
    }

    public CPAFunctionCp(int phase) {
        phasetype = phase;
    }

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
    public double calcValue(double[] dependentValues) {
        system.setTemperature(dependentValues[0]);
        system.setPressure(1.0);// system.getPhases()[0].getComponents()[0].getAntoineVaporPressure(dependentValues[0]));
        try {
            thermoOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            logger.error(e.toString());
        }
        system.init(3);
        // System.out.println("pres: " + system.getPressure());
        return system.getPhase(phasetype).getCp() / (system.getPhase(phasetype).getNumberOfMolesInPhase()
                * system.getPhase(phasetype).getMolarMass() * 1000);
    }

}