package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class SystemPCSAFTTest {
  @Test
  public void testInit() {
    SystemInterface testSystem = new SystemPCSAFT(250.0, 10.0);
    testSystem.addComponent("methane", 1.0);
    testSystem.addComponent("n-hexane", 1.0);
    testSystem.setMixingRule(1);
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();
    testSystem.initProperties();
    double cp = testSystem.getCp();
    assertEquals(208.85116193406583, cp, 0.1);
  }
}
