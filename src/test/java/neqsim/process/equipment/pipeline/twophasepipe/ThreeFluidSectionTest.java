package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ThreeFluidSection class.
 */
class ThreeFluidSectionTest {

  private ThreeFluidSection section;

  @BeforeEach
  void setUp() {
    section = new ThreeFluidSection(0.0, 10.0, 0.1, 0.0);

    // Set basic properties
    section.setGasDensity(50.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);

    section.setGasViscosity(1.5e-5);
    section.setOilViscosity(5e-3);
    section.setWaterViscosity(1e-3);

    section.setGasVelocity(5.0);
    section.setOilVelocity(1.0);
    section.setWaterVelocity(0.8);

    section.setPressure(50e5);
    section.setTemperature(300.0);
  }

  @Test
  void testSetHoldupsSumToOne() {
    section.setHoldups(0.7, 0.2, 0.1);

    assertEquals(0.7, section.getGasHoldup(), 1e-10);
    assertEquals(0.2, section.getOilHoldup(), 1e-10);
    assertEquals(0.1, section.getWaterHoldup(), 1e-10);

    double sum = section.getGasHoldup() + section.getOilHoldup() + section.getWaterHoldup();
    assertEquals(1.0, sum, 1e-10, "Holdups must sum to 1.0");
  }

  @Test
  void testSetHoldupsThrowsOnInvalidSum() {
    assertThrows(IllegalArgumentException.class, () -> {
      section.setHoldups(0.5, 0.3, 0.3); // Sum = 1.1
    });
  }

  @Test
  void testTotalLiquidHoldup() {
    section.setHoldups(0.6, 0.25, 0.15);

    double expected = 0.25 + 0.15;
    assertEquals(expected, section.getTotalLiquidHoldup(), 1e-10);
  }

  @Test
  void testWaterCutCalculation() {
    section.setHoldups(0.6, 0.2, 0.2);

    // Water cut = water / (oil + water) = 0.2 / 0.4 = 0.5
    assertEquals(0.5, section.getWaterCut(), 1e-10);
  }

  @Test
  void testWaterCutZeroWhenNoLiquid() {
    section.setHoldups(1.0, 0.0, 0.0);

    assertEquals(0.0, section.getWaterCut(), 1e-10);
  }

  @Test
  void testUpdateConservativeVariables() {
    section.setHoldups(0.7, 0.2, 0.1);
    section.updateConservativeVariables();

    double area = section.getArea();

    // Check gas mass per length
    double expectedGasMass = 0.7 * 50.0 * area;
    assertEquals(expectedGasMass, section.getGasMassPerLength(), 1e-10);

    // Check oil mass per length
    double expectedOilMass = 0.2 * 800.0 * area;
    assertEquals(expectedOilMass, section.getOilMassPerLength(), 1e-10);

    // Check water mass per length
    double expectedWaterMass = 0.1 * 1000.0 * area;
    assertEquals(expectedWaterMass, section.getWaterMassPerLength(), 1e-10);
  }

  @Test
  void testMixtureLiquidDensity() {
    section.setHoldups(0.6, 0.3, 0.1);

    // Weighted average: (0.3 * 800 + 0.1 * 1000) / 0.4 = 340 / 0.4 = 850
    double expected = (0.3 * 800.0 + 0.1 * 1000.0) / 0.4;
    assertEquals(expected, section.getMixtureLiquidDensity(), 1e-10);
  }

  @Test
  void testMixtureLiquidVelocity() {
    section.setHoldups(0.6, 0.3, 0.1);
    section.setOilVelocity(2.0);
    section.setWaterVelocity(1.5);

    // Flow-weighted: (0.3 * 2.0 + 0.1 * 1.5) / 0.4 = 0.75 / 0.4 = 1.875
    double expected = (0.3 * 2.0 + 0.1 * 1.5) / 0.4;
    assertEquals(expected, section.getMixtureLiquidVelocity(), 1e-10);
  }

  @Test
  void testThreeLayerGeometryCalculation() {
    section.setHoldups(0.5, 0.3, 0.2);
    section.updateThreeLayerGeometry();

    // Water level should be positive
    assertTrue(section.getWaterLevel() > 0, "Water level should be positive");
    assertTrue(section.getWaterLevel() < section.getDiameter(),
        "Water level should be less than diameter");

    // Oil level should be positive
    assertTrue(section.getOilLevel() >= 0, "Oil level should be non-negative");

    // Areas should match holdups
    double area = section.getArea();
    assertEquals(0.2 * area, section.getWaterArea(), 1e-10);
    assertEquals(0.3 * area, section.getOilArea(), 1e-10);
  }

  @Test
  void testWettedPerimetersArePositive() {
    section.setHoldups(0.5, 0.3, 0.2);
    section.updateThreeLayerGeometry();

    assertTrue(section.getWaterWettedPerimeter() >= 0,
        "Water wetted perimeter should be non-negative");
    assertTrue(section.getOilWettedPerimeter() >= 0, "Oil wetted perimeter should be non-negative");
  }

  @Test
  void testInterfacialWidthsArePositive() {
    section.setHoldups(0.5, 0.3, 0.2);
    section.updateThreeLayerGeometry();

    assertTrue(section.getOilWaterInterfacialWidth() >= 0,
        "Oil-water interfacial width should be non-negative");
    assertTrue(section.getGasOilInterfacialWidth() >= 0,
        "Gas-oil interfacial width should be non-negative");
  }

  @Test
  void testSurfaceTensionGettersSetters() {
    section.setGasOilSurfaceTension(0.025);
    section.setOilWaterSurfaceTension(0.035);
    section.setGasWaterSurfaceTension(0.072);

    assertEquals(0.025, section.getGasOilSurfaceTension(), 1e-10);
    assertEquals(0.035, section.getOilWaterSurfaceTension(), 1e-10);
    assertEquals(0.072, section.getGasWaterSurfaceTension(), 1e-10);
  }

  @Test
  void testCloneCreatesIndependentCopy() {
    section.setHoldups(0.6, 0.3, 0.1);
    ThreeFluidSection clone = section.clone();

    clone.setHoldups(0.8, 0.15, 0.05);

    // Original should be unchanged
    assertEquals(0.6, section.getGasHoldup(), 1e-10);
    assertEquals(0.8, clone.getGasHoldup(), 1e-10);
  }

  @Test
  void testExtractPrimitiveVariables() {
    section.setHoldups(0.6, 0.3, 0.1);
    section.updateConservativeVariables();

    // Modify conservative variables slightly
    double originalOilMass = section.getOilMassPerLength();
    section.setOilMassPerLength(originalOilMass * 1.1);

    section.extractPrimitiveVariables();

    // Oil holdup should have increased by ~10%
    assertTrue(section.getOilHoldup() > 0.3, "Oil holdup should have increased");
  }

  @Test
  void testMomentumConservativeVariables() {
    section.setHoldups(0.6, 0.3, 0.1);
    section.setGasVelocity(10.0);
    section.setOilVelocity(2.0);
    section.setWaterVelocity(1.5);

    section.updateConservativeVariables();

    // Check momentum per length = mass per length * velocity
    double gasMom = section.getGasMassPerLength() * 10.0;
    double oilMom = section.getOilMassPerLength() * 2.0;
    double waterMom = section.getWaterMassPerLength() * 1.5;

    assertEquals(gasMom, section.getGasMomentumPerLength(), 1e-10);
    assertEquals(oilMom, section.getOilMomentumPerLength(), 1e-10);
    assertEquals(waterMom, section.getWaterMomentumPerLength(), 1e-10);
  }

  @Test
  void testEvaporationRateGettersSetters() {
    section.setOilEvaporationRate(0.001);
    section.setWaterEvaporationRate(0.0005);

    assertEquals(0.001, section.getOilEvaporationRate(), 1e-10);
    assertEquals(0.0005, section.getWaterEvaporationRate(), 1e-10);
  }

  @Test
  void testEnthalpyGettersSetters() {
    section.setOilEnthalpy(-50000.0);
    section.setWaterEnthalpy(-100000.0);

    assertEquals(-50000.0, section.getOilEnthalpy(), 1e-10);
    assertEquals(-100000.0, section.getWaterEnthalpy(), 1e-10);
  }
}
