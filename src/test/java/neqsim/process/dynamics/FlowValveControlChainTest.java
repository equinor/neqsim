package neqsim.process.dynamics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.ControllerPerformanceMetrics;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.SensorFaultType;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * End-to-end qualification of sampled flow measurement, PID and a lagged inlet control valve.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FlowValveControlChainTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(FlowValveControlChainTest.class);

  /** An upstream-pressure step must produce bounded flow control with one shared start-of-step sample. */
  @Test
  void pressureDisturbanceProducesBoundedSampledFlowResponse() {
    FlowControlChain chain = createChain("disturbance", 4.0);
    assertTrue(chain.process.getTransientTransactionCoverage().isComplete(),
        chain.process.getTransientTransactionCoverage().getBlockingIssues().toString());
    double maximumFlow = chain.baselineFlow;
    double minimumFlow = chain.baselineFlow;
    double maximumMassResidual = 0.0;
    double maximumComponentResidual = 0.0;
    double maximumRelativeEnthalpyResidual = 0.0;
    chain.feed.setPressure(24.0, "bara");
    chain.feed.run();

    for (int index = 0; index < 150; index++) {
      double flowAtScan = chain.valve.getOutletStream().getFlowRate("kg/hr");
      UUID id = step("disturbance", index);
      chain.process.runTransient(1.0, id);
      FlowResidual residual = assertValveBalance(chain);
      maximumMassResidual = Math.max(maximumMassResidual, residual.massKgPerHour);
      maximumComponentResidual = Math.max(maximumComponentResidual, residual.componentMolesPerSecond);
      maximumRelativeEnthalpyResidual = Math.max(maximumRelativeEnthalpyResidual, residual.relativeEnthalpyRate);
      assertEquals(flowAtScan, chain.transmitter.getTransientSample("kg/hr"), 1.0e-9,
          "the physical FT scan must hold the start-of-step valve flow");
      assertEquals(chain.transmitter.getTransientSample("kg/hr"),
          chain.controller.getEventLog().get(chain.controller.getEventLog().size() - 1).getMeasuredValue(), 0.0,
          "the controller must consume the held flow sample");
      String[][] history = chain.process.getHistorySnapshot();
      assertEquals(chain.transmitter.getTransientSample("kg/hr"), Double.parseDouble(history[history.length - 1][2]),
          0.0, "measurement history must reuse the held flow sample");
      double flow = chain.valve.getOutletStream().getFlowRate("kg/hr");
      minimumFlow = Math.min(minimumFlow, flow);
      maximumFlow = Math.max(maximumFlow, flow);
      assertTrue(flow > 0.0);
      assertTrue(chain.valve.getPercentValveOpening() >= 5.0 && chain.valve.getPercentValveOpening() <= 95.0);
    }

    ControllerPerformanceMetrics metrics = chain.controller.getPerformanceMetrics();
    double finalFlow = chain.valve.getOutletStream().getFlowRate("kg/hr");
    logger.info(
        "flow-chain metrics setpoint={} final={} min={} max={} kg/hr opening={} percent IAE={} kg/hr-s peakError={} kg/hr travel={} pp settling={} s massResidual={} kg/hr componentResidual={} mol/s enthalpyResidual={}",
        chain.baselineFlow, finalFlow, minimumFlow, maximumFlow, chain.valve.getPercentValveOpening(),
        metrics.getIntegralAbsoluteError(), metrics.getPeakAbsoluteError(), metrics.getControllerOutputTravel(),
        metrics.getSettlingTime(), maximumMassResidual, maximumComponentResidual, maximumRelativeEnthalpyResidual);
    assertEquals(150, metrics.getSampleCount());
    assertTrue(maximumFlow > chain.baselineFlow + 300.0,
        "the upstream-pressure step must first increase physical valve flow");
    assertTrue(chain.valve.getPercentValveOpening() < 50.0,
        "reverse-acting flow control must close the inlet valve after high flow");
    assertTrue(maximumFlow <= 1441.0, "peak flow must remain below 1441 kg/hr");
    assertTrue(Math.abs(finalFlow - chain.baselineFlow) <= 14.0,
        "150 s final flow must recover within 14 kg/hr of setpoint");
    assertTrue(metrics.getIntegralAbsoluteError() <= 10850.0, "150 s flow-control IAE must remain below 10850 kg/hr-s");
    assertTrue(metrics.getPeakAbsoluteError() <= 335.0, "held-sample peak error must remain below 335 kg/hr");
    assertTrue(metrics.getControllerOutputTravel() <= 22.4,
        "controller-output travel must remain below 22.4 percentage points");
    assertTrue(metrics.getSettlingTime() <= 124.0,
        "flow must enter and remain in the default two-percent band by 124 s");
    assertTrue(maximumMassResidual <= 1.0e-6, "valve mass residual must remain below 1e-6 kg/hr");
    assertTrue(maximumComponentResidual <= 1.0e-10, "valve component residual must remain below 1e-10 mol/s");
    assertTrue(maximumRelativeEnthalpyResidual <= 1.0e-10,
        "adiabatic throttling enthalpy-rate residual must remain below 1e-10 relative");
  }

  /** Independent chains reproduce flow, held samples, controller and valve states bit-for-bit. */
  @Test
  void repeatedExecutionIsDeterministic() {
    FlowControlChain first = createChain("repeat first", 4.0);
    FlowControlChain second = createChain("repeat second", 4.0);
    first.feed.setPressure(24.0, "bara");
    second.feed.setPressure(24.0, "bara");
    first.feed.run();
    second.feed.run();

    for (int index = 0; index < 20; index++) {
      UUID id = step("repeat", index);
      first.process.runTransient(1.0, id);
      second.process.runTransient(1.0, id);
      assertEquals(first.valve.getOutletStream().getFlowRate("kg/hr"),
          second.valve.getOutletStream().getFlowRate("kg/hr"), 0.0);
      assertArrayEquals(componentRates(first.valve.getOutletStream().getThermoSystem()),
          componentRates(second.valve.getOutletStream().getThermoSystem()), 0.0);
      assertEquals(first.transmitter.getTransientSample("kg/hr"), second.transmitter.getTransientSample("kg/hr"), 0.0);
      assertEquals(first.controller.getResponse(), second.controller.getResponse(), 0.0);
      assertEquals(first.valve.getPercentValveOpening(), second.valve.getPercentValveOpening(), 0.0);
    }
  }

  /** Halving the physical scan interval keeps the ten-second flow and actuator response close. */
  @Test
  void timestepRefinementKeepsResponseWithinDeclaredSensitivity() {
    FlowControlChain coarse = createChain("coarse timestep", 4.0);
    FlowControlChain refined = createChain("refined timestep", 4.0);
    coarse.feed.setPressure(24.0, "bara");
    refined.feed.setPressure(24.0, "bara");
    coarse.feed.run();
    refined.feed.run();

    runSteps(coarse, 1.0, 10, "coarse timestep");
    runSteps(refined, 0.5, 20, "refined timestep");

    logger.info("flow-chain refinement flow coarse={} refined={} kg/hr opening coarse={} refined={} percent",
        coarse.valve.getOutletStream().getFlowRate("kg/hr"), refined.valve.getOutletStream().getFlowRate("kg/hr"),
        coarse.valve.getPercentValveOpening(), refined.valve.getPercentValveOpening());
    assertEquals(coarse.valve.getOutletStream().getFlowRate("kg/hr"),
        refined.valve.getOutletStream().getFlowRate("kg/hr"), 25.0,
        "halving dt must change ten-second flow by no more than 25 kg/hr");
    assertEquals(coarse.valve.getPercentValveOpening(), refined.valve.getPercentValveOpening(), 1.0,
        "halving dt must change ten-second valve opening by no more than one percentage point");
  }

  /** Rejected trials restore and replay the complete flow-control chain bit-for-bit. */
  @Test
  void rejectedStepRestoresAndReplaysWholeControlChain() {
    FlowControlChain chain = createChain("rollback", 4.0);
    chain.feed.setPressure(24.0, "bara");
    chain.feed.run();
    UUID id = step("rollback", 0);
    double processTime = chain.process.getTime();
    TransientStepTransaction transaction = chain.process.beginTransientStepTransaction();

    chain.process.runTransient(1.0, id);
    double trialFlow = chain.valve.getOutletStream().getFlowRate("kg/hr");
    double trialSample = chain.transmitter.getTransientSample("kg/hr");
    double trialResponse = chain.controller.getResponse();
    double trialOpening = chain.valve.getPercentValveOpening();
    double[] trialComponentRates = componentRates(chain.valve.getOutletStream().getThermoSystem());
    String trialHistory = chain.process.getHistorySnapshot()[0][2];
    transaction.rollback();

    assertEquals(processTime, chain.process.getTime(), 0.0);
    chain.process.runTransient(1.0, id);
    assertEquals(trialFlow, chain.valve.getOutletStream().getFlowRate("kg/hr"), 0.0);
    assertEquals(trialSample, chain.transmitter.getTransientSample("kg/hr"), 0.0);
    assertEquals(trialResponse, chain.controller.getResponse(), 0.0);
    assertEquals(trialOpening, chain.valve.getPercentValveOpening(), 0.0);
    assertArrayEquals(trialComponentRates, componentRates(chain.valve.getOutletStream().getThermoSystem()), 0.0);
    assertEquals(trialHistory, chain.process.getHistorySnapshot()[0][2]);
  }

  /** Java restart reproduces the next accepted flow-control step. */
  @Test
  void serializedRestartMatchesNextAcceptedStep() throws Exception {
    FlowControlChain chain = createChain("restart", 4.0);
    chain.feed.setPressure(24.0, "bara");
    chain.feed.run();
    chain.process.runTransient(1.0, step("restart", 0));
    ProcessSystem restored = roundTrip(chain.process);
    UUID next = step("restart", 1);

    chain.process.runTransient(1.0, next);
    restored.runTransient(1.0, next);

    ThrottlingValve restoredValve = (ThrottlingValve) restored.getUnit("restart FCV");
    VolumeFlowTransmitter restoredTransmitter = (VolumeFlowTransmitter) restored.getMeasurementDevice("restart FT");
    ControllerDeviceBaseClass restoredController = (ControllerDeviceBaseClass) restoredValve.getController();
    assertEquals(chain.valve.getOutletStream().getFlowRate("kg/hr"),
        restoredValve.getOutletStream().getFlowRate("kg/hr"), 0.0);
    assertArrayEquals(componentRates(chain.valve.getOutletStream().getThermoSystem()),
        componentRates(restoredValve.getOutletStream().getThermoSystem()), 0.0);
    assertEquals(chain.transmitter.getTransientSample("kg/hr"), restoredTransmitter.getTransientSample("kg/hr"), 0.0);
    assertEquals(chain.controller.getResponse(), restoredController.getResponse(), 0.0);
    assertEquals(chain.valve.getPercentValveOpening(), restoredValve.getPercentValveOpening(), 0.0);
  }

  /** A stuck transmitter masks high physical flow and is retained as a failed safeguarding action. */
  @Test
  void stuckFlowSignalMasksDisturbanceAndDoesNotProtectTheProcess() {
    FlowControlChain chain = createChain("fault", 4.0);
    chain.transmitter.setFault(SensorFaultType.STUCK_AT_VALUE, chain.baselineFlow);
    chain.feed.setPressure(24.0, "bara");
    chain.feed.run();
    runSteps(chain, 1.0, 20, "fault");

    double actualFlow = chain.valve.getOutletStream().getFlowRate("kg/hr");
    assertEquals(chain.baselineFlow, chain.transmitter.getTransientSample("kg/hr"), 0.0);
    assertTrue(actualFlow - chain.transmitter.getTransientSample("kg/hr") > 300.0,
        "a stuck FT must mask more than 300 kg/hr of actual high flow");
    assertEquals(50.0, chain.valve.getPercentValveOpening(), 1.0e-9,
        "a stuck-at-setpoint FT leaves the control valve at its initial opening");
  }

  /** Reports warmed wall time without imposing a machine-specific performance gate. */
  @Test
  void reportSyntheticFlowChainCost() {
    long[] nanosPerStep = new long[5];
    for (int round = 0; round < nanosPerStep.length; round++) {
      FlowControlChain chain = createChain("timing " + round, 4.0);
      chain.feed.setPressure(24.0, "bara");
      chain.feed.run();
      runSteps(chain, 1.0, 5, "timing warmup " + round);
      long start = System.nanoTime();
      runSteps(chain, 1.0, 20, "timing measurement " + round);
      nanosPerStep[round] = (System.nanoTime() - start) / 20L;
      assertTrue(Double.isFinite(chain.valve.getOutletStream().getFlowRate("kg/hr")));
    }
    java.util.Arrays.sort(nanosPerStep);
    logger.info("flow-chain warmed median={} ns/physical step samples={}", nanosPerStep[2],
        java.util.Arrays.toString(nanosPerStep));
  }

  /**
   * Builds the public synthetic inlet-flow control chain.
   *
   * @param prefix unique equipment-name prefix
   * @param actuatorTimeConstant valve actuator lag in seconds
   * @return initialized dynamic chain
   */
  private static FlowControlChain createChain(String prefix, double actuatorTimeConstant) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream(prefix + " feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    ThrottlingValve valve = new ThrottlingValve(prefix + " FCV", feed);
    valve.setOutletPressure(10.0, "bara");
    valve.setCv(10.0);
    valve.setPercentValveOpening(50.0);
    ProcessSystem process = new ProcessSystem(prefix + " flow-control process");
    process.add(feed);
    process.add(valve);
    process.run();
    valve.setCalculateSteadyState(false);
    valve.setPhysicalStepIdempotencyEnabled(true);
    valve.setTravelTimeConstant(actuatorTimeConstant);
    valve.runTransient(0.0, step(prefix + " hydraulic initialization", 0));
    double baselineFlow = valve.getOutletStream().getFlowRate("kg/hr");

    VolumeFlowTransmitter transmitter = new VolumeFlowTransmitter(prefix + " FT", valve.getOutletStream());
    transmitter.setUnit("kg/hr");
    transmitter.setMinimumValue(0.0);
    transmitter.setMaximumValue(2000.0);
    transmitter.setTransientSamplingEnabled(true);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass(prefix + " FIC");
    controller.setTransmitter(transmitter);
    controller.setControllerSetPoint(baselineFlow, "kg/hr");
    controller.setControllerParameters(0.02, 20.0, 0.0);
    controller.setReverseActing(true);
    controller.setOutputLimits(5.0, 95.0);
    valve.setController(controller);
    process.add(transmitter);
    process.setTime(0.0);
    return new FlowControlChain(process, feed, valve, transmitter, controller, baselineFlow);
  }

  /**
   * Advances a chain through deterministic physical steps.
   *
   * @param chain chain to advance
   * @param dt physical step size in seconds
   * @param count number of steps
   * @param scope identity scope
   */
  private static void runSteps(FlowControlChain chain, double dt, int count, String scope) {
    for (int index = 0; index < count; index++) {
      chain.process.runTransient(dt, step(scope, index));
    }
  }

  /**
   * Checks valve mass, component and adiabatic enthalpy-rate closure.
   *
   * @param chain configured control chain
   * @return maximum balance residuals for the current accepted state
   */
  private static FlowResidual assertValveBalance(FlowControlChain chain) {
    SystemInterface inlet = chain.valve.getInletStream().getThermoSystem();
    SystemInterface outlet = chain.valve.getOutletStream().getThermoSystem();
    double massResidual = Math.abs(inlet.getFlowRate("kg/hr") - outlet.getFlowRate("kg/hr"));
    double[] inletComponents = componentRates(inlet);
    double[] outletComponents = componentRates(outlet);
    assertArrayEquals(inletComponents, outletComponents, 1.0e-10,
        "throttling valve must conserve every component flow rate");
    double componentResidual = 0.0;
    for (int index = 0; index < inletComponents.length; index++) {
      componentResidual = Math.max(componentResidual, Math.abs(inletComponents[index] - outletComponents[index]));
    }
    double relativeEnthalpyResidual = Math.abs(inlet.getEnthalpy() - outlet.getEnthalpy())
        / Math.max(Math.abs(inlet.getEnthalpy()), 1.0);
    return new FlowResidual(massResidual, componentResidual, relativeEnthalpyResidual);
  }

  /**
   * Returns component molar rates in system order.
   *
   * @param system thermodynamic stream state
   * @return component molar rates in mol/s
   */
  private static double[] componentRates(SystemInterface system) {
    int componentCount = system.getPhase(0).getNumberOfComponents();
    double[] result = new double[componentCount];
    for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
      result[componentIndex] = system.getComponent(componentIndex).getNumberOfmoles();
    }
    return result;
  }

  /**
   * Creates a deterministic physical-step identity.
   *
   * @param scope identity scope
   * @param index physical-step index
   * @return deterministic identifier
   */
  private static UUID step(String scope, long index) {
    return TransientStepIdentifier.deterministicPhysicalStep("flow-valve-chain-" + scope, index);
  }

  /**
   * Serializes and restores a process.
   *
   * @param process source process
   * @return restored process
   * @throws Exception if Java serialization fails
   */
  private static ProcessSystem roundTrip(ProcessSystem process) throws Exception {
    byte[] serialized;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(process);
      output.flush();
      serialized = bytes.toByteArray();
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      return (ProcessSystem) input.readObject();
    }
  }

  /** Per-step valve conservation evidence. */
  private static final class FlowResidual {
    private final double massKgPerHour;
    private final double componentMolesPerSecond;
    private final double relativeEnthalpyRate;

    /**
     * Creates a residual tuple.
     *
     * @param massKgPerHour mass-flow residual in kg/hr
     * @param componentMolesPerSecond maximum component residual in mol/s
     * @param relativeEnthalpyRate relative enthalpy-rate residual
     */
    private FlowResidual(double massKgPerHour, double componentMolesPerSecond, double relativeEnthalpyRate) {
      this.massKgPerHour = massKgPerHour;
      this.componentMolesPerSecond = componentMolesPerSecond;
      this.relativeEnthalpyRate = relativeEnthalpyRate;
    }
  }

  /** Strongly typed fixture references. */
  private static final class FlowControlChain {
    private final ProcessSystem process;
    private final Stream feed;
    private final ThrottlingValve valve;
    private final VolumeFlowTransmitter transmitter;
    private final ControllerDeviceBaseClass controller;
    private final double baselineFlow;

    /**
     * Creates fixture references.
     *
     * @param process process system
     * @param feed upstream pressure boundary
     * @param valve controlled inlet valve
     * @param transmitter sampled flow transmitter
     * @param controller attached PID
     * @param baselineFlow flow setpoint in kg/hr
     */
    private FlowControlChain(ProcessSystem process, Stream feed, ThrottlingValve valve,
        VolumeFlowTransmitter transmitter, ControllerDeviceBaseClass controller, double baselineFlow) {
      this.process = process;
      this.feed = feed;
      this.valve = valve;
      this.transmitter = transmitter;
      this.controller = controller;
      this.baselineFlow = baselineFlow;
    }
  }
}
