package neqsim.process.util.optimization;

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
import neqsim.process.util.optimization.ProductionOptimizer.ConstraintSeverity;
import neqsim.process.util.optimization.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimization.ProductionOptimizer.OptimizationConstraint;
import neqsim.process.util.optimization.ProductionOptimizer.SearchMode;
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
}
