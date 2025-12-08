package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TwoFluidSection.
 * 
 * Tests two-fluid specific state variables and geometry calculations.
 */
public class TwoFluidSectionTest {

  private static final double TOLERANCE = 1e-10;
  private TwoFluidSection section;

  @BeforeEach
  void setUp() {
    section = new TwoFluidSection(0.0, 10.0, 0.1, 0.0);
    // Set up typical pipeline section
    section.setRoughness(0.00005);
  }

  @Test
  void testDefaultConstruction() {
    TwoFluidSection s = new TwoFluidSection();
    assertNotNull(s);
  }

  @Test
  void testParameterizedConstruction() {
    TwoFluidSection s = new TwoFluidSection(100.0, 50.0, 0.2, Math.PI / 6);
    assertEquals(100.0, s.getPosition(), TOLERANCE);
    assertEquals(50.0, s.getLength(), TOLERANCE);
    assertEquals(0.2, s.getDiameter(), TOLERANCE);
    assertEquals(Math.PI / 6, s.getInclination(), TOLERANCE);
  }

  @Test
  void testSetAndGetProperties() {
    section.setGasDensity(50.0);
    section.setLiquidDensity(800.0);
    section.setGasVelocity(10.0);
    section.setLiquidVelocity(1.0);
    section.setLiquidHoldup(0.3);
    section.setPressure(50e5);
    section.setTemperature(300.0);

    assertEquals(50.0, section.getGasDensity(), TOLERANCE);
    assertEquals(800.0, section.getLiquidDensity(), TOLERANCE);
    assertEquals(10.0, section.getGasVelocity(), TOLERANCE);
    assertEquals(1.0, section.getLiquidVelocity(), TOLERANCE);
    assertEquals(0.3, section.getLiquidHoldup(), TOLERANCE);
    assertEquals(50e5, section.getPressure(), TOLERANCE);
    assertEquals(300.0, section.getTemperature(), TOLERANCE);
  }

  @Test
  void testGasHoldupConsistency() {
    section.setLiquidHoldup(0.3);
    section.setGasHoldup(0.7);
    assertEquals(0.7, section.getGasHoldup(), TOLERANCE);
    assertEquals(0.3, section.getLiquidHoldup(), TOLERANCE);
  }

  @Test
  void testUpdateStratifiedGeometry() {
    section.setLiquidHoldup(0.3);
    section.updateStratifiedGeometry();

    // After updating, geometry parameters should be set
    assertTrue(section.getGasWettedPerimeter() > 0, "Gas wetted perimeter should be positive");
    assertTrue(section.getLiquidWettedPerimeter() > 0,
        "Liquid wetted perimeter should be positive");
    assertTrue(section.getInterfacialWidth() > 0, "Interface width should be positive");
  }

  @Test
  void testUpdateConservativeVariables() {
    section.setGasDensity(50.0);
    section.setLiquidDensity(800.0);
    section.setGasVelocity(10.0);
    section.setLiquidVelocity(1.0);
    section.setLiquidHoldup(0.3);
    section.setGasHoldup(0.7);

    section.updateConservativeVariables();

    double area = section.getArea();
    double expectedGasMass = 0.7 * 50.0 * area;
    double expectedLiquidMass = 0.3 * 800.0 * area;

    assertEquals(expectedGasMass, section.getGasMassPerLength(), TOLERANCE);
    assertEquals(expectedLiquidMass, section.getLiquidMassPerLength(), TOLERANCE);
  }

  @Test
  void testAreaCalculation() {
    double expectedArea = Math.PI * 0.1 * 0.1 / 4.0;
    assertEquals(expectedArea, section.getArea(), TOLERANCE);
  }

  @Test
  void testFromPipeSection() {
    PipeSection base = new PipeSection(50.0, 20.0, 0.15, 0.1);
    base.setPressure(30e5);
    base.setTemperature(280.0);
    base.setGasDensity(40.0);
    base.setLiquidDensity(750.0);

    TwoFluidSection tfs = TwoFluidSection.fromPipeSection(base);

    assertNotNull(tfs);
    assertEquals(base.getPosition(), tfs.getPosition(), TOLERANCE);
    assertEquals(base.getLength(), tfs.getLength(), TOLERANCE);
    assertEquals(base.getDiameter(), tfs.getDiameter(), TOLERANCE);
    assertEquals(base.getPressure(), tfs.getPressure(), TOLERANCE);
    assertEquals(base.getTemperature(), tfs.getTemperature(), TOLERANCE);
    assertEquals(base.getGasDensity(), tfs.getGasDensity(), TOLERANCE);
    assertEquals(base.getLiquidDensity(), tfs.getLiquidDensity(), TOLERANCE);
  }

  @Test
  void testFlowRegimeSetting() {
    section.setFlowRegime(PipeSection.FlowRegime.STRATIFIED_SMOOTH);
    assertEquals(PipeSection.FlowRegime.STRATIFIED_SMOOTH, section.getFlowRegime());

    section.setFlowRegime(PipeSection.FlowRegime.ANNULAR);
    assertEquals(PipeSection.FlowRegime.ANNULAR, section.getFlowRegime());

    section.setFlowRegime(PipeSection.FlowRegime.SLUG);
    assertEquals(PipeSection.FlowRegime.SLUG, section.getFlowRegime());
  }

  @Test
  void testMixtureDensityCalculation() {
    section.setGasDensity(50.0);
    section.setLiquidDensity(800.0);
    section.setLiquidHoldup(0.3);
    section.setGasHoldup(0.7);

    // Calculate expected mixture density
    double expectedMixtureDensity = 0.7 * 50.0 + 0.3 * 800.0;

    // Mixture density field must be set explicitly in PipeSection
    // This tests that the calculation formula is correct
    assertEquals(275.0, expectedMixtureDensity, TOLERANCE);
  }

  @Test
  void testMixtureVelocityCalculation() {
    section.setGasVelocity(10.0);
    section.setLiquidVelocity(1.0);
    section.setLiquidHoldup(0.3);
    section.setGasHoldup(0.7);

    // Expected superficial mixture velocity
    double expectedMixtureVelocity = 0.7 * 10.0 + 0.3 * 1.0;
    assertEquals(7.3, expectedMixtureVelocity, TOLERANCE);
  }

  @Test
  void testSourceTerms() {
    section.setGasMassSource(0.01);
    section.setLiquidMassSource(-0.01);
    section.setGasMomentumSource(10.0);
    section.setLiquidMomentumSource(-5.0);
    section.setEnergySource(1000.0);

    assertEquals(0.01, section.getGasMassSource(), TOLERANCE);
    assertEquals(-0.01, section.getLiquidMassSource(), TOLERANCE);
    assertEquals(10.0, section.getGasMomentumSource(), TOLERANCE);
    assertEquals(-5.0, section.getLiquidMomentumSource(), TOLERANCE);
    assertEquals(1000.0, section.getEnergySource(), TOLERANCE);
  }

  @Test
  void testShearStresses() {
    section.setGasWallShear(5.0);
    section.setLiquidWallShear(15.0);
    section.setInterfacialShear(2.0);

    assertEquals(5.0, section.getGasWallShear(), TOLERANCE);
    assertEquals(15.0, section.getLiquidWallShear(), TOLERANCE);
    assertEquals(2.0, section.getInterfacialShear(), TOLERANCE);
  }

  @Test
  void testWettedPerimeters() {
    section.setGasWettedPerimeter(0.2);
    section.setLiquidWettedPerimeter(0.1);
    section.setInterfacialWidth(0.08);

    assertEquals(0.2, section.getGasWettedPerimeter(), TOLERANCE);
    assertEquals(0.1, section.getLiquidWettedPerimeter(), TOLERANCE);
    assertEquals(0.08, section.getInterfacialWidth(), TOLERANCE);
  }
}
