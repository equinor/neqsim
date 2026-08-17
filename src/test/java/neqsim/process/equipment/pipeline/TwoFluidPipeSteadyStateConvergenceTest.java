package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for the steady-state convergence controls on {@link TwoFluidPipe}.
 *
 * <p>
 * The steady-state refinement loop is an under-relaxed fixed-point sweep, so information travels roughly one section
 * per iteration. A fixed iteration budget silently returns an unconverged profile on long, finely-discretised transport
 * lines. These tests guard the mesh-scaled default budget, the explicit override, and the convergence flag that makes
 * non-convergence visible.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class TwoFluidPipeSteadyStateConvergenceTest {

  /**
   * Build a lean gas-condensate stream typical of a wet gas export line.
   *
   * @return a run stream at 100 bara and 40 degrees Celsius
   */
  private Stream makeStream() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 100.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.03);
    fluid.addComponent("n-heptane", 0.02);
    fluid.setMixingRule("classic");

    Stream stream = new Stream("feed", fluid);
    stream.setFlowRate(200000.0, "kg/hr");
    stream.setPressure(100.0, "bara");
    stream.setTemperature(40.0, "C");
    stream.run();
    return stream;
  }

  /**
   * Build a horizontal pipe of the requested length and mesh.
   *
   * @param length pipe length in metres
   * @param sections number of finite-volume sections
   * @return a configured pipe that has not yet been run
   */
  private TwoFluidPipe makePipe(double length, int sections) {
    TwoFluidPipe pipe = new TwoFluidPipe("pipe", makeStream());
    pipe.setLength(length);
    // 0.3 m ran this line into the 1 bara pressure floor, which made every case clamp to the
    // same 99 bar drop: the terrain assertions below then compared the clamp against itself.
    pipe.setDiameter(0.5);
    pipe.setRoughness(4.5e-5);
    pipe.setNumberOfSections(sections);
    pipe.setElevationProfile(new double[sections + 1]);
    pipe.setEnableTerrainTracking(false);
    return pipe;
  }

  /** The iteration budget must scale with the mesh so long lines are not silently truncated. */
  @Test
  void testIterationBudgetScalesWithMesh() {
    TwoFluidPipe pipe = makePipe(40000.0, 80);
    pipe.run();

    assertTrue(pipe.isSteadyStateConverged(),
        "A horizontal line without terrain tracking must reach the steady-state tolerance");
    assertFalse(pipe.isSteadyStateWallClockLimited(), "The wall-clock guard must not trigger on a short test case");
    // The legacy hard-coded budget was 100; the mesh-scaled default must exceed it here.
    assertTrue(pipe.getSteadyStateMaxIterations() == 0,
        "No explicit limit was set, so the mesh-derived default must be in use");
  }

  /** An explicit, deliberately tiny budget must be honoured and reported as non-converged. */
  @Test
  void testExplicitIterationLimitIsHonoured() {
    TwoFluidPipe pipe = makePipe(40000.0, 80);
    pipe.setSteadyStateMaxIterations(2);
    assertEquals(2, pipe.getSteadyStateMaxIterations());
    pipe.run();

    assertTrue(pipe.getSteadyStateIterationsUsed() <= 2, "The solver must respect the user-specified iteration limit");
  }

  /** A horizontal line must reproduce a Darcy-Weisbach order-of-magnitude pressure drop. */
  @Test
  void testHorizontalPressureDropIsPhysical() {
    TwoFluidPipe pipe = makePipe(20000.0, 40);
    pipe.run();

    double[] profile = pipe.getPressureProfile();
    double dpBar = (profile[0] - profile[profile.length - 1]) / 1.0e5;
    assertTrue(dpBar > 0.0, "Pressure drop along a horizontal line must be positive");
    assertTrue(dpBar < 100.0, "Pressure drop must stay physically bounded");
  }

  /**
   * Solve a line whose elevation profile undulates with the given amplitude.
   *
   * @param amplitude undulation amplitude in metres, zero for a flat line
   * @return total pressure drop in bar
   */
  private double solveWithUndulation(double amplitude) {
    int sections = 60;
    TwoFluidPipe pipe = makePipe(40000.0, sections);

    // Sawtooth terrain with zero net elevation change over the line.
    double[] elevations = new double[sections + 1];
    for (int i = 0; i <= sections; i++) {
      elevations[i] = amplitude * Math.sin(2.0 * Math.PI * 6.0 * i / sections);
    }
    pipe.setElevationProfile(elevations);
    pipe.run();

    assertTrue(pipe.isSteadyStateConverged(), "Undulating line must reach the steady-state tolerance");
    double[] profile = pipe.getPressureProfile();
    return (profile[0] - profile[profile.length - 1]) / 1.0e5;
  }

  /**
   * Terrain undulation with zero net elevation change must not invent pressure drop.
   *
   * <p>
   * The forward-marching initialization and the iterative refinement must integrate the same discrete momentum balance.
   * When they disagree the hydrostatic terms stop telescoping and an undulating seabed profile invents a large spurious
   * pressure drop that appears discontinuously once the local inclination passes a threshold.
   * </p>
   *
   * <p>
   * Mild undulation must therefore leave the pressure drop essentially untouched. Once the slopes are steep enough to
   * reclassify sections of the flow map, a real terrain response appears and the pressure drop must RISE: uphill
   * sections hold more liquid and shear more wall. The single absolute bound that used to stand here was satisfied by a
   * model that instead LOWERED the pressure drop by 4.5 per cent, which is the signature of a homogeneous friction
   * form, where the gradient scales as {@code G^2 / rho_mix} and extra liquid therefore reduces it. Measured on this
   * fixture the response is now monotone and of the right sign: -0.08, -0.20, +2.06, +2.96, +4.88 and +7.12 per cent at
   * 1, 5, 10, 20, 35 and 50 m amplitude.
   * </p>
   */
  @Test
  void testZeroNetElevationUndulationDoesNotCreatePressureDrop() {
    double flat = solveWithUndulation(0.0);

    for (double amplitude : new double[] { 1.0, 5.0 }) {
      double undulating = solveWithUndulation(amplitude);
      double deviationPercent = 100.0 * Math.abs(undulating - flat) / flat;
      assertTrue(deviationPercent < 1.0,
          "Mild undulation of " + amplitude + " m with zero net elevation change moved the pressure drop from " + flat
              + " bar to " + undulating + " bar (" + deviationPercent + "%)");
    }

    double previous = flat;
    for (double amplitude : new double[] { 10.0, 20.0, 35.0, 50.0 }) {
      double undulating = solveWithUndulation(amplitude);
      double deviationPercent = 100.0 * (undulating - flat) / flat;
      assertTrue(deviationPercent > -1.0, "Undulation of " + amplitude
          + " m must not reduce the pressure drop, but moved it from " + flat + " bar to " + undulating + " bar");
      assertTrue(deviationPercent < 15.0,
          "Undulation of " + amplitude + " m with zero net elevation change moved the pressure drop from " + flat
              + " bar to " + undulating + " bar (" + deviationPercent + "%)");
      assertTrue(undulating >= previous - 1.0e-9, "The terrain response must grow with amplitude, but " + amplitude
          + " m gave " + undulating + " bar against " + previous + " bar at the previous amplitude");
      previous = undulating;
    }
  }

  /** The terrain response must be continuous in the undulation amplitude. */
  @Test
  void testTerrainResponseIsContinuousInAmplitude() {
    double previous = solveWithUndulation(0.0);
    for (double amplitude : new double[] { 2.0, 4.0, 6.0, 8.0, 10.0 }) {
      double current = solveWithUndulation(amplitude);
      double stepPercent = 100.0 * Math.abs(current - previous) / previous;
      assertTrue(stepPercent < 5.0, "Pressure drop stepped by " + stepPercent
          + "% when the undulation amplitude reached " + amplitude + " m; the terrain response must be continuous");
      previous = current;
    }
  }
}
