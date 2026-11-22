package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.dynamics.AdaptiveTransientOptions;
import neqsim.process.dynamics.AdaptiveTransientResult;
import neqsim.process.dynamics.EventScheduler;
import neqsim.process.dynamics.TransientErrorStateBinding;
import neqsim.process.dynamics.TransientErrorStateProvider;
import neqsim.process.dynamics.TransientErrorStateVariable;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.dynamics.TransientStepIdentifier;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

/**
 * Analytical, conservative, replay, event-time and rejection qualification of opt-in step doubling.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class AdaptiveTransientStepTest extends neqsim.NeqSimTest {
  /** Floating-point comparison tolerance for these small analytical fixtures. */
  private static final double ROUND_OFF = 1.0e-12;
  /** Logger for the opt-in synthetic coordinator-cost report. */
  private static final Logger logger = LogManager.getLogger(AdaptiveTransientStepTest.class);

  /** Tight tolerance rejects the full step and retains exactly the accepted pair of half steps. */
  @Test
  void errorControlledStepRejectsAndAcceptsConservativeTwoHalfStepState() {
    Study study = new Study("inventory", 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 16);
    UUID parent = step("rejection", 0);

    AdaptiveTransientResult result = study.process.runTransientAdaptive(1.0, parent, study.options);

    double accepted = result.getAcceptedStepSeconds();
    assertTrue(accepted > 0.0 && accepted < 1.0);
    assertTrue(result.getRejectedAttempts() > 0);
    assertEquals(result.getRejectedAttempts() + 1, result.getAttempts());
    assertTrue(result.getNormalizedError() <= 1.0);
    assertEquals(Math.pow(1.0 - accepted / 2.0, 2.0), study.unit.source, ROUND_OFF);
    assertEquals(1.0, study.unit.source + study.unit.receiver, ROUND_OFF);
    assertEquals(accepted, study.process.getTime(), ROUND_OFF);
    assertEquals(accepted, study.unit.getTime(), ROUND_OFF);
    assertEquals(2, study.unit.acceptedIds.size());
    assertEquals(result.getFirstHalfStepIdentifier(), study.unit.acceptedIds.get(0));
    assertEquals(result.getSecondHalfStepIdentifier(), study.unit.acceptedIds.get(1));
    assertEquals(parent, result.getPhysicalStepIdentifier());
    assertNotEquals(parent, result.getFirstHalfStepIdentifier());
    assertNotEquals(result.getFirstHalfStepIdentifier(), result.getSecondHalfStepIdentifier());
    assertNotNull(result.getLimitingVariable());
    assertSame(study.unit, study.process.getUnitOperations().get(0));
    assertTrue(result.getSuggestedNextStepSeconds() >= 1.0e-6);
    assertTrue(result.getSuggestedNextStepSeconds() <= 1.0);
  }

  /** Euler step doubling converges at its expected first order against the independently known exponential. */
  @Test
  void timestepRefinementConvergesToAnalyticalInventoryTransfer() {
    Study coarse = integrateInventory(0.25);
    Study fine = integrateInventory(0.125);
    double exact = Math.exp(-1.0);
    double coarseError = Math.abs(coarse.unit.source - exact);
    double fineError = Math.abs(fine.unit.source - exact);

    assertEquals(Math.pow(0.875, 8), coarse.unit.source, ROUND_OFF);
    assertEquals(Math.pow(0.9375, 16), fine.unit.source, ROUND_OFF);
    assertTrue(coarseError < 0.025);
    assertTrue(fineError < 0.51 * coarseError);
    assertEquals(1.0, coarse.unit.source + coarse.unit.receiver, ROUND_OFF);
    assertEquals(1.0, fine.unit.source + fine.unit.receiver, ROUND_OFF);
    assertEquals(1.0, coarse.process.getTime(), ROUND_OFF);
    assertEquals(1.0, fine.process.getTime(), ROUND_OFF);
  }

  /** The requested interval is an upper bound even when configured minimum steps are larger. */
  @Test
  void requestedStepIsNeverEnlarged() {
    Study study = new Study("bounded", 1.0, true, 1.0e-3, 0.0, 0.5, 2.0, 8);

    try {
      AdaptiveTransientResult result = study.process.runTransientAdaptive(0.1, step("small-request", 0), study.options);
      assertTrue(result.getAcceptedStepSeconds() <= 0.1);
      assertEquals(result.getAcceptedStepSeconds(), study.process.getTime(), ROUND_OFF);
    } catch (IllegalArgumentException ex) {
      assertEquals(0.0, study.process.getTime(), ROUND_OFF);
      assertEquals(0.0, study.unit.getTime(), ROUND_OFF);
      assertTrue(study.unit.acceptedIds.isEmpty());
    }
  }

  /** Exhausting the caller's retry budget restores all inventory, IDs, clocks and history. */
  @Test
  void retryExhaustionRestoresOriginalStateAndAllowsReplay() {
    Study study = new Study("exhaustion", 1.0, false, 1.0e-8, 0.0, 0.5, 1.0, 1);
    double previousTimeStep = study.process.getTimeStep();

    neqsim.process.dynamics.AdaptiveTransientException failure = assertThrows(
        neqsim.process.dynamics.AdaptiveTransientException.class,
        () -> study.process.runTransientAdaptive(1.0, step("exhaustion", 0), study.options));
    assertEquals(neqsim.process.dynamics.AdaptiveTransientException.Reason.ATTEMPT_LIMIT, failure.getReason());
    assertEquals(1, failure.getAttempts());
    assertEquals(1.0, failure.getAttemptedStepSeconds(), 0.0);
    assertTrue(failure.getNormalizedError() > 1.0);
    assertTrue(failure.getLimitingVariable().contains("receiver"));

    assertInitialState(study);
    assertEquals(previousTimeStep, study.process.getTimeStep(), ROUND_OFF);
    try (TransientStepTransaction transaction = study.process.beginTransientStepTransaction()) {
      assertTrue(transaction.isOpen());
    }
    AdaptiveTransientOptions relaxed = options(study.unit, 1.0, 0.0, 0.5, 1.0, 1);
    AdaptiveTransientResult result = study.process.runTransientAdaptive(1.0, step("exhaustion", 0), relaxed);
    assertEquals(1.0, result.getAcceptedStepSeconds(), ROUND_OFF);
    assertEquals(0.25, study.unit.source, ROUND_OFF);
  }

  /** Coverage checks reject a missing numerical provider before advancing any process element. */
  @Test
  void everyParticipantRequiresExplicitNumericalCoverage() {
    Study study = new Study("covered", 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 8);
    TransferUnit uncovered = new TransferUnit("uncovered", "uncovered", 2.0, false);
    study.process.add(uncovered);

    assertThrows(RuntimeException.class,
        () -> study.process.runTransientAdaptive(0.25, step("missing-provider", 0), study.options));

    assertInitialState(study);
    assertEquals(0.0, uncovered.getTime(), ROUND_OFF);
    assertTrue(uncovered.acceptedIds.isEmpty());
  }

  /** Numerical coverage includes equipment-attached controllers even when they are not separately registered. */
  @Test
  void attachedControllerRequiresItsOwnNumericalProvider() {
    Study study = new Study("attached-control", 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 8);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("attached-controller");
    study.unit.setController(controller);
    assertTrue(study.process.getTransientTransactionCoverage().isComplete());

    assertThrows(RuntimeException.class,
        () -> study.process.runTransientAdaptive(0.25, step("attached-control", 0), study.options));

    assertInitialState(study);
  }

  /** A provider cannot substitute a different participant with an identical textual identity. */
  @Test
  void externalNumericalBindingDoesNotSatisfyRegisteredParticipantCoverage() {
    Study study = new Study("registered", 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 8);
    TransferUnit impostor = new TransferUnit("impostor", "registered", 1.0, false);
    AdaptiveTransientOptions unrelated = options(impostor, 1.0e-4, 0.0, 1.0e-6, 1.0, 8);

    assertThrows(RuntimeException.class,
        () -> study.process.runTransientAdaptive(0.25, step("impostor", 0), unrelated));

    assertInitialState(study);
    assertEquals(0.0, impostor.getTime(), ROUND_OFF);
  }

  /** Providers must retain the pre-step variable identities, units and numerical scales. */
  @Test
  void changingVectorSchemaDuringTrialFailsAndRestores() {
    for (int mutation = 1; mutation <= 3; mutation++) {
      Study study = new Study("schema-" + mutation, 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 8);
      study.unit.schemaMutation = mutation;

      assertThrows(RuntimeException.class,
          () -> study.process.runTransientAdaptive(0.25, step("schema", 0), study.options));

      assertInitialState(study);
      assertEquals(mutation, study.unit.schemaMutation);
    }
  }

  /** Non-finite numerical output cannot become an accepted dynamic state. */
  @Test
  void nonFiniteTrialStateIsRejectedAndRestored() {
    Study study = new Study("nonfinite", 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 4);
    study.unit.nonFiniteAfterMutation = true;

    assertThrows(RuntimeException.class,
        () -> study.process.runTransientAdaptive(0.25, step("nonfinite", 0), study.options));

    assertInitialState(study);
    assertTrue(study.unit.nonFiniteAfterMutation);
  }

  /** The complete accepted state and physical child identifiers replay exactly from identical initial data. */
  @Test
  void rejectedTrialsAndChildIdentifiersAreDeterministic() {
    Study first = new Study("replay", 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 16);
    Study second = new Study("replay", 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 16);
    UUID parent = step("repeat", 42);

    AdaptiveTransientResult one = first.process.runTransientAdaptive(1.0, parent, first.options);
    AdaptiveTransientResult two = second.process.runTransientAdaptive(1.0, parent, second.options);

    assertEquals(one.getAcceptedStepSeconds(), two.getAcceptedStepSeconds(), 0.0);
    assertEquals(one.getNormalizedError(), two.getNormalizedError(), 0.0);
    assertEquals(one.getAttempts(), two.getAttempts());
    assertEquals(one.getFirstHalfStepIdentifier(), two.getFirstHalfStepIdentifier());
    assertEquals(one.getSecondHalfStepIdentifier(), two.getSecondHalfStepIdentifier());
    assertEquals(first.unit.source, second.unit.source, 0.0);
    assertEquals(first.unit.receiver, second.unit.receiver, 0.0);
    assertEquals(first.unit.acceptedIds, second.unit.acceptedIds);
  }

  /**
   * Java restart preserves participant bindings and produces the same subsequent accepted trajectory.
   *
   * @throws Exception if Java serialization fails
   */
  @Test
  void serializedRestartMatchesUninterruptedExecution() throws Exception {
    Study uninterrupted = new Study("restart", 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 16);
    uninterrupted.process.runTransientAdaptive(1.0, step("restart", 0), uninterrupted.options);
    Study restarted = roundTrip(uninterrupted);

    AdaptiveTransientResult one = uninterrupted.process.runTransientAdaptive(0.5, step("restart", 1),
        uninterrupted.options);
    AdaptiveTransientResult two = restarted.process.runTransientAdaptive(0.5, step("restart", 1), restarted.options);

    assertSame(restarted.unit, restarted.process.getUnitOperations().get(0));
    assertEquals(uninterrupted.unit.source, restarted.unit.source, 0.0);
    assertEquals(uninterrupted.unit.receiver, restarted.unit.receiver, 0.0);
    assertEquals(uninterrupted.process.getTime(), restarted.process.getTime(), 0.0);
    assertEquals(one.getAcceptedStepSeconds(), two.getAcceptedStepSeconds(), 0.0);
    assertEquals(one.getNormalizedError(), two.getNormalizedError(), 0.0);
    assertEquals(one.getFirstHalfStepIdentifier(), two.getFirstHalfStepIdentifier());
    assertEquals(one.getSecondHalfStepIdentifier(), two.getSecondHalfStepIdentifier());
    AdaptiveTransientResult savedResult = roundTrip(one);
    assertEquals(one.getPhysicalStepIdentifier(), savedResult.getPhysicalStepIdentifier());
    assertEquals(one.getAcceptedStepSeconds(), savedResult.getAcceptedStepSeconds(), 0.0);
  }

  /** A failure in a later process area restores earlier areas and leaves no open transaction behind. */
  @Test
  void laterAreaFailureRollsBackEveryArea() {
    Study first = new Study("area-one", 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 8);
    Study second = new Study("area-two", 2.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 8);
    second.unit.failAfterMutation = true;
    ProcessModel model = new ProcessModel();
    model.add("first", first.process);
    model.add("second", second.process);
    AdaptiveTransientOptions options = combinedOptions(first.unit, second.unit);

    assertThrows(RuntimeException.class, () -> model.runTransientAdaptive(0.25, step("multi-fail", 0), options));

    assertInitialState(first);
    assertInitialState(second);
    try (TransientStepTransaction transaction = model.beginTransientStepTransaction()) {
      assertTrue(transaction.isOpen());
    }
  }

  /** The accepted common interval is governed by the most demanding area's state and conserves both inventories. */
  @Test
  void processModelAcceptsOneCoordinatedStepWithAreaQualifiedIdentities() {
    Study first = new Study("shared-local-id", 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 16);
    Study second = new Study("shared-local-id", 2.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 16);
    ProcessModel model = new ProcessModel();
    model.add("first", first.process);
    model.add("second", second.process);

    AdaptiveTransientResult result = model.runTransientAdaptive(0.5, step("multi-accept", 0),
        combinedOptions(first.unit, second.unit));

    double dt = result.getAcceptedStepSeconds();
    assertTrue(dt < 0.5);
    assertTrue(result.getNormalizedError() <= 1.0);
    assertEquals(dt, first.process.getTime(), ROUND_OFF);
    assertEquals(dt, second.process.getTime(), ROUND_OFF);
    assertEquals(Math.pow(1.0 - dt / 2.0, 2), first.unit.source, ROUND_OFF);
    assertEquals(Math.pow(1.0 - dt, 2), second.unit.source, ROUND_OFF);
    assertEquals(1.0, first.unit.source + first.unit.receiver, ROUND_OFF);
    assertEquals(1.0, second.unit.source + second.unit.receiver, ROUND_OFF);
    assertEquals(first.unit.acceptedIds, second.unit.acceptedIds);
  }

  /** Future events divide intervals and take effect at the following interval's start. */
  @Test
  void scheduledBoundaryDoesNotApplyActionRetroactively() {
    Study study = new Study("event-inventory", 1.0, true, 1.0e-3, 0.0, 1.0e-6, 2.0, 8);
    EventScheduler scheduler = new EventScheduler();
    scheduler.scheduleTransactionalEvent(0.5, "double-rate", () -> study.unit.rate = 2.0,
        study.unit.getTransientStateIdentity());
    study.process.setEventScheduler(scheduler);

    AdaptiveTransientResult before = study.process.runTransientAdaptive(1.0, step("event", 0), study.options);

    assertEquals(0.5, before.getAcceptedStepSeconds(), ROUND_OFF);
    assertEquals(0.5, study.unit.receiver, ROUND_OFF);
    assertEquals(1, scheduler.getPendingEvents().size());
    assertTrue(scheduler.getFiredEvents().isEmpty());
    AdaptiveTransientResult after = study.process.runTransientAdaptive(0.25, step("event", 1), study.options);
    assertEquals(0.25, after.getAcceptedStepSeconds(), ROUND_OFF);
    assertEquals(1.0, study.unit.receiver, ROUND_OFF);
    assertEquals(0.75, study.process.getTime(), ROUND_OFF);
    assertTrue(scheduler.getPendingEvents().isEmpty());
    assertEquals(1, scheduler.getFiredEvents().size());
  }

  /** A shared scheduler changes both areas at the same physical start time. */
  @Test
  void processModelSharesEventBoundaryAcrossAreas() {
    Study first = new Study("event-first", 1.0, true, 1.0e-3, 0.0, 1.0e-6, 2.0, 8);
    Study second = new Study("event-second", 2.0, true, 1.0e-3, 0.0, 1.0e-6, 2.0, 8);
    ProcessModel model = new ProcessModel();
    model.add("first", first.process);
    model.add("second", second.process);
    EventScheduler scheduler = new EventScheduler();
    scheduler.scheduleTransactionalEvent(0.5, "change-both", () -> {
      first.unit.rate = 3.0;
      second.unit.rate = 4.0;
    }, first.unit.getTransientStateIdentity(), second.unit.getTransientStateIdentity());
    model.setEventScheduler(scheduler);
    AdaptiveTransientOptions options = combinedOptions(first.unit, second.unit);

    assertEquals(0.5, model.runTransientAdaptive(1.0, step("shared-event", 0), options).getAcceptedStepSeconds(),
        ROUND_OFF);
    assertEquals(0.5, first.unit.receiver, ROUND_OFF);
    assertEquals(1.0, second.unit.receiver, ROUND_OFF);
    model.runTransientAdaptive(0.25, step("shared-event", 1), options);
    assertEquals(1.25, first.unit.receiver, ROUND_OFF);
    assertEquals(2.0, second.unit.receiver, ROUND_OFF);
    assertEquals(0.75, first.process.getTime(), ROUND_OFF);
    assertEquals(0.75, second.process.getTime(), ROUND_OFF);
    assertEquals(1, scheduler.getFiredEvents().size());
  }

  /** A failing start event restores both its participant mutation and scheduler bookkeeping. */
  @Test
  void failingStartEventRollsBackMutationAndScheduler() {
    Study study = new Study("failed-event", 1.0, true, 1.0e-3, 0.0, 1.0e-6, 2.0, 3);
    EventScheduler scheduler = new EventScheduler();
    scheduler.scheduleTransactionalEvent(0.0, "fail-after-change", () -> {
      study.unit.rate = 9.0;
      throw new IllegalStateException("intentional event failure");
    }, study.unit.getTransientStateIdentity());
    study.process.setEventScheduler(scheduler);

    assertThrows(RuntimeException.class,
        () -> study.process.runTransientAdaptive(0.25, step("failed-event", 0), study.options));

    assertInitialState(study);
    assertEquals(1.0, study.unit.rate, 0.0);
    assertEquals(1, scheduler.getPendingEvents().size());
    assertTrue(scheduler.getFiredEvents().isEmpty());
  }

  /** An unscoped event remains blocked before numerical trial execution. */
  @Test
  void unscopedEventIsNotExecutedByAdaptiveTrials() {
    Study study = new Study("unscoped-event", 1.0, true, 1.0e-3, 0.0, 1.0e-6, 2.0, 3);
    EventScheduler scheduler = new EventScheduler();
    scheduler.scheduleEvent(0.0, "unscoped", () -> study.unit.rate = 9.0);
    study.process.setEventScheduler(scheduler);

    assertThrows(RuntimeException.class,
        () -> study.process.runTransientAdaptive(0.25, step("unscoped-event", 0), study.options));

    assertInitialState(study);
    assertEquals(1.0, study.unit.rate, 0.0);
    assertEquals(1, scheduler.getPendingEvents().size());
  }

  /** A start action may insert a future event that must shorten and replay the whole trial interval. */
  @Test
  void newlyInsertedFutureEventShortensAndReplaysFromOriginalStart() {
    Study study = new Study("inserted-event", 1.0, true, 1.0e-3, 0.0, 1.0e-6, 2.0, 8);
    EventScheduler scheduler = new EventScheduler();
    scheduler.scheduleTransactionalEvent(
        0.0, "insert-boundary", () -> scheduler.scheduleTransactionalEvent(0.125, "new-rate",
            () -> study.unit.rate = 4.0, study.unit.getTransientStateIdentity()),
        study.unit.getTransientStateIdentity());
    study.process.setEventScheduler(scheduler);

    AdaptiveTransientResult result = study.process.runTransientAdaptive(1.0, step("inserted-event", 0), study.options);

    assertEquals(0.125, result.getAcceptedStepSeconds(), ROUND_OFF);
    assertTrue(result.getRejectedAttempts() > 0);
    assertEquals(0.125, study.unit.receiver, ROUND_OFF);
    assertEquals(1.0, study.unit.rate, 0.0);
    assertEquals(1, scheduler.getFiredEvents().size());
    assertEquals("insert-boundary", scheduler.getFiredEvents().get(0).getLabel());
    assertEquals(1, scheduler.getPendingEvents().size());
    assertEquals(0.125, scheduler.getPendingEvents().get(0).getTime(), 0.0);
    study.process.runTransientAdaptive(0.125, step("inserted-event", 1), study.options);
    assertEquals(0.625, study.unit.receiver, ROUND_OFF);
    assertEquals(0.25, study.process.getTime(), ROUND_OFF);
    assertEquals(2, scheduler.getFiredEvents().size());
    assertTrue(scheduler.getPendingEvents().isEmpty());
  }

  /** Independent per-area schedulers are all inspected when selecting the next event boundary. */
  @Test
  void earliestBoundaryAcrossDistinctAreaSchedulersControlsCommonStep() {
    Study first = new Study("distinct-first", 1.0, true, 1.0e-3, 0.0, 1.0e-6, 2.0, 8);
    Study second = new Study("distinct-second", 2.0, true, 1.0e-3, 0.0, 1.0e-6, 2.0, 8);
    EventScheduler firstScheduler = new EventScheduler();
    EventScheduler secondScheduler = new EventScheduler();
    firstScheduler.scheduleTransactionalEvent(0.75, "first-rate", () -> first.unit.rate = 3.0,
        first.unit.getTransientStateIdentity());
    secondScheduler.scheduleTransactionalEvent(0.25, "second-rate", () -> second.unit.rate = 4.0,
        second.unit.getTransientStateIdentity());
    first.process.setEventScheduler(firstScheduler);
    second.process.setEventScheduler(secondScheduler);
    ProcessModel model = new ProcessModel();
    model.add("first", first.process);
    model.add("second", second.process);
    AdaptiveTransientOptions options = combinedOptions(first.unit, second.unit);

    assertEquals(0.25,
        model.runTransientAdaptive(1.0, step("distinct-schedulers", 0), options).getAcceptedStepSeconds(), ROUND_OFF);
    assertEquals(0.25, first.unit.receiver, ROUND_OFF);
    assertEquals(0.5, second.unit.receiver, ROUND_OFF);
    assertTrue(firstScheduler.getFiredEvents().isEmpty());
    assertTrue(secondScheduler.getFiredEvents().isEmpty());
    assertEquals(0.5, model.runTransientAdaptive(1.0, step("distinct-schedulers", 1), options).getAcceptedStepSeconds(),
        ROUND_OFF);
    assertEquals(0.75, first.unit.receiver, ROUND_OFF);
    assertEquals(2.5, second.unit.receiver, ROUND_OFF);
    assertTrue(firstScheduler.getFiredEvents().isEmpty());
    assertEquals(1, secondScheduler.getFiredEvents().size());
    model.runTransientAdaptive(0.25, step("distinct-schedulers", 2), options);
    assertEquals(1.5, first.unit.receiver, ROUND_OFF);
    assertEquals(3.5, second.unit.receiver, ROUND_OFF);
    assertEquals(1, firstScheduler.getFiredEvents().size());
    assertEquals(1, secondScheduler.getFiredEvents().size());
    assertEquals(1.0, first.process.getTime(), ROUND_OFF);
    assertEquals(1.0, second.process.getTime(), ROUND_OFF);
  }

  /** A shared mutable equipment object cannot be advanced twice through different process areas. */
  @Test
  void sharedMutableParticipantAcrossAreasFailsBeforeMutation() {
    Study study = new Study("aliased-unit", 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 8);
    ProcessSystem second = new ProcessSystem("second-alias");
    second.add(study.unit);
    ProcessModel model = new ProcessModel();
    model.add("first", study.process);
    model.add("second", second);

    assertThrows(IllegalStateException.class,
        () -> model.runTransientAdaptive(0.25, step("aliased-unit", 0), study.options));

    assertInitialState(study);
    assertEquals(0.0, second.getTime(), 0.0);
    assertSame(study.unit, second.getUnitOperations().get(0));
  }

  /** Reusing an accepted parent identifier is a caller error rather than another physical advance. */
  @Test
  void acceptedParentIdentifierCannotBeUsedForAnotherAdvance() {
    Study study = new Study("duplicate-parent", 1.0, false, 1.0, 0.0, 1.0e-6, 1.0, 8);
    UUID parent = step("duplicate-parent", 0);
    study.process.runTransientAdaptive(0.25, parent, study.options);
    double previousSource = study.unit.source;
    double previousReceiver = study.unit.receiver;
    int previousHistory = study.process.getHistorySize();
    List<UUID> previousIds = new ArrayList<UUID>(study.unit.acceptedIds);

    assertThrows(IllegalArgumentException.class, () -> study.process.runTransientAdaptive(0.25, parent, study.options));

    assertEquals(0.25, study.process.getTime(), ROUND_OFF);
    assertEquals(previousSource, study.unit.source, 0.0);
    assertEquals(previousReceiver, study.unit.receiver, 0.0);
    assertEquals(previousHistory, study.process.getHistorySize());
    assertEquals(previousIds, study.unit.acceptedIds);
  }

  /** Finite intervals that cannot advance the floating-point clock fail before any physical state changes. */
  @Test
  void unrepresentableHalfStepAtLargeAbsoluteTimeFailsBeforeMutation() {
    Study study = new Study("large-clock", 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 8);
    study.process.setTime(1.0e20);
    study.unit.setTime(1.0e20);

    assertThrows(IllegalStateException.class,
        () -> study.process.runTransientAdaptive(1.0, step("large-clock", 0), study.options));

    assertEquals(1.0e20, study.process.getTime(), 0.0);
    assertEquals(1.0e20, study.unit.getTime(), 0.0);
    assertEquals(1.0, study.unit.source, 0.0);
    assertEquals(0.0, study.unit.receiver, 0.0);
    assertTrue(study.unit.acceptedIds.isEmpty());
    assertNull(study.process.getCalculationIdentifier());
  }

  /** A physical model exception is propagated rather than hidden by reducing the timestep until it disappears. */
  @Test
  void equipmentExceptionIsNotRetriedAsNumericalErrorRejection() {
    Study study = new Study("physics-failure", 1.0, false, 1.0, 0.0, 1.0e-6, 1.0, 16);
    study.unit.maximumStepBeforeFailure = 0.1;

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> study.process.runTransientAdaptive(1.0, step("physics-failure", 0), study.options));

    assertEquals("physical model rejected the configured interval", failure.getMessage());
    assertEquals(1, study.unit.physicalEvaluations);
    assertInitialState(study);
    assertEquals(0.1, study.unit.maximumStepBeforeFailure, 0.0);
  }

  /** Decimal endpoints must be reached exactly without crossing or applying their event prematurely. */
  @Test
  void decimalEventEndpointsAreRetainedAtNonzeroAbsoluteTimes() {
    double[][] intervals = {{1.0, 1.2}, {1000.0, 1000.1}};
    for (int index = 0; index < intervals.length; index++) {
      Study study = new Study("decimal-event-" + index, 1.0, true, 1.0e-3, 0.0, 1.0e-9, 2.0, 8);
      double start = intervals[index][0];
      double endpoint = intervals[index][1];
      study.process.setTime(start);
      study.unit.setTime(start);
      EventScheduler scheduler = new EventScheduler();
      scheduler.scheduleTransactionalEvent(endpoint, "decimal-rate", () -> study.unit.rate = 3.0,
          study.unit.getTransientStateIdentity());
      study.process.setEventScheduler(scheduler);

      AdaptiveTransientResult result = study.process.runTransientAdaptive(0.5, step("decimal-event", index),
          study.options);

      assertEquals(endpoint - start, result.getAcceptedStepSeconds(), 0.0);
      assertEquals(endpoint, study.process.getTime(), 0.0);
      assertEquals(endpoint, study.unit.getTime(), 0.0);
      assertEquals(endpoint - start, study.unit.receiver, ROUND_OFF);
      assertTrue(scheduler.getFiredEvents().isEmpty());
      assertEquals(1, scheduler.getPendingEvents().size());
      study.process.runTransientAdaptive(0.1, step("decimal-event-after", index), study.options);
      assertEquals(endpoint - start + 0.3, study.unit.receiver, ROUND_OFF);
      assertEquals(1, scheduler.getFiredEvents().size());
      assertTrue(scheduler.getPendingEvents().isEmpty());
    }
  }

  /** Unreviewed process-system subclasses cannot inherit an adaptive execution contract accidentally. */
  @Test
  void processSystemSubclassFailsClosedBeforeMutation() {
    TransferUnit unit = new TransferUnit("subclass-unit", "subclass-unit", 1.0, false);
    ProcessSystem process = new UnauditedProcessSystem();
    process.add(unit);

    assertThrows(IllegalStateException.class, () -> process.runTransientAdaptive(0.25, step("subclass-system", 0),
        options(unit, 1.0e-4, 0.0, 1.0e-6, 1.0, 8)));

    assertEquals(0.0, process.getTime(), 0.0);
    assertEquals(0.0, unit.getTime(), 0.0);
    assertEquals(1.0, unit.source, 0.0);
    assertTrue(unit.acceptedIds.isEmpty());
  }

  /** Unreviewed model subclasses cannot bypass the shared coordinator's ownership assumptions. */
  @Test
  void processModelSubclassFailsClosedBeforeMutation() {
    Study study = new Study("subclass-area", 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 8);
    ProcessModel model = new UnauditedProcessModel();
    model.add("area", study.process);

    assertThrows(IllegalStateException.class,
        () -> model.runTransientAdaptive(0.25, step("subclass-model", 0), study.options));

    assertInitialState(study);
  }

  /** A failed rollback cannot be hidden by the otherwise recoverable event-boundary retry path. */
  @Test
  void eventBoundaryRetryAbortsWhenRollbackFailsAndRetainsSuppressedEvidence() {
    Study study = new Study("rollback-failure", 1.0, true, 1.0e-3, 0.0, 1.0e-6, 2.0, 8);
    EventScheduler scheduler = new EventScheduler();
    scheduler.scheduleTransactionalEvent(0.0, "insert-and-break-restore", () -> {
      study.unit.failRestore = true;
      scheduler.scheduleTransactionalEvent(0.125, "new-boundary", () -> study.unit.rate = 4.0,
          study.unit.getTransientStateIdentity());
    }, study.unit.getTransientStateIdentity());
    study.process.setEventScheduler(scheduler);

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> study.process.runTransientAdaptive(1.0, step("rollback-failure", 0), study.options));

    assertTrue(failure.getMessage().contains("restore"));
    assertNotNull(failure.getCause());
    assertTrue(containsFailureMessage(failure, "intentional rollback failure"));
    assertEquals(1, study.unit.restoreFailures);
    assertEquals(0.0, study.process.getTime(), 0.0);
    assertEquals(0.0, study.unit.getTime(), 0.0);
    assertEquals(1, scheduler.getPendingEvents().size());
    assertTrue(scheduler.getFiredEvents().isEmpty());
  }

  /**
   * Reports bounded warmed coordinator overhead on a constant-rate analytical fixture, excluding process physics. This
   * method is opt-in and makes no industrial speed or hardware-independent performance claim.
   */
  @Test
  @Tag("benchmark")
  void reportSyntheticPairedCoordinatorOverhead() {
    Study fixed = new Study("fixed-cost", 1.0, true, 1.0e-3, 0.0, 1.0e-9, 0.001, 8);
    Study adaptive = new Study("adaptive-cost", 1.0, true, 1.0e-3, 0.0, 1.0e-9, 0.001, 8);
    fixed.unit.source = 1.0e6;
    adaptive.unit.source = 1.0e6;
    fixed.process.setMeasurementHistoryRecordingEnabled(false);
    adaptive.process.setMeasurementHistoryRecordingEnabled(false);
    benchmarkBatch(fixed, false, 100, 0);
    benchmarkBatch(adaptive, true, 100, 0);
    long[] fixedNanos = new long[5];
    long[] adaptiveNanos = new long[5];
    long fixedEvaluations = 0;
    long adaptiveEvaluations = 0;
    for (int batch = 0; batch < 5; batch++) {
      int offset = 100 + batch * 100;
      long[] fixedBatch;
      long[] adaptiveBatch;
      if (batch % 2 == 0) {
        fixedBatch = benchmarkBatch(fixed, false, 100, offset);
        adaptiveBatch = benchmarkBatch(adaptive, true, 100, offset);
      } else {
        adaptiveBatch = benchmarkBatch(adaptive, true, 100, offset);
        fixedBatch = benchmarkBatch(fixed, false, 100, offset);
      }
      fixedNanos[batch] = fixedBatch[0];
      adaptiveNanos[batch] = adaptiveBatch[0];
      fixedEvaluations += fixedBatch[1];
      adaptiveEvaluations += adaptiveBatch[1];
    }
    long[] sortedFixed = fixedNanos.clone();
    long[] sortedAdaptive = adaptiveNanos.clone();
    Arrays.sort(sortedFixed);
    Arrays.sort(sortedAdaptive);
    assertEquals(500, fixedEvaluations);
    assertEquals(1500, adaptiveEvaluations);
    assertEquals(0.6, fixed.process.getTime(), ROUND_OFF);
    assertEquals(0.6, adaptive.process.getTime(), ROUND_OFF);
    assertEquals(0.6, fixed.unit.receiver, ROUND_OFF);
    assertEquals(0.6, adaptive.unit.receiver, ROUND_OFF);
    assertEquals(fixed.unit.source, adaptive.unit.source, 1.0e-7);
    logger.info(
        "Synthetic coordinator benchmark: fixed ns/step={}, adaptive ns/step={}, fixed median={}, "
            + "adaptive median={}, ratio={}, physical evaluations fixed={}, adaptive={}; five alternating batches "
            + "of 100 steps after 100 warmup steps, dt=0.001s, history disabled, analytical constant-rate fixture only",
        Arrays.toString(fixedNanos), Arrays.toString(adaptiveNanos), sortedFixed[2], sortedAdaptive[2],
        (double) sortedAdaptive[2] / sortedFixed[2], fixedEvaluations, adaptiveEvaluations);
    String evidence = "{\"fixedNanosPerStep\":" + Arrays.toString(fixedNanos) + ",\"adaptiveNanosPerStep\":"
        + Arrays.toString(adaptiveNanos) + ",\"fixedMedian\":" + sortedFixed[2] + ",\"adaptiveMedian\":"
        + sortedAdaptive[2] + ",\"ratio\":" + ((double) sortedAdaptive[2] / sortedFixed[2]) + ",\"fixedEvaluations\":"
        + fixedEvaluations + ",\"adaptiveEvaluations\":" + adaptiveEvaluations + "}";
    try {
      java.nio.file.Files.write(java.nio.file.Paths.get("target", "adaptive-transient-benchmark.json"),
          evidence.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("Could not preserve adaptive benchmark evidence", failure);
    }
  }

  /** Invalid requests are rejected before touching clocks or inventories. */
  @Test
  void invalidStepInputsFailBeforeMutation() {
    for (double dt : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      Study study = new Study("invalid-input", 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 8);
      assertThrows(IllegalArgumentException.class,
          () -> study.process.runTransientAdaptive(dt, step("invalid", 0), study.options));
      assertInitialState(study);
    }
    Study study = new Study("null-input", 1.0, false, 1.0e-4, 0.0, 1.0e-6, 1.0, 8);
    assertThrows(IllegalArgumentException.class, () -> study.process.runTransientAdaptive(0.1, null, study.options));
    assertThrows(IllegalArgumentException.class,
        () -> study.process.runTransientAdaptive(0.1, step("null-options", 0), null));
    assertInitialState(study);
  }

  /**
   * Integrates the independently defined first-order inventory transfer over one second.
   *
   * @param stepSeconds fixed requested outer interval
   * @return completed reference study
   */
  private static Study integrateInventory(double stepSeconds) {
    Study study = new Study("refinement", 1.0, false, 1.0, 0.0, 1.0e-6, stepSeconds, 8);
    int count = (int) Math.round(1.0 / stepSeconds);
    for (int i = 0; i < count; i++) {
      AdaptiveTransientResult result = study.process.runTransientAdaptive(stepSeconds, step("refinement", i),
          study.options);
      assertEquals(stepSeconds, result.getAcceptedStepSeconds(), ROUND_OFF);
      assertEquals(0, result.getRejectedAttempts());
      assertEquals(1.0, study.unit.source + study.unit.receiver, ROUND_OFF);
      assertTrue(study.unit.source >= 0.0 && study.unit.receiver >= 0.0);
    }
    return study;
  }

  /**
   * Checks every externally observable original fixture state after an unsuccessful trial.
   *
   * @param study original study
   */
  private static void assertInitialState(Study study) {
    assertEquals(study.unit.constantRate ? 10.0 : 1.0, study.unit.source, ROUND_OFF);
    assertEquals(0.0, study.unit.receiver, ROUND_OFF);
    assertEquals(0.0, study.unit.getTime(), ROUND_OFF);
    assertEquals(0.0, study.process.getTime(), ROUND_OFF);
    assertNull(study.unit.getCalculationIdentifier());
    assertNull(study.process.getCalculationIdentifier());
    assertTrue(study.unit.acceptedIds.isEmpty());
    assertEquals(0, study.process.getHistorySize());
    assertSame(study.unit, study.process.getUnitOperations().get(0));
  }

  /**
   * Constructs explicit state-provider coverage for a single process element.
   *
   * @param unit state-owning element
   * @param absoluteTolerance declared inventory tolerance in kg
   * @param relativeTolerance declared dimensionless relative tolerance
   * @param minimumStep minimum retry interval in seconds
   * @param maximumStep maximum retry interval in seconds
   * @param attempts retry budget including the first attempt
   * @return complete numerical configuration
   */
  private static AdaptiveTransientOptions options(TransferUnit unit, double absoluteTolerance, double relativeTolerance,
      double minimumStep, double maximumStep, int attempts) {
    TransientErrorStateBinding binding = new TransientErrorStateBinding(unit,
        new InventoryProvider(unit, absoluteTolerance, relativeTolerance));
    return new AdaptiveTransientOptions(minimumStep, maximumStep, attempts, 1, Collections.singletonList(binding));
  }

  /**
   * Constructs two-area numerical coverage without requiring globally unique local equipment identities.
   *
   * @param first first area inventory
   * @param second second area inventory
   * @return complete model-level configuration
   */
  private static AdaptiveTransientOptions combinedOptions(TransferUnit first, TransferUnit second) {
    return new AdaptiveTransientOptions(1.0e-6, 2.0, 16, 1,
        Arrays.asList(new TransientErrorStateBinding(first, new InventoryProvider(first, 1.0e-4, 0.0)),
            new TransientErrorStateBinding(second, new InventoryProvider(second, 1.0e-4, 0.0))));
  }

  /**
   * Creates deterministic parent provenance for a requested accepted interval.
   *
   * @param scope scenario identity
   * @param index requested interval index
   * @return deterministic parent identifier
   */
  private static UUID step(String scope, long index) {
    return TransientStepIdentifier.deterministicPhysicalStep(scope, index);
  }

  /**
   * Searches preserved causes and suppressed exceptions for a specific failure diagnostic.
   *
   * @param failure failure tree
   * @param text diagnostic fragment
   * @return whether the diagnostic survived propagation
   */
  private static boolean containsFailureMessage(Throwable failure, String text) {
    if (failure.getMessage() != null && failure.getMessage().contains(text)) {
      return true;
    }
    if (failure.getCause() != null && containsFailureMessage(failure.getCause(), text)) {
      return true;
    }
    for (Throwable suppressed : failure.getSuppressed()) {
      if (containsFailureMessage(suppressed, text)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Times one same-harness batch while counting actual physical evaluations independently of rollback.
   *
   * @param study warmed analytical fixture
   * @param adaptive whether to use the opt-in adaptive entry point
   * @param count accepted physical intervals
   * @param offset deterministic physical-step index offset
   * @return nanoseconds per accepted interval and actual physical evaluation count
   */
  private static long[] benchmarkBatch(Study study, boolean adaptive, int count, int offset) {
    long evaluationsBefore = study.unit.physicalEvaluations;
    long started = System.nanoTime();
    for (int index = 0; index < count; index++) {
      UUID parent = step("synthetic-cost", offset + index);
      if (adaptive) {
        study.process.runTransientAdaptive(0.001, parent, study.options);
      } else {
        study.process.runTransient(0.001, parent);
      }
      study.unit.acceptedIds.clear();
    }
    long elapsed = System.nanoTime() - started;
    return new long[] {elapsed / count, study.unit.physicalEvaluations - evaluationsBefore};
  }

  /**
   * Serializes and restores a complete reference graph or immutable numerical result.
   *
   * @param value serializable value
   * @param <T> serializable input type
   * @return independently restored graph
   * @throws Exception if Java serialization fails
   */
  @SuppressWarnings("unchecked")
  private static <T extends Serializable> T roundTrip(T value) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(value);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (T) input.readObject();
    }
  }

  /**
   * A complete serializable miniature process and its explicit numerical provider graph.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class Study implements Serializable {
    /** Serialization version. */
    private static final long serialVersionUID = 1000L;
    /** Process whose public transient entry point is under test. */
    private final ProcessSystem process;
    /** Conservative analytical reference element. */
    private final TransferUnit unit;
    /** Explicit numerical policy and coverage. */
    private final AdaptiveTransientOptions options;

    /**
     * Creates a first-order or constant-rate two-inventory process.
     *
     * @param identity stable state identity
     * @param rate transfer coefficient in inverse seconds or constant kg/s
     * @param constantRate true for a constant flux
     * @param absoluteTolerance absolute mass tolerance in kg
     * @param relativeTolerance relative tolerance
     * @param minimumStep minimum retry step in seconds
     * @param maximumStep maximum outer step in seconds
     * @param attempts retry budget
     */
    private Study(String identity, double rate, boolean constantRate, double absoluteTolerance,
        double relativeTolerance, double minimumStep, double maximumStep, int attempts) {
      unit = new TransferUnit(identity, identity, rate, constantRate);
      process = new ProcessSystem(identity);
      process.add(unit);
      options = options(unit, absoluteTolerance, relativeTolerance, minimumStep, maximumStep, attempts);
    }
  }

  /**
   * Pure explicit numerical observation of the two reference inventories.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class InventoryProvider implements TransientErrorStateProvider {
    /** Serialization version. */
    private static final long serialVersionUID = 1000L;
    /** Actual state owner; Java serialization preserves the binding identity. */
    private final TransferUnit unit;
    /** Absolute mass error scale in kg. */
    private final double absoluteTolerance;
    /** Relative error scale. */
    private final double relativeTolerance;

    /**
     * Creates an observer with explicit error scales.
     *
     * @param unit state owner
     * @param absoluteTolerance absolute mass scale in kg
     * @param relativeTolerance dimensionless relative scale
     */
    private InventoryProvider(TransferUnit unit, double absoluteTolerance, double relativeTolerance) {
      this.unit = unit;
      this.absoluteTolerance = absoluteTolerance;
      this.relativeTolerance = relativeTolerance;
    }

    /** {@inheritDoc} */
    @Override
    public List<TransientErrorStateVariable> getTransientErrorState() {
      boolean changed = unit.getTime() > 0.0;
      String variable = changed && unit.schemaMutation == 1 ? "changed-source" : "source";
      String quantityUnit = changed && unit.schemaMutation == 2 ? "g" : "kg";
      double scale = changed && unit.schemaMutation == 3 ? 2.0 * absoluteTolerance : absoluteTolerance;
      return Arrays.asList(
          new TransientErrorStateVariable(unit.identity, variable, quantityUnit, unit.source, scale, relativeTolerance),
          new TransientErrorStateVariable(unit.identity, "receiver", "kg", unit.receiver, absoluteTolerance,
              relativeTolerance));
    }
  }

  /**
   * Explicit Euler transfer from source to receiver with exact discrete total-mass conservation.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class TransferUnit extends ProcessEquipmentBaseClass
      implements TransientStateParticipant<TransferUnit.Snapshot> {
    /** Serialization version. */
    private static final long serialVersionUID = 1000L;
    /** Stable local participant identity. */
    private final String identity;
    /** True for a constant imposed flux rather than a first-order transfer law. */
    private final boolean constantRate;
    /** Source mass in kg. */
    private double source = 1.0;
    /** Receiver mass in kg. */
    private double receiver;
    /** Rate coefficient in inverse seconds or imposed flux in kg/s. */
    private double rate;
    /** Deliberate runtime failure after mutation. */
    private boolean failAfterMutation;
    /** Deliberate invalid physical output after mutation. */
    private boolean nonFiniteAfterMutation;
    /** Deliberate physics-domain interval limit, independent of the error estimator. */
    private double maximumStepBeforeFailure = Double.POSITIVE_INFINITY;
    /** Deliberate provider schema mutation for negative contract tests. */
    private int schemaMutation;
    /** Deliberate invalid rollback setting for the negative restore-failure contract. */
    private boolean failRestore;
    /** Diagnostic count of failed restores in the intentionally invalid rollback fixture. */
    private transient int restoreFailures;
    /**
     * Diagnostic work counter for the opt-in benchmark, excluded from physical rollback and never read by equations.
     */
    private transient long physicalEvaluations;
    /** Retained identities of the accepted physical substeps only. */
    private List<UUID> acceptedIds = new ArrayList<UUID>();

    /**
     * Creates the independently specified conservative reference ODE.
     *
     * @param name equipment name
     * @param identity stable local state identity
     * @param rate coefficient in inverse seconds or constant kg/s
     * @param constantRate true for constant imposed flux
     */
    private TransferUnit(String name, String identity, double rate, boolean constantRate) {
      super(name);
      this.identity = identity;
      this.rate = rate;
      this.constantRate = constantRate;
      source = constantRate ? 10.0 : 1.0;
      setCalculateSteadyState(false);
    }

    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt, UUID id) {
      if (id != null && id.equals(getCalculationIdentifier())) {
        return;
      }
      physicalEvaluations++;
      double transferred = (constantRate ? rate : rate * source) * dt;
      source -= transferred;
      receiver += transferred;
      increaseTime(dt);
      setCalculationIdentifier(id);
      acceptedIds.add(id);
      if (dt > maximumStepBeforeFailure) {
        throw new IllegalStateException("physical model rejected the configured interval");
      }
      if (nonFiniteAfterMutation) {
        source = Double.NaN;
      }
      if (failAfterMutation) {
        throw new IllegalStateException("intentional equipment failure");
      }
    }

    /** {@inheritDoc} */
    @Override
    public String getTransientStateIdentity() {
      return identity;
    }

    /** {@inheritDoc} */
    @Override
    public Snapshot captureTransientState() {
      return new Snapshot(this);
    }

    /** {@inheritDoc} */
    @Override
    public void restoreTransientState(Snapshot snapshot) {
      boolean failThisRestore = failRestore;
      source = snapshot.source;
      receiver = snapshot.receiver;
      rate = snapshot.rate;
      setTime(snapshot.time);
      setCalculationIdentifier(snapshot.calculationIdentifier);
      acceptedIds = new ArrayList<UUID>(snapshot.acceptedIds);
      failAfterMutation = snapshot.failAfterMutation;
      nonFiniteAfterMutation = snapshot.nonFiniteAfterMutation;
      maximumStepBeforeFailure = snapshot.maximumStepBeforeFailure;
      schemaMutation = snapshot.schemaMutation;
      failRestore = snapshot.failRestore;
      if (failThisRestore) {
        restoreFailures++;
        throw new IllegalStateException("intentional rollback failure");
      }
    }

    /**
     * Complete immutable inventory, equipment, identity and fault state for deterministic rollback.
     *
     * @author Even Solbraa
     * @version 1.0
     */
    private static final class Snapshot implements Serializable {
      /** Serialization version. */
      private static final long serialVersionUID = 1000L;
      /** Source mass in kg. */
      private final double source;
      /** Receiver mass in kg. */
      private final double receiver;
      /** Configured rate coefficient or flux. */
      private final double rate;
      /** Equipment clock in seconds. */
      private final double time;
      /** Last physical substep identity. */
      private final UUID calculationIdentifier;
      /** Accepted physical substep identities. */
      private final List<UUID> acceptedIds;
      /** Deliberate equipment-failure state. */
      private final boolean failAfterMutation;
      /** Deliberate invalid-state setting. */
      private final boolean nonFiniteAfterMutation;
      /** Configured physical-model interval bound. */
      private final double maximumStepBeforeFailure;
      /** Deliberate numerical provider-schema setting. */
      private final int schemaMutation;
      /** Deliberate restore-failure setting. */
      private final boolean failRestore;

      /**
       * Captures all mutable state independently of later unit changes.
       *
       * @param unit reference element
       */
      private Snapshot(TransferUnit unit) {
        source = unit.source;
        receiver = unit.receiver;
        rate = unit.rate;
        time = unit.getTime();
        calculationIdentifier = unit.getCalculationIdentifier();
        acceptedIds = new ArrayList<UUID>(unit.acceptedIds);
        failAfterMutation = unit.failAfterMutation;
        nonFiniteAfterMutation = unit.nonFiniteAfterMutation;
        maximumStepBeforeFailure = unit.maximumStepBeforeFailure;
        schemaMutation = unit.schemaMutation;
        failRestore = unit.failRestore;
      }
    }
  }

  /**
   * Unaudited process-system specialization used to verify explicit type boundaries.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class UnauditedProcessSystem extends ProcessSystem {
    /** Serialization version. */
    private static final long serialVersionUID = 1000L;

    /** Creates the deliberately unaudited process-system specialization. */
    private UnauditedProcessSystem() {
      super("unaudited-system");
    }
  }

  /**
   * Unaudited multi-area model specialization used to verify explicit type boundaries.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class UnauditedProcessModel extends ProcessModel {
    /** Serialization version. */
    private static final long serialVersionUID = 1000L;

    /** Creates the deliberately unaudited model specialization. */
    private UnauditedProcessModel() {
      super();
    }
  }
}
