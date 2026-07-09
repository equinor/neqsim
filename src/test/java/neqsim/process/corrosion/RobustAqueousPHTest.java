package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for {@link RobustAqueousPH} — always-finite in-situ pH with a correlation fallback.
 *
 * @author ESOL
 * @version 1.0
 */
public class RobustAqueousPHTest {

  /**
   * Verifies the CO2-water correlation gives an acidic, bounded pH and a neutral value with no CO2.
   */
  @Test
  void correlationIsBoundedAndAcidic() {
    double acidic = RobustAqueousPH.correlationPH(25.0, 1.0);
    assertTrue(acidic > 2.0 && acidic < 6.0, "CO2-water pH should be acidic, was " + acidic);

    double neutral = RobustAqueousPH.correlationPH(25.0, 0.0);
    assertEquals(7.0, neutral, 1.0e-9, "no CO2 should give neutral pH");

    // High CO2 partial pressure stays within the plausible clamp.
    double clamped = RobustAqueousPH.correlationPH(25.0, 1000.0);
    assertTrue(clamped >= 2.0 && clamped <= 9.0, "pH should be clamped to the plausible band, was " + clamped);
  }

  /**
   * Verifies that a properly reacted electrolyte brine yields the rigorous (electrolyte-sourced) pH.
   */
  @Test
  void usesRigorousElectrolytePHWhenValid() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(60.0 + 273.15, 50.0);
    fluid.addComponent("CO2", 0.10);
    fluid.addComponent("water", 0.88);
    fluid.addComponent("Na+", 0.01);
    fluid.addComponent("Cl-", 0.01);
    fluid.chemicalReactionInit();
    fluid.createDatabase(true);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();

    double pCO2 = 0.10 * 50.0;
    RobustAqueousPH.Result result = RobustAqueousPH.estimate(fluid, pCO2);
    assertEquals("electrolyte", result.getSource(), "a valid electrolyte pH should be used");
    assertFalse(result.isFellBack());
    assertTrue(result.getPH() > 2.0 && result.getPH() < 9.0, "pH should be plausible, was " + result.getPH());
  }

  /**
   * Verifies that a dry fluid (no aqueous phase) falls back to the CO2-water correlation.
   */
  @Test
  void fallsBackWhenNoAqueousPhase() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(60.0 + 273.15, 50.0);
    fluid.addComponent("CO2", 0.05);
    fluid.addComponent("methane", 0.95);
    fluid.createDatabase(true);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();

    RobustAqueousPH.Result result = RobustAqueousPH.estimate(fluid, 0.05 * 50.0);
    assertEquals("correlation", result.getSource(), "a dry fluid should use the correlation fallback");
    assertTrue(result.isFellBack());
    assertTrue(result.getPH() > 2.0 && result.getPH() < 9.0);
  }
}
