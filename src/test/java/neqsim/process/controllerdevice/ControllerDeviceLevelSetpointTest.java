package neqsim.process.controllerdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;

class ControllerDeviceLevelSetpointTest {
  static class DummyLevelTransmitter extends MeasurementDeviceBaseClass {
    private double value;

    DummyLevelTransmitter(String name) {
      super(name, "");
      setMinimumValue(0.0);
      setMaximumValue(1.0);
    }

    @Override
    public double getMeasuredValue() {
      return value;
    }

    @Override
    public double getMeasuredValue(String unit) {
      return value;
    }

    void setValue(double newValue) {
      value = Math.max(getMinimumValue(), Math.min(getMaximumValue(), newValue));
    }
  }

  @Test
  void controllerTracksLowerLevelSetpoint() {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("level");
    controller.setReverseActing(true);
    controller.setControllerParameters(25.8, 400.1, 0.0);

    DummyLevelTransmitter transmitter = new DummyLevelTransmitter("lt");
    controller.setTransmitter(transmitter);
    controller.setControllerSetPoint(0.3);

    double level = 0.3;
    double valveOpening = 30.0;
    double dt = 50.0;
    for (int i = 0; i < 800; i++) {
      transmitter.setValue(level);
      controller.runTransient(valveOpening, dt, UUID.randomUUID());
      valveOpening = clamp(controller.getResponse());
      level = updateLevel(level, valveOpening, dt);
    }

    assertEquals(0.3, level, 0.03);

    controller.setControllerSetPoint(0.27);

    for (int i = 0; i < 800; i++) {
      transmitter.setValue(level);
      controller.runTransient(valveOpening, dt, UUID.randomUUID());
      valveOpening = clamp(controller.getResponse());
      level = updateLevel(level, valveOpening, dt);
    }

    assertEquals(0.27, level, 0.03);
  }

  private double updateLevel(double currentLevel, double valveOpening, double dt) {
    double inflow = valveOpening / 100.0;
    double outflow = currentLevel / 0.5;
    double nextLevel = currentLevel + dt / 3600.0 * (inflow - outflow);
    return Math.max(0.0, Math.min(1.0, nextLevel));
  }

  private double clamp(double opening) {
    return Math.max(0.0, Math.min(100.0, opening));
  }
}
