package neqsim.process.engineering.safety.lifecycle;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.impact.GeneralizedImpactAnalyzer;
import neqsim.process.engineering.impact.ImpactAction;
import neqsim.process.engineering.impact.ImpactAnalysisResult;
import neqsim.process.engineering.impact.ImpactedEngineeringObject;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.processmodel.lifecycle.event.ModelChangeEvent;

/** Safety-lifecycle revalidation tasks derived from the canonical engineering change-impact graph. */
public final class SafetyStudyRevalidationPlanner {
  /** Engineering-node property used to identify a safety lifecycle work product. */
  public static final String SAFETY_STUDY_TYPE_PROPERTY = "safetyStudyType";

  /** Safety work-product types that require different revalidation actions. */
  public enum SafetyStudyType {
    HAZOP, LOPA, SRS, SIF_RELIABILITY, SIF_DYNAMIC_VERIFICATION, RELIEF_BLOWDOWN_FLARE, FACILITY_RESPONSE
  }

  /** Explicit work actions assigned by safety study type. */
  public enum SafetyRevalidationAction {
    REVIEW_SCENARIO_SET, RECHECK_IPL_ELIGIBILITY, RECALCULATE_FREQUENCY, REVISE_REQUIREMENTS,
    RECALCULATE_PFD_PFH_UNCERTAINTY, RECHECK_DEGRADED_MODES, RERUN_CLOSED_LOOP_SCENARIOS,
    REVIEW_SAFE_STATE_AND_DEADLINE, RECALCULATE_RELIEF_AND_TRANSIENT_DISPOSAL, REVIEW_CONCURRENCY_AND_CAPACITY,
    REBUILD_FACILITY_HANDOFF, REAPPROVE
  }

  /** One traceable safety revalidation task. */
  public static final class Task implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String nodeId;
    private final String label;
    private final SafetyStudyType studyType;
    private final boolean directChange;
    private final int propagationDepth;
    private final List<ImpactAction> graphActions;
    private final List<SafetyRevalidationAction> safetyActions;
    private final List<String> propagationPath;
    private final List<String> reasonEdgeIds;

    private Task(EngineeringNode node, SafetyStudyType studyType, ImpactedEngineeringObject impact) {
      nodeId = node.getId();
      label = node.getLabel();
      this.studyType = studyType;
      directChange = impact.isDirectChange();
      propagationDepth = impact.getPropagationDepth();
      graphActions = Collections.unmodifiableList(new ArrayList<ImpactAction>(impact.getRequiredActions()));
      safetyActions = Collections.unmodifiableList(actionsFor(studyType));
      propagationPath = Collections.unmodifiableList(new ArrayList<String>(impact.getPropagationPath()));
      reasonEdgeIds = Collections.unmodifiableList(new ArrayList<String>(impact.getReasonEdgeIds()));
    }

    /** @return canonical graph node identifier */
    public String getNodeId() {
      return nodeId;
    }

    /** @return safety work-product classification */
    public SafetyStudyType getStudyType() {
      return studyType;
    }

    /** @return immutable safety-specific work list */
    public List<SafetyRevalidationAction> getSafetyActions() {
      return safetyActions;
    }

    /** @return task with graph propagation trace */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("nodeId", nodeId);
      map.put("label", label);
      map.put("safetyStudyType", studyType.name());
      map.put("directChange", Boolean.valueOf(directChange));
      map.put("propagationDepth", Integer.valueOf(propagationDepth));
      List<String> impactActionNames = new ArrayList<String>();
      for (ImpactAction action : graphActions) {
        impactActionNames.add(action.name());
      }
      map.put("graphActions", impactActionNames);
      List<String> safetyActionNames = new ArrayList<String>();
      for (SafetyRevalidationAction action : safetyActions) {
        safetyActionNames.add(action.name());
      }
      map.put("safetyActions", safetyActionNames);
      map.put("propagationPath", new ArrayList<String>(propagationPath));
      map.put("reasonEdgeIds", new ArrayList<String>(reasonEdgeIds));
      map.put("completed", Boolean.FALSE);
      map.put("engineeringApprovalRequired", Boolean.TRUE);
      return map;
    }
  }

  /** Immutable revalidation plan retaining the generalized impact analysis. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final ImpactAnalysisResult impactAnalysis;
    private final List<Task> tasks;
    private final List<String> findings;

    private Result(ImpactAnalysisResult impactAnalysis, List<Task> tasks, List<String> findings) {
      this.impactAnalysis = impactAnalysis;
      this.tasks = Collections.unmodifiableList(new ArrayList<Task>(tasks));
      this.findings = Collections.unmodifiableList(new ArrayList<String>(findings));
    }

    /** @return underlying generalized change-impact result */
    public ImpactAnalysisResult getImpactAnalysis() {
      return impactAnalysis;
    }

    /** @return ordered safety-specific revalidation tasks */
    public List<Task> getTasks() {
      return tasks;
    }

    /** @return explicit plan findings and governance reminders */
    public List<String> getFindings() {
      return findings;
    }

    /** @return true when change propagation identified safety work or reapproval */
    public boolean isRevalidationRequired() {
      return !tasks.isEmpty() || !impactAnalysis.getReapprovalNodeIds().isEmpty();
    }

    /** @return versioned, machine-readable plan */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("schemaVersion", "safety_study_revalidation_plan.v1");
      map.put("impactAnalysis", impactAnalysis.toMap());
      List<Map<String, Object>> taskMaps = new ArrayList<Map<String, Object>>();
      for (Task task : tasks) {
        taskMaps.add(task.toMap());
      }
      map.put("tasks", taskMaps);
      map.put("findings", new ArrayList<String>(findings));
      map.put("revalidationRequired", Boolean.valueOf(isRevalidationRequired()));
      map.put("staleApprovalNodeIds", new ArrayList<String>(impactAnalysis.getReapprovalNodeIds()));
      map.put("fitForConstruction", Boolean.FALSE);
      map.put("engineeringApprovalRequired", Boolean.TRUE);
      return map;
    }

    /** @return pretty-printed JSON plan */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }
  }

  private final GeneralizedImpactAnalyzer impactAnalyzer;

  /** Creates a planner using NeqSim's default engineering impact-propagation rules. */
  public SafetyStudyRevalidationPlanner() {
    this(new GeneralizedImpactAnalyzer());
  }

  /** @param impactAnalyzer configured canonical graph impact analyzer */
  public SafetyStudyRevalidationPlanner(GeneralizedImpactAnalyzer impactAnalyzer) {
    if (impactAnalyzer == null) {
      throw new IllegalArgumentException("impactAnalyzer is required");
    }
    this.impactAnalyzer = impactAnalyzer;
  }

  /**
   * Propagates a governed model change and creates safety-specific revalidation tasks.
   *
   * @param graph canonical engineering dependency graph
   * @param event controlled model change event
   * @return traceable revalidation work plan
   */
  public Result plan(EngineeringGraph graph, ModelChangeEvent event) {
    ImpactAnalysisResult impact = impactAnalyzer.analyze(graph, event);
    List<Task> tasks = new ArrayList<Task>();
    List<String> findings = new ArrayList<String>();
    for (ImpactedEngineeringObject impacted : impact.getImpactedObjects()) {
      EngineeringNode node = graph.getNode(impacted.getNodeId());
      Object rawType = node.getProperties().get(SAFETY_STUDY_TYPE_PROPERTY);
      if (rawType == null) {
        continue;
      }
      SafetyStudyType type = parseType(rawType, node, findings);
      if (type != null) {
        tasks.add(new Task(node, type, impacted));
      }
    }
    if (tasks.isEmpty()) {
      findings.add("No impacted engineering node has a valid safetyStudyType classification");
    }
    if (!impact.getUnresolvedSubjectIds().isEmpty()) {
      findings.add("Unresolved model-change subjects must be closed before the revalidation scope is approved");
    }
    if (!impact.getRecalculationCycleNodeIds().isEmpty()) {
      findings.add("Calculation dependency cycles require manual revalidation sequencing");
    }
    findings.add("Task completion and restored approvals must be recorded outside this generated plan");
    return new Result(impact, tasks, findings);
  }

  private static SafetyStudyType parseType(Object rawType, EngineeringNode node, List<String> findings) {
    try {
      return SafetyStudyType.valueOf(String.valueOf(rawType).trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      findings.add("Unknown safetyStudyType on " + node.getId() + ": " + rawType);
      return null;
    }
  }

  private static List<SafetyRevalidationAction> actionsFor(SafetyStudyType type) {
    List<SafetyRevalidationAction> actions = new ArrayList<SafetyRevalidationAction>();
    switch (type) {
    case HAZOP:
      actions.add(SafetyRevalidationAction.REVIEW_SCENARIO_SET);
      break;
    case LOPA:
      actions.add(SafetyRevalidationAction.RECHECK_IPL_ELIGIBILITY);
      actions.add(SafetyRevalidationAction.RECALCULATE_FREQUENCY);
      break;
    case SRS:
      actions.add(SafetyRevalidationAction.REVISE_REQUIREMENTS);
      break;
    case SIF_RELIABILITY:
      actions.add(SafetyRevalidationAction.RECALCULATE_PFD_PFH_UNCERTAINTY);
      actions.add(SafetyRevalidationAction.RECHECK_DEGRADED_MODES);
      break;
    case SIF_DYNAMIC_VERIFICATION:
      actions.add(SafetyRevalidationAction.RERUN_CLOSED_LOOP_SCENARIOS);
      actions.add(SafetyRevalidationAction.REVIEW_SAFE_STATE_AND_DEADLINE);
      break;
    case RELIEF_BLOWDOWN_FLARE:
      actions.add(SafetyRevalidationAction.RECALCULATE_RELIEF_AND_TRANSIENT_DISPOSAL);
      actions.add(SafetyRevalidationAction.REVIEW_CONCURRENCY_AND_CAPACITY);
      break;
    case FACILITY_RESPONSE:
      actions.add(SafetyRevalidationAction.REBUILD_FACILITY_HANDOFF);
      break;
    default:
      throw new IllegalArgumentException("Unsupported safety study type " + type);
    }
    actions.add(SafetyRevalidationAction.REAPPROVE);
    return actions;
  }
}
