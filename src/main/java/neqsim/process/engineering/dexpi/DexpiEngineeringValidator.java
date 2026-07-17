package neqsim.process.engineering.dexpi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import com.google.gson.GsonBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.DexpiXmlReader;

/** Performs secure structural, reference and NeqSim round-trip checks on an engineering DEXPI file. */
public final class DexpiEngineeringValidator {
  private DexpiEngineeringValidator() {
  }

  /** Validation result with machine-readable findings and object counts. */
  public static final class ValidationResult {
    private final List<Map<String, Object>> findings = new ArrayList<Map<String, Object>>();
    private final Map<String, Integer> objectCounts = new LinkedHashMap<String, Integer>();
    private boolean wellFormed;
    private boolean referencesResolved;
    private boolean roundTripReadable;
    private int importedUnitCount;
    private String officialXsdValidation = "NOT_PERFORMED_OFFICIAL_PROJECT_XSD_NOT_SUPPLIED";

    private void add(String severity, String code, String objectId, String message) {
      Map<String, Object> finding = new LinkedHashMap<String, Object>();
      finding.put("severity", severity);
      finding.put("code", code);
      finding.put("objectId", objectId);
      finding.put("message", message);
      findings.add(finding);
    }

    /** @return true when no error-severity findings exist */
    public boolean isValid() {
      for (Map<String, Object> finding : findings) {
        if ("ERROR".equals(finding.get("severity"))) {
          return false;
        }
      }
      return wellFormed && referencesResolved;
    }

    /** @return JSON-ready result map */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("valid", isValid());
      map.put("wellFormed", wellFormed);
      map.put("referencesResolved", referencesResolved);
      map.put("roundTripReadable", roundTripReadable);
      map.put("importedUnitCount", importedUnitCount);
      map.put("objectCounts", objectCounts);
      map.put("findings", findings);
      map.put("officialXsdValidation", officialXsdValidation);
      map.put("governanceNote",
          "Structural validation does not replace validation in the project's selected DEXPI authoring tool.");
      return map;
    }

    /** @return pretty JSON representation */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }
  }

  /**
   * Validates one generated DEXPI XML file.
   *
   * @param dexpiFile generated DEXPI file
   * @return structural and round-trip validation result
   */
  public static ValidationResult validate(Path dexpiFile) {
    ValidationResult result = new ValidationResult();
    if (dexpiFile == null || !Files.exists(dexpiFile)) {
      result.add("ERROR", "DEXPI-FILE-001", "", "DEXPI file does not exist");
      return result;
    }
    try {
      Document document = parse(dexpiFile);
      result.wellFormed = true;
      validateDocument(document, dexpiFile.toAbsolutePath().normalize().getParent(), result);
    } catch (Exception ex) {
      result.add("ERROR", "DEXPI-XML-001", "", "XML parsing failed: " + safeMessage(ex));
      return result;
    }
    try {
      ProcessSystem imported = DexpiXmlReader.read(dexpiFile.toFile());
      result.roundTripReadable = imported != null;
      result.importedUnitCount = imported == null ? 0 : imported.getUnitOperations().size();
      if (result.importedUnitCount == 0) {
        result.add("WARNING", "DEXPI-ROUNDTRIP-001", "", "NeqSim reader imported no process units");
      }
    } catch (Exception ex) {
      result.roundTripReadable = false;
      result.add("WARNING", "DEXPI-ROUNDTRIP-002", "", "NeqSim round-trip read failed: " + safeMessage(ex));
    }
    return result;
  }

  /**
   * Validates a generated file and, when supplied, the project's controlled DEXPI XSD.
   *
   * @param dexpiFile generated DEXPI file
   * @param xsdFile controlled schema file selected by the project
   * @return combined structural, XSD and round-trip validation result
   */
  public static ValidationResult validate(Path dexpiFile, Path xsdFile) {
    ValidationResult result = validate(dexpiFile);
    if (xsdFile == null || !Files.exists(xsdFile)) {
      result.officialXsdValidation = "NOT_PERFORMED_XSD_FILE_NOT_FOUND";
      result.add("ERROR", "DEXPI-XSD-001", "", "Controlled DEXPI XSD file does not exist");
      return result;
    }
    try {
      SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      schemaFactory.newSchema(xsdFile.toFile()).newValidator().validate(new StreamSource(dexpiFile.toFile()));
      result.officialXsdValidation = "PASSED";
    } catch (Exception ex) {
      result.officialXsdValidation = "FAILED";
      result.add("ERROR", "DEXPI-XSD-002", "", "Controlled XSD validation failed: " + safeMessage(ex));
    }
    return result;
  }

  /** Writes a validation result next to the exchange package. */
  public static Path write(ValidationResult result, Path outputFile) throws IOException {
    Files.write(outputFile, result.toJson().getBytes(StandardCharsets.UTF_8));
    return outputFile;
  }

  private static Document parse(Path file) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory.newDocumentBuilder().parse(file.toFile());
  }

  private static void validateDocument(Document document, Path packageDirectory, ValidationResult result) {
    Element root = document.getDocumentElement();
    if (root == null) {
      result.add("ERROR", "DEXPI-ROOT-001", "", "XML has no document element");
      return;
    }
    count(document, result, "Equipment");
    count(document, result, "PipingComponent");
    count(document, result, "ProcessInstrumentationFunction");
    count(document, result, "InstrumentationLoopFunction");
    count(document, result, "InformationFlow");

    Set<String> ids = new HashSet<String>();
    Set<String> references = new HashSet<String>();
    NodeList all = document.getElementsByTagName("*");
    for (int i = 0; i < all.getLength(); i++) {
      Node node = all.item(i);
      if (!(node instanceof Element)) {
        continue;
      }
      Element element = (Element) node;
      String id = element.getAttribute("ID");
      if (!id.isEmpty() && !ids.add(id)) {
        result.add("ERROR", "DEXPI-ID-001", id, "Duplicate ID");
      }
      String itemId = element.getAttribute("ItemID");
      if (!itemId.isEmpty()) {
        references.add(itemId);
      }
    }
    result.referencesResolved = true;
    for (String reference : references) {
      if (!ids.contains(reference)) {
        result.referencesResolved = false;
        result.add("ERROR", "DEXPI-REF-001", reference, "ItemID does not resolve to an ID in the document");
      }
    }
    if (!hasEngineeringDocumentReference(document, "EngineeringManifestDocument")
        || !hasEngineeringDocumentReference(document, "EngineeringCalculationsDocument")
        || !hasEngineeringDocumentReference(document, "CauseAndEffectDocument")) {
      result.add("ERROR", "DEXPI-ENG-001", "PlantInformation",
          "Engineering sidecar document references are incomplete");
    }
    if (result.objectCounts.get("Equipment").intValue() == 0) {
      result.add("ERROR", "DEXPI-EQUIPMENT-001", "", "No equipment objects were exported");
    }
    validateSidecarPaths(document, packageDirectory, result);
  }

  private static void validateSidecarPaths(Document document, Path packageDirectory, ValidationResult result) {
    NodeList attributes = document.getElementsByTagName("GenericAttribute");
    for (int i = 0; i < attributes.getLength(); i++) {
      Element attribute = (Element) attributes.item(i);
      String name = attribute.getAttribute("Name");
      String value = attribute.getAttribute("Value");
      if (!name.endsWith("Document") || value.trim().isEmpty() || "DexpiValidationDocument".equals(name)) {
        continue;
      }
      Path referenced = packageDirectory.resolve(value).normalize();
      if (!referenced.startsWith(packageDirectory)) {
        result.add("ERROR", "DEXPI-SIDECAR-001", name, "Sidecar path escapes the package directory");
      } else if (!Files.exists(referenced)) {
        result.add("ERROR", "DEXPI-SIDECAR-002", name, "Referenced sidecar does not exist: " + value);
      }
    }
  }

  private static void count(Document document, ValidationResult result, String name) {
    result.objectCounts.put(name, Integer.valueOf(document.getElementsByTagName(name).getLength()));
  }

  private static boolean hasEngineeringDocumentReference(Document document, String name) {
    NodeList attributes = document.getElementsByTagName("GenericAttribute");
    for (int i = 0; i < attributes.getLength(); i++) {
      Element attribute = (Element) attributes.item(i);
      if (name.equals(attribute.getAttribute("Name")) && !attribute.getAttribute("Value").trim().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static String safeMessage(Exception ex) {
    return ex.getMessage() == null || ex.getMessage().trim().isEmpty() ? ex.getClass().getSimpleName()
        : ex.getMessage();
  }
}
