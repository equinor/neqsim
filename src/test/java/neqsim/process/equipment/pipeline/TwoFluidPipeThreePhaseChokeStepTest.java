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
 * Pressure-coupled production-choke step on the three-phase flowline and riser (equinor/neqsim#5046).
 *
 * <p>
 * Before the fix, a 65 to 58 percent choke step stopped riser water outflow within 3 s: the oil-water regime detector
 * reclassified the gas-agitated riser as stratified, the oil-water drag collapsed and the calibrated steady momentum
 * correction drove water back down the riser against rising oil.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
@Tag("slow")
class TwoFluidPipeThreePhaseChokeStepTest {
  private static final double OUTLET_PRESSURE_BARA = 56.40099362;
  private static final Phase[] PHASES = {Phase.GAS, Phase.OIL, Phase.WATER};
  private static final double INTERVAL = 0.25;

  /** All three phases keep leaving the riser after the choke step, and liquid builds up as gas slows. */
  @Test
  void chokeStepKeepsThreePhaseRiserOutflow() {
    double[] held = replay(65.0, 40.0);
    double[] stepped = replay(58.0, 40.0);
    // Index 0: minimum interval water outflow after the step, 1: final water inventory in kg,
    // 2: minimum water velocity in the riser cells after the step.
    assertTrue(stepped[0] > 1.0, "minimum water outflow after the step: " + stepped[0]);
    assertTrue(stepped[2] > 0.0, "riser water counterflow: " + stepped[2]);
    assertTrue(held[0] > 3.0, "held minimum water outflow: " + held[0]);
    assertTrue(stepped[1] > held[1], "lower gas rate must hold more water: " + stepped[1] + " vs " + held[1]);
  }

  /**
   * Replays the issue's coupled choke and pipe for one target opening.
   *
   * @param targetOpening choke opening in percent from 10 s
   * @param endTime replay length in s
   * @return minimum water outflow after the step in kg/s, final water inventory in kg and minimum riser water velocity
   * after the step in m/s
   */
  private static double[] replay(double targetOpening, double endTime) {
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

    TwoFluidPipe pipe = TwoFluidPipeThreePhaseFlowlineBenchmark.createPipe((Stream) choke.getOutletStream(), 1);
    pipe.setOutletPressure(OUTLET_PRESSURE_BARA, "bara");
    pipe.setAllowOutletPhaseBackflow(false);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setEnableInterfacialPressure(true);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.EULER);
    pipe.setEnableAdaptiveTimestepping(true);
    pipe.run();
    assertTrue(pipe.isSteadyStateConverged());

    double minimumWaterOutflow = Double.POSITIVE_INFINITY;
    double minimumRiserWaterVelocity = Double.POSITIVE_INFINITY;
    int steps = (int) Math.round(endTime / INTERVAL);
    for (int step = 0; step < steps; step++) {
      if (step > 0) {
        if (step == (int) Math.round(10.0 / INTERVAL)) {
          choke.setPercentValveOpening(targetOpening);
        }
        choke.getOutletStream().setPressure(pipe.getInletFacePressure("bara"), "bara");
        choke.runTransient(INTERVAL, UUID.randomUUID());
      }
      pipe.runTransient(INTERVAL, UUID.randomUUID());
      TwoFluidMassBalanceReport report = checkAcceptedInterval(pipe);
      if (step > 0) {
        assertEquals(choke.getOutletStream().getFlowRate("kg/sec"), report.getInletMassKg(Phase.TOTAL) / INTERVAL,
            1.0e-8);
      }
      if ((step + 1) * INTERVAL > 10.0) {
        minimumWaterOutflow = Math.min(minimumWaterOutflow, report.getOutletMassKg(Phase.WATER) / INTERVAL);
        TwoFluidSection[] sections = pipe.getSectionSnapshots();
        for (int cell = sections.length - 10; cell < sections.length; cell++) {
          minimumRiserWaterVelocity = Math.min(minimumRiserWaterVelocity, sections[cell].getWaterVelocity());
        }
      }
    }
    double waterInventory = 0.0;
    for (TwoFluidSection section : pipe.getSectionSnapshots()) {
      waterInventory += section.getWaterMassPerLength() * section.getLength();
    }
    return new double[] {minimumWaterOutflow, waterInventory, minimumRiserWaterVelocity};
  }

  /**
   * Checks outlet pressure, coupled-correction health, outflow-only transfer and phase mass closure.
   *
   * @param pipe accepted pipe state
   * @return accepted mass ledger
   */
  private static TwoFluidMassBalanceReport checkAcceptedInterval(TwoFluidPipe pipe) {
    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    assertEquals(INTERVAL, report.getElapsedTimeSeconds(), 1.0e-12);
    assertEquals(OUTLET_PRESSURE_BARA, pipe.getOutletStream().getPressure("bara"), 1.0e-9);
    assertFalse(pipe.isTransientCoupledPressureMomentumFailureDetected());
    assertFalse(pipe.isTransientCoupledPressureMomentumCorrectionLimited());
    for (Phase phase : PHASES) {
      assertTrue(report.getOutletMassKg(phase) >= 0.0, phase.name());
      assertEquals(0.0, report.getResidualKg(phase), 1.0e-8, phase.name());
    }
    return report;
  }
}
