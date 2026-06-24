package neqsim.process.util.scenario;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Dynamic validation benchmark test for the anti-surge recycle loop. Verifies that the {@link AntiSurgeController}
 * arrests a flow-reduction surge transient that the open-loop reference case fails to survive.
 *
 * @author NeqSim
 * @version 1.0
 */
public class AntiSurgeDynamicBenchmarkTest {

  /**
   * With the controller active the machine must stay out of surge through the transient.
   */
  @Test
  void testControllerArrestsSurgeTransient() {
    AntiSurgeDynamicBenchmark benchmark = new AntiSurgeDynamicBenchmark();
    benchmark.run(true);
    assertTrue(benchmark.isSurgeAvoided(),
        "controller should keep the machine out of surge; min margin = " + benchmark.getMinimumSurgeMargin());
    assertTrue(benchmark.getMaximumValveOpening() > 5.0,
        "recycle valve should open during the transient; max opening = " + benchmark.getMaximumValveOpening());
    // closed-loop margin should settle near the 10% set point, not collapse
    assertTrue(benchmark.getMinimumSurgeMargin() > 0.0, "closed-loop margin should remain positive");
  }

  /**
   * Without the controller the same disturbance must drive the machine into surge, confirming the benchmark is a
   * genuine challenge and the controller adds value.
   */
  @Test
  void testOpenLoopReferenceSurges() {
    AntiSurgeDynamicBenchmark benchmark = new AntiSurgeDynamicBenchmark();
    benchmark.run(false);
    assertTrue(benchmark.getMinimumSurgeMargin() < 0.0,
        "open-loop reference should surge; min margin = " + benchmark.getMinimumSurgeMargin());
    assertTrue(!benchmark.isSurgeAvoided(), "open-loop reference should not avoid surge");
  }

  /**
   * The recorded traces should be populated and consistent with the configured number of steps.
   */
  @Test
  void testTracesArePopulated() {
    AntiSurgeDynamicBenchmark benchmark = new AntiSurgeDynamicBenchmark();
    benchmark.setNumberOfSteps(60);
    benchmark.run(true);
    double[] marginTrace = benchmark.getSurgeMarginTrace();
    double[] valveTrace = benchmark.getValveOpeningTrace();
    assertTrue(marginTrace != null && marginTrace.length == 61, "margin trace length");
    assertTrue(valveTrace != null && valveTrace.length == 61, "valve trace length");
  }
}
