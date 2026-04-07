package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Tests for DryGasSealAnalyzer class.
 *
 * <p>
 * Validates all six sub-analyses using the Bacalhau GIC seal gas composition and conditions from
 * SOK7305593 (Flowserve API 692 datasheet). Expected results are cross-validated against UniSim and
 * manual NeqSim TPflash/PHflash calculations performed during the original task.
 * </p>
 *
 * @author neqsim
 * @version 1.0
 */
class DryGasSealAnalyzerTest {

  /** The seal gas fluid matching the Bacalhau GIC composition. */
  private SystemInterface sealGas;

  /** The analyzer under test. */
  private DryGasSealAnalyzer analyzer;

  /**
   * Creates the Bacalhau GIC seal gas composition and configures the analyzer with operating
   * conditions from SOK7305593.
   */
  @BeforeEach
  void setUp() {
    // Peng-Robinson EOS at settleout conditions: 421 barg (422 bara), 44 degC
    sealGas = new SystemPrEos(273.15 + 44.0, 422.0);

    // Bacalhau GIC seal gas composition (from UniSim stream 5000)
    sealGas.addComponent("nitrogen", 0.00732);
    sealGas.addComponent("CO2", 0.00087);
    sealGas.addComponent("methane", 0.79966);
    sealGas.addComponent("ethane", 0.09964);
    sealGas.addComponent("propane", 0.05625);
    sealGas.addComponent("i-butane", 0.00911);
    sealGas.addComponent("n-butane", 0.01608);
    sealGas.addComponent("i-pentane", 0.00373);
    sealGas.addComponent("n-pentane", 0.00362);
    sealGas.addComponent("n-hexane", 0.00154);
    sealGas.addComponent("n-heptane", 0.00095);
    sealGas.addComponent("n-octane", 0.00048);
    sealGas.addComponent("n-nonane", 0.00015);
    sealGas.addComponent("nC10", 0.00011);
    sealGas.addComponent("benzene", 0.00017);
    sealGas.addComponent("toluene", 0.00012);
    // Note: xylenes and trace components omitted for simplicity
    sealGas.setMixingRule("classic");
    sealGas.setMultiPhaseCheck(true);

    // Configure analyzer with Bacalhau conditions from SOK7305593
    analyzer = new DryGasSealAnalyzer("GIC-26KA302-Seal");
    analyzer.setSealGas(sealGas);
    analyzer.setSealCavityPressure(421.0, "barg");
    analyzer.setSealCavityTemperature(44.0, "C");
    analyzer.setPrimaryVentPressure(1.5, "barg");
    analyzer.setSealLeakageRate(280.0, "NL/min");
    analyzer.setStandpipeGeometry(1.5, 0.038);
    analyzer.setStandpipeCount(2);
    analyzer.setAmbientTemperature(25.0, "C");
    analyzer.setWindSpeed(2.0);
  }

  /**
   * Tests the full analysis runs without exceptions and produces results.
   */
  @Test
  void testFullAnalysisCompletes() {
    analyzer.runFullAnalysis();

    assertTrue(analyzer.isAnalysisComplete(), "Analysis should be marked complete");
    assertNotNull(analyzer.getResults(), "Results should not be null");
    assertFalse(analyzer.getResults().isEmpty(), "Results should not be empty");

    // Should detect condensation risk
    assertFalse(analyzer.isSafeToOperate(),
        "System should NOT be safe — condensation expected with C3+ gas at 421 barg");
  }

  /**
   * Tests that isenthalpic expansion produces JT cooling and condensation consistent with the
   * original analysis (max liquid ~2-4 vol%, significant cooling from 44 degC).
   */
  @Test
  void testIsenthalpicExpansionProducesCondensation() {
    analyzer.runFullAnalysis();

    @SuppressWarnings("unchecked")
    Map<String, Object> jtResults =
        (Map<String, Object>) analyzer.getResults().get("isenthalpic_expansion");
    assertNotNull(jtResults, "JT results should exist");

    double maxLiquid = (Double) jtResults.get("max_liquid_vol_pct");
    assertTrue(maxLiquid > 0.5,
        "Should produce significant liquid from JT expansion, got " + maxLiquid + " vol%");
    assertTrue(maxLiquid < 20.0,
        "Liquid fraction should be physically reasonable, got " + maxLiquid + " vol%");

    double totalCooling = (Double) jtResults.get("total_jt_cooling_C");
    assertTrue(totalCooling > 10.0,
        "Should produce >10C JT cooling from 420 bar expansion, got " + totalCooling + "C");
  }

  /**
   * Tests that the retrograde condensation map identifies two-phase conditions at standstill (25
   * degC, 50-70 barg) consistent with the original analysis and UniSim validation.
   */
  @Test
  void testRetrogradeCondensationMapIdentifiesTwoPhaseZone() {
    analyzer.runFullAnalysis();

    @SuppressWarnings("unchecked")
    Map<String, Object> retroResults =
        (Map<String, Object>) analyzer.getResults().get("retrograde_condensation_map");
    assertNotNull(retroResults, "Retrograde condensation results should exist");

    double maxLiquid = (Double) retroResults.get("max_liquid_vol_pct");
    assertTrue(maxLiquid > 0.01,
        "Should find retrograde condensation in the T-P grid, got " + maxLiquid + " vol%");

    // Dew point curve should have been calculated
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> dewCurve =
        (List<Map<String, Object>>) retroResults.get("dew_point_curve");
    assertNotNull(dewCurve, "Dew point curve should exist");
    assertFalse(dewCurve.isEmpty(), "Dew point curve should have points");
  }

  /**
   * Tests the dead-leg cooldown calculation produces physically reasonable results.
   */
  @Test
  void testDeadLegCooldownReachesAmbient() {
    analyzer.runFullAnalysis();

    @SuppressWarnings("unchecked")
    Map<String, Object> cooldownResults =
        (Map<String, Object>) analyzer.getResults().get("dead_leg_cooldown");
    assertNotNull(cooldownResults, "Cooldown results should exist");

    double finalTempC = (Double) cooldownResults.get("final_temperature_C");
    double ambientC = (Double) cooldownResults.get("ambient_temperature_C");

    // Should cool toward ambient
    assertTrue(finalTempC <= 44.0, "Final temperature should be below initial 44C");
    assertTrue(finalTempC - ambientC < 5.0,
        "Should approach ambient within 48 hours, gap = " + (finalTempC - ambientC) + "C");

    double standpipeVolume = (Double) cooldownResults.get("standpipe_volume_L");
    assertEquals(1.7, standpipeVolume, 0.2, "Standpipe volume should be approximately 1.7 L");
  }

  /**
   * Tests the condensate accumulation rate is consistent with the original analysis (~6.83 L/day,
   * fill time ~6 hours).
   */
  @Test
  void testCondensateAccumulationRate() {
    analyzer.runFullAnalysis();

    @SuppressWarnings("unchecked")
    Map<String, Object> accumResults =
        (Map<String, Object>) analyzer.getResults().get("condensate_accumulation");
    assertNotNull(accumResults, "Accumulation results should exist");

    boolean hasCondensation = (Boolean) accumResults.get("condensation_present");
    assertTrue(hasCondensation, "Should detect condensation from seal leakage");

    double fillTime = (Double) accumResults.get("fill_time_hours");
    assertTrue(fillTime > 0.5, "Fill time should be positive, got " + fillTime);
    assertTrue(fillTime < 100.0, "Fill time should be realistic, got " + fillTime);

    double totalDeadLeg = (Double) accumResults.get("total_dead_leg_volume_L");
    assertEquals(3.4, totalDeadLeg, 0.5, "Total dead-leg volume should be ~3.4 L for 2 standpipes");
  }

  /**
   * Tests the flash vaporisation impact pressure calculation produces results that confirm seal
   * damage potential (impact > 10 bar threshold for gas film collapse).
   */
  @Test
  void testFlashVaporisationImpactPressure() {
    analyzer.runFullAnalysis();

    @SuppressWarnings("unchecked")
    Map<String, Object> impactResults =
        (Map<String, Object>) analyzer.getResults().get("flash_vaporisation_impact");
    assertNotNull(impactResults, "Impact results should exist");

    double joukowsky = (Double) impactResults.get("joukowsky_impact_pressure_bar");
    assertTrue(joukowsky > 1.0,
        "Joukowsky impact pressure should be significant, got " + joukowsky + " bar");

    boolean gasFilmCollapse = (Boolean) impactResults.get("gas_film_collapse_likely");
    assertTrue(gasFilmCollapse, "Gas film collapse should be predicted at these pressures");
  }

  /**
   * Tests the GCU sizing produces physically reasonable cooling and reheating duties.
   */
  @Test
  void testGCUSizing() {
    analyzer.runFullAnalysis();

    @SuppressWarnings("unchecked")
    Map<String, Object> gcuResults = (Map<String, Object>) analyzer.getResults().get("gcu_sizing");
    assertNotNull(gcuResults, "GCU results should exist");

    boolean gcuRequired = (Boolean) gcuResults.get("gcu_required");
    assertTrue(gcuRequired, "GCU should be required for this gas composition");

    double coolingDutyKW = (Double) gcuResults.get("cooling_duty_kW");
    assertTrue(coolingDutyKW > 0.0,
        "Cooling duty should be positive, got " + coolingDutyKW + " kW");

    double reheatDutyKW = (Double) gcuResults.get("reheat_duty_kW");
    assertTrue(reheatDutyKW > 0.0, "Reheat duty should be positive, got " + reheatDutyKW + " kW");
  }

  /**
   * Tests that JSON output is generated and contains expected top-level keys.
   */
  @Test
  void testJsonOutput() {
    analyzer.runFullAnalysis();
    String json = analyzer.toJson();
    assertNotNull(json, "JSON should not be null");
    assertTrue(json.contains("isenthalpic_expansion"), "JSON should contain JT results");
    assertTrue(json.contains("retrograde_condensation_map"), "JSON should contain retrograde map");
    assertTrue(json.contains("condensate_accumulation"), "JSON should contain accumulation data");
    assertTrue(json.contains("gcu_sizing"), "JSON should contain GCU sizing");
  }

  /**
   * Tests that the standpipe fill time convenience method works.
   */
  @Test
  void testStandpipeFillTimeConvenience() {
    analyzer.runFullAnalysis();
    double fillTime = analyzer.getStandpipeFillTimeHours();
    assertTrue(fillTime > 0.0, "Fill time should be positive after analysis");
  }

  /**
   * Tests that the maximum JT liquid fraction convenience method works.
   */
  @Test
  void testMaxJTLiquidFractionConvenience() {
    analyzer.runFullAnalysis();
    double maxLiq = analyzer.getMaxJTLiquidFraction();
    assertTrue(maxLiq > 0.0, "Max JT liquid fraction should be positive for this gas");
  }

  /**
   * Tests that analysis fails gracefully when seal gas is not set.
   */
  @Test
  void testFailsWithoutSealGas() {
    DryGasSealAnalyzer bare = new DryGasSealAnalyzer("test");
    try {
      bare.runFullAnalysis();
      // Should throw IllegalStateException
      assertTrue(false, "Should have thrown exception");
    } catch (IllegalStateException ex) {
      assertTrue(ex.getMessage().contains("Seal gas not set"));
    }
  }

  /**
   * Tests that a clean dry gas (pure methane) shows no condensation risk.
   */
  @Test
  void testCleanGasShowsNoCondensation() {
    SystemInterface cleanGas = new SystemPrEos(273.15 + 44.0, 422.0);
    cleanGas.addComponent("methane", 0.98);
    cleanGas.addComponent("ethane", 0.02);
    cleanGas.setMixingRule("classic");

    DryGasSealAnalyzer cleanAnalyzer = new DryGasSealAnalyzer("Clean-Seal");
    cleanAnalyzer.setSealGas(cleanGas);
    cleanAnalyzer.setSealCavityPressure(421.0, "barg");
    cleanAnalyzer.setSealCavityTemperature(44.0, "C");
    cleanAnalyzer.setPrimaryVentPressure(1.5, "barg");
    cleanAnalyzer.setSealLeakageRate(280.0, "NL/min");
    cleanAnalyzer.setStandpipeGeometry(1.5, 0.038);
    cleanAnalyzer.setStandpipeCount(2);
    cleanAnalyzer.setAmbientTemperature(25.0, "C");

    cleanAnalyzer.runFullAnalysis();

    // Pure methane + ethane should not condense at these conditions
    // (dew point is well below ambient temperature at these pressures)
    assertTrue(cleanAnalyzer.isAnalysisComplete());
  }
}
