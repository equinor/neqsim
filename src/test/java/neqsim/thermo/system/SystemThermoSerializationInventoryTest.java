package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for total-mole inventory reconciliation during Java deserialization.
 */
class SystemThermoSerializationInventoryTest extends neqsim.NeqSimTest {
  @Test
  void deserializationReconcilesStaleTotalBeforeFlash() throws Exception {
    SystemThermo fluid = new SystemSrkCPAstatoil(278.45, 37.21325);
    fluid.addComponent("methane", 5.0);
    fluid.addComponent("water", 0.11833608283886514);
    fluid.addComponent("MEG", 0.2779633604538873);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    double expectedInventory = sumComponentMoles(fluid);
    fluid.setTotalNumberOfMolesRaw(1986.7470206432324);

    SystemThermo restored = roundTrip(fluid);

    assertEquals(expectedInventory, restored.getTotalNumberOfMoles(), 1.0e-12);

    new ThermodynamicOperations(restored).TPflash();

    assertEquals(1.0, sumOverallMoleFractions(restored), 1.0e-12);
    assertTrue(restored.getNumberOfPhases() >= 2);
    assertEquals(PhaseType.GAS, restored.getPhase("gas").getType());
    assertEquals(PhaseType.AQUEOUS, restored.getPhase("aqueous").getType());
  }

  @Test
  void deserializationPreservesConsistentTotal() throws Exception {
    SystemThermo fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 2.0);
    fluid.addComponent("ethane", 1.0);
    double expectedInventory = fluid.getTotalNumberOfMoles();

    SystemThermo restored = roundTrip(fluid);

    assertEquals(expectedInventory, restored.getTotalNumberOfMoles(), 1.0e-12);
    assertEquals(expectedInventory, sumComponentMoles(restored), 1.0e-12);
  }

  private static double sumComponentMoles(SystemThermo fluid) {
    double sum = 0.0;
    for (int componentIndex = 0;
        componentIndex < fluid.getPhase(0).getNumberOfComponents();
        componentIndex++) {
      sum += fluid.getPhase(0).getComponent(componentIndex).getNumberOfmoles();
    }
    return sum;
  }

  private static double sumOverallMoleFractions(SystemThermo fluid) {
    double sum = 0.0;
    for (int componentIndex = 0;
        componentIndex < fluid.getPhase(0).getNumberOfComponents();
        componentIndex++) {
      sum += fluid.getPhase(0).getComponent(componentIndex).getz();
    }
    return sum;
  }

  private static SystemThermo roundTrip(SystemThermo fluid) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(fluid);
    }
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (SystemThermo) input.readObject();
    }
  }
}
