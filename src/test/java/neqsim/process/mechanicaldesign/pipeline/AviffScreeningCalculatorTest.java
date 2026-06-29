package neqsim.process.mechanicaldesign.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AviffScreeningCalculator}.
 */
public class AviffScreeningCalculatorTest {
  /**
   * A low-energy single-phase liquid line should screen as LOW.
   */
  @Test
  void testLowEnergyScreensLow() {
    AviffScreeningCalculator calc = new AviffScreeningCalculator();
    calc.setFlowConditions(800.0, 1.5, 0.0);
    calc.setPipeInternalDiameter(0.1);
    calc.setSupportArrangement(AviffScreeningCalculator.SupportArrangement.STIFF);
    calc.calcScreening();

    assertEquals(800.0 * 1.5 * 1.5, calc.getKineticEnergy(), 1e-6, "Kinetic energy should be rho*v^2");
    assertEquals(2.0, calc.getFatigueCorrectionFactor(), 1e-9, "Stiff arrangement FCF should be 2.0");
    assertTrue(calc.getLikelihoodOfFailure() < 0.5, "Low-energy line should screen LOW");
    assertEquals("LOW", calc.getLikelihoodBand());
    assertNotNull(calc.toJson());
  }

  /**
   * A high-energy gas line on a flexible support should screen HIGH.
   */
  @Test
  void testHighEnergyScreensHigh() {
    AviffScreeningCalculator calc = new AviffScreeningCalculator();
    calc.setFlowConditions(120.0, 30.0, 1.0);
    calc.setPipeInternalDiameter(0.1);
    calc.setSupportArrangement(AviffScreeningCalculator.SupportArrangement.FLEXIBLE);
    calc.calcScreening();

    assertEquals(0.5, calc.getFatigueCorrectionFactor(), 1e-9, "Flexible arrangement FCF should be 0.5");
    assertTrue(calc.getLikelihoodOfFailure() >= 1.0, "High-energy flexible line should screen HIGH");
    assertEquals("HIGH", calc.getLikelihoodBand());
  }

  /**
   * The gas void fraction correction should amplify the intermittent multiphase region relative to single phase.
   */
  @Test
  void testGvfCorrectionAmplifiesMultiphase() {
    AviffScreeningCalculator single = new AviffScreeningCalculator();
    single.setFlowConditions(120.0, 12.0, 1.0);
    single.setPipeInternalDiameter(0.15);
    single.calcScreening();

    AviffScreeningCalculator multi = new AviffScreeningCalculator();
    multi.setFlowConditions(120.0, 12.0, 0.75);
    multi.setPipeInternalDiameter(0.15);
    multi.calcScreening();

    assertTrue(multi.getGvfCorrection() > single.getGvfCorrection(),
        "Intermittent multiphase region should have a larger GVF correction");
    assertTrue(multi.getLikelihoodOfFailure() > single.getLikelihoodOfFailure(),
        "Multiphase amplification should raise the LOF");
  }

  /**
   * Larger pipe bores should tolerate a higher kinetic energy (larger FVF).
   */
  @Test
  void testLargerBoreHasLargerFvf() {
    AviffScreeningCalculator small = new AviffScreeningCalculator();
    small.setFlowConditions(120.0, 12.0, 1.0);
    small.setPipeInternalDiameter(0.05);
    small.calcScreening();

    AviffScreeningCalculator large = new AviffScreeningCalculator();
    large.setFlowConditions(120.0, 12.0, 1.0);
    large.setPipeInternalDiameter(0.30);
    large.calcScreening();

    assertTrue(large.getFatigueVibrationFactor() > small.getFatigueVibrationFactor(),
        "Larger bore should have a higher Fatigue Vibration Factor");
  }
}
