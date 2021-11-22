package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.acentricFactorFitting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ClassicAcentricDens extends ClassicAcentricFunction {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(ClassicAcentricDens.class);

    int phasetype = 1;

    public ClassicAcentricDens() {}

    public ClassicAcentricDens(int phase) {
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

    @Override
    public double calcValue(double[] dependentValues) {
        system.setTemperature(dependentValues[0]);
        system.setPressure(system.getPhases()[0].getComponents()[0]
                .getAntoineVaporPressure(dependentValues[0]));
        // System.out.println("pres from antoine: " + system.getPressure());
        system.init(0);
        system.init(1);
        try {
            thermoOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            logger.error(e.toString());
        }
        // System.out.println("pres: " + system.getPressure());
        system.initPhysicalProperties();
        return system.getPhase(phasetype).getPhysicalProperties().getDensity();
    }
}
