package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Simulation-to-DEXPI-to-simulation round trips: a model exported with {@link DexpiXmlWriter} and rebuilt with
 * {@link DexpiSimulationBuilder} must reproduce the source flowsheet and results.
 */
public class DexpiSimulationRoundTripTest {
  @TempDir
  File tempDir;

  /**
   * Creates the test well fluid.
   *
   * @return fluid
   */
  private static SystemInterface wellFluid() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 70.0, 60.0);
    fluid.addComponent("nitrogen", 0.5);
    fluid.addComponent("CO2", 1.5);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("ethane", 8.0);
    fluid.addComponent("propane", 5.0);
    fluid.addComponent("n-butane", 3.0);
    fluid.addComponent("n-pentane", 1.5);
    fluid.addComponent("n-hexane", 1.5);
    fluid.addComponent("n-heptane", 3.0);
    fluid.addComponent("n-octane", 3.0);
    fluid.addComponent("n-decane", 3.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Builds an HP/LP separation train with scrubber liquid return and LP gas recompression recycled to the HP gas line.
   *
   * @return unconverged process
   */
  private static ProcessSystem separationTrainWithRecompression() {
    Stream feed = new Stream("20-FEED-001", wellFluid());
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(70.0, "C");
    feed.setPressure(60.0, "bara");
    Heater heater = new Heater("20-HA-001", feed);
    heater.setOutTemperature(273.15 + 75.0);
    Separator hp = new Separator("20-VA-001", heater.getOutletStream());
    Stream recycled = new Stream("23-RECYCLE", wellFluid());
    recycled.setFlowRate(100.0, "kg/hr");
    recycled.setTemperature(40.0, "C");
    recycled.setPressure(60.0, "bara");
    Mixer gasMixer = new Mixer("23-MA-001");
    gasMixer.addStream(hp.getGasOutStream());
    gasMixer.addStream(recycled);
    Cooler cooler = new Cooler("23-HA-001", gasMixer.getOutletStream());
    cooler.setOutTemperature(273.15 + 30.0);
    Separator scrubber = new Separator("23-VG-001", cooler.getOutletStream());
    Compressor compressor = new Compressor("23-KA-001", scrubber.getGasOutStream());
    compressor.setOutletPressure(120.0);
    ThrottlingValve hpLiquidValve = new ThrottlingValve("20-LV-001", hp.getLiquidOutStream());
    hpLiquidValve.setOutletPressure(10.0);
    ThrottlingValve scrubberValve = new ThrottlingValve("23-LV-001", scrubber.getLiquidOutStream());
    scrubberValve.setOutletPressure(10.0);
    Mixer liquidMixer = new Mixer("20-MA-002");
    liquidMixer.addStream(hpLiquidValve.getOutletStream());
    liquidMixer.addStream(scrubberValve.getOutletStream());
    Separator lp = new Separator("20-VA-002", liquidMixer.getOutletStream());
    Pump pump = new Pump("20-PA-001", lp.getLiquidOutStream());
    pump.setOutletPressure(30.0);
    Compressor recompressor = new Compressor("23-KA-002", lp.getGasOutStream());
    recompressor.setOutletPressure(60.0);
    Cooler recycleCooler = new Cooler("23-HA-003", recompressor.getOutletStream());
    recycleCooler.setOutTemperature(273.15 + 40.0);
    Recycle recycle = new Recycle("23-RECYCLE-CALC");
    recycle.addStream(recycleCooler.getOutletStream());
    recycle.setOutletStream(recycled);
    recycle.setTolerance(1.0e-3);

    ProcessSystem process = new ProcessSystem();
    process.setName("HP/LP separation with recompression");
    for (ProcessEquipmentInterface unit : new ProcessEquipmentInterface[] {feed, heater, hp, recycled, gasMixer, cooler,
        scrubber, compressor, hpLiquidValve, scrubberValve, liquidMixer, lp, pump, recompressor, recycleCooler,
        recycle}) {
      process.add(unit);
    }
    return process;
  }

  /**
   * Rebuilds and runs a process from a DEXPI file with the test fluid and feed.
   *
   * @param file DEXPI file
   * @return converged rebuilt process
   * @throws Exception on failure
   */
  private static ProcessSystem rebuild(File file) throws Exception {
    ProcessSystem rebuilt = new DexpiSimulationBuilder(file).setFluidTemplate(wellFluid()).setFeedPressure(60.0, "bara")
        .setFeedTemperature(70.0, "C").setFeedFlowRate(50000.0, "kg/hr").build();
    rebuilt.run();
    return rebuilt;
  }

  /**
   * Asserts that a rebuilt process reproduces the source separator, compressor, pump and heater results.
   *
   * @param source source process
   * @param rebuilt rebuilt process
   */
  private static void assertSameResults(ProcessSystem source, ProcessSystem rebuilt) {
    for (String name : new String[] {"20-VA-001", "23-VG-001", "20-VA-002"}) {
      Separator expected = (Separator) source.getUnit(name);
      Separator actual = (Separator) rebuilt.getUnit(name);
      assertNotNull(actual, name);
      assertEquals(expected.getGasOutStream().getFlowRate("kg/hr"), actual.getGasOutStream().getFlowRate("kg/hr"), 5.0,
          name + " gas");
      assertEquals(expected.getLiquidOutStream().getFlowRate("kg/hr"), actual.getLiquidOutStream().getFlowRate("kg/hr"),
          5.0, name + " liquid");
      assertEquals(expected.getGasOutStream().getPressure("bara"), actual.getGasOutStream().getPressure("bara"), 1e-6,
          name + " pressure");
    }
    for (String name : new String[] {"23-KA-001", "23-KA-002"}) {
      assertEquals(((Compressor) source.getUnit(name)).getPower("kW"),
          ((Compressor) rebuilt.getUnit(name)).getPower("kW"), 1.0, name + " power");
    }
    assertEquals(((Pump) source.getUnit("20-PA-001")).getPower("kW"),
        ((Pump) rebuilt.getUnit("20-PA-001")).getPower("kW"), 0.5);
    assertEquals(((Heater) source.getUnit("20-HA-001")).getDuty(), ((Heater) rebuilt.getUnit("20-HA-001")).getDuty(),
        1000.0);
    assertTrue(rebuilt.getUnit("20-LV-001") instanceof ThrottlingValve, "in-line valve must be rebuilt");
    assertEquals(2, ((Mixer) rebuilt.getUnit("20-MA-002")).getNumberOfInputStreams());
    assertEquals(2, ((Mixer) rebuilt.getUnit("23-MA-001")).getNumberOfInputStreams());
  }

  /**
   * Exports a recycle flowsheet and rebuilds it: separator outlets, in-line valves, mixer inlets, setpoints and the
   * recycle loop must all be preserved, so the rebuilt model reproduces the source results.
   *
   * @throws Exception on failure
   */
  @Test
  public void exportedRecycleFlowsheetRebuildsWithSameResults() throws Exception {
    ProcessSystem source = separationTrainWithRecompression();
    source.run();
    File file = new File(tempDir, "train.xml");
    DexpiXmlWriter.write(source, file);
    String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    assertTrue(xml.contains("Set=\"NeqSimPortAttributes\""));
    assertTrue(xml.contains("Value=\"GasOutlet\"") && xml.contains("Value=\"LiquidOutlet\""));

    assertSameResults(source, rebuild(file));
  }

  /**
   * Files without port roles (older exports) are rebuilt through the nozzle-position and downstream-type fallbacks.
   *
   * @throws Exception on failure
   */
  @Test
  public void legacyExportWithoutPortRolesRebuildsWithSameResults() throws Exception {
    ProcessSystem source = separationTrainWithRecompression();
    source.run();
    File file = new File(tempDir, "train.xml");
    DexpiXmlWriter.write(source, file);
    String legacy = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
        .replaceAll("(?s)<GenericAttributes Set=\"NeqSimPortAttributes\">.*?</GenericAttributes>", "");
    File legacyFile = new File(tempDir, "legacy.xml");
    Files.write(legacyFile.toPath(), legacy.getBytes(StandardCharsets.UTF_8));

    assertSameResults(source, rebuild(legacyFile));
  }

  /**
   * Three-phase separator outlets and every splitter branch survive the round trip, with split factors recovered from
   * the exported branch flows.
   *
   * @throws Exception on failure
   */
  @Test
  public void threePhaseSeparatorAndSplitterBranchesRoundTrip() throws Exception {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 60.0, 30.0);
    fluid.addComponent("methane", 60.0);
    fluid.addComponent("n-heptane", 15.0);
    fluid.addComponent("n-decane", 5.0);
    fluid.addComponent("water", 20.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(40000.0, "kg/hr");
    ThreePhaseSeparator separator = new ThreePhaseSeparator("20-VA-001", feed);
    Splitter splitter = new Splitter("20-SP-001", separator.getOilOutStream(), 3);
    splitter.setSplitFactors(new double[] {0.5, 0.3, 0.2});
    ThrottlingValve first = new ThrottlingValve("20-LV-101", splitter.getSplitStream(0));
    first.setOutletPressure(10.0);
    ThrottlingValve second = new ThrottlingValve("20-LV-102", splitter.getSplitStream(1));
    second.setOutletPressure(10.0);
    Pump pump = new Pump("20-PA-001", splitter.getSplitStream(2));
    pump.setOutletPressure(50.0);
    ThrottlingValve water = new ThrottlingValve("44-LV-001", separator.getWaterOutStream());
    water.setOutletPressure(5.0);
    Cooler gasCooler = new Cooler("23-HA-001", separator.getGasOutStream());
    gasCooler.setOutTemperature(273.15 + 25.0);
    ProcessSystem source = new ProcessSystem();
    for (ProcessEquipmentInterface unit : new ProcessEquipmentInterface[] {feed, separator, splitter, first, second,
        pump, water, gasCooler}) {
      source.add(unit);
    }
    source.run();
    File file = new File(tempDir, "threephase.xml");
    DexpiXmlWriter.write(source, file);

    ProcessSystem rebuilt = new DexpiSimulationBuilder(file).setFluidTemplate(fluid).setFeedPressure(30.0, "bara")
        .setFeedTemperature(60.0, "C").setFeedFlowRate(40000.0, "kg/hr").build();
    rebuilt.run();

    assertEquals(first.getOutletStream().getFlowRate("kg/hr"),
        ((ThrottlingValve) rebuilt.getUnit("20-LV-101")).getOutletStream().getFlowRate("kg/hr"), 5.0);
    assertEquals(second.getOutletStream().getFlowRate("kg/hr"),
        ((ThrottlingValve) rebuilt.getUnit("20-LV-102")).getOutletStream().getFlowRate("kg/hr"), 5.0);
    assertEquals(pump.getPower("kW"), ((Pump) rebuilt.getUnit("20-PA-001")).getPower("kW"), 0.2);
    assertEquals(water.getOutletStream().getFlowRate("kg/hr"),
        ((ThrottlingValve) rebuilt.getUnit("44-LV-001")).getOutletStream().getFlowRate("kg/hr"), 5.0);
    assertEquals(gasCooler.getDuty(), ((Cooler) rebuilt.getUnit("23-HA-001")).getDuty(), 1000.0);
  }

  /**
   * Native DEXPI 2.x files are rejected explicitly instead of producing an empty simulation.
   *
   * @throws Exception on failure
   */
  @Test
  public void nativeDexpi2FileIsRejectedExplicitly() throws Exception {
    ProcessSystem source = separationTrainWithRecompression();
    source.run();
    File file = new File(tempDir, "plant20.xml");
    Dexpi20XmlWriter.write(source, file);
    DexpiXmlReaderException error = assertThrows(DexpiXmlReaderException.class,
        () -> new DexpiSimulationBuilder(file).setFluidTemplate(wellFluid()).build());
    assertTrue(error.getMessage().contains("Native DEXPI 2.x"));
  }

  /**
   * A third-party P&amp;ID without operating data is connected into a flowsheet, and each specification a user must add
   * before running is reported.
   *
   * @throws Exception on failure
   */
  @Test
  public void thirdPartyPidReportsMissingSpecifications() throws Exception {
    File file = new File(getClass().getResource("/dexpi/C01V04-VER.EX01.xml").toURI());
    SystemSrkEos water = new SystemSrkEos(298.15, 5.0);
    water.addComponent("water", 1.0);
    water.setMixingRule("classic");
    DexpiSimulationBuilder builder = new DexpiSimulationBuilder(file).setFluidTemplate(water)
        .setFeedPressure(5.0, "bara").setFeedTemperature(25.0, "C").setFeedFlowRate(10000.0, "kg/hr");
    ProcessSystem process = builder.build();

    assertTrue(process.getUnit("P4711") instanceof Pump);
    List<String> missing = new ArrayList<>();
    List<String> codes = new ArrayList<>();
    for (DexpiSimulationBuilder.BuildDiagnostic diagnostic : builder.getDiagnostics()) {
      codes.add(diagnostic.getCode());
      if ("DEXPI_BUILD_SPECIFICATION_MISSING".equals(diagnostic.getCode())) {
        missing.add(diagnostic.getElementId());
      }
    }
    assertTrue(missing.contains("CentrifugalPump-1"), missing.toString());
    assertTrue(codes.contains("DEXPI_BUILD_NO_FLOW_BRANCH_EXCLUDED"), codes.toString());
    assertTrue(codes.contains("DEXPI_BUILD_RECYCLE_TEAR"), codes.toString());
  }
}
