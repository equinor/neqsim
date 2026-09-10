package neqsim.process.processmodel.diagram;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import neqsim.process.engineering.model.EngineeringEdge;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;

/**
 * Material-flow aggregation of a canonical engineering graph into declared process sections.
 *
 * <p>
 * Every material connection belongs to exactly one aggregate connection or to the internal-connection list of a block.
 * Unassigned equipment and boundary streams remain separate blocks; ordering the declarations never creates
 * connectivity. Opposite directions and recycle paths remain distinct. This is a schematic projection, not a new
 * simulation or a declaration of physical equipment sizes.
 * </p>
 */
public final class EngineeringBlockFlowProjection {
  private final String graphJson;

  private EngineeringBlockFlowProjection(EngineeringGraph graph) {
    graphJson = graph.toJson();
  }

  /**
   * Aggregates material connections using exact canonical owner identities.
   *
   * @param source canonical material graph, retained unchanged
   * @param sectionByOwnerId owner-node identity to human-readable section name; unassigned owners remain visible
   * @return immutable projection retaining the source connection identities
   * @throws IllegalArgumentException for unknown assignments or unresolved material endpoints
   */
  public static EngineeringBlockFlowProjection fromGraph(EngineeringGraph source,
      Map<String, String> sectionByOwnerId) {
    if (source == null || sectionByOwnerId == null) {
      throw new IllegalArgumentException("source and section assignments are required");
    }
    Map<String, String> assignments = new TreeMap<String, String>(sectionByOwnerId);
    for (Map.Entry<String, String> entry : assignments.entrySet()) {
      EngineeringNode owner = source.getNode(entry.getKey());
      if (owner == null || !isOwner(owner) || entry.getValue() == null || entry.getValue().trim().isEmpty()) {
        throw new IllegalArgumentException("Invalid block section assignment: " + entry.getKey());
      }
    }
    EngineeringGraph result = new EngineeringGraph(source.getProjectId() + ":BFD", source.getRevision());
    Map<String, EngineeringNode> blocks = new TreeMap<String, EngineeringNode>();
    Map<String, List<String>> members = new TreeMap<String, List<String>>();
    Map<String, List<String>> internal = new TreeMap<String, List<String>>();
    Map<String, String> blockByOwner = new TreeMap<String, String>();
    Map<String, EngineeringNode> ordered = new TreeMap<String, EngineeringNode>(source.getNodes());
    Object sourceFingerprint = source.toMap().get("fingerprint");
    for (EngineeringNode owner : ordered.values()) {
      if (!isOwner(owner)) {
        continue;
      }
      String section = assignments.get(owner.getId());
      String id = section == null ? "block:object:" + encode(owner.getId()) : "block:section:" + encode(section);
      blockByOwner.put(owner.getId(), id);
      if (!blocks.containsKey(id)) {
        EngineeringNode block = new EngineeringNode(id, EngineeringNode.Kind.EQUIPMENT, id,
            section == null ? owner.getLabel() : section).putProperty("projectionKind", "MATERIAL_BLOCK")
            .putProperty("sourceGraphFingerprint", sourceFingerprint);
        blocks.put(id, block);
        members.put(id, new ArrayList<String>());
        internal.put(id, new ArrayList<String>());
      }
      members.get(id).add(owner.getId());
    }
    Map<String, List<String>> connections = new TreeMap<String, List<String>>();
    Map<String, String[]> endpoints = new TreeMap<String, String[]>();
    Map<String, Boolean> recycles = new TreeMap<String, Boolean>();
    for (EngineeringNode connection : ordered.values()) {
      if (connection.getKind() != EngineeringNode.Kind.PIPE_SEGMENT) {
        continue;
      }
      String from = blockByOwner.get(ownerId(source, connection, "sourceEndpointId"));
      String to = blockByOwner.get(ownerId(source, connection, "targetEndpointId"));
      if (from == null || to == null) {
        throw new IllegalArgumentException("Unresolved material connection: " + connection.getId());
      }
      if (from.equals(to)) {
        internal.get(from).add(connection.getId());
        continue;
      }
      String id = "block-flow:" + encode(from) + ":" + encode(to);
      if (!connections.containsKey(id)) {
        connections.put(id, new ArrayList<String>());
        endpoints.put(id, new String[] { from, to });
      }
      connections.get(id).add(connection.getId());
      recycles.put(id, Boolean.valueOf(
          Boolean.TRUE.equals(recycles.get(id)) || Boolean.TRUE.equals(connection.getProperties().get("recycle"))));
    }
    for (EngineeringNode block : blocks.values()) {
      block.putProperty("sourceOwnerIds", members.get(block.getId())).putProperty("internalConnectionIds",
          internal.get(block.getId()));
      result.addNode(block);
    }
    int number = 1;
    for (Map.Entry<String, List<String>> entry : connections.entrySet()) {
      String id = entry.getKey();
      String from = endpoints.get(id)[0];
      String to = endpoints.get(id)[1];
      String fromPort = id + ":out";
      String toPort = id + ":in";
      result.addNode(port(fromPort, from, "OUTLET"));
      result.addNode(port(toPort, to, "INLET"));
      result.addNode(new EngineeringNode(id, EngineeringNode.Kind.PIPE_SEGMENT, id, "BF-" + number++)
          .putProperty("sourceEndpointId", fromPort).putProperty("targetEndpointId", toPort)
          .putProperty("sourceEquipment", blocks.get(from).getLabel())
          .putProperty("targetEquipment", blocks.get(to).getLabel()).putProperty("connectionType", "MATERIAL")
          .putProperty("sourceConnectionIds", entry.getValue()).putProperty("recycle", recycles.get(id)));
      result.addEdge(new EngineeringEdge(id + ":source-port", from, fromPort, EngineeringEdge.Kind.HAS_PORT, "outlet"));
      result.addEdge(new EngineeringEdge(id + ":target-port", to, toPort, EngineeringEdge.Kind.HAS_PORT, "inlet"));
      result
          .addEdge(new EngineeringEdge(id + ":source-flow", fromPort, id, EngineeringEdge.Kind.PROCESS_FLOW, "source"));
      result.addEdge(new EngineeringEdge(id + ":target-flow", id, toPort, EngineeringEdge.Kind.PROCESS_FLOW, "target"));
    }
    return new EngineeringBlockFlowProjection(result);
  }

  /** @return a fresh, independently editable graph for the existing BFD document and rendering APIs */
  public EngineeringGraph toGraph() {
    return EngineeringGraph.fromJson(graphJson);
  }

  /** @return deterministic graph JSON including every aggregate-to-canonical connection mapping */
  public String toJson() {
    return graphJson;
  }

  /** @return independent block identity to human-readable label map for layout declarations */
  public Map<String, String> getBlockLabels() {
    Map<String, String> result = new LinkedHashMap<String, String>();
    for (EngineeringNode node : toGraph().getNodes().values()) {
      if (node.getKind() == EngineeringNode.Kind.EQUIPMENT) {
        result.put(node.getId(), node.getLabel());
      }
    }
    return result;
  }

  private static boolean isOwner(EngineeringNode node) {
    return node.getKind() == EngineeringNode.Kind.EQUIPMENT || node.getKind() == EngineeringNode.Kind.LINE
        || node.getKind() == EngineeringNode.Kind.BOUNDARY || node.getKind() == EngineeringNode.Kind.PROCESS_TAP;
  }

  private static String ownerId(EngineeringGraph source, EngineeringNode connection, String property) {
    Object reference = connection.getProperties().get(property);
    EngineeringNode endpoint = reference == null ? null : source.getNode(reference.toString());
    if (endpoint == null) {
      return "";
    }
    Object owner = endpoint.getProperties().get("ownerNodeId");
    return owner == null && isOwner(endpoint) ? endpoint.getId() : owner == null ? "" : owner.toString();
  }

  private static EngineeringNode port(String id, String owner, String direction) {
    return new EngineeringNode(id, EngineeringNode.Kind.PORT, id, direction).putProperty("ownerNodeId", owner)
        .putProperty("direction", direction).putProperty("connectionType", "MATERIAL");
  }

  private static String encode(String value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }
}
