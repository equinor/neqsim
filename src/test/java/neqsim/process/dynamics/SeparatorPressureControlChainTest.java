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
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.PressureControlValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * End-to-end qualification of separator pressure inventory, sampled pressure measurement, PID and gas PCV.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SeparatorPressureControlChainTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(SeparatorPressureControlChainTest.class);
  private static final double STEP_SECONDS = 1.0;

  /** A feed-rate step must produce a bounded physical pressure response and control action. */
  @Test
  void feedDisturbanceProducesBoundedPressureControlResponse() {
    PressureControlChain chain = createChain("disturbance");
    assertTrue(chain.process.getTransientTransactionCoverage().isComplete());
    double initialPressure = chain.separator.getPressure("bara");
    double initialOpening = chain.gasValve.getPercentValveOpening();
    double maximumPressure = initialPressure;
    double minimumPressure = initialPressure;
    double maximumOpening = initialOpening;
    double maximumComponentResidual = 0.0;
    double maximumEnergyResidual = 0.0;
    double maximumVolumeResidual = 0.0;

    StepResidual baselineResidual = assertAcceptedStepBalance(chain, step("baseline", 0));
    maximumComponentResidual = Math.max(maximumComponentResidual, baselineResidual.componentMoles);
    maximumEnergyResidual = Math.max(maximumEnergyResidual, baselineResidual.relativeEnergy);
    maximumVolumeResidual = Math.max(maximumVolumeResidual, baselineResidual.relativeVolume);
    chain.feed.setFlowRate(28.0, "kg/sec");
    chain.feed.run();

    for (int index = 0; index < 70; index++) {
      double pressureAtScan = chain.separator.getGasOutStream().getPressure("bara");
      StepResidual residual = assertAcceptedStepBalance(chain, step("disturbance", index));
      maximumComponentResidual = Math.max(maximumComponentResidual, residual.componentMoles);
      maximumEnergyResidual = Math.max(maximumEnergyResidual, residual.relativeEnergy);
      maximumVolumeResidual = Math.max(maximumVolumeResidual, residual.relativeVolume);
      assertEquals(pressureAtScan, chain.transmitter.getTransientSample("bara"), 0.0,
          "the controller scan must hold the start-of-step separator pressure");
      assertEquals(chain.transmitter.getTransientSample("bara"),
          chain.controller.getEventLog().get(chain.controller.getEventLog().size() - 1).getMeasuredValue(), 0.0,
          "the controller event must reuse the held pressure sample");
      assertEquals(chain.transmitter.getTransientSample("bara"),
          Double.parseDouble(chain.process.getHistorySnapshot()[chain.process.getHistorySnapshot().length - 1][2]), 0.0,
          "process history must reuse the held pressure sample");
      assertTrue(chain.separator.getPressure("bara") > 0.0);
      assertTrue(chain.separator.getTemperature("K") > 0.0);
      assertTrue(chain.gasValve.getPercentValveOpening() >= 0.0 && chain.gasValve.getPercentValveOpening() <= 100.0);
      maximumPressure = Math.max(maximumPressure, chain.separator.getPressure("bara"));
      minimumPressure = Math.min(minimumPressure, chain.separator.getPressure("bara"));
      maximumOpening = Math.max(maximumOpening, chain.gasValve.getPercentValveOpening());
    }

    ControllerPerformanceMetrics metrics = chain.controller.getPerformanceMetrics();
    logger.info(
        "pressure-chain metrics initial={} bara final={} bara min={} bara max={} bara opening={} -> {} percent IAE={} bar-s peakError={} bar travel={} pp settling={} s componentResidual={} mol energyResidual={} volumeResidual={}",
        initialPressure, chain.separator.getPressure("bara"), minimumPressure, maximumPressure, initialOpening,
        maximumOpening, metrics.getIntegralAbsoluteError(), metrics.getPeakAbsoluteError(),
        metrics.getControllerOutputTravel(), metrics.getSettlingTime(), maximumComponentResidual, maximumEnergyResidual,
        maximumVolumeResidual);
    assertTrue(maximumPressure > initialPressure, "increased feed must first increase separator pressure");
    assertTrue(maximumOpening > initialOpening, "direct pressure action must open the gas PCV");
    assertEquals(71, metrics.getSampleCount());
    assertTrue(metrics.getIntegralAbsoluteError() > 0.0);
    assertTrue(metrics.getPeakAbsoluteError() > 0.0);
    assertTrue(metrics.getControllerOutputTravel() > 0.0);
    assertTrue(maximumPressure <= 56.68, "70 s peak pressure must remain below 56.68 bara");
    assertTrue(chain.separator.getPressure("bara") <= 55.72, "70 s final pressure must return below 55.72 bara");
    assertTrue(maximumOpening <= 71.38, "gas PCV opening must remain below 71.38 percent");
    assertTrue(metrics.getIntegralAbsoluteError() <= 84.6,
        "70 s pressure-control IAE must remain below 84.6 bar-seconds");
    assertTrue(metrics.getPeakAbsoluteError() <= 1.68, "held-sample peak pressure error must remain below 1.68 bar");
    assertTrue(metrics.getControllerOutputTravel() <= 21.38,
        "controller-output travel must remain below 21.38 percentage points");
    assertTrue(metrics.getSettlingTime() <= 53.0,
        "pressure must enter and remain in the default two-percent band by 53 s");
    assertTrue(maximumComponentResidual <= 1.0e-8, "component inventory residual must remain below 1e-8 mol per step");
    assertTrue(maximumEnergyResidual <= 5.0e-6,
        "accepted VU state must reproduce the discrete energy target within 5e-6 relative");
    assertTrue(maximumVolumeResidual <= 1.0e-6,
        "accepted VU state must reproduce the fixed vessel volume within 1e-6 relative");
  }

  /** Independent chains reproduce pressure, held samples, controller and valve states bit-for-bit. */
  @Test
  void repeatedExecutionIsDeterministic() {
    PressureControlChain first = createChain("repeat first");
    PressureControlChain second = createChain("repeat second");
    first.feed.setFlowRate(28.0, "kg/sec");
    second.feed.setFlowRate(28.0, "kg/sec");
    first.feed.run();
    second.feed.run();

    for (int index = 0; index < 10; index++) {
      UUID id = step("repeat", index);
      first.process.runTransient(STEP_SECONDS, id);
      second.process.runTransient(STEP_SECONDS, id);
      assertEquals(first.separator.getPressure("bara"), second.separator.getPressure("bara"), 0.0);
      assertArrayEquals(componentMoles(first.separator.getThermoSystem()),
          componentMoles(second.separator.getThermoSystem()), 0.0);
      assertEquals(first.transmitter.getTransientSample("bara"), second.transmitter.getTransientSample("bara"), 0.0);
      assertEquals(first.controller.getResponse(), second.controller.getResponse(), 0.0);
      assertEquals(first.gasValve.getPercentValveOpening(), second.gasValve.getPercentValveOpening(), 0.0);
    }
  }

  /** Halving the physical scan interval keeps the ten-second pressure and actuator response close. */
  @Test
  void timestepRefinementKeepsResponseWithinDeclaredSensitivity() {
    PressureControlChain coarse = createChain("coarse timestep");
    PressureControlChain refined = createChain("refined timestep");
    coarse.feed.setFlowRate(28.0, "kg/sec");
    refined.feed.setFlowRate(28.0, "kg/sec");
    coarse.feed.run();
    refined.feed.run();

    runSteps(coarse, 1.0, 10, "coarse timestep");
    runSteps(refined, 0.5, 20, "refined timestep");

    logger.info("pressure-chain refinement pressure coarse={} refined={} bara opening coarse={} refined={} percent",
        coarse.separator.getPressure("bara"), refined.separator.getPressure("bara"),
        coarse.gasValve.getPercentValveOpening(), refined.gasValve.getPercentValveOpening());
    assertEquals(coarse.separator.getPressure("bara"), refined.separator.getPressure("bara"), 0.05,
        "halving dt must change ten-second pressure by no more than 0.05 bar");
    assertEquals(coarse.gasValve.getPercentValveOpening(), refined.gasValve.getPercentValveOpening(), 0.35,
        "halving dt must change ten-second PCV opening by no more than 0.35 percentage point");
  }

  /** Rejected trials restore the complete pressure-control chain in place and replay bit-for-bit. */
  @Test
  void rejectedStepRestoresAndReplaysWholeControlChain() {
    PressureControlChain chain = createChain("rollback");
    chain.feed.setFlowRate(24.0, "kg/sec");
    chain.feed.run();
    UUID id = step("rollback", 0);
    double processTime = chain.process.getTime();
    TransientStepTransaction transaction = chain.process.beginTransientStepTransaction();

    assertAcceptedStepBalance(chain, id);
    double trialPressure = chain.separator.getPressure("bara");
    double trialSample = chain.transmitter.getTransientSample("bara");
    double trialResponse = chain.controller.getResponse();
    double trialOpening = chain.gasValve.getPercentValveOpening();
    double[] trialInventory = componentMoles(chain.separator.getThermoSystem());
    String trialHistory = chain.process.getHistorySnapshot()[0][2];
    transaction.rollback();

    assertEquals(processTime, chain.process.getTime(), 0.0);
    assertAcceptedStepBalance(chain, id);
    assertEquals(trialPressure, chain.separator.getPressure("bara"), 0.0);
    assertEquals(trialSample, chain.transmitter.getTransientSample("bara"), 0.0);
    assertEquals(trialResponse, chain.controller.getResponse(), 0.0);
    assertEquals(trialOpening, chain.gasValve.getPercentValveOpening(), 0.0);
    assertArrayEquals(trialInventory, componentMoles(chain.separator.getThermoSystem()), 0.0);
    assertEquals(trialHistory, chain.process.getHistorySnapshot()[0][2]);
  }

  /** Java restart produces the same next accepted pressure and control state. */
  @Test
  void serializedRestartMatchesNextAcceptedStep() throws Exception {
    PressureControlChain chain = createChain("restart");
    chain.feed.setFlowRate(24.0, "kg/sec");
    chain.feed.run();
    assertAcceptedStepBalance(chain, step("restart", 0));
    ProcessSystem restored = roundTrip(chain.process);
    UUID next = step("restart", 1);

    chain.process.runTransient(STEP_SECONDS, next);
    restored.runTransient(STEP_SECONDS, next);

    Separator restoredSeparator = (Separator) restored.getUnit("restart separator");
    PressureControlValve restoredValve = (PressureControlValve) restored.getUnit("restart PCV");
    PressureTransmitter restoredTransmitter = (PressureTransmitter) restored.getMeasurementDevice("restart PT");
    ControllerDeviceBaseClass restoredController = (ControllerDeviceBaseClass) restoredValve.getController();
    assertEquals(chain.separator.getPressure("bara"), restoredSeparator.getPressure("bara"), 0.0);
    assertArrayEquals(componentMoles(chain.separator.getThermoSystem()),
        componentMoles(restoredSeparator.getThermoSystem()), 0.0);
    assertEquals(chain.transmitter.getTransientSample("bara"), restoredTransmitter.getTransientSample("bara"), 0.0);
    assertEquals(chain.controller.getResponse(), restoredController.getResponse(), 0.0);
    assertEquals(chain.gasValve.getPercentValveOpening(), restoredValve.getPercentValveOpening(), 0.0);
  }

  /** Reports a warmed full-chain timing diagnostic without imposing a machine-specific speed gate. */
  @Test
  void reportSyntheticPressureChainCost() {
    long[] nanosPerStep = new long[5];
    for (int round = 0; round < nanosPerStep.length; round++) {
      PressureControlChain chain = createChain("timing " + round);
      chain.feed.setFlowRate(28.0, "kg/sec");
      chain.feed.run();
      runSteps(chain, 1.0, 5, "timing warmup " + round);
      long start = System.nanoTime();
      runSteps(chain, 1.0, 10, "timing measurement " + round);
      nanosPerStep[round] = (System.nanoTime() - start) / 10L;
      assertTrue(Double.isFinite(chain.separator.getPressure("bara")));
    }
    java.util.Arrays.sort(nanosPerStep);
    logger.info("pressure-chain warmed median={} ns/physical step samples={}", nanosPerStep[2],
        java.util.Arrays.toString(nanosPerStep));
  }

  /**
   * Runs one physical step and checks the separator's discrete component inventory equation.
   *
   * @param chain configured process chain
   * @param id physical-step identity
   * @return component, energy and volume residuals
   */
  private static StepResidual assertAcceptedStepBalance(PressureControlChain chain, UUID id) {
    double[] expected = componentMoles(chain.separator.getThermoSystem());
    SystemInterface inlet = chain.feed.getThermoSystem();
    SystemInterface gasOutlet = chain.separator.getGasOutStream().getThermoSystem();
    SystemInterface liquidOutlet = chain.separator.getLiquidOutStream().getThermoSystem();
    double expectedEnergy = chain.separator.getThermoSystem().getInternalEnergy()
        + STEP_SECONDS * (inlet.getEnthalpy() - gasOutlet.getEnthalpy() - liquidOutlet.getEnthalpy());
    double specifiedVolume = Math.PI / 4.0 * chain.separator.getInternalDiameter()
        * chain.separator.getInternalDiameter() * chain.separator.getSeparatorLength();
    for (int componentIndex = 0; componentIndex < expected.length; componentIndex++) {
      expected[componentIndex] += STEP_SECONDS * (inlet.getComponent(componentIndex).getNumberOfmoles()
          - gasOutlet.getComponent(componentIndex).getNumberOfmoles()
          - liquidOutlet.getComponent(componentIndex).getNumberOfmoles());
    }
    chain.process.runTransient(STEP_SECONDS, id);
    double[] actual = componentMoles(chain.separator.getThermoSystem());
    assertArrayEquals(expected, actual, 1.0e-8, "accepted separator step must conserve each component inventory");
    double maximumResidual = 0.0;
    for (int componentIndex = 0; componentIndex < expected.length; componentIndex++) {
      maximumResidual = Math.max(maximumResidual, Math.abs(expected[componentIndex] - actual[componentIndex]));
    }
    double relativeEnergyResidual = Math.abs(chain.separator.getThermoSystem().getInternalEnergy() - expectedEnergy)
        / Math.max(Math.abs(expectedEnergy), 1.0);
    double relativeVolumeResidual = Math.abs(chain.separator.getThermoSystem().getVolume("m3") - specifiedVolume)
        / specifiedVolume;
    return new StepResidual(maximumResidual, relativeEnergyResidual, relativeVolumeResidual);
  }

  /**
   * Advances a chain through deterministic physical steps.
   *
   * @param chain chain to advance
   * @param dt physical step size in seconds
   * @param count number of steps
   * @param scope deterministic identity scope
   */
  private static void runSteps(PressureControlChain chain, double dt, int count, String scope) {
    for (int index = 0; index < count; index++) {
      chain.process.runTransient(dt, step(scope, index));
    }
  }

  /**
   * Builds the public synthetic separator-pressure control chain.
   *
   * @param prefix unique equipment-name prefix
   * @return initialized dynamic chain
   */
  private static PressureControlChain createChain(String prefix) {
    SystemInterface fluid = new SystemSrkEos(288.15, 55.0);
    fluid.addComponent("methane", 0.72);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.04);
    fluid.addComponent("n-pentane", 0.03);
    fluid.addComponent("n-hexane", 0.05);
    fluid.addComponent("n-heptane", 0.05);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream(prefix + " feed", fluid);
    feed.setFlowRate(20.0, "kg/sec");
    feed.setPressure(55.0, "bara");
    feed.run();

    Separator separator = new Separator(prefix + " separator");
    separator.addStream(feed);
    separator.setInternalDiameter(2.2);
    separator.setSeparatorLength(7.0);
    separator.setLiquidLevel(0.5);
    separator.setCalculateSteadyState(false);

    ThrottlingValve liquidValve = new ThrottlingValve(prefix + " liquid valve", separator.getLiquidOutStream());
    liquidValve.setOutletPressure(40.0);
    liquidValve.setPercentValveOpening(50.0);
    liquidValve.setCalculateSteadyState(false);

    PressureControlValve gasValve = new PressureControlValve(prefix + " PCV", separator.getGasOutStream());
    gasValve.setOutletPressure(45.0);
    gasValve.setPercentValveOpening(50.0);
    gasValve.setTravelTimeConstant(4.0);
    gasValve.setPhysicalStepIdempotencyEnabled(true);
    gasValve.setAutoMode(false);
    gasValve.setCalculateSteadyState(false);

    PressureTransmitter transmitter = new PressureTransmitter(prefix + " PT", separator.getGasOutStream());
    transmitter.setUnit("bara");
    transmitter.setMinimumValue(40.0);
    transmitter.setMaximumValue(70.0);
    transmitter.setTransientSamplingEnabled(true);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass(prefix + " PIC");
    controller.setTransmitter(transmitter);
    controller.setControllerSetPoint(55.0, "bara");
    controller.setControllerParameters(6.0, 30.0, 0.0);
    controller.setReverseActing(false);
    controller.setOutputLimits(5.0, 95.0);
    gasValve.setController(controller);

    ProcessSystem process = new ProcessSystem(prefix + " pressure-control process");
    process.add(feed);
    process.add(separator);
    process.add(liquidValve);
    process.add(gasValve);
    process.add(transmitter);
    process.run();
    return new PressureControlChain(process, feed, separator, gasValve, transmitter, controller);
  }

  /**
   * Returns total component inventories in system component order.
   *
   * @param system thermodynamic system
   * @return component mole inventory
   */
  private static double[] componentMoles(SystemInterface system) {
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
   * @param scope test scope
   * @param index physical-step index
   * @return deterministic identifier
   */
  private static UUID step(String scope, long index) {
    return TransientStepIdentifier.deterministicPhysicalStep("separator-pressure-chain-" + scope, index);
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

  /** Per-step conservation evidence. */
  private static final class StepResidual {
    private final double componentMoles;
    private final double relativeEnergy;
    private final double relativeVolume;

    /**
     * Creates a residual tuple.
     *
     * @param componentMoles maximum component residual in mol
     * @param relativeEnergy relative internal-energy residual
     * @param relativeVolume relative fixed-volume residual
     */
    private StepResidual(double componentMoles, double relativeEnergy, double relativeVolume) {
      this.componentMoles = componentMoles;
      this.relativeEnergy = relativeEnergy;
      this.relativeVolume = relativeVolume;
    }
  }

  /** Strongly typed fixture references. */
  private static final class PressureControlChain {
    private final ProcessSystem process;
    private final Stream feed;
    private final Separator separator;
    private final PressureControlValve gasValve;
    private final PressureTransmitter transmitter;
    private final ControllerDeviceBaseClass controller;

    /**
     * Creates fixture references.
     *
     * @param process process system
     * @param feed separator feed
     * @param separator dynamic separator
     * @param gasValve controlled gas valve
     * @param transmitter sampled pressure transmitter
     * @param controller attached PID
     */
    private PressureControlChain(ProcessSystem process, Stream feed, Separator separator, PressureControlValve gasValve,
        PressureTransmitter transmitter, ControllerDeviceBaseClass controller) {
      this.process = process;
      this.feed = feed;
      this.separator = separator;
      this.gasValve = gasValve;
      this.transmitter = transmitter;
      this.controller = controller;
    }
  }
}
