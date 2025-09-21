package neqsim.process.controllerdevice;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;

/**
 * Example showcasing the {@link ModelPredictiveController} with moving horizon estimation. The
 * scenario mimics an electric heater whose true dynamics drift over time. The controller identifies
 * the process gain, time constant and bias online to maintain accurate tracking.
 */
public class MovingHorizonEstimationExampleTest extends neqsim.NeqSimTest {

  /**
   * Simple first-order process model acting as the transmitter for the MPC. The model permits
   * adjustments to the underlying gain, time constant and ambient bias to emulate plant drift.
   */
  private static final class AdaptiveHeaterMeasurement extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1L;
    private double ambientTemperature;
    private double timeConstant;
    private double processGain;
    private double temperature;

    AdaptiveHeaterMeasurement(String name, double ambient, double timeConstant, double gain) {
      super(name, "C");
      this.ambientTemperature = ambient;
      this.timeConstant = timeConstant;
      this.processGain = gain;
      this.temperature = ambient;
    }

    void advance(double controlSignal, double dt) {
      double tau = Math.max(timeConstant, 1.0e-6);
      double drivingForce = -(temperature - ambientTemperature) + processGain * controlSignal;
      temperature += drivingForce * dt / tau;
    }

    void setProcessGain(double gain) {
      this.processGain = gain;
    }

    void setTimeConstant(double timeConstant) {
      this.timeConstant = timeConstant;
    }

    void setAmbientTemperature(double ambientTemperature) {
      this.ambientTemperature = ambientTemperature;
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
      throw new IllegalArgumentException("Unsupported unit for adaptive heater measurement: " + unit);
    }
  }

  @Test
  public void exampleMovingHorizonEstimationAdaptsProcessModel() {
    double ambient = 18.0;
    double initialGain = 0.55;
    double initialTimeConstant = 45.0;
    double dt = 1.0;

    AdaptiveHeaterMeasurement measurement =
        new AdaptiveHeaterMeasurement("adaptive heater", ambient, initialTimeConstant, initialGain);

    ModelPredictiveController controller = new ModelPredictiveController("movingHorizonExample");
    controller.setTransmitter(measurement);
    controller.setControllerSetPoint(ambient + 22.0, "C");
    controller.setProcessModel(0.2, 20.0);
    controller.setProcessBias(ambient + 3.0);
    controller.setPredictionHorizon(20);
    controller.setWeights(1.0, 0.04, 0.2);
    controller.setPreferredControlValue(0.0);
    controller.setOutputLimits(0.0, 120.0);
    controller.enableMovingHorizonEstimation(60);

    Assertions.assertTrue(controller.isMovingHorizonEstimationEnabled(),
        "Moving horizon estimation should be enabled for the example");
    Assertions.assertEquals(60, controller.getMovingHorizonEstimationWindow());

    for (int step = 0; step < 240; step++) {
      controller.runTransient(controller.getResponse(), dt);
      double control = controller.getResponse();
      measurement.advance(control, dt);
    }

    ModelPredictiveController.MovingHorizonEstimate initialEstimate =
        controller.getLastMovingHorizonEstimate();
    Assertions.assertNotNull(initialEstimate,
        "Estimator should return an identification after accumulating samples");
    Assertions.assertTrue(initialEstimate.getSampleCount() >= 50,
        "Identification should utilise most of the horizon window");
    Assertions.assertEquals(initialGain, initialEstimate.getProcessGain(), 0.12,
        "Estimated gain should approach the true process gain");
    Assertions.assertEquals(initialTimeConstant, initialEstimate.getTimeConstant(), 8.0,
        "Estimated time constant should approach the real dynamics");
    Assertions.assertEquals(ambient, initialEstimate.getProcessBias(), 4.0,
        "Estimated bias should align with the ambient temperature");
    Assertions.assertTrue(initialEstimate.getMeanSquaredError() < 6.0,
        "Prediction error should be modest once the model converges");
    Assertions.assertEquals(controller.getControllerSetPoint(), measurement.getMeasuredValue(), 1.5,
        "Closed loop should settle near the target after identification");

    double fouledGain = 0.35;
    double fouledTimeConstant = 70.0;
    double newAmbient = ambient + 4.0;
    measurement.setProcessGain(fouledGain);
    measurement.setTimeConstant(fouledTimeConstant);
    measurement.setAmbientTemperature(newAmbient);
    controller.setControllerSetPoint(newAmbient + 20.0, "C");

    for (int step = 0; step < 260; step++) {
      controller.runTransient(controller.getResponse(), dt);
      double control = controller.getResponse();
      measurement.advance(control, dt);
    }

    ModelPredictiveController.MovingHorizonEstimate adaptedEstimate =
        controller.getLastMovingHorizonEstimate();
    Assertions.assertNotNull(adaptedEstimate,
        "Estimator should continue providing updated models after a drift event");
    Assertions.assertTrue(adaptedEstimate.getSampleCount() >= 50,
        "Updated identification should also leverage the full window");
    Assertions.assertEquals(fouledGain, adaptedEstimate.getProcessGain(), 0.12,
        "Estimated gain should adapt to the fouled heater");
    Assertions.assertEquals(fouledTimeConstant, adaptedEstimate.getTimeConstant(), 10.0,
        "Estimated time constant should reflect the slower dynamics");
    Assertions.assertEquals(newAmbient, adaptedEstimate.getProcessBias(), 4.0,
        "Estimated bias should shift with the new ambient condition");
    Assertions.assertTrue(adaptedEstimate.getMeanSquaredError() < 8.0,
        "Prediction error should remain bounded after the disturbance");
    Assertions.assertNotEquals(initialEstimate.getProcessGain(), adaptedEstimate.getProcessGain(), 1.0e-3,
        "Moving horizon estimation should adjust the process gain when conditions change");
    Assertions.assertEquals(controller.getControllerSetPoint(), measurement.getMeasuredValue(), 3.0,
        "Controller should re-steady close to the new temperature target");
  }
}

