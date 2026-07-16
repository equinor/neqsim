package neqsim.process.engineering.dexpi;

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
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.validation.EngineeringSchemaCatalog;

/** Reimports exported DEXPI representations and compares their stable equipment identities with the canonical graph. */
public final class EngineeringDexpiRoundTripQualifier {
  private EngineeringDexpiRoundTripQualifier() {
  }

  /** Performs an internal structural round-trip without claiming qualification in a commercial CAE product. */
  public static Map<String, Object> qualify(EngineeringGraph graph, Path nativeDexpi, Path proteus, Path pyDexpi)
      throws IOException {
    if (graph == null || nativeDexpi == null || proteus == null || pyDexpi == null) {
      throw new IllegalArgumentException("graph and all DEXPI exchange files are required");
    }
    Set<String> expectedEquipment = new LinkedHashSet<String>();
    for (EngineeringNode node : graph.getNodes().values()) {
      if (node.getKind() == EngineeringNode.Kind.EQUIPMENT) {
        expectedEquipment.add(node.getExternalKey());
      }
    }
    List<Map<String, Object>> formats = new ArrayList<Map<String, Object>>();
    formats.add(qualifyFormat("DEXPI_2_0_NATIVE", nativeDexpi, expectedEquipment, true));
    formats.add(qualifyFormat("PROTEUS_4_1_1", proteus, expectedEquipment, false));
    formats.add(qualifyFormat("PYDEXPI_PROTEUS", pyDexpi, expectedEquipment, false));
    boolean passed = true;
    for (Map<String, Object> format : formats) {
      passed &= "PASSED".equals(format.get("status"));
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", EngineeringSchemaCatalog.DEXPI_ROUNDTRIP_REPORT);
    result.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.DEXPI_ROUNDTRIP_REPORT));
    result.put("projectId", graph.getProjectId());
    result.put("revision", graph.getRevision());
    result.put("graphFingerprint", graph.toMap().get("fingerprint"));
    result.put("qualificationScope", "INTERNAL_STRUCTURAL_EXPORT_REIMPORT");
    result.put("status", passed ? "INTERNAL_STRUCTURAL_ROUNDTRIP_PASSED" : "INTERNAL_STRUCTURAL_ROUNDTRIP_FAILED");
    result.put("formats", formats);
    result.put("commercialCaeStatus", "QUALIFICATION_REQUIRED");
    result.put("commercialCaeEvidenceRequired",
        "Named product/version, successful import, exported round-trip file and accountable difference review");
    return result;
  }

  private static Map<String, Object> qualifyFormat(String profile, Path file, Set<String> expectedEquipment,
      boolean nativeFormat) throws IOException {
    Document document = parse(file);
    Extraction extraction = nativeFormat ? extractNative(document) : extractProteus(document);
    List<String> missing = difference(expectedEquipment, extraction.equipmentTags);
    List<String> unexpected = difference(extraction.equipmentTags, expectedEquipment);
    boolean passed = missing.isEmpty() && extraction.duplicateIdentityCount == 0
        && extraction.unresolvedReferences.isEmpty();
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("profile", profile);
    result.put("file", file.getFileName().toString());
    result.put("expectedEquipmentTags", sorted(expectedEquipment));
    result.put("reimportedEquipmentTags", sorted(extraction.equipmentTags));
    result.put("missingEquipmentTags", missing);
    result.put("additionalEquipmentTags", unexpected);
    result.put("objectIdentityCount", Integer.valueOf(extraction.identityCount));
    result.put("duplicateIdentityCount", Integer.valueOf(extraction.duplicateIdentityCount));
    result.put("referenceCount", Integer.valueOf(extraction.referenceCount));
    result.put("unresolvedReferences", sorted(extraction.unresolvedReferences));
    result.put("status", passed ? "PASSED" : "FAILED");
    return result;
  }

  private static Extraction extractNative(Document document) {
    Extraction result = collectIdentitiesAndReferences(document, "id", Arrays.asList("objects"));
    NodeList objects = document.getElementsByTagName("Object");
    for (int i = 0; i < objects.getLength(); i++) {
      Element object = (Element) objects.item(i);
      String type = object.getAttribute("type");
      if (type.startsWith("Plant/ProcessEquipment.") && !type.endsWith(".Nozzle")) {
        String tag = directDataValue(object, "TagName");
        if (!tag.isEmpty()) {
          result.equipmentTags.add(tag);
        }
      }
    }
    return result;
  }

  private static Extraction extractProteus(Document document) {
    Extraction result = collectIdentitiesAndReferences(document, "ID", Arrays.asList("ItemID", "FromID", "ToID"));
    NodeList equipment = document.getElementsByTagName("Equipment");
    for (int i = 0; i < equipment.getLength(); i++) {
      Element item = (Element) equipment.item(i);
      if (!item.hasAttribute("ComponentClass")) {
        continue;
      }
      String tag = genericAttributeValue(item, "TagName");
      if (tag.isEmpty() && item.getAttribute("ID").startsWith("ID-")) {
        tag = item.getAttribute("ID").substring(3);
      }
      if (!tag.isEmpty()) {
        result.equipmentTags.add(tag);
      }
    }
    return result;
  }

  private static Extraction collectIdentitiesAndReferences(Document document, String identityAttribute,
      List<String> referenceAttributes) {
    Extraction result = new Extraction();
    Set<String> identities = new LinkedHashSet<String>();
    List<String> references = new ArrayList<String>();
    NodeList elements = document.getElementsByTagName("*");
    for (int i = 0; i < elements.getLength(); i++) {
      Element element = (Element) elements.item(i);
      if (element.hasAttribute(identityAttribute)) {
        String identity = element.getAttribute(identityAttribute).trim();
        if (!identity.isEmpty() && !identities.add(identity)) {
          result.duplicateIdentityCount++;
        }
      }
      for (String attribute : referenceAttributes) {
        if (!element.hasAttribute(attribute)) {
          continue;
        }
        for (String reference : element.getAttribute(attribute).trim().split("\\s+")) {
          if (!reference.isEmpty()) {
            references.add(reference.startsWith("#") ? reference.substring(1) : reference);
          }
        }
      }
    }
    result.identityCount = identities.size();
    result.referenceCount = references.size();
    for (String reference : references) {
      if (!identities.contains(reference)) {
        result.unresolvedReferences.add(reference);
      }
    }
    return result;
  }

  private static String directDataValue(Element object, String property) {
    NodeList children = object.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child instanceof Element && "Data".equals(child.getNodeName())
          && property.equals(((Element) child).getAttribute("property"))) {
        return child.getTextContent().trim();
      }
    }
    return "";
  }

  private static String genericAttributeValue(Element root, String name) {
    NodeList attributes = root.getElementsByTagName("GenericAttribute");
    for (int i = 0; i < attributes.getLength(); i++) {
      Element attribute = (Element) attributes.item(i);
      if (name.equals(attribute.getAttribute("Name"))) {
        return attribute.getAttribute("Value").trim();
      }
    }
    return "";
  }

  private static Document parse(Path file) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      return factory.newDocumentBuilder().parse(file.toFile());
    } catch (SAXException ex) {
      throw new IOException("Unable to reimport " + file.getFileName() + ": " + ex.getMessage(), ex);
    } catch (Exception ex) {
      throw new IOException("Unable to configure secure DEXPI reimport: " + ex.getMessage(), ex);
    }
  }

  private static List<String> difference(Set<String> left, Set<String> right) {
    Set<String> values = new LinkedHashSet<String>(left);
    values.removeAll(right);
    return sorted(values);
  }

  private static List<String> sorted(Set<String> values) {
    List<String> result = new ArrayList<String>(values);
    Collections.sort(result);
    return result;
  }

  private static final class Extraction {
    private final Set<String> equipmentTags = new LinkedHashSet<String>();
    private final Set<String> unresolvedReferences = new LinkedHashSet<String>();
    private int identityCount;
    private int duplicateIdentityCount;
    private int referenceCount;
  }
}
