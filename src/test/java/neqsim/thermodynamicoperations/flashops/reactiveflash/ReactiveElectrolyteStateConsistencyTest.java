package neqsim.thermodynamicoperations.flashops.reactiveflash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/** Regression tests for extensive state consistency after a reactive electrolyte flash. */
class ReactiveElectrolyteStateConsistencyTest extends neqsim.NeqSimTest {
  private static final double ELEMENT_RELATIVE_TOLERANCE = 1.0e-8;

  /**
   * A ten-mole CO2/brine feed must retain its scale and conserved inventory when the modified RAND variables are copied
   * back to the public thermodynamic system.
   */
  @Test
  void co2BrineFlashReturnsConservedExtensiveState() {
    SystemInterface fluid = createFluid();
    Map<String, Double> feedElements = elementInventoryFromOverallMoles(fluid);

    ReactiveMultiphaseTPflash flash = new ReactiveMultiphaseTPflash(fluid);
    flash.run();

    assertTrue(flash.isConverged(), "Reactive flash should converge");
    assertTrue(flash.getFinalResidual() < 1.0e-5,
        "Chemical-potential and balance residual should be below the acceptance tolerance");
    assertTrue(flash.getFinalElementResidual() < 1.0e-8,
        "Normalized element-balance residual should be below the acceptance tolerance");
    assertEquals(flash.getEquilibriumTotalMoles(), fluid.getTotalNumberOfMoles(), 1.0e-12,
        "Solver and public system must report the same extensive total");

    double betaSum = 0.0;
    double phaseMoleSum = 0.0;
    double charge = 0.0;
    double[] componentMoles = new double[fluid.getPhase(0).getNumberOfComponents()];
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseInterface phaseState = fluid.getPhase(phase);
      betaSum += fluid.getBeta(phase);
      phaseMoleSum += phaseState.getNumberOfMolesInPhase();
      assertTrue(fluid.getBeta(phase) >= 0.0, "Phase fractions must be non-negative");
      double compositionSum = 0.0;
      for (int component = 0; component < phaseState.getNumberOfComponents(); component++) {
        ComponentInterface species = phaseState.getComponent(component);
        assertTrue(species.getx() >= 0.0, "Phase compositions must be non-negative");
        compositionSum += species.getx();
        componentMoles[component] += species.getNumberOfMolesInPhase();
        charge += species.getNumberOfMolesInPhase() * species.getIonicCharge();
        if ("gas".equalsIgnoreCase(phaseState.getPhaseTypeName()) && species.getIonicCharge() != 0) {
          assertTrue(species.getNumberOfMolesInPhase() < 1.0e-20, "Ions must remain excluded from the gas phase");
        }
      }
      assertEquals(1.0, compositionSum, 1.0e-12, "Every active phase must be normalized");
    }

    assertEquals(1.0, betaSum, 1.0e-12, "Active phase fractions must sum to one");
    assertEquals(fluid.getTotalNumberOfMoles(), phaseMoleSum, 1.0e-12,
        "Active phase moles must sum to the public system total");
    assertEquals(0.0, charge, 1.0e-9, "The returned multiphase state must be electroneutral");

    for (int component = 0; component < componentMoles.length; component++) {
      ComponentInterface species = fluid.getPhase(0).getComponent(component);
      assertEquals(componentMoles[component], species.getNumberOfmoles(), 1.0e-12,
          "Overall component moles must equal the phase sum for " + species.getComponentName());
      assertEquals(componentMoles[component] / fluid.getTotalNumberOfMoles(), species.getz(), 1.0e-12,
          "Overall composition must match the extensive phase state for " + species.getComponentName());
    }

    Map<String, Double> equilibriumElements = elementInventoryFromPhases(fluid);
    for (Map.Entry<String, Double> feedElement : feedElements.entrySet()) {
      double expected = feedElement.getValue().doubleValue();
      double actual = equilibriumElements.get(feedElement.getKey()).doubleValue();
      assertEquals(expected, actual, Math.max(1.0, Math.abs(expected)) * ELEMENT_RELATIVE_TOLERANCE,
          "Conserved inventory mismatch for element " + feedElement.getKey());
    }
  }

  private static SystemInterface createFluid() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(303.15, 14.0);
    fluid.addComponent("methane", 7.0);
    fluid.addComponent("CO2", 0.5);
    fluid.addComponent("water", 2.3);
    fluid.addComponent("Na+", 0.1);
    fluid.addComponent("Cl-", 0.1);
    fluid.chemicalReactionInit();
    fluid.createDatabase(true);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private static Map<String, Double> elementInventoryFromOverallMoles(SystemInterface fluid) {
    Map<String, Double> inventory = new LinkedHashMap<String, Double>();
    for (int component = 0; component < fluid.getPhase(0).getNumberOfComponents(); component++) {
      ComponentInterface species = fluid.getPhase(0).getComponent(component);
      addElements(inventory, species, species.getNumberOfmoles());
    }
    return inventory;
  }

  private static Map<String, Double> elementInventoryFromPhases(SystemInterface fluid) {
    Map<String, Double> inventory = new LinkedHashMap<String, Double>();
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseInterface phaseState = fluid.getPhase(phase);
      for (int component = 0; component < phaseState.getNumberOfComponents(); component++) {
        ComponentInterface species = phaseState.getComponent(component);
        addElements(inventory, species, species.getNumberOfMolesInPhase());
      }
    }
    return inventory;
  }

  private static void addElements(Map<String, Double> inventory, ComponentInterface species, double moles) {
    String[] names = species.getElements().getElementNames();
    double[] coefficients = species.getElements().getElementCoefs();
    for (int element = 0; element < names.length; element++) {
      Double current = inventory.get(names[element]);
      inventory.put(names[element], (current == null ? 0.0 : current.doubleValue()) + moles * coefficients[element]);
    }
  }
}
