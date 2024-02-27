package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class SystemPCSAFTTest {

  @Test
  public void testInit() {
    SystemInterface testSystem = new SystemPCSAFT(150.0, 10.0);
    testSystem.addComponent("methane", 1.0);
    testSystem.addComponent("n-hexane", 10.0001);
    testSystem.setMixingRule(1);
    testSystem.createDatabase(true);
    testSystem.init(0);
    testSystem.init(3);
    System.out.println("test");
    double cp = testSystem.getCp();
    assertEquals(870.7344058905189, cp);
  }
}
