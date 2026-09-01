package neqsim.process.engineering.impact;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/** Deterministic impact register, recalculation plan and approval actions for one model change. */
public final class ImpactAnalysisResult implements Serializable {
  private static final long serialVersionUID = 1000L;
  public static final String SCHEMA_VERSION = "neqsim_impact_analysis.v1";
  public static final String SCHEMA_URI = "urn:neqsim:schema:impact-analysis:v1";
  private final String eventId;
  private final String projectId;
  private final String graphRevision;
  private final List<ImpactedEngineeringObject> impactedObjects;
  private final List<String> unresolvedSubjectIds;
  private final List<String> propagationCycleEdgeIds;
  private final List<String> recalculationOrder;
  private final List<String> recalculationCycleNodeIds;
  private final List<String> reapprovalNodeIds;

  ImpactAnalysisResult(String eventId, String projectId, String graphRevision,
      List<ImpactedEngineeringObject> impactedObjects, List<String> unresolvedSubjectIds,
      List<String> propagationCycleEdgeIds, List<String> recalculationOrder, List<String> recalculationCycleNodeIds,
      List<String> reapprovalNodeIds) {
    this.eventId = eventId;
    this.projectId = projectId;
    this.graphRevision = graphRevision;
    this.impactedObjects = new ArrayList<ImpactedEngineeringObject>(impactedObjects);
    this.unresolvedSubjectIds = sorted(unresolvedSubjectIds);
    this.propagationCycleEdgeIds = sorted(propagationCycleEdgeIds);
    this.recalculationOrder = new ArrayList<String>(recalculationOrder);
    this.recalculationCycleNodeIds = sorted(recalculationCycleNodeIds);
    this.reapprovalNodeIds = sorted(reapprovalNodeIds);
  }

  public String getEventId() {
    return eventId;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getGraphRevision() {
    return graphRevision;
  }

  public List<ImpactedEngineeringObject> getImpactedObjects() {
    return Collections.unmodifiableList(impactedObjects);
  }

  public List<String> getUnresolvedSubjectIds() {
    return Collections.unmodifiableList(unresolvedSubjectIds);
  }

  public List<String> getPropagationCycleEdgeIds() {
    return Collections.unmodifiableList(propagationCycleEdgeIds);
  }

  public List<String> getRecalculationOrder() {
    return Collections.unmodifiableList(recalculationOrder);
  }

  public List<String> getRecalculationCycleNodeIds() {
    return Collections.unmodifiableList(recalculationCycleNodeIds);
  }

  public List<String> getReapprovalNodeIds() {
    return Collections.unmodifiableList(reapprovalNodeIds);
  }

  public ImpactedEngineeringObject getImpact(String nodeId) {
    for (ImpactedEngineeringObject value : impactedObjects) {
      if (value.getNodeId().equals(nodeId)) {
        return value;
      }
    }
    return null;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", SCHEMA_VERSION);
    result.put("schemaUri", SCHEMA_URI);
    result.put("eventId", eventId);
    result.put("projectId", projectId);
    result.put("graphRevision", graphRevision);
    List<Map<String, Object>> impacts = new ArrayList<Map<String, Object>>();
    for (ImpactedEngineeringObject impact : impactedObjects) {
      impacts.add(impact.toMap());
    }
    result.put("impactedObjects", impacts);
    result.put("unresolvedSubjectIds", new ArrayList<String>(unresolvedSubjectIds));
    result.put("propagationCycleEdgeIds", new ArrayList<String>(propagationCycleEdgeIds));
    result.put("recalculationOrder", new ArrayList<String>(recalculationOrder));
    result.put("recalculationCycleNodeIds", new ArrayList<String>(recalculationCycleNodeIds));
    result.put("reapprovalNodeIds", new ArrayList<String>(reapprovalNodeIds));
    result.put("fitnessForConstruction", Boolean.FALSE);
    result.put("governance", "Impact analysis proposes work; accountable engineers approve the resulting changes");
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  private static List<String> sorted(List<String> values) {
    List<String> result = new ArrayList<String>(values);
    Collections.sort(result);
    return result;
  }
}
