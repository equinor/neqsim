package neqsim.process.controllerdevice;

import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;

/**
 * Example showcasing the {@link ModelPredictiveController} with moving horizon estimation. The
 * scenario mimics an electric heater whose true dynamics drift over time. The controller identifies
 * the process gain, time constant and bias online to maintain accurate tracking.
 */
public class MovingHorizonEstimationExample {

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

  private MovingHorizonEstimationExample() {}

  public static void main(String[] args) {
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

    for (int step = 0; step < 240; step++) {
      controller.runTransient(controller.getResponse(), dt);
      double control = controller.getResponse();
      measurement.advance(control, dt);
    }

    ModelPredictiveController.MovingHorizonEstimate initialEstimate =
        controller.getLastMovingHorizonEstimate();
    if (initialEstimate != null) {
      System.out.println("Initial identification: gain=" + initialEstimate.getProcessGain()
          + ", tau=" + initialEstimate.getTimeConstant() + ", bias="
          + initialEstimate.getProcessBias() + ", mse=" + initialEstimate.getMeanSquaredError());
    }

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
    if (adaptedEstimate != null) {
      System.out.println("Adapted identification: gain=" + adaptedEstimate.getProcessGain()
          + ", tau=" + adaptedEstimate.getTimeConstant() + ", bias="
          + adaptedEstimate.getProcessBias() + ", mse=" + adaptedEstimate.getMeanSquaredError());
    }

    System.out.println("Final measured temperature: " + measurement.getMeasuredValue());
    System.out.println("Controller set point: " + controller.getControllerSetPoint());
  }
}
