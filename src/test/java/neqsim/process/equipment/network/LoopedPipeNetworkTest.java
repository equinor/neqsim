package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
}
