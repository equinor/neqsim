package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
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

/** Tests deterministic DEXPI Core graphical export-inspection equivalence. */
public class Dexpi20GraphicalProjectionAssessmentTest extends NeqSimTest {
  @TempDir
  Path temporaryDirectory;

  /** Verifies mapped identity, geometry, style, metadata, bounds, and deterministic evidence. */
  @Test
  public void assessesSupportedProjectionEquivalence() throws Exception {
    ProcessSystem process = process();
    EngineeringGraphicalProjection projection = projection();
    Path file = temporaryDirectory.resolve("graphical-assessment.dexpi.xml");

    Dexpi20GraphicalProjectionReport exportReport = Dexpi20GraphicalProjectionWriter.write(process, projection,
        file.toFile(), options());
    Dexpi20GraphicalProjectionAssessment.Report first = Dexpi20GraphicalProjectionAssessment.assess(projection, file);
    Dexpi20GraphicalProjectionAssessment.Report second = Dexpi20GraphicalProjectionAssessment.assess(projection, file);

    assertTrue(exportReport.isComplete());
    assertTrue(first.isSupportedProjectionEquivalent());
    assertEquals(4, first.getExpectedPrimitiveSignatures().size());
    assertEquals(4, first.getExportedPrimitiveSignatures().size());
    assertEquals(4, first.getMatchedPrimitiveCount());
    assertEquals(64, first.getInspectedFileSha256().length());
    assertEquals(first.toJson(), second.toJson());
    assertTrue(first.toJson().contains(first.getInspectedFileSha256()));
    assertTrue(first.getDiagnostics().stream()
        .anyMatch(item -> "DEXPI_GRAPHICS_FILL_COLOR_LOSS_CONFIRMED".equals(item.getCode())));
    assertTrue(first.getDiagnostics().stream()
        .anyMatch(item -> "DEXPI_GRAPHICS_DASH_APPROXIMATION_CONFIRMED".equals(item.getCode())));
    assertTrue(first.toJson().contains("\"approvalStatus\": \"REVIEW_REQUIRED\""));
  }

  /** Verifies fail-closed detection of missing and unexpected stable primitive identities. */
  @Test
  public void detectsCorruptedPrimitiveIdentity() throws Exception {
    EngineeringGraphicalProjection projection = projection();
    Path file = temporaryDirectory.resolve("graphical-corrupt-id.dexpi.xml");
    Dexpi20GraphicalProjectionWriter.write(process(), projection, file.toFile(), options());
    Document document = parse(file);
    Element primitive = firstObjectWithIdPrefix(document, "GraphicalPrimitive_");
    primitive.setAttribute("id", "GraphicalPrimitive_unexpected");
    write(document, file);

    Dexpi20GraphicalProjectionAssessment.Report report = Dexpi20GraphicalProjectionAssessment.assess(projection, file);

    assertFalse(report.isSupportedProjectionEquivalent());
    assertEquals(3, report.getMatchedPrimitiveCount());
    assertTrue(
        report.getDiagnostics().stream().anyMatch(item -> "DEXPI_GRAPHICS_PRIMITIVE_MISSING".equals(item.getCode())));
    assertTrue(report.getDiagnostics().stream()
        .anyMatch(item -> "DEXPI_GRAPHICS_PRIMITIVE_UNEXPECTED".equals(item.getCode())));
  }

  /** Verifies fail-closed detection of a broken represented-object reference. */
  @Test
  public void detectsBrokenRepresentationReference() throws Exception {
    EngineeringGraphicalProjection projection = projection();
    Path file = temporaryDirectory.resolve("graphical-corrupt-reference.dexpi.xml");
    Dexpi20GraphicalProjectionWriter.write(process(), projection, file.toFile(), options());
    Document document = parse(file);
    Element group = firstObject(document, "Core/Diagram.RepresentationGroup");
    directReference(group, "Represents").setAttribute("objects", "#MissingConceptualObject");
    write(document, file);

    Dexpi20GraphicalProjectionAssessment.Report report = Dexpi20GraphicalProjectionAssessment.assess(projection, file);

    assertFalse(report.isSupportedProjectionEquivalent());
    assertTrue(report.getDiagnostics().stream()
        .anyMatch(item -> "DEXPI_GRAPHICS_GROUP_REPRESENTS_UNRESOLVED".equals(item.getCode())));
    assertTrue(report.getDiagnostics().stream()
        .anyMatch(item -> "DEXPI_GRAPHICS_PRIMITIVE_GROUP_UNRESOLVED".equals(item.getCode())));
  }

  private static EngineeringGraphicalProjection projection() {
    Primitive rectangle = Primitive.rectangle("separator:shape", "equipment:separator", "10-VA-001", 10.0, 20.0, 28.0,
        14.0, "#123456", "#abcdef", 0.8);
    Primitive polygon = Primitive.polygon("separator:proposal", "equipment:separator", "10-VA-001",
        Arrays.asList(new Point(20.0, 18.0), new Point(24.0, 22.0), new Point(20.0, 26.0)), "#654321", "none", 0.6);
    Primitive route = Primitive.polyline("separator:route", "equipment:separator", "10-VA-001",
        Arrays.asList(new Point(5.0, 20.0), new Point(10.0, 20.0)), "#2563eb", 0.8, "4 2", false);
    Primitive label = Primitive.text("separator:label", "equipment:separator", "10-VA-001", 24.0, 27.0, 3.0,
        "10-VA-001", "#111827", "middle");
    return new EngineeringGraphicalProjection("synthetic-plant", "A", "graph-fingerprint", "DOC-PFD-001",
        VerificationStatus.PROPOSAL, Arrays.asList(rectangle, polygon, route, label),
        Collections.<EngineeringGraphicalProjection.Diagnostic>emptyList());
  }

  private static Dexpi20PlantExportOptions options() {
    Dexpi20PlantExportMetadata metadata = Dexpi20PlantExportMetadata
        .builder("2026-08-25T20:00:00Z", "NeqSim", "Equinor", "3.18.0")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_NAME,
            "Synthetic graphical assessment regression plant")
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
    ProcessSystem process = new ProcessSystem("DEXPI graphical assessment test");
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

  private static void write(Document document, Path file) throws Exception {
    TransformerFactory factory = TransformerFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    Transformer transformer = factory.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.transform(new DOMSource(document), new StreamResult(file.toFile()));
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

  private static Element firstObjectWithIdPrefix(Document document, String prefix) {
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      if (object.getAttribute("id").startsWith(prefix)) {
        return object;
      }
    }
    throw new AssertionError("Missing object id prefix " + prefix);
  }

  private static Element directReference(Element object, String property) {
    for (Node child = object.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "References".equals(((Element) child).getTagName())
          && property.equals(((Element) child).getAttribute("property"))) {
        return (Element) child;
      }
    }
    throw new AssertionError("Missing reference property " + property);
  }
}
