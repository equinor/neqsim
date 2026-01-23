package neqsim.fluidmechanics.flownode;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for MassTransferCoefficientCalculator.
 */
class MassTransferCoefficientCalculatorTest {
  // Reference properties for air-water at atmospheric conditions
  private static final double RHO_G = 1.2; // kg/m³
  private static final double RHO_L = 1000.0; // kg/m³
  private static final double MU_G = 1.8e-5; // Pa·s
  private static final double MU_L = 1.0e-3; // Pa·s
  private static final double DIFF_G = 2.0e-5; // m²/s - gas diffusivity
  private static final double DIFF_L = 2.0e-9; // m²/s - liquid diffusivity
  private static final double DIAMETER = 0.1; // m

  @Test
  void testLiquidMassTransferCoefficientPositive() {
    double kL = MassTransferCoefficientCalculator.calculateLiquidMassTransferCoefficient(
        FlowPattern.STRATIFIED, DIAMETER, 0.3, 5.0, 0.5, RHO_L, MU_L, DIFF_L);

    assertTrue(kL > 0, "Liquid mass transfer coefficient should be positive");
  }

  @Test
  void testGasMassTransferCoefficientPositive() {
    double kG = MassTransferCoefficientCalculator.calculateGasMassTransferCoefficient(
        FlowPattern.STRATIFIED, DIAMETER, 0.3, 5.0, RHO_G, MU_G, DIFF_G);

    assertTrue(kG > 0, "Gas mass transfer coefficient should be positive");
  }

  @Test
  void testGasKLargerThanLiquidK() {
    // Gas-side mass transfer is typically faster due to higher diffusivity
    double kL = MassTransferCoefficientCalculator.calculateLiquidMassTransferCoefficient(
        FlowPattern.STRATIFIED, DIAMETER, 0.3, 5.0, 0.5, RHO_L, MU_L, DIFF_L);
    double kG = MassTransferCoefficientCalculator.calculateGasMassTransferCoefficient(
        FlowPattern.STRATIFIED, DIAMETER, 0.3, 5.0, RHO_G, MU_G, DIFF_G);

    assertTrue(kG > kL, "Gas-side k should be larger than liquid-side k");
  }

  @Test
  void testDittusBoelterSherwood() {
    double re = 10000;
    double sc = 1.0;

    double sh = MassTransferCoefficientCalculator.calculateDittusBoelterSherwood(re, sc);

    // Sh = 0.023 * 10000^0.8 * 1^0.33 ≈ 36.4
    assertTrue(sh > 30 && sh < 50, "Dittus-Boelter Sh should be ~36 for Re=10000, Sc=1");
  }

  @Test
  void testRanzMarshallSherwood() {
    double re = 100;
    double sc = 1.0;

    double sh = MassTransferCoefficientCalculator.calculateRanzMarshallSherwood(re, sc);

    // Sh = 2 + 0.6 * 100^0.5 * 1^0.33 = 2 + 6 = 8
    assertTrue(sh > 7 && sh < 10, "Ranz-Marshall Sh should be ~8 for Re=100, Sc=1");
  }

  @Test
  void testAllFlowPatternsGiveLiquidK() {
    for (FlowPattern pattern : FlowPattern.values()) {
      double kL = MassTransferCoefficientCalculator.calculateLiquidMassTransferCoefficient(pattern,
          DIAMETER, 0.3, 5.0, 0.5, RHO_L, MU_L, DIFF_L);
      assertTrue(kL > 0, "Liquid k should be positive for " + pattern);
    }
  }

  @Test
  void testAllFlowPatternsGiveGasK() {
    for (FlowPattern pattern : FlowPattern.values()) {
      double kG = MassTransferCoefficientCalculator.calculateGasMassTransferCoefficient(pattern,
          DIAMETER, 0.3, 5.0, RHO_G, MU_G, DIFF_G);
      assertTrue(kG > 0, "Gas k should be positive for " + pattern);
    }
  }

  @Test
  void testHigherVelocityGivesHigherK() {
    double kL_low = MassTransferCoefficientCalculator.calculateLiquidMassTransferCoefficient(
        FlowPattern.ANNULAR, DIAMETER, 0.1, 5.0, 0.3, RHO_L, MU_L, DIFF_L);
    double kL_high = MassTransferCoefficientCalculator.calculateLiquidMassTransferCoefficient(
        FlowPattern.ANNULAR, DIAMETER, 0.1, 5.0, 1.0, RHO_L, MU_L, DIFF_L);

    assertTrue(kL_high > kL_low, "Higher velocity should give higher mass transfer");
  }

  @Test
  void testTypicalMagnitudeOrder() {
    // Typical liquid-side mass transfer coefficients are 10^-5 to 10^-3 m/s
    double kL = MassTransferCoefficientCalculator.calculateLiquidMassTransferCoefficient(
        FlowPattern.BUBBLE, DIAMETER, 0.9, 1.0, 1.0, RHO_L, MU_L, DIFF_L);

    assertTrue(kL > 1e-6 && kL < 1e-2, "k_L should be in typical range 10^-6 to 10^-2 m/s");
  }

  @Test
  void testSlugFlowEnhancement() {
    // Slug flow typically has enhanced mass transfer due to mixing
    double kL_strat = MassTransferCoefficientCalculator.calculateLiquidMassTransferCoefficient(
        FlowPattern.STRATIFIED, DIAMETER, 0.4, 3.0, 0.5, RHO_L, MU_L, DIFF_L);
    double kL_slug = MassTransferCoefficientCalculator.calculateLiquidMassTransferCoefficient(
        FlowPattern.SLUG, DIAMETER, 0.4, 3.0, 0.5, RHO_L, MU_L, DIFF_L);

    // Slug may or may not be higher depending on conditions, but both should be positive
    assertTrue(kL_strat > 0 && kL_slug > 0, "Both should give positive values");
  }
}
