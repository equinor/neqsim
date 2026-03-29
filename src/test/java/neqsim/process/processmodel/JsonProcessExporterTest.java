package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for JsonProcessExporter — exporting ProcessSystem to JSON and round-tripping via
 * JsonProcessBuilder.
 *
 * @author Even Solbraa
 */
class JsonProcessExporterTest {

  /**
   * Creates a simple test process with a feed, separator, compressor and valve.
   *
   * @return a built ProcessSystem
   */
  private ProcessSystem createSimpleProcess() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-pentane", 0.02);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(60.0, "bara");

    Separator hpSep = new Separator("HP Sep", feed);
    Compressor comp = new Compressor("Comp", hpSep.getGasOutStream());
    comp.setOutletPressure(120.0);
    ThrottlingValve valve = new ThrottlingValve("Valve", hpSep.getLiquidOutStream());
    valve.setOutletPressure(10.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(hpSep);
    process.add(comp);
    process.add(valve);
    process.run();

    return process;
  }

  @Test
  void testExportProducesValidJson() {
    ProcessSystem process = createSimpleProcess();
    String json = process.toJson();

    assertNotNull(json, "toJson() should not return null");
    assertTrue(json.length() > 50, "JSON should have substantial content");

    // Parse to verify it's valid JSON
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    assertTrue(root.has("fluid"), "JSON should have 'fluid' section");
    assertTrue(root.has("process"), "JSON should have 'process' section");
  }

  @Test
  void testExportFluidSection() {
    ProcessSystem process = createSimpleProcess();
    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    JsonObject fluid = root.getAsJsonObject("fluid");

    assertTrue(fluid.has("model"), "Fluid should have model");
    assertTrue(fluid.has("components"), "Fluid should have components");
    assertTrue(fluid.has("mixingRule"), "Fluid should have mixingRule");

    String model = fluid.get("model").getAsString();
    assertTrue("SRK".equals(model), "Model should be SRK, got: " + model);

    JsonObject components = fluid.getAsJsonObject("components");
    assertTrue(components.has("methane"), "Components should include methane");
    assertTrue(components.has("ethane"), "Components should include ethane");
  }

  @Test
  void testExportProcessUnits() {
    ProcessSystem process = createSimpleProcess();
    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    int processSize = root.getAsJsonArray("process").size();
    assertTrue(processSize == 4, "Should have 4 units (feed + sep + comp + valve), got: "
        + processSize);

    // Check types in order
    String type0 = root.getAsJsonArray("process").get(0).getAsJsonObject().get("type")
        .getAsString();
    assertTrue("Stream".equals(type0), "First unit should be Stream, got: " + type0);

    String type1 = root.getAsJsonArray("process").get(1).getAsJsonObject().get("type")
        .getAsString();
    assertTrue("Separator".equals(type1), "Second unit should be Separator, got: " + type1);
  }

  @Test
  void testExportStreamWiring() {
    ProcessSystem process = createSimpleProcess();
    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    // Separator should reference "feed" as inlet
    JsonObject sepJson = root.getAsJsonArray("process").get(1).getAsJsonObject();
    assertTrue(sepJson.has("inlet"), "Separator should have inlet reference");
    String sepInlet = sepJson.get("inlet").getAsString();
    assertTrue("feed".equals(sepInlet), "Separator inlet should be 'feed', got: " + sepInlet);

    // Compressor should reference "HP Sep.gasOut"
    JsonObject compJson = root.getAsJsonArray("process").get(2).getAsJsonObject();
    assertTrue(compJson.has("inlet"), "Compressor should have inlet reference");
    String compInlet = compJson.get("inlet").getAsString();
    assertTrue("HP Sep.gasOut".equals(compInlet),
        "Compressor inlet should be 'HP Sep.gasOut', got: " + compInlet);

    // Valve should reference "HP Sep.liquidOut"
    JsonObject valveJson = root.getAsJsonArray("process").get(3).getAsJsonObject();
    String valveInlet = valveJson.get("inlet").getAsString();
    assertTrue("HP Sep.liquidOut".equals(valveInlet),
        "Valve inlet should be 'HP Sep.liquidOut', got: " + valveInlet);
  }

  @Test
  void testExportCompressorProperties() {
    ProcessSystem process = createSimpleProcess();
    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    JsonObject compJson = root.getAsJsonArray("process").get(2).getAsJsonObject();
    assertTrue(compJson.has("properties"), "Compressor should have properties");
    JsonObject props = compJson.getAsJsonObject("properties");
    assertTrue(props.has("outletPressure"), "Compressor properties should have outletPressure");
    double outP = props.get("outletPressure").getAsDouble();
    assertTrue(Math.abs(outP - 120.0) < 0.1,
        "Compressor outlet pressure should be ~120 bara, got: " + outP);
  }

  @Test
  void testRoundTripViaJsonProcessBuilder() {
    ProcessSystem original = createSimpleProcess();
    String json = original.toJson();

    // Rebuild from JSON
    SimulationResult result = ProcessSystem.fromJsonAndRun(json);
    assertTrue(result.isSuccess(),
        "Round-trip build should succeed: " + result);

    ProcessSystem rebuilt = result.getProcessSystem();
    assertNotNull(rebuilt, "Rebuilt process should not be null");
    assertNotNull(rebuilt.getUnit("feed"), "Rebuilt should have 'feed'");
    assertNotNull(rebuilt.getUnit("HP Sep"), "Rebuilt should have 'HP Sep'");
    assertNotNull(rebuilt.getUnit("Comp"), "Rebuilt should have 'Comp'");
    assertNotNull(rebuilt.getUnit("Valve"), "Rebuilt should have 'Valve'");
  }

  @Test
  void testExportWithSplitter() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");

    Splitter splitter = new Splitter("TEE-1", feed, 2);
    splitter.setSplitFactors(new double[] {0.6, 0.4});

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(splitter);
    process.run();

    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    JsonObject splitterJson = root.getAsJsonArray("process").get(1).getAsJsonObject();
    assertTrue("Splitter".equals(splitterJson.get("type").getAsString()));
    assertTrue(splitterJson.has("properties"), "Splitter should have properties");
    assertTrue(splitterJson.getAsJsonObject("properties").has("splitFactors"),
        "Splitter properties should have splitFactors");
  }

  @Test
  void testExportWithCooler() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 80.0, 60.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("hot-gas", fluid);
    feed.setFlowRate(10000.0, "kg/hr");

    Cooler cooler = new Cooler("AC-1", feed);
    cooler.setOutletTemperature(273.15 + 30.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(cooler);
    process.run();

    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    JsonObject coolerJson = root.getAsJsonArray("process").get(1).getAsJsonObject();
    assertTrue("Cooler".equals(coolerJson.get("type").getAsString()));
    assertTrue(coolerJson.has("properties"), "Cooler should have properties");
  }

  @Test
  void testProcessModelRoundTrip() {
    // Build area 1 — separation
    SystemInterface fluid1 = new SystemSrkEos(273.15 + 30.0, 80.0);
    fluid1.addComponent("methane", 0.85);
    fluid1.addComponent("ethane", 0.10);
    fluid1.addComponent("propane", 0.05);
    fluid1.setMixingRule("classic");

    Stream feed1 = new Stream("feed", fluid1);
    feed1.setFlowRate(40000.0, "kg/hr");
    Separator sep = new Separator("HP Sep", feed1);

    ProcessSystem separation = new ProcessSystem();
    separation.add(feed1);
    separation.add(sep);
    separation.run();

    // Build area 2 — compression
    SystemInterface fluid2 = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid2.addComponent("methane", 0.90);
    fluid2.addComponent("ethane", 0.10);
    fluid2.setMixingRule("classic");

    Stream feed2 = new Stream("comp-feed", fluid2);
    feed2.setFlowRate(30000.0, "kg/hr");
    Compressor comp = new Compressor("Comp-1", feed2);
    comp.setOutletPressure(120.0);

    ProcessSystem compression = new ProcessSystem();
    compression.add(feed2);
    compression.add(comp);
    compression.run();

    // Assemble ProcessModel
    ProcessModel model = new ProcessModel();
    model.add("separation", separation);
    model.add("compression", compression);

    // Export
    String json = model.toJson();
    assertNotNull(json);
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    assertTrue(root.has("areas"));
    assertTrue(root.getAsJsonObject("areas").has("separation"));
    assertTrue(root.getAsJsonObject("areas").has("compression"));

    // Round-trip
    ProcessModel rebuilt = ProcessModel.fromJson(json);
    assertNotNull(rebuilt);
    assertNotNull(rebuilt.get("separation"), "separation area should exist after round-trip");
    assertNotNull(rebuilt.get("compression"), "compression area should exist after round-trip");

    // Verify unit counts match
    assertTrue(rebuilt.get("separation").getUnitOperations().size() >= 2,
        "separation area should have at least feed + separator");
    assertTrue(rebuilt.get("compression").getUnitOperations().size() >= 2,
        "compression area should have at least feed + compressor");
  }
}
