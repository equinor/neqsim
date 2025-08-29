package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SystemPitzerPrettyPrintTest extends neqsim.NeqSimTest {
  @Test
  public void testPrettyPrintTwoPhase() {
    SystemInterface system = new SystemPitzer(298.15, 10.0);
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
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.TPflash();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    String[][] table = system.createTable("test");
    assertEquals(Set.of("GAS", "AQUEOUS"), Set.of(table[0][2], table[0][3]));
    int compRows = system.getPhase(0).getNumberOfComponents();
    Set<String> names = new HashSet<>();
    for (int j = 1; j <= compRows; j++) {
      names.add(table[j][0]);
    }
    assertTrue(names.contains("methane"));
    assertTrue(names.contains("water"));
    assertTrue(names.contains("Na+"));
    assertTrue(names.contains("Cl-"));
    int densityRow = compRows + 2;
    assertFalse(table[densityRow][2].isEmpty());
    assertFalse(table[densityRow][3].isEmpty());
  }
}
