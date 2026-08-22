package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.chemicalreactions.ChemicalReactionOperations;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;

/** Regression test for reaction topology after the system component set changes. */
class SystemThermoChemicalReactionStaleStateTest extends neqsim.NeqSimTest {
  @Test
  void componentIdentityChangeRequiresReactionReinitialization() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    fluid.addComponent("water", 1.0);
    fluid.setPhaseType(0, PhaseType.AQUEOUS);
    fluid.chemicalReactionInit();
    ChemicalReactionOperations waterOnlyOperations = fluid.getChemicalReactionOperations();

    fluid.addComponent("CO2", 0.001);

    IllegalStateException accessFailure = assertThrows(IllegalStateException.class,
        fluid::getChemicalReactionOperations);
    assertTrue(accessFailure.getMessage().contains("chemicalReactionInit()"));
    assertTrue(accessFailure.getMessage().contains("createDatabase(true)"));
    assertThrows(IllegalStateException.class, fluid::isChemicalSystem);
    assertThrows(IllegalStateException.class, () -> waterOnlyOperations.solveChemEq(0, 1),
        "A retained operations reference must fail before using stale topology");
    assertThrows(IllegalStateException.class, waterOnlyOperations::getReactionLogResiduals);
    SystemInterface refreshedClone = fluid.clone();
    assertNotNull(refreshedClone);
    assertTrue(refreshedClone.isChemicalSystem());
    assertTrue(refreshedClone.getPhase(0).hasComponent("HCO3-"));
    assertThrows(IllegalStateException.class, fluid::isChemicalSystem,
        "Cloning must not silently clear stale state on the source");

    fluid.chemicalReactionInit();
    ChemicalReactionOperations refreshedOperations = fluid.getChemicalReactionOperations();
    assertNotSame(waterOnlyOperations, refreshedOperations);
    assertTrue(fluid.isChemicalSystem());
    assertTrue(fluid.getPhase(0).hasComponent("HCO3-"));
    assertTrue(fluid.getPhase(0).hasComponent("CO3--"));

    fluid.createDatabase(true);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(false);
    fluid.setMaxNumberOfPhases(1);
    fluid.setNumberOfPhases(1);
    fluid.setPhaseType(0, PhaseType.AQUEOUS);
    fluid.init(0);
    refreshedOperations.solveChemEq(0, 0);
    refreshedOperations.solveChemEq(0, 1);
    fluid.init(1);

    assertFalse(refreshedOperations.getReactionLogResiduals().isEmpty());
    double carbonMoles = 0.0;
    double chargeMoles = 0.0;
    for (int component = 0; component < fluid.getPhase(0).getNumberOfComponents(); component++) {
      ComponentInterface species = fluid.getPhase(0).getComponent(component);
      if ("CO2".equals(species.getComponentName()) || "HCO3-".equals(species.getComponentName())
          || "CO3--".equals(species.getComponentName())) {
        carbonMoles += species.getNumberOfMolesInPhase();
      }
      chargeMoles += species.getNumberOfMolesInPhase() * species.getIonicCharge();
    }
    assertEquals(0.001, carbonMoles, 1.0e-9);
    assertEquals(0.0, chargeMoles, 1.0e-9);
  }

  @Test
  void changingOnlyExistingComponentAmountKeepsReactionStateCurrent() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    fluid.addComponent("CO2", 0.001);
    fluid.addComponent("water", 1.0);
    fluid.chemicalReactionInit();
    ChemicalReactionOperations operations = fluid.getChemicalReactionOperations();

    fluid.addComponent("CO2", 0.0001);
    fluid.addComponent("CO2", 0.0001, 0);

    assertSame(operations, fluid.getChemicalReactionOperations());
    assertTrue(fluid.isChemicalSystem());
  }

  @Test
  void geElectrolyteModelUsesTheSameTopologyGuard() {
    SystemInterface fluid = new SystemPitzer(298.15, 10.0);
    fluid.addComponent("water", 55.508);
    fluid.chemicalReactionInit();

    fluid.addComponent("CO2", 0.001);

    assertThrows(IllegalStateException.class, fluid::getChemicalReactionOperations);
    fluid.chemicalReactionInit();
    assertTrue(fluid.isChemicalSystem());
    assertTrue(fluid.getPhase(0).hasComponent("HCO3-"));
  }

  @Test
  void phaseSpecificAdditionAndIdentityRemovalAlsoMarkReactionStateStale() {
    SystemInterface addition = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    addition.addComponent("water", 1.0);
    addition.chemicalReactionInit();
    addition.addComponent("CO2", 0.001, 0);
    assertThrows(IllegalStateException.class, addition::getChemicalReactionOperations);

    SystemInterface removal = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    removal.addComponent("CO2", 0.001);
    removal.addComponent("water", 1.0);
    removal.chemicalReactionInit();
    removal.removeComponent("CO2");
    assertThrows(IllegalStateException.class, removal::isChemicalSystem);

    SystemInterface rename = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    rename.addComponent("CO2", 0.001);
    rename.addComponent("water", 1.0);
    rename.chemicalReactionInit();
    rename.changeComponentName("CO2", "renamed CO2");
    assertThrows(IllegalStateException.class, rename::getChemicalReactionOperations);
  }

  @Test
  void clearAllRemovesChemicalReactionState() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    fluid.addComponent("water", 1.0);
    fluid.chemicalReactionInit();

    fluid.clearAll();

    assertFalse(fluid.isChemicalSystem());
    assertNull(fluid.getChemicalReactionOperations());
  }

  @Test
  void staleReactionStateSurvivesSerialization() throws Exception {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    fluid.addComponent("water", 1.0);
    fluid.chemicalReactionInit();
    fluid.addComponent("CO2", 0.001);

    SystemInterface restored = roundTrip(fluid);

    assertThrows(IllegalStateException.class, restored::getChemicalReactionOperations);
    assertThrows(IllegalStateException.class, restored::isChemicalSystem);
  }

  private SystemInterface roundTrip(SystemInterface fluid) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(fluid);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (SystemInterface) input.readObject();
    }
  }
}
