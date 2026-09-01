package neqsim.process.engineering.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.process.engineering.impact.ImpactAnalysisRule.Direction;
import neqsim.process.engineering.model.EngineeringEdge;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.processmodel.lifecycle.event.ModelChangeEvent;
import neqsim.process.processmodel.lifecycle.event.ModelChangeSubject;
import neqsim.process.processmodel.lifecycle.event.ModelChangeSubject.ChangeKind;
import neqsim.process.processmodel.lifecycle.event.ModelChangeSubject.SubjectType;

class GeneralizedImpactAnalyzerTest {
  @Test
  void propagatesFromEquipmentThroughCalculationsDocumentAndApproval() {
    EngineeringGraph graph = governedGraph();
    String equipmentId = nodeId(EngineeringNode.Kind.EQUIPMENT, "20-VG-001");
    String calculationOne = nodeId(EngineeringNode.Kind.CALCULATION, "CALC-1");
    String calculationTwo = nodeId(EngineeringNode.Kind.CALCULATION, "CALC-2");
    String documentId = nodeId(EngineeringNode.Kind.DOCUMENT, "PROCESS-DESIGN-BASIS");
    String approvalId = nodeId(EngineeringNode.Kind.APPROVAL, "PROCESS-APPROVAL");

    ImpactAnalysisResult result = new GeneralizedImpactAnalyzer().analyze(graph, event(equipmentId));

    assertTrue(result.getImpact(equipmentId).isDirectChange());
    assertTrue(result.getImpact(calculationOne).getRequiredActions().contains(ImpactAction.RECALCULATE));
    assertTrue(result.getImpact(calculationTwo).getRequiredActions().contains(ImpactAction.RECALCULATE));
    assertTrue(result.getImpact(documentId).getRequiredActions().contains(ImpactAction.REGENERATE));
    assertTrue(result.getImpact(approvalId).getRequiredActions().contains(ImpactAction.REAPPROVE));
    assertEquals(Arrays.asList(calculationOne, calculationTwo), result.getRecalculationOrder());
    assertEquals(Collections.singletonList(approvalId), result.getReapprovalNodeIds());
    assertNull(result.getImpact(nodeId(EngineeringNode.Kind.EQUIPMENT, "UNRELATED")));
    assertFalse((Boolean) result.toMap().get("fitnessForConstruction"));
  }

  @Test
  void acceptsProjectSpecificPropagationRules() {
    EngineeringGraph graph = new EngineeringGraph("PROJECT-A", "B");
    String source = nodeId(EngineeringNode.Kind.EQUIPMENT, "SOURCE");
    String target = nodeId(EngineeringNode.Kind.EQUIPMENT, "TARGET");
    graph.addNode(new EngineeringNode(source, EngineeringNode.Kind.EQUIPMENT, "SOURCE", "Source"));
    graph.addNode(new EngineeringNode(target, EngineeringNode.Kind.EQUIPMENT, "TARGET", "Target"));
    graph.addEdge(edge(EngineeringEdge.Kind.CONNECTS_TO, source, target, "project-rule"));
    ImpactAnalysisRule rule = new ImpactAnalysisRule(EngineeringEdge.Kind.CONNECTS_TO, Direction.SOURCE_TO_TARGET,
        ImpactAction.REQUALIFY);

    ImpactAnalysisResult result = new GeneralizedImpactAnalyzer(Collections.singletonList(rule)).analyze(graph,
        Collections.singletonList(source));

    assertNotNull(result.getImpact(target));
    assertTrue(result.getImpact(target).getRequiredActions().contains(ImpactAction.REQUALIFY));
  }

  @Test
  void reportsCalculationCyclesWithoutDroppingImpacts() {
    EngineeringGraph graph = new EngineeringGraph("PROJECT-A", "B");
    String first = nodeId(EngineeringNode.Kind.CALCULATION, "CALC-A");
    String second = nodeId(EngineeringNode.Kind.CALCULATION, "CALC-B");
    graph.addNode(new EngineeringNode(first, EngineeringNode.Kind.CALCULATION, "CALC-A", "Calculation A"));
    graph.addNode(new EngineeringNode(second, EngineeringNode.Kind.CALCULATION, "CALC-B", "Calculation B"));
    graph.addEdge(edge(EngineeringEdge.Kind.DEPENDS_ON, first, second, "input"));
    graph.addEdge(edge(EngineeringEdge.Kind.DEPENDS_ON, second, first, "input"));

    ImpactAnalysisResult result = new GeneralizedImpactAnalyzer().analyze(graph, Collections.singletonList(first));

    assertNotNull(result.getImpact(second));
    assertTrue(result.getRecalculationOrder().isEmpty());
    assertEquals(Arrays.asList(first, second), result.getRecalculationCycleNodeIds());
    assertFalse(result.getPropagationCycleEdgeIds().isEmpty());
  }

  private static EngineeringGraph governedGraph() {
    EngineeringGraph graph = new EngineeringGraph("PROJECT-A", "B");
    String equipment = nodeId(EngineeringNode.Kind.EQUIPMENT, "20-VG-001");
    String calculationOne = nodeId(EngineeringNode.Kind.CALCULATION, "CALC-1");
    String calculationTwo = nodeId(EngineeringNode.Kind.CALCULATION, "CALC-2");
    String document = nodeId(EngineeringNode.Kind.DOCUMENT, "PROCESS-DESIGN-BASIS");
    String approval = nodeId(EngineeringNode.Kind.APPROVAL, "PROCESS-APPROVAL");
    String unrelated = nodeId(EngineeringNode.Kind.EQUIPMENT, "UNRELATED");
    graph.addNode(new EngineeringNode(equipment, EngineeringNode.Kind.EQUIPMENT, "20-VG-001", "Separator"));
    graph.addNode(new EngineeringNode(calculationOne, EngineeringNode.Kind.CALCULATION, "CALC-1", "Calculation 1"));
    graph.addNode(new EngineeringNode(calculationTwo, EngineeringNode.Kind.CALCULATION, "CALC-2", "Calculation 2"));
    graph.addNode(new EngineeringNode(document, EngineeringNode.Kind.DOCUMENT, "PROCESS-DESIGN-BASIS", "Basis"));
    graph.addNode(new EngineeringNode(approval, EngineeringNode.Kind.APPROVAL, "PROCESS-APPROVAL", "Approval"));
    graph.addNode(new EngineeringNode(unrelated, EngineeringNode.Kind.EQUIPMENT, "UNRELATED", "Unrelated"));
    graph.addEdge(edge(EngineeringEdge.Kind.DEPENDS_ON, calculationOne, equipment, "input"));
    graph.addEdge(edge(EngineeringEdge.Kind.DEPENDS_ON, calculationTwo, calculationOne, "prerequisite"));
    graph.addEdge(edge(EngineeringEdge.Kind.GENERATED_FROM, document, calculationTwo, "calculation"));
    graph.addEdge(edge(EngineeringEdge.Kind.APPROVES, approval, document, "process"));
    return graph;
  }

  private static ModelChangeEvent event(String subjectId) {
    ModelChangeSubject subject = new ModelChangeSubject(subjectId, SubjectType.ENGINEERING_NODE, "EQUIPMENT",
        ChangeKind.MODIFIED, Collections.singletonList("designPressureBara"));
    return new ModelChangeEvent("EVENT-001", "KEY-001", ModelChangeEvent.EventType.MODEL_REVISED,
        Instant.parse("2026-07-18T08:00:00Z"), "NEQSIM", "PROCESS-TEAM", "", "ASSET-A", "MODEL-A", "A", "B",
        "Design pressure updated", Collections.singletonList(subject), Collections.<String>emptyList(),
        Collections.<String>emptyList());
  }

  private static EngineeringEdge edge(EngineeringEdge.Kind kind, String source, String target, String role) {
    return new EngineeringEdge(EngineeringIds.edgeId(kind, source, target, role), source, target, kind, role);
  }

  private static String nodeId(EngineeringNode.Kind kind, String externalKey) {
    return EngineeringIds.nodeId(kind, externalKey);
  }
}
