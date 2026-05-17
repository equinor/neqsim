package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.lifecycle.ProcessModelState;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for ProcessModel serialization functionality.
 *
 * @author ESOL
 */
public class ProcessModelSerializationTest {
  private Path tempDir;
  private ProcessModel testModel;

  @BeforeEach
  public void setUp() throws Exception {
    tempDir = Files.createTempDirectory("neqsim_test_");
    testModel = createTestModel();
  }

  @AfterEach
  public void tearDown() throws Exception {
    // Clean up temp files - ignore errors from locked files on Windows
    if (tempDir != null) {
      try {
        Files.walk(tempDir).sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
          try {
            Files.deleteIfExists(p);
          } catch (Exception ignored) {
            // Best-effort cleanup
          }
        });
      } catch (Exception ignored) {
        // Best-effort cleanup
      }
    }
  }

  private ProcessModel createTestModel() {
    ProcessModel model = new ProcessModel();

    // Create upstream process
    ProcessSystem upstream = createUpstreamProcess();
    model.add("upstream", upstream);

    // Create downstream process
    ProcessSystem downstream = createDownstreamProcess();
    model.add("downstream", downstream);

    model.setMaxIterations(25);
    model.setFlowTolerance(1e-5);
    model.run();

    return model;
  }

  private ProcessSystem createUpstreamProcess() {
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("upstream-feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    ThrottlingValve valve = new ThrottlingValve("upstream-valve", feed);
    valve.setOutletPressure(30.0, "bara");

    Separator separator = new Separator("upstream-separator");
    separator.setInletStream(valve.getOutletStream());

    ProcessSystem process = new ProcessSystem("upstream");
    process.add(feed);
    process.add(valve);
    process.add(separator);

    return process;
  }

  private ProcessSystem createDownstreamProcess() {
    SystemInterface fluid = new SystemSrkEos(298.0, 30.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("downstream-feed", fluid);
    feed.setFlowRate(80.0, "kg/hr");
    feed.setTemperature(20.0, "C");
    feed.setPressure(30.0, "bara");

    ThrottlingValve valve = new ThrottlingValve("downstream-valve", feed);
    valve.setOutletPressure(10.0, "bara");

    ProcessSystem process = new ProcessSystem("downstream");
    process.add(feed);
    process.add(valve);

    return process;
  }

  @Test
  public void testSaveAndLoadNeqsimFormat() {
    String filename = tempDir.resolve("test_model.neqsim").toString();

    // Save
    boolean saved = testModel.saveToNeqsim(filename);
    assertTrue(saved, "Should successfully save to .neqsim format");
    assertTrue(new File(filename).exists(), "File should exist after save");

    // Load
    ProcessModel loaded = ProcessModel.loadFromNeqsim(filename);
    assertNotNull(loaded, "Loaded model should not be null");

    // Verify structure
    assertEquals(2, loaded.getAllProcesses().size(), "Should have 2 process systems");
    assertNotNull(loaded.get("upstream"), "Should have upstream process");
    assertNotNull(loaded.get("downstream"), "Should have downstream process");

    // Verify configuration was preserved
    assertEquals(25, loaded.getMaxIterations(), "Max iterations should be preserved");
    assertEquals(1e-5, loaded.getFlowTolerance(), 1e-10, "Flow tolerance should be preserved");
  }

  @Test
  public void testSaveAndLoadJsonFormat() {
    String filename = tempDir.resolve("test_model.json").toString();

    // Save
    boolean saved = testModel.saveStateToFile(filename);
    assertTrue(saved, "Should successfully save to JSON format");
    assertTrue(new File(filename).exists(), "File should exist after save");

    // Load state
    ProcessModelState loadedState = ProcessModelState.loadFromFile(filename);
    assertNotNull(loadedState, "Loaded state should not be null");
    assertEquals(2, loadedState.getProcessCount(), "Should have 2 process states");

    // Verify execution config was preserved
    assertEquals(25, loadedState.getExecutionConfig().getMaxIterations(),
        "Max iterations should be preserved in state");
  }

  @Test
  public void testSaveAutoDetectsFormat() {
    // Test .neqsim extension
    String neqsimFile = tempDir.resolve("auto_test.neqsim").toString();
    assertTrue(testModel.saveAuto(neqsimFile), "Should save with .neqsim extension");
    assertTrue(new File(neqsimFile).exists(), "File should exist");

    // Test .json extension
    String jsonFile = tempDir.resolve("auto_test.json").toString();
    assertTrue(testModel.saveAuto(jsonFile), "Should save with .json extension");
    assertTrue(new File(jsonFile).exists(), "File should exist");
  }

  @Test
  public void testLoadAutoDetectsFormat() {
    // Save in .neqsim format
    String neqsimFile = tempDir.resolve("load_auto.neqsim").toString();
    testModel.saveToNeqsim(neqsimFile);

    // Load with auto-detection
    ProcessModel loaded = ProcessModel.loadAuto(neqsimFile);
    assertNotNull(loaded, "Should load .neqsim format");
    assertEquals(2, loaded.getAllProcesses().size());
  }

  @Test
  public void testProcessModelStateCapture() {
    ProcessModelState state = testModel.exportState();

    assertNotNull(state, "Exported state should not be null");
    assertEquals(2, state.getProcessCount(), "Should capture 2 process systems");

    // Check process states exist
    assertNotNull(state.getProcessStates().get("upstream"), "Should have upstream state");
    assertNotNull(state.getProcessStates().get("downstream"), "Should have downstream state");

    // Check execution config
    assertEquals(25, state.getExecutionConfig().getMaxIterations());
    assertEquals(1e-5, state.getExecutionConfig().getFlowTolerance(), 1e-10);
    assertTrue(state.getExecutionConfig().isUseOptimizedExecution());
  }

  @Test
  public void testProcessModelStateValidation() {
    ProcessModelState state = testModel.exportState();
    ProcessModelState.ValidationResult result = state.validate();

    assertTrue(result.isValid(), "Valid state should pass validation");
    assertTrue(result.getErrors().isEmpty(), "Should have no errors");
  }

  @Test
  public void testEmptyProcessModelValidation() {
    ProcessModel emptyModel = new ProcessModel();
    ProcessModelState state = ProcessModelState.fromProcessModel(emptyModel);
    ProcessModelState.ValidationResult result = state.validate();

    assertFalse(result.isValid(), "Empty model should fail validation");
    assertFalse(result.getErrors().isEmpty(), "Should have validation errors");
  }

  @Test
  public void testCompressedJsonFormat() {
    String filename = tempDir.resolve("test_model.json.gz").toString();

    // Save compressed
    ProcessModelState state = testModel.exportState();
    state.saveToFile(filename);
    assertTrue(new File(filename).exists(), "Compressed file should exist");

    // Load compressed
    ProcessModelState loaded = ProcessModelState.loadFromFile(filename);
    assertNotNull(loaded, "Should load compressed JSON");
    assertEquals(2, loaded.getProcessCount());
  }

  @Test
  public void testProcessModelStateMetadata() {
    ProcessModelState state = testModel.exportState();
    state.setName("Test Field Model");
    state.setVersion("1.0.0");
    state.setDescription("Unit test model");
    state.setCreatedBy("test-user");
    state.setCustomProperty("project", "Test Project");

    String filename = tempDir.resolve("metadata_test.json").toString();
    state.saveToFile(filename);

    ProcessModelState loaded = ProcessModelState.loadFromFile(filename);
    assertEquals("Test Field Model", loaded.getName());
    assertEquals("1.0.0", loaded.getVersion());
    assertEquals("Unit test model", loaded.getDescription());
    assertEquals("test-user", loaded.getCreatedBy());
    assertEquals("Test Project", loaded.getCustomProperties().get("project"));
  }

  @Test
  public void testProcessSystemBuilderJsonRoundTrip() {
    ProcessSystem upstream = createUpstreamProcess();
    upstream.run();

    String json = upstream.toJson();
    ProcessJsonValidator.ValidationReport validation = ProcessSystem.validateJson(json);
    assertTrue(validation.isValid(), "Exported process JSON should be valid");

    SimulationResult rebuiltResult = ProcessSystem.fromJsonAndRun(json);
    assertTrue(rebuiltResult.isSuccess(),
        "Exported ProcessSystem should rebuild from JSON: " + rebuiltResult.toJson());
    ProcessSystem rebuilt = rebuiltResult.getProcessSystem();

    assertNotNull(rebuilt.getUnit("upstream-feed"));
    assertNotNull(rebuilt.getUnit("upstream-valve"));
    assertNotNull(rebuilt.getUnit("upstream-separator"));
    assertNotNull(rebuilt.resolveStreamReference("upstream-valve.outlet"));
  }

  @Test
  public void testProcessModelBuilderJsonRoundTrip() {
    String json = testModel.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    assertTrue(root.has("areas"), "ProcessModel builder JSON should contain areas");
    ProcessJsonValidator.ValidationReport validation = ProcessJsonValidator.validate(json);
    assertTrue(validation.isValid(), "Exported ProcessModel JSON should be valid");

    ProcessModel rebuilt = ProcessModel.fromJsonAndRun(json);

    assertEquals(2, rebuilt.getAllProcesses().size(), "Round-tripped model should keep areas");
    assertNotNull(rebuilt.get("upstream"));
    assertNotNull(rebuilt.get("downstream"));
    assertNotNull(rebuilt.get("upstream").getUnit("upstream-valve"));
    assertNotNull(rebuilt.get("downstream").getUnit("downstream-valve"));
    assertEquals(25, rebuilt.getMaxIterations(), "Max iterations should round-trip");
    assertEquals(1e-5, rebuilt.getFlowTolerance(), 1e-10, "Flow tolerance should round-trip");
  }

  @Test
  public void testProcessModelGraphvizCommonAndAreaDotExport() throws Exception {
    String commonDot = testModel.toDOT();

    assertTrue(commonDot.contains("digraph"), "Common DOT should be a Graphviz graph");
    assertTrue(commonDot.contains("subgraph cluster_0"), "Common DOT should use area clusters");
    assertTrue(commonDot.contains("upstream"), "Common DOT should include upstream area");
    assertTrue(commonDot.contains("downstream"), "Common DOT should include downstream area");
    assertTrue(commonDot.contains("upstream-valve"), "Common DOT should include upstream units");
    assertTrue(commonDot.contains("downstream-valve"),
        "Common DOT should include downstream units");

    Path commonDotFile = tempDir.resolve("plant.dot");
    testModel.exportToGraphviz(commonDotFile.toString());
    assertTrue(Files.exists(commonDotFile), "Common DOT file should be written");

    Path areaDirectory = tempDir.resolve("area-dots");
    Map<String, Path> areaFiles = testModel.exportAreaDOT(areaDirectory);

    assertEquals(2, areaFiles.size(), "One DOT file should be written per area");
    assertTrue(Files.exists(areaFiles.get("upstream")), "Upstream area DOT should exist");
    assertTrue(Files.exists(areaFiles.get("downstream")), "Downstream area DOT should exist");
    assertTrue(new String(Files.readAllBytes(areaFiles.get("upstream")), StandardCharsets.UTF_8)
        .contains("upstream-valve"), "Upstream DOT should contain upstream equipment");
    assertTrue(new String(Files.readAllBytes(areaFiles.get("downstream")), StandardCharsets.UTF_8)
        .contains("downstream-valve"), "Downstream DOT should contain downstream equipment");
  }

  @Test
  public void testProcessModelCommonDotConnectsSharedInterAreaStream() {
    ProcessSystem upstream = createUpstreamProcess();
    upstream.run();
    Separator upstreamSeparator = (Separator) upstream.getUnit("upstream-separator");

    ThrottlingValve downstreamValve =
        new ThrottlingValve("downstream-inlet-valve", upstreamSeparator.getGasOutStream());
    downstreamValve.setOutletPressure(20.0, "bara");
    ProcessSystem downstream = new ProcessSystem("downstream");
    downstream.add(downstreamValve);

    ProcessModel linkedModel = new ProcessModel();
    linkedModel.add("separation", upstream);
    linkedModel.add("compression", downstream);

    String commonDot = linkedModel.toDOT();

    assertTrue(
        commonDot.contains(
            "\"separation::upstream-separator\" -> " + "\"compression::downstream-inlet-valve\""),
        "Common DOT should connect shared streams across ProcessSystem areas");
    assertTrue(commonDot.contains("penwidth=2.0"),
        "Cross-area stream edges should be highlighted in the common DOT");
  }

  @Test
  public void testLoadFromNeqsimReturnsNullForNonExistent() {
    ProcessModel loaded =
        assertDoesNotThrow(() -> ProcessModel.loadFromNeqsim("non_existent_file.neqsim"),
            "Should handle non-existent file gracefully");
    assertNull(loaded, "Should return null for non-existent file");
  }

  @Test
  public void testSchemaVersionIsSet() {
    ProcessModelState state = testModel.exportState();
    assertEquals("1.0", state.getSchemaVersion(), "Schema version should be set");
  }
}
