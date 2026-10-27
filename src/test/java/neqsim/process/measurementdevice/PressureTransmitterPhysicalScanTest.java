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
 * Quantitative physical-scan qualification for opt-in pressure-transmitter signal dynamics.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PressureTransmitterPhysicalScanTest extends neqsim.NeqSimTest {
  /** Analytical first-order response uses the declared physical timestep and a duplicate ID is idempotent. */
  @Test
  void firstOrderFilterUsesPhysicalTimestepAndDuplicateIdIsIdempotent() {
    PressureTransmitter transmitter = createTransmitter(10.0);
    transmitter.setTransientSamplingEnabled(true);
    transmitter.setFirstOrderTimeConstant(2.0);
    UUID first = step("filter", 0);
    UUID second = step("filter", 1);

    transmitter.sampleTransient(0.5, first);
    assertEquals(10.0, transmitter.getTransientSample("bara"), 1.0e-12);
    transmitter.getStream().setPressure(20.0, "bara");
    transmitter.sampleTransient(0.5, second);

    double expected = 10.0 + (1.0 - Math.exp(-0.5 / 2.0)) * 10.0;
    assertEquals(expected, transmitter.getTransientSample("bara"), 1.0e-12);
    transmitter.sampleTransient(9.0, second);
    assertEquals(expected, transmitter.getTransientSample("bara"), 1.0e-12);
  }

  /** Exact exponential filtering gives the same one-second endpoint for one or two constant-input samples. */
  @Test
  void firstOrderFilterIsInvariantToConstantInputRefinement() {
    PressureTransmitter coarse = createTransmitter(10.0);
    PressureTransmitter refined = createTransmitter(10.0);
    coarse.setTransientSamplingEnabled(true);
    refined.setTransientSamplingEnabled(true);
    coarse.setFirstOrderTimeConstant(2.0);
    refined.setFirstOrderTimeConstant(2.0);
    coarse.sampleTransient(0.25, step("coarse-initial", 0));
    refined.sampleTransient(0.25, step("refined-initial", 0));
    coarse.getStream().setPressure(20.0, "bara");
    refined.getStream().setPressure(20.0, "bara");

    coarse.sampleTransient(1.0, step("coarse", 0));
    refined.sampleTransient(0.5, step("refined", 1));
    refined.sampleTransient(0.5, step("refined", 2));

    assertEquals(coarse.getTransientSample("bara"), refined.getTransientSample("bara"), 1.0e-12);
  }

  /** Linear drift advances in engineering units per second, independently of held-value consumer reads. */
  @Test
  void linearDriftAdvancesOncePerPhysicalDuration() {
    PressureTransmitter transmitter = createTransmitter(10.0);
    transmitter.setTransientSamplingEnabled(true);
    transmitter.setFault(SensorFaultType.LINEAR_DRIFT, 2.0);

    transmitter.sampleTransient(0.25, step("drift", 0));
    assertEquals(10.5, transmitter.getTransientSample("bara"), 1.0e-12);
    assertEquals(10.5, transmitter.getTransientSample("bara"), 1.0e-12);
    transmitter.sampleTransient(1.5, step("drift", 1));
    assertEquals(13.5, transmitter.getTransientSample("bara"), 1.0e-12);
  }

  /** Discrete delay consumes one buffer position per unique physical step, not per consumer. */
  @Test
  void sampleDelayAdvancesOncePerPhysicalIdentifier() {
    PressureTransmitter transmitter = createTransmitter(10.0);
    transmitter.setTransientSamplingEnabled(true);
    transmitter.setDelaySteps(1);

    transmitter.sampleTransient(1.0, step("delay", 0));
    transmitter.getStream().setPressure(20.0, "bara");
    transmitter.sampleTransient(1.0, step("delay", 1));
    assertEquals(10.0, transmitter.getTransientSample("bara"), 1.0e-12);
    transmitter.getStream().setPressure(30.0, "bara");
    UUID third = step("delay", 2);
    transmitter.sampleTransient(1.0, third);
    assertEquals(20.0, transmitter.getTransientSample("bara"), 1.0e-12);
    transmitter.sampleTransient(1.0, third);
    assertEquals(20.0, transmitter.getTransientSample("bara"), 1.0e-12);
  }

  /** Process controller and history consumers observe the exact same held start-of-step sample. */
  @Test
  void processControllerAndHistoryShareOnePhysicalSample() {
    PressureTransmitter transmitter = createTransmitter(10.0);
    transmitter.setTransientSamplingEnabled(true);
    transmitter.setFault(SensorFaultType.LINEAR_DRIFT, 2.0);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("PC-101");
    controller.setTransmitter(transmitter);
    controller.setUnit("bara");
    controller.setControllerSetPoint(12.0);
    controller.setControllerParameters(1.0, 10.0, 0.0);

    ProcessSystem process = new ProcessSystem("sampled pressure control");
    process.add(transmitter);
    process.add(controller);
    UUID id = step("process-scan", 0);
    process.runTransient(0.25, id);

    double sample = transmitter.getTransientSample("bara");
    double controllerValue = controller.getEventLog().get(0).getMeasuredValue();
    String[][] history = process.getHistorySnapshot();
    assertEquals(10.5, sample, 1.0e-12);
    assertEquals(sample, controllerValue, 0.0);
    assertEquals(sample, Double.parseDouble(history[0][2]), 0.0);
  }

  /** Transaction rollback restores held samples, filter/fault state and exact deterministic replay. */
  @Test
  void transactionRollbackReplaysSampleControllerAndHistory() {
    PressureTransmitter transmitter = createTransmitter(10.0);
    transmitter.setTransientSamplingEnabled(true);
    transmitter.setNoiseStdDev(0.2);
    transmitter.setRandomSeed(991L);
    transmitter.setFirstOrderTimeConstant(1.5);
    transmitter.setFault(SensorFaultType.LINEAR_DRIFT, 0.1);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("PC-201");
    controller.setTransmitter(transmitter);
    controller.setUnit("bara");
    controller.setControllerSetPoint(12.0);

    ProcessSystem process = new ProcessSystem("pressure scan transaction");
    process.add(transmitter);
    process.add(controller);
    UUID id = step("rollback", 0);
    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    process.runTransient(0.5, id);
    double trialSample = transmitter.getTransientSample("bara");
    double trialResponse = controller.getResponse();
    String trialHistory = process.getHistorySnapshot()[0][2];
    transaction.rollback();

    assertEquals(0, process.getHistorySize());
    process.runTransient(0.5, id);
    assertEquals(trialSample, transmitter.getTransientSample("bara"), 0.0);
    assertEquals(trialResponse, controller.getResponse(), 0.0);
    assertEquals(trialHistory, process.getHistorySnapshot()[0][2]);
  }

  /** Java serialization preserves the held physical sample and its provenance. */
  @Test
  void serializationPreservesHeldSample() throws Exception {
    PressureTransmitter transmitter = createTransmitter(10.0);
    transmitter.setTransientSamplingEnabled(true);
    UUID id = step("serialization", 0);
    transmitter.sampleTransient(0.5, id);

    PressureTransmitter restored = roundTrip(transmitter);

    assertTrue(restored.hasTransientSample(id));
    assertEquals(10.0, restored.getTransientSample("bara"), 0.0);
  }

  /** Disabled sampling leaves legacy direct-read mutation in place and holds no physical sample. */
  @Test
  void disabledSamplingPreservesLegacyDirectReadBehaviour() {
    PressureTransmitter transmitter = createTransmitter(10.0);
    transmitter.setFault(SensorFaultType.LINEAR_DRIFT, 1.0);
    UUID id = step("legacy", 0);

    transmitter.sampleTransient(0.25, id);

    assertFalse(transmitter.hasTransientSample(id));
    assertEquals(11.0, transmitter.getMeasuredValue("bara"), 1.0e-12);
    assertEquals(12.0, transmitter.getMeasuredValue("bara"), 1.0e-12);
  }

  /** Enabled sampled execution rejects absent identifiers and invalid physical intervals. */
  @Test
  void invalidPhysicalSampleInputsFailBeforeMutation() {
    PressureTransmitter transmitter = createTransmitter(10.0);
    transmitter.setTransientSamplingEnabled(true);

    assertThrows(IllegalArgumentException.class, () -> transmitter.sampleTransient(1.0, null));
    assertThrows(IllegalArgumentException.class,
        () -> transmitter.sampleTransient(Double.NaN, step("invalid", 0)));
    assertThrows(IllegalStateException.class, () -> transmitter.getTransientSample("bara"));
  }

  /**
   * Creates a deterministic methane pressure transmitter.
   *
   * @param pressureBara stream pressure in bara
   * @return configured transmitter
   */
  private static PressureTransmitter createTransmitter(double pressureBara) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, pressureBara);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream stream = new Stream("pressure source", fluid);
    stream.setPressure(pressureBara, "bara");
    return new PressureTransmitter("PT-101", stream);
  }

  /**
   * Creates a deterministic physical-step identity.
   *
   * @param scope test scope
   * @param index physical-step index
   * @return deterministic identifier
   */
  private static UUID step(String scope, long index) {
    return TransientStepIdentifier.deterministicPhysicalStep("pressure-transmitter-" + scope, index);
  }

  /**
   * Serializes and restores a transmitter.
   *
   * @param transmitter source transmitter
   * @return restored transmitter
   * @throws Exception if Java serialization fails
   */
  private static PressureTransmitter roundTrip(PressureTransmitter transmitter) throws Exception {
    byte[] serialized;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(transmitter);
      output.flush();
      serialized = bytes.toByteArray();
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      return (PressureTransmitter) input.readObject();
    }
  }
}
