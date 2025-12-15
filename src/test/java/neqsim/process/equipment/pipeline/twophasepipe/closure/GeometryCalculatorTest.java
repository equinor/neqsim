package neqsim.process.equipment.pipeline.twophasepipe.closure;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator.StratifiedGeometry;

/**
 * Unit tests for GeometryCalculator.
 * 
 * Tests stratified flow geometry calculations for circular pipes.
 */
public class GeometryCalculatorTest {

  private static final double TOLERANCE = 1e-6;
  private GeometryCalculator calculator;

  @BeforeEach
  void setUp() {
    calculator = new GeometryCalculator();
  }

  @Test
  void testCalculateFromLiquidLevelEmpty() {
    double diameter = 0.1;
    double liquidLevel = 0.0;

    StratifiedGeometry geom = calculator.calculateFromLiquidLevel(liquidLevel, diameter);

    assertEquals(0.0, geom.liquidArea, TOLERANCE);
    assertEquals(0.0, geom.liquidHoldup, TOLERANCE);
  }

  @Test
  void testCalculateFromLiquidLevelFull() {
    double diameter = 0.1;
    double liquidLevel = diameter;

    StratifiedGeometry geom = calculator.calculateFromLiquidLevel(liquidLevel, diameter);

    double expectedArea = Math.PI * diameter * diameter / 4.0;
    assertEquals(expectedArea, geom.liquidArea, TOLERANCE);
    assertEquals(1.0, geom.liquidHoldup, TOLERANCE);
    assertEquals(0.0, geom.gasArea, TOLERANCE);
  }

  @Test
  void testCalculateFromLiquidLevelHalfFull() {
    double diameter = 0.1;
    double liquidLevel = diameter / 2.0;

    StratifiedGeometry geom = calculator.calculateFromLiquidLevel(liquidLevel, diameter);

    double expectedArea = Math.PI * diameter * diameter / 8.0;
    assertEquals(expectedArea, geom.liquidArea, TOLERANCE);
    assertEquals(0.5, geom.liquidHoldup, TOLERANCE);
  }

  @Test
  void testAreasSumToTotal() {
    double diameter = 0.1;
    double liquidLevel = 0.03;

    StratifiedGeometry geom = calculator.calculateFromLiquidLevel(liquidLevel, diameter);
    double totalArea = Math.PI * diameter * diameter / 4.0;

    assertEquals(totalArea, geom.liquidArea + geom.gasArea, TOLERANCE);
  }

  @Test
  void testInterfaceWidthAtCenter() {
    double diameter = 0.1;
    double liquidLevel = diameter / 2.0;

    StratifiedGeometry geom = calculator.calculateFromLiquidLevel(liquidLevel, diameter);
    assertEquals(diameter, geom.interfacialWidth, TOLERANCE);
  }

  @Test
  void testCalculateFromHoldupHalfFull() {
    double diameter = 0.1;

    StratifiedGeometry geom = calculator.calculateFromHoldup(0.5, diameter);
    assertEquals(diameter / 2.0, geom.liquidLevel, TOLERANCE);
    assertEquals(0.5, geom.liquidHoldup, TOLERANCE);
  }

  @Test
  void testHydraulicDiameters() {
    double diameter = 0.1;
    double liquidLevel = 0.03;

    StratifiedGeometry geom = calculator.calculateFromLiquidLevel(liquidLevel, diameter);

    assertTrue(geom.liquidHydraulicDiameter > 0, "Liquid hydraulic diameter should be positive");
    assertTrue(geom.gasHydraulicDiameter > 0, "Gas hydraulic diameter should be positive");
  }
}
