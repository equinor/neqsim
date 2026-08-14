package neqsim.process.controllerdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.TransientStepIdentifier;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Quantitative rollback, replay, coverage, multi-area, and restart evidence for the anti-surge controller state family.
 */
class AntiSurgeControllerTransientStateTransactionTest extends neqsim.NeqSimTest {
  private static final double TOLERANCE = 1.0e-12;

  @Test
  void rollbackRestoresInternalHistoryValveCommandAndDeterministicReplay() {
    Fixture fixture = new Fixture("rollback", 0.30);
    configure(fixture.controller);
    fixture.controller.runTransient(0.0, 1.0, TransientStepIdentifier.deterministicPhysicalStep("anti-surge-seed", 0L));

    ProcessSystem process = new ProcessSystem("anti-surge transaction");
    process.add(fixture.controller);
    assertEquals(1, process.getTransientTransactionCoverage().getProcessElementCount());
    assertEquals(1, process.getTransientTransactionCoverage().getParticipantCount());
    assertTrue(process.getTransientTransactionCoverage().isComplete());

    double initialOpening = fixture.controller.getValveOpening();
    double initialValveOpening = fixture.valve.getPercentValveOpening();
    double initialValveTarget = fixture.valve.getTargetPercentValveOpening();
    UUID stepId = TransientStepIdentifier.deterministicPhysicalStep("anti-surge-replay", 1L);
    fixture.compressor.setMargin(0.16);

    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    fixture.controller.runTransient(initialOpening, 1.0, stepId);
    double trialOpening = fixture.controller.getValveOpening();
    double trialTarget = fixture.controller.getTargetValveOpening();
    double trialRate = fixture.controller.getFilteredMarginRate();
    double trialPrediction = fixture.controller.getPredictedMargin();
    transaction.rollback();

    assertEquals(initialOpening, fixture.controller.getValveOpening(), 0.0);
    assertEquals(initialValveOpening, fixture.valve.getPercentValveOpening(), 0.0);
    assertEquals(initialValveTarget, fixture.valve.getTargetPercentValveOpening(), 0.0);
    assertFalse(fixture.controller.hasRunTransient(stepId));

    fixture.controller.runTransient(initialOpening, 1.0, stepId);
    assertEquals(trialOpening, fixture.controller.getValveOpening(), TOLERANCE);
    assertEquals(trialTarget, fixture.controller.getTargetValveOpening(), TOLERANCE);
    assertEquals(trialRate, fixture.controller.getFilteredMarginRate(), TOLERANCE);
    assertEquals(trialPrediction, fixture.controller.getPredictedMargin(), TOLERANCE);
    assertEquals(trialOpening, fixture.valve.getPercentValveOpening(), TOLERANCE);

    fixture.controller.runTransient(initialOpening, 1.0, stepId);
    assertEquals(trialOpening, fixture.controller.getValveOpening(), 0.0,
        "one physical-step identifier must not integrate the controller twice");
    assertEquals(trialOpening, fixture.valve.getPercentValveOpening(), 0.0);
  }

  @Test
  void rollbackRestoresConfigurationNameAndOriginalBindings() {
    Fixture fixture = new Fixture("configuration", 0.20);
    configure(fixture.controller);
    ProcessSystem process = new ProcessSystem("anti-surge configuration");
    process.add(fixture.controller);
    ProcessSystem expectedProcess = process.copy();
    AntiSurgeController expected = (AntiSurgeController) expectedProcess.getControllerDevices().get(0);

    String originalName = fixture.controller.getName();
    String stateIdentity = fixture.controller.getTransientStateIdentity();
    ThrottlingValve originalValve = fixture.valve;
    MarginCompressor originalCompressor = fixture.compressor;
    Fixture replacement = new Fixture("replacement", -0.20);

    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    fixture.controller.setName("trial name");
    fixture.controller.setCompressor(replacement.compressor);
    fixture.controller.setRecycleValve(replacement.valve);
    fixture.controller.setSurgeMarginSetPoint(0.90);
    fixture.controller.setProportionalGain(999.0);
    fixture.controller.setIntegralTime(0.0);
    fixture.controller.setOpeningRange(25.0, 35.0);
    fixture.controller.setPredictiveActionEnabled(false);
    fixture.controller.setPredictionHorizon(0.0);
    fixture.controller.setMarginRateFilterTime(0.0);
    fixture.controller.setActuatorDynamics(0.5, 50.0);
    fixture.controller.setEmergencyAction(1.0, 35.0);
    fixture.controller.runTransient(0.0, 2.0, UUID.randomUUID());
    transaction.rollback();

    assertEquals(originalName, fixture.controller.getName());
    assertEquals(stateIdentity, fixture.controller.getTransientStateIdentity());
    assertSame(originalCompressor, fixture.controller.getCompressor());
    assertSame(originalValve, fixture.controller.getRecycleValve());

    fixture.compressor.setMargin(0.08);
    ((MarginCompressor) expected.getCompressor()).setMargin(0.08);
    UUID replayId = TransientStepIdentifier.deterministicPhysicalStep("anti-surge-config", 0L);
    fixture.controller.runTransient(0.0, 0.5, replayId);
    expected.runTransient(0.0, 0.5, replayId);
    assertEquals(expected.getValveOpening(), fixture.controller.getValveOpening(), TOLERANCE);
    assertEquals(expected.getTargetValveOpening(), fixture.controller.getTargetValveOpening(), TOLERANCE);
    assertEquals(expected.getFilteredMarginRate(), fixture.controller.getFilteredMarginRate(), TOLERANCE);
    assertEquals(expected.getPredictedMargin(), fixture.controller.getPredictedMargin(), TOLERANCE);
  }

  @Test
  void multiAreaRollbackRestoresBothControllersAndValveCommands() {
    Fixture first = new Fixture("first", 0.20);
    Fixture second = new Fixture("second", 0.20);
    configure(first.controller);
    configure(second.controller);
    ProcessSystem firstArea = new ProcessSystem("first area");
    ProcessSystem secondArea = new ProcessSystem("second area");
    firstArea.add(first.controller);
    secondArea.add(second.controller);
    ProcessModel model = new ProcessModel();
    model.add("first", firstArea);
    model.add("second", secondArea);

    assertTrue(model.getTransientTransactionCoverage().isComplete());
    double firstOpening = first.valve.getPercentValveOpening();
    double secondOpening = second.valve.getPercentValveOpening();
    TransientStepTransaction transaction = model.beginTransientStepTransaction();
    first.compressor.setMargin(0.02);
    second.compressor.setMargin(-0.01);
    first.controller.runTransient(0.0, 1.0, UUID.randomUUID());
    second.controller.runTransient(0.0, 1.0, UUID.randomUUID());
    assertTrue(first.valve.getPercentValveOpening() > firstOpening);
    assertTrue(second.valve.getPercentValveOpening() > secondOpening);
    transaction.rollback();

    assertEquals(firstOpening, first.valve.getPercentValveOpening(), 0.0);
    assertEquals(secondOpening, second.valve.getPercentValveOpening(), 0.0);
    assertEquals(0.0, first.controller.getValveOpening(), 0.0);
    assertEquals(0.0, second.controller.getValveOpening(), 0.0);
  }

  @Test
  void restartContinuesExactlyAndSubclassesRemainFailClosed() {
    Fixture fixture = new Fixture("restart", 0.30);
    configure(fixture.controller);
    ProcessSystem process = new ProcessSystem("anti-surge restart");
    process.add(fixture.controller);
    fixture.controller.runTransient(0.0, 1.0,
        TransientStepIdentifier.deterministicPhysicalStep("anti-surge-restart", 0L));
    fixture.compressor.setMargin(0.18);
    fixture.controller.runTransient(fixture.controller.getValveOpening(), 1.0,
        TransientStepIdentifier.deterministicPhysicalStep("anti-surge-restart", 1L));

    ProcessSystem restartedProcess = process.copy();
    AntiSurgeController restarted = (AntiSurgeController) restartedProcess.getControllerDevices().get(0);
    assertTrue(restartedProcess.getTransientTransactionCoverage().isComplete());
    ((MarginCompressor) restarted.getCompressor()).setMargin(0.10);
    fixture.compressor.setMargin(0.10);
    UUID nextId = TransientStepIdentifier.deterministicPhysicalStep("anti-surge-restart", 2L);
    fixture.controller.runTransient(fixture.controller.getValveOpening(), 0.25, nextId);
    restarted.runTransient(restarted.getValveOpening(), 0.25, nextId);

    assertEquals(fixture.controller.getValveOpening(), restarted.getValveOpening(), TOLERANCE);
    assertEquals(fixture.controller.getTargetValveOpening(), restarted.getTargetValveOpening(), TOLERANCE);
    assertEquals(fixture.controller.getFilteredMarginRate(), restarted.getFilteredMarginRate(), TOLERANCE);
    assertEquals(fixture.controller.getPredictedMargin(), restarted.getPredictedMargin(), TOLERANCE);
    assertEquals(fixture.valve.getPercentValveOpening(), restarted.getRecycleValve().getPercentValveOpening(),
        TOLERANCE);

    ProcessSystem unsupported = new ProcessSystem("unsupported anti-surge subclass");
    unsupported.add(new DerivedAntiSurgeController());
    assertFalse(unsupported.getTransientTransactionCoverage().isComplete());
    assertTrue(unsupported.getTransientTransactionCoverage().getBlockingIssues().get(0)
        .contains(DerivedAntiSurgeController.class.getName()));

    Fixture foreign = new Fixture("foreign", 0.10);
    assertThrows(IllegalArgumentException.class,
        () -> fixture.controller.restoreTransientState(foreign.controller.captureTransientState()));
  }

  private static void configure(AntiSurgeController controller) {
    controller.setSurgeMarginSetPoint(0.10);
    controller.setProportionalGain(300.0);
    controller.setIntegralTime(10.0);
    controller.setOpeningRange(0.0, 90.0);
    controller.setPredictiveActionEnabled(true);
    controller.setPredictionHorizon(4.0);
    controller.setMarginRateFilterTime(2.0);
    controller.setActuatorDynamics(25.0, 2.0);
    controller.setEmergencyAction(-0.02, 80.0);
  }

  private static Stream gasStream(String name) {
    SystemInterface gas = new SystemSrkEos(303.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule(2);
    Stream stream = new Stream(name, gas);
    stream.setFlowRate(100000.0, "kg/hr");
    stream.run();
    return stream;
  }

  /** Serializable fixture for controller, source, and final-element bindings. */
  private static final class Fixture {
    private final MarginCompressor compressor;
    private final ThrottlingValve valve;
    private final AntiSurgeController controller;

    private Fixture(String name, double margin) {
      Stream feed = gasStream(name + " feed");
      compressor = new MarginCompressor(name + " compressor", feed, margin);
      valve = new ThrottlingValve(name + " recycle", feed);
      valve.setPercentValveOpening(0.0);
      controller = new AntiSurgeController(name + " controller", compressor, valve);
    }
  }

  /** Compressor stub with a serializable, controllable surge-margin input. */
  private static final class MarginCompressor extends Compressor {
    private static final long serialVersionUID = 1000L;
    private double margin;

    private MarginCompressor(String name, Stream feed, double margin) {
      super(name, feed);
      this.margin = margin;
    }

    @Override
    public double getDistanceToSurge() {
      return margin;
    }

    private void setMargin(double margin) {
      this.margin = margin;
    }
  }

  /** Unqualified descendant used to prove fail-closed subclass coverage. */
  private static final class DerivedAntiSurgeController extends AntiSurgeController {
    private static final long serialVersionUID = 1000L;
  }
}
