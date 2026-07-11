package neqsim.chemicalreactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for the acid-gas (carbonic / hydrosulfuric acid) in-situ pH estimate.
 *
 * <p>
 * These tests exercise the acid-gas dissociation fallback added to {@link neqsim.thermo.phase.Phase#getpH()}. The
 * fallback lets an electrolyte-CPA aqueous phase report a physically meaningful acidic pH for CO2/H2S-loaded water even
 * when the rigorous chemical-reaction equilibrium solver has not produced explicit H3O+ species. Previously such fluids
 * returned a flat, unphysical pH of 7.0.
 * </p>
 */
public class AcidGasPHTest {
  private static final Logger logger = LogManager.getLogger(AcidGasPHTest.class);

  /**
   * CO2-saturated water at ambient conditions should give the textbook pH of about 3.9.
   *
   * <p>
   * Excess CO2 over water at ~1 atm and 25 C forms a gas phase and saturates the aqueous phase. The dissolved CO2
   * concentration (~0.033 mol/L) and the carbonic-acid first dissociation constant give [H+] = sqrt(K1 * C_CO2) and
   * hence pH ~ 3.9. No chemicalReactionInit() is called here - the fallback must work for a plain electrolyte fluid.
   * </p>
   */
  @Test
  public void testCO2SaturatedWaterPH() {
    SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    system.addComponent("CO2", 5.0);
    system.addComponent("water", 10.0);
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double pH = system.getpH();
    logger.info("CO2-saturated water pH = {}", pH);

    assertTrue(Double.isFinite(pH), "pH should be finite, got " + pH);
    assertTrue(pH < 7.0, "CO2-saturated water should be acidic (pH < 7), got " + pH);
    assertEquals(3.9, pH, 0.5, "CO2-saturated water pH should be near 3.9, got " + pH);
  }

  /**
   * The explicit "acidgas" method returns the same acidic estimate as the default when acid gas is dissolved.
   */
  @Test
  public void testExplicitAcidGasMethod() {
    SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    system.addComponent("CO2", 5.0);
    system.addComponent("water", 10.0);
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    int aqueous = -1;
    for (int p = 0; p < system.getNumberOfPhases(); p++) {
      if (system.getPhase(p).getPhaseTypeName().equalsIgnoreCase("aqueous")) {
        aqueous = p;
      }
    }
    assertTrue(aqueous >= 0, "an aqueous phase should be present");

    double pHdefault = system.getPhase(aqueous).getpH();
    double pHacidgas = system.getPhase(aqueous).getpH("acidgas");
    logger.info("default pH = {}, acidgas pH = {}", pHdefault, pHacidgas);

    assertTrue(Double.isFinite(pHacidgas), "acidgas pH should be finite, got " + pHacidgas);
    assertTrue(pHacidgas < 7.0, "acidgas pH should be acidic, got " + pHacidgas);
    assertEquals(pHdefault, pHacidgas, 0.05, "default and acidgas pH should match when acid gas dissolved");
  }

  /**
   * Acid-gas-free water should report a near-neutral pH (about 7 at 25 C) rather than an acidic value.
   */
  @Test
  public void testNeutralWaterPH() {
    SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    system.addComponent("water", 10.0);
    system.createDatabase(true);
    system.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double pH = system.getpH();
    logger.info("neutral water pH = {}", pH);

    assertTrue(Double.isFinite(pH), "pH should be finite, got " + pH);
    assertEquals(7.0, pH, 0.5, "acid-gas-free water should be near neutral, got " + pH);
  }

  /**
   * Adding H2S on top of CO2 should not raise the pH above the acidic band (extra weak acid can only lower pH).
   */
  @Test
  public void testCO2AndH2SWaterIsAcidic() {
    SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    system.addComponent("CO2", 5.0);
    system.addComponent("H2S", 1.0);
    system.addComponent("water", 10.0);
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double pH = system.getpH();
    logger.info("CO2 + H2S water pH = {}", pH);

    assertTrue(Double.isFinite(pH), "pH should be finite, got " + pH);
    assertTrue(pH < 7.0, "CO2 + H2S water should be acidic (pH < 7), got " + pH);
    assertTrue(pH > 2.5, "pH should stay in a realistic acidic band, got " + pH);
  }
}
