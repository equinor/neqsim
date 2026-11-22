package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Quantitative physical-scan qualification for opt-in volume-flow-transmitter signal dynamics.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class VolumeFlowTransmitterPhysicalScanTest extends neqsim.NeqSimTest {
  /** Analytical first-order response uses the physical timestep and duplicate IDs are idempotent. */
  @Test
  void firstOrderFilterUsesPhysicalTimestepAndDuplicateIdIsIdempotent() {
    VolumeFlowTransmitter transmitter = createTransmitter(1000.0);
    transmitter.setTransientSamplingEnabled(true);
    transmitter.setFirstOrderTimeConstant(2.0);
    UUID first = step("filter", 0);
    UUID second = step("filter", 1);

    transmitter.sampleTransient(0.5, first);
    assertEquals(1000.0, transmitter.getTransientSample("kg/hr"), 1.0e-9);
    transmitter.getStream().setFlowRate(2000.0, "kg/hr");
    transmitter.sampleTransient(0.5, second);

    double expected = 1000.0 + (1.0 - Math.exp(-0.5 / 2.0)) * 1000.0;
    assertEquals(expected, transmitter.getTransientSample("kg/hr"), 1.0e-9);
    transmitter.sampleTransient(9.0, second);
    assertEquals(expected, transmitter.getTransientSample("kg/hr"), 1.0e-9);
  }

  /** Exact exponential filtering has an invariant endpoint under constant-input timestep refinement. */
  @Test
  void firstOrderFilterIsInvariantToConstantInputRefinement() {
    VolumeFlowTransmitter coarse = createTransmitter(1000.0);
    VolumeFlowTransmitter refined = createTransmitter(1000.0);
    coarse.setTransientSamplingEnabled(true);
    refined.setTransientSamplingEnabled(true);
    coarse.setFirstOrderTimeConstant(2.0);
    refined.setFirstOrderTimeConstant(2.0);
    coarse.sampleTransient(0.25, step("coarse-initial", 0));
    refined.sampleTransient(0.25, step("refined-initial", 0));
    coarse.getStream().setFlowRate(2000.0, "kg/hr");
    refined.getStream().setFlowRate(2000.0, "kg/hr");

    coarse.sampleTransient(1.0, step("coarse", 0));
    refined.sampleTransient(0.5, step("refined", 1));
    refined.sampleTransient(0.5, step("refined", 2));

    assertEquals(coarse.getTransientSample("kg/hr"), refined.getTransientSample("kg/hr"), 1.0e-9);
  }

  /** Flow-unit ratios are frozen at the sample instant and do not reread the changing stream. */
  @Test
  void heldUnitConversionUsesSampleInstantRatios() {
    VolumeFlowTransmitter transmitter = createTransmitter(1000.0);
    transmitter.setTransientSamplingEnabled(true);
    UUID id = step("unit conversion", 0);
    double expectedActual = transmitter.getMeasuredValue("m^3/hr");

    transmitter.sampleTransient(0.5, id);
    transmitter.getStream().setFlowRate(3000.0, "kg/hr");

    assertEquals(1000.0, transmitter.getTransientSample("kg/hr"), 1.0e-9);
    assertEquals(expectedActual, transmitter.getTransientSample("m^3/hr"), 1.0e-12);
    assertThrows(IllegalArgumentException.class, () -> transmitter.getTransientSample("kg/sec"));
  }

  /** Linear drift advances in configured engineering units per accepted physical duration. */
  @Test
  void linearDriftAdvancesOncePerPhysicalDuration() {
    VolumeFlowTransmitter transmitter = createTransmitter(1000.0);
    transmitter.setTransientSamplingEnabled(true);
    transmitter.setFault(SensorFaultType.LINEAR_DRIFT, 20.0);

    transmitter.sampleTransient(0.25, step("drift", 0));
    assertEquals(1005.0, transmitter.getTransientSample("kg/hr"), 1.0e-9);
    assertEquals(1005.0, transmitter.getTransientSample("kg/hr"), 1.0e-9);
    transmitter.sampleTransient(1.5, step("drift", 1));
    assertEquals(1035.0, transmitter.getTransientSample("kg/hr"), 1.0e-9);
  }

  /** Process controller and history consumers observe the same held start-of-step sample. */
  @Test
  void processControllerAndHistoryShareOnePhysicalSample() {
    VolumeFlowTransmitter transmitter = createTransmitter(1000.0);
    transmitter.setTransientSamplingEnabled(true);
    transmitter.setFault(SensorFaultType.LINEAR_DRIFT, 20.0);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("FIC-101");
    controller.setTransmitter(transmitter);
    controller.setUnit("kg/hr");
    controller.setControllerSetPoint(1100.0);
    controller.setControllerParameters(0.01, 10.0, 0.0);

    ProcessSystem process = new ProcessSystem("sampled flow control");
    process.add(transmitter);
    process.add(controller);
    UUID id = step("process scan", 0);
    process.runTransient(0.25, id);

    double sample = transmitter.getTransientSample("kg/hr");
    double controllerValue = controller.getEventLog().get(0).getMeasuredValue();
    String[][] history = process.getHistorySnapshot();
    assertEquals(1005.0, sample, 1.0e-9);
    assertEquals(sample, controllerValue, 0.0);
    assertEquals(sample, Double.parseDouble(history[0][2]), 0.0);
  }

  /** Transaction rollback restores the held sample, signal state and deterministic controller replay. */
  @Test
  void transactionRollbackReplaysSampleControllerAndHistory() {
    VolumeFlowTransmitter transmitter = createTransmitter(1000.0);
    transmitter.setTransientSamplingEnabled(true);
    transmitter.setNoiseStdDev(2.0);
    transmitter.setRandomSeed(991L);
    transmitter.setFirstOrderTimeConstant(1.5);
    transmitter.setFault(SensorFaultType.LINEAR_DRIFT, 0.1);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("FIC-201");
    controller.setTransmitter(transmitter);
    controller.setUnit("kg/hr");
    controller.setControllerSetPoint(1100.0);

    ProcessSystem process = new ProcessSystem("flow scan transaction");
    process.add(transmitter);
    process.add(controller);
    UUID id = step("rollback", 0);
    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    process.runTransient(0.5, id);
    double trialSample = transmitter.getTransientSample("kg/hr");
    double trialResponse = controller.getResponse();
    String trialHistory = process.getHistorySnapshot()[0][2];
    transaction.rollback();

    assertEquals(0, process.getHistorySize());
    process.runTransient(0.5, id);
    assertEquals(trialSample, transmitter.getTransientSample("kg/hr"), 0.0);
    assertEquals(trialResponse, controller.getResponse(), 0.0);
    assertEquals(trialHistory, process.getHistorySnapshot()[0][2]);
  }

  /** Java serialization preserves the held sample, conversion basis and physical-step provenance. */
  @Test
  void serializationPreservesHeldSample() throws Exception {
    VolumeFlowTransmitter transmitter = createTransmitter(1000.0);
    transmitter.setTransientSamplingEnabled(true);
    UUID id = step("serialization", 0);
    transmitter.sampleTransient(0.5, id);
    double actualVolume = transmitter.getTransientSample("m^3/hr");

    VolumeFlowTransmitter restored = roundTrip(transmitter);

    assertTrue(restored.hasTransientSample(id));
    assertEquals(1000.0, restored.getTransientSample("kg/hr"), 1.0e-9);
    assertEquals(actualVolume, restored.getTransientSample("m^3/hr"), 0.0);
  }

  /** Disabled sampling preserves the legacy raw-read path without advancing inherited signal state. */
  @Test
  void disabledSamplingPreservesLegacyRawReadBehaviour() {
    VolumeFlowTransmitter transmitter = createTransmitter(1000.0);
    transmitter.setFault(SensorFaultType.LINEAR_DRIFT, 20.0);
    UUID id = step("legacy", 0);

    transmitter.sampleTransient(0.25, id);

    assertFalse(transmitter.hasTransientSample(id));
    assertEquals(1000.0, transmitter.getMeasuredValue("kg/hr"), 1.0e-9);
    assertEquals(1000.0, transmitter.getMeasuredValue("kg/hr"), 1.0e-9);
  }

  /** Enabled sampled execution rejects absent identifiers and invalid intervals before mutation. */
  @Test
  void invalidPhysicalSampleInputsFailBeforeMutation() {
    VolumeFlowTransmitter transmitter = createTransmitter(1000.0);
    transmitter.setTransientSamplingEnabled(true);

    assertThrows(IllegalArgumentException.class, () -> transmitter.sampleTransient(1.0, null));
    assertThrows(IllegalArgumentException.class, () -> transmitter.sampleTransient(Double.NaN, step("invalid", 0)));
    assertThrows(IllegalStateException.class, () -> transmitter.getTransientSample("kg/hr"));
  }

  /**
   * Creates an initialized gas-flow transmitter.
   *
   * @param flowKgPerHour stream mass flow in kg/hr
   * @return configured transmitter
   */
  private static VolumeFlowTransmitter createTransmitter(double flowKgPerHour) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream stream = new Stream("flow source", fluid);
    stream.setFlowRate(flowKgPerHour, "kg/hr");
    stream.run();
    VolumeFlowTransmitter transmitter = new VolumeFlowTransmitter("FT-101", stream);
    transmitter.setUnit("kg/hr");
    return transmitter;
  }

  /**
   * Creates a deterministic physical-step identifier.
   *
   * @param scope identity scope
   * @param index physical-step index
   * @return deterministic identifier
   */
  private static UUID step(String scope, long index) {
    return TransientStepIdentifier.deterministicPhysicalStep("volume-flow-transmitter-" + scope, index);
  }

  /**
   * Serializes and restores a transmitter.
   *
   * @param transmitter source transmitter
   * @return restored transmitter
   * @throws Exception when Java serialization fails
   */
  private static VolumeFlowTransmitter roundTrip(VolumeFlowTransmitter transmitter) throws Exception {
    byte[] serialized;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(transmitter);
      output.flush();
      serialized = bytes.toByteArray();
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      return (VolumeFlowTransmitter) input.readObject();
    }
  }
}
