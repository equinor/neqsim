package neqsim.process.chemistry.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ScaleKinetics} — induction time, growth rates, and limiting-regime classification.
 *
 * @author ESOL
 * @version 1.0
 */
public class ScaleKineticsTest {

  /**
   * Verifies that a subsaturated solution (SI &lt;= 0) produces no growth and infinite induction time.
   */
  @Test
  void subsaturatedGivesNoGrowth() {
    ScaleKinetics k = new ScaleKinetics().setSaturationIndex(-0.5).evaluate();
    assertEquals("NONE", k.getLimitingRegime());
    assertEquals(0.0, k.getEffectiveGrowthRateMmYr(), 1.0e-12);
    assertTrue(Double.isInfinite(k.getInductionTimeHours()), "induction time should be infinite below saturation");
  }

  /**
   * Verifies that induction time decreases as supersaturation increases.
   */
  @Test
  void inductionTimeFallsWithSupersaturation() {
    double tLow = new ScaleKinetics().setSaturationIndex(0.3).evaluate().getInductionTimeHours();
    double tHigh = new ScaleKinetics().setSaturationIndex(1.5).evaluate().getInductionTimeHours();
    assertTrue(tHigh < tLow, "higher supersaturation should give a shorter induction time: " + tHigh + " vs " + tLow);
    assertTrue(tHigh > 0.0);
  }

  /**
   * Verifies reaction-limited default and transport-limited switch when transport is slow.
   */
  @Test
  void regimeClassification() {
    // No transport data supplied -> reaction controls.
    ScaleKinetics reaction = new ScaleKinetics().setSaturationIndex(1.0).evaluate();
    assertEquals("REACTION", reaction.getLimitingRegime());
    assertTrue(reaction.getSurfaceLimitedGrowthMmYr() > 0.0);

    // Slow mass transfer with a small driving concentration -> transport controls.
    ScaleKinetics transport = new ScaleKinetics().setSaturationIndex(2.0).setSurfaceReaction(50.0, 2.0)
        .setMassTransfer(1.0e-6, 1.0e-3, 0.0).evaluate();
    assertEquals("TRANSPORT", transport.getLimitingRegime());
    assertTrue(transport.getTransportLimitedGrowthMmYr() > 0.0);
    assertTrue(transport.getEffectiveGrowthRateMmYr() <= transport.getSurfaceLimitedGrowthMmYr());
    assertNotNull(transport.toJson());
  }
}
