package neqsim.process.controllerdevice;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;

class ControllerDevicePIDEnhancementsTest {
  static class DummyTransmitter extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1L;
    private double value = 0.0;

    DummyTransmitter(String name, String unit) {
      super(name, unit);
    }

    @Override
    public double getMeasuredValue() {
      return value;
    }

    @Override
    public double getMeasuredValue(String unit) {
      return value;
    }

    public void setValue(double value) {
      this.value = value;
    }
  }

  @Test
  void testAntiWindup() {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("test");
    DummyTransmitter transmitter = new DummyTransmitter("trans", "%");
    transmitter.setMinimumValue(0.0);
    transmitter.setMaximumValue(200.0);
    transmitter.setValue(200.0);
    controller.setTransmitter(transmitter);
    controller.setControllerSetPoint(50.0, "%");
    controller.setControllerParameters(2.0, 1.0, 0.0);
    controller.setOutputLimits(0.0, 100.0);

    controller.runTransient(50.0, 1.0, UUID.randomUUID());
    Assertions.assertEquals(100.0, controller.getResponse());

    controller.runTransient(100.0, 1.0, UUID.randomUUID());
    Assertions.assertEquals(100.0, controller.getResponse());
  }

  @Test
  void testEngineeringUnitVelocityPiDoesNotDoubleIntegrateSustainedError() {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("velocity-pi");
    DummyTransmitter transmitter = new DummyTransmitter("trans", "%");
    transmitter.setValue(60.0);
    controller.setTransmitter(transmitter);
    controller.setControllerSetPoint(50.0, "%");
    controller.setControllerParameters(2.0, 10.0, 0.0);

    controller.runTransient(100.0, 1.0, UUID.randomUUID());
    Assertions.assertEquals(102.0, controller.getResponse(), 1.0e-12);
    controller.runTransient(controller.getResponse(), 1.0, UUID.randomUUID());

    Assertions.assertEquals(104.0, controller.getResponse(), 1.0e-12);
  }

  @Test
  void testDerivativeContributionDisappearsAfterMeasurementStepSettles() {
    assertDerivativeStepResponse(0.0);
  }

  @Test
  void testFilteredDerivativeContributionDecaysAfterMeasurementStep() {
    assertDerivativeStepResponse(1.0);
  }

  private void assertDerivativeStepResponse(double filterTime) {
    DummyTransmitter transmitter = new DummyTransmitter("trans", "%");
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("derivative-step");
    controller.setTransmitter(transmitter);
    controller.setControllerSetPoint(0.0, "%");
    controller.setControllerParameters(2.0, 0.0, 3.0);
    controller.setDerivativeFilterTime(filterTime);
    double dt = 0.5;
    controller.runTransient(40.0, dt, UUID.randomUUID());
    transmitter.setValue(10.0);
    double derivative = 10.0 / (filterTime + dt);
    controller.runTransient(controller.getResponse(), dt, UUID.randomUUID());
    Assertions.assertEquals(60.0 + 6.0 * derivative, controller.getResponse(), 1.0e-10);
    for (int i = 0; i < 20; i++) {
      derivative *= filterTime / (filterTime + dt);
      controller.runTransient(controller.getResponse(), dt, UUID.randomUUID());
      Assertions.assertEquals(60.0 + 6.0 * derivative, controller.getResponse(), 1.0e-10,
          "Derivative action must decay while the proportional contribution remains");
    }
  }

  @Test
  void testSwitchingBackToDirectActionReversesIntegralDirection() {
    DummyTransmitter transmitter = new DummyTransmitter("trans", "%");
    transmitter.setValue(60.0);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("action-switch");
    controller.setTransmitter(transmitter);
    controller.setControllerSetPoint(50.0, "%");
    controller.setControllerParameters(2.0, 10.0, 0.0);
    controller.setReverseActing(true);
    controller.runTransient(100.0, 1.0, UUID.randomUUID());
    Assertions.assertEquals(98.0, controller.getResponse(), 1.0e-12);
    controller.setReverseActing(false);
    controller.runTransient(controller.getResponse(), 1.0, UUID.randomUUID());
    Assertions.assertEquals(100.0, controller.getResponse(), 1.0e-12);
    controller.setReverseActing(true);
    controller.runTransient(controller.getResponse(), 1.0, UUID.randomUUID());
    Assertions.assertEquals(98.0, controller.getResponse(), 1.0e-12);
  }

  @Test
  void testDerivativeFiltering() {
    DummyTransmitter trans1 = new DummyTransmitter("t1", "%");
    trans1.setMinimumValue(0.0);
    trans1.setMaximumValue(100.0);
    ControllerDeviceBaseClass filtered = new ControllerDeviceBaseClass("filtered");
    filtered.setTransmitter(trans1);
    filtered.setControllerSetPoint(0.0, "%");
    filtered.setControllerParameters(1.0, 0.0, 1.0);
    filtered.setDerivativeFilterTime(1.0);

    trans1.setValue(0.0);
    filtered.runTransient(0.0, 1.0, UUID.randomUUID());
    trans1.setValue(100.0);
    filtered.runTransient(filtered.getResponse(), 1.0, UUID.randomUUID());
    double filteredResp = filtered.getResponse();

    DummyTransmitter trans2 = new DummyTransmitter("t2", "%");
    trans2.setMinimumValue(0.0);
    trans2.setMaximumValue(100.0);
    ControllerDeviceBaseClass unfiltered = new ControllerDeviceBaseClass("unfiltered");
    unfiltered.setTransmitter(trans2);
    unfiltered.setControllerSetPoint(0.0, "%");
    unfiltered.setControllerParameters(1.0, 0.0, 1.0);

    trans2.setValue(0.0);
    unfiltered.runTransient(0.0, 1.0, UUID.randomUUID());
    trans2.setValue(100.0);
    unfiltered.runTransient(unfiltered.getResponse(), 1.0, UUID.randomUUID());
    double unfilteredResp = unfiltered.getResponse();

    Assertions.assertTrue(filteredResp < unfilteredResp);
  }

  @Test
  void testExplicitUnitHandling() {
    class ConvertingTransmitter extends MeasurementDeviceBaseClass {
      private static final long serialVersionUID = 1L;
      private double value = 0.0;

      ConvertingTransmitter(String name, String unit) {
        super(name, unit);
      }

      @Override
      public double getMeasuredValue() {
        return value;
      }

      @Override
      public double getMeasuredValue(String unit) {
        if (unit.equals(getUnit())) {
          return value;
        } else if (unit.equals("B")) {
          return value * 2.0;
        } else {
          return value / 2.0;
        }
      }

      public void setValue(double value) {
        this.value = value;
      }
    }

    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("unit");
    ConvertingTransmitter trans = new ConvertingTransmitter("t", "A");
    controller.setTransmitter(trans);
    controller.setControllerParameters(1.0, 0.0, 0.0);

    controller.setControllerSetPoint(10.0, "B");
    trans.setValue(5.0);
    controller.runTransient(0.0, 1.0, UUID.randomUUID());
    Assertions.assertEquals(0.0, controller.getResponse(), 1e-6);
    Assertions.assertEquals("B", controller.getUnit());
  }

  @Test
  void testLoggingAndPerformanceMetrics() {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("metrics");
    DummyTransmitter transmitter = new DummyTransmitter("t", "%");
    transmitter.setMinimumValue(0.0);
    transmitter.setMaximumValue(100.0);
    controller.setTransmitter(transmitter);
    controller.setControllerSetPoint(50.0, "%");
    controller.setControllerParameters(1.0, 1.0, 0.0);

    controller.resetEventLog();
    controller.resetPerformanceMetrics();
    double response = 0.0;
    for (int i = 0; i < 10; i++) {
      transmitter.setValue(i * 10.0);
      controller.runTransient(response, 1.0, UUID.randomUUID());
      response = controller.getResponse();
    }
    for (int i = 0; i < 5; i++) {
      transmitter.setValue(50.0);
      controller.runTransient(response, 1.0, UUID.randomUUID());
      response = controller.getResponse();
    }

    Assertions.assertEquals(15, controller.getEventLog().size());
    Assertions.assertEquals(250.0, controller.getIntegralAbsoluteError(), 1e-6);
    Assertions.assertEquals(10.0, controller.getSettlingTime(), 1e-6);
  }
}
