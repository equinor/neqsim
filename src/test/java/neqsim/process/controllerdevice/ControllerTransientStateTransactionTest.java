package neqsim.process.controllerdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.TransientStepIdentifier;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.dynamics.TransientTransactionCoverage;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Quantitative rollback, replay, coverage, and restart evidence for the base PID state family. */
class ControllerTransientStateTransactionTest extends neqsim.NeqSimTest {
  @Test
  void rollbackRestoresPidHistoryClockConfigurationIdentityAndReplay() {
    PressureTransmitter transmitter = createTransmitter(50.0);
    ControllerDeviceBaseClass controller = createController(transmitter);
    ProcessSystem process = new ProcessSystem("controller transaction");
    process.add(controller);

    TransientTransactionCoverage coverage = process.getTransientTransactionCoverage();
    assertEquals(1, coverage.getProcessElementCount());
    assertEquals(1, coverage.getParticipantCount());
    assertTrue(coverage.isComplete());

    String stateIdentity = controller.getTransientStateIdentity();
    UUID stepId = TransientStepIdentifier.deterministicPhysicalStep("controller-rollback", 0L);
    double initialResponse = controller.getResponse();

    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    controller.runTransient(initialResponse, 1.0, stepId);
    double automaticTrialResponse = controller.getResponse();
    controller.setTransmitter(createTransmitter(60.0));
    controller.setControllerSetPoint(70.0);
    controller.setControllerParameters(9.0, 8.0, 7.0);
    controller.setMode(ControllerDeviceInterface.ControllerMode.MANUAL);
    controller.setManualOutput(11.0);
    assertEquals(1, controller.getEventLog().size());
    assertTrue(controller.hasRunTransient(stepId));
    assertTrue(controller.getIntegralAbsoluteError() > 0.0);
    assertEquals(11.0, controller.getResponse(), 0.0);
    transaction.rollback();

    assertEquals(stateIdentity, controller.getTransientStateIdentity());
    transmitter.getStream().setPressure(51.0, "bara");
    assertEquals(51.0, controller.getMeasuredValue("bara"), 1.0e-12,
        "rollback must restore the original transmitter binding");
    assertEquals(0, controller.getEventLog().size());
    assertFalse(controller.hasRunTransient(stepId));
    assertEquals(0.0, controller.getIntegralAbsoluteError(), 0.0);
    assertEquals(initialResponse, controller.getResponse(), 0.0);
    assertEquals(55.0, controller.getControllerSetPoint(), 0.0);
    assertEquals(2.0, controller.getKp(), 0.0);
    assertEquals(10.0, controller.getTi(), 0.0);
    assertEquals(0.5, controller.getTd(), 0.0);
    assertEquals(ControllerDeviceInterface.ControllerMode.AUTO, controller.getMode());

    transmitter.getStream().setPressure(50.0, "bara");
    controller.runTransient(initialResponse, 1.0, stepId);
    assertEquals(automaticTrialResponse, controller.getResponse(), 1.0e-12);
    assertEquals(1, controller.getEventLog().size());
    assertEquals(1.0, controller.getEventLog().get(0).getTime(), 0.0);
  }

  @Test
  void rollbackRestoresMutableReferenceDesignationAndOriginalBinding() {
    ControllerDeviceBaseClass controller = createController(createTransmitter(50.0));
    neqsim.process.equipment.iec81346.ReferenceDesignation original = neqsim.process.equipment.iec81346.ReferenceDesignation
        .parse("=A1-B1+P1");
    controller.setReferenceDesignation(original);
    ProcessSystem process = new ProcessSystem("controller designation rollback");
    process.add(controller);

    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    original.setFunctionDesignation("TRIAL");
    original.setLetterCode(neqsim.process.equipment.iec81346.IEC81346LetterCode.K);
    original.setSequenceNumber(9);
    controller.setReferenceDesignation(neqsim.process.equipment.iec81346.ReferenceDesignation.parse("=OTHER-K9+TRIAL"));
    transaction.rollback();

    assertSame(original, controller.getReferenceDesignation(),
        "rollback must restore the original engineering-reference binding");
    assertEquals("=A1-B1+P1", controller.getReferenceDesignationString());
    assertEquals(neqsim.process.equipment.iec81346.IEC81346LetterCode.B,
        controller.getReferenceDesignation().getLetterCode());
    assertEquals(1, controller.getReferenceDesignation().getSequenceNumber());
  }

  @Test
  void processSystemTransactionalStepCommitsControllerAndProcessStateTogether() {
    ControllerDeviceBaseClass controller = createController(createTransmitter(50.0));
    ProcessSystem process = new ProcessSystem("controller commit");
    process.add(controller);
    UUID stepId = TransientStepIdentifier.deterministicPhysicalStep("controller-commit", 0L);

    process.runTransientTransactional(0.5, stepId);

    assertEquals(0.5, process.getTime(), 0.0);
    assertEquals(stepId, process.getCalculationIdentifier());
    assertTrue(controller.hasRunTransient(stepId));
    assertEquals(1, controller.getEventLog().size());
    assertEquals(0.5, controller.getEventLog().get(0).getTime(), 0.0);
  }

  @Test
  void closedSnapshotSurvivesJavaSerializationAndKeepsStableIdentity() throws Exception {
    ControllerDeviceBaseClass controller = createController(createTransmitter(50.0));
    String identity = controller.getTransientStateIdentity();
    ControllerDeviceBaseClass.ControllerTransientState snapshot = controller.captureTransientState();

    byte[] serialized;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(controller);
      output.writeObject(snapshot);
      serialized = bytes.toByteArray();
    }

    ControllerDeviceBaseClass restoredController;
    ControllerDeviceBaseClass.ControllerTransientState restoredSnapshot;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      restoredController = (ControllerDeviceBaseClass) input.readObject();
      restoredSnapshot = (ControllerDeviceBaseClass.ControllerTransientState) input.readObject();
    }

    assertEquals(identity, restoredController.getTransientStateIdentity());
    restoredController.restoreTransientState(restoredSnapshot);
    assertEquals(identity, restoredController.getTransientStateIdentity());
    assertEquals(controller.getControllerSetPoint(), restoredController.getControllerSetPoint(), 0.0);
    assertEquals(controller.getKp(), restoredController.getKp(), 0.0);
  }

  @Test
  void subclassWithoutExtendedSnapshotIsReportedBeforeMutation() {
    ProcessSystem process = new ProcessSystem("subclass coverage");
    process.add(new StatefulControllerSubclass("custom controller"));

    TransientTransactionCoverage coverage = process.getTransientTransactionCoverage();

    assertEquals(1, coverage.getProcessElementCount());
    assertEquals(1, coverage.getParticipantCount());
    assertFalse(coverage.isComplete());
    assertTrue(coverage.getBlockingIssues().get(0).contains("subclass-owned mutable state"));
  }

  private static ControllerDeviceBaseClass createController(PressureTransmitter transmitter) {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("PC-100");
    controller.setTransmitter(transmitter);
    controller.setUnit("bara");
    controller.setControllerSetPoint(55.0);
    controller.setControllerParameters(2.0, 10.0, 0.5);
    controller.setDerivativeFilterTime(0.25);
    controller.setOutputLimits(0.0, 100.0);
    controller.setSetpointWeight(0.7);
    controller.setDeadBand(0.01);
    controller.addGainSchedulePoint(50.0, 2.0, 10.0, 0.5);
    return controller;
  }

  private static PressureTransmitter createTransmitter(double pressureBara) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, pressureBara);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream stream = new Stream("feed", fluid);
    stream.setPressure(pressureBara, "bara");
    return new PressureTransmitter("PT-100", stream);
  }

  private static final class StatefulControllerSubclass extends ControllerDeviceBaseClass {
    private static final long serialVersionUID = 1000L;
    @SuppressWarnings("unused")
    private double customState;

    private StatefulControllerSubclass(String name) {
      super(name);
    }
  }
}
