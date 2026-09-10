package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;
import org.junit.jupiter.api.Test;

class EngineeringBlockFlowProjectionTest {
  @Test
  void preservesBranchesProductsRecyclesAndCompleteConnectionProvenance() {
    EngineeringGraph graph = graph();
    String original = graph.toJson();
    Map<String, String> groups = new LinkedHashMap<String, String>();
    groups.put("separator", "Separation");
    groups.put("heater", "Separation");
    groups.put("compressor", "Gas export");
    EngineeringBlockFlowProjection projection = EngineeringBlockFlowProjection.fromGraph(graph, groups);
    List<String> edges = new ArrayList<String>();
    List<String> evidence = new ArrayList<String>();
    for (EngineeringNode node : projection.toGraph().getNodes().values()) {
      if (node.getKind() == EngineeringNode.Kind.PIPE_SEGMENT) {
        edges.add(node.getProperties().get("sourceEquipment") + " -> " + node.getProperties().get("targetEquipment"));
        for (Object id : (List<?>) node.getProperties().get("sourceConnectionIds")) {
          evidence.add(id.toString());
        }
      } else if (node.getKind() == EngineeringNode.Kind.EQUIPMENT) {
        for (Object id : (List<?>) node.getProperties().get("internalConnectionIds")) {
          evidence.add(id.toString());
        }
      }
    }
    assertTrue(edges.containsAll(Arrays.asList("Separation -> Gas export", "Separation -> oil", "Gas export -> fuel",
        "Gas export -> gas", "Gas export -> Separation")), edges.toString());
    assertFalse(edges.contains("gas -> oil"));
    Collections.sort(evidence);
    assertEquals(Arrays.asList("c1", "c2", "c3", "c4", "c5", "c6", "c7"), evidence);
    assertEquals(original, graph.toJson());
    assertEquals(projection.toJson(),
        EngineeringBlockFlowProjection.fromGraph(graph, new TreeMap<String, String>(groups)).toJson());
    projection.toGraph().getNodes().values().iterator().next().setLabel("mutated copy");
    assertFalse(projection.toJson().contains("mutated copy"));
    assertThrows(IllegalArgumentException.class,
        () -> EngineeringBlockFlowProjection.fromGraph(graph, Collections.singletonMap("missing", "Section")));
  }

  @Test
  void rejectsBrokenMaterialEndpointsInsteadOfSilentlyDroppingAFlow() {
    EngineeringGraph graph = graph();
    graph.getNode("c1").putProperty("sourceEndpointId", "missing");
    assertThrows(IllegalArgumentException.class,
        () -> EngineeringBlockFlowProjection.fromGraph(graph, Collections.<String, String>emptyMap()));
  }

  private static EngineeringGraph graph() {
    EngineeringGraph graph = new EngineeringGraph("BFD-REGRESSION", "A");
    for (String name : Arrays.asList("separator", "heater", "compressor", "oil", "gas", "fuel")) {
      graph.addNode(new EngineeringNode(name, EngineeringNode.Kind.EQUIPMENT, name, name));
    }
    String[][] links = { { "separator", "heater" }, { "heater", "oil" }, { "separator", "compressor" },
        { "compressor", "gas" }, { "compressor", "fuel" }, { "compressor", "separator" }, { "heater", "compressor" } };
    for (int index = 0; index < links.length; index++) {
      String id = "c" + (index + 1);
      graph.addNode(new EngineeringNode(id, EngineeringNode.Kind.PIPE_SEGMENT, id, id)
          .putProperty("sourceEndpointId", links[index][0]).putProperty("targetEndpointId", links[index][1])
          .putProperty("connectionType", "MATERIAL").putProperty("recycle", Boolean.valueOf(index == 5)));
    }
    return graph;
  }
}
