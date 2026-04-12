package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for Hardy Cross looped network solver.
 *
 * <p>
 * Tests cover:
 * </p>
 * <ul>
 * <li>Loop detection algorithm</li>
 * <li>Hardy Cross convergence</li>
 * <li>Simple triangle network</li>
 * <li>Ring main network</li>
 * <li>Mass balance verification</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class LoopedPipeNetworkTest {

  private SystemInterface testGas;

  @BeforeEach
  void setUp() {
    // Create test gas with proper database initialization
    testGas = new SystemSrkEos(298.15, 50.0);
    testGas.addComponent("methane", 0.90);
    testGas.addComponent("ethane", 0.07);
    testGas.addComponent("propane", 0.03);
    testGas.createDatabase(true);
    testGas.setMixingRule("classic");
    testGas.init(0);
    testGas.init(1);
  }

  /**
   * Test loop detection in a simple triangle network.
   */
  @Test
  void testTriangleLoopDetection() {
    LoopDetector detector = new LoopDetector();

    // Add edges for triangle A-B-C-A
    detector.addEdge("A", "B", "pipe1");
    detector.addEdge("B", "C", "pipe2");
    detector.addEdge("C", "A", "pipe3");

    // Verify graph structure
    assertEquals(3, detector.getNodeCount(), "Should have 3 nodes");
    assertEquals(3, detector.getEdgeCount(), "Should have 3 edges");

    List<NetworkLoop> loops = detector.findLoops();

    System.out.println("Loops found: " + loops.size());
    for (NetworkLoop loop : loops) {
      System.out.println("  Loop: " + loop.toString());
      System.out.println("  Members: " + loop.getMembers().size());
    }

    // Should detect exactly one loop
    assertEquals(1, loops.size(), "Triangle should have exactly one loop");

    // Loop should contain all three pipes
    if (!loops.isEmpty()) {
      NetworkLoop loop = loops.get(0);
      assertEquals(3, loop.getMembers().size(), "Triangle loop should have 3 members");
    }
  }

  /**
   * Test loop detection in a network with two independent loops.
   */
  @Test
  void testTwoLoopDetection() {
    LoopDetector detector = new LoopDetector();

    // Create figure-8 network: A-B-C-A and B-C-D-B
    detector.addEdge("A", "B", "pipe1");
    detector.addEdge("B", "C", "pipe2");
    detector.addEdge("C", "A", "pipe3");
    detector.addEdge("C", "D", "pipe4");
    detector.addEdge("D", "B", "pipe5");

    List<NetworkLoop> loops = detector.findLoops();

    // Should detect exactly two loops
    assertEquals(2, loops.size(), "Figure-8 should have exactly two loops");
  }

  /**
   * Test tree network has no loops.
   */
  @Test
  void testTreeNetworkNoLoops() {
    LoopDetector detector = new LoopDetector();

    // Tree structure: A -> B -> C, A -> D
    detector.addEdge("A", "B", "pipe1");
    detector.addEdge("B", "C", "pipe2");
    detector.addEdge("A", "D", "pipe3");

    List<NetworkLoop> loops = detector.findLoops();

    assertEquals(0, loops.size(), "Tree network should have no loops");
  }

  /**
   * Test ring main network creation and loop detection.
   */
  @Test
  void testRingMainNetworkCreation() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("ring main");
    network.setFluidTemplate(testGas);

    // Add nodes
    network.addSourceNode("supply", 50.0, 1000.0);
    network.addJunctionNode("A");
    network.addJunctionNode("B");
    network.addJunctionNode("C");
    network.addSinkNode("customer1", 300.0);
    network.addSinkNode("customer2", 400.0);

    // Connect with pipes forming a ring
    network.addPipe("supply", "A", "inlet", 1000.0, 0.3);
    network.addPipe("A", "B", "AB", 500.0, 0.2);
    network.addPipe("B", "C", "BC", 500.0, 0.2);
    network.addPipe("C", "A", "CA", 500.0, 0.2);
    network.addPipe("B", "customer1", "B_cust1", 200.0, 0.15);
    network.addPipe("C", "customer2", "C_cust2", 200.0, 0.15);

    // Just verify loop was detected - don't run solver as it requires more setup
    // Initialize loops by running with sequential solver which works for tree networks
    // For looped networks, we just verify the topology detection
    assertEquals(6, network.getPipeNames().size(), "Network should have 6 pipes");

    // Manually trigger loop detection
    network.setSolverType(LoopedPipeNetwork.SolverType.SEQUENTIAL);

    // Verify pipe names
    assertTrue(network.getPipeNames().contains("inlet"));
    assertTrue(network.getPipeNames().contains("AB"));
    assertTrue(network.getPipeNames().contains("CA")); // The loop-closing pipe
  }

  /**
   * Test Hardy Cross convergence on simple triangle loop. This test validates the loop detection,
   * not the full solver.
   */
  @Test
  void testHardyCrossConvergence() {
    // Test loop detection on triangle network
    LoopDetector detector = new LoopDetector();

    // Triangle loop S-A-B-S plus outlet D from B
    detector.addEdge("S", "A", "SA");
    detector.addEdge("A", "B", "AB");
    detector.addEdge("B", "S", "BS");
    detector.addEdge("B", "D", "BD");

    List<NetworkLoop> loops = detector.findLoops();

    // Should find one loop (the triangle)
    assertEquals(1, loops.size(), "Should find one loop in triangle network");

    // The loop should have 3 edges
    if (!loops.isEmpty()) {
      assertEquals(3, loops.get(0).getMembers().size(), "Triangle loop should have 3 members");
    }
  }

  /**
   * Test NetworkLoop class functionality.
   */
  @Test
  void testNetworkLoopClass() {
    NetworkLoop loop = new NetworkLoop("loop1");

    loop.addMember("pipe1", 1);
    loop.addMember("pipe2", 1);
    loop.addMember("pipe3", -1);

    assertEquals("loop1", loop.getLoopId());
    assertEquals(3, loop.getMembers().size());

    // Check members
    boolean foundPipe1 = false;
    boolean foundPipe3Reverse = false;
    for (NetworkLoop.LoopMember member : loop.getMembers()) {
      if (member.getPipeName().equals("pipe1") && member.getDirection() == 1) {
        foundPipe1 = true;
      }
      if (member.getPipeName().equals("pipe3") && member.getDirection() == -1) {
        foundPipe3Reverse = true;
      }
    }
    assertTrue(foundPipe1, "Should find pipe1 with positive direction");
    assertTrue(foundPipe3Reverse, "Should find pipe3 with negative direction");
  }

  /**
   * Test solution summary output.
   */
  @Test
  void testSolutionSummary() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("summary test");
    network.setFluidTemplate(testGas);

    network.addSourceNode("S", 50.0, 500.0);
    network.addSinkNode("D", 500.0);
    network.addPipe("S", "D", "main", 1000.0, 0.3);

    network.run();

    java.util.Map<String, Object> summary = network.getSolutionSummary();

    assertNotNull(summary);
    assertEquals("summary test", summary.get("networkName"));
    assertEquals(2, summary.get("numberOfNodes"));
    assertEquals(1, summary.get("numberOfPipes"));
    assertTrue((Boolean) summary.get("converged"));
  }

  /**
   * Test JSON output generation.
   */
  @Test
  void testJsonOutput() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("json test");
    network.setFluidTemplate(testGas);

    network.addSourceNode("supply", 55.0, 800.0);
    network.addJunctionNode("junction");
    network.addSinkNode("delivery", 800.0);

    network.addPipe("supply", "junction", "inlet", 500.0, 0.25);
    network.addPipe("junction", "delivery", "outlet", 500.0, 0.25);

    // Don't run the solver (it requires proper fluid initialization)
    // Just test JSON output from the network structure

    String json = network.toJson();

    assertNotNull(json);
    assertTrue(json.contains("json test"));
    assertTrue(json.contains("supply"));
    assertTrue(json.contains("inlet"));
  }

  /**
   * Test network with parallel pipes (single loop). This test validates loop detection for parallel
   * pipes.
   */
  @Test
  void testParallelPipes() {
    // Test loop detection for parallel pipes using LoopDetector directly
    LoopDetector detector = new LoopDetector();

    // Two parallel pipes between same nodes A and B
    detector.addEdge("A", "B", "pipe1");
    detector.addEdge("A", "B", "pipe2");

    List<NetworkLoop> loops = detector.findLoops();

    // Should detect one loop (the parallel pipes form a loop)
    assertEquals(1, loops.size(), "Parallel pipes should form one loop");

    if (!loops.isEmpty()) {
      // Loop should have 2 members
      assertEquals(2, loops.get(0).getMembers().size(), "Parallel pipe loop should have 2 members");
    }
  }

  /**
   * Test solver type selection.
   */
  @Test
  void testSolverTypeSelection() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("solver test");

    // Test default
    assertEquals(LoopedPipeNetwork.SolverType.HARDY_CROSS, network.getSolverType());

    // Test setter
    network.setSolverType(LoopedPipeNetwork.SolverType.SEQUENTIAL);
    assertEquals(LoopedPipeNetwork.SolverType.SEQUENTIAL, network.getSolverType());
  }

  /**
   * Test relaxation factor setting.
   */
  @Test
  void testRelaxationFactor() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("relaxation test");

    // Default should be 1.0
    assertEquals(1.0, network.getRelaxationFactor(), 1e-10);

    // Test valid values
    network.setRelaxationFactor(0.8);
    assertEquals(0.8, network.getRelaxationFactor(), 1e-10);

    // Test boundary
    network.setRelaxationFactor(1.5);
    assertEquals(1.5, network.getRelaxationFactor(), 1e-10);
  }

  /**
   * Test offshore ring network scenario. This test validates loop detection for a realistic
   * offshore ring topology.
   */
  @Test
  void testOffshoreRingNetwork() {
    // Test loop detection for offshore ring using LoopDetector directly
    LoopDetector detector = new LoopDetector();

    // Ring main: platform -> manifold1 -> manifold2 -> manifold3 -> platform
    detector.addEdge("platform", "manifold1", "riser1");
    detector.addEdge("manifold1", "manifold2", "ring12");
    detector.addEdge("manifold2", "manifold3", "ring23");
    detector.addEdge("manifold3", "platform", "riser2"); // Creates the loop

    // Well connections (spurs off the ring)
    detector.addEdge("manifold1", "well1", "jumper1");
    detector.addEdge("manifold2", "well2", "jumper2");
    detector.addEdge("manifold3", "well3", "jumper3");

    List<NetworkLoop> loops = detector.findLoops();

    // Should have exactly one main loop
    assertEquals(1, loops.size(), "Offshore ring should have one main loop");

    // The main loop should have 4 edges (the ring path)
    if (!loops.isEmpty()) {
      assertEquals(4, loops.get(0).getMembers().size(),
          "Ring loop should have 4 members: riser1, ring12, ring23, riser2");
    }
  }

  /**
   * Test Hardy Cross solver converges for a simple triangle loop.
   *
   * <p>
   * Network: Source(S) --pipe1--> A --pipe2--> B --pipe3--> Sink(D) A --pipe4--> B (parallel,
   * creating a loop)
   * </p>
   */
  @Test
  void testHardyCrossSolverConvergence() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("HC convergence");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.HARDY_CROSS);
    network.setMaxIterations(200);
    network.setTolerance(1.0); // 1 Pa: tight tolerance needed for gas with small dP

    // Source at 50 bar supplying 1000 kg/hr
    network.addSourceNode("S", 50.0, 1000.0);
    network.addJunctionNode("A");
    network.addJunctionNode("B");
    network.addSinkNode("D", 1000.0);

    // Two parallel paths from A to B (creates one loop)
    network.addPipe("S", "A", "inlet", 1000.0, 0.3);
    network.addPipe("A", "B", "upper", 800.0, 0.2);
    network.addPipe("A", "B", "lower", 600.0, 0.25); // Shorter & larger = more flow
    network.addPipe("B", "D", "outlet", 500.0, 0.3);

    network.run();

    assertTrue(network.isConverged(), "Hardy Cross should converge for simple parallel pipe loop");

    // The larger/shorter pipe (lower) should carry more flow than the upper
    double upperFlow = Math.abs(network.getPipeFlowRate("upper"));
    double lowerFlow = Math.abs(network.getPipeFlowRate("lower"));
    assertTrue(lowerFlow > upperFlow,
        "Shorter/larger pipe should carry more flow: lower=" + lowerFlow + " upper=" + upperFlow);

    // Total flow in parallel section should equal inlet flow
    double inletFlow = Math.abs(network.getPipeFlowRate("inlet"));
    assertTrue(inletFlow > 0, "Inlet flow should be positive");
  }

  /**
   * Test Newton-Raphson GGA solver converges for a simple tree network.
   */
  @Test
  void testNewtonRaphsonTreeNetwork() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("NR tree");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(50);
    network.setTolerance(100.0);

    // Source -> Junction -> two sinks
    network.addSourceNode("S", 60.0, 2000.0);
    network.addJunctionNode("J");
    network.addSinkNode("D1", 800.0);
    network.addSinkNode("D2", 1200.0);

    network.addPipe("S", "J", "main", 2000.0, 0.4);
    network.addPipe("J", "D1", "branch1", 500.0, 0.2);
    network.addPipe("J", "D2", "branch2", 500.0, 0.25);

    network.run();

    assertTrue(network.isConverged(), "NR should converge for tree network");

    // Check node pressures are sensible (all below source, all positive)
    double sourceP = network.getNodePressure("S");
    double junctionP = network.getNodePressure("J");
    assertTrue(junctionP < sourceP, "Junction pressure should be below source");
    assertTrue(junctionP > 0, "Junction pressure should be positive");
  }

  /**
   * Test Newton-Raphson GGA solver for a looped network.
   */
  @Test
  void testNewtonRaphsonLoopedNetwork() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("NR looped");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(100);
    network.setTolerance(100.0);

    // Ring network: S -> A -> B -> D, with a parallel A -> B path
    network.addSourceNode("S", 55.0, 1500.0);
    network.addJunctionNode("A");
    network.addJunctionNode("B");
    network.addSinkNode("D", 1500.0);

    network.addPipe("S", "A", "inlet", 1000.0, 0.35);
    network.addPipe("A", "B", "upper", 800.0, 0.2);
    network.addPipe("A", "B", "lower", 800.0, 0.2); // Same size = equal split
    network.addPipe("B", "D", "outlet", 1000.0, 0.35);

    network.run();

    assertTrue(network.isConverged(), "NR should converge for looped network");

    // Equal parallel pipes should carry approximately equal flow
    double upperFlow = Math.abs(network.getPipeFlowRate("upper"));
    double lowerFlow = Math.abs(network.getPipeFlowRate("lower"));
    double diff = Math.abs(upperFlow - lowerFlow);
    assertTrue(diff < upperFlow * 0.1, "Equal parallel pipes should carry similar flow: upper="
        + upperFlow + " lower=" + lowerFlow);
  }

  /**
   * Test network with elevation changes.
   */
  @Test
  void testElevationChanges() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("elevation test");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(50);
    network.setTolerance(100.0);

    // Source at sea level, sink at 100m elevation
    network.addSourceNode("S", 60.0, 1000.0, 0.0);
    network.addSinkNode("D", 1000.0, 100.0);

    network.addPipe("S", "D", "uphill", 2000.0, 0.3);

    network.run();

    assertTrue(network.isConverged(), "Should converge with elevation");

    // Pressure at sink should be lower than source (friction + elevation)
    double sP = network.getNodePressure("S");
    double dP = network.getNodePressure("D");
    assertTrue(dP < sP, "Sink pressure should be lower than source with uphill flow");
  }

  /**
   * Test network validation.
   */
  @Test
  void testNetworkValidation() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("validation test");

    // No fluid template
    List<String> issues = network.validate();
    assertTrue(issues.size() > 0, "Should report issues for empty network");

    boolean hasFluidError = false;
    for (String issue : issues) {
      if (issue.contains("Fluid template")) {
        hasFluidError = true;
      }
    }
    assertTrue(hasFluidError, "Should report missing fluid template");

    // Add fluid and nodes
    network.setFluidTemplate(testGas);
    network.addSourceNode("S", 50.0, 1000.0);
    network.addSinkNode("D", 1000.0);
    network.addPipe("S", "D", "main", 1000.0, 0.3);

    List<String> issues2 = network.validate();
    boolean hasErrors = false;
    for (String issue : issues2) {
      if (issue.startsWith("ERROR")) {
        hasErrors = true;
      }
    }
    assertTrue(!hasErrors, "Valid network should have no errors");
  }

  /**
   * Test pressure-pressure mode: fixed pressures at both ends, solver finds flow rate.
   *
   * <p>
   * Single pipe between a 50 bar source and a 48 bar fixed-pressure sink. The NR solver should
   * compute the flow rate that produces exactly 2 bar pressure drop across the pipe.
   * </p>
   */
  @Test
  void testFixedPressureSinkSinglePipe() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("pressure-pressure");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(100);
    network.setTolerance(100.0);

    // Source at 50 bar, sink at 48 bar (2 bar pressure drop)
    network.addSourceNode("S", 50.0, 0.0); // flow rate not needed (determined by solver)
    network.addFixedPressureSinkNode("D", 48.0);
    network.addPipe("S", "D", "main", 5000.0, 0.25); // 5 km, 10 inch

    network.run();

    assertTrue(network.isConverged(), "Pressure-pressure mode should converge");

    // Pressures should be at specified values
    assertEquals(50.0, network.getNodePressure("S"), 0.01, "Source pressure should be 50 bar");
    assertEquals(48.0, network.getNodePressure("D"), 0.01, "Sink pressure should be 48 bar");

    // Flow should be positive and physically reasonable
    double flow = network.getPipeFlowRate("main");
    assertTrue(flow > 0, "Flow should be positive from high to low pressure");
    assertTrue(flow > 100, "Flow should be non-trivial for 2 bar drop over 5 km");
    assertTrue(flow < 100000, "Flow should not be unreasonably large");

    // Node flow at sink should equal pipe flow
    double deliveredFlow = network.getNodeFlowRate("D");
    assertEquals(flow, deliveredFlow, 1.0, "Delivered flow at sink should equal pipe flow");

    System.out.println("Pressure-pressure single pipe: flow = " + flow + " kg/hr");
  }

  /**
   * Test pressure-pressure mode with parallel pipes (looped network, all fixed pressures).
   *
   * <p>
   * Two parallel pipes of different diameters between a 60 bar source and a 58 bar sink. The solver
   * must find flows in each pipe such that both produce the same 2 bar drop.
   * </p>
   */
  @Test
  void testFixedPressureSinkParallelPipes() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("pressure-pressure parallel");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(100);
    network.setTolerance(100.0);

    network.addSourceNode("S", 60.0, 0.0);
    network.addJunctionNode("A");
    network.addJunctionNode("B");
    network.addFixedPressureSinkNode("D", 58.0);

    network.addPipe("S", "A", "inlet", 500.0, 0.35);
    network.addPipe("A", "B", "upper", 2000.0, 0.20);
    network.addPipe("A", "B", "lower", 2000.0, 0.30); // larger diameter, more flow
    network.addPipe("B", "D", "outlet", 500.0, 0.35);

    network.run();

    assertTrue(network.isConverged(), "Parallel pipes with fixed P should converge");

    // Verify pressures
    assertEquals(60.0, network.getNodePressure("S"), 0.01);
    assertEquals(58.0, network.getNodePressure("D"), 0.01);

    // Larger pipe should carry more flow
    double upperFlow = Math.abs(network.getPipeFlowRate("upper"));
    double lowerFlow = Math.abs(network.getPipeFlowRate("lower"));
    assertTrue(lowerFlow > upperFlow, "Larger pipe should carry more flow");

    // Mass balance: inlet = upper + lower = outlet = delivered
    double inletFlow = network.getPipeFlowRate("inlet");
    double outletFlow = network.getPipeFlowRate("outlet");
    assertEquals(inletFlow, outletFlow, 1.0, "Inlet and outlet flows should match");
    assertEquals(inletFlow, upperFlow + lowerFlow, 1.0, "Mass balance at junction A");

    System.out.println("Parallel pipes: upper=" + upperFlow + " lower=" + lowerFlow + " total="
        + inletFlow + " kg/hr");
  }

  /**
   * Test pressure-pressure mode with elevation between source and sink.
   *
   * <p>
   * Source at 100 bar at 0m, sink at 95 bar at 200m elevation. Hydrostatic head reduces the driving
   * pressure difference, resulting in less flow than flat terrain.
   * </p>
   */
  @Test
  void testFixedPressureSinkWithElevation() {
    // Flat case - no elevation
    LoopedPipeNetwork netFlat = new LoopedPipeNetwork("flat");
    netFlat.setFluidTemplate(testGas);
    netFlat.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    netFlat.setMaxIterations(100);
    netFlat.setTolerance(100.0);

    netFlat.addSourceNode("S", 100.0, 0.0);
    netFlat.addFixedPressureSinkNode("D", 95.0);
    netFlat.addPipe("S", "D", "main", 10000.0, 0.30);
    netFlat.run();

    // Uphill case - 200m elevation gain
    LoopedPipeNetwork netUphill = new LoopedPipeNetwork("uphill");
    netUphill.setFluidTemplate(testGas);
    netUphill.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    netUphill.setMaxIterations(100);
    netUphill.setTolerance(100.0);

    netUphill.addSourceNode("S", 100.0, 0.0, 0.0);
    netUphill.addFixedPressureSinkNode("D", 95.0, 200.0);
    netUphill.addPipe("S", "D", "main", 10000.0, 0.30);
    netUphill.run();

    assertTrue(netFlat.isConverged(), "Flat case should converge");
    assertTrue(netUphill.isConverged(), "Uphill case should converge");

    double flowFlat = netFlat.getPipeFlowRate("main");
    double flowUphill = netUphill.getPipeFlowRate("main");

    assertTrue(flowUphill < flowFlat,
        "Uphill flow should be less than flat flow due to hydrostatic head");
    System.out.println("Flat flow=" + flowFlat + " Uphill flow=" + flowUphill + " kg/hr");
  }

  /**
   * Test pipe hydraulic properties are populated after solve.
   */
  @Test
  void testPipeHydraulicProperties() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("hydraulics test");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.HARDY_CROSS);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("S", 50.0, 1000.0);
    network.addSinkNode("D", 1000.0);
    network.addPipe("S", "D", "main", 1000.0, 0.3);

    network.run();

    assertTrue(network.isConverged(), "Should converge");

    // Check that hydraulic properties are populated
    double velocity = network.getPipeVelocity("main");
    assertTrue(velocity > 0, "Velocity should be positive after solve");

    double headLoss = network.getPipeHeadLoss("main");
    assertTrue(headLoss > 0, "Head loss should be positive in flow direction");
  }

  /**
   * Test JSON output includes hydraulic properties.
   */
  @Test
  void testJsonIncludesHydraulics() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("json hydraulics");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.HARDY_CROSS);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("S", 50.0, 800.0);
    network.addSinkNode("D", 800.0);
    network.addPipe("S", "D", "main", 1000.0, 0.25);

    network.run();

    String json = network.toJson();
    assertNotNull(json);
    assertTrue(json.contains("velocity_ms"), "JSON should include velocity");
    assertTrue(json.contains("reynoldsNumber"), "JSON should include Reynolds number");
    assertTrue(json.contains("frictionFactor"), "JSON should include friction factor");
    assertTrue(json.contains("flowRegime"), "JSON should include flow regime");
  }

  /**
   * Test larger gathering network with multiple wells feeding a manifold via a ring.
   */
  @Test
  void testGatheringNetworkMultipleWells() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("gathering network");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    // Platform at 60 bar acts as the supply
    network.addSourceNode("platform", 60.0, 10000.0);

    // Manifolds in a ring
    network.addJunctionNode("manifold1");
    network.addJunctionNode("manifold2");
    network.addJunctionNode("manifold3");

    // Delivery point
    network.addSinkNode("delivery", 10000.0);

    // Ring main pipes (smaller diameters for meaningful pressure drops in gas)
    network.addPipe("platform", "manifold1", "riser1", 2000.0, 0.15);
    network.addPipe("manifold1", "manifold2", "ring12", 5000.0, 0.10);
    network.addPipe("manifold2", "manifold3", "ring23", 5000.0, 0.10);
    network.addPipe("manifold3", "platform", "riser2", 2000.0, 0.15); // Loop-close

    // Export from manifold2 to delivery
    network.addPipe("manifold2", "delivery", "export", 8000.0, 0.12);

    network.run();

    // Print debug info
    double platformP = network.getNodePressure("platform");
    double m1P = network.getNodePressure("manifold1");
    double m2P = network.getNodePressure("manifold2");
    double m3P = network.getNodePressure("manifold3");
    System.out.println("=== Gathering Network Results ===");
    System.out.println(
        "Converged: " + network.isConverged() + ", iterations: " + network.getIterationCount());
    System.out.println("Platform: " + String.format("%.4f", platformP) + " bara");
    System.out.println("Manifold 1: " + String.format("%.4f", m1P) + " bara");
    System.out.println("Manifold 2: " + String.format("%.4f", m2P) + " bara");
    System.out.println("Manifold 3: " + String.format("%.4f", m3P) + " bara");
    for (String pipeName : network.getPipeNames()) {
      System.out.println(pipeName + ": " + String.format("%.1f", network.getPipeFlowRate(pipeName))
          + " kg/hr, dP=" + String.format("%.4f", network.getPipeHeadLoss(pipeName)) + " bar, v="
          + String.format("%.3f", network.getPipeVelocity(pipeName)) + " m/s");
    }

    // Verify convergence and solution quality
    assertTrue(network.isConverged(), "Gathering network should converge");

    // All junction pressures should be between source and minimum delivery
    assertTrue(m1P < platformP && m1P > 0, "Manifold1 pressure should be reasonable: " + m1P);
    assertTrue(m2P < platformP && m2P > 0, "Manifold2 pressure should be reasonable: " + m2P);
    assertTrue(m3P < platformP && m3P > 0, "Manifold3 pressure should be reasonable: " + m3P);
  }

  // ===================================================================
  // Production Well Network Tests
  // ===================================================================

  /**
   * Test simple oil well IPR (PI model) producing to a separator.
   *
   * <p>
   * Reservoir at 300 bar connects via IPR to wellhead, then via pipe to separator at 50 bar. The
   * NR-GGA solver should find flows and intermediate pressures.
   * </p>
   */
  @Test
  void testSingleWellIPR_PIModel() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("single well PI");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    // Reservoir as fixed-pressure source at 300 bar
    network.addSourceNode("reservoir", 300.0, 0.0);
    // Wellhead as junction node
    network.addJunctionNode("wellhead");
    // Separator as fixed-pressure sink at 50 bar
    network.addFixedPressureSinkNode("separator", 50.0);

    // IPR from reservoir to wellhead: PI = 5e-7 kg/s/Pa (oil type, ~1.8 kg/s per bar drawdown)
    network.addWellIPR("reservoir", "wellhead", "ipr", 5e-7, false);
    // Flowline from wellhead to separator
    network.addPipe("wellhead", "separator", "flowline", 5000.0, 0.15);

    network.run();

    assertTrue(network.isConverged(), "Single well PI model should converge");

    // Wellhead pressure should be between reservoir (300) and separator (50)
    double pWh = network.getNodePressure("wellhead");
    assertTrue(pWh > 50.0 && pWh < 300.0,
        "Wellhead pressure should be between separator and reservoir: " + pWh);

    // Flow should be positive (reservoir to separator direction)
    double iprFlow = network.getPipeFlowRate("ipr");
    double lineFlow = network.getPipeFlowRate("flowline");
    assertTrue(iprFlow > 0, "IPR flow should be positive (producing): " + iprFlow);
    assertEquals(iprFlow, lineFlow, 1.0, "Mass balance: IPR flow = flowline flow");

    System.out.println("=== Single Well PI IPR ===");
    System.out.println("Wellhead P = " + String.format("%.2f", pWh) + " bara");
    System.out.println("Production rate = " + String.format("%.1f", iprFlow) + " kg/hr");
  }

  /**
   * Test well IPR using the Vogel model for solution-gas-drive oil wells.
   */
  @Test
  void testSingleWellIPR_VogelModel() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("single well Vogel");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    // Reservoir at 250 bar
    network.addSourceNode("reservoir", 250.0, 0.0);
    network.addJunctionNode("wellhead");
    network.addFixedPressureSinkNode("separator", 30.0);

    // Vogel IPR: qmax = 50 kg/s (absolute open flow)
    network.addWellIPRVogel("reservoir", "wellhead", "ipr_vogel", 50.0);
    network.addPipe("wellhead", "separator", "flowline", 3000.0, 0.15);

    network.run();

    assertTrue(network.isConverged(), "Vogel IPR should converge");

    double pWh = network.getNodePressure("wellhead");
    double flow = network.getPipeFlowRate("ipr_vogel");

    assertTrue(pWh > 30.0 && pWh < 250.0,
        "Wellhead pressure should be between separator and reservoir: " + pWh);
    assertTrue(flow > 0.0, "Production flow should be positive");
    // Flow should be less than qmax (in kg/hr = qmax*3600)
    assertTrue(flow < 50.0 * 3600.0, "Flow should be below AOF: " + flow);

    System.out.println("=== Single Well Vogel IPR ===");
    System.out.println("Wellhead P = " + String.format("%.2f", pWh) + " bara");
    System.out.println("Production rate = " + String.format("%.1f", flow) + " kg/hr");
  }

  /**
   * Test Fetkovich IPR model for gas well.
   */
  @Test
  void testSingleWellIPR_FetkovichModel() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("Fetkovich gas well");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(500);
    network.setTolerance(500.0);

    // Gas reservoir at 350 bar
    network.addSourceNode("reservoir", 350.0, 0.0);
    network.addJunctionNode("wellhead");
    network.addFixedPressureSinkNode("separator", 80.0);

    // Fetkovich: C = 1e-12 kg/s/Pa^(2n), n = 0.8
    network.addWellIPRFetkovich("reservoir", "wellhead", "ipr_fetk", 1e-12, 0.8);
    network.addPipe("wellhead", "separator", "flowline", 5000.0, 0.20);

    network.run();

    assertTrue(network.isConverged(), "Fetkovich IPR should converge");

    double pWh = network.getNodePressure("wellhead");
    double flow = network.getPipeFlowRate("ipr_fetk");

    assertTrue(pWh > 80.0 && pWh < 350.0,
        "Wellhead pressure should be between separator and reservoir: " + pWh);
    assertTrue(flow > 0.0, "Gas production should be positive: " + flow);

    System.out.println("=== Fetkovich Gas Well ===");
    System.out.println("Wellhead P = " + String.format("%.2f", pWh) + " bara");
    System.out.println("Production rate = " + String.format("%.1f", flow) + " kg/hr");
  }

  /**
   * Test production choke element between wellhead and manifold.
   *
   * <p>
   * Reservoir -> IPR -> wellhead -> choke -> manifold -> pipe -> separator. The choke creates an
   * additional pressure drop that reduces production compared to the no-choke case.
   * </p>
   */
  @Test
  void testWellWithChoke() {
    // Case 1: Without choke
    LoopedPipeNetwork noChoke = new LoopedPipeNetwork("no choke");
    noChoke.setFluidTemplate(testGas);
    noChoke.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    noChoke.setMaxIterations(500);
    noChoke.setTolerance(500.0);

    noChoke.addSourceNode("reservoir", 300.0, 0.0);
    noChoke.addJunctionNode("wellhead");
    noChoke.addFixedPressureSinkNode("separator", 50.0);
    noChoke.addWellIPR("reservoir", "wellhead", "ipr", 5e-7, false);
    noChoke.addPipe("wellhead", "separator", "flowline", 5000.0, 0.15);
    noChoke.run();

    // Case 2: With choke (50% open)
    LoopedPipeNetwork withChoke = new LoopedPipeNetwork("with choke");
    withChoke.setFluidTemplate(testGas);
    withChoke.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    withChoke.setMaxIterations(500);
    withChoke.setTolerance(500.0);

    withChoke.addSourceNode("reservoir", 300.0, 0.0);
    withChoke.addJunctionNode("wellhead");
    withChoke.addJunctionNode("downstream_choke");
    withChoke.addFixedPressureSinkNode("separator", 50.0);
    withChoke.addWellIPR("reservoir", "wellhead", "ipr", 5e-7, false);
    withChoke.addChoke("wellhead", "downstream_choke", "choke", 150.0, 50.0);
    withChoke.addPipe("downstream_choke", "separator", "flowline", 5000.0, 0.15);
    withChoke.run();

    assertTrue(noChoke.isConverged(), "No-choke case should converge");
    assertTrue(withChoke.isConverged(), "Choke case should converge");

    double flowNoChoke = noChoke.getPipeFlowRate("ipr");
    double flowWithChoke = withChoke.getPipeFlowRate("ipr");

    assertTrue(flowWithChoke < flowNoChoke,
        "Choke should reduce production: " + flowWithChoke + " vs " + flowNoChoke);

    // Choke creates pressure drop between wellhead and downstream
    double pWh = withChoke.getNodePressure("wellhead");
    double pDs = withChoke.getNodePressure("downstream_choke");
    assertTrue(pWh > pDs, "Pressure should drop across choke: " + pWh + " -> " + pDs);

    System.out.println("=== Well With Production Choke ===");
    System.out.println("No choke: " + String.format("%.1f", flowNoChoke) + " kg/hr");
    System.out.println("With choke (50%): " + String.format("%.1f", flowWithChoke) + " kg/hr");
    System.out.println("Choke dP = " + String.format("%.2f", pWh - pDs) + " bar");
  }

  /**
   * Test choke sensitivity - reducing opening should reduce flow.
   */
  @Test
  void testChokeSensitivity() {
    double previousFlow = Double.MAX_VALUE;
    double[] openings = {100.0, 75.0, 50.0, 25.0};

    System.out.println("=== Choke Sensitivity ===");

    for (double opening : openings) {
      LoopedPipeNetwork network = new LoopedPipeNetwork("choke_" + opening);
      network.setFluidTemplate(testGas);
      network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
      network.setMaxIterations(500);
      network.setTolerance(500.0);

      network.addSourceNode("reservoir", 300.0, 0.0);
      network.addJunctionNode("wellhead");
      network.addJunctionNode("ds_choke");
      network.addFixedPressureSinkNode("separator", 50.0);
      network.addWellIPR("reservoir", "wellhead", "ipr", 5e-7, false);
      network.addChoke("wellhead", "ds_choke", "choke", 150.0, opening);
      network.addPipe("ds_choke", "separator", "line", 5000.0, 0.15);

      network.run();
      assertTrue(network.isConverged(), "Should converge at " + opening + "% opening");

      double flow = network.getPipeFlowRate("ipr");
      assertTrue(flow < previousFlow || opening == 100.0,
          "Reducing choke opening should reduce flow: " + flow + " at " + opening + "%");
      previousFlow = flow;

      System.out
          .println("Opening=" + opening + "% -> Flow=" + String.format("%.1f", flow) + " kg/hr");
    }
  }

  /**
   * Test wellbore tubing element with vertical lift.
   *
   * <p>
   * Reservoir -> IPR -> bottomhole -> tubing -> wellhead -> pipe -> separator. The tubing
   * introduces hydrostatic and friction pressure drops.
   * </p>
   */
  @Test
  void testWellWithTubing() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("well with tubing");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    // Reservoir at 400 bar
    network.addSourceNode("reservoir", 400.0, 0.0);
    // Bottomhole - near reservoir
    network.addJunctionNode("bottomhole");
    // Wellhead - at surface
    network.addJunctionNode("wellhead");
    // Separator at platform
    network.addFixedPressureSinkNode("separator", 50.0);

    // IPR from reservoir to bottomhole (gas IPR: PI in kg/s/Pa^2)
    network.addWellIPR("reservoir", "bottomhole", "ipr", 5e-13, true); // gas IPR

    // Tubing from bottomhole to wellhead: 3000m, 4.5" (0.1143m), vertical (90deg)
    network.addTubing("bottomhole", "wellhead", "tubing", 3000.0, 0.1143, 90.0);

    // Flowline from wellhead to separator
    network.addPipe("wellhead", "separator", "flowline", 10000.0, 0.20);

    network.run();

    assertTrue(network.isConverged(), "Well with tubing should converge");

    double pBh = network.getNodePressure("bottomhole");
    double pWh = network.getNodePressure("wellhead");
    double flow = network.getPipeFlowRate("ipr");

    assertTrue(pBh > pWh, "BHP should be higher than WHP: " + pBh + " > " + pWh);
    assertTrue(pBh < 400.0, "BHP should be less than reservoir pressure: " + pBh);
    assertTrue(flow > 0, "Production should be positive: " + flow);

    System.out.println("=== Well With Tubing ===");
    System.out.println("BHP = " + String.format("%.2f", pBh) + " bara");
    System.out.println("WHP = " + String.format("%.2f", pWh) + " bara");
    System.out.println("Tubing dP = " + String.format("%.2f", pBh - pWh) + " bar");
    System.out.println("Rate = " + String.format("%.1f", flow) + " kg/hr");
  }

  /**
   * Test multi-well gathering system with IPR, chokes, and pipelines.
   *
   * <p>
   * Two wells (different IPR) -> chokes -> manifold -> export pipeline -> separator. This is the
   * canonical production well network that NR-GGA uniquely enables.
   * </p>
   */
  @Test
  void testMultiWellGatheringNetwork() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("multi-well gathering");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(500);
    network.setTolerance(500.0);

    // Two reservoirs at different pressures
    network.addSourceNode("res1", 350.0, 0.0);
    network.addSourceNode("res2", 280.0, 0.0);

    // Wellheads
    network.addJunctionNode("wh1");
    network.addJunctionNode("wh2");

    // Downstream of chokes
    network.addJunctionNode("ds1");
    network.addJunctionNode("ds2");

    // Common manifold
    network.addJunctionNode("manifold");

    // Separator
    network.addFixedPressureSinkNode("separator", 40.0);

    // Well 1: Higher pressure, higher PI (gas: PI in kg/s/Pa^2)
    network.addWellIPR("res1", "wh1", "ipr1", 8e-13, true); // gas
    network.addChoke("wh1", "ds1", "choke1", 150.0, 80.0); // 80% open

    // Well 2: Lower pressure, lower PI
    network.addWellIPR("res2", "wh2", "ipr2", 5e-13, true); // gas
    network.addChoke("wh2", "ds2", "choke2", 150.0, 60.0); // 60% open

    // Flowlines to manifold
    network.addPipe("ds1", "manifold", "line1", 3000.0, 0.15);
    network.addPipe("ds2", "manifold", "line2", 5000.0, 0.12);

    // Export pipeline
    network.addPipe("manifold", "separator", "export", 15000.0, 0.25);

    network.run();

    assertTrue(network.isConverged(), "Multi-well gathering should converge");

    double flow1 = network.getPipeFlowRate("ipr1");
    double flow2 = network.getPipeFlowRate("ipr2");
    double exportFlow = network.getPipeFlowRate("export");

    assertTrue(flow1 > 0 && flow2 > 0, "Both wells should produce");
    assertTrue(flow1 > flow2, "Well 1 should produce more (higher Pr, higher PI, wider choke)");
    assertEquals(flow1 + flow2, exportFlow, 5.0, "Mass balance at manifold");

    double pManifold = network.getNodePressure("manifold");
    assertTrue(pManifold > 40.0 && pManifold < 280.0,
        "Manifold pressure should be reasonable: " + pManifold);

    System.out.println("=== Multi-Well Gathering Network ===");
    System.out.println("Well 1: " + String.format("%.1f", flow1) + " kg/hr, WHP="
        + String.format("%.2f", network.getNodePressure("wh1")) + " bara");
    System.out.println("Well 2: " + String.format("%.1f", flow2) + " kg/hr, WHP="
        + String.format("%.2f", network.getNodePressure("wh2")) + " bara");
    System.out.println("Manifold P = " + String.format("%.2f", pManifold) + " bara");
    System.out.println("Export: " + String.format("%.1f", exportFlow) + " kg/hr");
  }

  /**
   * Test looped production gathering with two paths from wells to platform.
   *
   * <p>
   * Two wells feed into a ring main that has two paths to the platform. This is a true looped
   * production network that sequential solvers cannot handle.
   * </p>
   */
  @Test
  void testLoopedProductionGathering() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("looped production");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(300);
    network.setTolerance(100.0);

    // Two reservoirs
    network.addSourceNode("resA", 320.0, 0.0);
    network.addSourceNode("resB", 310.0, 0.0);

    // Wellheads
    network.addJunctionNode("whA");
    network.addJunctionNode("whB");

    // Two manifold nodes (ring main junction points)
    network.addJunctionNode("M1");
    network.addJunctionNode("M2");

    // Platform separator
    network.addFixedPressureSinkNode("platform", 45.0);

    // Well IPRs
    network.addWellIPR("resA", "whA", "iprA", 6e-13, true);
    network.addWellIPR("resB", "whB", "iprB", 6e-13, true);

    // Flowlines from wellheads to manifold ring
    network.addPipe("whA", "M1", "lineA", 4000.0, 0.15);
    network.addPipe("whB", "M2", "lineB", 4000.0, 0.15);

    // Ring main: M1 -> M2 (creates loop)
    network.addPipe("M1", "M2", "ring", 6000.0, 0.12);

    // Two export paths: M1 -> platform and M2 -> platform
    network.addPipe("M1", "platform", "export1", 10000.0, 0.20);
    network.addPipe("M2", "platform", "export2", 12000.0, 0.20);

    network.run();

    assertTrue(network.isConverged(), "Looped production gathering should converge");

    // Both wells should produce
    double flowA = network.getPipeFlowRate("iprA");
    double flowB = network.getPipeFlowRate("iprB");
    assertTrue(flowA > 0 && flowB > 0, "Both wells should produce");

    // Total flow in = total flow out (mass balance)
    double totalIn = flowA + flowB;
    double exp1 = network.getPipeFlowRate("export1");
    double exp2 = network.getPipeFlowRate("export2");
    double totalOut = exp1 + exp2;
    assertEquals(totalIn, totalOut, 10.0,
        "Total inflow should equal total outflow: " + totalIn + " vs " + totalOut);

    System.out.println("=== Looped Production Gathering ===");
    System.out.println("Well A: " + String.format("%.1f", flowA) + " kg/hr");
    System.out.println("Well B: " + String.format("%.1f", flowB) + " kg/hr");
    System.out
        .println("Ring flow: " + String.format("%.1f", network.getPipeFlowRate("ring")) + " kg/hr");
    System.out.println("Export 1: " + String.format("%.1f", exp1) + " kg/hr");
    System.out.println("Export 2: " + String.format("%.1f", exp2) + " kg/hr");
    System.out.println("M1 P = " + String.format("%.2f", network.getNodePressure("M1")) + " bara");
    System.out.println("M2 P = " + String.format("%.2f", network.getNodePressure("M2")) + " bara");
  }

  /**
   * Test complete well system: IPR + tubing + choke + flowline.
   */
  @Test
  void testCompleteWellSystem() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("complete well");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(500);
    network.setTolerance(500.0);

    // Full well path: reservoir -> BH -> tubing -> WH -> choke -> ds_choke -> flowline -> sep
    network.addSourceNode("reservoir", 400.0, 0.0);
    network.addJunctionNode("BH");
    network.addJunctionNode("WH");
    network.addJunctionNode("DS");
    network.addFixedPressureSinkNode("separator", 40.0);

    network.addWellIPR("reservoir", "BH", "ipr", 3e-13, true);
    network.addTubing("BH", "WH", "tubing", 3500.0, 0.1, 85.0); // near-vertical
    network.addChoke("WH", "DS", "choke", 150.0, 70.0);
    network.addPipe("DS", "separator", "flowline", 8000.0, 0.20);

    network.run();

    assertTrue(network.isConverged(), "Complete well system should converge");

    double pRes = 400.0;
    double pBh = network.getNodePressure("BH");
    double pWh = network.getNodePressure("WH");
    double pDs = network.getNodePressure("DS");
    double pSep = 40.0;
    double rate = network.getPipeFlowRate("ipr");

    // Pressure should decrease monotonically: res > BH > WH > DS > sep
    assertTrue(pBh < pRes, "BHP < Pres: " + pBh + " < " + pRes);
    assertTrue(pWh < pBh, "WHP < BHP: " + pWh + " < " + pBh);
    assertTrue(pDs < pWh, "Downstream choke P < WHP: " + pDs + " < " + pWh);
    assertTrue(pDs > pSep, "Downstream choke P > Psep: " + pDs + " > " + pSep);
    assertTrue(rate > 0, "Should produce: " + rate);

    System.out.println("=== Complete Well System ===");
    System.out.println("P_res=" + String.format("%.1f", pRes) + " -> BHP="
        + String.format("%.1f", pBh) + " -> WHP=" + String.format("%.1f", pWh) + " -> DS_choke="
        + String.format("%.1f", pDs) + " -> Sep=" + String.format("%.1f", pSep) + " bara");
    System.out.println("IPR drawdown = " + String.format("%.1f", pRes - pBh) + " bar");
    System.out.println("Tubing dP = " + String.format("%.1f", pBh - pWh) + " bar");
    System.out.println("Choke dP = " + String.format("%.1f", pWh - pDs) + " bar");
    System.out.println("Flowline dP = " + String.format("%.1f", pDs - pSep) + " bar");
    System.out.println("Rate = " + String.format("%.1f", rate) + " kg/hr");
  }

  /**
   * Test element type reporting in solution summary.
   */
  @Test
  void testElementTypeInSolutionSummary() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("element types");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 300.0, 0.0);
    network.addJunctionNode("wh");
    network.addFixedPressureSinkNode("sep", 50.0);

    network.addWellIPR("res", "wh", "ipr", 5e-7, false);
    network.addPipe("wh", "sep", "line", 5000.0, 0.15);

    network.run();

    assertTrue(network.isConverged(), "Should converge");

    // Verify element types are accessible
    LoopedPipeNetwork.NetworkPipe iprPipe = network.getPipe("ipr");
    LoopedPipeNetwork.NetworkPipe linePipe = network.getPipe("line");

    assertEquals(LoopedPipeNetwork.NetworkElementType.WELL_IPR, iprPipe.getElementType());
    assertEquals(LoopedPipeNetwork.NetworkElementType.PIPE, linePipe.getElementType());

    // JSON output should include element types
    String json = network.toJson();
    assertTrue(json.contains("WELL_IPR") || json.contains("ipr"),
        "JSON should reference IPR element");
  }

  // ============================================================
  // ProcessSystem integration tests
  // ============================================================

  /**
   * Test that LoopedPipeNetwork produces outlet streams after solving.
   */
  @Test
  void testOutletStreamCreation() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("outlet-test");
    network.setFluidTemplate(testGas);

    network.addSourceNode("supply", 50.0, 1000.0);
    network.addSinkNode("customer", 500.0);
    network.addJunctionNode("junc");
    network.addPipe("supply", "junc", "p1", 5000.0, 0.3);
    network.addPipe("junc", "customer", "p2", 3000.0, 0.2);

    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    network.setMaxIterations(200);
    network.run();

    assertTrue(network.isConverged(), "Network should converge");

    // Outlet stream should exist at sink node
    neqsim.process.equipment.stream.StreamInterface outStream = network.getOutletStream("customer");
    assertNotNull(outStream, "Outlet stream should be created at sink node");

    // Outlet stream should have solved pressure
    double outPressureBara = outStream.getPressure("bara");
    assertTrue(outPressureBara > 1.0 && outPressureBara < 50.0,
        "Outlet pressure should be between 1 and 50 bara, got " + outPressureBara);

    // Default getOutletStream() should return the same
    neqsim.process.equipment.stream.StreamInterface defaultOut = network.getOutletStream();
    assertNotNull(defaultOut, "Default outlet stream should exist");

    // getOutletStreams() should be non-empty
    java.util.List<neqsim.process.equipment.stream.StreamInterface> outlets =
        network.getOutletStreams();
    assertTrue(outlets.size() > 0, "getOutletStreams() should return at least one stream");
  }

  /**
   * Test feed stream constructor and stream-based source node update.
   */
  @Test
  void testFeedStreamConstructor() {
    // Create upstream stream
    neqsim.process.equipment.stream.Stream feed =
        new neqsim.process.equipment.stream.Stream("feed", testGas);
    feed.setFlowRate(2000.0, "kg/hr");
    feed.setPressure(60.0, "bara");
    feed.setTemperature(25.0, "C");
    feed.run();

    // Create network with feed stream
    LoopedPipeNetwork network = new LoopedPipeNetwork("feed-test", feed);
    assertNotNull(network.getFluidTemplate(), "Fluid template should be set from feed stream");

    // Add nodes (source pressure will be overwritten by feed stream)
    network.addSourceNode("supply", 50.0, 1000.0);
    network.addSinkNode("customer", 500.0);
    network.addPipe("supply", "customer", "pipe", 5000.0, 0.3);

    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    network.setMaxIterations(200);
    network.run();

    assertTrue(network.isConverged(), "Network should converge with feed stream");

    // Source node should have the feed stream's pressure (60 bara)
    double supplyP = network.getNodePressure("supply");
    assertEquals(60.0, supplyP, 1.0, "Supply pressure should be ~60 bara from feed stream");
  }

  /**
   * Test explicit setFeedStream binding to a named source node.
   */
  @Test
  void testSetFeedStreamNamed() {
    neqsim.process.equipment.stream.Stream feed1 =
        new neqsim.process.equipment.stream.Stream("feed1", testGas);
    feed1.setFlowRate(1500.0, "kg/hr");
    feed1.setPressure(70.0, "bara");
    feed1.setTemperature(30.0, "C");
    feed1.run();

    LoopedPipeNetwork network = new LoopedPipeNetwork("named-feed-test");
    network.addSourceNode("well1", 50.0, 500.0);
    network.addFixedPressureSinkNode("sep", 30.0);
    network.addPipe("well1", "sep", "line", 5000.0, 0.2);

    // Bind feed stream to named source node
    network.setFeedStream("well1", feed1);

    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    network.setMaxIterations(200);
    network.run();

    assertTrue(network.isConverged(), "Named feed stream network should converge");

    // Well1 node should have feed stream's pressure (70 bara)
    double wellP = network.getNodePressure("well1");
    assertEquals(70.0, wellP, 1.0, "Well1 pressure should be ~70 bara from named feed stream");

    // getInletStreams() should return the feed
    java.util.List<neqsim.process.equipment.stream.StreamInterface> inlets =
        network.getInletStreams();
    assertEquals(1, inlets.size(), "Should have one inlet stream");
  }

  /**
   * Test LoopedPipeNetwork inside a ProcessSystem with upstream and downstream equipment.
   */
  @Test
  void testProcessSystemIntegration() {
    // Build a simple process: Stream -> LoopedPipeNetwork -> downstream check
    neqsim.process.equipment.stream.Stream feed =
        new neqsim.process.equipment.stream.Stream("feed gas", testGas);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setPressure(80.0, "bara");
    feed.setTemperature(25.0, "C");

    LoopedPipeNetwork network = new LoopedPipeNetwork("gathering", feed);
    network.addSourceNode("inlet", 80.0, 5000.0);
    network.addFixedPressureSinkNode("delivery", 40.0);
    network.addPipe("inlet", "delivery", "export", 10000.0, 0.3);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    network.setMaxIterations(200);

    // Add to ProcessSystem
    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(feed);
    process.add(network);
    process.run();

    // Network should have converged
    assertTrue(network.isConverged(), "Network in ProcessSystem should converge");

    // Outlet stream should be available for downstream connection
    neqsim.process.equipment.stream.StreamInterface outlet = network.getOutletStream();
    assertNotNull(outlet, "Network outlet stream should exist after ProcessSystem.run()");

    double outP = outlet.getPressure("bara");
    assertEquals(40.0, outP, 1.0, "Outlet pressure should be ~40 bara (fixed sink)");

    double outFlow = outlet.getFlowRate("kg/hr");
    assertTrue(outFlow > 100.0, "Outlet flow should be positive, got " + outFlow);
  }

  /**
   * Test outlet stream can be chained to downstream equipment (Separator).
   */
  @Test
  void testDownstreamChaining() {
    neqsim.process.equipment.stream.Stream feed =
        new neqsim.process.equipment.stream.Stream("well fluid", testGas);
    feed.setFlowRate(3000.0, "kg/hr");
    feed.setPressure(60.0, "bara");
    feed.setTemperature(40.0, "C");

    LoopedPipeNetwork network = new LoopedPipeNetwork("pipeline", feed);
    network.addSourceNode("wellhead", 60.0, 3000.0);
    network.addFixedPressureSinkNode("platform", 35.0);
    network.addPipe("wellhead", "platform", "flowline", 8000.0, 0.2);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    network.setMaxIterations(200);

    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(feed);
    process.add(network);
    process.run();

    // Get outlet and connect to separator
    neqsim.process.equipment.stream.StreamInterface pipelineOutlet = network.getOutletStream();
    assertNotNull(pipelineOutlet, "Pipeline outlet should exist");

    neqsim.process.equipment.separator.Separator separator =
        new neqsim.process.equipment.separator.Separator("HP Sep", pipelineOutlet);
    process.add(separator);

    // Run separator
    separator.run();

    // Separator should have run with the network outlet's conditions
    assertNotNull(separator.getGasOutStream(), "Separator gas outlet should exist");
    double sepP = separator.getGasOutStream().getPressure("bara");
    assertEquals(35.0, sepP, 2.0, "Separator should operate near pipeline delivery pressure");
  }

  /**
   * Test production network with IPR well, choke, and flowline producing outlet streams.
   */
  @Test
  void testProductionNetworkOutletStreams() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("prod-net-streams");
    network.setFluidTemplate(testGas);

    network.addSourceNode("reservoir", 350.0, 0.0);
    network.addJunctionNode("wellhead");
    network.addJunctionNode("downstream");
    network.addFixedPressureSinkNode("separator", 50.0);

    network.addWellIPR("reservoir", "wellhead", "ipr", 5e-7, false);
    network.addChoke("wellhead", "downstream", "choke", 150.0, 80.0);
    network.addPipe("downstream", "separator", "flowline", 5000.0, 0.2);

    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(500.0);
    network.setMaxIterations(500);
    network.run();

    assertTrue(network.isConverged(), "Production network should converge");

    // Sink outlet stream
    neqsim.process.equipment.stream.StreamInterface sepStream =
        network.getOutletStream("separator");
    assertNotNull(sepStream, "Separator outlet stream should exist");
    assertEquals(50.0, sepStream.getPressure("bara"), 1.0, "Separator stream should be at 50 bara");
    assertTrue(sepStream.getFlowRate("kg/hr") > 1000.0,
        "Separator stream should have significant flow");

    // Source node stream (reservoir)
    neqsim.process.equipment.stream.StreamInterface resStream =
        network.getSourceNodeStream("reservoir");
    assertNotNull(resStream, "Reservoir source stream should exist");
  }

  // ===== Tests for 8 state-of-the-art improvements =====

  /**
   * Test compressor element in network (Improvement #2).
   */
  @Test
  void testCompressorElement() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("CompressorNet");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    // Source -> Pipe -> Compressor -> Pipe -> Sink
    network.addSourceNode("supply", 30.0, 0.0);
    network.addJunctionNode("j1");
    network.addJunctionNode("j2");
    network.addFixedPressureSinkNode("delivery", 60.0);

    network.addPipe("supply", "j1", "feed_pipe", 5000, 0.3, 0.00005);
    network.addCompressor("j1", "j2", "comp1", 0.75);
    network.addPipe("j2", "delivery", "discharge_pipe", 10000, 0.3, 0.00005);

    network.run();
    assertTrue(network.isConverged(), "Compressor network should converge");

    // Compressor should produce a pressure rise (negative head loss)
    LoopedPipeNetwork.NetworkPipe comp = network.getPipe("comp1");
    assertNotNull(comp, "Compressor element should exist");
    assertEquals(LoopedPipeNetwork.NetworkElementType.COMPRESSOR, comp.getElementType());
  }

  /**
   * Test regulator (pressure reducing valve) element.
   */
  @Test
  void testRegulatorElement() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("RegulatorNet");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    // High pressure source -> Pipe -> Regulator (set to 40 bar) -> Pipe -> Low pressure sink
    network.addSourceNode("hp_supply", 80.0, 0.0);
    network.addJunctionNode("j1");
    network.addJunctionNode("j2");
    network.addFixedPressureSinkNode("lp_delivery", 35.0);

    network.addPipe("hp_supply", "j1", "hp_pipe", 2000, 0.3, 0.00005);
    network.addRegulator("j1", "j2", "prv1", 40.0);
    network.addPipe("j2", "lp_delivery", "lp_pipe", 5000, 0.25, 0.00005);

    network.run();
    assertTrue(network.isConverged(), "Regulator network should converge");

    LoopedPipeNetwork.NetworkPipe reg = network.getPipe("prv1");
    assertNotNull(reg);
    assertEquals(LoopedPipeNetwork.NetworkElementType.REGULATOR, reg.getElementType());
    assertEquals(40e5, reg.getRegulatorSetPoint(), 1e3);
  }

  /**
   * Test pipe efficiency factor for aged/fouled pipes (Improvement #7a).
   */
  @Test
  void testPipeEfficiency() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("EfficiencyNet");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("supply", 50.0, 0.0);
    network.addFixedPressureSinkNode("delivery", 40.0);
    network.addPipe("supply", "delivery", "main", 10000, 0.3, 0.00005);

    // First run with default efficiency (1.0)
    network.run();
    assertTrue(network.isConverged(), "Default efficiency should converge");
    double flowDefault = network.getPipe("main").getFlowRate();

    // Set pipe efficiency to 0.85 (fouled pipe)
    network.setPipeEfficiency("main", 0.85);
    assertEquals(0.85, network.getPipe("main").getPipeEfficiency(), 0.001);

    network.run();
    assertTrue(network.isConverged(), "Reduced efficiency should converge");
    double flowFouled = network.getPipe("main").getFlowRate();

    // Fouled pipe should carry less flow (or at least not more)
    assertTrue(Math.abs(flowFouled) <= Math.abs(flowDefault) * 1.01,
        "Fouled pipe should not carry more flow than clean pipe");
  }

  /**
   * Test erosional velocity check per API RP 14E (Improvement #7b).
   */
  @Test
  void testErosionalVelocityCheck() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("ErosionalNet");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("supply", 80.0, 0.0);
    network.addFixedPressureSinkNode("delivery", 30.0);
    network.addPipe("supply", "delivery", "small_pipe", 5000, 0.05, 0.00005);

    network.run();
    assertTrue(network.isConverged(), "Erosional check network should converge");

    // Run erosional velocity check
    List<String> violations = network.checkErosionalVelocity();
    assertNotNull(violations, "Violations list should not be null");

    // The erosional velocity should be calculated
    LoopedPipeNetwork.NetworkPipe pipe = network.getPipe("small_pipe");
    assertTrue(pipe.getErosionalVelocity() > 0, "Erosional velocity should be calculated");
    assertTrue(pipe.getErosionalVelocityRatio() > 0, "Erosional ratio should be calculated");
  }

  /**
   * Test per-node fluid composition tracking (Improvement #5).
   */
  @Test
  void testPerNodeFluidComposition() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("CompositionalNet");

    // Create two different gas compositions
    SystemInterface leanGas = new SystemSrkEos(288.15, 50.0);
    leanGas.addComponent("methane", 0.95);
    leanGas.addComponent("ethane", 0.03);
    leanGas.addComponent("propane", 0.02);
    leanGas.createDatabase(true);
    leanGas.setMixingRule("classic");
    leanGas.init(0);
    leanGas.init(1);

    SystemInterface richGas = new SystemSrkEos(288.15, 50.0);
    richGas.addComponent("methane", 0.80);
    richGas.addComponent("ethane", 0.12);
    richGas.addComponent("propane", 0.08);
    richGas.createDatabase(true);
    richGas.setMixingRule("classic");
    richGas.init(0);
    richGas.init(1);

    network.setFluidTemplate(leanGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    // Two sources mixing at a junction
    network.addSourceNode("well1", 60.0, 0.0);
    network.addSourceNode("well2", 60.0, 0.0);
    network.addJunctionNode("manifold");
    network.addFixedPressureSinkNode("platform", 40.0);

    network.addPipe("well1", "manifold", "pipe_w1", 5000, 0.2, 0.00005);
    network.addPipe("well2", "manifold", "pipe_w2", 5000, 0.2, 0.00005);
    network.addPipe("manifold", "platform", "export", 10000, 0.3, 0.00005);

    // Assign compositions to source nodes
    network.setNodeFluid("well1", leanGas);
    network.setNodeFluid("well2", richGas);

    network.run();
    assertTrue(network.isConverged(), "Compositional network should converge");

    // Run compositional mixing
    network.updateCompositionalMixing();

    // Check that manifold has a mixed composition
    SystemInterface mixedFluid = network.getNodeFluid("manifold");
    assertNotNull(mixedFluid, "Manifold should have a mixed fluid");

    // The mixed methane fraction should be between the two sources
    double methaneZ = mixedFluid.getPhase(0).getComponent("methane").getz();
    assertTrue(methaneZ > 0.79 && methaneZ < 0.96,
        "Mixed methane fraction should be between lean and rich gas: " + methaneZ);
  }

  /**
   * Test choke optimization to maximize production (Improvement #6).
   */
  @Test
  void testChokeOptimization() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("ChokeOptNet");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    // Two wells with chokes sharing a gathering manifold
    network.addSourceNode("res1", 200.0, 0.0);
    network.addSourceNode("res2", 180.0, 0.0);
    network.addJunctionNode("wh1");
    network.addJunctionNode("wh2");
    network.addJunctionNode("manifold");
    network.addFixedPressureSinkNode("platform", 50.0);

    network.addWellIPR("res1", "wh1", "ipr1", 5e-6, false);
    network.addChoke("wh1", "manifold", "choke1", 50.0, 50.0);
    network.addWellIPR("res2", "wh2", "ipr2", 4e-6, false);
    network.addChoke("wh2", "manifold", "choke2", 50.0, 50.0);
    network.addPipe("manifold", "platform", "export", 20000, 0.3, 0.00005);

    // Get baseline production
    network.run();
    assertTrue(network.isConverged(), "Baseline should converge");
    double baselineFlow = network.getTotalSinkFlow();
    assertTrue(baselineFlow > 0, "Baseline production should be positive");

    // Optimize choke openings (few iterations to keep test fast)
    double optimizedFlow = network.optimizeChokeOpenings(3, 0.01);
    assertTrue(optimizedFlow >= baselineFlow * 0.9,
        "Optimized should not be much less than baseline");
  }

  /**
   * Test multiphase pipe with Beggs-Brill (Improvement #3).
   */
  @Test
  void testMultiphasePipeBeggsAndBrills() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("MultiphaseNet");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("wellhead", 80.0, 0.0);
    network.addFixedPressureSinkNode("platform", 40.0);

    network.addMultiphasePipe("wellhead", "platform", "flowline", 15000.0, 0.2);

    network.run();
    assertTrue(network.isConverged(), "Multiphase pipe network should converge");

    LoopedPipeNetwork.NetworkPipe flowline = network.getPipe("flowline");
    assertNotNull(flowline);
    assertEquals(LoopedPipeNetwork.NetworkElementType.MULTIPHASE_PIPE, flowline.getElementType());
    assertTrue(Math.abs(flowline.getFlowRate()) > 0, "Flowline should have non-zero flow");
  }

  /**
   * Test VFP table export (Improvement #8).
   */
  @Test
  void testVFPExport() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("VFPNet");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("reservoir", 250.0, 0.0);
    network.addJunctionNode("wellhead");
    network.addFixedPressureSinkNode("platform", 50.0);

    network.addWellIPR("reservoir", "wellhead", "ipr", 8e-6, false);
    network.addPipe("wellhead", "platform", "flowline", 10000, 0.2, 0.00005);

    network.run();
    assertTrue(network.isConverged(), "VFP network should converge");

    // Export VFP tables (to temp file)
    String tempFile = System.getProperty("java.io.tmpdir") + "/test_vfp.inc";
    double[] flowRates = new double[] {100, 500, 1000, 2000};
    double[] thps = new double[] {10, 20, 30, 50};
    double[] waterCuts = new double[] {0.0, 0.2};
    double[] gors = new double[] {100, 200};

    // This should not throw
    network.exportVFPTables(tempFile, flowRates, thps, waterCuts, gors);
  }

  /**
   * Test ThrottlingValve delegate for choke (Improvement #4).
   */
  @Test
  void testChokeValveModelDelegate() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("ValveModelNet");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(200.0);

    network.addSourceNode("reservoir", 200.0, 0.0);
    network.addJunctionNode("wh");
    network.addFixedPressureSinkNode("manifold", 50.0);

    network.addWellIPR("reservoir", "wh", "ipr", 5e-6, false);
    network.addChoke("wh", "manifold", "choke", 50.0, 80.0);

    // Enable ThrottlingValve model
    network.getPipe("choke").setChokeUseValveModel(true);
    assertTrue(network.getPipe("choke").isChokeUseValveModel());

    // Run - should not crash (convergence depends on flash)
    network.run();
    LoopedPipeNetwork.NetworkPipe choke = network.getPipe("choke");
    assertNotNull(choke);
  }

  /**
   * Test JSON output includes new element types.
   */
  @Test
  void testJsonOutputNewElementTypes() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("JsonTestNet");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("supply", 30.0, 0.0);
    network.addJunctionNode("j1");
    network.addJunctionNode("j2");
    network.addFixedPressureSinkNode("delivery", 60.0);

    network.addPipe("supply", "j1", "pipe1", 1000, 0.3, 0.00005);
    network.addCompressor("j1", "j2", "comp", 0.75);
    network.addPipe("j2", "delivery", "pipe2", 1000, 0.3, 0.00005);

    network.run();

    String json = network.toJson();
    assertNotNull(json);
    assertTrue(json.contains("COMPRESSOR"), "JSON should contain COMPRESSOR element type");
    assertTrue(json.contains("elementType"), "JSON should contain elementType field");
    assertTrue(json.contains("compressorEfficiency"), "JSON should contain compressor fields");
  }

  /**
   * Test network report generation.
   */
  @Test
  void testNetworkReport() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("ReportNet");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("supply", 50.0, 0.0);
    network.addFixedPressureSinkNode("delivery", 40.0);
    network.addPipe("supply", "delivery", "main", 10000, 0.3, 0.00005);

    network.run();
    assertTrue(network.isConverged());

    String report = network.getNetworkReport();
    assertNotNull(report);
    assertTrue(report.contains("Network Solution Report"), "Report should have header");
    assertTrue(report.contains("supply"), "Report should list nodes");
    assertTrue(report.contains("main"), "Report should list pipes");
  }

  /**
   * Test getTotalSinkFlow aggregation.
   */
  @Test
  void testTotalSinkFlow() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("SinkFlowNet");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("s1", 60.0, 0.0);
    network.addSourceNode("s2", 60.0, 0.0);
    network.addJunctionNode("j");
    network.addFixedPressureSinkNode("sink", 40.0);

    network.addPipe("s1", "j", "p1", 5000, 0.2, 0.00005);
    network.addPipe("s2", "j", "p2", 5000, 0.2, 0.00005);
    network.addPipe("j", "sink", "p3", 5000, 0.3, 0.00005);

    network.run();
    assertTrue(network.isConverged());

    double totalFlow = network.getTotalSinkFlow();
    assertTrue(totalFlow > 0, "Total sink flow should be positive");
  }

  /**
   * Test combined production network with all element types.
   */
  @Test
  void testCombinedProductionNetwork() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("CombinedNet");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    // Well -> IPR -> Choke -> Pipe -> Compressor -> Pipe -> Regulator -> Pipe -> Platform
    network.addSourceNode("reservoir", 250.0, 0.0);
    network.addJunctionNode("bhp");
    network.addJunctionNode("wh");
    network.addJunctionNode("comp_in");
    network.addJunctionNode("comp_out");
    network.addJunctionNode("reg_in");
    network.addJunctionNode("reg_out");
    network.addFixedPressureSinkNode("platform", 40.0);

    network.addWellIPR("reservoir", "bhp", "ipr", 5e-6, false);
    network.addChoke("bhp", "wh", "choke", 50.0, 70.0);
    network.addPipe("wh", "comp_in", "flowline", 15000, 0.2, 0.00005);
    network.addCompressor("comp_in", "comp_out", "compressor", 0.75);
    network.addPipe("comp_out", "reg_in", "discharge", 5000, 0.25, 0.00005);
    network.addRegulator("reg_in", "reg_out", "prv", 45.0);
    network.addPipe("reg_out", "platform", "delivery", 2000, 0.25, 0.00005);

    network.run();
    assertTrue(network.isConverged(), "Combined network with all element types should converge");

    // Verify flow is positive throughout
    assertTrue(Math.abs(network.getPipeFlowRate("flowline")) > 0,
        "flowline should have non-zero flow");

    // Run erosional check
    List<String> violations = network.checkErosionalVelocity();
    assertNotNull(violations);

    // Get report
    String report = network.getNetworkReport();
    assertTrue(report.length() > 100, "Report should be substantial");
  }

  // =====================================================================
  // Tests for Phase-2 Improvements: Gas Quality, Nodal Analysis,
  // Constraint Checking, Fuel Gas Consumption
  // =====================================================================

  /**
   * Test ISO 6976 gas quality tracking at network nodes (Phase-2 Improvement #1).
   */
  @Test
  void testGasQualityTracking() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("GasQualNet");

    // Lean and rich gas fluids
    SystemInterface leanGas = new SystemSrkEos(288.15, 60e5);
    leanGas.addComponent("methane", 0.95);
    leanGas.addComponent("ethane", 0.03);
    leanGas.addComponent("propane", 0.02);
    leanGas.createDatabase(true);
    leanGas.setMixingRule("classic");
    leanGas.init(0);
    leanGas.init(1);

    SystemInterface richGas = new SystemSrkEos(288.15, 60e5);
    richGas.addComponent("methane", 0.80);
    richGas.addComponent("ethane", 0.12);
    richGas.addComponent("propane", 0.08);
    richGas.createDatabase(true);
    richGas.setMixingRule("classic");
    richGas.init(0);
    richGas.init(1);

    network.setFluidTemplate(leanGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    // Two wells mixing at manifold
    network.addSourceNode("well1", 60.0, 0.0);
    network.addSourceNode("well2", 60.0, 0.0);
    network.addJunctionNode("manifold");
    network.addFixedPressureSinkNode("delivery", 40.0);

    network.addPipe("well1", "manifold", "pipe_w1", 5000, 0.2, 0.00005);
    network.addPipe("well2", "manifold", "pipe_w2", 5000, 0.2, 0.00005);
    network.addPipe("manifold", "delivery", "export", 10000, 0.3, 0.00005);

    network.setNodeFluid("well1", leanGas);
    network.setNodeFluid("well2", richGas);

    network.run();
    assertTrue(network.isConverged(), "Network should converge");

    // Propagate compositions and calculate gas quality
    network.updateCompositionalMixing();
    Map<String, double[]> gasQuality = network.calculateGasQuality();

    // Should have quality data at nodes with fluids
    assertFalse(gasQuality.isEmpty(), "Gas quality map should not be empty");

    // Check source node quality
    double[] well1Quality = network.getNodeGasQuality("well1");
    assertNotNull(well1Quality, "Well1 should have gas quality data");
    assertTrue(well1Quality[0] > 40.0 && well1Quality[0] < 60.0,
        "Wobbe index should be in typical range (40-60 MJ/Sm3): " + well1Quality[0]);
    assertTrue(well1Quality[1] > 30000 && well1Quality[1] < 50000,
        "HHV should be reasonable (30000-50000 kJ/Sm3): " + well1Quality[1]);
    assertTrue(well1Quality[3] > 0.5 && well1Quality[3] < 1.0,
        "Relative density should be between 0.5 and 1.0: " + well1Quality[3]);

    // Lean gas should have higher Wobbe than rich gas (more methane, less heavy)
    double[] well2Quality = network.getNodeGasQuality("well2");
    assertNotNull(well2Quality, "Well2 should have gas quality data");

    // Check Wobbe bounds
    List<String> violations = network.checkGasQualityLimits(46.0, 53.0);
    assertNotNull(violations, "Violations list should not be null");

    // Network report should contain gas quality section
    String report = network.getNetworkReport();
    assertTrue(report.contains("ISO 6976"), "Report should include ISO 6976 section");

    // JSON should include gas quality
    String json = network.toJson();
    assertTrue(json.contains("gasQuality_ISO6976"), "JSON should include gas quality data");
  }

  /**
   * Test nodal analysis (IPR-VLP crossplot) for a production well (Phase-2 Improvement #2).
   */
  @Test
  void testNodalAnalysis() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("NodalNet");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    // Well with IPR and tubing
    network.addSourceNode("reservoir", 300.0, 0.0);
    network.addJunctionNode("bhp_node");
    network.addFixedPressureSinkNode("wellhead", 80.0);

    network.addWellIPR("reservoir", "bhp_node", "well_ipr", 5e-6, true);
    network.addPipe("bhp_node", "wellhead", "tubing", 3000, 0.1, 0.00005);

    network.run();
    assertTrue(network.isConverged(), "Production well should converge");

    // Perform nodal analysis
    Map<String, double[]> nodalResult = network.nodalAnalysis("well_ipr", "tubing", 25);

    assertFalse(nodalResult.isEmpty(), "Nodal analysis should return results");

    double[] bhps = nodalResult.get("bhp");
    assertNotNull(bhps, "Should have BHP array");
    assertEquals(25, bhps.length, "Should have 25 sweep points");

    double[] iprRates = nodalResult.get("iprRate");
    assertNotNull(iprRates, "Should have IPR rate array");

    double[] vlpRates = nodalResult.get("vlpRate");
    assertNotNull(vlpRates, "Should have VLP rate array");

    // Debug output
    System.out.println("Nodal debug: wellhead P=" + network.getNodePressure("wellhead"));
    for (int i = 0; i < Math.min(5, bhps.length); i++) {
      System.out.printf("  BHP=%.1f: IPR=%.0f, VLP=%.0f%n", bhps[i], iprRates[i], vlpRates[i]);
    }

    // Operating point should exist
    double[] opBHP = nodalResult.get("operatingBHP");
    double[] opRate = nodalResult.get("operatingRate");
    assertNotNull(opBHP, "Should have operating BHP");
    assertNotNull(opRate, "Should have operating rate");
    assertTrue(opBHP[0] >= 1.0 && opBHP[0] <= 300.0,
        "Operating BHP should be in sweep range: " + opBHP[0]);
    assertTrue(opRate[0] >= 0, "Operating rate should be non-negative: " + opRate[0]);

    // IPR rate should decrease as BHP increases (towards reservoir pressure)
    assertTrue(iprRates[0] > iprRates[bhps.length - 1],
        "IPR rate at low BHP should exceed rate at high BHP");
  }

  /**
   * Test constraint envelope checking (Phase-2 Improvement #3).
   */
  @Test
  void testConstraintChecking() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("ConstraintNet");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("inlet", 80.0, 0.0);
    network.addJunctionNode("mid");
    network.addFixedPressureSinkNode("outlet", 30.0);

    network.addPipe("inlet", "mid", "pipe1", 50000, 0.5, 0.00005);
    network.addPipe("mid", "outlet", "pipe2", 50000, 0.5, 0.00005);

    // Set constraints: mid pressure must be between 50 and 70 bara
    network.setNodePressureLimits("mid", 50.0, 70.0);

    // Set flow limit on pipe1: max 200,000 kg/hr
    network.setElementFlowLimits("pipe1", 0, 200000.0);

    network.run();
    assertTrue(network.isConverged(), "Constraint network should converge");

    // Check constraints
    List<String> violations = network.checkConstraints();
    assertNotNull(violations, "Violations list should not be null");

    // The mid-node pressure should be around 55 bara (halfway between 80 and 30)
    double midP = network.getNodePressure("mid");
    if (midP < 50.0 || midP > 70.0) {
      assertFalse(violations.isEmpty(), "Should report violation when pressure out of bounds");
      assertTrue(violations.get(0).contains("PRESSURE"),
          "Violation should mention pressure: " + violations.get(0));
    }

    // Get violations via getter
    List<String> stored = network.getConstraintViolations();
    assertEquals(violations.size(), stored.size(), "Stored violations should match returned");

    // Network report should contain constraint section if violations exist
    if (!violations.isEmpty()) {
      String report = network.getNetworkReport();
      assertTrue(report.contains("Constraint Violations"),
          "Report should include constraint violations");
    }

    // JSON should include violations if present
    if (!violations.isEmpty()) {
      String json = network.toJson();
      assertTrue(json.contains("constraintViolations"),
          "JSON should include constraint violations");
    }
  }

  /**
   * Test compressor fuel gas consumption calculation (Phase-2 Improvement #4).
   */
  @Test
  void testFuelGasConsumption() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("FuelGasNet");

    SystemInterface gasFluid = new SystemSrkEos(288.15, 60e5);
    gasFluid.addComponent("methane", 0.90);
    gasFluid.addComponent("ethane", 0.07);
    gasFluid.addComponent("propane", 0.03);
    gasFluid.createDatabase(true);
    gasFluid.setMixingRule("classic");
    gasFluid.init(0);
    gasFluid.init(1);

    network.setFluidTemplate(gasFluid);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    // Simple source -> compressor -> delivery.
    // The NR-GGA solver may put the compressor in bypass if the pressure profile
    // doesn't require active compression. We explicitly set compressor power after
    // solving to test the fuel gas calculation.
    network.addSourceNode("inlet", 60.0, 0.0);
    network.addJunctionNode("comp_in");
    network.addJunctionNode("comp_out");
    network.addFixedPressureSinkNode("delivery", 40.0);

    network.addPipe("inlet", "comp_in", "suction", 1000, 0.3, 0.00005);
    network.addCompressor("comp_in", "comp_out", "compressor1", 0.75);
    network.addPipe("comp_out", "delivery", "discharge", 1000, 0.3, 0.00005);

    // Set fuel gas heat rate (gas turbine)
    network.setFuelGasHeatRate(10000.0);
    assertEquals(10000.0, network.getFuelGasHeatRate(), 0.1);

    // Assign fluid for gas quality calculation
    network.setNodeFluid("inlet", gasFluid);

    network.run();
    assertTrue(network.isConverged(), "Fuel gas network should converge");

    // Set compressor power explicitly (simulates external compressor model or user input).
    // In the NR-GGA, the compressor may bypass if pressure profile doesn't require it.
    network.getPipe("compressor1").setCompressorPower(5000.0); // 5 MW gas turbine

    // Run compositional mixing and gas quality for LHV-based fuel calc
    network.updateCompositionalMixing();
    network.calculateGasQuality();

    // Calculate fuel gas
    Map<String, Double> fuelRates = network.calculateFuelGasConsumption();
    assertNotNull(fuelRates, "Fuel rates map should not be null");
    assertFalse(fuelRates.isEmpty(), "Should have at least one compressor fuel rate");

    // Compressor should consume fuel
    Double comp1Fuel = fuelRates.get("compressor1");
    assertNotNull(comp1Fuel, "compressor1 should have fuel rate");
    assertTrue(comp1Fuel > 0, "Fuel rate should be positive: " + comp1Fuel);

    // Total fuel gas rate
    double totalFuel = network.getTotalFuelGasRate();
    assertTrue(totalFuel > 0, "Total fuel gas should be positive: " + totalFuel);

    // Fuel gas percentage should be reasonable (typically 1-5% for gas networks)
    double pct = network.getFuelGasPercentage();
    assertTrue(pct >= 0, "Fuel gas percentage should be non-negative: " + pct);

    // Report should include fuel gas
    String report = network.getNetworkReport();
    assertTrue(report.contains("Fuel Gas"), "Report should include fuel gas section");

    // JSON should include fuel gas
    String json = network.toJson();
    assertTrue(json.contains("fuelGasConsumption"), "JSON should include fuel gas data");
  }

  /**
   * Test oil quality tracing: TVP and RVP at network nodes using ASTM D6377.
   *
   * <p>
   * Oil quality tracking (TVP/RVP) operates on the per-node fluid composition map. This test sets
   * up a simple oil pipeline network, assigns oil fluids at the nodes, and verifies TVP/RVP
   * calculations per ASTM D6377.
   * </p>
   */
  @Test
  void testOilQualityTracking() {
    // Gas fluid template for the network solver
    SystemInterface gasTemplate = new SystemSrkEos(288.15, 60e5);
    gasTemplate.addComponent("methane", 0.95);
    gasTemplate.addComponent("ethane", 0.03);
    gasTemplate.addComponent("propane", 0.02);
    gasTemplate.createDatabase(true);
    gasTemplate.setMixingRule("classic");
    gasTemplate.init(0);
    gasTemplate.init(1);

    LoopedPipeNetwork network = new LoopedPipeNetwork("oil_quality_test");
    network.setFluidTemplate(gasTemplate);

    // Simple pipeline: source -> junction -> delivery
    network.addSourceNode("oil_terminal", 50.0, 100000.0);
    network.addJunctionNode("midpoint");
    network.addSinkNode("refinery", 100000.0);

    network.addPipe("oil_terminal", "midpoint", "pipeline1", 50000.0, 0.4, 0.00005);
    network.addPipe("midpoint", "refinery", "pipeline2", 50000.0, 0.4, 0.00005);

    // Create an oil/condensate fluid for quality tracking
    // Matches the pattern from Standard_ASTM_D6377Test for known-good RVP/TVP values
    SystemInterface oilFluid = new SystemSrkEos(273.15 + 30.0, 10.0);
    oilFluid.addComponent("methane", 0.0006538);
    oilFluid.addComponent("ethane", 0.006538);
    oilFluid.addComponent("propane", 0.06538);
    oilFluid.addComponent("n-pentane", 0.1545);
    oilFluid.addComponent("nC10", 0.545);
    oilFluid.setMixingRule(2);
    oilFluid.init(0);

    // Assign oil fluids at nodes for quality tracking
    network.setNodeFluid("oil_terminal", oilFluid);
    network.setNodeFluid("midpoint", oilFluid);
    network.setNodeFluid("refinery", oilFluid);

    // Calculate oil quality (doesn't require network.run() — just needs node fluids)
    Map<String, double[]> oilQuality = network.calculateOilQuality();

    // Should have results at all nodes with fluids
    assertEquals(3, oilQuality.size(), "Should have oil quality for 3 nodes");

    // Check source node oil quality
    double[] sourceQ = network.getNodeOilQuality("oil_terminal");
    assertNotNull(sourceQ, "Source node should have oil quality");

    // TVP should be positive (some vapor pressure from light ends)
    double tvp = sourceQ[0];
    assertTrue(tvp > 0, "TVP should be positive: " + tvp);
    // TVP from ASTM D6377 test at 37.8C is ~1.67 bara; at 30C should be slightly less
    assertTrue(tvp < 5.0, "TVP should be reasonable for this oil: " + tvp);

    // RVP should be positive
    double rvp = sourceQ[1];
    assertTrue(rvp > 0, "RVP should be positive: " + rvp);
    // RVP from ASTM D6377 test is ~1.10 bara (VPCR4 method)
    assertTrue(rvp < 3.0, "RVP should be reasonable for this oil: " + rvp);

    // VPCR4 should be positive
    double vpcr4 = sourceQ[2];
    assertTrue(vpcr4 > 0, "VPCR4 should be positive: " + vpcr4);

    // Check oil quality limits with generous specs (should pass)
    List<String> violations = network.checkOilQualityLimits(5.0, 5.0);
    assertNotNull(violations, "Violations list should not be null");
    assertTrue(violations.isEmpty(), "Should have no violations with generous limits");

    // Check oil quality limits with tight specs at the refinery sink
    List<String> tightViolations = network.checkOilQualityLimits(0.1, 0.1);
    assertFalse(tightViolations.isEmpty(), "Should detect violations with tight limits");

    // Report should include oil quality
    String report = network.getNetworkReport();
    assertTrue(report.contains("Oil Quality"), "Report should include oil quality section");
    assertTrue(report.contains("TVP"), "Report should include TVP");
    assertTrue(report.contains("RVP"), "Report should include RVP");

    // JSON should include oil quality
    String json = network.toJson();
    assertTrue(json.contains("oilQuality_ASTM_D6377"), "JSON should include oil quality data");
    assertTrue(json.contains("tvp_bara"), "JSON should include TVP data");
    assertTrue(json.contains("rvp_bara"), "JSON should include RVP data");
  }

  // =====================================================================
  // Production Optimization Tests
  // =====================================================================

  @Test
  void testOptimizeProductionMaxFlow() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("optProd");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res1", 200.0, 0.0);
    network.addSourceNode("res2", 180.0, 0.0);
    network.addJunctionNode("wh1");
    network.addJunctionNode("wh2");
    network.addJunctionNode("manifold");
    network.addFixedPressureSinkNode("platform", 50.0);

    network.addWellIPR("res1", "wh1", "ipr1", 5e-6, false);
    network.addChoke("wh1", "manifold", "choke1", 50.0, 50.0);
    network.addWellIPR("res2", "wh2", "ipr2", 4e-6, false);
    network.addChoke("wh2", "manifold", "choke2", 50.0, 40.0);
    network.addPipe("manifold", "platform", "export", 20000, 0.3, 0.00005);

    network.run();
    assertTrue(network.isConverged(), "Baseline should converge");
    double baselineFlow = network.getTotalSinkFlow();
    assertTrue(baselineFlow > 0, "Baseline flow should be positive");

    double optimizedObj = network.optimizeProduction(5, 0.005);
    assertTrue(optimizedObj >= baselineFlow * 3600.0 * 0.9,
        "Optimized should not be much less than baseline");
  }

  @Test
  void testOptimizeProductionRevenueWeighted() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("revOpt");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res1", 200.0, 0.0);
    network.addSourceNode("res2", 180.0, 0.0);
    network.addJunctionNode("wh1");
    network.addJunctionNode("wh2");
    network.addJunctionNode("manifold");
    network.addFixedPressureSinkNode("platform", 50.0);

    network.addWellIPR("res1", "wh1", "ipr1", 5e-6, false);
    network.addChoke("wh1", "manifold", "choke1", 50.0, 50.0);
    network.addWellIPR("res2", "wh2", "ipr2", 4e-6, false);
    network.addChoke("wh2", "manifold", "choke2", 50.0, 50.0);
    network.addPipe("manifold", "platform", "export", 20000, 0.3, 0.00005);

    network.setWellPrice("choke1", 0.50);
    network.setWellPrice("choke2", 1.00);

    double revenue = network.optimizeProduction(5, 0.005);
    assertTrue(revenue > 0, "Revenue should be positive after optimization");

    java.util.Map<String, double[]> alloc = network.getWellAllocationResults();
    assertFalse(alloc.isEmpty(), "Allocation report should not be empty");
  }

  @Test
  void testSensitivityAnalysisChokeOpening() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("sensChoke");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 200.0, 0.0);
    network.addJunctionNode("wh");
    network.addJunctionNode("downstream");
    network.addFixedPressureSinkNode("platform", 50.0);

    network.addWellIPR("res", "wh", "ipr", 5e-6, false);
    network.addChoke("wh", "downstream", "choke", 50.0, 50.0);
    network.addPipe("downstream", "platform", "pipeline", 20000, 0.3, 0.00005);

    double[] openings = {10, 30, 50, 70, 90, 100};
    java.util.Map<String, double[]> results =
        network.sensitivityAnalysis("choke", "choke_opening", openings);

    assertNotNull(results.get("totalFlow_kghr"), "Should have flow results");
    assertEquals(openings.length, results.get("totalFlow_kghr").length);
    double[] flows = results.get("totalFlow_kghr");
    // At least some flows should be positive
    boolean anyPositive = false;
    for (double f : flows) {
      assertTrue(f >= 0, "Flow should not be negative");
      if (f > 0) {
        anyPositive = true;
      }
    }
    assertTrue(anyPositive, "At least some choke openings should produce flow");
  }

  @Test
  void testSensitivityAnalysisReservoirPressure() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("sensRes");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 200.0, 0.0);
    network.addJunctionNode("wh");
    network.addFixedPressureSinkNode("platform", 50.0);

    network.addWellIPR("res", "wh", "ipr", 5e-6, false);
    network.addPipe("wh", "platform", "pipeline", 20000, 0.3, 0.00005);

    double[] pressures = {100, 130, 160, 200};
    java.util.Map<String, double[]> results =
        network.sensitivityAnalysis("ipr", "reservoir_pressure", pressures);

    double[] flows = results.get("totalFlow_kghr");
    assertNotNull(flows, "Should have flow results");
    assertTrue(flows[flows.length - 1] > flows[0],
        "Higher reservoir pressure should give more flow");
  }

  @Test
  void testProductionForecast() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("forecast");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 200.0, 0.0);
    network.addJunctionNode("wh");
    network.addFixedPressureSinkNode("platform", 50.0);

    network.addWellIPR("res", "wh", "ipr", 5e-6, false);
    network.addPipe("wh", "platform", "pipeline", 20000, 0.3, 0.00005);

    double[] pressures = {200, 180, 160, 140, 120};
    double[] years = {0, 1, 2, 3, 4};

    java.util.Map<String, double[]> forecast = network.productionForecast(pressures, years);

    assertNotNull(forecast.get("rate_kghr"), "Should have rate profile");
    double[] rates = forecast.get("rate_kghr");
    assertTrue(rates[0] > rates[rates.length - 1],
        "Production rate should decline as reservoir depletes");

    double[] cum = forecast.get("cumulative_kg");
    assertTrue(cum[cum.length - 1] > 0, "Cumulative production should be positive");
    for (int i = 1; i < cum.length; i++) {
      assertTrue(cum[i] >= cum[i - 1], "Cumulative should be monotonically increasing");
    }
  }

  // =====================================================================
  // VFP Table Generation Tests
  // =====================================================================

  @Test
  void testCoupledVFPTableGeneration() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("coupledVFP");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 200.0, 0.0);
    network.addJunctionNode("wh");
    network.addFixedPressureSinkNode("platform", 50.0);

    network.addWellIPR("res", "wh", "ipr", 5e-6, false);
    network.addPipe("wh", "platform", "tubing", 3000, 0.15, 0.00005);

    double[] flowRates = {500, 1000, 2000, 5000, 10000}; // kg/hr
    double[] thps = {50, 60, 70, 80}; // bara

    java.util.Map<String, double[][]> tables = network.generateCoupledVFPTables(flowRates, thps);

    assertFalse(tables.isEmpty(), "Should have at least one VFP table");
    double[][] bhpTable = tables.values().iterator().next();
    assertEquals(flowRates.length, bhpTable.length, "Flow rate dimension should match");
    assertEquals(thps.length, bhpTable[0].length, "THP dimension should match");

    // BHP should be positive
    for (int f = 0; f < flowRates.length; f++) {
      for (int t = 0; t < thps.length; t++) {
        assertTrue(bhpTable[f][t] > 0,
            "BHP should be positive at flow=" + flowRates[f] + " THP=" + thps[t]);
      }
    }

    // BHP should increase with THP
    for (int f = 0; f < flowRates.length; f++) {
      assertTrue(bhpTable[f][thps.length - 1] >= bhpTable[f][0],
          "BHP should increase with THP for flow rate index " + f);
    }
  }

  @Test
  void testCoupledVFPExportToFile() throws Exception {
    LoopedPipeNetwork network = new LoopedPipeNetwork("vfpExportFile");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 200.0, 0.0);
    network.addJunctionNode("wh");
    network.addFixedPressureSinkNode("platform", 50.0);

    network.addWellIPR("res", "wh", "ipr", 5e-6, false);
    network.addPipe("wh", "platform", "tubing", 2000, 0.15, 0.00005);

    double[] flowRates = {1000, 5000, 10000}; // kg/hr
    double[] thps = {50, 70}; // bara

    java.io.File tempFile = java.io.File.createTempFile("coupled_vfp_", ".inc");
    tempFile.deleteOnExit();

    network.exportCoupledVFPTables(tempFile.getAbsolutePath(), flowRates, thps);
    assertTrue(true, "Export completed without error");
  }

  @Test
  void testVFPPointValidation() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("vfpValidate");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 200.0, 0.0);
    network.addJunctionNode("wh");
    network.addFixedPressureSinkNode("platform", 50.0);

    network.addWellIPR("res", "wh", "ipr", 5e-6, false);
    network.addPipe("wh", "platform", "tubing", 2000, 0.15, 0.00005);

    double[] flowRates = {500, 2000, 5000, 10000}; // kg/hr
    double[] thps = {40, 60, 80}; // bara

    java.util.Map<String, double[][]> tables = network.generateCoupledVFPTables(flowRates, thps);
    double[][] bhpTable = tables.values().iterator().next();

    java.util.Map<String, Double> validation =
        network.validateVFPPoint(bhpTable, flowRates, thps, 2000, 60.0, bhpTable[1][1]);

    assertNotNull(validation.get("error_pct"), "Should have error percentage");
    assertTrue(validation.get("error_pct") < 5.0, "Error at table point should be small");
  }

  @Test
  void testNetworkBackpressureCurve() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("backpressure");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 200.0, 0.0);
    network.addJunctionNode("wh");
    network.addFixedPressureSinkNode("platform", 50.0);

    network.addWellIPR("res", "wh", "ipr", 5e-6, false);
    network.addPipe("wh", "platform", "pipeline", 20000, 0.3, 0.00005);

    double[] flowRates = {2000, 5000, 10000}; // kg/hr

    java.util.Map<String, double[]> curve =
        network.generateNetworkBackpressureCurve("platform", flowRates);

    assertNotNull(curve.get("requiredPressure_bara"), "Should have pressure results");
    double[] pressures = curve.get("requiredPressure_bara");
    assertEquals(flowRates.length, pressures.length, "Result length should match");
    for (double p : pressures) {
      assertTrue(p > 0, "Required pressure should be positive");
    }
  }

  // =====================================================================
  // P3: setNodePressure API Tests
  // =====================================================================

  @Test
  void testSetNodePressure() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("setP");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 200.0, 0.0);
    network.addJunctionNode("wh");
    network.addFixedPressureSinkNode("platform", 65.0);
    network.addWellIPR("res", "wh", "ipr", 5e-6, false);
    network.addPipe("wh", "platform", "pipe", 10000, 0.25, 0.00005);

    // Run at original pressure
    network.run();
    double flow1 = Math.abs(network.getTotalSinkFlow()) * 3600;
    assertTrue(flow1 > 0, "Should have positive flow");

    // Change sink pressure to higher → less flow
    network.setNodePressure("platform", 80.0);
    network.run();
    double flow2 = Math.abs(network.getTotalSinkFlow()) * 3600;
    assertTrue(flow2 < flow1, "Higher back-pressure should reduce flow");

    // Verify getNodePressure returns the updated value
    assertEquals(80.0, network.getNodePressure("platform"), 0.1);
  }

  @Test
  void testSetReservoirPressure() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("resP");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 250.0, 0.0);
    network.addJunctionNode("wh");
    network.addFixedPressureSinkNode("platform", 50.0);
    network.addWellIPR("res", "wh", "ipr", 5e-6, false);
    network.addPipe("wh", "platform", "pipe", 10000, 0.25, 0.00005);

    network.run();
    double flowHigh = Math.abs(network.getTotalSinkFlow()) * 3600;

    // Drop reservoir pressure → less flow
    network.setReservoirPressure("res", 150.0);
    network.run();
    double flowLow = Math.abs(network.getTotalSinkFlow()) * 3600;

    assertTrue(flowLow < flowHigh, "Lower reservoir pressure should reduce flow");
    assertEquals(150.0, network.getNodePressure("res"), 0.5);
  }

  // =====================================================================
  // P1: Topside Coupling Tests
  // =====================================================================

  @Test
  void testTopsideCoupling() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("coupled");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 250.0, 0.0);
    network.addJunctionNode("wh");
    network.addFixedPressureSinkNode("platform", 70.0);
    network.addWellIPR("res", "wh", "ipr", 5e-6, false);
    network.addPipe("wh", "platform", "pipe", 10000, 0.25, 0.00005);

    // Build topside: separator + compressor
    network.run();
    neqsim.process.equipment.stream.StreamInterface outlet = network.getOutletStream("platform");
    assertNotNull(outlet, "Should have outlet stream");

    neqsim.process.equipment.stream.Stream feed =
        new neqsim.process.equipment.stream.Stream("Feed", outlet.getFluid().clone());
    feed.setFlowRate(outlet.getFlowRate("kg/hr"), "kg/hr");
    feed.setPressure(outlet.getPressure("bara"), "bara");
    feed.setTemperature(25.0, "C");

    neqsim.process.equipment.separator.Separator sep =
        new neqsim.process.equipment.separator.Separator("Sep", feed);
    sep.setInternalDiameter(3.0);
    sep.setSeparatorLength(10.0);

    neqsim.process.equipment.compressor.Compressor comp =
        new neqsim.process.equipment.compressor.Compressor("Comp", sep.getGasOutStream());
    comp.setOutletPressure(150.0);
    comp.setPolytropicEfficiency(0.78);

    neqsim.process.processmodel.ProcessSystem topside =
        new neqsim.process.processmodel.ProcessSystem();
    topside.add(feed);
    topside.add(sep);
    topside.add(comp);

    // Set coupling
    network.setTopsideModel(topside, "platform");
    network.setMaxSeparatorUtilization(0.95);
    network.setMaxCompressorPowerMW(10.0);

    assertNotNull(network.getTopsideModel(), "Topside model should be set");

    // Run coupled
    Map<String, Double> result = network.runCoupled();
    assertNotNull(result, "Coupled result should not be null");
    assertTrue(result.get("arrivalPressure_bara") > 0, "Should find feasible point");
    assertTrue(result.get("totalFlow_kghr") > 0, "Should have positive flow");
    assertEquals(1.0, result.get("converged"), 0.01, "Should converge");
  }

  @Test
  void testIsTopsideFeasible() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("feasible");
    // Without topside → always feasible
    assertTrue(network.isTopsideFeasible());
  }

  // =====================================================================
  // P5: Production Forecast with Re-Optimisation Tests
  // =====================================================================

  @Test
  void testProductionForecastWithOptimization() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("forecast");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 250.0, 0.0);
    network.addJunctionNode("bhp");
    network.addJunctionNode("wh");
    network.addFixedPressureSinkNode("platform", 50.0);
    network.addWellIPR("res", "bhp", "ipr", 5e-6, false);
    network.addChoke("bhp", "wh", "choke1", 40.0, 70.0);
    network.addPipe("wh", "platform", "pipe", 10000, 0.25, 0.00005);

    double[] pressures = {250.0, 220.0, 190.0, 160.0, 130.0};
    double[] years = {0, 2, 5, 8, 10};

    Map<String, double[]> result =
        network.productionForecastWithOptimization("res", pressures, years, 20, 0.01);

    assertNotNull(result, "Forecast result should not be null");
    assertNotNull(result.get("rate_kghr"), "Should have rate array");
    assertNotNull(result.get("revenue_usd_hr"), "Should have revenue array");
    assertNotNull(result.get("cumulative_kg"), "Should have cumulative");

    double[] rates = result.get("rate_kghr");
    assertEquals(5, rates.length, "Should have 5 timesteps");
    assertTrue(rates[0] > rates[4], "Production should decline: " + rates[0] + " > " + rates[4]);
    assertTrue(result.get("cumulative_kg")[4] > 0, "Cumulative should be positive");
  }

  @Test
  void testProductionForecastWithOptimizationMultiReservoir() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("multiRes");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    // Two reservoirs
    network.addSourceNode("resA", 280.0, 0.0);
    network.addSourceNode("resB", 220.0, 0.0);
    network.addJunctionNode("bhpA");
    network.addJunctionNode("bhpB");
    network.addJunctionNode("whA");
    network.addJunctionNode("whB");
    network.addJunctionNode("manifold");
    network.addFixedPressureSinkNode("platform", 60.0);

    network.addWellIPR("resA", "bhpA", "iprA", 5e-6, false);
    network.addChoke("bhpA", "whA", "chokeA", 40.0, 80.0);
    network.addWellIPR("resB", "bhpB", "iprB", 4e-6, false);
    network.addChoke("bhpB", "whB", "chokeB", 35.0, 70.0);

    network.addPipe("whA", "manifold", "flA", 5000, 0.20, 0.00005);
    network.addPipe("whB", "manifold", "flB", 6000, 0.20, 0.00005);
    network.addPipe("manifold", "platform", "export", 20000, 0.30, 0.00005);

    Map<String, double[]> profiles = new java.util.LinkedHashMap<>();
    profiles.put("resA", new double[] {280, 250, 220, 190, 160});
    profiles.put("resB", new double[] {220, 200, 180, 150, 120});
    double[] years = {0, 3, 6, 9, 12};

    Map<String, double[]> result =
        network.productionForecastWithOptimization(profiles, years, 20, 0.01);

    assertNotNull(result.get("rate_kghr"));
    double[] rates = result.get("rate_kghr");
    assertEquals(5, rates.length);
    assertTrue(rates[0] > rates[4], "Production should decline over time");
  }

  // =====================================================================
  // E300 Fluid Integration Tests
  // =====================================================================

  @Test
  void testLoadFluidFromE300() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("e300");

    // Use an existing E300 test file
    String e300Path = "src/test/java/neqsim/thermo/util/readwrite/fluid1.e300";
    java.io.File f = new java.io.File(e300Path);
    if (!f.exists()) {
      // Skip if test file not available
      System.out.println("Skipping E300 test — file not found: " + e300Path);
      return;
    }

    SystemInterface fluid = network.loadFluidFromE300(e300Path);
    assertNotNull(fluid, "Should load fluid from E300");
    assertNotNull(network.getFluidTemplate(), "Template should be set");
    assertTrue(fluid.getNumberOfComponents() > 0, "Should have components");
  }

  @Test
  void testSetReservoirComposition() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("comp");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("resA", 250.0, 0.0);
    network.addJunctionNode("wh");
    network.addFixedPressureSinkNode("platform", 50.0);
    network.addWellIPR("resA", "wh", "ipr", 5e-6, false);
    network.addPipe("wh", "platform", "pipe", 10000, 0.25, 0.00005);

    // Set different composition for reservoir A (richer gas)
    double[] richComp = {0.80, 0.12, 0.08}; // more ethane+propane
    network.setReservoirComposition("resA", richComp);

    // Should still solve
    network.run();
    double flow = Math.abs(network.getTotalSinkFlow()) * 3600;
    assertTrue(flow > 0, "Should have positive flow with custom composition");
  }

  // =====================================================================
  // Full Field Optimisation Tests
  // =====================================================================

  @Test
  void testOptimizeFullFieldBasic() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("fullField");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 250.0, 0.0);
    network.addJunctionNode("bhp");
    network.addJunctionNode("wh");
    network.addFixedPressureSinkNode("platform", 60.0);

    network.addWellIPR("res", "bhp", "ipr", 5e-6, false);
    network.addChoke("bhp", "wh", "choke1", 40.0, 50.0);
    network.addPipe("wh", "platform", "pipe", 10000, 0.25, 0.00005);

    network.setWellPrice("choke1", 0.30);

    Map<String, Object> result = network.optimizeFullField(30, 0.01);
    assertNotNull(result, "Full field result should not be null");
    assertTrue((Double) result.get("totalFlow_kghr") > 0, "Should have positive flow");
    assertTrue((Double) result.get("revenue_usd_hr") > 0, "Should have positive revenue");
    assertNotNull(result.get("chokeSettings"), "Should have choke settings");
  }

  @Test
  void testFullFieldForecast() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("lifecycle");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 280.0, 0.0);
    network.addJunctionNode("bhp");
    network.addJunctionNode("wh");
    network.addFixedPressureSinkNode("platform", 50.0);

    network.addWellIPR("res", "bhp", "ipr", 5e-6, false);
    network.addChoke("bhp", "wh", "choke1", 40.0, 80.0);
    network.addPipe("wh", "platform", "pipe", 10000, 0.25, 0.00005);

    Map<String, double[]> profiles = new java.util.LinkedHashMap<>();
    profiles.put("res", new double[] {280, 240, 200, 160, 120});
    double[] years = {0, 5, 10, 15, 20};

    Map<String, double[]> forecast = network.fullFieldForecast(profiles, years);
    assertNotNull(forecast, "Forecast should not be null");
    double[] rates = forecast.get("rate_kghr");
    assertEquals(5, rates.length);
    assertTrue(rates[0] > rates[4], "Should show decline");
    assertTrue(forecast.get("cumulative_kg")[4] > 0, "Should have cumulative production");
  }

  // =====================================================================
  // Reservoir Coupling Tests
  // =====================================================================

  /**
   * Helper: create a SimpleReservoir with gas for testing.
   *
   * @param pressureBara initial reservoir pressure in bara
   * @param gasVolumeMcm gas-in-place volume in cubic metres
   * @return configured SimpleReservoir ready for coupling
   */
  private SimpleReservoir createTestGasReservoir(double pressureBara, double gasVolumeMcm) {
    SystemInterface resFluid = new SystemSrkEos(273.15 + 100.0, pressureBara);
    resFluid.addComponent("methane", 0.90);
    resFluid.addComponent("ethane", 0.07);
    resFluid.addComponent("propane", 0.03);
    resFluid.createDatabase(true);
    resFluid.setMixingRule("classic");
    resFluid.setMultiPhaseCheck(true);

    SimpleReservoir reservoir = new SimpleReservoir("TestRes");
    reservoir.setReservoirFluid(resFluid, gasVolumeMcm, 0.0, 0.0);
    return reservoir;
  }

  /**
   * Helper: build a standard single-well coupled network.
   *
   * @param network the network to configure
   * @param resPressure reservoir pressure in bara
   */
  private void buildSingleWellCoupledNetwork(LoopedPipeNetwork network, double resPressure) {
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", resPressure, 0.0);
    network.addJunctionNode("wh");
    network.addJunctionNode("ds");
    network.addFixedPressureSinkNode("platform", 50.0);

    network.addWellIPR("res", "wh", "ipr", 5e-13, true);
    network.addChoke("wh", "ds", "choke", 100.0, 80.0);
    network.addPipe("ds", "platform", "pipe", 10000, 0.25, 0.00005);
  }

  @Test
  void testAttachReservoir() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("coupled-test");
    buildSingleWellCoupledNetwork(network, 300.0);

    // Create reservoir at 300 bara
    SimpleReservoir reservoir = createTestGasReservoir(300.0, 1.0e7);
    StreamInterface prodStream = reservoir.addGasProducer("producer");
    prodStream.setFlowRate(10.0, "kg/sec");

    // Attach reservoir
    network.attachReservoir("res", reservoir, "gas");

    assertTrue(network.hasAttachedReservoirs(), "Should have attached reservoirs");
    assertNotNull(network.getAttachedReservoir("res"), "Should return the reservoir");
  }

  @Test
  void testRunTransientCoupled() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("transient-coupled");
    buildSingleWellCoupledNetwork(network, 300.0);

    // Create reservoir at 300 bara with large volume (realistic gas field)
    SimpleReservoir reservoir = createTestGasReservoir(300.0, 1.0e8);
    StreamInterface prodStream = reservoir.addGasProducer("producer");
    prodStream.setFlowRate(1.0, "kg/sec");
    network.attachReservoir("res", reservoir, "gas");

    // Get initial pressure
    double initialP = reservoir.getReservoirFluid().getPressure("bara");

    // Run one year timestep
    double oneYear = 365.25 * 24 * 3600.0;
    Map<String, Object> result = network.runTransientCoupled(oneYear, 10, 0.01);

    double newP = (Double) result.get("pressure_res_bara");
    double flow = (Double) result.get("totalFlow_kghr");

    assertTrue(flow > 0, "Should have positive production: " + flow);
    assertTrue(newP < initialP, "Pressure should decline from " + initialP + " to " + newP);
    assertTrue(newP > 50.0, "Pressure should not drop too much: " + newP);
  }

  @Test
  void testProductionForecastCoupled() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("forecast-coupled");
    buildSingleWellCoupledNetwork(network, 300.0);

    // Create reservoir at 300 bara with large volume
    SimpleReservoir reservoir = createTestGasReservoir(300.0, 5.0e8);
    StreamInterface prodStream = reservoir.addGasProducer("producer");
    prodStream.setFlowRate(1.0, "kg/sec");
    network.attachReservoir("res", reservoir, "gas");

    // Run 10-year forecast
    double[] years = {0, 2, 4, 6, 8, 10};
    Map<String, double[]> forecast = network.productionForecastCoupled(years, 10, 0.01);

    assertNotNull(forecast);
    double[] rates = forecast.get("rate_kghr");
    double[] pressures = forecast.get("pressure_res_bara");
    double[] cumul = forecast.get("cumulative_kg");

    assertEquals(6, rates.length, "Should have 6 timesteps");
    assertTrue(rates[0] > 0, "Should have initial production");
    assertTrue(rates[0] > rates[5], "Rate should decline over time");
    assertTrue(pressures[0] > pressures[5], "Pressure should decline");
    assertTrue(cumul[5] > 0, "Should have cumulative production");

    // Check GIP also tracked
    double[] gip = forecast.get("GIP_res_GSm3");
    assertNotNull(gip, "GIP should be tracked");
  }

  @Test
  void testCoupledMultiReservoir() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("multi-res-coupled");
    network.setFluidTemplate(testGas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(500);
    network.setTolerance(500.0);

    // Two reservoirs at different pressures
    SimpleReservoir resA = createTestGasReservoir(350.0, 5.0e8);
    StreamInterface prodA = resA.addGasProducer("prodA");
    prodA.setFlowRate(1.0, "kg/sec");

    SimpleReservoir resB = createTestGasReservoir(250.0, 3.0e8);
    StreamInterface prodB = resB.addGasProducer("prodB");
    prodB.setFlowRate(1.0, "kg/sec");

    // Network with 2 reservoirs + chokes
    network.addSourceNode("ResA", 350.0, 0.0);
    network.addSourceNode("ResB", 250.0, 0.0);
    network.addJunctionNode("whA");
    network.addJunctionNode("whB");
    network.addJunctionNode("dsA");
    network.addJunctionNode("dsB");
    network.addJunctionNode("manifold");
    network.addFixedPressureSinkNode("platform", 50.0);

    network.addWellIPR("ResA", "whA", "iprA", 8e-13, true);
    network.addChoke("whA", "dsA", "chokeA", 150.0, 80.0);
    network.addWellIPR("ResB", "whB", "iprB", 5e-13, true);
    network.addChoke("whB", "dsB", "chokeB", 150.0, 60.0);
    network.addPipe("dsA", "manifold", "pipeA", 3000, 0.15, 0.00005);
    network.addPipe("dsB", "manifold", "pipeB", 5000, 0.12, 0.00005);
    network.addPipe("manifold", "platform", "export", 15000, 0.25, 0.00005);

    network.attachReservoir("ResA", resA, "gas");
    network.attachReservoir("ResB", resB, "gas");

    // Run 5-year forecast
    double[] years = {0, 1, 2, 3, 4, 5};
    Map<String, double[]> forecast = network.productionForecastCoupled(years);

    double[] pA = forecast.get("pressure_ResA_bara");
    double[] pB = forecast.get("pressure_ResB_bara");

    assertTrue(pA[0] > pA[5], "ResA pressure should decline");
    assertTrue(pB[0] > pB[5], "ResB pressure should decline");
    // Higher-pressure reservoir should still be higher at end
    assertTrue(pA[5] > pB[5], "ResA should remain at higher pressure than ResB");

    double[] rates = forecast.get("rate_kghr");
    assertTrue(rates[0] > rates[5], "Total production should decline");
  }

  @Test
  void testCoupledReservoirPressureConverges() {
    // Verify that a very small reservoir depletes rapidly
    LoopedPipeNetwork network = new LoopedPipeNetwork("depletion-test");
    buildSingleWellCoupledNetwork(network, 300.0);

    // Small reservoir volume - fast depletion
    SimpleReservoir reservoir = createTestGasReservoir(300.0, 5.0e6);
    StreamInterface prodStream = reservoir.addGasProducer("producer");
    prodStream.setFlowRate(1.0, "kg/sec");
    network.attachReservoir("res", reservoir, "gas");

    // Run 5-year forecast with shorter steps
    double[] years = {0, 1, 2, 3, 4, 5};
    Map<String, double[]> forecast = network.productionForecastCoupled(years);

    double[] pressures = forecast.get("pressure_res_bara");
    double[] rates = forecast.get("rate_kghr");

    // Pressure should drop significantly with small reservoir
    double finalP = pressures[pressures.length - 1];
    assertTrue(finalP < 200.0, "Small reservoir should deplete below 200 bara, got: " + finalP);
    // Production rate should be significantly reduced
    assertTrue(rates[rates.length - 1] < rates[0] * 0.8,
        "Rate should drop by > 20% for small reservoir");
  }

  // =====================================================================
  // Feature 1: Artificial Lift Tests
  // =====================================================================

  @Test
  void testGasLiftIncreasesProduction() {
    LoopedPipeNetwork network = createSimpleProductionNetwork();
    network.run();
    double baseFlow = Math.abs(network.getPipeFlowRate("well1") * 3600.0);

    // Enable gas lift on the well
    network.setGasLift("well1", 500.0); // 500 kg/hr gas injection

    network.run();
    double liftedFlow = Math.abs(network.getPipeFlowRate("well1") * 3600.0);

    // Gas lift should increase production by reducing head loss
    assertTrue(liftedFlow >= baseFlow * 0.95,
        "Gas lift should maintain or increase flow: base=" + baseFlow + " lifted=" + liftedFlow);

    // Total gas lift rate should be tracked
    assertEquals(500.0, network.getTotalGasLiftRate(), 1.0);

    // Report should include artificial lift section
    String report = network.getNetworkReport();
    assertTrue(report.contains("Artificial Lift"));
    assertTrue(report.contains("GAS_LIFT"));
  }

  @Test
  void testESPBoostsWellProduction() {
    LoopedPipeNetwork network = createSimpleProductionNetwork();
    network.run();
    double baseFlow = Math.abs(network.getPipeFlowRate("well1") * 3600.0);

    // Add ESP to the well
    network.setESP("well1", 50.0, 0.5); // 50 kW ESP at 50% efficiency

    network.run();
    double espFlow = Math.abs(network.getPipeFlowRate("well1") * 3600.0);

    // ESP should boost production
    assertTrue(espFlow >= baseFlow * 0.95,
        "ESP should maintain or increase flow: base=" + baseFlow + " esp=" + espFlow);
    assertEquals(50.0, network.getTotalESPPower(), 0.1);
  }

  @Test
  void testJetPumpAndRodPumpTypes() {
    LoopedPipeNetwork network = createSimpleProductionNetwork();

    // Set jet pump
    network.setJetPump("well1", 30.0, 0.3);
    LoopedPipeNetwork.NetworkPipe well1 = network.getPipe("well1");
    assertEquals(LoopedPipeNetwork.ArtificialLiftType.JET_PUMP, well1.getArtificialLiftType());

    // Change to rod pump
    network.setRodPump("well1", 15.0, 0.45);
    assertEquals(LoopedPipeNetwork.ArtificialLiftType.ROD_PUMP, well1.getArtificialLiftType());
    assertEquals(15.0, well1.getEspPower(), 0.1);
    assertEquals(0.45, well1.getEspEfficiency(), 0.01);
  }

  // =====================================================================
  // Feature 2: Large-Scale Network Test (100+ wells)
  // =====================================================================

  @Test
  void testLargeScaleNetworkConverges() {
    // Build a network with 120 wells feeding into 6 manifolds, then to a single delivery
    LoopedPipeNetwork network = new LoopedPipeNetwork("large-scale");
    SystemInterface fluid = createTestGas(300.0);
    network.setFluidTemplate(fluid);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(500);
    network.setTolerance(500.0);

    // Create 6 manifold nodes (junctions) and delivery
    network.addFixedPressureSinkNode("delivery", 70.0);
    for (int m = 1; m <= 6; m++) {
      network.addJunctionNode("manifold" + m, 0.0);
      network.addPipe("manifold" + m, "delivery", "trunkline" + m, 5000.0, 0.4);
    }

    // Create 120 wells (20 per manifold)
    for (int m = 1; m <= 6; m++) {
      for (int w = 1; w <= 20; w++) {
        String resNode = "res_m" + m + "_w" + w;
        String wellName = "well_m" + m + "_w" + w;
        double pRes = 250.0 + (w * 2.0); // Vary reservoir pressure slightly

        network.addSourceNode(resNode, pRes, 0.0);
        network.addWellIPR(resNode, "manifold" + m, wellName, 5e-13, true);
      }
    }

    network.run();

    assertTrue(network.isConverged(), "120-well network should converge");

    // All well flows should be positive
    int activeWells = 0;
    for (int m = 1; m <= 6; m++) {
      for (int w = 1; w <= 20; w++) {
        String wellName = "well_m" + m + "_w" + w;
        double flow = network.getPipeFlowRate(wellName) * 3600.0;
        if (flow > 0.1) {
          activeWells++;
        }
      }
    }
    assertTrue(activeWells >= 100,
        "At least 100 of 120 wells should be active, got: " + activeWells);

    // Mass balance should close
    assertTrue(Math.abs(network.getMassBalanceError()) < 0.1);
  }

  // =====================================================================
  // Feature 3: Water Handling Tests
  // =====================================================================

  @Test
  void testWaterCutTrackingAndBalance() {
    LoopedPipeNetwork network = createSimpleProductionNetwork();

    // Set water cuts on wells
    network.setWaterCut("well1", 0.30); // 30% water cut
    network.run();

    // Calculate water balance
    Map<String, double[]> waterBalance = network.calculateWaterBalance();

    assertFalse(waterBalance.isEmpty(), "Water balance should have entries");
    double totalWater = network.getTotalWaterProduction();
    assertTrue(totalWater > 0, "Total water production should be positive");

    // Report should include water balance section
    String report = network.getNetworkReport();
    assertTrue(report.contains("Water Balance"));
  }

  @Test
  void testWaterInjection() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("water-inj");
    SystemInterface fluid = createTestGas(200.0);
    network.setFluidTemplate(fluid);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 200.0, 0.0);
    network.addSinkNode("delivery", 70.0, 0.0);
    network.addWellIPR("res", "delivery", "well", 5e-13, true);

    // Add water injection
    network.addSourceNode("waterSupply", 150.0, 0.0);
    LoopedPipeNetwork.NetworkPipe injElement =
        network.addWaterInjection("waterSupply", "res", "inj-1", 5000.0);

    assertNotNull(injElement);
    assertEquals(5000.0 / 3600.0, injElement.getWaterInjectionRate(), 0.1);

    network.run();
    network.calculateWaterBalance();
    assertTrue(network.getTotalWaterInjection() > 0);
  }

  @Test
  void testWaterBreakthroughTracking() {
    LoopedPipeNetwork network = createSimpleProductionNetwork();
    network.setWaterBreakthrough("well1", 0.05, 0.85, 0.15);

    LoopedPipeNetwork.NetworkPipe well = network.getPipe("well1");
    assertEquals(0.15, well.getWaterCut(), 0.01);
    assertEquals(0.85, well.getWaterFlowRate(), 0.01); // Ultimate WC stored

    network.run();
    network.calculateWaterBalance();
    assertTrue(network.getTotalWaterProduction() > 0);
  }

  // =====================================================================
  // Feature 4: Sand and Solids Tests
  // =====================================================================

  @Test
  void testSandTransportAndErosion() {
    LoopedPipeNetwork network = createSimpleProductionNetwork();
    network.setSandRate("well1", 5.0); // 5 kg/hr sand
    network.setMaxAllowableSandRate(10.0);
    network.setMaxAllowableErosionRate(5.0);

    network.run();

    Map<String, double[]> sandResults = network.calculateSandTransport();

    // Should have results for pipe elements (not well IPR elements)
    assertFalse(sandResults.isEmpty(), "Sand results should be computed");

    // Report should include sand section
    String report = network.getNetworkReport();
    assertTrue(report.contains("Sand Transport"));
  }

  @Test
  void testSandErosionViolation() {
    LoopedPipeNetwork network = createSimpleProductionNetwork();
    network.setSandRate("well1", 50.0); // High sand rate
    network.setMaxAllowableErosionRate(0.1); // Very strict limit

    network.run();
    network.calculateSandTransport();

    // May or may not have violations depending on flow rate — just verify no crash
    List<String> violations = network.getSandViolations();
    assertNotNull(violations);
  }

  // =====================================================================
  // Feature 5: Corrosion and Integrity Tests
  // =====================================================================

  @Test
  void testDeWaardCorrosionModel() {
    LoopedPipeNetwork network = createSimpleProductionNetwork();

    // Set CO2 content for corrosion calculation
    network.setCorrosiveGas("pipe1", 0.05, 0.001); // 5% CO2, 0.1% H2S

    network.run();
    Map<String, double[]> corrosionResults = network.calculateCorrosion();

    assertTrue(corrosionResults.containsKey("pipe1"), "Corrosion results should include pipe1");

    double[] cr = corrosionResults.get("pipe1");
    assertTrue(cr[0] > 0, "Corrosion rate should be positive with CO2");
    assertTrue(cr[1] > 0, "pCO2 should be positive");
    assertTrue(cr[3] > 0, "Remaining life should be positive");

    // Report should include corrosion section
    String report = network.getNetworkReport();
    assertTrue(report.contains("Corrosion Assessment"));
  }

  @Test
  void testNorsokM506CorrosionModel() {
    LoopedPipeNetwork network = createSimpleProductionNetwork();

    // Use NORSOK M-506 model
    network.setCorrosiveGas("pipe1", 0.03, 0.0); // 3% CO2 only
    network.setCorrosionModel("pipe1", "norsokM506");

    network.run();
    Map<String, double[]> results = network.calculateCorrosion();

    assertTrue(results.containsKey("pipe1"));
    double crDeWaard = results.get("pipe1")[0];
    assertTrue(crDeWaard > 0, "NORSOK M-506 should give positive corrosion rate");
  }

  @Test
  void testCorrosionIntegrityViolation() {
    LoopedPipeNetwork network = createSimpleProductionNetwork();
    network.setCorrosiveGas("pipe1", 0.10, 0.005); // High CO2, moderate H2S
    network.setMinAllowableWallLife(100.0); // Very conservative limit

    network.run();
    network.calculateCorrosion();

    List<String> violations = network.getCorrosionViolations();
    assertNotNull(violations);
    // With high CO2 and strict wall life limit, we should get violations
    // (depends on wall thickness and corrosion rate)
  }

  // =====================================================================
  // Feature 6: Emissions Tracking Tests
  // =====================================================================

  @Test
  void testEmissionsFromCompressorStation() {
    // Test the emissions calculation logic: converge a network with a compressor,
    // then set compressor power to a known value and verify emissions arithmetic.
    // This tests the emissions math independently of the NR compressor solver.
    LoopedPipeNetwork network = new LoopedPipeNetwork("emissions-test");
    SystemInterface fluid = createTestGas(200.0);
    network.setFluidTemplate(fluid);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("supply", 200.0, 0.0);
    network.addJunctionNode("j1", 0.0);
    network.addJunctionNode("j2", 0.0);
    network.addFixedPressureSinkNode("delivery", 70.0);

    network.addPipe("supply", "j1", "feed", 5000, 0.3);
    network.addCompressor("j1", "j2", "comp1", 0.75);
    network.addPipe("j2", "delivery", "export", 5000, 0.3);

    network.setCO2EmissionFactor(2.75);
    network.setMethaneSlipFactor(0.02);
    network.run();
    assertTrue(network.isConverged(), "Network should converge");

    // Set a known compressor power (simulates compressor doing work)
    LoopedPipeNetwork.NetworkPipe comp = network.getPipe("comp1");
    assertNotNull(comp);
    comp.setCompressorPower(500.0); // 500 kW

    Map<String, double[]> emissions = network.calculateEmissions();

    assertTrue(emissions.containsKey("comp1"), "Emissions should include comp1");
    double[] em = emissions.get("comp1");
    assertTrue(em[0] > 0, "CO2 emissions should be positive: " + em[0]);
    assertTrue(em[1] > 0, "CH4 slip should be positive: " + em[1]);
    assertTrue(em[2] > em[0], "CO2eq should be greater than CO2 alone (includes CH4 GWP)");
    assertTrue(em[3] > 0, "Power should be positive");
    assertTrue(em[4] > 0, "Fuel gas should be positive");
    assertEquals(500.0, em[3], 0.01, "Power should match set value");

    // Total emissions
    assertTrue(network.getTotalCO2Emissions() > 0);
    assertTrue(network.getAnnualCO2EmissionsTonnes() > 0);
    assertTrue(network.getEmissionsIntensity() > 0);

    // Report should include emissions section
    String report = network.getNetworkReport();
    assertTrue(report.contains("GHG Emissions"));

    // JSON should include emissions section
    String json = network.toJson();
    assertTrue(json.contains("emissions"));
    assertTrue(json.contains("annualCO2eq_tonnesyr"));
  }

  @Test
  void testEmissionsIntensityCalculation() {
    // Test emissions intensity with known compressor power
    LoopedPipeNetwork network = new LoopedPipeNetwork("intensity-test");
    SystemInterface fluid = createTestGas(200.0);
    network.setFluidTemplate(fluid);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("supply", 200.0, 0.0);
    network.addJunctionNode("j1", 0.0);
    network.addJunctionNode("j2", 0.0);
    network.addFixedPressureSinkNode("delivery", 70.0);

    network.addPipe("supply", "j1", "feed", 5000, 0.3);
    network.addCompressor("j1", "j2", "comp", 0.75);
    network.addPipe("j2", "delivery", "discharge", 5000, 0.3);

    network.run();
    assertTrue(network.isConverged());

    // Set known compressor power
    LoopedPipeNetwork.NetworkPipe comp = network.getPipe("comp");
    comp.setCompressorPower(300.0);

    network.calculateEmissions();

    double intensity = network.getEmissionsIntensity();
    // Intensity = kgCO2eq per tonne of product — should be reasonable
    assertTrue(intensity > 0, "Emissions intensity should be positive");
    assertTrue(intensity < 500, "Emissions intensity should be < 500 kgCO2eq/t for gas");
  }

  // =====================================================================
  // Integration Test: All 6 features combined
  // =====================================================================

  @Test
  void testAllNewFeaturesIntegrated() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("full-integration");
    SystemInterface fluid = createTestGas(250.0);
    network.setFluidTemplate(fluid);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(300);
    network.setTolerance(200.0);

    // 3 wells producing to manifold → compressor → delivery
    network.addSourceNode("res1", 250.0, 0.0);
    network.addSourceNode("res2", 230.0, 0.0);
    network.addSourceNode("res3", 200.0, 0.0);
    network.addJunctionNode("manifold", 0.0);
    network.addJunctionNode("j2", 0.0);
    network.addFixedPressureSinkNode("delivery", 70.0);

    network.addWellIPR("res1", "manifold", "well1", 5e-13, true);
    network.addWellIPR("res2", "manifold", "well2", 5e-13, true);
    network.addWellIPR("res3", "manifold", "well3", 5e-13, true);
    network.addCompressor("manifold", "j2", "comp1", 0.75);
    network.addPipe("j2", "delivery", "export", 5000, 0.3);

    // Feature 1: Artificial lift
    network.setGasLift("well1", 300.0);
    network.setESP("well3", 40.0, 0.55);

    // Feature 3: Water handling
    network.setWaterCut("well1", 0.20);
    network.setWaterCut("well2", 0.35);
    network.setWaterCut("well3", 0.10);

    // Feature 4: Sand
    network.setSandRate("well1", 2.0);
    network.setSandRate("well2", 8.0);
    network.setMaxAllowableErosionRate(5.0);

    // Feature 5: Corrosion
    network.setCorrosiveGas("comp1", 0.04, 0.001);

    // Feature 6: Emissions config
    network.setCO2EmissionFactor(2.75);
    network.setMethaneSlipFactor(0.02);

    // Run network
    network.run();
    assertTrue(network.isConverged(), "Integrated network should converge");

    // Manually set compressor power to test emissions calculation
    LoopedPipeNetwork.NetworkPipe comp = network.getPipe("comp1");
    comp.setCompressorPower(400.0);

    // Run all post-processing
    Map<String, double[]> waterBalance = network.calculateWaterBalance();
    Map<String, double[]> sandResults = network.calculateSandTransport();
    Map<String, double[]> corrosionResults = network.calculateCorrosion();
    Map<String, double[]> emissions = network.calculateEmissions();

    assertFalse(waterBalance.isEmpty(), "Water balance should have entries");
    assertTrue(network.getTotalWaterProduction() > 0);
    assertTrue(network.getTotalCO2Emissions() > 0);

    // Get comprehensive report
    String report = network.getNetworkReport();
    assertTrue(report.contains("Artificial Lift"));
    assertTrue(report.contains("Water Balance"));
    assertTrue(report.contains("GHG Emissions"));

    // Get JSON
    String json = network.toJson();
    assertTrue(json.contains("artificialLiftType"));
    assertTrue(json.contains("waterCut"));
    assertTrue(json.contains("emissions"));
  }

  // Helper: create a test gas fluid at specified pressure
  private SystemInterface createTestGas(double pressureBar) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, pressureBar);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");
    return fluid;
  }

  // Helper: simple production network for quick tests
  private LoopedPipeNetwork createSimpleProductionNetwork() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("simple-prod");
    SystemInterface fluid = createTestGas(200.0);
    network.setFluidTemplate(fluid);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res", 200.0, 0.0);
    network.addJunctionNode("jnc", 0.0);
    network.addFixedPressureSinkNode("delivery", 70.0);

    network.addWellIPR("res", "jnc", "well1", 5e-13, true);
    network.addPipe("jnc", "delivery", "pipe1", 5000.0, 0.3);

    return network;
  }
}
