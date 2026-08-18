package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import neqsim.NeqSimTest;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Tests explicit material-boundary handling in native DEXPI 2.0 Plant export. */
public class Dexpi20PlantBoundaryExportTest extends NeqSimTest {
  @TempDir
  Path temporaryDirectory;

  /** Verifies directional boundary objects, connected nodes, conformance, and deterministic output. */
  @Test
  public void writesExplicitBoundaryConnectorsDeterministically() throws Exception {
    ProcessSystem process = process();
    Dexpi20PlantExportOptions options = Dexpi20PlantExportOptions.builder(metadata())
        .boundaryConnectionMode(Dexpi20PlantExportOptions.BoundaryConnectionMode.EXPLICIT_OFF_PAGE_CONNECTORS).build();
    Path output = temporaryDirectory.resolve("explicit-boundaries.dexpi.xml");

    Dexpi20ConformanceAssessment.Report report = Dexpi20XmlWriter.writeAndAssess(process, output.toFile(), options);
    ByteArrayOutputStream repeated = new ByteArrayOutputStream();
    Dexpi20XmlWriter.write(process, repeated, options);

    assertTrue(report.isSchemaAndProfileConformant(), report.getErrors().toString());
    assertArrayEquals(Files.readAllBytes(output), repeated.toByteArray());

    Document document = parse(output);
    assertEquals(1, countObjects(document, "Plant/Piping.FlowInPipeOffPageConnector"));
    assertEquals(2, countObjects(document, "Plant/Piping.FlowOutPipeOffPageConnector"));
    assertEquals(3, Dexpi20ModelInspector.inspect(output).getOffPageConnectorCount());

    Set<String> nodeIds = objectIds(document, "Plant/Piping.PipingNode");
    Set<String> connectedNodeIds = referencedNodeIds(document);
    assertEquals(8, nodeIds.size());
    assertEquals(nodeIds, connectedNodeIds);
  }

  /** Verifies that boundary generation is opt-in and the metadata-only byte path is unchanged. */
  @Test
  public void preservesMetadataOnlyExportByDefault() throws Exception {
    ProcessSystem process = process();
    Dexpi20PlantExportMetadata metadata = metadata();
    Path established = temporaryDirectory.resolve("established-metadata.dexpi.xml");
    Path defaultOptions = temporaryDirectory.resolve("default-options.dexpi.xml");
    Path explicit = temporaryDirectory.resolve("explicit-options.dexpi.xml");

    Dexpi20XmlWriter.write(process, established.toFile(), metadata);
    Dexpi20XmlWriter.write(process, defaultOptions.toFile(), Dexpi20PlantExportOptions.builder(metadata).build());
    Dexpi20XmlWriter.write(process, explicit.toFile(),
        Dexpi20PlantExportOptions.builder(metadata)
            .boundaryConnectionMode(Dexpi20PlantExportOptions.BoundaryConnectionMode.EXPLICIT_OFF_PAGE_CONNECTORS)
            .build());

    assertArrayEquals(Files.readAllBytes(established), Files.readAllBytes(defaultOptions));
    assertNotEquals(new String(Files.readAllBytes(established), "UTF-8"),
        new String(Files.readAllBytes(explicit), "UTF-8"));
  }

  /** Verifies that incomplete option definitions fail before serialization. */
  @Test
  public void rejectsInvalidOptions() {
    assertThrows(IllegalArgumentException.class, () -> Dexpi20PlantExportOptions.builder(null));
    Dexpi20PlantExportOptions.Builder builder = Dexpi20PlantExportOptions.builder(metadata());
    assertThrows(IllegalArgumentException.class, () -> builder.boundaryConnectionMode(null));
    assertThrows(IllegalArgumentException.class,
        () -> Dexpi20XmlWriter.write(process(), new ByteArrayOutputStream(), (Dexpi20PlantExportOptions) null));
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

  private static int countObjects(Document document, String type) {
    int count = 0;
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      if (type.equals(((Element) objects.item(index)).getAttribute("type"))) {
        count++;
      }
    }
    return count;
  }

  private static Set<String> objectIds(Document document, String type) {
    Set<String> result = new LinkedHashSet<String>();
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      if (type.equals(object.getAttribute("type"))) {
        result.add(object.getAttribute("id"));
      }
    }
    return result;
  }

  private static Set<String> referencedNodeIds(Document document) {
    Set<String> result = new LinkedHashSet<String>();
    NodeList references = document.getElementsByTagName("References");
    for (int index = 0; index < references.getLength(); index++) {
      Element reference = (Element) references.item(index);
      String property = reference.getAttribute("property");
      if (!"SourceNode".equals(property) && !"TargetNode".equals(property)) {
        continue;
      }
      for (String value : reference.getAttribute("objects").trim().split("\\s+")) {
        if (value.startsWith("#")) {
          result.add(value.substring(1));
        }
      }
    }
    return result;
  }

  private static Dexpi20PlantExportMetadata metadata() {
    return Dexpi20PlantExportMetadata.builder("2026-08-18T11:00:00Z", "NeqSim", "Equinor", "3.17.0")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE,
            "SYNTHETIC-BOUNDARY-PLANT")
        .build();
  }

  private static ProcessSystem process() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("10-FEED-001", fluid);
    Separator separator = new Separator("10-VA-001", feed);
    Compressor compressor = new Compressor("10-KA-001", separator.getGasOutStream());
    ProcessSystem process = new ProcessSystem("controlled DEXPI boundary test");
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    return process;
  }
}
