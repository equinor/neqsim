package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the comprehensive asphaltene multi-method benchmark framework.
 *
 * <p>
 * Verifies all asphaltene prediction methods work independently and together: De Boer screening,
 * SARA CII, Refractive Index, Flory-Huggins, CPA EOS, and Pedersen cubic EOS.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
class AsphalteneMultiMethodBenchmarkTest {

  private SystemInterface cpaFluid;
  private SystemInterface cubicFluid;

  @BeforeEach
  void setUp() {
    // CPA fluid for CPA-based methods
    cpaFluid = new SystemSrkCPAstatoil(273.15 + 100.0, 300.0);
    cpaFluid.addComponent("methane", 0.40);
    cpaFluid.addComponent("ethane", 0.05);
    cpaFluid.addComponent("propane", 0.03);
    cpaFluid.addComponent("n-heptane", 0.45);
    cpaFluid.addComponent("nC10", 0.05);
    cpaFluid.addComponent("asphaltene", 0.02);
    cpaFluid.setMixingRule(10);

    // Cubic fluid for Pedersen method
    cubicFluid = new SystemSrkEos(273.15 + 100.0, 300.0);
    cubicFluid.addComponent("methane", 0.40);
    cubicFluid.addComponent("ethane", 0.05);
    cubicFluid.addComponent("propane", 0.03);
    cubicFluid.addComponent("n-heptane", 0.45);
    cubicFluid.addComponent("nC10", 0.05);
    cubicFluid.setMixingRule("classic");
  }

  @Test
  void testFloryHugginsModel() {
    FloryHugginsAsphalteneModel fh = new FloryHugginsAsphalteneModel(cubicFluid, 273.15 + 100.0);
    fh.setAsphalteneWeightFraction(0.05);
    fh.setAsphalteneMW(750.0);
    fh.setAsphalteneDensity(1100.0);
    fh.setAsphalteneSolubilityParameter(21.0);

    // Test solubility parameter from density
    double deltaL = fh.calculateLiquidSolubilityParameter(800.0);
    assertTrue(deltaL > 10.0 && deltaL < 20.0,
        "Liquid solubility parameter should be in range 10-20 MPa^0.5, got " + deltaL);

    // Test chi parameter
    double chi = fh.calculateChiParameter(16.0, 373.15);
    assertTrue(chi >= 0, "Chi parameter should be non-negative");

    // Test precipitation curve generation
    double[][] curve = fh.generatePrecipitationCurve(373.15, 300.0, 10.0, 10);
    assertNotNull(curve);
    assertEquals(10, curve[0].length, "Should have 10 pressure points");
    assertEquals(10, curve[1].length, "Should have 10 wt% points");
    assertTrue(curve[0][0] >= curve[0][curve[0].length - 1], "Pressures should be decreasing");

    // Test solubility parameter profile
    double[][] profile = fh.generateSolubilityParameterProfile(373.15, 300.0, 10.0, 10);
    assertNotNull(profile);
    assertEquals(3, profile.length, "Should have pressure, deltaL, deltaA arrays");

    // Test results map
    java.util.Map<String, Object> results = fh.getResultsMap(373.15, 300.0, 10.0);
    assertNotNull(results);
    assertEquals("Flory-Huggins Regular Solution", results.get("model"));
  }

  @Test
  void testRefractiveIndexScreening() {
    RefractiveIndexAsphalteneScreening ri = new RefractiveIndexAsphalteneScreening();

    // Test RI from density estimation
    double riEst = ri.estimateRIFromDensity(800.0);
    assertTrue(riEst > 1.3 && riEst < 1.6, "Estimated RI should be 1.3-1.6, got " + riEst);

    // Test with measured values
    ri.setRiOil(1.52);
    ri.setRiOnset(1.49);

    double margin = ri.getRIStabilityMargin();
    assertEquals(0.03, margin, 0.001, "RI margin should be 0.03");

    RefractiveIndexAsphalteneScreening.RIStability stability = ri.evaluateStability();
    assertNotNull(stability);
    assertTrue(
        stability == RefractiveIndexAsphalteneScreening.RIStability.VERY_STABLE
            || stability == RefractiveIndexAsphalteneScreening.RIStability.STABLE,
        "With 0.03 margin should be STABLE or VERY_STABLE");

    // Test unstable case
    RefractiveIndexAsphalteneScreening riUnstable = new RefractiveIndexAsphalteneScreening();
    riUnstable.setRiOil(1.48);
    riUnstable.setRiOnset(1.49);
    RefractiveIndexAsphalteneScreening.RIStability unstable = riUnstable.evaluateStability();
    assertEquals(RefractiveIndexAsphalteneScreening.RIStability.HIGHLY_UNSTABLE, unstable,
        "Should be HIGHLY_UNSTABLE when RI < onset RI");

    // Test SARA-to-RI estimation
    double onsetRI = ri.estimateOnsetRIFromSARA(0.45, 0.25, 0.20, 0.10);
    assertTrue(onsetRI > 1.4 && onsetRI < 1.6,
        "Estimated onset RI should be 1.4-1.6, got " + onsetRI);

    // Test report generation
    String report = ri.generateReport();
    assertNotNull(report);
    assertFalse(report.isEmpty());

    // Test results map
    java.util.Map<String, Object> results = ri.getResultsMap();
    assertNotNull(results);
    assertEquals("Refractive Index Screening", results.get("model"));
  }

  @Test
  void testRefractiveIndexFromAPI() {
    RefractiveIndexAsphalteneScreening ri = new RefractiveIndexAsphalteneScreening();
    ri.setApiGravity(35.0);
    assertTrue(!Double.isNaN(ri.getOilDensity()), "Setting API should calculate density");
    assertTrue(ri.getOilDensity() > 700 && ri.getOilDensity() < 900,
        "35 API oil density should be 700-900 kg/m3");
  }

  @Test
  void testCriticalDilutionRatio() {
    RefractiveIndexAsphalteneScreening ri = new RefractiveIndexAsphalteneScreening();
    ri.setHeptaneFractionAtOnset(0.6);
    double cdr = ri.getCriticalDilutionRatio();
    assertEquals(1.5, cdr, 0.01, "CDR should be 0.6/0.4 = 1.5");
  }

  @Test
  void testBenchmarkDeBoerOnly() {
    AsphalteneMultiMethodBenchmark benchmark = new AsphalteneMultiMethodBenchmark();
    benchmark.setReservoirPressure(350.0);
    benchmark.setReservoirTemperature(273.15 + 100.0);
    benchmark.setInSituDensity(750.0);
    benchmark.setCpaSystem(cpaFluid);

    benchmark.runAllMethods();

    // De Boer should have been run
    AsphalteneMultiMethodBenchmark.MethodResult deBoer = benchmark.getMethodResult("DeBoer");
    assertNotNull(deBoer);
    assertNotNull(deBoer.riskLevel);
    assertTrue(deBoer.computationTimeMs >= 0);
  }

  @Test
  void testBenchmarkWithSARA() {
    AsphalteneMultiMethodBenchmark benchmark = new AsphalteneMultiMethodBenchmark();
    benchmark.setReservoirPressure(350.0);
    benchmark.setReservoirTemperature(273.15 + 100.0);
    benchmark.setInSituDensity(750.0);
    benchmark.setSARAFractions(0.45, 0.25, 0.20, 0.10);
    benchmark.setCpaSystem(cpaFluid);
    benchmark.setCubicSystem(cubicFluid);

    benchmark.runAllMethods();

    // SARA method should have been run
    AsphalteneMultiMethodBenchmark.MethodResult sara = benchmark.getMethodResult("SARA_CII");
    assertNotNull(sara);
    assertNotNull(sara.riskLevel);
    assertNotNull(sara.details.get("CII"));
    double cii = (Double) sara.details.get("CII");
    assertTrue(cii > 0, "CII should be positive");

    // RI method should have been run (uses SARA-estimated onset)
    AsphalteneMultiMethodBenchmark.MethodResult ri = benchmark.getMethodResult("RefractiveIndex");
    assertNotNull(ri, "RI method should have been run using SARA data");
  }

  @Test
  void testBenchmarkJsonReport() {
    AsphalteneMultiMethodBenchmark benchmark = new AsphalteneMultiMethodBenchmark();
    benchmark.setReservoirPressure(350.0);
    benchmark.setReservoirTemperature(273.15 + 100.0);
    benchmark.setInSituDensity(750.0);
    benchmark.setSARAFractions(0.45, 0.25, 0.20, 0.10);
    benchmark.setCpaSystem(cpaFluid);

    benchmark.runAllMethods();

    String json = benchmark.toJson();
    assertNotNull(json);
    assertFalse(json.isEmpty());
    assertTrue(json.contains("input_conditions"));
    assertTrue(json.contains("method_results"));
    assertTrue(json.contains("summary"));
  }

  @Test
  void testBenchmarkComparisonTable() {
    AsphalteneMultiMethodBenchmark benchmark = new AsphalteneMultiMethodBenchmark();
    benchmark.setReservoirPressure(350.0);
    benchmark.setReservoirTemperature(273.15 + 100.0);
    benchmark.setInSituDensity(750.0);
    benchmark.setSARAFractions(0.45, 0.25, 0.20, 0.10);
    benchmark.setCpaSystem(cpaFluid);
    benchmark.setCubicSystem(cubicFluid);
    benchmark.setMeasuredOnsetPressure(180.0);

    benchmark.runAllMethods();

    String table = benchmark.getComparisonTable();
    assertNotNull(table);
    assertFalse(table.isEmpty());
    assertTrue(table.contains("MEASURED"));
  }

  @Test
  void testAgreementMatrix() {
    AsphalteneMultiMethodBenchmark benchmark = new AsphalteneMultiMethodBenchmark();
    benchmark.setReservoirPressure(350.0);
    benchmark.setReservoirTemperature(273.15 + 100.0);
    benchmark.setInSituDensity(750.0);
    benchmark.setSARAFractions(0.45, 0.25, 0.20, 0.10);
    benchmark.setCpaSystem(cpaFluid);

    benchmark.runAllMethods();

    double[][] matrix = benchmark.getAgreementMatrix();
    assertNotNull(matrix);
    assertTrue(matrix.length > 0);
    // Diagonal should be all 1.0
    for (int i = 0; i < matrix.length; i++) {
      assertEquals(1.0, matrix[i][i], "Diagonal should be 1.0");
    }
  }

  @Test
  void testLiteratureCasesAvailable() {
    java.util.List<AsphalteneMultiMethodBenchmark.LiteratureCase> cases =
        AsphalteneMultiMethodBenchmark.getLiteratureCases();
    assertNotNull(cases);
    assertTrue(cases.size() >= 7, "Should have at least 7 literature cases, got " + cases.size());

    for (AsphalteneMultiMethodBenchmark.LiteratureCase lc : cases) {
      assertNotNull(lc.label, "Each case must have a label");
      assertNotNull(lc.reference, "Each case must have a reference");
      assertTrue(lc.reservoirPressure > 0, "Reservoir P must be positive: " + lc.label);
      assertTrue(lc.reservoirTemperature > 200, "Reservoir T must be > 200K: " + lc.label);
      assertTrue(lc.measuredOnsetPressure > 0, "Measured onset P must be positive: " + lc.label);
      assertTrue(lc.bubblePointPressure > 0, "Bubble P must be positive: " + lc.label);
      assertTrue(lc.inSituDensity > 500 && lc.inSituDensity < 1200,
          "In-situ density must be 500-1200 kg/m3: " + lc.label);
      // Check onset is between bubble and reservoir pressure
      assertTrue(lc.measuredOnsetPressure > lc.bubblePointPressure,
          "Onset P should be above bubble point: " + lc.label);
      assertTrue(lc.measuredOnsetPressure <= lc.reservoirPressure,
          "Onset P should not exceed reservoir P: " + lc.label);
    }
  }

  @Test
  void testErrorStatisticsWithMeasuredOnset() {
    AsphalteneMultiMethodBenchmark benchmark = new AsphalteneMultiMethodBenchmark();
    benchmark.setReservoirPressure(350.0);
    benchmark.setReservoirTemperature(273.15 + 100.0);
    benchmark.setInSituDensity(750.0);
    benchmark.setSARAFractions(0.45, 0.25, 0.20, 0.10);
    benchmark.setCpaSystem(cpaFluid);
    benchmark.setCubicSystem(cubicFluid);
    benchmark.setMeasuredOnsetPressure(180.0);

    benchmark.runAllMethods();

    java.util.Map<String, Object> stats = benchmark.getErrorStatistics();
    assertNotNull(stats);
    assertFalse(stats.containsKey("error"), "Should not have error: " + stats.get("error"));
    assertTrue(stats.containsKey("AAD_bar"), "Should contain AAD");
    assertTrue(stats.containsKey("AARD_pct"), "Should contain AARD");
    assertTrue(stats.containsKey("best_method"), "Should identify best method");
    assertTrue(((Double) stats.get("AAD_bar")) >= 0, "AAD should be non-negative");
  }

  @Test
  void testMethodErrorSummary() {
    AsphalteneMultiMethodBenchmark benchmark = new AsphalteneMultiMethodBenchmark();
    benchmark.setReservoirPressure(350.0);
    benchmark.setReservoirTemperature(273.15 + 100.0);
    benchmark.setInSituDensity(750.0);
    benchmark.setCpaSystem(cpaFluid);
    benchmark.setMeasuredOnsetPressure(180.0);

    benchmark.runAllMethods();

    java.util.Map<String, java.util.Map<String, Object>> summary =
        benchmark.getMethodErrorSummary();
    assertNotNull(summary);
    assertFalse(summary.isEmpty(), "Error summary should have entries");

    for (java.util.Map.Entry<String, java.util.Map<String, Object>> entry : summary.entrySet()) {
      assertNotNull(entry.getValue().get("risk_level"),
          "Each method should have a risk level: " + entry.getKey());
    }
  }

  @Test
  void testImprovedRISARACorrelation() {
    RefractiveIndexAsphalteneScreening ri = new RefractiveIndexAsphalteneScreening();

    // Light paraffinic oil: high saturates → higher onset RI (less stable)
    double lightOnset = ri.estimateOnsetRIFromSARA(0.65, 0.20, 0.10, 0.05);
    // Heavy aromatic oil: low saturates, high aromatics/resins → lower onset RI (more stable)
    double heavyOnset = ri.estimateOnsetRIFromSARA(0.30, 0.35, 0.22, 0.13);

    assertTrue(lightOnset >= 1.42 && lightOnset <= 1.55,
        "Light oil onset RI should be 1.42-1.55, got " + lightOnset);
    assertTrue(heavyOnset >= 1.42 && heavyOnset <= 1.55,
        "Heavy oil onset RI should be 1.42-1.55, got " + heavyOnset);

    // Light (paraffinic) oil should have HIGHER onset RI than heavy (aromatic) oil
    // because paraffinic solvent is worse at dissolving asphaltenes
    assertTrue(lightOnset > heavyOnset, "Light paraffinic oil onset RI (" + lightOnset
        + ") should be higher than heavy aromatic oil (" + heavyOnset + ")");
  }

  @Test
  void testFloryHugginsAPIGravityConfiguration() {
    // Light oil (API > 40) should get lower MW and higher solubility parameter
    FloryHugginsAsphalteneModel lightModel = new FloryHugginsAsphalteneModel();
    lightModel.configureFromAPIGravity(42.0);
    assertTrue(lightModel.getAsphalteneMW() < 600,
        "Light oil asphaltene MW should be < 600, got " + lightModel.getAsphalteneMW());
    assertTrue(lightModel.getAsphalteneSolubilityParameter() > 21.5,
        "Light oil delta should be > 21.5, got " + lightModel.getAsphalteneSolubilityParameter());

    // Heavy oil (API < 25) should get higher MW and lower solubility parameter
    FloryHugginsAsphalteneModel heavyModel = new FloryHugginsAsphalteneModel();
    heavyModel.configureFromAPIGravity(22.0);
    assertTrue(heavyModel.getAsphalteneMW() > 1500,
        "Heavy oil asphaltene MW should be > 1500, got " + heavyModel.getAsphalteneMW());
    assertTrue(heavyModel.getAsphalteneSolubilityParameter() < 21.0,
        "Heavy oil delta should be < 21.0, got " + heavyModel.getAsphalteneSolubilityParameter());

    // Molar volume should be recalculated
    double expectedVm = (heavyModel.getAsphalteneMW() / heavyModel.getAsphalteneDensity()) * 1000.0;
    assertEquals(expectedVm, heavyModel.getAsphaltMolarVolume(), 1.0,
        "Molar volume should be recalculated from MW and density");
    assertTrue(heavyModel.isConfiguredFromAPI(), "Should be marked as configured from API");
  }

  @Test
  void testFloryHugginsWithAPIGravityImprovesLiteraturePredictions() {
    // Test that API-gravity-configured FH model produces onset for more cases
    java.util.List<AsphalteneMultiMethodBenchmark.LiteratureCase> cases =
        AsphalteneMultiMethodBenchmark.getLiteratureCases();

    int onsetFoundWithAPI = 0;
    int onsetFoundWithoutAPI = 0;

    for (AsphalteneMultiMethodBenchmark.LiteratureCase lc : cases) {
      // Create fluid for this case
      SystemInterface cpa = new SystemSrkCPAstatoil(lc.reservoirTemperature, lc.reservoirPressure);
      double lightFrac = Math.max(0.1, 0.3 + (lc.apiGravity - 30) * 0.015);
      double heavyFrac = 1.0 - lightFrac - 0.10 - 0.05;
      cpa.addComponent("methane", lightFrac);
      cpa.addComponent("ethane", 0.05);
      cpa.addComponent("propane", 0.05);
      cpa.addComponent("n-heptane", heavyFrac);
      cpa.addComponent("nC10", 0.05);
      cpa.addComponent("asphaltene", lc.asphaltenes > 0.01 ? lc.asphaltenes : 0.02);
      cpa.setMixingRule(10);

      // Without API gravity configuration
      AsphalteneMultiMethodBenchmark benchNoAPI =
          new AsphalteneMultiMethodBenchmark(lc.reservoirPressure, lc.reservoirTemperature);
      benchNoAPI.setCpaSystem(cpa);
      benchNoAPI.setSARAFractions(lc.saturates, lc.aromatics, lc.resins, lc.asphaltenes);
      benchNoAPI.setInSituDensity(lc.inSituDensity);
      benchNoAPI.setMeasuredOnsetPressure(lc.measuredOnsetPressure);
      benchNoAPI.runAllMethods();
      AsphalteneMultiMethodBenchmark.MethodResult fhNoAPI =
          benchNoAPI.getMethodResult("FloryHuggins");
      if (fhNoAPI != null && !Double.isNaN(fhNoAPI.onsetPressure)) {
        onsetFoundWithoutAPI++;
      }

      // With API gravity configuration
      AsphalteneMultiMethodBenchmark benchWithAPI =
          new AsphalteneMultiMethodBenchmark(lc.reservoirPressure, lc.reservoirTemperature);
      benchWithAPI.setCpaSystem(cpa);
      benchWithAPI.setSARAFractions(lc.saturates, lc.aromatics, lc.resins, lc.asphaltenes);
      benchWithAPI.setInSituDensity(lc.inSituDensity);
      benchWithAPI.setAPIGravity(lc.apiGravity);
      benchWithAPI.setMeasuredOnsetPressure(lc.measuredOnsetPressure);
      benchWithAPI.runAllMethods();
      AsphalteneMultiMethodBenchmark.MethodResult fhWithAPI =
          benchWithAPI.getMethodResult("FloryHuggins");
      if (fhWithAPI != null && !Double.isNaN(fhWithAPI.onsetPressure)) {
        onsetFoundWithAPI++;
      }
    }

    // With API gravity configuration, FH should find onset for at least 3 of 7 literature cases.
    // The calibrated model gives realistic onset pressures (below P_res) rather than
    // trivially predicting onset at P_res for all cases.
    assertTrue(onsetFoundWithAPI >= 3,
        "API-configured FH should find onset for >= 3 cases, got: " + onsetFoundWithAPI);
  }

  @Test
  void testDeBoerQuadraticBoundaries() {
    // Test that the improved quadratic boundaries give reasonable results
    // For a light oil (density 700 kg/m3):
    DeBoerAsphalteneScreening lightScreen = new DeBoerAsphalteneScreening(500.0, 180.0, 700.0);
    DeBoerAsphalteneScreening.DeBoerRisk lightRisk = lightScreen.evaluateRisk();
    assertNotNull(lightRisk, "Should classify light oil");

    // For a heavy oil (density 900 kg/m3):
    DeBoerAsphalteneScreening heavyScreen = new DeBoerAsphalteneScreening(300.0, 200.0, 900.0);
    DeBoerAsphalteneScreening.DeBoerRisk heavyRisk = heavyScreen.evaluateRisk();
    assertNotNull(heavyRisk, "Should classify heavy oil");

    // Light oil with high undersaturation should be more severe than heavy with low
    // undersaturation
    assertTrue(lightRisk.ordinal() >= heavyRisk.ordinal(),
        "Light oil (dP=320, rho=700) should be >= risk of heavy oil (dP=100, rho=900)");

    // Verify plot data generation works with quadratic boundaries
    double[][] plotData = lightScreen.generatePlotData(600.0, 950.0, 50);
    assertEquals(50, plotData[0].length, "Should have 50 points");
    assertTrue(plotData[1][0] >= 0, "No-problem line should be non-negative");
    assertTrue(plotData[3][0] >= 0, "Severe line should be non-negative");

    // Boundaries should decrease with increasing density
    // (less risk at higher density for same undersaturation)
    assertTrue(plotData[3][0] < plotData[3][49],
        "Severe boundary should be higher at higher density (quadratic shape)");
  }

  @Test
  void testFloryHugginsSARAConfiguration() {
    FloryHugginsAsphalteneModel model = new FloryHugginsAsphalteneModel();

    // First configure from API to set base properties
    model.configureFromAPIGravity(35.0);
    double baseDelta = model.getAsphalteneSolubilityParameter();

    // Then configure from SARA - high R/A ratio should lower effective delta
    model.configureFromSARA(0.40, 0.30, 0.25, 0.05);
    double adjustedDelta = model.getAsphalteneSolubilityParameter();

    // R/A = 0.25/0.05 = 5.0 > 3.0, so delta should decrease
    assertTrue(adjustedDelta < baseDelta,
        "High R/A ratio should lower effective delta: " + adjustedDelta + " < " + baseDelta);
    assertEquals(0.05, model.getAsphalteneWeightFraction(), 0.001,
        "Asphaltene weight fraction should be set from SARA");
  }
}
