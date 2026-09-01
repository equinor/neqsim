package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
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

  @Test
  void deserializedProcessReusesMultiphaseMixerWithParallelExecution() {
    SystemThermo gasFluid = new SystemSrkCPAstatoil(278.45, 37.21325);
    gasFluid.addComponent("methane", 5.0);
    gasFluid.addComponent("water", 0.11833608283886514);
    gasFluid.addComponent("MEG", 0.0);
    gasFluid.setMixingRule(10);
    gasFluid.setMultiPhaseCheck(true);

    SystemThermo megFluid = gasFluid.clone();
    megFluid.setMolarComposition(new double[] { 0.0, 0.1099744114900417, 0.8900255885099583 });

    Stream gasStream = new Stream("gas", gasFluid);
    gasStream.setFlowRate(168958.0, "Sm3/hr");
    gasStream.setTemperature(29.0, "C");
    gasStream.setPressure(74.1, "barg");

    Stream megStream = new Stream("MEG", megFluid);
    megStream.setFlowRate(0.01, "kg/hr");
    megStream.setTemperature(29.0, "C");
    megStream.setPressure(74.1, "barg");

    StaticMixer mixer = new StaticMixer("reused multiphase mixer");
    mixer.addStream(megStream);
    mixer.addStream(gasStream);

    ProcessSystem process = new ProcessSystem("serialized mixer process");
    process.add(gasStream);
    process.add(megStream);
    process.add(mixer);
    process.runOptimized();

    gasFluid.setTotalNumberOfMolesRaw(1986.7470206432324);
    ProcessSystem restored = process.copy();
    Stream restoredGasStream = (Stream) restored.getUnit("gas");
    Stream restoredMegStream = (Stream) restored.getUnit("MEG");
    StaticMixer restoredMixer = (StaticMixer) restored.getUnit("reused multiphase mixer");

    SystemThermo restoredGasFluid = (SystemThermo) restoredGasStream.getFluid();
    double restoredGasInventory = sumComponentMoles(restoredGasFluid);
    assertEquals(restoredGasInventory, restoredGasFluid.getTotalNumberOfMoles(), restoredGasInventory * 1.0e-12);

    restoredMegStream.setFlowRate(0.1, "kg/hr");
    restored.runOptimized();

    SystemThermo outletFluid = (SystemThermo) restoredMixer.getOutletStream().getFluid();
    double inletMassFlow = restoredGasStream.getFlowRate("kg/hr") + restoredMegStream.getFlowRate("kg/hr");
    double outletMassFlow = restoredMixer.getOutletStream().getFlowRate("kg/hr");
    double outletInventory = sumComponentMoles(outletFluid);

    assertTrue(outletFluid.hasPhaseType("gas"));
    assertEquals(inletMassFlow, outletMassFlow, inletMassFlow * 1.0e-8);
    assertEquals(outletInventory, outletFluid.getTotalNumberOfMoles(), outletInventory * 1.0e-12);
  }

  private static double sumComponentMoles(SystemThermo fluid) {
    double sum = 0.0;
    for (int componentIndex = 0; componentIndex < fluid.getPhase(0).getNumberOfComponents(); componentIndex++) {
      sum += fluid.getPhase(0).getComponent(componentIndex).getNumberOfmoles();
    }
    return sum;
  }

  private static double sumOverallMoleFractions(SystemThermo fluid) {
    double sum = 0.0;
    for (int componentIndex = 0; componentIndex < fluid.getPhase(0).getNumberOfComponents(); componentIndex++) {
      sum += fluid.getPhase(0).getComponent(componentIndex).getz();
    }
    return sum;
  }

  private static SystemThermo roundTrip(SystemThermo fluid) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(fluid);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (SystemThermo) input.readObject();
    }
  }
}
