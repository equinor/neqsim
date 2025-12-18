package neqsim.fluidmechanics.flownode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for HeatTransferCoefficientCalculator.
 */
class HeatTransferCoefficientCalculatorTest {

  // Typical fluid properties for testing
  private static final double DIAMETER = 0.1; // m
  private static final double RHO_L = 800.0; // kg/m³
  private static final double RHO_G = 50.0; // kg/m³
  private static final double MU_L = 0.001; // Pa·s
  private static final double MU_G = 1.5e-5; // Pa·s
  private static final double CP_L = 2000.0; // J/(kg·K)
  private static final double CP_G = 1000.0; // J/(kg·K)
  private static final double K_L = 0.15; // W/(m·K)
  private static final double K_G = 0.025; // W/(m·K)

  @Test
  void testLiquidHeatTransferCoefficientStratified() {
    double hL = HeatTransferCoefficientCalculator.calculateLiquidHeatTransferCoefficient(
        FlowPattern.STRATIFIED, DIAMETER, 0.3, 5.0, 0.5, RHO_L, MU_L, CP_L, K_L);
    assertTrue(hL > 0, "Liquid heat transfer coefficient should be positive");
    assertTrue(hL < 10000, "h_L should be in reasonable range");
  }

  @Test
  void testGasHeatTransferCoefficientStratified() {
    double hG = HeatTransferCoefficientCalculator.calculateGasHeatTransferCoefficient(
        FlowPattern.STRATIFIED, DIAMETER, 0.3, 5.0, RHO_G, MU_G, CP_G, K_G);
    assertTrue(hG > 0, "Gas heat transfer coefficient should be positive");
    assertTrue(hG < 5000, "h_G should be in reasonable range");
  }

  @Test
  void testOverallInterphaseCoefficient() {
    double hL = 500.0;
    double hG = 200.0;

    double u = HeatTransferCoefficientCalculator.calculateOverallInterphaseCoefficient(hL, hG);
    // Expected: 1/(1/500 + 1/200) = 1/(0.002 + 0.005) = 1/0.007 ≈ 142.86
    assertEquals(142.86, u, 1.0);
  }

  @Test
  void testDittusBoelterNusselt() {
    double re = 50000;
    double pr = 5.0;

    double nuHeating =
        HeatTransferCoefficientCalculator.calculateDittusBoelterNusselt(re, pr, true);
    double nuCooling =
        HeatTransferCoefficientCalculator.calculateDittusBoelterNusselt(re, pr, false);

    assertTrue(nuHeating > nuCooling, "Nu for heating should be greater than for cooling");
    assertTrue(nuHeating > 100, "Nu should be significant for turbulent flow");
  }

  @Test
  void testLaminarNusselt() {
    double nuConstTemp = HeatTransferCoefficientCalculator.calculateLaminarNusselt(true);
    double nuConstFlux = HeatTransferCoefficientCalculator.calculateLaminarNusselt(false);

    assertEquals(3.66, nuConstTemp, 0.01);
    assertEquals(4.36, nuConstFlux, 0.01);
  }

  @Test
  void testAnnularFlowHeatTransfer() {
    double hL = HeatTransferCoefficientCalculator.calculateLiquidHeatTransferCoefficient(
        FlowPattern.ANNULAR, DIAMETER, 0.1, 10.0, 0.2, RHO_L, MU_L, CP_L, K_L);
    assertTrue(hL > 0, "h_L for annular flow should be positive");
  }

  @Test
  void testBubbleFlowHeatTransfer() {
    double hL = HeatTransferCoefficientCalculator.calculateLiquidHeatTransferCoefficient(
        FlowPattern.BUBBLE, DIAMETER, 0.9, 1.0, 1.0, RHO_L, MU_L, CP_L, K_L);
    assertTrue(hL > 0, "h_L for bubble flow should be positive");
  }

  @Test
  void testSlugFlowHeatTransfer() {
    double hL = HeatTransferCoefficientCalculator.calculateLiquidHeatTransferCoefficient(
        FlowPattern.SLUG, DIAMETER, 0.5, 3.0, 0.5, RHO_L, MU_L, CP_L, K_L);
    assertTrue(hL > 0, "h_L for slug flow should be positive");
  }

  @Test
  void testDropletFlowHeatTransfer() {
    double hG = HeatTransferCoefficientCalculator.calculateGasHeatTransferCoefficient(
        FlowPattern.DROPLET, DIAMETER, 0.05, 20.0, RHO_G, MU_G, CP_G, K_G);
    assertTrue(hG > 0, "h_G for droplet flow should be positive");
  }

  @Test
  void testStantonNumber() {
    double h = 500.0;
    double rho = 800.0;
    double u = 1.0;
    double cp = 2000.0;

    double st = HeatTransferCoefficientCalculator.calculateStantonNumber(h, rho, u, cp);
    // Expected: 500 / (800 * 1.0 * 2000) = 500 / 1,600,000 = 3.125e-4
    assertEquals(3.125e-4, st, 1e-6);
  }

  @Test
  void testCondensationHTC() {
    double hfg = 2e6; // J/kg (latent heat of water)
    double deltaT = 10.0; // K

    double h = HeatTransferCoefficientCalculator.calculateCondensationHTC(RHO_L, RHO_G, K_L, hfg,
        CP_L, MU_L, DIAMETER, deltaT);
    assertTrue(h > 0, "Condensation HTC should be positive");
    assertTrue(h > 100, "Condensation HTC should be significant");
  }

  @Test
  void testInvalidInputsReturnZero() {
    // Test with zero diameter
    double h1 = HeatTransferCoefficientCalculator.calculateLiquidHeatTransferCoefficient(
        FlowPattern.STRATIFIED, 0, 0.3, 5.0, 0.5, RHO_L, MU_L, CP_L, K_L);
    assertEquals(0.0, h1);

    // Test with zero conductivity
    double h2 = HeatTransferCoefficientCalculator.calculateLiquidHeatTransferCoefficient(
        FlowPattern.STRATIFIED, DIAMETER, 0.3, 5.0, 0.5, RHO_L, MU_L, CP_L, 0.0);
    assertEquals(0.0, h2);

    // Test with negative density
    double h3 = HeatTransferCoefficientCalculator.calculateGasHeatTransferCoefficient(
        FlowPattern.STRATIFIED, DIAMETER, 0.3, 5.0, -RHO_G, MU_G, CP_G, K_G);
    assertEquals(0.0, h3);
  }

  @Test
  void testGnielinskiNusselt() {
    double re = 20000;
    double pr = 5.0;
    double f = 0.02; // Friction factor

    double nu = HeatTransferCoefficientCalculator.calculateGnielinskiNusselt(re, pr, f);
    assertTrue(nu > 3.66, "Nu should be greater than laminar value");
    assertTrue(nu < 1000, "Nu should be in reasonable range");
  }

  @Test
  void testChurnFlowHeatTransfer() {
    double hL = HeatTransferCoefficientCalculator.calculateLiquidHeatTransferCoefficient(
        FlowPattern.CHURN, DIAMETER, 0.4, 8.0, 1.0, RHO_L, MU_L, CP_L, K_L);
    assertTrue(hL > 0, "h_L for churn flow should be positive");

    double hG = HeatTransferCoefficientCalculator.calculateGasHeatTransferCoefficient(
        FlowPattern.CHURN, DIAMETER, 0.4, 8.0, RHO_G, MU_G, CP_G, K_G);
    assertTrue(hG > 0, "h_G for churn flow should be positive");
  }

  @Test
  void testFlowPatternDependence() {
    // Compare heat transfer coefficients for different flow patterns
    // at the same conditions - they should generally differ

    double hStratified = HeatTransferCoefficientCalculator.calculateLiquidHeatTransferCoefficient(
        FlowPattern.STRATIFIED, DIAMETER, 0.3, 5.0, 0.5, RHO_L, MU_L, CP_L, K_L);

    double hAnnular = HeatTransferCoefficientCalculator.calculateLiquidHeatTransferCoefficient(
        FlowPattern.ANNULAR, DIAMETER, 0.3, 5.0, 0.5, RHO_L, MU_L, CP_L, K_L);

    // Annular flow typically has higher heat transfer than stratified
    // due to thin film and turbulence
    assertTrue(hAnnular != hStratified, "Different flow patterns should give different h values");
  }
}
