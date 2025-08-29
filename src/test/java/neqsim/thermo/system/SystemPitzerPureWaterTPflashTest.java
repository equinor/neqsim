package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SystemPitzerPureWaterTPflashTest extends neqsim.NeqSimTest {
  @Test
  public void testPureWaterTPflash() {
    SystemInterface system = new SystemPitzer(298.15, 1.0);
    system.addComponent("water", 55.5);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());
    assertEquals(1, system.getNumberOfPhases());
  }
}
