package neqsim.process.processmodel.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class to verify that graph-based execution produces identical results to sequential
 * execution.
 * 
 * <p>
 * These tests ensure that the graph-based parallel and optimized execution strategies don't
 * introduce numerical differences compared to the traditional sequential execution.
 * </p>
 */
public class GraphVsSequentialExecutionTest {
  private SystemInterface testFluid;

  @BeforeEach
  void setUp() {
    testFluid = new SystemSrkEos(298.0, 50.0);
    testFluid.addComponent("methane", 0.85);
    testFluid.addComponent("ethane", 0.10);
    testFluid.addComponent("propane", 0.05);
    testFluid.setMixingRule("classic");
  }

  /**
   * Test that removeUnit() properly invalidates the graph cache.
   */
  @Test
  void testRemoveUnitInvalidatesGraph() {
    ProcessSystem system = new ProcessSystem("GraphInvalidation Test");

    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(1000.0, "kg/hr");
    system.add(feed);

    Heater heater = new Heater("heater", feed);
    heater.setOutTemperature(350.0, "K");
    system.add(heater);

    Separator separator = new Separator("separator", heater.getOutletStream());
    system.add(separator);

    // Build graph first
    ProcessGraph graph1 = system.buildGraph();
    assertEquals(3, graph1.getNodeCount());

    // Remove a unit
    system.removeUnit("heater");

    // Build graph again - should be rebuilt with 2 nodes
    ProcessGraph graph2 = system.buildGraph();
    assertEquals(2, graph2.getNodeCount());
  }

  /**
   * Test that clear() properly invalidates the graph cache.
   */
  @Test
  void testClearInvalidatesGraph() {
    ProcessSystem system = new ProcessSystem("Clear Test");

    Stream feed = new Stream("feed", testFluid.clone());
    feed.setFlowRate(1000.0, "kg/hr");
    system.add(feed);

    Heater heater = new Heater("heater", feed);
    system.add(heater);

    // Build graph first
    ProcessGraph graph1 = system.buildGraph();
    assertEquals(2, graph1.getNodeCount());

    // Clear the system
    system.clear();
    assertEquals(0, system.size());

    // Add new units
    Stream newFeed = new Stream("newFeed", testFluid.clone());
    newFeed.setFlowRate(500.0, "kg/hr");
    system.add(newFeed);

    // Build graph again - should reflect new structure
    ProcessGraph graph2 = system.buildGraph();
    assertEquals(1, graph2.getNodeCount());
  }

  /**
   * Test simple linear process gives identical results with sequential and graph-based execution.
   */
  @Test
  void testLinearProcessSequentialVsGraphBased() {
    // Create process with sequential execution
    ProcessSystem seqSystem = new ProcessSystem("Sequential Linear");
    seqSystem.setUseOptimizedExecution(false);

    Stream seqFeed = new Stream("feed", testFluid.clone());
    seqFeed.setFlowRate(1000.0, "kg/hr");
    seqFeed.setTemperature(25.0, "C");
    seqFeed.setPressure(50.0, "bara");
    seqSystem.add(seqFeed);

    Heater seqHeater = new Heater("heater", seqFeed);
    seqHeater.setOutTemperature(350.0, "K");
    seqSystem.add(seqHeater);

    Separator seqSep = new Separator("separator", seqHeater.getOutletStream());
    seqSystem.add(seqSep);

    seqSystem.run();

    double seqGasFlow = seqSep.getGasOutStream().getFlowRate("kg/hr");
    double seqLiqFlow = seqSep.getLiquidOutStream().getFlowRate("kg/hr");
    double seqOutTemp = seqSep.getGasOutStream().getTemperature("K");

    // Create identical process with graph-based execution
    ProcessSystem graphSystem = new ProcessSystem("Graph Linear");
    graphSystem.setUseGraphBasedExecution(true);

    Stream graphFeed = new Stream("feed", testFluid.clone());
    graphFeed.setFlowRate(1000.0, "kg/hr");
    graphFeed.setTemperature(25.0, "C");
    graphFeed.setPressure(50.0, "bara");
    graphSystem.add(graphFeed);

    Heater graphHeater = new Heater("heater", graphFeed);
    graphHeater.setOutTemperature(350.0, "K");
    graphSystem.add(graphHeater);

    Separator graphSep = new Separator("separator", graphHeater.getOutletStream());
    graphSystem.add(graphSep);

    graphSystem.run();

    double graphGasFlow = graphSep.getGasOutStream().getFlowRate("kg/hr");
    double graphLiqFlow = graphSep.getLiquidOutStream().getFlowRate("kg/hr");
    double graphOutTemp = graphSep.getGasOutStream().getTemperature("K");

    // Verify results match
    assertEquals(seqGasFlow, graphGasFlow, 1e-6, "Gas flow should match");
    assertEquals(seqLiqFlow, graphLiqFlow, 1e-6, "Liquid flow should match");
    assertEquals(seqOutTemp, graphOutTemp, 1e-6, "Temperature should match");
  }

  /**
   * Test branching process (splitter) gives identical results.
   */
  @Test
  void testBranchingProcessSequentialVsParallel() throws InterruptedException {
    // Create process with sequential execution
    ProcessSystem seqSystem = new ProcessSystem("Sequential Branching");
    seqSystem.setUseOptimizedExecution(false);

    Stream seqFeed = new Stream("feed", testFluid.clone());
    seqFeed.setFlowRate(1000.0, "kg/hr");
    seqFeed.setTemperature(25.0, "C");
    seqFeed.setPressure(50.0, "bara");
    seqSystem.add(seqFeed);

    Splitter seqSplitter = new Splitter("splitter", seqFeed);
    seqSplitter.setSplitFactors(new double[] {0.6, 0.4});
    seqSystem.add(seqSplitter);

    Heater seqHeater1 = new Heater("heater1", seqSplitter.getSplitStream(0));
    seqHeater1.setOutTemperature(350.0, "K");
    seqSystem.add(seqHeater1);

    Cooler seqCooler = new Cooler("cooler", seqSplitter.getSplitStream(1));
    seqCooler.setOutTemperature(280.0, "K");
    seqSystem.add(seqCooler);

    seqSystem.run();

    double seqHeater1Flow = seqHeater1.getOutletStream().getFlowRate("kg/hr");
    double seqHeater1Temp = seqHeater1.getOutletStream().getTemperature("K");
    double seqCoolerFlow = seqCooler.getOutletStream().getFlowRate("kg/hr");
    double seqCoolerTemp = seqCooler.getOutletStream().getTemperature("K");

    // Create identical process with parallel execution
    ProcessSystem parSystem = new ProcessSystem("Parallel Branching");

    Stream parFeed = new Stream("feed", testFluid.clone());
    parFeed.setFlowRate(1000.0, "kg/hr");
    parFeed.setTemperature(25.0, "C");
    parFeed.setPressure(50.0, "bara");
    parSystem.add(parFeed);

    Splitter parSplitter = new Splitter("splitter", parFeed);
    parSplitter.setSplitFactors(new double[] {0.6, 0.4});
    parSystem.add(parSplitter);

    Heater parHeater1 = new Heater("heater1", parSplitter.getSplitStream(0));
    parHeater1.setOutTemperature(350.0, "K");
    parSystem.add(parHeater1);

    Cooler parCooler = new Cooler("cooler", parSplitter.getSplitStream(1));
    parCooler.setOutTemperature(280.0, "K");
    parSystem.add(parCooler);

    parSystem.runParallel();

    double parHeater1Flow = parHeater1.getOutletStream().getFlowRate("kg/hr");
    double parHeater1Temp = parHeater1.getOutletStream().getTemperature("K");
    double parCoolerFlow = parCooler.getOutletStream().getFlowRate("kg/hr");
    double parCoolerTemp = parCooler.getOutletStream().getTemperature("K");

    // Verify results match
    assertEquals(seqHeater1Flow, parHeater1Flow, 1e-6, "Heater1 flow should match");
    assertEquals(seqHeater1Temp, parHeater1Temp, 1e-6, "Heater1 temp should match");
    assertEquals(seqCoolerFlow, parCoolerFlow, 1e-6, "Cooler flow should match");
    assertEquals(seqCoolerTemp, parCoolerTemp, 1e-6, "Cooler temp should match");
  }

  /**
   * Test that runOptimized correctly detects multi-input equipment.
   */
  @Test
  void testRunOptimizedDetectsMultiInputEquipment() {
    ProcessSystem system = new ProcessSystem("Multi-input Test");

    Stream feed1 = new Stream("feed1", testFluid.clone());
    feed1.setFlowRate(500.0, "kg/hr");
    system.add(feed1);

    Stream feed2 = new Stream("feed2", testFluid.clone());
    feed2.setFlowRate(500.0, "kg/hr");
    system.add(feed2);

    Mixer mixer = new Mixer("mixer");
    mixer.addStream(feed1);
    mixer.addStream(feed2);
    system.add(mixer);

    // Should detect multi-input equipment and NOT recommend parallel execution
    assertTrue(system.hasMultiInputEquipment(), "Should detect mixer as multi-input");
    assertFalse(system.isParallelExecutionBeneficial(),
        "Parallel should not be beneficial with mixer");
  }

  /**
   * Test complex process with valve and compressor.
   */
  @Test
  void testComplexProcessSequentialVsOptimized() {
    // Create process with sequential execution
    ProcessSystem seqSystem = new ProcessSystem("Sequential Complex");
    seqSystem.setUseOptimizedExecution(false);

    Stream seqFeed = new Stream("feed", testFluid.clone());
    seqFeed.setFlowRate(1000.0, "kg/hr");
    seqFeed.setTemperature(25.0, "C");
    seqFeed.setPressure(50.0, "bara");
    seqSystem.add(seqFeed);

    ThrottlingValve seqValve = new ThrottlingValve("valve", seqFeed);
    seqValve.setOutletPressure(30.0);
    seqSystem.add(seqValve);

    Heater seqHeater = new Heater("heater", seqValve.getOutletStream());
    seqHeater.setOutTemperature(320.0, "K");
    seqSystem.add(seqHeater);

    Compressor seqCompressor = new Compressor("compressor", seqHeater.getOutletStream());
    seqCompressor.setOutletPressure(80.0);
    seqSystem.add(seqCompressor);

    Cooler seqCooler = new Cooler("cooler", seqCompressor.getOutletStream());
    seqCooler.setOutTemperature(300.0, "K");
    seqSystem.add(seqCooler);

    seqSystem.run();

    double seqOutPressure = seqCooler.getOutletStream().getPressure("bara");
    double seqOutTemp = seqCooler.getOutletStream().getTemperature("K");
    double seqOutFlow = seqCooler.getOutletStream().getFlowRate("kg/hr");
    double seqCompPower = seqCompressor.getPower("kW");

    // Create identical process with optimized execution
    ProcessSystem optSystem = new ProcessSystem("Optimized Complex");

    Stream optFeed = new Stream("feed", testFluid.clone());
    optFeed.setFlowRate(1000.0, "kg/hr");
    optFeed.setTemperature(25.0, "C");
    optFeed.setPressure(50.0, "bara");
    optSystem.add(optFeed);

    ThrottlingValve optValve = new ThrottlingValve("valve", optFeed);
    optValve.setOutletPressure(30.0);
    optSystem.add(optValve);

    Heater optHeater = new Heater("heater", optValve.getOutletStream());
    optHeater.setOutTemperature(320.0, "K");
    optSystem.add(optHeater);

    Compressor optCompressor = new Compressor("compressor", optHeater.getOutletStream());
    optCompressor.setOutletPressure(80.0);
    optSystem.add(optCompressor);

    Cooler optCooler = new Cooler("cooler", optCompressor.getOutletStream());
    optCooler.setOutTemperature(300.0, "K");
    optSystem.add(optCooler);

    optSystem.runOptimized();

    double optOutPressure = optCooler.getOutletStream().getPressure("bara");
    double optOutTemp = optCooler.getOutletStream().getTemperature("K");
    double optOutFlow = optCooler.getOutletStream().getFlowRate("kg/hr");
    double optCompPower = optCompressor.getPower("kW");

    // Verify results match
    assertEquals(seqOutPressure, optOutPressure, 1e-6, "Outlet pressure should match");
    assertEquals(seqOutTemp, optOutTemp, 1e-6, "Outlet temperature should match");
    assertEquals(seqOutFlow, optOutFlow, 1e-6, "Outlet flow should match");
    assertEquals(seqCompPower, optCompPower, 1e-6, "Compressor power should match");
  }

  /**
   * Test that multiple runs produce consistent results with graph-based execution.
   */
  @Test
  void testMultipleRunsConsistency() throws InterruptedException {
    ProcessSystem system = new ProcessSystem("Consistency Test");

    Stream feed1 = new Stream("feed1", testFluid.clone());
    feed1.setFlowRate(500.0, "kg/hr");
    feed1.setTemperature(25.0, "C");
    feed1.setPressure(50.0, "bara");
    system.add(feed1);

    Stream feed2 = new Stream("feed2", testFluid.clone());
    feed2.setFlowRate(500.0, "kg/hr");
    feed2.setTemperature(30.0, "C");
    feed2.setPressure(50.0, "bara");
    system.add(feed2);

    Heater heater1 = new Heater("heater1", feed1);
    heater1.setOutTemperature(350.0, "K");
    system.add(heater1);

    Heater heater2 = new Heater("heater2", feed2);
    heater2.setOutTemperature(340.0, "K");
    system.add(heater2);

    // Run multiple times and verify consistency
    double[] results = new double[5];
    for (int i = 0; i < 5; i++) {
      system.runParallel();
      results[i] = heater1.getOutletStream().getFlowRate("kg/hr")
          + heater2.getOutletStream().getFlowRate("kg/hr");
    }

    // All results should be identical
    for (int i = 1; i < 5; i++) {
      assertEquals(results[0], results[i], 1e-10,
          "Run " + i + " should produce same result as run 0");
    }
  }
}
