package neqsim.process.controllerdevice;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.structure.CascadeControllerStructure;
import neqsim.process.controllerdevice.structure.ControlStructureInterface;
import neqsim.process.controllerdevice.structure.FeedForwardControllerStructure;
import neqsim.process.controllerdevice.structure.RatioControllerStructure;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;

/** Tests for advanced control structures coordinating multiple controllers. */
public class ControlStructureTest {

  /** Dummy controller returning set-point as response without dynamics. */
  static class DummyController implements ControllerDeviceInterface {
    double setPoint;
    double response;

    @Override
    public double getMeasuredValue() {
      return 0;
    }

    @Override
    public void setControllerSetPoint(double signal) {
      this.setPoint = signal;
    }

    @Override
    public String getUnit() {
      return "";
    }

    @Override
    public void setUnit(String unit) {}

    @Override
    public void setTransmitter(MeasurementDeviceInterface device) {}

    @Override
    public void runTransient(double initResponse, double dt, UUID id) {
      response = setPoint;
    }

    @Override
    public double getResponse() {
      return response;
    }

    @Override
    public boolean isReverseActing() {
      return false;
    }

    @Override
    public void setReverseActing(boolean reverseActing) {}

    @Override
    public void setControllerParameters(double Kp, double Ti, double Td) {}

    @Override
    public boolean equals(Object o) {
      return this == o;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public void setActive(boolean isActive) {}

    @Override
    public boolean isActive() {
      return true;
    }
  }

  /** Simple measurement device with manually set value. */
  static class DummyMeasurement extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1L;
    private double value;

    DummyMeasurement() {
      super("dummy", "");
    }

    void setValue(double value) {
      this.value = value;
    }

    @Override
    public double getMeasuredValue(String unit) {
      return value;
    }
  }

  @Test
  public void testCascadeStructurePassesSetPoint() {
    DummyController primary = new DummyController();
    DummyController secondary = new DummyController();
    ControlStructureInterface cascade =
        new CascadeControllerStructure(primary, secondary);
    primary.setControllerSetPoint(5.0);
    cascade.runTransient(1.0);
    Assertions.assertEquals(5.0, cascade.getOutput(), 1e-8);
  }

  @Test
  public void testRatioStructureUpdatesSetPoint() {
    DummyController controller = new DummyController();
    DummyMeasurement meas = new DummyMeasurement();
    meas.setValue(2.0);
    RatioControllerStructure ratio = new RatioControllerStructure(controller, meas);
    ratio.setRatio(3.0);
    ratio.runTransient(1.0);
    Assertions.assertEquals(6.0, ratio.getOutput(), 1e-8);
  }

  @Test
  public void testFeedForwardStructureAddsDisturbance() {
    DummyController controller = new DummyController();
    controller.setControllerSetPoint(1.0);
    DummyMeasurement meas = new DummyMeasurement();
    meas.setValue(2.0);
    FeedForwardControllerStructure ff = new FeedForwardControllerStructure(controller, meas);
    ff.setFeedForwardGain(0.5);
    ff.runTransient(1.0);
    Assertions.assertEquals(2.0, ff.getOutput(), 1e-8);
  }
}
