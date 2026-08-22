package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import neqsim.NeqSimTest;
import neqsim.process.engineering.model.EngineeringGraphicalProjection;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.Point;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.Primitive;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.VerificationStatus;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Tests the opt-in exchange-neutral projection to DEXPI Core adapter. */
public class Dexpi20GraphicalProjectionWriterTest extends NeqSimTest {
  @TempDir
  Path temporaryDirectory;

  /** Verifies deterministic non-empty primitives, identity references, and explicit losses. */
  @Test
  public void writesSupportedCorePrimitivesAndReportsUnmappedIdentity() throws Exception {
    ProcessSystem process = process();
    EngineeringGraphicalProjection projection = projection();
    Dexpi20PlantExportOptions options = options();
    Path first = temporaryDirectory.resolve("graphical-first.dexpi.xml");
    Path second = temporaryDirectory.resolve("graphical-second.dexpi.xml");

    Dexpi20GraphicalProjectionReport firstReport =
        Dexpi20GraphicalProjectionWriter.write(process, projection, first.toFile(), options);
    Dexpi20GraphicalProjectionReport secondReport =
        Dexpi20GraphicalProjectionWriter.write(process, projection, second.toFile(), options);

    assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    assertEquals(firstReport.toJson(), secondReport.toJson());
    assertEquals(1, firstReport.getEmittedRepresentationGroupCount());
    assertEquals(4, firstReport.getEmittedPrimitiveCount());
    assertEquals(1, firstReport.getSkippedPrimitiveCount());
    assertFalse(firstReport.isComplete());
    assertTrue(firstReport.getDiagnostics().stream()
        .anyMatch(item -> "DEXPI_GRAPHICS_UNMAPPED_REPRESENTED_OBJECT".equals(item.getCode())));

    Document document = parse(first);
    assertEquals(1, objectCount(document, "Core/Diagram.Diagram"));
    assertEquals(1, objectCount(document, "Core/Diagram.RepresentationGroup"));
    assertEquals(1, objectCount(document, "Core/Diagram.Static"));
    assertEquals(2, objectCount(document, "Core/Diagram.Polygon"));
    assertEquals(1, objectCount(document, "Core/Diagram.PolyLine"));
    assertEquals(1, objectCount(document, "Core/Diagram.Text"));
    Element group = firstObject(document, "Core/Diagram.RepresentationGroup");
    assertEquals("#Equipment1", directReference(group, "Represents"));
    assertTrue(hasSupportedPrimitive(group));
    assertTrue(Dexpi20SemanticValidator.validate(document).isValid());
  }

  /** Verifies that the semantic gate rejects a placeholder representation group. */
  @Test
  public void rejectsEmptyRepresentationGroupPlaceholder() throws Exception {
    Path file = temporaryDirectory.resolve("empty-group.dexpi.xml");
    Dexpi20GraphicalProjectionWriter.write(process(), projection(), file.toFile(), options());
    Document document = parse(file);
    Element group = firstObject(document, "Core/Diagram.RepresentationGroup");
    Element groups = directComponents(group, "Groups");
    group.removeChild(groups);

    Dexpi20SemanticValidator.ValidationReport report =
        Dexpi20SemanticValidator.validate(document);

    assertFalse(report.isValid());
    assertTrue(report.getErrors().stream()
        .anyMatch(item -> item.contains("RepresentationGroup has no non-empty supported primitive")));
  }

  private static EngineeringGraphicalProjection projection() {
    Primitive rectangle = Primitive.rectangle("separator:shape", "equipment:separator", "10-VA-001",
        10.0, 20.0, 28.0, 14.0, "#123456", "#abcdef", 0.8);
    Primitive polygon = Primitive.polygon("separator:proposal", "equipment:separator", "10-VA-001",
        Arrays.asList(new Point(20.0, 18.0), new Point(24.0, 22.0), new Point(20.0, 26.0)),
        "#654321", "none", 0.6);
    Primitive route = Primitive.polyline("separator:route", "equipment:separator", "10-VA-001",
        Arrays.asList(new Point(5.0, 20.0), new Point(10.0, 20.0)), "#2563eb", 0.8, "4 2",
        false);
    Primitive label = Primitive.text("separator:label", "equipment:separator", "10-VA-001",
        24.0, 27.0, 3.0, "10-VA-001", "#111827", "middle");
    Primitive unmapped = Primitive.rectangle("unknown:shape", "equipment:unknown", "UNKNOWN-1",
        60.0, 20.0, 20.0, 12.0, "#000000", "none", 0.8);
    return new EngineeringGraphicalProjection("synthetic-plant", "A", "graph-fingerprint",
        "DOC-PFD-001", VerificationStatus.PROPOSAL,
        Arrays.asList(rectangle, polygon, route, label, unmapped),
        Collections.<EngineeringGraphicalProjection.Diagnostic>emptyList());
  }

  private static Dexpi20PlantExportOptions options() {
    Dexpi20PlantExportMetadata metadata = Dexpi20PlantExportMetadata
        .builder("2026-08-22T12:00:00Z", "NeqSim", "Equinor", "3.17.0")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_NAME,
            "Synthetic graphical adapter regression plant")
        .build();
    return Dexpi20PlantExportOptions.builder(metadata).build();
  }

  private static ProcessSystem process() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("10-FEED-001", fluid);
    Separator separator = new Separator("10-VA-001", feed);
    Compressor compressor = new Compressor("10-KA-001", separator.getGasOutStream());
    ProcessSystem process = new ProcessSystem("DEXPI graphical projection test");
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    return process;
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

  private static int objectCount(Document document, String type) {
    int count = 0;
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      if (type.equals(((Element) objects.item(index)).getAttribute("type"))) {
        count++;
      }
    }
    return count;
  }

  private static Element firstObject(Document document, String type) {
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      if (type.equals(object.getAttribute("type"))) {
        return object;
      }
    }
    throw new AssertionError("Missing object type " + type);
  }

  private static String directReference(Element object, String property) {
    for (Node child = object.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "References".equals(((Element) child).getTagName())
          && property.equals(((Element) child).getAttribute("property"))) {
        return ((Element) child).getAttribute("objects");
      }
    }
    return "";
  }

  private static Element directComponents(Element object, String property) {
    for (Node child = object.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "Components".equals(((Element) child).getTagName())
          && property.equals(((Element) child).getAttribute("property"))) {
        return (Element) child;
      }
    }
    throw new AssertionError("Missing component property " + property);
  }

  private static boolean hasSupportedPrimitive(Element object) {
    NodeList descendants = object.getElementsByTagName("Object");
    for (int index = 0; index < descendants.getLength(); index++) {
      String type = ((Element) descendants.item(index)).getAttribute("type");
      if ("Core/Diagram.Polygon".equals(type) || "Core/Diagram.PolyLine".equals(type)
          || "Core/Diagram.Text".equals(type)) {
        return true;
      }
    }
    return false;
  }
}
