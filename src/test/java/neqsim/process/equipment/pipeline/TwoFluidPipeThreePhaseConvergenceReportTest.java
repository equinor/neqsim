package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.SteadyStateConvergenceReport.TerminationReason;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Physical and mesh-refinement contracts for three-phase steady-state convergence diagnostics. */
class TwoFluidPipeThreePhaseConvergenceReportTest {

  /** Results needed for an independent mesh comparison. */
  private static final class ThreePhaseResult {
    private final double arrivalPressurePa;
    private final double meanLiquidHoldup;

    private ThreePhaseResult(double arrivalPressurePa, double meanLiquidHoldup) {
      this.arrivalPressurePa = arrivalPressurePa;
      this.meanLiquidHoldup = meanLiquidHoldup;
    }
  }

  /** Build the public 3 km uphill gas/oil/water regression fixture. */
  private TwoFluidPipe makeThreePhaseUphillPipe(int sections) {
    SystemInterface fluid = new SystemSrkEos(290.15, 30.0);
    fluid.addComponent("methane", 0.40);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-pentane", 0.15);
    fluid.addComponent("n-heptane", 0.17);
    fluid.addComponent("water", 0.20);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("three-phase-inlet-" + sections, fluid);
    inlet.setFlowRate(8.0, "kg/sec");
    inlet.setTemperature(20.0, "C");
    inlet.setPressure(30.0, "bara");
    inlet.run();

    double pipeLength = 3000.0;
    double totalRise = pipeLength * Math.sin(Math.toRadians(10.0));
    double[] elevations = new double[sections];
    for (int section = 0; section < sections; section++) {
      elevations[section] = totalRise * section / (sections - 1.0);
    }

    TwoFluidPipe pipe = new TwoFluidPipe("three-phase-uphill-" + sections, inlet);
    pipe.setLength(pipeLength);
    pipe.setDiameter(0.15);
    pipe.setNumberOfSections(sections);
    pipe.setRoughness(4.5e-5);
    pipe.setElevationProfile(elevations);
    pipe.setEnableWaterOilSlip(true);
    return pipe;
  }

  /** Solve and check the residual, phase-volume, phase-flow, and slip contracts. */
  private ThreePhaseResult solveAndCheck(int sections) {
    TwoFluidPipe pipe = makeThreePhaseUphillPipe(sections);
    pipe.run();

    SteadyStateConvergenceReport report = pipe.getSteadyStateConvergenceReport();
    assertTrue(report.isConverged(),
        "three-phase solve stopped with " + report.getTerminationReason() + ", pressure-momentum="
            + report.getPressureMomentumResidual() + ", pressure-update=" + report.getPressureUpdateResidual()
            + ", total-holdup=" + report.getLiquidHoldupResidual() + ", liquid-split=" + report.getLiquidSplitResidual()
            + ", thermodynamics=" + report.getThermodynamicResidual() + ", pressure-drop="
            + report.getPressureDropResidual());
    assertEquals(TerminationReason.CONVERGED, report.getTerminationReason());
    assertFalse(pipe.isSteadyStatePressureFloorLimited());
    assertFalse(pipe.isSteadyStateWallClockLimited());
    assertTrue(report.getPressureMomentumResidual() < report.getTolerance());
    assertTrue(report.getPressureUpdateResidual() < report.getTolerance());
    assertTrue(report.getLiquidHoldupResidual() < report.getTolerance());
    assertTrue(report.getLiquidSplitResidual() < report.getTolerance());
    assertTrue(report.getThermodynamicResidual() < report.getTolerance());
    assertTrue(report.getPressureDropResidual() < report.getTolerance());

    double[] pressure = pipe.getPressureProfile();
    double[] liquidHoldup = pipe.getLiquidHoldupProfile();
    double[] oilHoldup = pipe.getOilHoldupProfile();
    double[] waterHoldup = pipe.getWaterHoldupProfile();
    double[] oilVelocity = pipe.getOilVelocityProfile();
    double[] waterVelocity = pipe.getWaterVelocityProfile();
    double[] gasMassFlow = pipe.getGasMassFlowProfile();
    double[] oilMassFlow = pipe.getOilMassFlowProfile();
    double[] waterMassFlow = pipe.getWaterMassFlowProfile();
    double meanLiquidHoldup = 0.0;
    double meanOilOverWaterSlip = 0.0;
    for (int section = 0; section < sections; section++) {
      assertTrue(pressure[section] > 1.0e5, "pressure floor at section " + section);
      assertTrue(oilHoldup[section] > 0.0 && waterHoldup[section] > 0.0,
          "oil and water must remain present at section " + section);
      assertEquals(liquidHoldup[section], oilHoldup[section] + waterHoldup[section], 1.0e-12,
          "phase volumes must close at section " + section);
      assertEquals(8.0, gasMassFlow[section] + oilMassFlow[section] + waterMassFlow[section], 8.0e-8,
          "phase mass flows must close at section " + section);
      meanLiquidHoldup += liquidHoldup[section];
      meanOilOverWaterSlip += oilVelocity[section] - waterVelocity[section];
    }
    meanLiquidHoldup /= sections;
    meanOilOverWaterSlip /= sections;
    assertTrue(meanOilOverWaterSlip > 1.0e-3,
        "uphill gravity must retard water relative to oil, slip=" + meanOilOverWaterSlip);
    return new ThreePhaseResult(pressure[sections - 1], meanLiquidHoldup);
  }

  /** The three-phase solution and all solved residuals must be stable under one mesh refinement. */
  @Test
  void testThreePhaseConvergenceAndMeshRefinement() {
    ThreePhaseResult coarse = solveAndCheck(30);
    ThreePhaseResult fine = solveAndCheck(60);

    double pressureSensitivity = Math.abs(fine.arrivalPressurePa - coarse.arrivalPressurePa)
        / Math.max(Math.abs(fine.arrivalPressurePa), 1.0e5);
    double holdupSensitivity = Math.abs(fine.meanLiquidHoldup - coarse.meanLiquidHoldup)
        / Math.max(Math.abs(fine.meanLiquidHoldup), 1.0e-12);
    assertTrue(pressureSensitivity < 0.02,
        "30/60-cell arrival-pressure sensitivity must be below 2%, got " + pressureSensitivity);
    assertTrue(holdupSensitivity < 0.02,
        "30/60-cell mean-holdup sensitivity must be below 2%, got " + holdupSensitivity);
  }
}
