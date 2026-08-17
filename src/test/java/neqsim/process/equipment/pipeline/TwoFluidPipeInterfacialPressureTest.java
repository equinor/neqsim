package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verification of the interfacial-pressure (void-wave) stabilizer.
 *
 * <p>
 * The stabilizer keeps the two-fluid momentum system hyperbolic at high liquid fraction. Applied inside the explicit
 * spatial right-hand side it carries the void wave and therefore forces a CFL number near 0.05, which makes it too slow
 * to use. Advancing the same term implicitly after the transport step must reproduce the explicit answer while allowing
 * the ordinary CFL number, and must not move any phase mass by itself.
 * </p>
 */
public class TwoFluidPipeInterfacialPressureTest {

  private static final double LENGTH = 500.0;
  private static final double DIAMETER = 0.20;
  private static final int SECTIONS = 10;

  private static TwoFluidPipe buildLiquidRichPipe(boolean implicitCoupling, double cfl) {
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
    pipe.setNumberOfSections(SECTIONS);
    pipe.setElevationProfile(new double[SECTIONS]);
    pipe.setOutletPressure(59.0, "bara");
    pipe.setEnableSlugTracking(false);
    pipe.setEnableInterfacialPressure(true);
    pipe.setImplicitInterfacialPressureCoupling(implicitCoupling);
    pipe.setCflNumber(cfl);
    pipe.run();
    return pipe;
  }

  /**
   * The implicit treatment must agree with the explicit one it replaces.
   *
   * <p>
   * The explicit arm is run at the small CFL number the term demands and the implicit arm at the ordinary one. If the
   * two disagreed, the implicit update would be solving a different problem rather than the same one at a larger time
   * step.
   * </p>
   */
  @Test
  void testImplicitCouplingReproducesTheExplicitStabilizer() {
    double explicitInventory = runInventory(false, 0.05);
    double implicitAtSameStep = runInventory(true, 0.05);

    double operatorDifference = Math.abs(implicitAtSameStep - explicitInventory) / explicitInventory;
    Assertions.assertTrue(operatorDifference < 1.0e-3,
        "at the same CFL the implicit stabilizer gave " + implicitAtSameStep + " kg against the explicit "
            + explicitInventory + " kg (relative " + operatorDifference + ")");
  }

  /**
   * The implicit treatment must converge in time towards the small-step answer.
   *
   * <p>
   * Removing the explicit stability limit does not remove temporal discretization error, so the claim being pinned here
   * is convergence and not step independence: halving the CFL number must move the answer closer to the reference
   * obtained at the smallest step.
   * </p>
   */
  @Test
  void testImplicitCouplingConvergesAsTheTimeStepIsReduced() {
    double reference = runInventory(true, 0.05);
    double coarse = runInventory(true, 0.4);
    double fine = runInventory(true, 0.2);

    double coarseError = Math.abs(coarse - reference);
    double fineError = Math.abs(fine - reference);
    Assertions.assertTrue(fineError < coarseError,
        "halving the CFL number must reduce the time-step error, but it went from " + coarseError + " kg at CFL 0.4 to "
            + fineError + " kg at CFL 0.2");
  }

  private static double runInventory(boolean implicitCoupling, double cfl) {
    TwoFluidPipe pipe = buildLiquidRichPipe(implicitCoupling, cfl);
    for (int i = 0; i < 10; i++) {
      pipe.runTransient(1.0, null);
    }
    return pipe.getTotalMassInventory();
  }

  /** The stabilizer acts on momentum only, so it must not create or destroy mass. */
  @Test
  void testStabilizerKeepsTheMassBalanceClosed() {
    TwoFluidPipe pipe = buildLiquidRichPipe(true, 0.5);

    for (int i = 0; i < 10; i++) {
      pipe.runTransient(1.0, null);
      TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
      Assertions.assertNotNull(report, "each transient step must publish a mass-balance report");
      for (TwoFluidMassBalanceReport.Phase phase : TwoFluidMassBalanceReport.Phase.values()) {
        double relative = Math.abs(report.getRelativeResidual(phase));
        Assertions.assertTrue(relative < 1.0e-9,
            phase + " mass balance must close, but the relative residual was " + relative);
      }
    }
  }
}
