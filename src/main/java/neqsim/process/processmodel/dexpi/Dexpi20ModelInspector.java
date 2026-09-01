package neqsim.process.processmodel.dexpi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Reads a deterministic semantic summary from native DEXPI 2.0 for regression and round-trip checks. */
public final class Dexpi20ModelInspector {
  private Dexpi20ModelInspector() {
  }

  /** Immutable native DEXPI model summary. */
  public static final class ModelSummary {
    private final Map<String, String> equipmentTypes;
    private final int pipingConnectionCount;
    private final int instrumentationFunctionCount;
    private final int offPageConnectorCount;
    private final int representationGroupCount;

    ModelSummary(Map<String, String> equipmentTypes, int pipingConnectionCount, int instrumentationFunctionCount,
        int offPageConnectorCount, int representationGroupCount) {
      this.equipmentTypes = Collections.unmodifiableMap(new LinkedHashMap<String, String>(equipmentTypes));
      this.pipingConnectionCount = pipingConnectionCount;
      this.instrumentationFunctionCount = instrumentationFunctionCount;
      this.offPageConnectorCount = offPageConnectorCount;
      this.representationGroupCount = representationGroupCount;
    }

    public Map<String, String> getEquipmentTypes() {
      return equipmentTypes;
    }

    public List<String> getEquipmentTags() {
      return Collections.unmodifiableList(new ArrayList<String>(equipmentTypes.keySet()));
    }

    public int getPipingConnectionCount() {
      return pipingConnectionCount;
    }

    public int getInstrumentationFunctionCount() {
      return instrumentationFunctionCount;
    }

    public int getOffPageConnectorCount() {
      return offPageConnectorCount;
    }

    public int getRepresentationGroupCount() {
      return representationGroupCount;
    }
  }

  /** Parses a native DEXPI document and extracts its exchange-significant content. */
  public static ModelSummary inspect(Path file) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      Document document = factory.newDocumentBuilder().parse(file.toFile());
      Map<String, String> equipment = new LinkedHashMap<String, String>();
      int pipes = 0;
      int functions = 0;
      int boundaries = 0;
      int representations = 0;
      NodeList objects = document.getElementsByTagName("Object");
      for (int i = 0; i < objects.getLength(); i++) {
        Element object = (Element) objects.item(i);
        String type = object.getAttribute("type");
        if (type.startsWith("Plant/ProcessEquipment.") && !type.endsWith(".Nozzle")) {
          String tag = directStringData(object, "TagName");
          if (tag != null) {
            equipment.put(tag, type);
          }
        } else if ("Plant/Piping.Pipe".equals(type)) {
          pipes++;
        } else if ("Plant/Instrumentation.ProcessInstrumentationFunction".equals(type)) {
          functions++;
        } else if (type.endsWith("PipeOffPageConnector")) {
          boundaries++;
        } else if ("Core/Diagram.RepresentationGroup".equals(type)) {
          representations++;
        }
      }
      return new ModelSummary(equipment, pipes, functions, boundaries, representations);
    } catch (ParserConfigurationException ex) {
      throw new IOException("Could not configure native DEXPI inspection", ex);
    } catch (SAXException ex) {
      throw new IOException("Could not parse native DEXPI document", ex);
    }
  }

  private static String directStringData(Element object, String property) {
    for (Node child = object.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "Data".equals(((Element) child).getTagName())
          && property.equals(((Element) child).getAttribute("property"))) {
        NodeList strings = ((Element) child).getElementsByTagName("String");
        return strings.getLength() == 0 ? null : strings.item(0).getTextContent();
      }
    }
    return null;
  }
}
