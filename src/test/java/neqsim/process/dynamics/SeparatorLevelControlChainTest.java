package neqsim.process.dynamics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.ControllerPerformanceMetrics;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.LevelControlValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * End-to-end qualification of a separator inventory, sampled level transmitter, PID and liquid control valve.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SeparatorLevelControlChainTest extends neqsim.NeqSimTest {
  private static final double STEP_SECONDS = 1.0;

  /**
   * A feed disturbance exercises inventory, scan delay, controller KPIs, actuator response and component balance.
   */
  @Test
  void feedDisturbanceClosesInventoryBalanceAndProducesBoundedControlResponse() {
    LevelControlChain chain = createChain("disturbance");
    assertTrue(chain.process.getTransientTransactionCoverage().isComplete());
    double initialLevel = chain.separator.getLiquidLevel();
    double initialOpening = chain.liquidValve.getPercentValveOpening();
    double maximumLevel = initialLevel;
    double maximumOpening = initialOpening;
    double maximumComponentResidual = 0.0;

    maximumComponentResidual = Math.max(maximumComponentResidual,
        assertAcceptedStepBalance(chain, step("baseline", 0)));
    chain.feed.setFlowRate(28.0, "kg/sec");
    chain.feed.run();

    for (int index = 0; index < 30; index++) {
      double levelAtScan = chain.separator.getLiquidLevel();
      maximumComponentResidual = Math.max(maximumComponentResidual,
          assertAcceptedStepBalance(chain, step("disturbance", index)));
      assertEquals(levelAtScan, chain.transmitter.getTransientSample(), 0.0,
          "the controller scan must hold the start-of-step vessel level");
      assertEquals(chain.transmitter.getTransientSample(),
          chain.controller.getEventLog().get(chain.controller.getEventLog().size() - 1).getMeasuredValue(), 0.0);
      assertTrue(chain.separator.getLiquidLevel() >= 0.0 && chain.separator.getLiquidLevel() <= 1.0);
      assertTrue(chain.separator.getThermoSystem().getPressure("bara") > 0.0);
      assertTrue(chain.separator.getThermoSystem().getTemperature("K") > 0.0);
      assertTrue(
          chain.liquidValve.getPercentValveOpening() >= 0.0 && chain.liquidValve.getPercentValveOpening() <= 100.0);
      maximumLevel = Math.max(maximumLevel, chain.separator.getLiquidLevel());
      maximumOpening = Math.max(maximumOpening, chain.liquidValve.getPercentValveOpening());
    }

    ControllerPerformanceMetrics metrics = chain.controller.getPerformanceMetrics();
    assertTrue(maximumLevel > initialLevel, "increased two-phase feed must increase liquid inventory");
    assertTrue(maximumOpening > initialOpening, "direct level action must open the liquid outlet valve");
    assertEquals(31, metrics.getSampleCount());
    assertTrue(metrics.getIntegralAbsoluteError() > 0.0);
    assertTrue(metrics.getPeakAbsoluteError() > 0.0);
    assertTrue(metrics.getControllerOutputTravel() > 0.0);
    assertTrue(Double.isFinite(metrics.getSettlingTime()));
    assertTrue(maximumLevel <= 0.513, "30 s peak level must remain below the declared 0.513 fraction limit");
    assertTrue(maximumOpening <= 51.1, "30 s valve opening must remain below the declared 51.1% limit");
    assertTrue(metrics.getIntegralAbsoluteError() <= 0.19,
        "30 s level-control IAE must remain below 0.19 fraction-seconds");
    assertTrue(metrics.getPeakAbsoluteError() <= 0.012,
        "held-sample peak absolute error must remain below 0.012 fraction");
    assertTrue(metrics.getControllerOutputTravel() <= 1.09,
        "controller-output travel must remain below 1.09 percentage points");
    assertEquals(0.0, metrics.getSettlingTime(), 0.0,
        "the held level must remain inside the default two-percent settling band");
    assertTrue(maximumComponentResidual <= 1.0e-8, "component inventory residual must remain below 1e-8 mol per step");
  }

  /** Two independent chains reproduce every physical and control value bit-for-bit. */
  @Test
  void repeatedExecutionIsDeterministic() {
    LevelControlChain first = createChain("repeat first");
    LevelControlChain second = createChain("repeat second");
    first.feed.setFlowRate(28.0, "kg/sec");
    second.feed.setFlowRate(28.0, "kg/sec");
    first.feed.run();
    second.feed.run();

    for (int index = 0; index < 12; index++) {
      UUID id = step("repeat", index);
      first.process.runTransient(STEP_SECONDS, id);
      second.process.runTransient(STEP_SECONDS, id);
      assertEquals(first.separator.getLiquidLevel(), second.separator.getLiquidLevel(), 0.0);
      assertArrayEquals(componentMoles(first.separator.getThermoSystem()),
          componentMoles(second.separator.getThermoSystem()), 0.0);
      assertEquals(first.transmitter.getTransientSample(), second.transmitter.getTransientSample(), 0.0);
      assertEquals(first.controller.getResponse(), second.controller.getResponse(), 0.0);
      assertEquals(first.liquidValve.getPercentValveOpening(), second.liquidValve.getPercentValveOpening(), 0.0);
    }
  }

  /** Halving the physical scan interval keeps the 30 s process and actuator response close. */
  @Test
  void timestepRefinementKeepsResponseWithinDeclaredSensitivity() {
    LevelControlChain coarse = createChain("coarse timestep");
    LevelControlChain refined = createChain("refined timestep");
    coarse.feed.setFlowRate(28.0, "kg/sec");
    refined.feed.setFlowRate(28.0, "kg/sec");
    coarse.feed.run();
    refined.feed.run();

    runSteps(coarse, 1.0, 30, "coarse timestep");
    runSteps(refined, 0.5, 60, "refined timestep");

    assertEquals(coarse.separator.getLiquidLevel(), refined.separator.getLiquidLevel(), 5.0e-4,
        "halving dt must change the 30 s level by no more than 0.0005 fraction");
    assertEquals(coarse.liquidValve.getPercentValveOpening(), refined.liquidValve.getPercentValveOpening(), 0.1,
        "halving dt must change the 30 s liquid-valve opening by no more than 0.1 percentage point");
  }

  /** Rejected trials restore the complete chain in place and replay bit-for-bit. */
  @Test
  void rejectedStepRestoresAndReplaysWholeControlChain() {
    LevelControlChain chain = createChain("rollback");
    chain.feed.setFlowRate(24.0, "kg/sec");
    chain.feed.run();
    UUID id = step("rollback", 0);
    double processTime = chain.process.getTime();
    StreamInterface gasOutlet = chain.separator.getGasOutStream();
    StreamInterface liquidOutlet = chain.separator.getLiquidOutStream();
    TransientStepTransaction transaction = chain.process.beginTransientStepTransaction();

    assertAcceptedStepBalance(chain, id);
    double trialLevel = chain.separator.getLiquidLevel();
    double trialSample = chain.transmitter.getTransientSample();
    double trialResponse = chain.controller.getResponse();
    double trialOpening = chain.liquidValve.getPercentValveOpening();
    double[] trialInventory = componentMoles(chain.separator.getThermoSystem());
    String trialHistory = chain.process.getHistorySnapshot()[0][2];
    transaction.rollback();

    assertEquals(processTime, chain.process.getTime(), 0.0);
    assertSame(gasOutlet, chain.separator.getGasOutStream());
    assertSame(liquidOutlet, chain.separator.getLiquidOutStream());
    assertAcceptedStepBalance(chain, id);
    assertEquals(trialLevel, chain.separator.getLiquidLevel(), 0.0);
    assertEquals(trialSample, chain.transmitter.getTransientSample(), 0.0);
    assertEquals(trialResponse, chain.controller.getResponse(), 0.0);
    assertEquals(trialOpening, chain.liquidValve.getPercentValveOpening(), 0.0);
    assertArrayEquals(trialInventory, componentMoles(chain.separator.getThermoSystem()), 0.0);
    assertEquals(trialHistory, chain.process.getHistorySnapshot()[0][2]);
  }

  /** Java restart produces the same next accepted process and control state. */
  @Test
  void serializedRestartMatchesNextAcceptedStep() throws Exception {
    LevelControlChain chain = createChain("restart");
    chain.feed.setFlowRate(24.0, "kg/sec");
    chain.feed.run();
    assertAcceptedStepBalance(chain, step("restart", 0));
    ProcessSystem restored = roundTrip(chain.process);
    UUID next = step("restart", 1);

    chain.process.runTransient(STEP_SECONDS, next);
    restored.runTransient(STEP_SECONDS, next);

    Separator restoredSeparator = (Separator) restored.getUnit("restart separator");
    LevelControlValve restoredValve = (LevelControlValve) restored.getUnit("restart LCV");
    LevelTransmitter restoredTransmitter = (LevelTransmitter) restored.getMeasurementDevice("restart LT");
    ControllerDeviceBaseClass restoredController = (ControllerDeviceBaseClass) restoredValve.getController();
    assertEquals(chain.separator.getLiquidLevel(), restoredSeparator.getLiquidLevel(), 0.0);
    assertArrayEquals(componentMoles(chain.separator.getThermoSystem()),
        componentMoles(restoredSeparator.getThermoSystem()), 0.0);
    assertEquals(chain.transmitter.getTransientSample(), restoredTransmitter.getTransientSample(), 0.0);
    assertEquals(chain.controller.getResponse(), restoredController.getResponse(), 0.0);
    assertEquals(chain.liquidValve.getPercentValveOpening(), restoredValve.getPercentValveOpening(), 0.0);
  }

  /**
   * Runs one physical step and checks the separator's discrete component inventory equation.
   *
   * @param chain configured process chain
   * @param id physical-step identity
   * @return maximum absolute component-inventory residual [mol]
   */
  private static double assertAcceptedStepBalance(LevelControlChain chain, UUID id) {
    double[] expected = componentMoles(chain.separator.getThermoSystem());
    SystemInterface inlet = chain.feed.getThermoSystem();
    SystemInterface gasOutlet = chain.separator.getGasOutStream().getThermoSystem();
    SystemInterface liquidOutlet = chain.separator.getLiquidOutStream().getThermoSystem();
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
    return maximumResidual;
  }

  /**
   * Advances a chain through deterministic physical steps.
   *
   * @param chain chain to advance
   * @param dt physical step size [s]
   * @param count number of steps
   * @param scope deterministic identity scope
   */
  private static void runSteps(LevelControlChain chain, double dt, int count, String scope) {
    for (int index = 0; index < count; index++) {
      chain.process.runTransient(dt, step(scope, index));
    }
  }

  /**
   * Builds the public synthetic separator-level control chain.
   *
   * @param prefix unique equipment-name prefix
   * @return initialized dynamic chain
   */
  private static LevelControlChain createChain(String prefix) {
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

    LevelControlValve liquidValve = new LevelControlValve(prefix + " LCV", separator.getLiquidOutStream());
    liquidValve.setOutletPressure(40.0);
    liquidValve.setPercentValveOpening(50.0);
    liquidValve.setTravelTimeConstant(4.0);
    liquidValve.setPhysicalStepIdempotencyEnabled(true);
    liquidValve.setAutoMode(false);
    liquidValve.setCalculateSteadyState(false);

    ThrottlingValve gasValve = new ThrottlingValve(prefix + " gas valve", separator.getGasOutStream());
    gasValve.setOutletPressure(45.0);
    gasValve.setPercentValveOpening(50.0);
    gasValve.setCalculateSteadyState(false);

    LevelTransmitter transmitter = new LevelTransmitter(prefix + " LT", separator);
    transmitter.setTransientSamplingEnabled(true);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass(prefix + " LIC");
    controller.setTransmitter(transmitter);
    controller.setControllerSetPoint(0.5);
    controller.setControllerParameters(0.8, 120.0, 0.0);
    controller.setReverseActing(false);
    liquidValve.setController(controller);

    ProcessSystem process = new ProcessSystem(prefix + " level-control process");
    process.add(feed);
    process.add(separator);
    process.add(liquidValve);
    process.add(gasValve);
    process.add(transmitter);
    process.run();
    return new LevelControlChain(process, feed, separator, liquidValve, transmitter, controller);
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
    return TransientStepIdentifier.deterministicPhysicalStep("separator-level-chain-" + scope, index);
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

  /** Strongly typed fixture references. */
  private static final class LevelControlChain {
    private final ProcessSystem process;
    private final Stream feed;
    private final Separator separator;
    private final LevelControlValve liquidValve;
    private final LevelTransmitter transmitter;
    private final ControllerDeviceBaseClass controller;

    /**
     * Creates fixture references.
     *
     * @param process process system
     * @param feed separator feed
     * @param separator dynamic separator
     * @param liquidValve controlled liquid valve
     * @param transmitter sampled level transmitter
     * @param controller attached PID
     */
    private LevelControlChain(ProcessSystem process, Stream feed, Separator separator, LevelControlValve liquidValve,
        LevelTransmitter transmitter, ControllerDeviceBaseClass controller) {
      this.process = process;
      this.feed = feed;
      this.separator = separator;
      this.liquidValve = liquidValve;
      this.transmitter = transmitter;
      this.controller = controller;
    }
  }
}
