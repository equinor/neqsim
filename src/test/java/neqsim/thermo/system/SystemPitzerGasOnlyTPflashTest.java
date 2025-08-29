package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression test ensuring gas-only systems flash without creating aqueous phases.
 */
public class SystemPitzerGasOnlyTPflashTest extends neqsim.NeqSimTest {
  @Test
  public void testGasOnlyTPflash() {
    SystemInterface system = new SystemPitzer(323.15, 10.0);
    system.addComponent("methane", 1.0);
    system.addComponent("water", 1e-6);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());
    assertEquals(1, system.getNumberOfPhases());
    assertEquals(neqsim.thermo.phase.PhaseType.GAS, system.getPhase(0).getType());
  }
}

