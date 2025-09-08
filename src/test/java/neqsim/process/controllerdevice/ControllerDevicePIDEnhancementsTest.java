package neqsim.process.controllerdevice;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;

class ControllerDevicePIDEnhancementsTest {
  static class DummyTransmitter extends MeasurementDeviceBaseClass {
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
