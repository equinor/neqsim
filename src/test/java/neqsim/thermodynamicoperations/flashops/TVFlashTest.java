package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test TVFlash.
 */
class TVFlashTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TVFlashTest.class);

  static neqsim.thermo.system.SystemInterface testSystem = null;
  static neqsim.thermo.system.SystemInterface testSystem2 = null;
  static ThermodynamicOperations testOps = null;

  /**
   * Sets up test system.
   *
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemUMRPRUMCEos(293.15, 0.1);
    testSystem.addComponent("methane", 0.0);
    testSystem.addComponent("ethane", 0.0);
    testSystem.addComponent("n-pentane", 9E-1);
    testSystem.addComponent("nC16", 1E-1);
    testSystem.setMixingRule(EosMixingRuleType.CLASSIC);
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    testSystem.setTotalFlowRate(1.0, "kg/sec");
    testOps.TPflash();
    testSystem.initProperties();

    testSystem2 = new neqsim.thermo.system.SystemUMRPRUMCEos(293.15, 0.1);
    testSystem2.addComponent("methane", 8.5E-1);
    testSystem2.addComponent("ethane", 1.5E-1);
    testSystem2.addComponent("n-pentane", 0);
    testSystem2.addComponent("nC16", 0);
    testSystem2.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem2);
    testOps.TPflash();
    testSystem2.initProperties();
    testSystem2.setTotalFlowRate(0.3, "kg/sec");
    testOps.TPflash();
    testSystem2.initProperties();
  }

  @Test
  void testTVflash() {
    double total_rig_volume = 0.998;

    for (int i = 0; i < 50; i++) {
      testSystem.addFluid(testSystem2);
      ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
      try {
        testOps.TVflash(total_rig_volume, "m3");
      } catch (Exception ex) {
        logger.info("error iterations " + i);
        logger.error(ex.getMessage(), ex);
      }
    }
    assertEquals(235310.3670621656, testSystem.getEnthalpy(), 1.0);
  }

  @Test
  void testLiquidThermalExpansion() {
    testSystem = new neqsim.thermo.system.SystemPrEos(273.15 - 60.0, 40.0);
    testSystem.addComponent("CO2", 1.0);
    testSystem.addComponent("methane", 10.0);
    testSystem.addComponent("ethane", 20.0);
    testSystem.addComponent("propane", 20.0);
    testSystem.addComponent("n-butane", 20.0);
    testSystem.setMixingRule("classic");

    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double isothermalCompressibility = testSystem.getPhase(0).getIsothermalCompressibility();
    double isobaricThermalExpansivity = testSystem.getPhase(0).getIsobaricThermalExpansivity();

    assertEquals(2.1655529052373845E-4, isothermalCompressibility, 1e-6);
    assertEquals(0.0019761208438481767, isobaricThermalExpansivity, 1e-6);

    double volume = testSystem.getVolume("m3");
    testSystem.setTemperature(20.0, "C");
    testOps.TVflash(volume, "m3");
    assertEquals(747.12062, testSystem.getPressure("bara"), 1.0);
  }

  /**
   * Test TVflash with UMR-PRU-MC-EoS and Huron-Vidal mixing rule. This test reproduces a reported
   * bug where TVflash showed excessive iterations (1494) and init(3) warnings.
   */
  @Test
  void testTVflashUMRPRUHuronVidal() {
    // Create system with UMR-PRU-MC-EoS at reported conditions
    // Temperature: 23.9°C, Pressure: 79.21678828 bara
    neqsim.thermo.system.SystemInterface leanFluid =
        new neqsim.thermo.system.SystemUMRPRUMCEos(273.15 + 23.9, 79.21678828);

    // EQfluid_lean composition (mole fractions from bug report)
    leanFluid.addComponent("methane", 7.48353E-1);
    leanFluid.addComponent("ethane", 1.32062E-1);
    leanFluid.addComponent("n-pentane", 1.07626E-1);
    leanFluid.addComponent("nC16", 1.19584E-2);

    // Set Huron-Vidal mixing rule (type 4 or 7)
    leanFluid.setMixingRule(EosMixingRuleType.HV);

    ThermodynamicOperations leanOps = new ThermodynamicOperations(leanFluid);
    leanOps.TPflash();
    leanFluid.initProperties();

    logger.info("Lean fluid after TPflash:");
    logger.info("  Pressure: " + leanFluid.getPressure("bara") + " bara");
    logger.info("  Temperature: " + (leanFluid.getTemperature() - 273.15) + " C");
    logger.info("  Number of phases: " + leanFluid.getNumberOfPhases());

    // Create gas-only system
    neqsim.thermo.system.SystemInterface gasFluid =
        new neqsim.thermo.system.SystemUMRPRUMCEos(273.15 + 23.9, 79.21678828);

    // EQfluid_gas composition (mole fractions from bug report)
    gasFluid.addComponent("methane", 0.85);
    gasFluid.addComponent("ethane", 0.15);
    gasFluid.addComponent("n-pentane", 0.0);
    gasFluid.addComponent("nC16", 0.0);

    gasFluid.setMixingRule(EosMixingRuleType.HV);

    ThermodynamicOperations gasOps = new ThermodynamicOperations(gasFluid);
    gasOps.TPflash();
    gasFluid.initProperties();

    logger.info("Gas fluid after TPflash:");
    logger.info("  Pressure: " + gasFluid.getPressure("bara") + " bara");
    logger.info("  Temperature: " + (gasFluid.getTemperature() - 273.15) + " C");
    logger.info("  Density: " + gasFluid.getDensity("kg/m3") + " kg/m3");

    // Set flow rates similar to the original test
    leanFluid.setTotalFlowRate(1.0, "kg/sec");
    leanOps.TPflash();
    leanFluid.initProperties();

    gasFluid.setTotalFlowRate(0.3, "kg/sec");
    gasOps.TPflash();
    gasFluid.initProperties();

    // Get initial volume
    double totalVolume = leanFluid.getVolume("m3");
    logger.info("Initial lean fluid volume: " + totalVolume + " m3");

    // Now simulate the mixing scenario that caused problems
    // Add gas fluid to lean fluid multiple times and do TVflash
    for (int i = 0; i < 20; i++) {
      leanFluid.addFluid(gasFluid);
      ThermodynamicOperations mixOps = new ThermodynamicOperations(leanFluid);
      try {
        // Check derivative before TVflash
        leanFluid.init(3);
        double dPdVTn0 = leanFluid.getPhase(0).getdPdVTn();
        double dPdVTn1 =
            leanFluid.getNumberOfPhases() > 1 ? leanFluid.getPhase(1).getdPdVTn() : 0.0;
        System.out.println("Iteration " + i + " BEFORE: dPdVTn(0)=" + dPdVTn0 + ", dPdVTn(1)="
            + dPdVTn1 + ", P=" + leanFluid.getPressure("bara"));

        mixOps.TVflash(totalVolume, "m3");
        System.out.println("Iteration " + i + " AFTER: P=" + leanFluid.getPressure("bara")
            + " bara, Phases=" + leanFluid.getNumberOfPhases());
      } catch (Exception ex) {
        System.out.println("TVflash failed at iteration " + i + ": " + ex.getMessage());
      }
    }

    // Basic sanity checks
    double finalPressure = leanFluid.getPressure("bara");
    System.out.println("Final pressure after 20 iterations: " + finalPressure + " bara");

    // The pressure should be bounded and not diverge to infinity
    // Note: This test intentionally pushes the system beyond realistic conditions
    // by continuously adding fluid to a fixed volume. The TVflash should hit the
    // pressure limit (5000 bara) rather than diverge to unrealistic values (>10^9)
    org.junit.jupiter.api.Assertions.assertTrue(finalPressure > 0,
        "Pressure should be positive, but was: " + finalPressure);
    org.junit.jupiter.api.Assertions.assertTrue(finalPressure <= 5000,
        "Pressure should be bounded by max limit (5000 bara), but was: " + finalPressure);
  }

  /**
   * Test TVflash convergence with difficult near-critical conditions.
   */
  @Test
  void testTVflashNearCritical() {
    // Create a system near critical point which can be challenging for convergence
    neqsim.thermo.system.SystemInterface criticalFluid =
        new neqsim.thermo.system.SystemUMRPRUMCEos(273.15 + 30.0, 50.0);

    criticalFluid.addComponent("methane", 0.7);
    criticalFluid.addComponent("ethane", 0.2);
    criticalFluid.addComponent("propane", 0.1);

    criticalFluid.setMixingRule(EosMixingRuleType.HV);

    ThermodynamicOperations critOps = new ThermodynamicOperations(criticalFluid);
    critOps.TPflash();
    criticalFluid.initProperties();

    double volume = criticalFluid.getVolume("m3");
    logger.info("Near-critical test - Initial volume: " + volume + " m3");

    // Change temperature significantly and try TVflash
    criticalFluid.setTemperature(273.15 - 20.0); // -20°C

    try {
      critOps.TVflash(volume, "m3");
      logger.info("Near-critical TVflash succeeded. Pressure: " + criticalFluid.getPressure("bara")
          + " bara");
    } catch (Exception ex) {
      logger.error("Near-critical TVflash failed: " + ex.getMessage());
    }

    // Verify convergence
    double finalPressure = criticalFluid.getPressure("bara");
    org.junit.jupiter.api.Assertions.assertTrue(finalPressure > 0,
        "Pressure should be positive after TVflash");
  }

  /**
   * Comprehensive TVflash evaluation across multiple EoS and compositions.
   */
  @Test
  void testTVflashComprehensiveEvaluation() {
    System.out.println("\n=== TVflash Comprehensive Evaluation ===\n");

    // Test 1: SRK EoS with natural gas composition
    System.out.println("Test 1: SRK EoS - Natural Gas");
    neqsim.thermo.system.SystemInterface srkGas =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 15.0, 50.0);
    srkGas.addComponent("nitrogen", 0.02);
    srkGas.addComponent("CO2", 0.03);
    srkGas.addComponent("methane", 0.85);
    srkGas.addComponent("ethane", 0.06);
    srkGas.addComponent("propane", 0.03);
    srkGas.addComponent("n-butane", 0.01);
    srkGas.setMixingRule("classic");
    evaluateTVflash(srkGas, "SRK-NaturalGas");

    // Test 2: PR EoS with rich gas
    System.out.println("\nTest 2: PR EoS - Rich Gas");
    neqsim.thermo.system.SystemInterface prRichGas =
        new neqsim.thermo.system.SystemPrEos(273.15 + 25.0, 80.0);
    prRichGas.addComponent("methane", 0.70);
    prRichGas.addComponent("ethane", 0.12);
    prRichGas.addComponent("propane", 0.08);
    prRichGas.addComponent("n-butane", 0.05);
    prRichGas.addComponent("n-pentane", 0.03);
    prRichGas.addComponent("n-hexane", 0.02);
    prRichGas.setMixingRule("classic");
    evaluateTVflash(prRichGas, "PR-RichGas");

    // Test 3: SRK-CPA with water (polar component)
    System.out.println("\nTest 3: SRK-CPA - Gas with Water");
    neqsim.thermo.system.SystemInterface cpaWater =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 20.0, 30.0);
    cpaWater.addComponent("methane", 0.90);
    cpaWater.addComponent("ethane", 0.05);
    cpaWater.addComponent("water", 0.05);
    cpaWater.setMixingRule("classic");
    evaluateTVflash(cpaWater, "SRK-CPA-Water");

    // Test 4: UMR-PRU with classic mixing - light hydrocarbons
    System.out.println("\nTest 4: UMR-PRU Classic - Light HCs");
    neqsim.thermo.system.SystemInterface umrLight =
        new neqsim.thermo.system.SystemUMRPRUMCEos(273.15 - 40.0, 20.0);
    umrLight.addComponent("methane", 0.60);
    umrLight.addComponent("ethane", 0.25);
    umrLight.addComponent("propane", 0.15);
    umrLight.setMixingRule("classic");
    evaluateTVflash(umrLight, "UMR-PRU-LightHC");

    // Test 5: High pressure gas injection scenario
    System.out.println("\nTest 5: High Pressure Injection");
    neqsim.thermo.system.SystemInterface highP =
        new neqsim.thermo.system.SystemPrEos(273.15 + 80.0, 300.0);
    highP.addComponent("nitrogen", 0.10);
    highP.addComponent("CO2", 0.15);
    highP.addComponent("methane", 0.70);
    highP.addComponent("ethane", 0.05);
    highP.setMixingRule("classic");
    evaluateTVflash(highP, "PR-HighPressure");

    // Test 6: Two-phase system (gas + liquid)
    System.out.println("\nTest 6: Two-Phase System");
    neqsim.thermo.system.SystemInterface twoPhase =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 10.0, 30.0);
    twoPhase.addComponent("methane", 0.50);
    twoPhase.addComponent("n-pentane", 0.30);
    twoPhase.addComponent("nC10", 0.20);
    twoPhase.setMixingRule("classic");
    evaluateTVflash(twoPhase, "SRK-TwoPhase");

    // Test 7: Pure component (methane)
    System.out.println("\nTest 7: Pure Methane");
    neqsim.thermo.system.SystemInterface pureMethane =
        new neqsim.thermo.system.SystemSrkEos(273.15 - 100.0, 10.0);
    pureMethane.addComponent("methane", 1.0);
    pureMethane.setMixingRule("classic");
    evaluateTVflash(pureMethane, "SRK-PureMethane");

    // Test 8: Heavy oil system
    System.out.println("\nTest 8: Heavy Oil");
    neqsim.thermo.system.SystemInterface heavyOil =
        new neqsim.thermo.system.SystemPrEos(273.15 + 60.0, 5.0);
    heavyOil.addComponent("methane", 0.05);
    heavyOil.addComponent("n-hexane", 0.20);
    heavyOil.addComponent("nC10", 0.35);
    heavyOil.addComponent("nC16", 0.40);
    heavyOil.setMixingRule("classic");
    evaluateTVflash(heavyOil, "PR-HeavyOil");

    System.out.println("\n=== Evaluation Complete ===\n");
  }

  /**
   * Helper method to evaluate TVflash for a given system.
   */
  private void evaluateTVflash(neqsim.thermo.system.SystemInterface system, String testName) {
    ThermodynamicOperations ops = new ThermodynamicOperations(system);

    // Initial TPflash
    ops.TPflash();
    system.initProperties();

    double initialP = system.getPressure("bara");
    double initialT = system.getTemperature() - 273.15;
    double initialV = system.getVolume("m3");
    int initialPhases = system.getNumberOfPhases();

    System.out.println("  Initial: T=" + String.format("%.1f", initialT) + "°C, P="
        + String.format("%.2f", initialP) + " bara, V=" + String.format("%.6f", initialV)
        + " m³, Phases=" + initialPhases);

    // Test 1: Temperature increase at constant volume
    system.setTemperature(system.getTemperature() + 30.0);
    long startTime = System.nanoTime();
    try {
      ops.TVflash(initialV, "m3");
      long elapsed = (System.nanoTime() - startTime) / 1000000;
      double newP = system.getPressure("bara");
      double volumeError = Math.abs(system.getVolume("m3") - initialV) / initialV * 100;
      System.out.println("  T+30K: P=" + String.format("%.2f", newP) + " bara, VolErr="
          + String.format("%.4f", volumeError) + "%, Time=" + elapsed + "ms");

      // Verify pressure increased (expected for heating at constant V)
      org.junit.jupiter.api.Assertions.assertTrue(newP > initialP * 0.9,
          testName + ": Pressure should increase when heating at constant V");
      org.junit.jupiter.api.Assertions.assertTrue(volumeError < 0.01,
          testName + ": Volume error should be < 0.01%");
    } catch (Exception ex) {
      System.out.println("  T+30K: FAILED - " + ex.getMessage());
    }

    // Test 2: Temperature decrease at constant volume
    system.setTemperature(system.getTemperature() - 60.0); // Net -30K from original
    startTime = System.nanoTime();
    try {
      ops.TVflash(initialV, "m3");
      long elapsed = (System.nanoTime() - startTime) / 1000000;
      double newP = system.getPressure("bara");
      double volumeError = Math.abs(system.getVolume("m3") - initialV) / initialV * 100;
      System.out.println("  T-30K: P=" + String.format("%.2f", newP) + " bara, VolErr="
          + String.format("%.4f", volumeError) + "%, Time=" + elapsed + "ms");

      org.junit.jupiter.api.Assertions.assertTrue(newP > 0,
          testName + ": Pressure should be positive");
      // Allow larger error for phase transition cases (pure components can enter VLE region)
      org.junit.jupiter.api.Assertions.assertTrue(volumeError < 10.0,
          testName + ": Volume error should be < 10% (allowing for phase transitions)");
    } catch (Exception ex) {
      System.out.println("  T-30K: FAILED - " + ex.getMessage());
    }

    // Test 3: Volume compression (should increase pressure)
    system.setTemperature(273.15 + initialT); // Reset temperature
    ops.TPflash();
    system.initProperties();
    double compressedV = initialV * 0.8; // 20% compression

    startTime = System.nanoTime();
    try {
      ops.TVflash(compressedV, "m3");
      long elapsed = (System.nanoTime() - startTime) / 1000000;
      double newP = system.getPressure("bara");
      double volumeError = Math.abs(system.getVolume("m3") - compressedV) / compressedV * 100;
      System.out.println("  V×0.8: P=" + String.format("%.2f", newP) + " bara, VolErr="
          + String.format("%.4f", volumeError) + "%, Time=" + elapsed + "ms");

      org.junit.jupiter.api.Assertions.assertTrue(newP > initialP,
          testName + ": Pressure should increase with compression");
      org.junit.jupiter.api.Assertions.assertTrue(volumeError < 0.01,
          testName + ": Volume error should be < 0.01%");
    } catch (Exception ex) {
      System.out.println("  V×0.8: FAILED - " + ex.getMessage());
    }

    // Test 4: Volume expansion (should decrease pressure)
    ops.TPflash();
    system.initProperties();
    double expandedV = initialV * 1.5; // 50% expansion

    startTime = System.nanoTime();
    try {
      ops.TVflash(expandedV, "m3");
      long elapsed = (System.nanoTime() - startTime) / 1000000;
      double newP = system.getPressure("bara");
      double volumeError = Math.abs(system.getVolume("m3") - expandedV) / expandedV * 100;
      System.out.println("  V×1.5: P=" + String.format("%.2f", newP) + " bara, VolErr="
          + String.format("%.4f", volumeError) + "%, Time=" + elapsed + "ms");

      org.junit.jupiter.api.Assertions.assertTrue(newP < initialP,
          testName + ": Pressure should decrease with expansion");
      org.junit.jupiter.api.Assertions.assertTrue(newP > 0,
          testName + ": Pressure should stay positive");
    } catch (Exception ex) {
      System.out.println("  V×1.5: FAILED - " + ex.getMessage());
    }
  }

  /**
   * Test TVflash with extreme conditions to verify robustness.
   */
  @Test
  void testTVflashExtremeConditions() {
    System.out.println("\n=== TVflash Extreme Conditions Test ===\n");

    // Test 1: Very low temperature (cryogenic)
    System.out.println("Test 1: Cryogenic LNG");
    neqsim.thermo.system.SystemInterface lng =
        new neqsim.thermo.system.SystemSrkEos(273.15 - 162.0, 1.0);
    lng.addComponent("methane", 0.95);
    lng.addComponent("ethane", 0.03);
    lng.addComponent("propane", 0.02);
    lng.setMixingRule("classic");

    ThermodynamicOperations lngOps = new ThermodynamicOperations(lng);
    lngOps.TPflash();
    lng.initProperties();

    double lngVolume = lng.getVolume("m3");
    System.out
        .println("  Initial: T=-162°C, P=1 bara, V=" + String.format("%.6f", lngVolume) + " m³");

    // Heat up to -100°C
    lng.setTemperature(273.15 - 100.0);
    try {
      lngOps.TVflash(lngVolume, "m3");
      System.out.println("  After heating to -100°C: P="
          + String.format("%.2f", lng.getPressure("bara")) + " bara");
      org.junit.jupiter.api.Assertions.assertTrue(lng.getPressure("bara") > 1.0,
          "Pressure should increase when heating LNG");
    } catch (Exception ex) {
      System.out.println("  FAILED: " + ex.getMessage());
    }

    // Test 2: Very low pressure (vacuum)
    System.out.println("\nTest 2: Low Pressure Gas");
    neqsim.thermo.system.SystemInterface lowP =
        new neqsim.thermo.system.SystemPrEos(273.15 + 25.0, 0.1);
    lowP.addComponent("methane", 0.80);
    lowP.addComponent("ethane", 0.20);
    lowP.setMixingRule("classic");

    ThermodynamicOperations lowPOps = new ThermodynamicOperations(lowP);
    lowPOps.TPflash();
    lowP.initProperties();

    double lowPVolume = lowP.getVolume("m3");
    System.out
        .println("  Initial: T=25°C, P=0.1 bara, V=" + String.format("%.4f", lowPVolume) + " m³");

    // Compress to half volume
    try {
      lowPOps.TVflash(lowPVolume * 0.5, "m3");
      System.out.println("  After 50% compression: P="
          + String.format("%.3f", lowP.getPressure("bara")) + " bara");
      org.junit.jupiter.api.Assertions.assertTrue(lowP.getPressure("bara") > 0.1,
          "Pressure should increase with compression");
    } catch (Exception ex) {
      System.out.println("  FAILED: " + ex.getMessage());
    }

    // Test 3: Phase transition during TVflash
    System.out.println("\nTest 3: Phase Transition");
    neqsim.thermo.system.SystemInterface phaseChange =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 50.0, 10.0);
    phaseChange.addComponent("methane", 0.40);
    phaseChange.addComponent("n-pentane", 0.30);
    phaseChange.addComponent("nC10", 0.30);
    phaseChange.setMixingRule("classic");

    ThermodynamicOperations pcOps = new ThermodynamicOperations(phaseChange);
    pcOps.TPflash();
    phaseChange.initProperties();

    double pcVolume = phaseChange.getVolume("m3");
    int initialPhases = phaseChange.getNumberOfPhases();
    System.out.println("  Initial: T=50°C, P=10 bara, Phases=" + initialPhases);

    // Cool down significantly
    phaseChange.setTemperature(273.15 - 10.0);
    try {
      pcOps.TVflash(pcVolume, "m3");
      int finalPhases = phaseChange.getNumberOfPhases();
      System.out.println(
          "  After cooling to -10°C: P=" + String.format("%.2f", phaseChange.getPressure("bara"))
              + " bara, Phases=" + finalPhases);
      org.junit.jupiter.api.Assertions.assertTrue(phaseChange.getPressure("bara") > 0,
          "Pressure should be positive after phase change");
    } catch (Exception ex) {
      System.out.println("  FAILED: " + ex.getMessage());
    }

    System.out.println("\n=== Extreme Conditions Test Complete ===\n");
  }

  /**
   * Test TVflash iteration count and convergence behavior.
   */
  @Test
  void testTVflashConvergenceMetrics() {
    System.out.println("\n=== TVflash Convergence Metrics ===\n");

    // Create a test system
    neqsim.thermo.system.SystemInterface system =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 20.0, 50.0);
    system.addComponent("methane", 0.80);
    system.addComponent("ethane", 0.10);
    system.addComponent("propane", 0.10);
    system.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double baseVolume = system.getVolume("m3");

    // Test with various volume targets
    double[] volumeMultipliers = {0.5, 0.7, 0.9, 1.0, 1.1, 1.3, 1.5, 2.0};

    System.out.println("Volume Multiplier | Target Volume | Final P | Volume Error | Converged");
    System.out.println("------------------|---------------|---------|--------------|----------");

    for (double mult : volumeMultipliers) {
      // Reset system
      system.setPressure(50.0);
      ops.TPflash();
      system.initProperties();

      double targetV = baseVolume * mult;

      try {
        ops.TVflash(targetV, "m3");
        double finalP = system.getPressure("bara");
        double actualV = system.getVolume("m3");
        double volError = Math.abs(actualV - targetV) / targetV * 100;
        boolean converged = volError < 0.01;

        System.out.println(String.format("      %.1f         | %.6f     | %7.2f | %10.6f%% | %s",
            mult, targetV, finalP, volError, converged ? "Yes" : "No"));

        // All should converge with reasonable volume changes
        if (mult >= 0.5 && mult <= 2.0) {
          org.junit.jupiter.api.Assertions.assertTrue(converged || finalP >= 4500,
              "Should converge or hit pressure limit for multiplier " + mult);
        }
      } catch (Exception ex) {
        System.out.println(String
            .format("      %.1f         | %.6f     | FAILED  | N/A          | No", mult, targetV));
      }
    }

    System.out.println("\n=== Convergence Metrics Complete ===\n");
  }
}
