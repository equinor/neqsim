package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test TP flash for methane-water-NaCl system using Pitzer model.
 */
public class SystemPitzerTPflashTest extends neqsim.NeqSimTest {
  SystemInterface system;
  ThermodynamicOperations ops;

  @BeforeEach
  public void setUp() {
    system = new SystemPitzer(298.15, 10.0);
    system.addComponent("methane", 5.0, 0);
    system.addComponent("water", 55.5, 1);
    system.addComponent("Na+", 1.0, 1);
    system.addComponent("Cl-", 1.0, 1);
    system.setMultiPhaseCheck(true);
    system.setMixingRule("classic");
    PhasePitzer liq = (PhasePitzer) system.getPhase(1);
    int na = liq.getComponent("Na+").getComponentNumber();
    int cl = liq.getComponent("Cl-").getComponentNumber();
    liq.setBinaryParameters(na, cl, 0.0765, 0.2664, 0.00127);
    system.init(0);
    ops = new ThermodynamicOperations(system);
  }

  @Test
  public void testTPflash() {
    try {
      ops.TPflash();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    assertEquals(2, system.getNumberOfPhases());
    assertEquals(neqsim.thermo.phase.PhaseType.AQUEOUS, system.getPhase(1).getType());
    assertEquals(neqsim.thermo.phase.PhaseType.GAS, system.getPhase(0).getType());
  }
}
