package neqsim.thermo.util.parameterFitting.Procede.CO2Water;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author agrawalnj
 */
public class Jamal {
    static Logger logger = LogManager.getLogger(Jamal.class);

    public Jamal() {
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        double temperature, x, pressure;

        for (temperature = 278; temperature <= 500; temperature += 5) {
            x = 1e-4;
            SystemInterface testSystem = new SystemSrkSchwartzentruberEos(temperature, 1);
            testSystem.addComponent("CO2", x);
            testSystem.addComponent("water", 1 - x);

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

            // System.out.println(testSystem.getPressure()*testSystem.getPhase(0).getComponent(0).getx());

            try (PrintStream p = new PrintStream(new FileOutputStream("C:/java/NeqSimSource/Patrick.txt", true))) {
                p.println(temperature + " "
                        + testSystem.getPressure() * testSystem.getPhase(0).getComponent(0).getx() / x);
            } catch (FileNotFoundException e) {
                logger.error("Could not find file");
                logger.error("Could not read from Patrick.txt" + e.getMessage());
            }

            logger.info("Finished");
        }
    }
}
