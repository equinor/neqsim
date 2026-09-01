package neqsim.process.engineering.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.engineering.validation.EngineeringSchemaCatalog;

/** Added, removed and modified engineering objects between two graph revisions. */
public final class EngineeringGraphDiff implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = new Gson();
  private final String projectId;
  private final String fromRevision;
  private final String toRevision;
  private final List<String> addedNodeIds = new ArrayList<String>();
  private final List<String> removedNodeIds = new ArrayList<String>();
  private final List<String> modifiedNodeIds = new ArrayList<String>();
  private final List<String> addedEdgeIds = new ArrayList<String>();
  private final List<String> removedEdgeIds = new ArrayList<String>();
  private final Set<String> impactedNodeIds = new LinkedHashSet<String>();

  private EngineeringGraphDiff(String projectId, String fromRevision, String toRevision) {
    this.projectId = projectId;
    this.fromRevision = fromRevision;
    this.toRevision = toRevision;
  }

  static EngineeringGraphDiff compare(EngineeringGraph older, EngineeringGraph newer) {
    if (older == null || newer == null) {
      throw new IllegalArgumentException("Both engineering graphs are required");
    }
    if (!older.getProjectId().equals(newer.getProjectId())) {
      throw new IllegalArgumentException("Engineering graphs belong to different projects");
    }
    EngineeringGraphDiff result = new EngineeringGraphDiff(older.getProjectId(), older.getRevision(),
        newer.getRevision());
    compareNodes(older, newer, result);
    compareEdges(older, newer, result);
    result.expandDownstreamImpact(newer);
    return result;
  }

  private static void compareNodes(EngineeringGraph older, EngineeringGraph newer, EngineeringGraphDiff result) {
    for (Map.Entry<String, EngineeringNode> entry : newer.getNodes().entrySet()) {
      EngineeringNode previous = older.getNode(entry.getKey());
      if (previous == null) {
        result.addedNodeIds.add(entry.getKey());
        result.impactedNodeIds.add(entry.getKey());
      } else if (!previous.canonicalForm().equals(entry.getValue().canonicalForm())) {
        result.modifiedNodeIds.add(entry.getKey());
        result.impactedNodeIds.add(entry.getKey());
      }
    }
    for (String nodeId : older.getNodes().keySet()) {
      if (newer.getNode(nodeId) == null) {
        result.removedNodeIds.add(nodeId);
        result.impactedNodeIds.add(nodeId);
      }
    }
  }

  private static void compareEdges(EngineeringGraph older, EngineeringGraph newer, EngineeringGraphDiff result) {
    for (Map.Entry<String, EngineeringEdge> entry : newer.getEdges().entrySet()) {
      EngineeringEdge previous = older.getEdges().get(entry.getKey());
      if (previous == null || !GSON.toJson(previous.toMap()).equals(GSON.toJson(entry.getValue().toMap()))) {
        result.addedEdgeIds.add(entry.getKey());
        result.impactedNodeIds.add(entry.getValue().getSourceId());
        result.impactedNodeIds.add(entry.getValue().getTargetId());
      }
    }
    for (Map.Entry<String, EngineeringEdge> entry : older.getEdges().entrySet()) {
      if (!newer.getEdges().containsKey(entry.getKey())) {
        result.removedEdgeIds.add(entry.getKey());
        result.impactedNodeIds.add(entry.getValue().getSourceId());
        result.impactedNodeIds.add(entry.getValue().getTargetId());
      }
    }
  }

  private void expandDownstreamImpact(EngineeringGraph newer) {
    boolean changed = true;
    while (changed) {
      changed = false;
      for (EngineeringEdge edge : newer.getEdges().values()) {
        if ((edge.getKind() == EngineeringEdge.Kind.DEPENDS_ON || edge.getKind() == EngineeringEdge.Kind.GENERATED_FROM)
            && impactedNodeIds.contains(edge.getTargetId()) && impactedNodeIds.add(edge.getSourceId())) {
          changed = true;
        }
      }
    }
  }

  public boolean isEmpty() {
    return addedNodeIds.isEmpty() && removedNodeIds.isEmpty() && modifiedNodeIds.isEmpty() && addedEdgeIds.isEmpty()
        && removedEdgeIds.isEmpty();
  }

  public String getProjectId() {
    return projectId;
  }

  public String getFromRevision() {
    return fromRevision;
  }

  public String getToRevision() {
    return toRevision;
  }

  public List<String> getAddedNodeIds() {
    return Collections.unmodifiableList(addedNodeIds);
  }

  public List<String> getRemovedNodeIds() {
    return Collections.unmodifiableList(removedNodeIds);
  }

  public List<String> getModifiedNodeIds() {
    return Collections.unmodifiableList(modifiedNodeIds);
  }

  public List<String> getAddedEdgeIds() {
    return Collections.unmodifiableList(addedEdgeIds);
  }

  public List<String> getRemovedEdgeIds() {
    return Collections.unmodifiableList(removedEdgeIds);
  }

  public Set<String> getImpactedNodeIds() {
    return Collections.unmodifiableSet(impactedNodeIds);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", EngineeringSchemaCatalog.REVISION_DIFF);
    result.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.REVISION_DIFF));
    result.put("projectId", projectId);
    result.put("fromRevision", fromRevision);
    result.put("toRevision", toRevision);
    result.put("addedNodeIds", new ArrayList<String>(addedNodeIds));
    result.put("removedNodeIds", new ArrayList<String>(removedNodeIds));
    result.put("modifiedNodeIds", new ArrayList<String>(modifiedNodeIds));
    result.put("addedEdgeIds", new ArrayList<String>(addedEdgeIds));
    result.put("removedEdgeIds", new ArrayList<String>(removedEdgeIds));
    result.put("impactedNodeIds", new ArrayList<String>(impactedNodeIds));
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }
}
