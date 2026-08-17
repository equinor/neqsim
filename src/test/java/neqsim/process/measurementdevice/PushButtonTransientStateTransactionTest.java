package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.alarm.AlarmState;
import neqsim.process.dynamics.EventScheduler;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.dynamics.TransientTransactionCoverage;
import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.equipment.valve.ControlValve;
import neqsim.process.logic.control.PressureControlLogic;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/** Rollback, event replay, restart, and side-effect boundary evidence for local push buttons. */
class PushButtonTransientStateTransactionTest extends neqsim.NeqSimTest {
  @Test
  void multiAreaRollbackRestoresScheduledPushesConfigurationAndAlarmState() {
    PushButton first = new PushButton("ESD-PB-101");
    AlarmConfig firstAlarm = AlarmConfig.builder().highLimit(0.5).delay(2.0).unit("binary").build();
    first.setAlarmConfig(firstAlarm);
    AlarmState originalFirstAlarmState = first.getAlarmState();

    BlowdownValve observedValve = new BlowdownValve("BDV-201");
    PushButton second = new PushButton("ESD-PB-201", observedValve);
    second.setAutoActivateValve(false);
    AlarmConfig secondAlarm = AlarmConfig.builder().highLimit(0.5).delay(2.0).unit("binary").build();
    second.setAlarmConfig(secondAlarm);
    AlarmState originalSecondAlarmState = second.getAlarmState();

    ProcessSystem firstArea = new ProcessSystem("first manual-input area");
    firstArea.setEventScheduler(new EventScheduler());
    firstArea.add(first);
    ProcessSystem secondArea = new ProcessSystem("second manual-input area");
    secondArea.setEventScheduler(new EventScheduler());
    secondArea.add(second);
    assertCompleteCoverage(firstArea.getTransientTransactionCoverage(), 1);
    assertCompleteCoverage(secondArea.getTransientTransactionCoverage(), 1);

    ProcessModel model = new ProcessModel();
    model.add("first", firstArea);
    model.add("second", secondArea);
    assertCompleteCoverage(model.getTransientTransactionCoverage(), 2);

    firstArea.getEventScheduler().scheduleTransactionalEvent(1.0, "first manual push", first::push,
        first.getTransientStateIdentity());
    secondArea.getEventScheduler().scheduleTransactionalEvent(1.0, "second manual push", second::push,
        second.getTransientStateIdentity());

    String firstIdentity = first.getTransientStateIdentity();
    String secondIdentity = second.getTransientStateIdentity();
    TransientStepTransaction transaction = model.beginTransientStepTransaction();

    assertEquals(1, firstArea.getEventScheduler().fireDueEvents(1.0));
    assertEquals(1, secondArea.getEventScheduler().fireDueEvents(1.0));
    assertTrue(first.isPushed());
    assertTrue(second.isPushed());
    assertFalse(observedValve.isActivated());
    assertTrue(first.evaluateAlarm(first.getMeasuredValue(), 1.0, 1.0).isEmpty());
    assertTrue(second.evaluateAlarm(second.getMeasuredValue(), 1.0, 1.0).isEmpty());

    first.setName("trial first button");
    first.setAutoActivateValve(false);
    second.setName("trial second button");
    second.linkToBlowdownValve(new BlowdownValve("trial valve"));
    second.setAutoActivateValve(true);
    transaction.rollback();

    assertEquals(firstIdentity, first.getTransientStateIdentity());
    assertEquals(secondIdentity, second.getTransientStateIdentity());
    assertEquals("ESD-PB-101", first.getName());
    assertEquals("ESD-PB-201", second.getName());
    assertFalse(first.isPushed());
    assertFalse(second.isPushed());
    assertSame(observedValve, second.getLinkedBlowdownValve());
    assertFalse(second.isAutoActivateValve());
    assertSame(firstAlarm, first.getAlarmConfig());
    assertSame(secondAlarm, second.getAlarmConfig());
    assertSame(originalFirstAlarmState, first.getAlarmState());
    assertSame(originalSecondAlarmState, second.getAlarmState());
    assertEquals(1, firstArea.getEventScheduler().getPendingEvents().size());
    assertEquals(0, firstArea.getEventScheduler().getFiredEvents().size());
    assertEquals(1, secondArea.getEventScheduler().getPendingEvents().size());
    assertEquals(0, secondArea.getEventScheduler().getFiredEvents().size());

    TransientStepTransaction replay = model.beginTransientStepTransaction();
    assertEquals(1, firstArea.getEventScheduler().fireDueEvents(1.0));
    assertEquals(1, secondArea.getEventScheduler().fireDueEvents(1.0));
    assertTrue(first.isPushed());
    assertTrue(second.isPushed());
    assertFalse(observedValve.isActivated());
    assertTrue(first.evaluateAlarm(first.getMeasuredValue(), 1.0, 1.0).isEmpty());
    assertEquals(1, first.evaluateAlarm(first.getMeasuredValue(), 1.0, 2.0).size());
    assertTrue(second.evaluateAlarm(second.getMeasuredValue(), 1.0, 1.0).isEmpty());
    assertEquals(1, second.evaluateAlarm(second.getMeasuredValue(), 1.0, 2.0).size());
    replay.commit();

    assertEquals(0, firstArea.getEventScheduler().getPendingEvents().size());
    assertEquals(1, firstArea.getEventScheduler().getFiredEvents().size());
    assertEquals(0, secondArea.getEventScheduler().getPendingEvents().size());
    assertEquals(1, secondArea.getEventScheduler().getFiredEvents().size());
  }

  @Test
  void serializedButtonAndSnapshotRestoreRestartStateAndBindings() throws Exception {
    BlowdownValve valve = new BlowdownValve("BDV-301");
    PushButton button = new PushButton("ESD-PB-301", valve);
    button.setAutoActivateValve(false);
    String identity = button.getTransientStateIdentity();
    PushButton.PushButtonState snapshot = button.captureTransientState();
    button.push();
    button.setName("trial button");
    button.setAutoActivateValve(true);

    byte[] serialized;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(button);
      output.writeObject(snapshot);
      serialized = bytes.toByteArray();
    }

    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      PushButton restored = (PushButton) input.readObject();
      PushButton.PushButtonState restoredSnapshot = (PushButton.PushButtonState) input.readObject();
      restored.restoreTransientState(restoredSnapshot);

      assertEquals(identity, restored.getTransientStateIdentity());
      assertEquals("ESD-PB-301", restored.getName());
      assertFalse(restored.isPushed());
      assertFalse(restored.isAutoActivateValve());
      assertEquals("BDV-301", restored.getLinkedBlowdownValve().getName());
      assertTrue(restored.getLinkedLogics().isEmpty());
    }
  }

  @Test
  void descendantsOnlineAndLinkedActionSideEffectsFailClosed() {
    PushButton subclass = new PushButton("subclass") {
      private static final long serialVersionUID = 1000L;
    };
    assertBlocked(subclass, "subclass-owned mutable state");

    PushButton online = new PushButton("online");
    online.setIsOnlineSignal(true, "plant", "PB-ONLINE");
    assertBlocked(online, "external I/O");

    PushButton automaticValve = new PushButton("automatic valve", new BlowdownValve("BDV-401"));
    assertBlocked(automaticValve, "automatic blowdown-valve activation");

    PushButton linkedLogic = new PushButton("linked logic");
    linkedLogic.linkToLogic(new PressureControlLogic("pressure action", new ControlValve("PCV-401"), 20.0));
    assertBlocked(linkedLogic, "linked process logic");

    PushButton first = new PushButton("first");
    PushButton second = new PushButton("second");
    assertThrows(IllegalArgumentException.class, () -> second.restoreTransientState(first.captureTransientState()));
    assertThrows(IllegalArgumentException.class, () -> first.restoreTransientState(null));
  }

  private static void assertBlocked(PushButton button, String expectedText) {
    ProcessSystem process = new ProcessSystem("push-button blocker");
    process.add(button);
    TransientTransactionCoverage coverage = process.getTransientTransactionCoverage();
    assertEquals(1, coverage.getProcessElementCount());
    assertEquals(1, coverage.getParticipantCount());
    assertFalse(coverage.isComplete());
    assertEquals(1, coverage.getBlockingIssues().size());
    assertTrue(coverage.getBlockingIssues().get(0).contains(expectedText));
  }

  private static void assertCompleteCoverage(TransientTransactionCoverage coverage, int expectedCount) {
    assertEquals(expectedCount, coverage.getProcessElementCount());
    assertEquals(expectedCount, coverage.getParticipantCount());
    assertTrue(coverage.isComplete(), coverage.getBlockingIssues().toString());
  }
}
