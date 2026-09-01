package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
import neqsim.process.util.optimizer.ProcessModelDebottleneckRanking.CandidateEvidence;
import neqsim.process.util.optimizer.ProcessModelDebottleneckRanking.CandidateStatus;
import neqsim.process.util.optimizer.ProcessModelDebottleneckRanking.RankingDirection;
import neqsim.process.util.optimizer.ProcessModelDebottleneckRanking.RankingOutcome;
import neqsim.process.util.optimizer.ProcessModelDebottleneckRanking.RankingPolicy;
import neqsim.process.util.optimizer.ProcessModelDebottleneckRanking.RankingResult;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.CandidateListSearch;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.CapacityAlternative;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.LimitDirection;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.MetricDefinition;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.MetricKind;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.StudyResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ProcessModelDebottleneckRanking}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessModelDebottleneckRankingTest {

  /** Shared process-model fixture proving that each paired study restores the same live state. */
  private static final class Fixture {
    private final Stream feed;
    private final CapacityConstraint installedCapacity;
    private final ProcessModelSimulationEvaluator evaluator;

    private Fixture(Stream feed, CapacityConstraint installedCapacity, ProcessModelSimulationEvaluator evaluator) {
      this.feed = feed;
      this.installedCapacity = installedCapacity;
      this.evaluator = evaluator;
    }
  }

  /**
   * Three independently executed studies must rank 200, 100 and 100 kg/hr production deltas with deterministic
   * competition ranks while retaining the submitted order inside the tie.
   *
   * @throws Exception when Java serialization fails
   */
  @Test
  void ranksCompatibleAlternativesDeterministicallyAndSerializes() throws Exception {
    Fixture fixture = createFixture();
    fixture.evaluator.evaluate(new double[] { 800.0 });
    List<double[]> candidates = commonCandidates();

    StudyResult result1100 = createStudy(fixture, "study-1100", 1100.0, 0.90, "kg/hr", "wet feed mass rate", candidates)
        .evaluate();
    assertOriginalStateRestored(fixture);
    StudyResult result1150 = createStudy(fixture, "study-1150", 1150.0, 0.80, "kg/hr", "wet feed mass rate", candidates)
        .evaluate();
    assertOriginalStateRestored(fixture);
    StudyResult result1200 = createStudy(fixture, "study-1200", 1200.0, 0.95, "kg/hr", "wet feed mass rate", candidates)
        .evaluate();
    assertOriginalStateRestored(fixture);

    ProcessModelDebottleneckRanking ranking = createProductionRanking(1.0e-8, 0.50, 0.90);
    RankingResult first = ranking.rank(Arrays.asList(result1100, result1150, result1200));

    assertEquals(RankingOutcome.COMPLETED, first.getOutcome());
    assertEquals(3, first.getRankedCandidates().size());
    assertEquals(0, first.getRejectedCandidates().size());
    assertEquals("separator-gas-1200", first.getBestCandidate().getAlternativeDefinition().getId());
    assertEquals(200.0, first.getBestCandidate().getDelta(), 1.0e-8);
    assertEquals(1, first.getBestCandidate().getRank());
    assertEquals("separator-gas-1100", first.getRankedCandidates().get(1).getAlternativeDefinition().getId());
    assertEquals(2, first.getRankedCandidates().get(1).getRank());
    assertEquals("separator-gas-1150", first.getRankedCandidates().get(2).getAlternativeDefinition().getId());
    assertEquals(2, first.getRankedCandidates().get(2).getRank());
    assertEquals("separator-gas-1100", first.getCandidatesInInputOrder().get(0).getAlternativeDefinition().getId());
    assertEquals("kg/hr", first.getBestCandidate().getBaselineMetric().getUnit());
    assertEquals("NeqSim stream result", first.getBestCandidate().getAlternativeMetric().getProvenance());
    assertThrows(UnsupportedOperationException.class, () -> first.getRankedCandidates().clear());

    RankingResult restored = roundTrip(first);
    assertEquals(first.getOutcome(), restored.getOutcome());
    assertEquals(first.getBestCandidate().getAlternativeDefinition().getQualifiedConstraintName(),
        restored.getBestCandidate().getAlternativeDefinition().getQualifiedConstraintName());
    assertEquals(first.getBestCandidate().getDelta(), restored.getBestCandidate().getDelta(), 0.0);
    assertEquals(first.getPolicy().getMetricProvenance(), restored.getPolicy().getMetricProvenance());

    RankingResult second = ranking.rank(new StudyResult[] { result1100, result1150, result1200 });
    for (int index = 0; index < first.getRankedCandidates().size(); index++) {
      CandidateEvidence expected = first.getRankedCandidates().get(index);
      CandidateEvidence actual = second.getRankedCandidates().get(index);
      assertEquals(expected.getAlternativeDefinition().getId(), actual.getAlternativeDefinition().getId());
      assertEquals(expected.getRank(), actual.getRank());
      assertEquals(expected.getDelta(), actual.getDelta(), 0.0);
    }
  }

  /** Incompatible units, baselines and confidence must be rejected without a synthetic score. */
  @Test
  void rejectsIncompatibleEvidenceWithoutComparingUnlikeMetrics() {
    Fixture fixture = createFixture();
    fixture.evaluator.evaluate(new double[] { 800.0 });

    StudyResult reference = createStudy(fixture, "reference", 1100.0, 0.90, "kg/hr", "wet feed mass rate",
        commonCandidates()).evaluate();
    StudyResult wrongUnit = createStudy(fixture, "wrong-unit", 1150.0, 0.90, "t/day", "wet feed mass rate",
        commonCandidates()).evaluate();
    List<double[]> differentBaselineCandidates = new ArrayList<double[]>();
    differentBaselineCandidates.add(new double[] { 800.0 });
    differentBaselineCandidates.add(new double[] { 899.0 });
    differentBaselineCandidates.add(new double[] { 1099.0 });
    differentBaselineCandidates.add(new double[] { 1199.0 });
    differentBaselineCandidates.add(new double[] { 1400.0 });
    StudyResult wrongBaseline = createStudy(fixture, "wrong-baseline", 1200.0, 0.90, "kg/hr", "wet feed mass rate",
        differentBaselineCandidates).evaluate();
    StudyResult lowConfidence = createStudy(fixture, "low-confidence", 1250.0, 0.40, "kg/hr", "wet feed mass rate",
        commonCandidates()).evaluate();

    RankingResult result = createProductionRanking(0.0, 0.50, 0.90)
        .rank(Arrays.asList(reference, wrongUnit, wrongBaseline, lowConfidence));

    assertEquals(RankingOutcome.PARTIAL, result.getOutcome());
    assertEquals(1, result.getRankedCandidates().size());
    assertEquals(3, result.getRejectedCandidates().size());
    assertEquals(CandidateStatus.METRIC_METADATA_MISMATCH, result.getCandidatesInInputOrder().get(1).getStatus());
    assertEquals(CandidateStatus.BASELINE_INCOMPATIBLE, result.getCandidatesInInputOrder().get(2).getStatus());
    assertEquals(CandidateStatus.ALTERNATIVE_CONFIDENCE_TOO_LOW, result.getCandidatesInInputOrder().get(3).getStatus());
    assertTrue(Double.isNaN(result.getCandidatesInInputOrder().get(1).getDelta()));
    assertTrue(Double.isNaN(result.getCandidatesInInputOrder().get(2).getDelta()));
    assertTrue(Double.isNaN(result.getCandidatesInInputOrder().get(3).getDelta()));
    assertOriginalStateRestored(fixture);
  }

  /** Duplicate study or alternative identities must fail before a ranking is constructed. */
  @Test
  void duplicateIdentityFailsClosed() {
    Fixture fixture = createFixture();
    fixture.evaluator.evaluate(new double[] { 800.0 });
    StudyResult result = createStudy(fixture, "duplicate", 1100.0, 0.90, "kg/hr", "wet feed mass rate",
        commonCandidates()).evaluate();

    assertThrows(IllegalArgumentException.class,
        () -> createProductionRanking(0.0, Double.NaN, Double.NaN).rank(Arrays.asList(result, result)));
  }

  /** Creates the exact production-delta policy used by the focused regressions. */
  private ProcessModelDebottleneckRanking createProductionRanking(double tieTolerance,
      double minimumAlternativeConfidence, double minimumMetricConfidence) {
    RankingPolicy policy = new RankingPolicy("production-delta", "Production delta ranking",
        "synthetic deterministic portfolio policy", "production", "Feed production", MetricKind.PRODUCTION, "kg/hr",
        "wet feed mass rate", "NeqSim stream result", "single steady state", RankingDirection.MAXIMIZE, tieTolerance,
        1.0e-8, minimumAlternativeConfidence, minimumMetricConfidence);
    return new ProcessModelDebottleneckRanking("separator-portfolio", "Separator alternatives portfolio",
        "synthetic deterministic portfolio", policy);
  }

  /** Creates one independent paired study over a declared candidate set. */
  private ProcessModelDebottleneckStudy createStudy(Fixture fixture, String studyId, double proposedLimit,
      double alternativeConfidence, String metricUnit, String metricBasis, List<double[]> candidates) {
    CandidateListSearch search = new CandidateListSearch("ordered-throughput-grid", "Ordered throughput grid",
        "synthetic portfolio candidate set", candidates, 0, 0.0);
    CapacityAlternative alternative = new CapacityAlternative("separator-gas-" + Integer.toString((int) proposedLimit),
        "Raise separator installed gas capacity to " + proposedLimit + " kg/hr", "synthetic brownfield screening case",
        "separation", "separator", "installed gas rate", proposedLimit, "kg/hr", LimitDirection.MAXIMUM,
        "synthetic replacement equipment basis", alternativeConfidence, 900.0, 1300.0);
    ProcessModelDebottleneckStudy study = new ProcessModelDebottleneckStudy(studyId, "Paired separator capacity study",
        "synthetic deterministic regression", fixture.evaluator, alternative, search, 0);
    study.addMetric(new MetricDefinition("production", "Feed production", MetricKind.PRODUCTION, metricUnit,
        metricBasis, "NeqSim stream result", "single steady state", 1.0, true,
        model -> model.getVariableValue("wells::feed.flowRate", "kg/hr")));
    return study;
  }

  /** @return common deterministic candidate set with exact baseline and alternative incumbents */
  private List<double[]> commonCandidates() {
    List<double[]> candidates = new ArrayList<double[]>();
    candidates.add(new double[] { 800.0 });
    candidates.add(new double[] { 999.0 });
    candidates.add(new double[] { 1099.0 });
    candidates.add(new double[] { 1199.0 });
    candidates.add(new double[] { 1400.0 });
    return candidates;
  }

  /** Creates a two-area process model with one reversible direct installed capacity limit. */
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
    return new Fixture(feed, installed, evaluator);
  }

  /** Confirms that every sequential study returned the shared live fixture to its original state. */
  private void assertOriginalStateRestored(Fixture fixture) {
    assertEquals(1000.0, fixture.installedCapacity.getDesignValue(), 0.0);
    assertEquals(1300.0, fixture.installedCapacity.getMaxValue(), 0.0);
    assertEquals("synthetic installed basis", fixture.installedCapacity.getDataSource());
    assertEquals(0.95, fixture.installedCapacity.getConfidence(), 0.0);
    assertEquals(800.0, fixture.feed.getFlowRate("kg/hr"), 1.0e-8);
  }

  /** Java-serializes one immutable result and returns its detached copy. */
  private RankingResult roundTrip(RankingResult result) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(result);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    RankingResult restored = (RankingResult) input.readObject();
    input.close();
    return restored;
  }
}
