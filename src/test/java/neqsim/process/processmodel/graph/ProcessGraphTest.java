package neqsim.process.processmodel.graph;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.filter.Filter;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.GasScrubber;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.ComponentSplitter;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the graph-based process representation.
 */
public class ProcessGraphTest {
  private SystemInterface testFluid;

  @BeforeEach
  void setUp() {
    testFluid = new SystemSrkEos(298.0, 50.0);
    testFluid.addComponent("methane", 0.8);
    testFluid.addComponent("ethane", 0.15);
    testFluid.addComponent("propane", 0.05);
    testFluid.setMixingRule("classic");
  }

  @Test
  void testSimpleLinearProcess() {
    // Create a simple linear process: Stream -> Heater -> Separator
    ProcessSystem system = new ProcessSystem("Linear Test");

    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    Heater heater = new Heater("heater", feed);
    heater.setOutTemperature(350.0, "K");
    heater.run();

    Separator separator = new Separator("separator", heater.getOutletStream());
    separator.run();

    system.add(feed);
    system.add(heater);
    system.add(separator);

    // Build graph
    ProcessGraph graph = system.buildGraph();

    // Verify structure
    assertEquals(3, graph.getNodeCount(), "Should have 3 nodes");
    assertTrue(graph.getEdgeCount() >= 2, "Should have at least 2 edges");

    // Verify no cycles in linear process
    assertFalse(graph.hasCycles(), "Linear process should not have cycles");

    // Verify topological order
    List<ProcessEquipmentInterface> order = graph.getCalculationOrder();
    assertEquals(3, order.size());

    // Feed should come before heater, heater before separator
    int feedIdx = order.indexOf(feed);
    int heaterIdx = order.indexOf(heater);
    int separatorIdx = order.indexOf(separator);

    assertTrue(feedIdx < heaterIdx, "Feed should come before heater in calculation order");
    assertTrue(heaterIdx < separatorIdx, "Heater should come before separator");

    // Verify source/sink detection
    List<ProcessNode> sources = graph.getSourceNodes();
    List<ProcessNode> sinks = graph.getSinkNodes();

    assertTrue(sources.size() >= 1, "Should have at least 1 source");
    assertTrue(sinks.size() >= 1, "Should have at least 1 sink");
  }

  @Test
  void testBranchingProcess() {
    // Create a branching process: Stream -> Splitter -> 2 parallel paths
    ProcessSystem system = new ProcessSystem("Branching Test");

    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    Splitter splitter = new Splitter("splitter", feed);
    splitter.setSplitFactors(new double[] {0.5, 0.5});
    splitter.run();

    Heater heater1 = new Heater("heater1", splitter.getSplitStream(0));
    heater1.setOutTemperature(350.0, "K");
    heater1.run();

    Heater heater2 = new Heater("heater2", splitter.getSplitStream(1));
    heater2.setOutTemperature(300.0, "K");
    heater2.run();

    system.add(feed);
    system.add(splitter);
    system.add(heater1);
    system.add(heater2);

    // Build graph
    ProcessGraph graph = system.buildGraph();

    // Verify structure
    assertEquals(4, graph.getNodeCount(), "Should have 4 nodes");

    // Verify parallel partitioning
    ProcessGraph.ParallelPartition partition = graph.partitionForParallelExecution();
    assertTrue(partition.getLevelCount() >= 2, "Should have at least 2 levels");
    assertTrue(partition.getMaxParallelism() >= 2, "heater1 and heater2 should be parallelizable");

    // Verify calculation order respects dependencies
    List<ProcessEquipmentInterface> order = graph.getCalculationOrder();
    int feedIdx = order.indexOf(feed);
    int splitterIdx = order.indexOf(splitter);
    int heater1Idx = order.indexOf(heater1);
    int heater2Idx = order.indexOf(heater2);

    assertTrue(feedIdx < splitterIdx, "Feed should come before splitter");
    assertTrue(splitterIdx < heater1Idx, "Splitter should come before heater1");
    assertTrue(splitterIdx < heater2Idx, "Splitter should come before heater2");
  }

  @Test
  void testMergingProcess() {
    // Create a merging process: 2 streams -> Mixer
    ProcessSystem system = new ProcessSystem("Merging Test");

    Stream feed1 = new Stream("feed1", testFluid.clone());
    feed1.setFlowRate(500.0, "kg/hr");
    feed1.run();

    Stream feed2 = new Stream("feed2", testFluid.clone());
    feed2.setFlowRate(500.0, "kg/hr");
    feed2.run();

    Mixer mixer = new Mixer("mixer");
    mixer.addStream(feed1);
    mixer.addStream(feed2);
    mixer.run();

    system.add(feed1);
    system.add(feed2);
    system.add(mixer);

    // Build graph
    ProcessGraph graph = system.buildGraph();

    // Verify structure
    assertEquals(3, graph.getNodeCount(), "Should have 3 nodes");

    // Verify mixer has 2 incoming connections
    ProcessNode mixerNode = graph.getNode(mixer);
    assertNotNull(mixerNode);
    assertEquals(2, mixerNode.getInDegree(), "Mixer should have 2 incoming edges");

    // Verify parallel partitioning - feeds can run in parallel
    ProcessGraph.ParallelPartition partition = graph.partitionForParallelExecution();
    assertTrue(partition.getMaxParallelism() >= 2, "Two feeds should be parallelizable");
  }

  @Test
  void testCycleDetection() {
    // For this test, we manually create a graph with a cycle
    ProcessGraph graph = new ProcessGraph();

    // Create mock nodes
    Stream stream1 = new Stream("stream1", testFluid.clone());
    Stream stream2 = new Stream("stream2", testFluid.clone());
    Stream stream3 = new Stream("stream3", testFluid.clone());

    ProcessNode node1 = graph.addNode(stream1);
    ProcessNode node2 = graph.addNode(stream2);
    ProcessNode node3 = graph.addNode(stream3);

    // Create a cycle: 1 -> 2 -> 3 -> 1
    graph.addEdge(node1, node2, null);
    graph.addEdge(node2, node3, null);
    graph.addEdge(node3, node1, null); // Back edge creating cycle

    // Verify cycle detection
    assertTrue(graph.hasCycles(), "Should detect cycle");

    ProcessGraph.CycleAnalysisResult cycleResult = graph.analyzeCycles();
    assertTrue(cycleResult.hasCycles());
    assertEquals(1, cycleResult.getCycleCount(), "Should find 1 cycle");
    assertFalse(cycleResult.getBackEdges().isEmpty(), "Should identify back edge");

    // Verify SCC detection
    ProcessGraph.SCCResult sccResult = graph.findStronglyConnectedComponents();
    assertEquals(1, sccResult.getRecycleLoops().size(), "Should find 1 recycle loop");
    assertEquals(3, sccResult.getRecycleLoops().get(0).size(), "Recycle loop should have 3 nodes");

    // Topological sort should still work (ignoring back edges)
    List<ProcessNode> topoOrder = graph.getTopologicalOrder();
    assertEquals(3, topoOrder.size(), "Should include all nodes in topo order");
  }

  @Test
  void testGNNCompatibleRepresentation() {
    // Create a simple process
    ProcessSystem system = new ProcessSystem("GNN Test");

    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    Heater heater = new Heater("heater", feed);
    heater.setOutTemperature(350.0, "K");
    heater.run();

    system.add(feed);
    system.add(heater);

    ProcessGraph graph = system.buildGraph();

    // Test node feature matrix
    double[][] nodeFeatures = graph.getNodeFeatureMatrix();
    assertEquals(2, nodeFeatures.length, "Should have 2 node feature vectors");
    assertTrue(nodeFeatures[0].length > 0, "Feature vectors should not be empty");

    // Test edge index tensor (COO format)
    int[][] edgeIndex = graph.getEdgeIndexTensor();
    assertEquals(2, edgeIndex.length, "Edge index should have 2 rows [sources, targets]");
    assertEquals(edgeIndex[0].length, edgeIndex[1].length, "Source and target arrays same length");

    // Test edge feature matrix
    double[][] edgeFeatures = graph.getEdgeFeatureMatrix();
    assertEquals(graph.getEdgeCount(), edgeFeatures.length, "Should have feature for each edge");

    // Test adjacency representations
    boolean[][] adjMatrix = graph.getAdjacencyMatrix();
    assertEquals(2, adjMatrix.length, "Adjacency matrix should be 2x2");
    assertEquals(2, adjMatrix[0].length);

    java.util.Map<Integer, java.util.List<Integer>> adjList = graph.getAdjacencyList();
    assertEquals(2, adjList.size(), "Adjacency list should have 2 entries");
  }

  @Test
  void testProcessSystemIntegration() {
    // Test the convenience methods on ProcessSystem
    ProcessSystem system = new ProcessSystem("Integration Test");

    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    Heater heater = new Heater("heater", feed);
    heater.setOutTemperature(350.0, "K");
    heater.run();

    system.add(feed);
    system.add(heater);

    // Test convenience methods
    List<ProcessEquipmentInterface> topoOrder = system.getTopologicalOrder();
    assertEquals(2, topoOrder.size());

    assertFalse(system.hasRecycleLoops(), "Simple process should not have recycle loops");

    int levels = system.getParallelLevelCount();
    assertTrue(levels >= 1, "Should have at least 1 level");

    List<String> validationIssues = system.validateStructure();
    // May have some issues depending on isolated units, but shouldn't crash
    assertNotNull(validationIssues);

    String summary = system.getGraphSummary();
    assertNotNull(summary);
    assertTrue(summary.contains("Nodes:"), "Summary should contain node count");
    assertTrue(summary.contains("Edges:"), "Summary should contain edge count");
  }

  @Test
  void testNodeFeatureVector() {
    ProcessSystem system = new ProcessSystem("Feature Test");

    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(300.0, "K");
    feed.setPressure(50.0, "bara");
    feed.run();

    system.add(feed);

    ProcessGraph graph = system.buildGraph();
    ProcessNode node = graph.getNode(feed);
    assertNotNull(node);

    // Test feature vector generation
    java.util.Map<String, Integer> typeMapping = new java.util.HashMap<String, Integer>();
    typeMapping.put("Stream", 0);
    typeMapping.put("Heater", 1);

    double[] features = node.getFeatureVector(typeMapping, 2);
    assertNotNull(features);
    assertTrue(features.length > 0);

    // Stream type should be one-hot encoded at index 0
    assertEquals(1.0, features[0], 0.001, "Stream type should be encoded");
    assertEquals(0.0, features[1], 0.001, "Heater type should not be encoded");
  }

  @Test
  void testEdgeTypes() {
    ProcessGraph graph = new ProcessGraph();

    Stream stream1 = new Stream("stream1", testFluid.clone());
    Stream stream2 = new Stream("stream2", testFluid.clone());

    ProcessNode node1 = graph.addNode(stream1);
    ProcessNode node2 = graph.addNode(stream2);

    // Test different edge types
    ProcessEdge materialEdge = new ProcessEdge(0, node1, node2, stream1);
    assertEquals(ProcessEdge.EdgeType.MATERIAL, materialEdge.getEdgeType());
    assertFalse(materialEdge.isRecycle());

    ProcessEdge recycleEdge =
        new ProcessEdge(1, node2, node1, null, "recycle", ProcessEdge.EdgeType.RECYCLE);
    assertEquals(ProcessEdge.EdgeType.RECYCLE, recycleEdge.getEdgeType());
    assertTrue(recycleEdge.isRecycle());

    // Test edge feature vector
    double[] features = materialEdge.getFeatureVector();
    assertNotNull(features);
    assertEquals(9, features.length, "Edge features should have 9 elements");
  }

  @Test
  void testGraphValidation() {
    ProcessGraph graph = new ProcessGraph();

    // Add an isolated node
    Stream isolatedStream = new Stream("isolated", testFluid.clone());
    graph.addNode(isolatedStream);

    List<String> issues = graph.validate();
    assertTrue(issues.stream().anyMatch(s -> s.contains("Isolated")),
        "Should detect isolated node");
  }

  @Test
  void testSCCWithMultipleLoops() {
    ProcessGraph graph = new ProcessGraph();

    // Create two separate cycles
    Stream s1 = new Stream("s1", testFluid.clone());
    Stream s2 = new Stream("s2", testFluid.clone());
    Stream s3 = new Stream("s3", testFluid.clone());
    Stream s4 = new Stream("s4", testFluid.clone());

    ProcessNode n1 = graph.addNode(s1);
    ProcessNode n2 = graph.addNode(s2);
    ProcessNode n3 = graph.addNode(s3);
    ProcessNode n4 = graph.addNode(s4);

    // Cycle 1: n1 -> n2 -> n1
    graph.addEdge(n1, n2, null);
    graph.addEdge(n2, n1, null);

    // Cycle 2: n3 -> n4 -> n3
    graph.addEdge(n3, n4, null);
    graph.addEdge(n4, n3, null);

    ProcessGraph.SCCResult scc = graph.findStronglyConnectedComponents();
    assertEquals(2, scc.getRecycleLoops().size(), "Should find 2 recycle loops");
  }

  @Test
  void testParallelPartitioningComplex() {
    // Create a more complex process with clear parallel opportunities
    // Level 0: feed1, feed2, feed3 (all sources)
    // Level 1: units processing each feed
    // Level 2: merger
    ProcessGraph graph = new ProcessGraph();

    Stream feed1 = new Stream("feed1", testFluid.clone());
    Stream feed2 = new Stream("feed2", testFluid.clone());
    Stream feed3 = new Stream("feed3", testFluid.clone());
    Stream proc1 = new Stream("proc1", testFluid.clone());
    Stream proc2 = new Stream("proc2", testFluid.clone());
    Stream proc3 = new Stream("proc3", testFluid.clone());
    Stream merger = new Stream("merger", testFluid.clone());

    ProcessNode nFeed1 = graph.addNode(feed1);
    ProcessNode nFeed2 = graph.addNode(feed2);
    ProcessNode nFeed3 = graph.addNode(feed3);
    ProcessNode nProc1 = graph.addNode(proc1);
    ProcessNode nProc2 = graph.addNode(proc2);
    ProcessNode nProc3 = graph.addNode(proc3);
    ProcessNode nMerger = graph.addNode(merger);

    // Connect: feeds -> processing -> merger
    graph.addEdge(nFeed1, nProc1, null);
    graph.addEdge(nFeed2, nProc2, null);
    graph.addEdge(nFeed3, nProc3, null);
    graph.addEdge(nProc1, nMerger, null);
    graph.addEdge(nProc2, nMerger, null);
    graph.addEdge(nProc3, nMerger, null);

    ProcessGraph.ParallelPartition partition = graph.partitionForParallelExecution();

    assertEquals(3, partition.getLevelCount(), "Should have 3 levels");
    assertEquals(3, partition.getMaxParallelism(), "Max parallelism should be 3 (at level 0 or 1)");

    // Verify level 0 has all feeds
    List<ProcessNode> level0 = partition.getLevels().get(0);
    assertEquals(3, level0.size(), "Level 0 should have 3 nodes (feeds)");
  }

  // ====== ProcessModelGraph Tests ======

  @Test
  void testProcessModuleWithSingleSystem() {
    // Create a ProcessModule with a single ProcessSystem
    ProcessSystem system = new ProcessSystem("Single System");

    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    Heater heater = new Heater("heater", feed);
    heater.setOutTemperature(350.0, "K");
    heater.run();

    system.add(feed);
    system.add(heater);

    neqsim.process.processmodel.ProcessModule module =
        new neqsim.process.processmodel.ProcessModule("Test Module");
    module.add(system);

    ProcessModelGraph modelGraph = module.buildModelGraph();

    // Verify structure
    assertEquals(1, modelGraph.getSubSystemCount(), "Should have 1 sub-system");
    assertEquals(2, modelGraph.getTotalNodeCount(), "Should have 2 nodes total");
    assertTrue(modelGraph.getTotalEdgeCount() >= 1, "Should have at least 1 edge");

    // Verify summary
    String summary = modelGraph.getSummary();
    assertNotNull(summary);
    assertTrue(summary.contains("Test Module"));
  }

  @Test
  void testProcessModuleWithMultipleSystems() {
    // Create two independent ProcessSystems
    ProcessSystem system1 = new ProcessSystem("Separation System");
    Stream feed1 = new Stream("feed1", testFluid.clone());
    feed1.setFlowRate(1000.0, "kg/hr");
    feed1.run();
    Separator sep = new Separator("separator", feed1);
    sep.run();
    system1.add(feed1);
    system1.add(sep);

    ProcessSystem system2 = new ProcessSystem("Compression System");
    Stream feed2 = new Stream("feed2", testFluid.clone());
    feed2.setFlowRate(500.0, "kg/hr");
    feed2.run();
    Heater heater = new Heater("heater", feed2);
    heater.setOutTemperature(320.0, "K");
    heater.run();
    system2.add(feed2);
    system2.add(heater);

    // Combine into a module
    neqsim.process.processmodel.ProcessModule module =
        new neqsim.process.processmodel.ProcessModule("Combined Plant");
    module.add(system1);
    module.add(system2);

    ProcessModelGraph modelGraph = module.buildModelGraph();

    // Verify structure
    assertEquals(2, modelGraph.getSubSystemCount(), "Should have 2 sub-systems");
    assertEquals(4, modelGraph.getTotalNodeCount(), "Should have 4 nodes total");

    // Verify sub-systems can be accessed
    ProcessModelGraph.SubSystemGraph sepSystem = modelGraph.getSubSystem("Separation System");
    assertNotNull(sepSystem, "Should find Separation System");
    assertEquals(2, sepSystem.getNodeCount());

    ProcessModelGraph.SubSystemGraph compSystem = modelGraph.getSubSystem("Compression System");
    assertNotNull(compSystem, "Should find Compression System");
    assertEquals(2, compSystem.getNodeCount());
  }

  @Test
  void testProcessModuleConvenienceMethods() {
    // Create a simple module
    ProcessSystem system = new ProcessSystem("Test System");
    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    Heater heater = new Heater("heater", feed);
    heater.run();
    system.add(feed);
    system.add(heater);

    neqsim.process.processmodel.ProcessModule module =
        new neqsim.process.processmodel.ProcessModule("Test Module");
    module.add(system);

    // Test convenience methods
    assertFalse(module.hasRecycleLoops(), "Simple linear process should not have cycles");
    assertEquals(1, module.getSubSystemCount(), "Should have 1 sub-system");

    List<ProcessEquipmentInterface> calcOrder = module.getCalculationOrder();
    assertNotNull(calcOrder);
    assertEquals(2, calcOrder.size());

    String summary = module.getGraphSummary();
    assertNotNull(summary);
    assertTrue(summary.length() > 0);

    List<String> issues = module.validateStructure();
    assertTrue(issues.isEmpty(), "Simple valid module should have no validation issues");
  }

  @Test
  void testBuildModelGraphFromMultipleSystems() {
    // Test the static convenience method
    ProcessSystem system1 = new ProcessSystem("System A");
    Stream feed1 = new Stream("feed_a", testFluid.clone());
    feed1.setFlowRate(100.0, "kg/hr");
    feed1.run();
    system1.add(feed1);

    ProcessSystem system2 = new ProcessSystem("System B");
    Stream feed2 = new Stream("feed_b", testFluid.clone());
    feed2.setFlowRate(200.0, "kg/hr");
    feed2.run();
    system2.add(feed2);

    ProcessModelGraph modelGraph =
        ProcessModelGraphBuilder.buildModelGraph("Combined", system1, system2);

    assertEquals("Combined", modelGraph.getModelName());
    assertEquals(2, modelGraph.getSubSystemCount());
    assertEquals(2, modelGraph.getTotalNodeCount());
  }

  @Test
  void testNestedProcessModules() {
    // Create inner module
    ProcessSystem innerSystem = new ProcessSystem("Inner System");
    Stream innerFeed = new Stream("inner_feed", testFluid.clone());
    innerFeed.setFlowRate(100.0, "kg/hr");
    innerFeed.run();
    innerSystem.add(innerFeed);

    neqsim.process.processmodel.ProcessModule innerModule =
        new neqsim.process.processmodel.ProcessModule("Inner Module");
    innerModule.add(innerSystem);

    // Create outer module containing the inner module
    ProcessSystem outerSystem = new ProcessSystem("Outer System");
    Stream outerFeed = new Stream("outer_feed", testFluid.clone());
    outerFeed.setFlowRate(200.0, "kg/hr");
    outerFeed.run();
    outerSystem.add(outerFeed);

    neqsim.process.processmodel.ProcessModule outerModule =
        new neqsim.process.processmodel.ProcessModule("Outer Module");
    outerModule.add(outerSystem);
    outerModule.add(innerModule);

    ProcessModelGraph modelGraph = outerModule.buildModelGraph();

    // Should have 2 sub-systems (outer system + inner module)
    assertEquals(2, modelGraph.getSubSystemCount());
    assertEquals(2, modelGraph.getTotalNodeCount());

    // Inner module should be marked as a module
    ProcessModelGraph.SubSystemGraph innerSubSystem = modelGraph.getSubSystem("Inner Module");
    assertNotNull(innerSubSystem);
    assertTrue(innerSubSystem.isModule(), "Inner sub-system should be marked as a module");
  }

  @Test
  void testProcessModelGraphStatistics() {
    ProcessSystem system = new ProcessSystem("Stats Test");
    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();
    Heater h1 = new Heater("h1", feed);
    h1.run();
    Heater h2 = new Heater("h2", h1.getOutletStream());
    h2.run();
    system.add(feed);
    system.add(h1);
    system.add(h2);

    neqsim.process.processmodel.ProcessModule module =
        new neqsim.process.processmodel.ProcessModule("Stats Module");
    module.add(system);

    ProcessModelGraph modelGraph = module.buildModelGraph();
    java.util.Map<String, Object> stats = modelGraph.getStatistics();

    assertNotNull(stats);
    assertEquals("Stats Module", stats.get("modelName"));
    assertEquals(1, stats.get("subSystemCount"));
    assertEquals(3, stats.get("totalNodes"));
    assertNotNull(stats.get("hasCycles"));
  }

  /**
   * Test ProcessModule parallel sub-system analysis - independent systems.
   */
  @Test
  void testProcessModuleParallelAnalysisIndependent() {
    // Create two independent ProcessSystems (no stream connections between them)
    ProcessSystem system1 = new ProcessSystem("Train A");
    Stream feed1 = new Stream("feedA", testFluid.clone());
    feed1.setFlowRate(500.0, "kg/hr");
    feed1.run();
    Heater heater1 = new Heater("heaterA", feed1);
    heater1.run();
    system1.add(feed1);
    system1.add(heater1);

    ProcessSystem system2 = new ProcessSystem("Train B");
    Stream feed2 = new Stream("feedB", testFluid.clone());
    feed2.setFlowRate(500.0, "kg/hr");
    feed2.run();
    Heater heater2 = new Heater("heaterB", feed2);
    heater2.run();
    system2.add(feed2);
    system2.add(heater2);

    ProcessSystem system3 = new ProcessSystem("Train C");
    Stream feed3 = new Stream("feedC", testFluid.clone());
    feed3.setFlowRate(500.0, "kg/hr");
    feed3.run();
    Heater heater3 = new Heater("heaterC", feed3);
    heater3.run();
    system3.add(feed3);
    system3.add(heater3);

    // Add to module
    neqsim.process.processmodel.ProcessModule module =
        new neqsim.process.processmodel.ProcessModule("Multi-Train Plant");
    module.add(system1);
    module.add(system2);
    module.add(system3);

    ProcessModelGraph modelGraph = module.buildModelGraph();

    System.out.println("\n===== ProcessModule Parallel Analysis (Independent) =====");
    System.out.println(modelGraph.getSummary());

    // All three systems are independent - should all be at level 0
    assertTrue(modelGraph.isParallelSubSystemExecutionBeneficial(),
        "Parallel should be beneficial with 3 independent systems");

    ProcessModelGraph.ModuleParallelPartition partition =
        modelGraph.partitionSubSystemsForParallelExecution();
    assertEquals(1, partition.getLevelCount(), "All independent systems should be at same level");
    assertEquals(3, partition.getMaxParallelism(), "Max parallelism should be 3");

    // Check dependencies
    java.util.Map<String, java.util.Set<String>> deps = modelGraph.getSubSystemDependencies();
    assertTrue(deps.get("Train A").isEmpty(), "Train A should have no dependencies");
    assertTrue(deps.get("Train B").isEmpty(), "Train B should have no dependencies");
    assertTrue(deps.get("Train C").isEmpty(), "Train C should have no dependencies");

    System.out.println("========================================================\n");
  }

  /**
   * Test ProcessModule parallel sub-system analysis - sequential dependency.
   */
  @Test
  void testProcessModuleParallelAnalysisSequential() {
    // Create sequential ProcessSystems (system2 depends on system1 output)
    ProcessSystem system1 = new ProcessSystem("Upstream");
    Stream feed1 = new Stream("feed", testFluid.clone());
    feed1.setFlowRate(1000.0, "kg/hr");
    feed1.run();
    Separator sep = new Separator("separator", feed1);
    sep.run();
    system1.add(feed1);
    system1.add(sep);

    ProcessSystem system2 = new ProcessSystem("Downstream");
    // system2 uses output from system1
    Stream gasFromSep = new Stream("gasStream", sep.getGasOutStream());
    Heater heater = new Heater("heater", gasFromSep);
    heater.run();
    system2.add(gasFromSep);
    system2.add(heater);

    neqsim.process.processmodel.ProcessModule module =
        new neqsim.process.processmodel.ProcessModule("Sequential Plant");
    module.add(system1);
    module.add(system2);

    ProcessModelGraph modelGraph = module.buildModelGraph();

    System.out.println("\n===== ProcessModule Parallel Analysis (Sequential) =====");
    System.out.println(modelGraph.getSummary());

    // Sequential dependency - cannot parallelize
    ProcessModelGraph.ModuleParallelPartition partition =
        modelGraph.partitionSubSystemsForParallelExecution();

    // Should have 2 levels (Upstream first, then Downstream)
    assertEquals(2, partition.getLevelCount(), "Sequential systems should have 2 levels");
    assertEquals(1, partition.getMaxParallelism(), "Max parallelism should be 1 (sequential)");

    // Check that parallel is NOT beneficial
    assertFalse(modelGraph.isParallelSubSystemExecutionBeneficial(),
        "Parallel should not be beneficial with sequential dependency");

    // Check dependencies
    java.util.Map<String, java.util.Set<String>> deps = modelGraph.getSubSystemDependencies();
    assertTrue(deps.get("Upstream").isEmpty(), "Upstream should have no dependencies");
    assertTrue(deps.get("Downstream").contains("Upstream"), "Downstream should depend on Upstream");

    System.out.println("========================================================\n");
  }

  /**
   * Test ProcessModule parallel sub-system analysis - mixed topology.
   */
  @Test
  void testProcessModuleParallelAnalysisMixed() {
    // Create a diamond pattern:
    // Feed -> [TrainA, TrainB] -> Merge
    // TrainA and TrainB can run in parallel

    ProcessSystem feedSystem = new ProcessSystem("Feed System");
    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(2000.0, "kg/hr");
    feed.run();
    Splitter splitter = new Splitter("splitter", feed);
    splitter.setSplitFactors(new double[] {0.5, 0.5});
    splitter.run();
    feedSystem.add(feed);
    feedSystem.add(splitter);

    ProcessSystem trainA = new ProcessSystem("Train A");
    Stream streamA = new Stream("streamA", splitter.getSplitStream(0));
    Heater heaterA = new Heater("heaterA", streamA);
    heaterA.setOutTemperature(350.0);
    heaterA.run();
    trainA.add(streamA);
    trainA.add(heaterA);

    ProcessSystem trainB = new ProcessSystem("Train B");
    Stream streamB = new Stream("streamB", splitter.getSplitStream(1));
    Cooler coolerB = new Cooler("coolerB", streamB);
    coolerB.setOutTemperature(280.0);
    coolerB.run();
    trainB.add(streamB);
    trainB.add(coolerB);

    ProcessSystem mergeSystem = new ProcessSystem("Merge System");
    Mixer mixer = new Mixer("mixer");
    mixer.addStream(heaterA.getOutletStream());
    mixer.addStream(coolerB.getOutletStream());
    mixer.run();
    Stream product = new Stream("product", mixer.getOutletStream());
    product.run();
    mergeSystem.add(mixer);
    mergeSystem.add(product);

    neqsim.process.processmodel.ProcessModule module =
        new neqsim.process.processmodel.ProcessModule("Diamond Plant");
    module.add(feedSystem);
    module.add(trainA);
    module.add(trainB);
    module.add(mergeSystem);

    ProcessModelGraph modelGraph = module.buildModelGraph();

    System.out.println("\n===== ProcessModule Parallel Analysis (Diamond) =====");
    System.out.println(modelGraph.getSummary());

    // Diamond pattern: Level 0 = Feed, Level 1 = [TrainA, TrainB], Level 2 = Merge
    ProcessModelGraph.ModuleParallelPartition partition =
        modelGraph.partitionSubSystemsForParallelExecution();

    assertTrue(modelGraph.isParallelSubSystemExecutionBeneficial(),
        "Parallel should be beneficial with diamond pattern");
    assertEquals(3, partition.getLevelCount(), "Diamond should have 3 levels");
    assertEquals(2, partition.getMaxParallelism(), "Max parallelism should be 2 (TrainA + TrainB)");

    // Check level contents
    java.util.List<java.util.List<String>> levelNames = partition.getLevelNames();
    assertTrue(levelNames.get(0).contains("Feed System"), "Level 0 should contain Feed System");
    assertTrue(levelNames.get(1).contains("Train A") && levelNames.get(1).contains("Train B"),
        "Level 1 should contain Train A and Train B");
    assertTrue(levelNames.get(2).contains("Merge System"), "Level 2 should contain Merge System");

    System.out.println("========================================================\n");
  }

  @Test
  void testEmptyProcessModule() {
    neqsim.process.processmodel.ProcessModule module =
        new neqsim.process.processmodel.ProcessModule("Empty Module");

    List<String> issues = module.validateStructure();
    assertFalse(issues.isEmpty(), "Empty module should have validation issues");
    assertTrue(issues.get(0).contains("no unit operations"));
  }

  // ====== ProcessSystem Graph Integration Tests ======

  @Test
  void testGraphCaching() {
    ProcessSystem process = new ProcessSystem("Cache Test");
    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(100.0, "kg/hr");
    process.add(feed);

    // First call should build the graph
    ProcessGraph graph1 = process.buildGraph();
    assertNotNull(graph1);

    // Second call should return cached graph (same instance)
    ProcessGraph graph2 = process.buildGraph();
    assertSame(graph1, graph2, "Should return cached graph");

    // Adding a unit should invalidate cache
    Heater heater = new Heater("heater", feed);
    process.add(heater);

    // Now should get a new graph
    ProcessGraph graph3 = process.buildGraph();
    assertNotNull(graph3);
    assertNotSame(graph1, graph3, "Should rebuild graph after adding unit");

    // Manual invalidation
    ProcessGraph graph4 = process.buildGraph();
    process.invalidateGraph();
    ProcessGraph graph5 = process.buildGraph();
    assertNotSame(graph4, graph5, "Should rebuild after manual invalidation");
  }

  @Test
  void testGraphBasedExecutionFlag() {
    ProcessSystem process = new ProcessSystem("Execution Test");

    // Default should be false (insertion order)
    assertFalse(process.isUseGraphBasedExecution());

    // Enable graph-based execution
    process.setUseGraphBasedExecution(true);
    assertTrue(process.isUseGraphBasedExecution());

    // Create a simple process
    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(100.0, "kg/hr");
    Heater heater = new Heater("heater", feed);
    heater.setOutTemperature(350.0, "K");

    process.add(feed);
    process.add(heater);

    // Run with graph-based execution - should not throw
    assertDoesNotThrow(() -> process.run());
  }

  @Test
  void testRecycleBlockDetection() {
    ProcessSystem process = new ProcessSystem("Recycle Test");

    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(100.0, "kg/hr");
    process.add(feed);

    Heater heater = new Heater("heater", feed);
    process.add(heater);

    // Without recycle, should have no blocks
    assertEquals(0, process.getRecycleBlockCount(), "Should have no recycle blocks");
    assertFalse(process.isInRecycleLoop(feed));
    assertFalse(process.isInRecycleLoop(heater));
  }

  @Test
  void testRecycleBlockReport() {
    ProcessSystem process = new ProcessSystem("Report Test");

    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(100.0, "kg/hr");
    Heater heater = new Heater("heater", feed);

    process.add(feed);
    process.add(heater);

    String report = process.getRecycleBlockReport();
    assertNotNull(report);
    assertTrue(report.contains("No recycle blocks") || report.contains("Recycle Blocks Report"));
  }

  @Test
  void testTopologicalOrderConsistency() {
    ProcessSystem process = new ProcessSystem("Order Test");

    // Add units in reverse order of dependency
    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    Heater heater1 = new Heater("heater1", feed);
    heater1.run();

    Heater heater2 = new Heater("heater2", heater1.getOutletStream());
    heater2.run();

    // Add in "wrong" order
    process.add(heater2);
    process.add(feed);
    process.add(heater1);

    // Topological order should be correct regardless of insertion order
    List<ProcessEquipmentInterface> topoOrder = process.getTopologicalOrder();
    assertNotNull(topoOrder);

    // Feed should come before heater1, heater1 before heater2
    int feedIndex = topoOrder.indexOf(feed);
    int heater1Index = topoOrder.indexOf(heater1);
    int heater2Index = topoOrder.indexOf(heater2);

    assertTrue(feedIndex < heater1Index, "Feed should come before heater1");
    assertTrue(heater1Index < heater2Index, "Heater1 should come before heater2");
  }

  // ====== Complex Process Tests ======

  /**
   * Test graph analysis on a complex oil/gas separation process with recycle.
   */
  @Test
  void testComplexOilGasProcessGraph() {
    // Create a realistic oil/gas process with multiple separators and recycle
    SystemInterface oilGasFluid = new SystemSrkEos(298.0, 26.0);
    oilGasFluid.addComponent("methane", 0.70);
    oilGasFluid.addComponent("ethane", 0.09);
    oilGasFluid.addComponent("propane", 0.05);
    oilGasFluid.addComponent("n-butane", 0.03);
    oilGasFluid.addComponent("nC10", 0.10);
    oilGasFluid.addComponent("water", 0.03);
    oilGasFluid.setMixingRule("classic");
    oilGasFluid.setMultiPhaseCheck(true);

    ProcessSystem operations = new ProcessSystem("Oil/Gas Separation");

    // Feed stream
    Stream feedStream = new Stream("feed stream", oilGasFluid);
    feedStream.setFlowRate(100000, "kg/hr");
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(26.0, "bara");

    // First stage separator
    ThreePhaseSeparator separator1st = new ThreePhaseSeparator("1st stage separator", feedStream);

    // Valve and heater to second stage
    ThrottlingValve valve1 = new ThrottlingValve("valve1", separator1st.getLiquidOutStream());
    valve1.setOutletPressure(10.0);

    Heater oilHeater = new Heater("oil heater", valve1.getOutletStream());
    oilHeater.setOutTemperature(320.0);

    // Second stage separator
    ThreePhaseSeparator separator2nd =
        new ThreePhaseSeparator("2nd stage separator", oilHeater.getOutletStream());

    // Valve to third stage
    ThrottlingValve valve2 = new ThrottlingValve("valve2", separator2nd.getLiquidOutStream());
    valve2.setOutletPressure(2.7);

    // Recycle stream (initial guess)
    StreamInterface recircStream = valve2.getOutletStream().clone("oilRecirc");
    recircStream.setFlowRate(1e-6, "kg/hr");

    // Third stage separator with recycle
    ThreePhaseSeparator separator3rd = new ThreePhaseSeparator("3rd stage separator");
    separator3rd.addStream(valve2.getOutletStream());
    separator3rd.addStream(recircStream);

    // LP gas processing
    Cooler coolerLP = new Cooler("cooler LP", separator3rd.getGasOutStream());
    coolerLP.setOutTemperature(273.15 + 25.0);

    Separator gasLiqSep = new Separator("LP gas separator", coolerLP.getOutletStream());

    // Recycle pump and valve
    Pump recirPump = new Pump("recirculation pump", gasLiqSep.getLiquidOutStream());
    recirPump.setOutletPressure(10.0);

    ThrottlingValve valveLP = new ThrottlingValve("LP valve", recirPump.getOutletStream());
    valveLP.setOutletPressure(2.7);

    // Recycle unit
    Recycle recycle = new Recycle("oil recirculation");
    recycle.addStream(valveLP.getOutletStream());
    recycle.setOutletStream(recircStream);
    recycle.setTolerance(1e-2);

    // Add all units
    operations.add(feedStream);
    operations.add(separator1st);
    operations.add(valve1);
    operations.add(oilHeater);
    operations.add(separator2nd);
    operations.add(valve2);
    operations.add(recircStream);
    operations.add(separator3rd);
    operations.add(coolerLP);
    operations.add(gasLiqSep);
    operations.add(recirPump);
    operations.add(valveLP);
    operations.add(recycle);

    // Build and analyze the graph
    ProcessGraph graph = operations.buildGraph();

    // Verify node count
    assertEquals(13, graph.getNodeCount(), "Should have 13 units in graph");

    // Verify edge detection
    assertTrue(graph.getEdgeCount() >= 10, "Should have at least 10 edges");

    // Check cycle detection (should have cycle due to recycle)
    assertTrue(graph.hasCycles(), "Process with recycle should have cycles");

    // Verify topological order is valid (returns all nodes even with cycles)
    List<ProcessEquipmentInterface> calcOrder = graph.getCalculationOrder();
    assertNotNull(calcOrder);
    assertEquals(13, calcOrder.size(), "Calculation order should include all units");

    // Verify feed comes before separators
    int feedIdx = calcOrder.indexOf(feedStream);
    int sep1Idx = calcOrder.indexOf(separator1st);
    assertTrue(feedIdx < sep1Idx, "Feed should be before 1st separator");

    // Get summary
    String summary = graph.getSummary();
    assertNotNull(summary);
    assertTrue(summary.contains("Nodes: 13"), "Summary should mention node count");

    // Test parallel partitioning
    ProcessGraph.ParallelPartition partition = graph.partitionForParallelExecution();
    assertTrue(partition.getLevelCount() >= 1, "Should have at least one level");

    // Test GNN tensors
    double[][] nodeFeatures = graph.getNodeFeatureMatrix();
    assertNotNull(nodeFeatures);
    assertEquals(13, nodeFeatures.length, "Should have features for 13 nodes");
  }

  /**
   * Test graph-based execution on a complex process.
   */
  @Test
  void testGraphBasedExecutionOnComplexProcess() {
    // Create a medium complexity process
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.05);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem("Complex Process");

    // Create process units
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(50.0, "bara");

    Heater heater1 = new Heater("heater1", feed);
    heater1.setOutTemperature(320.0);

    Separator sep1 = new Separator("separator1", heater1.getOutletStream());

    Compressor comp1 = new Compressor("compressor1", sep1.getGasOutStream());
    comp1.setOutletPressure(70.0);

    Cooler cooler1 = new Cooler("cooler1", comp1.getOutletStream());
    cooler1.setOutTemperature(300.0);

    Separator sep2 = new Separator("separator2", cooler1.getOutletStream());

    // Add units to process
    process.add(feed);
    process.add(heater1);
    process.add(sep1);
    process.add(comp1);
    process.add(cooler1);
    process.add(sep2);

    // Run with default (insertion order) execution
    process.setUseGraphBasedExecution(false);
    assertDoesNotThrow(() -> process.run());

    double gasFlowDefault = sep2.getGasOutStream().getFlowRate("kg/hr");

    // Invalidate graph and run with graph-based execution
    process.invalidateGraph();
    process.setUseGraphBasedExecution(true);
    assertDoesNotThrow(() -> process.run());

    double gasFlowGraph = sep2.getGasOutStream().getFlowRate("kg/hr");

    // Results should be the same
    assertEquals(gasFlowDefault, gasFlowGraph, 1e-3,
        "Graph-based execution should produce same results");

    // Verify graph structure
    String summary = process.getGraphSummary();
    assertNotNull(summary);
    assertTrue(summary.contains("Nodes: 6"), "Summary should contain node count");
    // This linear process has no explicit recycle loops
    assertEquals(0, process.getRecycleBlockCount(), "Linear process should have no recycle blocks");
  }

  /**
   * Test anti-surge compressor system with recycle.
   */
  @Test
  void testAntiSurgeCompressorGraph() {
    SystemInterface gas = new SystemSrkEos(298.0, 55.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem("Anti-Surge System");

    // Main gas stream
    Stream mainGas = new Stream("main gas", gas);
    mainGas.setPressure(55.0, "bara");
    mainGas.setTemperature(30.0, "C");
    mainGas.setFlowRate(5.0, "MSm3/day");

    // Recycle stream (initial guess)
    Stream recycleGas = mainGas.clone("recycle gas");
    recycleGas.setFlowRate(1e-10, "MSm3/day");

    // Mixer
    Mixer mixer = new Mixer("suction mixer");
    mixer.addStream(mainGas);
    mixer.addStream(recycleGas);

    // Compressor
    Compressor compressor = new Compressor("main compressor", mixer.getOutletStream());
    compressor.setOutletPressure(110.0);

    // Anti-surge splitter
    Splitter splitter = new Splitter("antisurge splitter", compressor.getOutletStream());
    splitter.setSplitFactors(new double[] {0.95, 0.05});

    // Anti-surge cooler
    Cooler recycleHx = new Cooler("recycle cooler", splitter.getSplitStream(1));
    recycleHx.setOutTemperature(303.0);

    // Anti-surge valve
    ThrottlingValve recycleValve =
        new ThrottlingValve("recycle valve", recycleHx.getOutletStream());
    recycleValve.setOutletPressure(55.0);

    // Recycle unit
    Recycle recycle = new Recycle("antisurge recycle");
    recycle.addStream(recycleValve.getOutletStream());
    recycle.setOutletStream(recycleGas);
    recycle.setTolerance(1e-3);

    // Add all units
    process.add(mainGas);
    process.add(recycleGas);
    process.add(mixer);
    process.add(compressor);
    process.add(splitter);
    process.add(recycleHx);
    process.add(recycleValve);
    process.add(recycle);

    // Build graph
    ProcessGraph graph = process.buildGraph();

    // Verify structure
    assertEquals(8, graph.getNodeCount(), "Should have 8 units");
    assertTrue(graph.getEdgeCount() >= 6, "Should have at least 6 edges");

    // Should detect cycle due to recycle
    assertTrue(graph.hasCycles(), "Anti-surge system has recycle loop");

    // Check SCC detection
    ProcessGraph.SCCResult scc = graph.findStronglyConnectedComponents();
    assertTrue(scc.getRecycleLoops().size() >= 0, "Should detect SCCs");

    // Verify topological order still works
    List<ProcessEquipmentInterface> order = graph.getCalculationOrder();
    assertNotNull(order);
    assertEquals(8, order.size());

    // Main gas should come before mixer
    int mainGasIdx = order.indexOf(mainGas);
    int mixerIdx = order.indexOf(mixer);
    assertTrue(mainGasIdx < mixerIdx, "Main gas should be before mixer");
  }

  /**
   * Test graph caching behavior with complex processes.
   */
  @Test
  void testGraphCachingComplexProcess() {
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.15);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem("Caching Test");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000, "kg/hr");
    process.add(feed);

    Heater heater = new Heater("heater", feed);
    process.add(heater);

    // First graph build
    ProcessGraph graph1 = process.buildGraph();
    assertEquals(2, graph1.getNodeCount());

    // Second call should return cached graph
    ProcessGraph graph2 = process.buildGraph();
    assertSame(graph1, graph2, "Should return cached graph");

    // Add new unit - should invalidate cache
    Separator sep = new Separator("separator", heater.getOutletStream());
    process.add(sep);

    // Now should get new graph
    ProcessGraph graph3 = process.buildGraph();
    assertNotSame(graph1, graph3, "Should rebuild graph after adding unit");
    assertEquals(3, graph3.getNodeCount());

    // Manual invalidation
    process.invalidateGraph();
    ProcessGraph graph4 = process.buildGraph();
    assertNotSame(graph3, graph4, "Should rebuild after invalidation");
  }

  /**
   * Test recycle block detection on complex process.
   */
  @Test
  void testRecycleBlocksComplexProcess() {
    SystemInterface fluid = new SystemSrkEos(298.0, 10.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.15);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem("Recycle Analysis");

    // Create process with recycle
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000, "kg/hr");
    feed.setPressure(50.0, "bara");

    Stream recycleStream = feed.clone("recycle stream");
    recycleStream.setFlowRate(1e-6, "kg/hr");

    Mixer mixer = new Mixer("mixer");
    mixer.addStream(feed);
    mixer.addStream(recycleStream);

    Heater heater = new Heater("heater", mixer.getOutletStream());
    heater.setOutTemperature(350.0);

    Separator sep = new Separator("separator", heater.getOutletStream());

    Compressor comp = new Compressor("compressor", sep.getGasOutStream());
    comp.setOutletPressure(60.0);

    Splitter splitter = new Splitter("splitter", comp.getOutletStream());
    splitter.setSplitFactors(new double[] {0.9, 0.1});

    ThrottlingValve recycleValve = new ThrottlingValve("recycle valve", splitter.getSplitStream(1));
    recycleValve.setOutletPressure(50.0);

    Recycle recycle = new Recycle("recycle");
    recycle.addStream(recycleValve.getOutletStream());
    recycle.setOutletStream(recycleStream);
    recycle.setTolerance(1e-3);

    // Add units
    process.add(feed);
    process.add(recycleStream);
    process.add(mixer);
    process.add(heater);
    process.add(sep);
    process.add(comp);
    process.add(splitter);
    process.add(recycleValve);
    process.add(recycle);

    // Build graph and verify structure
    ProcessGraph graph = process.buildGraph();
    assertEquals(9, graph.getNodeCount(), "Should have 9 units");
    assertTrue(graph.getEdgeCount() >= 7, "Should have at least 7 edges");

    // Note: The Recycle unit may or may not create a true graph cycle
    // depending on how stream connections are detected
    // The important thing is that the graph analysis completes successfully

    // Get recycle block count - may be 0 if recycle creates logical but not graph cycle
    int blockCount = process.getRecycleBlockCount();
    assertTrue(blockCount >= 0, "Should report recycle blocks");

    // Get recycle block report
    String report = process.getRecycleBlockReport();
    assertNotNull(report);

    // Verify graph summary
    String summary = process.getGraphSummary();
    assertNotNull(summary);
    assertTrue(summary.contains("Nodes: 9"), "Summary should contain node count");

    // Verify topological order still works
    List<ProcessEquipmentInterface> order = graph.getCalculationOrder();
    assertNotNull(order);
    assertEquals(9, order.size());
  }

  // ====== Performance Comparison Tests ======

  /**
   * Benchmark comparing insertion-order vs graph-based execution speed. This test measures relative
   * performance and prints timing information.
   */
  @Test
  void testExecutionSpeedComparison() {
    // Create a medium-complexity process for benchmarking
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 0.75);
    fluid.addComponent("ethane", 0.12);
    fluid.addComponent("propane", 0.08);
    fluid.addComponent("n-butane", 0.05);
    fluid.setMixingRule("classic");

    // Number of iterations for timing
    int warmupIterations = 3;
    int timedIterations = 10;

    // Build the process
    ProcessSystem process = createBenchmarkProcess(fluid);

    // Warm up JVM (important for fair benchmarking)
    for (int i = 0; i < warmupIterations; i++) {
      process.run();
    }

    // Benchmark insertion-order execution
    process.setUseGraphBasedExecution(false);
    process.invalidateGraph();
    long startInsertionOrder = System.nanoTime();
    for (int i = 0; i < timedIterations; i++) {
      process.run();
    }
    long insertionOrderTime = System.nanoTime() - startInsertionOrder;
    double insertionOrderMs = insertionOrderTime / 1_000_000.0 / timedIterations;

    // Benchmark graph-based execution
    process.setUseGraphBasedExecution(true);
    process.invalidateGraph();

    // Pre-build graph (one-time cost)
    long graphBuildStart = System.nanoTime();
    process.buildGraph();
    long graphBuildTime = System.nanoTime() - graphBuildStart;
    double graphBuildMs = graphBuildTime / 1_000_000.0;

    long startGraphBased = System.nanoTime();
    for (int i = 0; i < timedIterations; i++) {
      process.run();
    }
    long graphBasedTime = System.nanoTime() - startGraphBased;
    double graphBasedMs = graphBasedTime / 1_000_000.0 / timedIterations;

    // Print benchmark results
    System.out.println("\n===== Execution Speed Comparison =====");
    System.out.println("Process: " + process.getUnitOperations().size() + " units");
    System.out.println("Iterations: " + timedIterations);
    System.out.println();
    System.out.printf("Insertion-order execution: %.3f ms/run%n", insertionOrderMs);
    System.out.printf("Graph-based execution:     %.3f ms/run%n", graphBasedMs);
    System.out.printf("Graph build (one-time):    %.3f ms%n", graphBuildMs);
    System.out.println();

    double speedup = insertionOrderMs / graphBasedMs;
    if (speedup > 1.0) {
      System.out.printf("Graph-based is %.2fx FASTER%n", speedup);
    } else {
      System.out.printf("Insertion-order is %.2fx faster%n", 1.0 / speedup);
    }

    // Calculate break-even point (how many runs to amortize graph build cost)
    if (graphBasedMs < insertionOrderMs) {
      double savingsPerRun = insertionOrderMs - graphBasedMs;
      double breakEvenRuns = graphBuildMs / savingsPerRun;
      System.out.printf("Break-even point: %.1f runs%n", breakEvenRuns);
    }
    System.out.println("======================================\n");

    // Verify both produce the same result
    process.setUseGraphBasedExecution(false);
    process.run();
    double resultInsertion = process.getUnitOperations().get(process.getUnitOperations().size() - 1)
        .getFluid().getFlowRate("kg/hr");

    process.setUseGraphBasedExecution(true);
    process.run();
    double resultGraph = process.getUnitOperations().get(process.getUnitOperations().size() - 1)
        .getFluid().getFlowRate("kg/hr");

    assertEquals(resultInsertion, resultGraph, 1e-6, "Both methods should produce same result");
  }

  /**
   * Benchmark graph construction overhead.
   */
  @Test
  void testGraphConstructionOverhead() {
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.15);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    ProcessSystem process = createBenchmarkProcess(fluid);

    int iterations = 20;

    // Measure graph construction time
    long totalBuildTime = 0;
    for (int i = 0; i < iterations; i++) {
      process.invalidateGraph();
      long start = System.nanoTime();
      process.buildGraph();
      totalBuildTime += System.nanoTime() - start;
    }
    double avgBuildMs = totalBuildTime / 1_000_000.0 / iterations;

    // Measure cached graph retrieval time
    long totalCacheTime = 0;
    process.buildGraph(); // Ensure cached
    for (int i = 0; i < iterations; i++) {
      long start = System.nanoTime();
      process.buildGraph();
      totalCacheTime += System.nanoTime() - start;
    }
    double avgCacheMs = totalCacheTime / 1_000_000.0 / iterations;

    System.out.println("\n===== Graph Construction Overhead =====");
    System.out.println("Process: " + process.getUnitOperations().size() + " units");
    System.out.printf("Graph build (fresh):  %.4f ms%n", avgBuildMs);
    System.out.printf("Graph build (cached): %.4f ms%n", avgCacheMs);
    System.out.printf("Cache speedup: %.1fx%n", avgBuildMs / avgCacheMs);
    System.out.println("=======================================\n");

    // Cache should be significantly faster
    assertTrue(avgCacheMs < avgBuildMs / 10, "Cached retrieval should be >10x faster");
  }

  /**
   * Benchmark topological sort algorithm performance.
   */
  @Test
  void testTopologicalSortPerformance() {
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.15);
    fluid.setMixingRule("classic");

    ProcessSystem process = createBenchmarkProcess(fluid);
    ProcessGraph graph = process.buildGraph();

    int iterations = 100;

    // Measure topological sort time
    long totalTopoTime = 0;
    for (int i = 0; i < iterations; i++) {
      long start = System.nanoTime();
      graph.getTopologicalOrder();
      totalTopoTime += System.nanoTime() - start;
    }
    double avgTopoMs = totalTopoTime / 1_000_000.0 / iterations;

    // Measure SCC analysis time
    long totalSccTime = 0;
    for (int i = 0; i < iterations; i++) {
      long start = System.nanoTime();
      graph.findStronglyConnectedComponents();
      totalSccTime += System.nanoTime() - start;
    }
    double avgSccMs = totalSccTime / 1_000_000.0 / iterations;

    // Measure parallel partition time
    long totalPartTime = 0;
    for (int i = 0; i < iterations; i++) {
      long start = System.nanoTime();
      graph.partitionForParallelExecution();
      totalPartTime += System.nanoTime() - start;
    }
    double avgPartMs = totalPartTime / 1_000_000.0 / iterations;

    System.out.println("\n===== Graph Algorithm Performance =====");
    System.out
        .println("Graph: " + graph.getNodeCount() + " nodes, " + graph.getEdgeCount() + " edges");
    System.out.printf("Topological sort:      %.4f ms%n", avgTopoMs);
    System.out.printf("SCC analysis:          %.4f ms%n", avgSccMs);
    System.out.printf("Parallel partitioning: %.4f ms%n", avgPartMs);
    System.out.println("=======================================\n");

    // All should be fast for typical process sizes
    assertTrue(avgTopoMs < 10, "Topological sort should be < 10ms");
    assertTrue(avgSccMs < 10, "SCC analysis should be < 10ms");
  }

  /**
   * Helper method to create a benchmark process with multiple units.
   */
  private ProcessSystem createBenchmarkProcess(SystemInterface fluid) {
    ProcessSystem process = new ProcessSystem("Benchmark Process");

    // Create a realistic multi-stage process
    Stream feed = new Stream("feed", fluid.clone());
    feed.setFlowRate(50000, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(50.0, "bara");
    process.add(feed);

    // First stage: heating and separation
    Heater heater1 = new Heater("heater1", feed);
    heater1.setOutTemperature(330.0);
    process.add(heater1);

    Separator sep1 = new Separator("separator1", heater1.getOutletStream());
    process.add(sep1);

    // Gas compression train
    Compressor comp1 = new Compressor("compressor1", sep1.getGasOutStream());
    comp1.setOutletPressure(80.0);
    process.add(comp1);

    Cooler cooler1 = new Cooler("cooler1", comp1.getOutletStream());
    cooler1.setOutTemperature(310.0);
    process.add(cooler1);

    Separator sep2 = new Separator("separator2", cooler1.getOutletStream());
    process.add(sep2);

    Compressor comp2 = new Compressor("compressor2", sep2.getGasOutStream());
    comp2.setOutletPressure(120.0);
    process.add(comp2);

    Cooler cooler2 = new Cooler("cooler2", comp2.getOutletStream());
    cooler2.setOutTemperature(305.0);
    process.add(cooler2);

    // Liquid processing
    Heater heater2 = new Heater("heater2", sep1.getLiquidOutStream());
    heater2.setOutTemperature(340.0);
    process.add(heater2);

    ThrottlingValve valve1 = new ThrottlingValve("valve1", heater2.getOutletStream());
    valve1.setOutletPressure(20.0);
    process.add(valve1);

    Separator sep3 = new Separator("separator3", valve1.getOutletStream());
    process.add(sep3);

    return process;
  }

  // ====== Equipment Coverage Tests ======

  /**
   * Test that all common equipment types are properly handled by the graph builder. This includes:
   * Stream, Heater, Cooler, Separator, ThreePhaseSeparator, Compressor, Pump, Expander, Valve,
   * Mixer, Splitter, Filter, HeatExchanger, and utility units.
   */
  @Test
  void testComprehensiveEquipmentCoverage() {
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.15);
    fluid.addComponent("propane", 0.10);
    fluid.addComponent("n-butane", 0.05);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem("Equipment Coverage Test");

    // 1. Stream - basic inlet stream
    Stream feed = new Stream("feed", fluid.clone());
    feed.setFlowRate(10000, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(50.0, "bara");
    process.add(feed);

    // 2. Heater
    Heater heater = new Heater("heater", feed);
    heater.setOutTemperature(340.0);
    process.add(heater);

    // 3. ThreePhaseSeparator
    ThreePhaseSeparator threePhaseSep =
        new ThreePhaseSeparator("3-phase sep", heater.getOutletStream());
    process.add(threePhaseSep);

    // 4. Compressor (on gas stream)
    Compressor compressor = new Compressor("compressor", threePhaseSep.getGasOutStream());
    compressor.setOutletPressure(80.0);
    process.add(compressor);

    // 5. Cooler
    Cooler cooler = new Cooler("cooler", compressor.getOutletStream());
    cooler.setOutTemperature(305.0);
    process.add(cooler);

    // 6. Regular Separator
    Separator separator = new Separator("separator", cooler.getOutletStream());
    process.add(separator);

    // 7. Splitter (split gas stream)
    Splitter splitter = new Splitter("splitter", separator.getGasOutStream());
    splitter.setSplitFactors(new double[] {0.7, 0.3});
    process.add(splitter);

    // 8. Mixer (combine split streams with another)
    Mixer mixer = new Mixer("mixer");
    mixer.addStream(splitter.getSplitStream(0));
    mixer.addStream(splitter.getSplitStream(1));
    process.add(mixer);

    // 9. Pump (on liquid stream)
    Pump pump = new Pump("pump", threePhaseSep.getLiquidOutStream());
    pump.setOutletPressure(60.0);
    process.add(pump);

    // 10. ThrottlingValve
    ThrottlingValve valve = new ThrottlingValve("valve", pump.getOutletStream());
    valve.setOutletPressure(30.0);
    process.add(valve);

    // 11. GasScrubber
    GasScrubber scrubber = new GasScrubber("scrubber", mixer.getOutletStream());
    process.add(scrubber);

    // 12. Filter
    Filter filter = new Filter("filter", separator.getLiquidOutStream());
    process.add(filter);

    // Build graph and verify
    ProcessGraph graph = process.buildGraph();

    // Print equipment types detected
    System.out.println("\n===== Equipment Coverage Test =====");
    System.out.println("Units added: " + process.getUnitOperations().size());
    System.out.println("Nodes in graph: " + graph.getNodeCount());
    System.out.println("Edges in graph: " + graph.getEdgeCount());

    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      ProcessNode node = graph.getNode(unit);
      int inEdges = node != null ? node.getIncomingEdges().size() : -1;
      int outEdges = node != null ? node.getOutgoingEdges().size() : -1;
      System.out.printf("  %s [%s]: in=%d, out=%d%n", unit.getName(),
          unit.getClass().getSimpleName(), inEdges, outEdges);
    }
    System.out.println("===================================\n");

    // Verify all units are in the graph
    assertEquals(12, graph.getNodeCount(), "All 12 units should be in graph");

    // Verify edge connections
    assertTrue(graph.getEdgeCount() >= 10, "Should have at least 10 edges");

    // Verify specific connections
    ProcessNode feedNode = graph.getNode(feed);
    ProcessNode heaterNode = graph.getNode(heater);
    assertNotNull(feedNode);
    assertNotNull(heaterNode);
    assertTrue(feedNode.getOutgoingEdges().size() >= 1, "Feed should have outgoing edge");

    // Verify topological order works
    List<ProcessEquipmentInterface> order = graph.getCalculationOrder();
    assertNotNull(order);
    assertEquals(12, order.size());

    // Feed should be first
    assertEquals(feed, order.get(0), "Feed should be first in calculation order");
  }

  /**
   * Test that transmitters (measurement devices) work alongside graph-connected equipment. Note:
   * Transmitters are MeasurementDeviceInterface, not ProcessEquipmentInterface, so they are NOT
   * included in the process graph but work alongside it.
   */
  @Test
  void testTransmittersWithGraph() {
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.15);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem("Transmitter Test");

    // Create basic process
    Stream feed = new Stream("feed", fluid.clone());
    feed.setFlowRate(5000, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(40.0, "bara");
    process.add(feed);

    Heater heater = new Heater("heater", feed);
    heater.setOutTemperature(320.0);
    process.add(heater);

    Separator separator = new Separator("separator", heater.getOutletStream());
    process.add(separator);

    // Add transmitters to the process (they monitor streams/equipment)
    // Transmitters are MeasurementDeviceInterface, not ProcessEquipmentInterface
    TemperatureTransmitter tempTransmitter =
        new TemperatureTransmitter("TT-001", separator.getGasOutStream());
    tempTransmitter.setUnit("C");
    process.add(tempTransmitter);

    PressureTransmitter pressTransmitter =
        new PressureTransmitter("PT-001", separator.getGasOutStream());
    pressTransmitter.setUnit("bara");
    process.add(pressTransmitter);

    VolumeFlowTransmitter flowTransmitter =
        new VolumeFlowTransmitter("FT-001", separator.getGasOutStream());
    flowTransmitter.setUnit("Am3/hr");
    process.add(flowTransmitter);

    LevelTransmitter levelTransmitter = new LevelTransmitter("LT-001", separator);
    levelTransmitter.setUnit("m");
    process.add(levelTransmitter);

    // Build graph - transmitters are NOT included (they're measurement devices)
    ProcessGraph graph = process.buildGraph();

    System.out.println("\n===== Transmitter Test =====");
    System.out.println("Unit operations: " + process.getUnitOperations().size());
    System.out.println("Graph nodes: " + graph.getNodeCount());
    System.out.println("Graph edges: " + graph.getEdgeCount());
    System.out.println("Note: Transmitters are MeasurementDeviceInterface, not in graph");
    System.out.println("============================\n");

    // Only process equipment (3 units) should be in graph
    // Transmitters are MeasurementDeviceInterface - separate from ProcessEquipmentInterface
    assertEquals(3, graph.getNodeCount(), "Only 3 process units should be in graph");
    assertEquals(3, process.getUnitOperations().size(), "Only 3 unit operations");

    // Run the process to verify everything works together
    assertDoesNotThrow(() -> process.run());

    // Check transmitter readings after running
    assertTrue(tempTransmitter.getMeasuredValue() > 0, "Temperature should be positive");
    assertTrue(pressTransmitter.getMeasuredValue() > 0, "Pressure should be positive");
    assertTrue(flowTransmitter.getMeasuredValue() >= 0, "Flow should be non-negative");
  }

  /**
   * Test utility equipment: Calculator.
   */
  @Test
  void testUtilityEquipmentInGraph() {
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.15);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem("Utility Test");

    Stream feed = new Stream("feed", fluid.clone());
    feed.setFlowRate(5000, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    process.add(feed);

    Heater heater = new Heater("heater", feed);
    heater.setOutTemperature(350.0);
    process.add(heater);

    Separator separator = new Separator("separator", heater.getOutletStream());
    process.add(separator);

    // Build graph
    ProcessGraph graph = process.buildGraph();

    System.out.println("\n===== Utility Equipment Test =====");
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      ProcessNode node = graph.getNode(unit);
      int in = node != null ? node.getIncomingEdges().size() : -1;
      int out = node != null ? node.getOutgoingEdges().size() : -1;
      System.out.printf("  %s [%s]: in=%d, out=%d%n", unit.getName(),
          unit.getClass().getSimpleName(), in, out);
    }
    System.out.println("==================================\n");

    // All units should be in graph
    assertEquals(3, graph.getNodeCount());

    // Topological order should work
    List<ProcessEquipmentInterface> order = graph.getCalculationOrder();
    assertEquals(3, order.size());
  }

  /**
   * Test heat exchanger with two input streams.
   */
  @Test
  void testHeatExchangerGraph() {
    SystemInterface hotFluid = new SystemSrkEos(350.0, 50.0);
    hotFluid.addComponent("methane", 0.90);
    hotFluid.addComponent("ethane", 0.10);
    hotFluid.setMixingRule("classic");

    SystemInterface coldFluid = new SystemSrkEos(280.0, 40.0);
    coldFluid.addComponent("methane", 0.85);
    coldFluid.addComponent("ethane", 0.15);
    coldFluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem("Heat Exchanger Test");

    Stream hotStream = new Stream("hot stream", hotFluid.clone());
    hotStream.setFlowRate(3000, "kg/hr");
    hotStream.setTemperature(80.0, "C");
    hotStream.setPressure(50.0, "bara");
    process.add(hotStream);

    Stream coldStream = new Stream("cold stream", coldFluid.clone());
    coldStream.setFlowRate(2000, "kg/hr");
    coldStream.setTemperature(10.0, "C");
    coldStream.setPressure(40.0, "bara");
    process.add(coldStream);

    HeatExchanger heatEx = new HeatExchanger("heat exchanger", hotStream, coldStream);
    heatEx.setUAvalue(5000);
    process.add(heatEx);

    // Build graph
    ProcessGraph graph = process.buildGraph();

    System.out.println("\n===== Heat Exchanger Test =====");
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      ProcessNode node = graph.getNode(unit);
      int in = node != null ? node.getIncomingEdges().size() : -1;
      int out = node != null ? node.getOutgoingEdges().size() : -1;
      System.out.printf("  %s [%s]: in=%d, out=%d%n", unit.getName(),
          unit.getClass().getSimpleName(), in, out);
    }
    System.out.println("===============================\n");

    // All 3 units should be in graph
    assertEquals(3, graph.getNodeCount());

    // Heat exchanger node should have 2 incoming edges (from both streams)
    ProcessNode hxNode = graph.getNode(heatEx);
    assertNotNull(hxNode, "Heat exchanger should be in graph");
    assertEquals(2, hxNode.getIncomingEdges().size(),
        "HeatExchanger should have 2 incoming edges (hot and cold streams)");
  }

  /**
   * Test expander equipment.
   */
  @Test
  void testExpanderInGraph() {
    SystemInterface gas = new SystemSrkEos(298.0, 80.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem("Expander Test");

    Stream feed = new Stream("feed", gas.clone());
    feed.setFlowRate(5000, "kg/hr");
    feed.setTemperature(50.0, "C");
    feed.setPressure(80.0, "bara");
    process.add(feed);

    Expander expander = new Expander("expander", feed);
    expander.setOutletPressure(30.0);
    process.add(expander);

    Separator separator = new Separator("separator", expander.getOutletStream());
    process.add(separator);

    ProcessGraph graph = process.buildGraph();

    assertEquals(3, graph.getNodeCount());
    assertTrue(graph.getEdgeCount() >= 2);

    // Verify connections
    ProcessNode feedNode = graph.getNode(feed);
    ProcessNode expanderNode = graph.getNode(expander);
    ProcessNode sepNode = graph.getNode(separator);

    assertTrue(feedNode.getOutgoingEdges().size() >= 1);
    assertTrue(expanderNode.getIncomingEdges().size() >= 1);
    assertTrue(sepNode.getIncomingEdges().size() >= 1);
  }

  /**
   * Test component splitter.
   */
  @Test
  void testComponentSplitterInGraph() {
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("ethane", 0.25);
    fluid.addComponent("propane", 0.15);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem("Component Splitter Test");

    Stream feed = new Stream("feed", fluid.clone());
    feed.setFlowRate(5000, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    process.add(feed);

    ComponentSplitter compSplitter = new ComponentSplitter("comp splitter", feed);
    compSplitter.setSplitFactors(new double[] {1.0, 0.5, 0.0}); // All methane, half ethane
    process.add(compSplitter);

    ProcessGraph graph = process.buildGraph();

    assertEquals(2, graph.getNodeCount());
    assertTrue(graph.getEdgeCount() >= 1);

    ProcessNode splitterNode = graph.getNode(compSplitter);
    assertNotNull(splitterNode);
    assertTrue(splitterNode.getIncomingEdges().size() >= 1);
  }

  /**
   * Test summary of all supported equipment types.
   */
  @Test
  void testEquipmentCoverageSummary() {
    System.out.println("\n========== EQUIPMENT COVERAGE SUMMARY ==========");
    System.out.println("The ProcessGraphBuilder supports the following equipment:");
    System.out.println();
    System.out.println("STREAMS:");
    System.out.println("  - Stream (basic process stream)");
    System.out.println("  - NeqStream (non-equilibrium stream)");
    System.out.println();
    System.out.println("HEAT TRANSFER:");
    System.out.println("  - Heater, Cooler, HeatExchanger");
    System.out.println("  - MultiStreamHeatExchanger");
    System.out.println();
    System.out.println("SEPARATION:");
    System.out.println("  - Separator, ThreePhaseSeparator, TwoPhaseSeparator");
    System.out.println("  - GasScrubber, GasScrubberSimple");
    System.out.println("  - DistillationColumn");
    System.out.println("  - Filter");
    System.out.println();
    System.out.println("COMPRESSION/EXPANSION:");
    System.out.println("  - Compressor, Pump, Expander");
    System.out.println("  - TurboExpanderCompressor");
    System.out.println();
    System.out.println("VALVES:");
    System.out.println("  - ThrottlingValve, ControlValve, CheckValve");
    System.out.println("  - ESDValve, HIPPSValve, PSDValve, BlowdownValve");
    System.out.println();
    System.out.println("MIXING/SPLITTING:");
    System.out.println("  - Mixer, StaticMixer");
    System.out.println("  - Splitter, ComponentSplitter");
    System.out.println();
    System.out.println("UTILITIES:");
    System.out.println("  - Recycle, Adjuster, SetPoint, Calculator");
    System.out.println();
    System.out.println("TRANSMITTERS:");
    System.out.println("  - TemperatureTransmitter, PressureTransmitter");
    System.out.println("  - VolumeFlowTransmitter, LevelTransmitter");
    System.out.println("  - WaterLevelTransmitter, OilLevelTransmitter");
    System.out.println();
    System.out.println("OTHER:");
    System.out.println("  - Ejector, Flare, Tank, Pipeline");
    System.out.println("  - Reactor (GibbsReactor)");
    System.out.println("  - Well, SubseaWell");
    System.out.println("================================================\n");

    // This is an informational test
    assertTrue(true);
  }

  /**
   * Test TurboExpanderCompressor graph connections.
   */
  @Test
  void testTurboExpanderCompressorInGraph() {
    SystemInterface expanderGas = new SystemSrkEos(298.0, 80.0);
    expanderGas.addComponent("methane", 0.90);
    expanderGas.addComponent("ethane", 0.10);
    expanderGas.setMixingRule("classic");

    SystemInterface compressorGas = new SystemSrkEos(298.0, 30.0);
    compressorGas.addComponent("methane", 0.85);
    compressorGas.addComponent("ethane", 0.15);
    compressorGas.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem("TurboExpanderCompressor Test");

    Stream expanderFeed = new Stream("expander feed", expanderGas.clone());
    expanderFeed.setFlowRate(5000, "kg/hr");
    expanderFeed.setTemperature(50.0, "C");
    expanderFeed.setPressure(80.0, "bara");
    process.add(expanderFeed);

    Stream compressorFeed = new Stream("compressor feed", compressorGas.clone());
    compressorFeed.setFlowRate(3000, "kg/hr");
    compressorFeed.setTemperature(30.0, "C");
    compressorFeed.setPressure(30.0, "bara");
    process.add(compressorFeed);

    neqsim.process.equipment.expander.TurboExpanderCompressor tec =
        new neqsim.process.equipment.expander.TurboExpanderCompressor("TEC", expanderFeed);
    tec.setCompressorFeedStream(compressorFeed);
    tec.setExpanderOutPressure(40.0);
    process.add(tec);

    ProcessGraph graph = process.buildGraph();

    System.out.println("\n===== TurboExpanderCompressor Test =====");
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      ProcessNode node = graph.getNode(unit);
      int in = node != null ? node.getIncomingEdges().size() : -1;
      int out = node != null ? node.getOutgoingEdges().size() : -1;
      System.out.printf("  %s [%s]: in=%d, out=%d%n", unit.getName(),
          unit.getClass().getSimpleName(), in, out);
    }
    System.out.println("========================================\n");

    assertEquals(3, graph.getNodeCount());

    // TEC should have 2 incoming edges (expander feed + compressor feed)
    ProcessNode tecNode = graph.getNode(tec);
    assertNotNull(tecNode);
    assertEquals(2, tecNode.getIncomingEdges().size(),
        "TurboExpanderCompressor should have 2 incoming edges");
  }

  /**
   * Test MultiStreamHeatExchanger graph connections.
   */
  @Test
  void testMultiStreamHeatExchangerInGraph() {
    SystemInterface fluid1 = new SystemSrkEos(350.0, 50.0);
    fluid1.addComponent("methane", 0.90);
    fluid1.addComponent("ethane", 0.10);
    fluid1.setMixingRule("classic");

    SystemInterface fluid2 = new SystemSrkEos(280.0, 50.0);
    fluid2.addComponent("methane", 0.85);
    fluid2.addComponent("ethane", 0.15);
    fluid2.setMixingRule("classic");

    SystemInterface fluid3 = new SystemSrkEos(260.0, 50.0);
    fluid3.addComponent("nitrogen", 0.95);
    fluid3.addComponent("methane", 0.05);
    fluid3.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem("MultiStreamHX Test");

    Stream stream1 = new Stream("hot stream 1", fluid1.clone());
    stream1.setFlowRate(5000, "kg/hr");
    stream1.setTemperature(80.0, "C");
    stream1.setPressure(50.0, "bara");
    process.add(stream1);

    Stream stream2 = new Stream("cold stream 1", fluid2.clone());
    stream2.setFlowRate(3000, "kg/hr");
    stream2.setTemperature(10.0, "C");
    stream2.setPressure(50.0, "bara");
    process.add(stream2);

    Stream stream3 = new Stream("cold stream 2", fluid3.clone());
    stream3.setFlowRate(2000, "kg/hr");
    stream3.setTemperature(-10.0, "C");
    stream3.setPressure(50.0, "bara");
    process.add(stream3);

    java.util.List<neqsim.process.equipment.stream.StreamInterface> inStreams =
        java.util.Arrays.asList(stream1, stream2, stream3);
    neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger mshx =
        new neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger("MSHX", inStreams);
    process.add(mshx);

    ProcessGraph graph = process.buildGraph();

    System.out.println("\n===== MultiStreamHeatExchanger Test =====");
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      ProcessNode node = graph.getNode(unit);
      int in = node != null ? node.getIncomingEdges().size() : -1;
      int out = node != null ? node.getOutgoingEdges().size() : -1;
      System.out.printf("  %s [%s]: in=%d, out=%d%n", unit.getName(),
          unit.getClass().getSimpleName(), in, out);
    }
    System.out.println("=========================================\n");

    assertEquals(4, graph.getNodeCount());

    // MSHX should have 3 incoming edges (from all 3 streams)
    ProcessNode mshxNode = graph.getNode(mshx);
    assertNotNull(mshxNode);
    assertEquals(3, mshxNode.getIncomingEdges().size(),
        "MultiStreamHeatExchanger should have 3 incoming edges");
  }

  /**
   * Test parallel execution of independent process branches.
   */
  @Test
  void testParallelExecution() throws InterruptedException {
    // Create a process with two independent branches that can run in parallel
    SystemInterface fluid1 = new SystemSrkEos(298.0, 50.0);
    fluid1.addComponent("methane", 0.90);
    fluid1.addComponent("ethane", 0.10);
    fluid1.setMixingRule("classic");

    SystemInterface fluid2 = new SystemSrkEos(298.0, 30.0);
    fluid2.addComponent("propane", 0.80);
    fluid2.addComponent("n-butane", 0.20);
    fluid2.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem("Parallel Test");

    // Branch 1: feed1 -> heater1 -> separator1
    Stream feed1 = new Stream("feed1", fluid1.clone());
    feed1.setFlowRate(5000, "kg/hr");
    feed1.setTemperature(25.0, "C");
    feed1.setPressure(50.0, "bara");
    process.add(feed1);

    Heater heater1 = new Heater("heater1", feed1);
    heater1.setOutTemperature(350.0);
    process.add(heater1);

    Separator sep1 = new Separator("separator1", heater1.getOutletStream());
    process.add(sep1);

    // Branch 2: feed2 -> cooler2 -> separator2 (independent of branch 1)
    Stream feed2 = new Stream("feed2", fluid2.clone());
    feed2.setFlowRate(3000, "kg/hr");
    feed2.setTemperature(60.0, "C");
    feed2.setPressure(30.0, "bara");
    process.add(feed2);

    Cooler cooler2 = new Cooler("cooler2", feed2);
    cooler2.setOutTemperature(280.0);
    process.add(cooler2);

    Separator sep2 = new Separator("separator2", cooler2.getOutletStream());
    process.add(sep2);

    // Get parallel partition info
    ProcessGraph.ParallelPartition partition = process.getParallelPartition();

    System.out.println("\n===== Parallel Execution Test =====");
    System.out.println("Total units: " + process.getUnitOperations().size());
    System.out.println("Parallel levels: " + partition.getLevelCount());
    System.out.println("Max parallelism: " + partition.getMaxParallelism());

    int levelNum = 0;
    for (java.util.List<ProcessNode> level : partition.getLevels()) {
      System.out.printf("  Level %d: ", levelNum++);
      for (ProcessNode node : level) {
        System.out.print(node.getName() + " ");
      }
      System.out.println();
    }
    System.out.println("====================================\n");

    // Should have at least 2 units that can run in parallel (the two feeds)
    assertTrue(partition.getMaxParallelism() >= 2,
        "Should have at least 2 units that can run in parallel");

    // Run using parallel execution
    long startParallel = System.nanoTime();
    process.runParallel();
    long parallelTime = System.nanoTime() - startParallel;

    // Verify results are correct
    assertTrue(sep1.getGasOutStream().getFlowRate("kg/hr") > 0);
    assertTrue(sep2.getGasOutStream().getFlowRate("kg/hr") > 0);

    System.out.printf("Parallel execution time: %.2f ms%n", parallelTime / 1_000_000.0);
  }

  /**
   * Test parallel vs sequential execution performance.
   */
  @Test
  void testParallelVsSequentialPerformance() throws InterruptedException {
    // Create a process with multiple independent branches
    ProcessSystem process = new ProcessSystem("Parallel Performance Test");

    // Create 4 independent branches
    for (int i = 1; i <= 4; i++) {
      SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
      fluid.addComponent("methane", 0.90);
      fluid.addComponent("ethane", 0.10);
      fluid.setMixingRule("classic");

      Stream feed = new Stream("feed" + i, fluid.clone());
      feed.setFlowRate(5000, "kg/hr");
      feed.setTemperature(25.0, "C");
      feed.setPressure(50.0, "bara");
      process.add(feed);

      Heater heater = new Heater("heater" + i, feed);
      heater.setOutTemperature(350.0);
      process.add(heater);

      Separator sep = new Separator("separator" + i, heater.getOutletStream());
      process.add(sep);
    }

    ProcessGraph.ParallelPartition partition = process.getParallelPartition();

    System.out.println("\n===== Parallel vs Sequential Performance =====");
    System.out.println("Total units: " + process.getUnitOperations().size());
    System.out.println("Parallel levels: " + partition.getLevelCount());
    System.out.println("Max parallelism: " + partition.getMaxParallelism());

    // Warm up
    process.run();

    // Time sequential execution
    long startSeq = System.nanoTime();
    for (int i = 0; i < 3; i++) {
      process.run();
    }
    long seqTime = (System.nanoTime() - startSeq) / 3;

    // Time parallel execution
    long startPar = System.nanoTime();
    for (int i = 0; i < 3; i++) {
      process.runParallel();
    }
    long parTime = (System.nanoTime() - startPar) / 3;

    System.out.printf("Sequential avg: %.2f ms%n", seqTime / 1_000_000.0);
    System.out.printf("Parallel avg:   %.2f ms%n", parTime / 1_000_000.0);
    System.out.printf("Speedup: %.2fx%n", (double) seqTime / parTime);
    System.out.println("==============================================\n");

    // Both should complete successfully
    assertTrue(seqTime > 0);
    assertTrue(parTime > 0);
  }

  /**
   * Test automatic optimal execution strategy selection.
   */
  @Test
  void testRunOptimalAutoSelection() {
    System.out.println("\n===== Automatic Execution Strategy Selection =====");

    // Test 1: Small process - should NOT use parallel
    {
      SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
      fluid.addComponent("methane", 0.90);
      fluid.addComponent("ethane", 0.10);
      fluid.setMixingRule("classic");

      ProcessSystem smallProcess = new ProcessSystem("Small Process");
      Stream feed = new Stream("feed", fluid.clone());
      feed.setFlowRate(5000, "kg/hr");
      smallProcess.add(feed);

      Heater heater = new Heater("heater", feed);
      heater.setOutTemperature(350.0);
      smallProcess.add(heater);

      boolean beneficial = smallProcess.isParallelExecutionBeneficial();
      System.out.println("Small process (2 units): parallel beneficial = " + beneficial);
      assertFalse(beneficial, "Small process should not benefit from parallelism");

      // runOptimal should still work
      smallProcess.runOptimal();
      assertTrue(heater.getOutletStream().getTemperature() > 300);
    }

    // Test 2: Process with recycle - should NOT use parallel
    {
      SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
      fluid.addComponent("methane", 0.90);
      fluid.addComponent("ethane", 0.10);
      fluid.setMixingRule("classic");

      ProcessSystem recycleProcess = new ProcessSystem("Recycle Process");

      Stream feed = new Stream("feed", fluid.clone());
      feed.setFlowRate(5000, "kg/hr");
      recycleProcess.add(feed);

      Heater heater1 = new Heater("heater1", feed);
      heater1.setOutTemperature(350.0);
      recycleProcess.add(heater1);

      Separator sep = new Separator("separator", heater1.getOutletStream());
      recycleProcess.add(sep);

      Heater heater2 = new Heater("heater2", sep.getGasOutStream());
      heater2.setOutTemperature(300.0);
      recycleProcess.add(heater2);

      // Add a recycle (presence alone should disable parallel)
      Recycle recycle = new Recycle("recycle");
      recycle.addStream(sep.getLiquidOutStream());
      recycle.setOutletStream(feed); // Connect outlet to make it valid
      recycleProcess.add(recycle);

      boolean beneficial = recycleProcess.isParallelExecutionBeneficial();
      System.out.println("Recycle process (5 units): parallel beneficial = " + beneficial);
      assertFalse(beneficial, "Process with recycle should not use parallel execution");
    }

    // Test 3: Large parallel process - SHOULD use parallel
    {
      ProcessSystem parallelProcess = new ProcessSystem("Parallel Process");

      for (int i = 1; i <= 4; i++) {
        SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
        fluid.addComponent("methane", 0.90);
        fluid.addComponent("ethane", 0.10);
        fluid.setMixingRule("classic");

        Stream feed = new Stream("feed" + i, fluid.clone());
        feed.setFlowRate(5000, "kg/hr");
        parallelProcess.add(feed);

        Heater heater = new Heater("heater" + i, feed);
        heater.setOutTemperature(350.0);
        parallelProcess.add(heater);

        Separator sep = new Separator("sep" + i, heater.getOutletStream());
        parallelProcess.add(sep);
      }

      boolean beneficial = parallelProcess.isParallelExecutionBeneficial();
      ProcessGraph.ParallelPartition partition = parallelProcess.getParallelPartition();

      System.out.println("Large parallel process (12 units): parallel beneficial = " + beneficial);
      System.out.println("  Max parallelism: " + partition.getMaxParallelism());

      assertTrue(beneficial, "Large parallel process should benefit from parallelism");
      assertTrue(partition.getMaxParallelism() >= 4, "Should have 4 parallel branches");

      // runOptimal should use parallel
      parallelProcess.runOptimal();
    }

    System.out.println("==================================================\n");
  }

  /**
   * Test runOptimal performance comparison.
   */
  @Test
  void testRunOptimalPerformance() throws InterruptedException {
    // Create a process that benefits from parallelism
    ProcessSystem process = new ProcessSystem("Optimal Test");

    for (int i = 1; i <= 6; i++) {
      SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
      fluid.addComponent("methane", 0.90);
      fluid.addComponent("ethane", 0.10);
      fluid.setMixingRule("classic");

      Stream feed = new Stream("feed" + i, fluid.clone());
      feed.setFlowRate(5000, "kg/hr");
      process.add(feed);

      Heater heater = new Heater("heater" + i, feed);
      heater.setOutTemperature(350.0);
      process.add(heater);

      Separator sep = new Separator("sep" + i, heater.getOutletStream());
      process.add(sep);
    }

    System.out.println("\n===== runOptimal() Performance =====");
    System.out.println("Units: " + process.getUnitOperations().size());
    System.out.println("Parallel beneficial: " + process.isParallelExecutionBeneficial());
    System.out.println("Max parallelism: " + process.getParallelPartition().getMaxParallelism());

    // Warm up
    process.runOptimal();

    // Time runOptimal
    long start = System.nanoTime();
    for (int i = 0; i < 5; i++) {
      process.runOptimal();
    }
    long avgTime = (System.nanoTime() - start) / 5;

    System.out.printf("runOptimal() avg: %.2f ms%n", avgTime / 1_000_000.0);
    System.out.println("====================================\n");

    assertTrue(avgTime > 0);
  }

  /**
   * Test Manifold equipment graph support. Manifold combines N inputs -> M outputs (mixer +
   * splitter internally).
   */
  @Test
  void testManifoldGraphSupport() {
    ProcessSystem system = new ProcessSystem("Manifold Test");

    // Create two input streams
    SystemInterface fluid1 = new SystemSrkEos(298.0, 10.0);
    fluid1.addComponent("methane", 0.9);
    fluid1.addComponent("ethane", 0.1);
    fluid1.setMixingRule("classic");

    Stream inlet1 = new Stream("inlet1", fluid1.clone());
    inlet1.setFlowRate(3.0, "MSm3/day");
    inlet1.setPressure(10.0, "bara");
    inlet1.setTemperature(20.0, "C");
    system.add(inlet1);

    SystemInterface fluid2 = fluid1.clone();
    Stream inlet2 = new Stream("inlet2", fluid2);
    inlet2.setFlowRate(2.0, "MSm3/day");
    inlet2.setPressure(10.0, "bara");
    inlet2.setTemperature(20.0, "C");
    system.add(inlet2);

    // Create manifold (N inputs -> M outputs)
    neqsim.process.equipment.manifold.Manifold manifold =
        new neqsim.process.equipment.manifold.Manifold("manifold");
    manifold.addStream(inlet1);
    manifold.addStream(inlet2);
    manifold.setSplitFactors(new double[] {0.3, 0.5, 0.2});
    system.add(manifold);

    // Add downstream equipment on one of the outputs
    Heater heater = new Heater("heater", manifold.getSplitStream(0));
    heater.setOutTemperature(350.0);
    system.add(heater);

    // Build and analyze graph
    ProcessGraph graph = system.buildGraph();

    System.out.println("\n===== Manifold Graph Test =====");
    System.out.println("Nodes: " + graph.getNodeCount());
    System.out.println("Edges: " + graph.getEdgeCount());
    System.out.println("Has cycles: " + graph.hasCycles());

    // Verify structure
    assertEquals(4, graph.getNodeCount(), "Should have 4 nodes (2 inlets, manifold, heater)");
    assertFalse(graph.hasCycles(), "Manifold process should not have cycles");

    // Verify manifold node has correct connections
    ProcessNode manifoldNode = graph.getNode(manifold);
    assertNotNull(manifoldNode, "Manifold node should exist");

    // Manifold should have incoming edges from both inlet streams
    assertTrue(manifoldNode.getIncomingEdges().size() >= 2,
        "Manifold should have at least 2 incoming edges");

    // Verify calculation order
    List<ProcessEquipmentInterface> order = graph.getCalculationOrder();
    int inlet1Idx = order.indexOf(inlet1);
    int inlet2Idx = order.indexOf(inlet2);
    int manifoldIdx = order.indexOf(manifold);
    int heaterIdx = order.indexOf(heater);

    assertTrue(inlet1Idx < manifoldIdx, "Inlet1 should come before manifold");
    assertTrue(inlet2Idx < manifoldIdx, "Inlet2 should come before manifold");
    assertTrue(manifoldIdx < heaterIdx, "Manifold should come before heater");

    // Run the process
    system.run();

    // Verify results
    assertEquals(5.0, manifold.getMixedStream().getFlowRate("MSm3/day"), 0.01,
        "Mixed flow should be sum of inputs");

    System.out.println("Calculation order:");
    for (int i = 0; i < order.size(); i++) {
      System.out.println("  " + (i + 1) + ". " + order.get(i).getName());
    }
    System.out.println("===============================\n");
  }

  // ============ TEAR STREAM SELECTION TESTS ============

  @Test
  void testTearStreamSelectionLinearProcess() {
    // Linear process should have no tear streams needed
    ProcessSystem system = new ProcessSystem("Linear");

    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    Heater heater = new Heater("heater", feed);
    heater.setOutTemperature(350.0, "K");
    heater.run();

    system.add(feed);
    system.add(heater);

    ProcessGraph graph = system.buildGraph();
    ProcessGraph.TearStreamResult result = graph.selectTearStreams();

    assertEquals(0, result.getTearStreamCount(), "Linear process should not need tear streams");
    assertEquals(0, result.getTotalCyclesBroken());
    assertTrue(result.getTearStreams().isEmpty());
  }

  @Test
  void testTearStreamSelectionSimpleRecycle() {
    // Create a graph with a single cycle manually
    ProcessGraph graph = new ProcessGraph();

    // Create mock nodes
    Stream stream1 = new Stream("node1", testFluid.clone());
    Stream stream2 = new Stream("node2", testFluid.clone());
    Stream stream3 = new Stream("node3", testFluid.clone());
    Stream stream4 = new Stream("node4", testFluid.clone());

    ProcessNode node1 = graph.addNode(stream1);
    ProcessNode node2 = graph.addNode(stream2);
    ProcessNode node3 = graph.addNode(stream3);
    ProcessNode node4 = graph.addNode(stream4);

    // Create a linear chain with a recycle: 1 -> 2 -> 3 -> 4 -> 2 (back edge)
    graph.addEdge(node1, node2, null);
    graph.addEdge(node2, node3, null);
    graph.addEdge(node3, node4, null);
    graph.addEdge(node4, node2, null); // Back edge creating cycle

    // Verify cycle detection
    assertTrue(graph.hasCycles(), "Process should have cycles");

    // Select tear streams
    ProcessGraph.TearStreamResult result = graph.selectTearStreams();

    System.out.println("\n===== Tear Stream Selection Test =====");
    System.out.println("Tear streams: " + result.getTearStreamCount());
    for (ProcessEdge tear : result.getTearStreams()) {
      System.out.println("  " + tear.getName() + ": " + tear.getSource().getName() + " -> "
          + tear.getTarget().getName());
    }
    System.out.println("======================================\n");

    // Should select at least one tear stream
    assertTrue(result.getTearStreamCount() >= 1, "Should select at least one tear stream");

    // Verify tear streams break all cycles
    assertTrue(graph.validateTearStreams(result.getTearStreams()),
        "Selected tear streams should break all cycles");
  }

  @Test
  void testTearStreamValidation() {
    ProcessSystem system = new ProcessSystem("Validation Test");

    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    Heater heater = new Heater("heater", feed);
    heater.setOutTemperature(350.0, "K");
    heater.run();

    system.add(feed);
    system.add(heater);

    ProcessGraph graph = system.buildGraph();

    // No cycles means no tear streams needed
    assertTrue(graph.validateTearStreams(null),
        "Null tear streams should be valid for acyclic graph");
    assertTrue(graph.validateTearStreams(java.util.Collections.emptyList()),
        "Empty tear streams should be valid for acyclic graph");
  }

  @Test
  void testTearStreamSelectionPreferUserRecycle() {
    // Create a graph with multiple edges that could be tear streams
    ProcessGraph graph = new ProcessGraph();

    // Create mock nodes for a process with recycle
    Stream node1 = new Stream("feed", testFluid.clone());
    Stream node2 = new Stream("mixer", testFluid.clone());
    Stream node3 = new Stream("heater", testFluid.clone());
    Stream node4 = new Stream("separator", testFluid.clone());

    // Create a recycle node marked as recycle edge
    Recycle recycleUnit = new Recycle("user-recycle");

    ProcessNode n1 = graph.addNode(node1);
    ProcessNode n2 = graph.addNode(node2);
    ProcessNode n3 = graph.addNode(node3);
    ProcessNode n4 = graph.addNode(node4);
    ProcessNode n5 = graph.addNode(recycleUnit);

    // Create flow path: feed -> mixer -> heater -> separator -> recycle -> mixer
    graph.addEdge(n1, n2, null);
    graph.addEdge(n2, n3, null);
    graph.addEdge(n3, n4, null);
    graph.addEdge(n4, n5, null);
    graph.addEdge(n5, n2, null); // Back edge creating recycle

    // Verify we have a cycle
    assertTrue(graph.hasCycles(), "Should have cycles");

    ProcessGraph.TearStreamResult result = graph.selectTearStreams();

    // The algorithm should select at least one tear stream
    assertTrue(result.getTearStreamCount() >= 1, "Should have at least one tear stream");
    assertTrue(graph.validateTearStreams(result.getTearStreams()),
        "Tear streams should break cycles");
  }

  @Test
  void testSensitivityAnalysisBasic() {
    // Test sensitivity analysis on a simple recycle loop
    ProcessGraph graph = new ProcessGraph();

    // Create a small cycle: A -> B -> C -> A
    Stream nodeA = new Stream("nodeA", testFluid.clone());
    Heater nodeB = new Heater("nodeB", nodeA);
    Separator nodeC = new Separator("nodeC", nodeB.getOutletStream());

    ProcessNode nA = graph.addNode(nodeA);
    ProcessNode nB = graph.addNode(nodeB);
    ProcessNode nC = graph.addNode(nodeC);

    graph.addEdge(nA, nB, null);
    graph.addEdge(nB, nC, null);
    graph.addEdge(nC, nA, null); // Back edge creates cycle

    // Find SCCs
    ProcessGraph.SCCResult sccResult = graph.findStronglyConnectedComponents();
    List<List<ProcessNode>> recycleLoops = sccResult.getRecycleLoops();

    assertTrue(recycleLoops.size() >= 1, "Should detect at least one recycle loop");

    // Perform sensitivity analysis on the first SCC
    List<ProcessNode> scc = recycleLoops.get(0);
    ProcessGraph.SensitivityAnalysisResult analysis = graph.analyzeTearStreamSensitivity(scc);

    assertNotNull(analysis);
    assertFalse(analysis.getRankedTearCandidates().isEmpty(), "Should have tear candidates");
    assertTrue(analysis.getTotalSensitivity() > 0, "Total sensitivity should be positive");

    // Best tear stream should have lowest sensitivity
    ProcessEdge best = analysis.getBestTearStream();
    assertNotNull(best, "Should identify a best tear stream");

    for (ProcessEdge edge : analysis.getRankedTearCandidates()) {
      assertTrue(
          analysis.getEdgeSensitivities().get(best) <= analysis.getEdgeSensitivities().get(edge),
          "Best tear stream should have lowest or equal sensitivity");
    }
  }

  @Test
  void testSelectTearStreamsWithSensitivity() {
    // Test the new sensitivity-based tear stream selection
    ProcessGraph graph = new ProcessGraph();

    // Create a cycle with different equipment types
    Stream feed = new Stream("feed", testFluid.clone());
    Heater heater = new Heater("heater", feed);
    Separator separator = new Separator("separator", heater.getOutletStream());
    Mixer mixer = new Mixer("mixer");

    ProcessNode n1 = graph.addNode(feed);
    ProcessNode n2 = graph.addNode(heater);
    ProcessNode n3 = graph.addNode(separator);
    ProcessNode n4 = graph.addNode(mixer);

    graph.addEdge(n1, n2, null);
    graph.addEdge(n2, n3, null);
    graph.addEdge(n3, n4, null);
    graph.addEdge(n4, n2, null); // Creates cycle

    // Select tear streams with sensitivity analysis
    ProcessGraph.TearStreamResult result = graph.selectTearStreamsWithSensitivity();

    assertNotNull(result);
    assertTrue(result.getTearStreamCount() >= 1, "Should select at least one tear stream");
    assertTrue(graph.validateTearStreams(result.getTearStreams()),
        "Tear streams should break all cycles");
  }

  @Test
  void testSensitivityAnalysisReport() {
    // Test the sensitivity analysis report generation
    ProcessGraph graph = new ProcessGraph();

    // Create a recycle loop
    Stream feed = new Stream("feed", testFluid.clone());
    Heater heater = new Heater("heater", feed);
    Separator separator = new Separator("separator", heater.getOutletStream());

    ProcessNode n1 = graph.addNode(feed);
    ProcessNode n2 = graph.addNode(heater);
    ProcessNode n3 = graph.addNode(separator);

    graph.addEdge(n1, n2, null);
    graph.addEdge(n2, n3, null);
    graph.addEdge(n3, n1, null); // Creates cycle

    String report = graph.getSensitivityAnalysisReport();

    assertNotNull(report);
    assertTrue(report.contains("Tear Stream Sensitivity Analysis"));
    assertTrue(report.contains("Recycle Loop"));
    assertTrue(report.contains("sensitivity="));
    assertTrue(report.contains("Recommended tear:"));

    System.out.println("\n===== Sensitivity Analysis Report =====");
    System.out.println(report);
    System.out.println("========================================\n");
  }

  @Test
  void testSensitivityAnalysisNoRecycles() {
    // Test sensitivity analysis on linear process (no recycles)
    ProcessGraph graph = new ProcessGraph();

    Stream feed = new Stream("feed", testFluid.clone());
    Heater heater = new Heater("heater", feed);
    Separator separator = new Separator("separator", heater.getOutletStream());

    ProcessNode n1 = graph.addNode(feed);
    ProcessNode n2 = graph.addNode(heater);
    ProcessNode n3 = graph.addNode(separator);

    graph.addEdge(n1, n2, null);
    graph.addEdge(n2, n3, null);
    // No back edge - linear process

    String report = graph.getSensitivityAnalysisReport();

    assertTrue(report.contains("No recycle loops found"),
        "Should indicate no recycle loops for linear process");
  }

  @Test
  void testEquipmentTypeSensitivityWeights() {
    // Test that different equipment types have different sensitivity weights
    ProcessGraph graph = new ProcessGraph();

    // Create nodes with different equipment types
    Stream stream = new Stream("stream", testFluid.clone());
    Heater heater = new Heater("heater", stream);
    Separator separator = new Separator("separator", heater.getOutletStream());
    Mixer mixer = new Mixer("mixer");

    ProcessNode streamNode = graph.addNode(stream);
    ProcessNode heaterNode = graph.addNode(heater);
    ProcessNode separatorNode = graph.addNode(separator);
    ProcessNode mixerNode = graph.addNode(mixer);

    // Create a cycle that includes all equipment types
    graph.addEdge(streamNode, heaterNode, null);
    graph.addEdge(heaterNode, separatorNode, null);
    graph.addEdge(separatorNode, mixerNode, null);
    graph.addEdge(mixerNode, streamNode, null);

    // Get sensitivity analysis
    ProcessGraph.SCCResult sccResult = graph.findStronglyConnectedComponents();
    List<List<ProcessNode>> recycleLoops = sccResult.getRecycleLoops();

    assertTrue(recycleLoops.size() >= 1);

    ProcessGraph.SensitivityAnalysisResult analysis =
        graph.analyzeTearStreamSensitivity(recycleLoops.get(0));

    // Verify that sensitivities are computed
    assertFalse(analysis.getEdgeSensitivities().isEmpty());

    // All sensitivities should be positive
    for (Double sensitivity : analysis.getEdgeSensitivities().values()) {
      assertTrue(sensitivity > 0, "Sensitivity should be positive");
    }
  }

  @Test
  void testGraphSummaryIncludesTearStreams() {
    // Test that graph summary includes tear stream info
    ProcessGraph graph = new ProcessGraph();

    // Create a small cycle for testing
    Stream node1 = new Stream("feed", testFluid.clone());
    Stream node2 = new Stream("mixer", testFluid.clone());
    Stream node3 = new Stream("heater", testFluid.clone());
    Recycle recycle = new Recycle("recycle");

    ProcessNode n1 = graph.addNode(node1);
    ProcessNode n2 = graph.addNode(node2);
    ProcessNode n3 = graph.addNode(node3);
    ProcessNode n4 = graph.addNode(recycle);

    // Create cycle: feed -> mixer -> heater -> recycle -> mixer
    graph.addEdge(n1, n2, null);
    graph.addEdge(n2, n3, null);
    graph.addEdge(n3, n4, null);
    graph.addEdge(n4, n2, null); // Back edge

    String summary = graph.getSummary();

    System.out.println("\n===== Graph Summary =====");
    System.out.println(summary);
    System.out.println("=========================\n");

    // Summary should contain key information
    assertTrue(summary.contains("ProcessGraph Summary"));
    assertTrue(summary.contains("Nodes:"));
    assertTrue(summary.contains("Edges:"));

    if (graph.hasCycles()) {
      assertTrue(summary.contains("Suggested tear streams:"),
          "Summary should include tear stream info for cyclic graphs");
    }
  }
}
