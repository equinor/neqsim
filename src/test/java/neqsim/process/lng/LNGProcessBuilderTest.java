package neqsim.process.lng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.heatexchanger.LNGHeatExchanger;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
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
    LNGProcessModel model = new LNGProcessBuilder().setName("test " + cycle).setCycle(cycle).setFeedFlowRate(20000.0)
        .setNumberOfZones(4).setAdaptiveRefinement(false).build();

    assertEquals(cycle, model.getCycle());
    assertNotNull(model.getProcessSystem());
    assertNotNull(model.getFeedStream());
    assertNotNull(model.getProductStream());
    assertNotNull(model.getFlashGasStream());
    assertSame(model.getProductStream(), model.getOutputStream(LNGProcessModel.LNG_OUTPUT));
    assertSame(model.getFlashGasStream(), model.getOutputStream(LNGProcessModel.FLASH_GAS_OUTPUT));
    assertEquals(2, model.getOutputStreams().size());
    assertTrue(model.getCompressors().size() >= 2);
    assertTrue(model.getCryogenicHeatExchangers().size() >= 1);

    LNGHeatExchanger mainExchanger = model.getCryogenicHeatExchangers()
        .get(model.getCryogenicHeatExchangers().size() - 1);
    double coldSideInletC = mainExchanger.getInStream(2).getTemperature("C");
    double specifiedFeedOutletC = mainExchanger.getOutTemperature(0);
    assertTrue(coldSideInletC < specifiedFeedOutletC,
        cycle + " cold-side warm start must be colder than the specified feed outlet");

    if (cycle == LNGProcessCycle.NITROGEN_EXPANDER) {
      assertEquals(1, model.getExpanders().size());
    } else {
      assertTrue(model.getExpanders().isEmpty());
    }
  }

  @RepeatedTest(3)
  @Tag("slow")
  void testSmrRouteRunsAndReportsPerformance() {
    LNGProcessModel model = new LNGProcessBuilder().setName("SMR smoke test").setCycle(LNGProcessCycle.SMR)
        .setFeedFlowRate(20000.0).setNumberOfZones(4).setAdaptiveRefinement(false).build();

    LNGHeatExchanger seededExchanger = model.getCryogenicHeatExchangers()
        .get(model.getCryogenicHeatExchangers().size() - 1);
    System.out.println("SMR cold-side seed before process run: "
        + seededExchanger.getInStream(2).getTemperature("C"));
    model.getProcessSystem().setProgressListener(new ProcessSystem.SimulationProgressListener() {
      @Override
      public void onUnitComplete(neqsim.process.equipment.ProcessEquipmentInterface unit, int unitIndex,
          int totalUnits, int iterationNumber) {}

      @Override
      public void onBeforeUnit(neqsim.process.equipment.ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
          int iterationNumber) {
        System.out.println("SMR before " + unit.getName() + ": cold-side inlet "
            + seededExchanger.getInStream(2).getTemperature("C"));
      }
    });

    LNGProcessModel.Result result = model.run();

    assertFalse(model.getCryogenicHeatExchangers().isEmpty());
    LNGHeatExchanger mainExchanger = model.getCryogenicHeatExchangers()
        .get(model.getCryogenicHeatExchangers().size() - 1);
    assertEquals(0.0, mainExchanger.energyDiff(), 1.0e-3);
    assertEquals(3.0, mainExchanger.getTemperatureApproach(), 1.0e-3);

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

    LNGProcessModel model = new LNGProcessBuilder().setName("integrated LNG").setFeedStream(feed).setNumberOfZones(4)
        .build();

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
    PipeBeggsAndBrills feedPipeline = new PipeBeggsAndBrills("LNG feed pipeline", supply);
    feedPipeline.setLength(20000.0);
    feedPipeline.setDiameter(0.35);
    feedPipeline.addCapacityConstraint(new CapacityConstraint("pipelineThroughput", "kg/hr", ConstraintType.DESIGN)
        .setDesignValue(25000.0).setCurrentValue(20000.0));
    integratedProcess.add(feedPipeline);

    LNGProcessModel model = new LNGProcessBuilder().setName("pipeline integrated LNG")
        .setUpstreamProcess(integratedProcess, feedPipeline.getOutletStream()).setNumberOfZones(4).build();
    LNGProcessModel.CapacityResult capacity = model.evaluateCapacity();

    assertSame(integratedProcess, model.getProcessSystem());
    assertSame(feedPipeline.getOutletStream(), model.getFeedStream());
    assertEquals("LNG feed pipeline", capacity.getBottleneck().getEquipmentName());
    assertEquals(80.0, capacity.getRankedUtilizationPercent().get("LNG feed pipeline"), 1.0e-9);
    assertFalse(capacity.isAnyEquipmentOverloaded());
    assertNotNull(capacity.getUtilizationSnapshotJson());
    assertNotNull(capacity.getDesignReportJson());
    assertTrue(capacity.getDesignReportJson().contains("\"sizingDataAvailable\": false"));
  }

  @Test
  void testProfessionalFlowsheetExposesColumnsRecyclesAndProducts() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 65.0);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("methane", 0.82);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.025);
    fluid.addComponent("n-pentane", 0.015);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("conditioned rich gas", fluid);
    inlet.setFlowRate(30000.0, "kg/hr");

    ProcessSystem plant = new ProcessSystem("professional LNG flowsheet");
    plant.add(inlet);
    DistillationColumn scrubColumn = new DistillationColumn("heavy hydrocarbon scrub column", 8, true, true);
    scrubColumn.addFeedStream(inlet, 4);
    scrubColumn.setCondenserTemperature(-35.0, "C");
    scrubColumn.setReboilerTemperature(65.0, "C");
    plant.add(scrubColumn);

    StreamInterface treatedGas = scrubColumn.getGasOutStream();
    StreamInterface ngl = scrubColumn.getLiquidOutStream();
    LNGProcessModel model = new LNGProcessBuilder().setName("professional C3MR train").setCycle(LNGProcessCycle.C3MR)
        .setUpstreamProcess(plant, treatedGas).setNumberOfZones(4).build().registerOutputStream("NGL", ngl);

    assertSame(plant, model.getProcessSystem());
    assertSame(treatedGas, model.getFeedStream());
    assertSame(ngl, model.getOutputStream("NGL"));
    assertEquals(3, model.getOutputStreams().size());
    assertEquals(1, model.getEquipment(DistillationColumn.class).size());
    assertTrue(model.getEquipment(Compressor.class).size() >= 4);
    assertTrue(model.getEquipment(Recycle.class).size() >= 2);
    assertTrue(model.getEquipment().contains(scrubColumn));
    assertThrows(IllegalArgumentException.class, () -> model.getEquipment(null));
  }

  @Test
  void testRejectsInvalidConfiguration() {
    LNGProcessBuilder builder = new LNGProcessBuilder();

    assertThrows(IllegalArgumentException.class, () -> builder.setCycle(null));
    assertThrows(IllegalArgumentException.class, () -> builder.setFeedFlowRate(0.0));
    assertThrows(IllegalArgumentException.class, () -> builder.setFeedPressure(-1.0));
    assertThrows(IllegalArgumentException.class, () -> builder.setNumberOfZones(1));
    assertThrows(IllegalArgumentException.class, () -> builder.setFeedStream(null));
    assertThrows(IllegalArgumentException.class, () -> builder.setUpstreamProcess(null, null));
    assertThrows(IllegalArgumentException.class, () -> builder.setCompressorEfficiency(1.1));
  }
}
