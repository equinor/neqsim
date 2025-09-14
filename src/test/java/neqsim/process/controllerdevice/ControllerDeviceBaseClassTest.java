package neqsim.process.controllerdevice;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ControllerDeviceBaseClassTest {
  static ControllerDeviceBaseClass c;

  @BeforeAll
  static void setUp() {
    c = new ControllerDeviceBaseClass("testPID");
  }

  @Test
  void testSetControllerParameters() {
    double zero = 0;
    double positive = 9.9;
    double negative = -0.1;

    c.setControllerParameters(zero, zero, zero);

    Assertions.assertEquals(zero, c.getKp());
    Assertions.assertEquals(zero, c.getTd());
    Assertions.assertEquals(zero, c.getTi());

    c.setControllerParameters(positive, positive, positive);
    Assertions.assertEquals(positive, c.getKp());
    Assertions.assertEquals(positive, c.getTd());
    Assertions.assertEquals(positive, c.getTi());

    c.setControllerParameters(negative, positive, positive);
    Assertions.assertEquals(positive, c.getKp());
    Assertions.assertEquals(positive, c.getTd());
    Assertions.assertEquals(positive, c.getTi());

    c.setControllerParameters(positive, negative, positive);
    Assertions.assertEquals(positive, c.getKp());
    Assertions.assertEquals(positive, c.getTd());
    Assertions.assertEquals(positive, c.getTi());

    c.setControllerParameters(positive, positive, negative);
    Assertions.assertEquals(positive, c.getKp());
    Assertions.assertEquals(positive, c.getTd());
    Assertions.assertEquals(positive, c.getTi());
  }

  @Test
  void testGetKp() {
    double kp = c.getKp();
    Assertions.assertEquals(c.getKp(), kp);
  }

  @Test
  void testGetTd() {
    double td = c.getTd();
    Assertions.assertEquals(c.getTd(), td);
  }

  @Test
  void testGetTi() {
    double ti = c.getTi();
    Assertions.assertEquals(c.getTi(), ti);
  }

  @Test
  void testGetUnit() {
    String unit = c.getUnit();
    Assertions.assertEquals(c.getUnit(), unit);
  }

  @Test
  void testIsReverseActing() {
    boolean isReverse = c.isReverseActing();
    Assertions.assertEquals(isReverse, c.isReverseActing());
  }

  @Test
  void testSetReverseActing() {
    boolean testValue = true;
    boolean oldValue = c.isReverseActing();
    c.setReverseActing(testValue);
    Assertions.assertEquals(testValue, c.isReverseActing());
    c.setReverseActing(oldValue);
    Assertions.assertEquals(oldValue, c.isReverseActing());
  }

  @Test
  void testSetUnit() {
    String testUnit = "test";
    String oldUnit = c.getUnit();
    c.setUnit(testUnit);
    Assertions.assertEquals(testUnit, c.getUnit());
    c.setUnit(oldUnit);
    Assertions.assertEquals(oldUnit, c.getUnit());
  }

  @Test
  void testGetControllerSetPoint() {
    double setPoint = 5.0;
    c.setControllerSetPoint(setPoint);
    Assertions.assertEquals(setPoint, c.getControllerSetPoint());
  }
}
