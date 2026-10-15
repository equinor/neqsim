package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import neqsim.thermo.mixingrule.ElectrolyteMixingRulesInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.EosGeFlashModel;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;

/**
 * Independent balance, derivative and electrochemical-stationarity checks for distributed ions.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectroneutralLiquidPartitionTest {
  /**
   * Builds a synthetic legacy-parameter K15 stress endpoint and selects the pressure-valid gas root for its CO2-rich
   * phase.
   *
   * @return fixed three-fluid state with one ionic host
   */
  private SystemInterface k15() {
    return k15(new String[] {"CO2", "water", "K+", "Cl-"});
  }

  /**
   * Creates the same conserved fixed-topology seed with a chosen species registration order.
   *
   * @param order component names in registration order
   * @return initialized single-host endpoint with explicit legacy parameters
   */
  private SystemInterface k15(String[] order) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(282.814777132, 29.01);
    double salt = 14.97 / (100.0 - 14.97) / 0.0745513;
    for (String name : order) {
      fluid.addComponent(name, name.equals("CO2") ? 10.0 : name.equals("water") ? 1.0 / 0.01801528 : salt);
    }
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    fluid.setNumberOfPhases(3);
    setLegacyStressInteractions(fluid);
    double[] beta = {0.050786212269548, 0.75279094241510, 0.19642284531536};
    double[][] compositions = {{0.999391070282, 0.000608929718, 0.0, 0.0},
        {0.019363397332895, 0.98063660266710, 0.0, 0.0},
        {0.39228823836452, 0.26533804146526, 0.17118686008511, 0.17118686008511}};
    double[][] amounts = new double[3][order.length];
    for (int component = 0; component < order.length; component++) {
      int source = order[component].equals("CO2") ? 0
          : order[component].equals("water") ? 1 : order[component].equals("K+") ? 2 : 3;
      double sum = 0.0;
      for (int phase = 0; phase < 3; phase++) {
        amounts[phase][component] = beta[phase] * compositions[phase][source];
        sum += amounts[phase][component];
      }
      for (int phase = 0; phase < 3; phase++) {
        amounts[phase][component] *= fluid.getComponent(component).getz() / sum;
      }
    }
    for (int phase = 0; phase < 3; phase++) {
      fluid.setPhaseType(phase, phase == 0 ? PhaseType.GAS : phase == 1 ? PhaseType.LIQUID : PhaseType.AQUEOUS);
      double amount = 0.0;
      for (double value : amounts[phase]) {
        amount += value;
      }
      fluid.setBeta(phase, amount);
      for (int component = 0; component < order.length; component++) {
        fluid.getPhase(phase).getComponent(component).setx(amounts[phase][component] / amount);
      }
    }
    fluid.init(3);
    TPmultiflash solver = new TPmultiflash(fluid, false);
    solver.setDoubleArrays();
    for (int iteration = 0; iteration < 30; iteration++) {
      if (solver.solveBeta() < 1.0e-12) {
        break;
      }
    }
    return fluid;
  }

  /**
   * Verifies that changing registration order and the eliminated reference ion leaves the physical solution invariant.
   */
  @Test
  void registrationAndReferenceIonPermutationPreserveEquilibrium() {
    SystemInterface first = k15();
    SystemInterface second = k15(new String[] {"Cl-", "water", "K+", "CO2"});
    assertTrue(ElectroneutralLiquidPartition.refine(first));
    assertTrue(ElectroneutralLiquidPartition.refine(second));
    assertEquals(first.getGibbsEnergy(), second.getGibbsEnergy(), 2.0e-7);
    for (int phase = 0; phase < first.getNumberOfPhases(); phase++) {
      assertEquals(first.getBeta(phase), second.getBeta(phase), 1.0e-9);
      for (String name : new String[] {"CO2", "water", "K+", "Cl-"}) {
        assertEquals(first.getPhase(phase).getComponent(name).getx(), second.getPhase(phase).getComponent(name).getx(),
            1.0e-9);
      }
    }
    FluidEquilibriumChecks.validate(second, FluidEquilibriumChecks.inventory(second));
  }

  /** Verifies full inventory, charge and neutral salt chemical-potential equality at the larger-space equilibrium. */
  @Test
  void k15RetainsItsModelSplitWhileEquilibratingBothIonicLiquids() {
    SystemInterface fluid = k15();
    double[] original = FluidEquilibriumChecks.inventory(fluid);
    double gibbs = fluid.getGibbsEnergy();
    SystemInterface witness = chargeNeutralSaltTransferWitness(fluid);
    double witnessGibbs = witness.getGibbsEnergy();
    double roundoff = 64.0 * Math.ulp(Math.abs(gibbs));
    assertConservation(witness, original);
    assertTrue(witnessGibbs < gibbs - roundoff, "An independent feasible salt transfer lowers Gibbs energy");
    assertTrue(ElectroneutralLiquidPartition.refine(fluid));
    assertEquals(3, fluid.getNumberOfPhases());
    assertTrue(fluid.getGibbsEnergy() <= witnessGibbs + roundoff,
        "The converged endpoint must improve on the independent feasible transfer");
    FluidEquilibriumChecks.validate(fluid, original);
    double sum = 0.0;
    for (String ion : new String[] {"K+", "Cl-"}) {
      assertTrue(fluid.getPhase(1).getComponent(ion).getx() > 0.0);
      assertTrue(fluid.getPhase(2).getComponent(ion).getx() > 0.0);
      sum += Math.log(fluid.getPhase(1).getComponent(ion).getx())
          + fluid.getPhase(1).getComponent(ion).getLogFugacityCoefficient()
          - Math.log(fluid.getPhase(2).getComponent(ion).getx())
          - fluid.getPhase(2).getComponent(ion).getLogFugacityCoefficient();
    }
    assertEquals(0.0, sum, 1.0e-8);
    assertConservation(fluid, original);
    double repeated = fluid.getGibbsEnergy();
    assertTrue(ElectroneutralLiquidPartition.refine(fluid));
    assertEquals(repeated, fluid.getGibbsEnergy(), 2.0e-8);
  }

  /** Checks the projected Hessian against independent central differences of physical chemical potentials. */
  @Test
  void projectedCurvatureMatchesFiniteDifferences() {
    SystemInterface fluid = k15();
    ElectroneutralPhaseCoordinates coordinates = new ElectroneutralPhaseCoordinates(fluid);
    coordinates.seed(fluid, 0.01);
    double[] amounts = coordinates.amounts(fluid);
    for (int column = 0; column < coordinates.size(); column++) {
      double step = Math.min(1.0e-6, 0.001 * coordinates.coordinate(amounts, column));
      SystemInterface plus = fluid.clone();
      SystemInterface minus = fluid.clone();
      double[] upper = amounts.clone();
      double[] lower = amounts.clone();
      coordinates.increment(upper, column, step);
      coordinates.increment(lower, column, -step);
      coordinates.reconstruct(upper);
      coordinates.reconstruct(lower);
      assertTrue(coordinates.feasible(upper));
      assertTrue(coordinates.feasible(lower));
      coordinates.apply(plus, upper);
      coordinates.apply(minus, lower);
      plus.init(3);
      minus.init(3);
      for (int row = 0; row < coordinates.size(); row++) {
        double numerical = (coordinates.gradient(plus, row) - coordinates.gradient(minus, row)) / (2.0 * step);
        double analytic = coordinates.curvature(fluid, row, column);
        assertEquals(numerical, analytic, 5.0e-4 * Math.max(1.0, Math.abs(numerical)),
            "projected Hessian row " + row + " column " + column);
      }
    }
  }

  /** Verifies that a multi-cation, multi-anion, multivalent basis conserves every species and both liquid charges. */
  @Test
  void multisaltDirectionsPreserveIndependentInventoriesAndCharge() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 5.0);
    String[] names = {"CO2", "water", "K+", "Na+", "Ca++", "Cl-", "SO4--"};
    double[] totals = {0.1, 55.5, 0.2, 0.3, 0.1, 0.5, 0.1};
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], totals[i]);
    }
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.setPhaseType(0, PhaseType.AQUEOUS);
    fluid.setPhaseType(1, PhaseType.AQUEOUS);
    double total = fluid.getTotalNumberOfMoles();
    double beta = 0.0;
    double[] first = new double[names.length];
    for (int i = 0; i < names.length; i++) {
      first[i] = totals[i] / total * (i == 0 ? 0.8 : i == 1 ? 0.3 : 0.5);
      beta += first[i];
    }
    fluid.setBeta(0, beta);
    fluid.setBeta(1, 1.0 - beta);
    for (int i = 0; i < names.length; i++) {
      fluid.getPhase(0).getComponent(i).setx(first[i] / beta);
      fluid.getPhase(1).getComponent(i).setx((totals[i] / total - first[i]) / (1.0 - beta));
    }
    fluid.init(3);
    ElectroneutralPhaseCoordinates coordinates = new ElectroneutralPhaseCoordinates(fluid);
    assertEquals(6, coordinates.size());
    double[] amounts = coordinates.amounts(fluid);
    for (int row = 0; row < coordinates.size(); row++) {
      coordinates.increment(amounts, row, 1.0e-6 * (row + 1));
    }
    coordinates.reconstruct(amounts);
    assertTrue(coordinates.feasible(amounts));
    coordinates.apply(fluid, amounts);
    fluid.init(1);
    assertConservation(fluid, totals);
  }

  /** Verifies that public warm flashes preserve a distributed inventory through repeated and changed states. */
  @Test
  void publicFlashPreservesDistributedIonsAcrossStateChangesAndReturn() {
    SystemInterface fluid = publicK15(1.0);
    double[] original = FluidEquilibriumChecks.inventory(fluid);
    double originalGibbs = fluid.getGibbsEnergy();
    assertEquals(3, fluid.getNumberOfPhases());
    assertDistributedEquilibrium(fluid, original);
    new TPflash(fluid).run();
    assertDistributedEquilibrium(fluid, original);
    assertEquals(originalGibbs, fluid.getGibbsEnergy(), 2.0e-7);
    fluid.setTemperature(284.814777132);
    fluid.setPressure(31.01);
    new TPflash(fluid).run();
    assertDistributedEquilibrium(fluid, original);
    fluid.setTemperature(282.814777132);
    fluid.setPressure(29.01);
    new TPflash(fluid).run();
    assertDistributedEquilibrium(fluid, original);
    assertEquals(originalGibbs, fluid.getGibbsEnergy(), 2.0e-7);
  }

  /**
   * Verifies public flash invariance under extensive scaling without changing composition or admissible ionic roles.
   */
  @Test
  void publicFlashPreservesDistributedEquilibriumWhenInventoryIsScaled() {
    SystemInterface reference = publicK15(1.0);
    double molarGibbs = reference.getGibbsEnergy() / reference.getTotalNumberOfMoles();
    for (double scale : new double[] {1.0e-6, 1.0e6}) {
      SystemInterface fluid = publicK15(scale);
      double[] original = FluidEquilibriumChecks.inventory(fluid);
      assertDistributedEquilibrium(fluid, original);
      assertEquals(reference.getNumberOfPhases(), fluid.getNumberOfPhases());
      assertEquals(molarGibbs, fluid.getGibbsEnergy() / fluid.getTotalNumberOfMoles(), 2.0e-8);
      for (int phase = 0; phase < reference.getNumberOfPhases(); phase++) {
        assertEquals(reference.getBeta(phase), fluid.getBeta(phase), 1.0e-9);
        for (int component = 0; component < reference.getNumberOfComponents(); component++) {
          assertEquals(reference.getPhase(phase).getComponent(component).getx(),
              fluid.getPhase(phase).getComponent(component).getx(), 1.0e-9);
        }
      }
    }
  }

  /**
   * Runs an ordinary public TPflash with the explicit legacy-model stress configuration.
   *
   * @param scale extensive inventory multiplier
   * @return flashed fluid
   */
  private SystemInterface publicK15(double scale) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(282.814777132, 29.01);
    fluid.addComponent("CO2", 10.0 * scale);
    fluid.addComponent("water", scale / 0.01801528);
    double salt = scale * 14.97 / (100.0 - 14.97) / 0.0745513;
    fluid.addComponent("K+", salt);
    fluid.addComponent("Cl-", salt);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    setLegacyStressInteractions(fluid);
    new TPflash(fluid).run();
    return fluid;
  }

  /**
   * Retains a deliberately difficult three-fluid state independently of the improved default salt model.
   *
   * @param fluid stress-test fluid with initialized phase mixing rules
   */
  private void setLegacyStressInteractions(SystemInterface fluid) {
    for (PhaseInterface phase : fluid.getPhases()) {
      if (!(phase instanceof PhaseModifiedFurstElectrolyteEos) || !phase.hasComponent("K+")) {
        continue;
      }
      ElectrolyteMixingRulesInterface rule = ((PhaseModifiedFurstElectrolyteEos) phase).getElectrolyteMixingRule();
      int gas = phase.getComponent("CO2").getComponentNumber();
      for (String name : new String[] {"K+", "Cl-"}) {
        int ion = phase.getComponent(name).getComponentNumber();
        rule.setWijParameter(gas, ion, 1.27e-4);
        rule.setWijT1Parameter(gas, ion, name.equals("K+") ? 5.0e-4 : 0.0);
        rule.setWijT2Parameter(gas, ion, 0.0);
      }
    }
  }

  /**
   * Independently verifies molecular and neutral-salt affinities, inventory and normalized charge after public calls.
   *
   * @param fluid public flash endpoint
   * @param original extensive original species inventory
   */
  private void assertDistributedEquilibrium(SystemInterface fluid, double[] original) {
    fluid.init(3);
    FluidEquilibriumChecks.validate(fluid, original);
    double total = fluid.getTotalNumberOfMoles();
    for (int component = 0; component < original.length; component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        recovered += fluid.getPhase(phase).getComponent(component).getNumberOfMolesInPhase();
        if (component < 2 && phase > 0) {
          assertEquals(0.0,
              Math.log(fluid.getPhase(phase).getFugacity(component) / fluid.getPhase(0).getFugacity(component)),
              1.0e-8);
        }
      }
      assertEquals(original[component] / total, recovered / total, 1.0e-12);
    }
    double reference = Double.NaN;
    int ionicLiquids = 0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      double positive = fluid.getPhase(phase).getComponent("K+").getNumberOfMolesInPhase();
      double negative = fluid.getPhase(phase).getComponent("Cl-").getNumberOfMolesInPhase();
      assertEquals(0.0, (positive - negative) / total, 1.0e-12);
      if (positive > 0.0) {
        ionicLiquids++;
        assertEquals(PhaseType.AQUEOUS, fluid.getPhase(phase).getType());
        double saltPotential = 0.0;
        for (String name : new String[] {"K+", "Cl-"}) {
          saltPotential += Math.log(fluid.getPhase(phase).getComponent(name).getx())
              + fluid.getPhase(phase).getComponent(name).getLogFugacityCoefficient();
        }
        if (Double.isNaN(reference)) {
          reference = saltPotential;
        } else {
          assertEquals(reference, saltPotential, 1.0e-8);
        }
      }
    }
    assertEquals(2, ionicLiquids);
  }

  /**
   * Builds a feasible charge-neutral descent witness independently of the production amount-coordinate implementation.
   *
   * @param incumbent initialized single-host KCl endpoint
   * @return detached state with one percent of the salt transferred to the other aqueous liquid
   */
  private SystemInterface chargeNeutralSaltTransferWitness(SystemInterface incumbent) {
    SystemInterface witness = incumbent.clone();
    int components = witness.getNumberOfComponents();
    double[][] amounts = new double[witness.getNumberOfPhases()][components];
    for (int phase = 0; phase < amounts.length; phase++) {
      for (int component = 0; component < components; component++) {
        amounts[phase][component] = witness.getBeta(phase) * witness.getPhase(phase).getComponent(component).getx();
      }
    }
    int potassium = witness.getComponent("K+").getComponentNumber();
    int chloride = witness.getComponent("Cl-").getComponentNumber();
    double transferred = 0.01 * Math.min(amounts[2][potassium], amounts[2][chloride]);
    for (int component : new int[] {potassium, chloride}) {
      amounts[1][component] += transferred;
      amounts[2][component] -= transferred;
    }
    for (int phase = 0; phase < amounts.length; phase++) {
      double total = 0.0;
      for (double amount : amounts[phase]) {
        total += amount;
      }
      witness.setBeta(phase, total);
      for (int component = 0; component < components; component++) {
        witness.getPhase(phase).getComponent(component).setx(amounts[phase][component] / total);
      }
    }
    witness.init(3);
    for (int phase = 0; phase < amounts.length; phase++) {
      FluidEquilibriumChecks.validatePressureRoot(witness.getPhase(phase));
    }
    return witness;
  }

  /** Requires electrochemical stationarity even when all molecular and balance checks remain satisfied. */
  @Test
  void physicalGateRejectsWrongNeutralSaltChemicalPotential() {
    SystemInterface fluid = k15();
    assertTrue(ElectroneutralLiquidPartition.refine(fluid));
    double[] original = FluidEquilibriumChecks.inventory(fluid);
    fluid.getPhase(1).getComponent("K+")
        .setFugacityCoefficient(fluid.getPhase(1).getComponent("K+").getFugacityCoefficient() * 1.01);
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> FluidEquilibriumChecks.validate(fluid, original));
    assertTrue(failure.getMessage().contains("charge-neutral transfer stationarity"));
  }

  /** Preserves an explicit cancellation and the incumbent state before any candidate can be adopted. */
  @Test
  void interruptionPropagatesWithoutMutatingTheIncumbent() {
    SystemInterface fluid = k15();
    double gibbs = fluid.getGibbsEnergy();
    double beta = fluid.getBeta(0);
    try {
      Thread.currentThread().interrupt();
      assertThrows(CancellationException.class, () -> ElectroneutralLiquidPartition.refine(fluid));
      assertTrue(Thread.currentThread().isInterrupted());
      assertEquals(gibbs, fluid.getGibbsEnergy());
      assertEquals(beta, fluid.getBeta(0));
    } finally {
      Thread.interrupted();
    }
  }

  /**
   * Distinguishes the hybrid model's harmless gas trace from a second aqueous host while retaining its ion gate.
   */
  @Test
  void gasTraceDoesNotCreateDistributedIonicLiquidCoordinates() {
    SystemInterface fluid = new SystemPitzer(298.15, 10.0);
    fluid.addComponent("methane", 5.0);
    fluid.addComponent("water", 55.508);
    fluid.addComponent("Na+", 1.0);
    fluid.addComponent("Cl-", 1.0);
    fluid.setMixingRule("classic");
    double[] original = FluidEquilibriumChecks.inventory(fluid);
    new TPflash(fluid).run();
    assertEquals(2, fluid.getNumberOfPhases());
    assertEquals(1, ElectroneutralLiquidPartition.liquidHosts(fluid));
    int gas = fluid.getPhaseNumberOfPhase("gas");
    assertTrue(gas >= 0);
    double trace = fluid.getPhase(gas).getComponent("Na+").getx();
    assertTrue(trace > 0.0 && trace <= 1.0e-40);
    assertFalse(ElectroneutralLiquidPartition.hasDistributedIons(fluid));
    FluidEquilibriumChecks.validate(fluid, original);
    fluid.getPhase(gas).getComponent("Na+").setx(1.0e-30);
    assertFalse(ElectroneutralLiquidPartition.hasDistributedIons(fluid));
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> FluidEquilibriumChecks.validate(fluid, original));
    assertTrue(failure.getMessage().contains("aqueous-only ion"));
  }

  /** Keeps specialized EOS-GE phase equilibrium outside the qualified EOS amount-Hessian scope. */
  @Test
  void eosGeModelsRemainOutsideDistributedEosRefinement() {
    SystemInterface fluid = new UnqualifiedHybridSystem();
    fluid.addComponent("CO2", 0.1);
    fluid.addComponent("water", 55.5);
    fluid.addComponent("K+", 0.1);
    fluid.addComponent("Cl-", 0.1);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.setPhaseType(0, PhaseType.AQUEOUS);
    fluid.setPhaseType(1, PhaseType.AQUEOUS);
    fluid.init(3);
    assertEquals(2, ElectroneutralLiquidPartition.liquidHosts(fluid));
    assertTrue(FlashPhaseConstraints.hasIonicInventory(fluid));
    double gibbs = fluid.getGibbsEnergy();
    assertFalse(ElectroneutralLiquidPartition.refine(fluid));
    assertEquals(gibbs, fluid.getGibbsEnergy());
  }

  /**
   * Test system opting into the specialized EOS-GE marker without qualifying its phase Hessian.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  private static final class UnqualifiedHybridSystem extends SystemElectrolyteCPAstatoil implements EosGeFlashModel {
    private static final long serialVersionUID = 1000;

    /** Initializes the same EOS constituent model while requiring the EOS-GE exclusion policy. */
    private UnqualifiedHybridSystem() {
      super(298.15, 1.0);
    }
  }

  /** Avoids additional work for the common gas plus one aqueous host topology. */
  @Test
  void singleLiquidHostDoesNotRunPartitionRefinement() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 1.0);
    fluid.addComponent("CO2", 1.0);
    fluid.addComponent("water", 55.5);
    fluid.addComponent("K+", 0.1);
    fluid.addComponent("Cl-", 0.1);
    fluid.setMixingRule(10);
    new TPflash(fluid).run();
    assertFalse(ElectroneutralLiquidPartition.refine(fluid));
  }

  /**
   * Independently checks extensive inventory and phase charge from stored component amounts.
   *
   * @param fluid initialized candidate
   * @param original original species inventories
   */
  private void assertConservation(SystemInterface fluid, double[] original) {
    for (int component = 0; component < original.length; component++) {
      double sum = 0.0;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        sum += fluid.getPhase(phase).getComponent(component).getNumberOfMolesInPhase();
      }
      assertEquals(original[component], sum, 1.0e-12 * Math.max(1.0, original[component]));
    }
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      double charge = 0.0;
      for (int component = 0; component < original.length; component++) {
        charge += fluid.getPhase(phase).getComponent(component).getNumberOfMolesInPhase()
            * fluid.getComponent(component).getIonicCharge();
      }
      assertEquals(0.0, charge, 1.0e-12);
    }
  }
}
