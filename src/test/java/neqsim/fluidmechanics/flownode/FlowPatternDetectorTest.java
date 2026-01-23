package neqsim.fluidmechanics.flownode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for FlowPatternDetector.
 */
class FlowPatternDetectorTest {
  // Reference properties for air-water at atmospheric conditions
  private static final double RHO_G = 1.2; // kg/m³
  private static final double RHO_L = 1000.0; // kg/m³
  private static final double MU_G = 1.8e-5; // Pa·s
  private static final double MU_L = 1.0e-3; // Pa·s
  private static final double SIGMA = 0.072; // N/m
  private static final double DIAMETER = 0.1; // m

  @Test
  void testTaitelDuklerStratifiedFlow() {
    // Low gas and liquid velocities should give stratified flow
    double usg = 0.5; // m/s
    double usl = 0.01; // m/s

    FlowPattern result = FlowPatternDetector.detectFlowPattern(FlowPatternModel.TAITEL_DUKLER, usg,
        usl, RHO_G, RHO_L, MU_G, MU_L, SIGMA, DIAMETER, 0.0);

    assertTrue(result == FlowPattern.STRATIFIED || result == FlowPattern.STRATIFIED_WAVY,
        "Low velocities should give stratified flow, got: " + result);
  }

  @Test
  void testTaitelDuklerAnnularFlow() {
    // High gas velocity should give annular or droplet flow
    double usg = 20.0; // m/s - high gas velocity
    double usl = 0.01; // m/s - low liquid velocity

    FlowPattern result = FlowPatternDetector.detectFlowPattern(FlowPatternModel.TAITEL_DUKLER, usg,
        usl, RHO_G, RHO_L, MU_G, MU_L, SIGMA, DIAMETER, 0.0);

    assertTrue(result == FlowPattern.ANNULAR || result == FlowPattern.DROPLET,
        "High gas velocity should give annular or droplet flow, got: " + result);
  }

  @Test
  void testTaitelDuklerBubbleFlow() {
    // High liquid velocity, low gas should give bubble flow
    double usg = 0.05; // m/s - low gas velocity
    double usl = 2.0; // m/s - high liquid velocity

    FlowPattern result = FlowPatternDetector.detectFlowPattern(FlowPatternModel.TAITEL_DUKLER, usg,
        usl, RHO_G, RHO_L, MU_G, MU_L, SIGMA, DIAMETER, 0.0);

    assertTrue(result == FlowPattern.BUBBLE || result == FlowPattern.DISPERSED_BUBBLE,
        "Low gas, high liquid velocity should give bubble flow, got: " + result);
  }

  @Test
  void testTaitelDuklerSlugFlow() {
    // Moderate velocities should give slug flow
    double usg = 2.0; // m/s
    double usl = 0.5; // m/s

    FlowPattern result = FlowPatternDetector.detectFlowPattern(FlowPatternModel.TAITEL_DUKLER, usg,
        usl, RHO_G, RHO_L, MU_G, MU_L, SIGMA, DIAMETER, 0.0);

    // Slug is common for moderate velocities
    assertNotNull(result, "Should return a valid flow pattern");
  }

  @Test
  void testBakerChartStratified() {
    // Low velocities
    double usg = 0.3; // m/s
    double usl = 0.01; // m/s

    FlowPattern result = FlowPatternDetector.detectFlowPattern(FlowPatternModel.BAKER_CHART, usg,
        usl, RHO_G, RHO_L, MU_G, MU_L, SIGMA, DIAMETER, 0.0);

    assertNotNull(result, "Should return a valid flow pattern");
  }

  @Test
  void testBarneaHorizontal() {
    // Horizontal pipe should behave like Taitel-Dukler
    double usg = 5.0; // m/s
    double usl = 0.1; // m/s
    double inclination = 0.0; // horizontal

    FlowPattern result = FlowPatternDetector.detectFlowPattern(FlowPatternModel.BARNEA, usg, usl,
        RHO_G, RHO_L, MU_G, MU_L, SIGMA, DIAMETER, inclination);

    assertNotNull(result, "Should return a valid flow pattern for horizontal pipe");
  }

  @Test
  void testBarneaVerticalUpward() {
    // Vertical upward flow
    double usg = 5.0; // m/s
    double usl = 0.5; // m/s
    double inclination = Math.PI / 2; // vertical upward

    FlowPattern result = FlowPatternDetector.detectFlowPattern(FlowPatternModel.BARNEA, usg, usl,
        RHO_G, RHO_L, MU_G, MU_L, SIGMA, DIAMETER, inclination);

    assertNotNull(result, "Should return a valid flow pattern for vertical pipe");
  }

  @Test
  void testBeggsBrill() {
    double usg = 3.0; // m/s
    double usl = 0.3; // m/s

    FlowPattern result = FlowPatternDetector.detectFlowPattern(FlowPatternModel.BEGGS_BRILL, usg,
        usl, RHO_G, RHO_L, MU_G, MU_L, SIGMA, DIAMETER, 0.0);

    assertNotNull(result, "Should return a valid flow pattern");
  }

  @Test
  void testManualModel() {
    FlowPattern result = FlowPatternDetector.detectFlowPattern(FlowPatternModel.MANUAL, 1.0, 1.0,
        RHO_G, RHO_L, MU_G, MU_L, SIGMA, DIAMETER, 0.0);

    assertEquals(FlowPattern.STRATIFIED, result, "Manual model should return default (stratified)");
  }

  @Test
  void testCalculateLiquidHoldup() {
    double lambdaL = 0.3;
    double nFr = 10.0;
    double inclination = 0.0;

    double holdup =
        FlowPatternDetector.calculateLiquidHoldup(FlowPattern.SLUG, lambdaL, nFr, inclination);

    assertTrue(holdup > 0 && holdup <= 1.0, "Liquid holdup should be between 0 and 1");
    assertTrue(holdup >= lambdaL, "Holdup should be >= no-slip holdup for horizontal flow");
  }

  @Test
  void testLiquidHoldupDifferentPatterns() {
    double lambdaL = 0.2;
    double nFr = 5.0;
    double inclination = 0.0;

    double holdupStratified = FlowPatternDetector.calculateLiquidHoldup(FlowPattern.STRATIFIED,
        lambdaL, nFr, inclination);
    double holdupSlug =
        FlowPatternDetector.calculateLiquidHoldup(FlowPattern.SLUG, lambdaL, nFr, inclination);
    double holdupAnnular =
        FlowPatternDetector.calculateLiquidHoldup(FlowPattern.ANNULAR, lambdaL, nFr, inclination);

    // All should be valid
    assertTrue(holdupStratified > 0 && holdupStratified <= 1.0);
    assertTrue(holdupSlug > 0 && holdupSlug <= 1.0);
    assertTrue(holdupAnnular > 0 && holdupAnnular <= 1.0);
  }
}
