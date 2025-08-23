package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SystemBnsEosTest {
  @Test
  public void testZFactor100Bar() {
    SystemBnsEos sys = new SystemBnsEos(300.0, 100.0, 0.65, 0.02, 0.0, 0.01, 0.0, false);
    sys.useVolumeCorrection(true);
    sys.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    double z = sys.getPhase(0).getZ();
    assertEquals(0.7688865438065107, z, 0.01);
  }

  @Test
  public void testZFactor10Bar() {
    SystemBnsEos sys = new SystemBnsEos(300.0, 10.0, 0.65, 0.02, 0.0, 0.01, 0.0, false);
    sys.useVolumeCorrection(true);
    sys.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    double z = sys.getPhase(0).getZ();
    assertEquals(0.9741308690957756, z, 0.01);
  }
}
