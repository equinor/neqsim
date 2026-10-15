package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer.ObjectiveType;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Ensures failed compressor calculations cannot supply feasible optimizer evidence.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ProductionOptimizerCompressorFailureTest extends NeqSimTest {
  /**
   * Creates a public synthetic compressor map with a reachable 14 bara target at its design feed.
   *
   * @return the configured compressor
   */
  private Compressor createCompressor() {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 4.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.2);
    fluid.addComponent("propane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    Compressor compressor = new Compressor("compressor", feed);
    compressor.setUsePolytropicCalc(true);
    double[][] flow = {{100.0, 140.0, 180.0}, {200.0, 260.0, 320.0}, {300.0, 380.0, 460.0}};
    double[][] head = {{100.0, 80.0, 60.0}, {180.0, 140.0, 100.0}, {260.0, 200.0, 140.0}};
    double[][] efficiency = {{70.0, 71.0, 70.0}, {72.0, 73.0, 72.0}, {74.0, 75.0, 74.0}};
    compressor.getCompressorChart().setCurves(new double[] {25.0, 300.0, 1.0, 1.0},
        new double[] {1000.0, 2000.0, 3000.0}, flow, head, flow, efficiency);
    compressor.getCompressorChart().setHeadUnit("kJ/kg");
    compressor.setSpeed(2000.0);
    compressor.setMinimumSpeed(1000.0);
    compressor.setMaximumSpeed(3000.0);
    compressor.setSolveSpeed(true);
    compressor.setOutletPressure(14.0);
    return compressor;
  }

  /** A rejected chart trial must be infeasible without evaluating stale objectives. */
  @Test
  void invalidChartCandidateDoesNotAbortSearchOrEvaluateStaleResults() {
    Compressor compressor = createCompressor();
    compressor.getInletStream().setFlowRate(1.0e9, "kg/hr");
    compressor.getInletStream().run();
    assertThrows(Compressor.SpeedSolveException.class, () -> compressor.run());
    ProcessSystem process = new ProcessSystem();
    process.add(compressor.getInletStream());
    process.add(compressor);
    AtomicInteger objectiveCalls = new AtomicInteger();
    OptimizationObjective objective = new OptimizationObjective("power", ps -> {
      objectiveCalls.incrementAndGet();
      return compressor.getPower();
    }, 1.0, ObjectiveType.MINIMIZE);
    OptimizationConfig config = new OptimizationConfig(1.0e9, 1.0e9).rateUnit("kg/hr");
    OptimizationResult result = new ProductionOptimizer().optimize(process, compressor.getInletStream(), config,
        Collections.singletonList(objective), Collections.emptyList());
    assertFalse(result.isFeasible());
    assertTrue(result.getObjectiveValues().isEmpty());
    assertEquals(0, objectiveCalls.get());
    assertEquals("compressorSpeedSolve", result.getConstraintStatuses().get(0).getName());
    assertTrue(result.getConstraintStatuses().get(0).getDescription().contains("compressor"));
    assertTrue(result.getIterationHistory().stream().noneMatch(ProductionOptimizer.IterationRecord::isFeasible));
    assertEquals(1.0e9, result.getDecisionVariables().get("feed"), 1.0e-6);
  }

  /** Capacity-envelope handling and optional validation cannot hide a missed pressure target. */
  @Test
  void unconvergedSpeedIsHardFailureWithCapacityAnalysisEnabled() {
    Compressor compressor = createCompressor();
    compressor.setOutletPressure(1000.0);
    compressor.setLimitSpeed(true);
    compressor.setCapacityAnalysisEnabled(true);
    ProcessSystem process = new ProcessSystem();
    process.add(compressor.getInletStream());
    process.add(compressor);
    OptimizationConfig config = new OptimizationConfig(1000.0, 1000.0).rateUnit("kg/hr").rejectInvalidSimulations(false)
        .defaultUtilizationLimit(100.0);
    OptimizationResult result = new ProductionOptimizer().optimize(process, compressor.getInletStream(), config, null,
        null);
    assertFalse(compressor.isSpeedSolveConverged());
    assertFalse(result.isFeasible());
    assertEquals("compressorSpeedSolve", result.getConstraintStatuses().get(0).getName());
    assertTrue(result.getConstraintStatuses().get(0).getMargin() < 0.0);
  }

  /** A later valid trial must recover normally after the map rejected another operating point. */
  @Test
  void failedChartDoesNotPoisonLaterValidCandidate() {
    Compressor compressor = createCompressor();
    compressor.getInletStream().setFlowRate(1.0e9, "kg/hr");
    compressor.getInletStream().run();
    assertThrows(Compressor.SpeedSolveException.class, () -> compressor.run());
    ProcessSystem process = new ProcessSystem();
    process.add(compressor.getInletStream());
    process.add(compressor);
    OptimizationConfig config = new OptimizationConfig(1000.0, 1000.0).rateUnit("kg/hr").defaultUtilizationLimit(100.0);
    OptimizationResult result = new ProductionOptimizer().optimize(process, compressor.getInletStream(), config, null,
        null);
    assertTrue(compressor.isSpeedSolveConverged());
    assertTrue(result.isFeasible(), result.getInfeasibilityDiagnosis());
    assertEquals(14.0, compressor.getOutletStream().getPressure("bara"), 0.001);
    assertEquals(1000.0, compressor.getOutletStream().getFlowRate("kg/hr"), 1.0e-6);
  }
}
