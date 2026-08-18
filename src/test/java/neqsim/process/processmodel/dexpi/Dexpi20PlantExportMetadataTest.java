package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import neqsim.NeqSimTest;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Tests controlled provenance and plant identity in native DEXPI 2.0 Plant export. */
public class Dexpi20PlantExportMetadataTest extends NeqSimTest {
  @TempDir
  Path temporaryDirectory;

  /** Verifies deterministic schema-valid export and the exact DEXPI metadata placement. */
  @Test
  public void writesControlledMetadataDeterministically() throws Exception {
    ProcessSystem process = process();
    Dexpi20PlantExportMetadata metadata = Dexpi20PlantExportMetadata
        .builder("2026-08-18T05:00:00Z", "NeqSim", "Equinor", "3.17.0")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, "SYNTHETIC-PLANT")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_NAME, "Synthetic regression plant")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PLANT_AREA_IDENTIFICATION_CODE, "AREA-10")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PLANT_AREA_NAME, "Synthetic process area").build();
    Path first = temporaryDirectory.resolve("controlled-first.dexpi.xml");
    Path second = temporaryDirectory.resolve("controlled-second.dexpi.xml");

    Dexpi20ConformanceAssessment.Report report = Dexpi20XmlWriter.writeAndAssess(process, first.toFile(), metadata);
    Dexpi20XmlWriter.write(process, second.toFile(), metadata);

    assertTrue(report.isSchemaAndProfileConformant(), report.getErrors().toString());
    assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));

    Document document = parse(first);
    Element engineeringModel = objectByType(document, "Core/EngineeringModel");
    assertEquals("NeqSim", dataValue(engineeringModel, "OriginatingSystemName"));
    assertEquals("Equinor", dataValue(engineeringModel, "OriginatingSystemVendorName"));
    assertEquals("3.17.0", dataValue(engineeringModel, "OriginatingSystemVersion"));
    assertEquals("2026-08-18T05:00:00Z", dataValue(engineeringModel, "ExportDateTime"));

    Element plantModel = objectByType(document, "Plant/PlantModel");
    Element plantMetadata = componentObject(plantModel, "MetaData");
    assertEquals("Plant/Diagram.PlantMetaData", plantMetadata.getAttribute("type"));
    assertEquals("SYNTHETIC-PLANT", dataValue(plantMetadata, "ProcessPlantIdentificationCode"));
    assertEquals("Synthetic regression plant", dataValue(plantMetadata, "ProcessPlantName"));
    assertEquals("AREA-10", dataValue(plantMetadata, "PlantAreaIdentificationCode"));
    assertEquals("Synthetic process area", dataValue(plantMetadata, "PlantAreaName"));
  }

  /** Verifies that the compatibility overload remains metadata-free and deterministic. */
  @Test
  public void preservesCompatibilityExport() throws Exception {
    ProcessSystem process = process();
    Path first = temporaryDirectory.resolve("legacy-first.dexpi.xml");
    Path second = temporaryDirectory.resolve("legacy-second.dexpi.xml");

    Dexpi20XmlWriter.write(process, first.toFile());
    Dexpi20XmlWriter.write(process, second.toFile());

    assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    Document document = parse(first);
    Element engineeringModel = objectByType(document, "Core/EngineeringModel");
    assertEquals("", dataValue(engineeringModel, "ExportDateTime"));
    assertFalse(hasObjectType(document, "Plant/Diagram.PlantMetaData"));
  }

  /** Verifies that placeholders cannot be used to manufacture validator success. */
  @Test
  public void rejectsInvalidOrEmptyMetadata() {
    assertThrows(IllegalArgumentException.class,
        () -> Dexpi20PlantExportMetadata.builder("not-a-date", "NeqSim", "Equinor", "3.17.0"));
    assertThrows(IllegalArgumentException.class,
        () -> Dexpi20PlantExportMetadata.builder("2026-08-18T05:00:00Z", " ", "Equinor", "3.17.0"));
    Dexpi20PlantExportMetadata.Builder builder = Dexpi20PlantExportMetadata.builder("2026-08-18T05:00:00Z", "NeqSim",
        "Equinor", "3.17.0");
    assertThrows(IllegalStateException.class, builder::build);
  }

  private static Document parse(Path file) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory.newDocumentBuilder().parse(file.toFile());
  }

  private static Element objectByType(Document document, String type) {
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      if (type.equals(object.getAttribute("type"))) {
        return object;
      }
    }
    throw new AssertionError("Missing object type " + type);
  }

  private static boolean hasObjectType(Document document, String type) {
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      if (type.equals(((Element) objects.item(index)).getAttribute("type"))) {
        return true;
      }
    }
    return false;
  }

  private static Element componentObject(Element parent, String property) {
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "Components".equals(((Element) child).getTagName())
          && property.equals(((Element) child).getAttribute("property"))) {
        NodeList objects = ((Element) child).getElementsByTagName("Object");
        if (objects.getLength() > 0) {
          return (Element) objects.item(0);
        }
      }
    }
    throw new AssertionError("Missing component property " + property);
  }

  private static String dataValue(Element parent, String property) {
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "Data".equals(((Element) child).getTagName())
          && property.equals(((Element) child).getAttribute("property"))) {
        return child.getTextContent().trim();
      }
    }
    return "";
  }

  private static ProcessSystem process() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("10-FEED-001", fluid);
    Separator separator = new Separator("10-VA-001", feed);
    Compressor compressor = new Compressor("10-KA-001", separator.getGasOutStream());
    ProcessSystem process = new ProcessSystem("controlled DEXPI metadata test");
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    return process;
  }
}
