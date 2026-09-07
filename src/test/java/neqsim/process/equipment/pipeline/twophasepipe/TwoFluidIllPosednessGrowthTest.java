package neqsim.process.equipment.pipeline.twophasepipe;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Mesh-refinement behaviour of the shortest resolved holdup mode.
 *
 * <p>
 * {@link TwoFluidHyperbolicityTest} shows that the characteristics of the classical two-fluid system are complex
 * wherever two phases coexist with slip, so the initial-value problem is formally ill-posed. Ill-posedness has a
 * signature that can be measured: the growth rate of the shortest resolved wavelength is set by the mesh, so it must
 * rise as the mesh is refined, roughly as one over the cell size. A physical instability does not behave that way.
 * </p>
 *
 * <p>
 * A small alternating holdup perturbation is applied to the conservative state and compared with an unperturbed
 * trajectory. Subtracting that control is essential: even a smooth linear axial profile has a nonzero alternating sum,
 * and its changing startup gradient does not measure growth of a two-cell perturbation. Both meshes must damp the
 * seeded mode, with stronger damping on the finer mesh. This is a numerical regression for the specified flow regime,
 * not a general well-posedness proof for the model.
 * </p>
 */
public class TwoFluidIllPosednessGrowthTest {

  private static final double LENGTH = 500.0;
  private static final double DIAMETER = 0.20;

  private static TwoFluidPipe buildPipe(int sections, boolean stabilized) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 60.0);
    fluid.addComponent("methane", 60.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-heptane", 20.0);
    fluid.addComponent("nC10", 12.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(30.0 * 3600.0, "kg/hr");
    feed.setTemperature(50.0, "C");
    feed.setPressure(60.0, "bara");
    feed.run();

    TwoFluidPipe pipe = new TwoFluidPipe("pipe", feed);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setNumberOfSections(sections);
    pipe.setElevationProfile(new double[sections]);
    pipe.setOutletPressure(59.0, "bara");
    pipe.setEnableSlugTracking(false);
    pipe.setEnableInterfacialPressure(stabilized);
    pipe.run();
    return pipe;
  }

  /** Amplitude of the seeded two-cell holdup mode after subtracting the evolving control. */
  private static double sawtoothAmplitude(double[] holdup, double[] control) {
    double sum = 0.0;
    for (int i = 0; i < holdup.length; i++) {
      sum += ((i % 2 == 0) ? 1.0 : -1.0) * (holdup[i] - control[i]);
    }
    return Math.abs(sum) / holdup.length;
  }

  /**
   * Exponential growth rate of the two-cell mode over a short window.
   *
   * @param sections mesh size
   * @param stabilized whether the interfacial pressure closure is active
   * @return growth rate in one over seconds; negative means the mode decays
   */
  private static double growthRate(int sections, boolean stabilized) throws Exception {
    TwoFluidPipe pipe = buildPipe(sections, stabilized);
    TwoFluidPipe control = buildPipe(sections, stabilized);
    pipe.runTransient(2.0, null);
    control.runTransient(2.0, null);
    Assertions.assertArrayEquals(control.getLiquidHoldupProfile(), pipe.getLiquidHoldupProfile(), 0.0,
        "The seeded and control trajectories must start from identical states");
    double seedAmplitude = 1.0e-6;
    seedConservativeHoldupMode(pipe, seedAmplitude);
    double start = sawtoothAmplitude(pipe.getLiquidHoldupProfile(), control.getLiquidHoldupProfile());
    Assertions.assertEquals(seedAmplitude * (sections - 2.0) / sections, start, 1.0e-12,
        "The interior perturbation must populate the shortest resolved mode");
    double elapsed = 0.0;
    for (int i = 0; i < 5; i++) {
      pipe.runTransient(2.0, null);
      control.runTransient(2.0, null);
      elapsed += 2.0;
    }
    double end = sawtoothAmplitude(pipe.getLiquidHoldupProfile(), control.getLiquidHoldupProfile());
    Assertions.assertTrue(end < start, "The seeded short-wavelength mode must decay on " + sections + " cells");
    double floor = 1.0e-14;
    return Math.log(Math.max(end, floor) / Math.max(start, floor)) / elapsed;
  }

  /** Seed the two-phase state at fixed pressure, temperature and phase velocities, leaving boundary cells unchanged. */
  private static void seedConservativeHoldupMode(TwoFluidPipe pipe, double amplitude) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    TwoFluidSection[] cells = (TwoFluidSection[]) field.get(pipe);
    for (int i = 1; i < cells.length - 1; i++) {
      TwoFluidSection cell = cells[i];
      double[] state = cell.getStateVector();
      Assertions.assertTrue(state[0] > 0.0 && state[1] > 0.0, "The fixture requires gas and oil");
      Assertions.assertEquals(0.0, state[2], 0.0, "The fixture must not contain a water phase");
      double holdupChange = (i % 2 == 0 ? 1.0 : -1.0) * amplitude;
      double holdup = cell.getLiquidHoldup() + holdupChange;
      Assertions.assertTrue(holdup > 0.0 && holdup < 1.0, "The perturbation must retain both phases");
      double gasMassChange = -holdupChange * cell.getGasDensity() * cell.getArea();
      double oilMassChange = holdupChange * cell.getOilDensity() * cell.getArea();
      double gasVelocity = state[3] / state[0];
      double oilVelocity = state[4] / state[1];
      state[0] += gasMassChange;
      state[1] += oilMassChange;
      state[3] += gasMassChange * gasVelocity;
      state[4] += oilMassChange * oilVelocity;
      // Gas and oil exchange equal volumes, so the pressure-volume contributions cancel.
      state[6] += gasMassChange * (cell.getGasEnthalpy() + 0.5 * gasVelocity * gasVelocity)
          + oilMassChange * (cell.getLiquidEnthalpy() + 0.5 * oilVelocity * oilVelocity);
      cell.setLiquidHoldup(holdup);
      cell.setGasHoldup(1.0 - holdup);
      // Do not rebuild the whole conservative state from primitives: that would also change
      // any pre-existing volume residual and introduce a disturbance unrelated to the seed.
      cell.setStateVector(state);
    }
  }

  /** The short mode must not amplify as the mesh is refined, or the flux scheme is driving the runaway. */
  @Test
  void testShortWavelengthModeDoesNotAmplifyUnderMeshRefinement() throws Exception {
    double coarse = growthRate(20, false);
    double fine = growthRate(80, false);

    Assertions.assertTrue(fine < coarse,
        "a mesh-driven instability would grow faster on the finer mesh, but the two-cell growth rate went from "
            + coarse + " per second on 20 cells to " + fine + " per second on 80 cells");
  }

  /** The stabilizer must not introduce a short-wavelength mode of its own. */
  @Test
  void testStabilizerDoesNotAmplifyTheShortWavelengthMode() throws Exception {
    double coarse = growthRate(20, true);
    double fine = growthRate(80, true);

    Assertions.assertTrue(fine <= coarse + 1.0e-9, "the stabilized short-mode growth rate went from " + coarse
        + " per second on 20 cells to " + fine + " per second on 80 cells");
  }
}
