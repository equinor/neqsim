package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.dynamics.TransientTransactionCoverage;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Quantitative transaction and physical-step tests for dynamic valve actuator state. */
class ValveTransientStateTransactionTest {
  private static final double TOLERANCE = 1.0e-10;

  /** First-order valve travel follows the exact exponential response at every tested step size. */
  @Test
  void firstOrderTravelMatchesAnalyticalResponseAndTimestepRefinement() {
    double coarse = runFirstOrderTravel(1.0, 8);
    double fine = runFirstOrderTravel(0.125, 64);
    double expected = 80.0 + (20.0 - 80.0) * Math.exp(-8.0 / 4.0);

    assertEquals(expected, coarse, TOLERANCE);
    assertEquals(expected, fine, TOLERANCE);
    assertEquals(coarse, fine, TOLERANCE);
  }

  /** Repeated execution with one physical-step identity does not advance the actuator twice. */
  @Test
  void actuatorAdvancesOncePerPhysicalStepIdentity() {
    ValveStudy study = createStudy(new ThrottlingValve("FV-101", createFeed("feed")));
    configureFirstOrderTravel(study.valve);
    UUID stepId = UUID.randomUUID();

    study.valve.runTransient(1.0, stepId);
    double once = study.valve.getPercentValveOpening();
    study.valve.runTransient(1.0, stepId);

    assertEquals(once, study.valve.getPercentValveOpening(), TOLERANCE);
    assertEquals(80.0 + (20.0 - 80.0) * Math.exp(-1.0 / 4.0), once, TOLERANCE);
  }

  /** Rate limits, deadband, stiction, and reversal hysteresis retain their declared units. */
  @Test
  void actuatorNonlinearitiesHaveQuantitativeResponses() {
    ValveStudy study = createStudy(new ThrottlingValve("FV-103", createFeed("feed")));
    ThrottlingValve valve = study.valve;

    valve.setPercentValveOpening(50.0);
    valve.setValveDeadband(2.0);
    valve.setTargetPercentValveOpening(51.0);
    valve.runTransient(1.0, UUID.randomUUID());
    assertEquals(50.0, valve.getPercentValveOpening(), TOLERANCE);

    valve.setValveDeadband(0.0);
    valve.setValveStiction(5.0);
    valve.setTargetPercentValveOpening(53.0);
    valve.runTransient(1.0, UUID.randomUUID());
    assertEquals(50.0, valve.getPercentValveOpening(), TOLERANCE);
    valve.setTargetPercentValveOpening(56.0);
    valve.runTransient(1.0, UUID.randomUUID());
    assertEquals(56.0, valve.getPercentValveOpening(), TOLERANCE);

    valve.setValveStiction(0.0);
    valve.setValveHysteresis(4.0);
    valve.setTargetPercentValveOpening(60.0);
    valve.runTransient(1.0, UUID.randomUUID());
    assertEquals(60.0, valve.getPercentValveOpening(), TOLERANCE);
    valve.setTargetPercentValveOpening(40.0);
    valve.runTransient(1.0, UUID.randomUUID());
    assertEquals(42.0, valve.getPercentValveOpening(), TOLERANCE);
  }

  /** Asymmetric full-stroke times bound opening and closing travel independently. */
  @Test
  void asymmetricLinearTravelIsRateBounded() {
    ValveStudy study = createStudy(new ThrottlingValve("FV-104", createFeed("feed")));
    ThrottlingValve valve = study.valve;
    valve.setTravelModel(ValveTravelModel.LINEAR_RATE_LIMIT);
    valve.setOpeningTravelTime(10.0);
    valve.setClosingTravelTime(20.0);
    valve.setPercentValveOpening(50.0);

    valve.setTargetPercentValveOpening(100.0);
    valve.runTransient(1.0, UUID.randomUUID());
    assertEquals(60.0, valve.getPercentValveOpening(), TOLERANCE);

    valve.setTargetPercentValveOpening(0.0);
    valve.runTransient(1.0, UUID.randomUUID());
    assertEquals(55.0, valve.getPercentValveOpening(), TOLERANCE);
    assertTrue(valve.getPercentValveOpening() >= 0.0);
    assertTrue(valve.getPercentValveOpening() <= 100.0);
  }

  /** A rejected process step restores valve, outlet, clock, and actuator-step identity in place. */
  @Test
  void rejectedStepRestoresAndReplayReproducesValveState() {
    ValveStudy study = createStudy(new ThrottlingValve("FV-102", createFeed("feed")));
    configureFirstOrderTravel(study.valve);
    UUID stepId = UUID.randomUUID();
    double initialFlow = study.valve.getOutletStream().getFlowRate("kg/hr");
    Object outletIdentity = study.valve.getOutletStream();

    double acceptedOpening;
    double acceptedFlow;
    try (TransientStepTransaction transaction = study.process.beginTransientStepTransaction()) {
      study.process.runTransient(1.0, stepId);
      acceptedOpening = study.valve.getPercentValveOpening();
      acceptedFlow = study.valve.getOutletStream().getFlowRate("kg/hr");
      transaction.rollback();
    }

    assertEquals(20.0, study.valve.getPercentValveOpening(), TOLERANCE);
    assertEquals(80.0, study.valve.getTargetPercentValveOpening(), TOLERANCE);
    assertEquals(initialFlow, study.valve.getOutletStream().getFlowRate("kg/hr"), TOLERANCE);
    assertEquals(0.0, study.process.getTime(), TOLERANCE);
    assertTrue(outletIdentity == study.valve.getOutletStream());

    study.process.runTransientTransactional(1.0, stepId);

    assertEquals(acceptedOpening, study.valve.getPercentValveOpening(), TOLERANCE);
    assertEquals(acceptedFlow, study.valve.getOutletStream().getFlowRate("kg/hr"), TOLERANCE);
    assertEquals(0.0, study.valve.getMassBalance("kg/hr"), 1.0e-6);
    assertEquals(1.0, study.process.getTime(), TOLERANCE);
  }

  /**
   * Java restart retains the actuator step identity and reproduces the next physical step.
   *
   * @throws Exception if serialization fails
   */
  @Test
  void serializedRestartMatchesUninterruptedActuatorTrajectory() throws Exception {
    ValveStudy study = createStudy(new ThrottlingValve("FV-105", createFeed("feed")));
    configureFirstOrderTravel(study.valve);
    study.process.runTransientTransactional(1.0, UUID.randomUUID());
    ProcessSystem restarted = roundTrip(study.process);
    ThrottlingValve restartedValve = (ThrottlingValve) restarted.getUnit("FV-105");
    UUID nextStep = UUID.randomUUID();

    study.process.runTransientTransactional(0.5, nextStep);
    restarted.runTransientTransactional(0.5, nextStep);

    assertEquals(study.process.getTime(), restarted.getTime(), 0.0);
    assertEquals(study.valve.getPercentValveOpening(), restartedValve.getPercentValveOpening(), 0.0);
    assertEquals(study.valve.getTargetPercentValveOpening(), restartedValve.getTargetPercentValveOpening(), 0.0);
    assertEquals(study.valve.getOutletStream().getFlowRate("kg/hr"),
        restartedValve.getOutletStream().getFlowRate("kg/hr"), 0.0);
  }

  /** Attached PID state is captured independently while the valve snapshot remains complete. */
  @Test
  void attachedControllerAndValveHaveCompleteTransactionCoverage() {
    ValveStudy study = createStudy(new ControlValve("FCV-101", createFeed("feed")));
    study.valve.setController(new ControllerDeviceBaseClass("FC-101"));

    TransientTransactionCoverage coverage = study.process.getTransientTransactionCoverage();

    assertTrue(coverage.isComplete(), coverage.getBlockingIssues().toString());
    assertEquals(3, coverage.getProcessElementCount());
    assertEquals(3, coverage.getParticipantCount());
  }

  /** ESD fail-close travel is rollback-safe and advances once for a repeated step identity. */
  @Test
  void esdFailCloseStateRollsBackAndReplaysDeterministically() {
    ESDValve valve = new ESDValve("ESDV-101", createFeed("feed"));
    ValveStudy study = createStudy(valve);
    valve.setStrokeTime(4.0);
    valve.deEnergize();
    UUID stepId = UUID.randomUUID();

    try (TransientStepTransaction transaction = study.process.beginTransientStepTransaction()) {
      study.process.runTransient(1.0, stepId);
      assertEquals(75.0, valve.getPercentValveOpening(), TOLERANCE);
      assertEquals(1.0, valve.getTimeElapsedSinceTrip(), TOLERANCE);
      study.process.runTransient(1.0, stepId);
      assertEquals(75.0, valve.getPercentValveOpening(), TOLERANCE);
      assertEquals(1.0, valve.getTimeElapsedSinceTrip(), TOLERANCE);
      transaction.rollback();
    }

    assertEquals(100.0, valve.getPercentValveOpening(), TOLERANCE);
    assertEquals(0.0, valve.getTimeElapsedSinceTrip(), TOLERANCE);
    assertTrue(valve.isClosing());

    study.process.runTransientTransactional(1.0, stepId);
    assertEquals(75.0, valve.getPercentValveOpening(), TOLERANCE);
    assertEquals(1.0, valve.getTimeElapsedSinceTrip(), TOLERANCE);
  }

  /** Blowdown fail-open travel is rollback-safe and advances once for a repeated step identity. */
  @Test
  void blowdownFailOpenStateRollsBackAndReplaysDeterministically() {
    BlowdownValve valve = new BlowdownValve("BDV-101", createFeed("feed"));
    ValveStudy study = createStudy(valve);
    valve.setOpeningTime(4.0);
    valve.activate();
    UUID stepId = UUID.randomUUID();

    try (TransientStepTransaction transaction = study.process.beginTransientStepTransaction()) {
      study.process.runTransient(1.0, stepId);
      assertEquals(25.0, valve.getPercentValveOpening(), TOLERANCE);
      assertEquals(1.0, valve.getTimeElapsed(), TOLERANCE);
      study.process.runTransient(1.0, stepId);
      assertEquals(25.0, valve.getPercentValveOpening(), TOLERANCE);
      assertEquals(1.0, valve.getTimeElapsed(), TOLERANCE);
      transaction.rollback();
    }

    assertEquals(0.0, valve.getPercentValveOpening(), TOLERANCE);
    assertEquals(0.0, valve.getTimeElapsed(), TOLERANCE);
    assertTrue(valve.isActivated());
    assertTrue(valve.isOpening());

    study.process.runTransientTransactional(1.0, stepId);
    assertEquals(25.0, valve.getPercentValveOpening(), TOLERANCE);
    assertEquals(1.0, valve.getTimeElapsed(), TOLERANCE);
  }

  /** Unsupported safety subclasses remain explicit blockers rather than partial snapshots. */
  @Test
  void unsupportedValveSubclassReportsCoverageBlocker() {
    ValveStudy study = createStudy(new HIPPSValve("HIPPS-XV-101", createFeed("feed")));

    TransientTransactionCoverage coverage = study.process.getTransientTransactionCoverage();

    assertFalse(coverage.isComplete());
    assertTrue(coverage.getBlockingIssues().stream().anyMatch(issue -> issue.contains("must extend the snapshot")));
  }

  /**
   * Integrates an exact first-order actuator for a fixed eight-second interval.
   *
   * @param dt timestep in seconds
   * @param steps number of steps
   * @return final valve opening in percent
   */
  private static double runFirstOrderTravel(double dt, int steps) {
    ValveStudy study = createStudy(new ThrottlingValve("FV-benchmark", createFeed("feed")));
    configureFirstOrderTravel(study.valve);
    for (int i = 0; i < steps; i++) {
      study.valve.runTransient(dt, UUID.randomUUID());
    }
    return study.valve.getPercentValveOpening();
  }

  /**
   * Configures the common analytical first-order benchmark.
   *
   * @param valve valve to configure
   */
  private static void configureFirstOrderTravel(ThrottlingValve valve) {
    valve.setTravelModel(ValveTravelModel.FIRST_ORDER_LAG);
    valve.setTravelTimeConstant(4.0);
    valve.setPercentValveOpening(20.0);
    valve.setTargetPercentValveOpening(80.0);
  }

  /**
   * Initializes a two-element process with a steady valve state and zeroed process clock.
   *
   * @param valve valve whose inlet is the fixture feed
   * @return initialized study
   */
  private static ValveStudy createStudy(ThrottlingValve valve) {
    Stream feed = (Stream) valve.getInletStream();
    valve.setOutletPressure(10.0, "bara");
    valve.setCv(100.0);
    ProcessSystem process = new ProcessSystem("valve study");
    process.add(feed);
    process.add(valve);
    process.run();
    valve.setCalculateSteadyState(false);
    process.setTime(0.0);
    return new ValveStudy(process, valve);
  }

  /**
   * Creates the declared methane/ethane benchmark feed.
   *
   * @param name stream name
   * @return initialized feed
   */
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

  /**
   * Serializes and deserializes one process using the public Java object contract.
   *
   * @param process process to restart
   * @return independent deserialized process
   * @throws Exception if serialization fails
   */
  private static ProcessSystem roundTrip(ProcessSystem process) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(process);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (ProcessSystem) input.readObject();
    }
  }

  /** Small immutable fixture for a valve and its owning process. */
  private static final class ValveStudy {
    private final ProcessSystem process;
    private final ThrottlingValve valve;

    /**
     * Creates a fixture.
     *
     * @param process owning process
     * @param valve valve under test
     */
    private ValveStudy(ProcessSystem process, ThrottlingValve valve) {
      this.process = process;
      this.valve = valve;
    }
  }
}
