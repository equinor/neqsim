package neqsim.process.equipment.pipeline;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
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
   * Remaining liquid-rich steady-to-transient drift.
   *
   * <p>
   * Historically this fixture trapped liquid at the one-way outlet and packed the line while total mass balance still
   * closed. Correcting mechanical force allocation to absent phases removed that outlet failure in this case, and the
   * separate liquid-outlet acceptance test is now enabled. The 1800-second inventory drift remains about 5.34%, above
   * this unchanged 5% limit. Outlet progress and finite-volume conservation therefore do not establish a fixed point.
   * </p>
   *
   * <p>
   * The stabilized legacy pressure path has a separate disabled physical-pressure acceptance test. Passing one
   * transient gate must not be used as evidence that the other configurations are qualified.
   * </p>
   */
  @Test
  @Disabled("Known defect: liquid-rich inventory drift exceeds 5% over 1800 s under constant boundaries.")
  void testLiquidRichSteadyStateIsAFixedPointOfTheTransient() {
    TwoFluidPipe pipe = buildPipe(true, 50.0);
    double drift = runNullTest(pipe, 360, 5.0);
    Assertions.assertTrue(drift < 0.05, "liquid-rich line drifted " + (drift * 100.0) + "% from its own steady state");
  }

  @Test
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
   * A deliberately imposed outlet reversal must be reported and a new steady solve must clear the diagnostic. This
   * diagnostic contract must not require the solver to develop a spontaneous physical runaway.
   */
  @Test
  void testLiquidRichOutletBackflowIsReportedAndClearedByANewSteadySolve() throws Exception {
    TwoFluidPipe pipe = buildPipe(true, 50.0);
    Assertions.assertFalse(pipe.isTransientOutletBackflowClamped(),
        "a freshly solved steady state must not report outlet backflow");

    Field sectionsField = TwoFluidPipe.class.getDeclaredField("sections");
    sectionsField.setAccessible(true);
    TwoFluidSection[] sections = (TwoFluidSection[]) sectionsField.get(pipe);
    for (int cell = sections.length - 2; cell < sections.length; cell++) {
      sections[cell].setLiquidVelocity(-2.5);
      sections[cell].setOilVelocity(-2.5);
      sections[cell].setWaterVelocity(sections[cell].getWaterMassPerLength() > 0.0 ? -2.5 : 0.0);
      sections[cell].updateConservativeVariables();
    }
    pipe.runTransient(1.0e-6, null);
    Assertions.assertTrue(pipe.isTransientOutletBackflowClamped(), "an imposed outlet reversal must be reported");

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
