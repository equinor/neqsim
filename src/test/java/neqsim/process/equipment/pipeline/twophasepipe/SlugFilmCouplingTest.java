package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker.SlugBubbleUnit;

/**
 * Seven-variable conservative contracts for subcell slug/film reconstruction.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SlugFilmCouplingTest {
  @Test
  void testAllSevenInventoriesAreExactlyPartitioned() {
    TwoFluidSection section = cell(0.2, 0.3);
    double[] original = section.getStateVector();
    SlugFilmCoupling.Reconstruction result = SlugFilmCoupling.reconstruct(section,
        Collections.singletonList(slug(0.0, 1.0)));
    assertTrue(result.isActive());
    assertEquals(0.1, result.getBodyFraction(), 1.0e-12);
    assertTrue(result.getBodyState().getLiquidHoldup() > section.getLiquidHoldup());
    assertTrue(result.getFilmState().getLiquidHoldup() < section.getLiquidHoldup());
    assertPartition(original, result);
    assertArrayEquals(original, section.getStateVector(), 0.0, "Reconstruction must not mutate Eulerian inventory");
    assertTrue(result.getBodyState().getTemperature() > 0.0);
    assertTrue(result.getFilmState().getTemperature() > 0.0);
  }

  @Test
  void testOverlappingMarkersUseUnionOccupancy() {
    TwoFluidSection section = cell(0.3, 0.5);
    SlugFilmCoupling.Reconstruction result = SlugFilmCoupling.reconstruct(section,
        Arrays.asList(slug(0.0, 2.0), slug(1.0, 3.0), slug(0.5, 1.5)));
    assertEquals(0.3, result.getBodyFraction(), 1.0e-12);
    assertPartition(section.getStateVector(), result);
  }

  @Test
  void testInventoryLimitsBodyHoldupInsteadOfCreatingLiquid() {
    TwoFluidSection section = cell(0.1, 0.3);
    SlugFilmCoupling.Reconstruction result = SlugFilmCoupling.reconstruct(section,
        Collections.singletonList(slug(0.0, 8.0)));
    assertTrue(result.isActive());
    assertTrue(result.getBodyState().getLiquidHoldup() <= 0.1 / 0.8 + 1.0e-12);
    assertTrue(result.getFilmState().getLiquidHoldup() >= 0.0);
    assertPartition(section.getStateVector(), result);
  }

  @Test
  void testNoOverlapAndCompleteCoverageLeaveOriginalState() {
    TwoFluidSection section = cell(0.2, 0.3);
    for (SlugBubbleUnit marker : Arrays.asList(slug(11.0, 15.0), slug(-5.0, 15.0))) {
      SlugFilmCoupling.Reconstruction result = SlugFilmCoupling.reconstruct(section, Collections.singletonList(marker));
      assertFalse(result.isActive());
      assertArrayEquals(section.getStateVector(), result.getLeftFaceState().getStateVector(), 0.0);
      assertArrayEquals(section.getStateVector(), result.getRightFaceState().getStateVector(), 0.0);
      assertPartition(section.getStateVector(), result);
    }
  }

  @Test
  void testAbsentOilAndAbsentWaterRemainExactlyAbsent() {
    for (double waterCut : new double[] { 0.0, 1.0 }) {
      TwoFluidSection section = cell(0.2, waterCut);
      SlugFilmCoupling.Reconstruction result = SlugFilmCoupling.reconstruct(section,
          Collections.singletonList(slug(0.0, 1.0)));
      int absentPhase = waterCut == 0.0 ? 2 : 1;
      assertEquals(0.0, result.getBodyConservativeState()[absentPhase], 0.0);
      assertEquals(0.0, result.getFilmConservativeState()[absentPhase], 0.0);
      assertEquals(0.0, result.getBodyConservativeState()[absentPhase + 3], 0.0);
      assertEquals(0.0, result.getFilmConservativeState()[absentPhase + 3], 0.0);
      assertPartition(section.getStateVector(), result);
    }
  }

  @Test
  void testSinglePhaseStateDoesNotAcquireAnotherPhase() {
    for (double liquidHoldup : new double[] { 0.0, 1.0 }) {
      TwoFluidSection section = cell(liquidHoldup, 0.0);
      SlugFilmCoupling.Reconstruction result = SlugFilmCoupling.reconstruct(section,
          Collections.singletonList(slug(0.0, 1.0)));
      assertFalse(result.isActive());
      assertArrayEquals(section.getStateVector(), result.getBodyConservativeState(), 0.0);
      assertPartition(section.getStateVector(), result);
    }
  }

  @Test
  void testNonuniformGeometrySelectsCorrectFaces() {
    TwoFluidSection section = cell(0.7, 0.3);
    section.setPosition(4.0);
    section.setLength(4.0);
    SlugFilmCoupling.Reconstruction result = SlugFilmCoupling.reconstruct(section,
        Collections.singletonList(slug(4.0, 9.0)));
    assertEquals(0.5, result.getBodyFraction(), 1.0e-12);
    assertEquals(result.getFilmState().getLiquidHoldup(), result.getLeftFaceState().getLiquidHoldup(), 0.0);
    assertEquals(result.getBodyState().getLiquidHoldup(), result.getRightFaceState().getLiquidHoldup(), 0.0);
    assertPartition(section.getStateVector(), result);
  }

  @Test
  void testAbsoluteEnergyReferenceDoesNotChangeReconstructedVelocities() {
    TwoFluidSection section = cell(0.2, 0.3);
    SlugFilmCoupling.Reconstruction base = SlugFilmCoupling.reconstruct(section,
        Collections.singletonList(slug(0.0, 1.0)));
    TwoFluidSection shifted = section.clone();
    double offset = -1.0e8;
    double[] state = shifted.getStateVector();
    state[6] += offset * (state[0] + state[1] + state[2]);
    shifted.setStateVector(state);
    shifted.setGasEnthalpy(shifted.getGasEnthalpy() + offset);
    shifted.setLiquidEnthalpy(shifted.getLiquidEnthalpy() + offset);
    SlugFilmCoupling.Reconstruction result = SlugFilmCoupling.reconstruct(shifted,
        Collections.singletonList(slug(0.0, 1.0)));
    assertTrue(result.isActive());
    for (int j = 0; j < 6; j++) {
      assertEquals(base.getBodyConservativeState()[j], result.getBodyConservativeState()[j], 1.0e-12);
      assertEquals(base.getFilmConservativeState()[j], result.getFilmConservativeState()[j], 1.0e-12);
    }
    assertEquals(base.getBodyState().getTemperature(), result.getBodyState().getTemperature(), 1.0e-7);
    assertPartition(shifted.getStateVector(), result);
  }

  @Test
  void testSensibleEnergyLimitsKineticVariance() {
    TwoFluidSection section = cell(0.2, 0.3);
    section.setTemperature(1.0);
    section.setMixtureHeatCapacity(0.001);
    SlugFilmCoupling.Reconstruction result = SlugFilmCoupling.reconstruct(section,
        Collections.singletonList(slug(0.0, 1.0)));
    assertTrue(result.getBodyState().getTemperature() > 0.0);
    assertTrue(result.getFilmState().getTemperature() > 0.0);
    assertPartition(section.getStateVector(), result);
  }

  @Test
  void testReturnedStatesAndArraysCannotMutateReconstruction() {
    TwoFluidSection section = cell(0.2, 0.3);
    SlugFilmCoupling.Reconstruction result = SlugFilmCoupling.reconstruct(section,
        Collections.singletonList(slug(0.0, 1.0)));
    double[] expected = result.getBodyConservativeState();
    result.getBodyConservativeState()[0] = 123.0;
    TwoFluidSection face = result.getLeftFaceState();
    face.setStateVector(new double[7]);
    assertArrayEquals(expected, result.getBodyConservativeState(), 0.0);
    assertPartition(section.getStateVector(), result);
  }

  @Test
  void testTraceFilmVelocityEqualsItsConservativeMomentumRatio() {
    TwoFluidSection section = cell(0.1, 0.3);
    section.setDiameter(0.001);
    section.setOilVelocity(1.23456789);
    section.setWaterVelocity(0.987654321);
    section.updateDerivedQuantities();
    section.updateConservativeVariables();
    SlugFilmCoupling.Reconstruction result = SlugFilmCoupling.reconstruct(section,
        Collections.singletonList(slug(0.0, 8.0)));
    assertTrue(result.isActive());
    double[] film = result.getFilmConservativeState();
    assertTrue(film[1] > 0.0 && film[1] < 1.0e-12);
    assertTrue(film[2] > 0.0 && film[2] < 1.0e-12);
    assertEquals(film[4] / film[1], result.getFilmState().getOilVelocity(), 1.0e-12);
    assertEquals(film[5] / film[2], result.getFilmState().getWaterVelocity(), 1.0e-12);
    assertPartition(section.getStateVector(), result);
  }

  /**
   * Assert the complete conservative decomposition and phase positivity.
   *
   * @param mean original state
   * @param result reconstruction
   */
  private void assertPartition(double[] mean, SlugFilmCoupling.Reconstruction result) {
    double fraction = result.getBodyFraction();
    double[] body = result.getBodyConservativeState();
    double[] film = result.getFilmConservativeState();
    for (int j = 0; j < 7; j++) {
      assertEquals(mean[j], fraction * body[j] + (1.0 - fraction) * film[j], 2.0e-12 * Math.max(1.0, Math.abs(mean[j])),
          "Conservative variable " + j);
      if (j < 3) {
        assertTrue(body[j] >= 0.0);
        assertTrue(film[j] >= 0.0);
      }
    }
  }

  /**
   * Build a consistent finite-temperature, seven-variable cell.
   *
   * @param holdup total liquid holdup
   * @param waterCut water fraction of liquid volume
   * @return initialized cell
   */
  private TwoFluidSection cell(double holdup, double waterCut) {
    TwoFluidSection section = new TwoFluidSection(5.0, 10.0, 0.1, 0.0);
    section.setGasDensity(50.0);
    section.setLiquidDensity(800.0 * (1.0 - waterCut) + 1000.0 * waterCut);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setGasHoldup(1.0 - holdup);
    section.setLiquidHoldup(holdup);
    section.setWaterCut(waterCut);
    section.setGasVelocity(3.0);
    section.setLiquidVelocity(1.0);
    section.setOilVelocity(1.2);
    section.setWaterVelocity(0.8);
    section.setPressure(1.0e5);
    section.setTemperature(300.0);
    section.setMixtureHeatCapacity(2000.0);
    section.setGasEnthalpy(5.0e5);
    section.setLiquidEnthalpy(2.0e5);
    section.updateDerivedQuantities();
    section.updateConservativeVariables();
    return section;
  }

  /**
   * Create a marker with assumed body-holdup closure, independent of fluid inventory.
   *
   * @param tail upstream end (m)
   * @param front downstream end (m)
   * @return marker
   */
  private SlugBubbleUnit slug(double tail, double front) {
    SlugBubbleUnit slug = new SlugBubbleUnit();
    slug.tailPosition = tail;
    slug.frontPosition = front;
    slug.slugHoldup = 0.9;
    return slug;
  }
}
