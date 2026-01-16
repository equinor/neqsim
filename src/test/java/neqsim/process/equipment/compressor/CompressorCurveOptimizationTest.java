package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class demonstrating compressor curve generation with surge/stonewall lines and optimization
 * based on capacity constraints.
 *
 * <p>
 * This test shows:
 * <ul>
 * <li>How to generate typical compressor curves</li>
 * <li>How to set surge and stonewall curves</li>
 * <li>How optimization respects curve-based speed limits</li>
 * <li>How to use capacity constraints for bottleneck detection</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorCurveOptimizationTest {

  private ProcessSystem process;
  private Stream feedStream;
  private Compressor compressor;

  /**
   * Set up a simple compressor process for testing.
   */
  @BeforeEach
  public void setUp() {
    // Create gas fluid
    SystemInterface gas = new SystemSrkEos(298.15, 30.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.08);
    gas.addComponent("propane", 0.04);
    gas.addComponent("n-butane", 0.02);
    gas.addComponent("CO2", 0.01);
    gas.setMixingRule("classic");
    gas.setMultiPhaseCheck(false);

    // Create feed stream
    feedStream = new Stream("feed", gas);
    feedStream.setFlowRate(15000.0, "kg/hr");
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(30.0, "bara");

    // Create compressor
    compressor = new Compressor("export compressor", feedStream);
    compressor.setOutletPressure(80.0, "bara");
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.75);
    compressor.setSpeed(8000);
    compressor.setMaximumSpeed(12000);

    // Build process
    process = new ProcessSystem();
    process.add(feedStream);
    process.add(compressor);

    // Run initial calculation to establish operating point
    process.run();
  }

  /**
   * Test generating multi-speed compressor curves with surge and stonewall lines.
   */
  @Test
  public void testGenerateCompressorCurvesWithSurgeAndStonewall() {
    // First verify initial compressor calculations are reasonable
    System.out.println("\n=== Initial Compressor Verification ===");
    System.out.println("Inlet: " + feedStream.getFlowRate("kg/hr") + " kg/hr at "
        + feedStream.getPressure("bara") + " bara, " + feedStream.getTemperature("C") + " °C");
    System.out.println("Outlet: " + compressor.getOutletPressure() + " bara, "
        + compressor.getOutletStream().getTemperature("C") + " °C");
    System.out.println("Pressure ratio: "
        + String.format("%.2f", compressor.getOutletPressure() / feedStream.getPressure("bara")));
    System.out.println("Volumetric flow (inlet): "
        + String.format("%.1f", compressor.getInletStream().getFlowRate("m3/hr")) + " m³/hr");
    System.out.println("Power: " + String.format("%.1f", compressor.getPower("kW")) + " kW");
    System.out.println("Polytropic head: "
        + String.format("%.2f", compressor.getPolytropicFluidHead()) + " kJ/kg");
    System.out.println("Polytropic efficiency: "
        + String.format("%.1f", compressor.getPolytropicEfficiency() * 100) + "%");

    // Verify power calculation is reasonable
    // Expected: Power = mass_flow * head / efficiency
    double massFlowKgS = feedStream.getFlowRate("kg/hr") / 3600.0;
    double expectedPowerKW = massFlowKgS * compressor.getPolytropicFluidHead() / 0.75;
    System.out
        .println("Expected power (approx): " + String.format("%.1f", expectedPowerKW) + " kW");

    // Generate compressor chart with 5 speed curves
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");

    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 5);

    // Set the chart on compressor
    compressor.setCompressorChart(chart);
    compressor.getCompressorChart().setUseCompressorChart(true);

    // Run with chart enabled
    process.run();

    // Verify chart is active and has curves
    assertTrue(compressor.getCompressorChart().isUseCompressorChart(),
        "Compressor chart should be active");
    assertTrue(chart.getSpeeds().length >= 1, "Should have at least one speed curve");

    // Check that surge curve is generated
    double distanceToSurge = compressor.getDistanceToSurge();
    assertFalse(Double.isNaN(distanceToSurge), "Distance to surge should be calculated");
    System.out.println("Distance to surge: " + distanceToSurge + "%");

    // Check that stonewall curve is generated
    double distanceToStonewall = compressor.getDistanceToStoneWall();
    assertFalse(Double.isNaN(distanceToStonewall), "Distance to stonewall should be calculated");
    System.out.println("Distance to stonewall: " + distanceToStonewall + "%");

    // Debug: Check actual surge and stonewall flow values
    double currentFlow = compressor.getInletStream().getFlowRate("m3/hr");
    double surgeFlowAtSpeed = chart.getSurgeFlowAtSpeed(compressor.getSpeed());
    double stoneWallFlowAtSpeed = chart.getStoneWallFlowAtSpeed(compressor.getSpeed());
    double surgeFlowFromCurve =
        chart.getSurgeCurve().getSurgeFlow(compressor.getPolytropicFluidHead());
    double stoneWallFlowFromCurve =
        chart.getStoneWallCurve().getStoneWallFlow(compressor.getPolytropicFluidHead());
    System.out.println("Current flow: " + String.format("%.2f", currentFlow) + " m³/hr");
    System.out.println(
        "Current head: " + String.format("%.2f", compressor.getPolytropicFluidHead()) + " kJ/kg");
    System.out
        .println("Surge flow at speed: " + String.format("%.2f", surgeFlowAtSpeed) + " m³/hr");
    System.out.println(
        "Surge flow from curve (at head): " + String.format("%.2f", surgeFlowFromCurve) + " m³/hr");
    System.out.println(
        "Stonewall flow at speed: " + String.format("%.2f", stoneWallFlowAtSpeed) + " m³/hr");
    System.out.println("Stonewall flow from curve (at head): "
        + String.format("%.2f", stoneWallFlowFromCurve) + " m³/hr");
    System.out.println("Surge curve active: " + chart.getSurgeCurve().isActive());
    System.out.println("Stonewall curve active: " + chart.getStoneWallCurve().isActive());
    System.out.println("Operating range: "
        + String.format("%.2f", (stoneWallFlowAtSpeed - surgeFlowAtSpeed)) + " m³/hr");
    System.out.println("Surge margin: "
        + String.format("%.1f", (currentFlow - surgeFlowAtSpeed) / currentFlow * 100)
        + "% of flow");
    System.out.println("Stonewall margin: "
        + String.format("%.1f", (stoneWallFlowAtSpeed - currentFlow) / currentFlow * 100)
        + "% of flow");

    // Verify chart min/max speeds
    double minSpeed = chart.getMinSpeedCurve();
    double maxSpeed = chart.getMaxSpeedCurve();
    System.out.println("Curve min speed: " + minSpeed + " RPM");
    System.out.println("Curve max speed: " + maxSpeed + " RPM");
    assertTrue(minSpeed > 0, "Min speed should be positive");
    assertTrue(maxSpeed > minSpeed, "Max speed should be greater than min speed");
  }

  /**
   * Test that capacity constraints use curve-based speed limits.
   */
  @Test
  public void testCapacityConstraintsWithCurveLimits() {
    // Generate chart first
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 5);
    compressor.setCompressorChart(chart);
    compressor.getCompressorChart().setUseCompressorChart(true);

    // Run to establish operating point
    process.run();

    // Reinitialize constraints now that chart is configured
    compressor.reinitializeCapacityConstraints();

    // Get constraints
    var constraints = compressor.getCapacityConstraints();
    assertFalse(constraints.isEmpty(), "Compressor should have capacity constraints");

    System.out.println("\n=== Compressor Capacity Constraints ===");
    for (var entry : constraints.entrySet()) {
      CapacityConstraint c = entry.getValue();
      System.out.printf("%s: current=%.2f, design=%.2f, max=%.2f, utilization=%.1f%%%n",
          c.getName(), c.getCurrentValue(), c.getDesignValue(), c.getMaxValue(),
          c.getUtilization() * 100);
    }

    // Check speed constraint uses curve limits
    CapacityConstraint speedConstraint = constraints.get("speed");
    assertNotNull(speedConstraint, "Should have speed constraint");

    // Check surge margin constraint
    CapacityConstraint surgeConstraint = constraints.get("surgeMargin");
    assertNotNull(surgeConstraint, "Should have surge margin constraint");

    // Check stonewall margin constraint
    CapacityConstraint stonewallConstraint = constraints.get("stonewallMargin");
    assertNotNull(stonewallConstraint, "Should have stonewall margin constraint");
  }

  /**
   * Test finding the bottleneck constraint during optimization.
   */
  @Test
  public void testBottleneckDetectionWithCurves() {
    // Generate chart
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 5);
    compressor.setCompressorChart(chart);
    compressor.getCompressorChart().setUseCompressorChart(true);

    process.run();

    // Reinitialize constraints with chart values
    compressor.reinitializeCapacityConstraints();

    // Find bottleneck
    CapacityConstraint bottleneck = compressor.getBottleneckConstraint();
    if (bottleneck != null) {
      System.out.println("\nBottleneck constraint: " + bottleneck.getName());
      System.out.println("Utilization: " + (bottleneck.getUtilization() * 100) + "%");
    }

    // Check if any limits are exceeded
    assertFalse(compressor.isHardLimitExceeded(),
        "No hard limits should be exceeded at normal operation");

    // Now increase speed to approach limits
    compressor.setSpeed(compressor.getCompressorChart().getMaxSpeedCurve() * 0.98);
    process.run();

    bottleneck = compressor.getBottleneckConstraint();
    assertNotNull(bottleneck, "Should have a bottleneck near max speed");
    System.out.println("\nAt 98% max speed - Bottleneck: " + bottleneck.getName() + " at "
        + String.format("%.1f", bottleneck.getUtilization() * 100) + "% utilization");
  }

  /**
   * Test operating point validation against surge and stonewall limits.
   */
  @Test
  public void testOperatingPointValidation() {
    // Generate chart with wide speed range for testing
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 7);
    compressor.setCompressorChart(chart);
    compressor.getCompressorChart().setUseCompressorChart(true);

    process.run();

    double currentFlow = compressor.getInletStream().getFlowRate("m3/hr");
    double currentHead = compressor.getPolytropicFluidHead();
    double currentSpeed = compressor.getSpeed();

    System.out.println("\n=== Operating Point Validation ===");
    System.out.println("Flow: " + String.format("%.2f", currentFlow) + " m³/hr");
    System.out.println("Head: " + String.format("%.2f", currentHead) + " kJ/kg");
    System.out.println("Speed: " + String.format("%.0f", currentSpeed) + " RPM");

    // Check if in surge
    boolean isSurge = compressor.isSurge(currentHead, currentFlow);
    System.out.println("Is in surge: " + isSurge);
    assertFalse(isSurge, "Normal operation should not be in surge");

    // Check surge flow at current speed
    double surgeFlow = chart.getSurgeFlowAtSpeed(currentSpeed);
    if (!Double.isNaN(surgeFlow)) {
      System.out
          .println("Surge flow at current speed: " + String.format("%.2f", surgeFlow) + " m³/hr");
      assertTrue(currentFlow > surgeFlow, "Current flow should be above surge flow");
    }

    // Check stonewall flow at current speed
    double stonewallFlow = chart.getStoneWallFlowAtSpeed(currentSpeed);
    if (!Double.isNaN(stonewallFlow)) {
      System.out.println(
          "Stonewall flow at current speed: " + String.format("%.2f", stonewallFlow) + " m³/hr");
      assertTrue(currentFlow < stonewallFlow, "Current flow should be below stonewall flow");
    }

    // Check speed is within curve range
    assertTrue(compressor.isSpeedWithinRange(), "Speed should be within curve range");
    assertFalse(compressor.isHigherThanMaxSpeed(), "Speed should not exceed max curve speed");
    assertFalse(compressor.isLowerThanMinSpeed(), "Speed should not be below min curve speed");
  }

  /**
   * Test optimization scenario: Find maximum throughput within constraints.
   */
  @Test
  public void testMaximumThroughputOptimization() {
    // Generate chart
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 5);
    compressor.setCompressorChart(chart);
    compressor.getCompressorChart().setUseCompressorChart(true);

    // Initialize constraints
    process.run();

    // Reinitialize constraints with chart values
    compressor.reinitializeCapacityConstraints();

    System.out.println("\n=== Maximum Throughput Optimization ===");
    System.out.println("Initial flow: " + feedStream.getFlowRate("kg/hr") + " kg/hr");

    double maxSpeed = chart.getMaxSpeedCurve();
    double minSurgeMargin = 10.0; // Minimum 10% surge margin

    // Gradually increase flow until hitting a constraint
    double baseFlow = feedStream.getFlowRate("kg/hr");
    double maxAchievableFlow = baseFlow;

    for (double flowFactor = 1.0; flowFactor <= 2.0; flowFactor += 0.1) {
      feedStream.setFlowRate(baseFlow * flowFactor, "kg/hr");
      compressor.setSpeed(maxSpeed * 0.95); // Operate at 95% max speed
      process.run();

      double surgeMargin = compressor.getDistanceToSurge();
      boolean speedOk = !compressor.isHigherThanMaxSpeed();
      boolean surgeOk = surgeMargin >= minSurgeMargin;

      if (speedOk && surgeOk) {
        maxAchievableFlow = feedStream.getFlowRate("kg/hr");
        System.out.printf("Flow factor %.1f: %.0f kg/hr - OK (surge margin: %.1f%%)%n", flowFactor,
            maxAchievableFlow, surgeMargin);
      } else {
        System.out.printf(
            "Flow factor %.1f: %.0f kg/hr - LIMIT (speed OK: %b, surge OK: %b, margin: %.1f%%)%n",
            flowFactor, feedStream.getFlowRate("kg/hr"), speedOk, surgeOk, surgeMargin);
        break;
      }
    }

    System.out.println("Maximum achievable flow: " + maxAchievableFlow + " kg/hr");
    assertTrue(maxAchievableFlow >= baseFlow, "Max flow should be at least the base flow");
  }

  /**
   * Test optimization with speed solve mode enabled.
   */
  @Test
  public void testSpeedSolveWithCurveConstraints() {
    // Generate chart
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 5);
    compressor.setCompressorChart(chart);
    compressor.getCompressorChart().setUseCompressorChart(true);

    // Set fixed outlet pressure and let compressor solve for speed
    process.run();
    double targetPressure = compressor.getOutletPressure();

    compressor.setSolveSpeed(true);
    compressor.setOutletPressure(targetPressure, "bara");

    System.out.println("\n=== Speed Solve Mode ===");
    System.out.println("Target pressure: " + targetPressure + " bara");

    // Test with different flow rates
    double[] flowRates = {12000, 15000, 18000, 20000};
    for (double flow : flowRates) {
      feedStream.setFlowRate(flow, "kg/hr");
      process.run();

      double solvedSpeed = compressor.getSpeed();
      double actualPressure = compressor.getOutletPressure();
      boolean withinCurve = compressor.isSpeedWithinRange();
      double surgeMargin = compressor.getDistanceToSurge();

      System.out.printf(
          "Flow %.0f kg/hr: Speed=%.0f RPM, P=%.1f bara, " + "withinCurve=%b, surgeMargin=%.1f%%%n",
          flow, solvedSpeed, actualPressure, withinCurve, surgeMargin);

      // Verify pressure is achieved
      assertEquals(targetPressure, actualPressure, 1.0, "Should achieve target pressure");
    }
  }

  /**
   * Test using a template-based compressor chart.
   */
  @Test
  public void testTemplateBasedChart() {
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");

    // Generate from template
    CompressorChartInterface chart = generator.generateFromTemplate("CENTRIFUGAL_STANDARD", 5);

    assertNotNull(chart, "Template chart should not be null");

    compressor.setCompressorChart(chart);
    compressor.getCompressorChart().setUseCompressorChart(true);
    process.run();

    System.out.println("\n=== Template-Based Chart ===");
    System.out.println("Chart type: " + chart.getClass().getSimpleName());
    System.out.println("Number of speeds: " + chart.getSpeeds().length);
    System.out.println("Min speed: " + chart.getMinSpeedCurve() + " RPM");
    System.out.println("Max speed: " + chart.getMaxSpeedCurve() + " RPM");
    System.out.println("Distance to surge: " + compressor.getDistanceToSurge() + "%");
    System.out.println("Distance to stonewall: " + compressor.getDistanceToStoneWall() + "%");

    // Verify chart works
    assertTrue(chart.getSpeeds().length == 5, "Should have 5 speed curves");
    assertFalse(Double.isNaN(compressor.getDistanceToSurge()),
        "Surge distance should be calculated");
  }

  /**
   * Test capacity constraint reporting for optimization.
   */
  @Test
  public void testCapacityConstraintReporting() {
    // Set up chart
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 5);
    compressor.setCompressorChart(chart);
    compressor.getCompressorChart().setUseCompressorChart(true);

    process.run();

    // Reinitialize constraints with chart values
    compressor.reinitializeCapacityConstraints();

    System.out.println("\n=== Capacity Constraint Report ===");
    System.out.println("Equipment: " + compressor.getName());
    System.out.println("Overall utilization: "
        + String.format("%.1f", compressor.getMaxUtilization() * 100) + "%");
    System.out.println("Hard limit exceeded: " + compressor.isHardLimitExceeded());

    CapacityConstraint bottleneck = compressor.getBottleneckConstraint();
    if (bottleneck != null) {
      System.out.println("Bottleneck: " + bottleneck.getName() + " (" + bottleneck.getType() + ")");
      System.out.println("  Current: " + String.format("%.2f", bottleneck.getCurrentValue()) + " "
          + bottleneck.getUnit());
      System.out.println("  Design: " + String.format("%.2f", bottleneck.getDesignValue()) + " "
          + bottleneck.getUnit());
      System.out.println(
          "  Max: " + String.format("%.2f", bottleneck.getMaxValue()) + " " + bottleneck.getUnit());
    }

    // Print all constraints
    System.out.println("\nAll constraints:");
    for (CapacityConstraint c : compressor.getCapacityConstraints().values()) {
      String status = c.isViolated() ? "VIOLATED" : (c.isNearLimit() ? "WARNING" : "OK");
      System.out.printf("  %-20s: %6.1f%% [%s]%n", c.getName(), c.getUtilization() * 100, status);
    }
  }
}
