package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.dynamics.TransientTransactionCoverage;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.measurementdevice.vfm.SoftSensor;
import neqsim.process.measurementdevice.vfm.SoftSensor.PropertyType;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/** Rollback, restart, and multi-area evidence for state-mutating derived instruments. */
class StatefulDerivedInstrumentTransientStateTransactionTest extends neqsim.NeqSimTest {
  @Test
  void multiAreaRollbackRestoresCachedDerivedStateBindingsAndConfiguration() {
    StreamInterface phStream = createAqueousStream("pH source");
    pHProbe probe = new pHProbe("AIT-PH-100", phStream);
    probe.run();
    double expectedPH = probe.getMeasuredValue();

    StreamInterface softSensorStream = createSoftSensorStream("soft-sensor source");
    SoftSensor softSensor = new SoftSensor("AIT-SS-100", softSensorStream, PropertyType.DENSITY);
    softSensor.setInput("pressure", 30.0);
    softSensor.setInput("temperature", 300.0);
    double expectedEstimate = softSensor.estimate();
    double[] expectedSensitivity = softSensor.getSensitivity().clone();

    PipeBeggsAndBrills pipe = createSolvedPipe();
    FlowInducedVibrationAnalyser vibration = new FlowInducedVibrationAnalyser("AIT-FIV-100", pipe);
    vibration.setMethod("LOF");
    vibration.setSupportArrangement("Stiff");
    vibration.setSupportDistance(3.0);
    double expectedVibration = vibration.getMeasuredValue();

    ProcessSystem chemistryArea = new ProcessSystem("chemistry area");
    chemistryArea.add(probe);
    ProcessSystem inferenceArea = new ProcessSystem("inference area");
    inferenceArea.add(softSensor);
    ProcessSystem integrityArea = new ProcessSystem("integrity area");
    integrityArea.add(vibration);
    ProcessModel model = new ProcessModel();
    model.add("chemistry", chemistryArea);
    model.add("inference", inferenceArea);
    model.add("integrity", integrityArea);

    assertCompleteCoverage(model.getTransientTransactionCoverage(), 3);
    String probeIdentity = probe.getTransientStateIdentity();
    String softSensorIdentity = softSensor.getTransientStateIdentity();
    String vibrationIdentity = vibration.getTransientStateIdentity();

    TransientStepTransaction transaction = model.beginTransientStepTransaction();
    probe.setName("trial pH");
    probe.setAlkalinity(50.0);
    probe.setStream(createAqueousStream("trial pH source"));
    probe.run();
    softSensor.setName("trial soft sensor");
    softSensor.setPropertyType(PropertyType.WATER_CUT);
    softSensor.setInput("pressure", 5.0);
    softSensor.setInput("temperature", 330.0);
    softSensor.getSensitivity();
    vibration.setName("trial vibration");
    vibration.setMethod("FRMS");
    vibration.setSupportArrangement("Flexible");
    vibration.setSupportDistance(12.0);
    vibration.setSegment(0);
    vibration.getMeasuredValue();
    transaction.rollback();

    assertEquals(probeIdentity, probe.getTransientStateIdentity());
    assertEquals(softSensorIdentity, softSensor.getTransientStateIdentity());
    assertEquals(vibrationIdentity, vibration.getTransientStateIdentity());
    assertEquals("AIT-PH-100", probe.getName());
    assertSame(phStream, probe.getStream());
    assertEquals(0.0, probe.getAlkalinity(), 0.0);
    assertEquals(expectedPH, probe.getMeasuredValue(), 0.0, "rollback must restore the cached pH exactly");
    assertEquals("AIT-SS-100", softSensor.getName());
    assertSame(softSensorStream, softSensor.getStream());
    assertEquals(PropertyType.DENSITY, softSensor.getPropertyType());
    assertArrayEquals(expectedSensitivity, softSensor.getLastSensitivity(), 0.0);
    assertEquals(expectedEstimate, softSensor.estimate(), 1.0e-12);
    assertEquals("AIT-FIV-100", vibration.getName());
    assertEquals("LOF", vibration.getMethod());
    assertEquals("Stiff", vibration.getSupportArrangement());
    assertEquals(3.0, vibration.getSupportDistance(), 0.0);
    assertEquals(expectedVibration, vibration.getMeasuredValue(), 0.0);

    TransientStepTransaction accepted = model.beginTransientStepTransaction();
    probe.setAlkalinity(25.0);
    softSensor.setPropertyType(PropertyType.GOR);
    vibration.setSupportArrangement("Medium");
    accepted.commit();
    assertEquals(25.0, probe.getAlkalinity(), 0.0);
    assertEquals(PropertyType.GOR, softSensor.getPropertyType());
    assertEquals("Medium", vibration.getSupportArrangement());
  }

  @Test
  void serializedClosedSnapshotsRestoreDerivedInstrumentContinuation() throws Exception {
    pHProbe probe = new pHProbe("AIT-PH-200", createAqueousStream("restart pH source"));
    probe.run();
    double expectedPH = probe.getMeasuredValue();
    pHProbe.PHProbeState probeState = probe.captureTransientState();

    SoftSensor softSensor = new SoftSensor("AIT-SS-200", createSoftSensorStream("restart soft source"),
        PropertyType.DENSITY);
    softSensor.setInput("pressure", 30.0);
    softSensor.setInput("temperature", 300.0);
    softSensor.estimate();
    double[] expectedSensitivity = softSensor.getSensitivity().clone();
    SoftSensor.SoftSensorState softSensorState = softSensor.captureTransientState();

    FlowInducedVibrationAnalyser vibration = new FlowInducedVibrationAnalyser("AIT-FIV-200", createSolvedPipe());
    vibration.setMethod("LOF");
    vibration.setSupportArrangement("Stiff");
    double expectedVibration = vibration.getMeasuredValue();
    FlowInducedVibrationAnalyser.FlowInducedVibrationAnalyserState vibrationState = vibration.captureTransientState();

    byte[] serialized;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(probe);
      output.writeObject(probeState);
      output.writeObject(softSensor);
      output.writeObject(softSensorState);
      output.writeObject(vibration);
      output.writeObject(vibrationState);
      serialized = bytes.toByteArray();
    }

    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      pHProbe restoredProbe = (pHProbe) input.readObject();
      pHProbe.PHProbeState restoredProbeState = (pHProbe.PHProbeState) input.readObject();
      SoftSensor restoredSoftSensor = (SoftSensor) input.readObject();
      SoftSensor.SoftSensorState restoredSoftSensorState = (SoftSensor.SoftSensorState) input.readObject();
      FlowInducedVibrationAnalyser restoredVibration = (FlowInducedVibrationAnalyser) input.readObject();
      FlowInducedVibrationAnalyser.FlowInducedVibrationAnalyserState restoredVibrationState = (FlowInducedVibrationAnalyser.FlowInducedVibrationAnalyserState) input
          .readObject();

      restoredProbe.setAlkalinity(50.0);
      restoredProbe.restoreTransientState(restoredProbeState);
      restoredSoftSensor.setPropertyType(PropertyType.WATER_CUT);
      restoredSoftSensor.restoreTransientState(restoredSoftSensorState);
      restoredVibration.setMethod("FRMS");
      restoredVibration.restoreTransientState(restoredVibrationState);

      assertEquals(expectedPH, restoredProbe.getMeasuredValue(), 0.0);
      assertArrayEquals(expectedSensitivity, restoredSoftSensor.getLastSensitivity(), 0.0);
      assertEquals(PropertyType.DENSITY, restoredSoftSensor.getPropertyType());
      assertEquals(expectedVibration, restoredVibration.getMeasuredValue(), 0.0);
    }
  }

  @Test
  void descendantsOnlineModesNullAndForeignSnapshotsFailClosed() {
    StreamInterface phStream = createAqueousStream("coverage pH source");
    StreamInterface softStream = createSoftSensorStream("coverage soft source");
    PipeBeggsAndBrills pipe = createSolvedPipe();
    ProcessSystem descendants = new ProcessSystem("derived instrument descendants");
    descendants.add(new pHProbe(phStream) {
      private static final long serialVersionUID = 1000L;
    });
    descendants.add(new SoftSensor("derived soft sensor", softStream, PropertyType.DENSITY) {
      private static final long serialVersionUID = 1000L;
    });
    descendants.add(new FlowInducedVibrationAnalyser("derived vibration", pipe) {
      private static final long serialVersionUID = 1000L;
    });
    TransientTransactionCoverage descendantCoverage = descendants.getTransientTransactionCoverage();
    assertEquals(3, descendantCoverage.getParticipantCount());
    assertFalse(descendantCoverage.isComplete());
    assertEquals(3, descendantCoverage.getBlockingIssues().size());
    for (String issue : descendantCoverage.getBlockingIssues()) {
      assertTrue(issue.contains("subclass-owned mutable state"));
    }

    SoftSensor online = new SoftSensor("online soft sensor", softStream, PropertyType.DENSITY);
    online.setIsOnlineSignal(true, "plant", "tag");
    ProcessSystem onlineProcess = new ProcessSystem("online derived instrument");
    onlineProcess.add(online);
    assertFalse(onlineProcess.getTransientTransactionCoverage().isComplete());
    assertTrue(onlineProcess.getTransientTransactionCoverage().getBlockingIssues().get(0).contains("external I/O"));

    pHProbe firstProbe = new pHProbe(phStream);
    pHProbe secondProbe = new pHProbe(phStream);
    assertThrows(IllegalArgumentException.class,
        () -> secondProbe.restoreTransientState(firstProbe.captureTransientState()));
    assertThrows(IllegalArgumentException.class, () -> firstProbe.restoreTransientState(null));
    SoftSensor firstSoft = new SoftSensor("first soft", softStream, PropertyType.DENSITY);
    SoftSensor secondSoft = new SoftSensor("second soft", softStream, PropertyType.DENSITY);
    assertThrows(IllegalArgumentException.class,
        () -> secondSoft.restoreTransientState(firstSoft.captureTransientState()));
    assertThrows(IllegalArgumentException.class, () -> firstSoft.restoreTransientState(null));
    FlowInducedVibrationAnalyser firstVibration = new FlowInducedVibrationAnalyser("first vibration", pipe);
    FlowInducedVibrationAnalyser secondVibration = new FlowInducedVibrationAnalyser("second vibration", pipe);
    assertThrows(IllegalArgumentException.class,
        () -> secondVibration.restoreTransientState(firstVibration.captureTransientState()));
    assertThrows(IllegalArgumentException.class, () -> firstVibration.restoreTransientState(null));
  }

  private static StreamInterface createAqueousStream(String name) {
    SystemInterface fluid = new SystemSrkCPAstatoil(318.15, 50.0);
    fluid.addComponent("nitrogen", 1.205);
    fluid.addComponent("CO2", 1.340);
    fluid.addComponent("methane", 87.974);
    fluid.addComponent("ethane", 5.258);
    fluid.addComponent("propane", 3.283);
    fluid.addComponent("nC10", 14.053);
    fluid.addComponent("water", 141.053);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    Stream stream = new Stream(name, fluid);
    stream.run();
    return stream;
  }

  private static StreamInterface createSoftSensorStream(String name) {
    SystemInterface fluid = new SystemSrkEos(300.0, 30.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("n-heptane", 0.15);
    fluid.addComponent("water", 0.05);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream stream = new Stream(name, fluid);
    stream.setFlowRate(1000.0, "kg/hr");
    stream.run();
    return stream;
  }

  private static PipeBeggsAndBrills createSolvedPipe() {
    SystemInterface fluid = new SystemSrkEos(298.15, 70.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(100.0, "kg/hr");
    Stream stream = new Stream("vibration source", fluid);
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("vibration pipe", stream);
    pipe.setDiameter(0.1);
    pipe.setThickness(0.01);
    pipe.setLength(50.0);
    pipe.setElevation(0.0);
    pipe.setPipeWallRoughness(1.0e-5);
    pipe.setNumberOfIncrements(10);
    ProcessSystem process = new ProcessSystem("vibration source process");
    process.add(stream);
    process.add(pipe);
    process.run();
    return pipe;
  }

  private static void assertCompleteCoverage(TransientTransactionCoverage coverage, int expectedCount) {
    assertEquals(expectedCount, coverage.getProcessElementCount());
    assertEquals(expectedCount, coverage.getParticipantCount());
    assertTrue(coverage.isComplete(), coverage.getBlockingIssues().toString());
  }
}
