package neqsim.thermodynamicOperations.flashOps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import java.util.concurrent.TimeUnit;

/**
 * @author ESOL
 */
class PHFlashCPATest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  @Test
  void testRun()  throws InterruptedException{

    // org.ejml
    //TimeUnit.SECONDS.sleep(30);

    testSystem = new neqsim.thermo.system.SystemSrkCPAstatoil(323.15, 100.0);
    testSystem.addComponent("CO2", 9.0);
    testSystem.addComponent("methane", 90.0);
    testSystem.addComponent("ethane", 12.0);
    testSystem.addComponent("propane", 1.0);
    testSystem.addComponent("i-butane", 1.0);
    testSystem.addComponent("n-butane", 1.0);
    testSystem.addComponent("i-pentane", 1.0);
    testSystem.addComponent("n-pentane", 1.0);
    testSystem.addComponent("n-hexane", 0.001);
    testSystem.addComponent("water", 10.0);
    testSystem.addComponent("MEG", 10.0);
    testSystem.setMixingRule(10);
    testSystem.setMultiPhaseCheck(true);

    testOps = new ThermodynamicOperations(testSystem);
    long startTime = System.currentTimeMillis();
    testOps.TPflash();
    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;

    System.out.println("Execution time TPflash: " + duration + " milliseconds");
    testSystem.initProperties();
    double enthalpy = testSystem.getEnthalpy();
    double entropy = testSystem.getEntropy();
    testSystem.setPressure(50.0);

    long startTimePH = System.currentTimeMillis();
    testOps.PHflash(enthalpy);
    long endTimePH = System.currentTimeMillis();
    long durationPH = endTimePH - startTimePH;

    System.out.println("Execution time PHflash: " + durationPH + " milliseconds");
    assertEquals(enthalpy, testSystem.getEnthalpy(), 1e-2);
    assertEquals(307.5036701214, testSystem.getTemperature(), 1e-2);
    testOps.PSflash(entropy);
    assertEquals(287.0197047, testSystem.getTemperature(), 1e-2);
  }


}
