package neqsim.thermo.util.parameterFitting.Procede.WaterMDEA;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Water_MDEA1 class.
 * </p>
 *
 * @author agrawalnj
 * @version $Id: $Id
 */
public class Water_MDEA1 {
    static Logger logger = LogManager.getLogger(Water_MDEA1.class);

    /**
     * <p>
     * Constructor for Water_MDEA1.
     * </p>
     */
    public Water_MDEA1() {}

    /**
     * <p>
     * main.
     * </p>
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        double temperature = 40 + 273.16;
        double pressure = 1.0;
        double x = 0;

        for (x = 0.85; x <= 1; x += 0.010) {
            SystemInterface testSystem = new SystemSrkSchwartzentruberEos(temperature, pressure);
            testSystem.addComponent("water", x);
            testSystem.addComponent("MDEA", 1 - x);

            testSystem.createDatabase(true);
            testSystem.setMixingRule(4);
            testSystem.init(0);
            testSystem.init(1);

            ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
            try {
                testOps.bubblePointPressureFlash(false);
            } catch (Exception e) {
                logger.error(e.toString());
            }

            // double aMDEA = testSystem.getPhase(1).getActivityCoefficient(1);
            // double awater = testSystem.getPhase(1).getActivityCoefficient(0);
            // double yMDEA = testSystem.getPhase(0).getComponent(1).getx();
            // double Hm = testSystem.getPhase(1).getHresTP();
            // logger.info("Activity MDEA "+aMDEA+" "+yMDEA);
            logger.info("pressure " + testSystem.getPressure());

            /*
             * logger.info("Excess Heat kJ "+Hm/1000);
             * logger.info("Excess Heat kJ "+testSystem.getPhase(1).getComponent(0).
             * getHresTP(temperature)/1000);
             * logger.info("Excess Heat kJ "+testSystem.getPhase(1).getComponent(1).
             * getHresTP(temperature)/1000);
             */
            try (PrintStream p = new PrintStream(
                    new FileOutputStream("C:/java/NeqSimSource/Patrick.txt", true))) {
                // p.println(x+" "+testSystem.getPhase(0).getComponent(0).getx()+"
                // "+testSystem.getPhase(0).getComponent(1).getx());
                p.println(x + " " + testSystem.getPhase(0).getComponent(1).getx() + " "
                        + testSystem.getPressure() + " "
                        + testSystem.getPhase(0).getComponent(1).getFugasityCoeffisient());
                // p.println(x+" "+aMDEA+" "+awater);
            } catch (FileNotFoundException e) {
                logger.error("Could not find file" + e.getMessage());
            }
        }
        logger.info("Finished");
    }
}
