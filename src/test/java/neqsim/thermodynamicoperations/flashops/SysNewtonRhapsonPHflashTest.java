package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test class for SysNewtonRhapsonPHflash.
 */
public class SysNewtonRhapsonPHflashTest {
  @Test
  void testDirectPHFlash() {
    // Use a system that is definitely 2-phase
    SystemInterface testSystem = new SystemSrkEos(250.0, 20.0);
    testSystem.addComponent("methane", 50.0);
    testSystem.addComponent("propane", 50.0);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double beta = testSystem.getBeta();
    assertTrue(beta > 0.01 && beta < 0.99,
        "System should be in 2-phase region for this test, beta=" + beta);

    double targetEnthalpy = testSystem.getEnthalpy();
    double initialTemperature = testSystem.getTemperature();

    // Perturb temperature slightly
    testSystem.setTemperature(255.0);
    testSystem.init(3);

    // Run direct PH flash (type 1)
    try {
      testOps.PHflash(targetEnthalpy, 1);
    } catch (Exception e) {
      e.printStackTrace();
    }

    assertEquals(targetEnthalpy, testSystem.getEnthalpy(), Math.abs(targetEnthalpy) * 1e-4,
        "Enthalpy should match target");
    assertEquals(initialTemperature, testSystem.getTemperature(), 1e-2,
        "Temperature should be recovered");
  }

  @Test
  void testDirectPHFlashTwoPhase() {
    SystemInterface testSystem = new SystemSrkEos(200.0, 20.0);
    testSystem.addComponent("methane", 50.0);
    testSystem.addComponent("propane", 50.0);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double beta = testSystem.getBeta();
    assertTrue(beta > 0.01 && beta < 0.99,
        "System should be in 2-phase region for this test, beta=" + beta);

    double targetEnthalpy = testSystem.getEnthalpy();
    double initialTemperature = testSystem.getTemperature();

    // Perturb temperature
    testSystem.setTemperature(210.0);
    testSystem.init(3);

    // Run direct PH flash (type 1)
    try {
      testOps.PHflash(targetEnthalpy, 1);
    } catch (Exception e) {
      e.printStackTrace();
    }

    assertEquals(targetEnthalpy, testSystem.getEnthalpy(), Math.abs(targetEnthalpy) * 1e-4,
        "Enthalpy should match target in two-phase");
    assertEquals(initialTemperature, testSystem.getTemperature(), 0.2,
        "Temperature should be recovered in two-phase");
  }

  @Test
  void testDirectPHFlashSinglePhase() {
    // Single phase gas
    SystemInterface testSystem = new SystemSrkEos(300.0, 10.0); // High T, Low P -> Gas
    testSystem.addComponent("methane", 100.0);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double beta = testSystem.getBeta();
    assertEquals(1.0, beta, 1e-6, "System should be single phase gas");

    double targetEnthalpy = testSystem.getEnthalpy();
    double initialTemperature = testSystem.getTemperature();

    // Perturb temperature
    testSystem.setTemperature(310.0);
    testSystem.init(3);

    // Run direct PH flash (type 1)
    try {
      testOps.PHflash(targetEnthalpy, 1);
    } catch (Exception e) {
      e.printStackTrace();
    }

    assertEquals(targetEnthalpy, testSystem.getEnthalpy(), Math.abs(targetEnthalpy) * 1e-4,
        "Enthalpy should match target in single phase");
    assertEquals(initialTemperature, testSystem.getTemperature(), 1e-2,
        "Temperature should be recovered in single phase");
  }
}
