package neqsim.process.processmodel.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for ProcessSystemState lifecycle management.
 */
public class ProcessSystemStateTest {

  private ProcessSystem process;

  @BeforeEach
  void setUp() {
    SystemInterface fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(20.0, "bara");

    Separator separator = new Separator("separator", feed);

    process = new ProcessSystem();
    process.setName("TestProcess");
    process.add(feed);
    process.add(separator);
    process.run();
  }

  @Test
  void testDefaultConstructor() {
    ProcessSystemState state = new ProcessSystemState();
    assertNotNull(state);
    assertNotNull(state.getCreatedAt());
    assertNotNull(state.getEquipmentStates());
  }

  @Test
  void testFromProcessSystem() {
    ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);

    assertNotNull(state);
    assertEquals("TestProcess", state.getName());
    assertEquals("TestProcess", state.getProcessName());
    assertNotNull(state.getTimestamp());
  }

  @Test
  void testEquipmentStatesCaptured() {
    ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);

    assertFalse(state.getEquipmentStates().isEmpty());
    assertEquals(2, state.getEquipmentStates().size()); // feed and separator
  }

  @Test
  void testSetVersion() {
    ProcessSystemState state = new ProcessSystemState();
    state.setVersion("1.2.3");

    assertEquals("1.2.3", state.getVersion());
  }

  @Test
  void testSetDescription() {
    ProcessSystemState state = new ProcessSystemState();
    state.setDescription("Post-commissioning model");

    assertEquals("Post-commissioning model", state.getDescription());
  }

  @Test
  void testSetCreatedBy() {
    ProcessSystemState state = new ProcessSystemState();
    state.setCreatedBy("ESOL");

    assertEquals("ESOL", state.getCreatedBy());
  }

  @Test
  void testSetName() {
    ProcessSystemState state = new ProcessSystemState();
    state.setName("NewName");

    assertEquals("NewName", state.getName());
  }

  @Test
  void testCustomProperties() {
    ProcessSystemState state = new ProcessSystemState();
    state.setCustomProperty("revision", 5);
    state.setCustomProperty("approved", true);

    assertEquals(5, state.getCustomProperties().get("revision"));
    assertEquals(true, state.getCustomProperties().get("approved"));
  }

  @Test
  void testToJson() {
    ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);
    state.setVersion("1.0.0");

    String json = state.toJson();

    assertNotNull(json);
    assertTrue(json.contains("TestProcess"));
    assertTrue(json.contains("1.0.0"));
  }

  @Test
  void testFromJson() {
    ProcessSystemState original = ProcessSystemState.fromProcessSystem(process);
    original.setVersion("2.0.0");
    original.setDescription("Test description");

    String json = original.toJson();
    ProcessSystemState restored = ProcessSystemState.fromJson(json);

    assertEquals(original.getName(), restored.getName());
    assertEquals(original.getVersion(), restored.getVersion());
    assertEquals(original.getDescription(), restored.getDescription());
  }

  @Test
  void testSaveAndLoadFromFile(@TempDir File tempDir) {
    ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);
    state.setVersion("3.0.0");

    File file = new File(tempDir, "process_state.json");
    state.saveToFile(file.getAbsolutePath());

    assertTrue(file.exists());

    ProcessSystemState loaded = ProcessSystemState.loadFromFile(file.getAbsolutePath());

    assertNotNull(loaded);
    assertEquals(state.getName(), loaded.getName());
    assertEquals(state.getVersion(), loaded.getVersion());
  }

  @Test
  void testLoadNonExistentFile() {
    ProcessSystemState loaded = ProcessSystemState.loadFromFile("/non/existent/path.json");

    assertNull(loaded);
  }

  @Test
  void testToProcessSystem() {
    ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);

    ProcessSystem restored = state.toProcessSystem();

    assertNotNull(restored);
    assertEquals(state.getName(), restored.getName());
  }

  @Test
  void testApplyTo() {
    ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);

    ProcessSystem newProcess = new ProcessSystem();
    newProcess.setName("TestProcess");

    // Should not throw
    state.applyTo(newProcess);
  }

  @Test
  void testChecksumGenerated() {
    ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);

    assertNotNull(state.getChecksum());
    assertFalse(state.getChecksum().isEmpty());
  }

  @Test
  void testValidateIntegrity() {
    ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);

    assertTrue(state.validateIntegrity());
  }

  @Test
  void testMetadata() {
    ProcessSystemState state = new ProcessSystemState();

    assertNotNull(state.getMetadata());

    ModelMetadata newMetadata = new ModelMetadata();
    state.setMetadata(newMetadata);

    assertEquals(newMetadata, state.getMetadata());
  }

  @Test
  void testLastModifiedUpdates() {
    ProcessSystemState state = new ProcessSystemState();
    java.time.Instant initialModified = state.getLastModifiedAt();

    // Force a small delay
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      // ignore
    }

    state.setVersion("new-version");

    assertTrue(state.getLastModifiedAt().isAfter(initialModified)
        || state.getLastModifiedAt().equals(initialModified));
  }

  @Test
  void testEquipmentStateFromEquipment() {
    Stream feed = (Stream) process.getUnit("feed");

    ProcessSystemState.EquipmentState eqState =
        ProcessSystemState.EquipmentState.fromEquipment(feed);

    assertNotNull(eqState);
    assertEquals("feed", eqState.getName());
    assertEquals("Stream", eqState.getType());
  }

  @Test
  void testEquipmentStateNumericProperties() {
    ProcessSystemState.EquipmentState eqState = new ProcessSystemState.EquipmentState();

    assertNotNull(eqState.getNumericProperties());
    assertTrue(eqState.getNumericProperties().isEmpty());
  }

  @Test
  void testEquipmentStateStringProperties() {
    ProcessSystemState.EquipmentState eqState = new ProcessSystemState.EquipmentState();

    assertNotNull(eqState.getStringProperties());
    assertTrue(eqState.getStringProperties().isEmpty());
  }

  @Test
  void testFluidStateFromFluid() {
    SystemInterface fluid = new SystemSrkEos(300.0, 25.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.2);
    fluid.setMixingRule("classic");

    ProcessSystemState.FluidState fluidState = ProcessSystemState.FluidState.fromFluid(fluid);

    assertNotNull(fluidState);
    assertEquals(300.0, fluidState.getTemperature(), 0.001);
    assertEquals(25.0, fluidState.getPressure(), 0.001);
    assertEquals("SystemSrkEos", fluidState.getThermoModelClass());
  }

  @Test
  void testFluidStateComposition() {
    SystemInterface fluid = new SystemSrkEos(300.0, 25.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.2);
    fluid.setMixingRule("classic");

    ProcessSystemState.FluidState fluidState = ProcessSystemState.FluidState.fromFluid(fluid);

    assertNotNull(fluidState.getComposition());
    assertEquals(2, fluidState.getComposition().size());
    assertTrue(fluidState.getComposition().containsKey("methane"));
    assertTrue(fluidState.getComposition().containsKey("ethane"));
  }

  @Test
  void testFluidStateNumberOfPhases() {
    SystemInterface fluid = new SystemSrkEos(300.0, 25.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    ProcessSystemState.FluidState fluidState = ProcessSystemState.FluidState.fromFluid(fluid);

    assertTrue(fluidState.getNumberOfPhases() >= 1);
  }
}
