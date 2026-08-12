package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.Serializable;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.dynamics.EventScheduler;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.dynamics.TransientStepIdentifier;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.dynamics.TransientTransactionCoverage;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

/**
 * Quantitative identity, rollback, replay, and multi-area tests for transient step transactions.
 */
public class TransientStepTransactionTest extends neqsim.NeqSimTest {
  private static final double TOLERANCE = 1.0e-12;

  /**
   * A failed physical step restores participant, process-clock, calculation-identifier, and event bookkeeping state.
   * Replaying the same physical-step identifier then matches a clean run.
   */
  @Test
  void failedStepRollsBackInPlaceAndReplaysDeterministically() {
    StatefulTestUnit rejectedUnit = new StatefulTestUnit("state", "area/state", 2.0);
    rejectedUnit.setFailAfterMutation(true);
    ProcessSystem rejectedProcess = createProcess("rejected", rejectedUnit);
    EventScheduler rejectedScheduler = scheduleIncrement(rejectedProcess, rejectedUnit, 3.0);
    UUID physicalStepId = TransientStepIdentifier.deterministicPhysicalStep("transaction-replay", 0L);

    RuntimeException failure = assertThrows(RuntimeException.class,
        () -> rejectedProcess.runTransientTransactional(1.0, physicalStepId));
    assertEquals("intentional trial failure", failure.getMessage());
    assertSame(rejectedUnit, rejectedProcess.getUnitOperations().get(0));
    assertEquals(0.0, rejectedUnit.getValue(), TOLERANCE);
    assertEquals(0.0, rejectedUnit.getTime(), TOLERANCE);
    assertEquals(0.0, rejectedProcess.getTime(), TOLERANCE);
    assertEquals(0, rejectedProcess.getHistorySize());
    assertEquals(1, rejectedScheduler.getPendingEvents().size());
    assertEquals(0, rejectedScheduler.getFiredEvents().size());
    assertNull(rejectedProcess.getCalculationIdentifier());

    rejectedUnit.setFailAfterMutation(false);
    rejectedProcess.runTransientTransactional(1.0, physicalStepId);

    StatefulTestUnit cleanUnit = new StatefulTestUnit("state", "area/state", 2.0);
    ProcessSystem cleanProcess = createProcess("clean", cleanUnit);
    EventScheduler cleanScheduler = scheduleIncrement(cleanProcess, cleanUnit, 3.0);
    cleanProcess.runTransientTransactional(1.0, physicalStepId);

    assertEquals(cleanUnit.getValue(), rejectedUnit.getValue(), TOLERANCE);
    assertEquals(cleanUnit.getTime(), rejectedUnit.getTime(), TOLERANCE);
    assertEquals(cleanProcess.getTime(), rejectedProcess.getTime(), TOLERANCE);
    assertEquals(cleanProcess.getCalculationIdentifier(), rejectedProcess.getCalculationIdentifier());
    assertEquals(0, rejectedScheduler.getPendingEvents().size());
    assertEquals(1, rejectedScheduler.getFiredEvents().size());
    assertEquals(0, cleanScheduler.getPendingEvents().size());
    assertEquals(1, cleanScheduler.getFiredEvents().size());
    assertEquals(5.0, rejectedUnit.getValue(), TOLERANCE);
  }

  /**
   * A later-area failure restores every earlier area and the one shared scheduler.
   */
  @Test
  void processModelRollbackIsCoordinatedAcrossAreas() {
    StatefulTestUnit firstUnit = new StatefulTestUnit("first", "model/first", 1.0);
    StatefulTestUnit secondUnit = new StatefulTestUnit("second", "model/second", 2.0);
    secondUnit.setFailAfterMutation(true);
    ProcessSystem firstArea = createProcess("first-area", firstUnit);
    ProcessSystem secondArea = createProcess("second-area", secondUnit);
    ProcessModel model = new ProcessModel();
    model.add("first-area", firstArea);
    model.add("second-area", secondArea);
    EventScheduler scheduler = new EventScheduler();
    scheduler.scheduleEvent(1.0, "first-area-event", () -> firstUnit.addValue(10.0));
    model.setEventScheduler(scheduler);
    UUID physicalStepId = TransientStepIdentifier.deterministicPhysicalStep("model-transaction", 0L);

    assertThrows(RuntimeException.class, () -> model.runTransientTransactional(1.0, physicalStepId));
    assertSame(firstArea, model.get("first-area"));
    assertSame(secondArea, model.get("second-area"));
    assertSame(firstUnit, firstArea.getUnitOperations().get(0));
    assertSame(secondUnit, secondArea.getUnitOperations().get(0));
    assertEquals(0.0, firstUnit.getValue(), TOLERANCE);
    assertEquals(0.0, secondUnit.getValue(), TOLERANCE);
    assertEquals(0.0, firstArea.getTime(), TOLERANCE);
    assertEquals(0.0, secondArea.getTime(), TOLERANCE);
    assertEquals(1, scheduler.getPendingEvents().size());
    assertEquals(0, scheduler.getFiredEvents().size());

    secondUnit.setFailAfterMutation(false);
    model.runTransientTransactional(1.0, physicalStepId);
    assertEquals(11.0, firstUnit.getValue(), TOLERANCE);
    assertEquals(2.0, secondUnit.getValue(), TOLERANCE);
    assertEquals(1.0, firstArea.getTime(), TOLERANCE);
    assertEquals(1.0, secondArea.getTime(), TOLERANCE);
    assertEquals(0, scheduler.getPendingEvents().size());
    assertEquals(1, scheduler.getFiredEvents().size());
  }

  /**
   * A later-area commit-contract failure is detected before any earlier child transaction is committed.
   */
  @Test
  void processModelPreparesEveryAreaBeforeCommittingAnyChild() {
    StatefulTestUnit firstUnit = new StatefulTestUnit("first", "prepare/first", 1.0);
    StatefulTestUnit secondUnit = new StatefulTestUnit("second", "prepare/second", 1.0);
    ProcessModel model = new ProcessModel();
    model.add("first-area", createProcess("first-area", firstUnit));
    model.add("second-area", createProcess("second-area", secondUnit));

    TransientStepTransaction transaction = model.beginTransientStepTransaction();
    firstUnit.addValue(4.0);
    secondUnit.addValue(7.0);
    secondUnit.setStateIdentity("prepare/changed");

    IllegalStateException failure = assertThrows(IllegalStateException.class, transaction::commit);
    assertTrue(failure.getMessage().contains("second-area"));
    assertEquals(TransientStepTransaction.Status.ROLLED_BACK, transaction.getStatus());
    assertEquals(0.0, firstUnit.getValue(), TOLERANCE);
    assertEquals(0.0, secondUnit.getValue(), TOLERANCE);
    assertEquals("prepare/first", firstUnit.getTransientStateIdentity());
    assertEquals("prepare/second", secondUnit.getTransientStateIdentity());
  }

  /**
   * Coverage diagnostics are quantitative and reject unsupported or ambiguous state before mutation.
   */
  @Test
  void incompleteAndDuplicateCoverageFailBeforeTrialMutation() {
    ProcessSystem unsupported = new ProcessSystem("unsupported");
    unsupported.add(new NonTransactionalTestUnit("legacy"));
    TransientTransactionCoverage unsupportedCoverage = unsupported.getTransientTransactionCoverage();
    assertEquals(1, unsupportedCoverage.getProcessElementCount());
    assertEquals(0, unsupportedCoverage.getParticipantCount());
    assertFalse(unsupportedCoverage.isComplete());
    assertTrue(unsupportedCoverage.getBlockingIssues().get(0).contains("does not implement"));
    assertThrows(IllegalStateException.class, unsupported::beginTransientStepTransaction);
    assertEquals(0.0, unsupported.getTime(), TOLERANCE);

    ProcessSystem duplicate = new ProcessSystem("duplicate");
    duplicate.add(new StatefulTestUnit("one", "duplicate-state", 1.0));
    duplicate.add(new StatefulTestUnit("two", "duplicate-state", 1.0));
    TransientTransactionCoverage duplicateCoverage = duplicate.getTransientTransactionCoverage();
    assertEquals(2, duplicateCoverage.getProcessElementCount());
    assertEquals(2, duplicateCoverage.getParticipantCount());
    assertFalse(duplicateCoverage.isComplete());
    assertTrue(duplicateCoverage.getBlockingIssues().get(0).contains("is shared by"));
    assertThrows(IllegalStateException.class, duplicate::beginTransientStepTransaction);
    assertEquals(0.0, duplicate.getTime(), TOLERANCE);

    StatefulTestUnit controlledUnit = new StatefulTestUnit("controlled", "controlled-state", 1.0);
    controlledUnit.setController(new ControllerDeviceBaseClass("attached-controller"));
    ProcessSystem attachedController = createProcess("attached-controller", controlledUnit);
    TransientTransactionCoverage attachedCoverage = attachedController.getTransientTransactionCoverage();
    assertEquals(2, attachedCoverage.getProcessElementCount());
    assertEquals(1, attachedCoverage.getParticipantCount());
    assertFalse(attachedCoverage.isComplete());
    assertTrue(attachedCoverage.getBlockingIssues().get(0).contains("attached-controller"));
    assertThrows(IllegalStateException.class, attachedController::beginTransientStepTransaction);
    assertEquals(0.0, attachedController.getTime(), TOLERANCE);
  }

  /**
   * Commit retains state, close rolls open state back, and closed transactions are excluded from serialized restart
   * state.
   */
  @Test
  void commitCloseAndSerializationHaveSingleUseSemantics() {
    StatefulTestUnit unit = new StatefulTestUnit("state", "lifecycle/state", 1.0);
    ProcessSystem process = createProcess("lifecycle", unit);

    try (TransientStepTransaction transaction = process.beginTransientStepTransaction()) {
      unit.addValue(4.0);
    }
    assertEquals(0.0, unit.getValue(), TOLERANCE);

    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    unit.addValue(6.0);
    transaction.commit();
    assertEquals(TransientStepTransaction.Status.COMMITTED, transaction.getStatus());
    assertEquals(6.0, unit.getValue(), TOLERANCE);
    assertThrows(IllegalStateException.class, transaction::rollback);

    ProcessSystem restarted = process.copy();
    assertNotSame(process, restarted);
    StatefulTestUnit restartedUnit = (StatefulTestUnit) restarted.getUnitOperations().get(0);
    assertNotSame(unit, restartedUnit);
    assertEquals(6.0, restartedUnit.getValue(), TOLERANCE);
    assertTrue(restarted.getTransientTransactionCoverage().isComplete());
  }

  private static ProcessSystem createProcess(String name, StatefulTestUnit unit) {
    ProcessSystem process = new ProcessSystem(name);
    process.add(unit);
    return process;
  }

  private static EventScheduler scheduleIncrement(ProcessSystem process, StatefulTestUnit unit, double increment) {
    EventScheduler scheduler = new EventScheduler();
    scheduler.scheduleEvent(1.0, "participant-increment", () -> unit.addValue(increment));
    process.setEventScheduler(scheduler);
    return scheduler;
  }

  /** Stateful equipment used to exercise the public participant contract. */
  private static final class StatefulTestUnit extends ProcessEquipmentBaseClass
      implements TransientStateParticipant<StatefulTestUnit.Snapshot> {
    private static final long serialVersionUID = 1000L;
    private String stateIdentity;
    private final double rate;
    private double value;
    private boolean failAfterMutation;

    private StatefulTestUnit(String name, String stateIdentity, double rate) {
      super(name);
      this.stateIdentity = stateIdentity;
      this.rate = rate;
    }

    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    @Override
    public void runTransient(double dt, UUID id) {
      value += rate * dt;
      increaseTime(dt);
      setCalculationIdentifier(id);
      if (failAfterMutation) {
        throw new RuntimeException("intentional trial failure");
      }
    }

    @Override
    public String getTransientStateIdentity() {
      return stateIdentity;
    }

    @Override
    public Snapshot captureTransientState() {
      return new Snapshot(stateIdentity, value, getTime(), getCalculationIdentifier(), failAfterMutation);
    }

    @Override
    public void restoreTransientState(Snapshot snapshot) {
      stateIdentity = snapshot.stateIdentity;
      value = snapshot.value;
      setTime(snapshot.time);
      setCalculationIdentifier(snapshot.calculationIdentifier);
      failAfterMutation = snapshot.failAfterMutation;
    }

    private double getValue() {
      return value;
    }

    private void addValue(double increment) {
      value += increment;
    }

    private void setFailAfterMutation(boolean failAfterMutation) {
      this.failAfterMutation = failAfterMutation;
    }

    private void setStateIdentity(String stateIdentity) {
      this.stateIdentity = stateIdentity;
    }

    /** Immutable participant snapshot. */
    private static final class Snapshot implements Serializable {
      private static final long serialVersionUID = 1000L;
      private final String stateIdentity;
      private final double value;
      private final double time;
      private final UUID calculationIdentifier;
      private final boolean failAfterMutation;

      private Snapshot(String stateIdentity, double value, double time, UUID calculationIdentifier,
          boolean failAfterMutation) {
        this.stateIdentity = stateIdentity;
        this.value = value;
        this.time = time;
        this.calculationIdentifier = calculationIdentifier;
        this.failAfterMutation = failAfterMutation;
      }
    }
  }

  /** Legacy element used to prove fail-before-mutation coverage handling. */
  private static final class NonTransactionalTestUnit extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;

    private NonTransactionalTestUnit(String name) {
      super(name);
    }

    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }
  }
}
