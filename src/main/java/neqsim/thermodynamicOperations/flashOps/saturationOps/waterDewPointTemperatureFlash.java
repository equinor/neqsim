package neqsim.thermodynamicOperations.flashOps.saturationOps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * waterDewPointTemperatureFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class waterDewPointTemperatureFlash extends constantDutyTemperatureFlash {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(waterDewPointTemperatureFlash.class);

    /**
     * <p>
     * Constructor for waterDewPointTemperatureFlash.
     * </p>
     */
    public waterDewPointTemperatureFlash() {}

    /**
     * <p>
     * Constructor for waterDewPointTemperatureFlash.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public waterDewPointTemperatureFlash(SystemInterface system) {
        super(system);
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        int iterations = 0, maxNumberOfIterations = 15000;
        double yold = 0, ytotal = 1;
        double deriv = 0, funk = 0;
        double maxTemperature = 0, minTemperature = 1e6;
        system.init(0);

        // system.display();

        system.setNumberOfPhases(2);

        for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
            if (system.getPhase(0).getComponent(k).getComponentName().equals("water")
                    || system.getPhase(0).getComponent(k).getComponentName().equals("MEG")) {
                system.setTemperature(
                        system.getPhases()[0].getComponents()[k].getMeltingPointTemperature());
                for (int l = 0; l < system.getPhases()[0].getNumberOfComponents(); l++) {
                    system.getPhase(1).getComponent(l).setx(1e-30);
                    // logger.info("here");
                }
                system.getPhase(1).getComponent(k).setx(1.0);
                system.init(1);
                // system.display();
                iterations = 0;
                do {
                    funk = 0;
                    deriv = 0.0;
                    iterations++;
                    system.init(3);
                    funk = system.getPhases()[0].getComponents()[k].getz();

                    funk -= system.getPhases()[0].getBeta()
                            * system.getPhases()[1].getComponents()[k].getFugasityCoeffisient()
                            / system.getPhases()[0].getComponents()[k].getFugasityCoeffisient();
                    deriv -= system.getPhases()[0].getBeta()
                            * (system.getPhases()[1].getComponents()[k].getFugasityCoeffisient()
                                    * system.getPhases()[0].getComponents()[k].getdfugdt() * -1.0
                                    / Math.pow(system.getPhases()[0].getComponents()[k]
                                            .getFugasityCoeffisient(), 2.0)
                                    + system.getPhases()[1].getComponents()[k].getdfugdt()
                                            / system.getPhases()[i].getComponents()[k]
                                                    .getFugasityCoeffisient());

                    // logger.info("funk " + funk);

                    // double newTemp = system.getTemperature() - funk/deriv;
                    // system.setTemperature(newTemp);

                    system.setTemperature(system.getTemperature() + 100.0 * funk);

                    // logger.info("temp " + system.getTemperature());
                    // if(system.getPhase(0).getComponent(k).getComponentName().equals("MEG"))
                    // logger.info("funk " + funk + " temp " + system.getTemperature());
                } while (Math.abs(funk) >= 0.0000001 && iterations < 10000);

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

    /** {@inheritDoc} */
    @Override
    public void printToFile(String name) {}
}
