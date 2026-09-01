package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.engineering.calculation.EquipmentDesignCalculations;
import neqsim.process.engineering.piping.PipingNetworkDesignCalculation;
import neqsim.process.engineering.piping.PipingRulePack;
import neqsim.process.engineering.production.DexpiToolQualificationRunner;
import neqsim.process.engineering.production.EngineeringBenchmarkDataset;
import neqsim.process.engineering.production.EngineeringPilotProjectEvidence;
import neqsim.process.engineering.production.EngineeringPilotQualificationRunner;
import neqsim.process.engineering.production.EngineeringReleaseQualificationRunner;
import neqsim.process.engineering.production.EngineeringValidationBenchmark;
import neqsim.process.engineering.safety.ReliefSizingCalculation;
import org.junit.jupiter.api.Test;

/** Tests executable production-qualification workflows without fabricating external acceptance. */
class EngineeringProductionQualificationWorkflowTest {

  @Test
  void runsControlledIndependentBenchmarkDataset() {
    EngineeringBenchmarkDataset dataset = new EngineeringBenchmarkDataset("separator-reference", "A")
        .add(new EngineeringBenchmarkDataset.Case("SEP-CASE-1", "separator-method", "2.0",
            EngineeringValidationBenchmark.SourceClass.INDEPENDENT_CALCULATION, "CALC-SEP-001", "A",
            "Independent checker / CALC-SEP-001-A").expect("diameter", 2.0, "m", 0.02, 0.01));
    Map<String, Map<String, Double>> actual = new LinkedHashMap<String, Map<String, Double>>();
    Map<String, Double> outputs = new LinkedHashMap<String, Double>();
    outputs.put("diameter", Double.valueOf(2.01));
    actual.put("SEP-CASE-1", outputs);

    EngineeringBenchmarkDataset.RunResult result = dataset.run(actual);

    assertTrue(result.isComplete());
    assertTrue(result.getReport().isPassed());
    assertTrue(dataset.toJson().contains("CALC-SEP-001"));

    dataset.add(new EngineeringBenchmarkDataset.Case("SEP-CASE-2", "separator-method", "2.0",
        EngineeringValidationBenchmark.SourceClass.INDEPENDENT_CALCULATION, "CALC-SEP-002", "A",
        "Independent checker / CALC-SEP-002-A").expect("diameter", 3.0, "m", 0.02, 0.01));
    Map<String, Double> failingOutputs = new LinkedHashMap<String, Double>();
    failingOutputs.put("diameter", Double.valueOf(2.0));
    actual.put("SEP-CASE-2", failingOutputs);
    assertFalse(dataset.run(actual).getReport().isPassed());
  }

  @Test
  void detectsDexpiSemanticDifferencesAndAcceptsExactRoundTrip() {
    DexpiToolQualificationRunner.Snapshot reference = snapshot("80-VA-001");
    DexpiToolQualificationRunner.Snapshot changed = snapshot("80-VA-002");

    DexpiToolQualificationRunner.Result failed = DexpiToolQualificationRunner.compare("Named CAE", "2026.1", reference,
        changed, changed, "DEXPI-TEST-001", "Independent CAE checker");
    DexpiToolQualificationRunner.Result passed = DexpiToolQualificationRunner.compare("Named CAE", "2026.1", reference,
        reference, reference, "DEXPI-TEST-002", "Independent CAE checker");

    assertFalse(failed.isQualified());
    assertTrue(passed.isQualified());
  }

  @Test
  void materialPilotDiscrepancyBlocksAcceptance() {
    List<EngineeringPilotQualificationRunner.Comparison> comparisons = Arrays.asList(
        new EngineeringPilotQualificationRunner.Comparison("separator diameter", 2.0, 2.01, "m", 0.02, 0.01, true),
        new EngineeringPilotQualificationRunner.Comparison("driver power", 5000.0, 5600.0, "kW", 50.0, 0.05, true));

    EngineeringPilotQualificationRunner.Result result = EngineeringPilotQualificationRunner.run("PILOT-1",
        EngineeringPilotProjectEvidence.Scope.SEPARATION_AND_COMPRESSION, "REFERENCE-PACKAGE-A", comparisons,
        "Independent checker", "PILOT-ACCEPTANCE-A");

    assertFalse(result.getEvidence().isAccepted());
  }

  @Test
  void derivesReleaseEvidenceFromMeasurements() {
    EngineeringReleaseQualificationRunner.Input input = new EngineeringReleaseQualificationRunner.Input("2.0-rc1",
        "CI-RUN-001", "Release authority").fullCiPassed(true).requireJavaVersion("8").passedJavaVersion("8")
        .deterministicFingerprint("abc").deterministicFingerprint("abc").performanceSample(2.0).performanceSample(2.2)
        .maximumAcceptedSeconds(5.0).apiFingerprints("api-a", "api-a").serializationMigrationPassed(true)
        .openHighSeveritySecurityFindings(0);

    EngineeringReleaseQualificationRunner.Result result = EngineeringReleaseQualificationRunner.run(input);

    assertTrue(result.getEvidence().isPassed());

    EngineeringReleaseQualificationRunner.Input missingSecurityReview = new EngineeringReleaseQualificationRunner.Input(
        "2.0-rc1", "CI-RUN-002", "Release authority").fullCiPassed(true).requireJavaVersion("8").passedJavaVersion("8")
        .deterministicFingerprint("abc").deterministicFingerprint("abc").performanceSample(2.0)
        .maximumAcceptedSeconds(5.0).apiFingerprints("api-a", "api-a").serializationMigrationPassed(true);
    assertFalse(EngineeringReleaseQualificationRunner.run(missingSecurityReview).getEvidence().isPassed());
  }

  @Test
  void productionModeRejectsDefaultsAndUsesDetailedNetworkHydraulics() {
    EngineeringCalculationContext context = EngineeringCalculationContext.builder().designCaseId("maximum")
        .simulationFingerprint("simulation-a").addStandardReference("PROJECT-PIPING-BASIS-A")
        .addEvidenceReference("HYDRAULIC-CALC-A").attribute("productionQualification", "true").build();
    EquipmentDesignCalculations.Input incompleteSeparator = EquipmentDesignCalculations.Input
        .builder("V-1", "maximum", "DB-A").value("gasFlowM3s", 1.0).value("liquidFlowM3s", 0.01)
        .value("gasDensityKgM3", 20.0).value("liquidDensityKgM3", 800.0).build();
    assertEquals(EngineeringCalculationResult.Status.BLOCKED,
        new EquipmentDesignCalculations.Separator().calculate(incompleteSeparator, context).getStatus());

    List<PipingNetworkDesignCalculation.Candidate> candidates = Arrays.asList(
        new PipingNetworkDesignCalculation.Candidate("DN100", "SCH40", 0.102, 100.0),
        new PipingNetworkDesignCalculation.Candidate("DN200", "SCH40", 0.202, 100.0));
    PipingNetworkDesignCalculation.Segment segment = new PipingNetworkDesignCalculation.Segment("L-1", true, false,
        1000.0, 100.0, 10.0, 1.1, 0.0).simultaneousDemandGroup("export-header")
        .addCase(PipingNetworkDesignCalculation.Case.detailed("maximum", 0.05, 50.0, 20.0, 1.0e-5, 4.6e-5));
    Map<String, Double> demands = new LinkedHashMap<String, Double>();
    demands.put("export-header", Double.valueOf(0.01));
    PipingNetworkDesignCalculation.Input network = new PipingNetworkDesignCalculation.Input("network-a",
        PipingRulePack.builder("project-piping-a").standard("PROJECT PIPING BASIS").edition("A")
            .velocityLimits(30.0, 5.0, 0.0).maximumPressureGradientBarPerKm(5.0).build(),
        candidates, new ArrayList<PipingNetworkDesignCalculation.Segment>(Arrays.asList(segment)), demands);

    EngineeringCalculationResult<PipingNetworkDesignCalculation.Result> result = new PipingNetworkDesignCalculation()
        .calculate(network, context);

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertTrue(result.getValue().toMap().toString().contains("maximumReynoldsNumber"));
  }

  @Test
  void sizesGasReliefAndBlocksUnapprovedTwoPhaseMethod() {
    EngineeringCalculationContext context = EngineeringCalculationContext.builder().designCaseId("blocked-outlet")
        .addStandardReference("API-520-PROJECT-EDITION").addEvidenceReference("RELIEF-CALC-001")
        .attribute("productionQualification", "true").build();
    ReliefSizingCalculation.Input gas = ReliefSizingCalculation.Input
        .builder("blocked-outlet", ReliefSizingCalculation.Phase.GAS).flowAndPressure(2.0, 60.0, 2.0)
        .gasProperties(320.0, 1.28, 0.95, 20.0).correctionFactors(0.975, 1.0, 1.0)
        .orificeCandidatesIn2(0.11, 0.196, 0.307, 0.503, 0.785, 1.287).build();
    ReliefSizingCalculation.Input twoPhase = ReliefSizingCalculation.Input
        .builder("fire", ReliefSizingCalculation.Phase.TWO_PHASE).flowAndPressure(2.0, 60.0, 2.0)
        .correctionFactors(0.85, 1.0, 1.0).orificeCandidatesIn2(0.11, 0.196, 0.307).build();
    ReliefSizingCalculation.Input undersizedTable = ReliefSizingCalculation.Input
        .builder("blocked-outlet", ReliefSizingCalculation.Phase.GAS).flowAndPressure(200.0, 60.0, 2.0)
        .gasProperties(320.0, 1.28, 0.95, 20.0).correctionFactors(0.975, 1.0, 1.0).orificeCandidatesIn2(0.11).build();

    EngineeringCalculationResult<ReliefSizingCalculation.Result> gasResult = new ReliefSizingCalculation()
        .calculate(gas, context);
    EngineeringCalculationResult<ReliefSizingCalculation.Result> twoPhaseResult = new ReliefSizingCalculation()
        .calculate(twoPhase, context);
    EngineeringCalculationResult<ReliefSizingCalculation.Result> undersizedResult = new ReliefSizingCalculation()
        .calculate(undersizedTable, context);

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, gasResult.getStatus());
    assertTrue(gasResult.getValue().toMap().containsKey("selectedOrificeAreaIn2"));
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, twoPhaseResult.getStatus());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, undersizedResult.getStatus());
  }

  private DexpiToolQualificationRunner.Snapshot snapshot(String tag) {
    Map<String, String> properties = new LinkedHashMap<String, String>();
    properties.put("tag", tag);
    properties.put("class", "Vessel");
    return new DexpiToolQualificationRunner.Snapshot("DEXPI 2.0").addObject("urn:equipment:1", properties)
        .addObject("urn:nozzle:1", new LinkedHashMap<String, String>())
        .addConnection("urn:equipment:1", "HAS_NOZZLE", "urn:nozzle:1");
  }
}
