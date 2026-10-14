package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.atomelement.Element;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent material and equilibrium checks for nonreactive electrolyte mixtures. Inventories are captured before
 * flashing, so changing the feed cannot make an invalid phase split pass.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
@Tag("slow")
class GeneralElectrolyteEquilibriumTest {
  private static final double BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;

  /**
   * Supplies mixed gases, mixed inhibitors, different salts and concentration limits.
   *
   * @return independently reported feed cases
   */
  static Stream<Arguments> mixtures() {
    return Stream.of(
        Arguments.of("nitrogen-brine", new String[] {"nitrogen", "water", "Na+", "Cl-"},
            new double[] {0.1, 1.0, 0.018, 0.018}, 298.15, 50.0),
        Arguments.of("mixed-gas", new String[] {"CO2", "methane", "nitrogen", "water", "Na+", "Cl-"},
            new double[] {0.12, 0.20, 0.03, 1.0, 0.018, 0.018}, 298.15, 50.0),
        Arguments.of("mixed-inhibitors",
            new String[] {"CO2", "methane", "nitrogen", "water", "MEG", "methanol", "Na+", "Cl-"},
            new double[] {0.12, 0.20, 0.03, 1.0, 0.03, 0.02, 0.018, 0.018}, 298.15, 50.0),
        Arguments.of("mixed-salts",
            new String[] {"methane", "nitrogen", "water", "MEG", "methanol", "Na+", "K+", "Ca++", "Cl-"},
            new double[] {0.20, 0.03, 1.0, 0.03, 0.02, 0.010, 0.005, 0.002, 0.019}, 298.15, 30.0),
        Arguments.of("dilute-salt", new String[] {"CO2", "methane", "water", "MEG", "methanol", "Na+", "Cl-"},
            new double[] {0.12, 0.20, 1.0, 0.03, 0.02, 1.0e-7, 1.0e-7}, 298.40, 49.5),
        Arguments.of("concentrated-salt", new String[] {"CO2", "methane", "water", "MEG", "methanol", "Na+", "Cl-"},
            new double[] {0.12, 0.20, 1.0, 0.03, 0.02, 0.04, 0.04}, 297.90, 50.5));
  }

  /**
   * Checks constraints without assuming an unmeasured density, solubility or phase fraction.
   *
   * @param label case description
   * @param names feed species
   * @param amounts feed amounts in mol
   * @param temperature temperature in K
   * @param pressure pressure in bara
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("mixtures")
  void mixedSpeciesRetainOriginalInventoryAndEquilibrate(String label, String[] names, double[] amounts,
      double temperature, double pressure) {
    SystemInterface fluid = create(names, amounts, temperature, pressure);
    Inventory original = new Inventory(fluid);
    flash(fluid);
    assertEndpoint(fluid, original, label);
    if ("nitrogen-brine".equals(label)) {
      assertEquals(2, fluid.getNumberOfPhases(), "established nitrogen gas/brine topology");
      assertTrue(fluid.hasPhaseType(PhaseType.GAS), "nitrogen-rich material phase");
    }
  }

  /**
   * Tests trace additions across the former CO2/brine molecular-name routing boundary.
   *
   * @param trace trace molecule
   */
  @ParameterizedTest
  @ValueSource(strings = {"methane", "methanol"})
  void traceMoleculesDoNotChangeTheFiniteComponentEndpoint(String trace) {
    SystemInterface reference = create(new String[] {"CO2", "water", "Na+", "Cl-"},
        new double[] {10.0, 55.508435, 0.90055, 0.90055}, 283.15, 50.0);
    SystemInterface perturbed = create(new String[] {"CO2", "water", "Na+", "Cl-", trace},
        new double[] {10.0, 55.508435, 0.90055, 0.90055, 1.0e-8}, 283.15, 50.0);
    Inventory referenceFeed = new Inventory(reference);
    Inventory perturbedFeed = new Inventory(perturbed);
    flash(reference);
    flash(perturbed);
    assertEndpoint(reference, referenceFeed, "CO2/brine reference");
    assertEndpoint(perturbed, perturbedFeed, "trace " + trace);
    assertEquivalent(reference, perturbed, 1.0e-7);
  }

  /**
   * Checks cold starts, poor phase fractions, warm state changes and independently flashed clones.
   */
  @Test
  void initialGuessesRepeatsAndNearbyStatesGiveTheSameEndpoint() {
    String[] names = {"methane", "water", "Na+", "Cl-"};
    double[] amounts = {0.1, 1.0, 0.001, 0.001};
    SystemInterface reference = create(names, amounts, 298.15, 10.01325);
    Inventory original = new Inventory(reference);
    flash(reference);
    assertEndpoint(reference, original, "reference");
    for (double initialBeta : new double[] {1.0e-8, 0.5, 1.0 - 1.0e-8}) {
      SystemInterface alternative = create(names, amounts, 298.15, 10.01325);
      alternative.setBeta(0, initialBeta);
      alternative.setBeta(1, 1.0 - initialBeta);
      flash(alternative);
      assertEndpoint(alternative, original, "initial beta " + initialBeta);
      assertEquivalent(reference, alternative, 1.0e-8);
    }
    SystemInterface repeated = reference.clone();
    for (int iteration = 0; iteration < 3; iteration++) {
      flash(repeated);
      assertEndpoint(repeated, original, "repeat " + iteration);
      assertEquivalent(reference, repeated, 1.0e-8);
    }
    for (double direction : new double[] {-1.0, 1.0}) {
      double temperature = 298.15 + direction * 0.25;
      double pressure = 10.01325 + direction * 0.1;
      repeated.setTemperature(temperature);
      repeated.setPressure(pressure);
      SystemInterface fresh = create(names, amounts, temperature, pressure);
      flash(repeated);
      flash(fresh);
      assertEndpoint(repeated, original, "changed state");
      assertEndpoint(fresh, original, "fresh nearby state");
      assertEquivalent(fresh, repeated, 1.0e-8);
    }
  }

  /** Uses the established gas/oil/brine fixture in SystemElectrolyteCPATest. */
  @Test
  void establishedThreePhaseFixtureConservesAllSpeciesAndEquilibrates() {
    SystemInterface fluid = create(new String[] {"methane", "n-heptane", "n-butane", "water", "Na+", "Cl-"},
        new double[] {0.5, 0.3, 0.1, 1.0, 0.02, 0.02}, 298.15, 20.0);
    Inventory original = new Inventory(fluid);
    flash(fluid);
    assertEndpoint(fluid, original, "gas/oil/brine");
    assertEquals(3, fluid.getNumberOfPhases(), "established gas/oil/brine topology");
    assertTrue(fluid.hasPhaseType(PhaseType.GAS));
    assertTrue(fluid.hasPhaseType(PhaseType.OIL));
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));
  }

  /**
   * Builds an unreacted feed; all molecules are treated alike by the acceptance checks.
   *
   * @param names species names
   * @param amounts amounts in mol
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return unflashed feed
   */
  private static SystemInterface create(String[] names, double[] amounts, double temperature, double pressure) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(temperature, pressure);
    for (int component = 0; component < names.length; component++) {
      fluid.addComponent(names[component], amounts[component]);
    }
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Evaluates a complete flash and refreshes the fugacity derivatives.
   *
   * @param fluid mutable system
   */
  private static void flash(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
  }

  /**
   * Checks original species and element inventories, charge, phase normalization and neutral fugacity.
   *
   * @param fluid flashed system
   * @param original immutable original feed
   * @param label diagnostic context
   */
  static void assertEndpoint(SystemInterface fluid, Inventory original, String label) {
    List<String> errors = new ArrayList<String>();
    assertEquals(original.names.length, fluid.getNumberOfComponents(), label + " species count");
    if (!fluid.hasPhaseType(PhaseType.AQUEOUS)) {
      errors.add("missing aqueous material host");
    }
    near(errors, "original total mol", original.total, fluid.getTotalNumberOfMoles(),
        original.total * BALANCE_TOLERANCE);
    double[] reconstructed = new double[original.names.length];
    double[] storedAmounts = new double[original.names.length];
    double betaSum = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseInterface current = fluid.getPhase(phase);
      double beta = fluid.getBeta(phase);
      betaSum += beta;
      near(errors, "phase " + phase + " original mol", beta * original.total, current.getNumberOfMolesInPhase(),
          original.total * BALANCE_TOLERANCE);
      if (!Double.isFinite(beta) || beta <= 0.0 || beta > 1.0) {
        errors.add("invalid phase fraction " + phase + ": " + beta);
      }
      if (!Double.isFinite(current.getZ()) || current.getZ() <= 0.0) {
        errors.add("invalid compressibility " + phase);
      }
      double compositionSum = 0.0;
      double charge = 0.0;
      for (int component = 0; component < original.names.length; component++) {
        ComponentInterface species = current.getComponent(original.names[component]);
        double x = species.getx();
        if (!Double.isFinite(x) || x < 0.0 || x > 1.0) {
          errors.add("invalid composition " + phase + "/" + original.names[component] + ": " + x);
        }
        compositionSum += x;
        charge += species.getIonicCharge() * x;
        reconstructed[component] += beta * x;
        storedAmounts[component] += species.getNumberOfMolesInPhase() / original.total;
        near(errors, "phase " + phase + "/" + original.names[component] + " stored fraction", beta * x,
            species.getNumberOfMolesInPhase() / original.total, BALANCE_TOLERANCE);
        if ((species.isIsIon() || species.getIonicCharge() != 0.0) && current.getType() != PhaseType.AQUEOUS
            && x > 1.0e-40) {
          errors.add("ion outside aqueous " + phase + "/" + original.names[component] + ": " + x);
        }
      }
      near(errors, "phase " + phase + " composition sum", 1.0, compositionSum, BALANCE_TOLERANCE);
      near(errors, "phase " + phase + " charge", 0.0, charge, BALANCE_TOLERANCE);
    }
    near(errors, "phase fraction sum", 1.0, betaSum, BALANCE_TOLERANCE);
    Map<String, Double> elements = new LinkedHashMap<String, Double>();
    for (int component = 0; component < original.names.length; component++) {
      String name = original.names[component];
      double feedFraction = original.moles[component] / original.total;
      double tolerance = Math.min(BALANCE_TOLERANCE, feedFraction * 1.0e-6);
      near(errors, name + " original feed fraction", feedFraction, fluid.getPhase(0).getComponent(name).getz(),
          tolerance);
      near(errors, name + " recovered fraction", feedFraction, reconstructed[component], tolerance);
      near(errors, name + " stored phase fraction", feedFraction, storedAmounts[component], tolerance);
      near(errors, name + " original mol", original.moles[component],
          fluid.getPhase(0).getComponent(name).getNumberOfmoles(), tolerance * original.total);
      for (Map.Entry<String, Double> atom : original.atoms.get(component).entrySet()) {
        add(elements, atom.getKey(), reconstructed[component] * original.total * atom.getValue());
      }
      ComponentInterface first = fluid.getPhase(0).getComponent(name);
      if (first.isIsIon() || first.getIonicCharge() != 0.0 || original.moles[component] == 0.0) {
        continue;
      }
      for (int phase = 1; phase < fluid.getNumberOfPhases(); phase++) {
        ComponentInterface other = fluid.getPhase(phase).getComponent(name);
        double residual = Math.log(first.getx()) + first.getLogFugacityCoefficient() - Math.log(other.getx())
            - other.getLogFugacityCoefficient();
        near(errors, name + " log fugacity 0/" + phase, 0.0, residual, FUGACITY_TOLERANCE);
      }
    }
    for (Map.Entry<String, Double> element : original.elements.entrySet()) {
      near(errors, element.getKey() + " original element mol", element.getValue(), elements.get(element.getKey()),
          Math.max(1.0, element.getValue()) * BALANCE_TOLERANCE);
    }
    assertTrue(Double.isFinite(fluid.getGibbsEnergy()), label + " finite Gibbs energy");
    assertTrue(errors.isEmpty(), label + ": " + String.join("; ", errors));
  }

  /**
   * Compares like phase types and original molecules, allowing trace additions in the second system.
   *
   * @param expected reference endpoint
   * @param actual tested endpoint
   * @param tolerance absolute composition and phase-fraction tolerance
   */
  static void assertEquivalent(SystemInterface expected, SystemInterface actual, double tolerance) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases(), "phase topology");
    for (int phase = 0; phase < expected.getNumberOfPhases(); phase++) {
      PhaseInterface source = expected.getPhase(phase);
      String type = source.getType().getDesc();
      assertTrue(actual.hasPhaseType(type), "missing phase " + type);
      int target = actual.getPhaseNumberOfPhase(type);
      assertEquals(expected.getBeta(phase), actual.getBeta(target), tolerance, type + " fraction");
      for (int component = 0; component < expected.getNumberOfComponents(); component++) {
        String name = source.getComponent(component).getComponentName();
        assertEquals(source.getComponent(component).getx(), actual.getPhase(target).getComponent(name).getx(),
            tolerance, type + "/" + name);
      }
    }
    double energyScale = Math.max(1.0, Math.abs(expected.getGibbsEnergy()));
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), energyScale * tolerance, "Gibbs energy");
  }

  /**
   * Records all residual failures so one endpoint produces useful diagnostic evidence.
   *
   * @param errors accumulated failures
   * @param label quantity name
   * @param expected reference value
   * @param actual measured value
   * @param tolerance maximum absolute difference
   */
  private static void near(List<String> errors, String label, double expected, double actual, double tolerance) {
    if (!Double.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
      errors.add(label + " expected=" + expected + " actual=" + actual + " tolerance=" + tolerance);
    }
  }

  /**
   * Adds an atomic amount to an inventory.
   *
   * @param inventory accumulated element amounts
   * @param element chemical symbol
   * @param amount amount in mol
   */
  private static void add(Map<String, Double> inventory, String element, double amount) {
    Double existing = inventory.get(element);
    inventory.put(element, (existing == null ? 0.0 : existing) + amount);
  }

  /**
   * Feed snapshot with independent atomic stoichiometry for MEG, whose element table entry is absent.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  static final class Inventory {
    private final double total;
    private final String[] names;
    private final double[] moles;
    private final List<Map<String, Double>> atoms = new ArrayList<Map<String, Double>>();
    private final Map<String, Double> elements = new LinkedHashMap<String, Double>();

    /**
     * Captures the unflashed feed without retaining mutable thermodynamic objects.
     *
     * @param fluid original feed
     */
    Inventory(SystemInterface fluid) {
      total = fluid.getTotalNumberOfMoles();
      names = new String[fluid.getNumberOfComponents()];
      moles = new double[names.length];
      for (int component = 0; component < names.length; component++) {
        ComponentInterface species = fluid.getPhase(0).getComponent(component);
        names[component] = species.getComponentName();
        moles[component] = species.getNumberOfmoles();
        Map<String, Double> coefficients = new LinkedHashMap<String, Double>();
        if ("MEG".equals(names[component])) {
          coefficients.put("C", 2.0);
          coefficients.put("H", 6.0);
          coefficients.put("O", 2.0);
        } else {
          Element formula = species.getElements();
          assertNotNull(formula.getElementNames(), "fixture element data " + names[component]);
          for (int atom = 0; atom < formula.getElementNames().length; atom++) {
            coefficients.put(formula.getElementNames()[atom], formula.getElementCoefs()[atom]);
          }
        }
        atoms.add(coefficients);
        for (Map.Entry<String, Double> atom : coefficients.entrySet()) {
          add(elements, atom.getKey(), atom.getValue() * moles[component]);
        }
      }
    }
  }
}
