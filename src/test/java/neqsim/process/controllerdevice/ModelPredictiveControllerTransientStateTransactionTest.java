package neqsim.process.controllerdevice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.TransientStepIdentifier;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Quantitative rollback, replay, multi-area, fail-closed and restart evidence for the concrete local MPC.
 */
class ModelPredictiveControllerTransientStateTransactionTest extends neqsim.NeqSimTest {
  private static final double TOLERANCE = 1.0e-12;

  @Test
  void singleInputRollbackRestoresConfigurationAndReplaysExactly() {
    MutableMeasurement measurement = new MutableMeasurement("temperature", "C", 35.0);
    ModelPredictiveController controller = configuredSingleInput("mpc", measurement);
    ProcessSystem process = new ProcessSystem("mpc transaction");
    process.add(controller);

    assertEquals(1, process.getTransientTransactionCoverage().getProcessElementCount());
    assertEquals(1, process.getTransientTransactionCoverage().getParticipantCount());
    assertTrue(process.getTransientTransactionCoverage().isComplete());

    String originalName = controller.getName();
    UUID stepId = TransientStepIdentifier.deterministicPhysicalStep("mpc-replay", 0L);
    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    controller.setName("trial name");
    controller.setControllerSetPoint(62.0);
    controller.setProcessModel(3.0, 4.0);
    controller.runTransient(15.0, 0.5, stepId);
    double trialResponse = controller.getResponse();
    double trialMeasurement = controller.getLastSampledValue();
    transaction.rollback();

    assertEquals(originalName, controller.getName());
    assertEquals(50.0, controller.getControllerSetPoint(), 0.0);
    assertEquals(2.0, controller.getProcessGain(), 0.0);
    assertEquals(8.0, controller.getTimeConstant(), 0.0);
    assertFalse(stepId.equals(controller.getCalcIdentifier()));

    controller.setControllerSetPoint(62.0);
    controller.setProcessModel(3.0, 4.0);
    controller.runTransient(15.0, 0.5, stepId);
    assertEquals(trialResponse, controller.getResponse(), TOLERANCE);
    assertEquals(trialMeasurement, controller.getLastSampledValue(), 0.0);

    controller.runTransient(-999.0, 0.5, stepId);
    assertEquals(trialResponse, controller.getResponse(), 0.0,
        "one physical-step identifier must not advance MPC state twice");
  }

  @Test
  void multivariableRollbackRestoresQualityFeedAndControlVectors() {
    MutableMeasurement quality = new MutableMeasurement("quality", "ppm", 9.0);
    ModelPredictiveController controller = configuredMultivariable("multivariable", quality);
    ProcessSystem process = new ProcessSystem("multivariable transaction");
    process.add(controller);

    double[] initialControls = controller.getControlVector();
    Map<String, Double> trialFeed = new LinkedHashMap<>();
    trialFeed.put("heavy", 0.30);
    UUID stepId = TransientStepIdentifier.deterministicPhysicalStep("mpc-mv-replay", 0L);
    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    quality.setValue(13.0);
    controller.updateQualityMeasurement("product", 13.0);
    controller.updateFeedConditions(trialFeed, 1.25);
    controller.runTransient(controller.getResponse(), 1.0, stepId);
    double[] trialControls = controller.getControlVector();
    double trialPrediction = controller.getPredictedQuality("product");
    transaction.rollback();

    assertArrayEquals(initialControls, controller.getControlVector(), 0.0);
    assertTrue(Double.isNaN(controller.getPredictedQuality("product")));
    controller.updateQualityMeasurement("product", 13.0);
    controller.updateFeedConditions(trialFeed, 1.25);
    controller.runTransient(controller.getResponse(), 1.0, stepId);
    assertArrayEquals(trialControls, controller.getControlVector(), TOLERANCE);
    assertEquals(trialPrediction, controller.getPredictedQuality("product"), TOLERANCE);
  }

  @Test
  void multivariableRollbackRestoresLinearMoveConstraints() {
    ModelPredictiveController controller = new ModelPredictiveController("constrained multivariable");
    controller.configureControls("choke A", "choke B");
    controller.setInitialControlValues(10.0, 10.0);
    controller.setControlLimits(0, 0.0, 100.0);
    controller.setControlLimits(1, 0.0, 100.0);
    controller.setControlWeights(1.0, 1.0);
    controller.setMoveWeights(0.0, 0.0);
    controller.setPreferredControlVector(30.0, 30.0);
    controller.addLinearMoveConstraint(new ModelPredictiveController.LinearMoveConstraint("shared opening budget",
        new double[] { 1.0, 1.0 }, Double.NEGATIVE_INFINITY, 2.0));
    ProcessSystem process = new ProcessSystem("linear constraint transaction");
    process.add(controller);

    UUID stepId = TransientStepIdentifier.deterministicPhysicalStep("linear-constraint-replay", 0L);
    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    controller.clearLinearMoveConstraints();
    controller.addLinearMoveConstraint(new ModelPredictiveController.LinearMoveConstraint("trial opening budget",
        new double[] { 1.0, 1.0 }, Double.NEGATIVE_INFINITY, 100.0));
    controller.runTransient(Double.NaN, 1.0, stepId);
    double unconstrainedOpening = controller.getControlValue(0) + controller.getControlValue(1) - 20.0;
    assertTrue(unconstrainedOpening > 2.0 + 1.0e-6);
    transaction.rollback();

    assertArrayEquals(new double[] { 10.0, 10.0 }, controller.getControlVector(), 0.0);
    controller.runTransient(Double.NaN, 1.0, stepId);
    double constrainedOpening = controller.getControlValue(0) + controller.getControlValue(1) - 20.0;
    assertEquals(2.0, constrainedOpening, 1.0e-6);
  }

  @Test
  void movingHorizonStateAndSerializableSnapshotContinueExactlyAfterRestart() throws Exception {
    MutableMeasurement measurement = new MutableMeasurement("temperature", "C", 25.0);
    ModelPredictiveController controller = configuredSingleInput("restart", measurement);
    controller.enableMovingHorizonEstimation(8);
    for (int i = 0; i < 8; i++) {
      measurement.setValue(20.0 + 5.0 * Math.pow(0.75, i));
      controller.runTransient(5.0, 1.0, TransientStepIdentifier.deterministicPhysicalStep("mpc-estimation", i));
    }
    assertNotNull(controller.getLastMovingHorizonEstimate());

    ModelPredictiveController.MpcTransientState snapshot = roundTrip(controller.captureTransientState());
    controller.restoreTransientState(snapshot);

    ProcessSystem process = new ProcessSystem("mpc restart");
    process.add(controller);
    ProcessSystem restartedProcess = process.copy();
    ModelPredictiveController restarted = (ModelPredictiveController) restartedProcess.getControllerDevices().get(0);
    assertTrue(restartedProcess.getTransientTransactionCoverage().isComplete());
    assertEquals(controller.getTransientStateIdentity(), restarted.getTransientStateIdentity());

    UUID nextId = TransientStepIdentifier.deterministicPhysicalStep("mpc-estimation", 8L);
    controller.runTransient(controller.getResponse(), 0.5, nextId);
    restarted.runTransient(restarted.getResponse(), 0.5, nextId);

    assertEquals(controller.getResponse(), restarted.getResponse(), TOLERANCE);
    assertEquals(controller.getProcessGain(), restarted.getProcessGain(), TOLERANCE);
    assertEquals(controller.getTimeConstant(), restarted.getTimeConstant(), TOLERANCE);
    assertEquals(controller.getProcessBias(), restarted.getProcessBias(), TOLERANCE);
    assertEquals(controller.getLastMovingHorizonEstimate().getMeanSquaredError(),
        restarted.getLastMovingHorizonEstimate().getMeanSquaredError(), TOLERANCE);
  }

  @Test
  void coordinatedMultiAreaRollbackRestoresBothControllers() {
    MutableMeasurement firstMeasurement = new MutableMeasurement("first measurement", "C", 20.0);
    MutableMeasurement secondMeasurement = new MutableMeasurement("second measurement", "C", 30.0);
    ModelPredictiveController first = configuredSingleInput("first controller", firstMeasurement);
    ModelPredictiveController second = configuredSingleInput("second controller", secondMeasurement);
    ProcessSystem firstArea = new ProcessSystem("first area");
    ProcessSystem secondArea = new ProcessSystem("second area");
    firstArea.add(first);
    secondArea.add(second);
    ProcessModel model = new ProcessModel();
    model.add("first", firstArea);
    model.add("second", secondArea);

    assertEquals(2, model.getTransientTransactionCoverage().getProcessElementCount());
    assertEquals(2, model.getTransientTransactionCoverage().getParticipantCount());
    assertTrue(model.getTransientTransactionCoverage().isComplete());
    double firstResponse = first.getResponse();
    double secondResponse = second.getResponse();
    TransientStepTransaction transaction = model.beginTransientStepTransaction();
    firstMeasurement.setValue(80.0);
    secondMeasurement.setValue(5.0);
    first.runTransient(firstResponse, 1.0, UUID.randomUUID());
    second.runTransient(secondResponse, 1.0, UUID.randomUUID());
    transaction.rollback();

    assertEquals(firstResponse, first.getResponse(), 0.0);
    assertEquals(secondResponse, second.getResponse(), 0.0);
    assertEquals(80.0, first.getMeasuredValue(), 0.0);
    assertEquals(5.0, second.getMeasuredValue(), 0.0);
  }

  @Test
  void descendantsForeignSnapshotsAndOnlineMeasurementsFailClosed() {
    ProcessSystem descendantProcess = new ProcessSystem("unsupported MPC descendant");
    descendantProcess.add(new DerivedModelPredictiveController());
    assertFalse(descendantProcess.getTransientTransactionCoverage().isComplete());
    assertTrue(descendantProcess.getTransientTransactionCoverage().getBlockingIssues().get(0)
        .contains(DerivedModelPredictiveController.class.getName()));

    ModelPredictiveController first = configuredSingleInput("first", new MutableMeasurement("first", "C", 1.0));
    ModelPredictiveController second = configuredSingleInput("second", new MutableMeasurement("second", "C", 1.0));
    assertThrows(IllegalArgumentException.class, () -> first.restoreTransientState(second.captureTransientState()));
    assertThrows(IllegalArgumentException.class, () -> first.restoreTransientState(null));

    MutableMeasurement online = new MutableMeasurement("online", "C", 1.0);
    online.setIsOnlineSignal(true, "plant", "TT-1");
    ProcessSystem onlineProcess = new ProcessSystem("online MPC");
    onlineProcess.add(configuredSingleInput("online controller", online));
    assertFalse(onlineProcess.getTransientTransactionCoverage().isComplete());
    assertTrue(onlineProcess.getTransientTransactionCoverage().getBlockingIssues().get(0).contains("external I/O"));
  }

  private static ModelPredictiveController configuredSingleInput(String name, MutableMeasurement measurement) {
    ModelPredictiveController controller = new ModelPredictiveController(name);
    controller.setTransmitter(measurement);
    controller.setUnit(measurement.getUnit());
    controller.setControllerSetPoint(50.0);
    controller.setProcessModel(2.0, 8.0);
    controller.setProcessBias(10.0);
    controller.setWeights(2.0, 0.2, 0.8);
    controller.setPreferredControlValue(5.0);
    controller.setOutputLimits(-100.0, 100.0);
    controller.setMoveLimits(-20.0, 20.0);
    return controller;
  }

  private static ModelPredictiveController configuredMultivariable(String name, MutableMeasurement quality) {
    ModelPredictiveController controller = new ModelPredictiveController(name);
    controller.configureControls("heater", "cooler");
    controller.setInitialControlValues(4.0, 6.0);
    controller.setControlLimits(0, 0.0, 20.0);
    controller.setControlLimits(1, 0.0, 20.0);
    controller.setControlMoveLimits(0, -2.0, 2.0);
    controller.setControlMoveLimits(1, -2.0, 2.0);
    controller.setControlWeights(1.0, 1.5);
    controller.setMoveWeights(2.0, 2.5);
    controller.setPreferredControlVector(5.0, 5.0);
    controller.addQualityConstraint(ModelPredictiveController.QualityConstraint.builder("product").measurement(quality)
        .unit("ppm").limit(10.0).margin(0.5).controlSensitivity(-0.8, 0.4).compositionSensitivity("heavy", 3.0)
        .rateSensitivity(0.5).build());
    Map<String, Double> initialFeed = new LinkedHashMap<>();
    initialFeed.put("heavy", 0.10);
    controller.updateFeedConditions(initialFeed, 1.0);
    return controller;
  }

  @SuppressWarnings("unchecked")
  private static <T extends Serializable> T roundTrip(T value) throws Exception {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
      output.writeObject(value);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
      return (T) input.readObject();
    }
  }

  /** Serializable controllable local measurement used by the transaction regression. */
  private static final class MutableMeasurement extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1000L;
    private double value;

    private MutableMeasurement(String name, String unit, double value) {
      super(name, unit);
      this.value = value;
    }

    private void setValue(double value) {
      this.value = value;
    }

    @Override
    public double getMeasuredValue(String unit) {
      if (unit == null || unit.isEmpty() || unit.equals(getUnit())) {
        return value;
      }
      throw new IllegalArgumentException("Unsupported unit " + unit);
    }
  }

  /** Deliberately unqualified descendant used to prove fail-closed coverage. */
  private static final class DerivedModelPredictiveController extends ModelPredictiveController {
    private static final long serialVersionUID = 1000L;
  }
}
