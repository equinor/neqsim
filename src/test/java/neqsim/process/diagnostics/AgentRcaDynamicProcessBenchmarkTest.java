package neqsim.process.diagnostics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.diagnostics.AgentRcaDynamicProcessBenchmark.Scenario;
import neqsim.process.diagnostics.AgentRcaDynamicProcessBenchmark.ScenarioRun;

/**
 * Integration tests for the dynamic NeqSim AgentRCA benchmark.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class AgentRcaDynamicProcessBenchmarkTest extends NeqSimTest {
  private static final int TEST_SAMPLES = 24;

  /**
   * Runs all controlled faults, verifies their physical injection and checks zero-shot Top-2 recovery.
   */
  @Test
  public void testControlledDynamicFaultsAreEvidenceGrounded() {
    AgentRcaDynamicProcessBenchmark benchmark = new AgentRcaDynamicProcessBenchmark();
    ScenarioRun training = benchmark.runScenario(Scenario.NORMAL, 1.0, TEST_SAMPLES, 10L);
    RcaNormalOperationModel normalModel = RcaNormalOperationModel.fit(Collections.singletonList(training.getWindow()));
    List<RcaFaultHypothesis> hypotheses = benchmark.createDefaultHypotheses();
    RcaDiagnosisEngine engine = new RcaDiagnosisEngine();
    Map<Scenario, ScenarioRun> runs = new EnumMap<Scenario, ScenarioRun>(Scenario.class);

    for (Scenario scenario : Scenario.values()) {
      ScenarioRun run = benchmark.runScenario(scenario, 1.0, TEST_SAMPLES, 100L + scenario.ordinal());
      runs.put(scenario, run);
      RcaDiagnosis diagnosis = engine.diagnose(normalModel, run.getWindow(), hypotheses);
      assertTrue(diagnosis.isInTop(scenario.name(), 2), scenario + " was not recovered in Top-2; top hypothesis was "
          + diagnosis.getTopHypothesis().getName() + " with evidence " + diagnosis.toJson());
    }

    assertEquals(0.0, runs.get(Scenario.NORMAL).getLeakedMassKg(), 1.0e-12);
    assertTrue(runs.get(Scenario.EXPORT_GAS_LEAK).getLeakedMassKg() > 0.0);
    assertEquals(0.75, runs.get(Scenario.INLET_BLOCKAGE).getFinalInletValveFoulingFraction(), 1.0e-12);
    assertTrue(mean(runs.get(Scenario.PRESSURE_SENSOR_BIAS).getWindow()
        .getSignal(AgentRcaDynamicProcessBenchmark.SEPARATOR_PRESSURE))
        - mean(
            runs.get(Scenario.NORMAL).getWindow().getSignal(AgentRcaDynamicProcessBenchmark.SEPARATOR_PRESSURE)) > 1.5);
    assertTrue(variance(runs.get(Scenario.MULTIPHASE_SLUGGING).getWindow()
        .getSignal(AgentRcaDynamicProcessBenchmark.LIQUID_FEED_FLOW)) > 100.0);
  }

  /**
   * Checks repeated execution and a nearby time step for finite, deterministic process windows.
   */
  @Test
  public void testRepeatedExecutionAndNearbyTimeStep() {
    AgentRcaDynamicProcessBenchmark benchmark = new AgentRcaDynamicProcessBenchmark();
    ScenarioRun first = benchmark.runScenario(Scenario.PRESSURE_SENSOR_BIAS, 1.0, 8, 42L);
    ScenarioRun second = benchmark.runScenario(Scenario.PRESSURE_SENSOR_BIAS, 1.0, 8, 42L);
    assertArrayEquals(first.getWindow().getSignal(AgentRcaDynamicProcessBenchmark.SEPARATOR_PRESSURE),
        second.getWindow().getSignal(AgentRcaDynamicProcessBenchmark.SEPARATOR_PRESSURE), 1.0e-10);

    ScenarioRun nearby = benchmark.runScenario(Scenario.NORMAL, 0.5, 8, 42L);
    assertTrue(nearby.getFinalSeparatorPressureBara() > 0.0);
    assertTrue(nearby.getFinalSeparatorLevel() >= 0.0);
    assertTrue(nearby.getFinalSeparatorLevel() <= 1.0);
  }

  private static double mean(double[] values) {
    double sum = 0.0;
    for (double value : values) {
      sum += value;
    }
    return sum / values.length;
  }

  private static double variance(double[] values) {
    double mean = mean(values);
    double sum = 0.0;
    for (double value : values) {
      double delta = value - mean;
      sum += delta * delta;
    }
    return sum / Math.max(1.0, values.length - 1.0);
  }
}
