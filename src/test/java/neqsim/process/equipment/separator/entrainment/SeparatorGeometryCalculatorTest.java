package neqsim.process.equipment.separator.entrainment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SeparatorGeometryCalculator}.
 *
 * @author NeqSim team
 * @version 1.0
 */
class SeparatorGeometryCalculatorTest {

  /**
   * Tests horizontal two-phase geometry calculation.
   */
  @Test
  void testHorizontalTwoPhase() {
    SeparatorGeometryCalculator calc = new SeparatorGeometryCalculator();
    calc.setOrientation("horizontal");
    calc.setInternalDiameter(2.0);
    calc.setTangentToTangentLength(6.0);
    calc.setNormalLiquidLevel(0.5);

    calc.calculate(3.0, 0.1);

    assertTrue(calc.getGasArea() > 0, "Gas area should be positive");
    assertTrue(calc.getLiquidArea() > 0, "Liquid area should be positive");
    assertTrue(calc.getEffectiveGasSettlingHeight() > 0, "Settling height should be positive");
    assertTrue(calc.getGasResidenceTime() > 0, "Gas residence time should be positive");
    assertTrue(calc.getLiquidResidenceTime() > 0, "Liquid residence time should be positive");

    // For 50% liquid level, gas and liquid areas should be approximately equal
    double totalArea = Math.PI * 2.0 * 2.0 / 4.0;
    assertEquals(totalArea / 2.0, calc.getGasArea(), totalArea * 0.05,
        "Gas area should be ~50% of total at 50% level");
  }

  /**
   * Tests vertical two-phase geometry calculation.
   */
  @Test
  void testVerticalTwoPhase() {
    SeparatorGeometryCalculator calc = new SeparatorGeometryCalculator();
    calc.setOrientation("vertical");
    calc.setInternalDiameter(1.5);
    calc.setTangentToTangentLength(5.0);
    calc.setNormalLiquidLevel(0.4);

    calc.calculate(5.0, 0.05);

    assertTrue(calc.getGasArea() > 0, "Gas area should be positive");
    assertTrue(calc.getEffectiveGasSettlingHeight() > 0, "Settling height should be positive");
    assertTrue(calc.getGasResidenceTime() > 0, "Gas residence time should be positive");

    // Vertical: gas area = full cross section
    double fullArea = Math.PI * 1.5 * 1.5 / 4.0;
    assertEquals(fullArea, calc.getGasArea(), fullArea * 0.01,
        "Vertical gas area should be full cross section");
  }

  /**
   * Tests three-phase geometry calculation.
   */
  @Test
  void testHorizontalThreePhase() {
    SeparatorGeometryCalculator calc = new SeparatorGeometryCalculator();
    calc.setOrientation("horizontal");
    calc.setInternalDiameter(2.5);
    calc.setTangentToTangentLength(8.0);
    calc.setNormalLiquidLevel(0.6);

    // oilLevelFraction = fraction of liquid that is oil
    calc.calculateThreePhase(3.0, 0.10, 0.05, 0.6);

    assertTrue(calc.getOilPadThickness() > 0, "Oil pad thickness should be positive");
    assertTrue(calc.getWaterLayerHeight() > 0, "Water layer height should be positive");
  }

  /**
   * Tests K-factor calculation (Souders-Brown) — static method.
   */
  @Test
  void testKFactorCalculation() {
    // K = vg * sqrt(rhoG / (rhoL - rhoG))
    double gasVelocity = 1.5; // m/s
    double gasDensity = 50.0;
    double liquidDensity = 800.0;

    double kFactor =
        SeparatorGeometryCalculator.calcKFactor(gasVelocity, gasDensity, liquidDensity);
    assertTrue(kFactor > 0, "K-factor should be positive");
    // Typical K-factor range for gas-liquid separators: 0.01 - 0.3 m/s
    assertTrue(kFactor < 1.0, "K-factor should be in reasonable range (< 1.0 m/s)");
  }

  /**
   * Tests segment area calculation for horizontal vessel.
   */
  @Test
  void testSegmentArea() {
    SeparatorGeometryCalculator calc = new SeparatorGeometryCalculator();
    double fullArea = Math.PI * 1.0 * 1.0 / 4.0; // diameter = 1.0
    calc.setInternalDiameter(1.0);
    calc.setTangentToTangentLength(3.0);

    // At 50% fill, segment area should be half the circle
    calc.setNormalLiquidLevel(0.5);
    calc.setOrientation("horizontal");
    calc.calculate(3.0, 0.1);

    double liquidArea = calc.getLiquidArea();
    assertEquals(fullArea / 2.0, liquidArea, fullArea * 0.02,
        "At 50% fill, liquid area should be half");
  }

  /**
   * Tests effective liquid settling height for horizontal vessel.
   */
  @Test
  void testLiquidSettlingHeight() {
    SeparatorGeometryCalculator calc = new SeparatorGeometryCalculator();
    calc.setOrientation("horizontal");
    calc.setInternalDiameter(2.0);
    calc.setTangentToTangentLength(6.0);
    calc.setNormalLiquidLevel(0.3);

    calc.calculate(3.0, 0.1);

    double liquidSettlingHeight = calc.getEffectiveLiquidSettlingHeight();
    assertTrue(liquidSettlingHeight > 0 && liquidSettlingHeight < 2.0,
        "Liquid settling height should be between 0 and diameter");
  }

  /**
   * Tests that gas velocity affects residence time.
   */
  @Test
  void testGasVelocityEffectOnResidenceTime() {
    SeparatorGeometryCalculator calc = new SeparatorGeometryCalculator();
    calc.setOrientation("horizontal");
    calc.setInternalDiameter(2.0);
    calc.setTangentToTangentLength(6.0);
    calc.setNormalLiquidLevel(0.5);

    // Low velocity
    calc.calculate(1.0, 0.1);
    double lowVelResTime = calc.getGasResidenceTime();

    // High velocity
    calc.calculate(5.0, 0.1);
    double highVelResTime = calc.getGasResidenceTime();

    assertTrue(lowVelResTime > highVelResTime, "Higher gas velocity should reduce residence time");
  }
}
