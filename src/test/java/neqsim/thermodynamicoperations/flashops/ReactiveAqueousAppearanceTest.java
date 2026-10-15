package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReaction;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.reactiveflash.FormulaMatrix;

/**
 * Exact-zero ionic products do not exempt a molecular endpoint from reactive aqueous stability.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ReactiveAqueousAppearanceTest extends neqsim.NeqSimTest {
  /** A configured reaction with a known feasible Gibbs-decreasing aqueous trial cannot be labelled stable. */
  @Test
  void reactionInducedAqueousInstabilityIsNotAcceptedAsStableGas() {
    SystemInterface fluid = neutralFeed(70.0);
    double[] before = FluidEquilibriumChecks.inventory(fluid);
    int phases = fluid.getNumberOfPhases();
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> new ThermodynamicOperations(fluid).TPflash());
    assertTrue(failure.getMessage().contains("reactive aqueous stability"));
    assertArrayEquals(before, FluidEquilibriumChecks.inventory(fluid), 0.0);
    assertEquals(phases, fluid.getNumberOfPhases());
    assertFalse(FlashPhaseConstraints.hasIonicInventory(fluid));
  }

  /** The independently stable original reaction configuration retains its neutral gas endpoint. */
  @Test
  void ordinaryZeroIonGasRetainsQualifiedNeutralEndpoint() {
    SystemInterface fluid = neutralFeed(0.0);
    double[] before = FluidEquilibriumChecks.inventory(fluid);
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(1, fluid.getNumberOfPhases());
    assertEquals(PhaseType.GAS, fluid.getPhase(0).getType());
    assertFalse(FlashPhaseConstraints.hasIonicInventory(fluid));
    assertArrayEquals(before, FluidEquilibriumChecks.inventory(fluid), 0.0);
    FluidEquilibriumChecks.validate(fluid, before);
  }

  /** A finite conservative split independently demonstrates the Gibbs-decreasing state behind the rejection. */
  @Test
  void finiteAqueousWitnessHasLowerGibbsWithBalancedElementsAndCharge() {
    for (double shift : new double[] {0.0, 70.0}) {
      SystemInterface feed = neutralFeed(shift);
      SystemInterface gas = feed.clone();
      gas.isChemicalSystem(false);
      new ThermodynamicOperations(gas).TPflash();
      assertEquals(1, gas.getNumberOfPhases());
      assertEquals(PhaseType.GAS, gas.getPhase(0).getType());
      double[] original = FluidEquilibriumChecks.inventory(gas);
      int water = gas.getComponent("water").getComponentNumber();
      int methane = gas.getComponent("methane").getComponentNumber();
      int hydroxide = gas.getComponent("OH-").getComponentNumber();
      int hydronium = gas.getComponent("H3O+").getComponentNumber();
      double[] liquidAmounts = new double[original.length];
      liquidAmounts[water] = 1.0;
      liquidAmounts[methane] = 1.0e-6;
      liquidAmounts[hydroxide] = 0.1;
      liquidAmounts[hydronium] = 0.1;
      double splitAmount = 1.0e-6;
      double extent = splitAmount * 0.1;
      double[] remaining = original.clone();
      // Each trial consumes two additional water molecules per H3O+/OH- pair formed.
      remaining[water] -= splitAmount * 1.2;
      remaining[methane] -= splitAmount * 1.0e-6;
      SystemInterface liquid = fixedPhase(feed, liquidAmounts, PhaseType.AQUEOUS);
      SystemInterface reservoir = fixedPhase(feed, remaining, PhaseType.GAS);
      assertStablePressureRoot(gas.getPhase(0));
      assertStablePressureRoot(liquid.getPhase(0));
      assertStablePressureRoot(reservoir.getPhase(0));
      double[] combined = remaining.clone();
      for (int i = 0; i < combined.length; i++) {
        combined[i] += splitAmount * liquidAmounts[i];
      }
      FormulaMatrix formula = new FormulaMatrix(feed);
      assertArrayEquals(formula.computeElementVector(original), formula.computeElementVector(combined), 1.0e-15);
      assertEquals(0.0, liquidAmounts[hydronium] - liquidAmounts[hydroxide], 0.0);
      ChemicalReaction reaction = feed.getChemicalReactionOperations().getReactionList().getChemicalReactionList()
          .get(0);
      assertEquals("waterreac", reaction.getName());
      PhaseInterface aqueous = liquid.getPhase(0);
      double logReferenceQuotient = aqueous.getLogInfiniteDiluteFugacity(hydroxide, water)
          + aqueous.getLogInfiniteDiluteFugacity(hydronium, water) - 2.0 * aqueous.getLogPureComponentFugacity(water);
      // Chemical standard potentials satisfy sum(nu_i * mu_i^0 / RT) = -ln(K).
      // Apply the standard-state correction once to the finite net extent; no boundary solver duals,
      // minimizer, numerical differentiation or state returned by its search is used here.
      double change = fugacityGibbs(reservoir.getPhase(0), remaining)
          + splitAmount * fugacityGibbs(aqueous, liquidAmounts) - fugacityGibbs(gas.getPhase(0), original)
          - extent * (Math.log(reaction.getK(aqueous)) + logReferenceQuotient);
      assertTrue(Double.isFinite(change));
      if (shift == 0.0) {
        assertTrue(change > 1.0e-8, "the original reaction does not favor this aqueous witness");
      } else {
        assertTrue(change < -1.0e-8, "the configured reaction has a finite Gibbs-decreasing aqueous split");
      }
    }
  }

  /**
   * Evaluates an owned fixed-composition phase without relaxing its chemical inventory.
   *
   * @param source source model and registered reaction configuration
   * @param amounts phase species amounts in moles
   * @param type required fluid root
   * @return initialized one-phase trial
   */
  private static SystemInterface fixedPhase(SystemInterface source, double[] amounts, PhaseType type) {
    SystemInterface result = source.clone();
    result.isChemicalSystem(false);
    result.setMolarFlowRates(amounts);
    result.setNumberOfPhases(1);
    result.setPhaseType(0, type);
    result.setBeta(0, 1.0);
    double total = 0.0;
    for (double amount : amounts) {
      assertTrue(Double.isFinite(amount) && amount >= 0.0);
      total += amount;
    }
    for (int i = 0; i < amounts.length; i++) {
      result.getPhase(0).getComponent(i).setx(amounts[i] / total);
      result.getPhase(0).getComponent(i).setNumberOfMolesInPhase(amounts[i]);
    }
    result.init(3);
    return result;
  }

  /**
   * Checks actual EOS pressure and mechanical stability independently of the boundary's acceptance decision.
   *
   * @param phase initialized EOS phase
   */
  private static void assertStablePressureRoot(PhaseInterface phase) {
    PhaseEosInterface eos = (PhaseEosInterface) phase;
    assertEquals(10.0, eos.calcPressure(), 2.0e-6);
    assertTrue(Double.isFinite(eos.calcPressuredV()) && eos.calcPressuredV() < 0.0);
  }

  /**
   * Evaluates the fugacity part of dimensionless extensive Gibbs energy at the shared pressure.
   *
   * @param phase initialized phase
   * @param amounts phase species amounts in moles
   * @return sum of amount times log mole-fraction fugacity coefficient product
   */
  private static double fugacityGibbs(PhaseInterface phase, double[] amounts) {
    double total = 0.0;
    for (double amount : amounts) {
      total += amount;
    }
    double value = 0.0;
    for (int i = 0; i < amounts.length; i++) {
      if (amounts[i] > 0.0) {
        value += amounts[i] * (Math.log(amounts[i] / total) + phase.getComponent(i).getLogFugacityCoefficient());
      }
    }
    return value;
  }

  /**
   * Creates the independent negative-TPD stress fixture without introducing any ionic inventory.
   *
   * @param coefficientShift configured offset in the water reaction log-equilibrium constant
   * @return neutral registered reactive feed
   */
  private static SystemInterface neutralFeed(double coefficientShift) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(320.0, 10.0);
    fluid.addComponent("water", 1.0e-4);
    fluid.addComponent("methane", 0.1);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.chemicalReactionInit();
    fluid.setMixingRule(10);
    for (ChemicalReaction reaction : fluid.getChemicalReactionOperations().getReactionList()
        .getChemicalReactionList()) {
      reaction.setK(0, reaction.getK()[0] + coefficientShift);
    }
    double[] neutral = new double[fluid.getNumberOfComponents()];
    neutral[fluid.getComponent("water").getComponentNumber()] = 1.0e-4;
    neutral[fluid.getComponent("methane").getComponentNumber()] = 0.1;
    fluid.setMolarFlowRates(neutral);
    fluid.getChemicalReactionOperations().reinitialize();
    return fluid;
  }
}
