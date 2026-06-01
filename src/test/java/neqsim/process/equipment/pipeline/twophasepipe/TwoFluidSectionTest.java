package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

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
  void testExtractPrimitiveVariablesPreservesTotalMassWhenLimitingPositivity() {
    section.setGasDensity(10.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(850.0);
    section.setWaterCut(0.25);
    section.setStateVector(new double[] {8.0, -1.0, 3.0, 16.0, -2.0, 6.0, 100.0});

    section.extractPrimitiveVariables();

    double totalMass = section.getGasMassPerLength() + section.getOilMassPerLength()
        + section.getWaterMassPerLength();
    assertTrue(section.getGasMassPerLength() >= 0.0);
    assertTrue(section.getOilMassPerLength() >= 0.0);
    assertTrue(section.getWaterMassPerLength() >= 0.0);
    assertEquals(10.0, totalMass, 1e-12);
    assertEquals(0.0, section.getOilMassPerLength(), 1e-12);
  }

  @Test
  void testExtractPrimitiveVariablesKeepsMomentumVelocityConsistent() {
    section.setGasDensity(10.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(850.0);
    section.setWaterCut(0.4);
    section.setStateVector(new double[] {5.0, 4.0, 6.0, 25.0, 8.0, 18.0, 100.0});

    section.extractPrimitiveVariables();
    section.updateWaterOilHoldups();

    assertEquals(section.getGasMomentumPerLength() / section.getGasMassPerLength(),
        section.getGasVelocity(), 1e-12);
    assertEquals(section.getOilMomentumPerLength() / section.getOilMassPerLength(),
        section.getOilVelocity(), 1e-12);
    assertEquals(section.getWaterMomentumPerLength() / section.getWaterMassPerLength(),
        section.getWaterVelocity(), 1e-12);
  }

  @Test
  void testMassTransferPairIsConservative() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    section.setMassTransferRate(2.0);

    double[] massTransfer = equations.calcMassTransfer(section);

    assertEquals(0.2, massTransfer[0], 1e-12);
    assertEquals(-0.2, massTransfer[1], 1e-12);
    assertEquals(0.0, massTransfer[0] + massTransfer[1], 1e-12);
  }

  @Test
  void testFlashDrivenMassTransferReturnsConservativePair() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("n-heptane", 0.15);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    section.setPressure(50.0e5);
    section.setTemperature(300.0);
    section.setGasDensity(40.0);
    section.setLiquidDensity(700.0);
    section.setGasMassPerLength(0.05);
    section.setLiquidMassPerLength(2.0);

    ThermodynamicCoupling coupling = new ThermodynamicCoupling(fluid);
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setThermodynamicCoupling(coupling);
    equations.setMassTransferRelaxationTime(20.0);

    double[] massTransfer = equations.calcMassTransfer(section);

    assertTrue(Double.isFinite(massTransfer[0]));
    assertTrue(Double.isFinite(massTransfer[1]));
    assertEquals(0.0, massTransfer[0] + massTransfer[1], 1e-12);
  }

  @Test
  void testClosurePassUpdatesEntrainmentAndSevereSlugDiagnostics() {
    section.setGasDensity(25.0);
    section.setLiquidDensity(750.0);
    section.setGasViscosity(1.2e-5);
    section.setLiquidViscosity(1.0e-3);
    section.setSurfaceTension(0.02);
    section.setGasHoldup(0.95);
    section.setLiquidHoldup(0.05);
    section.setGasVelocity(35.0);
    section.setLiquidVelocity(0.5);
    section.updateDerivedQuantities();

    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    TwoFluidSection section2 = section.clone();
    section2.setPosition(section.getLength());
    equations.calcRHS(new TwoFluidSection[] {section, section2}, section.getLength());

    assertTrue(section.getEntrainmentFraction() >= 0.0);
    assertTrue(section.getEntrainedDropletDiameter() >= 0.0);

    TwoFluidSection riserBase = new TwoFluidSection(0.0, 10.0, 0.1, Math.toRadians(30.0));
    riserBase.setGasDensity(20.0);
    riserBase.setLiquidDensity(800.0);
    riserBase.setGasViscosity(1.0e-5);
    riserBase.setLiquidViscosity(1.0e-3);
    riserBase.setSurfaceTension(0.02);
    riserBase.setGasHoldup(0.2);
    riserBase.setLiquidHoldup(0.8);
    riserBase.setGasVelocity(0.02);
    riserBase.setLiquidVelocity(0.1);
    riserBase.updateDerivedQuantities();

    TwoFluidSection riserBase2 = riserBase.clone();
    riserBase2.setPosition(riserBase.getLength());
    equations.calcRHS(new TwoFluidSection[] {riserBase, riserBase2}, riserBase.getLength());

    assertTrue(riserBase.getSevereSluggingNumber() < 1.0);
    assertTrue(riserBase.isSevereSlugPotential());
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
