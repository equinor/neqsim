package neqsim.process.processmodel.dexpi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import neqsim.process.equipment.EquipmentEnum;

/**
 * Material-flow graph extracted from a Proteus-compatible DEXPI P&amp;ID for building a runnable simulation.
 *
 * <p>
 * Unlike {@link DexpiTopologyResolver}, which collapses every piping component to produce an equipment-only topology,
 * this graph keeps in-line throttling valves that carry an operating outlet pressure as real flowsheet nodes, because
 * they change the process state. Other piping components (tees, reducers, isolation valves without an operating
 * pressure, strainers) are collapsed as pass-through items. Branches that pass through a pressure-safety device or a
 * blind are excluded from the material flowsheet, because they carry no flow at normal operation.
 * </p>
 *
 * <p>
 * Each edge keeps the source nozzle, which the builder uses to select the correct outlet of multi-outlet equipment
 * (separator gas versus liquid, splitter branch). The port is resolved from, in order: the {@code PortRole} generic
 * attribute written by {@link DexpiXmlWriter}; the type of the downstream unit (compressor implies gas, pump implies
 * liquid); the vertical nozzle position (highest outlet is gas); and finally document order.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
final class DexpiFlowsheetGraph {
  /** Generic attribute set written on nozzles by {@link DexpiXmlWriter}. */
  static final String PORT_ATTRIBUTE_SET = "NeqSimPortAttributes";
  /** Generic attribute holding the process role of a nozzle. */
  static final String PORT_ROLE = "PortRole";

  /** Generic attribute on a piping segment; {@code false} marks a run without flow in normal operation. */
  static final String NORMAL_FLOW = "NormalFlow";
  /** Generic attribute holding the zero-based outlet index of a nozzle. */
  static final String PORT_INDEX = "PortIndex";

  /** Port roles written on nozzles. */
  static final String ROLE_INLET = "Inlet";
  static final String ROLE_OUTLET = "Outlet";
  static final String ROLE_GAS = "GasOutlet";
  static final String ROLE_LIQUID = "LiquidOutlet";
  static final String ROLE_OIL = "OilOutlet";
  static final String ROLE_WATER = "WaterOutlet";

  private static final Set<String> NO_FLOW_COMPONENTS = new HashSet<>(java.util.Arrays.asList("PressureSafetyValve",
      "PressureReliefValve", "SpringLoadedGlobeSafetyValve", "SpringLoadedAngleGlobeSafetyValve",
      "PilotOperatedSafetyValve", "SafetyValveOrFitting", "RuptureDisk", "BlindFlange", "SpectacleBlind", "LineBlind"));

  /** One directed material connection between two flowsheet nodes. */
  static final class Edge {
    final String source;
    final String target;
    final String sourceNozzleId;
    final Element segment;

    /**
     * Creates one material edge.
     *
     * @param source source node id
     * @param target target node id
     * @param sourceNozzleId source nozzle id, or null
     * @param segment piping segment carrying the connection
     */
    Edge(String source, String target, String sourceNozzleId, Element segment) {
      this.source = source;
      this.target = target;
      this.sourceNozzleId = sourceNozzleId;
      this.segment = segment;
    }
  }

  /** Nozzle facts used for port resolution. */
  static final class NozzleInfo {
    final String id;
    final String owner;
    final int documentIndex;
    final Double y;
    final String role;
    final Integer portIndex;

    /**
     * Creates nozzle facts.
     *
     * @param id nozzle id
     * @param owner owning node id
     * @param documentIndex document order index
     * @param y vertical position, or null when absent
     * @param role port role, or null
     * @param portIndex outlet index, or null
     */
    NozzleInfo(String id, String owner, int documentIndex, Double y, String role, Integer portIndex) {
      this.id = id;
      this.owner = owner;
      this.documentIndex = documentIndex;
      this.y = y;
      this.role = role;
      this.portIndex = portIndex;
    }
  }

  /** Flowsheet nodes in document order: equipment ids and in-line valve ids. */
  final Map<String, Element> nodes = new LinkedHashMap<>();
  /** Mapped equipment type of every node. */
  final Map<String, EquipmentEnum> nodeTypes = new HashMap<>();
  /** Material edges after pass-through collapse. */
  final List<Edge> edges = new ArrayList<>();
  /** Nozzle facts by nozzle id. */
  final Map<String, NozzleInfo> nozzles = new HashMap<>();
  /** Human-readable notes on dropped or collapsed content, in the form CODE|elementId|message. */
  final List<String> notes = new ArrayList<>();

  private DexpiFlowsheetGraph() {
  }

  /**
   * Extracts the material-flow graph from a parsed Proteus-compatible document.
   *
   * @param document parsed DEXPI document
   * @param topology equipment topology from {@link DexpiTopologyResolver}
   * @param equipmentTypes mapped equipment type for each supported equipment id
   * @param pipingMapping DEXPI piping component class to equipment type mapping
   * @return the extracted graph
   */
  static DexpiFlowsheetGraph extract(Document document, DexpiTopologyResolver.ResolvedTopology topology,
      Map<String, EquipmentEnum> equipmentTypes, Map<String, EquipmentEnum> pipingMapping) {
    DexpiFlowsheetGraph graph = new DexpiFlowsheetGraph();
    Map<String, String> owner = new HashMap<>();
    Set<String> passThrough = new LinkedHashSet<>();
    Set<String> noFlow = new HashSet<>();
    int[] order = new int[] {0};

    for (Map.Entry<String, Element> entry : topology.getEquipmentElements().entrySet()) {
      String id = entry.getKey();
      EquipmentEnum type = equipmentTypes.get(id);
      owner.put(id, id);
      if (type == null) {
        passThrough.add(id);
      } else {
        graph.nodes.put(id, entry.getValue());
        graph.nodeTypes.put(id, type);
      }
      graph.collectNozzles(entry.getValue(), id, owner, order);
    }

    NodeList components = document.getElementsByTagName("PipingComponent");
    for (int i = 0; i < components.getLength(); i++) {
      Element component = (Element) components.item(i);
      String id = component.getAttribute("ID");
      if (id == null || id.isEmpty()) {
        continue;
      }
      String componentClass = component.getAttribute("ComponentClass");
      owner.put(id, id);
      graph.collectNozzles(component, id, owner, order);
      EquipmentEnum mapped = pipingMapping.get(componentClass);
      if (NO_FLOW_COMPONENTS.contains(componentClass)) {
        noFlow.add(id);
      } else if (mapped == EquipmentEnum.ThrottlingValve
          && attributeValue(component, "OperatingPressureValue") != null) {
        graph.nodes.put(id, component);
        graph.nodeTypes.put(id, EquipmentEnum.ThrottlingValve);
      } else {
        passThrough.add(id);
      }
    }

    // Raw edges from every Connection, plus implicit edges for valves that only sit inside a segment.
    List<Edge> raw = new ArrayList<>();
    NodeList segments = document.getElementsByTagName("PipingNetworkSegment");
    for (int i = 0; i < segments.getLength(); i++) {
      Element segment = (Element) segments.item(i);
      if ("false".equalsIgnoreCase(attributeValue(segment, NORMAL_FLOW))) {
        graph.notes.add("DEXPI_BUILD_NO_FLOW_BRANCH_EXCLUDED|" + segment.getAttribute("ID")
            + "|pipe run marked NormalFlow=false (drain, flare, utility) excluded from the steady-state flowsheet");
        continue;
      }
      for (Element connection : directChildren(segment, "Connection")) {
        String from = connection.getAttribute("FromID");
        String to = connection.getAttribute("ToID");
        String source = owner.get(from);
        String target = owner.get(to);
        if (source == null || target == null) {
          graph.notes.add("DEXPI_BUILD_CONNECTION_UNRESOLVED|" + segment.getAttribute("ID") + "|connection " + from
              + " -> " + to + " does not end at a known nozzle or item (off-page or unsupported)");
          continue;
        }
        if (!source.equals(target)) {
          raw.add(new Edge(source, target, graph.nozzles.containsKey(from) ? from : null, segment));
        }
      }
    }

    // Branches through safety devices and blinds carry no flow at normal operation.
    Set<String> blocked = new HashSet<>(noFlow);
    for (String id : noFlow) {
      graph.notes.add("DEXPI_BUILD_NO_FLOW_BRANCH_EXCLUDED|" + id
          + "|safety device or blind excluded from the steady-state material flowsheet");
    }
    raw.removeIf(edge -> blocked.contains(edge.source) || blocked.contains(edge.target));

    // Collapse pass-through items (tees, isolation valves, reducers, unsupported equipment).
    for (String id : passThrough) {
      List<Edge> incoming = new ArrayList<>();
      List<Edge> outgoing = new ArrayList<>();
      for (Edge edge : raw) {
        if (edge.target.equals(id)) {
          incoming.add(edge);
        } else if (edge.source.equals(id)) {
          outgoing.add(edge);
        }
      }
      raw.removeAll(incoming);
      raw.removeAll(outgoing);
      for (Edge in : incoming) {
        for (Edge out : outgoing) {
          if (!in.source.equals(out.target)) {
            raw.add(new Edge(in.source, out.target, in.sourceNozzleId, out.segment));
          }
        }
      }
      if (!incoming.isEmpty() || !outgoing.isEmpty()) {
        graph.notes.add("DEXPI_BUILD_PASS_THROUGH|" + id + "|item has no flowsheet model and was collapsed");
      }
    }

    Set<String> seen = new HashSet<>();
    for (Edge edge : raw) {
      if (graph.nodes.containsKey(edge.source) && graph.nodes.containsKey(edge.target)
          && seen.add(edge.source + "\u0000" + edge.target + "\u0000" + edge.sourceNozzleId)) {
        graph.edges.add(edge);
      }
    }
    return graph;
  }

  /**
   * Returns incoming edges of a node.
   *
   * @param id node id
   * @return incoming edges in extraction order
   */
  List<Edge> incoming(String id) {
    List<Edge> result = new ArrayList<>();
    for (Edge edge : edges) {
      if (edge.target.equals(id)) {
        result.add(edge);
      }
    }
    return result;
  }

  /**
   * Returns outgoing edges of a node.
   *
   * @param id node id
   * @return outgoing edges in extraction order
   */
  List<Edge> outgoing(String id) {
    List<Edge> result = new ArrayList<>();
    for (Edge edge : edges) {
      if (edge.source.equals(id)) {
        result.add(edge);
      }
    }
    return result;
  }

  /**
   * Resolves which outlet a connection leaves from on a multi-outlet unit.
   *
   * @param edge the outgoing edge
   * @param outletCount number of outlets on the source unit (2 for a separator, 3 for a three-phase separator)
   * @return zero-based outlet index; for separators 0 is gas, 1 liquid or oil, 2 water
   */
  int resolveOutletIndex(Edge edge, int outletCount) {
    NozzleInfo nozzle = edge.sourceNozzleId == null ? null : nozzles.get(edge.sourceNozzleId);
    if (nozzle != null && nozzle.portIndex != null && nozzle.portIndex >= 0 && nozzle.portIndex < outletCount) {
      return nozzle.portIndex;
    }
    if (nozzle != null && nozzle.role != null) {
      switch (nozzle.role) {
      case ROLE_GAS:
        return 0;
      case ROLE_LIQUID:
      case ROLE_OIL:
        return Math.min(1, outletCount - 1);
      case ROLE_WATER:
        return Math.min(2, outletCount - 1);
      default:
        break;
      }
    }
    EquipmentEnum targetType = nodeTypes.get(edge.target);
    EquipmentEnum sourceType = nodeTypes.get(edge.source);
    boolean separator = sourceType == EquipmentEnum.Separator || sourceType == EquipmentEnum.ThreePhaseSeparator;
    if (separator && (targetType == EquipmentEnum.Compressor || targetType == EquipmentEnum.Expander)) {
      return 0;
    }
    if (separator && targetType == EquipmentEnum.Pump) {
      return Math.min(1, outletCount - 1);
    }
    List<NozzleInfo> outlets = outletNozzles(edge.source);
    if (nozzle != null && !outlets.isEmpty()) {
      boolean allPositioned = true;
      for (NozzleInfo info : outlets) {
        allPositioned &= info.y != null;
      }
      List<NozzleInfo> sorted = new ArrayList<>(outlets);
      if (separator && allPositioned) {
        // Proteus coordinates are Y-up: the highest outlet is the gas outlet.
        sorted.sort((a, b) -> Double.compare(b.y, a.y));
      } else {
        sorted.sort((a, b) -> Integer.compare(a.documentIndex, b.documentIndex));
      }
      for (int i = 0; i < sorted.size(); i++) {
        if (sorted.get(i).id.equals(nozzle.id)) {
          return Math.min(i, outletCount - 1);
        }
      }
    }
    List<Edge> outgoing = outgoing(edge.source);
    return Math.min(Math.max(0, outgoing.indexOf(edge)), outletCount - 1);
  }

  /**
   * Returns the nozzles of a node used as the source of at least one edge.
   *
   * @param id node id
   * @return outlet nozzles in edge order
   */
  private List<NozzleInfo> outletNozzles(String id) {
    Set<String> ids = new LinkedHashSet<>();
    for (Edge edge : edges) {
      if (edge.source.equals(id) && edge.sourceNozzleId != null) {
        ids.add(edge.sourceNozzleId);
      }
    }
    List<NozzleInfo> result = new ArrayList<>();
    for (String nozzleId : ids) {
      NozzleInfo info = nozzles.get(nozzleId);
      if (info != null) {
        result.add(info);
      }
    }
    return result;
  }

  /**
   * Records the nozzles owned by an element and its sub-components.
   *
   * @param element owning element
   * @param ownerId node id that owns the nozzles
   * @param owner map updated with nozzle id to owner id
   * @param order running document-order counter
   */
  private void collectNozzles(Element element, String ownerId, Map<String, String> owner, int[] order) {
    NodeList children = element.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element childElement = (Element) child;
      String tag = childElement.getTagName();
      if ("Nozzle".equals(tag)) {
        String id = childElement.getAttribute("ID");
        if (id != null && !id.isEmpty()) {
          owner.put(id, ownerId);
          nozzles.put(id, new NozzleInfo(id, ownerId, order[0]++, nozzleY(childElement),
              attributeValue(childElement, PORT_ROLE), parseInteger(attributeValue(childElement, PORT_INDEX))));
        }
      } else if (!"GenericAttributes".equals(tag) && !"PipingComponent".equals(tag)) {
        collectNozzles(childElement, ownerId, owner, order);
      }
    }
  }

  /**
   * Returns the vertical position of a nozzle from its Position/Location element.
   *
   * @param nozzle nozzle element
   * @return Y coordinate, or null when absent or invalid
   */
  private static Double nozzleY(Element nozzle) {
    for (Element position : directChildren(nozzle, "Position")) {
      for (Element location : directChildren(position, "Location")) {
        try {
          return Double.valueOf(location.getAttribute("Y"));
        } catch (NumberFormatException e) {
          return null;
        }
      }
    }
    return null;
  }

  /**
   * Parses an optional integer.
   *
   * @param value text, possibly null
   * @return the integer, or null when absent or invalid
   */
  private static Integer parseInteger(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Integer.valueOf(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Returns direct child elements with the given tag name.
   *
   * @param parent parent element
   * @param tag tag name
   * @return matching children in document order
   */
  static List<Element> directChildren(Element parent, String tag) {
    List<Element> result = new ArrayList<>();
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE && tag.equals(((Element) child).getTagName())) {
        result.add((Element) child);
      }
    }
    return result;
  }

  /**
   * Reads a direct GenericAttribute value of an element from any attribute set.
   *
   * @param element owning element
   * @param name attribute name
   * @return the value or null
   */
  static String attributeValue(Element element, String name) {
    Element attribute = attribute(element, name);
    if (attribute == null) {
      return null;
    }
    String value = attribute.getAttribute("Value");
    return value == null || value.isEmpty() ? null : value;
  }

  /**
   * Reads a direct GenericAttribute unit of an element.
   *
   * @param element owning element
   * @param name attribute name
   * @return the unit, or null
   */
  static String attributeUnit(Element element, String name) {
    Element attribute = attribute(element, name);
    if (attribute == null) {
      return null;
    }
    String unit = attribute.getAttribute("Unit");
    return unit == null || unit.isEmpty() ? null : unit;
  }

  /**
   * Finds a direct GenericAttribute element by name in any attribute set.
   *
   * @param element owning element
   * @param name attribute name
   * @return the attribute element, or null
   */
  private static Element attribute(Element element, String name) {
    for (Element set : directChildren(element, "GenericAttributes")) {
      for (Element attribute : directChildren(set, "GenericAttribute")) {
        if (name.equals(attribute.getAttribute("Name"))) {
          return attribute;
        }
      }
    }
    return null;
  }

  /**
   * Reads an operating pressure in bara from an element, or NaN when absent or in an unsupported unit.
   *
   * @param element source element
   * @return absolute pressure in bara or NaN
   */
  static double operatingPressureBara(Element element) {
    double value = parseDouble(attributeValue(element, "OperatingPressureValue"));
    if (Double.isNaN(value)) {
      return Double.NaN;
    }
    String unit = attributeUnit(element, "OperatingPressureValue");
    if (unit == null) {
      unit = attributeValue(element, "OperatingPressureUnit");
    }
    String u = unit == null ? "bara" : unit.trim().toLowerCase(Locale.ROOT);
    switch (u) {
    case "bara":
    case "bar":
      return value;
    case "barg":
      return value + 1.01325;
    case "pa":
      return value / 1.0e5;
    case "kpa":
      return value / 100.0;
    case "mpa":
      return value * 10.0;
    case "psia":
      return value * 0.0689475729;
    case "psig":
      return value * 0.0689475729 + 1.01325;
    default:
      return Double.NaN;
    }
  }

  /**
   * Reads an operating temperature in kelvin from an element, or NaN when absent or in an unsupported unit.
   *
   * @param element source element
   * @return temperature in kelvin or NaN
   */
  static double operatingTemperatureK(Element element) {
    double value = parseDouble(attributeValue(element, "OperatingTemperatureValue"));
    if (Double.isNaN(value)) {
      return Double.NaN;
    }
    String unit = attributeUnit(element, "OperatingTemperatureValue");
    if (unit == null) {
      unit = attributeValue(element, "OperatingTemperatureUnit");
    }
    String u = unit == null ? "c" : unit.trim().toLowerCase(Locale.ROOT);
    switch (u) {
    case "c":
    case "degc":
    case "°c":
      return value + 273.15;
    case "k":
      return value;
    case "f":
    case "degf":
      return (value - 32.0) / 1.8 + 273.15;
    default:
      return Double.NaN;
    }
  }

  /**
   * Parses an optional finite number.
   *
   * @param value text, possibly null
   * @return the number, or NaN when absent, invalid or not finite
   */
  private static double parseDouble(String value) {
    if (value == null) {
      return Double.NaN;
    }
    try {
      double parsed = Double.parseDouble(value.trim());
      return Double.isFinite(parsed) ? parsed : Double.NaN;
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }

  /**
   * Returns an unmodifiable view of the notes.
   *
   * @return notes
   */
  List<String> getNotes() {
    return Collections.unmodifiableList(notes);
  }
}
