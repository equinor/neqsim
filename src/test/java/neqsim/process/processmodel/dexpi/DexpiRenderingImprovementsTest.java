package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import neqsim.NeqSimTest;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the DEXPI rendering improvements: orthogonal pipe-routing that places the vertical
 * riser near the target column to reduce line crossings, and presence of the standard ISO 10628
 * symbol stencils in the {@code <ShapeCatalogue>}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class DexpiRenderingImprovementsTest extends NeqSimTest {

  /**
   * Horizontal approach distance (mm) used by {@code DexpiLayoutEngine.computeBranchRiserX}. Must
   * match the {@code BRANCH_APPROACH} constant in the layout engine.
   */
  private static final double BRANCH_APPROACH = 18.0;

  /**
   * Creates an empty DOM document for direct layout-engine unit tests.
   *
   * @return a new empty XML document
   * @throws Exception if the document builder cannot be created
   */
  private Document newDocument() throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.newDocument();
  }

  /**
   * Reads the X coordinate of the vertical riser (second point) from a routed CenterLine.
   *
   * @param parent the element that received the appended CenterLine
   * @return the riser X coordinate
   */
  private double riserXOf(Element parent) {
    NodeList centerLines = parent.getElementsByTagName("CenterLine");
    assertEquals(1, centerLines.getLength(), "Expected exactly one CenterLine");
    Element centerLine = (Element) centerLines.item(0);
    NodeList coords = centerLine.getElementsByTagName("Coordinate");
    assertTrue(coords.getLength() >= 3, "Orthogonal route must have at least 3 points");
    // Point index 1 is the start of the vertical riser.
    return Double.parseDouble(((Element) coords.item(1)).getAttribute("X"));
  }

  /**
   * A forward branch with horizontal room places its riser one approach distance before the target,
   * not at the segment midpoint. This keeps the long horizontal run on the source line.
   *
   * @throws Exception if XML construction fails
   */
  @Test
  public void testRiserPlacedNearTargetForForwardBranch() throws Exception {
    Document doc = newDocument();
    Element parent = doc.createElement("PipingNetworkSegment");
    doc.appendChild(parent);

    double fromX = 80.0;
    double toX = 280.0;
    DexpiLayoutEngine.appendConnectionLine(doc, parent, fromX, 150.0, toX, 90.0);

    double riserX = riserXOf(parent);
    assertEquals(toX - BRANCH_APPROACH, riserX, 1e-6,
        "Riser should sit one approach distance before the target");
  }

  /**
   * Two branches to targets in different columns get distinct riser channels, which is what reduces
   * line crossings on multi-branch diagrams (e.g. a three-phase separator).
   *
   * @throws Exception if XML construction fails
   */
  @Test
  public void testDistinctRisersForDifferentTargetColumns() throws Exception {
    Document doc = newDocument();

    Element gasBranch = doc.createElement("PipingNetworkSegment");
    Element oilBranch = doc.createElement("PipingNetworkSegment");
    Element waterBranch = doc.createElement("PipingNetworkSegment");
    Element root = doc.createElement("root");
    root.appendChild(gasBranch);
    root.appendChild(oilBranch);
    root.appendChild(waterBranch);
    doc.appendChild(root);

    // Three downstream units in three distinct columns.
    DexpiLayoutEngine.appendConnectionLine(doc, gasBranch, 80.0, 150.0, 280.0, 90.0);
    DexpiLayoutEngine.appendConnectionLine(doc, oilBranch, 80.0, 150.0, 380.0, 150.0);
    DexpiLayoutEngine.appendConnectionLine(doc, waterBranch, 80.0, 150.0, 480.0, 210.0);

    double gasRiser = riserXOf(gasBranch);
    double waterRiser = riserXOf(waterBranch);

    // Gas and water targets are in different columns, so their risers must differ.
    assertTrue(Math.abs(gasRiser - waterRiser) > 1.0,
        "Risers for different target columns must be distinct to avoid overlap");
    assertEquals(280.0 - BRANCH_APPROACH, gasRiser, 1e-6);
    assertEquals(480.0 - BRANCH_APPROACH, waterRiser, 1e-6);
  }

  /**
   * A short or backward (recycle) connection with no horizontal room falls back to the segment
   * midpoint so the riser stays inside the available span.
   *
   * @throws Exception if XML construction fails
   */
  @Test
  public void testRecycleConnectionFallsBackToMidpoint() throws Exception {
    Document doc = newDocument();
    Element parent = doc.createElement("PipingNetworkSegment");
    doc.appendChild(parent);

    double fromX = 300.0;
    double toX = 120.0; // target is upstream (backward / recycle)
    DexpiLayoutEngine.appendConnectionLine(doc, parent, fromX, 150.0, toX, 90.0);

    double riserX = riserXOf(parent);
    assertEquals((fromX + toX) / 2.0, riserX, 1e-6,
        "Backward connection should fall back to the segment midpoint");
  }

  /**
   * The static ShapeCatalogue must always contain the standard ISO 10628 / ISA-5.1 stencils, so a
   * future fall-through (e.g. a new equipment type silently rendered as a box) is caught.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testShapeCatalogueContainsStandardSymbols() throws IOException {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule(2);
    fluid.init(0);
    Stream feed = new Stream("feed", fluid);
    feed.setPressure(50.0, "bara");
    feed.setTemperature(30.0, "C");
    feed.setFlowRate(1.0, "MSm3/day");

    ThreePhaseSeparator sep = new ThreePhaseSeparator("Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertNotNull(xml);
    List<String> expectedShapes = new ArrayList<String>();
    expectedShapes.add(DexpiShapeCatalog.SEPARATOR_SHAPE);
    expectedShapes.add(DexpiShapeCatalog.THREE_PHASE_SEPARATOR_SHAPE);
    expectedShapes.add(DexpiShapeCatalog.COMPRESSOR_SHAPE);
    expectedShapes.add(DexpiShapeCatalog.PUMP_SHAPE);
    expectedShapes.add(DexpiShapeCatalog.COOLER_SHAPE);
    expectedShapes.add(DexpiShapeCatalog.HEATER_SHAPE);
    expectedShapes.add(DexpiShapeCatalog.HEAT_EXCHANGER_SHAPE);
    expectedShapes.add(DexpiShapeCatalog.TANK_SHAPE);
    expectedShapes.add(DexpiShapeCatalog.FILTER_SHAPE);
    expectedShapes.add(DexpiShapeCatalog.GLOBE_VALVE_SHAPE);
    expectedShapes.add(DexpiShapeCatalog.EXPANDER_SHAPE);
    expectedShapes.add(DexpiShapeCatalog.MIXER_SHAPE);
    expectedShapes.add(DexpiShapeCatalog.SPLITTER_SHAPE);
    expectedShapes.add(DexpiShapeCatalog.DISTILLATION_COLUMN_SHAPE);
    expectedShapes.add(DexpiShapeCatalog.INSTRUMENT_BUBBLE_FIELD_SHAPE);
    expectedShapes.add(DexpiShapeCatalog.INSTRUMENT_BUBBLE_CENTRAL_SHAPE);

    for (String shape : expectedShapes) {
      assertTrue(xml.contains(shape), "ShapeCatalogue should contain symbol: " + shape);
    }
  }

  /**
   * ISA-5.1 signal line kinds must be encoded as distinct DEXPI line types so the measurement and
   * command sides of an instrument loop are visually different.
   *
   * @throws Exception if XML construction fails
   */
  @Test
  public void testSignalLineKindsUseDistinctLineTypes() throws Exception {
    Document doc = newDocument();
    Element electric = doc.createElement("InformationFlow");
    Element pneumatic = doc.createElement("InformationFlow");
    Element software = doc.createElement("InformationFlow");
    Element root = doc.createElement("root");
    root.appendChild(electric);
    root.appendChild(pneumatic);
    root.appendChild(software);
    doc.appendChild(root);

    DexpiLayoutEngine.appendSignalLine(doc, electric, 100.0, 200.0, 100.0, 150.0,
        DexpiLayoutEngine.SignalLineKind.ELECTRIC);
    DexpiLayoutEngine.appendSignalLine(doc, pneumatic, 100.0, 200.0, 100.0, 150.0,
        DexpiLayoutEngine.SignalLineKind.PNEUMATIC);
    DexpiLayoutEngine.appendSignalLine(doc, software, 100.0, 200.0, 100.0, 150.0,
        DexpiLayoutEngine.SignalLineKind.SOFTWARE);

    String electricType = lineTypeOf(electric, "CenterLine");
    String pneumaticType = lineTypeOf(pneumatic, "CenterLine");
    String softwareType = lineTypeOf(software, "CenterLine");

    assertTrue(!electricType.equals(pneumaticType),
        "Electric and pneumatic signals must use different line types");
    assertTrue(!pneumaticType.equals(softwareType),
        "Pneumatic and software signals must use different line types");
  }

  /**
   * ISO 15519-1 line-weight hierarchy: main process lines must be heavier than utility lines.
   */
  @Test
  public void testServiceLineWeightHierarchy() {
    double mainWeight = DexpiServiceClassifier.ServiceType.MAIN_PROCESS.getLineWeight();
    double secondaryWeight = DexpiServiceClassifier.ServiceType.SECONDARY_PROCESS.getLineWeight();
    double utilityWeight = DexpiServiceClassifier.ServiceType.UTILITY.getLineWeight();

    assertTrue(mainWeight > secondaryWeight, "Main process must be heavier than secondary process");
    assertTrue(secondaryWeight > utilityWeight, "Secondary process must be heavier than utility");
  }

  /**
   * ISO 10628-2 service styles: streams named as flare, drain and utility get non-solid line types,
   * while process streams stay solid.
   */
  @Test
  public void testServiceClassificationByName() {
    assertEquals(DexpiServiceClassifier.ServiceType.MAIN_PROCESS,
        DexpiServiceClassifier.classifyByName("export gas"));
    assertEquals(DexpiServiceClassifier.ServiceType.FLARE,
        DexpiServiceClassifier.classifyByName("HP flare header"));
    assertEquals(DexpiServiceClassifier.ServiceType.DRAIN,
        DexpiServiceClassifier.classifyByName("closed drain"));
    assertEquals(DexpiServiceClassifier.ServiceType.UTILITY,
        DexpiServiceClassifier.classifyByName("LP steam supply"));
    assertEquals(DexpiServiceClassifier.ServiceType.FUEL_GAS,
        DexpiServiceClassifier.classifyByName("fuel gas to turbine"));
    // Process lines remain solid (line type 0).
    assertEquals(0, DexpiServiceClassifier.ServiceType.MAIN_PROCESS.getLineType());
    // Utility lines are dashed (line type 1).
    assertEquals(1, DexpiServiceClassifier.ServiceType.UTILITY.getLineType());
  }

  /**
   * A styled service connection line carries the service line type and weight.
   *
   * @throws Exception if XML construction fails
   */
  @Test
  public void testServiceConnectionLineCarriesStyle() throws Exception {
    Document doc = newDocument();
    Element parent = doc.createElement("PipingNetworkSegment");
    doc.appendChild(parent);

    DexpiLayoutEngine.appendServiceConnectionLine(doc, parent, 80.0, 150.0, 280.0, 150.0,
        DexpiServiceClassifier.ServiceType.UTILITY);

    NodeList polys = parent.getElementsByTagName("PolyLine");
    assertEquals(1, polys.getLength());
    Element pres = (Element) ((Element) polys.item(0)).getElementsByTagName("Presentation").item(0);
    assertEquals("1", pres.getAttribute("LineType"), "Utility line must be dashed");
    assertEquals(String.valueOf(DexpiServiceClassifier.ServiceType.UTILITY.getLineWeight()),
        pres.getAttribute("LineWeight"), "Utility line must use the utility weight");
  }

  /**
   * A line crossing draws a hop arc; a tee-junction (endpoint touching) does not.
   *
   * @throws Exception if XML construction fails
   */
  @Test
  public void testCrossingHopDrawnOnlyForTrueCrossing() throws Exception {
    Document doc = newDocument();
    Element crossing = doc.createElement("seg");
    Element tee = doc.createElement("seg");
    Element root = doc.createElement("root");
    root.appendChild(crossing);
    root.appendChild(tee);
    doc.appendChild(root);

    // Horizontal line y=150 from x=100..300; vertical line x=200 from y=120..180 -> crosses.
    boolean drewCrossing = DexpiLayoutEngine.appendCrossingHop(doc, crossing, 100.0, 300.0, 150.0,
        200.0, 120.0, 180.0);
    // Vertical line touches the horizontal endpoint at x=300 -> not an interior crossing.
    boolean drewTee =
        DexpiLayoutEngine.appendCrossingHop(doc, tee, 100.0, 300.0, 150.0, 300.0, 120.0, 180.0);

    assertTrue(drewCrossing, "A true crossing must draw a hop");
    assertTrue(!drewTee, "A junction at the endpoint must not draw a hop");
    assertEquals(1, crossing.getElementsByTagName("TrimmedCurve").getLength());
    assertEquals(0, tee.getElementsByTagName("TrimmedCurve").getLength());
  }

  /**
   * An off-page connector draws a closed six-point pentagon and its cross-reference label.
   *
   * @throws Exception if XML construction fails
   */
  @Test
  public void testOffPageConnectorDrawsPentagonAndReference() throws Exception {
    Document doc = newDocument();
    Element parent = doc.createElement("Drawing");
    doc.appendChild(parent);

    DexpiLayoutEngine.appendOffPageConnector(doc, parent, 400.0, 150.0, "To D-200", true);

    NodeList polys = parent.getElementsByTagName("PolyLine");
    assertEquals(1, polys.getLength());
    assertEquals("6", ((Element) polys.item(0)).getAttribute("NumPoints"),
        "Pentagon must be a closed 6-point polyline");

    NodeList texts = parent.getElementsByTagName("Text");
    boolean hasRef = false;
    for (int i = 0; i < texts.getLength(); i++) {
      if ("To D-200".equals(((Element) texts.item(i)).getAttribute("String"))) {
        hasRef = true;
      }
    }
    assertTrue(hasRef, "Off-page connector must carry its cross-reference text");
  }

  /**
   * ISA-5.1 tag validation accepts well-formed tags and rejects malformed ones.
   */
  @Test
  public void testIsaTagValidation() {
    assertTrue(IsaTagValidator.isValid("PT-101"), "PT-101 is a valid pressure transmitter tag");
    assertTrue(IsaTagValidator.isValid("FIC-204"), "FIC-204 is a valid flow controller tag");
    assertTrue(IsaTagValidator.isValid("LSHH-330"), "LSHH-330 is a valid level switch tag");
    assertTrue(IsaTagValidator.isValid("PDT 415"),
        "PDT-415 is a valid differential pressure transmitter tag");
    // Invalid: no loop number.
    assertTrue(!IsaTagValidator.isValid("PT"), "Tag with no loop number must be rejected");
    // Invalid: unknown first letter.
    assertTrue(!IsaTagValidator.isValid("@T-101"), "Tag with bad character must be rejected");
    // Description includes the measured variable.
    IsaTagValidator.ValidationResult res = IsaTagValidator.validate("PT-101");
    assertTrue(res.getMessage().contains("Pressure"),
        "Validation message should describe the measured variable");
  }

  /**
   * NORSOK Z-003 line number composes size, fluid code, sequence, piping class and insulation, and
   * omits empty fields.
   */
  @Test
  public void testNorsokLineNumberComposition() {
    String full = new NorsokLineNumber().size("6").fluidCode("PG").sequence("1001")
        .pipingClass("A1B").insulation("H25").build();
    assertEquals("6\"-PG-1001-A1B-H25", full,
        "Full NORSOK line number must concatenate all fields");

    String partial = new NorsokLineNumber().size("4\"").fluidCode("HC").sequence("200").build();
    assertEquals("4\"-HC-200", partial, "Missing fields must be omitted without trailing hyphens");

    String empty = new NorsokLineNumber().build();
    assertEquals("", empty, "An unset line number builds to an empty string");
  }

  /**
   * The exported drawing must include the ISO 7200 title-block fields (owner/originator and
   * document type / scale / sheet).
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testTitleBlockContainsIso7200Fields() throws IOException {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule(2);
    fluid.init(0);
    Stream feed = new Stream("feed", fluid);
    feed.setPressure(50.0, "bara");
    feed.setTemperature(30.0, "C");
    feed.setFlowRate(1.0, "MSm3/day");
    ThreePhaseSeparator sep = new ThreePhaseSeparator("Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("Owner:"), "Title block must include the ISO 7200 owner field");
    assertTrue(xml.contains("Scale:"), "Title block must include the ISO 7200 scale field");
    assertTrue(xml.contains("Sheet:"), "Title block must include the ISO 7200 sheet field");
  }

  /**
   * Builds a small flowsheet (feed stream into a three-phase separator whose gas outlet feeds a
   * downstream throttling valve) so genuine equipment-to-equipment wiring is present.
   *
   * @param name the process system name
   * @return a runnable process system
   */
  private ProcessSystem buildSeparatorProcess(String name) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("nC10", 0.1);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule(2);
    fluid.init(0);
    Stream feed = new Stream("feed", fluid);
    feed.setPressure(50.0, "bara");
    feed.setTemperature(30.0, "C");
    feed.setFlowRate(1.0, "MSm3/day");
    ThreePhaseSeparator sep = new ThreePhaseSeparator("Sep", feed);
    neqsim.process.equipment.valve.ThrottlingValve gasValve =
        new neqsim.process.equipment.valve.ThrottlingValve("GasValve", sep.getGasOutStream());
    gasValve.setOutletPressure(30.0, "bara");
    ProcessSystem process = new ProcessSystem();
    process.setName(name);
    process.add(feed);
    process.add(sep);
    process.add(gasValve);
    return process;
  }

  /**
   * Parses an XML string into a DOM document.
   *
   * @param xml the XML text
   * @return the parsed document
   * @throws Exception if parsing fails
   */
  private Document parseXml(String xml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Improvement #1: a whole {@link ProcessModel} exports in a single call, flattening all process
   * areas into one drawing that contains equipment from every area.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testProcessModelSingleCallExport() throws IOException {
    ProcessSystem area1 = buildSeparatorProcess("Inlet");
    ProcessSystem area2 = buildSeparatorProcess("Treating");
    area2.getUnit("Sep").setName("Sep2");

    ProcessModel plant = new ProcessModel();
    plant.add("Inlet", area1);
    plant.add("Treating", area2);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(plant, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertNotNull(xml);
    assertTrue(xml.contains("PlantModel"), "Flattened model must be a valid PlantModel drawing");
    assertTrue(xml.contains("Sep") && xml.contains("Sep2"),
        "Both process areas' equipment must appear in the single export");
  }

  /**
   * Improvement #3: the pyDEXPI-friendly writer omits the default {@code xmlns} namespace so the
   * Proteus reader can resolve unqualified element names, while still producing the PlantModel
   * root.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testWriteForPyDexpiOmitsDefaultNamespace() throws IOException {
    ProcessSystem process = buildSeparatorProcess("Inlet");

    ByteArrayOutputStream pydexpi = new ByteArrayOutputStream();
    DexpiXmlWriter.writeForPyDexpi(process, pydexpi);
    String pyXml = pydexpi.toString(StandardCharsets.UTF_8.name());

    ByteArrayOutputStream standard = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, standard);
    String stdXml = standard.toString(StandardCharsets.UTF_8.name());

    assertTrue(pyXml.contains("PlantModel"), "pyDEXPI export must still have a PlantModel root");
    assertTrue(pyXml.contains("PlantInformation"),
        "pyDEXPI export must contain the PlantInformation element");
    assertTrue(!pyXml.contains("xmlns=\"http://sandbox.dexpi.org/xml\""),
        "pyDEXPI export must omit the default xmlns namespace");
    assertTrue(stdXml.contains("xmlns=\"http://sandbox.dexpi.org/xml\""),
        "Standard export should keep the default xmlns namespace");
  }

  /**
   * Improvement #4: equipment elements carry an RDL {@code ComponentClassURI} so no symbol falls
   * through to an unclassified default.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testEquipmentComponentClassUriPresent() throws IOException {
    ProcessSystem process = buildSeparatorProcess("Inlet");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("ComponentClassURI=\"http://sandbox.dexpi.org/rdl/"),
        "Equipment must reference a DEXPI RDL component-class URI");
  }

  /**
   * Improvement #6 and #8: each piping connection carries operating line data (fluid code and
   * operating pressure) and a NORSOK line-identification label.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testConnectionCarriesLineDataAndLineNumber() throws IOException {
    ProcessSystem process = buildSeparatorProcess("Inlet");
    process.run();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains(DexpiMetadata.FLUID_CODE),
        "Connection lines must carry a FluidCode generic attribute");
    assertTrue(xml.contains(DexpiMetadata.OPERATING_PRESSURE_VALUE),
        "Connection lines must carry the operating pressure value");
    assertTrue(xml.contains("PG-0") || xml.contains("PL-0"),
        "Connection lines must carry a NORSOK line-identification label");
  }

  /**
   * Improvement #2 and #5: battery-limit feeds and products are marked with off-page connector
   * cross references (FEED / PRODUCT).
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testBoundaryOffPageConnectors() throws IOException {
    ProcessSystem process = buildSeparatorProcess("Inlet");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("FEED") || xml.contains("PRODUCT"),
        "Boundary feeds/products must be marked with off-page connector references");
  }

  /**
   * Improvement #7: a drawing written by {@link DexpiXmlWriter} can be read back by
   * {@link DexpiXmlReader} and yields a process system with at least one unit operation.
   *
   * @throws Exception if writing or reading fails
   */
  @Test
  public void testRoundTripExportThenRead() throws Exception {
    ProcessSystem process = buildSeparatorProcess("Inlet");

    java.io.File tmp = java.io.File.createTempFile("dexpi-roundtrip", ".xml");
    tmp.deleteOnExit();
    DexpiXmlWriter.writeForPyDexpi(process, tmp);

    ProcessSystem readBack = DexpiXmlReader.read(tmp);
    assertNotNull(readBack, "Reader must return a process system");
    assertTrue(readBack.getUnitOperations().size() > 0,
        "Round-tripped process must contain at least one unit operation");
  }

  /**
   * Improvement #9: the {@code DexpiDiagramBridge.exportForPyDexpi} convenience method writes a
   * pyDEXPI-friendly file (default namespace omitted) that the reader can load back.
   *
   * @throws Exception if writing, parsing or reading fails
   */
  @Test
  public void testDiagramBridgeExportForPyDexpi() throws Exception {
    ProcessSystem process = buildSeparatorProcess("Inlet");

    java.io.File tmp = java.io.File.createTempFile("dexpi-bridge", ".xml");
    tmp.deleteOnExit();
    neqsim.process.processmodel.diagram.DexpiDiagramBridge.exportForPyDexpi(process, tmp.toPath());

    String xml = new String(java.nio.file.Files.readAllBytes(tmp.toPath()), StandardCharsets.UTF_8);
    assertTrue(xml.contains("<PlantModel"), "Bridge export must produce a PlantModel root");
    assertTrue(!xml.contains("xmlns=\"http://sandbox.dexpi.org/xml\""),
        "Bridge export must omit the default DEXPI namespace for pyDEXPI");

    ProcessSystem readBack = DexpiXmlReader.read(tmp);
    assertNotNull(readBack, "Reader must load the bridge-exported file");
    assertTrue(readBack.getUnitOperations().size() > 0,
        "Bridge-exported process must contain at least one unit operation");
  }

  /**
   * Improvement #8 schema gate: the emitted drawing is well-formed XML with a PlantModel root,
   * PlantInformation metadata and at least one Equipment and one PipingNetworkSystem.
   *
   * @throws Exception if writing or parsing fails
   */
  @Test
  public void testEmittedXmlIsWellFormedAndStructurallyValid() throws Exception {
    ProcessSystem process = buildSeparatorProcess("Inlet");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    Document doc = parseXml(xml);
    assertEquals("PlantModel", doc.getDocumentElement().getNodeName(),
        "Root element must be PlantModel");
    assertTrue(doc.getElementsByTagName("PlantInformation").getLength() >= 1,
        "Drawing must declare PlantInformation");
    assertTrue(doc.getElementsByTagName("Equipment").getLength() >= 1,
        "Drawing must contain at least one Equipment element");
    assertTrue(doc.getElementsByTagName("PipingNetworkSystem").getLength() >= 1,
        "Drawing must contain at least one PipingNetworkSystem");
  }

  /**
   * Improvement #8: the service classifier exposes a NORSOK fluid code for each service category.
   */
  @Test
  public void testServiceFluidCodes() {
    assertEquals("PG", DexpiServiceClassifier.ServiceType.MAIN_PROCESS.getFluidCode());
    assertEquals("PL", DexpiServiceClassifier.ServiceType.SECONDARY_PROCESS.getFluidCode());
    assertEquals("FL", DexpiServiceClassifier.ServiceType.FLARE.getFluidCode());
    assertEquals("DR", DexpiServiceClassifier.ServiceType.DRAIN.getFluidCode());
    assertEquals("FG", DexpiServiceClassifier.ServiceType.FUEL_GAS.getFluidCode());
    assertEquals("UT", DexpiServiceClassifier.ServiceType.UTILITY.getFluidCode());
  }

  /**
   * Improvement #2: {@code routeConnection} returns the orthogonal H-V-H points used by the
   * crossing-hop pass; a straight horizontal run returns two points and an offset returns four.
   */
  @Test
  public void testRouteConnectionPoints() {
    double[][] straight = DexpiLayoutEngine.routeConnection(80.0, 150.0, 280.0, 150.0);
    assertEquals(2, straight.length, "A straight horizontal run has two points");

    double[][] offset = DexpiLayoutEngine.routeConnection(80.0, 150.0, 280.0, 90.0);
    assertEquals(4, offset.length, "An offset H-V-H route has four points");
  }

  /**
   * Reads the LineType attribute of the first child element of the given tag.
   *
   * @param parent the parent element
   * @param tagName the child element tag name (e.g. "CenterLine" or "PolyLine")
   * @return the LineType attribute value
   */
  private String lineTypeOf(Element parent, String tagName) {
    Element line = (Element) parent.getElementsByTagName(tagName).item(0);
    Element pres = (Element) line.getElementsByTagName("Presentation").item(0);
    return pres.getAttribute("LineType");
  }

  /**
   * ISO 10628 layout convention: a separator's gas/overhead branch routes to the upper part of the
   * sheet and the liquid/bottoms branch to the lower part. With the DEXPI Y axis pointing up, the
   * gas-fed compressor must end up at a higher Y than the separator while the oil-fed valve ends up
   * at a lower Y, so the bottoms line no longer crosses the gas equipment.
   */
  @Test
  public void testGasUpLiquidDownLayout() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("nC10", 0.1);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule(2);
    fluid.init(0);
    Stream feed = new Stream("feed", fluid);
    feed.setPressure(50.0, "bara");
    feed.setTemperature(30.0, "C");
    feed.setFlowRate(1.0, "MSm3/day");
    ThreePhaseSeparator sep = new ThreePhaseSeparator("Sep", feed);
    neqsim.process.equipment.compressor.Compressor gasComp =
        new neqsim.process.equipment.compressor.Compressor("GasComp", sep.getGasOutStream());
    gasComp.setOutletPressure(80.0, "bara");
    neqsim.process.equipment.valve.ThrottlingValve oilValve =
        new neqsim.process.equipment.valve.ThrottlingValve("OilValve", sep.getOilOutStream());
    oilValve.setOutletPressure(30.0, "bara");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.add(gasComp);
    process.add(oilValve);

    java.util.Map<String, DexpiLayoutEngine.EquipmentPosition> positions =
        DexpiLayoutEngine.computeLayout(process);

    DexpiLayoutEngine.EquipmentPosition sepPos = positions.get("Sep");
    DexpiLayoutEngine.EquipmentPosition gasPos = positions.get("GasComp");
    DexpiLayoutEngine.EquipmentPosition oilPos = positions.get("OilValve");
    assertNotNull(sepPos, "Separator must have a layout position");
    assertNotNull(gasPos, "Gas compressor must have a layout position");
    assertNotNull(oilPos, "Oil valve must have a layout position");

    assertTrue(gasPos.y > sepPos.y,
        "Gas/overhead branch must route to the upper part (higher Y than the separator)");
    assertTrue(oilPos.y < sepPos.y,
        "Liquid/bottoms branch must route to the lower part (lower Y than the separator)");
  }
}
