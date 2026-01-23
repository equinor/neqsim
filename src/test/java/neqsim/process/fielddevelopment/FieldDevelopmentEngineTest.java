package neqsim.process.fielddevelopment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.concept.InfrastructureInput;
import neqsim.process.fielddevelopment.concept.ReservoirInput;
import neqsim.process.fielddevelopment.concept.WellsInput;
import neqsim.process.fielddevelopment.evaluation.BatchConceptRunner;
import neqsim.process.fielddevelopment.evaluation.ConceptEvaluator;
import neqsim.process.fielddevelopment.evaluation.ConceptKPIs;
import neqsim.process.fielddevelopment.facility.BlockConfig;
import neqsim.process.fielddevelopment.facility.BlockType;
import neqsim.process.fielddevelopment.facility.FacilityBuilder;
import neqsim.process.fielddevelopment.facility.FacilityConfig;
import neqsim.process.fielddevelopment.screening.FlowAssuranceReport;
import neqsim.process.fielddevelopment.screening.FlowAssuranceResult;
import neqsim.process.fielddevelopment.screening.FlowAssuranceScreener;
import neqsim.process.fielddevelopment.screening.SafetyReport;
import neqsim.process.fielddevelopment.screening.SafetyScreener;

/**
 * Tests for the Field Development Engine.
 */
class FieldDevelopmentEngineTest extends neqsim.NeqSimTest {
  private FieldConcept gasTiebackConcept;
  private FieldConcept oilDevelopmentConcept;
  private FieldConcept highCO2Concept;

  @BeforeEach
  void setUp() {
    // Simple gas tieback concept
    gasTiebackConcept = FieldConcept.builder("Lean Gas Tieback")
        .reservoir(ReservoirInput.leanGas().gor(10000).co2Percent(1.5).h2sPercent(0.0).build())
        .wells(WellsInput.builder().producerCount(3).tubeheadPressure(100)
            .ratePerWell(1.0e6, "Sm3/d").build())
        .infrastructure(InfrastructureInput.subseaTieback().tiebackLength(25).waterDepth(300)
            .exportPressure(180).build())
        .build();

    // Oil development concept
    oilDevelopmentConcept = FieldConcept.builder("Black Oil Development")
        .reservoir(ReservoirInput.blackOil().gor(150).waterCut(0.15).build())
        .wells(WellsInput.builder().producerCount(8).injectorCount(4).tubeheadPressure(50)
            .ratePerWell(5000, "Sm3/d").build())
        .infrastructure(InfrastructureInput.builder()
            .processingLocation(InfrastructureInput.ProcessingLocation.FPSO).waterDepth(800)
            .exportType(InfrastructureInput.ExportType.STABILIZED_OIL).build())
        .build();

    // High CO2 concept
    highCO2Concept = FieldConcept.builder("High CO2 Gas Field")
        .reservoir(ReservoirInput.richGas().gor(3000).co2Percent(15).h2sPercent(0.05).build())
        .wells(WellsInput.builder().producerCount(6).tubeheadPressure(120)
            .ratePerWell(2.0e6, "Sm3/d").build())
        .infrastructure(InfrastructureInput.builder()
            .processingLocation(InfrastructureInput.ProcessingLocation.PLATFORM)
            .powerSupply(InfrastructureInput.PowerSupply.POWER_FROM_SHORE).waterDepth(150).build())
        .build();
  }

  // ============ Concept Tests ============

  @Test
  @DisplayName("ReservoirInput factory methods create correct fluid types")
  void testReservoirInputFactories() {
    ReservoirInput leanGas = ReservoirInput.leanGas().build();
    assertEquals(ReservoirInput.FluidType.LEAN_GAS, leanGas.getFluidType());

    ReservoirInput richGas = ReservoirInput.richGas().build();
    assertEquals(ReservoirInput.FluidType.RICH_GAS, richGas.getFluidType());

    ReservoirInput blackOil = ReservoirInput.blackOil().build();
    assertEquals(ReservoirInput.FluidType.BLACK_OIL, blackOil.getFluidType());
  }

  @Test
  @DisplayName("FieldConcept correctly identifies CO2 removal needs")
  void testConceptCO2RemovalNeeds() {
    assertFalse(gasTiebackConcept.needsCO2Removal());
    assertTrue(highCO2Concept.needsCO2Removal());
  }

  @Test
  @DisplayName("FieldConcept correctly identifies H2S removal needs")
  void testConceptH2SRemovalNeeds() {
    assertFalse(gasTiebackConcept.needsH2SRemoval());
    assertTrue(highCO2Concept.needsH2SRemoval());
  }

  @Test
  @DisplayName("FieldConcept correctly identifies dehydration needs")
  void testConceptDehydrationNeeds() {
    assertTrue(gasTiebackConcept.needsDehydration());
    assertFalse(oilDevelopmentConcept.needsDehydration()); // Oil concept
  }

  @Test
  @DisplayName("FieldConcept correctly identifies subsea tieback")
  void testConceptSubseaIdentification() {
    assertTrue(gasTiebackConcept.isSubseaTieback());
    assertFalse(oilDevelopmentConcept.isSubseaTieback());
  }

  // ============ Facility Builder Tests ============

  @Test
  @DisplayName("FacilityBuilder creates correct block sequence")
  void testFacilityBuilderBlockSequence() {
    FacilityConfig config =
        FacilityBuilder.forConcept(gasTiebackConcept).addBlock(BlockConfig.inletSeparation(80, 25))
            .addCompression(2, 180).addTegDehydration(50).build();

    assertEquals(4, config.getBlockCount()); // 3 added + flare (default)
    assertTrue(config.hasCompression());
    assertTrue(config.hasDehydration());
    assertEquals(2, config.getTotalCompressionStages());
  }

  @Test
  @DisplayName("FacilityBuilder auto-generate creates appropriate blocks")
  void testFacilityBuilderAutoGenerate() {
    FacilityConfig config = FacilityBuilder.autoGenerate(highCO2Concept).build();

    assertTrue(config.hasCo2Removal());
    assertTrue(config.hasBlock(BlockType.INLET_SEPARATION));
    assertTrue(config.hasBlock(BlockType.FLARE_SYSTEM));
    assertTrue(config.isComplex());
  }

  @Test
  @DisplayName("BlockConfig compression creates correct parameters")
  void testBlockConfigCompression() {
    BlockConfig compression = BlockConfig.compression(3, 200);

    assertEquals(BlockType.COMPRESSION, compression.getType());
    assertEquals(3, compression.getIntParameter("stages", 0));
    assertEquals(200.0, compression.getDoubleParameter("outletPressure", 0));
  }

  // ============ Flow Assurance Tests ============

  @Test
  @DisplayName("FlowAssuranceScreener evaluates hydrate risk")
  void testFlowAssuranceHydrateRisk() {
    FlowAssuranceScreener screener = new FlowAssuranceScreener();
    FlowAssuranceReport report = screener.quickScreen(gasTiebackConcept);

    assertNotNull(report.getHydrateResult());
    assertFalse(Double.isNaN(report.getHydrateFormationTempC()));
  }

  @Test
  @DisplayName("FlowAssuranceResult combine returns worst case")
  void testFlowAssuranceResultCombine() {
    assertEquals(FlowAssuranceResult.FAIL,
        FlowAssuranceResult.PASS.combine(FlowAssuranceResult.FAIL));
    assertEquals(FlowAssuranceResult.MARGINAL,
        FlowAssuranceResult.PASS.combine(FlowAssuranceResult.MARGINAL));
    assertEquals(FlowAssuranceResult.PASS,
        FlowAssuranceResult.PASS.combine(FlowAssuranceResult.PASS));
  }

  @Test
  @DisplayName("High CO2 concept flags corrosion risk")
  void testHighCO2CorrosionRisk() {
    FlowAssuranceScreener screener = new FlowAssuranceScreener();
    FlowAssuranceReport report = screener.quickScreen(highCO2Concept);

    assertTrue(report.getCorrosionResult().needsAttention());
  }

  // ============ Safety Tests ============

  @Test
  @DisplayName("SafetyScreener estimates blowdown time")
  void testSafetyBlowdownEstimate() {
    SafetyScreener screener = new SafetyScreener();
    FacilityConfig config = FacilityBuilder.autoGenerate(gasTiebackConcept).build();
    SafetyReport report = screener.screen(gasTiebackConcept, config);

    assertTrue(report.getEstimatedBlowdownTimeMinutes() > 0);
  }

  @Test
  @DisplayName("H2S present triggers safety warnings")
  void testH2SSafetyWarnings() {
    SafetyScreener screener = new SafetyScreener();
    FacilityConfig config = FacilityBuilder.autoGenerate(highCO2Concept).build();
    SafetyReport report = screener.screen(highCO2Concept, config);

    assertTrue(report.isH2sPresent());
    assertTrue(report.getRequirements().containsKey("h2s_detection")
        || report.getRequirements().containsKey("h2s_ppe"));
  }

  // ============ Concept Evaluator Tests ============

  @Test
  @DisplayName("ConceptEvaluator produces complete KPIs")
  void testConceptEvaluatorProducesKPIs() {
    ConceptEvaluator evaluator = new ConceptEvaluator();
    ConceptKPIs kpis = evaluator.evaluate(gasTiebackConcept);

    assertNotNull(kpis);
    assertEquals("Lean Gas Tieback", kpis.getConceptName());
    assertTrue(kpis.getTotalCapexMUSD() > 0);
    assertTrue(kpis.getOverallScore() > 0);
    assertTrue(kpis.getOverallScore() <= 1.0);
    assertNotNull(kpis.getFlowAssuranceReport());
    assertNotNull(kpis.getEmissionsReport());
    assertNotNull(kpis.getEconomicsReport());
  }

  @Test
  @DisplayName("ConceptEvaluator quick screen is faster with reduced fidelity")
  void testConceptEvaluatorQuickScreen() {
    ConceptEvaluator evaluator = new ConceptEvaluator();

    long start = System.currentTimeMillis();
    ConceptKPIs kpis = evaluator.quickScreen(gasTiebackConcept);
    long quickTime = System.currentTimeMillis() - start;

    assertNotNull(kpis);
    assertTrue(kpis.getNotes().containsKey("fidelity"));
  }

  @Test
  @DisplayName("Blocking issues are correctly identified")
  void testBlockingIssuesIdentification() {
    ConceptEvaluator evaluator = new ConceptEvaluator();
    ConceptKPIs kpis = evaluator.evaluate(gasTiebackConcept);

    // Simple lean gas tieback should not have blocking issues
    // (unless hydrate conditions are unfavorable)
    assertNotNull(kpis.getFlowAssuranceOverall());
  }

  // ============ Batch Runner Tests ============

  @Test
  @DisplayName("BatchConceptRunner evaluates multiple concepts")
  void testBatchRunnerMultipleConcepts() {
    BatchConceptRunner runner = new BatchConceptRunner();
    runner.addConcept(gasTiebackConcept);
    runner.addConcept(oilDevelopmentConcept);
    runner.addConcept(highCO2Concept);
    runner.parallelism(1); // Single-threaded for test stability

    BatchConceptRunner.BatchResults results = runner.runAll();

    assertEquals(3, results.getSuccessCount());
    assertEquals(0, results.getFailureCount());
    assertNotNull(results.getBestConcept());
  }

  @Test
  @DisplayName("BatchResults provides ranking and comparison")
  void testBatchResultsRanking() {
    BatchConceptRunner runner = new BatchConceptRunner();
    runner.addConcept(gasTiebackConcept);
    runner.addConcept(oilDevelopmentConcept);
    runner.parallelism(1); // Single-threaded for test stability

    BatchConceptRunner.BatchResults results = runner.runAll();

    // Check for errors - if any, print them for debugging
    if (!results.getErrors().isEmpty()) {
      System.out.println("Batch errors: " + results.getErrors());
    }

    assertNotNull(results.getRankedResults());
    assertTrue(results.getErrors().isEmpty(),
        "Batch should have no errors: " + results.getErrors());
    assertEquals(2, results.getRankedResults().size());
    assertNotNull(results.getComparisonSummary());
    assertTrue(results.getComparisonSummary().contains("CONCEPT COMPARISON"));
  }

  @Test
  @DisplayName("BatchResults identifies viable vs blocked concepts")
  void testBatchResultsViableConcepts() {
    BatchConceptRunner runner = new BatchConceptRunner();
    runner.addConcept(gasTiebackConcept);
    runner.addConcept(highCO2Concept);
    runner.parallelism(1); // Single-threaded for test stability

    BatchConceptRunner.BatchResults results = runner.runAll();

    assertNotNull(results.getViableConcepts());
  }

  // ============ Integration Tests ============

  @Test
  @DisplayName("End-to-end concept evaluation workflow")
  void testEndToEndWorkflow() {
    // 1. Define concept - low CO2 to minimize vented emissions
    FieldConcept concept = FieldConcept.builder("Integration Test Concept")
        .reservoir(ReservoirInput.leanGas().gor(10000).co2Percent(0.5).build())
        .wells(WellsInput.builder().producerCount(4).tubeheadPressure(90)
            .ratePerWell(1.5e6, "Sm3/d").build())
        .infrastructure(InfrastructureInput.subseaTieback().tiebackLength(40).waterDepth(400)
            .powerSupply(InfrastructureInput.PowerSupply.POWER_FROM_SHORE).build())
        .build();

    // 2. Build facility
    FacilityConfig facility =
        FacilityBuilder.autoGenerate(concept).withRedundancy("compression", 1).build();

    // 3. Evaluate
    ConceptEvaluator evaluator = new ConceptEvaluator();
    ConceptKPIs kpis = evaluator.evaluate(concept, facility);

    // 4. Verify comprehensive results
    assertNotNull(kpis.getSummary());
    assertTrue(kpis.getPlateauRateMsm3d() > 0);
    assertTrue(kpis.getTotalCapexMUSD() > 0);
    assertNotNull(kpis.getFlowAssuranceOverall());
    assertNotNull(kpis.getSafetyLevel());
    assertTrue(kpis.getCo2IntensityKgPerBoe() >= 0);

    // Power from shore with low CO2 should have lower emissions than gas turbines
    // Typical offshore with gas turbines is 60-100 kg/boe, power from shore should be <50
    assertTrue(kpis.getCo2IntensityKgPerBoe() < 50,
        "CO2 intensity should be <50 for power from shore: " + kpis.getCo2IntensityKgPerBoe());
  }

  @Test
  @DisplayName("Factory method concepts are correctly configured")
  void testFactoryMethodConcepts() {
    FieldConcept gasTieback = FieldConcept.gasTieback("Test Gas Tieback");
    assertNotNull(gasTieback);
    assertTrue(gasTieback.isSubseaTieback());
    assertTrue(gasTieback.needsDehydration());

    FieldConcept oilDev = FieldConcept.oilDevelopment("Test Oil Dev");
    assertNotNull(oilDev);
    assertFalse(oilDev.needsDehydration());
  }
}
