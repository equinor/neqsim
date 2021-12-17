package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkTwuCoonStatoilEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/*
 *
 * @author esol @version
 */
public class TestmercuryTPflash {
    static Logger logger = LogManager.getLogger(TestmercuryTPflash.class);

    public static void main(String[] args) {
        SystemInterface testSystem = new SystemSrkTwuCoonStatoilEos(273.15 - 172.0, 1.0);

        testSystem.addComponent("nitrogen", 2.97007999748152e-002);
        testSystem.addComponent("methane", 0.902244);
        testSystem.addComponent("ethane", 0.053167);
        testSystem.addComponent("propane", 0.010742);
        testSystem.addComponent("i-butane", 0.000902);
        testSystem.addComponent("n-heptane", 0.02692);
        testSystem.addComponent("mercury", 2.12608096955523e-10);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        testSystem.setMultiPhaseCheck(true);
        // testSystem.setSolidPhaseCheck("mercury");
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.TPflash();
            // testOps.freezingPointTemperatureFlash("mercury");
            testSystem.display();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        // ((PhaseEosInterface)testSystem.getPhase(0)).displayInteractionCoefficients("");
        System.out.println("vapour pressure "
                + testSystem.getPhase(0).getComponent("mercury").getx() * testSystem.getPressure());
        System.out.println("Ttrip "
                + testSystem.getPhase(0).getComponent("mercury").getTriplePointTemperature());
    }
}
