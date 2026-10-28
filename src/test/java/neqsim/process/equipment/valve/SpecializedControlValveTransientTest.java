package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Quantitative scan-order, hydraulic-response, and rollback tests for embedded LCV/PCV control. */
class SpecializedControlValveTransientTest {
  private static final double TOLERANCE = 1.0e-10;

  /** Direct level action opens a discharge valve on high level and increases physical flow. */
  @Test
  void directLevelActionHasDeclaredHydraulicDirection() {
    LevelStudy high = createLevelStudy("high level");
    double baselineFlow = high.valve.getOutletStream().getFlowRate("kg/hr");
    high.valve.setMeasuredLevel(60.0);
    high.valve.runTransient(1.0, UUID.randomUUID());

    assertEquals(-10.0, high.valve.getControlError(), 0.0);
    assertEquals(60.0, high.valve.getPercentValveOpening(), TOLERANCE);
    assertTrue(high.valve.getOutletStream().getFlowRate("kg/hr") > baselineFlow);
    assertEquals(0.0, high.valve.getMassBalance("kg/hr"), 1.0e-6);

    LevelStudy low = createLevelStudy("low level");
    double lowBaselineFlow = low.valve.getOutletStream().getFlowRate("kg/hr");
    low.valve.setMeasuredLevel(40.0);
    low.valve.runTransient(1.0, UUID.randomUUID());

    assertEquals(10.0, low.valve.getControlError(), 0.0);
    assertEquals(40.0, low.valve.getPercentValveOpening(), TOLERANCE);
    assertTrue(low.valve.getOutletStream().getFlowRate("kg/hr") < lowBaselineFlow);
  }

  /** The embedded command precedes an exact first-order actuator and advances once per UUID. */
  @Test
  void levelControlScanOrderAndActuatorLagAreQuantitative() {
    LevelStudy study = createLevelStudy("lagged level");
    LevelControlValve valve = study.valve;
    valve.setMeasuredLevel(60.0);
    valve.setControllerGain(3.0);
    valve.setTravelModel(ValveTravelModel.FIRST_ORDER_LAG);
    valve.setTravelTimeConstant(4.0);
    UUID stepId = UUID.randomUUID();

    valve.runTransient(1.0, stepId);
    double expectedOpening = 50.0 + (80.0 - 50.0) * (1.0 - Math.exp(-1.0 / 4.0));
    double firstFlow = valve.getOutletStream().getFlowRate("kg/hr");

    assertEquals(80.0, valve.getTargetPercentValveOpening(), TOLERANCE);
    assertEquals(expectedOpening, valve.getPercentValveOpening(), TOLERANCE);
    valve.runTransient(1.0, stepId);
    assertEquals(80.0, valve.getTargetPercentValveOpening(), TOLERANCE);
    assertEquals(expectedOpening, valve.getPercentValveOpening(), TOLERANCE);
    assertEquals(firstFlow, valve.getOutletStream().getFlowRate("kg/hr"), TOLERANCE);
  }

  /** Rejection restores embedded control memory, actuator state, outlet state, and object identity. */
  @Test
  void levelControlRollbackAndReplayAreDeterministic() {
    LevelStudy study = createLevelStudy("rollback level");
    LevelControlValve valve = study.valve;
    valve.setMeasuredLevel(60.0);
    valve.setControllerGain(3.0);
    valve.setTravelModel(ValveTravelModel.FIRST_ORDER_LAG);
    valve.setTravelTimeConstant(4.0);
    UUID stepId = UUID.randomUUID();
    Object outletIdentity = valve.getOutletStream();
    double initialFlow = valve.getOutletStream().getFlowRate("kg/hr");
    double trialOpening;
    double trialFlow;

    try (TransientStepTransaction transaction = study.process.beginTransientStepTransaction()) {
      study.process.runTransient(1.0, stepId);
      trialOpening = valve.getPercentValveOpening();
      trialFlow = valve.getOutletStream().getFlowRate("kg/hr");
      transaction.rollback();
    }

    assertEquals(50.0, valve.getPercentValveOpening(), TOLERANCE);
    assertEquals(50.0, valve.getTargetPercentValveOpening(), TOLERANCE);
    assertEquals(0.0, valve.getControlError(), TOLERANCE);
    assertEquals(initialFlow, valve.getOutletStream().getFlowRate("kg/hr"), TOLERANCE);
    assertSame(outletIdentity, valve.getOutletStream());
    assertTrue(study.process.getTransientTransactionCoverage().isComplete(),
        study.process.getTransientTransactionCoverage().getBlockingIssues().toString());

    study.process.runTransientTransactional(1.0, stepId);
    assertEquals(trialOpening, valve.getPercentValveOpening(), TOLERANCE);
    assertEquals(trialFlow, valve.getOutletStream().getFlowRate("kg/hr"), TOLERANCE);
  }

  /** Downstream and back-pressure modes move opening and real flow in the declared directions. */
  @Test
  void pressureModesHaveDeclaredHydraulicDirections() {
    PressureStudy downstream = createPressureStudy("downstream pressure");
    double downstreamBaseline = downstream.valve.getOutletStream().getFlowRate("kg/hr");
    downstream.valve.setControlMode(PressureControlValve.ControlMode.DOWNSTREAM);
    downstream.valve.setPressureSetpoint(12.0);
    downstream.valve.setControllerGain(2.0);
    downstream.valve.runTransient(1.0, UUID.randomUUID());

    assertEquals(10.0, downstream.valve.getProcessVariable(), 1.0e-8);
    assertEquals(2.0, downstream.valve.getControlError(), 1.0e-8);
    assertEquals(54.0, downstream.valve.getPercentValveOpening(), TOLERANCE);
    assertTrue(downstream.valve.getOutletStream().getFlowRate("kg/hr") > downstreamBaseline);

    PressureStudy upstream = createPressureStudy("upstream pressure");
    double upstreamBaseline = upstream.valve.getOutletStream().getFlowRate("kg/hr");
    upstream.valve.setControlMode(PressureControlValve.ControlMode.UPSTREAM);
    upstream.valve.setPressureSetpoint(25.0);
    upstream.valve.setControllerGain(2.0);
    upstream.valve.runTransient(1.0, UUID.randomUUID());

    assertEquals(20.0, upstream.valve.getProcessVariable(), 1.0e-8);
    assertEquals(5.0, upstream.valve.getControlError(), 1.0e-8);
    assertEquals(40.0, upstream.valve.getPercentValveOpening(), TOLERANCE);
    assertTrue(upstream.valve.getOutletStream().getFlowRate("kg/hr") < upstreamBaseline);
    assertEquals(0.0, upstream.valve.getMassBalance("kg/hr"), 1.0e-6);
  }

  /** Java restart retains embedded pressure-control scan identity and reproduces the next step. */
  @Test
  void pressureControlRestartMatchesUninterruptedExecution() throws Exception {
    PressureStudy study = createPressureStudy("restart pressure");
    PressureControlValve valve = study.valve;
    valve.setPressureSetpoint(12.0);
    valve.setControllerGain(2.0);
    study.process.runTransientTransactional(1.0, UUID.randomUUID());

    ProcessSystem restarted = roundTrip(study.process);
    PressureControlValve restartedValve = (PressureControlValve) restarted.getUnit("restart pressure PCV");
    UUID nextStep = UUID.randomUUID();
    study.process.runTransientTransactional(0.5, nextStep);
    restarted.runTransientTransactional(0.5, nextStep);

    assertEquals(valve.getProcessVariable(), restartedValve.getProcessVariable(), 0.0);
    assertEquals(valve.getControlError(), restartedValve.getControlError(), 0.0);
    assertEquals(valve.getTargetPercentValveOpening(), restartedValve.getTargetPercentValveOpening(), 0.0);
    assertEquals(valve.getPercentValveOpening(), restartedValve.getPercentValveOpening(), 0.0);
    assertEquals(valve.getOutletStream().getFlowRate("kg/hr"), restartedValve.getOutletStream().getFlowRate("kg/hr"),
        0.0);
  }

  private static LevelStudy createLevelStudy(String name) {
    Stream feed = createFeed(name + " feed");
    LevelControlValve valve = new LevelControlValve(name + " LCV", feed);
    valve.setAutoMode(false);
    valve.setPercentValveOpening(50.0);
    valve.setCv(100.0);
    valve.setOutletPressure(10.0, "bara");
    ProcessSystem process = new ProcessSystem(name + " process");
    process.add(feed);
    process.add(valve);
    process.run();
    valve.setCalculateSteadyState(false);
    valve.setPhysicalStepIdempotencyEnabled(true);
    valve.setLevelSetpoint(50.0);
    valve.setControllerGain(1.0);
    valve.setAutoMode(true);
    process.setTime(0.0);
    return new LevelStudy(process, valve);
  }

  private static PressureStudy createPressureStudy(String name) {
    Stream feed = createFeed(name + " feed");
    PressureControlValve valve = new PressureControlValve(name + " PCV", feed);
    valve.setAutoMode(false);
    valve.setPercentValveOpening(50.0);
    valve.setCv(100.0);
    valve.setOutletPressure(10.0, "bara");
    ProcessSystem process = new ProcessSystem(name + " process");
    process.add(feed);
    process.add(valve);
    process.run();
    valve.setCalculateSteadyState(false);
    valve.setPhysicalStepIdempotencyEnabled(true);
    valve.setAutoMode(true);
    process.setTime(0.0);
    return new PressureStudy(process, valve);
  }

  private static Stream createFeed(String name) {
    SystemInterface fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    return feed;
  }

  private static ProcessSystem roundTrip(ProcessSystem process) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(process);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (ProcessSystem) input.readObject();
    }
  }

  private static final class LevelStudy {
    private final ProcessSystem process;
    private final LevelControlValve valve;

    private LevelStudy(ProcessSystem process, LevelControlValve valve) {
      this.process = process;
      this.valve = valve;
    }
  }

  private static final class PressureStudy {
    private final ProcessSystem process;
    private final PressureControlValve valve;

    private PressureStudy(ProcessSystem process, PressureControlValve valve) {
      this.process = process;
      this.valve = valve;
    }
  }
}
