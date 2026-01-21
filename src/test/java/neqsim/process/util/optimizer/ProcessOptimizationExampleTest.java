package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChartGenerator;
import neqsim.process.equipment.compressor.CompressorChartInterface;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer.CapacityRule;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.process.util.optimizer.ProductionOptimizer.UtilizationRecord;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Comprehensive example demonstrating process optimization with separators and compressors.
 * 
 * <p>
 * This test class shows how to:
 * <ul>
 * <li>Build an oil/gas processing train with separators and compressors</li>
 * <li>Configure separator capacity based on gas load factor (K-factor)</li>
 * <li>Set up compressor curves with surge/stonewall limits</li>
 * <li>Use capacity constraints for bottleneck detection</li>
 * <li>Optimize feed flow to maximize throughput within equipment limits</li>
 * </ul>
 * 
 * <p>
 * The process train simulated is a typical offshore oil processing facility:
 * 
 * <pre>
 * Feed --> HP Separator --> Gas Scrubber --> 1st Stage Compressor --> Cooler 
 *              |                                    |
 *              v                                    v
 *         Oil Outlet                    2nd Stage Compressor --> Export
 * </pre>
 * 
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessOptimizationExampleTest {
  private ProcessSystem process;
  private Stream feedStream;
  private Separator hpSeparator;
  private Separator gasScrubber;
  private Compressor firstStageCompressor;
  private Compressor secondStageCompressor;
  private Cooler interStageCooler;

  /**
   * Sets up a typical offshore oil/gas processing train.
   */
  @BeforeEach
  public void setUp() {
    // Create feed fluid - typical North Sea well stream
    SystemInterface feedFluid = new SystemSrkEos(273.15 + 60.0, 80.0);
    feedFluid.addComponent("nitrogen", 0.5);
    feedFluid.addComponent("CO2", 2.0);
    feedFluid.addComponent("methane", 75.0);
    feedFluid.addComponent("ethane", 7.0);
    feedFluid.addComponent("propane", 4.0);
    feedFluid.addComponent("i-butane", 1.5);
    feedFluid.addComponent("n-butane", 2.0);
    feedFluid.addComponent("i-pentane", 1.0);
    feedFluid.addComponent("n-pentane", 1.0);
    feedFluid.addComponent("n-hexane", 2.0);
    feedFluid.addComponent("n-heptane", 2.0);
    feedFluid.addComponent("n-octane", 2.0);
    feedFluid.setMixingRule("classic");
    feedFluid.setMultiPhaseCheck(true);

    // Create process system
    process = new ProcessSystem();

    // Feed stream
    feedStream = new Stream("Feed Stream", feedFluid);
    feedStream.setFlowRate(50000.0, "kg/hr");
    feedStream.setTemperature(60.0, "C");
    feedStream.setPressure(80.0, "bara");
    process.add(feedStream);

    // Inlet valve (choke)
    ThrottlingValve inletValve = new ThrottlingValve("Inlet Choke", feedStream);
    inletValve.setOutletPressure(40.0);
    process.add(inletValve);

    // HP Separator - sized for gas capacity using K-factor
    hpSeparator = new Separator.Builder("HP Separator").inletStream(inletValve.getOutletStream())
        .orientation("horizontal").length(6.0) // 6 meters long
        .diameter(2.4) // 2.4 meters diameter
        .designLiquidLevelFraction(0.5).build();
    hpSeparator.setDesignGasLoadFactor(0.10); // K-factor for horizontal separator
    process.add(hpSeparator);

    // Gas Scrubber - removes liquid droplets before compression
    gasScrubber = new Separator.Builder("Gas Scrubber").inletStream(hpSeparator.getGasOutStream())
        .orientation("vertical").length(4.0).diameter(1.5).designLiquidLevelFraction(0.3).build();
    gasScrubber.setDesignGasLoadFactor(0.07); // Lower K-factor for vertical scrubber
    process.add(gasScrubber);

    // First Stage Compressor
    firstStageCompressor = new Compressor("1st Stage Compressor", gasScrubber.getGasOutStream());
    firstStageCompressor.setOutletPressure(80.0, "bara");
    firstStageCompressor.setPolytropicEfficiency(0.78);
    firstStageCompressor.setUsePolytropicCalc(true);
    process.add(firstStageCompressor);

    // Interstage Cooler
    interStageCooler = new Cooler("Interstage Cooler", firstStageCompressor.getOutletStream());
    interStageCooler.setOutTemperature(273.15 + 40.0);
    process.add(interStageCooler);

    // Second Stage Compressor
    secondStageCompressor =
        new Compressor("2nd Stage Compressor", interStageCooler.getOutletStream());
    secondStageCompressor.setOutletPressure(150.0, "bara");
    secondStageCompressor.setPolytropicEfficiency(0.76);
    secondStageCompressor.setUsePolytropicCalc(true);
    process.add(secondStageCompressor);

    // Run initial simulation
    process.run();

    // Set compressor design power limits based on initial operating point with margin
    // Design power = current power * safety factor (e.g., 1.5x for 50% margin)
    double designMargin = 1.5;
    firstStageCompressor.getMechanicalDesign()
        .setMaxDesignPower(firstStageCompressor.getPower() * designMargin); // Watts
    secondStageCompressor.getMechanicalDesign()
        .setMaxDesignPower(secondStageCompressor.getPower() * designMargin); // Watts
  }

  /**
   * Demonstrates basic bottleneck detection in the process.
   * 
   * <p>
   * Shows how to identify the limiting equipment and its utilization using the capacity constraint
   * framework.
   */
  @Test
  public void testBasicBottleneckDetection() {
    System.out.println("\n=== Basic Bottleneck Detection ===");
    System.out.println("Feed rate: " + feedStream.getFlowRate("kg/hr") + " kg/hr");

    // Find the bottleneck equipment
    ProcessEquipmentInterface bottleneck = process.getBottleneck();

    if (bottleneck != null) {
      System.out.println("\nBottleneck equipment: " + bottleneck.getName());

      double utilization = 0.0;
      if (bottleneck.getCapacityMax() > 0) {
        utilization = bottleneck.getCapacityDuty() / bottleneck.getCapacityMax();
      }
      System.out.printf("Utilization: %.1f%%%n", utilization * 100);
    }

    // Print utilization for all key equipment
    System.out.println("\n--- Equipment Utilization Summary ---");
    printEquipmentUtilization(hpSeparator);
    printEquipmentUtilization(gasScrubber);
    printEquipmentUtilization(firstStageCompressor);
    printEquipmentUtilization(secondStageCompressor);
  }

  /**
   * Demonstrates separator capacity estimation based on Souders-Brown K-factor.
   * 
   * <p>
   * The separator capacity is calculated using: V_max = K * sqrt((rho_liq - rho_gas) / rho_gas)
   */
  @Test
  public void testSeparatorCapacityEstimation() {
    System.out.println("\n=== Separator Capacity Estimation ===");

    // HP Separator capacity analysis
    System.out.println("\n--- HP Separator ---");
    System.out.printf("Internal diameter: %.2f m%n", hpSeparator.getInternalDiameter());
    System.out.printf("Length: %.2f m%n", hpSeparator.getSeparatorLength());
    System.out.printf("Design K-factor: %.3f m/s%n", hpSeparator.getDesignGasLoadFactor());
    System.out.printf("Max allowable gas velocity: %.3f m/s%n",
        hpSeparator.getMaxAllowableGasVelocity());
    System.out.printf("Max allowable gas flow: %.1f m³/s%n",
        hpSeparator.getMaxAllowableGasFlowRate());
    System.out.printf("Current gas flow: %.1f m³/hr%n",
        hpSeparator.getGasOutStream().getFlowRate("m3/hr"));
    System.out.printf("Capacity utilization: %.1f%%%n", hpSeparator.getCapacityUtilization() * 100);

    // Gas Scrubber capacity analysis
    System.out.println("\n--- Gas Scrubber ---");
    System.out.printf("Internal diameter: %.2f m%n", gasScrubber.getInternalDiameter());
    System.out.printf("Length: %.2f m%n", gasScrubber.getSeparatorLength());
    System.out.printf("Design K-factor: %.3f m/s%n", gasScrubber.getDesignGasLoadFactor());
    System.out.printf("Max allowable gas velocity: %.3f m/s%n",
        gasScrubber.getMaxAllowableGasVelocity());
    System.out.printf("Capacity utilization: %.1f%%%n", gasScrubber.getCapacityUtilization() * 100);

    // Verify utilization is reasonable
    double hpUtilization = hpSeparator.getCapacityUtilization();
    if (!Double.isNaN(hpUtilization)) {
      assertTrue(hpUtilization > 0.0, "HP Separator should have positive utilization");
      // Note: utilization can exceed 100% if separator is undersized for the flow
      System.out.printf("HP Separator utilization: %.1f%% (may exceed 100%% if undersized)%n",
          hpUtilization * 100);
    }
  }

  /**
   * Demonstrates compressor curve generation and capacity constraints.
   * 
   * <p>
   * Shows how to:
   * <ul>
   * <li>Generate compressor performance curves</li>
   * <li>Set up surge and stonewall curves</li>
   * <li>Monitor distance to surge/stonewall</li>
   * </ul>
   */
  @Test
  public void testCompressorCurveSetup() {
    System.out.println("\n=== Compressor Curve Setup ===");

    // Generate curves for first stage compressor
    CompressorChartGenerator generator1 = new CompressorChartGenerator(firstStageCompressor);
    generator1.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart1 = generator1.generateCompressorChart("normal curves", 5);
    firstStageCompressor.setCompressorChart(chart1);
    firstStageCompressor.getCompressorChart().setUseCompressorChart(true);

    // Generate curves for second stage compressor
    CompressorChartGenerator generator2 = new CompressorChartGenerator(secondStageCompressor);
    generator2.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart2 = generator2.generateCompressorChart("normal curves", 5);
    secondStageCompressor.setCompressorChart(chart2);
    secondStageCompressor.getCompressorChart().setUseCompressorChart(true);

    // Re-run process with compressor charts
    process.run();

    // Reinitialize capacity constraints with curve values
    firstStageCompressor.reinitializeCapacityConstraints();
    secondStageCompressor.reinitializeCapacityConstraints();

    // Print compressor operating points
    System.out.println("\n--- 1st Stage Compressor ---");
    printCompressorStatus(firstStageCompressor, chart1);

    System.out.println("\n--- 2nd Stage Compressor ---");
    printCompressorStatus(secondStageCompressor, chart2);

    // Print capacity constraints for first stage compressor
    System.out.println("\n--- 1st Stage Compressor Constraints ---");
    Map<String, CapacityConstraint> constraints = firstStageCompressor.getCapacityConstraints();
    for (CapacityConstraint c : constraints.values()) {
      System.out.printf("  %s: current=%.2f, design=%.2f, utilization=%.1f%%%n", c.getName(),
          c.getCurrentValue(), c.getDesignValue(), c.getUtilization() * 100);
    }
  }

  /**
   * Demonstrates throughput optimization using the ProductionOptimizer.
   * 
   * <p>
   * Finds the maximum feed rate while respecting equipment capacity constraints. Note: Compressors
   * have a minimum flow requirement (surge limit) so there may be a minimum feed rate below which
   * operation is infeasible.
   */
  @Test
  public void testThroughputOptimization() {
    System.out.println("\n=== Throughput Optimization ===");

    // Set up compressor charts
    setupCompressorCharts();

    // Create optimizer
    ProductionOptimizer optimizer = new ProductionOptimizer();

    // Configure optimization
    // The compressor charts were generated at 50,000 kg/hr design point
    // Compressors typically have ~15-20% turndown before surge and some stonewall margin
    // Use a realistic search range around the design point that avoids surge
    double lowerBound = 48000.0; // 96% of design (safely above surge)
    double upperBound = 55000.0; // 110% of design (below stonewall)

    // Configure optimization with custom capacity rules for compressors
    // Use CapacityConstrainedEquipment interface for compressor utilization
    // This considers surge margin, stonewall margin, speed limits etc.
    CapacityRule compressorCapacityRule = new CapacityRule(
        // Duty: return max utilization across all constraints (0-1 scale, 1.0 = 100%)
        unit -> {
          if (unit instanceof CapacityConstrainedEquipment) {
            return ((CapacityConstrainedEquipment) unit).getMaxUtilization();
          }
          return unit.getCapacityDuty();
        },
        // Max: return 1.0 since getMaxUtilization already returns a ratio
        unit -> 1.0);

    OptimizationConfig config =
        new OptimizationConfig(lowerBound, upperBound).rateUnit("kg/hr").tolerance(100.0) // 100
                                                                                          // kg/hr
                                                                                          // tolerance
            .maxIterations(50)
            // Add capacity rule for compressors using multi-constraint utilization
            .capacityRuleForType(Compressor.class, compressorCapacityRule)
            // Allow compressors to operate at up to 100% of their capacity
            // (surge margin, speed, power constraints)
            .utilizationLimitForType(Compressor.class, 1.0);

    System.out.printf("Initial feed rate: %.0f kg/hr%n", feedStream.getFlowRate("kg/hr"));
    System.out.printf("Search range: %.0f - %.0f kg/hr%n", lowerBound, upperBound);

    // Run optimization
    OptimizationResult result = optimizer.optimize(process, feedStream, config,
        Collections.emptyList(), Collections.emptyList());

    // Print results
    System.out.println("\n--- Optimization Results ---");
    System.out.printf("Optimal feed rate: %.0f kg/hr%n", result.getOptimalRate());

    if (result.getBottleneck() != null) {
      System.out.printf("Limiting equipment: %s%n", result.getBottleneck().getName());
      System.out.printf("Bottleneck utilization: %.1f%%%n",
          result.getBottleneckUtilization() * 100);
    }

    System.out.println("\n--- Equipment Utilization at Optimum ---");
    for (UtilizationRecord record : result.getUtilizationRecords()) {
      boolean isBottleneck = result.getBottleneck() != null
          && record.getEquipmentName().equals(result.getBottleneck().getName());
      System.out.printf("  %-20s: %6.1f%% (limit: %.0f%%) %s%n", record.getEquipmentName(),
          record.getUtilization() * 100, record.getUtilizationLimit() * 100,
          isBottleneck ? " <-- BOTTLENECK" : "");
    }

    // Show compressor-specific metrics
    System.out.println("\n--- Compressor Status at Optimum ---");
    printCompressorOptimizationStatus(firstStageCompressor);
    printCompressorOptimizationStatus(secondStageCompressor);

    System.out.println("\n========================================");
    System.out.println("       OPTIMIZATION SUMMARY");
    System.out.println("========================================");
    System.out.printf("  Optimal Feed Rate:    %.0f kg/hr%n", result.getOptimalRate());
    System.out.printf("  Bottleneck Equipment: %s%n",
        result.getBottleneck() != null ? result.getBottleneck().getName() : "None");
    System.out.printf("  Bottleneck Util:      %.1f%%%n", result.getBottleneckUtilization() * 100);
    System.out.println("========================================");

    // Verify optimization found a valid solution
    assertTrue(result.getOptimalRate() >= lowerBound, "Optimal rate should be above lower bound");
    assertTrue(result.isFeasible(), "Solution should be feasible");
  }

  /**
   * Prints compressor optimization status including power, surge margin, and constraint
   * utilization.
   */
  private void printCompressorOptimizationStatus(Compressor compressor) {
    System.out.printf("\n  %s:%n", compressor.getName());
    System.out.printf("    Power:           %.1f kW%n", compressor.getPower("kW"));
    System.out.printf("    Inlet Flow:      %.1f m³/hr%n",
        compressor.getInletStream().getFlowRate("m3/hr"));
    System.out.printf("    Polytropic Head: %.2f kJ/kg%n", compressor.getPolytropicFluidHead());
    System.out.printf("    Speed:           %.0f RPM%n", compressor.getSpeed());

    // Surge/stonewall margins
    double surgeMargin = compressor.getDistanceToSurge();
    double stonewallMargin = compressor.getDistanceToStoneWall();
    if (!Double.isNaN(surgeMargin) && !Double.isInfinite(surgeMargin)) {
      System.out.printf("    Surge Margin:    %.1f%%%n", surgeMargin);
    }
    if (!Double.isNaN(stonewallMargin) && !Double.isInfinite(stonewallMargin)) {
      System.out.printf("    Stonewall Margin: %.1f%%%n", stonewallMargin);
    }

    // Show constraint utilizations
    Map<String, CapacityConstraint> constraints = compressor.getCapacityConstraints();
    if (!constraints.isEmpty()) {
      System.out.println("    Constraints:");
      for (CapacityConstraint c : constraints.values()) {
        double util = c.getUtilization();
        if (!Double.isNaN(util) && !Double.isInfinite(util) && util > 0.01) {
          System.out.printf("      %-15s: %6.1f%%%n", c.getName(), util * 100);
        }
      }
    }
  }

  /**
   * Demonstrates finding bottleneck with detailed constraint information.
   * 
   * <p>
   * Uses the multi-constraint framework to identify exactly which constraint is limiting.
   */
  @Test
  public void testDetailedBottleneckAnalysis() {
    System.out.println("\n=== Detailed Bottleneck Analysis ===");

    // Set up compressor charts
    setupCompressorCharts();

    // Increase feed to create a bottleneck situation
    feedStream.setFlowRate(80000.0, "kg/hr");
    process.run();

    System.out.printf("Feed rate: %.0f kg/hr%n", feedStream.getFlowRate("kg/hr"));

    // Find detailed bottleneck information
    BottleneckResult bottleneck = process.findBottleneck();

    if (bottleneck != null) {
      System.out.println("\n--- Bottleneck Details ---");
      System.out.printf("Equipment: %s%n", bottleneck.getEquipmentName());

      CapacityConstraint constraint = bottleneck.getConstraint();
      if (constraint != null) {
        System.out.printf("Constraint: %s%n", constraint.getName());
        System.out.printf("Current value: %.2f%n", constraint.getCurrentValue());
        System.out.printf("Design value: %.2f%n", constraint.getDesignValue());
        System.out.printf("Utilization: %.1f%%%n", constraint.getUtilization() * 100);
        System.out.printf("Is violated: %b%n", constraint.isViolated());
      }
    }

    // Print all near-bottleneck equipment
    System.out.println("\n--- Equipment Status (> 50% utilization) ---");
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit.getCapacityMax() > 0 && unit.getCapacityDuty() > 0) {
        double util = unit.getCapacityDuty() / unit.getCapacityMax();
        if (util > 0.5 && !Double.isInfinite(util) && !Double.isNaN(util)) {
          System.out.printf("  %s: %.1f%%%n", unit.getName(), util * 100);
        }
      }
    }
  }

  /**
   * Demonstrates optimization with varying inlet conditions.
   * 
   * <p>
   * Shows how bottleneck shifts with changing feed composition and conditions.
   */
  @Test
  public void testOptimizationWithVaryingConditions() {
    System.out.println("\n=== Optimization with Varying Conditions ===");

    // Set up compressor charts
    setupCompressorCharts();

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config = new OptimizationConfig(10000.0, 150000.0).rateUnit("kg/hr");

    // Test at different inlet pressures
    double[] inletPressures = {60.0, 80.0, 100.0};

    System.out.println("\n--- Effect of Inlet Pressure ---");
    System.out.printf("%-15s %-15s %-20s %-15s%n", "Inlet P (bara)", "Max Rate (kg/hr)",
        "Bottleneck", "Utilization");
    System.out.println("----------------------------------------------------------------------");

    for (double pressure : inletPressures) {
      // Update inlet pressure
      feedStream.setPressure(pressure, "bara");
      feedStream.setFlowRate(50000.0, "kg/hr"); // Reset flow
      process.run();

      // Optimize
      OptimizationResult result = optimizer.optimize(process, feedStream, config,
          Collections.emptyList(), Collections.emptyList());

      String bottleneckName =
          result.getBottleneck() != null ? result.getBottleneck().getName() : "None";

      System.out.printf("%-15.0f %-15.0f %-20s %-15.1f%%%n", pressure, result.getOptimalRate(),
          bottleneckName, result.getBottleneckUtilization() * 100);
    }
  }

  /**
   * Demonstrates manual flow optimization using bisection method.
   * 
   * <p>
   * Shows how to implement a simple optimization loop checking equipment capacity.
   */
  @Test
  public void testManualFlowOptimization() {
    System.out.println("\n=== Manual Flow Optimization ===");

    // Set up compressor charts
    setupCompressorCharts();

    double lowRate = 10000.0;
    double highRate = 150000.0;
    double tolerance = 500.0;
    double targetUtilization = 0.95;

    System.out.printf("Target utilization: %.0f%%%n", targetUtilization * 100);
    System.out.printf("Search range: %.0f - %.0f kg/hr%n", lowRate, highRate);

    int iteration = 0;
    while ((highRate - lowRate) > tolerance && iteration < 30) {
      double midRate = (lowRate + highRate) / 2.0;

      // Set new flow rate and run
      feedStream.setFlowRate(midRate, "kg/hr");
      process.run();

      // Check if any equipment exceeds target utilization
      double maxUtilization = getMaxSystemUtilization();

      System.out.printf("Iteration %2d: Rate=%.0f kg/hr, Max Util=%.1f%%%n", iteration, midRate,
          maxUtilization * 100);

      if (maxUtilization > targetUtilization) {
        highRate = midRate;
      } else {
        lowRate = midRate;
      }

      iteration++;
    }

    double optimalRate = lowRate;
    feedStream.setFlowRate(optimalRate, "kg/hr");
    process.run();

    System.out.printf("%nOptimal rate: %.0f kg/hr%n", optimalRate);
    System.out.printf("Final max utilization: %.1f%%%n", getMaxSystemUtilization() * 100);

    // Print final equipment status
    System.out.println("\n--- Final Equipment Status ---");
    printEquipmentUtilization(hpSeparator);
    printEquipmentUtilization(gasScrubber);
    printEquipmentUtilization(firstStageCompressor);
    printEquipmentUtilization(secondStageCompressor);
  }

  // ==================== Helper Methods ====================

  /**
   * Sets up compressor performance charts for both compression stages.
   * 
   * <p>
   * Also configures speed constraints with proper max speed values to allow for optimization
   * headroom.
   * </p>
   */
  private void setupCompressorCharts() {
    // First stage compressor chart
    CompressorChartGenerator gen1 = new CompressorChartGenerator(firstStageCompressor);
    gen1.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart1 = gen1.generateCompressorChart("normal curves", 5);
    firstStageCompressor.setCompressorChart(chart1);
    firstStageCompressor.getCompressorChart().setUseCompressorChart(true);

    // Set max speed higher than operating speed to allow headroom
    // Operating speed is design point, max speed is physical limit
    double speed1 = firstStageCompressor.getSpeed();
    firstStageCompressor.setMaximumSpeed(speed1 * 1.15); // 15% speed margin

    // Second stage compressor chart
    CompressorChartGenerator gen2 = new CompressorChartGenerator(secondStageCompressor);
    gen2.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart2 = gen2.generateCompressorChart("normal curves", 5);
    secondStageCompressor.setCompressorChart(chart2);
    secondStageCompressor.getCompressorChart().setUseCompressorChart(true);

    // Set max speed for second stage as well
    double speed2 = secondStageCompressor.getSpeed();
    secondStageCompressor.setMaximumSpeed(speed2 * 1.15); // 15% speed margin

    // Re-run and reinitialize constraints with new max speeds
    process.run();
    firstStageCompressor.reinitializeCapacityConstraints();
    secondStageCompressor.reinitializeCapacityConstraints();
  }

  /**
   * Prints utilization status for a separator.
   * 
   * @param separator the separator to print status for
   */
  private void printEquipmentUtilization(Separator separator) {
    double utilization = separator.getCapacityUtilization();
    if (Double.isNaN(utilization) || Double.isInfinite(utilization)) {
      System.out.printf("%s: N/A (single phase or calculation error)%n", separator.getName());
    } else {
      System.out.printf("%s: %.1f%% (K-factor based)%n", separator.getName(), utilization * 100);
    }
  }

  /**
   * Prints utilization status for a compressor.
   * 
   * @param compressor the compressor to print status for
   */
  private void printEquipmentUtilization(Compressor compressor) {
    double power = compressor.getPower("kW");
    double maxPower =
        compressor.getMechanicalDesign() != null ? compressor.getMechanicalDesign().maxDesignPower
            : 0.0;

    if (maxPower <= 0) {
      System.out.printf("%s: Power=%.1f kW (no design limit set)%n", compressor.getName(), power);
    } else {
      double utilization = power / maxPower;
      System.out.printf("%s: %.1f%% (%.1f / %.1f kW)%n", compressor.getName(), utilization * 100,
          power, maxPower);
    }

    // Also print surge margin if available
    double surgeMargin = compressor.getDistanceToSurge();
    if (!Double.isNaN(surgeMargin)) {
      System.out.printf("  Surge margin: %.1f%%%n", surgeMargin);
    }
  }

  /**
   * Prints detailed compressor operating status.
   * 
   * @param compressor the compressor
   * @param chart the compressor chart
   */
  private void printCompressorStatus(Compressor compressor, CompressorChartInterface chart) {
    System.out.printf("Inlet flow: %.1f m³/hr%n", compressor.getInletStream().getFlowRate("m3/hr"));
    System.out.printf("Polytropic head: %.2f kJ/kg%n", compressor.getPolytropicFluidHead());
    System.out.printf("Polytropic efficiency: %.1f%%%n",
        compressor.getPolytropicEfficiency() * 100);
    System.out.printf("Power: %.1f kW%n", compressor.getPower("kW"));
    System.out.printf("Speed: %.0f RPM%n", compressor.getSpeed());

    // Chart limits
    System.out.printf("Speed range: %.0f - %.0f RPM%n", chart.getMinSpeedCurve(),
        chart.getMaxSpeedCurve());

    // Surge/stonewall margins
    double surgeMargin = compressor.getDistanceToSurge();
    double stonewallMargin = compressor.getDistanceToStoneWall();
    System.out.printf("Distance to surge: %.1f%%%n", surgeMargin);
    System.out.printf("Distance to stonewall: %.1f%%%n", stonewallMargin);

    // Operating status
    boolean inSurge = compressor.isSurge();
    boolean inStonewall = compressor.isStoneWall();
    System.out.printf("In surge: %b, In stonewall: %b%n", inSurge, inStonewall);
  }

  /**
   * Gets the maximum utilization across all equipment in the system.
   * 
   * @return maximum utilization (0.0 to 1.0+)
   */
  private double getMaxSystemUtilization() {
    double maxUtil = 0.0;

    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      double capacity = unit.getCapacityMax();
      double duty = unit.getCapacityDuty();

      if (capacity > 0 && duty > 0) {
        double util = duty / capacity;
        if (!Double.isNaN(util) && !Double.isInfinite(util) && util > maxUtil) {
          maxUtil = util;
        }
      }
    }

    return maxUtil;
  }
}

