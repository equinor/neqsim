package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.EclipseVFPExporter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * VFP process integration, surface flash and ownership regressions.
 *
 * @author ESOL
 * @version 1.0
 */
class VfpPipelineTest {
  /**
   * Creates a reproducible dry gas feed.
   *
   * @return flowing gas stream at 95 bara and 40 C
   */
  private Stream feed() {
    SystemInterface fluid = new SystemSrkEos(313.15, 95);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream stream = new Stream("feed", fluid);
    stream.setFlowRate(1500000, "Sm3/day");
    stream.run();
    return stream;
  }

  /**
   * Creates a pipeline with an explicit synthetic metering definition.
   *
   * @param feed upstream stream
   * @return configured pipeline
   */
  private VfpPipeline pipeline(StreamInterface feed) {
    VfpPipeline pipe = new VfpPipeline("line", feed);
    pipe.setVfpTable(VfpTable.fromString(VfpTableTest.deck()));
    pipe.setOutletTemperature(4, "C");
    pipe.setTableArguments(stream -> new double[] {stream.getFlowRate("Sm3/day"), 0, 100});
    return pipe;
  }

  /** Tests pressure round trip, flow/composition conservation and inlet ownership. */
  @Test
  void forwardBackwardConsistencyAndOutletFlash() {
    Stream feed = feed();
    VfpPipeline pipe = pipeline(feed);
    UUID id = UUID.randomUUID();
    pipe.run(id);
    assertEquals(30, pipe.getOutletPressure(), 1e-9);
    assertEquals(277.15, pipe.getOutletTemperature(), 1e-9);
    assertEquals(95, feed.getPressure("bara"));
    assertEquals(313.15, feed.getTemperature("K"));
    assertEquals(feed.getFlowRate("kg/sec"), pipe.getOutletStream().getFlowRate("kg/sec"), 1e-9);
    assertEquals(feed.getThermoSystem().getComponent(0).getz(),
        pipe.getOutletStream().getThermoSystem().getComponent(0).getz(), 1e-12);
    assertEquals(id, pipe.getOutletStream().getCalculationIdentifier());
    pipe.setOutletPressure(pipe.getOutletPressure() * 100000, "Pa");
    pipe.run();
    assertEquals(95, pipe.getRequiredInletPressure("bara"), 1e-9);
    assertEquals(95, feed.getPressure("bara"));
    double[] trace = pipe.getTableArguments();
    trace[0] = 0;
    assertEquals(1500000, pipe.getTableArguments()[0], 1e-6);
    pipe.setForwardMode();
    pipe.run();
    assertEquals(30, pipe.getOutletPressure(), 1e-9);
  }

  /** Tests rate splitting and re-execution after upstream state changes. */
  @Test
  void parallelLinesPreserveTotalFlowAndRatios() {
    Stream feed = feed();
    VfpPipeline pipe = pipeline(feed);
    pipe.run();
    double pressure = pipe.getOutletPressure();
    feed.setFlowRate(3000000, "Sm3/day");
    feed.run();
    pipe.setNumberOfParallelLines(2);
    pipe.run();
    assertEquals(pressure, pipe.getOutletPressure(), 1e-9);
    assertEquals(3000000, pipe.getOutletStream().getFlowRate("Sm3/day"), 1e-6);
    assertEquals(1500000, pipe.getTableArguments()[0], 1e-6);
    feed.setPressure(110, "bara");
    feed.run();
    pipe.run();
    assertEquals(36, pipe.getOutletPressure(), 1e-9);
    VfpTableTest.assertError("parallel lines", () -> pipe.setNumberOfParallelLines(0));
  }

  /** Tests explicit GOR-to-OGR conversion at the pipeline boundary. */
  @Test
  void ogrTablesUseTheReciprocalOfProviderGor() {
    Stream feed = feed();
    VfpPipeline pipe = pipeline(feed);
    pipe.setVfpTable(VfpTable.fromString(VfpTableTest.deck().replace("'GOR'", "'OGR'").replace("100 /", "0.01 /")));
    pipe.run();
    assertEquals(30, pipe.getOutletPressure(), 1e-9);
    assertEquals(0.01, pipe.getTableGasRatio());
    assertEquals(100, pipe.getTableArguments()[2]);
  }

  /** Tests all mandatory setup settings with actionable failures. */
  @Test
  void missingSettingsAreNamedAndValidated() {
    VfpPipeline pipe = new VfpPipeline("line", feed());
    assertFalse(pipe.validateSetup().isValid());
    assertTrue(assertThrows(IllegalStateException.class, pipe::run).getMessage().contains("VFP table"));
    pipe.setVfpTable(VfpTable.fromString(VfpTableTest.deck()));
    assertTrue(assertThrows(IllegalStateException.class, pipe::run).getMessage().contains("outlet temperature"));
    pipe.setOutletTemperature(4, "C");
    assertTrue(assertThrows(IllegalStateException.class, pipe::run).getMessage().contains("table-argument"));
    pipe.setTableArguments(stream -> new double[] {1500000, 0, 100});
    assertTrue(pipe.validateSetup().isValid());
    VfpTableTest.assertError("temperature", () -> pipe.setOutletTemperature(-1, "K"));
    VfpTableTest.assertError("pressure", () -> pipe.setOutletPressure(Double.NaN));
    pipe.setTableArguments(stream -> new double[] {1, 2});
    VfpTableTest.assertError("gas rate", pipe::run);
  }

  /** Tests zero-flow rejection and explicit bypass without calling the provider. */
  @Test
  void zeroFlowNeedsExplicitPermission() {
    Stream feed = feed();
    VfpPipeline pipe = pipeline(feed);
    feed.setFlowRate(0, "kg/sec");
    pipe.setTableArguments(stream -> {
      throw new AssertionError("Zero-flow bypass must not flash surface arguments");
    });
    VfpTableTest.assertError("Zero flow", pipe::run);
    pipe.setAllowZeroFlow(true);
    pipe.run();
    assertEquals(95, pipe.getOutletPressure(), 1e-9);
    assertEquals(0, pipe.getOutletStream().getFlowRate("kg/sec"));
    assertNull(pipe.getTableArguments());
  }

  /** Tests standard-state dry gas against an independent EOS volume evaluation at the same T/P. */
  @Test
  void standardFlashDryGasUsesExplicitConditionsAndLeavesFeedUntouched() {
    Stream feed = feed();
    StandardConditionsFlash definition = new StandardConditionsFlash(15, "C", 1.01325, "bara");
    double[] args = definition.calculate(feed);
    SystemInterface reference = feed.getThermoSystem().clone();
    reference.setTemperature(288.15);
    reference.setPressure(1.01325);
    new ThermodynamicOperations(reference).TPflash();
    reference.initProperties();
    assertEquals(reference.getFlowRate("m3/day"), args[0], 1e-6);
    assertEquals(0, args[1]);
    assertEquals(Double.POSITIVE_INFINITY, args[2]);
    assertEquals(95, feed.getPressure("bara"));
    assertEquals(313.15, feed.getTemperature("K"));
    double[] warm = new StandardConditionsFlash(30, "C", 1.01325, "bara").calculate(feed);
    assertTrue(warm[0] > args[0]);
    VfpPipeline pipe = pipeline(feed);
    pipe.setVfpTable(VfpTable.fromString(VfpTableTest.deck().replace("'GOR'", "'OGR'").replace("100 /", "0 /")));
    pipe.setTableArguments(definition);
    pipe.run();
    assertEquals(0, pipe.getTableGasRatio());
    VfpTableTest.assertError("GOR", () -> {
      pipe.setVfpTable(VfpTable.fromString(VfpTableTest.deck()));
      pipe.run();
    });
  }

  /** Tests phase-volume definitions for wet condensate rather than total-feed standard moles. */
  @Test
  void wetCondensateArgumentsMatchSeparatedVolumes() {
    SystemInterface fluid = new SystemSrkEos(313.15, 95);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("n-heptane", 0.15);
    fluid.addComponent("water", 0.05);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("wet", fluid);
    feed.setFlowRate(10, "kg/sec");
    feed.run();
    double[] args = new StandardConditionsFlash(15, "C", 1.01325, "bara").calculate(feed);
    SystemInterface separated = feed.getThermoSystem().clone();
    separated.setTemperature(288.15);
    separated.setPressure(1.01325);
    new ThermodynamicOperations(separated).TPflash();
    separated.init(1);
    assertTrue(args[1] > 0);
    assertTrue(Double.isFinite(args[2]) && args[2] > 0);
    double gas = separated.getPhase("gas").getFlowRate("m3/sec") * 86400;
    double water = separated.getPhase("aqueous").getFlowRate("m3/sec") * 86400;
    double oil = separated.getPhase("oil").getFlowRate("m3/sec") * 86400;
    assertEquals(gas, args[0], 1e-6);
    assertEquals(water / gas, args[1], 1e-12);
    assertEquals(gas / oil, args[2], 1e-9);
  }

  /** Tests ProcessSystem composition, mixer connection and upstream table-grid consistency. */
  @Test
  void processSystemAndWriterGridPoint() {
    Stream feed = feed();
    feed.setFlowRate(1000000, "Sm3/day");
    VfpPipeline line = pipeline(feed);
    EclipseVFPExporter writer = new EclipseVFPExporter();
    writer.setFlowRateType("GAS");
    writer.setWaterCutType("WGR");
    writer.setGORType("GOR");
    writer.setFlowRates(new double[] {1000000, 2000000});
    writer.setTHPs(new double[] {20});
    writer.setWaterCuts(new double[] {0});
    writer.setGORs(new double[] {100});
    writer.setBHPTable(new double[][][][][] {{{{{60}}}}, {{{{80}}}}});
    line.setVfpTable(VfpTable.fromString(writer.getVFPPRODString()));
    line.setOutletPressure(20, "bara");
    Mixer tee = new Mixer("tee");
    tee.addStream(line.getOutletStream());
    VfpPipeline export = pipeline(tee.getOutletStream());
    export.setName("export");
    export.setOutletPressure(20, "bara");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(line);
    process.add(tee);
    process.add(export);
    process.run();
    assertEquals(60, line.getRequiredInletPressure("bara"), 1e-9);
    assertEquals(60, export.getRequiredInletPressure("bara"), 1e-9);
    assertEquals(feed.getFlowRate("kg/sec"), export.getOutletStream().getFlowRate("kg/sec"), 1e-9);
  }

  /**
   * Tests Java serialization of pipeline, table and built-in surface definition.
   *
   * @throws Exception if serializing or restoring the configured equipment fails
   */
  @Test
  void serializableEquipmentPreservesConfiguration() throws Exception {
    VfpPipeline pipe = pipeline(feed());
    pipe.setVfpTable(VfpTable.fromString(VfpTableTest.deck().replace("'GOR'", "'OGR'").replace("100 /", "0 /")));
    pipe.setTableArguments(new StandardConditionsFlash(15, "C", 1.01325, "bara"));
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(pipe);
    }
    VfpPipeline restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (VfpPipeline) input.readObject();
    }
    restored.run();
    pipe.run();
    assertEquals(pipe.getOutletPressure(), restored.getOutletPressure(), 1e-9);
  }
}
