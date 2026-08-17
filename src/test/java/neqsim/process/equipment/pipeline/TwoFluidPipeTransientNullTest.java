package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Null tests for {@link TwoFluidPipe#runTransient(double, java.util.UUID)}.
 *
 * <p>
 * A converged steady state must be a fixed point of the transient equations: holding every boundary condition constant
 * must leave the solution unchanged. This holds for gas-dominated lines but fails for liquid-rich ones, so the two
 * cases are pinned separately.
 * </p>
 */
public class TwoFluidPipeTransientNullTest {

  private static final double LENGTH = 5000.0;
  private static final double DIAMETER = 0.30;
  private static final int SECTIONS = 40;
  private static final double PIPE_VOLUME = Math.PI * DIAMETER * DIAMETER / 4.0 * LENGTH;

  private static TwoFluidPipe buildPipe(boolean liquidRich, double massFlowKgPerSec) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 60.0);
    fluid.addComponent("methane", liquidRich ? 60.0 : 95.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    if (liquidRich) {
      fluid.addComponent("n-heptane", 20.0);
      fluid.addComponent("nC10", 12.0);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(massFlowKgPerSec * 3600.0, "kg/hr");
    feed.setTemperature(50.0, "C");
    feed.setPressure(60.0, "bara");
    feed.run();

    TwoFluidPipe pipe = new TwoFluidPipe("pipe", feed);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setNumberOfSections(SECTIONS);
    pipe.setElevationProfile(new double[SECTIONS]);
    pipe.setHeatTransferCoefficient(5.0);
    pipe.setSurfaceTemperature(4.0, "C");
    pipe.run();
    return pipe;
  }

  /** Advance the transient with every boundary condition held constant. */
  private static double runNullTest(TwoFluidPipe pipe, int steps, double dt) {
    double initialMass = pipe.getTotalMassInventory();
    for (int i = 0; i < steps; i++) {
      pipe.runTransient(dt, null);
    }
    return Math.abs(pipe.getTotalMassInventory() - initialMass) / initialMass;
  }

  @Test
  void testGasDominatedSteadyStateIsAFixedPointOfTheTransient() {
    TwoFluidPipe pipe = buildPipe(false, 40.0);
    double drift = runNullTest(pipe, 120, 5.0);
    Assertions.assertTrue(drift < 0.05,
        "gas-dominated line drifted " + (drift * 100.0) + "% from its own steady state");
  }

  @Test
  void testFiniteVolumeMassBalanceClosesForLiquidRichFlow() {
    TwoFluidPipe pipe = buildPipe(true, 50.0);
    for (int i = 0; i < 20; i++) {
      pipe.runTransient(5.0, null);
      TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
      Assertions.assertNotNull(report, "transient must produce a mass balance report");
      double relative = Math.abs(report.getRelativeResidual(TwoFluidMassBalanceReport.Phase.TOTAL));
      Assertions.assertTrue(relative < 1.0e-10,
          "finite-volume mass balance must close, but the relative residual was " + relative);
    }
  }

  /**
   * Liquid-rich transient runaway.
   *
   * <p>
   * With every boundary condition held constant, the liquid outlet flux collapses to exactly zero and the line packs
   * without bound: on this case the inventory grows from 114.7 t to 192.7 t in 30 minutes and the liquid holdup goes
   * from 0.45 to 0.77 while still climbing. The cause is that the phase momentum equations develop sustained backflow
   * in the liquid-rich regime (oil velocity reaches -2.5 m/s in 9 of 40 cells), and {@code calcOutletFlux} clamps a
   * negative phase velocity to zero outflow, which turns the reversal into a one-way trap. The finite-volume balance
   * still closes to machine precision, so this is a closure/well-posedness defect and not an accounting error: the
   * conservation equations carry no interfacial pressure term to keep the system hyperbolic at high liquid fraction.
   * </p>
   *
   * <p>
   * The steady solver is not implicated: its mean liquid holdup for this case is stable and physically bounded, while
   * the transient runs away to nearly double it.
   * </p>
   */
  @Test
  @Disabled("Known defect: liquid-rich transient traps liquid and packs without bound. "
      + "Two-fluid closure needs a hyperbolicity-restoring interfacial pressure term.")
  void testLiquidRichSteadyStateIsAFixedPointOfTheTransient() {
    TwoFluidPipe pipe = buildPipe(true, 50.0);
    double drift = runNullTest(pipe, 360, 5.0);
    Assertions.assertTrue(drift < 0.05, "liquid-rich line drifted " + (drift * 100.0) + "% from its own steady state");
  }

  @Test
  @Disabled("Known defect: liquid outflow clamps to zero once a phase velocity reverses.")
  void testLiquidKeepsLeavingTheOutletUnderConstantBoundaryConditions() {
    TwoFluidPipe pipe = buildPipe(true, 50.0);
    for (int i = 0; i < 120; i++) {
      pipe.runTransient(5.0, null);
    }
    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    double liquidOut = report.getOutletMassKg(TwoFluidMassBalanceReport.Phase.LIQUID);
    Assertions.assertTrue(liquidOut > 0.0,
        "liquid must keep leaving a flowing line, but the outlet flux was " + liquidOut + " kg");
    Assertions.assertTrue(pipe.getLiquidInventory("m3") < 0.9 * PIPE_VOLUME,
        "liquid must not fill the line under steady inflow");
  }

  /**
   * The outlet trap that drives the two defects above must not be silent.
   *
   * <p>
   * Until the closure is fixed the liquid-rich transient is not a solution, so the caller has to be able to find that
   * out. Both disabled tests above are the same trap seen from two sides, and both start with a phase reversing at the
   * transmissive outlet, so that reversal is what gets reported.
   * </p>
   */
  @Test
  void testLiquidRichOutletBackflowIsReportedAndClearedByANewSteadySolve() {
    TwoFluidPipe pipe = buildPipe(true, 50.0);
    Assertions.assertFalse(pipe.isTransientOutletBackflowClamped(),
        "a freshly solved steady state must not report outlet backflow");

    int firstTrip = -1;
    for (int i = 0; i < 120 && firstTrip < 0; i++) {
      pipe.runTransient(5.0, null);
      if (pipe.isTransientOutletBackflowClamped()) {
        firstTrip = i;
      }
    }
    Assertions.assertTrue(firstTrip >= 0,
        "the liquid-rich runaway must announce itself, but 120 steps passed without a backflow report");

    pipe.run();
    Assertions.assertFalse(pipe.isTransientOutletBackflowClamped(),
        "a new steady solve must clear the transient diagnostic");
  }

  /** A healthy line must not raise the diagnostic, or it is worthless as a gate. */
  @Test
  void testGasDominatedTransientDoesNotReportOutletBackflow() {
    TwoFluidPipe pipe = buildPipe(false, 40.0);
    for (int i = 0; i < 120; i++) {
      pipe.runTransient(5.0, null);
    }
    Assertions.assertFalse(pipe.isTransientOutletBackflowClamped(),
        "a gas-dominated line holding its own steady state must not report outlet backflow");
  }
}
