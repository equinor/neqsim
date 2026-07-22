package neqsim.process.safety.vacuum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.vacuum.VacuumCollapseAnalyzer.CoolingPoint;
import neqsim.process.safety.vacuum.VacuumCollapseAnalyzer.VacuumVerdict;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link VacuumCollapseAnalyzer}.
 *
 * @author ESOL
 * @version 1.0
 */
public class VacuumCollapseAnalyzerTest {

  /**
   * A blocked-in steam space cooled from 120 C to 20 C must develop a deep vacuum that exceeds a partial-vacuum rating.
   */
  @Test
  void steamCooldownDevelopsVacuumExceedingRating() {
    SystemInterface water = new SystemSrkCPAstatoil(393.15, 1.8);
    water.addComponent("water", 1.0);
    water.setMixingRule(10);
    water.setMultiPhaseCheck(true);

    VacuumCollapseAnalyzer analyzer = new VacuumCollapseAnalyzer(water, 10.0)
        .setInitialConditions(1.8, "bara", 120.0, "C").setColdEndTemperature(20.0, "C")
        .setExternalPressureRating(0.5, "bara").setCoolingSteps(20);

    VacuumCollapseResult result = analyzer.analyze();

    assertTrue(result.isVacuumPresent(), "cooling condensing steam must develop a vacuum");
    assertFalse(result.isWithinRating(), "deep vacuum must fall below a 0.5 bara rating");
    assertEquals(VacuumVerdict.VACUUM_EXCEEDS_RATING, result.getVerdict());
    assertTrue(result.getFinalPressureBara() < result.getInitialPressureBara(),
        "internal pressure must drop on cooldown");
    assertTrue(result.getVacuumDepthBar() > 0.5, "vacuum depth should be substantial for condensing steam");
    assertTrue(result.getMarginToRatingBar() < 0.0, "margin to rating must be negative when the rating is exceeded");
    assertTrue(result.getMakeupGasMassKg() > 0.0, "make-up gas is required to restore pressure");

    List<CoolingPoint> curve = result.getCoolingCurve();
    assertTrue(curve.size() > 1, "cooling curve must contain multiple points");
    for (int i = 1; i < curve.size(); i++) {
      assertTrue(curve.get(i).pressureBara <= curve.get(i - 1).pressureBara + 1.0e-6,
          "pressure must be monotonically non-increasing on cooldown");
    }
  }

  /**
   * A blocked-in dry methane space cooled modestly stays well above atmospheric pressure: no vacuum, no make-up.
   */
  @Test
  void dryGasModestCooldownStaysAboveAtmospheric() {
    SystemInterface gas = new SystemSrkEos(323.15, 10.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");

    VacuumCollapseAnalyzer analyzer = new VacuumCollapseAnalyzer(gas, 10.0)
        .setInitialConditions(10.0, "bara", 50.0, "C").setColdEndTemperature(0.0, "C")
        .setExternalPressureRating(0.0, "bara").setCoolingSteps(10);

    VacuumCollapseResult result = analyzer.analyze();

    assertFalse(result.isVacuumPresent(), "dry gas cooled modestly must stay above atmospheric");
    assertEquals(VacuumVerdict.NO_VACUUM, result.getVerdict());
    assertTrue(result.isWithinRating());
    assertEquals(0.0, result.getVacuumDepthBar(), 1.0e-9, "no vacuum depth when pressure stays above atmospheric");
    assertEquals(0.0, result.getMakeupGasMassKg(), 1.0e-9, "no make-up gas when pressure stays above the set point");
    assertTrue(result.getMinimumPressureBara() > result.getAtmosphericPressureBara());
  }

  /**
   * The analyzer must reject a cold-end temperature that is not below the initial temperature.
   */
  @Test
  void rejectsNonCoolingCase() {
    SystemInterface gas = new SystemSrkEos(323.15, 10.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");

    VacuumCollapseAnalyzer analyzer = new VacuumCollapseAnalyzer(gas, 5.0).setInitialConditions(10.0, "bara", 50.0, "C")
        .setColdEndTemperature(60.0, "C");

    assertThrows(IllegalStateException.class, analyzer::analyze);
  }

  /**
   * The constructor must reject a non-positive vessel volume.
   */
  @Test
  void rejectsNonPositiveVolume() {
    SystemInterface gas = new SystemSrkEos(323.15, 10.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");

    assertThrows(IllegalArgumentException.class, () -> new VacuumCollapseAnalyzer(gas, 0.0));
  }
}
