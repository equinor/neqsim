package neqsim.process.engineering.safety.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.util.Collections;
import neqsim.process.engineering.model.EngineeringEdge;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.safety.lifecycle.SafetyStudyRevalidationPlanner.SafetyRevalidationAction;
import neqsim.process.engineering.safety.lifecycle.SafetyStudyRevalidationPlanner.SafetyStudyType;
import neqsim.process.processmodel.lifecycle.event.ModelChangeEvent;
import neqsim.process.processmodel.lifecycle.event.ModelChangeSubject;
import neqsim.process.processmodel.lifecycle.event.ModelChangeSubject.ChangeKind;
import neqsim.process.processmodel.lifecycle.event.ModelChangeSubject.SubjectType;
import org.junit.jupiter.api.Test;

/** Tests safety-specific work planning on top of generalized graph change propagation. */
class SafetyStudyRevalidationPlannerTest {

  @Test
  void propagatesEquipmentChangeAcrossSafetyLifecycleAndInvalidatesApproval() {
    EngineeringGraph graph = safetyGraph();
    SafetyStudyRevalidationPlanner.Result result = new SafetyStudyRevalidationPlanner().plan(graph,
        event(nodeId(EngineeringNode.Kind.EQUIPMENT, "V-101")));

    assertTrue(result.isRevalidationRequired());
    assertEquals(5, result.getTasks().size());
    assertNotNull(task(result, SafetyStudyType.HAZOP));
    SafetyStudyRevalidationPlanner.Task lopa = task(result, SafetyStudyType.LOPA);
    assertNotNull(lopa);
    assertTrue(lopa.getSafetyActions().contains(SafetyRevalidationAction.RECHECK_IPL_ELIGIBILITY));
    assertTrue(lopa.getSafetyActions().contains(SafetyRevalidationAction.RECALCULATE_FREQUENCY));
    assertFalse(result.getImpactAnalysis().getReapprovalNodeIds().isEmpty());
    assertTrue(result.toJson().contains("RERUN_CLOSED_LOOP_SCENARIOS"));
    assertTrue(result.toJson().contains("fitForConstruction"));
  }

  private SafetyStudyRevalidationPlanner.Task task(SafetyStudyRevalidationPlanner.Result result, SafetyStudyType type) {
    for (SafetyStudyRevalidationPlanner.Task task : result.getTasks()) {
      if (task.getStudyType() == type) {
        return task;
      }
    }
    return null;
  }

  private EngineeringGraph safetyGraph() {
    EngineeringGraph graph = new EngineeringGraph("PROJECT-SAFETY", "B");
    String equipment = add(graph, EngineeringNode.Kind.EQUIPMENT, "V-101", "Separator", null);
    String hazop = add(graph, EngineeringNode.Kind.DOCUMENT, "HAZOP-101", "HAZOP node", SafetyStudyType.HAZOP);
    String lopa = add(graph, EngineeringNode.Kind.CALCULATION, "LOPA-101", "LOPA scenario", SafetyStudyType.LOPA);
    String srs = add(graph, EngineeringNode.Kind.DOCUMENT, "SRS-101", "Safety requirements", SafetyStudyType.SRS);
    String dynamic = add(graph, EngineeringNode.Kind.CALCULATION, "SIF-DYNAMIC-101", "Closed-loop verification",
        SafetyStudyType.SIF_DYNAMIC_VERIFICATION);
    String facility = add(graph, EngineeringNode.Kind.CALCULATION, "FACILITY-101", "Facility response",
        SafetyStudyType.FACILITY_RESPONSE);
    String approval = add(graph, EngineeringNode.Kind.APPROVAL, "SAFETY-APPROVAL-101", "Safety approval", null);
    graph.addEdge(edge(EngineeringEdge.Kind.GENERATED_FROM, hazop, equipment, "process-basis"));
    graph.addEdge(edge(EngineeringEdge.Kind.DEPENDS_ON, lopa, hazop, "hazop-scenario"));
    graph.addEdge(edge(EngineeringEdge.Kind.GENERATED_FROM, srs, lopa, "lopa-gap"));
    graph.addEdge(edge(EngineeringEdge.Kind.DEPENDS_ON, dynamic, srs, "safe-state-requirement"));
    graph.addEdge(edge(EngineeringEdge.Kind.DEPENDS_ON, facility, dynamic, "dynamic-evidence"));
    graph.addEdge(edge(EngineeringEdge.Kind.APPROVES, approval, facility, "facility-safety"));
    return graph;
  }

  private String add(EngineeringGraph graph, EngineeringNode.Kind kind, String key, String label,
      SafetyStudyType type) {
    String id = nodeId(kind, key);
    EngineeringNode node = new EngineeringNode(id, kind, key, label);
    if (type != null) {
      node.putProperty(SafetyStudyRevalidationPlanner.SAFETY_STUDY_TYPE_PROPERTY, type.name());
    }
    graph.addNode(node);
    return id;
  }

  private ModelChangeEvent event(String subjectId) {
    ModelChangeSubject subject = new ModelChangeSubject(subjectId, SubjectType.ENGINEERING_NODE, "EQUIPMENT",
        ChangeKind.MODIFIED, Collections.singletonList("designPressureBara"));
    return new ModelChangeEvent("EVENT-SAFETY-001", "KEY-SAFETY-001", ModelChangeEvent.EventType.MODEL_REVISED,
        Instant.parse("2026-07-18T08:00:00Z"), "NEQSIM", "PROCESS-TEAM", "", "ASSET-A", "MODEL-A", "A", "B",
        "Separator design pressure updated", Collections.singletonList(subject), Collections.<String>emptyList(),
        Collections.singletonList("MOC-2026-014"));
  }

  private EngineeringEdge edge(EngineeringEdge.Kind kind, String source, String target, String role) {
    return new EngineeringEdge(EngineeringIds.edgeId(kind, source, target, role), source, target, kind, role);
  }

  private String nodeId(EngineeringNode.Kind kind, String key) {
    return EngineeringIds.nodeId(kind, key);
  }
}
