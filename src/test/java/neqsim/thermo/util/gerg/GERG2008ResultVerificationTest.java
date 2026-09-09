package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verification test to ensure GERG-2008 caching optimizations don't change numerical results.
 *
 * This test compares results from: 1. Fresh GERG-2008 model (no caching) 2. Cached GERG-2008 model 3. Native
 * SystemGERG2008Eos 4. SRK + GERG property lookup
 *
 * All methods should produce identical results within numerical precision.
 *
 * @author esol
 */
public class GERG2008ResultVerificationTest {
  private static final Logger logger = LogManager.getLogger(GERG2008ResultVerificationTest.class);

  private static final double TOLERANCE = 1e-8;

  @BeforeEach
  void clearCache() {
    // Clear cache before each test to ensure fresh state
    NeqSimGERG2008.clearCache();
  }

  /**
   * Verify that cached GERG-2008 model produces same results as fresh model.
   */
  @Test
  void verifyCachedVsFreshGERG2008() {
    logger.info("=== Verify Cached vs Fresh GERG-2008 Model ===\n");

    // Create test fluid
    SystemInterface fluid = new SystemSrkEos(303.15, 75.0);
    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 7.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("i-butane", 0.5);
    fluid.addComponent("n-butane", 0.8);
    fluid.addComponent("CO2", 2.0);
    fluid.addComponent("nitrogen", 1.5);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();

    PhaseInterface phase = fluid.getPhase(0);

    // Test 1: Clear cache and get first result
    NeqSimGERG2008.clearCache();
    NeqSimGERG2008 gerg1 = new NeqSimGERG2008(phase);
    double[] props1 = gerg1.propertiesGERG();

    // Test 2: Second call should use cached model
    NeqSimGERG2008 gerg2 = new NeqSimGERG2008(phase);
    double[] props2 = gerg2.propertiesGERG();

    // Test 3: Third call to confirm consistency
    NeqSimGERG2008 gerg3 = new NeqSimGERG2008(phase);
    double[] props3 = gerg3.propertiesGERG();

    // Compare results
    logger.info("Property                  | Call 1         | Call 2         | Call 3");
    logger.info("--------------------------|----------------|----------------|--------");
    String[] propNames = { "Pressure (kPa)", "Z-factor", "dPdD", "d2PdD2", "d2PdTD", "dPdT", "U (J/mol)", "H (J/mol)",
        "S (J/mol-K)", "Cv (J/mol-K)", "Cp (J/mol-K)", "W (m/s)", "G (J/mol)", "JT (K/kPa)", "Kappa" };

    for (int i = 0; i < Math.min(props1.length, 15); i++) {
      String status = (props1[i] == props2[i] && props2[i] == props3[i]) ? "OK" : "FAIL";
      logger.printf(org.apache.logging.log4j.Level.INFO, "%-25s | %14.6f | %14.6f | %s%n",
          i < propNames.length ? propNames[i] : "Prop " + i, props1[i], props2[i], status);
      assertEquals(props1[i], props2[i], TOLERANCE, "Property " + i + " mismatch between calls");
      assertEquals(props2[i], props3[i], TOLERANCE, "Property " + i + " mismatch between calls");
    }

    logger.info("\n✓ All calls produce identical results with cached GERG-2008 model");
  }

  /**
   * Verify that multiple calls with same state return identical results.
   */
  @Test
  void verifyRepeatedCallsReturnSameResults() {
    logger.info("\n=== Verify Repeated Calls Return Same Results ===\n");

    SystemInterface fluid = new SystemGERG2008Eos(320.0, 80.0);
    fluid.addComponent("methane", 90.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 2.0);
    fluid.addComponent("CO2", 2.0);
    fluid.addComponent("nitrogen", 1.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // First call - properties calculated
    double density1 = fluid.getPhase(0).getDensity();
    double enthalpy1 = fluid.getPhase(0).getEnthalpy();
    double entropy1 = fluid.getPhase(0).getEntropy();
    double cp1 = fluid.getPhase(0).getCp();
    double cv1 = fluid.getPhase(0).getCv();
    double z1 = fluid.getPhase(0).getZ();

    // Second call - should use cached values
    fluid.init(2);
    double density2 = fluid.getPhase(0).getDensity();
    double enthalpy2 = fluid.getPhase(0).getEnthalpy();
    double entropy2 = fluid.getPhase(0).getEntropy();
    double cp2 = fluid.getPhase(0).getCp();
    double cv2 = fluid.getPhase(0).getCv();
    double z2 = fluid.getPhase(0).getZ();

    // Third call - still cached
    fluid.init(2);
    double density3 = fluid.getPhase(0).getDensity();
    double enthalpy3 = fluid.getPhase(0).getEnthalpy();
    double entropy3 = fluid.getPhase(0).getEntropy();
    double cp3 = fluid.getPhase(0).getCp();
    double cv3 = fluid.getPhase(0).getCv();
    double z3 = fluid.getPhase(0).getZ();

    logger.info("Property     | Call 1         | Call 2         | Call 3         | Match");
    logger.info("-------------|----------------|----------------|----------------|------");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Density      | %14.6f | %14.6f | %14.6f | %s%n", density1,
        density2, density3, density1 == density2 && density2 == density3 ? "OK" : "FAIL");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Enthalpy     | %14.6f | %14.6f | %14.6f | %s%n", enthalpy1,
        enthalpy2, enthalpy3, enthalpy1 == enthalpy2 && enthalpy2 == enthalpy3 ? "OK" : "FAIL");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Entropy      | %14.6f | %14.6f | %14.6f | %s%n", entropy1,
        entropy2, entropy3, entropy1 == entropy2 && entropy2 == entropy3 ? "OK" : "FAIL");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Cp           | %14.6f | %14.6f | %14.6f | %s%n", cp1, cp2, cp3,
        cp1 == cp2 && cp2 == cp3 ? "OK" : "FAIL");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Cv           | %14.6f | %14.6f | %14.6f | %s%n", cv1, cv2, cv3,
        cv1 == cv2 && cv2 == cv3 ? "OK" : "FAIL");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Z-factor     | %14.6f | %14.6f | %14.6f | %s%n", z1, z2, z3,
        z1 == z2 && z2 == z3 ? "OK" : "FAIL");

    // Verify exact equality (caching should return identical values)
    assertEquals(density1, density2, 0.0, "Density should be identical");
    assertEquals(density2, density3, 0.0, "Density should be identical");
    assertEquals(enthalpy1, enthalpy2, 0.0, "Enthalpy should be identical");
    assertEquals(enthalpy2, enthalpy3, 0.0, "Enthalpy should be identical");
    assertEquals(entropy1, entropy2, 0.0, "Entropy should be identical");
    assertEquals(cp1, cp2, 0.0, "Cp should be identical");
    assertEquals(cv1, cv2, 0.0, "Cv should be identical");
    assertEquals(z1, z2, 0.0, "Z-factor should be identical");

    logger.info("\n✓ All repeated calls return identical results");
  }

  /**
   * Verify that direct GERG-2008 state changes recalculate and return without stale cached properties.
   */
  @Test
  void verifyStateChangeTriggersRecalculation() {
    SystemInterface fluid = createDirectGergGas(300.0, 50.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    assertDirectGasFlashState(fluid, "300 K / 50 bara");
    SystemInterface reference = fluid.clone();
    double density1 = fluid.getPhase(0).getDensity();
    double enthalpy1 = fluid.getPhase(0).getEnthalpy();

    fluid.setTemperature(350.0);
    ops.TPflash();
    assertDirectGasFlashState(fluid, "350 K / 50 bara");
    double density2 = fluid.getPhase(0).getDensity();
    double enthalpy2 = fluid.getPhase(0).getEnthalpy();

    fluid.setPressure(100.0);
    ops.TPflash();
    assertDirectGasFlashState(fluid, "350 K / 100 bara");
    double density3 = fluid.getPhase(0).getDensity();

    SystemInterface freshChanged = createDirectGergGas(350.0, 100.0);
    new ThermodynamicOperations(freshChanged).TPflash();
    assertDirectGasFlashEquivalent(freshChanged, fluid, 1.0e-8, "fresh/reused changed state");

    assertTrue(density2 < density1, "higher temperature at constant pressure must lower density");
    assertTrue(density3 > density2, "higher pressure at constant temperature must raise density");
    assertTrue(enthalpy2 > enthalpy1, "higher temperature must raise enthalpy");

    fluid.setTemperature(300.0);
    fluid.setPressure(50.0);
    ops.TPflash();
    assertDirectGasFlashEquivalent(reference, fluid, 1.0e-8, "returned state");

    SystemInterface previous = fluid.clone();
    ops.TPflash();
    assertDirectGasFlashEquivalent(previous, fluid, 1.0e-10, "deterministic repeat");
  }

  /**
   * Compare native GERG-2008 EoS with SRK + GERG property lookup.
   */
  @Test
  void compareNativeGERGWithSRKPlusGERG() {
    logger.info("\n=== Compare Native GERG-2008 vs SRK + GERG Property Lookup ===\n");

    double temperature = 310.0;
    double pressure = 60.0;

    // Native GERG-2008 EoS
    SystemInterface gergFluid = new SystemGERG2008Eos(temperature, pressure);
    gergFluid.addComponent("methane", 85.0);
    gergFluid.addComponent("ethane", 7.0);
    gergFluid.addComponent("propane", 3.0);
    gergFluid.addComponent("CO2", 3.0);
    gergFluid.addComponent("nitrogen", 2.0);
    gergFluid.setMixingRule("classic");
    gergFluid.setForceSinglePhase("GAS");
    gergFluid.init(2);

    // SRK + GERG property lookup
    SystemInterface srkFluid = new SystemSrkEos(temperature, pressure);
    srkFluid.addComponent("methane", 85.0);
    srkFluid.addComponent("ethane", 7.0);
    srkFluid.addComponent("propane", 3.0);
    srkFluid.addComponent("CO2", 3.0);
    srkFluid.addComponent("nitrogen", 2.0);
    srkFluid.setMixingRule("classic");
    srkFluid.setForceSinglePhase("GAS");
    srkFluid.init(2);

    // Get GERG properties from SRK phase
    double[] srkGergProps = srkFluid.getPhase(0).getProperties_GERG2008();

    // Get GERG properties directly for comparison
    double[] nativeGergProps = gergFluid.getPhase(0).getProperties_GERG2008();

    logger.info("Property          | Native GERG EoS | SRK + GERG Props | Diff (%)");
    logger.info("------------------|-----------------|------------------|----------");

    // Compare key properties
    double densityDiffPct = 100.0 * Math.abs(nativeGergProps[0] - srkGergProps[0]) / Math.abs(srkGergProps[0]);
    double zDiffPct = 100.0 * Math.abs(nativeGergProps[1] - srkGergProps[1]) / Math.abs(srkGergProps[1]);
    double enthalpyDiffPct = 100.0 * Math.abs(nativeGergProps[7] - srkGergProps[7]) / Math.abs(srkGergProps[7]);
    double entropyDiffPct = 100.0 * Math.abs(nativeGergProps[8] - srkGergProps[8]) / Math.abs(srkGergProps[8]);
    double cpDiffPct = 100.0 * Math.abs(nativeGergProps[10] - srkGergProps[10]) / Math.abs(srkGergProps[10]);

    logger.printf(org.apache.logging.log4j.Level.INFO, "Pressure (kPa)    | %15.6f | %16.6f | %8.6f%n",
        nativeGergProps[0], srkGergProps[0], densityDiffPct);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Z-factor          | %15.6f | %16.6f | %8.6f%n",
        nativeGergProps[1], srkGergProps[1], zDiffPct);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Enthalpy (J/mol)  | %15.6f | %16.6f | %8.6f%n",
        nativeGergProps[7], srkGergProps[7], enthalpyDiffPct);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Entropy (J/mol-K) | %15.6f | %16.6f | %8.6f%n",
        nativeGergProps[8], srkGergProps[8], entropyDiffPct);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Cp (J/mol-K)      | %15.6f | %16.6f | %8.6f%n",
        nativeGergProps[10], srkGergProps[10], cpDiffPct);

    // Both should use the same GERG-2008 calculation, so results should be identical
    assertEquals(nativeGergProps[0], srkGergProps[0], TOLERANCE,
        "Pressure should match between native GERG and SRK+GERG");
    assertEquals(nativeGergProps[1], srkGergProps[1], TOLERANCE,
        "Z-factor should match between native GERG and SRK+GERG");
    assertEquals(nativeGergProps[7], srkGergProps[7], TOLERANCE,
        "Enthalpy should match between native GERG and SRK+GERG");
    assertEquals(nativeGergProps[8], srkGergProps[8], TOLERANCE,
        "Entropy should match between native GERG and SRK+GERG");
    assertEquals(nativeGergProps[10], srkGergProps[10], TOLERANCE, "Cp should match between native GERG and SRK+GERG");

    logger.info("\n✓ Native GERG-2008 EoS and SRK + GERG property lookup produce identical results");
  }

  /**
   * Verify compressor results are consistent with GERG-2008 optimizations.
   */
  @Test
  void verifyCompressorResultsWithGERG() {
    logger.info("\n=== Verify Compressor Results with GERG-2008 ===\n");

    // Create fluid
    SystemInterface fluid = new SystemSrkEos(303.15, 50.0);
    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 7.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("i-butane", 0.5);
    fluid.addComponent("n-butane", 0.8);
    fluid.addComponent("CO2", 2.0);
    fluid.addComponent("nitrogen", 1.5);
    fluid.setMixingRule("classic");

    neqsim.process.equipment.stream.Stream inletStream = new neqsim.process.equipment.stream.Stream("inlet", fluid);
    inletStream.setFlowRate(100.0, "kg/sec");
    inletStream.run();

    // Run compressor with GERG-2008
    neqsim.process.equipment.compressor.Compressor compressor = new neqsim.process.equipment.compressor.Compressor(
        "compressor", inletStream);
    compressor.setOutletPressure(100.0, "bara");
    compressor.setPolytropicEfficiency(0.78);
    compressor.setUsePolytropicCalc(true);
    compressor.setUseGERG2008(true);
    compressor.run();

    double power1 = compressor.getPower();
    double outletTemp1 = compressor.getOutletStream().getTemperature();
    double polytropicHead1 = compressor.getPolytropicHead();

    // Run again - results should be identical
    compressor.run();
    double power2 = compressor.getPower();
    double outletTemp2 = compressor.getOutletStream().getTemperature();
    double polytropicHead2 = compressor.getPolytropicHead();

    // Run a third time
    compressor.run();
    double power3 = compressor.getPower();
    double outletTemp3 = compressor.getOutletStream().getTemperature();
    double polytropicHead3 = compressor.getPolytropicHead();

    logger.info("Run | Power (kW)      | Outlet T (K)    | Polytropic Head (kJ/kg)");
    logger.info("----|-----------------|-----------------|------------------------");
    logger.printf(org.apache.logging.log4j.Level.INFO, "1   | %15.3f | %15.3f | %22.3f%n", power1 / 1000, outletTemp1,
        polytropicHead1);
    logger.printf(org.apache.logging.log4j.Level.INFO, "2   | %15.3f | %15.3f | %22.3f%n", power2 / 1000, outletTemp2,
        polytropicHead2);
    logger.printf(org.apache.logging.log4j.Level.INFO, "3   | %15.3f | %15.3f | %22.3f%n", power3 / 1000, outletTemp3,
        polytropicHead3);

    // Verify consistent results
    assertEquals(power1, power2, 1.0, "Power should be consistent between runs");
    assertEquals(power2, power3, 1.0, "Power should be consistent between runs");
    assertEquals(outletTemp1, outletTemp2, 0.01, "Outlet temperature should be consistent");
    assertEquals(outletTemp2, outletTemp3, 0.01, "Outlet temperature should be consistent");
    assertEquals(polytropicHead1, polytropicHead2, 0.1, "Polytropic head should be consistent");
    assertEquals(polytropicHead2, polytropicHead3, 0.1, "Polytropic head should be consistent");

    logger.info("\n✓ Compressor produces consistent results with GERG-2008 optimizations");
  }

  /**
   * Test that direct GERG-2008 TP flash exposes finite thermodynamic properties.
   *
   * <p>
   * Transport properties remain unavailable for native GERG-2008 phases; use a compatible transport-property model when
   * viscosity or thermal conductivity is required.
   * </p>
   */
  @Test
  void verifyThermodynamicPropertiesAccessible() {
    SystemInterface fluid = createDirectGergGas(303.15, 50.0);
    new ThermodynamicOperations(fluid).TPflash();
    assertDirectGasFlashState(fluid, "thermodynamic property access");

    assertTrue(fluid.getPhase(0).getDensity() > 0.0, "density must be positive");
    assertTrue(fluid.getPhase(0).getCp() > 0.0, "Cp must be positive");
    assertTrue(fluid.getPhase(0).getCv() > 0.0, "Cv must be positive");
    assertTrue(fluid.getPhase(0).getSoundSpeed() > 0.0, "sound speed must be positive");
  }

  private SystemInterface createDirectGergGas(double temperatureK, double pressureBara) {
    SystemInterface fluid = new SystemGERG2008Eos(temperatureK, pressureBara);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("CO2", 0.02);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private void assertDirectGasFlashState(SystemInterface fluid, String label) {
    assertEquals(1, fluid.getNumberOfPhases(), label + " phase count");
    assertEquals(neqsim.thermo.phase.PhaseType.GAS, fluid.getPhase(0).getType(), label + " phase type");
    assertEquals(1.0, fluid.getBeta(0), 5.0e-12, label + " beta");

    double compositionTotal = 0.0;
    double maximumMaterialResidual = 0.0;
    for (int component = 0; component < fluid.getPhase(0).getNumberOfComponents(); component++) {
      double composition = fluid.getPhase(0).getComponent(component).getx();
      double feedComposition = fluid.getPhase(0).getComponent(component).getz();
      double fugacityCoefficient = fluid.getPhase(0).getComponent(component).getFugacityCoefficient();
      assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0,
          label + " composition " + component);
      assertTrue(Double.isFinite(fugacityCoefficient) && fugacityCoefficient > 0.0,
          label + " fugacity coefficient " + component);
      compositionTotal += composition;
      maximumMaterialResidual = Math.max(maximumMaterialResidual, Math.abs(feedComposition - composition));
    }
    assertEquals(1.0, compositionTotal, 5.0e-12, label + " composition normalization");
    assertTrue(maximumMaterialResidual < 1.0e-10, label + " single-phase material residual " + maximumMaterialResidual);

    assertTrue(Double.isFinite(fluid.getPhase(0).getZ()) && fluid.getPhase(0).getZ() > 0.0, label + " compressibility");
    assertTrue(Double.isFinite(fluid.getPhase(0).getDensity()) && fluid.getPhase(0).getDensity() > 0.0,
        label + " density");
    assertTrue(Double.isFinite(fluid.getPhase(0).getEnthalpy()), label + " enthalpy");
    assertTrue(Double.isFinite(fluid.getPhase(0).getEntropy()), label + " entropy");
    assertTrue(Double.isFinite(fluid.getPhase(0).getCp()) && fluid.getPhase(0).getCp() > 0.0, label + " Cp");
    assertTrue(Double.isFinite(fluid.getPhase(0).getCv()) && fluid.getPhase(0).getCv() > 0.0, label + " Cv");
    assertTrue(Double.isFinite(fluid.getPhase(0).getSoundSpeed()) && fluid.getPhase(0).getSoundSpeed() > 0.0,
        label + " sound speed");
    assertTrue(Double.isFinite(fluid.getPhase(0).getJouleThomsonCoefficient()), label + " Joule-Thomson coefficient");
  }

  private void assertDirectGasFlashEquivalent(SystemInterface expected, SystemInterface actual,
      double relativeTolerance, String label) {
    assertDirectGasFlashState(expected, label + " expected");
    assertDirectGasFlashState(actual, label + " actual");
    assertRelativeEquals(expected.getBeta(0), actual.getBeta(0), relativeTolerance, label + " beta");
    assertRelativeEquals(expected.getPhase(0).getZ(), actual.getPhase(0).getZ(), relativeTolerance,
        label + " compressibility");
    assertRelativeEquals(expected.getPhase(0).getDensity(), actual.getPhase(0).getDensity(), relativeTolerance,
        label + " density");
    assertRelativeEquals(expected.getPhase(0).getEnthalpy(), actual.getPhase(0).getEnthalpy(), relativeTolerance,
        label + " enthalpy");
    assertRelativeEquals(expected.getPhase(0).getEntropy(), actual.getPhase(0).getEntropy(), relativeTolerance,
        label + " entropy");
    assertRelativeEquals(expected.getPhase(0).getCp(), actual.getPhase(0).getCp(), relativeTolerance, label + " Cp");
    assertRelativeEquals(expected.getPhase(0).getCv(), actual.getPhase(0).getCv(), relativeTolerance, label + " Cv");
    assertRelativeEquals(expected.getPhase(0).getSoundSpeed(), actual.getPhase(0).getSoundSpeed(), relativeTolerance,
        label + " sound speed");
    assertRelativeEquals(expected.getPhase(0).getJouleThomsonCoefficient(),
        actual.getPhase(0).getJouleThomsonCoefficient(), relativeTolerance, label + " Joule-Thomson coefficient");

    for (int component = 0; component < expected.getPhase(0).getNumberOfComponents(); component++) {
      assertRelativeEquals(expected.getPhase(0).getComponent(component).getx(),
          actual.getPhase(0).getComponent(component).getx(), relativeTolerance, label + " composition " + component);
      assertRelativeEquals(expected.getPhase(0).getComponent(component).getFugacityCoefficient(),
          actual.getPhase(0).getComponent(component).getFugacityCoefficient(), relativeTolerance,
          label + " fugacity coefficient " + component);
    }
  }

  private void assertRelativeEquals(double expected, double actual, double relativeTolerance, String label) {
    assertEquals(expected, actual, Math.max(1.0e-10, relativeTolerance * Math.abs(expected)), label);
  }
}
