package neqsim.thermo.util.parameterFitting.Procede.CO2MDEA;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>N2O class.</p>
 *
 * @author agrawalnj
 * @version $Id: $Id
 */
public class N2O {
    static Logger logger = LogManager.getLogger(N2O.class);

    /**
     * <p>Constructor for N2O.</p>
     */
    public N2O() {
    }

    /**
     * <p>main.</p>
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        double temperature = 313;
        double wt, x;
        wt = 0.4;

        for (temperature = 283; temperature <= 363; temperature += 10) {
            x = (wt / 119.16) / (wt / 119.16 + (1 - wt) / 18.02);

            SystemInterface testSystem = new SystemSrkSchwartzentruberEos(temperature, 1.0);
            testSystem.addComponent("CO2", 1e-3 * x);
            testSystem.addComponent("water", 1 - x);
            testSystem.addComponent("MDEA", x);

            testSystem.createDatabase(true);
            testSystem.setMixingRule(4);
            testSystem.init(0);
            testSystem.init(1);

            // System.out.println(x);
            ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
            try {
                testOps.bubblePointPressureFlash(false);
            } catch (Exception e) {
                logger.error(e.toString());
            }

            double aCO2 = testSystem.getPhase(1).getActivityCoefficient(0, 1);
            double aMDEA = testSystem.getPhase(1).getActivityCoefficient(2);
            double awater = testSystem.getPhase(1).getActivityCoefficient(1);
            logger.info(aCO2);

            try (PrintStream p = new PrintStream(new FileOutputStream("C:/java/NeqSimSource/Patrick.txt", true))) {
                p.println(temperature + " " + testSystem.getPressure() * testSystem.getPhase(0).getComponent(0).getx()
                        + " " + aCO2 + " " + aMDEA + " " + awater);
            } catch (FileNotFoundException e) {
                logger.error("Could not find file" + e.getMessage());
                logger.error("Could not read from Patrick.txt" + e.getMessage());
            }
        }
        logger.info("Finished");
    }
}
