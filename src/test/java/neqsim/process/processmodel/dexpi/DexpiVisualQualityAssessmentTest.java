package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import neqsim.NeqSimTest;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.diagram.EngineeringDiagramReferenceFixtures;
import neqsim.thermo.system.SystemSrkEos;

/** Fixed structural and rendering benchmarks for NeqSim-generated P&amp;ID proposals. */
class DexpiVisualQualityAssessmentTest extends NeqSimTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void assessesSimpleAndBranchedReferenceCasesDeterministically() throws Exception {
    assertCleanAndDeterministic("simple", EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem());
    assertCleanAndDeterministic("branched",
        EngineeringDiagramReferenceFixtures.branchedSeparatorCompressionTrain().getProcessSystem());
  }

  @Test
  void assessesEveryMultiAreaReferenceSheet() throws Exception {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = EngineeringDiagramReferenceFixtures.multiAreaFacility();
    fixture.getProcessModel().run();
    Path sheetsDirectory = temporaryDirectory.resolve("multi-area");
    List<java.io.File> sheets = DexpiXmlWriter.writeSheets(fixture.getProcessModel(), sheetsDirectory.toFile());

    assertEquals(fixture.getAreaNames().size(), sheets.size());
    for (java.io.File sheet : sheets) {
      DexpiVisualQualityAssessment.Report report = DexpiVisualQualityAssessment.assess(sheet);
      assertFalse(report.hasErrors(), report.toJson());
      assertTrue(report.getMetrics().get("svgTexts") > 0, report.toJson());
      assertTrue(report.getSvgSha256().matches("[0-9a-f]{64}"));
    }
  }

  @Test
  void coversMixerSplitterRecycleControlLoopAndEmptyPlaceholder() throws Exception {
    ProcessSystem process = recycleControlBenchmark();
    process.run();
    Stream emptyPlaceholder = new Stream("90-EMPTY-SPARE");
    process.add(emptyPlaceholder);
    process.run();

    Path dexpi = temporaryDirectory.resolve("recycle-control.xml");
    DexpiXmlWriter.writeForPyDexpi(process, dexpi.toFile());
    String xml = new String(Files.readAllBytes(dexpi), StandardCharsets.UTF_8);
    DexpiVisualQualityAssessment.Report report = DexpiVisualQualityAssessment.assess(dexpi.toFile());

    assertNull(emptyPlaceholder.getFluid());
    assertFalse(xml.contains("90-EMPTY-SPARE"));
    assertTrue(xml.contains("PT-5001"));
    assertTrue(xml.contains("PC-5001"));
    assertFalse(report.hasErrors(), report.toJson());
    assertTrue(report.getMetrics().get("componentInstances") >= 4, report.toJson());
    assertTrue(report.getMetrics().get("sourceTexts") >= 4, report.toJson());
    assertTrue(report.getMetrics().get("sourceCenterLines") > 0, report.toJson());
  }

  @Test
  void reportsMissingShapeDuplicateIdentityOutOfBoundsAndSmallText() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<PlantModel><PlantInformation SchemaVersion=\"4.1.1\"/>"
        + "<Drawing Name=\"Defect benchmark\"><Extent><Min X=\"0\" Y=\"0\"/>"
        + "<Max X=\"100\" Y=\"70\"/></Extent></Drawing>"
        + "<ShapeCatalogue><Shape ComponentName=\"KNOWN\"><Circle Radius=\"2\"/>" + "</Shape></ShapeCatalogue>"
        + "<Equipment ID=\"DUP-1\" ComponentName=\"MISSING\"><Position>"
        + "<Location X=\"10\" Y=\"10\"/></Position></Equipment>"
        + "<Equipment ID=\"DUP-1\" ComponentName=\"KNOWN\"><Position>"
        + "<Location X=\"20\" Y=\"20\"/></Position></Equipment>"
        + "<CenterLine><Coordinate X=\"120\" Y=\"20\"/><Coordinate X=\"20\" "
        + "Y=\"20\"/></CenterLine><Text String=\"tiny\" Height=\"1.2\"><Position>"
        + "<Location X=\"10\" Y=\"10\"/></Position></Text></PlantModel>";
    Path dexpi = temporaryDirectory.resolve("defects.xml");
    Files.write(dexpi, xml.getBytes(StandardCharsets.UTF_8));

    DexpiVisualQualityAssessment.Report report = DexpiVisualQualityAssessment.assess(dexpi.toFile());

    assertTrue(report.hasErrors());
    assertTrue(hasFinding(report, "DUPLICATE_ID"));
    assertTrue(hasFinding(report, "SHAPE_REFERENCE_MISSING"));
    assertTrue(hasFinding(report, "COORDINATE_OUTSIDE_DRAWING"));
    assertTrue(hasFinding(report, "TEXT_HEIGHT_BELOW_RENDERER_MINIMUM"));
    assertEquals(1, report.getMetrics().get("duplicateIds"));
    assertEquals(1, report.getMetrics().get("outOfBoundsCoordinates"));
    assertEquals(report.toJson(), DexpiVisualQualityAssessment.assess(dexpi.toFile()).toJson());
  }

  @Test
  void keepsInstrumentBubblesClearOfDataBarsAndInsideBatteryLimit() throws Exception {
    Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    Element root = document.createElement("PlantModel");
    document.appendChild(root);
    DexpiLayoutEngine.EquipmentPosition equipment = new DexpiLayoutEngine.EquipmentPosition(100.0, 150.0, 1.0, 1.0);
    Element equipmentElement = document.createElement("Equipment");
    root.appendChild(equipmentElement);
    List<String[]> extraRows = new ArrayList<String[]>();
    extraRows.add(new String[] { "Design P.", "110 bara" });
    extraRows.add(new String[] { "Length", "5000 mm" });
    extraRows.add(new String[] { "ID", "1000 mm" });
    DexpiLayoutEngine.appendEquipmentBarLabel(document, equipmentElement, "20-VA-001", equipment, "BAR-1",
        "ID-20-VA-001", 60.0, 30.0, 0.3, extraRows);
    double[] instrument = DexpiLayoutEngine.computeInstrumentPosition(equipment, 0, 1);

    double barTop = maximumCoordinateY(equipmentElement);
    assertTrue(instrument[1] - DexpiLayoutEngine.INSTRUMENT_BUBBLE_RADIUS > barTop + 4.0,
        "Instrument bubble must clear the complete seven-row equipment data bar");

    Map<String, DexpiLayoutEngine.EquipmentPosition> positions = new LinkedHashMap<String, DexpiLayoutEngine.EquipmentPosition>();
    positions.put("20-VA-001", equipment);
    DexpiLayoutEngine.appendBatteryLimitBoundary(document, root, positions, "Area 20");
    Element boundary = identifiedElement(document, "BatteryLimit-1");
    assertTrue(maximumCoordinateY(boundary) > instrument[1] + DexpiLayoutEngine.INSTRUMENT_BUBBLE_RADIUS,
        "Battery limit must enclose the highest instrument bubble");
  }

  private void assertCleanAndDeterministic(String name, ProcessSystem process) throws Exception {
    process.run();
    Path first = temporaryDirectory.resolve(name + "-first.xml");
    Path second = temporaryDirectory.resolve(name + "-second.xml");
    DexpiXmlWriter.writeForPyDexpi(process, first.toFile());
    DexpiXmlWriter.writeForPyDexpi(process, second.toFile());

    DexpiVisualQualityAssessment.Report firstReport = DexpiVisualQualityAssessment.assess(first.toFile());
    DexpiVisualQualityAssessment.Report secondReport = DexpiVisualQualityAssessment.assess(second.toFile());

    assertFalse(firstReport.hasErrors(), firstReport.toJson());
    assertEquals("Proteus-compatible DEXPI Plant/P&ID (SchemaVersion 4.1.1)", firstReport.getProfile());
    assertTrue(firstReport.getMetrics().get("componentInstances") > 0, firstReport.toJson());
    assertTrue(firstReport.getMetrics().get("renderedInstances") > 0, firstReport.toJson());
    assertEquals(firstReport.getSvgSha256(), secondReport.getSvgSha256());
    assertEquals(firstReport.toJson(), secondReport.toJson());
  }

  private static ProcessSystem recycleControlBenchmark() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.15);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("50-FEED-001", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Mixer mixer = new Mixer("50-MX-001");
    mixer.addStream(feed);
    Heater heater = new Heater("50-HA-001", mixer.getOutletStream());
    heater.setOutTemperature(35.0, "C");
    Splitter splitter = new Splitter("50-SP-001", heater.getOutletStream(), 2);
    splitter.setSplitFactors(new double[] { 0.8, 0.2 });
    Stream product = new Stream("50-PRODUCT-001", splitter.getSplitStream(0));
    Recycle recycle = new Recycle("50-RC-001");
    recycle.addStream(splitter.getSplitStream(1));
    recycle.setOutletStream(new Stream("50-RECYCLE-001", fluid.clone()));
    mixer.addStream(recycle.getOutletStream());
    PressureTransmitter pressure = new PressureTransmitter("PT-5001", heater.getOutletStream());
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("PC-5001");
    controller.setControllerSetPoint(50.0);
    controller.setControllerParameters(1.0, 30.0, 0.0);
    controller.setTransmitter(pressure);

    ProcessSystem process = new ProcessSystem("Recycle and control visual benchmark");
    process.add(feed);
    process.add(mixer);
    process.add(heater);
    process.add(splitter);
    process.add(product);
    process.add(recycle);
    process.add(pressure);
    process.add(controller);
    return process;
  }

  private static boolean hasFinding(DexpiVisualQualityAssessment.Report report, String code) {
    for (DexpiVisualQualityAssessment.Finding finding : report.getFindings()) {
      if (code.equals(finding.getCode())) {
        return true;
      }
    }
    return false;
  }

  private static double maximumCoordinateY(Element parent) {
    NodeList coordinates = parent.getElementsByTagName("Coordinate");
    double maximum = -Double.MAX_VALUE;
    for (int index = 0; index < coordinates.getLength(); index++) {
      maximum = Math.max(maximum, Double.parseDouble(((Element) coordinates.item(index)).getAttribute("Y")));
    }
    return maximum;
  }

  private static Element identifiedElement(Document document, String identity) {
    NodeList elements = document.getElementsByTagName("*");
    for (int index = 0; index < elements.getLength(); index++) {
      Element element = (Element) elements.item(index);
      if (identity.equals(element.getAttribute("ID"))) {
        return element;
      }
    }
    throw new AssertionError("Missing element " + identity);
  }
}
