package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

class DexpiXmlReaderTest {

  private Stream createExampleFeedStream() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule(2);
    fluid.init(0);
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1.0, "MSm3/day");
    feed.setTemperature(30.0, "C");
    feed.setPressure(50.0, "bara");
    feed.setSpecification("TP");
    return feed;
  }

  @Test
  void convertsDexpiExampleIntoRunnableProcess() throws Exception {
    try (InputStream inputStream = getClass().getResourceAsStream("/dexpi/C01V04-VER.EX01.xml")) {
      assertNotNull(inputStream, "Test resource dexpi/C01V04-VER.EX01.xml is missing");

      Stream templateStream = createExampleFeedStream();
      ProcessSystem processSystem = new ProcessSystem("DEXPI example with feed");
      processSystem.add(templateStream);

      DexpiXmlReader.load(inputStream, processSystem, templateStream);
      processSystem.run();

      Path dotOutput = Paths.get("target", "test-output", "dexpi-dexpi_example_process.dot");
      Files.createDirectories(dotOutput.getParent());
      processSystem.exportToGraphviz(dotOutput.toString());
      assertTrue(Files.exists(dotOutput), "Graphviz export should produce a DOT file");

      DexpiProcessUnit heatExchanger1 = (DexpiProcessUnit) processSystem.getUnit("H1007");
      DexpiProcessUnit heatExchanger2 = (DexpiProcessUnit) processSystem.getUnit("H1008");
      DexpiProcessUnit pump1 = (DexpiProcessUnit) processSystem.getUnit("P4711");
      DexpiProcessUnit pump2 = (DexpiProcessUnit) processSystem.getUnit("P4712");
      DexpiProcessUnit tank = (DexpiProcessUnit) processSystem.getUnit("T4750");

      assertTrue(heatExchanger1.getMappedEquipment() == EquipmentEnum.HeatExchanger);
      assertTrue(heatExchanger2.getMappedEquipment() == EquipmentEnum.HeatExchanger);
      assertTrue(pump1.getMappedEquipment() == EquipmentEnum.Pump);
      assertTrue(pump2.getMappedEquipment() == EquipmentEnum.Pump);
      assertTrue(tank.getMappedEquipment() == EquipmentEnum.Tank);

      long throttlingValveCount = processSystem.getUnitOperations().stream()
          .filter(unit -> unit instanceof DexpiProcessUnit)
          .map(unit -> ((DexpiProcessUnit) unit).getMappedEquipment())
          .filter(EquipmentEnum.ThrottlingValve::equals).count();
      assertTrue(throttlingValveCount >= 3, "Expected at least three throttling valves");

      long dexpiStreamCount = processSystem.getUnitOperations().stream()
          .filter(DexpiStream.class::isInstance).count();
      assertTrue(dexpiStreamCount >= 5, "Expected to discover several piping segments as streams");

      DexpiStream referenceStream = (DexpiStream) processSystem.getUnit("47121-S1");
      assertNotNull(referenceStream, "Reference piping segment should be available as a stream");
      assertEquals(templateStream.getFlowRate("MSm3/day"),
          referenceStream.getFlowRate("MSm3/day"), 1e-9);

      long activeStreams = processSystem.getUnitOperations().stream()
          .filter(DexpiStream.class::isInstance).map(DexpiStream.class::cast)
          .filter(Stream::isActive).count();
      assertTrue(activeStreams > 0, "At least one imported stream should calculate a TP flash");
    }
  }

  @Test
  void exportsProcessToDexpiXmlForExampleProcess() throws Exception {
    try (InputStream inputStream = getClass().getResourceAsStream("/dexpi/C01V04-VER.EX01.xml")) {
      assertNotNull(inputStream, "Test resource dexpi/C01V04-VER.EX01.xml is missing");

      Stream templateStream = createExampleFeedStream();
      ProcessSystem processSystem = new ProcessSystem("DEXPI example for export");
      processSystem.add(templateStream);

      DexpiXmlReader.load(inputStream, processSystem, templateStream);

      Path exportPath = Paths.get("target", "test-output", "dexpi-export.xml");
      Files.createDirectories(exportPath.getParent());
      DexpiXmlWriter.write(processSystem, exportPath.toFile());
      assertTrue(Files.exists(exportPath), "DEXPI export should produce an XML file");

      Document document = parseExport(exportPath);
      NodeList equipment = document.getElementsByTagName("Equipment");
      NodeList pipingComponents = document.getElementsByTagName("PipingComponent");
      NodeList pipingSegments = document.getElementsByTagName("PipingNetworkSegment");
      NodeList systems = document.getElementsByTagName("PipingNetworkSystem");

      assertTrue(equipment.getLength() > 0, "Export should contain equipment elements");
      assertTrue(pipingSegments.getLength() > 0,
          "Export should contain piping network segments");
      assertTrue(systems.getLength() > 0, "Export should include piping network systems");
      assertTrue(equipment.getLength() + pipingComponents.getLength() > 5,
          "Export should include multiple pieces of equipment and piping components");
    }
  }

  private Document parseExport(Path exportPath) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    factory.setNamespaceAware(false);
    factory.setExpandEntityReferences(false);
    DocumentBuilder builder = factory.newDocumentBuilder();
    try (InputStream exportStream = Files.newInputStream(exportPath)) {
      return builder.parse(exportStream);
    }
  }
}
