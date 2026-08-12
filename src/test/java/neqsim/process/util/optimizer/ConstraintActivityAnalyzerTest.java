package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ConstraintActivityAnalyzer.ActivityPolicy;
import neqsim.process.util.optimizer.ConstraintActivityAnalyzer.ActivityStatus;
import neqsim.process.util.optimizer.ConstraintActivityAnalyzer.ConstraintActivityAssessment;
import neqsim.process.util.optimizer.ConstraintActivityAnalyzer.ConstraintScale;
import neqsim.process.util.optimizer.ConstraintActivityAnalyzer.ScaledSensitivity;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ConstraintActivityAnalyzer}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ConstraintActivityAnalyzerTest {

  /** Test fixture holding a writable feed and its process model. */
  private static final class ModelFixture {
    /** Feed stream used as the decision variable. */
    private final Stream feed;

    /** Process model containing the feed. */
    private final ProcessModel model;

    /**
     * Creates a model fixture.
     *
     * @param feed feed stream
     * @param model process model
     */
    private ModelFixture(Stream feed, ProcessModel model) {
      this.feed = feed;
      this.model = model;
    }
  }

  /**
   * Creates a small deterministic model with one writable feed.
   *
   * @return configured model fixture
   */
  private ModelFixture createModelFixture() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(1000.0, "kg/hr");

    Stream feed = new Stream("feed", fluid);
    ThrottlingValve choke = new ThrottlingValve("choke", feed);
    choke.setOutletPressure(30.0, "bara");
    ProcessSystem area = new ProcessSystem("wells");
    area.add(feed);
    area.add(choke);
    ProcessModel model = new ProcessModel();
    model.add("wells", area);
    return new ModelFixture(feed, model);
  }

  /**
   * Creates the sensitivity result used by scaling and activity tests.
   *
   * @param fixture model fixture
   * @return configured evaluator after sampling
   */
  private ProcessModelSimulationEvaluator createEvaluator(ModelFixture fixture) {
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addParameter("field feed", "wells::feed.flowRate", 500.0, 1500.0, "kg/hr");
    evaluator.addObjective("export production", model -> fixture.feed.getFlowRate("kg/hr"),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    evaluator.getObjectives().get(0).setUnit("kg/hr");
    evaluator.addConstraintUpperBound("near installed limit", model -> fixture.feed.getFlowRate("kg/hr"), 1050.0);
    evaluator.addConstraintUpperBound("spare installed limit", model -> fixture.feed.getFlowRate("kg/hr"), 1300.0);
    evaluator.addConstraintUpperBound("violated installed limit", model -> fixture.feed.getFlowRate("kg/hr"), 950.0);
    evaluator.addConstraintUpperBound("soft operating target", model -> fixture.feed.getFlowRate("kg/hr"), 1025.0);
    for (ProcessModelSimulationEvaluator.ConstraintDefinition constraint : evaluator.getConstraints()) {
      constraint.setUnit("kg/hr");
    }
    evaluator.getConstraints().get(3).setHard(false);
    evaluator.setUseRelativeStep(false);
    evaluator.setFiniteDifferenceStep(10.0);
    return evaluator;
  }

  /** Verifies explicit scales produce deterministic dimensionless activity diagnostics. */
  @Test
  void explicitScalingClassifiesActivityWithoutAnotherProcessRun() throws Exception {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = createEvaluator(fixture);
    ProcessModelSimulationEvaluator.SensitivityQualityResult result = evaluator
        .estimateSensitivitiesWithQuality(new double[] { 1000.0 });
    int evaluationsAfterSampling = evaluator.getEvaluationCount();

    ConstraintScale near = ConstraintScale.fromSnapshot(result.getConstraintSnapshots().get(0), 500.0,
        "installed throughput operating envelope");
    ConstraintScale spare = ConstraintScale.fromSnapshot(result.getConstraintSnapshots().get(1), 500.0,
        "installed throughput operating envelope");
    ConstraintScale violated = ConstraintScale.fromSnapshot(result.getConstraintSnapshots().get(2), 500.0,
        "installed throughput operating envelope");
    ConstraintScale soft = ConstraintScale.fromSnapshot(result.getConstraintSnapshots().get(3), 500.0,
        "operator target range");
    List<ConstraintScale> outOfOrderScales = Arrays.asList(soft, violated, near, spare);
    ActivityPolicy policy = ActivityPolicy.hardConstraints(0.10,
        ProcessModelSimulationEvaluator.SensitivityQualificationPolicy.numericalOnly(1.0e-8));

    List<ConstraintActivityAssessment> assessments = ConstraintActivityAnalyzer.assess(result, outOfOrderScales,
        policy);

    assertEquals(4, assessments.size());
    assertEquals(ActivityStatus.CANDIDATE_ACTIVE, assessments.get(0).getStatus());
    assertEquals(ActivityStatus.INACTIVE, assessments.get(1).getStatus());
    assertEquals(ActivityStatus.VIOLATED, assessments.get(2).getStatus());
    assertEquals(ActivityStatus.EXCLUDED_SOFT, assessments.get(3).getStatus());
    assertEquals(0.10, assessments.get(0).getNormalizedMargin(), 1.0e-12);
    assertEquals(0.60, assessments.get(1).getNormalizedMargin(), 1.0e-12);
    assertEquals(-0.10, assessments.get(2).getNormalizedMargin(), 1.0e-12);
    assertEquals("near installed limit", assessments.get(0).getConstraint().getName());
    assertEquals("kg/hr", assessments.get(0).getScale().getUnit());
    assertEquals("installed throughput operating envelope", assessments.get(0).getScale().getProvenance());
    assertEquals(policy, assessments.get(0).getPolicy());
    assertEquals(1, ConstraintActivityAnalyzer.getCandidateActiveConstraints(assessments).size());
    assertEquals(1, ConstraintActivityAnalyzer.getViolatedConstraints(assessments).size());

    ScaledSensitivity sensitivity = assessments.get(0).getSensitivities().get(0);
    assertTrue(sensitivity.isUsable());
    assertEquals(-0.002, sensitivity.getNormalizedMarginDerivative(), 1.0e-12);
    assertEquals("1 per kg/hr", sensitivity.getNormalizedMarginDerivativeUnit());
    assertEquals(-1.0, sensitivity.getSensitivityAssessment().getMarginDerivative(), 1.0e-12);
    assertEquals(1, assessments.get(0).getUsableSensitivities().size());
    assertEquals(evaluationsAfterSampling, evaluator.getEvaluationCount(),
        "scaling and activity analysis must not rerun the process model");

    ActivityPolicy includeSoft = new ActivityPolicy(0.10,
        ProcessModelSimulationEvaluator.SensitivityQualificationPolicy.numericalOnly(1.0e-8), true);
    List<ConstraintActivityAssessment> includingSoft = ConstraintActivityAnalyzer.assess(result, outOfOrderScales,
        includeSoft);
    assertEquals(ActivityStatus.CANDIDATE_ACTIVE, includingSoft.get(3).getStatus());
    assertEquals(2, ConstraintActivityAnalyzer.getCandidateActiveConstraints(includingSoft).size());
    assertEquals(evaluationsAfterSampling, evaluator.getEvaluationCount());

    assertThrows(UnsupportedOperationException.class, () -> assessments.clear());
    assertThrows(UnsupportedOperationException.class, () -> assessments.get(0).getSensitivities().clear());
    assertThrows(UnsupportedOperationException.class, () -> assessments.get(0).getDiagnostics().clear());

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(assessments.get(0));
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    ConstraintActivityAssessment restored = (ConstraintActivityAssessment) input.readObject();
    input.close();
    assertEquals(ActivityStatus.CANDIDATE_ACTIVE, restored.getStatus());
    assertEquals(0.10, restored.getNormalizedMargin(), 1.0e-12);
    assertEquals("installed throughput operating envelope", restored.getScale().getProvenance());
    assertEquals(0.10, restored.getPolicy().getActiveNormalizedMarginTolerance(), 0.0);
  }

  /** Verifies activity status does not make rejected local derivatives usable. */
  @Test
  void strictSensitivityPolicyRetainsRejectionEvidence() {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = createEvaluator(fixture);
    ProcessModelSimulationEvaluator.SensitivityQualityResult result = evaluator
        .estimateSensitivitiesWithQuality(new double[] { 1000.0 });
    List<ConstraintScale> scales = Arrays.asList(
        ConstraintScale.fromSnapshot(result.getConstraintSnapshots().get(0), 500.0, "basis"),
        ConstraintScale.fromSnapshot(result.getConstraintSnapshots().get(1), 500.0, "basis"),
        ConstraintScale.fromSnapshot(result.getConstraintSnapshots().get(2), 500.0, "basis"),
        ConstraintScale.fromSnapshot(result.getConstraintSnapshots().get(3), 500.0, "basis"));
    ActivityPolicy strict = ActivityPolicy.hardConstraints(0.10,
        ProcessModelSimulationEvaluator.SensitivityQualificationPolicy.strict(1.0e-8));

    List<ConstraintActivityAssessment> assessments = ConstraintActivityAnalyzer.assess(result, scales, strict);
    ScaledSensitivity rejected = assessments.get(0).getSensitivities().get(0);

    assertEquals(ActivityStatus.CANDIDATE_ACTIVE, assessments.get(0).getStatus());
    assertFalse(rejected.isUsable());
    assertTrue(assessments.get(0).getUsableSensitivities().isEmpty());
    assertTrue(rejected.getRejectionReasons()
        .contains(ProcessModelSimulationEvaluator.SensitivityEvidenceFlag.BASE_INFEASIBLE));
  }

  /** Verifies missing, stale, unitless, duplicate, and invalid scale policies fail closed. */
  @Test
  void invalidOrStaleScalingEvidenceFailsClosed() {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = createEvaluator(fixture);
    ProcessModelSimulationEvaluator.SensitivityQualityResult result = evaluator
        .estimateSensitivitiesWithQuality(new double[] { 1000.0 });
    ConstraintScale near = ConstraintScale.fromSnapshot(result.getConstraintSnapshots().get(0), 500.0, "basis");
    ConstraintScale spare = ConstraintScale.fromSnapshot(result.getConstraintSnapshots().get(1), 500.0, "basis");
    ConstraintScale violated = ConstraintScale.fromSnapshot(result.getConstraintSnapshots().get(2), 500.0, "basis");
    ConstraintScale soft = ConstraintScale.fromSnapshot(result.getConstraintSnapshots().get(3), 500.0, "basis");
    ActivityPolicy policy = ActivityPolicy.hardConstraints(0.10,
        ProcessModelSimulationEvaluator.SensitivityQualificationPolicy.numericalOnly(1.0e-8));

    assertThrows(IllegalArgumentException.class,
        () -> ConstraintActivityAnalyzer.assess(result, Arrays.asList(near, spare, violated), policy));
    assertThrows(IllegalArgumentException.class,
        () -> ConstraintActivityAnalyzer.assess(result, Arrays.asList(near, near, violated, soft), policy));
    assertThrows(IllegalArgumentException.class,
        () -> ConstraintScale.fromSnapshot(result.getConstraintSnapshots().get(0), 0.0, "basis"));
    assertThrows(IllegalArgumentException.class,
        () -> ConstraintScale.fromSnapshot(result.getConstraintSnapshots().get(0), 500.0, " "));
    assertThrows(IllegalArgumentException.class, () -> new ActivityPolicy(Double.NaN,
        ProcessModelSimulationEvaluator.SensitivityQualificationPolicy.numericalOnly(1.0), false));
    assertThrows(IllegalArgumentException.class, () -> new ActivityPolicy(0.1, null, false));
    assertThrows(IllegalArgumentException.class, () -> ConstraintActivityAnalyzer.getCandidateActiveConstraints(null));

    ProcessModelSimulationEvaluator changedEvaluator = createEvaluator(fixture);
    changedEvaluator.getConstraints().get(0).setUpperBound(1060.0);
    ProcessModelSimulationEvaluator.SensitivityQualityResult changedResult = changedEvaluator
        .estimateSensitivitiesWithQuality(new double[] { 1000.0 });
    assertThrows(IllegalArgumentException.class, () -> ConstraintActivityAnalyzer.assess(changedResult,
        Arrays.asList(near, ConstraintScale.fromSnapshot(changedResult.getConstraintSnapshots().get(1), 500.0, "basis"),
            ConstraintScale.fromSnapshot(changedResult.getConstraintSnapshots().get(2), 500.0, "basis"),
            ConstraintScale.fromSnapshot(changedResult.getConstraintSnapshots().get(3), 500.0, "basis")),
        policy));

    ProcessModelSimulationEvaluator unitlessEvaluator = new ProcessModelSimulationEvaluator(fixture.model);
    unitlessEvaluator.addParameter("field feed", "wells::feed.flowRate", 500.0, 1500.0, "kg/hr");
    unitlessEvaluator.addObjective("feed", model -> fixture.feed.getFlowRate("kg/hr"));
    unitlessEvaluator.addConstraintUpperBound("unitless", model -> fixture.feed.getFlowRate("kg/hr"), 1100.0);
    ProcessModelSimulationEvaluator.SensitivityQualityResult unitlessResult = unitlessEvaluator
        .estimateSensitivitiesWithQuality(new double[] { 1000.0 });
    assertThrows(IllegalArgumentException.class,
        () -> ConstraintScale.fromSnapshot(unitlessResult.getConstraintSnapshots().get(0), 100.0, "basis"));
  }
}
