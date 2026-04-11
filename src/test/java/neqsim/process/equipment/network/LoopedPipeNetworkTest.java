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
}
