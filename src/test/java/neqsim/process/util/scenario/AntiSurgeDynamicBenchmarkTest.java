package neqsim.process.util.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.AntiSurgeController;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveTravelModel;

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

  /** Verifies one finite actuator advance per sample and gas-path response to actual position rather than demand. */
  @Test
  void testFiniteValveTravelDrivesTheSurrogatePlant() {
    AntiSurgeDynamicBenchmark benchmark = new AntiSurgeDynamicBenchmark();
    benchmark.setInitialMargin(0.05);
    benchmark.setTimeStep(0.1);
    benchmark.setNumberOfSteps(3);
    benchmark.setDisturbanceRate(0.02);
    benchmark.setRecycleAuthority(0.06);
    AntiSurgeController controller = benchmark.getController();
    controller.setProportionalGain(450.0);
    controller.setIntegralTime(18.0);
    ThrottlingValve valve = controller.getRecycleValve();
    valve.setTravelModel(ValveTravelModel.LINEAR_RATE_LIMIT);
    valve.setTravelTime(10.0);

    benchmark.run(true);

    double[] margin = benchmark.getSurgeMarginTrace();
    double[] actual = benchmark.getValveOpeningTrace();
    double[] target = benchmark.getTargetValveOpeningTrace();
    double expectedIntegral = 0.0;
    for (int step = 1; step < actual.length; step++) {
      assertEquals(step, actual[step], 1.0e-12, "ten-second travel permits one percentage point per sample");
      assertEquals(margin[step - 1] - 0.02 * 0.1 + 0.06 * actual[step - 1] / 100.0 * 0.1, margin[step], 1.0e-12,
          "only the previous actual valve position supplies recycle authority");
      double error = 0.10 - margin[step];
      expectedIntegral += 450.0 / 18.0 * error * 0.1;
      assertEquals(450.0 * error + expectedIntegral, target[step], 1.0e-12,
          "the valve owns exactly one controller scan per physical interval");
    }
    assertTrue(target[1] > actual[1] + 20.0, "unreached demand must not be used as actual recycle opening");
    assertEquals(actual[actual.length - 1], valve.getPercentValveOpening(), 0.0);
  }

  /**
   * With finite valve stroke speed and actuator lag, rate-aware prediction should create earlier recycle action and a
   * higher minimum surge margin than current-margin PI alone.
   */
  @Test
  void testPredictiveControllerImprovesRateLimitedTransient() {
    AntiSurgeDynamicBenchmark piBenchmark = new AntiSurgeDynamicBenchmark();
    piBenchmark.getController().setActuatorDynamics(10.0, 3.0);
    piBenchmark.run(true);

    AntiSurgeDynamicBenchmark predictiveBenchmark = new AntiSurgeDynamicBenchmark();
    AntiSurgeController predictiveController = predictiveBenchmark.getController();
    predictiveController.setActuatorDynamics(10.0, 3.0);
    predictiveController.setPredictiveActionEnabled(true);
    predictiveController.setPredictionHorizon(8.0);
    predictiveController.setMarginRateFilterTime(5.0);
    predictiveBenchmark.run(true);

    assertTrue(piBenchmark.getMinimumSurgeMargin() < 0.0,
        "rate-limited current-margin PI should briefly cross surge; min margin = "
            + piBenchmark.getMinimumSurgeMargin());
    assertTrue(predictiveBenchmark.getMinimumSurgeMargin() > 0.0,
        "predictive supervision should keep positive surge margin; min margin = "
            + predictiveBenchmark.getMinimumSurgeMargin());
    assertTrue(predictiveBenchmark.getMinimumSurgeMargin() > piBenchmark.getMinimumSurgeMargin(),
        "predictive supervision should improve the minimum margin");
    assertTrue(predictiveBenchmark.getPredictedSurgeMarginTrace() != null,
        "predictive margin trace should be recorded");
  }
}
