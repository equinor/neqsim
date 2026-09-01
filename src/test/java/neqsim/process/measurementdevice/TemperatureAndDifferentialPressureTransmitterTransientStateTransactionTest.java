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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.alarm.AlarmState;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.dynamics.TransientTransactionCoverage;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Quantitative rollback, replay, multi-area coverage, and restart evidence for local temperature and
 * differential-pressure transmitter state.
 */
class TemperatureAndDifferentialPressureTransmitterTransientStateTransactionTest extends neqsim.NeqSimTest {
  @Test
  void multiAreaRollbackRestoresBindingsSignalStateAndExactReplay() {
    TemperatureTransmitter temperature = new TemperatureTransmitter("TT-100",
        createStream("temperature source", 330.0, 60.0));
    StreamInterface originalTemperatureStream = temperature.getStream();
    DifferentialPressureTransmitter differentialPressure = new DifferentialPressureTransmitter("PDT-100",
        createStream("high pressure source", 300.0, 70.0), createStream("low pressure source", 300.0, 50.0));
    StreamInterface originalHighPressureStream = differentialPressure.getHighPressureStream();
    StreamInterface originalLowPressureStream = differentialPressure.getLowPressureStream();

    configureSignalDynamics(temperature, 8123L);
    configureSignalDynamics(differentialPressure, 9917L);
    AlarmConfig temperatureAlarm = AlarmConfig.builder().highLimit(320.0).delay(2.0).unit("K").build();
    AlarmConfig differentialPressureAlarm = AlarmConfig.builder().highLimit(15.0).delay(2.0).unit("bar").build();
    temperature.setAlarmConfig(temperatureAlarm);
    differentialPressure.setAlarmConfig(differentialPressureAlarm);
    AlarmState originalTemperatureAlarmState = temperature.getAlarmState();
    AlarmState originalDifferentialPressureAlarmState = differentialPressure.getAlarmState();

    // Establish non-empty delay, filter, Gaussian-cache, drift, and pending-alarm state.
    temperature.getMeasuredValue();
    differentialPressure.getMeasuredValue();
    temperature.getMeasuredValue();
    differentialPressure.getMeasuredValue();
    assertTrue(temperature.evaluateAlarm(330.0, 1.0, 1.0).isEmpty());
    assertTrue(differentialPressure.evaluateAlarm(20.0, 1.0, 1.0).isEmpty());

    ProcessSystem utilitiesArea = new ProcessSystem("utilities area");
    utilitiesArea.add(temperature);
    ProcessSystem compressionArea = new ProcessSystem("compression area");
    compressionArea.add(differentialPressure);
    assertCompleteCoverage(utilitiesArea.getTransientTransactionCoverage(), 1);
    assertCompleteCoverage(compressionArea.getTransientTransactionCoverage(), 1);

    ProcessModel model = new ProcessModel();
    model.add("utilities", utilitiesArea);
    model.add("compression", compressionArea);
    assertCompleteCoverage(model.getTransientTransactionCoverage(), 2);

    String temperatureIdentity = temperature.getTransientStateIdentity();
    String differentialPressureIdentity = differentialPressure.getTransientStateIdentity();
    TransientStepTransaction transaction = model.beginTransientStepTransaction();
    List<Double> trialTemperatures = new ArrayList<Double>();
    List<Double> trialDifferentialPressures = new ArrayList<Double>();
    for (int i = 0; i < 4; i++) {
      trialTemperatures.add(temperature.getMeasuredValue());
      trialDifferentialPressures.add(differentialPressure.getMeasuredValue());
    }
    assertEquals(1, temperature.evaluateAlarm(330.0, 1.0, 2.0).size());
    assertEquals(1, differentialPressure.evaluateAlarm(20.0, 1.0, 2.0).size());

    temperature.setName("trial temperature");
    temperature.setUnit("C");
    temperature.setDelaySteps(0);
    temperature.setNoiseStdDev(9.0);
    temperature.setFirstOrderTimeConstant(0.0);
    temperature.clearFault();
    temperature.setAlarmConfig(null);
    temperature.setStream(createStream("trial temperature source", 280.0, 10.0));
    differentialPressure.setName("trial differential pressure");
    differentialPressure.setUnit("Pa");
    differentialPressure.setDelaySteps(0);
    differentialPressure.setNoiseStdDev(8.0);
    differentialPressure.setFirstOrderTimeConstant(0.0);
    differentialPressure.clearFault();
    differentialPressure.setAlarmConfig(null);
    transaction.rollback();

    assertEquals(temperatureIdentity, temperature.getTransientStateIdentity());
    assertEquals(differentialPressureIdentity, differentialPressure.getTransientStateIdentity());
    assertEquals("TT-100", temperature.getName());
    assertEquals("PDT-100", differentialPressure.getName());
    assertEquals("K", temperature.getUnit());
    assertEquals("bar", differentialPressure.getUnit());
    assertSame(originalTemperatureStream, temperature.getStream());
    assertSame(originalHighPressureStream, differentialPressure.getHighPressureStream());
    assertSame(originalLowPressureStream, differentialPressure.getLowPressureStream());
    assertSame(temperatureAlarm, temperature.getAlarmConfig());
    assertSame(differentialPressureAlarm, differentialPressure.getAlarmConfig());
    assertSame(originalTemperatureAlarmState, temperature.getAlarmState());
    assertSame(originalDifferentialPressureAlarmState, differentialPressure.getAlarmState());

    for (int i = 0; i < trialTemperatures.size(); i++) {
      assertEquals(trialTemperatures.get(i), temperature.getMeasuredValue(), 0.0,
          "temperature rollback must replay Gaussian, drift, filter, and delay state exactly");
      assertEquals(trialDifferentialPressures.get(i), differentialPressure.getMeasuredValue(), 0.0,
          "differential-pressure rollback must replay Gaussian, drift, filter, and delay state exactly");
    }
    assertEquals(1, temperature.evaluateAlarm(330.0, 1.0, 2.0).size());
    assertEquals(1, differentialPressure.evaluateAlarm(20.0, 1.0, 2.0).size());
  }

  @Test
  void serializedSnapshotsRestoreStableIdentityBindingsAndRandomContinuation() throws Exception {
    TemperatureTransmitter temperature = new TemperatureTransmitter("TT-200",
        createStream("temperature source", 315.0, 40.0));
    DifferentialPressureTransmitter differentialPressure = new DifferentialPressureTransmitter("PDT-200",
        createStream("high pressure source", 300.0, 80.0), createStream("low pressure source", 300.0, 55.0));
    configureSignalDynamics(temperature, 123456L);
    configureSignalDynamics(differentialPressure, 654321L);

    String temperatureIdentity = temperature.getTransientStateIdentity();
    String differentialPressureIdentity = differentialPressure.getTransientStateIdentity();
    TemperatureTransmitter.TemperatureTransmitterState temperatureSnapshot = temperature.captureTransientState();
    DifferentialPressureTransmitter.DifferentialPressureTransmitterState differentialPressureSnapshot = differentialPressure
        .captureTransientState();
    double expectedTemperature = temperature.getMeasuredValue();
    double expectedDifferentialPressure = differentialPressure.getMeasuredValue();

    byte[] serialized;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(temperature);
      output.writeObject(temperatureSnapshot);
      output.writeObject(differentialPressure);
      output.writeObject(differentialPressureSnapshot);
      serialized = bytes.toByteArray();
    }

    TemperatureTransmitter restoredTemperature;
    TemperatureTransmitter.TemperatureTransmitterState restoredTemperatureSnapshot;
    DifferentialPressureTransmitter restoredDifferentialPressure;
    DifferentialPressureTransmitter.DifferentialPressureTransmitterState restoredDifferentialPressureSnapshot;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      restoredTemperature = (TemperatureTransmitter) input.readObject();
      restoredTemperatureSnapshot = (TemperatureTransmitter.TemperatureTransmitterState) input.readObject();
      restoredDifferentialPressure = (DifferentialPressureTransmitter) input.readObject();
      restoredDifferentialPressureSnapshot = (DifferentialPressureTransmitter.DifferentialPressureTransmitterState) input
          .readObject();
    }

    restoredTemperature.restoreTransientState(restoredTemperatureSnapshot);
    restoredDifferentialPressure.restoreTransientState(restoredDifferentialPressureSnapshot);
    assertEquals(temperatureIdentity, restoredTemperature.getTransientStateIdentity());
    assertEquals(differentialPressureIdentity, restoredDifferentialPressure.getTransientStateIdentity());
    assertEquals(expectedTemperature, restoredTemperature.getMeasuredValue(), 0.0);
    assertEquals(expectedDifferentialPressure, restoredDifferentialPressure.getMeasuredValue(), 0.0);
  }

  @Test
  void foreignSnapshotsAreRejectedWithoutMutation() {
    TemperatureTransmitter first = new TemperatureTransmitter("TT-300", createStream("first source", 300.0, 20.0));
    TemperatureTransmitter second = new TemperatureTransmitter("TT-301", createStream("second source", 310.0, 20.0));
    TemperatureTransmitter.TemperatureTransmitterState foreignSnapshot = first.captureTransientState();
    StreamInterface secondStream = second.getStream();

    assertThrows(IllegalArgumentException.class, () -> second.restoreTransientState(foreignSnapshot));
    assertSame(secondStream, second.getStream());
    assertEquals("TT-301", second.getName());
  }

  @Test
  void subclassesAndOnlineBindingsFailCoverageBeforeTrialMutation() {
    ProcessSystem subclassProcess = new ProcessSystem("transmitter subclass coverage");
    subclassProcess.add(new StatefulTemperatureTransmitterSubclass(createStream("temperature", 300.0, 20.0)));
    subclassProcess.add(new StatefulDifferentialPressureTransmitterSubclass(createStream("high pressure", 300.0, 30.0),
        createStream("low pressure", 300.0, 20.0)));
    TransientTransactionCoverage subclassCoverage = subclassProcess.getTransientTransactionCoverage();
    assertEquals(2, subclassCoverage.getProcessElementCount());
    assertEquals(2, subclassCoverage.getParticipantCount());
    assertFalse(subclassCoverage.isComplete());
    assertEquals(2, subclassCoverage.getBlockingIssues().size());
    assertTrue(subclassCoverage.getBlockingIssues().get(0).contains("subclass-owned mutable state"));
    assertThrows(IllegalStateException.class, subclassProcess::beginTransientStepTransaction);

    TemperatureTransmitter onlineTemperature = new TemperatureTransmitter("online TT",
        createStream("online source", 300.0, 20.0));
    onlineTemperature.setIsOnlineSignal(true, "test plant", "test tag");
    ProcessSystem onlineProcess = new ProcessSystem("online transmitter coverage");
    onlineProcess.add(onlineTemperature);
    TransientTransactionCoverage onlineCoverage = onlineProcess.getTransientTransactionCoverage();
    assertEquals(1, onlineCoverage.getProcessElementCount());
    assertEquals(1, onlineCoverage.getParticipantCount());
    assertFalse(onlineCoverage.isComplete());
    assertTrue(onlineCoverage.getBlockingIssues().get(0).contains("external I/O"));
    assertThrows(IllegalStateException.class, onlineProcess::beginTransientStepTransaction);
  }

  private static void configureSignalDynamics(MeasurementDeviceBaseClass transmitter, long seed) {
    transmitter.setDelaySteps(2);
    transmitter.setNoiseStdDev(0.35);
    transmitter.setRandomSeed(seed);
    transmitter.setFirstOrderTimeConstant(2.0);
    transmitter.setFault(SensorFaultType.LINEAR_DRIFT, 0.15);
  }

  private static void assertCompleteCoverage(TransientTransactionCoverage coverage, int expectedCount) {
    assertEquals(expectedCount, coverage.getProcessElementCount());
    assertEquals(expectedCount, coverage.getParticipantCount());
    assertTrue(coverage.isComplete());
    assertTrue(coverage.getBlockingIssues().isEmpty());
  }

  private static Stream createStream(String name, double temperatureKelvin, double pressureBara) {
    SystemSrkEos fluid = new SystemSrkEos(temperatureKelvin, pressureBara);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream stream = new Stream(name, fluid);
    stream.setTemperature(temperatureKelvin, "K");
    stream.setPressure(pressureBara, "bara");
    return stream;
  }

  private static final class StatefulTemperatureTransmitterSubclass extends TemperatureTransmitter {
    private static final long serialVersionUID = 1000L;
    @SuppressWarnings("unused")
    private double customState;

    private StatefulTemperatureTransmitterSubclass(StreamInterface stream) {
      super("custom temperature transmitter", stream);
    }
  }

  private static final class StatefulDifferentialPressureTransmitterSubclass extends DifferentialPressureTransmitter {
    private static final long serialVersionUID = 1000L;
    @SuppressWarnings("unused")
    private double customState;

    private StatefulDifferentialPressureTransmitterSubclass(StreamInterface highPressureStream,
        StreamInterface lowPressureStream) {
      super("custom differential-pressure transmitter", highPressureStream, lowPressureStream);
    }
  }
}
