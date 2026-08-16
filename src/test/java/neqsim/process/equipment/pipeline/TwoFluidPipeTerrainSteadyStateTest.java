package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for terrain handling in the {@link TwoFluidPipe} steady-state solve.
 *
 * <p>
 * The steady-state refinement loop used to call {@code LiquidAccumulationTracker.updateAccumulation} once per sweep
 * with a nominal one-second time step. That tracker is a time integrator: it adds a strictly non-negative accumulation
 * rate, explicitly refuses to drain, ratchets its own tracked volume up to the liquid already present in the sections,
 * and then adds that volume back on top of the section holdup which already contains it. Driven by the iteration
 * counter instead of physical time it has no fixed point, so on an undulating line the valley sections climbed to the
 * holdup cap, the inflated mixture density steepened the pressure gradient, and the solve never settled.
 * </p>
 *
 * <p>
 * A steady state carries zero net liquid accumulation by definition, so the tracker is no longer integrated during the
 * steady solve. Terrain still acts through {@code applyTerrainAccumulation}, which is algebraic in the section's own
 * Froude number and is therefore compatible with a fixed-point sweep.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
class TwoFluidPipeTerrainSteadyStateTest {
  /** Inside diameter, in m. */
  private static final double DIAMETER = 0.30;

  /** Pipe length, in m. */
  private static final double LENGTH = 25000.0;

  /** Wall roughness, in m. */
  private static final double ROUGHNESS = 4.5e-5;

  /** Inlet pressure, in bara. */
  private static final double INLET_PRESSURE = 120.0;

  /** Inlet temperature, in degrees Celsius. */
  private static final double INLET_TEMPERATURE = 35.0;

  /** Mass flow, in kg/hr. */
  private static final double MASS_FLOW = 150000.0;

  /** Number of finite volumes. */
  private static final int SECTIONS = 100;

  /** Number of undulations along the line. */
  private static final int UNDULATIONS = 6;

  /** Peak-to-trough elevation amplitude, in m. */
  private static final double AMPLITUDE = 25.0;

  /**
   * Builds a wet gas that carries a small condensate fraction, so terrain zones have liquid to pool.
   *
   * @return a flashed fluid with its physical properties initialised
   */
  private SystemInterface buildFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + INLET_TEMPERATURE, INLET_PRESSURE);
    fluid.addComponent("methane", 0.88);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-heptane", 0.02);
    fluid.setMixingRule("classic");
    fluid.setPressure(INLET_PRESSURE, "bara");
    fluid.setTemperature(INLET_TEMPERATURE, "C");
    fluid.setTotalFlowRate(MASS_FLOW, "kg/hr");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }

  /**
   * Runs the undulating line with terrain handling either enabled or disabled.
   *
   * @param terrainTracking true to enable terrain accumulation
   * @return the pipe after it has been run
   */
  private TwoFluidPipe runUndulatingLine(boolean terrainTracking) {
    Stream stream = new Stream("feed", buildFluid());
    stream.setFlowRate(MASS_FLOW, "kg/hr");
    stream.setPressure(INLET_PRESSURE, "bara");
    stream.setTemperature(INLET_TEMPERATURE, "C");
    stream.run();

    TwoFluidPipe pipe = new TwoFluidPipe("undulating", stream);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setRoughness(ROUGHNESS);
    pipe.setNumberOfSections(SECTIONS);

    double[] elevation = new double[SECTIONS + 1];
    for (int i = 0; i <= SECTIONS; i++) {
      elevation[i] = -AMPLITUDE * Math.sin(2.0 * Math.PI * UNDULATIONS * i / SECTIONS);
    }
    pipe.setElevationProfile(elevation);
    pipe.setIncludeEnergyEquation(false);
    pipe.setEnableTerrainTracking(terrainTracking);
    pipe.run();
    return pipe;
  }

  /**
   * The steady solve must reach a fixed point on an undulating line with terrain handling on.
   */
  @Test
  @DisplayName("Terrain tracking must not prevent steady-state convergence")
  void testUndulatingLineConvergesWithTerrainTracking() {
    TwoFluidPipe pipe = runUndulatingLine(true);

    Assertions.assertFalse(pipe.isSteadyStatePressureFloorLimited(),
        "an undulating line at this rate is deliverable and must not rest on the pressure floor");
    Assertions.assertFalse(pipe.isSteadyStateWallClockLimited(),
        "the solve must settle well inside the wall-clock budget");
    Assertions.assertTrue(pipe.isSteadyStateConverged(),
        "terrain accumulation must not stop the steady-state sweep from reaching a fixed point");
  }

  /**
   * No section may be driven onto the terrain holdup cap by the steady solve.
   *
   * <p>
   * The unbounded tracker integration saturated valley sections at the cap regardless of the momentum balance. A holdup
   * that sits exactly on a clamp is a property of the clamp, not of the flow, so it must not be produced by a steady
   * solve on a gas-dominated line.
   * </p>
   */
  @Test
  @DisplayName("Steady-state terrain holdup must stay off the accumulation cap")
  void testTerrainHoldupStaysOffTheCap() {
    TwoFluidPipe pipe = runUndulatingLine(true);

    double maxHoldup = 0.0;
    for (double holdup : pipe.getLiquidHoldupProfile()) {
      maxHoldup = Math.max(maxHoldup, holdup);
    }

    Assertions.assertTrue(maxHoldup < 0.80,
        "no section may be saturated at the terrain accumulation cap, but the maximum holdup was " + maxHoldup);
  }

  /**
   * Terrain must perturb the pressure drop, not dominate it.
   *
   * <p>
   * On a gas-dominated line the liquid inventory is small, so switching terrain handling on may move the pressure drop
   * by a few per cent through the extra valley holdup. The unbounded integration instead roughly doubled it, which is
   * the regression this bound catches.
   * </p>
   */
  @Test
  @DisplayName("Terrain tracking must perturb pressure drop, not double it")
  void testTerrainPressureDropStaysCloseToTerrainFreeSolve() {
    TwoFluidPipe withTerrain = runUndulatingLine(true);
    TwoFluidPipe withoutTerrain = runUndulatingLine(false);

    Assertions.assertTrue(withTerrain.isSteadyStateConverged(), "terrain-tracking case must converge");
    Assertions.assertTrue(withoutTerrain.isSteadyStateConverged(), "terrain-free case must converge");

    double[] withTerrainProfile = withTerrain.getPressureProfile();
    double[] withoutTerrainProfile = withoutTerrain.getPressureProfile();
    double dropWithTerrain = withTerrainProfile[0] - withTerrainProfile[withTerrainProfile.length - 1];
    double dropWithoutTerrain = withoutTerrainProfile[0] - withoutTerrainProfile[withoutTerrainProfile.length - 1];

    Assertions.assertTrue(dropWithoutTerrain > 0.0, "the terrain-free line must lose pressure");
    double relativeDifference = Math.abs(dropWithTerrain - dropWithoutTerrain) / dropWithoutTerrain;
    Assertions.assertTrue(relativeDifference < 0.35,
        "terrain handling moved the pressure drop by " + (100.0 * relativeDifference)
            + "%, which indicates unbounded liquid accumulation rather than a terrain correction");
  }
}
