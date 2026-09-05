package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.TwoFluidPipe.BoundaryCondition;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Progress regression for the coupled solver on Tengesdal's public 2002 large-facility test 3.
 *
 * <p>
 * This fixture verifies numerical progress, conservation, and diagnostics. It records but does not qualify the outlet
 * range; the public experimental benchmark remains disabled until the full physical acceptance gates pass.
 */
@Tag("slow")
class CoupledPressureMomentumTengesdalProgressTest {
  private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager
      .getLogger(CoupledPressureMomentumTengesdalProgressTest.class);
  private static final double FLOWLINE_LENGTH_M = 19.81;
  private static final double RISER_HEIGHT_M = 14.94;
  private static final double DIAMETER_M = 0.0762;
  private static final double PIPE_AREA_M2 = Math.PI * DIAMETER_M * DIAMETER_M / 4.0;
  private static final double LIQUID_DENSITY_KG_PER_M3 = 856.0;
  private static final double LIQUID_FEED_KG_PER_S = 0.50 * PIPE_AREA_M2 * LIQUID_DENSITY_KG_PER_M3;
  private static final double GAS_FEED_KG_PER_S = 1.00 * PIPE_AREA_M2 * 1.204;
  private static final double STORED_COMPARISON_MINIMUM_LIQUID_OUTLET_KG_PER_S = 0.375;
  private static final double STORED_COMPARISON_MAXIMUM_LIQUID_OUTLET_KG_PER_S = 4.03;

  @Test
  void coupledConfigurationCompletesFiftyStepsWithConservationAndDiagnostics() {
    TwoFluidPipe pipe = createTestThreePipe();
    assertEquals(24, pipe.getCoupledPressureMomentumMaximumIterations());
    assertEquals(1.0e-7, pipe.getCoupledPressureMomentumRelativeVolumeTolerance(), 0.0);
    double minimumLiquidOutletRate = Double.POSITIVE_INFINITY;
    double maximumLiquidOutletRate = Double.NEGATIVE_INFINITY;
    int minimumLiquidOutletRateStep = -1;
    int maximumLiquidOutletRateStep = -1;
    double outletStateRateAtMinimum = Double.NaN;
    double outletStateRateAtMaximum = Double.NaN;

    for (int step = 0; step < 50; step++) {
      double startTime = pipe.getSimulationTime();
      UUID physicalStepId = UUID
          .nameUUIDFromBytes(("Tengesdal-coupled-progress-" + step).getBytes(StandardCharsets.UTF_8));
      pipe.runTransient(0.1, physicalStepId);
      TwoFluidMassBalanceReport balance = pipe.getLastMassBalanceReport();

      assertEquals(0.1, balance.getElapsedTimeSeconds(), 1.0e-10,
          "coupled solver did not complete interval " + (step + 1));
      assertEquals(startTime + 0.1, pipe.getSimulationTime(), 1.0e-10,
          "simulation clock did not complete interval " + (step + 1));
      assertTrue(pipe.isCoupledPressureMomentumConverged(), "coupled correction failed at interval " + (step + 1));
      for (Phase phase : Phase.values()) {
        assertTrue(balance.getRelativeResidual(phase) < 1.0e-9, phase + " mass residual exceeded tolerance at interval "
            + (step + 1) + ": " + balance.getRelativeResidual(phase));
      }

      double liquidOutletRate = balance.getOutletMassKg(Phase.LIQUID) / balance.getElapsedTimeSeconds();
      if (liquidOutletRate < minimumLiquidOutletRate) {
        minimumLiquidOutletRate = liquidOutletRate;
        minimumLiquidOutletRateStep = step + 1;
        double[] oilMassFlowProfile = pipe.getOilMassFlowProfile();
        outletStateRateAtMinimum = oilMassFlowProfile[oilMassFlowProfile.length - 1];
      }
      if (liquidOutletRate > maximumLiquidOutletRate) {
        maximumLiquidOutletRate = liquidOutletRate;
        maximumLiquidOutletRateStep = step + 1;
        double[] oilMassFlowProfile = pipe.getOilMassFlowProfile();
        outletStateRateAtMaximum = oilMassFlowProfile[oilMassFlowProfile.length - 1];
      }
    }

    assertTrue(Double.isFinite(minimumLiquidOutletRate));
    assertTrue(Double.isFinite(maximumLiquidOutletRate));
    logger.info(
        "Tengesdal coupled 50-step outlet comparison: observed={} to {} kg/s at steps {}/{}, final-state rates={}/{} "
            + "kg/s; stored comparison={} to {} kg/s; latest correction limited={}",
        minimumLiquidOutletRate, maximumLiquidOutletRate, minimumLiquidOutletRateStep, maximumLiquidOutletRateStep,
        outletStateRateAtMinimum, outletStateRateAtMaximum, STORED_COMPARISON_MINIMUM_LIQUID_OUTLET_KG_PER_S,
        STORED_COMPARISON_MAXIMUM_LIQUID_OUTLET_KG_PER_S, pipe.isCoupledPressureMomentumPressureCorrectionLimited());
    assertFalse(pipe.isTransientOutletBackflowClamped(),
        "the signed outlet must not fall back to the one-way phase clamp");
    assertFalse(pipe.isTransientCoupledPressureMomentumFailureDetected(),
        "the default nonlinear budget must not reject a coupled correction");
    assertEquals(0, pipe.getTransientCoupledPressureMomentumRejectedSubsteps());
    assertTrue(pipe.isTransientCoupledPressureMomentumCorrectionLimited(),
        "the known Tengesdal limiter event must remain visible through the sticky diagnostic");
  }

  @Test
  void impossibleConfiguredGateFailsLoudlyAndKeepsDiagnostics() {
    TwoFluidPipe pipe = createTestThreePipe();
    pipe.setCoupledPressureMomentumMaximumIterations(1);
    pipe.setCoupledPressureMomentumRelativeVolumeTolerance(1.0e-14);

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> pipe.runTransient(0.1, UUID.nameUUIDFromBytes("Tengesdal-fail-loud".getBytes(StandardCharsets.UTF_8))));

    assertTrue(failure.getMessage().contains("advanced"));
    assertTrue(failure.getMessage().contains("tolerance=1.0E-14"));
    assertTrue(pipe.isTransientCoupledPressureMomentumFailureDetected());
    assertTrue(pipe.getTransientCoupledPressureMomentumRejectedSubsteps() > 0);
    assertNotNull(pipe.getLastMassBalanceReport());
    assertTrue(pipe.getLastMassBalanceReport().getElapsedTimeSeconds() < 0.1);
    assertEquals(1, pipe.getCoupledPressureMomentumMaximumIterations());
    assertEquals(1.0e-14, pipe.getCoupledPressureMomentumRelativeVolumeTolerance(), 0.0);
  }

  @Test
  void refinedMeshAndHalfOuterStepAlsoCompleteFiveSeconds() {
    TwoFluidPipe pipe = createTestThreePipe(24);
    for (int step = 0; step < 100; step++) {
      UUID physicalStepId = UUID
          .nameUUIDFromBytes(("Tengesdal-coupled-refined-" + step).getBytes(StandardCharsets.UTF_8));
      pipe.runTransient(0.05, physicalStepId);
      TwoFluidMassBalanceReport balance = pipe.getLastMassBalanceReport();

      assertEquals(0.05, balance.getElapsedTimeSeconds(), 1.0e-10,
          "refined coupled solver did not complete interval " + (step + 1));
      assertTrue(pipe.isCoupledPressureMomentumConverged(),
          "refined coupled correction failed at interval " + (step + 1));
      for (Phase phase : Phase.values()) {
        assertTrue(balance.getRelativeResidual(phase) < 1.0e-9,
            phase + " refined-mesh mass residual exceeded tolerance at interval " + (step + 1) + ": "
                + balance.getRelativeResidual(phase));
      }
    }

    assertEquals(5.0, pipe.getSimulationTime(), 1.0e-9);
    assertFalse(pipe.isTransientOutletBackflowClamped());
    assertFalse(pipe.isTransientCoupledPressureMomentumFailureDetected(),
        "refined coupled solve recorded " + pipe.getTransientCoupledPressureMomentumRejectedSubsteps()
            + " rejected substeps: " + pipe.getTransientCoupledPressureMomentumFailureDiagnostic());
    assertEquals(0, pipe.getTransientCoupledPressureMomentumRejectedSubsteps());
  }

  private static TwoFluidPipe createTestThreePipe() {
    return createTestThreePipe(16);
  }

  private static TwoFluidPipe createTestThreePipe(int numberOfSections) {
    double liquidMolarMassKgPerMol = 0.220;
    double nitrogenMolarMassKgPerMol = 0.0280134;
    SystemInterface fluid = new SystemSrkEos(298.15, 2.3);
    fluid.addComponent("nitrogen", GAS_FEED_KG_PER_S / nitrogenMolarMassKgPerMol);
    fluid.addTBPfraction("Crystex", LIQUID_FEED_KG_PER_S / liquidMolarMassKgPerMol, liquidMolarMassKgPerMol,
        LIQUID_DENSITY_KG_PER_M3 / 1000.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("Tengesdal 2002 large facility test 3", fluid);
    inlet.setFlowRate(LIQUID_FEED_KG_PER_S + GAS_FEED_KG_PER_S, "kg/sec");
    inlet.setTemperature(25.0, "C");
    inlet.setPressure(2.3, "bara");
    inlet.run();

    double totalLengthM = FLOWLINE_LENGTH_M + RISER_HEIGHT_M;
    double inclinationRad = Math.toRadians(-3.0);
    double flowlineDropM = FLOWLINE_LENGTH_M * Math.sin(inclinationRad);
    double[] elevationM = new double[numberOfSections];
    for (int section = 0; section < numberOfSections; section++) {
      double positionM = totalLengthM * section / (numberOfSections - 1.0);
      elevationM[section] = positionM <= FLOWLINE_LENGTH_M ? positionM * Math.sin(inclinationRad)
          : flowlineDropM + positionM - FLOWLINE_LENGTH_M;
    }

    TwoFluidPipe pipe = new TwoFluidPipe("Tengesdal coupled progress", inlet);
    pipe.setLength(totalLengthM);
    pipe.setDiameter(DIAMETER_M);
    pipe.setRoughness(1.5e-6);
    pipe.setNumberOfSections(numberOfSections);
    pipe.setElevationProfile(elevationM);
    pipe.setOutletPressure(1.01325, "bara");
    pipe.setInletBoundaryCondition(BoundaryCondition.STREAM_CONNECTED);
    pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.RK4);
    pipe.setEnableAdaptiveTimestepping(true);
    pipe.setThermodynamicUpdateInterval(1000);
    pipe.setIncludeMassTransfer(false);
    pipe.setEnableInterfacialPressure(true);
    pipe.setImplicitInterfacialPressureCoupling(true);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setAllowOutletPhaseBackflow(true);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    return pipe;
  }
}
