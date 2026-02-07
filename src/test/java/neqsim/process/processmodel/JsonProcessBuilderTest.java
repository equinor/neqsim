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
    assertTrue(result.isError());
    assertEquals("STREAM_NOT_FOUND", result.getErrors().get(0).getCode());
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
}
