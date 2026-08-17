package neqsim.process.equipment.pipeline.twophasepipe;

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
 * That signature is absent here. The two-cell mode is damped more strongly as the mesh is refined, because the upwind
 * flux carries numerical diffusion that also scales as one over the cell size and dominates at these resolutions. The
 * liquid-rich transient runaway recorded in {@code TwoFluidPipeTransientNullTest} is therefore not short-wavelength
 * ill-posedness in this regime, and the flux scheme is the wrong place to look for it. This test keeps that conclusion
 * falsifiable: if a change ever makes the short mode amplify under refinement, it fails.
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

  /** Amplitude of the shortest resolved (two-cell) holdup mode. */
  private static double sawtoothAmplitude(double[] holdup) {
    double sum = 0.0;
    for (int i = 0; i < holdup.length; i++) {
      sum += ((i % 2 == 0) ? 1.0 : -1.0) * holdup[i];
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
  private static double growthRate(int sections, boolean stabilized) {
    TwoFluidPipe pipe = buildPipe(sections, stabilized);
    pipe.runTransient(2.0, null);
    double start = sawtoothAmplitude(pipe.getLiquidHoldupProfile());
    double elapsed = 0.0;
    for (int i = 0; i < 5; i++) {
      pipe.runTransient(2.0, null);
      elapsed += 2.0;
    }
    double end = sawtoothAmplitude(pipe.getLiquidHoldupProfile());
    double floor = 1.0e-14;
    return Math.log(Math.max(end, floor) / Math.max(start, floor)) / elapsed;
  }

  /** The short mode must not amplify as the mesh is refined, or the flux scheme is driving the runaway. */
  @Test
  void testShortWavelengthModeDoesNotAmplifyUnderMeshRefinement() {
    double coarse = growthRate(20, false);
    double fine = growthRate(80, false);

    Assertions.assertTrue(fine < coarse,
        "a mesh-driven instability would grow faster on the finer mesh, but the two-cell growth rate went from "
            + coarse + " per second on 20 cells to " + fine + " per second on 80 cells");
  }

  /** The stabilizer must not introduce a short-wavelength mode of its own. */
  @Test
  void testStabilizerDoesNotAmplifyTheShortWavelengthMode() {
    double coarse = growthRate(20, true);
    double fine = growthRate(80, true);

    Assertions.assertTrue(fine <= coarse + 1.0e-9, "the stabilized short-mode growth rate went from " + coarse
        + " per second on 20 cells to " + fine + " per second on 80 cells");
  }
}
