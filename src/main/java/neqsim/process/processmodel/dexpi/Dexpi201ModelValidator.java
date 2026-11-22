package neqsim.process.processmodel.dexpi;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Checks supported native exchanges against pinned official DEXPI 2.0.1 model declarations. Checks class and property
 * identities, inheritance, property kinds, multiplicities, enumeration references, and typed object relationships. This
 * is not CAE qualification.
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class Dexpi201ModelValidator {
  private static volatile Map<String, Element> definitions;
  private static final String[] MODELS = {"Core", "Plant", "Process", "Builtin"};
  private static final String[] HASHES = {"7136f8440c08c8bf7759c4e9eb614b046cc3196f2f45695122629a2020e61e4a",
      "0a5175535c1263464fee653c7b99f38eb810c15824f84155748eac165f50edbf",
      "b4d6a7e35fe62e6fd81153cf1f4c54e3f08718802d96ac21cdd4167d5753d341",
      "75bb247a5f1ddcd12e5df74b6acf6877496b8962d1332ec4184e0aaabdfa485e"};

  /** Prevents instantiation. */
  private Dexpi201ModelValidator() {
  }

  /**
   * Validates emitted objects using authentic offline model definitions.
   *
   * @param document exchange document
   * @return deterministic model-definition errors; empty means the checked scope passed
   */
  public static List<String> validate(Document document) {
    List<String> errors = new ArrayList<String>();
    try {
      Map<String, Element> model = loadDefinitions();
      Map<String, Element> objects = new LinkedHashMap<String, Element>();
      NodeList nodes = document.getElementsByTagName("Object");
      for (int index = 0; index < nodes.getLength(); index++) {
        Element object = (Element) nodes.item(index);
        if (!object.getAttribute("id").isEmpty()) {
          objects.put(object.getAttribute("id"), object);
        }
      }
      for (int index = 0; index < nodes.getLength(); index++) {
        Element object = (Element) nodes.item(index);
        String type = object.getAttribute("type");
        Element definition = model.get(type);
        if (definition == null || !"ConcreteClass".equals(definition.getTagName())) {
          errors.add("DEXPI 2.0.1 object type is not an official concrete class: " + type);
          continue;
        }
        Map<String, Element> properties = properties(type, model, new HashSet<String>());
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (Element value : children(object)) {
          String name = value.getAttribute("property");
          Element property = properties.get(name);
          if (property == null) {
            errors.add("Unknown DEXPI 2.0.1 property " + type + "." + name);
            continue;
          }
          String expectedKind = "DataProperty".equals(property.getTagName()) ? "Data"
              : "ReferenceProperty".equals(property.getTagName()) ? "References" : "Components";
          if (!expectedKind.equals(value.getTagName())) {
            errors.add("Wrong property kind for " + type + "." + name);
          }
          int count = "References".equals(value.getTagName())
              ? value.getAttribute("objects").trim().split("\\s+").length
              : children(value).size();
          counts.put(name, Integer.valueOf((counts.containsKey(name) ? counts.get(name).intValue() : 0) + count));
          validateClassValues(type, property, value, model, objects, errors);
        }
        for (Map.Entry<String, Element> property : properties.entrySet()) {
          int count = counts.containsKey(property.getKey()) ? counts.get(property.getKey()).intValue() : 0;
          int lower = integer(property.getValue().getAttribute("lower"), 0);
          int upper = integer(property.getValue().getAttribute("upper"), Integer.MAX_VALUE);
          if (count < lower || count > upper) {
            errors.add("DEXPI 2.0.1 multiplicity violation " + type + "." + property.getKey() + ": " + count);
          }
        }
      }
      validateNodeOwnership(objects, errors);
      NodeList references = document.getElementsByTagName("DataReference");
      for (int index = 0; index < references.getLength(); index++) {
        String name = ((Element) references.item(index)).getAttribute("data");
        Element definition = model.get(name);
        if (definition == null || !("EnumerationLiteral".equals(definition.getTagName())
            || "SingletonValue".equals(definition.getTagName()))) {
          errors.add("Unknown official DEXPI 2.0.1 data reference: " + name);
        }
      }
    } catch (IOException exception) {
      errors.add(exception.getMessage());
    }
    return Collections.unmodifiableList(errors);
  }

  /**
   * Checks that a piping endpoint references the actual owner of its piping node.
   *
   * @param objects instance identity index
   * @param errors collected failures
   */
  private static void validateNodeOwnership(Map<String, Element> objects, List<String> errors) {
    for (Element object : objects.values()) {
      for (String direction : new String[] {"Source", "Target"}) {
        String item = "";
        String nodeId = "";
        for (Element property : children(object)) {
          if ("References".equals(property.getTagName())) {
            if ((direction + "Item").equals(property.getAttribute("property"))) {
              item = property.getAttribute("objects");
            } else if ((direction + "Node").equals(property.getAttribute("property"))) {
              nodeId = property.getAttribute("objects");
            }
          }
        }
        boolean physicalPipe = "Plant/Piping.Pipe".equals(object.getAttribute("type"));
        if (physicalPipe && (!item.startsWith("#") || !nodeId.startsWith("#"))) {
          errors.add("DEXPI 2.0.1 supported physical pipe requires " + direction + "Item and " + direction + "Node: "
              + object.getAttribute("id"));
        }
        if (item.startsWith("#") && nodeId.startsWith("#")) {
          Element node = objects.get(nodeId.substring(1));
          if (node != null && node.getParentNode() != null && node.getParentNode().getParentNode() instanceof Element) {
            Element owner = (Element) node.getParentNode().getParentNode();
            if (!item.equals("#" + owner.getAttribute("id"))) {
              errors.add("DEXPI 2.0.1 piping node is not owned by " + direction + "Item: " + object.getAttribute("id"));
            }
          }
        }
      }
    }
  }

  /**
   * Checks class-valued properties against the declared inheritance hierarchy.
   *
   * @param owner owner type
   * @param property official property declaration
   * @param value exchange property value
   * @param model official definitions
   * @param objects instance identity index
   * @param errors collected failures
   */
  private static void validateClassValues(String owner, Element property, Element value, Map<String, Element> model,
      Map<String, Element> objects, List<String> errors) {
    if ("DataProperty".equals(property.getTagName())) {
      return;
    }
    NodeList expected = property.getElementsByTagName("ClassReference");
    List<Element> targets = new ArrayList<Element>();
    if ("References".equals(value.getTagName())) {
      for (String reference : value.getAttribute("objects").trim().split("\\s+")) {
        if (reference.startsWith("#") && objects.containsKey(reference.substring(1))) {
          targets.add(objects.get(reference.substring(1)));
        }
      }
    } else {
      for (Element child : children(value)) {
        if ("Object".equals(child.getTagName())) {
          targets.add(child);
        } else if ("ObjectReference".equals(child.getTagName())) {
          String reference = child.getAttribute("object");
          if (reference.startsWith("#") && objects.containsKey(reference.substring(1))) {
            targets.add(objects.get(reference.substring(1)));
          }
        }
      }
    }
    for (Element target : targets) {
      boolean compatible = false;
      for (int index = 0; index < expected.getLength(); index++) {
        String required = qualify(((Element) expected.item(index)).getAttribute("type"),
            property.getUserData("dexpiModelPrefix") + "/_");
        compatible |= isSubtype(target.getAttribute("type"), required, model, new HashSet<String>());
      }
      if (!compatible) {
        errors.add("DEXPI 2.0.1 relationship type mismatch " + owner + "." + property.getAttribute("name") + " -> "
            + target.getAttribute("type"));
      }
    }
  }

  /**
   * Collects all inherited property declarations.
   *
   * @param type qualified class name
   * @param model official definitions
   * @param visited cycle guard
   * @return inherited properties in deterministic order
   */
  private static Map<String, Element> properties(String type, Map<String, Element> model, Set<String> visited) {
    Map<String, Element> result = new LinkedHashMap<String, Element>();
    Element definition = model.get(type);
    if (definition == null || !visited.add(type)) {
      return result;
    }
    for (String parent : definition.getAttribute("superTypes").trim().split("\\s+")) {
      if (!parent.isEmpty()) {
        result.putAll(properties(qualify(parent, type), model, visited));
      }
    }
    for (Element child : children(definition)) {
      if (child.getTagName().endsWith("Property")) {
        result.put(child.getAttribute("name"), child);
      }
    }
    return result;
  }

  /**
   * Resolves official inheritance without relying on string suffixes.
   *
   * @param actual instance type
   * @param expected required type
   * @param model official definitions
   * @param visited cycle guard
   * @return whether actual equals or derives from expected
   */
  private static boolean isSubtype(String actual, String expected, Map<String, Element> model, Set<String> visited) {
    if (actual.equals(expected)) {
      return true;
    }
    Element definition = model.get(actual);
    if (definition == null || !visited.add(actual)) {
      return false;
    }
    for (String parent : definition.getAttribute("superTypes").trim().split("\\s+")) {
      if (!parent.isEmpty() && isSubtype(qualify(parent, actual), expected, model, visited)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Resolves a model-local reference.
   *
   * @param reference official reference
   * @param owner qualified owning type
   * @return qualified model reference
   */
  private static String qualify(String reference, String owner) {
    return reference.startsWith("/") ? owner.substring(0, owner.indexOf('/')) + reference : reference;
  }

  /**
   * Parses optional multiplicity bounds.
   *
   * @param value bound text
   * @param fallback omitted-bound value
   * @return parsed bound
   */
  private static int integer(String value, int fallback) {
    return value.isEmpty() ? fallback : Integer.parseInt(value);
  }

  /**
   * Lists direct element children.
   *
   * @param parent owner node
   * @return direct children
   */
  private static List<Element> children(Element parent) {
    List<Element> result = new ArrayList<Element>();
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element) {
        result.add((Element) child);
      }
    }
    return result;
  }

  /**
   * Loads and verifies immutable offline specification definitions once.
   *
   * @return cached official definitions
   * @throws IOException if a definition is absent, altered or malformed
   */
  private static synchronized Map<String, Element> loadDefinitions() throws IOException {
    if (definitions != null) {
      return definitions;
    }
    Map<String, Element> result = new LinkedHashMap<String, Element>();
    try {
      for (int index = 0; index < MODELS.length; index++) {
        String resource = "/dexpi/2.0.1/" + MODELS[index] + ".xml";
        InputStream input = Dexpi201ModelValidator.class.getResourceAsStream(resource);
        if (input == null) {
          throw new IOException("Missing official DEXPI 2.0.1 definition: " + resource);
        }
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try {
          byte[] buffer = new byte[8192];
          int count;
          while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
          }
        } finally {
          input.close();
        }
        byte[] bytes = output.toByteArray();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest(bytes)) {
          hash.append(String.format("%02x", Integer.valueOf(value & 0xff)));
        }
        if (!HASHES[index].equals(hash.toString())) {
          throw new IOException("Official DEXPI 2.0.1 definition fingerprint mismatch: " + resource);
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document parsed = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes));
        collect(parsed.getDocumentElement(), MODELS[index] + "/", result);
      }
    } catch (IOException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IOException("Could not load official DEXPI 2.0.1 model definitions", exception);
    }
    definitions = Collections.unmodifiableMap(result);
    return definitions;
  }

  /**
   * Indexes nested official names, including enumeration literals.
   *
   * @param parent declaration owner
   * @param prefix qualified name prefix
   * @param result declaration index
   */
  private static void collect(Element parent, String prefix, Map<String, Element> result) {
    for (Element child : children(parent)) {
      String name = child.getAttribute("name");
      if (!name.isEmpty()) {
        String qualified = prefix + name;
        child.setUserData("dexpiModelPrefix", qualified.substring(0, qualified.indexOf('/')), null);
        result.put(qualified, child);
        collect(child, qualified + ".", result);
      }
    }
  }
}
