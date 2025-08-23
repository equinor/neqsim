package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SystemBnsEosTest {
  @Test
  public void testZFactor100Bar() {
    SystemBnsEos sys = new SystemBnsEos();
    sys.setTemperature(300.0);
    sys.setPressure(100.0);
    sys.setAssociatedGas(false);
    sys.setRelativeDensity(0.65);
    sys.setComposition(0.02, 0.0, 0.01, 0.0);
    sys.useVolumeCorrection(true);
    sys.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    double z = sys.getPhase(0).getZ();
    assertEquals(0.7688865438065107, z, 0.01);
  }

  @Test
  public void testZFactor10Bar() {
    SystemBnsEos sys = new SystemBnsEos();
    sys.setTemperature(300.0);
    sys.setPressure(10.0);
    sys.setAssociatedGas(false);
    sys.setRelativeDensity(0.65);
    sys.setComposition(0.02, 0.0, 0.01, 0.0);
    sys.useVolumeCorrection(true);
    sys.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    double z = sys.getPhase(0).getZ();
    assertEquals(0.9741308690957756, z, 0.01);
  }

  @Test
  public void testPseudoCriticalUpdates() {
    SystemBnsEos sys = new SystemBnsEos();
    sys.setTemperature(300.0);
    sys.setPressure(100.0);
    sys.setAssociatedGas(false);
    sys.setRelativeDensity(0.65);
    sys.setComposition(0.02, 0.0, 0.01, 0.0);
    double tc1 = sys.getPhase(0).getComponent("HC").getTC();
    sys.setRelativeDensity(0.70);
    double tc2 = sys.getPhase(0).getComponent("HC").getTC();
    assertNotEquals(tc1, tc2);
  }
}
