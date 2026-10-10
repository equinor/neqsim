package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.CO2BrinePhaseEquilibrium;
import neqsim.thermodynamicoperations.flashops.ReactiveCO2BrinePhaseEquilibrium;

/**
 * Conserved CO2/brine/MEG equilibrium regressions for the 44 bara NaCl failure.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CO2BrineMegHydrateTest {
  /**
   * Reproduces the reported endpoint and distinguishes the two concentration bases.
   *
   * @param waterBasis whether dosage is specified per mass of water
   * @param expectedTemperature expected numerical regression temperature in Celsius
   * @throws Exception if the hydrate operation fails
   */
  @ParameterizedTest
  @CsvSource({"false,1.90296", "true,2.70828"})
  void reportedNaClMegCaseConservesTheOriginalFeed(boolean waterBasis, double expectedTemperature) throws Exception {
    SystemInterface fluid = mixture(44.0, 10.0, 5.0, 10.0, waterBasis);
    HydrateEquilibriumDiagnostics evidence = solveAndAudit(fluid, Double.NaN);
    assertTrue(evidence.isSaturatedCO2Boundary());
    assertEquals(expectedTemperature, fluid.getTemperature("C"), 0.005);
    assertEquals(2, fluid.getNumberOfPhases());
  }

  /**
   * Preserves the passing five-percent salt control and the inhibition trend.
   *
   * @throws Exception if the hydrate operation fails
   */
  @Test
  void megLowersTheTemperatureWithoutChangingTheFivePercentControl() throws Exception {
    SystemInterface control = mixture(44.0, 5.0, 5.0, 10.0, false);
    solveAndAudit(control, Double.NaN);
    assertEquals(5.43259, control.getTemperature("C"), 0.005);
    double previous = Double.POSITIVE_INFINITY;
    for (double meg : new double[] {0.0, 5.0, 10.0, 20.0}) {
      SystemInterface fluid = mixture(44.0, 10.0, meg, 10.0, false);
      assertTrue(solveAndAudit(fluid, Double.NaN).isSaturatedCO2Boundary());
      assertTrue(fluid.getTemperature() < previous, "Increasing MEG must lower the verified boundary");
      previous = fluid.getTemperature();
    }
  }

  /**
   * Checks adjacent pressures, alternative seeds and gas/liquid CO2 trials.
   *
   * @param pressure pressure in bara
   * @param meg MEG mass percent on water plus MEG
   * @throws Exception if the hydrate operation fails
   */
  @Tag("slow")
  @ParameterizedTest
  @CsvSource({"40,5", "44,5", "48,5", "50,10", "100,5", "200,5", "44,20"})
  void independentSeedsAndNearbyStatesCloseAllMolecularFugacities(double pressure, double meg) throws Exception {
    double reference = Double.NaN;
    for (double guess : new double[] {268.15, 278.15, 288.15}) {
      SystemInterface fluid = mixture(pressure, 10.0, meg, 10.0, false);
      assertTrue(solveAndAudit(fluid, guess).isSaturatedCO2Boundary());
      if (Double.isFinite(reference)) {
        assertEquals(reference, fluid.getTemperature(), 2.0e-4);
      }
      reference = fluid.getTemperature();
    }
  }

  /**
   * Does not create a free CO2 phase for an undersaturated feed or lose MEG on repeated runs.
   *
   * @throws Exception if the hydrate operation fails
   */
  @Tag("slow")
  @Test
  void finiteInventoryAndRepeatedRunsRetainTheSuppliedSpecies() throws Exception {
    SystemInterface limited = mixture(100.0, 10.0, 5.0, 0.5, false);
    HydrateEquilibriumDiagnostics finite = solveAndAudit(limited, 273.15);
    assertFalse(finite.hasCO2RichPhase());
    assertFalse(finite.isSaturatedCO2Boundary());
    assertEquals(1, limited.getNumberOfPhases());
    SystemInterface fluid = mixture(44.0, 10.0, 5.0, 10.0, false);
    solveAndAudit(fluid, Double.NaN);
    double temperature = fluid.getTemperature();
    solveAndAudit(fluid, 283.15);
    assertEquals(temperature, fluid.getTemperature(), 2.0e-4);
    SystemInterface copy = fluid.clone();
    solveAndAudit(copy, 273.15);
    assertEquals(temperature, copy.getTemperature(), 2.0e-4);
  }

  /**
   * Preserves the molecular scope of the reactive and general multiphase paths.
   */
  @Test
  void otherMoleculesAndReactiveMegDoNotEnterTheConstrainedSolver() {
    SystemInterface fluid = mixture(44.0, 10.0, 5.0, 10.0, false);
    assertTrue(CO2BrinePhaseEquilibrium.isApplicable(fluid));
    fluid.addComponent("methane", 1.0);
    assertFalse(CO2BrinePhaseEquilibrium.isApplicable(fluid));
    SystemInterface methanol = mixture(44.0, 10.0, 5.0, 10.0, false);
    methanol.addComponent("methanol", 1.0);
    assertFalse(CO2BrinePhaseEquilibrium.isApplicable(methanol));
    SystemInterface reactive = mixture(44.0, 10.0, 5.0, 10.0, false);
    reactive.chemicalReactionInit();
    assertFalse(CO2BrinePhaseEquilibrium.isApplicable(reactive));
    assertFalse(ReactiveCO2BrinePhaseEquilibrium.isApplicable(reactive));
    SystemInterface organicRich = mixture(44.0, 10.0, 95.0, 10.0, false);
    assertFalse(CO2BrinePhaseEquilibrium.isApplicable(organicRich));
  }

  /**
   * Constructs a one-kilogram-water NaCl/MEG recipe without adding CO2 during solution.
   *
   * @param pressure pressure in bara
   * @param salt salt percent on water plus salt, or on water alone when waterBasis is true
   * @param meg MEG percent on water plus MEG, or on water alone when waterBasis is true
   * @param co2 molar CO2 inventory
   * @param waterBasis whether the percentages are dosages per mass of water
   * @return configured non-reactive electrolyte CPA mixture
   */
  private static SystemInterface mixture(double pressure, double salt, double meg, double co2, boolean waterBasis) {
    double saltOnBrine = waterBasis ? 100.0 * salt / (100.0 + salt) : salt;
    SystemInterface fluid = CO2BrineHydratePhaseStateTest.brine(pressure, saltOnBrine, co2);
    if (meg > 0.0) {
      double megMass = waterBasis ? meg / 100.0 : meg / (100.0 - meg);
      fluid.addComponent("MEG", megMass / 0.06206784);
      fluid.setMixingRule(10);
    }
    return fluid;
  }

  /**
   * Solves the endpoint and checks original species, phase balances and every molecular fugacity.
   *
   * @param fluid fluid to solve and audit
   * @param guess temperature guess in kelvin, or NaN for the default policy
   * @return immutable diagnostic evidence
   * @throws Exception if the hydrate operation fails
   */
  private static HydrateEquilibriumDiagnostics solveAndAudit(SystemInterface fluid, double guess) throws Exception {
    double[] input = new double[fluid.getNumberOfComponents()];
    for (int component = 0; component < input.length; component++) {
      input[component] = fluid.getPhase(0).getComponent(component).getNumberOfmoles();
    }
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    if (Double.isFinite(guess)) {
      operations.hydrateFormationTemperature(guess);
    } else {
      operations.hydrateFormationTemperature();
    }
    HydrateFormationTemperatureFlash flash = (HydrateFormationTemperatureFlash) operations.getOperation();
    assertTrue(flash.isConverged());
    HydrateEquilibriumDiagnostics evidence = flash.getDiagnostics();
    assertTrue(evidence.isConverged());
    assertTrue(evidence.hasAqueousPhase());
    assertTrue(Double.isFinite(evidence.getMinimumCo2TrialDistance()));
    assertTrue(evidence.getComponentBalanceResidual() < 1.0e-10);
    assertTrue(evidence.getChargeResidual() < 1.0e-10);
    CO2BrineHydratePhaseStateTest.assertEndpoint(fluid, evidence.hasCO2RichPhase());
    for (int component = 0; component < input.length; component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        recovered += fluid.getTotalNumberOfMoles() * fluid.getBeta(phase)
            * fluid.getPhase(phase).getComponent(component).getx();
      }
      assertEquals(input[component], recovered, 1.0e-8);
      if (fluid.getNumberOfPhases() == 2 && fluid.getPhase(0).getComponent(component).getIonicCharge() == 0.0) {
        double ratio = fluid.getPhase(0).getFugacity(component) / fluid.getPhase(1).getFugacity(component);
        assertEquals(0.0, Math.log(ratio), 1.0e-8, "CO2, water and MEG must each equilibrate");
      }
    }
    return evidence;
  }
}
