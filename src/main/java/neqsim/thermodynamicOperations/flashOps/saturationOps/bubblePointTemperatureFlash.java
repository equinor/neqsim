/*
 * bubblePointFlash.java
 *
 * Created on 14. oktober 2000, 16:30
 */
package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermo.system.SystemInterface;
import org.apache.log4j.Logger;

public class bubblePointTemperatureFlash extends constantDutyTemperatureFlash {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(bubblePointTemperatureFlash.class);

    /**
     * Creates new bubblePointFlash
     */
    public bubblePointTemperatureFlash() {
    }

    public bubblePointTemperatureFlash(SystemInterface system) {
        super(system);
    }

    public void run() {

        int iterations = 0, maxNumberOfIterations = 10000;
        double yold = 0, ytotal = 1;
        double deriv = 0, funk = 0;

        for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
            system.getPhases()[1].getComponents()[i].setx(system.getPhases()[0].getComponents()[i].getz());
            system.getPhases()[0].getComponents()[i].setx(system.getPhases()[0].getComponents()[i].getK() * system.getPhases()[1].getComponents()[i].getx());
        }
        system.setNumberOfPhases(2);
        do {
            system.setTemperature((system.getTemperature() + system.getTemperature() / ytotal) / 10);
            //   logger.info("temp . " + system.getTemperature());
            funk = 0;
            deriv = 0;
            ytotal = 0;
            system.init(1);
            for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {

                do {
                    iterations++;

                    yold = system.getPhases()[0].getComponents()[i].getx();
                    system.getPhases()[0].getComponents()[i].setK(system.getPhases()[1].getComponents()[i].getFugasityCoeffisient() / system.getPhases()[0].getComponents()[i].getFugasityCoeffisient());
                    system.getPhases()[1].getComponents()[i].setK(system.getPhases()[0].getComponents()[i].getK());
                    system.getPhases()[0].getComponents()[i].setx(system.getPhases()[1].getComponents()[i].getx() * system.getPhases()[1].getComponents()[i].getFugasityCoeffisient() / system.getPhases()[0].getComponents()[i].getFugasityCoeffisient());

                } while ((Math.abs(yold - system.getPhases()[1].getComponents()[i].getx()) > 1e-10) && (iterations < maxNumberOfIterations));

                ytotal += system.getPhases()[0].getComponents()[i].getx();
                funk += system.getPhases()[1].getComponents()[i].getx() * system.getPhases()[1].getComponents()[i].getK();
                deriv += system.getPhases()[1].getComponents()[i].getx() * system.getPhases()[1].getComponents()[i].getK() * (system.getPhases()[1].getComponents()[i].getdfugdt() - system.getPhases()[0].getComponents()[i].getdfugdt());
            }

            //  logger.info("FUNK: " + funk);
            // logger.info("temp: " + system.getTemperature());
            // system.setPressure(-Math.log(funk)/(deriv/funk)+system.getPressure());
            system.setTemperature(-(funk - 1) / deriv + system.getTemperature());

        } while ((Math.abs(ytotal - 1) > 1e-10) && (iterations < maxNumberOfIterations));

    }

    public void printToFile(String name) {
    }
}
