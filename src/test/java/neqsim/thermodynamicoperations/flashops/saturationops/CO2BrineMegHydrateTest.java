package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verifies conserved CO2/NaCl/MEG hydrate equilibrium near the reported 44 bara failure.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CO2BrineMegHydrateTest {
  /**
   * Runs the reported dosage and compares it with independently defined solution percentages and the salt control.
   *
   * @param saltGrams NaCl per kilogram water, in grams
   * @param megGrams MEG per kilogram water, in grams
   * @param expectedC numerical hydrate temperature in degrees Celsius
   * @throws Exception if the hydrate search fails
   */
  @ParameterizedTest
  @CsvSource({"100.0,50.0,2.7083", "111.111111111,52.631578947,1.9030", "50.0,50.0,5.6751"})
  void defaultSearchConservesBothConcentrationBases(double saltGrams, double megGrams, double expectedC)
      throws Exception {
    SystemInterface fluid = mixture(44.0, saltGrams, megGrams, 10.0, false);
    assertEquals(expectedC, solveAndAudit(fluid, Double.NaN, true), 0.005);
  }

  /**
   * Checks independent initial guesses, component order and the nearby pressures.
   *
   * @param pressure absolute pressure in bar
   * @throws Exception if either independent search fails
   */
  @Tag("slow")
  @ParameterizedTest
  @CsvSource({"37.0", "40.0", "44.0", "48.0", "100.0"})
  void nearReportedConditionsAreIndependentOfStartAndComponentOrder(double pressure) throws Exception {
    SystemInterface normalOrder = mixture(pressure, 100.0, 50.0, 10.0, false);
    SystemInterface ionsFirst = mixture(pressure, 100.0, 50.0, 10.0, true);
    double cold = solveAndAudit(normalOrder, 268.15, true);
    double warm = solveAndAudit(ionsFirst, 283.15, true);
    assertEquals(cold, warm, 2.0e-4);
  }

  /**
   * Checks that a feed with insufficient CO2 retains an aqueous-only endpoint.
   *
   * @throws Exception if the hydrate search fails
   */
  @Tag("slow")
  @Test
  void finiteInventoryDoesNotCreateAFreeCo2Phase() throws Exception {
    SystemInterface fluid = mixture(100.0, 100.0, 50.0, 0.5, false);
    assertTrue(Double.isFinite(solveAndAudit(fluid, 273.15, false)));
  }

  /**
   * Builds a non-reactive, charge-balanced NaCl and MEG solution with one kilogram of water.
   *
   * @param pressure absolute pressure in bar
   * @param saltGrams sodium chloride in grams
   * @param megGrams monoethylene glycol in grams
   * @param co2Moles carbon dioxide in moles
   * @param ionsFirst whether to register the ions before the molecular species
   * @return the configured equilibrium feed
   */
  private static SystemInterface mixture(double pressure, double saltGrams, double megGrams, double co2Moles,
      boolean ionsFirst) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(283.15, pressure);
    double saltMoles = saltGrams / 58.44277;
    String[] names = {"CO2", "water", "MEG", "Na+", "Cl-"};
    double[] amounts = {co2Moles, 1.0 / 0.01801528, megGrams / 62.06784, saltMoles, saltMoles};
    int offset = ionsFirst ? 3 : 0;
    for (int index = 0; index < names.length; index++) {
      int component = (index + offset) % names.length;
      fluid.addComponent(names[component], amounts[component]);
    }
    fluid.setMixingRule(10);
    fluid.setHydrateCheck(true);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Solves the feed and audits the returned incipient hydrate and material fluid state.
   *
   * @param fluid configured feed
   * @param guess starting temperature in kelvin, or NaN to use the default search
   * @param saturated whether a distinct CO2-rich material phase is expected
   * @return hydrate temperature in degrees Celsius
   * @throws Exception if the hydrate search fails
   */
  private static double solveAndAudit(SystemInterface fluid, double guess, boolean saturated) throws Exception {
    double[] supplied = new double[fluid.getNumberOfComponents()];
    for (int component = 0; component < supplied.length; component++) {
      supplied[component] = fluid.getPhase(0).getComponent(component).getNumberOfmoles();
    }
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    if (Double.isFinite(guess)) {
      operations.hydrateFormationTemperature(guess);
    } else {
      operations.hydrateFormationTemperature();
    }
    HydrateEquilibriumDiagnostics evidence = ((HydrateFormationTemperatureFlash) operations.getOperation())
        .getDiagnostics();
    assertTrue(evidence.isConverged());
    assertTrue(evidence.hasAqueousPhase());
    assertEquals(saturated, evidence.hasCO2RichPhase());
    assertEquals(saturated, evidence.isSaturatedCO2Boundary());
    assertEquals(saturated, evidence.getMinimumCo2TrialDistance() < 0.0);
    assertTrue(evidence.getComponentBalanceResidual() < 1.0e-10);
    assertTrue(evidence.getChargeResidual() < 1.0e-10);
    if (saturated) {
      assertTrue(evidence.getFluidFugacityResidual() < 1.0e-8);
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        assertTrue(fluid.getPhase(phase).getComponent("MEG").getx() > 1.0e-20);
      }
    } else {
      assertFalse(Double.isFinite(evidence.getFluidFugacityResidual()));
    }
    CO2BrineHydratePhaseStateTest.assertEndpoint(fluid, saturated);
    for (int component = 0; component < supplied.length; component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        recovered += fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(component).getx();
      }
      assertEquals(supplied[component], fluid.getTotalNumberOfMoles() * recovered, 1.0e-8);
    }
    return fluid.getTemperature("C");
  }
}
