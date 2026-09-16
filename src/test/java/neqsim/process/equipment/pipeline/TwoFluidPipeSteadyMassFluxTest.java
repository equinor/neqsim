package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression for source-free steady phase mass flux at high-throughput well conditions (#3686). */
class TwoFluidPipeSteadyMassFluxTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(TwoFluidPipeSteadyMassFluxTest.class);

  /** Translate the public stock-tank rates into the issue's synthetic SRK fluid. */
  private TwoFluidPipe makeWell(double rateScale) {
    double oilMass = rateScale * 15069.4462890625 * 859.5 / 86400.0;
    double gasMass = rateScale * 1630701.625 * 0.854 / 86400.0;
    double waterMass = rateScale * 7.13818359375 * 1033.0 / 86400.0;
    String[] components = { "methane", "ethane", "propane", "nitrogen", "CO2" };
    double[] fractions = { 0.86, 0.07, 0.035, 0.015, 0.02 };
    double[] molarMasses = { 0.016043, 0.030070, 0.044097, 0.0280134, 0.04401 };
    double gasMolarMass = 0.0;
    for (int i = 0; i < components.length; i++) {
      gasMolarMass += fractions[i] * molarMasses[i];
    }
    SystemInterface fluid = new SystemSrkEos(363.15, 205.0);
    for (int i = 0; i < components.length; i++) {
      fluid.addComponent(components[i], gasMass / gasMolarMass * fractions[i]);
    }
    fluid.addTBPfraction("stock_oil", oilMass / 0.200, 0.200, 0.8595);
    fluid.addComponent("water", waterMass / 0.01801528);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    Stream inlet = new Stream("reservoir handoff", fluid);
    inlet.setFlowRate(oilMass + gasMass + waterMass, "kg/sec");
    inlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe("well", inlet);
    pipe.setLength(3100.0);
    pipe.setDiameter(0.23);
    pipe.setRoughness(4.5e-5);
    pipe.setNumberOfSections(20);
    double[] elevations = new double[20];
    for (int i = 0; i < elevations.length; i++) {
      elevations[i] = 2380.0 * i / (elevations.length - 1.0);
    }
    pipe.setElevationProfile(elevations);
    pipe.setEnableWaterOilSlip(true);
    pipe.setSteadyStateMaxIterations(250);
    return pipe;
  }

  @ParameterizedTest
  @ValueSource(doubles = { 0.95, 1.0, 1.05 })
  void convergedWellConservesTotalPhaseMassFlux(double rateScale) {
    TwoFluidPipe pipe = makeWell(rateScale);
    pipe.run();
    SteadyStateConvergenceReport report = pipe.getSteadyStateConvergenceReport();
    assertTrue(report.isConverged(), "steady solve: " + report.getTerminationReason());
    assertTrue(pipe.isSteadyStateConverged());
    assertFalse(pipe.isSteadyStatePressureFloorLimited());
    assertHydraulicResidualsPassed(report);
    assertMassFluxCloses(pipe);
    if (rateScale >= 1.0) {
      assertTrue(pipe.getGasVelocityProfile()[19] > 100.0, "continuity requires gas velocity above the old cap");
    }
    logger.info("rate scale={}, inlet={} kg/s, maximum relative mass-flux error={}, outlet gas velocity={} m/s",
        rateScale, pipe.getInletStream().getFlowRate("kg/sec"), report.getMassFluxResidual(),
        pipe.getGasVelocityProfile()[19]);
  }

  private void assertHydraulicResidualsPassed(SteadyStateConvergenceReport report) {
    double[] residuals = { report.getPressureMomentumResidual(), report.getPressureUpdateResidual(),
        report.getLiquidHoldupResidual(), report.getLiquidSplitResidual(), report.getThermodynamicResidual(),
        report.getPressureDropResidual() };
    for (double residual : residuals) {
      assertTrue(Double.isFinite(residual) && residual < report.getTolerance(), "hydraulic residual=" + residual);
    }
  }

  private void assertMassFluxCloses(TwoFluidPipe pipe) {
    SteadyStateConvergenceReport report = pipe.getSteadyStateConvergenceReport();
    assertEquals(1.0e-8, report.getMassFluxTolerance());
    assertTrue(report.getMassFluxResidual() < report.getMassFluxTolerance());
    double inletMass = pipe.getInletStream().getFlowRate("kg/sec");
    double[] gas = pipe.getGasMassFlowProfile();
    double[] oil = pipe.getOilMassFlowProfile();
    double[] water = pipe.getWaterMassFlowProfile();
    double maximumError = 0.0;
    for (int i = 0; i < gas.length; i++) {
      maximumError = Math.max(maximumError,
          Math.abs(gas[i] + oil[i] + water[i] - inletMass) / Math.max(Math.abs(inletMass), 1.0e-12));
      assertEquals(inletMass, gas[i] + oil[i] + water[i], Math.abs(inletMass) * 1.0e-10 + 1.0e-12,
          "section " + i + ": gas=" + gas[i] + ", oil=" + oil[i] + ", water=" + water[i] + ", vg="
              + pipe.getGasVelocityProfile()[i] + ", vl=" + pipe.getLiquidVelocityProfile()[i] + ", vo="
              + pipe.getOilVelocityProfile()[i] + ", vw=" + pipe.getWaterVelocityProfile()[i]);
      assertEquals(pipe.getLiquidHoldupProfile()[i], pipe.getOilHoldupProfile()[i] + pipe.getWaterHoldupProfile()[i],
          1.0e-12);
    }
    assertEquals(maximumError, report.getMassFluxResidual(), 1.0e-15,
        "the diagnostic must describe the final published profiles");
  }

  @ParameterizedTest
  @ValueSource(strings = { "methane", "nC10", "water" })
  void singlePhaseSteadyFlowIsNotClipped(String component) {
    SystemInterface fluid = new SystemSrkEos(298.15, 100.0);
    fluid.addComponent(component, 1.0);
    fluid.setMixingRule(2);
    Stream inlet = new Stream("single phase", fluid);
    inlet.run();
    assertEquals(1, inlet.getFluid().getNumberOfPhases());
    boolean gas = inlet.getFluid().hasPhaseType("gas");
    double velocityLimit = gas ? 100.0 : 50.0;
    double area = Math.PI * 0.1 * 0.1 / 4.0;
    inlet.setFlowRate(1.2 * velocityLimit * area * inlet.getFluid().getPhase(0).getDensity("kg/m3"), "kg/sec");
    inlet.run();
    TwoFluidPipe pipe = new TwoFluidPipe("short pipe", inlet);
    pipe.setLength(0.01);
    pipe.setDiameter(0.1);
    pipe.setNumberOfSections(3);
    pipe.run();
    assertTrue(pipe.isSteadyStateConverged());
    assertMassFluxCloses(pipe);
    double[] velocities = gas ? pipe.getGasVelocityProfile() : pipe.getLiquidVelocityProfile();
    for (double velocity : velocities) {
      assertTrue(velocity > velocityLimit);
    }
  }

  @ParameterizedTest
  @ValueSource(doubles = { 0.98, Double.NaN })
  void aSettledHydraulicProfileCannotHideAFluxError(double reportedFraction) {
    SystemInterface fluid = new SystemSrkEos(298.15, 100.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule(2);
    Stream inlet = new Stream("gas", fluid);
    inlet.setFlowRate(2.0, "kg/sec");
    inlet.run();
    // Fault injection: emulate a clipped or nonfinite phase-flux reconstruction while the
    // hydraulic calculation still reaches its fixed point. The independent mass gate must fail.
    TwoFluidPipe pipe = new TwoFluidPipe("bad flux", inlet) {
      private static final long serialVersionUID = 1L;

      @Override
      public double[] getGasMassFlowProfile() {
        double[] flows = super.getGasMassFlowProfile();
        for (int i = 0; i < flows.length; i++) {
          flows[i] *= reportedFraction;
        }
        return flows;
      }
    };
    pipe.setLength(1.0);
    pipe.setNumberOfSections(3);
    pipe.setSteadyStateMaxIterations(40);
    pipe.run();
    SteadyStateConvergenceReport report = pipe.getSteadyStateConvergenceReport();
    assertHydraulicResidualsPassed(report);
    assertFalse(report.isConverged());
    assertFalse(pipe.isSteadyStateConverged());
    assertFalse(pipe.isSteadyStatePressureFloorLimited());
    assertEquals(SteadyStateConvergenceReport.TerminationReason.ITERATION_LIMIT, report.getTerminationReason());
    assertEquals(Double.isNaN(reportedFraction) ? Double.POSITIVE_INFINITY : 0.02, report.getMassFluxResidual(),
        1.0e-12);
  }

  @Test
  void legacyReportsRetainTheirContractWithoutInventingAMassMeasurement() {
    SteadyStateConvergenceReport legacy = new SteadyStateConvergenceReport(
        SteadyStateConvergenceReport.TerminationReason.CONVERGED, 1, 1.0e-4, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    assertTrue(legacy.isConverged());
    assertTrue(Double.isNaN(legacy.getMassFluxResidual()));
    assertTrue(Double.isNaN(legacy.getMassFluxTolerance()));
  }

  @ParameterizedTest
  @ValueSource(doubles = { 0.02, Double.NaN, Double.POSITIVE_INFINITY })
  void recordedMassFluxFailurePreventsAConvergedReport(double residual) {
    SteadyStateConvergenceReport report = new SteadyStateConvergenceReport(
        SteadyStateConvergenceReport.TerminationReason.CONVERGED, 1, 1.0e-4, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, residual,
        1.0e-8);
    assertFalse(report.isConverged());
  }
}
