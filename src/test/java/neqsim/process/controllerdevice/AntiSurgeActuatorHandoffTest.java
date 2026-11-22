package neqsim.process.controllerdevice;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveTravelModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression evidence for command/actual actuator separation and native safety precedence.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class AntiSurgeActuatorHandoffTest {
  /** Checks the configured finite stroke in the actual ProcessSystem physical-step path. */
  @Test
  void processStepCannotTeleportSlowAsv() {
    Fixture fixture = new Fixture(false, -0.1);
    fixture.process.runTransient(0.1, UUID.randomUUID());
    assertEquals(100.0, fixture.valve.getTargetPercentValveOpening(), 1.0e-12);
    assertEquals(2.0, fixture.valve.getPercentValveOpening(), 1.0e-12);
  }

  /** Checks that an unavailable actuator still has an actual command but cannot move. */
  @Test
  void unavailableActuatorRetainsActualPositionDespiteRegulatingDemand() {
    Fixture fixture = new Fixture(true, -0.1);
    ((ESDValve) fixture.valve).setActuatorAvailable(false);
    fixture.process.runTransient(0.1, UUID.randomUUID());
    assertEquals(100.0, fixture.controller.getResponse(), 1.0e-12);
    assertEquals(100.0, fixture.valve.getTargetPercentValveOpening(), 1.0e-12);
    assertEquals(0.0, fixture.valve.getPercentValveOpening(), 1.0e-12);
  }

  /** Checks native fail-open motion wins over an opposing controller, including repeated UUID dispatch. */
  @Test
  void failOpenTripHasPriorityOverControllerAndAdvancesOnlyOnce() {
    Fixture fixture = new Fixture(true, 0.5);
    ((ESDValve) fixture.valve).trip();
    UUID id = UUID.randomUUID();
    fixture.process.runTransient(0.1, id);
    assertEquals(100.0, fixture.valve.getTargetPercentValveOpening(), 1.0e-12);
    assertEquals(2.0, fixture.valve.getPercentValveOpening(), 1.0e-12);
    fixture.valve.runTransient(0.1, id);
    assertEquals(2.0, fixture.valve.getPercentValveOpening(), 1.0e-12);
  }

  /** Checks a zero-time solve cannot consume the positive safety interval with the same UUID. */
  @Test
  void zeroTimeSolveDoesNotConsumeSafetyPhysicalInterval() {
    Fixture fixture = new Fixture(true, 0.5);
    ESDValve valve = (ESDValve) fixture.valve;
    valve.trip();
    UUID id = UUID.randomUUID();
    valve.runTransient(0.0, id);
    assertEquals(0.0, valve.getPercentValveOpening(), 0.0);
    valve.runTransient(0.1, id);
    assertEquals(2.0, valve.getPercentValveOpening(), 1.0e-12);
    valve.runTransient(0.0, id);
    assertEquals(2.0, valve.getPercentValveOpening(), 1.0e-12);
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> valve.runTransient(-0.1, UUID.randomUUID()));
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> valve.runTransient(Double.NaN, UUID.randomUUID()));
  }

  /** Checks PST owns its complete interval, including completion, replay and a subsequent normal scan. */
  @Test
  void partialStrokeOwnershipSurvivesCompletionAndSnapshotReplay() {
    Fixture fixture = new Fixture(true, 0.5);
    ESDValve valve = (ESDValve) fixture.valve;
    valve.setPercentValveOpening(100.0);
    valve.startPartialStrokeTest(80.0);
    UUID id = UUID.randomUUID();
    fixture.process.runTransient(0.1, id);
    assertEquals(98.0, valve.getPercentValveOpening(), 1.0e-12);
    assertEquals(80.0, valve.getTargetPercentValveOpening(), 1.0e-12);
    ThrottlingValve.TransientState snapshot = valve.captureTransientState();
    valve.completePartialStrokeTest();
    double completed = valve.getPercentValveOpening();
    valve.runTransient(0.1, id);
    assertEquals(completed, valve.getPercentValveOpening(), 1.0e-12,
        "completed PST interval cannot also execute normal actuator travel");
    valve.restoreTransientState(snapshot);
    assertTrue(valve.isPartialStrokeTestActive());
    valve.completePartialStrokeTest();
    valve.runTransient(0.1, id);
    assertEquals(completed, valve.getPercentValveOpening(), 1.0e-12);
    valve.runTransient(0.1, UUID.randomUUID());
    assertEquals(98.0, valve.getPercentValveOpening(), 1.0e-12,
        "normal native travel resumes on the next physical interval");
  }

  /** Checks standalone controller execution requests travel and preserves physical position until a valve scan. */
  @Test
  void standaloneControllerDoesNotApplyPhysicalPosition() {
    Fixture fixture = new Fixture(false, -0.1);
    fixture.controller.runTransient(0.0, 0.1, UUID.randomUUID());
    assertEquals(100.0, fixture.valve.getTargetPercentValveOpening(), 1.0e-12);
    assertEquals(0.0, fixture.valve.getPercentValveOpening(), 1.0e-12);
    fixture.valve.runController(0.1, UUID.randomUUID());
    assertTrue(fixture.valve.getPercentValveOpening() <= 2.0 + 1.0e-12);
  }

  /** Isolated gas path with an explicit test-only map-margin oracle. */
  private static final class Fixture {
    private final ProcessSystem process = new ProcessSystem();
    private final ThrottlingValve valve;
    private final AntiSurgeController controller;

    /**
     * Creates a real native valve path without making a compressor gas-path benchmark claim.
     *
     * @param esd whether native safety actuation is used
     * @param margin explicit test-only margin to isolate actuator behavior
     */
    private Fixture(boolean esd, final double margin) {
      SystemSrkEos fluid = new SystemSrkEos(300.0, 10.0);
      fluid.addComponent("methane", 1.0);
      fluid.setMixingRule("classic");
      Stream feed = new Stream("FEED", fluid);
      feed.setFlowRate(1000.0, "kg/hr");
      feed.run();
      valve = esd ? new ESDValve("ASV", feed) : new ThrottlingValve("ASV", feed);
      valve.setOutletPressure(5.0);
      valve.setCv(10.0);
      valve.setTravelModel(ValveTravelModel.LINEAR_RATE_LIMIT);
      valve.setTravelTime(5.0);
      valve.setPhysicalStepIdempotencyEnabled(true);
      if (esd) {
        ((ESDValve) valve).setStrokeTime(5.0);
        ((ESDValve) valve).setFailSafePosition(100.0);
      }
      Compressor source = new MarginCompressor(feed, margin);
      controller = new AntiSurgeController("ASC", source, valve);
      controller.setEmergencyAction(0.0, 100.0);
      controller.setIntegralTime(0.0);
      valve.setController(controller);
      process.add(feed);
      process.add(valve);
      process.run();
      valve.setPercentValveOpening(0.0);
      valve.setCalculateSteadyState(false);
    }
  }

  /**
   * Serializable test-only map-margin oracle without an enclosing fixture reference.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class MarginCompressor extends Compressor {
    private static final long serialVersionUID = 1L;
    private final double margin;

    /**
     * Creates an isolated oracle for actuator handoff tests.
     *
     * @param feed actual gas stream
     * @param margin fixed test-only map margin
     */
    private MarginCompressor(Stream feed, double margin) {
      super("UNIT-TEST-MARGIN", feed);
      this.margin = margin;
    }

    /** {@inheritDoc} */
    @Override
    public double getDistanceToSurge() {
      return margin;
    }
  }

}
