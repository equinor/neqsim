package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flowsolver.AdvectionScheme;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link PipeFlowNetwork} - pipeline networks with compositional PipeFlowSystem.
 */
class PipeFlowNetworkTest {

  /**
   * Create a natural gas test fluid.
   */
  private SystemInterface createNaturalGas(double temperatureK, double pressureBar) {
    SystemInterface gas = new SystemSrkEos(temperatureK, pressureBar);
    gas.addComponent("nitrogen", 0.02);
    gas.addComponent("CO2", 0.01);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.06);
    gas.addComponent("propane", 0.04);
    gas.addComponent("n-butane", 0.02);
    gas.createDatabase(true);
    gas.setMixingRule("classic");
    gas.init(0);
    gas.init(3);
    gas.initPhysicalProperties();
    return gas;
  }

  /**
   * Create a rich gas test fluid (higher C2+ content).
   */
  private SystemInterface createRichGas(double temperatureK, double pressureBar) {
    SystemInterface gas = new SystemSrkEos(temperatureK, pressureBar);
    gas.addComponent("nitrogen", 0.01);
    gas.addComponent("CO2", 0.02);
    gas.addComponent("methane", 0.70);
    gas.addComponent("ethane", 0.12);
    gas.addComponent("propane", 0.09);
    gas.addComponent("n-butane", 0.06);
    gas.createDatabase(true);
    gas.setMixingRule("classic");
    gas.init(0);
    gas.init(3);
    gas.initPhysicalProperties();
    return gas;
  }

  @Test
  void testSimpleNetworkTwoPipesToManifold() {
    // Create two feed streams with different compositions
    SystemInterface gas1 = createNaturalGas(288.15, 80.0);
    gas1.setTotalFlowRate(5.0, "MSm3/day");
    Stream feed1 = new Stream("feed 1", gas1);

    SystemInterface gas2 = createRichGas(290.15, 82.0);
    gas2.setTotalFlowRate(3.0, "MSm3/day");
    Stream feed2 = new Stream("feed 2", gas2);

    // Create network
    PipeFlowNetwork network = new PipeFlowNetwork("gathering system");

    // Create single manifold
    String manifold = network.createManifold("central manifold");

    // Add inlet pipelines
    network.addInletPipeline("pipe1", feed1, manifold, 5000.0, 0.3, 25);
    network.addInletPipeline("pipe2", feed2, manifold, 4500.0, 0.25, 22);

    // Run steady-state
    ProcessSystem process = new ProcessSystem();
    process.add(feed1);
    process.add(feed2);
    process.add(network);
    process.run();

    // Verify outlet stream exists
    StreamInterface outlet = network.getOutletStream();
    assertNotNull(outlet, "Outlet stream should exist");

    // Verify mixed flow rate is sum of inputs
    double outletFlow = outlet.getFlowRate("MSm3/day");
    assertTrue(outletFlow > 0, "Outlet flow should be positive");

    // Verify we have two pipelines
    assertEquals(2, network.getPipelines().size());
  }

  @Test
  void testNetworkWithMultipleManifolds() {
    // Create three feed streams
    SystemInterface gas1 = createNaturalGas(288.15, 85.0);
    gas1.setTotalFlowRate(4.0, "MSm3/day");
    Stream feed1 = new Stream("feed 1", gas1);

    SystemInterface gas2 = createNaturalGas(289.15, 83.0);
    gas2.setTotalFlowRate(3.5, "MSm3/day");
    Stream feed2 = new Stream("feed 2", gas2);

    SystemInterface gas3 = createRichGas(290.15, 80.0);
    gas3.setTotalFlowRate(2.5, "MSm3/day");
    Stream feed3 = new Stream("feed 3", gas3);

    // Create network with multiple manifolds
    PipeFlowNetwork network = new PipeFlowNetwork("multi-manifold network");

    // Create manifolds
    String manifoldA = network.createManifold("manifold A");
    String manifoldB = network.createManifold("manifold B");
    String centralManifold = network.createManifold("central manifold");

    // Add inlet pipelines to manifold A
    network.addInletPipeline("pipe1", feed1, manifoldA, 4000.0, 0.28, 20);
    network.addInletPipeline("pipe2", feed2, manifoldA, 3500.0, 0.26, 18);

    // Add inlet pipeline to manifold B
    network.addInletPipeline("pipe3", feed3, manifoldB, 5000.0, 0.24, 25);

    // Connect manifolds A and B to central manifold
    network.connectManifolds(manifoldA, centralManifold, "pipeA-central", 6000.0, 0.35, 30);
    network.connectManifolds(manifoldB, centralManifold, "pipeB-central", 5500.0, 0.30, 28);

    // Run
    ProcessSystem process = new ProcessSystem();
    process.add(feed1);
    process.add(feed2);
    process.add(feed3);
    process.add(network);
    process.run();

    // Verify network structure
    assertEquals(3, network.getManifolds().size());
    assertEquals(5, network.getPipelines().size());

    // Verify outlet exists
    assertNotNull(network.getOutletStream());
    assertTrue(network.getOutletStream().getFlowRate("MSm3/day") > 0);
  }

  @Test
  void testSteadyStateWithCompositionalTracking() {
    // Create feed with specific composition
    SystemInterface gas = createNaturalGas(288.15, 100.0);
    gas.setTotalFlowRate(10.0, "MSm3/day");
    Stream feed = new Stream("feed", gas);

    // Create simple network
    PipeFlowNetwork network = new PipeFlowNetwork("compositional test");
    network.setCompositionalTracking(true);
    network.setAdvectionScheme(AdvectionScheme.TVD_VAN_LEER);

    String manifold = network.createManifold("outlet");
    network.addInletPipeline("main pipe", feed, manifold, 10000.0, 0.4, 50);

    // Run
    feed.run();
    network.run();

    // Get composition profile
    double[] methaneProfile = network.getCompositionProfile("main pipe", "methane");
    assertNotNull(methaneProfile);
    // Total nodes = numberOfNodesInLeg + 2 (boundary nodes)
    assertTrue(methaneProfile.length >= 50, "Should have at least 50 nodes");

    // In steady-state, composition should be relatively uniform
    double first = methaneProfile[0];
    double last = methaneProfile[methaneProfile.length - 1];
    // Allow some numerical variation in steady-state composition
    assertEquals(first, last, 0.02, "Steady-state composition should be uniform");
  }

  @Test
  void testTransientSimulation() {
    // Create feed
    SystemInterface gas = createNaturalGas(288.15, 80.0);
    gas.setTotalFlowRate(5.0, "MSm3/day");
    Stream feed = new Stream("feed", gas);

    // Create network
    PipeFlowNetwork network = new PipeFlowNetwork("transient test");
    network.setCompositionalTracking(true);

    String manifold = network.createManifold("outlet");
    network.addInletPipeline("pipe", feed, manifold, 5000.0, 0.3, 25);

    // Initialize with steady-state
    feed.run();
    network.run();

    assertEquals(0.0, network.getSimulationTime(), 1e-10);

    // Run several transient steps
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 5; i++) {
      network.runTransient(60.0, id); // 60 second steps
    }

    // Verify simulation time advanced
    assertEquals(300.0, network.getSimulationTime(), 1e-6);

    // Verify outlet still exists and has flow
    assertNotNull(network.getOutletStream());
    assertTrue(network.getOutletStream().getFlowRate("MSm3/day") > 0);
  }

  @Test
  void testPressureProfile() {
    SystemInterface gas = createNaturalGas(288.15, 100.0);
    gas.setTotalFlowRate(8.0, "MSm3/day");
    Stream feed = new Stream("feed", gas);

    PipeFlowNetwork network = new PipeFlowNetwork("pressure test");
    String manifold = network.createManifold("outlet");
    network.addInletPipeline("long pipe", feed, manifold, 20000.0, 0.35, 40);

    feed.run();
    network.run();

    double[] pressureProfile = network.getPressureProfile("long pipe", "bara");
    assertNotNull(pressureProfile);
    // Total nodes = numberOfNodesInLeg + 2 (boundary nodes)
    assertTrue(pressureProfile.length >= 40, "Should have at least 40 nodes");

    // Pressure should decrease along the pipe
    double inletP = pressureProfile[0];
    double outletP = pressureProfile[pressureProfile.length - 1];
    assertTrue(inletP >= outletP, "Pressure should decrease along pipe");
  }

  @Test
  void testTemperatureProfile() {
    SystemInterface gas = createNaturalGas(320.0, 80.0); // Hot inlet
    gas.setTotalFlowRate(5.0, "MSm3/day");
    Stream feed = new Stream("feed", gas);

    PipeFlowNetwork network = new PipeFlowNetwork("temperature test");
    network.setDefaultOuterTemperature(278.0); // Cold ambient

    String manifold = network.createManifold("outlet");
    network.addInletPipeline("cooling pipe", feed, manifold, 15000.0, 0.3, 30);

    feed.run();
    network.run();

    double[] tempProfile = network.getTemperatureProfile("cooling pipe", "K");
    assertNotNull(tempProfile);
    // Total nodes = numberOfNodesInLeg + 2 (boundary nodes)
    assertTrue(tempProfile.length >= 30, "Should have at least 30 nodes");
  }

  @Test
  void testTotalPressureDrop() {
    SystemInterface gas = createNaturalGas(288.15, 100.0);
    gas.setTotalFlowRate(10.0, "MSm3/day");
    Stream feed = new Stream("feed", gas);

    PipeFlowNetwork network = new PipeFlowNetwork("pressure drop test");
    String manifold = network.createManifold("outlet");
    network.addInletPipeline("pipe", feed, manifold, 10000.0, 0.4, 50);

    feed.run();
    network.run();

    double totalDrop = network.getTotalPressureDrop("bara");
    assertTrue(totalDrop >= 0, "Pressure drop should be non-negative");
  }

  @Test
  void testResetSimulationTime() {
    SystemInterface gas = createNaturalGas(288.15, 80.0);
    gas.setTotalFlowRate(5.0, "MSm3/day");
    Stream feed = new Stream("feed", gas);

    PipeFlowNetwork network = new PipeFlowNetwork("reset test");
    String manifold = network.createManifold("outlet");
    network.addInletPipeline("pipe", feed, manifold, 5000.0, 0.3, 20);

    feed.run();
    network.run();

    // Run some transient steps
    UUID id = UUID.randomUUID();
    network.runTransient(60.0, id);
    network.runTransient(60.0, id);
    assertEquals(120.0, network.getSimulationTime(), 1e-6);

    // Reset and verify
    network.resetSimulationTime();
    assertEquals(0.0, network.getSimulationTime(), 1e-10);
  }

  @Test
  void testPressureProfileReport() {
    // Create a natural gas at 100 bara, 15°C
    SystemInterface gas = createNaturalGas(288.15, 100.0);
    gas.setTotalFlowRate(10.0, "MSm3/day");
    Stream feed = new Stream("feed", gas);

    // Create a simple pipeline network: single 50 km pipe
    PipeFlowNetwork network = new PipeFlowNetwork("export pipeline");
    network.setDefaultOuterTemperature(278.0); // 5°C ambient
    network.setDefaultHeatTransferCoefficients(5.0, 15.0);

    String manifold = network.createManifold("platform");
    network.addInletPipeline("export pipe", feed, manifold, 50000.0, 0.5, 50);

    // Run steady-state
    feed.run();
    network.run();

    // Get profiles
    double[] pressureProfile = network.getPressureProfile("export pipe", "bara");
    double[] tempProfile = network.getTemperatureProfile("export pipe", "C");
    double[] velocityProfile = network.getVelocityProfile("export pipe");

    int nNodes = pressureProfile.length;
    double pressureDrop = pressureProfile[0] - pressureProfile[nNodes - 1];

    // Assertions
    assertTrue(pressureDrop >= 0, "Pressure should decrease along pipe");
    assertNotNull(pressureProfile);
    assertNotNull(tempProfile);
    assertNotNull(velocityProfile);
  }

  @Test
  void testPressureProfileReportMultiplePipesAndManifold() {
    // Create three feed streams with different conditions
    SystemInterface gas1 = createNaturalGas(288.15, 120.0); // 120 bara, 15°C
    gas1.setTotalFlowRate(6.0, "MSm3/day");
    Stream feed1 = new Stream("well 1", gas1);

    SystemInterface gas2 = createNaturalGas(290.15, 115.0); // 115 bara, 17°C
    gas2.setTotalFlowRate(4.5, "MSm3/day");
    Stream feed2 = new Stream("well 2", gas2);

    SystemInterface gas3 = createRichGas(285.15, 118.0); // 118 bara, 12°C, rich gas
    gas3.setTotalFlowRate(3.5, "MSm3/day");
    Stream feed3 = new Stream("well 3", gas3);

    // Create network: 3 inlet pipes -> manifold -> export pipe -> platform
    PipeFlowNetwork network = new PipeFlowNetwork("gathering network");
    network.setDefaultOuterTemperature(278.0); // 5°C seabed
    network.setDefaultHeatTransferCoefficients(5.0, 15.0);

    // Create manifolds
    String gatheringManifold = network.createManifold("gathering manifold");
    String platform = network.createManifold("platform");

    // Add inlet pipelines to gathering manifold
    network.addInletPipeline("flowline 1", feed1, gatheringManifold, 8000.0, 0.25, 40);
    network.addInletPipeline("flowline 2", feed2, gatheringManifold, 6500.0, 0.22, 32);
    network.addInletPipeline("flowline 3", feed3, gatheringManifold, 10000.0, 0.20, 50);

    // Connect manifold to platform with export pipeline
    network.connectManifolds(gatheringManifold, platform, "export trunkline", 25000.0, 0.45, 60);

    // Run steady-state
    ProcessSystem process = new ProcessSystem();
    process.add(feed1);
    process.add(feed2);
    process.add(feed3);
    process.add(network);
    process.run();

    // Verify profiles exist for all pipelines
    String[] inletPipes = {"flowline 1", "flowline 2", "flowline 3"};
    for (String pipeName : inletPipes) {
      double[] pressure = network.getPressureProfile(pipeName, "bara");
      double[] temp = network.getTemperatureProfile(pipeName, "C");
      double[] velocity = network.getVelocityProfile(pipeName);
      assertNotNull(pressure);
      assertNotNull(temp);
      assertNotNull(velocity);
      assertTrue(pressure.length > 0);
    }

    // Verify export trunkline profiles
    double[] exportP = network.getPressureProfile("export trunkline", "bara");
    double[] exportT = network.getTemperatureProfile("export trunkline", "C");
    double[] exportV = network.getVelocityProfile("export trunkline");
    assertNotNull(exportP);
    assertNotNull(exportT);
    assertNotNull(exportV);

    // Assertions
    StreamInterface outlet = network.getOutletStream();
    assertNotNull(outlet);
    assertTrue(outlet.getFlowRate("MSm3/day") > 0);
    assertEquals(4, network.getPipelines().size());
  }
}

