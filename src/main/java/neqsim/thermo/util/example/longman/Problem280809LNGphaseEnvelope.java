package neqsim.thermo.util.example.longman;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemGERG2004Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author lozhang
 */
public class Problem280809LNGphaseEnvelope {
    static Logger logger = LogManager.getLogger(Problem280809LNGphaseEnvelope.class);

    public static void main(String[] args) {
        SystemInterface testSystem = new SystemGERG2004Eos(230, 50.00);
        testSystem.addComponent("methane", 0.80);
        testSystem.addComponent("ethane", 0.05);
        testSystem.addComponent("propane", 0.03);
        testSystem.addComponent("CO2", 0.06);
        testSystem.addComponent("nitrogen", 0.05);
        // testSystem.addComponent("benzene",0.01);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        try {
            testOps.calcPTphaseEnvelope(true);// 0.05, 0.000005);
            testOps.displayResult();
        } catch (Exception e) {
            logger.error("error", e);
        }
        testSystem.display();
        // System.out.println("tempeerature " + (testSystem.getTemperature() - 273.15));
        // testOps.displayResult();
        // System.out.println("Cricondenbar " + testOps.get("cricondenbar")[0] + " " +
        // testOps.get("cricondenbar")[1]);
    }
}
