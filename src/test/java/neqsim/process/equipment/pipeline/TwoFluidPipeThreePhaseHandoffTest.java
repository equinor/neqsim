package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;

/**
 * Steady-to-transient handoff on a three-phase flowline and rising outlet.
 *
 * @author Even Solbraa
 * @version 1.0
 */
@Tag("slow")
class TwoFluidPipeThreePhaseHandoffTest {
  private static final double OUTLET_PRESSURE_BARA = 56.40099362;
  private static final Phase[] PHASES = {Phase.GAS, Phase.OIL, Phase.WATER};

  /** Checks the fixed-boundary transient at common time across mesh and reporting-step refinement. */
  @Test
  void holdsThreePhaseThroughFlowWithPrescribedOutletFace() {
    double coarse = fixedBoundaryReplay(1, 0.25);
    assertEquals(coarse, fixedBoundaryReplay(2, 0.25), 0.15);
    assertEquals(coarse, fixedBoundaryReplay(1, 0.125), 0.05);
  }

  /** A prescribed physical inlet face delivers the specified feed at every legacy RK4 stage. */
  @Test
  void legacyRk4RetainsPrescribedThreePhaseInletRate() {
    TwoFluidPipe pipe = configure(TwoFluidPipeThreePhaseFlowlineBenchmark.createInlet(), 1);
    pipe.setEnableCoupledPressureMomentum(false);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.RK4);
    pipe.run();
    assertTrue(pipe.isSteadyStateConverged());
    for (int step = 0; step < 4; step++) {
      pipe.runTransient(0.5, UUID.randomUUID());
      TwoFluidMassBalanceReport report = checkAcceptedInterval(pipe, 0.5);
      assertEquals(20.0, report.getInletMassKg(Phase.TOTAL) / 0.5, 1.0e-8);
      assertEquals(20.0, report.getOutletMassKg(Phase.TOTAL) / 0.5, 0.1);
    }
  }

  /** Checks that a small choke move changes inlet rate without reversing the riser outlet phase flux. */
  @Test
  void respondsToBackpressureCoupledChokeOpening() {
    double held = chokeReplay(65.0);
    double reduced = chokeReplay(61.0);
    assertTrue(held > 19.0 && held < 21.0, "held opening: " + held);
    assertTrue(reduced < held - 0.5 && reduced > held - 2.5, "reduced opening: " + reduced);
  }

  /**
   * Checks all phase rates, pressure and the volume/mass ledgers for each accepted reporting interval.
   *
   * @param refinement cell count multiplier
   * @param interval reporting interval in s
   * @return final outlet mass flow in kg/s
   */
  private static double fixedBoundaryReplay(int refinement, double interval) {
    Stream inlet = TwoFluidPipeThreePhaseFlowlineBenchmark.createInlet();
    TwoFluidPipe pipe = configure(inlet, refinement);
    pipe.run();
    assertTrue(pipe.isSteadyStateConverged());
    TwoFluidSection[] initial = pipe.getSectionSnapshots();
    TwoFluidSection outlet = initial[initial.length - 1];
    double[] steadyRates = {outlet.getGasMassPerLength() * outlet.getGasVelocity(),
        outlet.getOilMassPerLength() * outlet.getOilVelocity(),
        outlet.getWaterMassPerLength() * outlet.getWaterVelocity()};
    double initialInletPressure = pipe.getInletFacePressure("bara");

    for (int step = 0; step < (int) (10.0 / interval); step++) {
      pipe.runTransient(interval, UUID.randomUUID());
      TwoFluidMassBalanceReport report = checkAcceptedInterval(pipe, interval);
      assertEquals(20.0, report.getInletMassKg(Phase.TOTAL) / interval, 1.0e-8);
      assertEquals(20.0, report.getOutletMassKg(Phase.TOTAL) / interval, 1.0);
      for (int phase = 0; phase < PHASES.length; phase++) {
        assertEquals(steadyRates[phase], report.getOutletMassKg(PHASES[phase]) / interval, 0.08 * steadyRates[phase],
            PHASES[phase].name());
      }
      assertEquals(initialInletPressure, pipe.getInletFacePressure("bara"), 0.3);
    }
    return pipe.getOutletStream().getFlowRate("kg/sec");
  }

  /**
   * Runs the same pressure-driven choke and pipeline with either the held or a lower opening.
   *
   * @param targetOpening opening in percent after the common preconditioning interval
   * @return hydraulic rate through the choke at the final step in kg/s
   */
  private static double chokeReplay(double targetOpening) {
    Stream upstream = TwoFluidPipeThreePhaseFlowlineBenchmark.createInlet();
    upstream.setPressure(80.0, "bara");
    upstream.run();
    ThrottlingValve choke = new ThrottlingValve("Production choke", upstream);
    choke.setOutletPressure(70.0, "bara");
    choke.setIsoThermal(true);
    choke.setPercentValveOpening(65.0);
    choke.run();
    double initialCapacity = choke.calculateMolarFlow() * upstream.getFluid().getMolarMass("kg/mol");
    choke.setKv(choke.getKv() * 20.0 / initialCapacity);
    choke.setCalculateSteadyState(false);

    TwoFluidPipe pipe = configure((Stream) choke.getOutletStream(), 1);
    pipe.run();
    assertTrue(pipe.isSteadyStateConverged());
    double interval = 0.25;
    pipe.runTransient(interval, UUID.randomUUID());
    checkAcceptedInterval(pipe, interval);
    for (int step = 1; step < 16; step++) {
      if (step == 8) {
        choke.setPercentValveOpening(targetOpening);
      }
      double inletFacePressure = pipe.getInletFacePressure("bara");
      choke.getOutletStream().setPressure(inletFacePressure, "bara");
      choke.runTransient(interval, UUID.randomUUID());
      pipe.runTransient(interval, UUID.randomUUID());
      TwoFluidMassBalanceReport report = checkAcceptedInterval(pipe, interval);
      assertEquals(choke.getOutletStream().getFlowRate("kg/sec"), report.getInletMassKg(Phase.TOTAL) / interval,
          1.0e-8);
      assertTrue(report.getOutletMassKg(Phase.TOTAL) / interval > 18.0);
    }
    return choke.getOutletStream().getFlowRate("kg/sec");
  }

  /**
   * Checks reconciled transfer after both explicit advection and the acoustic correction.
   *
   * @param pipe accepted pipe state
   * @param interval requested physical interval in s
   * @return accepted mass ledger
   */
  private static TwoFluidMassBalanceReport checkAcceptedInterval(TwoFluidPipe pipe, double interval) {
    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    assertEquals(interval, report.getElapsedTimeSeconds(), 1.0e-12);
    assertEquals(OUTLET_PRESSURE_BARA, pipe.getOutletStream().getPressure("bara"), 1.0e-9);
    assertFalse(pipe.isTransientCoupledPressureMomentumFailureDetected());
    assertFalse(pipe.isTransientCoupledPressureMomentumCorrectionLimited());
    for (Phase phase : PHASES) {
      assertTrue(report.getOutletMassKg(phase) >= 0.0, phase.name());
      assertEquals(0.0, report.getResidualKg(phase), 1.0e-8, phase.name());
    }
    return report;
  }

  /**
   * Configures the fixed-physical-outlet coupled Euler route from the shared flowline fixture.
   *
   * @param inlet initialized feed
   * @param refinement cell count multiplier
   * @return uninitialized pipe
   */
  private static TwoFluidPipe configure(Stream inlet, int refinement) {
    TwoFluidPipe pipe = TwoFluidPipeThreePhaseFlowlineBenchmark.createPipe(inlet, refinement);
    pipe.setOutletPressure(OUTLET_PRESSURE_BARA, "bara");
    pipe.setAllowOutletPhaseBackflow(false);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setEnableInterfacialPressure(true);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.EULER);
    pipe.setEnableAdaptiveTimestepping(true);
    return pipe;
  }
}
