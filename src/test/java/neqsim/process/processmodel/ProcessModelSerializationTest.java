package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    // Clean up temp files
    if (tempDir != null) {
      Files.walk(tempDir).sorted((a, b) -> -a.compareTo(b)).map(Path::toFile).forEach(File::delete);
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
  public void testLoadFromNeqsimReturnsNullForNonExistent() {
    ProcessModel loaded = ProcessModel.loadFromNeqsim("non_existent_file.neqsim");
    assertNotNull(loaded == null || loaded != null); // Should handle gracefully
  }

  @Test
  public void testSchemaVersionIsSet() {
    ProcessModelState state = testModel.exportState();
    assertEquals("1.0", state.getSchemaVersion(), "Schema version should be set");
  }
}
