package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.ThreeFluidConservationEquations.ThreeFluidRHS;

/**
 * Phase disappearance, conservative state, and analytical source checks for the standalone three-fluid model.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ThreeFluidDynamicConservationTest {

  /** Independent state APIs must address the same phase inventories and conservative energy. */
  @Test
  void roundTripsAllSevenConservativeVariables() {
    ThreeFluidSection section = createSection();
    section.setHoldups(0.6, 0.25, 0.15);
    section.setGasVelocity(3.0);
    section.setOilVelocity(-0.5);
    section.setWaterVelocity(1.5);
    section.setGasEnthalpy(50000.0);
    section.setOilEnthalpy(-20000.0);
    section.setWaterEnthalpy(-80000.0);
    section.updateConservativeVariables();
    ThreeFluidConservationEquations equations = new ThreeFluidConservationEquations();
    double[] state = equations.getStateVector(section);
    assertArrayEquals(section.getStateVector(), state, 0.0);
    double expectedEnergy = state[0] * (50000.0 + 4.5) + state[1] * (-20000.0 + 0.125) + state[2] * (-80000.0 + 1.125)
        - section.getPressure() * section.getArea();
    assertEquals(expectedEnergy, state[6], 1e-9);
    state[6] += 500.0;
    equations.setStateVector(section, state);
    section.extractPrimitiveVariables();
    assertArrayEquals(state, section.getStateVector(), 1e-12);
    assertEquals(3.0, section.getGasVelocity(), 1e-12);
    assertEquals(-0.5, section.getOilVelocity(), 1e-12);
    assertEquals(1.5, section.getWaterVelocity(), 1e-12);
    assertEquals(state[1] + state[2], section.getLiquidMassPerLength(), 1e-12);
    assertEquals(state[4] + state[5], section.getLiquidMomentumPerLength(), 1e-12);
  }

  /** Cloning preserves the canonical inherited state and changes to the copy remain independent. */
  @Test
  void cloneRetainsIndependentConservativeState() {
    ThreeFluidSection section = createSection();
    section.setHoldups(0.6, 0.25, 0.15);
    section.setOilVelocity(-0.5);
    section.setWaterVelocity(1.5);
    section.updateConservativeVariables();
    double[] original = section.getStateVector();
    ThreeFluidSection copy = section.clone();
    assertArrayEquals(original, copy.getStateVector(), 0.0);
    double[] modified = copy.getStateVector();
    modified[1] *= 1.5;
    modified[6] += 100.0;
    copy.setStateVector(modified);
    assertArrayEquals(original, section.getStateVector(), 0.0);
    assertArrayEquals(modified, copy.getStateVector(), 0.0);
  }

  /** Vanishing liquid phases must not retain water cut or velocities from an earlier state. */
  @Test
  void recoversGasOnlyStateWithoutStaleLiquid() {
    ThreeFluidSection section = createSection();
    section.setHoldups(0.6, 0.2, 0.2);
    section.updateConservativeVariables();
    double gasMass = section.getGasDensity() * section.getArea();
    section.setStateVector(new double[] { gasMass, 0.0, 0.0, 2.0 * gasMass, 0.0, 0.0, 100.0 });
    section.extractPrimitiveVariables();
    assertEquals(1.0, section.getGasHoldup(), 0.0);
    assertEquals(0.0, section.getLiquidHoldup(), 0.0);
    assertEquals(0.0, section.getWaterCut(), 0.0);
    assertEquals(0.0, section.getOilVelocity(), 0.0);
    assertEquals(0.0, section.getWaterVelocity(), 0.0);
  }

  /** Both liquid phases retain their wall contact when gas has disappeared. */
  @Test
  void liquidFilledGeometryPartitionsEntireWall() {
    ThreeFluidSection section = createSection();
    double[] waterFractions = { 0.0, 0.3, 1.0 };
    for (double water : waterFractions) {
      section.setHoldups(0.0, 1.0 - water, water);
      section.updateThreeLayerGeometry();
      assertEquals(Math.PI * section.getDiameter(), section.getOilWettedPerimeter() + section.getWaterWettedPerimeter(),
          1e-12);
      assertEquals(0.0, section.getGasOilInterfacialWidth(), 0.0);
      assertEquals(0.0, section.getGasWaterInterfacialWidth(), 0.0);
    }
  }

  /** Thin liquid films retain their cross-sectional area without a diameter-dependent thickness floor. */
  @Test
  void thinWaterFilmGeometryRecoversSpecifiedArea() {
    double[] diameters = { 0.05, 0.5, 2.0 };
    double[] waterHoldups = { 1e-10, 1e-8, 1.0 - 1e-8 };
    for (double diameter : diameters) {
      for (double waterHoldup : waterHoldups) {
        ThreeFluidSection section = new ThreeFluidSection(0.0, 10.0, diameter, 0.0);
        section.setHoldups(1.0 - waterHoldup, 0.0, waterHoldup);
        section.updateThreeLayerGeometry();
        double theta = 2.0 * Math.acos(1.0 - 2.0 * section.getWaterLevel() / diameter);
        double reconstructedArea = diameter * diameter * (theta - Math.sin(theta)) / 8.0;
        assertEquals(waterHoldup * section.getArea(), reconstructedArea, 1e-13 * section.getArea());
      }
    }
  }

  /** Gas-water drag remains active and pairwise conservative in the zero-oil limit. */
  @Test
  void gasWaterInterfaceTransfersEqualAndOppositeMomentum() {
    ThreeFluidSection section = createSection();
    section.setHoldups(0.6, 0.0, 0.4);
    section.setGasVelocity(5.0);
    section.setWaterVelocity(1.0);
    ThreeFluidRHS rhs = new ThreeFluidConservationEquations().calcRHS(section, 0.0, section, section);
    assertTrue(section.getGasWaterInterfacialWidth() > 0.0);
    assertTrue(rhs.gasWaterInterfacialShear > 0.0);
    assertEquals(0.0, rhs.oilMomentum, 0.0);
    assertEquals(0.0, rhs.gasOilInterfacialShear, 0.0);
    double wallForce = rhs.gasWallShear * (Math.PI * section.getDiameter() - section.getWaterWettedPerimeter())
        + rhs.waterWallShear * section.getWaterWettedPerimeter();
    assertEquals(-wallForce, rhs.gasMomentum + rhs.oilMomentum + rhs.waterMomentum, 1e-12);
  }

  /** The creeping-flow pure-oil limit obeys the Hagen-Poiseuille wall force in either direction. */
  @Test
  void pureOilLowReynoldsFrictionMatchesPoiseuille() {
    ThreeFluidSection section = createSection();
    section.setHoldups(0.0, 1.0, 0.0);
    double[] velocities = { -1e-4, 1e-4 };
    for (double velocity : velocities) {
      section.setOilVelocity(velocity);
      ThreeFluidRHS rhs = new ThreeFluidConservationEquations().calcRHS(section, 0.0, section, section);
      double wallForce = -8.0 * Math.PI * section.getOilViscosity() * velocity;
      assertEquals(wallForce, rhs.oilMomentum, 1e-14);
      assertEquals(0.0, rhs.gasMomentum, 0.0);
      assertEquals(0.0, rhs.waterMomentum, 0.0);
    }
  }

  /** Evaporation and condensation exchange momentum at their donor phase velocities. */
  @Test
  void phaseChangeTransfersDonorMomentumConservatively() {
    ThreeFluidSection section = createSection();
    section.setHoldups(0.6, 0.2, 0.2);
    section.setGasVelocity(4.0);
    section.setOilVelocity(0.5);
    section.setWaterVelocity(-0.25);
    ThreeFluidConservationEquations equations = new ThreeFluidConservationEquations();
    ThreeFluidRHS baseline = equations.calcRHS(section, 0.0, section, section);
    section.setOilEvaporationRate(0.1);
    section.setWaterEvaporationRate(-0.2);
    ThreeFluidRHS transfer = equations.calcRHS(section, 0.0, section, section);
    assertEquals(0.0, transfer.gasMass + transfer.oilMass + transfer.waterMass, 1e-12);
    assertEquals(0.1 * 0.5 - 0.2 * 4.0, transfer.gasMomentum - baseline.gasMomentum, 1e-12);
    assertEquals(-0.1 * 0.5, transfer.oilMomentum - baseline.oilMomentum, 1e-12);
    assertEquals(0.2 * 4.0, transfer.waterMomentum - baseline.waterMomentum, 1e-12);
    assertEquals(baseline.gasMomentum + baseline.oilMomentum + baseline.waterMomentum,
        transfer.gasMomentum + transfer.oilMomentum + transfer.waterMomentum, 1e-12);
  }

  /** Gravitational power matches the potential-energy change per unit length. */
  @Test
  void gravityWorkUsesAllThreePhaseMassFluxes() {
    ThreeFluidSection section = createSection();
    section.setHoldups(0.6, 0.2, 0.2);
    section.setInclination(Math.PI / 6.0);
    section.setGasVelocity(4.0);
    section.setOilVelocity(0.5);
    section.setWaterVelocity(-0.25);
    section.updateConservativeVariables();
    ThreeFluidRHS rhs = new ThreeFluidConservationEquations().calcRHS(section, 0.0, section, section);
    double phaseMassFlux = section.getGasMomentumPerLength() + section.getOilMomentumPerLength()
        + section.getWaterMomentumPerLength();
    assertEquals(-9.81 * Math.sin(section.getInclination()) * phaseMassFlux, rhs.energy, 1e-12);
  }

  /** Holdups must describe a physical partition, even when invalid values happen to sum to one. */
  @Test
  void rejectsNonPhysicalHoldupPartitions() {
    ThreeFluidSection section = createSection();
    assertThrows(IllegalArgumentException.class, () -> section.setHoldups(1.1, -0.1, 0.0));
    assertThrows(IllegalArgumentException.class, () -> section.setHoldups(Double.NaN, 0.5, 0.5));
  }

  /**
   * Construct a section with phase properties but no assumption about its holdup distribution.
   *
   * @return configured section
   */
  private static ThreeFluidSection createSection() {
    ThreeFluidSection section = new ThreeFluidSection(0.0, 10.0, 0.1, 0.0);
    section.setGasDensity(50.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setGasViscosity(1.5e-5);
    section.setOilViscosity(5e-3);
    section.setWaterViscosity(1e-3);
    section.setPressure(2e5);
    return section;
  }
}
