package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.FetkovichAquifer.State;

/**
 * Independent analytical, differential-equation and finite-storage-limit checks for the physical aquifer.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FetkovichAquiferTest {
  /** Checks the published constant-boundary-pressure exponential and aquifer material balance. */
  @Test
  void reproducesConstantPressureExponentialAndStorageBalance() {
    FetkovichAquifer model = new FetkovichAquifer(1.0e6, 1.0e-4, 0.02, 300.0);
    State result = model.advance(model.initialState(), 12000.0, 250.0, 250.0);
    double expected = 5000.0 * (1.0 - Math.exp(-2.4));
    Assertions.assertEquals(expected, result.getCumulativeInfluxM3(), 1.0e-10);
    Assertions.assertEquals(300.0 - expected / 100.0, result.getPressureBara(), 1.0e-12);
    Assertions.assertEquals(12000.0, result.getTimeSeconds(), 0.0);
    Assertions.assertEquals(expected, result.getIntervalInfluxM3(), 0.0);
    Assertions.assertEquals(0.0, result.getIntervalMinimumCumulativeInfluxM3(), 0.0);
    Assertions.assertEquals(expected, result.getIntervalMaximumCumulativeInfluxM3(), 0.0);
    Assertions.assertEquals(0.0, model.initialState().getCumulativeInfluxM3(), 0.0);
  }

  /** Checks a linear reservoir-pressure decline against a separate fourth-order ODE integration. */
  @Test
  void linearRampAgreesWithIndependentRungeKuttaAndIntervalRefinement() {
    FetkovichAquifer model = new FetkovichAquifer(1.0e6, 1.0e-4, 0.02, 300.0);
    State exact = model.advance(model.initialState(), 20000.0, 300.0, 180.0);
    double numericalPressure = 300.0;
    int intervals = 20000;
    double dt = 20000.0 / intervals;
    State refined = model.initialState();
    for (int i = 0; i < intervals; i++) {
      double start = 300.0 - 120.0 * i / intervals;
      double end = 300.0 - 120.0 * (i + 1) / intervals;
      double middle = 0.5 * (start + end);
      double k1 = -0.0002 * (numericalPressure - start);
      double k2 = -0.0002 * (numericalPressure + 0.5 * dt * k1 - middle);
      double k3 = -0.0002 * (numericalPressure + 0.5 * dt * k2 - middle);
      double k4 = -0.0002 * (numericalPressure + dt * k3 - end);
      numericalPressure += dt * (k1 + 2.0 * k2 + 2.0 * k3 + k4) / 6.0;
      refined = model.advance(refined, dt, start, end);
    }
    Assertions.assertEquals(numericalPressure, exact.getPressureBara(), 1.0e-9);
    Assertions.assertEquals(100.0 * (300.0 - numericalPressure), exact.getCumulativeInfluxM3(), 1.0e-7);
    Assertions.assertEquals(exact.getPressureBara(), refined.getPressureBara(), 1.0e-8);
    Assertions.assertEquals(exact.getCumulativeInfluxM3(), refined.getCumulativeInfluxM3(), 1.0e-6);
  }

  /** Checks cancellation-free tiny exposure and the asymptotic large-exposure limit. */
  @Test
  void retainsSmallInfluxAndFiniteLargeStepEvidence() {
    FetkovichAquifer model = new FetkovichAquifer(1.0e12, 1.0e-4, 0.02, 300.0);
    State tiny = model.advance(model.initialState(), 1.0e-5, 300.0, 250.0);
    Assertions.assertEquals(0.02 * 1.0e-5 * 25.0, tiny.getCumulativeInfluxM3(), 1.0e-19);
    State large = model.advance(model.initialState(), 1.0e20, 300.0, 200.0);
    Assertions.assertEquals(200.0, large.getPressureBara(), 1.0e-8);
    Assertions.assertEquals(1.0e10 * (1.0 - 1.0 / 2.0e10), large.getCumulativeInfluxM3(), 1.0e-5);
  }

  /**
   * Numerically integrates the aquifer pressure ODE and samples both cumulative-water extrema.
   *
   * @param startPressure reservoir pressure at time zero, bara
   * @param endPressure reservoir pressure at 10000 seconds, bara
   * @return sampled minimum, maximum and final cumulative influx, reservoir m3
   */
  private double[] independentRampExtrema(double startPressure, double endPressure) {
    double aquiferPressure = 300.0;
    double minimum = 0.0;
    double maximum = 0.0;
    int subdivisions = 100000;
    double dt = 10000.0 / subdivisions;
    for (int i = 0; i < subdivisions; i++) {
      double start = startPressure + (endPressure - startPressure) * i / subdivisions;
      double end = startPressure + (endPressure - startPressure) * (i + 1) / subdivisions;
      double midpoint = 0.5 * (start + end);
      double k1 = -0.0002 * (aquiferPressure - start);
      double k2 = -0.0002 * (aquiferPressure + 0.5 * dt * k1 - midpoint);
      double k3 = -0.0002 * (aquiferPressure + 0.5 * dt * k2 - midpoint);
      double k4 = -0.0002 * (aquiferPressure + dt * k3 - end);
      aquiferPressure += dt * (k1 + 2.0 * k2 + 2.0 * k3 + k4) / 6.0;
      double water = 100.0 * (300.0 - aquiferPressure);
      minimum = Math.min(minimum, water);
      maximum = Math.max(maximum, water);
    }
    return new double[] {minimum, maximum, 100.0 * (300.0 - aquiferPressure)};
  }

  /** Both pressure-ramp directions expose interior water bounds that endpoint-only checks would miss. */
  @Test
  void finiteStorageTracksInteriorMinimumAndMaximumWater() {
    FetkovichAquifer model = new FetkovichAquifer(1.0e6, 1.0e-4, 0.02, 300.0);
    State upward = model.advance(model.initialState(), 10000.0, 250.0, 400.0);
    State downward = model.advance(model.initialState(), 10000.0, 350.0, 200.0);
    double[] risingReference = independentRampExtrema(250.0, 400.0);
    double[] fallingReference = independentRampExtrema(350.0, 200.0);
    Assertions.assertEquals(risingReference[0], upward.getIntervalMinimumCumulativeInfluxM3(), 1.0e-4);
    Assertions.assertEquals(risingReference[1], upward.getIntervalMaximumCumulativeInfluxM3(), 1.0e-4);
    Assertions.assertEquals(risingReference[2], upward.getCumulativeInfluxM3(), 1.0e-6);
    Assertions.assertEquals(fallingReference[0], downward.getIntervalMinimumCumulativeInfluxM3(), 1.0e-4);
    Assertions.assertEquals(fallingReference[1], downward.getIntervalMaximumCumulativeInfluxM3(), 1.0e-4);
    Assertions.assertEquals(fallingReference[2], downward.getCumulativeInfluxM3(), 1.0e-6);
    Assertions.assertTrue(upward.getIntervalMaximumCumulativeInfluxM3() > 0.0);
    Assertions.assertTrue(upward.getCumulativeInfluxM3() < 0.0);
    Assertions.assertTrue(downward.getIntervalMinimumCumulativeInfluxM3() < 0.0);
    Assertions.assertTrue(downward.getCumulativeInfluxM3() > 0.0);
    State restored = SerializationUtils.clone(upward);
    Assertions.assertEquals(upward.getIntervalMaximumCumulativeInfluxM3(),
        restored.getIntervalMaximumCumulativeInfluxM3(), 0.0);
  }

  /** Infinite-storage flow is a linear rate, so water extrema agree with independently integrated quadratics. */
  @Test
  void infiniteStorageTracksBothInteriorWaterExtrema() {
    FetkovichAquifer model = new FetkovichAquifer(Double.POSITIVE_INFINITY, 1.0e-4, 0.02, 300.0);
    State upward = model.advance(model.initialState(), 1000.0, 250.0, 400.0);
    State downward = model.advance(model.initialState(), 1000.0, 350.0, 200.0);
    Assertions.assertEquals(-500.0, upward.getCumulativeInfluxM3(), 1.0e-12);
    Assertions.assertEquals(-500.0, upward.getIntervalMinimumCumulativeInfluxM3(), 1.0e-12);
    Assertions.assertEquals(500.0 / 3.0, upward.getIntervalMaximumCumulativeInfluxM3(), 1.0e-12);
    Assertions.assertEquals(500.0, downward.getCumulativeInfluxM3(), 1.0e-12);
    Assertions.assertEquals(-500.0 / 3.0, downward.getIntervalMinimumCumulativeInfluxM3(), 1.0e-12);
    Assertions.assertEquals(500.0, downward.getIntervalMaximumCumulativeInfluxM3(), 1.0e-12);
  }

  /** Tiny finite exposure retains sub-roundoff pressure changes as finite exchanged-water extrema. */
  @Test
  void tinyExposureRetainsInteriorWaterExtrema() {
    FetkovichAquifer model = new FetkovichAquifer(1.0e12, 1.0e-4, 0.02, 300.0);
    State state = model.advance(model.initialState(), 1.0e-5, 250.0, 400.0);
    Assertions.assertEquals(-5.0e-6, state.getIntervalMinimumCumulativeInfluxM3(), 1.0e-19);
    Assertions.assertEquals(5.0e-6 / 3.0, state.getIntervalMaximumCumulativeInfluxM3(), 1.0e-19);
  }

  /** Checks explicit no-flow, zero-storage, and infinite-storage limits. */
  @Test
  void handlesZeroAndInfiniteSupportWithoutInventingWater() {
    FetkovichAquifer closed = new FetkovichAquifer(1.0e6, 1.0e-4, 0.0, 300.0);
    State noFlow = closed.advance(closed.initialState(), 1000.0, 300.0, 100.0);
    Assertions.assertEquals(0.0, noFlow.getCumulativeInfluxM3(), 0.0);
    Assertions.assertEquals(300.0, noFlow.getPressureBara(), 0.0);
    FetkovichAquifer rigid = new FetkovichAquifer(1.0e6, 0.0, 0.02, 300.0);
    State noStorage = rigid.advance(rigid.initialState(), 1000.0, 300.0, 100.0);
    Assertions.assertEquals(0.0, noStorage.getCumulativeInfluxM3(), 0.0);
    Assertions.assertEquals(100.0, noStorage.getPressureBara(), 0.0);
    FetkovichAquifer infinite = new FetkovichAquifer(Double.POSITIVE_INFINITY, 1.0e-4, 0.02, 300.0);
    State supported = infinite.advance(infinite.initialState(), 1000.0, 300.0, 100.0);
    Assertions.assertEquals(2000.0, supported.getCumulativeInfluxM3(), 1.0e-12);
    Assertions.assertEquals(300.0, supported.getPressureBara(), 0.0);
    Assertions.assertEquals(Double.POSITIVE_INFINITY, infinite.getCapacityM3PerBar());
  }

  /** Checks conservative signed recharge rather than silently clipping negative influx. */
  @Test
  void rechargeRaisesAquiferPressureAndPreservesSignedStorage() {
    FetkovichAquifer model = new FetkovichAquifer(1.0e6, 1.0e-4, 0.02, 300.0);
    State initial = model.initialState();
    State charged = model.advance(initial, 100000.0, 350.0, 350.0);
    Assertions.assertTrue(charged.getCumulativeInfluxM3() < 0.0);
    Assertions.assertTrue(charged.getPressureBara() > 300.0);
    Assertions.assertEquals(300.0 - charged.getCumulativeInfluxM3() / 100.0, charged.getPressureBara(), 1.0e-12);
    State returned = model.advance(charged, 100000.0, 300.0, 300.0);
    Assertions.assertTrue(returned.getIntervalInfluxM3() > 0.0);
    Assertions.assertEquals(300.0, returned.getPressureBara(), 1.0e-6);
    Assertions.assertEquals(300.0, initial.getPressureBara(), 0.0);
    Assertions.assertEquals(0.0, initial.getCumulativeInfluxM3(), 0.0);
  }

  /** Compares only the shared late finite-storage limit of Carter-Tracy and Fetkovich approximations. */
  @Test
  void matchesCarterTracyFiniteCapacityLimit() {
    double radiusRatio = 3.0;
    double aquiferConstant = 10.0;
    double capacitance = aquiferConstant * (radiusRatio * radiusRatio - 1.0) / 2.0;
    double productivity = aquiferConstant / (Math.log(radiusRatio) - 0.75);
    double[] time = {0.0, 1.0e4, 2.0e4, 3.0e4};
    double[] pressureDrop = {0.0, 50.0, 50.0, 50.0};
    double[] carterTracy = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(time, pressureDrop, aquiferConstant,
        radiusRatio);
    FetkovichAquifer finite = new FetkovichAquifer(1.0e6, capacitance / 1.0e6, productivity, 300.0);
    State equilibrated = finite.advance(finite.initialState(), 3.0e4, 250.0, 250.0);
    Assertions.assertEquals(capacitance * 50.0, carterTracy[3], 1.0e-7);
    Assertions.assertEquals(carterTracy[3], equilibrated.getCumulativeInfluxM3(), 1.0e-7);
    // The early-time radial diffusion response is deliberately not equated to a constant PI model.
  }

  /** Checks serialization, independent initial states, and rejection of a mismatched model. */
  @Test
  void serializedStatesRemainReusableWithIdenticalParameters() {
    FetkovichAquifer model = new FetkovichAquifer(1.0e6, 1.0e-4, 0.02, 300.0);
    State first = model.advance(model.initialState(), 1000.0, 300.0, 290.0);
    State restored = SerializationUtils.deserialize(SerializationUtils.serialize(first));
    State actual = model.advance(restored, 1000.0, 290.0, 280.0);
    State expected = model.advance(first, 1000.0, 290.0, 280.0);
    Assertions.assertEquals(expected.getPressureBara(), actual.getPressureBara(), 0.0);
    Assertions.assertEquals(expected.getCumulativeInfluxM3(), actual.getCumulativeInfluxM3(), 0.0);
    FetkovichAquifer mismatch = new FetkovichAquifer(2.0e6, 1.0e-4, 0.02, 300.0);
    Assertions.assertThrows(IllegalArgumentException.class, () -> mismatch.advance(first, 1000.0, 290.0, 280.0));
    Assertions.assertEquals(1000.0, first.getTimeSeconds(), 0.0);
  }

  /** Rejects invalid input, finite-storage overflow and a volume-exhausting interval without state mutation. */
  @Test
  void invalidOrExhaustedIntervalsFailWithoutClamping() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new FetkovichAquifer(-1.0, 1.0e-4, 0.02, 300.0));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new FetkovichAquifer(1.0, Double.NaN, 0.02, 300.0));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new FetkovichAquifer(1.0, 1.0e-4, -0.02, 300.0));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new FetkovichAquifer(1.0, 1.0e-4, 0.02, 0.0));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new FetkovichAquifer(Double.MAX_VALUE, 2.0, 0.02, 300.0));
    FetkovichAquifer model = new FetkovichAquifer(1.0, 0.1, 1.0, 300.0);
    State initial = model.initialState();
    Assertions.assertThrows(IllegalArgumentException.class, () -> model.advance(initial, 0.0, 300.0, 250.0));
    Assertions.assertThrows(IllegalArgumentException.class, () -> model.advance(initial, 1.0, 300.0, Double.NaN));
    Assertions.assertThrows(FetkovichAquifer.InventoryExhaustedException.class,
        () -> model.advance(initial, 100.0, 250.0, 250.0));
    // End-of-interval recharge cannot conceal an inventory-exhausting earlier pressure minimum.
    Assertions.assertThrows(FetkovichAquifer.InventoryExhaustedException.class,
        () -> model.advance(initial, 10.0, 250.0, 1000.0));
    Assertions.assertEquals(300.0, initial.getPressureBara(), 0.0);
    Assertions.assertEquals(0.0, initial.getCumulativeInfluxM3(), 0.0);
  }
}
