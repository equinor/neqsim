package neqsim.process.engineering.model;

import java.io.Serializable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.engineering.validation.EngineeringSchemaCatalog;

/** Canonical, exchange-format-independent engineering model with stable node identities. */
public final class EngineeringGraph implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = new Gson();
  public static final String SCHEMA_VERSION = EngineeringSchemaCatalog.GRAPH;
  private final String projectId;
  private final String revision;
  private final Map<String, EngineeringNode> nodes = new LinkedHashMap<String, EngineeringNode>();
  private final Map<String, EngineeringEdge> edges = new LinkedHashMap<String, EngineeringEdge>();

  public EngineeringGraph(String projectId, String revision) {
    this.projectId = requireText(projectId, "projectId");
    this.revision = requireText(revision, "revision");
  }

  public EngineeringGraph addNode(EngineeringNode node) {
    if (node == null) {
      throw new IllegalArgumentException("node must not be null");
    }
    if (nodes.containsKey(node.getId())) {
      throw new IllegalArgumentException("Duplicate engineering node " + node.getId());
    }
    nodes.put(node.getId(), node);
    return this;
  }

  public EngineeringGraph addEdge(EngineeringEdge edge) {
    if (edge == null) {
      throw new IllegalArgumentException("edge must not be null");
    }
    if (edges.containsKey(edge.getId())) {
      throw new IllegalArgumentException("Duplicate engineering edge " + edge.getId());
    }
    if (!nodes.containsKey(edge.getSourceId()) || !nodes.containsKey(edge.getTargetId())) {
      throw new IllegalArgumentException("Engineering edge references an unknown node: " + edge.getId());
    }
    edges.put(edge.getId(), edge);
    return this;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getRevision() {
    return revision;
  }

  public EngineeringNode getNode(String id) {
    return nodes.get(id);
  }

  public Map<String, EngineeringNode> getNodes() {
    return Collections.unmodifiableMap(nodes);
  }

  public Map<String, EngineeringEdge> getEdges() {
    return Collections.unmodifiableMap(edges);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", SCHEMA_VERSION);
    result.put("schemaUri", EngineeringSchemaCatalog.schemaUri(SCHEMA_VERSION));
    result.put("projectId", projectId);
    result.put("revision", revision);
    List<Map<String, Object>> nodeMaps = new ArrayList<Map<String, Object>>();
    for (EngineeringNode node : nodes.values()) {
      nodeMaps.add(node.toMap());
    }
    result.put("nodes", nodeMaps);
    List<Map<String, Object>> edgeMaps = new ArrayList<Map<String, Object>>();
    for (EngineeringEdge edge : edges.values()) {
      edgeMaps.add(edge.toMap());
    }
    result.put("edges", edgeMaps);
    result.put("fingerprint", fingerprintWithoutSelfReference(nodeMaps, edgeMaps));
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  /** Reconstructs a graph snapshot previously written by {@link #toJson()}. */
  public static EngineeringGraph fromJson(String json) {
    if (json == null || json.trim().isEmpty()) {
      throw new IllegalArgumentException("json must not be blank");
    }
    JsonElement parsed = JsonParser.parseString(json);
    if (!parsed.isJsonObject()) {
      throw new IllegalArgumentException("Engineering graph JSON root must be an object");
    }
    JsonObject root = parsed.getAsJsonObject();
    if (!root.has("schemaVersion") || !SCHEMA_VERSION.equals(root.get("schemaVersion").getAsString())) {
      throw new IllegalArgumentException("Unsupported engineering graph schema version");
    }
    EngineeringGraph graph = new EngineeringGraph(root.get("projectId").getAsString(),
        root.get("revision").getAsString());
    JsonArray nodeArray = root.getAsJsonArray("nodes");
    for (JsonElement element : nodeArray) {
      JsonObject item = element.getAsJsonObject();
      EngineeringNode node = new EngineeringNode(item.get("id").getAsString(),
          EngineeringNode.Kind.valueOf(item.get("kind").getAsString()), item.get("externalKey").getAsString(),
          item.get("label").getAsString());
      JsonObject properties = item.getAsJsonObject("properties");
      for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
        node.putProperty(property.getKey(), GSON.fromJson(property.getValue(), Object.class));
      }
      JsonArray provenance = item.getAsJsonArray("provenance");
      for (JsonElement provenanceElement : provenance) {
        JsonObject provenanceItem = provenanceElement.getAsJsonObject();
        EngineeringProvenance record = new EngineeringProvenance(
            provenanceItem.get("sourceType").getAsString(), provenanceItem.get("sourceReference").getAsString())
                .setMethod(provenanceItem.get("method").getAsString())
                .setDesignCaseId(provenanceItem.get("designCaseId").getAsString())
                .setApprovalStatus(provenanceItem.get("approvalStatus").getAsString());
        for (JsonElement evidence : provenanceItem.getAsJsonArray("evidenceReferences")) {
          record.addEvidenceReference(evidence.getAsString());
        }
        node.addProvenance(record);
      }
      graph.addNode(node);
    }
    JsonArray edgeArray = root.getAsJsonArray("edges");
    for (JsonElement element : edgeArray) {
      JsonObject item = element.getAsJsonObject();
      graph.addEdge(new EngineeringEdge(item.get("id").getAsString(), item.get("sourceId").getAsString(),
          item.get("targetId").getAsString(), EngineeringEdge.Kind.valueOf(item.get("kind").getAsString()),
          item.get("role").getAsString()));
    }
    if (root.has("fingerprint")) {
      String expected = String.valueOf(graph.toMap().get("fingerprint"));
      if (!expected.equals(root.get("fingerprint").getAsString())) {
        throw new IllegalArgumentException("Engineering graph fingerprint does not match its content");
      }
    }
    return graph;
  }

  /** Reads a persisted canonical graph snapshot. */
  public static EngineeringGraph read(Path file) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    return fromJson(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
  }

  public EngineeringGraphDiff compareTo(EngineeringGraph newer) {
    return EngineeringGraphDiff.compare(this, newer);
  }

  private String fingerprintWithoutSelfReference(List<Map<String, Object>> nodeMaps,
      List<Map<String, Object>> edgeMaps) {
    Map<String, Object> content = new LinkedHashMap<String, Object>();
    content.put("projectId", projectId);
    content.put("revision", revision);
    content.put("nodes", nodeMaps);
    content.put("edges", edgeMaps);
    String json = new GsonBuilder().create().toJson(content);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte value : hash) {
        result.append(String.format("%02x", value & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
