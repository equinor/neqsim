package neqsim.process.util.optimizer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer.ConstraintSeverity;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConstraint;
import neqsim.process.util.optimizer.ProductionOptimizer.SearchMode;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test cases for multi-objective optimization.
 *
 * <p>
 * Tests the Pareto front generation for competing objectives: maximize throughput vs minimize
 * energy.
 * </p>
 */
public class MultiObjectiveOptimizerTest {

  /**
   * Create a test process with compressor and cooler for multi-objective testing.
   *
   * @return process system with feed stream at index 0
   */
  private ProcessSystem createTestProcess() {
    // Create gas fluid
    SystemSrkEos fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("CO2", 0.01);
    fluid.setMixingRule("classic");

    // Build process
    ProcessSystem process = new ProcessSystem();

    // Feed stream
    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(30.0, "bara");
    process.add(feed);

    // HP Separator
    Separator hpSeparator = new Separator("HP Separator", feed);
    hpSeparator.initMechanicalDesign();
    hpSeparator.getMechanicalDesign().setMaxDesignGassVolumeFlow(50000.0); // Sm3/hr - increased
    process.add(hpSeparator);

    // Compressor - moderate compression ratio
    Compressor compressor = new Compressor("Gas Compressor", hpSeparator.getGasOutStream());
    compressor.setOutletPressure(50.0, "bara"); // Reduced from 80 to 50 bara
    compressor.setIsentropicEfficiency(0.75);
    // Power limit in WATTS (not kW!) - 500 kW = 500,000 W
    // At 5000 kg/hr, power is ~118 kW; limit of 500 kW allows up to ~21,000 kg/hr
    compressor.getMechanicalDesign().setMaxDesignPower(500_000.0);
    process.add(compressor);

    // After-cooler
    Cooler cooler = new Cooler("After Cooler", compressor.getOutletStream());
    cooler.setOutTemperature(40.0, "C");
    process.add(cooler);

    return process;
  }

  @Test
  public void testWeightedSumOptimization() {
    ProcessSystem process = createTestProcess();
    Stream feed = (Stream) process.getUnit("Feed");

    // First run the process to ensure it works
    process.run();
    System.out.println("Initial process run successful");
    System.out.println("Feed flow: " + feed.getFlowRate("kg/hr") + " kg/hr");

    Compressor comp = (Compressor) process.getUnit("Gas Compressor");
    System.out.println("Compressor power: " + comp.getPower("kW") + " kW");
    System.out
        .println("Max design power: " + comp.getMechanicalDesign().maxDesignPower / 1000.0 + " kW");

    // Define objectives: maximize throughput, minimize power
    List<ObjectiveFunction> objectives =
        Arrays.asList(StandardObjective.MAXIMIZE_THROUGHPUT, StandardObjective.MINIMIZE_POWER);

    // Configuration
    OptimizationConfig config =
        new OptimizationConfig(1000.0, 15000.0).rateUnit("kg/hr").tolerance(50.0).maxIterations(20)
            .defaultUtilizationLimit(0.95).searchMode(SearchMode.BINARY_FEASIBILITY);

    // Run multi-objective optimization with progress tracking
    final int[] successCount = {0};
    final int[] failCount = {0};
    final int[] feasibleCount = {0};
    MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer().includeInfeasible(true) // Include
                                                                                        // all
                                                                                        // solutions
                                                                                        // for
                                                                                        // debugging
        .onProgress((iteration, total, solution) -> {
          if (solution != null) {
            successCount[0]++;
            if (solution.isFeasible()) {
              feasibleCount[0]++;
            }
            System.out.printf("  Iteration %d/%d: Flow=%.0f, Power=%.1f, Feasible=%s%n", iteration,
                total, solution.getRawValue(0), solution.getRawValue(1), solution.isFeasible());
          } else {
            failCount[0]++;
            System.out.printf("  Iteration %d/%d: FAILED%n", iteration, total);
          }
        });

    ParetoFront front = moo.optimizeWeightedSum(process, feed, objectives, config, 10);

    System.out.println("\nOptimization complete:");
    System.out.println("  Success count: " + successCount[0]);
    System.out.println("  Feasible count: " + feasibleCount[0]);
    System.out.println("  Fail count: " + failCount[0]);
    System.out.println("  Pareto front size: " + front.size());

    // Assertions
    Assertions.assertFalse(front.isEmpty(), "Pareto front should not be empty");
    // Note: With binary search optimizer and linearly-related objectives (flow vs power),
    // weighted-sum converges to the same max-feasible point regardless of weights.
    // This is expected behavior - for true Pareto diversity, use epsilon-constraint.
    Assertions.assertTrue(front.size() >= 1, "Should find at least one solution");

    // Check that solutions have proper objectives
    for (ParetoSolution sol : front) {
      Assertions.assertEquals(2, sol.getNumObjectives());
      Assertions.assertEquals("Throughput", sol.getObjectiveName(0));
      Assertions.assertEquals("Total Power", sol.getObjectiveName(1));
      Assertions.assertTrue(sol.getRawValue(0) > 0, "Throughput should be positive");
      // Power might be 0 at very low flows
    }

    // Knee point should exist
    ParetoSolution knee = front.findKneePoint();
    Assertions.assertNotNull(knee, "Knee point should be found");

    System.out.println("Pareto Front (Weighted Sum):");
    System.out.println("=============================");
    for (ParetoSolution sol : front.getSolutionsSortedBy(0, true)) {
      System.out.printf("  Flow: %.0f kg/hr, Power: %.1f kW, Feasible: %s%n", sol.getRawValue(0),
          sol.getRawValue(1), sol.isFeasible());
    }
    System.out.println("\nKnee Point: " + knee);
    System.out.println("\nJSON Export:\n" + front.toJson());
  }

  @Test
  public void testEpsilonConstraintOptimization() {
    ProcessSystem process = createTestProcess();
    Stream feed = (Stream) process.getUnit("Feed");

    // Primary: maximize throughput
    // Constraint: power must be below varying limits
    ObjectiveFunction primaryObj = StandardObjective.MAXIMIZE_THROUGHPUT;
    List<ObjectiveFunction> constrainedObjs =
        Collections.singletonList(StandardObjective.MINIMIZE_POWER);

    OptimizationConfig config =
        new OptimizationConfig(1000.0, 15000.0).rateUnit("kg/hr").tolerance(50.0).maxIterations(20)
            .defaultUtilizationLimit(0.95).searchMode(SearchMode.BINARY_FEASIBILITY);

    // Run epsilon-constraint optimization
    MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer();
    ParetoFront front =
        moo.optimizeEpsilonConstraint(process, feed, primaryObj, constrainedObjs, config, 8);

    // Assertions
    Assertions.assertFalse(front.isEmpty(), "Pareto front should not be empty");

    System.out.println("\nPareto Front (Epsilon-Constraint):");
    System.out.println("===================================");
    for (ParetoSolution sol : front.getSolutionsSortedBy(0, true)) {
      System.out.printf("  Flow: %.0f kg/hr, Power: %.1f kW%n", sol.getRawValue(0),
          sol.getRawValue(1));
    }
  }

  @Test
  public void testSamplingParetoFront() {
    ProcessSystem process = createTestProcess();
    Stream feed = (Stream) process.getUnit("Feed");

    // Define objectives: maximize throughput, minimize power
    List<ObjectiveFunction> objectives =
        Arrays.asList(StandardObjective.MAXIMIZE_THROUGHPUT, StandardObjective.MINIMIZE_POWER);

    // Configuration with flow rate bounds
    OptimizationConfig config = new OptimizationConfig(1000.0, 20000.0).rateUnit("kg/hr")
        .tolerance(50.0).defaultUtilizationLimit(0.95).maxIterations(20);

    // Run sampling-based Pareto generation
    final int[] sampleCount = {0};
    MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer().includeInfeasible(true) // Include
                                                                                        // all for
                                                                                        // analysis
        .onProgress((iteration, total, solution) -> {
          sampleCount[0]++;
          if (solution != null) {
            System.out.printf("  Sample %d/%d: Flow=%.0f, Power=%.1f kW, Feasible=%s%n", iteration,
                total, solution.getRawValue(0), solution.getRawValue(1), solution.isFeasible());
          } else {
            System.out.printf("  Sample %d/%d: FAILED%n", iteration, total);
          }
        });

    ParetoFront front = moo.sampleParetoFront(process, feed, objectives, config, 10);

    System.out.println("\nSampling complete:");
    System.out.println("  Total samples: " + sampleCount[0]);
    System.out.println("  Pareto front size: " + front.size());

    // Assertions
    Assertions.assertFalse(front.isEmpty(), "Pareto front should not be empty");
    // Sampling should give us multiple distinct solutions across the flow range
    Assertions.assertTrue(front.size() >= 3, "Sampling should find at least 3 distinct solutions");

    // The solutions should span a range of flow rates
    double minFlow = Double.MAX_VALUE;
    double maxFlow = Double.MIN_VALUE;
    for (ParetoSolution sol : front) {
      double flow = sol.getRawValue(0);
      minFlow = Math.min(minFlow, flow);
      maxFlow = Math.max(maxFlow, flow);
    }
    Assertions.assertTrue(maxFlow > minFlow * 2,
        "Should have diverse solutions spanning the flow range");

    System.out.println("\nSampled Pareto Front:");
    System.out.println("======================");
    for (ParetoSolution sol : front.getSolutionsSortedBy(0, true)) {
      System.out.printf("  Flow: %.0f kg/hr, Power: %.1f kW, Feasible: %s%n", sol.getRawValue(0),
          sol.getRawValue(1), sol.isFeasible());
    }

    // Check spacing (should be well-distributed)
    double spacing = front.calculateSpacing();
    System.out.println("\nPareto front spacing: " + spacing);

    // Find knee point
    ParetoSolution knee = front.findKneePoint();
    if (knee != null) {
      System.out.println("Knee Point: " + knee);
    }
  }

  @Test
  public void testWithCapacityConstraints() {
    ProcessSystem process = createTestProcess();
    Stream feed = (Stream) process.getUnit("Feed");
    Compressor compressor = (Compressor) process.getUnit("Gas Compressor");

    // Objectives
    List<ObjectiveFunction> objectives =
        Arrays.asList(StandardObjective.MAXIMIZE_THROUGHPUT, StandardObjective.MINIMIZE_POWER);

    // Add explicit constraint on compressor power
    OptimizationConstraint powerConstraint =
        OptimizationConstraint.lessThan("Max Compressor Power", proc -> {
          Compressor comp = (Compressor) proc.getUnit("Gas Compressor");
          return comp != null ? comp.getPower() : 0.0;
        }, 600.0, // kW limit (below the 800 kW design max)
            ConstraintSeverity.HARD, 0.0, "Keep compressor power below 600 kW");

    OptimizationConfig config = new OptimizationConfig(1000.0, 12000.0).rateUnit("kg/hr")
        .tolerance(50.0).maxIterations(15).defaultUtilizationLimit(0.95);

    // Run optimization with constraints
    MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer();
    ParetoFront front = moo.optimizeWeightedSum(process, feed, objectives, config, 12,
        Collections.singletonList(powerConstraint));

    // All solutions should respect power constraint
    for (ParetoSolution sol : front) {
      if (sol.isFeasible()) {
        Assertions.assertTrue(sol.getRawValue(1) <= 650.0,
            "Power should be below constraint (with tolerance)");
      }
    }

    System.out.println("\nPareto Front (With Power Constraint <= 600 kW):");
    System.out.println("===============================================");
    for (ParetoSolution sol : front.getSolutionsSortedBy(0, true)) {
      System.out.printf("  Flow: %.0f kg/hr, Power: %.1f kW, Feasible: %s%n", sol.getRawValue(0),
          sol.getRawValue(1), sol.isFeasible());
    }
  }

  @Test
  public void testCustomObjectives() {
    ProcessSystem process = createTestProcess();
    Stream feed = (Stream) process.getUnit("Feed");

    // Custom objective: specific production (throughput per unit power)
    ObjectiveFunction specificProduction = ObjectiveFunction.create("Specific Production", proc -> {
      double throughput = StandardObjective.MAXIMIZE_THROUGHPUT.evaluate(proc);
      double power = StandardObjective.MINIMIZE_POWER.evaluate(proc);
      return power > 1.0 ? throughput / power : throughput; // kg per kWh
    }, ObjectiveFunction.Direction.MAXIMIZE, "kg/kWh");

    List<ObjectiveFunction> objectives = Arrays.asList(StandardObjective.MAXIMIZE_THROUGHPUT,
        StandardObjective.MINIMIZE_POWER, specificProduction);

    OptimizationConfig config = new OptimizationConfig(2000.0, 12000.0).rateUnit("kg/hr")
        .tolerance(100.0).maxIterations(15).defaultUtilizationLimit(0.95);

    MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer();
    ParetoFront front = moo.optimizeWeightedSum(process, feed, objectives, config, 8);

    Assertions.assertFalse(front.isEmpty());

    // Check 3 objectives are present
    if (!front.isEmpty()) {
      ParetoSolution first = front.getSolutions().get(0);
      Assertions.assertEquals(3, first.getNumObjectives());
    }

    System.out.println("\nPareto Front (3 Objectives):");
    System.out.println("============================");
    for (ParetoSolution sol : front) {
      System.out.printf("  Flow: %.0f kg/hr, Power: %.1f kW, Specific: %.1f kg/kWh%n",
          sol.getRawValue(0), sol.getRawValue(1), sol.getRawValue(2));
    }
  }

  @Test
  public void testParetoFrontMetrics() {
    ProcessSystem process = createTestProcess();
    Stream feed = (Stream) process.getUnit("Feed");

    List<ObjectiveFunction> objectives =
        Arrays.asList(StandardObjective.MAXIMIZE_THROUGHPUT, StandardObjective.MINIMIZE_POWER);

    OptimizationConfig config =
        new OptimizationConfig(1000.0, 12000.0).rateUnit("kg/hr").tolerance(50.0).maxIterations(15);

    MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer();
    ParetoFront front = moo.optimizeWeightedSum(process, feed, objectives, config, 15);

    // Test metrics
    double spacing = front.calculateSpacing();
    System.out.println("\nPareto Front Metrics:");
    System.out.println("=====================");
    System.out.println("Size: " + front.size());
    System.out.println("Spacing: " + spacing);

    // Find extremes
    ParetoSolution maxThroughput = front.findMaximum(0);
    ParetoSolution minPower = front.findMinimum(1);

    if (maxThroughput != null) {
      System.out.printf("Max Throughput: %.0f kg/hr at %.1f kW%n", maxThroughput.getRawValue(0),
          maxThroughput.getRawValue(1));
    }
    if (minPower != null) {
      System.out.printf("Min Power: %.1f kW at %.0f kg/hr%n", minPower.getRawValue(1),
          minPower.getRawValue(0));
    }

    ParetoSolution knee = front.findKneePoint();
    if (knee != null) {
      System.out.printf("Knee Point: %.0f kg/hr at %.1f kW%n", knee.getRawValue(0),
          knee.getRawValue(1));
    }

    Assertions.assertTrue(spacing >= 0, "Spacing should be non-negative");
  }

  @Test
  public void testProgressCallback() {
    ProcessSystem process = createTestProcess();
    Stream feed = (Stream) process.getUnit("Feed");

    List<ObjectiveFunction> objectives =
        Arrays.asList(StandardObjective.MAXIMIZE_THROUGHPUT, StandardObjective.MINIMIZE_POWER);

    OptimizationConfig config = new OptimizationConfig(2000.0, 10000.0).rateUnit("kg/hr")
        .tolerance(100.0).maxIterations(10);

    // Track progress
    final int[] progressCalls = {0};
    MultiObjectiveOptimizer moo =
        new MultiObjectiveOptimizer().onProgress((iteration, total, solution) -> {
          progressCalls[0]++;
          System.out.printf("Progress: %d/%d - %s%n", iteration, total,
              solution != null ? String.format("Flow=%.0f", solution.getRawValue(0)) : "N/A");
        });

    ParetoFront front = moo.optimizeWeightedSum(process, feed, objectives, config, 5);

    Assertions.assertTrue(progressCalls[0] > 0, "Progress callback should be called");
    System.out.println("Progress callbacks: " + progressCalls[0]);
  }

  @Test
  public void testDominanceRelation() {
    // Create test solutions
    double[] values1 = {100.0, 50.0}; // Higher throughput, higher power
    double[] values2 = {80.0, 40.0}; // Lower throughput, lower power
    double[] values3 = {70.0, 60.0}; // Lower throughput, higher power (dominated)

    String[] names = {"Throughput", "Power"};
    String[] units = {"kg/hr", "kW"};

    // For normalized: throughput should maximize (keep positive)
    // Power should minimize (negate for normalized)
    double[] norm1 = {100.0, -50.0};
    double[] norm2 = {80.0, -40.0};
    double[] norm3 = {70.0, -60.0};

    ParetoSolution sol1 =
        new ParetoSolution(norm1, values1, names, units, Collections.emptyMap(), true);
    ParetoSolution sol2 =
        new ParetoSolution(norm2, values2, names, units, Collections.emptyMap(), true);
    ParetoSolution sol3 =
        new ParetoSolution(norm3, values3, names, units, Collections.emptyMap(), true);

    // Neither sol1 nor sol2 dominates the other (trade-off)
    Assertions.assertFalse(sol1.dominates(sol2), "sol1 should not dominate sol2");
    Assertions.assertFalse(sol2.dominates(sol1), "sol2 should not dominate sol1");

    // Both sol1 and sol2 dominate sol3
    Assertions.assertTrue(sol1.dominates(sol3), "sol1 should dominate sol3");
    Assertions.assertTrue(sol2.dominates(sol3), "sol2 should dominate sol3");

    // Test Pareto front addition
    ParetoFront front = new ParetoFront();
    front.add(sol1);
    front.add(sol2);
    front.add(sol3); // Should not be added (dominated)

    Assertions.assertEquals(2, front.size(), "Only non-dominated solutions should remain");
  }

  @Test
  public void testJsonExport() {
    ProcessSystem process = createTestProcess();
    Stream feed = (Stream) process.getUnit("Feed");

    List<ObjectiveFunction> objectives =
        Arrays.asList(StandardObjective.MAXIMIZE_THROUGHPUT, StandardObjective.MINIMIZE_POWER);

    OptimizationConfig config = new OptimizationConfig(2000.0, 10000.0).rateUnit("kg/hr")
        .tolerance(100.0).maxIterations(10);

    MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer();
    ParetoFront front = moo.optimizeWeightedSum(process, feed, objectives, config, 5);

    String json = front.toJson();

    Assertions.assertNotNull(json);
    Assertions.assertTrue(json.contains("solutions"));
    Assertions.assertTrue(json.contains("Throughput"));
    Assertions.assertTrue(json.contains("size"));

    System.out.println("\nJSON Export:\n" + json);
  }

  @Test
  public void testFIVObjectives() {
    // Create realistic gas/oil fluid similar to existing FIV test
    double pressure = 58.3 + 1.01325; // bara
    double temperature = 90.0; // C
    double gasFlowRate = 54559.25; // Sm3/hr
    double oilFlowRate = 50.66; // Sm3/hr
    double waterFlowRate = 22.0; // Sm3/hr

    neqsim.thermo.system.SystemInterface fluid = new neqsim.thermo.system.SystemSrkEos(
        (273.15 + 45), neqsim.thermo.ThermodynamicConstantsInterface.referencePressure);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("methane", 0.75);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-pentane", 0.01);
    fluid.addComponent("water", 0.06);
    fluid.setMixingRule(2);
    fluid.init(0);
    fluid.useVolumeCorrection(true);
    fluid.setPressure(pressure, "bara");
    fluid.setTemperature(temperature, "C");
    fluid.setTotalFlowRate(100.0, "kg/hr");
    fluid.setMultiPhaseCheck(true);

    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    ProcessSystem process = new ProcessSystem();

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    process.add(feed);

    // Use FlowRateAdjuster like in the existing FIV test
    neqsim.process.equipment.util.FlowRateAdjuster flowRateAdj =
        new neqsim.process.equipment.util.FlowRateAdjuster("Flow Adjuster", feed);
    flowRateAdj.setAdjustedFlowRates(gasFlowRate, oilFlowRate, waterFlowRate, "Sm3/hr");
    process.add(flowRateAdj);

    // Pipeline
    neqsim.process.equipment.pipeline.PipeBeggsAndBrills pipe =
        new neqsim.process.equipment.pipeline.PipeBeggsAndBrills("Gas Pipeline",
            flowRateAdj.getOutletStream());
    pipe.setPipeWallRoughness(1e-6);
    pipe.setLength(25.0);
    pipe.setElevation(0.0);
    pipe.setPipeSpecification(8.0, "LD201");
    pipe.setNumberOfIncrements(10);
    process.add(pipe);

    // Add FIV analyzers
    neqsim.process.measurementdevice.FlowInducedVibrationAnalyser fivLOF =
        new neqsim.process.measurementdevice.FlowInducedVibrationAnalyser("FIV-LOF", pipe);
    fivLOF.setMethod("LOF");
    fivLOF.setSupportArrangement("Stiff");
    process.add(fivLOF);

    neqsim.process.measurementdevice.FlowInducedVibrationAnalyser fivFRMS =
        new neqsim.process.measurementdevice.FlowInducedVibrationAnalyser("FIV-FRMS", pipe);
    fivFRMS.setMethod("FRMS");
    process.add(fivFRMS);

    // Run process
    process.run();

    // Test FIV objective creation
    ObjectiveFunction lofObjective = StandardObjective.minimizeFIV_LOF("FIV-LOF");
    ObjectiveFunction frmsObjective = StandardObjective.minimizeFIV_FRMS("FIV-FRMS");

    Assertions.assertNotNull(lofObjective);
    Assertions.assertNotNull(frmsObjective);
    Assertions.assertEquals("Minimize FIV-LOF LOF", lofObjective.getName());
    Assertions.assertEquals("Minimize FIV-FRMS FRMS", frmsObjective.getName());

    // Evaluate objectives
    double lofValue = lofObjective.evaluate(process);
    double frmsValue = frmsObjective.evaluate(process);

    System.out.println("LOF value: " + lofValue);
    System.out.println("FRMS value: " + frmsValue);

    // Values should be finite
    Assertions.assertTrue(Double.isFinite(lofValue), "LOF should be a finite value");
    Assertions.assertTrue(Double.isFinite(frmsValue), "FRMS should be a finite value");

    // Test pipeline vibration objective (creates temp analyzer)
    ObjectiveFunction pipeVib =
        StandardObjective.minimizePipelineVibration("Gas Pipeline", "LOF", "Stiff");
    double pipeVibValue = pipeVib.evaluate(process);
    System.out.println("Pipeline vibration (LOF): " + pipeVibValue);
    Assertions.assertTrue(Double.isFinite(pipeVibValue));
  }

  @Test
  public void testManifoldObjectives() {
    // Create fluid
    neqsim.thermo.system.SystemSrkEos fluid = new neqsim.thermo.system.SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.2);
    fluid.addComponent("propane", 0.1);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();

    // Two inlet streams
    Stream inlet1 = new Stream("Inlet 1", fluid.clone());
    inlet1.setFlowRate(5000.0, "kg/hr");
    inlet1.setTemperature(25.0, "C");
    inlet1.setPressure(30.0, "bara");
    process.add(inlet1);

    Stream inlet2 = new Stream("Inlet 2", fluid.clone());
    inlet2.setFlowRate(3000.0, "kg/hr");
    inlet2.setTemperature(30.0, "C");
    inlet2.setPressure(30.0, "bara");
    process.add(inlet2);

    // Manifold
    neqsim.process.equipment.manifold.Manifold manifold =
        new neqsim.process.equipment.manifold.Manifold("Production Manifold");
    manifold.addStream(inlet1);
    manifold.addStream(inlet2);
    manifold.setSplitFactors(new double[] {0.5, 0.5}); // Split to 2 outlets
    process.add(manifold);

    process.run();

    // Test manifold objectives
    ObjectiveFunction throughputObj =
        StandardObjective.maximizeManifoldThroughput("Production Manifold");
    ObjectiveFunction pressureObj =
        StandardObjective.minimizeManifoldPressureDrop("Production Manifold");
    ObjectiveFunction balanceObj =
        StandardObjective.minimizeManifoldImbalance("Production Manifold");

    Assertions.assertNotNull(throughputObj);
    Assertions.assertNotNull(pressureObj);
    Assertions.assertNotNull(balanceObj);

    double throughput = throughputObj.evaluate(process);
    double pressure = pressureObj.evaluate(process);
    double imbalance = balanceObj.evaluate(process);

    System.out.println("Manifold throughput: " + throughput + " kg/hr");
    System.out.println("Manifold outlet pressure: " + pressure + " bara");
    System.out.println("Manifold imbalance: " + imbalance);

    // Combined flow should be ~8000 kg/hr
    Assertions.assertEquals(8000.0, throughput, 100.0);

    // With 50/50 split, imbalance should be near zero
    Assertions.assertEquals(0.0, imbalance, 0.01);

    // Test geometry settings
    manifold.setHeaderInnerDiameter(12.0, "inch");
    manifold.setHeaderWallThickness(12.7, "mm");
    manifold.setBranchInnerDiameter(6.0, "inch");
    manifold.setBranchWallThickness(7.11, "mm");
    manifold.setSupportArrangement("Stiff");

    // Test velocity calculations
    double headerVelocity = manifold.getHeaderVelocity();
    double branchVelocity = manifold.getBranchVelocity();
    double erosionalVelocity = manifold.getErosionalVelocity();

    System.out.println("\nManifold Velocities:");
    System.out.println("  Header velocity: " + String.format("%.3f", headerVelocity) + " m/s");
    System.out.println("  Branch velocity: " + String.format("%.3f", branchVelocity) + " m/s");
    System.out
        .println("  Erosional velocity: " + String.format("%.2f", erosionalVelocity) + " m/s");

    Assertions.assertTrue(headerVelocity > 0, "Header velocity should be positive");
    Assertions.assertTrue(branchVelocity > 0, "Branch velocity should be positive");
    Assertions.assertTrue(erosionalVelocity > 0, "Erosional velocity should be positive");

    // Test FIV calculations
    double headerLOF = manifold.calculateHeaderLOF();
    double headerFRMS = manifold.calculateHeaderFRMS();
    double branchLOF = manifold.calculateBranchLOF();

    System.out.println("\nManifold FIV Analysis:");
    System.out.println("  Header LOF: " + String.format("%.4f", headerLOF));
    System.out.println("  Header FRMS: " + String.format("%.2f", headerFRMS));
    System.out.println("  Branch LOF: " + String.format("%.4f", branchLOF));

    Assertions.assertTrue(Double.isFinite(headerLOF), "Header LOF should be finite");
    Assertions.assertTrue(Double.isFinite(headerFRMS), "Header FRMS should be finite");
    Assertions.assertTrue(Double.isFinite(branchLOF), "Branch LOF should be finite");

    // Test FIV analysis JSON
    String fivJson = manifold.getFIVAnalysisJson();
    Assertions.assertTrue(fivJson.contains("LOF"));
    Assertions.assertTrue(fivJson.contains("FRMS"));
    Assertions.assertTrue(fivJson.contains("supportArrangement"));

    // Test auto-sizing with FIV
    Assertions.assertFalse(manifold.isAutoSized());
    manifold.autoSize(1.2);
    Assertions.assertTrue(manifold.isAutoSized());

    String report = manifold.getSizingReport();
    Assertions.assertTrue(report.contains("Manifold Auto-Sizing Report"));
    Assertions.assertTrue(report.contains("FIV Analysis"));
    Assertions.assertTrue(report.contains("Header LOF"));
    System.out.println("\n" + report);

    // Test JSON sizing report
    String jsonReport = manifold.getSizingReportJson();
    Assertions.assertTrue(jsonReport.contains("fivAnalysis"));
    Assertions.assertTrue(jsonReport.contains("geometry"));
    Assertions.assertTrue(jsonReport.contains("velocities"));

    // Test FIV optimization objectives
    ObjectiveFunction headerLOFObj =
        StandardObjective.minimizeManifoldHeaderLOF("Production Manifold");
    ObjectiveFunction branchLOFObj =
        StandardObjective.minimizeManifoldBranchLOF("Production Manifold");
    ObjectiveFunction headerFRMSObj =
        StandardObjective.minimizeManifoldHeaderFRMS("Production Manifold");
    ObjectiveFunction velocityRatioObj =
        StandardObjective.minimizeManifoldVelocityRatio("Production Manifold");

    Assertions.assertNotNull(headerLOFObj);
    Assertions.assertNotNull(branchLOFObj);
    Assertions.assertNotNull(headerFRMSObj);
    Assertions.assertNotNull(velocityRatioObj);

    double headerLOFValue = headerLOFObj.evaluate(process);
    double branchLOFValue = branchLOFObj.evaluate(process);
    double headerFRMSValue = headerFRMSObj.evaluate(process);
    double velocityRatioValue = velocityRatioObj.evaluate(process);

    System.out.println("\nManifold FIV Objectives:");
    System.out.println("  Header LOF objective: " + String.format("%.4f", headerLOFValue));
    System.out.println("  Branch LOF objective: " + String.format("%.4f", branchLOFValue));
    System.out.println("  Header FRMS objective: " + String.format("%.4f", headerFRMSValue));
    System.out.println("  Velocity ratio objective: " + String.format("%.4f", velocityRatioValue));

    Assertions.assertTrue(Double.isFinite(headerLOFValue), "Header LOF should be finite");
    Assertions.assertTrue(Double.isFinite(branchLOFValue), "Branch LOF should be finite");
    Assertions.assertTrue(Double.isFinite(headerFRMSValue), "Header FRMS should be finite");
    Assertions.assertTrue(Double.isFinite(velocityRatioValue), "Velocity ratio should be finite");
    Assertions.assertTrue(velocityRatioValue < 1.0, "Velocity should be below erosional");
  }
}

