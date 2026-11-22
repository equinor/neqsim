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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.dynamics.TransientStepIdentifier;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Quantitative physical-scan qualification for opt-in vessel-level signal dynamics.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class LevelTransmitterPhysicalScanTest extends neqsim.NeqSimTest {
  /** Analytical filtering uses physical time and is invariant to constant-input refinement. */
  @Test
  void firstOrderFilterUsesPhysicalTimestepAndRefinesExactly() {
    LevelTransmitter coarse = createTransmitter(0.5);
    LevelTransmitter refined = createTransmitter(0.5);
    coarse.setTransientSamplingEnabled(true);
    refined.setTransientSamplingEnabled(true);
    coarse.setFirstOrderTimeConstant(2.0);
    refined.setFirstOrderTimeConstant(2.0);
    coarse.sampleTransient(0.25, step("coarse-initial", 0));
    refined.sampleTransient(0.25, step("refined-initial", 0));
    coarse.getSeparator().setLiquidLevel(0.8);
    refined.getSeparator().setLiquidLevel(0.8);

    coarse.sampleTransient(1.0, step("coarse", 0));
    refined.sampleTransient(0.5, step("refined", 0));
    refined.sampleTransient(0.5, step("refined", 1));

    double expected = 0.5 + (1.0 - Math.exp(-1.0 / 2.0)) * 0.3;
    assertEquals(expected, coarse.getTransientSample(), 1.0e-12);
    assertEquals(expected, refined.getTransientSample(), 1.0e-12);
    UUID duplicate = step("duplicate", 0);
    coarse.sampleTransient(0.25, duplicate);
    double once = coarse.getTransientSample();
    coarse.sampleTransient(9.0, duplicate);
    assertEquals(once, coarse.getTransientSample(), 0.0);
  }

  /** Drift and delay advance once per accepted physical identifier, not once per consumer read. */
  @Test
  void driftAndDelayAdvanceOncePerPhysicalIdentifier() {
    LevelTransmitter drift = createTransmitter(0.5);
    drift.setTransientSamplingEnabled(true);
    drift.setFault(SensorFaultType.LINEAR_DRIFT, 0.1);
    UUID first = step("drift", 0);
    drift.sampleTransient(0.25, first);
    assertEquals(0.525, drift.getTransientSample(), 1.0e-12);
    drift.sampleTransient(3.0, first);
    assertEquals(0.525, drift.getTransientSample(), 1.0e-12);
    drift.sampleTransient(1.5, step("drift", 1));
    assertEquals(0.675, drift.getTransientSample(), 1.0e-12);

    LevelTransmitter delayed = createTransmitter(0.2);
    delayed.setTransientSamplingEnabled(true);
    delayed.setDelaySteps(1);
    delayed.sampleTransient(1.0, step("delay", 0));
    delayed.getSeparator().setLiquidLevel(0.5);
    delayed.sampleTransient(1.0, step("delay", 1));
    assertEquals(0.2, delayed.getTransientSample(), 0.0);
    delayed.getSeparator().setLiquidLevel(0.8);
    delayed.sampleTransient(1.0, step("delay", 2));
    assertEquals(0.5, delayed.getTransientSample(), 0.0);
  }

  /** Controller, event log and history consume one held start-of-step level sample. */
  @Test
  void defaultUnitControllerAndHistoryShareHeldSample() {
    LevelTransmitter transmitter = createTransmitter(0.5);
    transmitter.setTransientSamplingEnabled(true);
    transmitter.setFault(SensorFaultType.LINEAR_DRIFT, 0.1);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("LC-101");
    controller.setTransmitter(transmitter);
    controller.setControllerSetPoint(0.5);
    controller.setControllerParameters(1.0, 0.0, 0.0);

    ProcessSystem process = new ProcessSystem("sampled level control");
    process.add(transmitter);
    process.add(controller);
    UUID id = step("process", 0);
    process.runTransient(0.25, id);

    double held = transmitter.getTransientSample();
    assertEquals(0.525, held, 1.0e-12);
    assertEquals(held, controller.getEventLog().get(0).getMeasuredValue(), 0.0);
    assertEquals(held, Double.parseDouble(process.getHistorySnapshot()[0][2]), 0.0);
    assertEquals(32.5, controller.getResponse(), 1.0e-12,
        "percent controller must use the held sample instead of a second live read");
  }

  /** Rollback restores noise, filter, fault, held sample, controller and history for exact replay. */
  @Test
  void transactionRollbackReplaysLevelSampleControllerAndHistory() {
    LevelTransmitter transmitter = createTransmitter(0.5);
    transmitter.setTransientSamplingEnabled(true);
    transmitter.setNoiseStdDev(0.01);
    transmitter.setRandomSeed(2911L);
    transmitter.setFirstOrderTimeConstant(1.5);
    transmitter.setFault(SensorFaultType.LINEAR_DRIFT, 0.01);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("LC-201");
    controller.setTransmitter(transmitter);
    controller.setControllerSetPoint(0.55);

    ProcessSystem process = new ProcessSystem("level scan transaction");
    process.add(transmitter);
    process.add(controller);
    assertTrue(process.getTransientTransactionCoverage().isComplete());
    UUID id = step("rollback", 0);
    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    process.runTransient(0.5, id);
    double trialSample = transmitter.getTransientSample();
    double trialResponse = controller.getResponse();
    String trialHistory = process.getHistorySnapshot()[0][2];
    transaction.rollback();

    assertEquals(0, process.getHistorySize());
    process.runTransient(0.5, id);
    assertEquals(trialSample, transmitter.getTransientSample(), 0.0);
    assertEquals(trialResponse, controller.getResponse(), 0.0);
    assertEquals(trialHistory, process.getHistorySnapshot()[0][2]);
  }

  /** Java serialization preserves held value, provenance and vessel association. */
  @Test
  void serializationPreservesHeldSampleAndVessel() throws Exception {
    LevelTransmitter transmitter = createTransmitter(0.5);
    transmitter.setTransientSamplingEnabled(true);
    UUID id = step("serialization", 0);
    transmitter.sampleTransient(0.5, id);

    LevelTransmitter restored = roundTrip(transmitter);

    assertTrue(restored.hasTransientSample(id));
    assertEquals(0.5, restored.getTransientSample(), 0.0);
    assertSame(restored.getSeparator(), restored.getVessel());
    assertEquals(0.5, restored.getMeasuredValue(""), 0.0);
  }

  /** Disabled sampling retains legacy per-read mutation and invalid sampled inputs fail before mutation. */
  @Test
  void compatibilityAndInvalidInputsAreExplicit() {
    LevelTransmitter transmitter = createTransmitter(0.5);
    transmitter.setFault(SensorFaultType.LINEAR_DRIFT, 0.1);
    UUID id = step("legacy", 0);
    transmitter.sampleTransient(0.25, id);
    assertFalse(transmitter.hasTransientSample(id));
    assertEquals(0.6, transmitter.getMeasuredValue(""), 1.0e-12);
    assertEquals(0.7, transmitter.getMeasuredValue(""), 1.0e-12);

    transmitter.clearFault();
    transmitter.setTransientSamplingEnabled(true);
    assertThrows(IllegalArgumentException.class, () -> transmitter.sampleTransient(1.0, null));
    assertThrows(IllegalArgumentException.class, () -> transmitter.sampleTransient(Double.NaN, step("invalid", 0)));
    assertThrows(IllegalStateException.class, transmitter::getTransientSample);

    LevelTransmitter subclass = new LevelTransmitter("custom LT", transmitter.getSeparator()) {
      private static final long serialVersionUID = 1L;
    };
    assertTrue(subclass.getTransientStateCoverageIssue().contains("subclass"));
    assertThrows(IllegalStateException.class, subclass::captureTransientState);
  }

  /** A zero configured measurement span produces a finite neutral percent-domain error. */
  @Test
  void zeroMeasurementSpanDoesNotDivideByZero() {
    LevelTransmitter transmitter = createTransmitter(0.5);
    transmitter.setMinimumValue(0.5);
    transmitter.setMaximumValue(0.5);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("LC-zero-span");
    controller.setTransmitter(transmitter);
    controller.setControllerSetPoint(0.5);
    controller.setControllerParameters(1.0, 0.0, 0.0);

    controller.runTransient(37.0, 1.0, step("zero-span", 0));

    assertTrue(Double.isFinite(controller.getResponse()));
    assertEquals(37.0, controller.getResponse(), 0.0);
  }

  /**
   * Creates a deterministic separator-backed level transmitter.
   *
   * @param levelFraction vessel level fraction
   * @return configured transmitter
   */
  private static LevelTransmitter createTransmitter(double levelFraction) {
    Separator separator = new Separator("level source");
    separator.setInternalDiameter(2.0);
    separator.setSeparatorLength(4.0);
    separator.setLiquidLevel(levelFraction);
    return new LevelTransmitter("LT-101", separator);
  }

  /**
   * Creates a deterministic physical-step identity.
   *
   * @param scope test scope
   * @param index physical-step index
   * @return deterministic identifier
   */
  private static UUID step(String scope, long index) {
    return TransientStepIdentifier.deterministicPhysicalStep("level-transmitter-" + scope, index);
  }

  /**
   * Serializes and restores a transmitter.
   *
   * @param transmitter source transmitter
   * @return restored transmitter
   * @throws Exception if Java serialization fails
   */
  private static LevelTransmitter roundTrip(LevelTransmitter transmitter) throws Exception {
    byte[] serialized;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(transmitter);
      output.flush();
      serialized = bytes.toByteArray();
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      return (LevelTransmitter) input.readObject();
    }
  }
}
