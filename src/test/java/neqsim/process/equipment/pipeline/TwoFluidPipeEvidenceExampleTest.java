package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Executable steady-to-transient TwoFluidPipe evidence example with explicit numerical gates.
 *
 * <p>
 * The example is a deterministic liquid-rich gas-condensate line. It reports the complete steady convergence decision,
 * advances ten seconds with unchanged boundaries, and rejects a transient that violates total-mass conservation, the
 * validated two-percent inventory-drift screen, or the coupled-solver diagnostics. It is numerical evidence for this
 * configured case, not experimental qualification of general liquid-rich or severe-slugging flow.
 * </p>
 */
class TwoFluidPipeEvidenceExampleTest {
  private static final Logger logger = LogManager.getLogger(TwoFluidPipeEvidenceExampleTest.class);
  private static final double TRANSIENT_DURATION_SECONDS = 10.0;
  private static final double OUTER_STEP_SECONDS = 1.0;

  @Test
  @Timeout(value = 3, unit = TimeUnit.MINUTES)
  void runSteadyAndUnchangedBoundaryTransientEvidenceChain() {
    TwoFluidPipe pipe = createPipe();
    pipe.run();

    SteadyStateConvergenceReport steady = pipe.getSteadyStateConvergenceReport();
    assertTrue(steady.isConverged(), "Steady solve stopped with " + steady.getTerminationReason());
    assertFalse(pipe.isSteadyStatePressureFloorLimited());
    assertFalse(pipe.isSteadyStateWallClockLimited());

    double initialInventoryKg = pipe.getTotalMassInventory();
    double initialOutletPressurePa = last(pipe.getPressureProfile());
    double initialMeanLiquidHoldup = mean(pipe.getLiquidHoldupProfile());

    double maximumTotalMassResidualKg = 0.0;
    double maximumTotalMassRelativeResidual = 0.0;
    int numberOfSteps = (int) Math.round(TRANSIENT_DURATION_SECONDS / OUTER_STEP_SECONDS);
    for (int step = 0; step < numberOfSteps; step++) {
      pipe.runTransient(OUTER_STEP_SECONDS, UUID.randomUUID());
      TwoFluidMassBalanceReport massBalance = pipe.getLastMassBalanceReport();
      maximumTotalMassResidualKg = Math.max(maximumTotalMassResidualKg,
          Math.abs(massBalance.getResidualKg(Phase.TOTAL)));
      maximumTotalMassRelativeResidual = Math.max(maximumTotalMassRelativeResidual,
          massBalance.getRelativeResidual(Phase.TOTAL));
    }

    double finalInventoryKg = pipe.getTotalMassInventory();
    double finalOutletPressurePa = last(pipe.getPressureProfile());
    double finalMeanLiquidHoldup = mean(pipe.getLiquidHoldupProfile());
    double inventoryDrift = Math.abs(finalInventoryKg - initialInventoryKg) / initialInventoryKg;
    double outletPressureDrift = Math.abs(finalOutletPressurePa - initialOutletPressurePa) / initialOutletPressurePa;

    assertEquals(TRANSIENT_DURATION_SECONDS, pipe.getSimulationTime(), 1.0e-10);
    assertTrue(maximumTotalMassRelativeResidual <= 1.0e-10,
        "Total-mass relative residual was " + maximumTotalMassRelativeResidual);
    assertTrue(inventoryDrift <= 0.02, "Inventory drift was " + inventoryDrift);
    assertTrue(outletPressureDrift <= 0.02, "Outlet-pressure drift was " + outletPressureDrift);
    assertFalse(pipe.isTransientOutletBackflowClamped());
    assertFalse(pipe.isTransientCoupledPressureMomentumFailureDetected());
    assertFalse(pipe.isTransientCoupledPressureMomentumCorrectionLimited());
    assertEquals(0, pipe.getTransientCoupledPressureMomentumRejectedSubsteps());

    logger.info(
        "Steady evidence: termination={}, iterations={}, tolerance={}, outletPressureBar={}, "
            + "meanLiquidHoldup={}, inventoryKg={}",
        steady.getTerminationReason(), steady.getIterations(), steady.getTolerance(), initialOutletPressurePa / 1.0e5,
        initialMeanLiquidHoldup, initialInventoryKg);
    logger.info(
        "Transient evidence: durationSeconds={}, maximumTotalMassResidualKg={}, "
            + "maximumTotalMassRelativeResidual={}, inventoryDriftPercent={}, "
            + "outletPressureDriftPercent={}, finalMeanLiquidHoldup={}",
        pipe.getSimulationTime(), maximumTotalMassResidualKg, maximumTotalMassRelativeResidual, 100.0 * inventoryDrift,
        100.0 * outletPressureDrift, finalMeanLiquidHoldup);
  }

  private static TwoFluidPipe createPipe() {
    SystemInterface fluid = new SystemSrkEos(323.15, 60.0);
    fluid.addComponent("methane", 60.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-heptane", 20.0);
    fluid.addComponent("nC10", 12.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("evidence feed", fluid);
    feed.setFlowRate(50.0, "kg/sec");
    feed.setTemperature(50.0, "C");
    feed.setPressure(60.0, "bara");
    feed.run();

    TwoFluidPipe pipe = new TwoFluidPipe("evidence pipe", feed);
    pipe.setLength(5000.0);
    pipe.setDiameter(0.30);
    pipe.setNumberOfSections(20);
    pipe.setCflNumber(0.5);
    pipe.setElevationProfile(new double[20]);
    pipe.setHeatTransferCoefficient(5.0);
    pipe.setSurfaceTemperature(4.0, "C");
    pipe.setSteadyStateMaxWallClockTime(120.0);
    pipe.setSharedSlugForceBalanceEnabled(true);
    pipe.setEnableInterfacialPressure(true);
    pipe.setEnableCoupledPressureMomentum(true);
    return pipe;
  }

  private static double last(double[] values) {
    return values[values.length - 1];
  }

  private static double mean(double[] values) {
    double sum = 0.0;
    for (double value : values) {
      sum += value;
    }
    return sum / values.length;
  }
}
