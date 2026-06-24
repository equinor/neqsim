package neqsim.process.safety.rupture;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Source-traceable P&amp;ID topology evidence for pipe-fire rupture studies.
 *
 * <p>
 * The class records the deterministic graph extracted by P&amp;ID/OCR agents: equipment, nozzles,
 * valves, and line edges plus isolation-boundary and annotation-overlay status. It does not perform
 * image recognition itself; it provides the contract that drawing readers hand to the NeqSim safety
 * readiness gate.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class PidTopologyEvidence implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String drawingId;
  private final String revision;
  private final boolean embeddedTextRead;
  private final boolean ocrFallbackUsed;
  private final boolean overlayGenerated;
  private final boolean boundaryVerified;
  private final List<String> inScopeTags;
  private final List<String> boundaryTags;
  private final List<String> missingTags;
  private final List<TopologyNode> nodes;
  private final List<TopologyEdge> edges;

  /**
   * Creates P&amp;ID topology evidence.
   *
   * @param builder populated builder
   */
  private PidTopologyEvidence(Builder builder) {
    builder.validate();
    this.drawingId = clean(builder.drawingId);
    this.revision = clean(builder.revision);
    this.embeddedTextRead = builder.embeddedTextRead;
    this.ocrFallbackUsed = builder.ocrFallbackUsed;
    this.overlayGenerated = builder.overlayGenerated;
    this.boundaryVerified = builder.boundaryVerified;
    this.inScopeTags = immutableText(builder.inScopeTags);
    this.boundaryTags = immutableText(builder.boundaryTags);
    this.missingTags = immutableText(builder.missingTags);
    this.nodes = Collections.unmodifiableList(new ArrayList<TopologyNode>(builder.nodes));
    this.edges = Collections.unmodifiableList(new ArrayList<TopologyEdge>(builder.edges));
  }

  /**
   * Creates a topology-evidence builder.
   *
   * @param drawingId drawing id or document number
   * @return topology evidence builder
   */
  public static Builder builder(String drawingId) {
    return new Builder(drawingId);
  }

  /**
   * Checks if the topology is ready to support simulation boundary decisions.
   *
   * @return true when graph, boundary, and missing-tag checks are satisfactory
   */
  public boolean isSimulationReady() {
    return boundaryVerified && !nodes.isEmpty() && !edges.isEmpty() && missingTags.isEmpty();
  }

  /**
   * Creates the topology readiness verdict.
   *
   * @return readiness result with findings
   */
  public SafetyStudyReadiness readiness() {
    SafetyStudyReadiness.Builder readiness = SafetyStudyReadiness.builder();
    if (nodes.isEmpty()) {
      readiness.addWarning("pid_topology", "No topology nodes were extracted from the drawing.",
          "Extract equipment, nozzles, valves, and boundary nodes from source drawing evidence.");
    }
    if (edges.isEmpty()) {
      readiness.addWarning("pid_topology", "No process-line edges were extracted from the drawing.",
          "Trace line segments between equipment, valves, nozzles, and battery limits.");
    }
    if (!boundaryVerified) {
      readiness.addWarning("pid_topology", "Isolation or blowdown boundary is not marked verified.",
          "Verify upstream/downstream valves, nozzles, vents, drains, relief/blowdown paths, and battery limits.");
    }
    if (!missingTags.isEmpty()) {
      readiness.addWarning("pid_topology", "Topology extraction has missing tags: " + missingTags,
          "Close missing drawing tags or keep them as explicit study gaps.");
    }
    if (!embeddedTextRead && !ocrFallbackUsed) {
      readiness.addWarning("pid_topology",
          "Neither embedded text nor OCR fallback is marked complete.",
          "Run embedded-text extraction or OCR fallback before relying on the topology.");
    }
    if (!overlayGenerated) {
      readiness.addInfo("pid_annotation", "No deterministic drawing overlay is recorded.",
          "Generate an SVG/PNG overlay for review when the drawing is safety-critical.");
    }
    return readiness.build();
  }

  /**
   * Converts topology evidence to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("schemaVersion", "pid_topology_evidence.v1");
    map.put("drawingId", drawingId);
    map.put("revision", revision);
    map.put("embeddedTextRead", Boolean.valueOf(embeddedTextRead));
    map.put("ocrFallbackUsed", Boolean.valueOf(ocrFallbackUsed));
    map.put("overlayGenerated", Boolean.valueOf(overlayGenerated));
    map.put("boundaryVerified", Boolean.valueOf(boundaryVerified));
    map.put("simulationReady", Boolean.valueOf(isSimulationReady()));
    map.put("inScopeTags", inScopeTags);
    map.put("boundaryTags", boundaryTags);
    map.put("missingTags", missingTags);
    List<Map<String, Object>> nodeMaps = new ArrayList<Map<String, Object>>();
    for (TopologyNode node : nodes) {
      nodeMaps.add(node.toMap());
    }
    map.put("nodes", nodeMaps);
    List<Map<String, Object>> edgeMaps = new ArrayList<Map<String, Object>>();
    for (TopologyEdge edge : edges) {
      edgeMaps.add(edge.toMap());
    }
    map.put("edges", edgeMaps);
    map.put("readiness", readiness().toMap());
    return map;
  }

  /**
   * Converts topology evidence to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  /**
   * Copies text values.
   *
   * @param values values to copy
   * @return immutable copy
   */
  private static List<String> immutableText(List<String> values) {
    return Collections.unmodifiableList(new ArrayList<String>(values));
  }

  /**
   * Normalizes nullable text.
   *
   * @param value text value
   * @return trimmed text or empty string
   */
  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  /** One topology node. */
  public static final class TopologyNode implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String tag;
    private final String type;
    private final String role;

    /**
     * Creates a topology node.
     *
     * @param id node id
     * @param tag equipment, valve, nozzle, or boundary tag
     * @param type node type
     * @param role role in the study boundary
     */
    public TopologyNode(String id, String tag, String type, String role) {
      if (clean(id).isEmpty()) {
        throw new IllegalArgumentException("node id must not be empty");
      }
      this.id = clean(id);
      this.tag = clean(tag);
      this.type = clean(type);
      this.role = clean(role);
    }

    /**
     * Converts node to a map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("id", id);
      map.put("tag", tag);
      map.put("type", type);
      map.put("role", role);
      return map;
    }
  }

  /** One topology edge. */
  public static final class TopologyEdge implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String fromNodeId;
    private final String toNodeId;
    private final String lineTag;
    private final String medium;

    /**
     * Creates a topology edge.
     *
     * @param id edge id
     * @param fromNodeId upstream node id
     * @param toNodeId downstream node id
     * @param lineTag line tag or line number
     * @param medium process medium description
     */
    public TopologyEdge(String id, String fromNodeId, String toNodeId, String lineTag,
        String medium) {
      if (clean(id).isEmpty()) {
        throw new IllegalArgumentException("edge id must not be empty");
      }
      this.id = clean(id);
      this.fromNodeId = clean(fromNodeId);
      this.toNodeId = clean(toNodeId);
      this.lineTag = clean(lineTag);
      this.medium = clean(medium);
    }

    /**
     * Converts edge to a map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("id", id);
      map.put("fromNodeId", fromNodeId);
      map.put("toNodeId", toNodeId);
      map.put("lineTag", lineTag);
      map.put("medium", medium);
      return map;
    }
  }

  /** Builder for {@link PidTopologyEvidence}. */
  public static final class Builder {
    private final String drawingId;
    private String revision = "";
    private boolean embeddedTextRead;
    private boolean ocrFallbackUsed;
    private boolean overlayGenerated;
    private boolean boundaryVerified;
    private final List<String> inScopeTags = new ArrayList<String>();
    private final List<String> boundaryTags = new ArrayList<String>();
    private final List<String> missingTags = new ArrayList<String>();
    private final List<TopologyNode> nodes = new ArrayList<TopologyNode>();
    private final List<TopologyEdge> edges = new ArrayList<TopologyEdge>();

    /**
     * Creates a builder.
     *
     * @param drawingId drawing id
     */
    private Builder(String drawingId) {
      this.drawingId = drawingId;
    }

    /**
     * Sets drawing revision.
     *
     * @param revision revision text
     * @return this builder
     */
    public Builder revision(String revision) {
      this.revision = revision;
      return this;
    }

    /**
     * Marks embedded text extraction complete.
     *
     * @param embeddedTextRead true if embedded text was read
     * @return this builder
     */
    public Builder embeddedTextRead(boolean embeddedTextRead) {
      this.embeddedTextRead = embeddedTextRead;
      return this;
    }

    /**
     * Marks OCR fallback usage.
     *
     * @param ocrFallbackUsed true if OCR fallback was used
     * @return this builder
     */
    public Builder ocrFallbackUsed(boolean ocrFallbackUsed) {
      this.ocrFallbackUsed = ocrFallbackUsed;
      return this;
    }

    /**
     * Marks deterministic drawing overlay generation.
     *
     * @param overlayGenerated true if review overlay was generated
     * @return this builder
     */
    public Builder overlayGenerated(boolean overlayGenerated) {
      this.overlayGenerated = overlayGenerated;
      return this;
    }

    /**
     * Marks isolation or blowdown boundary verification.
     *
     * @param boundaryVerified true if verified
     * @return this builder
     */
    public Builder boundaryVerified(boolean boundaryVerified) {
      this.boundaryVerified = boundaryVerified;
      return this;
    }

    /**
     * Adds an in-scope tag.
     *
     * @param tag tag text
     * @return this builder
     */
    public Builder addInScopeTag(String tag) {
      addText(inScopeTags, tag);
      return this;
    }

    /**
     * Adds a boundary tag.
     *
     * @param tag tag text
     * @return this builder
     */
    public Builder addBoundaryTag(String tag) {
      addText(boundaryTags, tag);
      return this;
    }

    /**
     * Adds a missing tag.
     *
     * @param tag tag text
     * @return this builder
     */
    public Builder addMissingTag(String tag) {
      addText(missingTags, tag);
      return this;
    }

    /**
     * Adds a topology node.
     *
     * @param id node id
     * @param tag node tag
     * @param type node type
     * @param role node role
     * @return this builder
     */
    public Builder addNode(String id, String tag, String type, String role) {
      nodes.add(new TopologyNode(id, tag, type, role));
      return this;
    }

    /**
     * Adds a topology edge.
     *
     * @param id edge id
     * @param fromNodeId upstream node id
     * @param toNodeId downstream node id
     * @param lineTag line tag
     * @param medium process medium
     * @return this builder
     */
    public Builder addEdge(String id, String fromNodeId, String toNodeId, String lineTag,
        String medium) {
      edges.add(new TopologyEdge(id, fromNodeId, toNodeId, lineTag, medium));
      return this;
    }

    /**
     * Builds topology evidence.
     *
     * @return topology evidence
     */
    public PidTopologyEvidence build() {
      return new PidTopologyEvidence(this);
    }

    /**
     * Validates builder state.
     *
     * @throws IllegalArgumentException if drawing id is missing
     */
    private void validate() {
      if (clean(drawingId).isEmpty()) {
        throw new IllegalArgumentException("drawingId must not be empty");
      }
    }

    /**
     * Adds non-empty text to a list.
     *
     * @param target target list
     * @param value text value
     */
    private static void addText(List<String> target, String value) {
      if (!clean(value).isEmpty()) {
        target.add(clean(value));
      }
    }
  }
}
