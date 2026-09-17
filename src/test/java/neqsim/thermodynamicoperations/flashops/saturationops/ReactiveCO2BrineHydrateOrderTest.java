package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.ReactiveCO2BrinePhaseEquilibrium;
import neqsim.thermodynamicoperations.flashops.reactiveflash.FormulaMatrix;

/** Reactive hydrate permutation regressions for issue 3758. */
class ReactiveCO2BrineHydrateOrderTest {
  static SystemInterface brine(int order, double pressure, double saltScale, boolean reactive) {
    String[] names = { "CO2", "water", "Na+", "K+", "Cl-" };
    double sodium = saltScale * (3.0 / 95.0) / 0.05844277;
    double potassium = saltScale * (2.0 / 95.0) / 0.0745513;
    double[] amounts = { 10.0, 1.0 / 0.01801528, sodium, potassium, sodium + potassium };
    int[][] orders = { { 0, 1, 2, 3, 4 }, { 2, 3, 4, 0, 1 }, { 1, 0, 2, 3, 4 }, { 4, 3, 2, 1, 0 } };
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(283.15, pressure);
    for (int component : orders[order]) {
      fluid.addComponent(names[component], amounts[component]);
    }
    if (reactive) {
      fluid.chemicalReactionInit();
    }
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.setHydrateCheck(true);
    return fluid;
  }

  @Test
  void co2FirstReactiveCaseCompletesWithChemicalEquilibrium() {
    assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
      SystemInterface fluid = brine(0, 50.0, 1.0, true);
      solveAndVerify(fluid);
      assertEquals(280.65013, fluid.getTemperature(), 2.0e-4);
    });
  }

  @ParameterizedTest
  @ValueSource(ints = { 1, 2, 3 })
  void otherOrdersHaveTheSameQualifiedEndpoint(int order) {
    assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
      SystemInterface fluid = brine(order, 50.0, 1.0, true);
      solveAndVerify(fluid);
      assertEquals(280.65013, fluid.getTemperature(), 2.0e-4);
    });
  }

  @ParameterizedTest
  @CsvSource({ "40,0.9", "60,1.1" })
  void adjacentStatesPreservePermutationInvariance(double pressure, double saltScale) {
    assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
      SystemInterface first = brine(0, pressure, saltScale, true);
      SystemInterface second = brine(1, pressure, saltScale, true);
      solveAndVerify(first);
      solveAndVerify(second);
      assertEquivalent(first, second);
    });
  }

  @Test
  void repeatedAndClonedCalculationsPreserveSpeciationAndInventory() {
    assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
      SystemInterface fluid = brine(0, 50.0, 1.0, true);
      fluid.setMultiPhaseCheck(false);
      solveAndVerify(fluid);
      assertFalse(fluid.doMultiPhaseCheck());
      SystemInterface original = fluid.clone();
      SystemInterface copy = fluid.clone();
      solveAndVerify(copy);
      solveAndVerify(fluid);
      assertEquivalent(original, fluid);
      assertEquivalent(original, copy);
      assertFalse(fluid.doMultiPhaseCheck());
    });
  }

  @Test
  void nonReactiveControlsRemainUnchanged() {
    assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
      SystemInterface first = brine(0, 50.0, 1.0, false);
      SystemInterface second = brine(1, 50.0, 1.0, false);
      new ThermodynamicOperations(first).hydrateFormationTemperature();
      new ThermodynamicOperations(second).hydrateFormationTemperature();
      assertEquals(280.6780331, first.getTemperature(), 2.0e-4);
      assertEquivalent(first, second);
    });
  }

  @Test
  void denseCo2FluidStateSurvivesReinitialization() {
    assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
      SystemInterface fluid = brine(0, 100.0, 1.0, true);
      new ReactiveCO2BrinePhaseEquilibrium(fluid).run();
      assertEquals(2, fluid.getNumberOfPhases());
      PhaseType firstType = fluid.getPhase(0).getType();
      double firstBeta = fluid.getBeta(0);
      fluid.init(1);
      assertEquals(firstType, fluid.getPhase(0).getType());
      assertEquals(firstBeta, fluid.getBeta(0), 0.0);
      assertMolecularFugacitiesEqual(fluid);
      assertTrue(fluid.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 2.0e-6);
    });
  }

  @Test
  void rejectedAndInterruptedFluidSolvesDoNotMutateTheCaller() {
    SystemInterface fluid = brine(0, 50.0, 1.0, true);
    fluid.addComponent("Na+", 0.1);
    double[] before = overallMoles(fluid);
    double temperature = fluid.getTemperature();
    int phases = fluid.getNumberOfPhases();
    ReactiveCO2BrinePhaseEquilibrium flash = new ReactiveCO2BrinePhaseEquilibrium(fluid);
    assertThrows(IllegalStateException.class, flash::run);
    assertArrayEquals(before, overallMoles(fluid), 0.0);
    assertEquals(temperature, fluid.getTemperature(), 0.0);
    assertEquals(phases, fluid.getNumberOfPhases());
    assertTrue(Double.isNaN(flash.getMinimumTrialDistance()));
    Thread.currentThread().interrupt();
    try {
      assertThrows(CancellationException.class, flash::run);
    } finally {
      Thread.interrupted();
    }
    assertArrayEquals(before, overallMoles(fluid), 0.0);
  }

  @Test
  void publicTemperatureOperationPropagatesCancellationAndRestoresInventory() {
    SystemInterface fluid = brine(0, 50.0, 1.0, true);
    fluid.setMultiPhaseCheck(false);
    double[] before = overallMoles(fluid);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    Thread.currentThread().interrupt();
    try {
      assertThrows(CancellationException.class, ops::hydrateFormationTemperature);
    } finally {
      Thread.interrupted();
    }
    assertArrayEquals(before, overallMoles(fluid), 1.0e-12);
    assertTrue(Double.isNaN(fluid.getTemperature()));
    assertFalse(fluid.doMultiPhaseCheck());
    assertFalse(((HydrateFormationTemperatureFlash) ops.getOperation()).isConverged());
  }

  private static void solveAndVerify(SystemInterface fluid) throws Exception {
    FormulaMatrix formula = new FormulaMatrix(fluid);
    double[] elements = formula.computeElementVector(overallMoles(fluid));
    String[] names = fluid.getComponentNames().clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateFormationTemperature();
    HydrateFormationTemperatureFlash operation = (HydrateFormationTemperatureFlash) ops.getOperation();
    assertTrue(operation.isConverged());
    assertTrue(fluid.isChemicalSystem());
    assertArrayEquals(names, fluid.getComponentNames());
    assertTrue(fluid.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 2.0e-6);
    assertTrue(fluid.getChemicalReactionOperations().getMaximumAbsoluteElementBalanceResidual() < 1.0e-8);
    assertEquals(0.0, fluid.getChemicalReactionOperations().getReactivePhaseChargeMoles(), 1.0e-8);
    assertEquals(0.0, 1.0 - fluid.getPhase(4).getFugacity("water") / fluid.getPhase("aqueous").getFugacity("water"),
        1.0e-6);
    assertEquals(2, fluid.getNumberOfPhases());
    double[] recovered = new double[fluid.getNumberOfComponents()];
    double betaSum = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      betaSum += fluid.getBeta(phase);
      double sum = 0.0;
      for (int component = 0; component < recovered.length; component++) {
        double x = fluid.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(x) && x >= 0.0 && x <= 1.0);
        sum += x;
        recovered[component] += fluid.getNumberOfMoles() * fluid.getBeta(phase) * x;
        if (fluid.getPhase(phase).getType() != PhaseType.AQUEOUS
            && fluid.getPhase(phase).getComponent(component).getIonicCharge() != 0.0) {
          assertTrue(x < 1.0e-40);
        }
      }
      assertEquals(1.0, sum, 1.0e-9);
    }
    assertEquals(1.0, betaSum, 1.0e-12);
    assertArrayEquals(elements, formula.computeElementVector(recovered), 2.0e-8);
    assertArrayEquals(overallMoles(fluid), recovered, 1.0e-9);
    // Later callers routinely reinitialize properties, so the accepted EOS roots must survive the copy.
    fluid.init(1);
    assertMolecularFugacitiesEqual(fluid);
  }

  private static void assertMolecularFugacitiesEqual(SystemInterface fluid) {
    for (String component : new String[] { "CO2", "water" }) {
      double ratio = fluid.getPhase(0).getFugacity(component) / fluid.getPhase(1).getFugacity(component);
      assertEquals(0.0, Math.log(ratio), 1.0e-8);
    }
  }

  private static void assertEquivalent(SystemInterface first, SystemInterface second) {
    assertEquals(first.getTemperature(), second.getTemperature(), 2.0e-4);
    assertEquals(first.getNumberOfPhases(), second.getNumberOfPhases());
    for (int phase = 0; phase < first.getNumberOfPhases(); phase++) {
      PhaseType type = first.getPhase(phase).getType();
      assertEquals(first.getBeta(phase), second.getPhase(type).getBeta(), 1.0e-7);
      for (String component : first.getComponentNames()) {
        assertEquals(first.getPhase(phase).getComponent(component).getx(),
            second.getPhase(type).getComponent(component).getx(), 1.0e-7, component);
      }
    }
  }

  private static double[] overallMoles(SystemInterface fluid) {
    double[] moles = new double[fluid.getNumberOfComponents()];
    for (int component = 0; component < moles.length; component++) {
      moles[component] = fluid.getPhase(0).getComponent(component).getNumberOfmoles();
    }
    return moles;
  }
}
