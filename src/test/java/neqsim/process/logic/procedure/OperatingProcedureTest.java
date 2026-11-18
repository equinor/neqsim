package neqsim.process.logic.procedure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.TransientStepIdentifier;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveTravelModel;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.LogicAction;
import neqsim.process.logic.action.SetValveTargetAction;
import neqsim.process.logic.condition.PressureCondition;
import neqsim.process.logic.condition.ValvePositionCondition;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Quantifies executable operating procedures against native valve actuator dynamics.
 *
 * @author ESOL
 * @version 1.0
 */
class OperatingProcedureTest {
  /** Physical process fixture used by one procedure replay. */
  private static final class Fixture {
    private final ProcessSystem process;
    private final Stream feed;
    private final ThrottlingValve valve;

    /**
     * Creates a procedure fixture.
     *
     * @param process configured process
     * @param feed feed stream used by permissives
     * @param valve physical final element
     */
    private Fixture(ProcessSystem process, Stream feed, ThrottlingValve valve) {
      this.process = process;
      this.feed = feed;
      this.valve = valve;
    }
  }

  /** Counts target-command invocations while retaining the production action behavior. */
  private static final class CountingValveTargetAction implements LogicAction {
    private final SetValveTargetAction delegate;
    private int executionCount;

    /**
     * Creates a counted valve target command.
     *
     * @param valve physical valve
     * @param targetOpening requested target in percent
     * @param completionTolerance measured tolerance in percentage points
     */
    private CountingValveTargetAction(ThrottlingValve valve, double targetOpening, double completionTolerance) {
      delegate = new SetValveTargetAction(valve, targetOpening, completionTolerance);
    }

    /** {@inheritDoc} */
    @Override
    public void execute() {
      executionCount++;
      delegate.execute();
    }

    /** {@inheritDoc} */
    @Override
    public void reset() {
      executionCount = 0;
      delegate.reset();
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
      return delegate.getDescription();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isComplete() {
      return delegate.isComplete();
    }

    /** {@inheritDoc} */
    @Override
    public String getTargetName() {
      return delegate.getTargetName();
    }

    /** {@inheritDoc} */
    @Override
    public List<ProcessEquipmentInterface> getTargetEquipment() {
      return delegate.getTargetEquipment();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTargetEquipmentIdentityComplete() {
      return delegate.isTargetEquipmentIdentityComplete();
    }

    /**
     * Returns the number of command invocations.
     *
     * @return execution count
     */
    private int getExecutionCount() {
      return executionCount;
    }
  }

  /** A four-second valve stroke completes on measured feedback and replays identically. */
  @Test
  void physicalValveProcedureCompletesAndReplaysDeterministically() {
    Fixture firstFixture = fixture(0.0, 4.0, 0.0);
    OperatingProcedure first = openingProcedure(firstFixture, CommandKind.AUTOMATIC);
    OperatingProcedure.ExecutionResult firstResult = OperatingProcedureRunner.run(firstFixture.process, first, 0.5, 8.0,
        "procedure-open-replay");

    assertEquals(LogicState.COMPLETED, first.getState(), firstResult.toJson());
    assertEquals(100.0, firstFixture.valve.getPercentValveOpening(), 1.0e-12);
    assertEvent(first.getEvents(), OperatingProcedure.EventType.COMMAND_ISSUED, 0.0);
    assertEvent(first.getEvents(), OperatingProcedure.EventType.STEP_COMPLETED, 4.0);

    Fixture secondFixture = fixture(0.0, 4.0, 0.0);
    OperatingProcedure second = openingProcedure(secondFixture, CommandKind.AUTOMATIC);
    OperatingProcedure.ExecutionResult secondResult = OperatingProcedureRunner.run(secondFixture.process, second, 0.5,
        8.0, "procedure-open-replay");

    assertEquals(firstResult.toJson(), secondResult.toJson());

    Fixture fineFixture = fixture(0.0, 4.0, 0.0);
    OperatingProcedure fine = openingProcedure(fineFixture, CommandKind.AUTOMATIC);
    OperatingProcedureRunner.run(fineFixture.process, fine, 0.25, 8.0, "procedure-open-fine-step");
    assertEquals(LogicState.COMPLETED, fine.getState(), fine.snapshot().toJson());
    assertEquals(100.0, fineFixture.valve.getPercentValveOpening(), 1.0e-12);
    assertEvent(fine.getEvents(), OperatingProcedure.EventType.STEP_COMPLETED, 4.0);
  }

  /** Stiction holds the stem, reaches the declared timeout and executes the recovery action. */
  @Test
  void stuckValveAbortsWithFirstOutAndRecovery() {
    Fixture fixture = fixture(0.0, 4.0, 150.0);
    OperatingProcedure procedure = new OperatingProcedure("OPS-STUCK-001", "A", "Stuck actuator recovery");
    procedure.addPrerequisite(new PressureCondition(fixture.feed, 10.0, ">"));
    procedure.addAutomaticStep("OPEN-SDV", null, new SetValveTargetAction(fixture.valve, 100.0, 0.5),
        new ValvePositionCondition(fixture.valve, ">=", 99.5, 0.5), 2.0,
        new SetValveTargetAction(fixture.valve, 0.0, 0.5));

    OperatingProcedureRunner.run(fixture.process, procedure, 0.5, 5.0, "procedure-stuck-replay");

    assertEquals(LogicState.FAILED, procedure.getState());
    assertEquals("OPEN-SDV", procedure.getFirstOutStepId());
    assertEquals("Step completion timeout", procedure.getFailureReason());
    assertEquals(0.0, fixture.valve.getPercentValveOpening(), 1.0e-12);
    assertEvent(procedure.getEvents(), OperatingProcedure.EventType.RECOVERY_COMMAND, 2.0);
    assertEvent(procedure.getEvents(), OperatingProcedure.EventType.ABORTED, 2.0);
  }

  /** An inhibited restart records operator wait and fails without issuing the opening command. */
  @Test
  void inhibitedOperatorRestartTimesOutWithoutMovingValve() {
    Fixture fixture = fixture(0.0, 4.0, 0.0);
    OperatingProcedure procedure = openingProcedure(fixture, CommandKind.OPERATOR);
    procedure.activate();
    procedure.execute(0.0);
    for (int index = 0; index < 4 && procedure.isActive(); index++) {
      fixture.process.runTransient(0.5, TransientStepIdentifier.deterministicPhysicalStep("operator-inhibited", index));
      procedure.execute(0.5);
    }

    assertEquals(LogicState.FAILED, procedure.getState());
    assertEquals(0.0, fixture.valve.getPercentValveOpening(), 1.0e-12);
    assertEvent(procedure.getEvents(), OperatingProcedure.EventType.WAITING_OPERATOR, 0.0);
    assertFalse(hasEvent(procedure.getEvents(), OperatingProcedure.EventType.COMMAND_ISSUED));
  }

  /** An authorized operator step issues a command and completes on subsequent measured feedback. */
  @Test
  void authorizedOperatorStepCompletesOnMeasuredFeedback() {
    Fixture fixture = fixture(0.0, 4.0, 0.0);
    OperatingProcedure procedure = new OperatingProcedure("OPS-START-001", "A", "Open inlet isolation");
    procedure.addPrerequisite(new PressureCondition(fixture.feed, 10.0, ">"));
    procedure.addOperatorStep("OPEN-SDV", null, new SetValveTargetAction(fixture.valve, 100.0, 0.5),
        new ValvePositionCondition(fixture.valve, ">=", 99.5, 0.5), 5.0,
        new SetValveTargetAction(fixture.valve, 0.0, 0.5));
    assertThrows(IllegalStateException.class, () -> procedure.authorizeOperatorStep("OPEN-SDV"));
    procedure.activate();
    procedure.execute(0.0);
    assertThrows(IllegalStateException.class, () -> procedure.authorizeOperatorStep("WRONG-STEP"));
    procedure.authorizeOperatorStep("OPEN-SDV");
    procedure.execute(0.0);
    for (int index = 0; index < 8 && procedure.isActive(); index++) {
      fixture.process.runTransient(0.5,
          TransientStepIdentifier.deterministicPhysicalStep("operator-authorized", index));
      procedure.execute(0.5);
    }

    assertEquals(LogicState.COMPLETED, procedure.getState(), procedure.snapshot().toJson());
    assertEquals(100.0, fixture.valve.getPercentValveOpening(), 1.0e-12);
    assertEvent(procedure.getEvents(), OperatingProcedure.EventType.OPERATOR_AUTHORIZED, 0.0);
    assertEvent(procedure.getEvents(), OperatingProcedure.EventType.COMMAND_ISSUED, 0.0);
    assertEvent(procedure.getEvents(), OperatingProcedure.EventType.STEP_COMPLETED, 4.0);
  }

  /** A slow physical stroke does not cause the procedure scan to reissue its command. */
  @Test
  void automaticCommandIsIssuedOnceWhileFeedbackTravels() {
    Fixture fixture = fixture(0.0, 4.0, 0.0);
    CountingValveTargetAction action = new CountingValveTargetAction(fixture.valve, 100.0, 0.5);
    OperatingProcedure procedure = new OperatingProcedure("OPS-ONESHOT-001", "A", "One-shot command");
    procedure.addAutomaticStep("OPEN-SDV", null, action, new ValvePositionCondition(fixture.valve, ">=", 99.5, 0.5),
        5.0, null);

    OperatingProcedureRunner.run(fixture.process, procedure, 0.5, 8.0, "procedure-one-shot");

    assertEquals(LogicState.COMPLETED, procedure.getState(), procedure.snapshot().toJson());
    assertEquals(1, action.getExecutionCount());
  }

  /** Creates a gas valve process with declared actuator travel and stiction. */
  private static Fixture fixture(double initialOpening, double travelTimeSeconds, double stictionPercent) {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 20.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("20-FEED-001", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    ThrottlingValve valve = new ThrottlingValve("20-SDV-001", feed);
    valve.setCv(30.0);
    valve.setOutletPressure(10.0, "bara");
    valve.setPercentValveOpening(initialOpening);
    valve.setTargetPercentValveOpening(initialOpening);
    valve.setTravelModel(ValveTravelModel.LINEAR_RATE_LIMIT);
    valve.setTravelTime(travelTimeSeconds);
    valve.setValveStiction(stictionPercent);
    valve.setPhysicalStepIdempotencyEnabled(true);
    ProcessSystem process = new ProcessSystem("procedure-process");
    process.add(feed);
    process.add(valve);
    process.run();
    valve.setCalculateSteadyState(false);
    return new Fixture(process, feed, valve);
  }

  /** Distinguishes automatic and operator-owned test configurations. */
  private enum CommandKind {
    /** Procedure issues the command. */
    AUTOMATIC,
    /** Operator authorization is required. */
    OPERATOR
  }

  /** Creates the standard opening procedure. */
  private static OperatingProcedure openingProcedure(Fixture fixture, CommandKind commandKind) {
    OperatingProcedure procedure = new OperatingProcedure("OPS-START-001", "A", "Open inlet isolation");
    procedure.addPrerequisite(new PressureCondition(fixture.feed, 10.0, ">"));
    SetValveTargetAction action = new SetValveTargetAction(fixture.valve, 100.0, 0.5);
    ValvePositionCondition completion = new ValvePositionCondition(fixture.valve, ">=", 99.5, 0.5);
    if (commandKind == CommandKind.OPERATOR) {
      procedure.addOperatorStep("OPEN-SDV", null, action, completion, 2.0,
          new SetValveTargetAction(fixture.valve, 0.0, 0.5));
    } else {
      procedure.addAutomaticStep("OPEN-SDV", null, action, completion, 5.0,
          new SetValveTargetAction(fixture.valve, 0.0, 0.5));
    }
    return procedure;
  }

  /** Asserts one event type at an exact scan time. */
  private static void assertEvent(List<OperatingProcedure.ExecutionEvent> events, OperatingProcedure.EventType type,
      double timeSeconds) {
    assertTrue(events.stream()
        .anyMatch(event -> event.getType() == type && Math.abs(event.getTimeSeconds() - timeSeconds) < 1.0e-12));
  }

  /** Reports whether an event list contains a type. */
  private static boolean hasEvent(List<OperatingProcedure.ExecutionEvent> events, OperatingProcedure.EventType type) {
    return events.stream().anyMatch(event -> event.getType() == type);
  }
}
