package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Qualification of phase identity and momentum when constructing dynamic pipe inventories.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidPhaseConservationTest {

  /** Verify oil-only, mixed, and water-only inventories, including repeated recovery. */
  @Test
  void preservesLiquidPhaseIdentityAtWaterCutEndpoints() {
    double[] waterCuts = { 0.0, 0.25, 0.75, 1.0 };
    for (double waterCut : waterCuts) {
      TwoFluidSection section = createSection(waterCut);
      section.setGasVelocity(3.0);
      section.setOilVelocity(-0.5);
      section.setWaterVelocity(1.25);
      section.updateConservativeVariables();
      double area = section.getArea();
      double expectedOilMass = 0.4 * (1.0 - waterCut) * 800.0 * area;
      double expectedWaterMass = 0.4 * waterCut * 1000.0 * area;
      for (int i = 0; i < 5; i++) {
        assertEquals(expectedOilMass, section.getOilMassPerLength(), 1e-12, "Oil mass at water cut " + waterCut);
        assertEquals(expectedWaterMass, section.getWaterMassPerLength(), 1e-12, "Water mass at water cut " + waterCut);
        assertEquals(-0.5 * expectedOilMass, section.getOilMomentumPerLength(), 1e-12);
        assertEquals(1.25 * expectedWaterMass, section.getWaterMomentumPerLength(), 1e-12);
        section.extractPrimitiveVariables();
        section.updateWaterOilHoldups();
        assertEquals(waterCut, section.getWaterCut(), 1e-12);
        section.updateConservativeVariables();
      }
    }
  }

  /** A stationary oil layer must retain zero momentum while water moves through it. */
  @Test
  void respectsExplicitZeroPhaseVelocityAndIndependentKineticEnergy() {
    TwoFluidSection section = createSection(0.5);
    section.setGasVelocity(2.0);
    section.setLiquidVelocity(1.0);
    section.setOilVelocity(0.0);
    section.setWaterVelocity(2.0);
    section.setGasEnthalpy(50000.0);
    section.setLiquidEnthalpy(-10000.0);
    section.setPressure(2e5);
    section.updateConservativeVariables();
    double expectedEnergy = section.getGasMassPerLength() * (50000.0 + 2.0) - section.getLiquidMassPerLength() * 10000.0
        + 2.0 * section.getWaterMassPerLength() - 2e5 * section.getArea();
    assertEquals(0.0, section.getOilMomentumPerLength(), 0.0);
    assertEquals(2.0 * section.getWaterMassPerLength(), section.getWaterMomentumPerLength(), 1e-12);
    assertEquals(expectedEnergy, section.getEnergyPerLength(), 1e-9);
    assertEquals(section.getOilMomentumPerLength() + section.getWaterMomentumPerLength(),
        section.getLiquidMomentumPerLength(), 1e-12);
  }

  /** Legacy callers that specify only bulk liquid velocity retain the no-slip initialization. */
  @Test
  void retainsBulkLiquidVelocityFallback() {
    TwoFluidSection section = createSection(0.4);
    section.setLiquidVelocity(1.75);
    section.updateConservativeVariables();
    assertEquals(1.75 * section.getOilMassPerLength(), section.getOilMomentumPerLength(), 1e-12);
    assertEquals(1.75 * section.getWaterMassPerLength(), section.getWaterMomentumPerLength(), 1e-12);
    section.extractPrimitiveVariables();
    section.setLiquidVelocity(0.75);
    section.updateConservativeVariables();
    assertEquals(0.75 * section.getOilMassPerLength(), section.getOilMomentumPerLength(), 1e-12);
    assertEquals(0.75 * section.getWaterMassPerLength(), section.getWaterMomentumPerLength(), 1e-12);
  }

  /** Recovered slip survives rebuilding without converting internal recovery into explicit velocity ownership. */
  @Test
  void recoveredSlipPersistsUntilCallerChangesUnownedBulkVelocity() {
    TwoFluidSection section = createSection(0.4);
    section.setGasVelocity(3.0);
    section.updateConservativeVariables();
    double[] state = section.getStateVector();
    state[4] = 0.5 * state[1];
    state[5] = 2.0 * state[2];
    state[6] += 0.5 * state[1] * 0.5 * 0.5 + 0.5 * state[2] * 2.0 * 2.0;
    section.setStateVector(state);
    for (int i = 0; i < 3; i++) {
      section.extractPrimitiveVariables();
      section.updateConservativeVariables();
      assertArrayEquals(state, section.getStateVector(), 1e-10);
      assertEquals(0.5, section.getOilVelocity(), 1e-12);
      assertEquals(2.0, section.getWaterVelocity(), 1e-12);
    }
    section.setLiquidVelocity(1.25);
    section.updateConservativeVariables();
    assertEquals(1.25 * state[1], section.getOilMomentumPerLength(), 1e-12);
    assertEquals(1.25 * state[2], section.getWaterMomentumPerLength(), 1e-12);
  }

  /** Explicit stationary and countercurrent phase velocities remain authoritative after a bulk update. */
  @Test
  void explicitPhaseVelocitiesSurviveRecoveryAndBulkUpdates() {
    TwoFluidSection section = createSection(0.4);
    section.setLiquidVelocity(2.0);
    section.setOilVelocity(0.0);
    section.setWaterVelocity(-1.25);
    section.updateConservativeVariables();
    double[] state = section.getStateVector();
    section.extractPrimitiveVariables();
    section.setLiquidVelocity(3.0);
    section.updateConservativeVariables();
    assertEquals(0.0, section.getOilVelocity(), 0.0);
    assertEquals(-1.25, section.getWaterVelocity(), 1e-12);
    assertArrayEquals(state, section.getStateVector(), 1e-10);
  }

  /** Standalone three-fluid initialization and internal mixture refresh preserve the same caller ownership rules. */
  @Test
  void threeFluidRecoveryDoesNotClaimCallerVelocityOwnership() {
    ThreeFluidSection section = new ThreeFluidSection(0.0, 10.0, 0.1, 0.0);
    section.setGasDensity(50.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setHoldups(0.6, 0.2, 0.2);
    section.setLiquidVelocity(1.75);
    section.updateConservativeVariables();
    section.extractPrimitiveVariables();
    section.setLiquidVelocity(0.75);
    section.updateConservativeVariables();
    assertEquals(0.75 * section.getOilMassPerLength(), section.getOilMomentumPerLength(), 1e-12);
    assertEquals(0.75 * section.getWaterMassPerLength(), section.getWaterMomentumPerLength(), 1e-12);
    section.setOilVelocity(0.0);
    section.setWaterVelocity(-1.25);
    section.updateConservativeVariables();
    double[] state = section.getStateVector();
    section.extractPrimitiveVariables();
    section.setLiquidVelocity(2.0);
    section.updateConservativeVariables();
    assertArrayEquals(state, section.getStateVector(), 1e-10);
  }

  /** Primitive phase appearance inherits bulk flow without overwriting existing recovered slip. */
  @Test
  void newlyPresentUnownedLiquidsInheritBulkVelocity() {
    for (boolean standalone : new boolean[] { false, true }) {
      for (double initialWaterCut : new double[] { 0.0, 1.0 }) {
        TwoFluidSection section = standalone ? new ThreeFluidSection(0.0, 10.0, 0.1, 0.0)
            : createSection(initialWaterCut);
        section.setGasDensity(50.0);
        section.setOilDensity(800.0);
        section.setWaterDensity(1000.0);
        section.setGasHoldup(0.6);
        section.setLiquidHoldup(0.4);
        section.setWaterCut(initialWaterCut);
        section.setOilHoldup(0.4 * (1.0 - initialWaterCut));
        section.setWaterHoldup(0.4 * initialWaterCut);
        section.setLiquidVelocity(2.0);
        section.updateConservativeVariables();
        section.extractPrimitiveVariables();
        section.setWaterCut(0.5);
        section.setOilHoldup(0.2);
        section.setWaterHoldup(0.2);
        section.updateConservativeVariables();
        assertEquals(2.0, section.getOilVelocity(), 1e-12);
        assertEquals(2.0, section.getWaterVelocity(), 1e-12);
        assertEquals(2.0 * section.getOilMassPerLength(), section.getOilMomentumPerLength(), 1e-12);
        assertEquals(2.0 * section.getWaterMassPerLength(), section.getWaterMomentumPerLength(), 1e-12);
      }
    }
  }

  /** A liquid-filled section does not require a fictitious gas density to construct finite energy. */
  @Test
  void waterOnlyEnergyRemainsFiniteWithoutGasDensity() {
    TwoFluidSection section = createSection(1.0);
    section.setGasHoldup(0.0);
    section.setLiquidHoldup(1.0);
    section.setGasDensity(0.0);
    section.setWaterVelocity(0.1);
    section.updateConservativeVariables();
    assertEquals(0.0, section.getGasMassPerLength(), 0.0);
    assertEquals(0.0, section.getOilMassPerLength(), 0.0);
    assertTrue(Double.isFinite(section.getEnergyPerLength()));
    assertEquals(1000.0 * section.getArea(), section.getWaterMassPerLength(), 1e-12);
  }

  /** Valid low densities and countercurrent velocities retain momenta within legacy recovery limits. */
  @Test
  void recoveryPreservesPhysicalDensitiesWithinLegacyVelocityBounds() {
    TwoFluidSection section = createSection(0.5);
    section.setGasDensity(0.02);
    section.setOilDensity(80.0);
    section.setLiquidDensity(540.0);
    section.setGasVelocity(40.0);
    section.setOilVelocity(20.0);
    section.setWaterVelocity(-25.0);
    section.updateConservativeVariables();
    double[] state = section.getStateVector();
    for (int i = 0; i < 5; i++) {
      section.extractPrimitiveVariables();
      assertEquals(0.6, section.getGasHoldup(), 1e-12);
      assertEquals(0.2, section.getOilHoldup(), 1e-12);
      assertEquals(0.2, section.getWaterHoldup(), 1e-12);
      assertEquals(40.0, section.getGasVelocity(), 1e-12);
      assertEquals(20.0, section.getOilVelocity(), 1e-12);
      assertEquals(-25.0, section.getWaterVelocity(), 1e-12);
      assertArrayEquals(state, section.getStateVector(), 1e-9);
      section.updateConservativeVariables();
      assertArrayEquals(state, section.getStateVector(), 1e-9);
    }
  }

  /**
   * Construct a section with independently specified liquid densities.
   *
   * @param waterCut in-situ liquid water volume fraction
   * @return configured section
   */
  private static TwoFluidSection createSection(double waterCut) {
    TwoFluidSection section = new TwoFluidSection(0.0, 10.0, 0.1, 0.0);
    section.setGasDensity(50.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity((1.0 - waterCut) * 800.0 + waterCut * 1000.0);
    section.setWaterCut(waterCut);
    section.setGasHoldup(0.6);
    section.setLiquidHoldup(0.4);
    return section;
  }
}
