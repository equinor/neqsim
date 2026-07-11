package neqsim.process.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CausalTopologyModel} topology-informed classification of statistical relationships.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class CausalTopologyModelTest {

  /**
   * Builds a simple A -> B -> C process chain plus an unconnected unit D, and verifies that each relationship is
   * classified correctly against the topology.
   */
  @Test
  void classifiesRelationshipsAgainstTopology() {
    // Downstream adjacency: A -> B -> C.
    Map<String, Set<String>> adjacency = new HashMap<String, Set<String>>();
    adjacency.put("A", new HashSet<String>(Arrays.asList("B")));
    adjacency.put("B", new HashSet<String>(Arrays.asList("C")));

    Map<String, String> tagToEquipment = new HashMap<String, String>();
    tagToEquipment.put("A.pressure", "A");
    tagToEquipment.put("B.pressure", "B");
    tagToEquipment.put("C.pressure", "C");
    tagToEquipment.put("A.temp", "A");
    tagToEquipment.put("D.pressure", "D");

    CausalTopologyModel model = new CausalTopologyModel(adjacency, tagToEquipment);

    // A leads C: A is upstream of C -> causal candidate.
    RelationshipGraph.Relationship aLeadsC = new RelationshipGraph.Relationship("A.pressure", "C.pressure",
        RelationshipGraph.Direction.LEADS, 3, 180.0, 0.9, 0.8);
    // C leads A statistically, but A is upstream -> counter-flow.
    RelationshipGraph.Relationship cLeadsA = new RelationshipGraph.Relationship("C.pressure", "A.pressure",
        RelationshipGraph.Direction.LEADS, 2, 120.0, 0.85, 0.7);
    // A.pressure vs A.temp: same equipment -> local.
    RelationshipGraph.Relationship local = new RelationshipGraph.Relationship("A.pressure", "A.temp",
        RelationshipGraph.Direction.SYNCHRONOUS, 0, 0.0, 0.95, 0.95);
    // A vs D: no process path -> common cause / artifact.
    RelationshipGraph.Relationship aVsD = new RelationshipGraph.Relationship("A.pressure", "D.pressure",
        RelationshipGraph.Direction.LEADS, 1, 60.0, 0.8, 0.75);
    // Unmapped tag -> unknown.
    RelationshipGraph.Relationship unmapped = new RelationshipGraph.Relationship("A.pressure", "X.pressure",
        RelationshipGraph.Direction.LEADS, 1, 60.0, 0.8, 0.75);

    List<RelationshipGraph.Relationship> relationships = new ArrayList<RelationshipGraph.Relationship>(
        Arrays.asList(aLeadsC, cLeadsA, local, aVsD, unmapped));

    List<CausalTopologyModel.CausalEdge> edges = model.classify(relationships);
    assertEquals(5, edges.size());
    assertEquals(CausalTopologyModel.Verdict.CAUSAL_CANDIDATE, edges.get(0).getVerdict());
    assertEquals("A", edges.get(0).getLeaderEquipment());
    assertEquals("C", edges.get(0).getFollowerEquipment());
    assertEquals(CausalTopologyModel.Verdict.COUNTER_FLOW, edges.get(1).getVerdict());
    assertEquals(CausalTopologyModel.Verdict.LOCAL, edges.get(2).getVerdict());
    assertEquals(CausalTopologyModel.Verdict.COMMON_CAUSE_OR_ARTIFACT, edges.get(3).getVerdict());
    assertEquals(CausalTopologyModel.Verdict.UNKNOWN, edges.get(4).getVerdict());
    assertNotNull(edges.get(0).toString());
  }

  /**
   * Verifies that a null relationship list yields an empty classification without error.
   */
  @Test
  void handlesNullRelationships() {
    CausalTopologyModel model = new CausalTopologyModel(null, null);
    assertEquals(0, model.classify(null).size());
  }
}
