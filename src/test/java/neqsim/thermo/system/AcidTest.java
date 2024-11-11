package neqsim.thermo.system;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class AcidTest extends neqsim.NeqSimTest {

  /**
   * <p>
   * testAcid.
   * </p>
   */
  @Test
  @DisplayName("test equilibrium of formic acid")
  public void testAcid() {
    neqsim.thermo.system.SystemSrkCPAstatoil testSystem =
        new neqsim.thermo.system.SystemSrkCPAstatoil(298.0, 10.0);
    testSystem.addComponent("methane", 1.0, "kg/sec");
    testSystem.addComponent("formic acid", 25.0, "kg/sec");
    testSystem.addComponent("water", 100.0, "kg/sec");
    testSystem.setMixingRule(10);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.prettyPrint();
  }

  /**
   * <p>
   * testtestBubpAcid.
   * </p>
   */
  @Test
  @DisplayName("test bublepoint of formic acid")
  public void testtestBubpAcid() {
    neqsim.thermo.system.SystemSrkCPAstatoil testSystem =
        new neqsim.thermo.system.SystemSrkCPAstatoil(398.0, 1.01325);
    testSystem.addComponent("formic acid", 25.0, "kg/sec");
    testSystem.setMixingRule(10);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.bubblePointTemperatureFlash();
    } catch (Exception e) {
      e.printStackTrace();
    }
    testSystem.prettyPrint();
  }
}
