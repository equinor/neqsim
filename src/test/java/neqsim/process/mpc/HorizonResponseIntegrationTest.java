package neqsim.process.mpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Analytical delayed process and measured disturbance checks through the public controller API.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class HorizonResponseIntegrationTest {
  /** A delayed unit-gain plant gives zero first move over one step and 8/3 over three. */
  @Test
  void delayedModelChangesFirstMoveAndRemembersTheAppliedInput() {
    ProcessLinkedMPC controller = new ProcessLinkedMPC("delayed", new ProcessSystem());
    ManipulatedVariable mv = controller.addMV("input", "pressure", 0.0, 100.0).setMoveWeight(1.0);
    mv.setCurrentValue(50.0);
    ControlledVariable cv = controller.addCV("output", "pressure", 4.0);
    cv.setCurrentValue(0.0);
    controller.setSampleTime(1.0);
    controller.setControlHorizon(1);
    controller.setPredictionHorizon(1);
    controller.setHorizonResponses(new StepResponse(mv.getName(), cv.getName(), new double[] {0, 1, 2},
        new double[] {0, 0, 1}, 1, 0, 1, "bar", "bar"));
    assertEquals(50.0, controller.calculate()[0], 1e-6);

    controller.setPredictionHorizon(3);
    double expected = 50.0 + 8.0 / 3.0;
    assertEquals(expected, controller.calculate()[0], 1e-5);
    assertEquals(expected, controller.calculate()[0], 1e-5);
    assertEquals(50.0, mv.getCurrentValue(), 1e-10);
    controller.applyMoves();
    assertThrows(IllegalStateException.class, controller::applyMoves);
    controller.calculate();
    assertEquals(8.0 / 3.0, controller.getLastHorizonPlan().getPredictedCvs()[0][0], 1e-5);
  }

  /** Reject a stale multi-input request before applying any part of the plan. */
  @Test
  void changedSecondActuatorInvalidatesAllMoves() {
    ProcessLinkedMPC controller = new ProcessLinkedMPC("two actuators", new ProcessSystem());
    ManipulatedVariable first = controller.addMV("first", "pressure", 0, 100, 1);
    ManipulatedVariable second = controller.addMV("second", "pressure", 0, 100, 1);
    first.setCurrentValue(50);
    second.setCurrentValue(50);
    ControlledVariable cv = controller.addCV("output", "pressure", 20);
    cv.setCurrentValue(0);
    controller.setSampleTime(1);
    controller.setControlHorizon(1);
    controller.setPredictionHorizon(2);
    controller.setHorizonResponses(
        new StepResponse(first.getName(), cv.getName(), new double[] {1}, new double[] {1}, 1, 0, 1, "", ""),
        new StepResponse(second.getName(), cv.getName(), new double[] {1}, new double[] {1}, 1, 0, 1, "", ""));
    assertEquals(51, controller.calculate()[0], 1e-6);
    second.setCurrentValue(60);
    assertThrows(IllegalStateException.class, controller::applyMoves);
    assertEquals(50, first.getCurrentValue(), 0);
    assertEquals(60, second.getCurrentValue(), 0);
    assertNull(controller.getLastMoves());

    controller.calculate();
    second.setRateLimit(-1, 0);
    assertThrows(IllegalStateException.class, controller::applyMoves);
    assertEquals(50, first.getCurrentValue(), 0);
  }

  /** A partially previewed measured disturbance settles over three intervals with a held tail. */
  @Test
  void delayedDisturbanceUsesMeasuredHistoryAndRepeatedPreviewsDoNotAdvanceIt() {
    ProcessLinkedMPC controller = new ProcessLinkedMPC("preview", new ProcessSystem());
    ManipulatedVariable mv = controller.addMV("input", "flowRate", 5, 5);
    mv.setCurrentValue(5);
    ControlledVariable cv = controller.addCV("output", "pressure", 10);
    cv.setCurrentValue(10);
    DisturbanceVariable dv = controller.addDV("load", "flowRate");
    dv.setCurrentValue(20);
    controller.setSampleTime(1);
    controller.setPredictionHorizon(3);
    controller.setControlHorizon(1);
    controller.setHorizonResponses(
        new StepResponse(mv.getName(), cv.getName(), new double[] {1}, new double[] {0}, 1, 0, 1, "", ""),
        new StepResponse(dv.getName(), cv.getName(), new double[] {1, 2, 3}, new double[] {0, 1, 3}, 1, 0, 1, "", ""));
    controller.calculate(new double[][] {{22}});
    assertTrajectory(controller.getLastHorizonPlan(), 10, 12, 16);
    controller.applyMoves();
    dv.setCurrentValue(22);
    controller.calculate();
    assertTrajectory(controller.getLastHorizonPlan(), 12, 16, 16);
    controller.applyMoves();
    cv.setCurrentValue(12);
    controller.calculate();
    assertTrajectory(controller.getLastHorizonPlan(), 16, 16, 16);
    assertThrows(IllegalArgumentException.class, () -> controller.calculate(new double[][] {{Double.NaN}}));
    assertNull(controller.getLastMoves());
    assertThrows(IllegalStateException.class, controller::applyMoves);
  }

  /**
   * Compare the single CV prediction with the known step response.
   *
   * @param plan successful scan
   * @param expected expected trajectory
   */
  private static void assertTrajectory(HorizonPlan plan, double... expected) {
    double[][] predicted = plan.getPredictedCvs();
    assertEquals(expected.length, predicted.length);
    for (int t = 0; t < expected.length; t++) {
      assertEquals(expected[t], predicted[t][0], 1e-7);
    }
  }
}
