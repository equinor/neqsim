package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.TwoFluidPipe.SlugTrackingMode;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Executes the transient and slug-tracking contracts in the pipeline cookbook. */
class PipelineTransientRecipesDocumentationTest extends NeqSimTest {
  private static final double ABSOLUTE_BALANCE_TOLERANCE_KG = 1.0e-7;
  private static final double RELATIVE_BALANCE_TOLERANCE = 1.0e-8;

  @Test
  void documentedTransientUsesJavaUuidAndClosesMassBalance() {
    TwoFluidPipe pipe = createPipe("documented-transient", 8);
    pipe.setEnableSlugTracking(false);
    pipe.run();

    assertTrue(pipe.isSteadyStateConverged());
    assertFalse(pipe.isSteadyStatePressureFloorLimited());
    assertFalse(pipe.isSteadyStateWallClockLimited());

    UUID runId = UUID.fromString("00000000-0000-0000-0000-00000000d095");
    pipe.runTransient(1.0e-3, runId);

    TwoFluidMassBalanceReport balance = pipe.getLastMassBalanceReport();
    assertNotNull(balance);
    assertTrue(balance.getAcceptedSubsteps() > 0);
    assertTrue(balance.isWithinTolerance(Phase.TOTAL, ABSOLUTE_BALANCE_TOLERANCE_KG,
        RELATIVE_BALANCE_TOLERANCE));
    assertTrue(Double.isFinite(balance.getRelativeResidual(Phase.TOTAL)));
  }

  @Test
  void documentedLagrangianModeUsesMatchingTracker() {
    TwoFluidPipe pipe = createPipe("documented-lagrangian", 12);
    pipe.setSlugTrackingMode(SlugTrackingMode.LAGRANGIAN);
    pipe.configureLagrangianSlugTracking(true, true, true);

    assertEquals(SlugTrackingMode.LAGRANGIAN, pipe.getSlugTrackingMode());
    LagrangianSlugTracker tracker = pipe.getLagrangianSlugTracker();
    assertNotNull(tracker);
    assertTrue(tracker.getSlugCount() >= 0);
    assertTrue(tracker.getAverageSlugLength() >= 0.0);
    assertTrue(tracker.getSlugFrequency() >= 0.0);
    assertNotNull(pipe.getSlugTrackingStatisticsJson());
  }

  @Test
  void documentedThreePhaseProfilesAreBounded() {
    TwoFluidPipe pipe = createPipe("documented-three-phase", 8);
    pipe.setEnableSlugTracking(false);
    pipe.run();

    double[] oilHoldup = pipe.getOilHoldupProfile();
    double[] waterHoldup = pipe.getWaterHoldupProfile();

    assertEquals(8, oilHoldup.length);
    assertEquals(8, waterHoldup.length);
    for (int section = 0; section < oilHoldup.length; section++) {
      assertTrue(oilHoldup[section] >= 0.0 && oilHoldup[section] <= 1.0);
      assertTrue(waterHoldup[section] >= 0.0 && waterHoldup[section] <= 1.0);
      assertTrue(oilHoldup[section] + waterHoldup[section] <= 1.0 + 1.0e-12);
    }
  }

  private static TwoFluidPipe createPipe(String name, int sections) {
    SystemInterface fluid = new SystemSrkEos(293.15, 80.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-pentane", 0.03);
    fluid.addComponent("n-heptane", 0.02);
    fluid.addComponent("nC10", 0.02);
    fluid.addComponent("water", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream(name + "-inlet", fluid);
    inlet.setFlowRate(6.0, "kg/sec");
    inlet.setTemperature(20.0, "C");
    inlet.setPressure(80.0, "bara");
    inlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe(name + "-pipe", inlet);
    pipe.setLength(300.0);
    pipe.setDiameter(0.15);
    pipe.setRoughness(1.0e-5);
    pipe.setNumberOfSections(sections);
    pipe.setElevationProfile(new double[sections]);
    pipe.setOutletPressure(60.0, "bara");
    pipe.setEnableAdaptiveTimestepping(false);
    pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
    pipe.setSteadyStateMaxWallClockTime(10.0);
    return pipe;
  }
}
