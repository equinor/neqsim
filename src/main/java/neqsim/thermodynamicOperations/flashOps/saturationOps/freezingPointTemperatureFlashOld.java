/*
 * bubblePointFlash.java
 *
 * Created on 14. oktober 2000, 16:30
 */

package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

public class freezingPointTemperatureFlashOld extends constantDutyTemperatureFlash {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(freezingPointTemperatureFlashOld.class);

    /** Creates new bubblePointFlash */
    public freezingPointTemperatureFlashOld() {
    }

    public freezingPointTemperatureFlashOld(SystemInterface system) {
        super(system);
    }

    @Override
	public void run() {
        ThermodynamicOperations ops = new ThermodynamicOperations(system);

        int iterations = 0, maxNumberOfIterations = 15000;
        double yold = 0, ytotal = 1;
        double deriv = 0, funk = 0, funkOld = 0;
        double maxTemperature = 0, minTemperature = 1e6, oldTemperature = 0.0;
        for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
            if (system.getPhase(3).getComponent(k).fugcoef(system.getPhase(3)) < 9e4
                    && system.getPhase(3).getComponent(k).doSolidCheck()) { // checks if solid can be formed from
                                                                            // component k
                system.setTemperature(system.getPhases()[0].getComponents()[k].getMeltingPointTemperature());
                system.init(0);
                system.init(1);
                iterations = 0;
                do {
                    funk = 0.0;
                    deriv = 0.0;
                    iterations++;
                    system.setSolidPhaseCheck(false);
                    ops.TPflash();
                    system.getPhase(3).getComponent(k).fugcoef(system.getPhase(3));

                    funk = system.getPhases()[0].getComponents()[k].getz();
                    logger.info("phase " + system.getNumberOfPhases());

                    for (int i = 0; i < system.getNumberOfPhases(); i++) {
                        funk -= system.getPhases()[i].getBeta()
                                * system.getPhases()[3].getComponents()[k].getFugasityCoeffisient()
                                / system.getPhases()[i].getComponents()[k].getFugasityCoeffisient();
                        deriv -= 0.01 * system.getPhases()[i].getBeta()
                                * (system.getPhases()[3].getComponents()[k].getFugasityCoeffisient()
                                        * Math.exp(system.getPhases()[i].getComponents()[k].getdfugdt()) * -1.0
                                        / Math.pow(system.getPhases()[i].getComponents()[k].getFugasityCoeffisient(),
                                                2.0)
                                        + Math.exp(system.getPhases()[3].getComponents()[k].getdfugdt())
                                                / system.getPhases()[i].getComponents()[k].getFugasityCoeffisient());
                    }
                    if (iterations >= 2) {
                        deriv = -(funk - funkOld) / (system.getTemperature() - oldTemperature);
                    } else {
                        deriv = -funk;
                    }

                    oldTemperature = system.getTemperature();
                    funkOld = funk;

                    system.setTemperature(
                            system.getTemperature() + 0.5 * (iterations / (10.0 + iterations)) * funk / deriv);

                    logger.info("funk/deriv " + funk / deriv);
                    logger.info("temperature " + system.getTemperature());
                } while ((Math.abs(funk / deriv) >= 1e-6 && iterations < 100));

                // logger.info("funk " + funk + k + " " + system.getTemperature());
                if (system.getTemperature() < minTemperature) {
                    minTemperature = system.getTemperature();
                }
                if (system.getTemperature() > maxTemperature) {
                    maxTemperature = system.getTemperature();
                }
            }
        }

        system.setTemperature(maxTemperature);
        // logger.info("min freezing temp " + minTemperature);
        // logger.info("max freezing temp " + maxTemperature);
    }

    @Override
	public void printToFile(String name) {
    }

}
