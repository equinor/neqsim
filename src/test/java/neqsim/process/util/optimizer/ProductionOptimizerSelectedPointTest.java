package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer.IterationRecord;
import neqsim.process.util.optimizer.ProductionOptimizer.ObjectiveType;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConstraint;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.process.util.optimizer.ProductionOptimizer.SearchMode;
import neqsim.thermo.system.SystemSrkEos;

/** Ensures optimizer evidence and live equipment refer to the same exact decision vector. */
class ProductionOptimizerSelectedPointTest extends NeqSimTest {
  private Stream createFeed() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    return feed;
  }

  @Test
  void binarySearchLeavesCompressorAtVerifiedSelectedFlow() {
    Stream feed = createFeed();
    Compressor compressor = new Compressor("compressor", feed);
    compressor.setOutletPressure(100.0, "bara");
    compressor.getMechanicalDesign().setMaxDesignPower(500.0);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    OptimizationConfig config = new OptimizationConfig(1000.0, 30000.0).rateUnit("kg/hr").tolerance(10.0)
        .defaultUtilizationLimit(0.9);
    OptimizationObjective power = new OptimizationObjective("power", ps -> compressor.getPower("kW"), 1.0,
        ObjectiveType.MINIMIZE);
    OptimizationResult result = new ProductionOptimizer().optimize(process, feed, config,
        Collections.singletonList(power), Collections.emptyList());
    assertTrue(result.isFeasible(), result.getInfeasibilityDiagnosis());
    assertEquals(result.getOptimalRate(), feed.getFlowRate("kg/hr"), 1.0e-7);
    assertEquals(result.getOptimalRate(), compressor.getOutletStream().getFlowRate("kg/hr"), 1.0e-7);
    assertEquals(result.getOptimalRate(), result.getDecisionVariables().get("feed"), 1.0e-10);
    assertEquals(compressor.getPower("kW"), result.getObjectiveValues().get("power"), 1.0e-8);
    assertEquals(compressor.getMaxUtilization(), result.getBottleneckUtilization(), 1.0e-8);
    assertTrue(compressor.getPower("kW") <= 450.0);
  }

  @Test
  void subTenthFlowCandidatesKeepExactDecisionsAndScoreEvidence() {
    Stream feed = createFeed();
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    final double target = 1.035;
    OptimizationObjective targetObjective = new OptimizationObjective("target", ps -> {
      double distance = feed.getFlowRate("kg/hr") - target;
      return -distance * distance;
    }, 1.0, ObjectiveType.MAXIMIZE);
    OptimizationConstraint upper = OptimizationConstraint.lessThan("flow ceiling", ps -> feed.getFlowRate("kg/hr"),
        1.049, ProductionOptimizer.ConstraintSeverity.HARD, 0.0, "Synthetic cache-boundary regression");
    OptimizationConfig config = new OptimizationConfig(1.0, 1.09).rateUnit("kg/hr")
        .searchMode(SearchMode.GOLDEN_SECTION_SCORE).tolerance(1.0e-6).maxIterations(40);
    OptimizationResult result = new ProductionOptimizer().optimize(process, feed, config,
        Collections.singletonList(targetObjective), Collections.singletonList(upper));
    assertTrue(result.isFeasible());
    assertEquals(target, result.getOptimalRate(), 1.0e-5);
    assertEquals(result.getOptimalRate(), feed.getFlowRate("kg/hr"), 1.0e-10);
    assertEquals(targetObjective.evaluate(process), result.getObjectiveValues().get("target"), 1.0e-15);
    assertEquals(1.049 - feed.getFlowRate("kg/hr"), result.getConstraintStatuses().get(0).getMargin(), 1.0e-12);
    for (IterationRecord record : result.getIterationHistory()) {
      assertEquals(record.getRate(), record.getDecisionVariables().get("feed"), 1.0e-12,
          "A nearby cache bucket must not report another point's decisions");
    }
  }

  @Test
  void failedFinalSolveCannotReturnEarlierFeasibleEvidence() {
    Stream feed = createFeed();
    ProcessSystem process = new ProcessSystem() {
      private static final long serialVersionUID = 1L;
      private int runs;

      @Override
      public void run() {
        runs++;
        if (runs == 2) {
          throw new IllegalStateException("Selected-point verification failed");
        }
        super.run();
      }
    };
    process.add(feed);
    OptimizationConfig config = new OptimizationConfig(1000.0, 2000.0).maxIterations(1);
    assertThrows(IllegalStateException.class,
        () -> new ProductionOptimizer().optimize(process, feed, config, null, null));
  }

  @Test
  void binarySearchFallsBackOnlyToFreshlyVerifiedFeasiblePoint() {
    Stream feed = createFeed();
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    AtomicInteger selectedPointEvaluations = new AtomicInteger();
    OptimizationConstraint replaySensitiveLimit = OptimizationConstraint.lessThan("replay-sensitive limit", ps -> {
      double rate = feed.getFlowRate("kg/hr");
      if (Math.abs(rate - 1750.0) < 1.0e-12 && selectedPointEvaluations.incrementAndGet() > 1) {
        return 1800.000001;
      }
      return rate;
    }, 1800.0, ProductionOptimizer.ConstraintSeverity.HARD, 1.0,
        "Synthetic non-repeatable boundary used to verify conservative replay");
    OptimizationConfig config = new OptimizationConfig(1000.0, 2000.0).rateUnit("kg/hr").maxIterations(3).tolerance(1.0)
        .searchMode(SearchMode.BINARY_FEASIBILITY);

    OptimizationResult result = new ProductionOptimizer().optimize(process, feed, config, null,
        Collections.singletonList(replaySensitiveLimit));

    assertTrue(result.isFeasible(), result.getInfeasibilityDiagnosis());
    assertEquals(1500.0, result.getOptimalRate(), 1.0e-12);
    assertEquals(result.getOptimalRate(), feed.getFlowRate("kg/hr"), 1.0e-12);
    assertTrue(result.getIterationHistory().size() > 3, "Replay attempts must remain visible in the evidence");
  }
}
