package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.CandidateListSearch;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.CapacityAlternative;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.LimitDirection;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.MetricDefinition;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.MetricKind;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.MetricSampler;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.StudyOutcome;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.StudyResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ProcessModelDebottleneckStudy}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessModelDebottleneckStudyTest {

  /** Small process-model fixture with one direct installed gas-capacity limit. */
  private static final class Fixture {
    private final Stream feed;
    private final CapacityConstraint installedCapacity;
    private final ProcessModel model;
    private final ProcessModelSimulationEvaluator evaluator;

    private Fixture(Stream feed, CapacityConstraint installedCapacity, ProcessModel model,
        ProcessModelSimulationEvaluator evaluator) {
      this.feed = feed;
      this.installedCapacity = installedCapacity;
      this.model = model;
      this.evaluator = evaluator;
    }
  }

  /**
   * A 20% installed-capacity increase must produce a paired 200 kg/hr throughput gain using candidates 1 kg/hr below
   * each installed limit, while every original mutable limit field and the pre-study operating point are restored
   * exactly.
   *
   * @throws Exception when result serialization fails
   */
  @Test
  void pairedCapacityAlternativeIsDeterministicSerializableAndReversible() throws Exception {
    Fixture fixture = createFixture();
    fixture.evaluator.evaluate(new double[] { 800.0 });
    int evaluationCountBeforeStudy = fixture.evaluator.getEvaluationCount();

    ProcessModelDebottleneckStudy study = createStudy(fixture, "kg/hr");
    StudyResult first = study.evaluate();

    assertEquals(StudyOutcome.COMPLETED, first.getOutcome());
    assertTrue(first.isCapacityRestored());
    assertTrue(first.isProcessStateRestored());
    assertTrue(first.isRecoverySimulationConverged());
    assertArrayEquals(new double[] { 999.0 }, first.getBaseline().getSelectedParameters(), 0.0);
    assertArrayEquals(new double[] { 1199.0 }, first.getAlternative().getSelectedParameters(), 0.0);
    assertEquals(200.0, first.getObjectiveDelta(), 1.0e-8);
    assertEquals(5, first.getBaseline().getEvaluationCount());
    assertEquals(5, first.getAlternative().getEvaluationCount());
    assertEquals(evaluationCountBeforeStudy + 11, fixture.evaluator.getEvaluationCount());

    assertEquals(4, first.getMetricComparisons().size());
    assertEquals(200.0, first.getMetricComparisons().get(0).getDelta(), 1.0e-8);
    assertEquals(2.0, first.getMetricComparisons().get(1).getDelta(), 1.0e-8);
    assertEquals(0.8, first.getMetricComparisons().get(2).getDelta(), 1.0e-8);
    assertEquals(1000.0, first.getMetricComparisons().get(3).getDelta(), 1.0e-8);
    assertEquals("kg/hr", first.getMetricComparisons().get(0).getBaseline().getUnit());
    assertEquals("synthetic linear power relationship",
        first.getMetricComparisons().get(1).getBaseline().getProvenance());
    assertEquals(MetricKind.SCREENING_ECONOMIC, first.getMetricComparisons().get(3).getBaseline().getKind());

    assertEquals(1000.0, first.getOriginalCapacityState().getApplicableLimit(), 0.0);
    assertEquals(1200.0, first.getAppliedCapacityState().getApplicableLimit(), 0.0);
    assertEquals(1000.0, first.getBaseline().getInstalledCapacityEvidence().get(0).getApplicableLimit(), 0.0);
    assertEquals(1200.0, first.getAlternative().getInstalledCapacityEvidence().get(0).getApplicableLimit(), 0.0);
    assertEquals("synthetic replacement equipment basis",
        first.getAlternative().getInstalledCapacityEvidence().get(0).getDataSource());
    assertEquals(0.90, first.getAlternative().getInstalledCapacityEvidence().get(0).getConfidence(), 0.0);
    assertOriginalStateRestored(fixture);

    double[] exposed = first.getAlternative().getSelectedParameters();
    exposed[0] = -1.0;
    assertArrayEquals(new double[] { 1199.0 }, first.getAlternative().getSelectedParameters(), 0.0);
    assertThrows(UnsupportedOperationException.class, () -> first.getMetricComparisons().clear());

    StudyResult serialized = roundTrip(first);
    assertEquals(first.getOutcome(), serialized.getOutcome());
    assertEquals(first.getAlternativeDefinition().getQualifiedConstraintName(),
        serialized.getAlternativeDefinition().getQualifiedConstraintName());
    assertEquals(first.getObjectiveDelta(), serialized.getObjectiveDelta(), 0.0);
    assertEquals(first.getMetricComparisons().get(2).getAlternative().getProvenance(),
        serialized.getMetricComparisons().get(2).getAlternative().getProvenance());

    StudyResult second = study.evaluate();
    assertEquals(first.getOutcome(), second.getOutcome());
    assertArrayEquals(first.getBaseline().getSelectedParameters(), second.getBaseline().getSelectedParameters(), 0.0);
    assertArrayEquals(first.getAlternative().getSelectedParameters(), second.getAlternative().getSelectedParameters(),
        0.0);
    assertEquals(first.getObjectiveDelta(), second.getObjectiveDelta(), 0.0);
    assertNotSame(first.getMetricComparisons(), second.getMetricComparisons());
    assertOriginalStateRestored(fixture);
  }

  /** A unit mismatch must fail closed before any simulator evaluation or model mutation. */
  @Test
  void incompatibleCapacityAlternativeFailsClosedWithoutEvaluation() {
    Fixture fixture = createFixture();
    fixture.evaluator.evaluate(new double[] { 800.0 });
    int evaluationCountBeforeStudy = fixture.evaluator.getEvaluationCount();

    StudyResult result = createStudy(fixture, "t/day").evaluate();

    assertEquals(StudyOutcome.ALTERNATIVE_NOT_APPLICABLE, result.getOutcome());
    assertEquals(evaluationCountBeforeStudy, fixture.evaluator.getEvaluationCount());
    assertFalse(result.isObjectiveDeltaCalculable());
    assertTrue(result.getBaseline() == null);
    assertTrue(result.getAlternative() == null);
    assertOriginalStateRestored(fixture);
  }

  /** A non-finite required metric must be diagnosed without discarding valid physical scenarios. */
  @Test
  void unavailableRequiredMetricRetainsPhysicalEvidenceAndRestoresState() {
    Fixture fixture = createFixture();
    fixture.evaluator.evaluate(new double[] { 800.0 });
    ProcessModelDebottleneckStudy study = createStudy(fixture, "kg/hr");
    study.addMetric(new MetricDefinition("required-quality", "Required quality", MetricKind.OTHER, "mol/mol",
        "dry product basis", "synthetic unavailable analyzer", "single steady state", 0.5, true, new MetricSampler() {
          private static final long serialVersionUID = 1L;

          @Override
          public double sample(ProcessModel model) {
            return Double.NaN;
          }
        }));

    StudyResult result = study.evaluate();

    assertEquals(StudyOutcome.REQUIRED_METRIC_UNAVAILABLE, result.getOutcome());
    assertTrue(result.getBaseline().isQualified());
    assertTrue(result.getAlternative().isQualified());
    assertTrue(result.isObjectiveDeltaCalculable());
    assertFalse(result.getMetricComparisons().get(4).isCalculable());
    assertTrue(result.isCapacityRestored());
    assertTrue(result.isProcessStateRestored());
    assertOriginalStateRestored(fixture);
  }

  /** Creates the deterministic paired study used by the focused tests. */
  private ProcessModelDebottleneckStudy createStudy(Fixture fixture, String alternativeUnit) {
    List<double[]> candidates = new ArrayList<double[]>();
    candidates.add(new double[] { 800.0 });
    // Keep selected points 1 kg/hr inside each limit so unit-conversion roundoff cannot make equality infeasible.
    candidates.add(new double[] { 999.0 });
    candidates.add(new double[] { 1199.0 });
    candidates.add(new double[] { 1400.0 });
    CandidateListSearch search = new CandidateListSearch("ordered-throughput-grid", "Ordered throughput grid",
        "synthetic acceptance candidate set", candidates, 0, 0.0);
    CapacityAlternative alternative = new CapacityAlternative("separator-gas-1200",
        "Raise separator installed gas capacity", "synthetic brownfield screening case", "separation", "separator",
        "installed gas rate", 1200.0, alternativeUnit, LimitDirection.MAXIMUM, "synthetic replacement equipment basis",
        0.90, 900.0, 1300.0);
    ProcessModelDebottleneckStudy study = new ProcessModelDebottleneckStudy("paired-separator-study",
        "Paired separator capacity study", "synthetic deterministic regression", fixture.evaluator, alternative, search,
        0);
    study.addMetric(new MetricDefinition("production", "Feed production", MetricKind.PRODUCTION, "kg/hr",
        "wet feed mass rate", "NeqSim stream result", "single steady state", 1.0, true,
        model -> model.getVariableValue("wells::feed.flowRate", "kg/hr")));
    study.addMetric(new MetricDefinition("power", "Synthetic power", MetricKind.POWER, "kW", "shaft power",
        "synthetic linear power relationship", "single steady state", 0.8, true,
        model -> 0.01 * model.getVariableValue("wells::feed.flowRate", "kg/hr")));
    study.addMetric(new MetricDefinition("indirect-emissions", "Synthetic indirect emissions",
        MetricKind.INDIRECT_EMISSIONS, "kgCO2e/hr", "location-based electricity", "synthetic 0.4 kgCO2e/kWh factor",
        "single steady state", 0.5, false, model -> 0.004 * model.getVariableValue("wells::feed.flowRate", "kg/hr")));
    study.addMetric(new MetricDefinition("screening-value", "Synthetic screening value", MetricKind.SCREENING_ECONOMIC,
        "NOK/hr", "gross value before costs", "synthetic educational 5 NOK/kg factor", "single steady state", 0.2,
        false, model -> 5.0 * model.getVariableValue("wells::feed.flowRate", "kg/hr")));
    return study;
  }

  /** Creates a two-area process model with one mutable direct capacity constraint. */
  private Fixture createFixture() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(800.0, "kg/hr");
    final Stream feed = new Stream("feed", fluid);
    final CapacityConstraint installed = new CapacityConstraint("installed gas rate", "kg/hr", ConstraintType.HARD)
        .setDesignValue(1000.0).setMaxValue(1300.0).setWarningThreshold(0.90).setSeverity(ConstraintSeverity.HARD)
        .setDataSource("synthetic installed basis").setConfidence(0.95).setValidityRange(500.0, 1400.0)
        .setShadowPrice(12.3).setValueSupplier(new DoubleSupplier() {
          @Override
          public double getAsDouble() {
            return feed.getFlowRate("kg/hr");
          }
        });
    Separator separator = new Separator("separator", feed);
    separator.clearCapacityConstraints();
    separator.addCapacityConstraint(installed);

    ProcessSystem wells = new ProcessSystem("wells");
    wells.add(feed);
    ProcessSystem separation = new ProcessSystem("separation");
    separation.add(separator);
    ProcessModel model = new ProcessModel();
    model.add("wells", wells);
    model.add("separation", separation);

    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(model);
    evaluator.setIncludeStrategyCapacityConstraints(false);
    evaluator.addParameter("wells::feed.flowRate", 800.0, 1400.0, "kg/hr")
        .addObjective("feed production", new ToDoubleFunction<ProcessModel>() {
          @Override
          public double applyAsDouble(ProcessModel completedModel) {
            return completedModel.getVariableValue("wells::feed.flowRate", "kg/hr");
          }
        }, ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE).addEquipmentCapacityConstraints();
    evaluator.getObjectives().get(0).setUnit("kg/hr");
    return new Fixture(feed, installed, model, evaluator);
  }

  /** Confirms exact recovery of every mutable installed-capacity field and the process set point. */
  private void assertOriginalStateRestored(Fixture fixture) {
    assertEquals(1000.0, fixture.installedCapacity.getDesignValue(), 0.0);
    assertEquals(1300.0, fixture.installedCapacity.getMaxValue(), 0.0);
    assertEquals(0.0, fixture.installedCapacity.getMinValue(), 0.0);
    assertEquals(0.90, fixture.installedCapacity.getWarningThreshold(), 0.0);
    assertEquals("kg/hr", fixture.installedCapacity.getUnit());
    assertEquals(ConstraintSeverity.HARD, fixture.installedCapacity.getSeverity());
    assertTrue(fixture.installedCapacity.isEnabled());
    assertEquals("synthetic installed basis", fixture.installedCapacity.getDataSource());
    assertTrue(fixture.installedCapacity.hasConfidence());
    assertEquals(0.95, fixture.installedCapacity.getConfidence(), 0.0);
    assertTrue(fixture.installedCapacity.hasValidityRange());
    assertEquals(500.0, fixture.installedCapacity.getValidityMinimum(), 0.0);
    assertEquals(1400.0, fixture.installedCapacity.getValidityMaximum(), 0.0);
    assertEquals(12.3, fixture.installedCapacity.getShadowPrice(), 0.0);
    assertEquals(800.0, fixture.feed.getFlowRate("kg/hr"), 1.0e-8);
  }

  /** Java-serializes one immutable result and returns the deserialized copy. */
  private StudyResult roundTrip(StudyResult result) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(result);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    StudyResult restored = (StudyResult) input.readObject();
    input.close();
    return restored;
  }
}
