package neqsim.thermo.util.parameterFitting.Procede.CO2MDEA;

import java.io.*;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

/*
 * Sleipneracetate.java
 *
 * Created on August 6, 2004, 11:41 AM
 */

/**
 *
 * @author agrawalnj
 */
public class N2O {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(N2O.class);

    /** Creates a new instance of Sleipneracetate */
    public N2O() {
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        FileOutputStream outfile;
        PrintStream p;
        try {
            outfile = new FileOutputStream("C:/java/NeqSimSource/Patrick.txt");
            p = new PrintStream(outfile);
            p.close();
        } catch (IOException e) {
            logger.error("Could not find file");
        }
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

            try {
                outfile = new FileOutputStream("C:/java/NeqSimSource/Patrick.txt", true);
                p = new PrintStream(outfile);
                p.println(temperature + " " + testSystem.getPressure() * testSystem.getPhase(0).getComponent(0).getx()
                        + " " + aCO2 + " " + aMDEA + " " + awater);
                p.close();
            } catch (FileNotFoundException e) {
                logger.error("Could not find file" + e.getMessage());
                logger.error("Could not read from Patrick.txt" + e.getMessage());
            }
}
}
    }
}
