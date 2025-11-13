package neqsim.process.controllerdevice;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceInterface.StepResponseTuningMethod;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;

class ControllerDeviceAutoTuneGainSchedulingTest {
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
  void testAutoTuneUltimate() {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("auto");
    controller.autoTune(4.0, 10.0);
    Assertions.assertEquals(2.4, controller.getKp(), 1e-6);
    Assertions.assertEquals(5.0, controller.getTi(), 1e-6);
    Assertions.assertEquals(1.25, controller.getTd(), 1e-6);
  }

  @Test
  void testAutoTuneStepResponse() {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("step");
    controller.setStepResponseTuningMethod(StepResponseTuningMethod.CLASSIC);
    controller.autoTuneStepResponse(2.0, 10.0, 2.0);
    Assertions.assertEquals(3.0, controller.getKp(), 1e-6);
    Assertions.assertEquals(4.0, controller.getTi(), 1e-6);
    Assertions.assertEquals(1.0, controller.getTd(), 1e-6);
  }

  @Test
  void testAutoTuneStepResponseSimc() {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("stepSimc");
    controller.setStepResponseTuningMethod(StepResponseTuningMethod.SIMC);
    controller.autoTuneStepResponse(2.0, 10.0, 2.0);
    Assertions.assertEquals(1.5714286, controller.getKp(), 1e-6);
    Assertions.assertEquals(11.0, controller.getTi(), 1e-6);
    Assertions.assertEquals(0.9090909, controller.getTd(), 1e-6);
  }

  @Test
  void testAutoTuneStepResponseSimcPiOnly() {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("stepSimcPi");
    controller.setStepResponseTuningMethod(StepResponseTuningMethod.SIMC);
    controller.autoTuneStepResponse(2.0, 10.0, 2.0, false);
    Assertions.assertEquals(1.1111111, controller.getKp(), 1e-6);
    Assertions.assertEquals(10.0, controller.getTi(), 1e-6);
    Assertions.assertEquals(0.0, controller.getTd(), 1e-6);
  }

  @Test
  void testGainScheduling() {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("sched");
    DummyTransmitter trans = new DummyTransmitter("t", "%");
    trans.setMinimumValue(0.0);
    trans.setMaximumValue(100.0);
    controller.setTransmitter(trans);
    controller.setControllerSetPoint(0.0, "%");
    controller.addGainSchedulePoint(0.0, 1.0, 1.0, 0.0);
    controller.addGainSchedulePoint(50.0, 2.0, 2.0, 0.0);

    trans.setValue(25.0);
    controller.runTransient(0.0, 1.0, UUID.randomUUID());
    Assertions.assertEquals(1.0, controller.getKp(), 1e-6);
    Assertions.assertEquals(1.0, controller.getTi(), 1e-6);

    trans.setValue(75.0);
    controller.runTransient(controller.getResponse(), 1.0, UUID.randomUUID());
    Assertions.assertEquals(2.0, controller.getKp(), 1e-6);
    Assertions.assertEquals(2.0, controller.getTi(), 1e-6);
  }

  @Test
  void testAutoTuneFromEventLog() {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("log");
    DummyTransmitter trans = new DummyTransmitter("t", "%");
    controller.setTransmitter(trans);
    controller.setControllerSetPoint(0.0, "%");

    controller.resetEventLog();
    List<ControllerEvent> log = controller.getEventLog();
    log.add(new ControllerEvent(0.0, 5.0, 0.0, 0.0, 10.0));
    log.add(new ControllerEvent(2.0, 5.0, 0.0, 0.0, 40.0));
    log.add(new ControllerEvent(4.0, 8.0, 0.0, 0.0, 40.0));
    log.add(new ControllerEvent(6.0, 14.0, 0.0, 0.0, 40.0));
    log.add(new ControllerEvent(8.0, 18.0, 0.0, 0.0, 40.0));
    log.add(new ControllerEvent(10.0, 20.0, 0.0, 0.0, 40.0));
    log.add(new ControllerEvent(12.0, 20.0, 0.0, 0.0, 40.0));
    log.add(new ControllerEvent(14.0, 20.0, 0.0, 0.0, 40.0));
    log.add(new ControllerEvent(16.0, 20.0, 0.0, 0.0, 40.0));
    log.add(new ControllerEvent(18.0, 20.0, 0.0, 0.0, 40.0));

    boolean tuned = controller.autoTuneFromEventLog();

    Assertions.assertTrue(tuned);
    Assertions.assertEquals(2.4, controller.getKp(), 1e-6);
    Assertions.assertEquals(8.0, controller.getTi(), 1e-6);
    Assertions.assertEquals(2.0, controller.getTd(), 1e-6);
  }
}
