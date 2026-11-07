package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies algebraic evaluations do not consume a positive physical actuator interval.
 *
 * @author NeqSim study validation
 * @version 1.0
 */
class ValveZeroDurationUuidTest {
  /**
   * Builds a native gas valve with a five-second physical stroke.
   *
   * @return initialized finite-travel valve
   */
  private ThrottlingValve valve() {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("FEED", fluid);
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();
    ThrottlingValve valve = new ThrottlingValve("FINITE-VALVE", inlet);
    valve.setCv(10.0);
    valve.setOutletPressure(5.0);
    valve.setCalculateSteadyState(false);
    valve.setTravelModel(ValveTravelModel.LINEAR_RATE_LIMIT);
    valve.setTravelTime(5.0);
    valve.setPercentValveOpening(0.0);
    valve.setTargetPercentValveOpening(100.0);
    valve.setPhysicalStepIdempotencyEnabled(true);
    return valve;
  }

  /** Checks actual actuator movement and replay after a zero-time command evaluation. */
  @Test
  void zeroCommandEvaluationDoesNotConsumePositiveInterval() {
    ThrottlingValve valve = valve();
    UUID id = UUID.randomUUID();
    valve.runController(0.0, id);
    assertEquals(0.0, valve.getPercentValveOpening(), 0.0);
    valve.runController(0.1, id);
    assertEquals(2.0, valve.getPercentValveOpening(), 1.0e-12);
    valve.runController(0.1, id);
    assertEquals(2.0, valve.getPercentValveOpening(), 1.0e-12);
  }

  /** Checks native hydraulic evaluation before and after the one positive interval. */
  @Test
  void zeroHydraulicEvaluationRetainsLaterPhysicalMovement() {
    ThrottlingValve valve = valve();
    UUID id = UUID.randomUUID();
    valve.runTransient(0.0, id);
    assertEquals(0.0, valve.getOutletStream().getFlowRate("mole/sec"), 0.0);
    valve.runTransient(0.1, id);
    assertEquals(2.0, valve.getPercentValveOpening(), 1.0e-12);
    assertTrue(valve.getOutletStream().getFlowRate("mole/sec") > 0.0);
    valve.runTransient(0.0, id);
    assertEquals(2.0, valve.getPercentValveOpening(), 1.0e-12);
  }
}
