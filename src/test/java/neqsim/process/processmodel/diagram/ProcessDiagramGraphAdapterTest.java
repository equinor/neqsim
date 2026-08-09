package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.model.EngineeringEdge;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.validation.EngineeringPackageValidator;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessConnection;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Tests the neutral process-to-canonical-diagram topology adapter. */
class ProcessDiagramGraphAdapterTest {

  @Test
  void createsStableAreaEquipmentPortAndConnectionIdentities() {
    Stream feed = createFeed("feed");
    Heater heater = new Heater("heater", feed);
    ProcessSystem process = new ProcessSystem("heating area");
    process.add(feed);
    process.add(heater);

    ProcessDiagramGraphAdapter.Result first = ProcessDiagramGraphAdapter.fromProcessSystem(process, "PLANT-A", "A");
    ProcessDiagramGraphAdapter.Result second = ProcessDiagramGraphAdapter.fromProcessSystem(process, "PLANT-A", "A");

    assertEquals(first.getGraphJson(), second.getGraphJson());
    assertEquals(first.getFingerprint(), second.getFingerprint());
    EngineeringGraph graph = first.getGraph();
    assertNotNull(graph.getNode(EngineeringIds.nodeId(EngineeringNode.Kind.AREA, "PLANT-A/heating area")));
    assertNotNull(graph.getNode(EngineeringIds.nodeId(EngineeringNode.Kind.EQUIPMENT, "PLANT-A/heating area/heater")));
    assertTrue(hasNodeKind(graph, EngineeringNode.Kind.PORT));
    assertTrue(hasNodeKind(graph, EngineeringNode.Kind.NOZZLE));
    assertEquals(1, countNodeKind(graph, EngineeringNode.Kind.PIPE_SEGMENT));
    assertEquals(2, countEdgeKind(graph, EngineeringEdge.Kind.PROCESS_FLOW));
    assertTrue(EngineeringPackageValidator.validateGraph(graph).isValid());
  }

  @Test
  void preservesParallelMaterialConnectionsInCanonicalSnapshot() {
    Stream feed = createFeed("parallel feed");
    feed.run();
    Splitter splitter = new Splitter("parallel splitter", feed);
    splitter.setSplitFactors(new double[] { 0.4, 0.6 });
    splitter.run();
    StreamInterface firstBranch = splitter.getSplitStream(0);
    StreamInterface secondBranch = splitter.getSplitStream(1);
    Mixer mixer = new Mixer("parallel mixer");
    mixer.addStream(firstBranch);
    mixer.addStream(secondBranch);
    ProcessSystem process = new ProcessSystem("parallel area");
    process.add(feed);
    process.add(splitter);
    process.add(mixer);

    EngineeringGraph graph = ProcessDiagramGraphAdapter.fromProcessSystem(process, "PLANT-PARALLEL", "A").getGraph();
    List<EngineeringNode> segments = nodesOfKind(graph, EngineeringNode.Kind.PIPE_SEGMENT);
    int parallelSegments = 0;
    for (EngineeringNode segment : segments) {
      if ("parallel splitter".equals(segment.getProperties().get("sourceEquipment"))
          && "parallel mixer".equals(segment.getProperties().get("targetEquipment"))) {
        parallelSegments++;
      }
    }

    assertEquals(2, parallelSegments);
  }

  @Test
  void representsMultiAreaPlantAsOneGraphWithCrossAreaConnection() {
    Stream feed = createFeed("plant feed");
    Heater upstreamHeater = new Heater("upstream heater", feed);
    ProcessSystem upstream = new ProcessSystem("upstream");
    upstream.add(feed);
    upstream.add(upstreamHeater);

    Heater downstreamHeater = new Heater("downstream heater", upstreamHeater.getOutletStream());
    ProcessSystem downstream = new ProcessSystem("downstream");
    downstream.add(downstreamHeater);

    ProcessModel plant = new ProcessModel();
    plant.add("Upstream", upstream);
    plant.add("Downstream", downstream);

    EngineeringGraph graph = ProcessDiagramGraphAdapter.fromProcessModel(plant, "PLANT-MULTI", "A").getGraph();

    assertEquals(2, countNodeKind(graph, EngineeringNode.Kind.AREA));
    EngineeringNode crossArea = findConnection(graph, "Upstream", "Downstream");
    assertNotNull(crossArea);
    assertEquals(Boolean.TRUE, crossArea.getProperties().get("crossArea"));
    assertEquals(1, countContainsOwners(graph, crossArea.getId()));
    assertTrue(EngineeringPackageValidator.validateGraph(graph).isValid());
  }

  @Test
  void disambiguatesCanonicalAreaNameCollisionsWithoutBreakingGraphValidation() {
    ProcessSystem firstArea = new ProcessSystem("first");
    firstArea.add(createFeed("first feed"));
    ProcessSystem secondArea = new ProcessSystem("second");
    secondArea.add(createFeed("second feed"));
    ProcessModel plant = new ProcessModel();
    plant.add("Area A", firstArea);
    plant.add("Area-A", secondArea);

    ProcessDiagramGraphAdapter.Result result = ProcessDiagramGraphAdapter.fromProcessModel(plant, "PLANT-COLLISION",
        "A");

    assertEquals(2, countNodeKind(result.getGraph(), EngineeringNode.Kind.AREA));
    assertTrue(hasDiagnostic(result, "DIAGRAM_TOPOLOGY_CANONICAL_ID_COLLISION"));
    assertTrue(EngineeringPackageValidator.validateGraph(result.getGraph()).isValid());
  }

  @Test
  void includesTopologyFirstSignalConnectionsAndReturnsDefensiveGraphCopies() {
    Stream feed = createFeed("signal feed");
    Heater heater = new Heater("signal heater", feed);
    ProcessSystem process = new ProcessSystem("signal area");
    process.add(feed);
    process.add(heater);
    process.connect("signal heater", "temperatureSignal", "signal feed", "temperatureSetpoint",
        ProcessConnection.ConnectionType.SIGNAL);

    ProcessDiagramGraphAdapter.Result result = ProcessDiagramGraphAdapter.fromProcessSystem(process, "PLANT-SIGNAL",
        "A");
    EngineeringGraph firstCopy = result.getGraph();
    String temporaryId = EngineeringIds.nodeId(EngineeringNode.Kind.DOCUMENT, "temporary mutation");
    firstCopy.addNode(
        new EngineeringNode(temporaryId, EngineeringNode.Kind.DOCUMENT, "temporary mutation", "Temporary mutation"));
    EngineeringGraph secondCopy = result.getGraph();

    assertEquals(1, countNodeKind(secondCopy, EngineeringNode.Kind.SIGNAL_CONNECTION));
    assertEquals(2, countEdgeKind(secondCopy, EngineeringEdge.Kind.SIGNAL_FLOW));
    assertFalse(secondCopy.getNodes().containsKey(temporaryId));
  }

  private static Stream createFeed(String name) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    return feed;
  }

  private static boolean hasNodeKind(EngineeringGraph graph, EngineeringNode.Kind kind) {
    return countNodeKind(graph, kind) > 0;
  }

  private static int countNodeKind(EngineeringGraph graph, EngineeringNode.Kind kind) {
    return nodesOfKind(graph, kind).size();
  }

  private static List<EngineeringNode> nodesOfKind(EngineeringGraph graph, EngineeringNode.Kind kind) {
    List<EngineeringNode> result = new ArrayList<EngineeringNode>();
    for (EngineeringNode node : graph.getNodes().values()) {
      if (node.getKind() == kind) {
        result.add(node);
      }
    }
    return result;
  }

  private static int countEdgeKind(EngineeringGraph graph, EngineeringEdge.Kind kind) {
    int count = 0;
    for (EngineeringEdge edge : graph.getEdges().values()) {
      if (edge.getKind() == kind) {
        count++;
      }
    }
    return count;
  }

  private static EngineeringNode findConnection(EngineeringGraph graph, String sourceArea, String targetArea) {
    for (EngineeringNode node : nodesOfKind(graph, EngineeringNode.Kind.PIPE_SEGMENT)) {
      if (sourceArea.equals(node.getProperties().get("sourceArea"))
          && targetArea.equals(node.getProperties().get("targetArea"))) {
        return node;
      }
    }
    return null;
  }

  private static int countContainsOwners(EngineeringGraph graph, String nodeId) {
    int count = 0;
    for (EngineeringEdge edge : graph.getEdges().values()) {
      if (edge.getKind() == EngineeringEdge.Kind.CONTAINS && nodeId.equals(edge.getTargetId())) {
        count++;
      }
    }
    return count;
  }

  private static boolean hasDiagnostic(ProcessDiagramGraphAdapter.Result result, String code) {
    for (ProcessDiagramGraphAdapter.Diagnostic diagnostic : result.getDiagnostics()) {
      if (code.equals(diagnostic.getCode())) {
        return true;
      }
    }
    return false;
  }
}
