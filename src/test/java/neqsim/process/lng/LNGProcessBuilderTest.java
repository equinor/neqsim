package neqsim.process.lng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link LNGProcessBuilder} and {@link LNGProcessModel}.
 *
 * @author NeqSim contributors
 */
class LNGProcessBuilderTest {
  @ParameterizedTest
  @EnumSource(LNGProcessCycle.class)
  void testBuildsEverySupportedCycle(LNGProcessCycle cycle) {
    LNGProcessModel model = new LNGProcessBuilder().setName("test " + cycle)
        .setCycle(cycle).setFeedFlowRate(20000.0).setNumberOfZones(4)
        .setAdaptiveRefinement(false).build();

    assertEquals(cycle, model.getCycle());
    assertNotNull(model.getProcessSystem());
    assertNotNull(model.getFeedStream());
    assertNotNull(model.getProductStream());
    assertNotNull(model.getFlashGasStream());
    assertSame(model.getProductStream(),
        model.getOutputStream(LNGProcessModel.LNG_OUTPUT));
    assertSame(model.getFlashGasStream(),
        model.getOutputStream(LNGProcessModel.FLASH_GAS_OUTPUT));
    assertEquals(2, model.getOutputStreams().size());
    assertTrue(model.getCompressors().size() >= 2);
    assertTrue(model.getCryogenicHeatExchangers().size() >= 1);

    if (cycle == LNGProcessCycle.NITROGEN_EXPANDER) {
      assertEquals(1, model.getExpanders().size());
    } else {
      assertTrue(model.getExpanders().isEmpty());
    }
  }

  @Test
  @Tag("slow")
  void testSmrRouteRunsAndReportsPerformance() {
    LNGProcessModel model = new LNGProcessBuilder().setName("SMR smoke test")
        .setCycle(LNGProcessCycle.SMR).setFeedFlowRate(20000.0)
        .setNumberOfZones(4).setAdaptiveRefinement(false).build();

    LNGProcessModel.Result result = model.run();

    assertTrue(Double.isFinite(result.getLNGMassFlowKgPerHour()));
    assertTrue(result.getLNGMassFlowKgPerHour() > 0.0);
    assertTrue(Double.isFinite(result.getLNGYield()));
    assertTrue(result.getLNGYield() > 0.0 && result.getLNGYield() <= 1.000001);
    assertTrue(Double.isFinite(result.getSpecificEnergyKWhPerKgLNG()));
    assertTrue(result.getSpecificEnergyKWhPerKgLNG() > 0.0);
    assertTrue(Double.isFinite(result.getProductTemperatureC()));
    assertTrue(result.getRunTimeMilliseconds() >= 0.0);
    assertNotNull(model.toJson());
  }

  @Test
  void testAcceptsLiveNeqSimFeedStream() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("methane", 0.92);
    fluid.addComponent("ethane", 0.08);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("external LNG feed", fluid);
    feed.setFlowRate(25000.0, "kg/hr");

    LNGProcessModel model = new LNGProcessBuilder().setName("integrated LNG")
        .setFeedStream(feed).setNumberOfZones(4).build();

    assertSame(feed, model.getFeedStream());
    feed.setFlowRate(30000.0, "kg/hr");
    assertEquals(30000.0, model.getFeedStream().getFlowRate("kg/hr"), 1.0e-9);
    model.registerOutputStream("CUSTOM_PRODUCT", feed);
    assertSame(feed, model.getOutputStream("CUSTOM_PRODUCT"));
  }

  @Test
  void testIntegratesPipelineWithProcessCapacityFramework() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 70.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    Stream supply = new Stream("pipeline supply", fluid);
    supply.setFlowRate(20000.0, "kg/hr");

    ProcessSystem integratedProcess = new ProcessSystem("integrated LNG plant");
    integratedProcess.add(supply);
    PipeBeggsAndBrills feedPipeline =
        new PipeBeggsAndBrills("LNG feed pipeline", supply);
    feedPipeline.setLength(20000.0);
    feedPipeline.setDiameter(0.35);
    feedPipeline.addCapacityConstraint(
        new CapacityConstraint("pipelineThroughput", "kg/hr", ConstraintType.DESIGN)
            .setDesignValue(25000.0).setCurrentValue(20000.0));
    integratedProcess.add(feedPipeline);

    LNGProcessModel model = new LNGProcessBuilder().setName("pipeline integrated LNG")
        .setUpstreamProcess(integratedProcess, feedPipeline.getOutletStream())
        .setNumberOfZones(4).build();
    LNGProcessModel.CapacityResult capacity = model.evaluateCapacity();

    assertSame(integratedProcess, model.getProcessSystem());
    assertSame(feedPipeline.getOutletStream(), model.getFeedStream());
    assertEquals("LNG feed pipeline", capacity.getBottleneck().getEquipmentName());
    assertEquals(80.0,
        capacity.getRankedUtilizationPercent().get("LNG feed pipeline"), 1.0e-9);
    assertFalse(capacity.isAnyEquipmentOverloaded());
    assertNotNull(capacity.getUtilizationSnapshotJson());
    assertNotNull(capacity.getDesignReportJson());
  }

  @Test
  void testRejectsInvalidConfiguration() {
    LNGProcessBuilder builder = new LNGProcessBuilder();

    assertThrows(IllegalArgumentException.class, () -> builder.setCycle(null));
    assertThrows(IllegalArgumentException.class, () -> builder.setFeedFlowRate(0.0));
    assertThrows(IllegalArgumentException.class, () -> builder.setFeedPressure(-1.0));
    assertThrows(IllegalArgumentException.class, () -> builder.setNumberOfZones(1));
    assertThrows(IllegalArgumentException.class, () -> builder.setFeedStream(null));
    assertThrows(IllegalArgumentException.class,
        () -> builder.setUpstreamProcess(null, null));
    assertThrows(IllegalArgumentException.class,
        () -> builder.setCompressorEfficiency(1.1));
  }
}
