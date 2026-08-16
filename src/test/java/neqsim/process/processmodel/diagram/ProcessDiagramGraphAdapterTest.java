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
  void preservesParallelEnergyAndSignalConnectionsByExplicitPorts() {
    Stream feed = createFeed("parallel explicit feed");
    Heater source = new Heater("parallel explicit source", feed);
    Heater target = new Heater("parallel explicit target", source.getOutletStream());
    ProcessSystem process = new ProcessSystem("parallel explicit area");
    process.add(feed);
    process.add(source);
    process.add(target);
    process.connect(source.getName(), "duty-out-a", target.getName(), "duty-in-a",
        ProcessConnection.ConnectionType.ENERGY);
    process.connect(source.getName(), "duty-out-b", target.getName(), "duty-in-b",
        ProcessConnection.ConnectionType.ENERGY);
    process.connect(target.getName(), "temperature-signal-a", source.getName(), "temperature-setpoint-a",
        ProcessConnection.ConnectionType.SIGNAL);
    process.connect(target.getName(), "temperature-signal-b", source.getName(), "temperature-setpoint-b",
        ProcessConnection.ConnectionType.SIGNAL);

    EngineeringGraph graph = ProcessDiagramGraphAdapter.fromProcessSystem(process, "PLANT-PARALLEL-EXPLICIT", "A")
        .getGraph();

    assertEquals(2, countNodeKind(graph, EngineeringNode.Kind.ENERGY_CONNECTION));
    assertEquals(2, countNodeKind(graph, EngineeringNode.Kind.SIGNAL_CONNECTION));
    assertEquals(4, countEdgeKind(graph, EngineeringEdge.Kind.ENERGY_FLOW));
    assertEquals(4, countEdgeKind(graph, EngineeringEdge.Kind.SIGNAL_FLOW));
    assertTrue(EngineeringPackageValidator.validateGraph(graph).isValid());
  }

  @Test
  void reportsAmbiguousDuplicateConnectionDeclarationsInsteadOfSilentlyLosingMultiplicity() {
    Stream feed = createFeed("duplicate connection feed");
    Heater heater = new Heater("duplicate connection heater", feed);
    ProcessSystem process = new ProcessSystem("duplicate connection area");
    process.add(feed);
    process.add(heater);
    process.connect(heater.getName(), "temperature-signal", feed.getName(), "temperature-setpoint",
        ProcessConnection.ConnectionType.SIGNAL);
    process.connect(heater.getName(), "temperature-signal", feed.getName(), "temperature-setpoint",
        ProcessConnection.ConnectionType.SIGNAL);

    ProcessDiagramGraphAdapter.Result result = ProcessDiagramGraphAdapter.fromProcessSystem(process,
        "PLANT-DUPLICATE-CONNECTION", "A");

    assertEquals(1, countNodeKind(result.getGraph(), EngineeringNode.Kind.SIGNAL_CONNECTION));
    assertTrue(hasDiagnostic(result, "DIAGRAM_TOPOLOGY_DUPLICATE_CONNECTION_COLLAPSED"));
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
  void keepsCrossAreaSnapshotDeterministicAcrossEquivalentFreshModels() {
    String expectedJson = null;
    String expectedFingerprint = null;
    for (int attempt = 0; attempt < 32; attempt++) {
      ProcessDiagramGraphAdapter.Result result = ProcessDiagramGraphAdapter.fromProcessModel(createFourAreaPlant(),
          "PLANT-DETERMINISTIC", "A");
      if (expectedJson == null) {
        expectedJson = result.getGraphJson();
        expectedFingerprint = result.getFingerprint();
      } else {
        assertEquals(expectedJson, result.getGraphJson());
        assertEquals(expectedFingerprint, result.getFingerprint());
      }
    }
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

  @Test
  void capturesCaseScopedUnitExplicitOperatingValuesWithoutChangingTopologyOnlyApi() {
    Stream feed = createFeed("operating feed");
    Heater heater = new Heater("operating heater", feed);
    ProcessSystem process = new ProcessSystem("operating area");
    process.add(feed);
    process.add(heater);
    process.run();

    EngineeringGraph topologyOnly = ProcessDiagramGraphAdapter.fromProcessSystem(process, "PLANT-OPERATING", "A")
        .getGraph();
    EngineeringGraph enriched = ProcessDiagramGraphAdapter
        .fromProcessSystem(process, "PLANT-OPERATING", "A", "NORMAL-001").getGraph();

    assertEquals(0, countNodeKind(topologyOnly, EngineeringNode.Kind.DESIGN_CASE));
    assertEquals(0, countNodeKind(topologyOnly, EngineeringNode.Kind.CALCULATION));
    assertEquals(1, countNodeKind(enriched, EngineeringNode.Kind.DESIGN_CASE));
    assertEquals(4, countNodeKind(enriched, EngineeringNode.Kind.CALCULATION));

    EngineeringNode pressure = findOperatingValue(enriched, "operating feed", "pressure");
    EngineeringNode temperature = findOperatingValue(enriched, "operating feed", "temperature");
    EngineeringNode massFlow = findOperatingValue(enriched, "operating feed", "massFlow");
    EngineeringNode specificEnthalpy = findOperatingValue(enriched, "operating feed", "specificEnthalpy");
    assertNotNull(pressure);
    assertNotNull(temperature);
    assertNotNull(massFlow);
    assertNotNull(specificEnthalpy);
    assertEquals(40.0, ((Number) pressure.getProperties().get("resultValue")).doubleValue(), 1.0e-12);
    assertEquals("bara", pressure.getProperties().get("resultUnit"));
    assertEquals("ABSOLUTE", pressure.getProperties().get("quantityBasis"));
    assertEquals(298.15, ((Number) temperature.getProperties().get("resultValue")).doubleValue(), 1.0e-12);
    assertEquals("K", temperature.getProperties().get("resultUnit"));
    assertEquals(1000.0 / 3600.0, ((Number) massFlow.getProperties().get("resultValue")).doubleValue(), 1.0e-12);
    assertEquals("kg/s", massFlow.getProperties().get("resultUnit"));
    assertEquals("J/kg", specificEnthalpy.getProperties().get("resultUnit"));
    assertEquals("MASS_SPECIFIC", specificEnthalpy.getProperties().get("quantityBasis"));
    assertEquals("NORMAL-001", pressure.getProperties().get("designCaseId"));
    assertEquals("SIMULATION_RESULT", pressure.getProvenance().get(0).getSourceType());
    assertEquals("NORMAL-001", pressure.getProvenance().get(0).getDesignCaseId());
    assertEquals("REVIEW_REQUIRED", pressure.getProvenance().get(0).getApprovalStatus());
    assertTrue(EngineeringPackageValidator.validateGraph(enriched).isValid());
  }

  @Test
  void capturesOneOperatingCaseAcrossMultiAreaProcessModel() {
    ProcessModel plant = createFourAreaPlant();
    plant.run();

    ProcessDiagramGraphAdapter.Result result = ProcessDiagramGraphAdapter.fromProcessModel(plant,
        "PLANT-MULTI-OPERATING", "A", "NORMAL-001");

    assertEquals(1, countNodeKind(result.getGraph(), EngineeringNode.Kind.DESIGN_CASE));
    assertEquals(4, countNodeKind(result.getGraph(), EngineeringNode.Kind.CALCULATION));
    assertNotNull(findOperatingValue(result.getGraph(), "deterministic feed", "pressure"));
    assertTrue(EngineeringPackageValidator.validateGraph(result.getGraph()).isValid());
  }

  @Test
  void reportsMissingSuccessfulRunInsteadOfPublishingStaleOperatingValues() {
    Stream feed = createFeed("not run feed");
    ProcessSystem process = new ProcessSystem("not run area");
    process.add(feed);

    ProcessDiagramGraphAdapter.Result result = ProcessDiagramGraphAdapter.fromProcessSystem(process, "PLANT-NOT-RUN",
        "A", "NORMAL-001");

    assertEquals(1, countNodeKind(result.getGraph(), EngineeringNode.Kind.DESIGN_CASE));
    assertEquals(0, countNodeKind(result.getGraph(), EngineeringNode.Kind.CALCULATION));
    assertTrue(hasDiagnostic(result, "DIAGRAM_OPERATING_CASE_NOT_SUCCESSFUL"));
    assertTrue(EngineeringPackageValidator.validateGraph(result.getGraph()).isValid());
  }

  @Test
  void keepsOperatingCaseSnapshotDeterministicAcrossEquivalentFreshModels() {
    String expectedJson = null;
    for (int attempt = 0; attempt < 8; attempt++) {
      Stream feed = createFeed("deterministic operating feed");
      Heater heater = new Heater("deterministic operating heater", feed);
      ProcessSystem process = new ProcessSystem("deterministic operating area");
      process.add(feed);
      process.add(heater);
      process.run();
      String graphJson = ProcessDiagramGraphAdapter
          .fromProcessSystem(process, "PLANT-DETERMINISTIC-OPERATING", "A", "NORMAL-001").getGraphJson();
      if (expectedJson == null) {
        expectedJson = graphJson;
      } else {
        assertEquals(expectedJson, graphJson);
      }
    }
  }

  private static Stream createFeed(String name) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    return feed;
  }

  private static ProcessModel createFourAreaPlant() {
    Stream feed = createFeed("deterministic feed");
    Heater firstHeater = new Heater("first heater", feed);
    ProcessSystem firstArea = new ProcessSystem("first area");
    firstArea.add(feed);
    firstArea.add(firstHeater);

    Heater secondHeater = new Heater("second heater", firstHeater.getOutletStream());
    ProcessSystem secondArea = new ProcessSystem("second area");
    secondArea.add(secondHeater);

    Heater thirdHeater = new Heater("third heater", secondHeater.getOutletStream());
    ProcessSystem thirdArea = new ProcessSystem("third area");
    thirdArea.add(thirdHeater);

    Heater fourthHeater = new Heater("fourth heater", thirdHeater.getOutletStream());
    ProcessSystem fourthArea = new ProcessSystem("fourth area");
    fourthArea.add(fourthHeater);

    ProcessModel plant = new ProcessModel();
    plant.add("Area 1", firstArea);
    plant.add("Area 2", secondArea);
    plant.add("Area 3", thirdArea);
    plant.add("Area 4", fourthArea);
    return plant;
  }

  private static boolean hasNodeKind(EngineeringGraph graph, EngineeringNode.Kind kind) {
    return countNodeKind(graph, kind) > 0;
  }

  private static int countNodeKind(EngineeringGraph graph, EngineeringNode.Kind kind) {
    return nodesOfKind(graph, kind).size();
  }

  private static EngineeringNode findOperatingValue(EngineeringGraph graph, String subjectName, String quantity) {
    for (EngineeringNode node : nodesOfKind(graph, EngineeringNode.Kind.CALCULATION)) {
      if (subjectName.equals(node.getProperties().get("subjectName"))
          && quantity.equals(node.getProperties().get("quantity"))) {
        return node;
      }
    }
    return null;
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
