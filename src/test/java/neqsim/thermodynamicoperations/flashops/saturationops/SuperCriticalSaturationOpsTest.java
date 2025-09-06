package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;

public class SuperCriticalSaturationOpsTest {
  @Test
  void bubblePointThrowsForSupercriticalPureComponent() {
    SystemSrkEos sys = new SystemSrkEos(300.0, 10.0);
    sys.addComponent("methane", 1.0);
    sys.setMixingRule("classic");
    BubblePointPressureFlash calc = new BubblePointPressureFlash(sys);
    assertThrows(IllegalStateException.class, calc::run);
  }

  @Test
  void dewPointThrowsForSupercriticalPureComponent() {
    SystemSrkEos sys = new SystemSrkEos(300.0, 10.0);
    sys.addComponent("methane", 1.0);
    sys.setMixingRule("classic");
    DewPointPressureFlash calc = new DewPointPressureFlash(sys);
    assertThrows(IllegalStateException.class, calc::run);
  }

  @Test
  void bubblePointPressureIgnoresHighPressure() {
    SystemSrkEos sys = new SystemSrkEos(150.0, 100.0);
    sys.addComponent("methane", 1.0);
    sys.setMixingRule("classic");
    BubblePointPressureFlash calc = new BubblePointPressureFlash(sys);
    assertDoesNotThrow(calc::run);
  }

  @Test
  void dewPointPressureIgnoresHighPressure() {
    SystemSrkEos sys = new SystemSrkEos(150.0, 100.0);
    sys.addComponent("methane", 1.0);
    sys.setMixingRule("classic");
    DewPointPressureFlash calc = new DewPointPressureFlash(sys);
    assertDoesNotThrow(calc::run);
  }

  @Test
  void dewPointTemperatureThrowsForSupercriticalPressure() {
    SystemSrkEos sys = new SystemSrkEos(150.0, 100.0);
    sys.addComponent("methane", 1.0);
    sys.setMixingRule("classic");
    DewPointTemperatureFlash calc = new DewPointTemperatureFlash(sys);
    assertThrows(IllegalStateException.class, calc::run);
  }

  @Test
  void bubblePointTemperatureThrowsForSupercriticalPressure() {
    SystemSrkEos sys = new SystemSrkEos(150.0, 100.0);
    sys.addComponent("methane", 1.0);
    sys.setMixingRule("classic");
    BubblePointTemperatureFlash calc = new BubblePointTemperatureFlash(sys);
    assertThrows(IllegalStateException.class, calc::run);
  }
}
