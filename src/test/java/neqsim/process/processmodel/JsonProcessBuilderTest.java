package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for JsonProcessBuilder — building ProcessSystem from JSON definitions.
 *
 * @author Even Solbraa
 */
class JsonProcessBuilderTest {

  @Test
  void testBuildSimpleStream() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0,"
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [50000.0, \"kg/hr\"]}}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    assertNotNull(result.getProcessSystem());
    assertNotNull(result.getProcessSystem().getUnit("feed"));
  }

  @Test
  void testBuildStreamAndSeparator() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0,"
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [50000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Separator\", \"name\": \"HP Sep\"," + "   \"inlet\": \"feed\"}" + "]"
        + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    ProcessSystem process = result.getProcessSystem();
    assertNotNull(process.getUnit("feed"));
    assertNotNull(process.getUnit("HP Sep"));
  }

  @Test
  void testBuildWithSeparatorAndCompressor() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0,"
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [50000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Separator\", \"name\": \"HP Sep\"," + "   \"inlet\": \"feed\"},"
        + "  {\"type\": \"Compressor\", \"name\": \"Comp\"," + "   \"inlet\": \"HP Sep.gasOut\"}"
        + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    ProcessSystem process = result.getProcessSystem();
    assertNotNull(process.getUnit("Comp"));
  }

  @Test
  void testBuildWithWhitespaceAroundStreamReference() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    neqsim.process.equipment.separator.Separator separator =
        new neqsim.process.equipment.separator.Separator("HP Sep", feed);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);

    assertNotNull(process.resolveStreamReference("  feed  "),
        "resolveStreamReference should trim whitespace for plain stream names");
    StreamInterface gasOutWithWhitespace = process.resolveStreamReference("  HP Sep. gasOut  ");
    assertNotNull(gasOutWithWhitespace,
        "resolveStreamReference should trim whitespace around unit and port tokens");
  }

  @Test
  void testBuildWithMultipleFluids() {
    String json = "{" + "\"fluids\": {" + "  \"gas\": {" + "    \"model\": \"SRK\","
        + "    \"temperature\": 298.15," + "    \"pressure\": 50.0,"
        + "    \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "  }," + "  \"oil\": {"
        + "    \"model\": \"PR\"," + "    \"temperature\": 350.0," + "    \"pressure\": 100.0,"
        + "    \"components\": {\"methane\": 0.3, \"nC10\": 0.7}" + "  }" + "}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"gasFeed\", \"fluidRef\": \"gas\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Stream\", \"name\": \"oilFeed\", \"fluidRef\": \"oil\","
        + "   \"properties\": {\"flowRate\": [50000.0, \"kg/hr\"]}}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    ProcessSystem process = result.getProcessSystem();
    assertNotNull(process.getUnit("gasFeed"));
    assertNotNull(process.getUnit("oilFeed"));
  }

  @Test
  void testBuildAndRun() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0,"
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "},"
        + "\"autoRun\": true," + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [50000.0, \"kg/hr\"]}}" + "]" + "}";

    SimulationResult result = JsonProcessBuilder.buildAndRun(json);
    assertTrue(result.isSuccess(), "Build and run should succeed: " + result);
    assertNotNull(result.getReportJson());
  }

  @Test
  void testStaticFromJson() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0," + "  \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}}" + "]" + "}";

    SimulationResult result = ProcessSystem.fromJson(json);
    assertTrue(result.isSuccess());
    assertNotNull(result.getProcessSystem());
  }

  @Test
  void testStaticFromJsonAndRun() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0," + "  \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}}" + "]" + "}";

    SimulationResult result = ProcessSystem.fromJsonAndRun(json);
    assertTrue(result.isSuccess());
    assertNotNull(result.getReportJson());
  }

  @Test
  void testBuildWithInvalidJson() {
    SimulationResult result = new JsonProcessBuilder().build("not valid json");
    assertTrue(result.isError());
    assertFalse(result.getErrors().isEmpty());
    assertEquals("JSON_PARSE_ERROR", result.getErrors().get(0).getCode());
  }

  @Test
  void testBuildWithNullJson() {
    SimulationResult result = new JsonProcessBuilder().build(null);
    assertTrue(result.isError());
    assertEquals("JSON_PARSE_ERROR", result.getErrors().get(0).getCode());
  }

  @Test
  void testBuildWithMissingProcess() {
    String json = "{\"fluid\": {\"model\": \"SRK\", \"components\": {\"methane\": 1.0}}}";
    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isError());
    assertEquals("MISSING_PROCESS", result.getErrors().get(0).getCode());
  }

  @Test
  void testBuildWithMissingType() {
    String json = "{" + "\"process\": [{\"name\": \"test\"}]" + "}";
    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isError());
    assertEquals("MISSING_TYPE", result.getErrors().get(0).getCode());
  }

  @Test
  void testBuildWithInvalidInletRef() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0," + "  \"components\": {\"methane\": 1.0}" + "}," + "\"process\": ["
        + "  {\"type\": \"Separator\", \"name\": \"sep\"," + "   \"inlet\": \"nonexistent\"}" + "]"
        + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    // Invalid inlet refs are now warnings (partial success) rather than hard errors,
    // to allow complex models with sub-flowsheets to build partially.
    assertTrue(result.isSuccess());
    assertFalse(result.getWarnings().isEmpty());
  }

  @Test
  void testBuildWithUnknownModel() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"INVALID_MODEL\","
        + "  \"components\": {\"methane\": 1.0}" + "}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"feed\"}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    // Should still fail since stream needs a fluid
    assertTrue(result.isError());
  }

  @Test
  void testBuildWithValve() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0," + "  \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"valve\", \"name\": \"choke\"," + "   \"inlet\": \"feed\","
        + "   \"properties\": {\"outletPressure\": 20.0}}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    assertNotNull(result.getProcessSystem().getUnit("choke"));
  }

  @Test
  void testBuildWithHeater() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0," + "  \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Heater\", \"name\": \"heater1\"," + "   \"inlet\": \"feed\"}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    assertNotNull(result.getProcessSystem().getUnit("heater1"));
  }

  @Test
  void testBuildWithMixer() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0,"
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"stream1\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Stream\", \"name\": \"stream2\","
        + "   \"properties\": {\"flowRate\": [20000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Mixer\", \"name\": \"mixer1\","
        + "   \"inlets\": [\"stream1\", \"stream2\"]}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    assertNotNull(result.getProcessSystem().getUnit("mixer1"));
  }

  @Test
  void testBuildWithSplitter() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0," + "  \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Splitter\", \"name\": \"splitter1\"," + "   \"inlet\": \"feed\","
        + "   \"properties\": {\"splitFactors\": [0.6, 0.4]}}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    assertNotNull(result.getProcessSystem().getUnit("splitter1"));
  }

  @Test
  void testBuildWithSplitterAndDownstream() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0," + "  \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Splitter\", \"name\": \"spl\"," + "   \"inlet\": \"feed\","
        + "   \"properties\": {\"splitFactors\": [0.7, 0.3]}},"
        + "  {\"type\": \"Heater\", \"name\": \"heater1\"," + "   \"inlet\": \"spl.split0\"},"
        + "  {\"type\": \"Heater\", \"name\": \"heater2\"," + "   \"inlet\": \"spl.split1\"}" + "]"
        + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    assertNotNull(result.getProcessSystem().getUnit("heater1"));
    assertNotNull(result.getProcessSystem().getUnit("heater2"));
  }

  @Test
  void testBuildWithThreePhaseSeparator() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0,"
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [50000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"ThreePhaseSeparator\", \"name\": \"3PS\"," + "   \"inlet\": \"feed\"},"
        + "  {\"type\": \"Compressor\", \"name\": \"gasComp\"," + "   \"inlet\": \"3PS.gasOut\"}"
        + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    assertNotNull(result.getProcessSystem().getUnit("3PS"));
    assertNotNull(result.getProcessSystem().getUnit("gasComp"));
  }

  @Test
  void testBuildWithDistillationColumn() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0,"
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"DistillationColumn\", \"name\": \"col1\"," + "   \"inlet\": \"feed\","
        + "   \"properties\": {" + "     \"numberOfTrays\": 8," + "     \"hasReboiler\": true,"
        + "     \"hasCondenser\": true" + "   }}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    assertNotNull(result.getProcessSystem().getUnit("col1"));
  }

  @Test
  void testBuildWithCooler() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 350.0,"
        + "  \"pressure\": 50.0," + "  \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Cooler\", \"name\": \"cooler1\"," + "   \"inlet\": \"feed\","
        + "   \"properties\": {\"outTemperature\": [30.0, \"C\"]}}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    assertNotNull(result.getProcessSystem().getUnit("cooler1"));
  }

  @Test
  void testBuildWithPump() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 10.0," + "  \"components\": {\"water\": 1.0}" + "}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [5000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Pump\", \"name\": \"pump1\"," + "   \"inlet\": \"feed\","
        + "   \"properties\": {\"outletPressure\": 80.0}}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    assertNotNull(result.getProcessSystem().getUnit("pump1"));
  }

  @Test
  void testBuildWithExpander() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 80.0," + "  \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Expander\", \"name\": \"exp1\"," + "   \"inlet\": \"feed\","
        + "   \"properties\": {\"outletPressure\": 20.0}}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    assertNotNull(result.getProcessSystem().getUnit("exp1"));
  }

  @Test
  void testBuildWithAdiabaticPipe() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0," + "  \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"AdiabaticPipe\", \"name\": \"pipe1\"," + "   \"inlet\": \"feed\","
        + "   \"properties\": {\"length\": 1000.0, \"diameter\": 0.3}}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    assertNotNull(result.getProcessSystem().getUnit("pipe1"));
  }

  @Test
  void testBuildWithPropertyUnitArray() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0," + "  \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"valve\", \"name\": \"choke\"," + "   \"inlet\": \"feed\","
        + "   \"properties\": {\"outletPressure\": [20.0, \"bara\"]}}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    assertNotNull(result.getProcessSystem().getUnit("choke"));
  }

  @Test
  void testBuildWithHeatExchangerMultiInlet() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0,"
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"hotStream\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Stream\", \"name\": \"coldStream\","
        + "   \"properties\": {\"flowRate\": [15000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"HeatExchanger\", \"name\": \"HX1\","
        + "   \"inlets\": [\"hotStream\", \"coldStream\"]}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    assertNotNull(result.getProcessSystem().getUnit("HX1"));
  }

  @Test
  void testTolerantWiringRemovesUnwiredUnit() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0," + "  \"components\": {\"methane\": 1.0}" + "}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Separator\", \"name\": \"goodSep\"," + "   \"inlet\": \"feed\"},"
        + "  {\"type\": \"Compressor\", \"name\": \"badComp\","
        + "   \"inlet\": \"nonexistent.gasOut\"}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed with warnings: " + result);
    assertTrue(result.hasWarnings(), "Should have wiring warnings");
    assertNotNull(result.getProcessSystem().getUnit("goodSep"));
    // The unwired compressor should have been removed from the process
    assertNull(result.getProcessSystem().getUnit("badComp"));
  }

  @Test
  void testBuildMixerDownstreamWiring() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0," + "  \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"s1\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Stream\", \"name\": \"s2\","
        + "   \"properties\": {\"flowRate\": [20000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Mixer\", \"name\": \"Mix\"," + "   \"inlets\": [\"s1\", \"s2\"]},"
        + "  {\"type\": \"Separator\", \"name\": \"Sep\"," + "   \"inlet\": \"Mix.outlet\"}" + "]"
        + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    assertNotNull(result.getProcessSystem().getUnit("Mix"));
    assertNotNull(result.getProcessSystem().getUnit("Sep"));
  }

  @Test
  void testBuildFullProcessChain() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0,"
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [50000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Separator\", \"name\": \"HPsep\"," + "   \"inlet\": \"feed\"},"
        + "  {\"type\": \"Compressor\", \"name\": \"comp\"," + "   \"inlet\": \"HPsep.gasOut\","
        + "   \"properties\": {\"outletPressure\": 100.0}},"
        + "  {\"type\": \"Cooler\", \"name\": \"cooler\"," + "   \"inlet\": \"comp\","
        + "   \"properties\": {\"outTemperature\": [30.0, \"C\"]}},"
        + "  {\"type\": \"valve\", \"name\": \"lpValve\"," + "   \"inlet\": \"HPsep.liquidOut\","
        + "   \"properties\": {\"outletPressure\": 10.0}}" + "]" + "}";

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Build should succeed: " + result);
    ProcessSystem process = result.getProcessSystem();
    assertNotNull(process.getUnit("feed"));
    assertNotNull(process.getUnit("HPsep"));
    assertNotNull(process.getUnit("comp"));
    assertNotNull(process.getUnit("cooler"));
    assertNotNull(process.getUnit("lpValve"));
  }
}
