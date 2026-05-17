package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.DexpiInstrumentInfo;
import neqsim.process.processmodel.dexpi.DexpiXmlReaderException;
import neqsim.process.util.DynamicProcessHelper;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for DEXPI instrument export/import functionality.
 *
 * @author NeqSim
 * @version 1.0
 */
public class DexpiInstrumentTest extends NeqSimTest {

  /**
   * Tests that the writer produces ProcessInstrumentationFunction elements for transmitters.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void testWriterProducesInstrumentElements() throws IOException {
    // Build a simple process with instruments
    ProcessSystem process = buildSeparatorProcess();
    DynamicProcessHelper helper = new DynamicProcessHelper(process);
    helper.instrumentAndControl();

    Map<String, MeasurementDeviceInterface> transmitters = helper.getTransmitters();
    Map<String, ControllerDeviceInterface> controllers = helper.getControllers();
    assertFalse(transmitters.isEmpty(), "Should have transmitters");

    // Export to DEXPI XML
    File tempFile = File.createTempFile("dexpi-instrument-test", ".xml");
    tempFile.deleteOnExit();
    DexpiXmlWriter.write(process, tempFile, transmitters, controllers);

    // Read back and verify XML contains instrument elements
    String xml = new String(Files.readAllBytes(tempFile.toPath()), StandardCharsets.UTF_8);
    assertTrue(xml.contains("ProcessInstrumentationFunction"),
        "XML should contain ProcessInstrumentationFunction");
    assertTrue(xml.contains("ProcessSignalGeneratingFunction"),
        "XML should contain ProcessSignalGeneratingFunction");
    assertTrue(xml.contains("InstrumentationLoopFunction"),
        "XML should contain InstrumentationLoopFunction");
  }

  /**
   * Tests that the writer produces ActuatingFunction elements when controllers are present.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void testWriterProducesActuatingFunctions() throws IOException {
    ProcessSystem process = buildSeparatorProcess();
    DynamicProcessHelper helper = new DynamicProcessHelper(process);
    helper.instrumentAndControl();

    Map<String, MeasurementDeviceInterface> transmitters = helper.getTransmitters();
    Map<String, ControllerDeviceInterface> controllers = helper.getControllers();
    assertFalse(controllers.isEmpty(), "Should have controllers");

    File tempFile = File.createTempFile("dexpi-actuating-test", ".xml");
    tempFile.deleteOnExit();
    DexpiXmlWriter.write(process, tempFile, transmitters, controllers);

    String xml = new String(Files.readAllBytes(tempFile.toPath()), StandardCharsets.UTF_8);
    assertTrue(xml.contains("ActuatingFunction"),
        "XML should contain ActuatingFunction for controllers");
    assertTrue(xml.contains("SignalConveyingFunction"),
        "XML should contain SignalConveyingFunction for signal lines");
  }

  /**
   * Tests that the writer without instruments still works (backwards compatibility).
   *
   * @throws IOException if file operations fail
   * @throws DexpiXmlReaderException if parsing fails
   */
  @Test
  public void testWriterWithoutInstrumentsIsBackwardsCompatible()
      throws IOException, DexpiXmlReaderException {
    ProcessSystem process = buildSeparatorProcess();

    File tempFile = File.createTempFile("dexpi-noinstru-test", ".xml");
    tempFile.deleteOnExit();
    DexpiXmlWriter.write(process, tempFile);

    String xml = new String(Files.readAllBytes(tempFile.toPath()), StandardCharsets.UTF_8);
    assertTrue(xml.contains("PlantModel"), "XML should contain PlantModel root");
    // ShapeCatalogue contains ProcessInstrumentationFunction shape definitions,
    // but no actual instrument instances should be present outside ShapeCatalogue
    List<DexpiInstrumentInfo> instruments = DexpiXmlReader.readInstruments(tempFile);
    assertEquals(0, instruments.size(), "Should have no actual instruments when none provided");
  }

  /**
   * Tests reading instrument metadata from a DEXPI XML file.
   *
   * @throws IOException if file operations fail
   * @throws DexpiXmlReaderException if parsing fails
   */
  @Test
  public void testReadInstrumentsFromXml() throws IOException, DexpiXmlReaderException {
    String xml = buildInstrumentXml();
    File tempFile = File.createTempFile("dexpi-read-instr", ".xml");
    tempFile.deleteOnExit();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(xml);
    }

    List<DexpiInstrumentInfo> instruments = DexpiXmlReader.readInstruments(tempFile);
    assertNotNull(instruments, "Should return non-null list");
    assertEquals(1, instruments.size(), "Should find 1 instrument");

    DexpiInstrumentInfo info = instruments.get(0);
    assertEquals("P", info.getCategory(), "Category should be P for pressure");
    assertEquals("ICSA", info.getFunctions(), "Functions should be ICSA");
    assertEquals("4712.02", info.getInstrumentNumber(), "Number should match");
    assertTrue(info.hasControlFunction(), "Should have control function (C in ICSA)");
  }

  /**
   * Tests reading instruments with loop associations.
   *
   * @throws IOException if file operations fail
   * @throws DexpiXmlReaderException if parsing fails
   */
  @Test
  public void testReadInstrumentsWithLoop() throws IOException, DexpiXmlReaderException {
    String xml = buildInstrumentWithLoopXml();
    File tempFile = File.createTempFile("dexpi-loop-instr", ".xml");
    tempFile.deleteOnExit();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(xml);
    }

    List<DexpiInstrumentInfo> instruments = DexpiXmlReader.readInstruments(tempFile);
    assertEquals(1, instruments.size());

    DexpiInstrumentInfo info = instruments.get(0);
    assertTrue(info.isInLoop(), "Instrument should be in a loop");
    assertNotNull(info.getLoopNumber(), "Loop number should not be null");
  }

  /**
   * Tests the round-trip: export instruments then read them back.
   *
   * @throws IOException if file operations fail
   * @throws DexpiXmlReaderException if parsing fails
   */
  @Test
  public void testRoundTripInstruments() throws IOException, DexpiXmlReaderException {
    ProcessSystem process = buildSeparatorProcess();
    DynamicProcessHelper helper = new DynamicProcessHelper(process);
    helper.instrumentAndControl();

    Map<String, MeasurementDeviceInterface> transmitters = helper.getTransmitters();
    Map<String, ControllerDeviceInterface> controllers = helper.getControllers();
    int expectedInstrumentCount = transmitters.size() + controllers.size();
    assertTrue(transmitters.size() > 0, "Should have at least 1 transmitter");

    // Export
    File tempFile = File.createTempFile("dexpi-roundtrip", ".xml");
    tempFile.deleteOnExit();
    helper.exportDexpi(tempFile);

    // Import
    List<DexpiInstrumentInfo> instruments = helper.readDexpiInstruments(tempFile);
    assertNotNull(instruments);
    assertEquals(expectedInstrumentCount, instruments.size(),
        "Round-trip should preserve instrument count");
  }

  /**
   * Tests the DexpiInstrumentInfo data class.
   */
  @Test
  public void testDexpiInstrumentInfoDataClass() {
    DexpiInstrumentInfo info = new DexpiInstrumentInfo("PIF-1", "PICSA 4712.02", "P", "ICSA",
        "4712.02", "Loop-1", "bara", "PV-4712.02");

    assertEquals("PIF-1", info.getId());
    assertEquals("PICSA 4712.02", info.getTagName());
    assertEquals("P", info.getCategory());
    assertEquals("ICSA", info.getFunctions());
    assertEquals("4712.02", info.getInstrumentNumber());
    assertEquals("Loop-1", info.getLoopNumber());
    assertEquals("bara", info.getMeasurementUnit());
    assertEquals("PV-4712.02", info.getActuatingTag());
    assertTrue(info.hasControlFunction());
    assertTrue(info.isInLoop());
    assertTrue(info.toString().contains("PICSA 4712.02"));
  }

  /**
   * Tests DexpiInstrumentInfo without control or loop.
   */
  @Test
  public void testDexpiInstrumentInfoNoControlNoLoop() {
    DexpiInstrumentInfo info =
        new DexpiInstrumentInfo("TI-1", "TI 101", "T", "I", "101", null, "C", null);

    assertFalse(info.hasControlFunction(), "T+I should not have control function");
    assertFalse(info.isInLoop(), "Null loop means not in loop");
  }

  /**
   * Tests the DynamicProcessHelper exportDexpi convenience method.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void testDynamicProcessHelperExportDexpi() throws IOException {
    ProcessSystem process = buildSeparatorProcess();
    DynamicProcessHelper helper = new DynamicProcessHelper(process);
    helper.instrumentAndControl();

    File tempFile = File.createTempFile("helper-export", ".xml");
    tempFile.deleteOnExit();
    helper.exportDexpi(tempFile);

    assertTrue(tempFile.length() > 0, "Exported file should not be empty");
    String xml = new String(Files.readAllBytes(tempFile.toPath()), StandardCharsets.UTF_8);
    assertTrue(xml.contains("PlantModel"));
    assertTrue(xml.contains("ProcessInstrumentationFunction"));
  }

  // --- Helper methods ---

  private ProcessSystem buildSeparatorProcess() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-heptane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setPressure(50.0, "bara");
    feed.setTemperature(25.0, "C");

    Separator sep = new Separator("HP sep", feed);

    ThrottlingValve gasValve = new ThrottlingValve("Gas valve", sep.getGasOutStream());
    gasValve.setOutletPressure(20.0);

    ThrottlingValve liqValve = new ThrottlingValve("Liq valve", sep.getLiquidOutStream());
    liqValve.setOutletPressure(5.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.add(gasValve);
    process.add(liqValve);
    process.run();
    return process;
  }

  private String buildInstrumentXml() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>"
        + "  <ProcessInstrumentationFunction ComponentClass=\"ProcessInstrumentationFunction\""
        + "    ID=\"PIF-4712\">" + "    <GenericAttributes>"
        + "      <GenericAttribute Name=\"ProcessInstrumentationFunctionCategoryAssignmentClass\""
        + "        Value=\"P\" />"
        + "      <GenericAttribute Name=\"ProcessInstrumentationFunctionsAssignmentClass\""
        + "        Value=\"ICSA\" />"
        + "      <GenericAttribute Name=\"ProcessInstrumentationFunctionNumberAssignmentClass\""
        + "        Value=\"4712.02\" />" + "    </GenericAttributes>"
        + "  </ProcessInstrumentationFunction>" + "</PlantModel>";
  }

  private String buildInstrumentWithLoopXml() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>"
        + "  <ProcessInstrumentationFunction ComponentClass=\"ProcessInstrumentationFunction\""
        + "    ID=\"PIF-4712\">" + "    <GenericAttributes>"
        + "      <GenericAttribute Name=\"ProcessInstrumentationFunctionCategoryAssignmentClass\""
        + "        Value=\"P\" />"
        + "      <GenericAttribute Name=\"ProcessInstrumentationFunctionsAssignmentClass\""
        + "        Value=\"IC\" />"
        + "      <GenericAttribute Name=\"ProcessInstrumentationFunctionNumberAssignmentClass\""
        + "        Value=\"4712\" />" + "    </GenericAttributes>"
        + "  </ProcessInstrumentationFunction>"
        + "  <InstrumentationLoopFunction ComponentClass=\"InstrumentationLoopFunction\""
        + "    ID=\"Loop-1\">" + "    <GenericAttributes>"
        + "      <GenericAttribute Name=\"InstrumentationLoopFunctionNumberAssignmentClass\""
        + "        Value=\"4712\" />" + "    </GenericAttributes>"
        + "    <Association Type=\"is a collection including\" ItemID=\"PIF-4712\" />"
        + "  </InstrumentationLoopFunction>" + "</PlantModel>";
  }
}
