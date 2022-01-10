package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.cpaParam;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * CPAFunctionCp class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class CPAFunctionCp extends CPAFunction {
    static Logger logger = LogManager.getLogger(CPAFunctionCp.class);

    int phasetype = 1;

    /**
     * <p>
     * Constructor for CPAFunctionCp.
     * </p>
     */
    public CPAFunctionCp() {}

    /**
     * <p>
     * Constructor for CPAFunctionCp.
     * </p>
     *
     * @param phase a int
     */
    public CPAFunctionCp(int phase) {
        phasetype = phase;
    }

    /** {@inheritDoc} */
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
    /** {@inheritDoc} */
    @Override
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
        return system.getPhase(phasetype).getCp()
                / (system.getPhase(phasetype).getNumberOfMolesInPhase()
                        * system.getPhase(phasetype).getMolarMass() * 1000);
    }
}
