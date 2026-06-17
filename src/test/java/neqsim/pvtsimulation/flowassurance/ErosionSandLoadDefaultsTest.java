package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for erosion rate prediction with sand load defaults by completion type.
 *
 * @author NeqSim
 */
public class ErosionSandLoadDefaultsTest {

  // ============================================================================
  // Sand load defaults tests
  // ============================================================================

  @Test
  public void testNaturalCompletionDefaults() {
    ErosionPredictionCalculator.SandLoadDefaults defaults =
        ErosionPredictionCalculator.getSandLoadDefaults("natural");
    assertNotNull(defaults);
    assertEquals(1.0, defaults.getLiquidPpmWt(), 1e-10);
    assertEquals(0.05, defaults.getGasPpmWt(), 1e-10);
    assertEquals(250.0, defaults.getParticleSizeMicrons(), 1e-10);
  }

  @Test
  public void testNaturalFailureDefaults() {
    ErosionPredictionCalculator.SandLoadDefaults defaults =
        ErosionPredictionCalculator.getSandLoadDefaults("natural_failure");
    assertNotNull(defaults);
    assertEquals(10.0, defaults.getLiquidPpmWt(), 1e-10);
    assertEquals(0.5, defaults.getGasPpmWt(), 1e-10);
    assertEquals(250.0, defaults.getParticleSizeMicrons(), 1e-10);
  }

  @Test
  public void testSASCompletionDefaults() {
    ErosionPredictionCalculator.SandLoadDefaults defaults =
        ErosionPredictionCalculator.getSandLoadDefaults("sas");
    assertNotNull(defaults);
    assertEquals(3.0, defaults.getLiquidPpmWt(), 1e-10);
    assertEquals(0.3, defaults.getGasPpmWt(), 1e-10);
    assertEquals(100.0, defaults.getParticleSizeMicrons(), 1e-10);
  }

  @Test
  public void testOHGPCompleteDefaults() {
    ErosionPredictionCalculator.SandLoadDefaults defaults =
        ErosionPredictionCalculator.getSandLoadDefaults("ohgp_complete");
    assertNotNull(defaults);
    assertEquals(1.0, defaults.getLiquidPpmWt(), 1e-10);
    assertEquals(0.05, defaults.getGasPpmWt(), 1e-10);
    assertEquals(50.0, defaults.getParticleSizeMicrons(), 1e-10);
  }

  @Test
  public void testUnknownCompletionTypeReturnsNull() {
    ErosionPredictionCalculator.SandLoadDefaults defaults =
        ErosionPredictionCalculator.getSandLoadDefaults("unknown_type");
    assertNull(defaults);
  }

  @Test
  public void testNullCompletionTypeReturnsNull() {
    ErosionPredictionCalculator.SandLoadDefaults defaults =
        ErosionPredictionCalculator.getSandLoadDefaults(null);
    assertNull(defaults);
  }

  @Test
  public void testAvailableCompletionTypes() {
    String[] types = ErosionPredictionCalculator.getAvailableCompletionTypes();
    assertNotNull(types);
    assertEquals(5, types.length);
  }

  // ============================================================================
  // Apply sand load defaults tests
  // ============================================================================

  @Test
  public void testApplySandLoadDefaultsLiquidFlow() {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();

    // Natural completion: 1 ppm wt sand in liquid, 100 m3/day at 800 kg/m3
    calc.applySandLoadDefaults("natural", 100.0, 0.0, 800.0);

    // Sand rate = 100 * 800 * 1e-6 = 0.08 kg/day
    assertEquals(0.08, calc.getSandRate(), 0.001);
    assertEquals(0.25, calc.getSandParticleDiameter(), 0.001); // 250 microns = 0.25 mm
  }

  @Test
  public void testApplySandLoadDefaultsGasFlow() {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();

    // Natural completion failure: 0.5 ppm wt in gas, 100000 kg/day gas
    calc.applySandLoadDefaults("natural_failure", 0.0, 100000.0, 0.0);

    // Sand rate = 100000 * 0.5e-6 = 0.05 kg/day
    assertEquals(0.05, calc.getSandRate(), 0.001);
    assertEquals(0.25, calc.getSandParticleDiameter(), 0.001);
  }

  @Test
  public void testApplySandLoadDefaultsMultiphase() {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();

    // SAS completion: 3 ppm wt liquid, 0.3 ppm wt gas
    // Liquid: 200 m3/day * 800 kg/m3 * 3e-6 = 0.48 kg/day
    // Gas: 500000 kg/day * 0.3e-6 = 0.15 kg/day
    // Max of liquid and gas = 0.48
    calc.applySandLoadDefaults("sas", 200.0, 500000.0, 800.0);

    assertEquals(0.48, calc.getSandRate(), 0.001);
    assertEquals(0.10, calc.getSandParticleDiameter(), 0.001); // 100 microns
  }

  // ============================================================================
  // Corrosion protection velocity limit tests
  // ============================================================================

  @Test
  public void testMaxVelocityForCorrosionProtection() {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
    calc.setMixtureDensity(100.0);

    // Without inhibitor, without sand
    double v1 = calc.calcMaxVelocityForCorrosionProtection(false, false);
    assertTrue(v1 > 0);

    // With inhibitor -> higher C-factor -> higher velocity
    double v2 = calc.calcMaxVelocityForCorrosionProtection(true, false);
    assertTrue(v2 > v1);

    // With sand -> reduced velocity
    double v3 = calc.calcMaxVelocityForCorrosionProtection(true, true);
    assertTrue(v3 < v2);
    assertTrue(v3 > 0);
  }

  @Test
  public void testMaxVelocityZeroDensity() {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
    calc.setMixtureDensity(0.0);

    double v = calc.calcMaxVelocityForCorrosionProtection(false, false);
    assertEquals(0.0, v, 1e-10);
  }

  // ============================================================================
  // Integration tests with existing calculator
  // ============================================================================

  @Test
  public void testFullCalculationWithSandDefaults() {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
    calc.setMixtureDensity(150.0);
    calc.setMixtureVelocity(10.0);
    calc.setPipeDiameter(0.1524);
    calc.setWallThickness(10.0);
    calc.setPipeMaterial("carbon_steel");
    calc.setGeometry("elbow");
    calc.setDesignLife(25.0);
    calc.setCorrosionAllowance(3.0);

    // Apply sand defaults for natural failure completion
    calc.applySandLoadDefaults("natural_failure", 500.0, 200000.0, 800.0);

    calc.calculate();

    assertTrue(calc.getErosionalVelocity() > 0);
    assertTrue(calc.getErosionRate() > 0);
    assertNotNull(calc.getRiskLevel());
    assertNotNull(calc.toJson());
  }

  @Test
  public void testSandLoadDefaultsGetterMethods() {
    ErosionPredictionCalculator.SandLoadDefaults defaults =
        ErosionPredictionCalculator.getSandLoadDefaults("natural");
    assertNotNull(defaults.getCompletionType());
    assertTrue(defaults.getCompletionType().contains("Natural"));
  }

  /**
   * Gets the erosion rate from the calculator.
   *
   * @param calc the calculator
   * @return erosion rate in mm/year
   */
  private double getErosionRate(ErosionPredictionCalculator calc) {
    // Access via JSON output since getErosionRate might not be a direct method name
    String json = calc.toJson();
    assertNotNull(json);
    return calc.getErosionRate();
  }
}
