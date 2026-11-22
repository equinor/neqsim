package neqsim.process.processmodel.dexpi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Locates plant items ({@code Equipment} and {@code PipingComponent}) in a Proteus / DEXPI XML document.
 *
 * <p>
 * Two layouts are supported:
 * </p>
 * <ul>
 * <li><b>Standard Proteus form</b> (DEXPI 1.3/1.4, {@link DexpiXmlWriter} output, the DEXPI reference examples): the
 * {@code <Equipment>} or {@code <PipingComponent>} element is the plant item itself and carries its own
 * {@code ComponentClass}. Its children ({@code Nozzle}, {@code Label}, {@code GenericAttributes}, {@code Position},
 * ...) are not plant items. Nested elements of the same tag that carry a {@code ComponentClass} (for example
 * {@code Chamber}, {@code Impeller} or {@code TubeBundle} inside a pump or heat exchanger) are sub-equipment of the
 * enclosing item and are not returned as separate items.</li>
 * <li><b>Wrapper form</b>: the {@code <Equipment>} or {@code <PipingComponent>} element has no {@code ComponentClass}
 * and each child element is a plant item, for example {@code <Equipment><CentrifugalPump ComponentClass="..."/>
 * </Equipment>}.</li>
 * </ul>
 *
 * <p>
 * Elements inside a {@code ShapeCatalogue} are symbol definitions, not plant items, and are always ignored.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiPlantItems {
  /** Child element names that never represent a plant item. */
  private static final Set<String> NON_ITEM_CHILD_TAGS = Collections
      .unmodifiableSet(new HashSet<String>(Arrays.asList("Nozzle", "Label", "GenericAttributes", "GenericAttribute",
          "Position", "Extent", "Presentation", "PersistentID", "Text", "Association", "ConnectionPoints", "Node",
          "Shape", "Line", "PolyLine", "Circle", "Ellipse", "CenterLine", "Description", "History")));

  private DexpiPlantItems() {
  }

  /**
   * Returns the candidate plant-item elements for the given tag in document order.
   *
   * <p>
   * In the standard form the returned element is the {@code Equipment}/{@code PipingComponent} element itself. In the
   * wrapper form the returned elements are the children of the wrapper; such children may lack a
   * {@code ComponentClass}, so callers decide how to report them.
   * </p>
   *
   * @param document the parsed DEXPI document
   * @param tagName {@code "Equipment"} or {@code "PipingComponent"}
   * @return candidate plant-item elements, never {@code null}
   */
  public static List<Element> findCandidates(Document document, String tagName) {
    List<Element> result = new ArrayList<Element>();
    NodeList nodes = document.getElementsByTagName(tagName);
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element element = (Element) node;
      if (isInShapeCatalogue(element)) {
        continue;
      }
      if (hasComponentClass(element)) {
        // Standard form: the element is the plant item, unless it is sub-equipment of an enclosing item.
        if (!hasPlantItemAncestor(element, tagName)) {
          result.add(element);
        }
        continue;
      }
      // Wrapper form: the children are the plant items.
      NodeList children = element.getChildNodes();
      for (int j = 0; j < children.getLength(); j++) {
        Node child = children.item(j);
        if (child.getNodeType() != Node.ELEMENT_NODE) {
          continue;
        }
        Element childElement = (Element) child;
        String childTag = localName(childElement);
        if (tagName.equals(childTag)) {
          // Visited on its own by the outer loop.
          continue;
        }
        if (NON_ITEM_CHILD_TAGS.contains(childTag)) {
          continue;
        }
        result.add(childElement);
      }
    }
    return result;
  }

  /**
   * Returns whether the element is a nozzle, label or other non-item child of a plant item.
   *
   * @param element the element to test
   * @return {@code true} if the element name never represents a plant item
   */
  public static boolean isNonItemElement(Element element) {
    return NON_ITEM_CHILD_TAGS.contains(localName(element));
  }

  private static boolean hasComponentClass(Element element) {
    String componentClass = element.getAttribute("ComponentClass");
    return componentClass != null && !componentClass.trim().isEmpty();
  }

  private static boolean hasPlantItemAncestor(Element element, String tagName) {
    Node parent = element.getParentNode();
    while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
      Element parentElement = (Element) parent;
      if (tagName.equals(localName(parentElement)) && hasComponentClass(parentElement)) {
        return true;
      }
      parent = parent.getParentNode();
    }
    return false;
  }

  private static boolean isInShapeCatalogue(Element element) {
    Node parent = element.getParentNode();
    while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
      if ("ShapeCatalogue".equals(localName((Element) parent))) {
        return true;
      }
      parent = parent.getParentNode();
    }
    return false;
  }

  private static String localName(Element element) {
    String name = element.getLocalName();
    if (name == null) {
      name = element.getTagName();
      int colon = name.indexOf(':');
      if (colon >= 0) {
        name = name.substring(colon + 1);
      }
    }
    return name;
  }
}
