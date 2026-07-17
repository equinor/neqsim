package neqsim.process.processmodel.dexpi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Performs semantic integrity checks for the NeqSim-supported DEXPI 2.0 profile. */
public final class Dexpi20SemanticValidator {
  private static final String CORE_MODEL = "https://data.dexpi.org/models/2.0.0/Core.xml";
  private static final String PLANT_MODEL = "https://data.dexpi.org/models/2.0.0/Plant.xml";
  private static final Set<String> SUPPORTED_TYPES = Collections
      .unmodifiableSet(new LinkedHashSet<String>(Arrays.asList("Core/EngineeringModel", "Core/Diagram.Diagram",
          "Core/Diagram.RepresentationGroup", "Core/Diagram.Text", "Core/Diagram.Point", "Plant/PlantModel",
          "Plant/ProcessEquipment.ProcessEquipment", "Plant/ProcessEquipment.CentrifugalCompressor",
          "Plant/ProcessEquipment.CentrifugalPump", "Plant/ProcessEquipment.Separator",
          "Plant/ProcessEquipment.AirCoolingSystem", "Plant/ProcessEquipment.TubularHeatExchanger",
          "Plant/ProcessEquipment.FiredHeater", "Plant/ProcessEquipment.Tank", "Plant/ProcessEquipment.Nozzle",
          "Plant/Piping.PipingNetworkSystem", "Plant/Piping.PipingNetworkSegment", "Plant/Piping.PipingNode",
          "Plant/Piping.Pipe", "Plant/Piping.GlobeValve", "Plant/Piping.SpringLoadedGlobeSafetyValve",
          "Plant/Piping.SwingCheckValve", "Plant/Piping.FlowInPipeOffPageConnector",
          "Plant/Piping.FlowOutPipeOffPageConnector", "Plant/Instrumentation.ProcessInstrumentationFunction",
          "Plant/Instrumentation.ProcessSignalGeneratingFunction", "Plant/Instrumentation.MeasuringLineFunction")));

  private Dexpi20SemanticValidator() {
  }

  /** Immutable semantic validation result. */
  public static final class ValidationReport {
    private final List<String> errors;
    private final List<String> warnings;

    ValidationReport(List<String> errors, List<String> warnings) {
      this.errors = Collections.unmodifiableList(new ArrayList<String>(errors));
      this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    /** @return whether no semantic errors were found */
    public boolean isValid() {
      return errors.isEmpty();
    }

    /** @return immutable error messages */
    public List<String> getErrors() {
      return errors;
    }

    /** @return immutable warning messages */
    public List<String> getWarnings() {
      return warnings;
    }
  }

  /**
   * Validates imported-model declarations, object identity, references and supported-profile topology.
   *
   * @param file DEXPI XML document
   * @return semantic validation report
   * @throws IOException if the document cannot be read
   */
  public static ValidationReport validate(Path file) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      return validate(factory.newDocumentBuilder().parse(file.toFile()));
    } catch (ParserConfigurationException ex) {
      throw new IOException("Could not configure DEXPI semantic validation", ex);
    } catch (SAXException ex) {
      throw new IOException("Could not parse DEXPI XML", ex);
    }
  }

  /** Validates a parsed DEXPI XML document. */
  public static ValidationReport validate(Document document) {
    if (document == null) {
      throw new IllegalArgumentException("document must not be null");
    }
    List<String> errors = new ArrayList<String>();
    List<String> warnings = new ArrayList<String>();
    Element root = document.getDocumentElement();
    if (root == null || !"Model".equals(root.getTagName())) {
      errors.add("Root element must be Model.");
      return new ValidationReport(errors, warnings);
    }

    Map<String, String> imports = collectImports(root, errors);
    requireImport(imports, "Core", CORE_MODEL, errors);
    requireImport(imports, "Plant", PLANT_MODEL, errors);

    Map<String, Element> objectsById = new LinkedHashMap<String, Element>();
    Set<String> tagNames = new LinkedHashSet<String>();
    NodeList objects = root.getElementsByTagName("Object");
    for (int i = 0; i < objects.getLength(); i++) {
      Element object = (Element) objects.item(i);
      String type = object.getAttribute("type");
      validateType(type, imports, true, errors);
      String id = object.getAttribute("id");
      if (!id.isEmpty() && objectsById.put(id, object) != null) {
        errors.add("Duplicate object id: " + id);
      }
      String tagName = directStringData(object, "TagName");
      if (tagName != null && !tagNames.add(tagName)) {
        errors.add("Duplicate plant item TagName: " + tagName);
      }
    }
    NodeList values = root.getElementsByTagName("AggregatedDataValue");
    for (int i = 0; i < values.getLength(); i++) {
      validateType(((Element) values.item(i)).getAttribute("type"), imports, true, errors);
    }
    NodeList dataReferences = root.getElementsByTagName("DataReference");
    for (int i = 0; i < dataReferences.getLength(); i++) {
      validateType(((Element) dataReferences.item(i)).getAttribute("data"), imports, false, errors);
    }

    validateReferences(root, objectsById, errors);
    validateEquipment(objects, errors, warnings);
    validatePipingReferences(root, objectsById, errors);
    return new ValidationReport(errors, warnings);
  }

  /** Throws when semantic validation reports an error. */
  public static void validateOrThrow(Path file) throws IOException {
    ValidationReport report = validate(file);
    if (!report.isValid()) {
      throw new IOException("DEXPI 2.0 semantic validation failed: " + report.getErrors());
    }
  }

  private static Map<String, String> collectImports(Element root, List<String> errors) {
    Map<String, String> imports = new LinkedHashMap<String, String>();
    NodeList nodes = root.getElementsByTagName("Import");
    for (int i = 0; i < nodes.getLength(); i++) {
      Element item = (Element) nodes.item(i);
      String prefix = item.getAttribute("prefix");
      String source = item.getAttribute("source");
      if (prefix.isEmpty() || source.isEmpty()) {
        errors.add("Every Import requires prefix and source.");
      } else if (imports.put(prefix, source) != null) {
        errors.add("Duplicate import prefix: " + prefix);
      }
    }
    return imports;
  }

  private static void requireImport(Map<String, String> imports, String prefix, String source, List<String> errors) {
    if (!source.equals(imports.get(prefix))) {
      errors.add("Import " + prefix + " must resolve to " + source);
    }
  }

  private static void validateType(String type, Map<String, String> imports, boolean requireSupportedType,
      List<String> errors) {
    int separator = type.indexOf('/');
    if (separator <= 0 || separator == type.length() - 1) {
      errors.add("Object type is not an imported model reference: " + type);
      return;
    }
    if (!imports.containsKey(type.substring(0, separator))) {
      errors.add("Object type uses an undeclared import prefix: " + type);
    } else if (requireSupportedType && !SUPPORTED_TYPES.contains(type)) {
      errors.add("Type is outside the supported NeqSim DEXPI 2.0 profile: " + type);
    }
  }

  private static void validateReferences(Element root, Map<String, Element> objectsById, List<String> errors) {
    NodeList references = root.getElementsByTagName("References");
    for (int i = 0; i < references.getLength(); i++) {
      Element reference = (Element) references.item(i);
      String values = reference.getAttribute("objects").trim();
      if (values.isEmpty()) {
        errors.add("References element has no objects value.");
        continue;
      }
      for (String value : values.split("\\s+")) {
        if (!value.startsWith("#") || !objectsById.containsKey(value.substring(1))) {
          errors.add("Dangling object reference: " + value);
        }
      }
    }
  }

  private static void validateEquipment(NodeList objects, List<String> errors, List<String> warnings) {
    for (int i = 0; i < objects.getLength(); i++) {
      Element object = (Element) objects.item(i);
      String type = object.getAttribute("type");
      if (!type.startsWith("Plant/ProcessEquipment.") || type.endsWith(".Nozzle")) {
        continue;
      }
      if (directStringData(object, "TagName") == null) {
        errors.add("Tagged process equipment has no TagName: " + object.getAttribute("id"));
      }
      if (!hasDirectComponents(object, "Nozzles")) {
        warnings.add("Process equipment has no Nozzles: " + object.getAttribute("id"));
      }
    }
  }

  private static void validatePipingReferences(Element root, Map<String, Element> objectsById, List<String> errors) {
    NodeList references = root.getElementsByTagName("References");
    for (int i = 0; i < references.getLength(); i++) {
      Element reference = (Element) references.item(i);
      String property = reference.getAttribute("property");
      if (!"SourceNode".equals(property) && !"TargetNode".equals(property)) {
        continue;
      }
      for (String value : reference.getAttribute("objects").trim().split("\\s+")) {
        Element target = value.startsWith("#") ? objectsById.get(value.substring(1)) : null;
        if (target != null && !"Plant/Piping.PipingNode".equals(target.getAttribute("type"))) {
          errors.add(property + " must reference Plant/Piping.PipingNode: " + value);
        }
      }
    }
  }

  private static boolean hasDirectComponents(Element object, String property) {
    for (Node child = object.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "Components".equals(((Element) child).getTagName())
          && property.equals(((Element) child).getAttribute("property"))) {
        return true;
      }
    }
    return false;
  }

  private static String directStringData(Element object, String property) {
    for (Node child = object.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (!(child instanceof Element) || !"Data".equals(((Element) child).getTagName())
          || !property.equals(((Element) child).getAttribute("property"))) {
        continue;
      }
      NodeList strings = ((Element) child).getElementsByTagName("String");
      return strings.getLength() == 0 ? null : strings.item(0).getTextContent();
    }
    return null;
  }
}
