package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Comprehensive tests for Q-function based flash calculations. Tests TSFlash, VUflash, VHflash,
 * VSflash, and PSflash implementations based on Michelsen's Q-function methodology.
 *
 * @author AI-generated based on Michelsen (1999) methodology
 * @version 1.0
 */
class QfuncFlashTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(QfuncFlashTest.class);

  /**
   * Test TSFlash (Temperature-Entropy flash). Given T and S, solve for P such that S(T,P) = Sspec.
   */
  @Test
  void testTSFlash_singlePhaseGas() {
    SystemInterface testSystem = new SystemSrkEos(350.0, 50.0);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("ethane", 0.1);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    // Store original state
    double originalPressure = testSystem.getPressure();
    double originalEntropy = testSystem.getEntropy();
    double originalTemperature = testSystem.getTemperature();

    // Change pressure, then flash back to original entropy
    testSystem.setPressure(30.0);
    testOps.TSflash(originalEntropy);

    // Verify entropy matches specification
    double resultEntropy = testSystem.getEntropy();
    assertEquals(originalEntropy, resultEntropy, Math.abs(originalEntropy) * 0.01,
        "TSFlash should converge to specified entropy");

    // Temperature should remain unchanged (it's a TS flash)
    assertEquals(originalTemperature, testSystem.getTemperature(), 0.1,
        "Temperature should remain at specified value");
  }

  /**
   * Test TSFlash with two-phase system.
   */
  @Test
  void testTSFlash_twoPhase() {
    SystemInterface testSystem = new SystemSrkEos(280.0, 40.0);
    testSystem.addComponent("methane", 0.7);
    testSystem.addComponent("n-pentane", 0.3);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double originalEntropy = testSystem.getEntropy();

    // Change pressure, then flash back
    testSystem.setPressure(20.0);
    testOps.TSflash(originalEntropy);

    double resultEntropy = testSystem.getEntropy();
    assertEquals(originalEntropy, resultEntropy, Math.abs(originalEntropy) * 0.02,
        "TSFlash should converge in two-phase region");
  }

  /**
   * Test VUflash (Volume-Internal Energy flash). Given V and U, solve for T and P.
   */
  @Test
  void testVUFlash_singlePhase() {
    SystemInterface testSystem = new SystemSrkEos(320.0, 35.0);
    testSystem.addComponent("methane", 0.85);
    testSystem.addComponent("ethane", 0.10);
    testSystem.addComponent("propane", 0.05);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double originalVolume = testSystem.getVolume();
    double originalInternalEnergy = testSystem.getInternalEnergy();

    // Perturb T and P, then flash back
    testSystem.setTemperature(350.0);
    testSystem.setPressure(45.0);
    testOps.VUflash(originalVolume, originalInternalEnergy);

    // Verify volume and internal energy match specifications
    assertEquals(originalVolume, testSystem.getVolume(), Math.abs(originalVolume) * 0.01,
        "VUFlash should converge to specified volume");
    assertEquals(originalInternalEnergy, testSystem.getInternalEnergy(),
        Math.abs(originalInternalEnergy) * 0.01, "VUFlash should converge to specified energy");
  }

  /**
   * Test VUflash with two-phase system.
   */
  @Test
  void testVUFlash_twoPhase() {
    SystemInterface testSystem = new SystemSrkEos(280.0, 30.0);
    testSystem.addComponent("methane", 0.6);
    testSystem.addComponent("n-pentane", 0.4);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double originalVolume = testSystem.getVolume();
    double originalInternalEnergy = testSystem.getInternalEnergy();

    // Perturb T and P
    testSystem.setTemperature(300.0);
    testSystem.setPressure(40.0);
    testOps.VUflash(originalVolume, originalInternalEnergy);

    assertEquals(originalVolume, testSystem.getVolume(), Math.abs(originalVolume) * 0.02,
        "VUFlash should converge in two-phase region");
    assertEquals(originalInternalEnergy, testSystem.getInternalEnergy(),
        Math.abs(originalInternalEnergy) * 0.02, "VUFlash energy should converge in two-phase");
  }

  /**
   * Test VHflash (Volume-Enthalpy flash). Given V and H, solve for T and P.
   */
  @Test
  void testVHFlash_singlePhase() {
    SystemInterface testSystem = new SystemSrkEos(330.0, 40.0);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("ethane", 0.1);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double originalVolume = testSystem.getVolume();
    double originalEnthalpy = testSystem.getEnthalpy();

    // Perturb T and P
    testSystem.setTemperature(360.0);
    testSystem.setPressure(50.0);
    testOps.VHflash(originalVolume, originalEnthalpy);

    assertEquals(originalVolume, testSystem.getVolume(), Math.abs(originalVolume) * 0.01,
        "VHFlash should converge to specified volume");
    assertEquals(originalEnthalpy, testSystem.getEnthalpy(), Math.abs(originalEnthalpy) * 0.01,
        "VHFlash should converge to specified enthalpy");
  }

  /**
   * Test VHflash with two-phase system.
   */
  @Test
  void testVHFlash_twoPhase() {
    SystemInterface testSystem = new SystemSrkEos(290.0, 35.0);
    testSystem.addComponent("methane", 0.65);
    testSystem.addComponent("n-pentane", 0.35);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double originalVolume = testSystem.getVolume();
    double originalEnthalpy = testSystem.getEnthalpy();

    // Perturb T and P
    testSystem.setTemperature(310.0);
    testSystem.setPressure(45.0);
    testOps.VHflash(originalVolume, originalEnthalpy);

    assertEquals(originalVolume, testSystem.getVolume(), Math.abs(originalVolume) * 0.02,
        "VHFlash should converge in two-phase region");
    assertEquals(originalEnthalpy, testSystem.getEnthalpy(), Math.abs(originalEnthalpy) * 0.02,
        "VHFlash enthalpy should converge in two-phase");
  }

  /**
   * Test VSflash (Volume-Entropy flash). Given V and S, solve for T and P.
   */
  @Test
  void testVSFlash_singlePhase() {
    SystemInterface testSystem = new SystemSrkEos(340.0, 45.0);
    testSystem.addComponent("methane", 0.88);
    testSystem.addComponent("ethane", 0.12);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double originalVolume = testSystem.getVolume();
    double originalEntropy = testSystem.getEntropy();

    // Perturb T and P
    testSystem.setTemperature(370.0);
    testSystem.setPressure(55.0);
    testOps.VSflash(originalVolume, originalEntropy);

    assertEquals(originalVolume, testSystem.getVolume(), Math.abs(originalVolume) * 0.01,
        "VSFlash should converge to specified volume");
    assertEquals(originalEntropy, testSystem.getEntropy(), Math.abs(originalEntropy) * 0.01,
        "VSFlash should converge to specified entropy");
  }

  /**
   * Test VSflash with two-phase system.
   */
  @Test
  void testVSFlash_twoPhase() {
    SystemInterface testSystem = new SystemSrkEos(285.0, 32.0);
    testSystem.addComponent("methane", 0.62);
    testSystem.addComponent("n-pentane", 0.38);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double originalVolume = testSystem.getVolume();
    double originalEntropy = testSystem.getEntropy();

    // Perturb T and P
    testSystem.setTemperature(305.0);
    testSystem.setPressure(42.0);
    testOps.VSflash(originalVolume, originalEntropy);

    assertEquals(originalVolume, testSystem.getVolume(), Math.abs(originalVolume) * 0.02,
        "VSFlash should converge in two-phase region");
    assertEquals(originalEntropy, testSystem.getEntropy(), Math.abs(originalEntropy) * 0.02,
        "VSFlash entropy should converge in two-phase");
  }

  /**
   * Test PSflash (Pressure-Entropy flash). Given P and S, solve for T.
   */
  @Test
  void testPSFlash_singlePhase() {
    SystemInterface testSystem = new SystemSrkEos(350.0, 50.0);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("ethane", 0.1);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double originalPressure = testSystem.getPressure();
    double originalEntropy = testSystem.getEntropy();
    double originalTemperature = testSystem.getTemperature();

    // Change temperature, then flash back
    testSystem.setTemperature(400.0);
    testOps.PSflash(originalEntropy);

    assertEquals(originalEntropy, testSystem.getEntropy(), Math.abs(originalEntropy) * 0.01,
        "PSFlash should converge to specified entropy");
    assertEquals(originalPressure, testSystem.getPressure(), 0.01,
        "Pressure should remain at specified value");
  }

  /**
   * Test PSflash with two-phase system.
   */
  @Test
  void testPSFlash_twoPhase() {
    SystemInterface testSystem = new SystemSrkEos(280.0, 30.0);
    testSystem.addComponent("methane", 0.7);
    testSystem.addComponent("n-pentane", 0.3);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double originalEntropy = testSystem.getEntropy();
    double originalPressure = testSystem.getPressure();

    // Change temperature
    testSystem.setTemperature(320.0);
    testOps.PSflash(originalEntropy);

    assertEquals(originalEntropy, testSystem.getEntropy(), Math.abs(originalEntropy) * 0.02,
        "PSFlash should converge in two-phase region");
    assertEquals(originalPressure, testSystem.getPressure(), 0.01,
        "Pressure should remain constant in PSFlash");
  }

  /**
   * Test convergence robustness with large perturbations.
   */
  @Test
  void testVUFlash_largeDeviation() {
    SystemInterface testSystem = new SystemSrkEos(300.0, 30.0);
    testSystem.addComponent("methane", 0.8);
    testSystem.addComponent("propane", 0.2);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double originalVolume = testSystem.getVolume();
    double originalInternalEnergy = testSystem.getInternalEnergy();

    // Large perturbation - should still converge with step limits
    testSystem.setTemperature(450.0);
    testSystem.setPressure(100.0);
    testOps.VUflash(originalVolume, originalInternalEnergy);

    // Should converge within reasonable tolerance
    double volumeError = Math.abs(testSystem.getVolume() - originalVolume) / originalVolume;
    double energyError =
        Math.abs(testSystem.getInternalEnergy() - originalInternalEnergy) / originalInternalEnergy;

    assertTrue(volumeError < 0.05, "VUFlash should converge even with large initial deviation");
    assertTrue(energyError < 0.05,
        "VUFlash energy should converge even with large initial deviation");
  }

  /**
   * Test that VH flash handles near-critical conditions.
   */
  @Test
  void testVHFlash_nearCritical() {
    // Near-critical conditions for methane (Tc ~190K, Pc ~46 bar)
    SystemInterface testSystem = new SystemSrkEos(185.0, 44.0);
    testSystem.addComponent("methane", 1.0);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double originalVolume = testSystem.getVolume();
    double originalEnthalpy = testSystem.getEnthalpy();

    // Small perturbation near critical point
    testSystem.setTemperature(190.0);
    testSystem.setPressure(46.0);
    testOps.VHflash(originalVolume, originalEnthalpy);

    // Near-critical requires looser tolerance
    assertEquals(originalVolume, testSystem.getVolume(), Math.abs(originalVolume) * 0.05,
        "VHFlash should handle near-critical conditions");
  }

  /**
   * Test multi-component system with all flash types.
   */
  @Test
  void testMultiComponentSystem() {
    SystemInterface testSystem = new SystemSrkEos(310.0, 40.0);
    testSystem.addComponent("nitrogen", 0.02);
    testSystem.addComponent("methane", 0.75);
    testSystem.addComponent("ethane", 0.10);
    testSystem.addComponent("propane", 0.08);
    testSystem.addComponent("n-butane", 0.05);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    // Store original state
    double volume = testSystem.getVolume();
    double enthalpy = testSystem.getEnthalpy();
    double entropy = testSystem.getEntropy();
    double internalEnergy = testSystem.getInternalEnergy();
    double originalT = testSystem.getTemperature();
    double originalP = testSystem.getPressure();

    // Test VU flash
    testSystem.setTemperature(330.0);
    testSystem.setPressure(50.0);
    testOps.VUflash(volume, internalEnergy);
    assertEquals(volume, testSystem.getVolume(), Math.abs(volume) * 0.02,
        "Multi-component VU flash volume");

    // Reset and test VH flash
    testSystem.setTemperature(originalT);
    testSystem.setPressure(originalP);
    testOps.TPflash();
    testSystem.initProperties();

    testSystem.setTemperature(330.0);
    testSystem.setPressure(50.0);
    testOps.VHflash(volume, enthalpy);
    assertEquals(volume, testSystem.getVolume(), Math.abs(volume) * 0.02,
        "Multi-component VH flash volume");
  }

  // ============== NEW FLASH TYPE TESTS ==============

  /**
   * Test THflash (Temperature-Enthalpy flash). Given T and H, solve for P such that H(T,P) = Hspec.
   */
  @Test
  void testTHFlash_singlePhaseGas() {
    SystemInterface testSystem = new SystemSrkEos(350.0, 50.0);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("ethane", 0.1);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    // Store original state
    double originalPressure = testSystem.getPressure();
    double originalEnthalpy = testSystem.getEnthalpy();
    double originalTemperature = testSystem.getTemperature();

    // Change pressure, then flash back to original enthalpy
    testSystem.setPressure(30.0);
    testOps.THflash(originalEnthalpy);

    // Verify enthalpy matches specification
    double resultEnthalpy = testSystem.getEnthalpy();
    assertEquals(originalEnthalpy, resultEnthalpy, Math.abs(originalEnthalpy) * 0.01,
        "THFlash should converge to specified enthalpy");

    // Temperature should remain unchanged (it's a TH flash)
    assertEquals(originalTemperature, testSystem.getTemperature(), 0.1,
        "Temperature should remain at specified value");
  }

  /**
   * Test THflash with two-phase system.
   */
  @Test
  void testTHFlash_twoPhase() {
    SystemInterface testSystem = new SystemSrkEos(280.0, 40.0);
    testSystem.addComponent("methane", 0.7);
    testSystem.addComponent("n-pentane", 0.3);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double originalEnthalpy = testSystem.getEnthalpy();

    // Change pressure, then flash back
    testSystem.setPressure(20.0);
    testOps.THflash(originalEnthalpy);

    double resultEnthalpy = testSystem.getEnthalpy();
    assertEquals(originalEnthalpy, resultEnthalpy, Math.abs(originalEnthalpy) * 0.02,
        "THFlash should converge in two-phase region");
  }

  /**
   * Test TUflash (Temperature-Internal Energy flash). Given T and U, solve for P.
   */
  @Test
  void testTUFlash_singlePhaseGas() {
    SystemInterface testSystem = new SystemSrkEos(350.0, 50.0);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("ethane", 0.1);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    // Store original state
    double originalPressure = testSystem.getPressure();
    double originalInternalEnergy = testSystem.getInternalEnergy();
    double originalTemperature = testSystem.getTemperature();

    // Change pressure, then flash back to original internal energy
    testSystem.setPressure(30.0);
    testOps.TUflash(originalInternalEnergy);

    // Verify internal energy matches specification
    double resultInternalEnergy = testSystem.getInternalEnergy();
    assertEquals(originalInternalEnergy, resultInternalEnergy,
        Math.abs(originalInternalEnergy) * 0.01,
        "TUFlash should converge to specified internal energy");

    // Temperature should remain unchanged
    assertEquals(originalTemperature, testSystem.getTemperature(), 0.1,
        "Temperature should remain at specified value");
  }

  /**
   * Test TUflash with two-phase system.
   */
  @Test
  void testTUFlash_twoPhase() {
    SystemInterface testSystem = new SystemSrkEos(280.0, 40.0);
    testSystem.addComponent("methane", 0.7);
    testSystem.addComponent("n-pentane", 0.3);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double originalInternalEnergy = testSystem.getInternalEnergy();

    // Change pressure, then flash back
    testSystem.setPressure(20.0);
    testOps.TUflash(originalInternalEnergy);

    double resultInternalEnergy = testSystem.getInternalEnergy();
    assertEquals(originalInternalEnergy, resultInternalEnergy,
        Math.abs(originalInternalEnergy) * 0.02, "TUFlash should converge in two-phase region");
  }

  /**
   * Test PVflash (Pressure-Volume flash). Given P and V, solve for T such that V(T,P) = Vspec.
   */
  @Test
  void testPVFlash_singlePhaseGas() {
    SystemInterface testSystem = new SystemSrkEos(350.0, 50.0);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("ethane", 0.1);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    // Store original state
    double originalTemperature = testSystem.getTemperature();
    double originalVolume = testSystem.getVolume();
    double originalPressure = testSystem.getPressure();

    // Change temperature, then flash back to original volume
    testSystem.setTemperature(400.0);
    testOps.PVflash(originalVolume);

    // Verify volume matches specification
    double resultVolume = testSystem.getVolume();
    assertEquals(originalVolume, resultVolume, Math.abs(originalVolume) * 0.01,
        "PVFlash should converge to specified volume");

    // Pressure should remain unchanged (it's a PV flash)
    assertEquals(originalPressure, testSystem.getPressure(), 0.01,
        "Pressure should remain at specified value");
  }

  /**
   * Test PVflash with two-phase system.
   */
  @Test
  void testPVFlash_twoPhase() {
    SystemInterface testSystem = new SystemSrkEos(280.0, 40.0);
    testSystem.addComponent("methane", 0.7);
    testSystem.addComponent("n-pentane", 0.3);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double originalVolume = testSystem.getVolume();

    // Change temperature, then flash back
    testSystem.setTemperature(320.0);
    testOps.PVflash(originalVolume);

    double resultVolume = testSystem.getVolume();
    assertEquals(originalVolume, resultVolume, Math.abs(originalVolume) * 0.02,
        "PVFlash should converge in two-phase region");
  }

  /**
   * Test PVflash with single-phase liquid.
   */
  @Test
  void testPVFlash_singlePhaseLiquid() {
    SystemInterface testSystem = new SystemSrkEos(250.0, 80.0);
    testSystem.addComponent("n-pentane", 1.0);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double originalVolume = testSystem.getVolume();

    // Change temperature, then flash back
    testSystem.setTemperature(280.0);
    testOps.PVflash(originalVolume);

    double resultVolume = testSystem.getVolume();
    assertEquals(originalVolume, resultVolume, Math.abs(originalVolume) * 0.01,
        "PVFlash should converge for single-phase liquid");
  }

  /**
   * Test all new flash types with multi-component system.
   */
  @Test
  void testNewFlashTypes_multiComponent() {
    SystemInterface testSystem = new SystemSrkEos(310.0, 40.0);
    testSystem.addComponent("nitrogen", 0.02);
    testSystem.addComponent("methane", 0.75);
    testSystem.addComponent("ethane", 0.10);
    testSystem.addComponent("propane", 0.08);
    testSystem.addComponent("n-butane", 0.05);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    // Store original state
    double volume = testSystem.getVolume();
    double enthalpy = testSystem.getEnthalpy();
    double internalEnergy = testSystem.getInternalEnergy();
    double originalT = testSystem.getTemperature();
    double originalP = testSystem.getPressure();

    // Test TH flash
    testSystem.setPressure(60.0);
    testOps.THflash(enthalpy);
    assertEquals(enthalpy, testSystem.getEnthalpy(), Math.abs(enthalpy) * 0.02,
        "Multi-component TH flash enthalpy");

    // Reset and test TU flash
    testSystem.setTemperature(originalT);
    testSystem.setPressure(originalP);
    testOps.TPflash();
    testSystem.initProperties();

    testSystem.setPressure(60.0);
    testOps.TUflash(internalEnergy);
    assertEquals(internalEnergy, testSystem.getInternalEnergy(), Math.abs(internalEnergy) * 0.02,
        "Multi-component TU flash internal energy");

    // Reset and test PV flash
    testSystem.setTemperature(originalT);
    testSystem.setPressure(originalP);
    testOps.TPflash();
    testSystem.initProperties();

    testSystem.setTemperature(350.0);
    testOps.PVflash(volume);
    assertEquals(volume, testSystem.getVolume(), Math.abs(volume) * 0.02,
        "Multi-component PV flash volume");
  }
}
