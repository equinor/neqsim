package neqsim.process.controllerdevice;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;

/**
 * Tests for the PID controller deadband (SP-PV) that freezes the controller output while the absolute control error
 * stays inside the configured band. Motivated by PEPR 80300477 (Aasta Hansteen System 24, 24LIC0213 TEG contactor
 * level) where a DCS deadband caused a valve limit cycle.
 *
 * @author Copilot
 * @version 1.0
 */
class ControllerDeviceDeadBandTest {
  /**
   * Minimal transmitter whose measured value can be set directly.
   */
  static class DummyTransmitter extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1L;
    private double value = 0.0;

    /**
     * Constructor.
     *
     * @param name device name
     * @param unit engineering unit
     */
    DummyTransmitter(String name, String unit) {
      super(name, unit);
    }

    /** {@inheritDoc} */
    @Override
    public double getMeasuredValue() {
      return value;
    }

    /** {@inheritDoc} */
    @Override
    public double getMeasuredValue(String unit) {
      return value;
    }

    /**
     * Set the measured value.
     *
     * @param value measured value
     */
    public void setValue(double value) {
      this.value = value;
    }
  }

  private ControllerDeviceBaseClass makeController(double deadBand) {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("24LIC0213");
    DummyTransmitter t = new DummyTransmitter("LT", "%");
    t.setMinimumValue(0.0);
    t.setMaximumValue(100.0);
    t.setValue(45.5);
    controller.setTransmitter(t);
    controller.setControllerSetPoint(45.0, "%");
    controller.setControllerParameters(4.0, 1400.0, 0.0);
    controller.setReverseActing(true);
    controller.setDeadBand(deadBand);
    return controller;
  }

  @Test
  void testDeadbandGetterSetterClampsNegative() {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("c");
    Assertions.assertEquals(0.0, controller.getDeadBand(), 1e-12);
    controller.setDeadBand(0.5);
    Assertions.assertEquals(0.5, controller.getDeadBand(), 1e-12);
    controller.setDeadBand(-3.0);
    Assertions.assertEquals(0.0, controller.getDeadBand(), 1e-12);
  }

  @Test
  void testWithinDeadbandHoldsOutput() {
    // |PV - SP| = 0.5 which is inside the 1.0 deadband -> output held.
    ControllerDeviceBaseClass controller = makeController(1.0);
    double init = 80.0;
    controller.runTransient(init, 1.0, UUID.randomUUID());
    Assertions.assertEquals(init, controller.getResponse(), 1e-9);
  }

  @Test
  void testOutsideDeadbandActs() {
    // |PV - SP| = 0.5 with deadband disabled -> controller moves the valve.
    ControllerDeviceBaseClass controller = makeController(0.0);
    double init = 80.0;
    controller.runTransient(init, 1.0, UUID.randomUUID());
    Assertions.assertNotEquals(init, controller.getResponse(), 1e-6);
  }

  @Test
  void testErrorBeyondDeadbandActs() {
    // |PV - SP| = 3.0 which is outside the 1.0 deadband -> controller acts.
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("24LIC0213");
    DummyTransmitter t = new DummyTransmitter("LT", "%");
    t.setMinimumValue(0.0);
    t.setMaximumValue(100.0);
    t.setValue(48.0);
    controller.setTransmitter(t);
    controller.setControllerSetPoint(45.0, "%");
    controller.setControllerParameters(4.0, 1400.0, 0.0);
    controller.setReverseActing(true);
    controller.setDeadBand(1.0);
    double init = 80.0;
    controller.runTransient(init, 1.0, UUID.randomUUID());
    Assertions.assertNotEquals(init, controller.getResponse(), 1e-6);
  }
}
