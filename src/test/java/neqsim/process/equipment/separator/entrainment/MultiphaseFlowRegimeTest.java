package neqsim.process.equipment.separator.entrainment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MultiphaseFlowRegime}.
 *
 * @author NeqSim team
 * @version 1.0
 */
class MultiphaseFlowRegimeTest {

  /**
   * Helper to configure a MultiphaseFlowRegime with common properties.
   *
   * @param calc the calculator to configure
   * @param vsg gas superficial velocity [m/s]
   * @param vsl liquid superficial velocity [m/s]
   * @param gasDensity gas density [kg/m3]
   * @param liquidDensity liquid density [kg/m3]
   * @param gasViscosity gas viscosity [Pa.s]
   * @param liquidViscosity liquid viscosity [Pa.s]
   * @param surfaceTension surface tension [N/m]
   * @param pipeDiameter pipe diameter [m]
   * @param pipeOrientation "horizontal" or "vertical"
   */
  private void configure(MultiphaseFlowRegime calc, double vsg, double vsl, double gasDensity,
      double liquidDensity, double gasViscosity, double liquidViscosity, double surfaceTension,
      double pipeDiameter, String pipeOrientation) {
    calc.setGasSuperficialVelocity(vsg);
    calc.setLiquidSuperficialVelocity(vsl);
    calc.setGasDensity(gasDensity);
    calc.setLiquidDensity(liquidDensity);
    calc.setGasViscosity(gasViscosity);
    calc.setLiquidViscosity(liquidViscosity);
    calc.setSurfaceTension(surfaceTension);
    calc.setPipeDiameter(pipeDiameter);
    calc.setPipeOrientation(pipeOrientation);
  }

  /**
   * Tests horizontal flow regime prediction for stratified (low gas and liquid velocities).
   */
  @Test
  void testPredictHorizontalStratified() {
    MultiphaseFlowRegime calc = new MultiphaseFlowRegime();
    // Very low velocities for stratified flow on Mandhane map
    configure(calc, 0.3, 0.001, 5.0, 800.0, 1.0e-5, 1.0e-3, 0.025, 0.15, "horizontal");
    calc.predict();
    MultiphaseFlowRegime.FlowRegime regime = calc.getPredictedRegime();
    assertNotNull(regime);
    assertTrue(
        regime == MultiphaseFlowRegime.FlowRegime.STRATIFIED_SMOOTH
            || regime == MultiphaseFlowRegime.FlowRegime.STRATIFIED_WAVY
            || regime == MultiphaseFlowRegime.FlowRegime.SLUG,
        "Low velocities should give stratified or slug regime, got: " + regime);
  }

  /**
   * Tests horizontal flow regime prediction for annular flow (high gas velocity).
   */
  @Test
  void testPredictHorizontalAnnular() {
    MultiphaseFlowRegime calc = new MultiphaseFlowRegime();
    configure(calc, 25.0, 0.01, 50.0, 800.0, 1.0e-5, 1.0e-3, 0.025, 0.15, "horizontal");
    calc.predict();
    MultiphaseFlowRegime.FlowRegime regime = calc.getPredictedRegime();
    assertNotNull(regime);
    assertTrue(
        regime == MultiphaseFlowRegime.FlowRegime.ANNULAR
            || regime == MultiphaseFlowRegime.FlowRegime.ANNULAR_MIST,
        "High gas velocity should give annular, got: " + regime);
  }

  /**
   * Tests vertical flow regime prediction for bubble flow.
   */
  @Test
  void testPredictVerticalBubble() {
    MultiphaseFlowRegime calc = new MultiphaseFlowRegime();
    configure(calc, 0.2, 1.5, 50.0, 800.0, 1.0e-5, 1.0e-3, 0.025, 0.15, "vertical");
    calc.predict();
    MultiphaseFlowRegime.FlowRegime regime = calc.getPredictedRegime();
    assertNotNull(regime);
    assertTrue(
        regime == MultiphaseFlowRegime.FlowRegime.BUBBLE
            || regime == MultiphaseFlowRegime.FlowRegime.DISPERSED_BUBBLE,
        "Low gas velocity vertical should give bubble, got: " + regime);
  }

  /**
   * Tests vertical flow regime prediction for churn/annular at high gas velocity.
   */
  @Test
  void testPredictVerticalHighGas() {
    MultiphaseFlowRegime calc = new MultiphaseFlowRegime();
    configure(calc, 20.0, 0.1, 50.0, 800.0, 1.0e-5, 1.0e-3, 0.025, 0.15, "vertical");
    calc.predict();
    MultiphaseFlowRegime.FlowRegime regime = calc.getPredictedRegime();
    assertNotNull(regime);
    assertTrue(
        regime == MultiphaseFlowRegime.FlowRegime.ANNULAR
            || regime == MultiphaseFlowRegime.FlowRegime.CHURN
            || regime == MultiphaseFlowRegime.FlowRegime.ANNULAR_MIST,
        "High gas vertical should give annular/churn, got: " + regime);
  }

  /**
   * Tests DSD generation for annular flow — should produce a valid distribution.
   */
  @Test
  void testGenerateDSDForAnnularRegime() {
    MultiphaseFlowRegime calc = new MultiphaseFlowRegime();
    configure(calc, 15.0, 0.01, 50.0, 800.0, 1.0e-5, 1.0e-3, 0.025, 0.15, "horizontal");
    calc.predict();
    DropletSizeDistribution dsd = calc.getGeneratedDSD();
    assertNotNull(dsd, "DSD should not be null after prediction");
    assertTrue(dsd.getD50() > 0, "D50 should be positive");
    assertTrue(dsd.getD50() < 1.0e-2, "D50 should be reasonable (< 10 mm)");
    assertTrue(dsd.getSauterMeanDiameter() > 0, "D32 should be positive");
  }

  /**
   * Tests DSD generation for slug flow.
   */
  @Test
  void testGenerateDSDForSlugRegime() {
    MultiphaseFlowRegime calc = new MultiphaseFlowRegime();
    configure(calc, 5.0, 1.0, 50.0, 800.0, 1.0e-5, 1.0e-3, 0.025, 0.15, "horizontal");
    calc.predict();
    DropletSizeDistribution dsd = calc.getGeneratedDSD();
    assertNotNull(dsd, "DSD should not be null after prediction");
    assertTrue(dsd.getD50() > 0, "D50 should be positive");
  }

  /**
   * Tests entrained liquid fraction calculation (Oliemans et al.).
   */
  @Test
  void testCalcEntrainedLiquidFraction() {
    MultiphaseFlowRegime calc = new MultiphaseFlowRegime();
    configure(calc, 15.0, 0.1, 50.0, 800.0, 1.0e-5, 1.0e-3, 0.025, 0.15, "horizontal");
    double fraction = calc.calcEntrainedLiquidFraction();
    assertTrue(fraction >= 0.0 && fraction <= 1.0,
        "Entrained fraction should be [0,1], got: " + fraction);
  }
}
