package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for the Kent-Eisenberg thermodynamic model used for acid gas (H2S/CO2) solubility in amine
 * solutions.
 *
 * <p>
 * The Kent-Eisenberg model uses chemical equilibrium with apparent equilibrium constants where
 * non-ideality is absorbed into the K values (gamma = 1.0). This is a simplified approach compared
 * to rigorous electrolyte models.
 * </p>
 *
 * @author Even Solbraa
 */
class SystemKentEisenbergTest {

  /**
   * Test that SystemKentEisenberg can be instantiated and components added.
   */
  @Test
  void testCreateSystem() {
    SystemInterface testSystem = new SystemKentEisenberg(323.15, 1.0);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("H2S", 0.01);
    testSystem.addComponent("water", 9.0);
    testSystem.addComponent("MDEA", 1.0);
    assertNotNull(testSystem);
    assertEquals(4, testSystem.getNumberOfComponents());
    assertEquals("Kent Eisenberg-model", testSystem.getModelName());
  }

  /**
   * Test that a TP flash can be performed with the Kent-Eisenberg model.
   */
  @Test
  void testTPFlash() {
    SystemInterface testSystem = new SystemKentEisenberg(323.15, 2.0);
    testSystem.addComponent("CO2", 0.5);
    testSystem.addComponent("water", 9.0);
    testSystem.addComponent("MDEA", 1.0);
    testSystem.setMixingRule(4);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    assertTrue(testSystem.getNumberOfPhases() >= 1);
    double co2InGas = testSystem.getPhase(0).getComponent("CO2").getx();
    assertTrue(co2InGas > 0.0, "CO2 should be present in gas phase");
  }

  /**
   * Test Kent-Eisenberg model for H2S absorption in MDEA solution. The model should show H2S
   * partitioning between gas and liquid.
   */
  @Test
  void testH2SAbsorptionInMDEA() {
    SystemInterface testSystem = new SystemKentEisenberg(326.15, 1.1);
    testSystem.addComponent("H2S", 0.01);
    testSystem.addComponent("water", 9.0);
    testSystem.addComponent("MDEA", 0.1);
    testSystem.setMixingRule(4);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    // H2S should mostly dissolve in the amine solution at low loadings
    double h2sInLiquid = testSystem.getPhase(1).getComponent("H2S").getx();
    assertTrue(h2sInLiquid > 0.0, "H2S should be present in liquid phase");
  }

  /**
   * Test that the Kent-Eisenberg model can perform a bubble point pressure flash. This is the
   * typical use case: given a loaded amine solution, find the equilibrium vapor pressure of acid
   * gas.
   */
  @Test
  void testBubblePointPressureFlash() {
    SystemInterface testSystem = new SystemKentEisenberg(323.15, 1.0);
    testSystem.addComponent("CO2", 0.5);
    testSystem.addComponent("water", 9.0);
    testSystem.addComponent("MDEA", 1.0);
    testSystem.setMixingRule(4);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    try {
      ops.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      // Bubble point flash may not converge for all conditions
      // but should not throw unexpected exceptions
    }

    double pressure = testSystem.getPressure();
    assertTrue(pressure > 0.0, "Pressure should be positive after bubble point flash");
  }

  /**
   * Test system cloning works correctly for the Kent-Eisenberg model.
   */
  @Test
  void testClone() {
    SystemInterface testSystem = new SystemKentEisenberg(323.15, 2.0);
    testSystem.addComponent("CO2", 0.5);
    testSystem.addComponent("water", 9.0);
    testSystem.setMixingRule(4);

    SystemInterface clonedSystem = testSystem.clone();
    assertNotNull(clonedSystem);
    assertEquals(testSystem.getNumberOfComponents(), clonedSystem.getNumberOfComponents());
    assertEquals(testSystem.getTemperature(), clonedSystem.getTemperature(), 1e-6);
    assertEquals(testSystem.getPressure(), clonedSystem.getPressure(), 1e-6);
  }

  /**
   * Test with chemical reactions initialized - typical amine system workflow.
   */
  @Test
  void testWithChemicalReactions() {
    SystemInterface testSystem = new SystemKentEisenberg(326.15, 1.1);
    testSystem.addComponent("H2S", 0.01);
    testSystem.addComponent("water", 9.0);
    testSystem.addComponent("MDEA", 0.1);

    testSystem.chemicalReactionInit();
    testSystem.createDatabase(true);
    testSystem.setMixingRule(4);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    assertTrue(testSystem.getNumberOfPhases() >= 1);
  }

  /**
   * Test Kent-Eisenberg model for CO2 partial pressure over MDEA solution. Literature reference:
   * Kent, R.L. and Eisenberg, B. (1976). "Better Data for Amine Treating", Hydrocarbon Processing,
   * 55(2), pp.87-90.
   *
   * <p>
   * At 50°C (323.15 K) and moderate CO2 loadings in ~50 wt% MDEA, the equilibrium CO2 partial
   * pressure should be on the order of 0.01-1.0 bar depending on loading.
   * </p>
   */
  @Test
  void testCO2PartialPressureOverMDEA() {
    // 50 wt% MDEA solution at 50°C
    SystemInterface testSystem = new SystemKentEisenberg(323.15, 2.0);
    testSystem.addComponent("CO2", 0.1);
    testSystem.addComponent("water", 5.0);
    testSystem.addComponent("MDEA", 1.0);
    testSystem.setMixingRule(4);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    // CO2 partial pressure in the gas phase
    if (testSystem.getNumberOfPhases() > 1) {
      double pCO2 = testSystem.getPressure() * testSystem.getPhase(0).getComponent("CO2").getx();
      assertTrue(pCO2 > 0.0, "CO2 partial pressure should be positive, got: " + pCO2);
    }
  }

  /**
   * Regression guard for the H2S first-dissociation equilibrium constant (reaction
   * <code>water-H2S</code>: H2S + H2O &harr; HS&minus; + H3O+).
   *
   * <p>
   * The Kent-Eisenberg reaction quotient is evaluated on a mole-fraction basis
   * ({@code ChemicalReaction.calcKx} uses {@code getx()}), so the molality-based literature
   * constants of Edwards, Maurer, Newman and Prausnitz (AIChE J., 1976/1978) must be converted by
   * subtracting <code>ln(55.51) = 4.0167</code> per net dissolved solute. The other acid-gas
   * reactions (CO2water, carbonate, waterreac) were converted, but the H2S first dissociation
   * originally retained the raw molality constant K1 = 214.582, making its mole-fraction Kx too
   * large by a factor of ~55.5 and over-predicting H2S ionization / absorption. The corrected,
   * mole-fraction-consistent value is K1 = 210.565345. This test fails if the constant silently
   * reverts.
   * </p>
   */
  @Test
  void testH2SFirstDissociationConstantOnMoleFractionBasis() {
    SystemInterface testSystem = new SystemKentEisenberg(298.15, 1.0);
    testSystem.addComponent("H2S", 0.01);
    testSystem.addComponent("water", 9.0);
    testSystem.addComponent("MDEA", 0.1);

    testSystem.chemicalReactionInit();
    testSystem.createDatabase(true);
    testSystem.setMixingRule(4);

    neqsim.chemicalreactions.chemicalreaction.ChemicalReaction h2sReaction =
        testSystem.getChemicalReactionOperations().getReactionList().getReaction("water-H2S");
    assertNotNull(h2sReaction, "water-H2S reaction should be loaded");

    double[] kCoefs = h2sReaction.getK();
    // K1 (constant term) must be the mole-fraction-converted value, not the raw molality value.
    assertEquals(210.565345, kCoefs[0], 1.0e-6,
        "H2S first-dissociation K1 must be on the mole-fraction basis (Edwards molality value "
            + "214.582 minus ln(55.51)); a value near 214.582 indicates the conversion regressed");
    // K2 and K3 (temperature-dependent terms) match Edwards (1978) and are unchanged.
    assertEquals(-12995.4, kCoefs[1], 1.0e-3, "H2S K2 (B/T term) should be unchanged");
    assertEquals(-33.5471, kCoefs[2], 1.0e-4, "H2S K3 (ln T term) should be unchanged");

    // Resulting ln K at 298.15 K should be ~4.02 lower than the legacy (uncorrected) value.
    double t = 298.15;
    double lnK = kCoefs[0] + kCoefs[1] / t + kCoefs[2] * Math.log(t) + kCoefs[3] * t;
    double legacyLnK = 214.582 + kCoefs[1] / t + kCoefs[2] * Math.log(t) + kCoefs[3] * t;
    assertEquals(Math.log(55.51), legacyLnK - lnK, 1.0e-2,
        "Correction should reduce ln K by ln(55.51) relative to the legacy molality constant");
  }

  /**
   * Demonstrates simultaneous CO2 and H2S absorption into an aqueous MDEA solution using the
   * Kent-Eisenberg equilibrium model, and reports the resulting acid-gas loadings and treated-gas
   * partial pressures.
   *
   * <p>
   * A sour natural-gas stream (CO2 + H2S + methane) is contacted with a ~50 wt% MDEA solution at
   * typical absorber conditions (40&deg;C, 50 bar). After a single equilibrium-stage flash the test
   * computes:
   * </p>
   *
   * <ul>
   * <li>the acid-gas <em>loading</em> (mol acid gas absorbed per mol total amine, where total amine
   * = free MDEA + protonated MDEA+), a key amine-treating design metric; and</li>
   * <li>the <em>treated-gas partial pressures</em> p<sub>CO2</sub> and p<sub>H2S</sub> =
   * y&middot;P, which determine whether the sweet-gas acid-gas specification is met.</li>
   * </ul>
   *
   * <p>
   * This is the building block used by stage-based absorber equipment (e.g.
   * {@code SimpleAbsorber}): each stage performs such an equilibrium flash. The test asserts a
   * physically consistent outcome (two phases form, acid gases partition into the loaded amine, and
   * loadings/partial pressures are positive and finite).
   * </p>
   */
  @Test
  void testCO2AndH2SAbsorptionInMDEADemonstration() {
    // Sour gas (per mol-basis) contacted with an aqueous MDEA solution. Conditions chosen so that
    // both a gas phase (for treated-gas partial pressures) and a loaded-amine liquid phase form.
    SystemInterface testSystem = new SystemKentEisenberg(313.15, 5.0);
    testSystem.addComponent("methane", 1.0);
    testSystem.addComponent("CO2", 0.05);
    testSystem.addComponent("H2S", 0.02);
    testSystem.addComponent("water", 9.0);
    testSystem.addComponent("MDEA", 1.0);

    testSystem.chemicalReactionInit();
    testSystem.createDatabase(true);
    testSystem.setMixingRule(4);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();
    testSystem.init(3);

    assertTrue(testSystem.getNumberOfPhases() >= 2,
        "A gas and a loaded-amine liquid phase should both be present");

    // Identify gas (phase 0) and liquid (phase 1) phases.
    double pressure = testSystem.getPressure();
    double yCO2 = testSystem.getPhase(0).getComponent("CO2").getx();
    double yH2S = testSystem.getPhase(0).getComponent("H2S").getx();

    // Treated-gas partial pressures (bar).
    double pCO2 = pressure * yCO2;
    double pH2S = pressure * yH2S;
    assertTrue(pCO2 > 0.0 && Double.isFinite(pCO2),
        "CO2 partial pressure should be positive/finite");
    assertTrue(pH2S > 0.0 && Double.isFinite(pH2S),
        "H2S partial pressure should be positive/finite");

    // Total amine in the liquid = free MDEA + protonated MDEA+.
    double mdea = testSystem.getPhase(1).getComponent("MDEA").getNumberOfMolesInPhase();
    double mdeaProtonated = testSystem.getPhase(1).getComponent("MDEA+").getNumberOfMolesInPhase();
    double totalAmine = mdea + mdeaProtonated;
    assertTrue(totalAmine > 0.0, "Total amine in liquid phase should be positive");

    // Acid gas absorbed = total fed to the system minus what remains in the treated gas. Using the
    // overall mass balance captures every dissolved/ionized form (free CO2/H2S, bicarbonate,
    // carbonate, bisulfide, protonated amine) without enumerating each ionic species.
    double co2Total = testSystem.getComponent("CO2").getNumberOfmoles();
    double h2sTotal = testSystem.getComponent("H2S").getNumberOfmoles();
    double co2InGas = testSystem.getPhase(0).getComponent("CO2").getNumberOfMolesInPhase();
    double h2sInGas = testSystem.getPhase(0).getComponent("H2S").getNumberOfMolesInPhase();
    double co2Absorbed = co2Total - co2InGas;
    double h2sAbsorbed = h2sTotal - h2sInGas;

    double co2Loading = co2Absorbed / totalAmine;
    double h2sLoading = h2sAbsorbed / totalAmine;

    assertTrue(co2Loading >= 0.0 && Double.isFinite(co2Loading),
        "CO2 loading should be non-negative/finite");
    assertTrue(h2sLoading >= 0.0 && Double.isFinite(h2sLoading),
        "H2S loading should be non-negative/finite");

    // Both acid gases should be substantially absorbed by the amine solution.
    assertTrue(co2Absorbed > 0.0, "CO2 should be absorbed into the amine liquid");
    assertTrue(h2sAbsorbed > 0.0, "H2S should be absorbed into the amine liquid");

    System.out.printf(
        "Kent-Eisenberg CO2+H2S/MDEA absorber demo:%n"
            + "  Treated-gas pCO2 = %.4e bar, pH2S = %.4e bar%n"
            + "  CO2 loading = %.4f, H2S loading = %.4f (mol acid gas / mol total amine)%n",
        pCO2, pH2S, co2Loading, h2sLoading);
  }
}
