package neqsim.process.logic.hipps;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.valve.HIPPSValve;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.sis.Detector;
import neqsim.process.logic.sis.VotingLogic;

/**
 * Current-scan demand, degraded voting and measured final-element travel for a synthetic HIPPS.
 * 
 * @author NeqSim contributors
 * @version 1.0
 */
class HIPPSCommissioningTest {
  /**
   * Creates a three-channel pressure vote at 29 bara.
   * 
   * @return configured solver
   */
  private HIPPSLogic solver() {
    HIPPSLogic logic = new HIPPSLogic("HIPPS-101", VotingLogic.TWO_OUT_OF_THREE);
    for (String name : new String[] {"A", "B", "C"}) {
      logic.addPressureSensor(
          new Detector(name, Detector.DetectorType.PRESSURE, Detector.AlarmLevel.HIGH_HIGH, 29, "bara"));
    }
    return logic;
  }

  /** Sequential single-channel disturbances cannot combine into one vote, and escalation needs sustained demand. */
  @Test
  void evaluatesCurrentSamplesAndContinuousEscalation() {
    HIPPSLogic logic = solver();
    ESDLogic esd = new ESDLogic("ESD");
    logic.linkToEscalationLogic(esd, 2);
    logic.update(30, 26, 26);
    logic.update(26, 30, 26);
    assertFalse(logic.isTripped());
    logic.update(30, 30, 26);
    logic.execute(1.5);
    assertTrue(logic.isTripped());
    logic.update(26, 26, 26);
    logic.execute(3);
    assertFalse(logic.hasEscalated());
    logic.update(30, 30, 26);
    logic.execute(1.5);
    assertFalse(logic.hasEscalated());
    logic.execute(.5);
    assertTrue(logic.hasEscalated());
    assertTrue(esd.isActive());
  }

  /** Treat a bad and bypassed reading on one detector as a single unavailable channel. */
  @Test
  void separatesLossOfVotingCapacityFromProcessDemand() {
    HIPPSLogic logic = solver();
    logic.getPressureSensor(0).setBypass(true);
    logic.getPressureSensor(0).setFaulty(true);
    logic.update(Double.NaN, 26, 26);
    assertEquals(LogicState.IDLE, logic.getState());
    logic.update(Double.NaN, Double.POSITIVE_INFINITY, 26);
    assertEquals(LogicState.FAILED, logic.getState());
    assertFalse(logic.isTripped());
    logic.setTripOnLostVotingCapacity(true);
    logic.update(Double.NaN, Double.POSITIVE_INFINITY, 26);
    assertTrue(logic.isTripped());
    assertFalse(logic.reset());
    logic.getPressureSensor(0).setBypass(false);
    logic.getPressureSensor(0).setFaulty(false);
    logic.update(26, 26, 26);
    assertTrue(logic.reset());
  }

  /** Each actuator owns its travel; a safety trip interrupts a partial stroke and survives a reopen command. */
  @Test
  void tripsTwoActuatorsAndRequiresSeparateRestart() {
    HIPPSLogic logic = solver();
    HIPPSValve first = new HIPPSValve("XV-1");
    HIPPSValve second = new HIPPSValve("XV-2");
    first.setCalculateSteadyState(false);
    second.setCalculateSteadyState(false);
    logic.setIsolationValve(first);
    logic.addIsolationValve(second);
    logic.setValveClosureTime(2);
    second.setClosureTime(4);
    first.performPartialStrokeTest(.15);
    logic.update(30, 30, 26);
    assertFalse(first.isPartialStrokeTestActive());
    assertEquals(100, first.getPercentValveOpening(), 0);
    assertEquals(0, first.getTargetPercentValveOpening(), 0);
    assertThrows(IllegalStateException.class, () -> first.performPartialStrokeTest(.15));
    first.setTargetPercentValveOpening(100);
    first.setPercentValveOpening(100);
    first.runController(1, UUID.randomUUID());
    second.runController(1, UUID.randomUUID());
    assertEquals(50, first.getPercentValveOpening(), 0);
    assertEquals(75, second.getPercentValveOpening(), 0);
    assertFalse(logic.reset());
    first.runController(3, UUID.randomUUID());
    second.runController(3, UUID.randomUUID());
    logic.update(26, 26, 26);
    assertTrue(logic.reset());
    assertEquals(0, first.getPercentValveOpening(), 0);
    assertEquals(0, second.getTargetPercentValveOpening(), 0);
    assertFalse(second.hasTripped());
  }
}
