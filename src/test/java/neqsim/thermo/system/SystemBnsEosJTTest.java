package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Additional JT coefficient checks for BNS-PR system at low and high pressure.
 */
public class SystemBnsEosJTTest {
  private SystemBnsEos createSystem(double pressure) {
    SystemBnsEos sys = new SystemBnsEos();
    sys.setTemperature(300.0);
    sys.setPressure(pressure);
    sys.setAssociatedGas(false);
    sys.setRelativeDensity(0.65);
    sys.setComposition(0.02, 0.0, 0.01, 0.0);
    sys.useVolumeCorrection(true);
    sys.setMixingRule(12);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initProperties();
    return sys;
  }

  @Test
  public void testLowPressureJT() {
    SystemBnsEos sys = createSystem(1.0);
    double jt = sys.getPhase(0).getJouleThomsonCoefficient() * 10.0;
    assertEquals(5.515119413490732, jt, 0.2);
  }

  @Test
  public void testHighPressureJT() {
    SystemBnsEos sys = createSystem(300.0);
    double jt = sys.getPhase(0).getJouleThomsonCoefficient() * 10.0;
    assertEquals(0.5560838462776915, jt, 0.05);
  }
}
