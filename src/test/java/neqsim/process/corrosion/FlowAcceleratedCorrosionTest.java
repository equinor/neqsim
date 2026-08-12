package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FlowAcceleratedCorrosion}.
 *
 * @author ESOL
 * @version 1.0
 */
public class FlowAcceleratedCorrosionTest {

  /** Chromium content of plain carbon steel [wt%]. */
  private static final double CARBON_STEEL_CR = 0.02;

  /** Chromium content of ASTM A335 P11 [wt%]. */
  private static final double P11_CR = 1.25;

  /**
   * Build a base case representative of a hot glycol/water heating-medium tube.
   *
   * @return a configured model
   */
  private FlowAcceleratedCorrosion baseCase() {
    return new FlowAcceleratedCorrosion().setFlow(2.50, 0.025).setFluidProperties(931.0, 0.487).setTemperature(150.0)
        .setInSituPH(7.1).setGeometry(FacGeometry.STRAIGHT_PIPE).setChromiumContent(CARBON_STEEL_CR);
  }

  /**
   * The temperature factor must peak at the magnetite solubility maximum near 150 C, which is the temperature API RP
   * 571 identifies as most severe for FAC.
   */
  @Test
  void temperatureFactorPeaksNear150C() {
    double peak = FlowAcceleratedCorrosion.temperatureFactor(150.0);
    assertEquals(1.0, peak, 1.0e-9, "the bell must be normalised to 1 at the peak");
    assertTrue(FlowAcceleratedCorrosion.temperatureFactor(80.0) < peak, "80 C must be less severe than the peak");
    assertTrue(FlowAcceleratedCorrosion.temperatureFactor(230.0) < peak, "230 C must be less severe than the peak");
    assertTrue(FlowAcceleratedCorrosion.temperatureFactor(150.0) > FlowAcceleratedCorrosion.temperatureFactor(100.0),
        "150 C must be more severe than 100 C");
  }

  /**
   * The dimensionless groups must match a direct evaluation of their definitions.
   */
  @Test
  void dimensionlessGroupsMatchTheirDefinitions() {
    FlowAcceleratedCorrosionResult r = baseCase().calculate();

    double expectedRe = 931.0 * 2.50 * 0.025 / (0.487e-3);
    assertEquals(expectedRe, r.getReynolds(), expectedRe * 1.0e-9);
    assertEquals(0.0165 * Math.pow(r.getReynolds(), 0.86) * Math.pow(r.getSchmidt(), 0.33), r.getSherwood(),
        r.getSherwood() * 1.0e-9);
    assertTrue(r.getMassTransferCoefficientMs() > 0.0, "mass-transfer coefficient must be positive");
    assertTrue(r.getReynolds() > 10000.0, "the base case must be turbulent");
  }

  /**
   * A small velocity exceedance is a disproportionately larger shear-stress exceedance, because shear scales with
   * roughly the square of velocity. This is why a 3 % velocity overshoot is not the small number it appears to be.
   */
  @Test
  void smallVelocityExceedanceGivesLargerShearExceedance() {
    double tauLow = baseCase().setFlow(2.50, 0.025).calculate().getWallShearStressPa();
    double tauHigh = baseCase().setFlow(2.66, 0.025).calculate().getWallShearStressPa();

    double velocityIncrease = 2.66 / 2.50 - 1.0;
    double shearIncrease = tauHigh / tauLow - 1.0;

    // Blasius friction gives tau proportional to v^1.75, so the relative shear rise is ~1.75x the velocity rise.
    assertTrue(shearIncrease > 1.6 * velocityIncrease,
        "shear must rise much faster than velocity: " + shearIncrease + " vs " + velocityIncrease);
    assertEquals(0.115, shearIncrease, 0.02, "a 6.4 % velocity rise should give about 11-12 % more shear");
  }

  /**
   * About 1 % chromium must reduce susceptibility by roughly an order of magnitude, which is the quantitative basis for
   * specifying a low-alloy Cr-Mo steel in place of plain carbon steel.
   */
  @Test
  void chromiumGivesAnOrderOfMagnitudeImprovement() {
    FlowAcceleratedCorrosionResult carbonSteel = baseCase().setChromiumContent(CARBON_STEEL_CR).calculate();
    FlowAcceleratedCorrosionResult p11 = baseCase().setChromiumContent(P11_CR).calculate();

    double ratio = carbonSteel.ratioTo(p11);
    assertTrue(ratio > 8.0 && ratio < 12.0, "P11 should be roughly ten times more resistant, ratio was " + ratio);
  }

  /**
   * Raising the in-situ pH by one unit must reduce susceptibility by the configured number of solubility decades.
   */
  @Test
  void pHSensitivityFollowsTheConfiguredSlope() {
    FlowAcceleratedCorrosionResult low = baseCase().setInSituPH(7.0).calculate();
    FlowAcceleratedCorrosionResult high = baseCase().setInSituPH(8.0).calculate();
    assertEquals(10.0, low.ratioTo(high), 1.0e-6, "one pH unit must give one decade at the default slope");

    FlowAcceleratedCorrosionResult gentle = baseCase().setInSituPH(8.0).setPhSensitivity(0.5).calculate();
    FlowAcceleratedCorrosionResult gentleRef = baseCase().setInSituPH(7.0).setPhSensitivity(0.5).calculate();
    assertEquals(Math.pow(10.0, 0.5), gentleRef.ratioTo(gentle), 1.0e-6, "a custom slope must be honoured");
  }

  /**
   * Local geometry must scale susceptibility, so a weld at a bend outlet ranks well above a straight run. This is why
   * damage concentrates at welds and bends rather than being uniformly distributed.
   */
  @Test
  void weldAtBendRanksAboveStraightPipe() {
    FlowAcceleratedCorrosionResult straight = baseCase().setGeometry(FacGeometry.STRAIGHT_PIPE).calculate();
    FlowAcceleratedCorrosionResult bend = baseCase().setGeometry(FacGeometry.ELBOW_BEND).calculate();
    FlowAcceleratedCorrosionResult weldAtBend = baseCase().setGeometry(FacGeometry.WELD_AT_BEND).calculate();

    assertTrue(bend.ratioTo(straight) > 1.0, "a bend must rank above a straight run");
    assertTrue(weldAtBend.ratioTo(bend) > 1.0, "a weld at a bend must rank above a plain bend");
    assertEquals(FacGeometry.WELD_AT_BEND.getEnhancementFactor(), weldAtBend.getGeometryFactor(), 1.0e-12);
  }

  /**
   * The dominant factor must identify the lever with the most leverage, so an investigation can rank mitigation options
   * rather than treating all contributors as equal.
   */
  @Test
  void dominantFactorIdentifiesTheControllingLever() {
    FlowAcceleratedCorrosionResult carbonSteelAtBend = baseCase().setGeometry(FacGeometry.WELD_AT_BEND).setInSituPH(7.1)
        .calculate();
    assertEquals("geometry", carbonSteelAtBend.getDominantFactor(),
        "with near-neutral pH and carbon steel, local geometry should dominate");

    FlowAcceleratedCorrosionResult wellBuffered = baseCase().setGeometry(FacGeometry.STRAIGHT_PIPE).setInSituPH(9.0)
        .calculate();
    assertEquals("pH", wellBuffered.getDominantFactor(), "a strongly alkaline fluid should make pH the dominant lever");
  }

  /**
   * Cooling the surface away from the solubility peak must reduce susceptibility even at unchanged flow, so a flow
   * reduction that raises metal temperature can partly cancel its own benefit.
   */
  @Test
  void movingAwayFromThePeakTemperatureHelps() {
    FlowAcceleratedCorrosionResult atPeak = baseCase().setTemperature(150.0).calculate();
    FlowAcceleratedCorrosionResult cooler = baseCase().setTemperature(110.0).calculate();
    assertTrue(atPeak.ratioTo(cooler) > 1.0, "operating away from the solubility peak must reduce susceptibility");
  }

  /**
   * Limitations must always be disclosed, in particular that the index is not a wall-loss rate and that the model does
   * not represent erosion-corrosion.
   */
  @Test
  void limitationsAreAlwaysDisclosed() {
    FlowAcceleratedCorrosionResult r = baseCase().calculate();
    boolean notRate = false;
    boolean notErosion = false;
    for (int i = 0; i < r.getWarnings().size(); i++) {
      String w = r.getWarnings().get(i);
      if (w.contains("not a wall-loss rate")) {
        notRate = true;
      }
      if (w.contains("erosion-corrosion")) {
        notErosion = true;
      }
    }
    assertTrue(notRate, "the index must be disclosed as non-absolute");
    assertTrue(notErosion, "the distinction from erosion-corrosion must be disclosed");
  }

  /**
   * Missing or invalid inputs must be rejected.
   */
  @Test
  void invalidInputsAreRejected() {
    assertThrows(IllegalStateException.class, () -> new FlowAcceleratedCorrosion().setFlow(2.5, 0.025).calculate(),
        "missing fluid properties must fail");
    assertThrows(IllegalArgumentException.class, () -> new FlowAcceleratedCorrosion().setFlow(-1.0, 0.025),
        "a negative velocity must be rejected");
    assertThrows(IllegalArgumentException.class, () -> new FlowAcceleratedCorrosion().setInSituPH(15.0),
        "an out-of-range pH must be rejected");
    assertThrows(IllegalArgumentException.class, () -> new FlowAcceleratedCorrosion().setChromiumContent(-0.1),
        "a negative chromium content must be rejected");
    assertThrows(IllegalArgumentException.class, () -> new FlowAcceleratedCorrosion().setGeometry(null),
        "a null geometry must be rejected");
  }

  /**
   * The result must serialise to JSON for reporting.
   */
  @Test
  void resultSerialisesToJson() {
    String json = baseCase().calculate().toJson();
    assertTrue(json.contains("susceptibilityIndex"), "JSON must contain the index");
    assertTrue(json.contains("massTransferCoefficientMs"), "JSON must contain the mass-transfer coefficient");
  }
}
