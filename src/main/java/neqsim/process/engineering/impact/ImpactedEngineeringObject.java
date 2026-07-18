package neqsim.process.engineering.impact;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.model.EngineeringNode;

/** Immutable explanation of why one graph object is affected and what must happen next. */
public final class ImpactedEngineeringObject implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String nodeId;
  private final EngineeringNode.Kind nodeKind;
  private final boolean directChange;
  private final int propagationDepth;
  private final List<ImpactAction> requiredActions;
  private final List<String> propagationPath;
  private final List<String> reasonEdgeIds;

  ImpactedEngineeringObject(String nodeId, EngineeringNode.Kind nodeKind, boolean directChange, int propagationDepth,
      Set<ImpactAction> requiredActions, List<String> propagationPath, Set<String> reasonEdgeIds) {
    this.nodeId = nodeId;
    this.nodeKind = nodeKind;
    this.directChange = directChange;
    this.propagationDepth = propagationDepth;
    this.requiredActions = new ArrayList<ImpactAction>(requiredActions);
    this.propagationPath = new ArrayList<String>(propagationPath);
    this.reasonEdgeIds = new ArrayList<String>(reasonEdgeIds);
    Collections.sort(this.reasonEdgeIds);
  }

  public String getNodeId() {
    return nodeId;
  }

  public EngineeringNode.Kind getNodeKind() {
    return nodeKind;
  }

  public boolean isDirectChange() {
    return directChange;
  }

  public int getPropagationDepth() {
    return propagationDepth;
  }

  public List<ImpactAction> getRequiredActions() {
    return Collections.unmodifiableList(requiredActions);
  }

  public List<String> getPropagationPath() {
    return Collections.unmodifiableList(propagationPath);
  }

  public List<String> getReasonEdgeIds() {
    return Collections.unmodifiableList(reasonEdgeIds);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("nodeId", nodeId);
    result.put("nodeKind", nodeKind.name());
    result.put("directChange", Boolean.valueOf(directChange));
    result.put("propagationDepth", Integer.valueOf(propagationDepth));
    List<String> actions = new ArrayList<String>();
    for (ImpactAction action : requiredActions) {
      actions.add(action.name());
    }
    result.put("requiredActions", actions);
    result.put("propagationPath", new ArrayList<String>(propagationPath));
    result.put("reasonEdgeIds", new ArrayList<String>(reasonEdgeIds));
    return result;
  }
}
