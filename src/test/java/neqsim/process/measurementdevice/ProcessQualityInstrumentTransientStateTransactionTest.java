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
import neqsim.process.measurementdevice.CompositionAnalyzer.AnalyzerPhase;
import neqsim.process.measurementdevice.FlowRatioMeter.FlowBasis;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Quantitative rollback, replay, multi-area coverage, and restart evidence for local process-quality instruments.
 */
class ProcessQualityInstrumentTransientStateTransactionTest extends neqsim.NeqSimTest {
  @Test
  void multiAreaRollbackRestoresBindingsConfigurationAndExactSignalReplay() {
    StreamInterface source = createStream("quality source", 120.0);
    StreamInterface denominator = createStream("ratio denominator", 80.0);

    MolarMassAnalyser molarMass = new MolarMassAnalyser("AIT-MM-100", source);
    WaterContentAnalyser waterContent = new WaterContentAnalyser("AIT-W-100", source);
    CompositionAnalyzer composition = new CompositionAnalyzer("AIT-Z-100", source, "methane", AnalyzerPhase.OVERALL);
    FlowRatioMeter flowRatio = new FlowRatioMeter("FIT-R-100", source, denominator, FlowBasis.MASS);
    ImpurityMonitor impurity = new ImpurityMonitor("AIT-I-100", source);
    impurity.addTrackedComponent("methane", 0.50);

    List<MeasurementDeviceBaseClass> devices = new ArrayList<MeasurementDeviceBaseClass>();
    devices.add(molarMass);
    devices.add(waterContent);
    devices.add(composition);
    devices.add(flowRatio);
    devices.add(impurity);
    for (int i = 0; i < devices.size(); i++) {
      configureSignalDynamics(devices.get(i), 9100L + i);
      devices.get(i).getMeasuredValue();
      devices.get(i).getMeasuredValue();
    }

    AlarmConfig alarmConfig = AlarmConfig.builder().highLimit(0.0).delay(2.0).unit("gr/mol").build();
    molarMass.setAlarmConfig(alarmConfig);
    AlarmState originalAlarmState = molarMass.getAlarmState();

    ProcessSystem qualityArea = new ProcessSystem("quality area");
    qualityArea.add(molarMass);
    qualityArea.add(waterContent);
    qualityArea.add(composition);
    ProcessSystem meteringArea = new ProcessSystem("metering area");
    meteringArea.add(flowRatio);
    meteringArea.add(impurity);

    assertCompleteCoverage(qualityArea.getTransientTransactionCoverage(), 3);
    assertCompleteCoverage(meteringArea.getTransientTransactionCoverage(), 2);

    ProcessModel model = new ProcessModel();
    model.add("quality", qualityArea);
    model.add("metering", meteringArea);
    assertCompleteCoverage(model.getTransientTransactionCoverage(), 5);

    StreamInterface originalMolarMassStream = molarMass.getStream();
    StreamInterface originalWaterContentStream = waterContent.getStream();
    StreamInterface originalCompositionStream = composition.getStream();
    StreamInterface originalNumeratorStream = flowRatio.getNumeratorStream();
    StreamInterface originalDenominatorStream = flowRatio.getDenominatorStream();
    StreamInterface originalImpurityStream = impurity.getStream();
    List<String> identities = transientIdentities(molarMass, waterContent, composition, flowRatio, impurity);

    TransientStepTransaction transaction = model.beginTransientStepTransaction();
    List<double[]> trialSequence = new ArrayList<double[]>();
    for (int i = 0; i < 4; i++) {
      trialSequence.add(readAll(molarMass, waterContent, composition, flowRatio, impurity));
    }
    assertTrue(molarMass.evaluateAlarm(100.0, 1.0, 1.0).isEmpty());

    StreamInterface trialStream = createStream("trial source", 15.0);
    for (MeasurementDeviceBaseClass device : devices) {
      device.setName("trial " + device.getName());
      device.setUnit("trial");
      device.setDelaySteps(0);
      device.setNoiseStdDev(9.0);
      device.setRandomSeed(1L);
      device.setFirstOrderTimeConstant(0.0);
      device.clearFault();
      device.setAlarmConfig(null);
    }
    molarMass.setStream(trialStream);
    waterContent.setStream(trialStream);
    composition.setStream(trialStream);
    impurity.setStream(trialStream);
    impurity.setPrimaryComponent("water");
    impurity.addTrackedComponent("hydrogen", 0.01);
    transaction.rollback();

    assertEquals(identities, transientIdentities(molarMass, waterContent, composition, flowRatio, impurity));
    assertEquals("AIT-MM-100", molarMass.getName());
    assertEquals("AIT-W-100", waterContent.getName());
    assertEquals("AIT-Z-100", composition.getName());
    assertEquals("FIT-R-100", flowRatio.getName());
    assertEquals("AIT-I-100", impurity.getName());
    assertSame(originalMolarMassStream, molarMass.getStream());
    assertSame(originalWaterContentStream, waterContent.getStream());
    assertSame(originalCompositionStream, composition.getStream());
    assertSame(originalNumeratorStream, flowRatio.getNumeratorStream());
    assertSame(originalDenominatorStream, flowRatio.getDenominatorStream());
    assertSame(originalImpurityStream, impurity.getStream());
    assertSame(alarmConfig, molarMass.getAlarmConfig());
    assertSame(originalAlarmState, molarMass.getAlarmState());
    assertFalse(impurity.getFullReport().containsKey("hydrogen"));

    for (double[] expected : trialSequence) {
      double[] actual = readAll(molarMass, waterContent, composition, flowRatio, impurity);
      for (int i = 0; i < expected.length; i++) {
        assertEquals(expected[i], actual[i], 0.0,
            "rollback must replay Gaussian, drift, filter, and delay state exactly");
      }
    }
    assertTrue(molarMass.evaluateAlarm(100.0, 1.0, 1.0).isEmpty());
    assertEquals(1, molarMass.evaluateAlarm(100.0, 1.0, 2.0).size());
  }

  @Test
  void serializedImpuritySnapshotRestoresSubclassStateAndRandomContinuation() throws Exception {
    ImpurityMonitor monitor = new ImpurityMonitor("AIT-I-200", createStream("restart source", 100.0));
    monitor.addTrackedComponent("methane", 0.50);
    configureSignalDynamics(monitor, 7151L);
    monitor.getMeasuredValue();
    String identity = monitor.getTransientStateIdentity();
    ImpurityMonitor.ImpurityMonitorState snapshot = monitor.captureTransientState();
    double expectedNextValue = monitor.getMeasuredValue();

    byte[] serialized;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(monitor);
      output.writeObject(snapshot);
      serialized = bytes.toByteArray();
    }

    ImpurityMonitor restoredMonitor;
    ImpurityMonitor.ImpurityMonitorState restoredSnapshot;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      restoredMonitor = (ImpurityMonitor) input.readObject();
      restoredSnapshot = (ImpurityMonitor.ImpurityMonitorState) input.readObject();
    }

    assertEquals(identity, restoredMonitor.getTransientStateIdentity());
    restoredMonitor.setPrimaryComponent("water");
    restoredMonitor.addTrackedComponent("hydrogen", 0.01);
    restoredMonitor.restoreTransientState(restoredSnapshot);
    assertEquals(identity, restoredMonitor.getTransientStateIdentity());
    assertFalse(restoredMonitor.getFullReport().containsKey("hydrogen"));
    assertEquals(expectedNextValue, restoredMonitor.getMeasuredValue(), 0.0);
  }

  @Test
  void descendantsOnlineModesAndForeignSnapshotsFailClosed() {
    StreamInterface source = createStream("coverage source", 100.0);
    StreamInterface denominator = createStream("coverage denominator", 75.0);
    ProcessSystem process = new ProcessSystem("quality instrument blocker coverage");
    process.add(new MolarMassAnalyser(source) {
      private static final long serialVersionUID = 1000L;
    });
    process.add(new WaterContentAnalyser(source) {
      private static final long serialVersionUID = 1000L;
    });
    process.add(new CompositionAnalyzer(source, "methane", AnalyzerPhase.OVERALL) {
      private static final long serialVersionUID = 1000L;
    });
    process.add(new FlowRatioMeter(source, denominator, FlowBasis.MASS) {
      private static final long serialVersionUID = 1000L;
    });
    process.add(new ImpurityMonitor(source) {
      private static final long serialVersionUID = 1000L;
    });

    TransientTransactionCoverage coverage = process.getTransientTransactionCoverage();
    assertEquals(5, coverage.getProcessElementCount());
    assertEquals(5, coverage.getParticipantCount());
    assertFalse(coverage.isComplete());
    assertEquals(5, coverage.getBlockingIssues().size());
    for (String issue : coverage.getBlockingIssues()) {
      assertTrue(issue.contains("subclass-owned mutable state"));
    }

    MolarMassAnalyser online = new MolarMassAnalyser(source);
    online.setIsOnlineSignal(true, "plant", "tag");
    ProcessSystem onlineProcess = new ProcessSystem("online blocker");
    onlineProcess.add(online);
    assertFalse(onlineProcess.getTransientTransactionCoverage().isComplete());
    assertTrue(onlineProcess.getTransientTransactionCoverage().getBlockingIssues().get(0).contains("external I/O"));

    MolarMassAnalyser first = new MolarMassAnalyser(source);
    MolarMassAnalyser second = new MolarMassAnalyser(source);
    MolarMassAnalyser.MolarMassAnalyserState foreign = first.captureTransientState();
    assertThrows(IllegalArgumentException.class, () -> second.restoreTransientState(foreign));
  }

  private static void configureSignalDynamics(MeasurementDeviceBaseClass device, long seed) {
    device.setDelaySteps(2);
    device.setNoiseStdDev(0.05);
    device.setRandomSeed(seed);
    device.setFirstOrderTimeConstant(2.0);
    device.setFault(SensorFaultType.LINEAR_DRIFT, 0.01);
  }

  private static double[] readAll(MolarMassAnalyser molarMass, WaterContentAnalyser waterContent,
      CompositionAnalyzer composition, FlowRatioMeter flowRatio, ImpurityMonitor impurity) {
    return new double[] { molarMass.getMeasuredValue("gr/mol"), waterContent.getMeasuredValue("kg/day"),
        composition.getMeasuredValue("mole/mole"), flowRatio.getMeasuredValue(""), impurity.getMeasuredValue("mol%") };
  }

  private static List<String> transientIdentities(MolarMassAnalyser molarMass, WaterContentAnalyser waterContent,
      CompositionAnalyzer composition, FlowRatioMeter flowRatio, ImpurityMonitor impurity) {
    List<String> identities = new ArrayList<String>();
    identities.add(molarMass.getTransientStateIdentity());
    identities.add(waterContent.getTransientStateIdentity());
    identities.add(composition.getTransientStateIdentity());
    identities.add(flowRatio.getTransientStateIdentity());
    identities.add(impurity.getTransientStateIdentity());
    return identities;
  }

  private static void assertCompleteCoverage(TransientTransactionCoverage coverage, int expectedCount) {
    assertEquals(expectedCount, coverage.getProcessElementCount());
    assertEquals(expectedCount, coverage.getParticipantCount());
    assertTrue(coverage.isComplete(), coverage.getBlockingIssues().toString());
  }

  private static StreamInterface createStream(String name, double massFlowKgPerHour) {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("methane", 0.99);
    fluid.addComponent("water", 0.01);
    fluid.setMultiPhaseCheck(true);
    Stream stream = new Stream(name, fluid);
    stream.setFlowRate(massFlowKgPerHour, "kg/hr");
    stream.run();
    return stream;
  }
}
