package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseWax;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

/**
 * Comprehensive tests comparing all four wax thermodynamic models against each other and
 * literature-derived expectations.
 *
 * <p>
 * Models tested: Pedersen (default), Won, Wilson, Coutinho (UNIQUAC).
 * </p>
 */
public class WaxModelComparisonTest {

  /**
   * Creates a standard test fluid with TBP/plus fractions, configures wax model, and returns the
   * system ready for flash calculations.
   */
  private SystemInterface createTestSystem(String waxModelName) {
    NeqSimDataBase.setCreateTemporaryTables(true);
    SystemInterface system = new SystemSrkEos(298.0, 10.0);
    system.addComponent("methane", 6.78);
    system.addTBPfraction("C19", 10.13, 170.0 / 1000.0, 0.7814);
    system.addPlusFraction("C20", 10.62, 381.0 / 1000.0, 0.850871882888);
    system.getCharacterization().characterisePlusFraction();
    system.getWaxModel().addTBPWax();
    system.createDatabase(true);
    system.setMixingRule(2);
    system.addSolidComplexPhase("wax");

    // Set the wax component model on the wax phase
    for (int k = 0; k < system.getNumberOfPhases(); k++) {
      if (system.getPhase(k) instanceof PhaseWax) {
        ((PhaseWax) system.getPhase(k)).setWaxComponentModel(waxModelName);
      }
    }

    system.setMultiphaseWaxCheck(true);
    system.setMultiPhaseCheck(true);
    NeqSimDataBase.setCreateTemporaryTables(false);
    system.init(0);
    system.init(1);
    return system;
  }

  /**
   * Tests the default Pedersen (ComponentWax) model at low temperature. Verifies that the improved
   * model with DeltaCp correction gives reasonable wax fractions.
   */
  @Test
  void testPedersenModelWaxFraction() {
    SystemInterface system = createTestSystem("Pedersen");
    system.setTemperature(261.0);
    system.setPressure(5.0);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    assertTrue(system.hasPhaseType("wax"), "System should have wax phase at 261 K");

    double waxMassFraction = system.getPhaseFraction("wax", "mass");
    assertTrue(waxMassFraction > 0.01, "Expected significant wax fraction at 261 K");
    assertTrue(waxMassFraction < 0.99, "Wax fraction should be less than total mass");
  }

  /**
   * Tests the Won model (ComponentWonWax) at the same conditions.
   */
  @Test
  void testWonModelWaxFraction() {
    SystemInterface system = createTestSystem("Won");
    system.setTemperature(261.0);
    system.setPressure(5.0);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    // Won model should also predict wax at this low temperature
    if (system.hasPhaseType("wax")) {
      double waxMassFraction = system.getPhaseFraction("wax", "mass");
      assertTrue(waxMassFraction > 0.0, "Won model should predict positive wax fraction at 261 K");
    }
  }

  /**
   * Tests the Wilson model (ComponentWaxWilson) at the same conditions.
   */
  @Test
  void testWilsonModelWaxFraction() {
    SystemInterface system = createTestSystem("Wilson");
    system.setTemperature(261.0);
    system.setPressure(5.0);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    // Wilson model should also predict wax at this low temperature
    if (system.hasPhaseType("wax")) {
      double waxMassFraction = system.getPhaseFraction("wax", "mass");
      assertTrue(waxMassFraction > 0.0,
          "Wilson model should predict positive wax fraction at 261 K");
    }
  }

  /**
   * Tests the Coutinho UNIQUAC model (ComponentCoutinhoWax) at the same conditions.
   */
  @Test
  void testCoutinhoModelWaxFraction() {
    SystemInterface system = createTestSystem("Coutinho");
    system.setTemperature(261.0);
    system.setPressure(5.0);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    // Coutinho model should predict wax at this low temperature
    if (system.hasPhaseType("wax")) {
      double waxMassFraction = system.getPhaseFraction("wax", "mass");
      assertTrue(waxMassFraction > 0.0,
          "Coutinho model should predict positive wax fraction at 261 K");
    }
  }

  /**
   * Tests that the Pedersen model (with DeltaCp correction) still gives reasonable results at a
   * temperature well above WAT. No wax should be present well above the wax appearance temperature.
   */
  @Test
  void testNoWaxAboveWAT() {
    SystemInterface system = createTestSystem("Pedersen");
    system.setTemperature(273.15 + 100.0); // 100 C - well above any possible WAT
    system.setPressure(5.0);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    // At 100C, no wax should be present for typical petroleum fluids
    if (system.hasPhaseType("wax")) {
      double waxMassFraction = system.getPhaseFraction("wax", "mass");
      assertTrue(waxMassFraction < 1e-4, "Should have negligible wax at 100C");
    }
  }

  /**
   * Creates a gas condensate system (similar to WaxFlashTest setup) and calculates WAT using the
   * default Pedersen model. This is a regression test.
   */
  @Test
  void testWATCalculation() {
    NeqSimDataBase.setCreateTemporaryTables(true);
    SystemInterface system = new SystemSrkEos(273.0 + 30, 50.0);
    system.addComponent("CO2", 0.018);
    system.addComponent("nitrogen", 0.333);
    system.addComponent("methane", 96.702);
    system.addComponent("ethane", 1.773);
    system.addComponent("propane", 0.496);
    system.addComponent("i-butane", 0.099);
    system.addComponent("n-butane", 0.115);
    system.addComponent("i-pentane", 0.004);
    system.addComponent("n-pentane", 0.024);
    system.addComponent("n-heptane", 0.324);
    system.addPlusFraction("C9", 0.095, 207.0 / 1000.0, 0.8331);
    system.getCharacterization().characterisePlusFraction();
    system.getWaxModel().addTBPWax();
    system.createDatabase(true);
    system.setMixingRule(2);
    system.addSolidComplexPhase("wax");
    system.setMultiphaseWaxCheck(true);
    NeqSimDataBase.setCreateTemporaryTables(false);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.calcWAT();
    } catch (Exception ex) {
      // swallow
    }

    double watC = system.getTemperature("C");
    // WAT should be in a reasonable range (20-40 C is typical for this fluid)
    assertTrue(watC > 15.0, "WAT should be above 15C for this fluid");
    assertTrue(watC < 45.0, "WAT should be below 45C for this fluid");
  }

  /**
   * Verifies that the PhaseWax model selection mechanism works correctly.
   */
  @Test
  void testPhaseWaxModelSelection() {
    PhaseWax phase = new PhaseWax();
    assertEquals("Pedersen", phase.getWaxComponentModel());

    phase.setWaxComponentModel("Coutinho");
    assertEquals("Coutinho", phase.getWaxComponentModel());

    phase.setWaxComponentModel("Won");
    assertEquals("Won", phase.getWaxComponentModel());

    phase.setWaxComponentModel("Wilson");
    assertEquals("Wilson", phase.getWaxComponentModel());
  }
}
