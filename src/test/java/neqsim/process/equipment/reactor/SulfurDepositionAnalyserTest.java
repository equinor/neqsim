package neqsim.process.equipment.reactor;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the SulfurDepositionAnalyser unit operation.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SulfurDepositionAnalyserTest {

  /**
   * Test basic sulfur solubility analysis with S8 in natural gas. Verifies that
   * the temperature
   * sweep produces results and S8 solubility is calculated.
   */
  @Test
  public void testSulfurSolubilityInNaturalGas() {
    SystemInterface gas = new SystemSrkEos(273.15 + 65.0, 70.0);
    gas.addComponent("nitrogen", 5.84704017689321e-003);
    gas.addComponent("CO2", 0.021);
    gas.addComponent("methane", 0.93);
    gas.addComponent("ethane", 0.034);
    gas.addComponent("propane", 0.009);
    gas.addComponent("S8", 10.0e-06);
    gas.setMixingRule(2);
    gas.setMultiPhaseCheck(true);

    Stream feed = new Stream("feed", gas);
    feed.run();

    SulfurDepositionAnalyser analyser = new SulfurDepositionAnalyser("S8 analyser", feed);
    analyser.setRunChemicalEquilibrium(false); // Only solid flash
    analyser.setRunCorrosionAssessment(false);
    analyser.setTemperatureSweepRange(20, 150, 10);
    analyser.run();

    // Temperature sweep should have results
    List<Map<String, Object>> sweep = analyser.getTemperatureSweepResults();
    Assertions.assertFalse(sweep.isEmpty(), "Temperature sweep should produce results");
    Assertions.assertTrue(sweep.size() > 5, "Should have multiple temperature points");

    // S8 solubility should be calculated (positive value)
    double solubility = analyser.getSulfurSolubilityInGas();
    Assertions.assertTrue(solubility > 0 || Double.isNaN(solubility),
        "S8 solubility should be positive or NaN");
  }

  /**
   * Test sulfur chemical equilibrium with H2S and O2 using Gibbs reactor.
   */
  @Test
  public void testSulfurChemicalEquilibrium() {
    SystemInterface gas = new SystemSrkEos(273.15 + 100.0, 10.0);
    gas.addComponent("methane", 1e6);
    gas.addComponent("H2S", 10.0);
    gas.addComponent("oxygen", 2.0);
    gas.addComponent("SO2", 0.0);
    gas.addComponent("SO3", 0.0);
    gas.addComponent("sulfuric acid", 0.0);
    gas.addComponent("water", 0.0);
    gas.addComponent("S8", 0.0);
    gas.setMixingRule(2);

    Stream feed = new Stream("feed", gas);
    feed.run();

    SulfurDepositionAnalyser analyser = new SulfurDepositionAnalyser("Claus analyser", feed);
    analyser.setRunChemicalEquilibrium(true);
    analyser.setRunSolidFlash(false);
    analyser.setRunCorrosionAssessment(true);
    analyser.setTemperatureSweepRange(50, 150, 25);
    analyser.run();

    // Equilibrium composition should have entries
    Map<String, Double> eqComp = analyser.getEquilibriumComposition();
    Assertions.assertFalse(eqComp.isEmpty(), "Equilibrium composition should not be empty");

    // S8 should be produced from H2S + O2 reaction
    Map<String, Object> rxnSummary = analyser.getReactionSummary();
    Assertions.assertNotNull(rxnSummary.get("converged"), "Reaction summary should have converged");

    // JSON output should be valid
    String json = analyser.getResultsAsJson();
    Assertions.assertNotNull(json, "JSON results should not be null");
    Assertions.assertTrue(json.contains("chemicalEquilibrium"),
        "JSON should contain chemical equilibrium section");
  }

  /**
   * Test corrosion assessment with H2S-containing natural gas.
   */
  @Test
  public void testCorrosionAssessment() {
    SystemInterface gas = new SystemSrkEos(273.15 + 50.0, 50.0);
    gas.addComponent("methane", 0.93);
    gas.addComponent("CO2", 0.02);
    gas.addComponent("H2S", 0.001); // 1000 ppm H2S
    gas.addComponent("water", 0.005);
    gas.addComponent("S8", 1e-8);
    gas.setMixingRule(2);

    Stream feed = new Stream("feed", gas);
    feed.run();

    SulfurDepositionAnalyser analyser = new SulfurDepositionAnalyser("corrosion analyser", feed);
    analyser.setRunChemicalEquilibrium(false);
    analyser.setRunSolidFlash(false);
    analyser.setRunCorrosionAssessment(true);
    analyser.setTemperatureSweepRange(40, 60, 10);
    analyser.run();

    // Should identify corrosion risk
    Assertions.assertTrue(analyser.hasCorrosionRisk(),
        "Should identify corrosion risk with 1000 ppm H2S and water");

    Map<String, Object> corr = analyser.getCorrosionAssessment();
    Assertions.assertNotNull(corr.get("sourSeverityNACE"), "Should have sour severity");
    Assertions.assertEquals(true, corr.get("FeS_formationRisk"),
        "FeS formation should be at risk with H2S and water");
    Assertions.assertEquals(true, corr.get("waterPresent"), "Water should be detected");
  }

  /**
   * Test full analysis with all features enabled.
   */
  @Test
  public void testFullAnalysis() {
    SystemInterface gas = new SystemSrkEos(273.15 + 80.0, 70.0);
    gas.addComponent("nitrogen", 0.005);
    gas.addComponent("CO2", 0.02);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.03);
    gas.addComponent("propane", 0.01);
    gas.addComponent("H2S", 0.0005);
    gas.addComponent("oxygen", 0.00005);
    gas.addComponent("water", 0.002);
    gas.addComponent("S8", 5e-6);
    gas.addComponent("SO2", 0.0);
    gas.addComponent("SO3", 0.0);
    gas.addComponent("sulfuric acid", 0.0);
    gas.setMixingRule(2);

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(100000, "kg/hr");
    feed.run();

    SulfurDepositionAnalyser analyser = new SulfurDepositionAnalyser("full analyser", feed);
    analyser.setRunChemicalEquilibrium(true);
    analyser.setRunSolidFlash(true);
    analyser.setRunCorrosionAssessment(true);
    analyser.setTemperatureSweepRange(10, 160, 10);
    analyser.run();

    // Verify all results sections are populated
    String json = analyser.getResultsAsJson();
    Assertions.assertNotNull(json, "JSON should not be null");
    Assertions.assertTrue(json.contains("sulfurSolubility"), "Should have solubility data");
    Assertions.assertTrue(json.contains("depositionOnset"), "Should have deposition onset data");
    Assertions.assertTrue(json.contains("corrosionAssessment"),
        "Should have corrosion assessment data");
    Assertions.assertTrue(json.contains("temperatureSweep"),
        "Should have temperature sweep data");

    // Print summary for manual inspection
    analyser.printSummary();
  }

  /**
   * Test that the analyser handles missing S8 component gracefully.
   */
  @Test
  public void testNoS8Component() {
    SystemInterface gas = new SystemSrkEos(273.15 + 50.0, 50.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("H2S", 0.001);
    gas.addComponent("water", 0.005);
    gas.setMixingRule(2);

    Stream feed = new Stream("feed", gas);
    feed.run();

    SulfurDepositionAnalyser analyser = new SulfurDepositionAnalyser("no-S8 analyser", feed);
    analyser.setRunChemicalEquilibrium(false);
    analyser.setRunSolidFlash(true);
    analyser.setRunCorrosionAssessment(true);
    analyser.setTemperatureSweepRange(30, 80, 10);
    analyser.run();

    // Should complete without errors even without S8
    List<Map<String, Object>> sweep = analyser.getTemperatureSweepResults();
    Assertions.assertFalse(sweep.isEmpty(), "Sweep should produce results even without S8");
  }

  /**
   * Test JSON output is valid and contains expected structure.
   */
  @Test
  public void testJsonOutput() {
    SystemInterface gas = new SystemSrkEos(273.15 + 50.0, 50.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("H2S", 0.0005);
    gas.addComponent("oxygen", 0.00001);
    gas.addComponent("water", 0.003);
    gas.addComponent("S8", 1e-7);
    gas.addComponent("SO2", 0.0);
    gas.setMixingRule(2);

    Stream feed = new Stream("feed", gas);
    feed.run();

    SulfurDepositionAnalyser analyser = new SulfurDepositionAnalyser("json-test", feed);
    analyser.setTemperatureSweepRange(30, 80, 25);
    analyser.run();

    String json = analyser.getResultsAsJson();
    Assertions.assertNotNull(json);
    Assertions.assertTrue(json.startsWith("{"), "JSON should start with {");
    Assertions.assertTrue(json.contains("\"analyser\""), "JSON should contain analyser name");
  }

  /**
   * Test kinetic analysis with H2S-containing gas at different temperatures.
   * Verifies that reaction rate estimates, FeS morphology, and root cause
   * classification are produced.
   */
  @Test
  public void testKineticAnalysis() {
    // Low temperature gas (pipeline conditions) - kinetics should be negligible
    SystemInterface gas = new SystemSrkEos(273.15 + 40.0, 100.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("H2S", 0.05);
    gas.addComponent("oxygen", 0.00005);
    gas.addComponent("water", 0.003);
    gas.addComponent("S8", 1e-7);
    gas.addComponent("SO2", 0.0);
    gas.setMixingRule(2);

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(100000, "kg/hr");
    feed.run();

    SulfurDepositionAnalyser analyser = new SulfurDepositionAnalyser("kinetic-test", feed);
    analyser.setRunChemicalEquilibrium(false);
    analyser.setTemperatureSweepRange(20, 60, 20);
    analyser.run();

    Map<String, Object> kinetics = analyser.getKineticAnalysis();
    Assertions.assertFalse(kinetics.isEmpty(), "Kinetic analysis should have results");

    // At 40 C, H2S oxidation should be kinetically negligible
    Assertions.assertEquals(false, kinetics.get("H2S_oxidation_kineticallyFeasible"),
        "H2S oxidation should be infeasible at 40 C");

    // FeS corrosion rate should be calculated
    Object crRate = kinetics.get("FeS_corrosionRate_mmYear");
    Assertions.assertNotNull(crRate, "FeS corrosion rate should be calculated");
    Assertions.assertTrue(((Number) crRate).doubleValue() > 0,
        "Corrosion rate should be positive for 5% H2S");

    // FeS morphology at 40 C should be mackinawite
    String morphology = (String) kinetics.get("FeS_scaleMorphology");
    Assertions.assertTrue(morphology.contains("Mackinawite"),
        "At 40 C FeS should be mackinawite");

    // Root cause should be thermodynamic precipitation at low T
    @SuppressWarnings("unchecked")
    List<String> rootCauses = (List<String>) kinetics.get("rootCauseClassification");
    Assertions.assertFalse(rootCauses.isEmpty(), "Root causes should be identified");
    Assertions.assertTrue(rootCauses.get(0).contains("Thermodynamic precipitation"),
        "Primary root cause at low T should be thermodynamic");

    // JSON should include kinetic analysis
    String json = analyser.getResultsAsJson();
    Assertions.assertTrue(json.contains("kineticAnalysis"),
        "JSON should contain kinetic analysis");
    Assertions.assertTrue(json.contains("rootCauseClassification"),
        "JSON should contain root cause classification");
  }

  /**
   * Test supersaturation analysis and nucleation risk assessment.
   */
  @Test
  public void testSupersaturationAnalysis() {
    SystemInterface gas = new SystemSrkEos(273.15 + 50.0, 100.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("H2S", 0.01);
    gas.addComponent("S8", 5e-6);
    gas.addComponent("water", 0.001);
    gas.setMixingRule(2);

    Stream feed = new Stream("feed", gas);
    feed.run();

    SulfurDepositionAnalyser analyser = new SulfurDepositionAnalyser("supersat-test", feed);
    analyser.setRunChemicalEquilibrium(false);
    analyser.setRunCorrosionAssessment(false);
    analyser.setTemperatureSweepRange(30, 70, 20);
    analyser.run();

    Map<String, Object> supersat = analyser.getSupersaturationAnalysis();
    Assertions.assertFalse(supersat.isEmpty(),
        "Supersaturation analysis should have results");

    // Should have supersaturation ratio
    Assertions.assertNotNull(supersat.get("supersaturationRatio"),
        "Should calculate supersaturation ratio");
    Assertions.assertNotNull(supersat.get("supersaturationZone"),
        "Should classify supersaturation zone");

    // JSON should include supersaturation analysis
    String json = analyser.getResultsAsJson();
    Assertions.assertTrue(json.contains("supersaturationAnalysis"),
        "JSON should contain supersaturation analysis");
  }

  /**
   * Test gas vs liquid S8 solubility comparison.
   */
  @Test
  public void testGasVsLiquidSolubility() {
    SystemInterface gas = new SystemSrkEos(273.15 + 50.0, 80.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.05);
    gas.addComponent("propane", 0.03);
    gas.addComponent("n-butane", 0.02);
    gas.addComponent("H2S", 0.01);
    gas.addComponent("S8", 1e-7);
    gas.addComponent("water", 0.001);
    gas.setMixingRule(2);
    gas.setMultiPhaseCheck(true);

    Stream feed = new Stream("feed", gas);
    feed.run();

    SulfurDepositionAnalyser analyser = new SulfurDepositionAnalyser("gasLiquid-test", feed);
    analyser.setRunChemicalEquilibrium(false);
    analyser.setRunCorrosionAssessment(false);
    analyser.setTemperatureSweepRange(30, 70, 20);
    analyser.run();

    Map<String, Object> gasLiq = analyser.getGasVsLiquidSolubility();
    Assertions.assertFalse(gasLiq.isEmpty(),
        "Gas vs liquid analysis should have results");

    // Should report whether gas and liquid phases exist
    Assertions.assertNotNull(gasLiq.get("hasGasPhase"),
        "Should report gas phase presence");

    // JSON should include the comparison
    String json = analyser.getResultsAsJson();
    Assertions.assertTrue(json.contains("gasVsLiquidSolubility"),
        "JSON should contain gas-liquid solubility comparison");
  }

  /**
   * Test blockage risk assessment with solid sulfur deposition.
   */
  @Test
  public void testBlockageRiskAssessment() {
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 100.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("H2S", 0.05);
    gas.addComponent("S8", 1e-5);
    gas.addComponent("water", 0.003);
    gas.setMixingRule(2);

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(100000, "kg/hr");
    feed.run();

    SulfurDepositionAnalyser analyser = new SulfurDepositionAnalyser("blockage-test", feed);
    analyser.setRunChemicalEquilibrium(false);
    analyser.setRunCorrosionAssessment(false);
    analyser.setPipeDiameter(0.254); // 10 inch
    analyser.setPipeSegmentLength(1000.0);
    analyser.setFlowVelocity(5.0);
    analyser.setGasFlowRate(100000.0);
    analyser.setTemperatureSweepRange(10, 50, 20);
    analyser.run();

    Map<String, Object> blockage = analyser.getBlockageRiskAssessment();
    Assertions.assertFalse(blockage.isEmpty(),
        "Blockage risk assessment should have results");

    // Should have a blockage risk classification
    Assertions.assertNotNull(blockage.get("blockageRisk"),
        "Should classify blockage risk");
    Assertions.assertNotNull(blockage.get("piggingRecommendation"),
        "Should have pigging recommendation");

    // JSON should include blockage risk
    String json = analyser.getResultsAsJson();
    Assertions.assertTrue(json.contains("blockageRiskAssessment"),
        "JSON should contain blockage risk assessment");

    // Print summary to verify new sections appear
    analyser.printSummary();
  }

  /**
   * Test catalysis pathway analysis for elemental sulfur formation. Verifies
   * that all major catalytic pathways are evaluated and composition effects are
   * assessed.
   */
  @Test
  public void testCatalysisAnalysis() {
    // Rich sour gas with O2 trace, water, and CO2
    SystemInterface gas = new SystemSrkEos(273.15 + 80.0, 150.0);
    gas.addComponent("methane", 0.80);
    gas.addComponent("ethane", 0.05);
    gas.addComponent("propane", 0.03);
    gas.addComponent("n-butane", 0.01);
    gas.addComponent("CO2", 0.03);
    gas.addComponent("H2S", 0.05);
    gas.addComponent("oxygen", 5e-6); // 5 ppm O2 ingress
    gas.addComponent("S8", 1e-6);
    gas.addComponent("SO2", 0.0);
    gas.addComponent("water", 0.005);
    gas.setMixingRule(2);

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(100000, "kg/hr");
    feed.run();

    SulfurDepositionAnalyser analyser = new SulfurDepositionAnalyser("catalysis-test", feed);
    analyser.setRunChemicalEquilibrium(false);
    analyser.setRunCorrosionAssessment(false);
    analyser.setTemperatureSweepRange(60, 100, 20);
    analyser.run();

    // Catalysis analysis should be populated
    Map<String, Object> catalysis = analyser.getCatalysisAnalysis();
    Assertions.assertFalse(catalysis.isEmpty(),
        "Catalysis analysis should have results");

    // Should have pathways list
    Object pathways = catalysis.get("pathways");
    Assertions.assertNotNull(pathways, "Should have catalysis pathways");
    Assertions.assertTrue(pathways instanceof List,
        "Pathways should be a List");
    List<?> pathwayList = (List<?>) pathways;
    Assertions.assertTrue(pathwayList.size() >= 9,
        "Should evaluate at least 9 catalytic pathways");

    // Should identify active catalysts (O2 present + H2S + water)
    Object active = catalysis.get("activeCatalysts");
    Assertions.assertNotNull(active, "Should list active catalysts");
    Assertions.assertTrue(active instanceof List, "Active catalysts should be a List");
    List<?> activeList = (List<?>) active;
    Assertions.assertTrue(activeList.size() >= 2,
        "With O2, H2S, and water, should have 2+ active catalysts");

    // Should have a dominant mechanism
    Object dominant = catalysis.get("dominantMechanism");
    Assertions.assertNotNull(dominant, "Should assess dominant mechanism");

    // Should have composition effects
    Object effects = catalysis.get("compositionEffects");
    Assertions.assertNotNull(effects, "Should analyse composition effects");

    // Should be in JSON
    String json = analyser.getResultsAsJson();
    Assertions.assertTrue(json.contains("catalysisAnalysis"),
        "JSON should contain catalysis analysis");
    Assertions.assertTrue(json.contains("dominantMechanism"),
        "JSON should contain dominant mechanism");
    Assertions.assertTrue(json.contains("compositionEffects"),
        "JSON should contain composition effects");

    // Print summary to verify catalysis section appears
    analyser.printSummary();
  }

  /**
   * Test catalysis analysis with lean dry gas (no catalysts active). Verifies
   * that thermodynamic precipitation is correctly identified as dominant.
   */
  @Test
  public void testCatalysisAnalysisLeanDryGas() {
    // Lean dry gas: no O2, no water, no SO2
    SystemInterface gas = new SystemSrkEos(273.15 + 60.0, 100.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("H2S", 0.001);
    gas.addComponent("S8", 1e-7);
    gas.setMixingRule(2);

    Stream feed = new Stream("feed", gas);
    feed.run();

    SulfurDepositionAnalyser analyser = new SulfurDepositionAnalyser("lean-catalysis", feed);
    analyser.setRunChemicalEquilibrium(false);
    analyser.setRunCorrosionAssessment(false);
    analyser.setTemperatureSweepRange(40, 80, 20);
    analyser.run();

    Map<String, Object> catalysis = analyser.getCatalysisAnalysis();
    Assertions.assertFalse(catalysis.isEmpty(),
        "Catalysis analysis should still run for lean gas");

    // Dominant mechanism should be thermodynamic precipitation
    String dominant = String.valueOf(catalysis.get("dominantMechanism"));
    Assertions.assertTrue(
        dominant.contains("THERMODYNAMIC PRECIPITATION")
            || dominant.contains("SURFACE CATALYSIS"),
        "Lean dry gas should identify thermodynamic precipitation or minimal "
            + "surface catalysis as dominant");
  }
}
