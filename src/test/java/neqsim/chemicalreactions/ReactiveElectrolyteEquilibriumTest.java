package neqsim.chemicalreactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Coupled reaction, charge and material-balance gates for electrolyte EOS and GE flashes. */
class ReactiveElectrolyteEquilibriumTest extends neqsim.NeqSimTest {
  private static final double ELEMENT_RELATIVE_TOLERANCE = 1.0e-7;

  /** Both electrolyte model families retain a certified reactive state across repeated and changed flashes. */
  @Test
  void carbonateFlashClosesForElectrolyteEosAndGe() throws Exception {
    assertReactiveFlashContract(createPitzerSystem());
    assertReactiveFlashContract(createElectrolyteCpaSystem());
  }

  private static void assertReactiveFlashContract(SystemInterface system) throws Exception {
    Map<String, Double> feedElements = elementInventoryFromOverallMoles(system);
    ThermodynamicOperations operations = new ThermodynamicOperations(system);

    runModelSelectedReactiveFlash(operations);
    assertEquilibriumState(system, feedElements);

    runModelSelectedReactiveFlash(operations);
    assertEquilibriumState(system, feedElements);

    system.setPressure(60.0);
    runModelSelectedReactiveFlash(operations);
    assertEquilibriumState(system, feedElements);

    SystemInterface clonedSystem = system.clone();
    runModelSelectedReactiveFlash(new ThermodynamicOperations(clonedSystem));
    assertEquilibriumState(clonedSystem, feedElements);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(system);
    }
    SystemInterface restoredSystem;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restoredSystem = (SystemInterface) input.readObject();
    }
    runModelSelectedReactiveFlash(new ThermodynamicOperations(restoredSystem));
    assertEquilibriumState(restoredSystem, feedElements);
  }

  private static void runModelSelectedReactiveFlash(ThermodynamicOperations operations) {
    operations.TPflash();
  }

  private static void assertEquilibriumState(SystemInterface system, Map<String, Double> feedElements) {
    ChemicalReactionOperations reactions = system.getChemicalReactionOperations();
    Map<String, Double> reactionResiduals = reactions.getReactionLogResiduals();
    assertFalse(reactionResiduals.isEmpty());
    for (Map.Entry<String, Double> residual : reactionResiduals.entrySet()) {
      assertTrue(Double.isFinite(residual.getValue()), residual.getKey());
    }
    assertTrue(reactions.getMaximumAbsoluteReactionLogResidual() <= 2.0e-6,
        "Maximum absolute ln(Q/K) was " + reactions.getMaximumAbsoluteReactionLogResidual());
    assertTrue(Math.abs(reactions.getReactivePhaseChargeMoles()) <= 1.0e-8,
        "Reactive phase charge was " + reactions.getReactivePhaseChargeMoles() + " mol");
    assertTrue(reactions.getMaximumAbsoluteElementBalanceResidual() <= 1.0e-8,
        "Maximum element residual was " + reactions.getMaximumAbsoluteElementBalanceResidual());

    double betaSum = 0.0;
    double phaseMoleSum = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = system.getPhase(phaseIndex);
      betaSum += system.getBeta(phaseIndex);
      phaseMoleSum += phase.getNumberOfMolesInPhase();
      assertTrue(system.getBeta(phaseIndex) >= 0.0);
      double compositionSum = 0.0;
      for (int componentIndex = 0; componentIndex < phase.getNumberOfComponents(); componentIndex++) {
        double moleFraction = phase.getComponent(componentIndex).getx();
        assertTrue(Double.isFinite(moleFraction));
        assertTrue(moleFraction >= 0.0);
        compositionSum += moleFraction;
      }
      assertEquals(1.0, compositionSum, 1.0e-10);
    }
    assertEquals(1.0, betaSum, 1.0e-10, system.getModelName());
    assertEquals(system.getTotalNumberOfMoles(), phaseMoleSum,
        Math.max(1.0e-8, Math.abs(system.getTotalNumberOfMoles()) * 1.0e-8));

    Map<String, Double> equilibriumElements = elementInventoryFromPhases(system);
    for (Map.Entry<String, Double> feedElement : feedElements.entrySet()) {
      double expected = feedElement.getValue();
      double actual = equilibriumElements.get(feedElement.getKey());
      assertEquals(expected, actual, Math.max(1.0, Math.abs(expected)) * ELEMENT_RELATIVE_TOLERANCE,
          "Conserved inventory mismatch for element " + feedElement.getKey());
    }
  }

  private static SystemInterface createPitzerSystem() {
    SystemPitzer system = new SystemPitzer(313.15, 50.0);
    addCarbonateFeed(system);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    return system;
  }

  private static SystemInterface createElectrolyteCpaSystem() {
    SystemInterface system = new SystemElectrolyteCPAstatoil(303.15, 14.0);
    system.addComponent("water", 55.508);
    system.addComponent("CO2", 0.1);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(false);
    return system;
  }

  private static void addCarbonateFeed(SystemInterface system) {
    system.addComponent("methane", 5.0);
    system.addComponent("n-heptane", 2.0);
    system.addComponent("water", 55.508);
    system.addComponent("CO2", 0.1);
  }

  private static Map<String, Double> elementInventoryFromOverallMoles(SystemInterface system) {
    Map<String, Double> inventory = new LinkedHashMap<String, Double>();
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = system.getPhase(0).getComponent(componentIndex);
      addElements(inventory, component, component.getNumberOfmoles());
    }
    return inventory;
  }

  private static Map<String, Double> elementInventoryFromPhases(SystemInterface system) {
    Map<String, Double> inventory = new LinkedHashMap<String, Double>();
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = system.getPhase(phaseIndex);
      for (int componentIndex = 0; componentIndex < phase.getNumberOfComponents(); componentIndex++) {
        ComponentInterface component = phase.getComponent(componentIndex);
        addElements(inventory, component, component.getNumberOfMolesInPhase());
      }
    }
    return inventory;
  }

  private static void addElements(Map<String, Double> inventory, ComponentInterface component, double moles) {
    String[] elementNames = component.getElements().getElementNames();
    double[] elementCoefficients = component.getElements().getElementCoefs();
    for (int elementIndex = 0; elementIndex < elementNames.length; elementIndex++) {
      Double current = inventory.get(elementNames[elementIndex]);
      inventory.put(elementNames[elementIndex],
          (current == null ? 0.0 : current) + moles * elementCoefficients[elementIndex]);
    }
  }
}
