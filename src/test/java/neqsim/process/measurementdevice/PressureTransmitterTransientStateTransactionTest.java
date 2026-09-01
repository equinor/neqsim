package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.alarm.AlarmState;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.dynamics.TransientStepIdentifier;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.dynamics.TransientTransactionCoverage;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Quantitative rollback, replay, coverage, and restart evidence for pressure-transmitter state. */
class PressureTransmitterTransientStateTransactionTest extends neqsim.NeqSimTest {
  @Test
  void rollbackRestoresSignalAlarmConfigurationBindingsAndExactReplay() {
    PressureTransmitter transmitter = createTransmitter("PT-100", 50.0);
    Stream originalStream = (Stream) transmitter.getStream();
    transmitter.setTagNumber("T-100");
    transmitter.setMaximumValue(100.0);
    transmitter.setMinimumValue(0.0);
    transmitter.setLogging(true);
    transmitter.setOnlineMeasurementValue(49.5, "bara");
    transmitter.setDelaySteps(2);
    transmitter.setNoiseStdDev(0.35);
    transmitter.setRandomSeed(73013L);
    transmitter.setFirstOrderTimeConstant(2.0);
    transmitter.setFault(SensorFaultType.LINEAR_DRIFT, 0.15);
    transmitter.setConditionAnalysis(false);
    transmitter.setQualityCheckMessage("captured");
    transmitter.setConditionAnalysisMaxDeviation(1.25);
    transmitter.setTag("PT-100");
    transmitter.setTagRole(InstrumentTagRole.BENCHMARK);
    transmitter.setFieldValue(49.0);
    AlarmConfig alarmConfig = AlarmConfig.builder().highLimit(55.0).delay(2.0).unit("bara").build();
    transmitter.setAlarmConfig(alarmConfig);
    AlarmState originalAlarmState = transmitter.getAlarmState();

    // Establish non-empty delay/filter/fault/RNG history before the rollback point.
    transmitter.getMeasuredValue();
    transmitter.getMeasuredValue();

    ProcessSystem process = new ProcessSystem("pressure transmitter transaction");
    process.add(transmitter);
    TransientTransactionCoverage coverage = process.getTransientTransactionCoverage();
    assertEquals(1, coverage.getProcessElementCount());
    assertEquals(1, coverage.getParticipantCount());
    assertTrue(coverage.isComplete());

    String stateIdentity = transmitter.getTransientStateIdentity();
    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    List<Double> trialValues = new ArrayList<Double>();
    for (int i = 0; i < 4; i++) {
      trialValues.add(transmitter.getMeasuredValue());
    }
    assertTrue(transmitter.evaluateAlarm(60.0, 1.0, 1.0).isEmpty());

    transmitter.setName("trial transmitter");
    transmitter.setTagNumber("TRIAL");
    transmitter.setUnit("Pa");
    transmitter.setMaximumValue(999.0);
    transmitter.setMinimumValue(-999.0);
    transmitter.setLogging(false);
    transmitter.setOnlineMeasurementValue(1.0, "Pa");
    transmitter.setDelaySteps(0);
    transmitter.setNoiseStdDev(9.0);
    transmitter.setRandomSeed(1L);
    transmitter.setFirstOrderTimeConstant(0.0);
    transmitter.clearFault();
    transmitter.setConditionAnalysis(true);
    transmitter.setQualityCheckMessage("trial");
    transmitter.setConditionAnalysisMaxDeviation(99.0);
    transmitter.setTag("TRIAL");
    transmitter.setTagRole(InstrumentTagRole.INPUT);
    transmitter.setFieldValue(1.0);
    transmitter.setAlarmConfig(null);
    transmitter.setStream(createTransmitter("trial stream transmitter", 70.0).getStream());
    transaction.rollback();

    assertEquals(stateIdentity, transmitter.getTransientStateIdentity());
    assertEquals("PT-100", transmitter.getName());
    assertEquals("T-100", transmitter.getTagNumber());
    assertSame(originalStream, transmitter.getStream());
    assertEquals("bar", transmitter.getUnit());
    assertEquals(100.0, transmitter.getMaximumValue(), 0.0);
    assertEquals(0.0, transmitter.getMinimumValue(), 0.0);
    assertTrue(transmitter.isLogging());
    assertEquals(49.5, transmitter.getOnlineMeasurementValue(), 0.0);
    assertEquals(2, transmitter.getDelaySteps());
    assertEquals(0.35, transmitter.getNoiseStdDev(), 0.0);
    assertEquals(2.0, transmitter.getFirstOrderTimeConstant(), 0.0);
    assertEquals(SensorFaultType.LINEAR_DRIFT, transmitter.getFaultType());
    assertEquals(0.15, transmitter.getFaultParameter(), 0.0);
    assertFalse(transmitter.doConditionAnalysis());
    assertEquals("captured", transmitter.getConditionAnalysisMessage());
    assertEquals(1.25, transmitter.getConditionAnalysisMaxDeviation(), 0.0);
    assertEquals("PT-100", transmitter.getTag());
    assertEquals(InstrumentTagRole.BENCHMARK, transmitter.getTagRole());
    assertEquals(49.0, transmitter.getFieldValue(), 0.0);
    assertSame(alarmConfig, transmitter.getAlarmConfig());
    assertSame(originalAlarmState, transmitter.getAlarmState());

    for (int i = 0; i < trialValues.size(); i++) {
      assertEquals(trialValues.get(i), transmitter.getMeasuredValue(), 0.0,
          "rollback must replay Gaussian, drift, filter, and delay state exactly");
    }
    assertTrue(transmitter.evaluateAlarm(60.0, 1.0, 1.0).isEmpty(),
        "rolled-back pending alarm time must not activate one sample early");
    assertEquals(1, transmitter.evaluateAlarm(60.0, 1.0, 2.0).size());
  }

  @Test
  void pressureTransmitterAndBasePidReplayAsOneProcessTransaction() {
    PressureTransmitter transmitter = createTransmitter("PT-200", 50.0);
    transmitter.setNoiseStdDev(0.2);
    transmitter.setRandomSeed(991L);
    transmitter.setDelaySteps(1);
    transmitter.setFirstOrderTimeConstant(1.5);

    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("PC-200");
    controller.setTransmitter(transmitter);
    controller.setUnit("bara");
    controller.setControllerSetPoint(55.0);
    controller.setControllerParameters(2.0, 10.0, 0.5);

    ProcessSystem process = new ProcessSystem("instrument controller transaction");
    process.add(transmitter);
    process.add(controller);
    TransientTransactionCoverage coverage = process.getTransientTransactionCoverage();
    assertEquals(2, coverage.getProcessElementCount());
    assertEquals(2, coverage.getParticipantCount());
    assertTrue(coverage.isComplete());

    UUID stepId = TransientStepIdentifier.deterministicPhysicalStep("instrument-controller-replay", 0L);
    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    process.runTransient(0.5, stepId);
    double trialControllerResponse = controller.getResponse();
    double trialNextMeasurement = transmitter.getMeasuredValue();
    transaction.rollback();

    assertEquals(0.0, process.getTime(), 0.0);
    process.runTransient(0.5, stepId);
    assertEquals(trialControllerResponse, controller.getResponse(), 1.0e-12);
    assertEquals(trialNextMeasurement, transmitter.getMeasuredValue(), 0.0);
    assertEquals(0.5, process.getTime(), 0.0);
  }

  @Test
  void serializedSnapshotRestoresStableIdentityAndRandomContinuation() throws Exception {
    PressureTransmitter transmitter = createTransmitter("PT-300", 50.0);
    transmitter.setNoiseStdDev(0.5);
    transmitter.setRandomSeed(123456L);
    String identity = transmitter.getTransientStateIdentity();
    PressureTransmitter.PressureTransmitterState snapshot = transmitter.captureTransientState();
    double expectedNextValue = transmitter.getMeasuredValue();

    byte[] serialized;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(transmitter);
      output.writeObject(snapshot);
      serialized = bytes.toByteArray();
    }

    PressureTransmitter restoredTransmitter;
    PressureTransmitter.PressureTransmitterState restoredSnapshot;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      restoredTransmitter = (PressureTransmitter) input.readObject();
      restoredSnapshot = (PressureTransmitter.PressureTransmitterState) input.readObject();
    }

    assertEquals(identity, restoredTransmitter.getTransientStateIdentity());
    restoredTransmitter.restoreTransientState(restoredSnapshot);
    assertEquals(identity, restoredTransmitter.getTransientStateIdentity());
    assertEquals(expectedNextValue, restoredTransmitter.getMeasuredValue(), 0.0);
  }

  @Test
  void subclassWithoutExtendedSnapshotIsReportedBeforeMutation() {
    ProcessSystem process = new ProcessSystem("pressure transmitter subclass coverage");
    process.add(new StatefulPressureTransmitterSubclass(createTransmitter("source", 50.0).getStream()));

    TransientTransactionCoverage coverage = process.getTransientTransactionCoverage();

    assertEquals(1, coverage.getProcessElementCount());
    assertEquals(1, coverage.getParticipantCount());
    assertFalse(coverage.isComplete());
    assertTrue(coverage.getBlockingIssues().get(0).contains("subclass-owned mutable state"));
  }

  private static PressureTransmitter createTransmitter(String name, double pressureBara) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, pressureBara);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream stream = new Stream(name + " stream", fluid);
    stream.setPressure(pressureBara, "bara");
    return new PressureTransmitter(name, stream);
  }

  private static final class StatefulPressureTransmitterSubclass extends PressureTransmitter {
    private static final long serialVersionUID = 1000L;
    @SuppressWarnings("unused")
    private double customState;

    private StatefulPressureTransmitterSubclass(neqsim.process.equipment.stream.StreamInterface stream) {
      super("custom pressure transmitter", stream);
    }
  }
}
