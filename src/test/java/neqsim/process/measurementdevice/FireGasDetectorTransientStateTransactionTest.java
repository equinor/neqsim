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
import neqsim.process.measurementdevice.GasDetector.GasType;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Rollback, event replay, multi-area coverage, and restart evidence for local fire-and-gas detectors.
 */
class FireGasDetectorTransientStateTransactionTest extends neqsim.NeqSimTest {
  @Test
  void multiAreaRollbackRestoresScheduledDetectorActionsAndAlarmState() {
    GasDetector gas = new GasDetector("GD-101", GasType.COMBUSTIBLE, "separator module");
    gas.setGasConcentration(5.0);
    gas.setGasSpecies("methane");
    gas.setLowerExplosiveLimit(50000.0);
    gas.setResponseTime(8.0);
    AlarmConfig gasAlarm = AlarmConfig.builder().highLimit(20.0).delay(2.0).unit("% LEL").build();
    gas.setAlarmConfig(gasAlarm);
    AlarmState originalGasAlarmState = gas.getAlarmState();

    FireDetector fire = new FireDetector("FD-201", "compressor module");
    fire.setDetectionThreshold(0.8);
    fire.setDetectionDelay(3.0);
    fire.setSignalLevel(0.2);
    AlarmConfig fireAlarm = AlarmConfig.builder().highLimit(0.5).delay(2.0).unit("binary").build();
    fire.setAlarmConfig(fireAlarm);
    AlarmState originalFireAlarmState = fire.getAlarmState();

    ProcessSystem gasArea = new ProcessSystem("gas detection area");
    gasArea.setEventScheduler(new EventScheduler());
    gasArea.add(gas);
    ProcessSystem fireArea = new ProcessSystem("fire detection area");
    fireArea.setEventScheduler(new EventScheduler());
    fireArea.add(fire);
    assertCompleteCoverage(gasArea.getTransientTransactionCoverage(), 1);
    assertCompleteCoverage(fireArea.getTransientTransactionCoverage(), 1);

    ProcessModel model = new ProcessModel();
    model.add("gas", gasArea);
    model.add("fire", fireArea);
    assertCompleteCoverage(model.getTransientTransactionCoverage(), 2);

    gasArea.getEventScheduler().scheduleTransactionalEvent(1.0, "gas release", () -> {
      gas.setGasConcentration(70.0);
      gas.setGasSpecies("propane");
      gas.setLocation("trial gas zone");
    }, gas.getTransientStateIdentity());
    fireArea.getEventScheduler().scheduleTransactionalEvent(1.0, "fire", () -> {
      fire.detectFire();
      fire.setLocation("trial fire zone");
    }, fire.getTransientStateIdentity());

    String gasIdentity = gas.getTransientStateIdentity();
    String fireIdentity = fire.getTransientStateIdentity();
    TransientStepTransaction transaction = model.beginTransientStepTransaction();

    assertEquals(1, gasArea.getEventScheduler().fireDueEvents(1.0));
    assertEquals(1, fireArea.getEventScheduler().fireDueEvents(1.0));
    assertEquals(70.0, gas.getGasConcentration(), 0.0);
    assertTrue(fire.isFireDetected());
    assertTrue(gas.evaluateAlarm(gas.getMeasuredValue(), 1.0, 1.0).isEmpty());
    assertTrue(fire.evaluateAlarm(fire.getMeasuredValue(), 1.0, 1.0).isEmpty());

    gas.setLowerExplosiveLimit(21000.0);
    gas.setResponseTime(1.0);
    fire.setDetectionThreshold(0.1);
    fire.setDetectionDelay(0.0);
    gas.setName("trial gas detector");
    fire.setName("trial fire detector");
    transaction.rollback();

    assertEquals(gasIdentity, gas.getTransientStateIdentity());
    assertEquals(fireIdentity, fire.getTransientStateIdentity());
    assertEquals("GD-101", gas.getName());
    assertEquals("FD-201", fire.getName());
    assertEquals(5.0, gas.getGasConcentration(), 0.0);
    assertEquals("methane", gas.getGasSpecies());
    assertEquals("separator module", gas.getLocation());
    assertEquals(50000.0, gas.getLowerExplosiveLimit(), 0.0);
    assertEquals(8.0, gas.getResponseTime(), 0.0);
    assertFalse(fire.isFireDetected());
    assertEquals(0.2, fire.getSignalLevel(), 0.0);
    assertEquals(0.8, fire.getDetectionThreshold(), 0.0);
    assertEquals(3.0, fire.getDetectionDelay(), 0.0);
    assertEquals("compressor module", fire.getLocation());
    assertSame(gasAlarm, gas.getAlarmConfig());
    assertSame(fireAlarm, fire.getAlarmConfig());
    assertSame(originalGasAlarmState, gas.getAlarmState());
    assertSame(originalFireAlarmState, fire.getAlarmState());
    assertEquals(1, gasArea.getEventScheduler().getPendingEvents().size());
    assertEquals(0, gasArea.getEventScheduler().getFiredEvents().size());
    assertEquals(1, fireArea.getEventScheduler().getPendingEvents().size());
    assertEquals(0, fireArea.getEventScheduler().getFiredEvents().size());

    TransientStepTransaction replay = model.beginTransientStepTransaction();
    assertEquals(1, gasArea.getEventScheduler().fireDueEvents(1.0));
    assertEquals(1, fireArea.getEventScheduler().fireDueEvents(1.0));
    assertEquals(70.0, gas.getGasConcentration(), 0.0);
    assertEquals("propane", gas.getGasSpecies());
    assertEquals("trial gas zone", gas.getLocation());
    assertTrue(fire.isFireDetected());
    assertEquals(1.0, fire.getSignalLevel(), 0.0);
    assertEquals("trial fire zone", fire.getLocation());
    assertTrue(gas.evaluateAlarm(gas.getMeasuredValue(), 1.0, 1.0).isEmpty());
    assertEquals(1, gas.evaluateAlarm(gas.getMeasuredValue(), 1.0, 2.0).size());
    assertTrue(fire.evaluateAlarm(fire.getMeasuredValue(), 1.0, 1.0).isEmpty());
    assertEquals(1, fire.evaluateAlarm(fire.getMeasuredValue(), 1.0, 2.0).size());
    replay.commit();

    assertEquals(0, gasArea.getEventScheduler().getPendingEvents().size());
    assertEquals(1, gasArea.getEventScheduler().getFiredEvents().size());
    assertEquals(0, fireArea.getEventScheduler().getPendingEvents().size());
    assertEquals(1, fireArea.getEventScheduler().getFiredEvents().size());
  }

  @Test
  void serializedDetectorSnapshotsRestoreRestartContinuation() throws Exception {
    GasDetector gas = new GasDetector("GD-301", GasType.TOXIC, "utility area");
    gas.setGasConcentration(15.0);
    gas.setGasSpecies("H2S");
    gas.setLowerExplosiveLimit(44000.0);
    gas.setResponseTime(4.0);
    String gasIdentity = gas.getTransientStateIdentity();
    GasDetector.GasDetectorState gasSnapshot = gas.captureTransientState();
    gas.setGasConcentration(200.0);
    gas.setGasSpecies("CO");

    FireDetector fire = new FireDetector("FD-301", "utility area");
    fire.setDetectionThreshold(0.7);
    fire.setDetectionDelay(2.5);
    fire.setSignalLevel(0.4);
    String fireIdentity = fire.getTransientStateIdentity();
    FireDetector.FireDetectorState fireSnapshot = fire.captureTransientState();
    fire.detectFire();

    byte[] serialized;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(gas);
      output.writeObject(gasSnapshot);
      output.writeObject(fire);
      output.writeObject(fireSnapshot);
      serialized = bytes.toByteArray();
    }

    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      GasDetector restoredGas = (GasDetector) input.readObject();
      GasDetector.GasDetectorState restoredGasSnapshot = (GasDetector.GasDetectorState) input.readObject();
      FireDetector restoredFire = (FireDetector) input.readObject();
      FireDetector.FireDetectorState restoredFireSnapshot = (FireDetector.FireDetectorState) input.readObject();

      restoredGas.restoreTransientState(restoredGasSnapshot);
      restoredFire.restoreTransientState(restoredFireSnapshot);

      assertEquals(gasIdentity, restoredGas.getTransientStateIdentity());
      assertEquals(15.0, restoredGas.getGasConcentration(), 0.0);
      assertEquals("H2S", restoredGas.getGasSpecies());
      assertEquals(44000.0, restoredGas.getLowerExplosiveLimit(), 0.0);
      assertEquals(4.0, restoredGas.getResponseTime(), 0.0);
      assertEquals(fireIdentity, restoredFire.getTransientStateIdentity());
      assertFalse(restoredFire.isFireDetected());
      assertEquals(0.4, restoredFire.getSignalLevel(), 0.0);
      assertEquals(0.7, restoredFire.getDetectionThreshold(), 0.0);
      assertEquals(2.5, restoredFire.getDetectionDelay(), 0.0);
    }
  }

  @Test
  void descendantsOnlineModesNullAndForeignSnapshotsFailClosed() {
    GasDetector gasSubclass = new GasDetector("gas subclass") {
      private static final long serialVersionUID = 1000L;
    };
    FireDetector fireSubclass = new FireDetector("fire subclass") {
      private static final long serialVersionUID = 1000L;
    };
    ProcessSystem process = new ProcessSystem("detector coverage blockers");
    process.add(gasSubclass);
    process.add(fireSubclass);

    TransientTransactionCoverage coverage = process.getTransientTransactionCoverage();
    assertEquals(2, coverage.getProcessElementCount());
    assertEquals(2, coverage.getParticipantCount());
    assertFalse(coverage.isComplete());
    assertEquals(2, coverage.getBlockingIssues().size());
    for (String issue : coverage.getBlockingIssues()) {
      assertTrue(issue.contains("subclass-owned mutable state"));
    }

    GasDetector onlineGas = new GasDetector("online gas");
    onlineGas.setIsOnlineSignal(true, "plant", "GD-ONLINE");
    ProcessSystem onlineProcess = new ProcessSystem("online detector blocker");
    onlineProcess.add(onlineGas);
    assertFalse(onlineProcess.getTransientTransactionCoverage().isComplete());
    assertTrue(onlineProcess.getTransientTransactionCoverage().getBlockingIssues().get(0).contains("external I/O"));

    GasDetector firstGas = new GasDetector("first gas");
    GasDetector secondGas = new GasDetector("second gas");
    assertThrows(IllegalArgumentException.class,
        () -> secondGas.restoreTransientState(firstGas.captureTransientState()));
    assertThrows(IllegalArgumentException.class, () -> firstGas.restoreTransientState(null));

    FireDetector firstFire = new FireDetector("first fire");
    FireDetector secondFire = new FireDetector("second fire");
    assertThrows(IllegalArgumentException.class,
        () -> secondFire.restoreTransientState(firstFire.captureTransientState()));
    assertThrows(IllegalArgumentException.class, () -> firstFire.restoreTransientState(null));
  }

  private static void assertCompleteCoverage(TransientTransactionCoverage coverage, int expectedCount) {
    assertEquals(expectedCount, coverage.getProcessElementCount());
    assertEquals(expectedCount, coverage.getParticipantCount());
    assertTrue(coverage.isComplete(), coverage.getBlockingIssues().toString());
  }
}
