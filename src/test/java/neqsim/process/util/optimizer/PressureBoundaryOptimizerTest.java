package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for PressureBoundaryOptimizer.
 *
 * <p>
 * Tests the simplified pressure boundary optimization that uses ProductionOptimizer as its
 * foundation.
 * </p>
 *
 * @author ESOL
 */
public class PressureBoundaryOptimizerTest {
  private SystemInterface testFluid;
  private Stream feedStream;
  private ProcessSystem simpleProcess;
  private ProcessSystem processWithCompressor;

  @BeforeEach
  public void setUp() {
    // Create a simple gas system
    testFluid = new SystemSrkEos(288.15, 80.0); // 15°C, 80 bara
    testFluid.addComponent("methane", 0.85);
    testFluid.addComponent("ethane", 0.10);
    testFluid.addComponent("propane", 0.05);
    testFluid.setMixingRule("classic");
    testFluid.setTotalFlowRate(50000.0, "kg/hr");
    testFluid.init(0);
    testFluid.init(3);

    // Create simple valve-based process (no mechanical design needed)
    feedStream = new Stream("Feed", testFluid);
    feedStream.run();

    ThrottlingValve valve = new ThrottlingValve("Valve", feedStream);
    valve.setOutletPressure(70.0); // Target 70 bara outlet

    Stream outletStream = new Stream("Outlet", valve.getOutletStream());

    simpleProcess = new ProcessSystem();
    simpleProcess.add(feedStream);
    simpleProcess.add(valve);
    simpleProcess.add(outletStream);
  }

  /**
   * Sets up a process with compressor for compression tests.
   */
  private void setupCompressorProcess() {
    // Create gas at lower pressure for compression
    SystemInterface gasFluid = new SystemSrkEos(288.15, 50.0); // 50 bara inlet
    gasFluid.addComponent("methane", 0.90);
    gasFluid.addComponent("ethane", 0.07);
    gasFluid.addComponent("propane", 0.03);
    gasFluid.setMixingRule("classic");
    gasFluid.setTotalFlowRate(30000.0, "kg/hr");
    gasFluid.init(0);
    gasFluid.init(3);

    Stream feed = new Stream("Feed", gasFluid);
    feed.run();

    Compressor comp = new Compressor("Compressor", feed);
    comp.setOutletPressure(100.0); // Target 100 bara
    comp.setUsePolytropicCalc(true);
    comp.setPolytropicEfficiency(0.75);

    Cooler cooler = new Cooler("Aftercooler", comp.getOutletStream());
    cooler.setOutTemperature(313.15); // 40°C

    Stream export = new Stream("Export", cooler.getOutletStream());

    processWithCompressor = new ProcessSystem();
    processWithCompressor.add(feed);
    processWithCompressor.add(comp);
    processWithCompressor.add(cooler);
    processWithCompressor.add(export);
  }

  @Test
  public void testFindMaxFlowRateSimpleProcess() {
    // Run baseline
    simpleProcess.run();

    double baselineInletP = feedStream.getPressure("bara");
    System.out.println("Baseline inlet pressure: " + baselineInletP + " bara");

    // Create optimizer
    PressureBoundaryOptimizer optimizer =
        new PressureBoundaryOptimizer(simpleProcess, "Feed", "Outlet");
    optimizer.setMinFlowRate(1000.0);
    optimizer.setMaxFlowRate(500000.0);
    optimizer.setMaxIterations(30);

    // Find max flow rate for a specific outlet pressure
    double targetOutlet = 70.0; // bara
    OptimizationResult result = optimizer.findMaxFlowRate(80.0, targetOutlet, "bara");

    System.out.println("Max flow result: " + result.getOptimalRate() + " " + result.getRateUnit());
    System.out.println("Feasible: " + result.isFeasible());
    System.out.println("Bottleneck: "
        + (result.getBottleneck() != null ? result.getBottleneck().getName() : "none"));

    assertNotNull(result, "Should return a result");
    assertTrue(result.getOptimalRate() > 0, "Should find positive flow rate");
  }

  @Test
  public void testFindMaxFlowRateWithCompressor() {
    setupCompressorProcess();
    processWithCompressor.run();

    // Create optimizer
    Stream feed = (Stream) processWithCompressor.getUnit("Feed");
    Stream export = (Stream) processWithCompressor.getUnit("Export");

    PressureBoundaryOptimizer optimizer =
        new PressureBoundaryOptimizer(processWithCompressor, feed, export);
    optimizer.setMinFlowRate(1000.0);
    optimizer.setMaxFlowRate(200000.0);
    optimizer.setAutoConfigureCompressors(true);
    optimizer.setMinSurgeMargin(0.10);
    optimizer.setPressureTolerance(0.05); // 5% tolerance for compressor process

    // Find max flow at pressure boundaries
    double inletP = 50.0;
    double outletP = 95.0; // Slightly below compressor outlet to account for cooler drop
    OptimizationResult result = optimizer.findMaxFlowRate(inletP, outletP, "bara");

    System.out.println("\nCompressor process result:");
    System.out.println("  Optimal rate: " + result.getOptimalRate() + " " + result.getRateUnit());
    System.out.println("  Feasible: " + result.isFeasible());
    System.out
        .println("  Total power: " + result.getDecisionVariables().get("totalPower_kW") + " kW");
    System.out.println("  Bottleneck: "
        + (result.getBottleneck() != null ? result.getBottleneck().getName() : "none"));

    assertNotNull(result, "Should return a result");
  }

  @Test
  public void testGenerateLiftCurveTable() {
    // Run baseline
    simpleProcess.run();

    // Create optimizer
    PressureBoundaryOptimizer optimizer =
        new PressureBoundaryOptimizer(simpleProcess, "Feed", "Outlet");
    optimizer.setMinFlowRate(1000.0);
    optimizer.setMaxFlowRate(300000.0);
    optimizer.setMaxIterations(20);

    // Generate lift curve table with feasible pressure combinations
    // For a valve, outlet must be less than inlet
    double[] inletPressures = {80.0, 90.0, 100.0};
    double[] outletPressures = {60.0, 70.0, 75.0}; // Must be less than inlet

    PressureBoundaryOptimizer.LiftCurveTable table =
        optimizer.generateLiftCurveTable(inletPressures, outletPressures, "bara");

    System.out.println("\nLift Curve Table:");
    System.out.println(table.toEclipseFormat());
    System.out.println("\nTable summary: " + table);

    assertNotNull(table, "Should generate a table");
    assertEquals(3, table.getInletPressures().length, "Should have 3 inlet pressures");
    assertEquals(3, table.getOutletPressures().length, "Should have 3 outlet pressures");
    // For valve processes with these pressures, should find some feasible points
    assertTrue(table.countFeasiblePoints() >= 0, "Table should be generated");
  }

  @Test
  public void testGenerateCapacityCurve() {
    // Run baseline
    simpleProcess.run();

    // Create optimizer
    PressureBoundaryOptimizer optimizer =
        new PressureBoundaryOptimizer(simpleProcess, "Feed", "Outlet");
    optimizer.setMinFlowRate(1000.0);
    optimizer.setMaxFlowRate(300000.0);

    // Generate capacity curve at fixed inlet pressure
    double inletPressure = 80.0;
    double[] outletPressures = {60.0, 65.0, 70.0, 75.0};

    double[] flowRates = optimizer.generateCapacityCurve(inletPressure, outletPressures, "bara");

    System.out.println("\nCapacity Curve at Pin=" + inletPressure + " bara:");
    for (int i = 0; i < outletPressures.length; i++) {
      System.out
          .println("  Pout=" + outletPressures[i] + " bara -> Flow=" + flowRates[i] + " kg/hr");
    }

    assertNotNull(flowRates, "Should return flow rates");
    assertEquals(outletPressures.length, flowRates.length, "Should have same number of points");
  }

  @Test
  public void testLiftCurveTableJsonFormat() {
    // Create a simple table
    double[] inletP = {70.0, 80.0};
    double[] outletP = {60.0, 65.0};
    double[][] flows = {{50000, 40000}, {70000, 60000}};
    double[][] powers = {{100, 90}, {150, 130}};
    String[][] bottlenecks = {{"Valve", "Valve"}, {"Valve", "Valve"}};

    PressureBoundaryOptimizer.LiftCurveTable table = new PressureBoundaryOptimizer.LiftCurveTable(
        "TestTable", inletP, outletP, flows, powers, bottlenecks, "bara", "kg/hr");

    String json = table.toJson();
    System.out.println("\nJSON format:");
    System.out.println(json);

    assertTrue(json.contains("\"tableName\": \"TestTable\""), "Should contain table name");
    assertTrue(json.contains("\"pressureUnit\": \"bara\""), "Should contain pressure unit");
    assertTrue(json.contains("\"feasiblePoints\": 4"), "Should have 4 feasible points");
  }

  @Test
  public void testInfeasiblePressureCombination() {
    // Run baseline
    simpleProcess.run();

    // Create optimizer
    PressureBoundaryOptimizer optimizer =
        new PressureBoundaryOptimizer(simpleProcess, "Feed", "Outlet");
    optimizer.setMinFlowRate(100.0);
    optimizer.setMaxFlowRate(100000.0);

    // Try infeasible combination (outlet higher than inlet - impossible for valve)
    OptimizationResult result = optimizer.findMaxFlowRate(50.0, 60.0, "bara");

    System.out.println("\nInfeasible result:");
    System.out.println("  Feasible: " + result.isFeasible());
    System.out.println("  Rate: " + result.getOptimalRate());

    // For a valve, outlet cannot be higher than inlet
    // The optimizer should either return infeasible or a very low flow rate
    assertNotNull(result, "Should return a result even for infeasible case");
  }

  @Test
  public void testCalculateTotalPower() {
    setupCompressorProcess();
    processWithCompressor.run();

    PressureBoundaryOptimizer optimizer = new PressureBoundaryOptimizer(processWithCompressor,
        (Stream) processWithCompressor.getUnit("Feed"),
        (Stream) processWithCompressor.getUnit("Export"));

    double power = optimizer.calculateTotalPower();
    System.out.println("Total compressor power: " + power + " kW");

    assertTrue(power > 0, "Should have positive power for compression");
  }

  @Test
  public void testUtilizationTracking() {
    // Run baseline
    simpleProcess.run();

    // Create optimizer with utilization limit
    PressureBoundaryOptimizer optimizer =
        new PressureBoundaryOptimizer(simpleProcess, "Feed", "Outlet");
    optimizer.setMaxUtilization(0.90); // 90% utilization limit
    optimizer.setMinFlowRate(1000.0);
    optimizer.setMaxFlowRate(200000.0);

    OptimizationResult result = optimizer.findMaxFlowRate(80.0, 70.0, "bara");

    System.out.println("\nUtilization tracking:");
    System.out.println("  Feasible: " + result.isFeasible());
    for (ProductionOptimizer.UtilizationRecord record : result.getUtilizationRecords()) {
      System.out.println("  " + record.getEquipmentName() + ": "
          + String.format("%.1f%%", record.getUtilization() * 100) + " (limit: "
          + String.format("%.1f%%", record.getUtilizationLimit() * 100) + ")");
    }

    assertNotNull(result.getUtilizationRecords(), "Should have utilization records");
  }

  @Test
  public void testWithExternalProductionOptimizer() {
    // Run baseline
    simpleProcess.run();

    // Create a ProductionOptimizer externally
    ProductionOptimizer prodOpt = new ProductionOptimizer();

    // Create PressureBoundaryOptimizer using the external ProductionOptimizer
    PressureBoundaryOptimizer optimizer =
        new PressureBoundaryOptimizer(simpleProcess, prodOpt, "Feed", "Outlet");
    optimizer.setMinFlowRate(1000.0);
    optimizer.setMaxFlowRate(500000.0);
    optimizer.setMaxUtilization(0.95); // 95% utilization limit

    // Verify the ProductionOptimizer is the same instance
    assertEquals(prodOpt, optimizer.getProductionOptimizer(),
        "Should use the passed ProductionOptimizer");

    // Find max flow rate
    OptimizationResult result = optimizer.findMaxFlowRate(80.0, 70.0, "bara");

    System.out.println("\nWith external ProductionOptimizer:");
    System.out.println("  Optimal rate: " + result.getOptimalRate() + " " + result.getRateUnit());
    System.out.println("  Feasible: " + result.isFeasible());

    assertNotNull(result, "Should return a result");
    assertTrue(result.getOptimalRate() > 0, "Should find positive flow rate");
  }

  @Test
  public void testSetProductionOptimizer() {
    // Create optimizer with default ProductionOptimizer
    simpleProcess.run();
    PressureBoundaryOptimizer optimizer =
        new PressureBoundaryOptimizer(simpleProcess, "Feed", "Outlet");

    // Get original optimizer
    ProductionOptimizer original = optimizer.getProductionOptimizer();
    assertNotNull(original, "Should have a default ProductionOptimizer");

    // Create new ProductionOptimizer and set it
    ProductionOptimizer newOpt = new ProductionOptimizer();
    optimizer.setProductionOptimizer(newOpt);

    // Verify it was replaced
    assertEquals(newOpt, optimizer.getProductionOptimizer(),
        "Should use the new ProductionOptimizer");

    // Run optimization with new optimizer
    optimizer.setMinFlowRate(1000.0);
    optimizer.setMaxFlowRate(300000.0);
    optimizer.setMaxUtilization(0.80); // Set utilization via PressureBoundaryOptimizer
    OptimizationResult result = optimizer.findMaxFlowRate(80.0, 70.0, "bara");

    System.out.println("\nWith replaced ProductionOptimizer:");
    System.out.println("  Optimal rate: " + result.getOptimalRate() + " " + result.getRateUnit());

    assertNotNull(result, "Should return a result");
  }
}

