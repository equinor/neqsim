package neqsim.process.processmodel.dexpi;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Resolves the topology (stream connectivity) of a DEXPI P&amp;ID XML document.
 *
 * <p>
 * This class parses {@code <Nozzle>} elements on equipment, {@code <Connection>} elements inside
 * piping network segments, and piping components to build a directed graph of equipment-level
 * connections. The graph is then topologically sorted so that equipment can be instantiated in
 * correct upstream-to-downstream order.
 * </p>
 *
 * <p>
 * DEXPI topology model:
 * </p>
 * <ul>
 * <li>Each equipment element contains one or more {@code <Nozzle>} children with unique IDs</li>
 * <li>{@code <PipingNetworkSegment>} elements contain {@code <Connection>} elements linking nozzles
 * and piping components via {@code FromID}/{@code ToID} attributes</li>
 * <li>Piping components (valves, reducers, tees) appear inline in segments and also participate in
 * connections</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiTopologyResolver {
  private static final Logger logger = LogManager.getLogger(DexpiTopologyResolver.class);

  private DexpiTopologyResolver() {}

  /**
   * Represents a directed edge between two DEXPI elements in the process topology.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class TopologyEdge implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sourceEquipmentId;
    private final String targetEquipmentId;
    private final String sourceNozzleId;
    private final String targetNozzleId;
    private final String pipingSegmentId;

    /**
     * Creates a new topology edge.
     *
     * @param sourceEquipmentId the ID of the source equipment
     * @param targetEquipmentId the ID of the target equipment
     * @param sourceNozzleId the ID of the source nozzle (may be null)
     * @param targetNozzleId the ID of the target nozzle (may be null)
     * @param pipingSegmentId the piping segment ID carrying this connection (may be null)
     */
    public TopologyEdge(String sourceEquipmentId, String targetEquipmentId, String sourceNozzleId,
        String targetNozzleId, String pipingSegmentId) {
      this.sourceEquipmentId = sourceEquipmentId;
      this.targetEquipmentId = targetEquipmentId;
      this.sourceNozzleId = sourceNozzleId;
      this.targetNozzleId = targetNozzleId;
      this.pipingSegmentId = pipingSegmentId;
    }

    /**
     * Gets the source equipment ID.
     *
     * @return source equipment ID
     */
    public String getSourceEquipmentId() {
      return sourceEquipmentId;
    }

    /**
     * Gets the target equipment ID.
     *
     * @return target equipment ID
     */
    public String getTargetEquipmentId() {
      return targetEquipmentId;
    }

    /**
     * Gets the source nozzle ID.
     *
     * @return source nozzle ID, or null
     */
    public String getSourceNozzleId() {
      return sourceNozzleId;
    }

    /**
     * Gets the target nozzle ID.
     *
     * @return target nozzle ID, or null
     */
    public String getTargetNozzleId() {
      return targetNozzleId;
    }

    /**
     * Gets the piping segment ID.
     *
     * @return piping segment ID, or null
     */
    public String getPipingSegmentId() {
      return pipingSegmentId;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      return sourceEquipmentId + " -> " + targetEquipmentId + " (segment=" + pipingSegmentId + ")";
    }
  }

  /**
   * The resolved topology of a DEXPI document, containing equipment IDs in topological order and
   * the edges (connections) between them.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class ResolvedTopology implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<String> orderedEquipmentIds;
    private final List<TopologyEdge> edges;
    private final Map<String, String> nozzleToEquipment;
    private final transient Map<String, Element> equipmentElements;

    /**
     * Creates a new resolved topology.
     *
     * @param orderedEquipmentIds equipment IDs in topological order
     * @param edges the directed edges between equipment
     * @param nozzleToEquipment map from nozzle ID to owning equipment ID
     * @param equipmentElements map from equipment ID to its XML element
     */
    public ResolvedTopology(List<String> orderedEquipmentIds, List<TopologyEdge> edges,
        Map<String, String> nozzleToEquipment, Map<String, Element> equipmentElements) {
      this.orderedEquipmentIds = Collections.unmodifiableList(new ArrayList<>(orderedEquipmentIds));
      this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
      this.nozzleToEquipment = Collections.unmodifiableMap(new HashMap<>(nozzleToEquipment));
      this.equipmentElements = Collections.unmodifiableMap(new LinkedHashMap<>(equipmentElements));
    }

    /**
     * Gets equipment IDs in topological (upstream to downstream) order.
     *
     * @return ordered list of equipment IDs
     */
    public List<String> getOrderedEquipmentIds() {
      return orderedEquipmentIds;
    }

    /**
     * Gets all directed edges.
     *
     * @return list of topology edges
     */
    public List<TopologyEdge> getEdges() {
      return edges;
    }

    /**
     * Gets the map from nozzle ID to owning equipment ID.
     *
     * @return nozzle-to-equipment map
     */
    public Map<String, String> getNozzleToEquipment() {
      return nozzleToEquipment;
    }

    /**
     * Gets the map from equipment ID to its XML element.
     *
     * @return equipment elements map
     */
    public Map<String, Element> getEquipmentElements() {
      return equipmentElements;
    }

    /**
     * Gets edges whose source is the given equipment ID.
     *
     * @param equipmentId the source equipment ID
     * @return list of outgoing edges (may be empty)
     */
    public List<TopologyEdge> getOutgoingEdges(String equipmentId) {
      List<TopologyEdge> result = new ArrayList<>();
      for (TopologyEdge edge : edges) {
        if (edge.getSourceEquipmentId().equals(equipmentId)) {
          result.add(edge);
        }
      }
      return result;
    }

    /**
     * Gets edges whose target is the given equipment ID.
     *
     * @param equipmentId the target equipment ID
     * @return list of incoming edges (may be empty)
     */
    public List<TopologyEdge> getIncomingEdges(String equipmentId) {
      List<TopologyEdge> result = new ArrayList<>();
      for (TopologyEdge edge : edges) {
        if (edge.getTargetEquipmentId().equals(equipmentId)) {
          result.add(edge);
        }
      }
      return result;
    }

    /**
     * Checks whether the topology contains a cycle. A cycle exists when the number of topologically
     * sorted nodes is less than the total number of equipment nodes, meaning Kahn's algorithm could
     * not resolve all dependencies.
     *
     * @return true if a cycle was detected
     */
    public boolean hasCycle() {
      // Recompute Kahn's sort to detect cycles
      Map<String, Integer> inDegree = new LinkedHashMap<>();
      Map<String, List<String>> adjacency = new HashMap<>();

      for (String id : orderedEquipmentIds) {
        inDegree.put(id, 0);
        adjacency.put(id, new ArrayList<String>());
      }

      for (TopologyEdge edge : edges) {
        if (inDegree.containsKey(edge.getSourceEquipmentId())
            && inDegree.containsKey(edge.getTargetEquipmentId())) {
          adjacency.get(edge.getSourceEquipmentId()).add(edge.getTargetEquipmentId());
          inDegree.put(edge.getTargetEquipmentId(), inDegree.get(edge.getTargetEquipmentId()) + 1);
        }
      }

      Queue<String> queue = new LinkedList<>();
      for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
        if (entry.getValue() == 0) {
          queue.add(entry.getKey());
        }
      }

      int count = 0;
      while (!queue.isEmpty()) {
        String current = queue.poll();
        count++;
        for (String neighbor : adjacency.get(current)) {
          int newDegree = inDegree.get(neighbor) - 1;
          inDegree.put(neighbor, newDegree);
          if (newDegree == 0) {
            queue.add(neighbor);
          }
        }
      }

      return count < orderedEquipmentIds.size();
    }
  }

  /**
   * Resolves the topology of a DEXPI XML document.
   *
   * @param document the parsed DEXPI XML document
   * @return the resolved topology with equipment in topological order
   */
  public static ResolvedTopology resolve(Document document) {
    // Step 1: Build nozzle -> equipment map and collect equipment elements
    Map<String, String> nozzleToEquipment = new HashMap<>();
    Map<String, Element> equipmentElements = new LinkedHashMap<>();
    Set<String> pipingComponentIds = new HashSet<>();

    collectEquipmentAndNozzles(document, nozzleToEquipment, equipmentElements);
    collectPipingComponentIds(document, pipingComponentIds);

    // Step 2: Parse all Connection elements and build edges
    List<TopologyEdge> rawEdges = parseConnections(document, nozzleToEquipment, pipingComponentIds);

    // Step 3: Collapse piping-component-only paths to equipment-level edges
    List<TopologyEdge> equipmentEdges =
        collapseToEquipmentEdges(rawEdges, equipmentElements.keySet(), pipingComponentIds);

    // Step 4: Topological sort
    List<String> ordered = topologicalSort(equipmentElements.keySet(), equipmentEdges);

    logger.info("Resolved topology: {} equipment, {} edges, {} in order", equipmentElements.size(),
        equipmentEdges.size(), ordered.size());
    return new ResolvedTopology(ordered, equipmentEdges, nozzleToEquipment, equipmentElements);
  }

  /**
   * Collects all equipment elements and their nozzle-to-equipment mappings from the document.
   *
   * @param document the XML document
   * @param nozzleToEquipment map to populate with nozzle ID to equipment ID
   * @param equipmentElements map to populate with equipment ID to XML element
   */
  private static void collectEquipmentAndNozzles(Document document,
      Map<String, String> nozzleToEquipment, Map<String, Element> equipmentElements) {
    NodeList equipmentNodes = document.getElementsByTagName("Equipment");
    for (int i = 0; i < equipmentNodes.getLength(); i++) {
      Node eqNode = equipmentNodes.item(i);
      if (eqNode.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element eqParent = (Element) eqNode;
      // Equipment element is a wrapper; actual equipment is in child elements
      NodeList children = eqParent.getChildNodes();
      for (int j = 0; j < children.getLength(); j++) {
        Node child = children.item(j);
        if (child.getNodeType() != Node.ELEMENT_NODE) {
          continue;
        }
        Element childElement = (Element) child;
        String componentClass = childElement.getAttribute("ComponentClass");
        if (componentClass == null || componentClass.trim().isEmpty()) {
          continue;
        }
        // Skip sub-components like Chamber, TubeBundle, Impeller, etc
        if (isSubComponent(componentClass)) {
          continue;
        }
        String equipId = childElement.getAttribute("ID");
        if (equipId == null || equipId.trim().isEmpty()) {
          continue;
        }
        equipmentElements.put(equipId, childElement);

        // Find nozzles owned by this equipment
        collectNozzlesFrom(childElement, equipId, nozzleToEquipment);
      }
    }
    logger.debug("Found {} equipment items, {} nozzles", equipmentElements.size(),
        nozzleToEquipment.size());
  }

  /**
   * Recursively collects Nozzle elements from an equipment element and its sub-components.
   *
   * @param element the parent element
   * @param equipId the owning equipment ID
   * @param nozzleToEquipment map to populate
   */
  private static void collectNozzlesFrom(Element element, String equipId,
      Map<String, String> nozzleToEquipment) {
    NodeList children = element.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element childElement = (Element) child;
      if ("Nozzle".equals(childElement.getTagName())) {
        String nozzleId = childElement.getAttribute("ID");
        if (nozzleId != null && !nozzleId.trim().isEmpty()) {
          nozzleToEquipment.put(nozzleId, equipId);
        }
      } else {
        // Recurse into sub-components (e.g. Chamber, TubeBundle)
        collectNozzlesFrom(childElement, equipId, nozzleToEquipment);
      }
    }
  }

  /**
   * Collects all piping component IDs from PipingComponent wrapper elements.
   *
   * @param document the XML document
   * @param pipingComponentIds set to populate with IDs
   */
  private static void collectPipingComponentIds(Document document, Set<String> pipingComponentIds) {
    NodeList pipingNodes = document.getElementsByTagName("PipingComponent");
    for (int i = 0; i < pipingNodes.getLength(); i++) {
      Node pcNode = pipingNodes.item(i);
      if (pcNode.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element pcParent = (Element) pcNode;
      NodeList children = pcParent.getChildNodes();
      for (int j = 0; j < children.getLength(); j++) {
        Node child = children.item(j);
        if (child.getNodeType() != Node.ELEMENT_NODE) {
          continue;
        }
        Element childElement = (Element) child;
        String id = childElement.getAttribute("ID");
        if (id != null && !id.trim().isEmpty()) {
          pipingComponentIds.add(id);
        }
      }
    }
  }

  /**
   * Parses all Connection elements from PipingNetworkSegments into raw edges.
   *
   * @param document the XML document
   * @param nozzleToEquipment nozzle to equipment mapping
   * @param pipingComponentIds known piping component IDs
   * @return list of raw edges (may reference nozzles, piping components, or off-page connectors)
   */
  private static List<TopologyEdge> parseConnections(Document document,
      Map<String, String> nozzleToEquipment, Set<String> pipingComponentIds) {
    List<TopologyEdge> edges = new ArrayList<>();
    NodeList segmentNodes = document.getElementsByTagName("PipingNetworkSegment");

    for (int i = 0; i < segmentNodes.getLength(); i++) {
      Node segNode = segmentNodes.item(i);
      if (segNode.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element segment = (Element) segNode;
      String segmentId = segment.getAttribute("ID");

      // Find Connection children (direct children only)
      NodeList children = segment.getChildNodes();
      for (int j = 0; j < children.getLength(); j++) {
        Node child = children.item(j);
        if (child.getNodeType() != Node.ELEMENT_NODE) {
          continue;
        }
        Element childEl = (Element) child;
        if (!"Connection".equals(childEl.getTagName())) {
          continue;
        }
        String fromId = childEl.getAttribute("FromID");
        String toId = childEl.getAttribute("ToID");
        if (fromId == null || fromId.trim().isEmpty() || toId == null || toId.trim().isEmpty()) {
          continue;
        }

        // Resolve from/to to equipment IDs where possible
        String sourceEquip = resolveToEquipment(fromId, nozzleToEquipment);
        String targetEquip = resolveToEquipment(toId, nozzleToEquipment);

        // If from/to is a piping component, use the component ID as-is
        if (sourceEquip == null && pipingComponentIds.contains(fromId)) {
          sourceEquip = fromId;
        }
        if (targetEquip == null && pipingComponentIds.contains(toId)) {
          targetEquip = toId;
        }

        // Skip off-page connectors
        if (sourceEquip == null || targetEquip == null) {
          logger.debug("Skipping connection {}->{} (unresolved)", fromId, toId);
          continue;
        }

        // Skip self-loops
        if (sourceEquip.equals(targetEquip)) {
          continue;
        }

        edges.add(new TopologyEdge(sourceEquip, targetEquip,
            nozzleToEquipment.containsKey(fromId) ? fromId : null,
            nozzleToEquipment.containsKey(toId) ? toId : null, segmentId));
      }
    }
    logger.debug("Parsed {} raw connection edges", edges.size());
    return edges;
  }

  /**
   * Resolves an ID (nozzle or equipment) to an equipment ID.
   *
   * @param id the ID to resolve
   * @param nozzleToEquipment nozzle mapping
   * @return equipment ID, or null if not found
   */
  private static String resolveToEquipment(String id, Map<String, String> nozzleToEquipment) {
    if (nozzleToEquipment.containsKey(id)) {
      return nozzleToEquipment.get(id);
    }
    return null;
  }

  /**
   * Collapses raw edges that pass through intermediate piping components into direct
   * equipment-to-equipment edges. For example, Equipment-A &gt; Valve-1 &gt; Equipment-B becomes
   * Equipment-A &gt; Equipment-B.
   *
   * @param rawEdges the raw edges from Connection elements
   * @param equipmentIds the set of actual equipment IDs
   * @param pipingComponentIds the set of piping component IDs
   * @return list of equipment-level edges
   */
  private static List<TopologyEdge> collapseToEquipmentEdges(List<TopologyEdge> rawEdges,
      Set<String> equipmentIds, Set<String> pipingComponentIds) {
    // Build adjacency for traversal
    Map<String, List<TopologyEdge>> outgoing = new HashMap<>();
    for (TopologyEdge edge : rawEdges) {
      List<TopologyEdge> list = outgoing.get(edge.getSourceEquipmentId());
      if (list == null) {
        list = new ArrayList<>();
        outgoing.put(edge.getSourceEquipmentId(), list);
      }
      list.add(edge);
    }

    // For each edge starting from equipment, follow through piping components
    Set<String> seen = new HashSet<>();
    List<TopologyEdge> result = new ArrayList<>();

    for (TopologyEdge edge : rawEdges) {
      if (!equipmentIds.contains(edge.getSourceEquipmentId())) {
        continue;
      }

      // BFS through piping components to find the next equipment
      Queue<String> queue = new LinkedList<>();
      queue.add(edge.getTargetEquipmentId());
      Set<String> visited = new HashSet<>();

      while (!queue.isEmpty()) {
        String current = queue.poll();
        if (visited.contains(current)) {
          continue;
        }
        visited.add(current);

        if (equipmentIds.contains(current)) {
          String key = edge.getSourceEquipmentId() + "->" + current;
          if (!seen.contains(key)) {
            seen.add(key);
            result.add(new TopologyEdge(edge.getSourceEquipmentId(), current,
                edge.getSourceNozzleId(), edge.getTargetNozzleId(), edge.getPipingSegmentId()));
          }
        } else if (pipingComponentIds.contains(current)) {
          // Follow through piping component
          List<TopologyEdge> next = outgoing.get(current);
          if (next != null) {
            for (TopologyEdge nextEdge : next) {
              queue.add(nextEdge.getTargetEquipmentId());
            }
          }
        }
      }
    }

    // Also add direct equipment-to-equipment edges
    for (TopologyEdge edge : rawEdges) {
      if (equipmentIds.contains(edge.getSourceEquipmentId())
          && equipmentIds.contains(edge.getTargetEquipmentId())) {
        String key = edge.getSourceEquipmentId() + "->" + edge.getTargetEquipmentId();
        if (!seen.contains(key)) {
          seen.add(key);
          result.add(edge);
        }
      }
    }

    logger.debug("Collapsed to {} equipment-level edges", result.size());
    return result;
  }

  /**
   * Performs a topological sort (Kahn's algorithm) on the equipment graph.
   *
   * @param equipmentIds all equipment IDs
   * @param edges directed edges between equipment
   * @return equipment IDs in topological order
   */
  private static List<String> topologicalSort(Set<String> equipmentIds, List<TopologyEdge> edges) {
    Map<String, Integer> inDegree = new LinkedHashMap<>();
    Map<String, List<String>> adjacency = new HashMap<>();

    for (String id : equipmentIds) {
      inDegree.put(id, 0);
      adjacency.put(id, new ArrayList<String>());
    }

    for (TopologyEdge edge : edges) {
      if (equipmentIds.contains(edge.getSourceEquipmentId())
          && equipmentIds.contains(edge.getTargetEquipmentId())) {
        adjacency.get(edge.getSourceEquipmentId()).add(edge.getTargetEquipmentId());
        inDegree.put(edge.getTargetEquipmentId(), inDegree.get(edge.getTargetEquipmentId()) + 1);
      }
    }

    Queue<String> queue = new LinkedList<>();
    for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
      if (entry.getValue() == 0) {
        queue.add(entry.getKey());
      }
    }

    List<String> sorted = new ArrayList<>();
    while (!queue.isEmpty()) {
      String current = queue.poll();
      sorted.add(current);
      for (String neighbor : adjacency.get(current)) {
        int newDegree = inDegree.get(neighbor) - 1;
        inDegree.put(neighbor, newDegree);
        if (newDegree == 0) {
          queue.add(neighbor);
        }
      }
    }

    // Add any remaining equipment not in a connected graph — may indicate a cycle
    Set<String> sortedSet = new LinkedHashSet<>(sorted);
    List<String> unsorted = new ArrayList<>();
    for (String id : equipmentIds) {
      if (!sortedSet.contains(id)) {
        unsorted.add(id);
        sorted.add(id);
      }
    }
    if (!unsorted.isEmpty()) {
      logger.warn("Cycle detected in topology: {} equipment nodes could not be topologically"
          + " sorted and were appended in discovery order: {}", unsorted.size(), unsorted);
    }

    return sorted;
  }

  /**
   * Checks whether a DEXPI ComponentClass is a sub-component of equipment (e.g. Chamber,
   * TubeBundle, Impeller) rather than a top-level equipment item.
   *
   * @param componentClass the DEXPI component class name
   * @return true if it is a sub-component
   */
  private static boolean isSubComponent(String componentClass) {
    return "Chamber".equals(componentClass) || "TubeBundle".equals(componentClass)
        || "Impeller".equals(componentClass) || "NozzleShape".equals(componentClass)
        || "TaggedPlantItemShape".equals(componentClass) || "EquipmentShape".equals(componentClass)
        || "Shape".equals(componentClass) || componentClass.endsWith("Shape");
  }
}
