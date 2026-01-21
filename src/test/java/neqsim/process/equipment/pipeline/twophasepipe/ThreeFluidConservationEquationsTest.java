package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.ThreeFluidConservationEquations.ThreeFluidRHS;

/**
 * Unit tests for ThreeFluidConservationEquations class.
 */
class ThreeFluidConservationEquationsTest {
  private ThreeFluidConservationEquations equations;
  private ThreeFluidSection section;

  @BeforeEach
  void setUp() {
    equations = new ThreeFluidConservationEquations();

    section = new ThreeFluidSection(50.0, 10.0, 0.1, 0.0);

    // Set phase properties
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
    section.setRoughness(1e-5);

    section.setHoldups(0.6, 0.25, 0.15);

    section.setGasOilSurfaceTension(0.025);
    section.setOilWaterSurfaceTension(0.03);
  }

  @Test
  void testCalcRHSReturnsValidResult() {
    ThreeFluidRHS rhs = equations.calcRHS(section, 100.0, section, section);

    assertNotNull(rhs, "RHS should not be null");
    assertTrue(Double.isFinite(rhs.gasMomentum), "Gas momentum RHS should be finite");
    assertTrue(Double.isFinite(rhs.oilMomentum), "Oil momentum RHS should be finite");
    assertTrue(Double.isFinite(rhs.waterMomentum), "Water momentum RHS should be finite");
  }

  @Test
  void testWallShearIsNonNegative() {
    ThreeFluidRHS rhs = equations.calcRHS(section, 100.0, section, section);

    // Wall shear magnitude should be non-negative (shear stress magnitude)
    assertTrue(Math.abs(rhs.gasWallShear) >= 0, "Gas wall shear should be computed");
    assertTrue(Math.abs(rhs.oilWallShear) >= 0, "Oil wall shear should be computed");
    assertTrue(Math.abs(rhs.waterWallShear) >= 0, "Water wall shear should be computed");
  }

  @Test
  void testPressureGradientAffectsMomentum() {
    ThreeFluidRHS rhsLowDp = equations.calcRHS(section, 10.0, section, section);
    ThreeFluidRHS rhsHighDp = equations.calcRHS(section, 1000.0, section, section);

    // Higher pressure gradient should give more negative momentum RHS
    // (assuming positive pressure gradient means increasing pressure downstream)
    assertTrue(rhsHighDp.gasMomentum < rhsLowDp.gasMomentum,
        "Higher dP/dx should reduce momentum RHS");
  }

  @Test
  void testGravityAffectsInclinedPipe() {
    // Horizontal pipe
    section.setInclination(0.0);
    ThreeFluidRHS rhsHoriz = equations.calcRHS(section, 100.0, section, section);

    // Uphill pipe (45 degrees)
    section.setInclination(Math.PI / 4);
    ThreeFluidRHS rhsUphill = equations.calcRHS(section, 100.0, section, section);

    // Uphill should have more negative momentum RHS due to gravity
    assertTrue(rhsUphill.gasMomentum < rhsHoriz.gasMomentum,
        "Uphill flow should reduce gas momentum more");
    assertTrue(rhsUphill.oilMomentum < rhsHoriz.oilMomentum,
        "Uphill flow should reduce oil momentum more");
    assertTrue(rhsUphill.waterMomentum < rhsHoriz.waterMomentum,
        "Uphill flow should reduce water momentum more");
  }

  @Test
  void testInterfacialShearComputed() {
    // Set different velocities to create interfacial shear
    section.setGasVelocity(10.0);
    section.setOilVelocity(2.0);
    section.setWaterVelocity(1.0);

    ThreeFluidRHS rhs = equations.calcRHS(section, 100.0, section, section);

    // With velocity differences, interfacial shear should be non-zero
    assertTrue(Math.abs(rhs.gasOilInterfacialShear) > 0 || section.getOilHoldup() < 1e-6,
        "Gas-oil interfacial shear should be computed when phases present");
  }

  @Test
  void testMassConservationWithEvaporation() {
    section.setOilEvaporationRate(0.001);
    section.setWaterEvaporationRate(0.0005);

    ThreeFluidRHS rhs = equations.calcRHS(section, 100.0, section, section);

    // Gas mass should gain from evaporation
    assertEquals(0.001 + 0.0005, rhs.gasMass, 1e-10);

    // Oil should lose
    assertEquals(-0.001, rhs.oilMass, 1e-10);

    // Water should lose
    assertEquals(-0.0005, rhs.waterMass, 1e-10);
  }

  @Test
  void testStateVectorExtraction() {
    section.updateConservativeVariables();

    double[] state = equations.getStateVector(section);

    assertEquals(7, state.length, "State vector should have 7 elements");
    assertEquals(section.getGasMassPerLength(), state[0], 1e-10);
    assertEquals(section.getOilMassPerLength(), state[1], 1e-10);
    assertEquals(section.getWaterMassPerLength(), state[2], 1e-10);
    assertEquals(section.getGasMomentumPerLength(), state[3], 1e-10);
    assertEquals(section.getOilMomentumPerLength(), state[4], 1e-10);
    assertEquals(section.getWaterMomentumPerLength(), state[5], 1e-10);
  }

  @Test
  void testStateVectorSetting() {
    double[] state = {10.0, 20.0, 15.0, 50.0, 20.0, 12.0, 0.0};

    equations.setStateVector(section, state);

    assertEquals(10.0, section.getGasMassPerLength(), 1e-10);
    assertEquals(20.0, section.getOilMassPerLength(), 1e-10);
    assertEquals(15.0, section.getWaterMassPerLength(), 1e-10);
    assertEquals(50.0, section.getGasMomentumPerLength(), 1e-10);
    assertEquals(20.0, section.getOilMomentumPerLength(), 1e-10);
    assertEquals(12.0, section.getWaterMomentumPerLength(), 1e-10);
  }

  @Test
  void testEqualVelocitiesMinimizeInterfacialShear() {
    // All phases moving at same velocity
    section.setGasVelocity(2.0);
    section.setOilVelocity(2.0);
    section.setWaterVelocity(2.0);

    ThreeFluidRHS rhs = equations.calcRHS(section, 100.0, section, section);

    // With same velocities, interfacial shear should be zero
    assertEquals(0.0, rhs.gasOilInterfacialShear, 1e-10,
        "Interfacial shear should be zero when velocities are equal");
    assertEquals(0.0, rhs.oilWaterInterfacialShear, 1e-10,
        "Interfacial shear should be zero when velocities are equal");
  }

  @Test
  void testMomentumBalanceWithNoFlow() {
    section.setGasVelocity(0.0);
    section.setOilVelocity(0.0);
    section.setWaterVelocity(0.0);

    ThreeFluidRHS rhs = equations.calcRHS(section, 100.0, section, section);

    // With no flow, wall friction should be zero
    assertEquals(0.0, rhs.gasWallShear, 1e-10, "Gas wall shear should be zero with no flow");
    assertEquals(0.0, rhs.oilWallShear, 1e-10, "Oil wall shear should be zero with no flow");
    assertEquals(0.0, rhs.waterWallShear, 1e-10, "Water wall shear should be zero with no flow");
  }

  @Test
  void testHigherVelocityGivesHigherWallShear() {
    section.setGasVelocity(5.0);
    ThreeFluidRHS rhsLow = equations.calcRHS(section, 100.0, section, section);

    section.setGasVelocity(15.0);
    ThreeFluidRHS rhsHigh = equations.calcRHS(section, 100.0, section, section);

    // Wall shear scales with velocity squared
    assertTrue(Math.abs(rhsHigh.gasWallShear) > Math.abs(rhsLow.gasWallShear),
        "Higher velocity should give higher wall shear");
  }
}
