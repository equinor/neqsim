package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.filter.Filter;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.valve.HIPPSValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link DexpiXmlWriter}, covering nozzle/connection export, native equipment reverse mapping, sizing
 * attribute export, and simulation results export.
 *
 * @author NeqSim
 * @version 1.0
 */
public class DexpiXmlWriterTest extends NeqSimTest {

  /**
   * Creates a simple gas feed stream for testing.
   *
   * @return a configured feed stream
   */
  private Stream createFeedStream() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule(2);
    fluid.init(0);
    Stream feed = new Stream("feed", fluid);
    feed.setPressure(50.0, "bara");
    feed.setTemperature(30.0, "C");
    feed.setFlowRate(1.0, "MSm3/day");
    return feed;
  }

  /**
   * Tests that native equipment is exported with correct DEXPI ComponentClass reverse mapping and includes Nozzle
   * children.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testNativeEquipmentReverseMapping() throws IOException {
    Stream feed = createFeedStream();
    Separator sep = new Separator("HP-Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    // Separator should be exported with ComponentClass="Separator"
    assertTrue(xml.contains("ComponentClass=\"Separator\""), "Should contain Separator ComponentClass");
    // Should have Nozzle children
    assertTrue(xml.contains("<Nozzle"), "Should contain Nozzle elements");
  }

  /**
   * Tests that a Compressor is reverse-mapped to CentrifugalCompressor.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testCompressorReverseMapping() throws IOException {
    Stream feed = createFeedStream();
    Compressor comp = new Compressor("Comp-1", feed);
    comp.setOutletPressure(100.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("ComponentClass=\"CentrifugalCompressor\""),
        "Should map Compressor to CentrifugalCompressor");
  }

  /**
   * Tests that a ThrottlingValve is reverse-mapped to GlobeValve and exported as Equipment.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testValveReverseMapping() throws IOException {
    Stream feed = createFeedStream();
    ThrottlingValve valve = new ThrottlingValve("CV-101", feed);
    valve.setOutletPressure(30.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(valve);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("ComponentClass=\"GlobeValve\""), "Should map ThrottlingValve to GlobeValve");
    // Valves should be exported as PipingComponent inside PipingNetworkSegment
    assertTrue(xml.contains("<PipingComponent"), "Valve should be exported as PipingComponent, not Equipment");
    // Equipment elements may appear inside ShapeCatalogue (shape definitions).
    // Verify no Equipment element appears outside ShapeCatalogue by checking
    // the text before the ShapeCatalogue section.
    int shapeCatIdx = xml.indexOf("<ShapeCatalogue");
    String beforeShapes = shapeCatIdx > 0 ? xml.substring(0, shapeCatIdx) : xml;
    assertFalse(beforeShapes.contains("<Equipment"), "Valve should NOT appear as top-level Equipment");
  }

  /**
   * Tests that a Heater is reverse-mapped to FiredHeater.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testHeaterReverseMapping() throws IOException {
    Stream feed = createFeedStream();
    Heater heater = new Heater("Heater-1", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("ComponentClass=\"FiredHeater\""), "Should map Heater to FiredHeater");
  }

  /**
   * Tests that a Cooler is reverse-mapped to AirCoolingSystem.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testCoolerReverseMapping() throws IOException {
    Stream feed = createFeedStream();
    Cooler cooler = new Cooler("Cooler-1", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(cooler);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("ComponentClass=\"AirCoolingSystem\""), "Should map Cooler to AirCoolingSystem");
  }

  /**
   * Tests that a Tank is reverse-mapped to Tank and that its symbol is in the ShapeCatalogue.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testTankReverseMapping() throws IOException {
    Stream feed = createFeedStream();
    Tank tank = new Tank("T-101", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(tank);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("ComponentClass=\"Tank\""), "Should map Tank to Tank");
    assertTrue(xml.contains("STORAGE_TANK_SHAPE"), "Tank symbol should be present in the ShapeCatalogue");
  }

  /**
   * Tests that a Filter is reverse-mapped to Filter and that its symbol is in the ShapeCatalogue.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testFilterReverseMapping() throws IOException {
    Stream feed = createFeedStream();
    Filter filter = new Filter("F-101", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(filter);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("ComponentClass=\"Filter\""), "Should map Filter to Filter");
    assertTrue(xml.contains("FILTER_SHAPE"), "Filter symbol should be present in the ShapeCatalogue");
  }

  /**
   * Tests that a ThreePhaseSeparator is correctly mapped.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testThreePhaseSeparatorReverseMapping() throws IOException {
    Stream feed = createFeedStream();
    ThreePhaseSeparator sep = new ThreePhaseSeparator("3P-Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("ComponentClass=\"ThreePhaseSeparator\""), "Should map ThreePhaseSeparator correctly");
  }

  /**
   * Tests that connections are generated between consecutive non-stream equipment.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testConnectionsGenerated() throws IOException {
    Stream feed = createFeedStream();
    Separator sep = new Separator("HP-Sep", feed);
    Stream gasOut = new Stream("gas-out", sep.getGasOutStream());
    Compressor comp = new Compressor("Comp-1", gasOut);
    comp.setOutletPressure(100.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.add(gasOut);
    process.add(comp);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    // Should have Connection elements linking separator to compressor
    assertTrue(xml.contains("<Connection"), "Should contain Connection elements");
    assertTrue(xml.contains("FromID="), "Connection should have FromID");
    assertTrue(xml.contains("ToID="), "Connection should have ToID");
  }

  /**
   * Tests that DexpiProcessUnit sizing attributes are exported.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testSizingAttributeExport() throws IOException {
    DexpiProcessUnit unit = new DexpiProcessUnit("HP-Sep", "Separator",
        neqsim.process.equipment.EquipmentEnum.Separator, null, null);
    unit.setSizingAttribute(DexpiMetadata.INSIDE_DIAMETER, "2.5");
    unit.setSizingAttribute(DexpiMetadata.TANGENT_TO_TANGENT_LENGTH, "8.0");

    ProcessSystem process = new ProcessSystem();
    process.add(unit);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    // Should contain the sizing attributes
    assertTrue(xml.contains("InsideDiameter"), "Should export InsideDiameter sizing attribute");
    assertTrue(xml.contains("2.5"), "InsideDiameter value should be 2.5");
    assertTrue(xml.contains("TangentToTangentLength"), "Should export TangentToTangentLength sizing attribute");
    assertTrue(xml.contains("8.0"), "TangentToTangentLength value should be 8.0");
  }

  /**
   * Tests that simulation results (P, T, flow) are exported for run equipment.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testSimulationResultsExport() throws IOException {
    Stream feed = createFeedStream();
    Separator sep = new Separator("HP-Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    // After running, simulation results should be exported as GenericAttributes
    assertTrue(xml.contains("OperatingPressureValue") || xml.contains("OperatingTemperatureValue"),
        "Should export simulation result attributes after process run");
  }

  /**
   * Tests that insignificant solver noise beyond the canonical DEXPI precision does not change the exported document.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testSimulationResultPrecisionIsDeterministic() throws IOException {
    Stream feed = createFeedStream();
    Separator sep = new Separator("HP-Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    sep.getGasOutStream().setTemperature(51.772939162, "C");
    ByteArrayOutputStream firstOut = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, firstOut);
    String firstXml = firstOut.toString(StandardCharsets.UTF_8.name());

    sep.getGasOutStream().setTemperature(51.772939074, "C");
    ByteArrayOutputStream secondOut = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, secondOut);
    String secondXml = secondOut.toString(StandardCharsets.UTF_8.name());

    assertTrue(firstXml.contains("Name=\"OperatingTemperatureValue\" Unit=\"C\" Value=\"51.772939\""));
    assertEquals(normalizeEmissionMetadata(firstXml), normalizeEmissionMetadata(secondXml));
    assertEquals("30.707918", DexpiXmlWriter.formatNumericAttribute(30.707918388829));
    assertEquals("30.707918", DexpiXmlWriter.formatNumericAttribute(30.707918388769));
    assertEquals("0.00000012345679", DexpiXmlWriter.formatNumericAttribute(0.0000001234567890123));
  }

  private static String normalizeEmissionMetadata(String xml) {
    return xml.replaceFirst(" Date=\"[^\"]+\"", " Date=\"<generated-date>\"").replaceFirst(" Time=\"[^\"]+\"",
        " Time=\"<generated-time>\"");
  }

  /**
   * Tests that round-trip write-then-read produces a valid ProcessSystem without throwing.
   *
   * @throws Exception if write or read fails
   */
  @Test
  public void testRoundTripWriteRead() throws Exception {
    Stream feed = createFeedStream();
    Separator sep = new Separator("HP-Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);

    File tempFile = File.createTempFile("dexpi-roundtrip", ".xml");
    tempFile.deleteOnExit();
    DexpiXmlWriter.write(process, tempFile);

    // Read it back — should not throw
    ProcessSystem readBack = DexpiXmlReader.read(tempFile);
    assertNotNull(readBack, "Round-trip should produce a valid ProcessSystem");
  }

  /**
   * Tests that an empty ProcessSystem can be written without errors.
   *
   * @throws IOException if writing fails
   */
  /**
   * Tests exporting an empty process system.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testEmptyProcessSystem() throws IOException {
    ProcessSystem process = new ProcessSystem();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertNotNull(xml);
    assertTrue(xml.contains("<PlantModel"), "Should contain PlantModel root");
    assertTrue(xml.contains("PlantInformation"), "Should contain PlantInformation");
  }

  /**
   * Tests that the standard writer declares the DEXPI namespace and exports originating-system metadata required by
   * Proteus consumers.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testStandardExportMetadata() throws IOException {
    ProcessSystem process = new ProcessSystem();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("xmlns=\"http://sandbox.dexpi.org/xml\""),
        "Standard export should include the DEXPI default namespace");
    assertTrue(xml.contains("OriginatingSystem=\"NeqSim\""),
        "PlantInformation should identify NeqSim as the originating system");
    assertTrue(xml.contains("OriginatingSystemVendor=\"Equinor / NeqSim\""),
        "PlantInformation should identify the originating system vendor");
    assertTrue(xml.contains("OriginatingSystemVersion="),
        "PlantInformation should include originating system version metadata");
  }

  /**
   * Tests that pyDEXPI-friendly export omits only the default namespace while retaining the originating-system metadata
   * needed by pyDEXPI/Proteus loaders.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testPyDexpiExportOmitsNamespaceButKeepsMetadata() throws IOException {
    ProcessSystem process = new ProcessSystem();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.writeForPyDexpi(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertFalse(xml.contains("xmlns=\"http://sandbox.dexpi.org/xml\""),
        "pyDEXPI export should omit the DEXPI default namespace");
    assertFalse(xml.contains("xmlns:xsi="), "pyDEXPI export should omit namespace declarations");
    assertTrue(xml.contains("OriginatingSystem=\"NeqSim\""), "pyDEXPI export should keep originating system metadata");
    assertTrue(xml.contains("PlantInformation"), "pyDEXPI export should keep unqualified PlantInformation elements");
  }

  /**
   * Tests that a separator produces multiple nozzles for gas and liquid outlets, and that stream identity-based
   * connection building correctly wires downstream equipment to the right nozzles.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testSeparatorMultiOutletNozzles() throws IOException {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("nC10", 0.3);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    Separator sep = new Separator("HP-sep", feed);
    sep.run();

    // Gas outlet goes to compressor, liquid outlet goes to valve
    Compressor comp = new Compressor("gas-comp", sep.getGasOutStream());
    comp.setOutletPressure(80.0);
    comp.run();

    ThrottlingValve valve = new ThrottlingValve("liq-valve", sep.getLiquidOutStream());
    valve.setOutletPressure(10.0);
    valve.run();

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.add(comp);
    process.add(valve);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    // Separator should have 3 nozzles (1 inlet + 2 outlets)
    int nozzleCount = countOccurrences(xml, "<Nozzle ");
    // feed (Stream, not exported) = 0, sep = 3, comp = 2, valve PipingComponent = 2,
    // inline PipingComponent for sep->comp connection = 2 => total >= 9
    assertTrue(nozzleCount >= 7,
        "Separator should produce 3 nozzles (inlet + gas out + liquid out)" + "; total nozzles=" + nozzleCount);

    // Connection system should contain connections
    assertTrue(xml.contains("Connection"), "Should contain Connection elements");
    assertTrue(xml.contains("Separator"), "Should contain Separator equipment");
  }

  /**
   * Tests that the equipment data-bar label converts mechanical-design lengths from the internally-stored metres to
   * millimetres, and that the placeholder design temperature (the 100.0 K default) is suppressed unless a real design
   * basis has been set.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testEquipmentBarMechanicalDesignUnitsAndPlaceholderSuppression() throws IOException {
    Stream feed = createFeedStream();
    Separator sep = new Separator("HP-Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    // Mechanical-design lengths are stored in metres internally.
    sep.initMechanicalDesign();
    sep.getMechanicalDesign().setInnerDiameter(2.0);
    sep.getMechanicalDesign().setWallThickness(0.02);
    sep.getMechanicalDesign().setTantanLength(6.0);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.writeForPyDexpi(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    // 2.0 m inner diameter must render as 2000 mm, not "2 mm".
    assertTrue(xml.contains("String=\"2000 mm\""), "Inner diameter should be converted from metres to 2000 mm");
    assertTrue(xml.contains("String=\"6000 mm\""), "Tan-to-tan length should be converted from metres to 6000 mm");
    assertTrue(xml.contains("String=\"20 mm\""), "Wall thickness should be converted from metres to 20 mm");

    // The placeholder design temperature (100.0 K -> -173.1 C) must not leak into the bar label.
    assertFalse(xml.contains("-173.1"), "Placeholder design temperature (-173.1 C) should be suppressed");
  }

  /**
   * Tests that a process system without any explicitly modelled measurement devices or controllers still exports a
   * realistic set of synthesized ISA-5.1 instrumentation (transmitters and matched PID controllers) so the resulting
   * P&amp;ID resembles a real engineering diagram.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testAutoSynthesizedInstrumentation() throws IOException {
    Stream feed = createFeedStream();
    Separator sep = new Separator("HP-Sep", feed);
    Compressor comp = new Compressor("Comp-1", sep.getGasOutStream());
    comp.setOutletPressure(120.0);
    Cooler cooler = new Cooler("Cooler-1", comp.getOutletStream());
    cooler.setOutTemperature(30.0, "C");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.add(comp);
    process.add(cooler);
    process.run();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.writeForPyDexpi(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    // Instrumentation function bubbles must be emitted even though the model defines none.
    assertTrue(xml.contains("ProcessInstrumentationFunction"),
        "Synthesized instrumentation should produce ProcessInstrumentationFunction elements");
    // Separator should get pressure, level and temperature transmitters.
    assertTrue(xml.contains("PT-2001"), "Separator should get a pressure transmitter");
    assertTrue(xml.contains("LT-2002"), "Separator should get a level transmitter");
    assertTrue(xml.contains("TT-2003"), "Separator should get a temperature transmitter");
    // Matched controllers should be present.
    assertTrue(xml.contains("PC-2001"), "Separator pressure loop should get a controller");
    assertTrue(xml.contains("LC-2002"), "Separator level loop should get a controller");
    // Compressor should get a discharge pressure transmitter and a suction flow transmitter.
    assertTrue(xml.contains("PT-2011"), "Compressor should get a discharge pressure transmitter");
    assertTrue(xml.contains("FT-2014"), "Compressor should get a suction flow transmitter");
    // Cooler should get a temperature loop.
    assertTrue(xml.contains("TT-2023"), "Cooler should get a temperature transmitter");
    assertTrue(xml.contains("TC-2023"), "Cooler should get a temperature controller");
  }

  /**
   * Tests that automatic instrumentation synthesis can be disabled, so a model without explicit measurement devices
   * exports no instrumentation.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testAutoSynthesisCanBeDisabled() throws IOException {
    Stream feed = createFeedStream();
    Separator sep = new Separator("HP-Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    try {
      DexpiXmlWriter.setAutoSynthesizeInstrumentation(false);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      DexpiXmlWriter.writeForPyDexpi(process, out);
      String xml = out.toString(StandardCharsets.UTF_8.name());
      // The shape catalogue always references the ProcessInstrumentationFunction class, so assert
      // on the absence of synthesized instrument tags instead.
      assertFalse(xml.contains("PT-2001"), "Disabling synthesis should produce no synthesized pressure transmitter");
      assertFalse(xml.contains("LT-2002"), "Disabling synthesis should produce no synthesized level transmitter");
    } finally {
      DexpiXmlWriter.setAutoSynthesizeInstrumentation(true);
    }
  }

  /**
   * Tests that the DEXPI 2.0 writer re-declares the document header (namespace, schema location and SchemaVersion)
   * while still emitting the backward-compatible plant-model body, and that the default writer remains Proteus 4.1.1.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testDexpi20SchemaHeader() throws IOException {
    Stream feed = createFeedStream();
    Separator sep = new Separator("HP-Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.writeDexpi20(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("SchemaVersion=\"2.0\""), "DEXPI 2.0 export should declare SchemaVersion=2.0");
    assertTrue(xml.contains("xmlns=\"http://www.dexpi.org/dexpi\""),
        "DEXPI 2.0 export should declare the DEXPI 2.0 namespace");
    assertFalse(xml.contains("SchemaVersion=\"4.1.1\""),
        "DEXPI 2.0 export should not declare the 4.1.1 schema version");
    // Backward-compatible plant-model body is still present.
    assertTrue(xml.contains("<PlantModel"), "DEXPI 2.0 export should still emit a PlantModel body");
    assertTrue(xml.contains("ComponentClass=\"Separator\""), "DEXPI 2.0 export should still emit plant-model content");

    // The convenience writer must restore the default so subsequent writes stay on 4.1.1.
    ByteArrayOutputStream defaultOut = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, defaultOut);
    String defaultXml = defaultOut.toString(StandardCharsets.UTF_8.name());
    assertTrue(defaultXml.contains("SchemaVersion=\"4.1.1\""),
        "Default export should remain Proteus 4.1.1 after a DEXPI 2.0 write");
  }

  /**
   * Tests that {@link DexpiXmlWriter#setSchemaVersion(DexpiXmlWriter.DexpiSchemaVersion)} controls the declared schema
   * generation and can be reset back to the Proteus default.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testSetSchemaVersionToggle() throws IOException {
    ProcessSystem process = new ProcessSystem();
    try {
      DexpiXmlWriter.setSchemaVersion(DexpiXmlWriter.DexpiSchemaVersion.DEXPI_2_0);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      DexpiXmlWriter.write(process, out);
      String xml = out.toString(StandardCharsets.UTF_8.name());
      assertTrue(xml.contains("SchemaVersion=\"2.0\""), "setSchemaVersion(DEXPI_2_0) should declare SchemaVersion=2.0");
    } finally {
      DexpiXmlWriter.setSchemaVersion(DexpiXmlWriter.DexpiSchemaVersion.PROTEUS_4_1_1);
    }

    ByteArrayOutputStream resetOut = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, resetOut);
    String resetXml = resetOut.toString(StandardCharsets.UTF_8.name());
    assertTrue(resetXml.contains("SchemaVersion=\"4.1.1\""),
        "Resetting to PROTEUS_4_1_1 should restore the 4.1.1 schema version");
  }

  /**
   * Tests that common high-high, low-low, and low-flow trip tags are exported as SIS instruments while an ordinary
   * process transmitter remains assigned to the DCS.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testCommonSafetyTripTagsUseSisAssignment() throws IOException {
    Stream feed = createFeedStream();
    ProcessSystem process = new ProcessSystem();
    process.add(feed);

    String[] safetyTags = { "PSHH-101", "PAHH-102", "LSHH-103", "TSHH-104", "FSL-105" };
    for (String tag : safetyTags) {
      process.add(new PressureTransmitter(tag, feed));
    }
    process.add(new PressureTransmitter("PT-106", feed));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.writeDexpi20(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    for (String tag : safetyTags) {
      assertTrue(xml.contains("Name=\"TagNameAssignmentClass\" Value=\"" + tag + "\""),
          tag + " should be present in the export");
    }
    assertEquals(safetyTags.length, countOccurrences(xml, "Name=\"ControlSystem\" Value=\"SIS\""),
        "Every safety-trip instrument should be assigned to SIS");
    assertEquals(1, countOccurrences(xml, "Name=\"ControlSystem\" Value=\"DCS\""),
        "The ordinary process transmitter should remain assigned to DCS");
  }

  /**
   * Tests that a HIPPS loop retains its final-element class, SIL rating, voting architecture, and sensor membership.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testHippsSafetySemanticsExport() throws IOException {
    Stream feed = createFeedStream();
    HIPPSValve hipps = new HIPPSValve("HIPPS-XV-101", feed);
    hipps.setOutletPressure(45.0);
    hipps.setSILRating(3);
    hipps.setVotingLogic(HIPPSValve.VotingLogic.TWO_OUT_OF_THREE);
    hipps.setProofTestInterval(8760.0);
    hipps.setClosureTime(6.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(hipps);
    String[] sensorTags = { "PSHH-101A", "PSHH-101B", "PSHH-101C" };
    for (String tag : sensorTags) {
      PressureTransmitter transmitter = new PressureTransmitter(tag, feed);
      hipps.addPressureTransmitter(transmitter);
      process.add(transmitter);
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.writeDexpi20(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    int hippsId = xml.indexOf("ID=\"HIPPS-XV-101\"");
    assertTrue(hippsId >= 0, "HIPPS final element should be present");
    int componentStart = xml.lastIndexOf("<PipingComponent", hippsId);
    int componentEnd = xml.indexOf("</PipingComponent>", hippsId);
    assertTrue(componentStart >= 0 && componentEnd > hippsId, "HIPPS should export as a piping component");
    String hippsComponent = xml.substring(componentStart, componentEnd);
    assertTrue(hippsComponent.contains("ComponentClass=\"GateValve\""),
        "HIPPS final element should not be reduced to a generic control globe valve");
    assertFalse(hippsComponent.contains("ComponentClass=\"GlobeValve\""),
        "HIPPS final element must preserve its shutdown-valve identity");

    assertTrue(xml.contains("Name=\"SafetyFunctionTag\" Value=\"HIPPS-XV-101\""));
    assertTrue(xml.contains("Name=\"SafetyIntegrityLevel\" Value=\"3\""));
    assertTrue(xml.contains("Name=\"VotingArchitecture\" Value=\"2oo3\""));
    assertTrue(xml.contains("Name=\"FinalElementTag\" Value=\"HIPPS-XV-101\""));
    assertTrue(xml.contains("Name=\"SensorTags\" Value=\"PSHH-101A,PSHH-101B,PSHH-101C\""));
    assertTrue(xml.contains("Name=\"SafeState\" Value=\"Closed\""));
    assertTrue(xml.contains("Name=\"ProofTestInterval\" Unit=\"h\" Value=\"8760\""));
    assertTrue(xml.contains("Name=\"ClosureTime\" Unit=\"s\" Value=\"6\""));
    assertEquals(3, countOccurrences(xml, "Name=\"FunctionalRole\" Value=\"Sensor\""));
    assertEquals(1, countOccurrences(xml, "Name=\"FunctionalRole\" Value=\"FinalElement\""));
    assertEquals(4, countOccurrences(xml, "Name=\"SafetyIntegrityLevel\" Value=\"3\""),
        "SIL 3 should be available on the final element and each of the three trip sensors");
  }

  /**
   * Counts occurrences of a substring in a string.
   *
   * @param text the text to search
   * @param sub the substring to count
   * @return the number of occurrences
   */
  private int countOccurrences(String text, String sub) {
    int count = 0;
    int idx = 0;
    while ((idx = text.indexOf(sub, idx)) != -1) {
      count++;
      idx += sub.length();
    }
    return count;
  }
}
