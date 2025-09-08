package neqsim.process.measurementdevice;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MeasurementDeviceNoiseDelayTest {
  static class DummyTransmitter extends MeasurementDeviceBaseClass {
    private double value = 0.0;

    DummyTransmitter(String name, String unit) {
      super(name, unit);
    }

    void setValue(double value) {
      this.value = value;
    }

    @Override
    public double getMeasuredValue(String unit) {
      return applySignalModifiers(value);
    }
  }

  @Test
  void testNoise() {
    DummyTransmitter trans = new DummyTransmitter("noise", "u");
    trans.setRandomSeed(0L);
    trans.setNoiseStdDev(1.0);
    trans.setValue(10.0);
    double measured = trans.getMeasuredValue("u");
    Assertions.assertEquals(10.0 + 0.8025330637390305, measured, 1e-12);
  }

  @Test
  void testDelay() {
    DummyTransmitter trans = new DummyTransmitter("delay", "u");
    trans.setDelaySteps(2);
    trans.setNoiseStdDev(0.0);
    trans.setValue(1.0);
    Assertions.assertEquals(1.0, trans.getMeasuredValue("u"), 1e-12);
    trans.setValue(2.0);
    Assertions.assertEquals(2.0, trans.getMeasuredValue("u"), 1e-12);
    trans.setValue(3.0);
    Assertions.assertEquals(1.0, trans.getMeasuredValue("u"), 1e-12);
    trans.setValue(4.0);
    Assertions.assertEquals(2.0, trans.getMeasuredValue("u"), 1e-12);
  }
}

