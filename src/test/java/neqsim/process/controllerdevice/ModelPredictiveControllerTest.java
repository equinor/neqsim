package neqsim.process.controllerdevice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;

/**
 * Integration style test for the {@link ModelPredictiveController}. The test mimics optimisation of
 * heater duty (energy usage) while targeting a desired outlet temperature.
 */
public class ModelPredictiveControllerTest extends neqsim.NeqSimTest {

  /**
   * Simple first-order heater model used as transmitter for the controller.
   */
  static class SimpleHeaterMeasurement extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1L;
    private final double ambientTemperature;
    private final double timeConstant;
    private final double processGain;
    private double temperature;

    SimpleHeaterMeasurement(String name, double ambient, double timeConstant, double gain) {
      super(name, "C");
      this.ambientTemperature = ambient;
      this.timeConstant = timeConstant;
      this.processGain = gain;
      this.temperature = ambient;
    }

    void advance(double controlSignal, double dt) {
      double drivingForce = -(temperature - ambientTemperature) + processGain * controlSignal;
      temperature += drivingForce * dt / timeConstant;
    }

    @Override
    public double getMeasuredValue() {
      return temperature;
    }

    @Override
    public double getMeasuredValue(String unit) {
      if (unit == null || unit.isEmpty() || unit.equals(getUnit())) {
        return temperature;
      }
      throw new IllegalArgumentException("Unit conversion not supported in test transmitter");
    }
  }

  /**
   * Simplified process representation coupling two manipulated variables and feed quality to generic
   * product quality indicators. The model is linear to keep the MPC validation generic.
   */
  static class SyntheticQualityProcess {
    private static final double BASE_QUALITY_A = -9.5;
    private static final double BASE_QUALITY_B = 6.5;
    private static final double QUALITY_A_CONTROL1_COEFF = 0.12;
    private static final double QUALITY_A_CONTROL2_COEFF = -0.45;
    private static final double QUALITY_B_CONTROL1_COEFF = -0.06;
    private static final double QUALITY_B_CONTROL2_COEFF = 0.15;
    private static final double QUALITY_A_RATE_COEFF = 1.2;
    private static final double QUALITY_B_RATE_COEFF = 0.9;
    private static final double QUALITY_A_MID_COMPONENT_COEFF = 4.0;
    private static final double QUALITY_A_HEAVY_COMPONENT_COEFF = 12.0;
    private static final double QUALITY_B_MID_COMPONENT_COEFF = 8.0;
    private static final double QUALITY_B_HEAVY_COMPONENT_COEFF = 15.0;

    private final double baseControl1;
    private final double baseControl2;
    private final Map<String, Double> baseComposition;
    private final double baseRate;

    private double control1;
    private double control2;
    private Map<String, Double> composition;
    private double rate;
    private double qualityA;
    private double qualityB;

    SyntheticQualityProcess(double baseControl1, double baseControl2,
        Map<String, Double> baseComposition, double baseRate) {
      this.baseControl1 = baseControl1;
      this.baseControl2 = baseControl2;
      this.baseComposition = new LinkedHashMap<>(baseComposition);
      this.baseRate = baseRate;
      this.control1 = baseControl1;
      this.control2 = baseControl2;
      this.composition = new LinkedHashMap<>(baseComposition);
      this.rate = baseRate;
      recompute();
    }

    void applyControl(double control1, double control2) {
      this.control1 = control1;
      this.control2 = control2;
      recompute();
    }

    void updateFeed(Map<String, Double> composition, double rate) {
      this.composition = new LinkedHashMap<>(composition);
      this.rate = rate;
      recompute();
    }

    double getQualityA() {
      return qualityA;
    }

    double getQualityB() {
      return qualityB;
    }

    private void recompute() {
      double mid = composition.getOrDefault("mid", 0.0);
      double heavy = composition.getOrDefault("heavy", 0.0);
      double baseMid = baseComposition.getOrDefault("mid", 0.0);
      double baseHeavy = baseComposition.getOrDefault("heavy", 0.0);
      double midDelta = mid - baseMid;
      double heavyDelta = heavy - baseHeavy;
      double rateDelta = rate - baseRate;

      qualityA = BASE_QUALITY_A + QUALITY_A_CONTROL1_COEFF * (control1 - baseControl1)
          + QUALITY_A_CONTROL2_COEFF * (control2 - baseControl2)
          + QUALITY_A_MID_COMPONENT_COEFF * midDelta
          + QUALITY_A_HEAVY_COMPONENT_COEFF * heavyDelta + QUALITY_A_RATE_COEFF * rateDelta;

      qualityB = BASE_QUALITY_B + QUALITY_B_CONTROL1_COEFF * (control1 - baseControl1)
          + QUALITY_B_CONTROL2_COEFF * (control2 - baseControl2)
          + QUALITY_B_MID_COMPONENT_COEFF * midDelta
          + QUALITY_B_HEAVY_COMPONENT_COEFF * heavyDelta + QUALITY_B_RATE_COEFF * rateDelta;
    }
  }

  static class QualityMeasurement extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1L;
    private final DoubleSupplier supplier;

    QualityMeasurement(String name, String unit, DoubleSupplier supplier) {
      super(name, unit);
      this.supplier = supplier;
    }

    @Override
    public double getMeasuredValue() {
      return supplier.getAsDouble();
    }

    @Override
    public double getMeasuredValue(String unit) {
      if (unit == null || unit.isEmpty() || unit.equals(getUnit())) {
        return supplier.getAsDouble();
      }
      throw new IllegalArgumentException("Unit conversion not supported in quality measurement");
    }
  }

  @Test
  public void testModelPredictiveControllerReducesControlEffort() {
    double ambient = 25.0; // degC
    double processGain = 0.4; // degC change per unit energy
    double timeConstant = 30.0; // seconds
    double setPoint = 60.0; // degC
    double dt = 1.0;

    SimpleHeaterMeasurement measurement =
        new SimpleHeaterMeasurement("heaterTemp", ambient, timeConstant, processGain);

    ModelPredictiveController controller = new ModelPredictiveController("heaterMPC");
    controller.setTransmitter(measurement);
    controller.setControllerSetPoint(setPoint, "C");
    controller.setProcessModel(processGain, timeConstant);
    controller.setProcessBias(ambient);
    controller.setPredictionHorizon(25);
    controller.setWeights(1.0, 0.01, 0.25);
    controller.setPreferredControlValue(0.0);
    controller.setOutputLimits(0.0, 120.0);
    controller.setMoveLimits(-8.0, 10.0);

    double baselineControl = (setPoint - ambient) / processGain;
    double energyAccumulator = 0.0;
    int energySamples = 0;

    double previousControlValue = controller.getResponse();
    for (int step = 0; step < 240; step++) {
      controller.runTransient(previousControlValue, dt);
      double control = controller.getResponse();
      double delta = control - previousControlValue;
      Assertions.assertTrue(delta >= -8.0 - 1.0e-9 && delta <= 10.0 + 1.0e-9,
          "Control move should respect configured rate limits");
      measurement.advance(control, dt);
      previousControlValue = control;

      if (step >= 120) {
        energyAccumulator += control;
        energySamples++;
      }
    }

    double finalTemperature = measurement.getMeasuredValue();
    double finalControl = controller.getResponse();
    double averageLateControl = energyAccumulator / energySamples;

    Assertions.assertTrue(finalTemperature > setPoint - 8.0 && finalTemperature < setPoint + 2.0,
        "Controller should approach the temperature target");
    Assertions.assertTrue(finalControl < baselineControl,
        "Energy optimised control should stay below the theoretical steady-state requirement");
    Assertions.assertTrue(averageLateControl < baselineControl,
        "Average control effort should be reduced compared to the steady-state requirement");
    Assertions.assertTrue(finalControl >= controller.getMinResponse());
    Assertions.assertTrue(finalControl <= controller.getMaxResponse());
  }

  @Test
  public void testAutoTuneConfiguresControllerFromHistory() {
    double ambient = 25.0;
    double processGain = 0.45;
    double timeConstant = 28.0;
    double dt = 1.0;

    SimpleHeaterMeasurement measurement =
        new SimpleHeaterMeasurement("autoTuneHeater", ambient, timeConstant, processGain);

    ModelPredictiveController controller = new ModelPredictiveController("autoTuneController");
    controller.setTransmitter(measurement);
    controller.setControllerSetPoint(ambient + 20.0, "C");
    controller.setProcessModel(0.1, 5.0);
    controller.setProcessBias(ambient - 5.0);
    controller.setPredictionHorizon(8);
    controller.setWeights(0.5, 0.0, 0.0);
    controller.setOutputLimits(0.0, 150.0);
    controller.enableMovingHorizonEstimation(70);

    double previousControl = controller.getResponse();
    for (int step = 0; step < 200; step++) {
      controller.runTransient(previousControl, dt);
      double control = controller.getResponse();
      measurement.advance(control, dt);
      previousControl = control;
      if (step == 120) {
        controller.setControllerSetPoint(ambient + 30.0, "C");
      }
    }

    ModelPredictiveController.AutoTuneResult result = controller.autoTune();

    Assertions.assertTrue(result.isApplied(), "Auto-tune should apply tuning by default");
    Assertions.assertEquals(controller.getPredictionHorizon(), result.getPredictionHorizon());
    Assertions.assertEquals(controller.getOutputWeight(), result.getOutputWeight(), 1.0e-12);
    Assertions.assertEquals(controller.getControlWeight(), result.getControlWeight(), 1.0e-12);
    Assertions.assertEquals(controller.getMoveWeight(), result.getMoveWeight(), 1.0e-12);

    Assertions.assertEquals(processGain, result.getProcessGain(), 0.1);
    Assertions.assertEquals(timeConstant, result.getTimeConstant(), 5.0);
    Assertions.assertEquals(ambient, result.getProcessBias(), 2.0);

    Assertions.assertEquals(processGain, controller.getProcessGain(), 0.1);
    Assertions.assertEquals(timeConstant, controller.getTimeConstant(), 5.0);
    Assertions.assertEquals(result.getProcessBias(), controller.getProcessBias(), 1.0e-6);
  }

  @Test
  public void testAutoTuneWithExternalSamplesRespectsApplyFlag() {
    double bias = 30.0;
    double gain = 0.35;
    double timeConstant = 18.0;
    double dt = 1.0;

    List<Double> measurements = new ArrayList<>();
    List<Double> controls = new ArrayList<>();
    List<Double> sampleTimes = new ArrayList<>();

    double temperature = bias;
    measurements.add(temperature);
    for (int step = 0; step < 60; step++) {
      double control = step < 20 ? 0.0 : 12.0;
      controls.add(control);
      sampleTimes.add(dt);
      double drivingForce = -(temperature - bias) + gain * control;
      temperature += drivingForce * dt / timeConstant;
      measurements.add(temperature);
    }

    ModelPredictiveController controller = new ModelPredictiveController("offlineAutoTune");
    controller.setProcessModel(0.1, 5.0);
    controller.setProcessBias(0.0);
    controller.setPredictionHorizon(6);
    controller.setWeights(1.0, 1.0, 1.0);

    ModelPredictiveController.AutoTuneConfiguration configuration =
        ModelPredictiveController.AutoTuneConfiguration.builder().applyImmediately(false).build();

    ModelPredictiveController.AutoTuneResult result = controller.autoTune(measurements, controls,
        sampleTimes, configuration);

    Assertions.assertFalse(result.isApplied(), "Auto-tune should not update controller state");
    Assertions.assertEquals(0.1, controller.getProcessGain(), 1.0e-12);
    Assertions.assertEquals(5.0, controller.getTimeConstant(), 1.0e-12);
    Assertions.assertEquals(0.0, controller.getProcessBias(), 1.0e-12);

    Assertions.assertEquals(gain, result.getProcessGain(), 0.05);
    Assertions.assertEquals(timeConstant, result.getTimeConstant(), 3.0);
    Assertions.assertEquals(bias, result.getProcessBias(), 1.0);
  }

  @Test
  public void testModelPredictiveControllerHandlesQualityConstraints() {
    Map<String, Double> baseComposition = new LinkedHashMap<>();
    baseComposition.put("light", 0.75);
    baseComposition.put("mid", 0.18);
    baseComposition.put("heavy", 0.07);
    double baseRate = 1.2;

    SyntheticQualityProcess process =
        new SyntheticQualityProcess(50.0, 40.0, baseComposition, baseRate);
    QualityMeasurement qualityAMeasurement =
        new QualityMeasurement("qualityA", "qa", process::getQualityA);
    QualityMeasurement qualityBMeasurement =
        new QualityMeasurement("qualityB", "qb", process::getQualityB);

    ModelPredictiveController controller = new ModelPredictiveController("qualityMpc");
    controller.configureControls("mv1", "mv2");
    controller.setPrimaryControlIndex(1);
    controller.setInitialControlValues(50.0, 40.0);
    controller.setControlLimits("mv1", 32.0, 70.0);
    controller.setControlLimits("mv2", 28.0, 65.0);
    controller.setControlWeights(0.4, 0.6);
    controller.setMoveWeights(0.2, 0.2);
    controller.setPreferredControlVector(35.0, 30.0);
    controller.setControlMoveLimits("mv1", -4.0, 5.0);
    controller.setControlMoveLimits("mv2", -3.0, 4.5);
    controller.addQualityConstraint(ModelPredictiveController.QualityConstraint.builder("qualityA")
        .measurement(qualityAMeasurement)
        .unit("qa")
        .limit(-6.0)
        .margin(0.2)
        .controlSensitivity(0.12, -0.45)
        .compositionSensitivity("mid", 4.0)
        .compositionSensitivity("heavy", 12.0)
        .rateSensitivity(1.2)
        .build());
    controller.addQualityConstraint(ModelPredictiveController.QualityConstraint.builder("qualityB")
        .measurement(qualityBMeasurement)
        .unit("qb")
        .limit(6.8)
        .margin(0.2)
        .controlSensitivity(-0.06, 0.15)
        .compositionSensitivity("mid", 8.0)
        .compositionSensitivity("heavy", 15.0)
        .rateSensitivity(0.9)
        .build());

    controller.updateFeedConditions(baseComposition, baseRate);
    process.updateFeed(baseComposition, baseRate);

    double dt = 1.0;
    double previousMv1 = controller.getControlValue("mv1");
    double previousMv2 = controller.getControlValue("mv2");
    for (int step = 0; step < 10; step++) {
      controller.runTransient(Double.NaN, dt);
      double mv1 = controller.getControlValue("mv1");
      double mv2 = controller.getControlValue("mv2");
      Assertions.assertTrue(mv1 - previousMv1 >= -4.0 - 1.0e-9
          && mv1 - previousMv1 <= 5.0 + 1.0e-9);
      Assertions.assertTrue(mv2 - previousMv2 >= -3.0 - 1.0e-9
          && mv2 - previousMv2 <= 4.5 + 1.0e-9);
      previousMv1 = mv1;
      previousMv2 = mv2;
      process.applyControl(mv1, mv2);
      process.updateFeed(baseComposition, baseRate);
    }

    double baselineControl1 = controller.getControlValue("mv1");
    double baselineControl2 = controller.getControlValue("mv2");
    Assertions.assertTrue(baselineControl1 < 50.0,
        "Optimisation should reduce control 1 from the initial point");
    Assertions.assertTrue(baselineControl2 < 40.0,
        "Optimisation should reduce control 2 from the initial point");
    Assertions.assertTrue(process.getQualityA() <= -6.0 + 1.0e-6,
        "Quality A should stay within specification at baseline");
    Assertions.assertTrue(process.getQualityB() <= 6.8 + 1.0e-6,
        "Quality B should stay within specification at baseline");

    Map<String, Double> heavyFeed = new LinkedHashMap<>(baseComposition);
    heavyFeed.put("light", heavyFeed.get("light") - 0.06);
    heavyFeed.put("mid", heavyFeed.get("mid") + 0.04);
    heavyFeed.put("heavy", heavyFeed.get("heavy") + 0.02);
    double heavyRate = 1.7;

    controller.updateFeedConditions(heavyFeed, heavyRate);
    controller.runTransient(Double.NaN, dt);
    double predictedControl1 = controller.getControlValue("mv1");
    double predictedControl2 = controller.getControlValue("mv2");
    Assertions.assertTrue(predictedControl1 - previousMv1 >= -4.0 - 1.0e-9
        && predictedControl1 - previousMv1 <= 5.0 + 1.0e-9);
    Assertions.assertTrue(predictedControl2 - previousMv2 >= -3.0 - 1.0e-9
        && predictedControl2 - previousMv2 <= 4.5 + 1.0e-9);
    double predictedQualityA = controller.getPredictedQuality("qualityA");
    double predictedQualityB = controller.getPredictedQuality("qualityB");
    Assertions.assertTrue(predictedControl1 > baselineControl1 + 1.0e-6,
        "Controller should anticipate the heavier feed by increasing control 1");
    Assertions.assertTrue(predictedControl2 > baselineControl2 + 1.0e-6,
        "Controller should anticipate the heavier feed by increasing control 2");
    Assertions.assertTrue(predictedQualityA <= -6.0 + 0.3);
    Assertions.assertTrue(predictedQualityB <= 6.8 + 0.3);

    process.applyControl(predictedControl1, predictedControl2);
    process.updateFeed(heavyFeed, heavyRate);

    for (int step = 0; step < 6; step++) {
      controller.updateFeedConditions(heavyFeed, heavyRate);
      controller.runTransient(Double.NaN, dt);
      double mv1 = controller.getControlValue("mv1");
      double mv2 = controller.getControlValue("mv2");
      Assertions.assertTrue(mv1 - previousMv1 >= -4.0 - 1.0e-9
          && mv1 - previousMv1 <= 5.0 + 1.0e-9);
      Assertions.assertTrue(mv2 - previousMv2 >= -3.0 - 1.0e-9
          && mv2 - previousMv2 <= 4.5 + 1.0e-9);
      previousMv1 = mv1;
      previousMv2 = mv2;
      process.applyControl(mv1, mv2);
      process.updateFeed(heavyFeed, heavyRate);
    }

    double disturbedControl1 = controller.getControlValue("mv1");
    double disturbedControl2 = controller.getControlValue("mv2");
    Assertions.assertTrue(disturbedControl1 >= predictedControl1 - 1.0e-9);
    Assertions.assertTrue(disturbedControl2 >= predictedControl2 - 1.0e-9);
    Assertions.assertTrue(disturbedControl1 > baselineControl1,
        "Control 1 should increase relative to the base case after the disturbance");
    Assertions.assertTrue(disturbedControl2 > baselineControl2,
        "Control 2 should increase relative to the base case after the disturbance");
    Assertions.assertTrue(process.getQualityA() <= -6.0 + 1.0e-6,
        "Quality A constraint should remain satisfied after the disturbance");
    Assertions.assertTrue(process.getQualityB() <= 6.8 + 1.0e-6,
        "Quality B constraint should remain satisfied after the disturbance");
  }

  @Test
  public void testMovingHorizonEstimationTunesProcessModel() {
    double ambient = 18.0;
    double trueGain = 0.55;
    double trueTimeConstant = 40.0;
    double dt = 1.0;

    SimpleHeaterMeasurement measurement =
        new SimpleHeaterMeasurement("heaterAdaptive", ambient, trueTimeConstant, trueGain);

    ModelPredictiveController controller = new ModelPredictiveController("adaptiveMpc");
    controller.setTransmitter(measurement);
    controller.enableMovingHorizonEstimation(45);
    Assertions.assertTrue(controller.isMovingHorizonEstimationEnabled());
    Assertions.assertEquals(45, controller.getMovingHorizonEstimationWindow());

    controller.setControllerSetPoint(ambient + 15.0, "C");
    controller.setProcessModel(0.2, 15.0);
    controller.setProcessBias(ambient + 3.0);
    controller.setPredictionHorizon(18);
    controller.setWeights(1.0, 0.05, 0.15);
    controller.setPreferredControlValue(0.0);
    controller.setOutputLimits(0.0, 120.0);

    double[] setpoints = {ambient + 15.0, ambient + 32.0, ambient + 20.0, ambient + 26.0};
    int[] durations = {80, 110, 90, 90};
    int stage = 0;
    int elapsedInStage = 0;

    for (int step = 0; step < 360; step++) {
      controller.runTransient(controller.getResponse(), dt);
      double control = controller.getResponse();
      measurement.advance(control, dt);

      elapsedInStage++;
      if (stage < setpoints.length - 1 && elapsedInStage >= durations[stage]) {
        stage++;
        controller.setControllerSetPoint(setpoints[stage], "C");
        elapsedInStage = 0;
      }
    }

    ModelPredictiveController.MovingHorizonEstimate estimate =
        controller.getLastMovingHorizonEstimate();
    Assertions.assertNotNull(estimate, "Estimator should return a result after sufficient samples");
    Assertions.assertTrue(estimate.getSampleCount() >= 40, "Expected estimation window to be used");

    Assertions.assertEquals(trueGain, estimate.getProcessGain(), 0.12,
        "Estimated process gain should be close to the true value");
    Assertions.assertEquals(trueTimeConstant, estimate.getTimeConstant(), 8.0,
        "Estimated time constant should approach the real process");
    Assertions.assertEquals(ambient, estimate.getProcessBias(), 4.0,
        "Estimated bias should reflect the ambient temperature");
    Assertions.assertTrue(estimate.getMeanSquaredError() < 4.0,
        "Residual prediction error should be small for the identified model");

    controller.disableMovingHorizonEstimation();
    Assertions.assertFalse(controller.isMovingHorizonEstimationEnabled());
    controller.clearMovingHorizonHistory();
    Assertions.assertNull(controller.getLastMovingHorizonEstimate());
  }

  @Test
  public void testControllerUsesManualPlantSamples() {
    double ambient = 25.0;
    double processGain = 0.35;
    double timeConstant = 25.0;
    double setPoint = 55.0;
    double dt = 1.0;

    SimpleHeaterMeasurement plant =
        new SimpleHeaterMeasurement("manualPlant", ambient, timeConstant, processGain);

    ModelPredictiveController controller = new ModelPredictiveController("manualPlantMpc");
    controller.setControllerSetPoint(setPoint, "C");
    controller.setProcessModel(processGain * 0.9, timeConstant * 1.1);
    controller.setProcessBias(ambient);
    controller.setPredictionHorizon(20);
    controller.setWeights(1.0, 0.02, 0.1);
    controller.setPreferredControlValue(0.0);
    controller.setOutputLimits(0.0, 120.0);
    controller.enableMovingHorizonEstimation(8);

    double appliedControl = 0.0;
    for (int step = 0; step < 80; step++) {
      controller.ingestPlantSample(plant.getMeasuredValue(), appliedControl, dt);
      controller.runTransient(Double.NaN, dt, UUID.randomUUID());
      appliedControl = controller.getResponse();
      plant.advance(appliedControl, dt);
    }

    Assertions.assertTrue(Math.abs(plant.getMeasuredValue() - setPoint) < 5.0,
        "Manual plant ingestion should drive temperature close to set point");
    Assertions.assertNotNull(controller.getLastMovingHorizonEstimate(),
        "Moving horizon estimator should update when ingesting plant data");
    Assertions.assertTrue(Double.isFinite(controller.getLastAppliedControl()),
        "Controller should track the latest applied control from the plant sample");
  }

  @Test
  public void testQualityMeasurementUpdateAppliesManualSamples() {
    ModelPredictiveController controller = new ModelPredictiveController("qualityFusion");
    controller.configureControls("heaterDuty");
    controller.setInitialControlValues(0.0);
    controller.setControlLimits("heaterDuty", -5.0, 5.0);
    controller.setControlWeights(1.0);
    controller.setMoveWeights(0.1);
    controller.setPreferredControlVector(0.0);

    ModelPredictiveController.QualityConstraint constraint =
        ModelPredictiveController.QualityConstraint.builder("spec")
            .limit(9.5)
            .margin(0.0)
            .controlSensitivity(0.4)
            .build();
    controller.addQualityConstraint(constraint);

    controller.runTransient(Double.NaN, 1.0, UUID.randomUUID());
    double predictedWithoutManual = controller.getPredictedQuality("spec");

    controller.setInitialControlValues(0.0);
    controller.updateQualityMeasurement("spec", 7.5);
    controller.runTransient(Double.NaN, 1.0, UUID.randomUUID());
    double predictedWithManual = controller.getPredictedQuality("spec");

    Assertions.assertTrue(predictedWithoutManual > predictedWithManual,
        "Manual quality measurements should override the default limit baseline");
  }

  @Test
  public void testPredictedTrajectoryApproachesSteadyState() {
    ModelPredictiveController controller = new ModelPredictiveController("trajectoryMpc");
    controller.setProcessModel(0.5, 20.0);
    controller.setProcessBias(30.0);
    controller.setPreferredControlValue(0.0);
    controller.setOutputLimits(0.0, 100.0);
    controller.ingestPlantSample(32.0, 10.0, 2.0);

    double[] trajectory = controller.getPredictedTrajectory(8, 2.0);
    Assertions.assertEquals(8, trajectory.length);
    double steadyState = 30.0 + 0.5 * 10.0;
    double previous = 32.0;
    for (double value : trajectory) {
      Assertions.assertTrue(Double.isFinite(value));
      Assertions.assertTrue(value >= previous - 1.0e-9,
          "Trajectory should not decrease for a positive control gain");
      Assertions.assertTrue(value <= steadyState + 1.0e-6,
          "Prediction should not overshoot the steady state for a first-order model");
      previous = value;
    }
    Assertions.assertEquals(steadyState, trajectory[trajectory.length - 1], 0.5);
  }
}
