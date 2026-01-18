package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChartGenerator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer.ConstraintDirection;
import neqsim.process.util.optimizer.ProductionOptimizer.ConstraintSeverity;
import neqsim.process.util.optimizer.ProductionOptimizer.ObjectiveType;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConstraint;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.process.util.optimizer.ProductionOptimizer.SearchMode;

/**
 * Optimizer for finding maximum flow rate given inlet and outlet pressure boundary conditions.
 *
 * <p>
 * This class provides a simplified interface for lift curve generation and flow rate optimization
 * where pressures (inlet and outlet) are the boundary conditions. It is designed for generating
 * lift curves for reservoir simulation integration (e.g., Eclipse VFP tables) and for capacity
 * analysis of process systems with compressors, pipelines, and other equipment.
 * </p>
 *
 * <p>
 * The optimizer leverages the {@link ProductionOptimizer} framework for the underlying search
 * algorithms (binary search, golden section) and constraint handling.
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li><b>Pressure Boundary Optimization</b> - Find maximum flow rate at given inlet/outlet pressure
 * boundaries while respecting equipment constraints</li>
 * <li><b>Lift Curve Table Generation</b> - Generate 2D tables (VLP/IPR) compatible with Eclipse
 * reservoir simulator format</li>
 * <li><b>Automatic Compressor Configuration</b> - Auto-generates compressor performance charts
 * based on design conditions</li>
 * <li><b>Power Tracking</b> - Reports total compressor power consumption for each operating
 * point</li>
 * <li><b>Minimum Power Optimization</b> - Find operating point that minimizes power while meeting
 * flow requirements</li>
 * <li><b>Utilization Constraints</b> - Respects equipment capacity limits and surge margins</li>
 * </ul>
 *
 * <h2>Typical Use Cases</h2>
 * <ul>
 * <li>Generating VFP tables for Eclipse reservoir simulation coupling</li>
 * <li>Capacity analysis for gas compression systems</li>
 * <li>Export pipeline capacity studies</li>
 * <li>Compressor power optimization</li>
 * <li>Process debottlenecking studies</li>
 * </ul>
 *
 * <h2>Example 1: Simple Pipeline Process</h2>
 * 
 * <pre>
 * // Create a simple pipeline process
 * SystemInterface gas = new SystemSrkEos(288.15, 80.0);
 * gas.addComponent("methane", 0.9);
 * gas.addComponent("ethane", 0.1);
 * gas.setMixingRule("classic");
 * gas.setTotalFlowRate(50000.0, "kg/hr");
 *
 * Stream feed = new Stream("Feed", gas);
 * ThrottlingValve valve = new ThrottlingValve("Valve", feed);
 * valve.setOutletPressure(70.0);
 * Stream outlet = new Stream("Outlet", valve.getOutletStream());
 *
 * ProcessSystem process = new ProcessSystem();
 * process.add(feed);
 * process.add(valve);
 * process.add(outlet);
 * process.run();
 *
 * // Create optimizer and find max flow
 * PressureBoundaryOptimizer optimizer = new PressureBoundaryOptimizer(process, "Feed", "Outlet");
 * optimizer.setMinFlowRate(1000.0);
 * optimizer.setMaxFlowRate(500000.0);
 *
 * OptimizationResult result = optimizer.findMaxFlowRate(80.0, 70.0, "bara");
 * System.out.println("Max flow: " + result.getOptimalRate() + " kg/hr");
 * System.out.println("Feasible: " + result.isFeasible());
 * </pre>
 *
 * <h2>Example 2: Gas Compression System</h2>
 * 
 * <pre>
 * // Create compression system
 * SystemInterface gas = new SystemSrkEos(288.15, 50.0);
 * gas.addComponent("methane", 0.90);
 * gas.addComponent("ethane", 0.07);
 * gas.addComponent("propane", 0.03);
 * gas.setMixingRule("classic");
 * gas.setTotalFlowRate(30000.0, "kg/hr");
 *
 * Stream feed = new Stream("Feed", gas);
 * feed.run();
 *
 * Compressor comp = new Compressor("Compressor", feed);
 * comp.setOutletPressure(100.0);
 * comp.setUsePolytropicCalc(true);
 * comp.setPolytropicEfficiency(0.75);
 *
 * Cooler cooler = new Cooler("Aftercooler", comp.getOutletStream());
 * cooler.setOutTemperature(313.15);
 *
 * Stream export = new Stream("Export", cooler.getOutletStream());
 *
 * ProcessSystem process = new ProcessSystem();
 * process.add(feed);
 * process.add(comp);
 * process.add(cooler);
 * process.add(export);
 * process.run();
 *
 * // Create optimizer with compressor constraints
 * PressureBoundaryOptimizer optimizer = new PressureBoundaryOptimizer(process, feed, export);
 * optimizer.setAutoConfigureCompressors(true); // Auto-generate compressor charts
 * optimizer.setMinSurgeMargin(0.15); // 15% surge margin
 * optimizer.setMaxPowerLimit(5000.0); // 5 MW power limit
 * optimizer.setPressureTolerance(0.05); // 5% pressure tolerance
 *
 * // Find max flow at pressure boundaries
 * OptimizationResult result = optimizer.findMaxFlowRate(50.0, 95.0, "bara");
 * System.out.println("Max flow: " + result.getOptimalRate() + " kg/hr");
 * System.out.println("Total power: " + result.getDecisionVariables().get("totalPower_kW") + " kW");
 * </pre>
 *
 * <h2>Example 3: Generate Eclipse VFP Table</h2>
 * 
 * <pre>
 * // Create optimizer (assuming process is already set up)
 * PressureBoundaryOptimizer optimizer = new PressureBoundaryOptimizer(process, "Feed", "Export");
 * optimizer.setMinFlowRate(5000.0);
 * optimizer.setMaxFlowRate(100000.0);
 *
 * // Define pressure ranges for the lift curve table
 * double[] inletPressures = {50.0, 60.0, 70.0, 80.0}; // Reservoir/wellhead pressures
 * double[] outletPressures = {90.0, 100.0, 110.0, 120.0}; // Export/delivery pressures
 *
 * // Generate the lift curve table
 * LiftCurveTable table = optimizer.generateLiftCurveTable(inletPressures, outletPressures, "bara");
 *
 * // Output in Eclipse VFP format
 * System.out.println(table.toEclipseFormat());
 *
 * // Or get JSON for other integrations
 * System.out.println(table.toJson());
 *
 * // Check table statistics
 * System.out.println("Feasible points: " + table.countFeasiblePoints());
 * </pre>
 *
 * <h2>Example 4: Capacity Curve at Fixed Inlet Pressure</h2>
 * 
 * <pre>
 * // Generate capacity curve showing max flow vs outlet pressure
 * double inletPressure = 70.0;
 * double[] outletPressures = {80.0, 90.0, 100.0, 110.0, 120.0};
 *
 * double[] maxFlowRates = optimizer.generateCapacityCurve(inletPressure, outletPressures, "bara");
 *
 * System.out.println("Capacity Curve at Pin=" + inletPressure + " bara:");
 * for (int i = 0; i &lt; outletPressures.length; i++) {
 *   System.out.println("  Pout=" + outletPressures[i] + " bara -&gt; Max Flow="
 *       + (Double.isNaN(maxFlowRates[i]) ? "Infeasible" : maxFlowRates[i] + " kg/hr"));
 * }
 * </pre>
 *
 * <h2>Example 5: Minimum Power Optimization</h2>
 * 
 * <pre>
 * // Find operating point that minimizes power while achieving target flow
 * double targetFlow = 50000.0; // kg/hr
 * OptimizationResult minPowerResult = optimizer.findMinimumPowerOperatingPoint(50.0, // inlet
 *                                                                                    // pressure
 *     100.0, // outlet pressure
 *     "bara", targetFlow);
 *
 * System.out.println("Minimum power configuration:");
 * System.out.println("  Flow: " + minPowerResult.getOptimalRate() + " kg/hr");
 * System.out
 *     .println("  Power: " + minPowerResult.getDecisionVariables().get("totalPower_kW") + " kW");
 * </pre>
 *
 * <h2>Configuration Options</h2>
 * <table border="1">
 * <caption>Optimizer Configuration Parameters</caption>
 * <tr>
 * <th>Parameter</th>
 * <th>Default</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>minFlowRate</td>
 * <td>0.001</td>
 * <td>Minimum flow rate for search (kg/hr)</td>
 * </tr>
 * <tr>
 * <td>maxFlowRate</td>
 * <td>1e9</td>
 * <td>Maximum flow rate for search (kg/hr)</td>
 * </tr>
 * <tr>
 * <td>tolerance</td>
 * <td>0.001</td>
 * <td>Relative convergence tolerance</td>
 * </tr>
 * <tr>
 * <td>maxIterations</td>
 * <td>50</td>
 * <td>Maximum optimization iterations</td>
 * </tr>
 * <tr>
 * <td>maxUtilization</td>
 * <td>1.0</td>
 * <td>Maximum equipment utilization (1.0 = 100%)</td>
 * </tr>
 * <tr>
 * <td>pressureTolerance</td>
 * <td>0.02</td>
 * <td>Relative tolerance for outlet pressure matching (2%)</td>
 * </tr>
 * <tr>
 * <td>rateUnit</td>
 * <td>"kg/hr"</td>
 * <td>Flow rate unit for results</td>
 * </tr>
 * <tr>
 * <td>autoConfigureCompressors</td>
 * <td>true</td>
 * <td>Auto-generate compressor charts if not configured</td>
 * </tr>
 * <tr>
 * <td>minSurgeMargin</td>
 * <td>0.10</td>
 * <td>Minimum compressor surge margin (10%)</td>
 * </tr>
 * <tr>
 * <td>maxPowerLimit</td>
 * <td>MAX_VALUE</td>
 * <td>Maximum compressor power limit (kW)</td>
 * </tr>
 * </table>
 *
 * <h2>Integration with Eclipse Reservoir Simulator</h2>
 * <p>
 * The {@link LiftCurveTable#toEclipseFormat()} method generates output compatible with Eclipse
 * VFPPROD keyword format. The table can be directly included in Eclipse data files for
 * reservoir-to-surface coupling simulations.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is not thread-safe. Each thread should use its own instance of
 * PressureBoundaryOptimizer with its own ProcessSystem clone.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see ProductionOptimizer
 * @see LiftCurveTable
 * @see CompressorChartGenerator
 */
public class PressureBoundaryOptimizer implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PressureBoundaryOptimizer.class);

  // Core components
  private final ProcessSystem process;
  private final StreamInterface feedStream;
  private final StreamInterface outletStream;
  private ProductionOptimizer productionOptimizer;

  // Configuration
  private double minFlowRate = 0.001; // kg/hr
  private double maxFlowRate = 1e9; // kg/hr
  private double tolerance = 0.001; // Relative tolerance
  private int maxIterations = 50;
  private double maxUtilization = 1.0; // 100% default
  private double pressureTolerance = 0.02; // 2% pressure tolerance
  private String rateUnit = "kg/hr";

  // Compressor configuration
  private boolean autoConfigureCompressors = true;
  private double minSurgeMargin = 0.10;
  private double maxPowerLimit = Double.MAX_VALUE;
  private double maxSpeedLimit = Double.MAX_VALUE;
  private double minSpeedLimit = 0.0;

  // Internal state
  private List<Compressor> compressors;
  private boolean compressorsConfigured = false;

  /**
   * Creates a pressure boundary optimizer for a process system.
   *
   * @param process the process system to optimize
   * @param feedStream the feed stream (inlet boundary)
   * @param outletStream the outlet stream (outlet boundary)
   */
  public PressureBoundaryOptimizer(ProcessSystem process, StreamInterface feedStream,
      StreamInterface outletStream) {
    this.process = Objects.requireNonNull(process, "Process system is required");
    this.feedStream = Objects.requireNonNull(feedStream, "Feed stream is required");
    this.outletStream = Objects.requireNonNull(outletStream, "Outlet stream is required");
    this.productionOptimizer = new ProductionOptimizer();
    this.compressors = findCompressors();
  }

  /**
   * Creates a pressure boundary optimizer for a process system.
   *
   * @param process the process system to optimize
   * @param feedStreamName the name of the feed stream
   * @param outletStreamName the name of the outlet stream
   */
  public PressureBoundaryOptimizer(ProcessSystem process, String feedStreamName,
      String outletStreamName) {
    this.process = Objects.requireNonNull(process, "Process system is required");
    this.feedStream = findStream(process, feedStreamName);
    this.outletStream = findStream(process, outletStreamName);
    this.productionOptimizer = new ProductionOptimizer();
    this.compressors = findCompressors();
  }

  /**
   * Creates a pressure boundary optimizer using an existing ProductionOptimizer.
   *
   * <p>
   * This constructor allows reusing a pre-configured ProductionOptimizer with custom objectives,
   * constraints, and equipment utilization limits already defined.
   * </p>
   *
   * <h3>Example</h3>
   * 
   * <pre>
   * // Configure production optimizer with custom settings
   * ProductionOptimizer prodOpt = new ProductionOptimizer();
   * prodOpt.setEquipmentUtilizationLimit("Compressor1", 0.85);
   * prodOpt.setEquipmentUtilizationLimit("Separator", 0.90);
   *
   * // Create pressure boundary optimizer using the configured optimizer
   * PressureBoundaryOptimizer pbo = new PressureBoundaryOptimizer(process, prodOpt, feed, outlet);
   * pbo.setMinSurgeMargin(0.15);
   *
   * // Run optimization - uses settings from both optimizers
   * OptimizationResult result = pbo.findMaxFlowRate(50.0, 100.0, "bara");
   * </pre>
   *
   * @param process the process system to optimize
   * @param productionOptimizer the pre-configured production optimizer to use
   * @param feedStream the feed stream (inlet boundary)
   * @param outletStream the outlet stream (outlet boundary)
   */
  public PressureBoundaryOptimizer(ProcessSystem process, ProductionOptimizer productionOptimizer,
      StreamInterface feedStream, StreamInterface outletStream) {
    this.process = Objects.requireNonNull(process, "Process system is required");
    this.productionOptimizer =
        Objects.requireNonNull(productionOptimizer, "Production optimizer is required");
    this.feedStream = Objects.requireNonNull(feedStream, "Feed stream is required");
    this.outletStream = Objects.requireNonNull(outletStream, "Outlet stream is required");
    this.compressors = findCompressors();
  }

  /**
   * Creates a pressure boundary optimizer using an existing ProductionOptimizer.
   *
   * <p>
   * This constructor allows reusing a pre-configured ProductionOptimizer with custom objectives,
   * constraints, and equipment utilization limits already defined.
   * </p>
   *
   * @param process the process system to optimize
   * @param productionOptimizer the pre-configured production optimizer to use
   * @param feedStreamName the name of the feed stream
   * @param outletStreamName the name of the outlet stream
   */
  public PressureBoundaryOptimizer(ProcessSystem process, ProductionOptimizer productionOptimizer,
      String feedStreamName, String outletStreamName) {
    this.process = Objects.requireNonNull(process, "Process system is required");
    this.productionOptimizer =
        Objects.requireNonNull(productionOptimizer, "Production optimizer is required");
    this.feedStream = findStream(process, feedStreamName);
    this.outletStream = findStream(process, outletStreamName);
    this.compressors = findCompressors();
  }

  /**
   * Finds a stream by name in the process.
   *
   * @param process the process system
   * @param streamName the stream name
   * @return the stream interface
   */
  private StreamInterface findStream(ProcessSystem process, String streamName) {
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof StreamInterface && unit.getName().equals(streamName)) {
        return (StreamInterface) unit;
      }
    }
    throw new IllegalArgumentException("Stream not found: " + streamName);
  }

  /**
   * Finds all compressors in the process.
   *
   * @return list of compressors
   */
  private List<Compressor> findCompressors() {
    List<Compressor> result = new ArrayList<Compressor>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof Compressor) {
        result.add((Compressor) unit);
      }
    }
    return result;
  }

  /**
   * Configures compressor charts for all compressors in the process.
   *
   * <p>
   * For each compressor without a chart, this method generates a chart based on the current
   * operating conditions using {@link CompressorChartGenerator}.
   * </p>
   */
  public void configureCompressorCharts() {
    if (compressorsConfigured) {
      return;
    }

    for (Compressor comp : compressors) {
      if (comp.getCompressorChart() == null || !comp.getCompressorChart().isUseCompressorChart()) {
        // Run to establish baseline
        process.run();

        // Generate chart
        CompressorChartGenerator generator = new CompressorChartGenerator(comp);
        generator.generateCompressorChart("interpolate");

        // Configure compressor
        comp.setUsePolytropicCalc(true);
        comp.setSolveSpeed(true);
        comp.getCompressorChart().setUseCompressorChart(true);

        logger.info("Configured compressor chart for: {}", comp.getName());
      }
    }

    compressorsConfigured = true;
  }

  /**
   * Finds the maximum flow rate at given pressure boundary conditions.
   *
   * <p>
   * Uses bisection search to find the highest feasible flow rate where:
   * <ul>
   * <li>Inlet pressure matches the specified value</li>
   * <li>Outlet pressure matches the specified target (within tolerance)</li>
   * <li>All equipment utilization is within limits</li>
   * <li>All compressors are within operating envelope (surge, stonewall)</li>
   * </ul>
   *
   * @param inletPressure the inlet pressure boundary condition
   * @param targetOutletPressure the target outlet pressure boundary condition
   * @param pressureUnit the pressure unit (e.g., "bara", "barg")
   * @return optimization result with max flow rate and equipment utilization
   */
  public OptimizationResult findMaxFlowRate(double inletPressure, double targetOutletPressure,
      String pressureUnit) {
    // Auto-configure compressors
    if (autoConfigureCompressors && !compressors.isEmpty()) {
      configureCompressorCharts();
    }

    // Create outlet pressure constraint
    OptimizationConstraint outletPressureConstraint =
        createOutletPressureConstraint(targetOutletPressure, pressureUnit, pressureTolerance);

    // Create compressor constraints
    List<OptimizationConstraint> constraints = new ArrayList<OptimizationConstraint>();
    constraints.add(outletPressureConstraint);
    constraints.addAll(createCompressorConstraints());

    // Create throughput objective
    OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
        proc -> feedStream.getFlowRate(rateUnit), 1.0, ObjectiveType.MAXIMIZE);

    // Configure optimization
    OptimizationConfig config = new OptimizationConfig(minFlowRate, maxFlowRate).rateUnit(rateUnit)
        .tolerance(tolerance * (maxFlowRate - minFlowRate)).maxIterations(maxIterations)
        .searchMode(SearchMode.BINARY_FEASIBILITY).defaultUtilizationLimit(maxUtilization);

    // Set inlet pressure before optimization
    double originalInletPressure = feedStream.getPressure(pressureUnit);
    feedStream.setPressure(inletPressure, pressureUnit);

    try {
      // Run optimization
      OptimizationResult result = productionOptimizer.optimize(process, feedStream, config,
          Collections.singletonList(throughputObjective), constraints);

      // Add power information to decision variables
      Map<String, Double> decisionVars =
          new LinkedHashMap<String, Double>(result.getDecisionVariables());
      decisionVars.put("totalPower_kW", calculateTotalPower());
      decisionVars.put("inletPressure_" + pressureUnit, inletPressure);
      decisionVars.put("outletPressure_" + pressureUnit, outletStream.getPressure(pressureUnit));

      return new OptimizationResult(result.getOptimalRate(), result.getRateUnit(), decisionVars,
          result.getBottleneck(), result.getBottleneckUtilization(), result.getUtilizationRecords(),
          result.getObjectiveValues(), result.getConstraintStatuses(), result.isFeasible(),
          result.getScore(), result.getIterations(), result.getIterationHistory());
    } finally {
      // Restore original inlet pressure
      feedStream.setPressure(originalInletPressure, pressureUnit);
    }
  }

  /**
   * Creates an outlet pressure constraint.
   *
   * @param targetPressure the target outlet pressure
   * @param pressureUnit the pressure unit
   * @param tolerance the relative tolerance
   * @return the optimization constraint
   */
  private OptimizationConstraint createOutletPressureConstraint(double targetPressure,
      String pressureUnit, double tolerance) {
    double pressureTol = targetPressure * tolerance;

    return new OptimizationConstraint("outletPressure",
        proc -> Math.abs(outletStream.getPressure(pressureUnit) - targetPressure), pressureTol,
        ConstraintDirection.LESS_THAN, ConstraintSeverity.HARD, 10.0,
        "Outlet pressure must be within " + (tolerance * 100) + "% of target " + targetPressure
            + " " + pressureUnit);
  }

  /**
   * Creates compressor operating envelope constraints.
   *
   * @return list of constraints for compressor operation
   */
  private List<OptimizationConstraint> createCompressorConstraints() {
    List<OptimizationConstraint> constraints = new ArrayList<OptimizationConstraint>();

    for (final Compressor comp : compressors) {
      // Surge margin constraint
      constraints.add(new OptimizationConstraint(comp.getName() + "_surgeMargin",
          proc -> comp.getDistanceToSurge(), minSurgeMargin, ConstraintDirection.GREATER_THAN,
          ConstraintSeverity.HARD, 10.0,
          "Compressor " + comp.getName() + " must maintain surge margin > " + minSurgeMargin));

      // Power limit constraint
      if (maxPowerLimit < Double.MAX_VALUE) {
        constraints
            .add(new OptimizationConstraint(comp.getName() + "_power", proc -> comp.getPower("kW"),
                maxPowerLimit, ConstraintDirection.LESS_THAN, ConstraintSeverity.HARD, 5.0,
                "Compressor " + comp.getName() + " power must be < " + maxPowerLimit + " kW"));
      }

      // Speed limits
      if (maxSpeedLimit < Double.MAX_VALUE) {
        constraints
            .add(new OptimizationConstraint(comp.getName() + "_maxSpeed", proc -> comp.getSpeed(),
                maxSpeedLimit, ConstraintDirection.LESS_THAN, ConstraintSeverity.HARD, 5.0,
                "Compressor " + comp.getName() + " speed must be < " + maxSpeedLimit + " RPM"));
      }
      if (minSpeedLimit > 0) {
        constraints
            .add(new OptimizationConstraint(comp.getName() + "_minSpeed", proc -> comp.getSpeed(),
                minSpeedLimit, ConstraintDirection.GREATER_THAN, ConstraintSeverity.HARD, 5.0,
                "Compressor " + comp.getName() + " speed must be > " + minSpeedLimit + " RPM"));
      }
    }

    return constraints;
  }

  /**
   * Calculates total compressor power.
   *
   * @return total power in kW
   */
  public double calculateTotalPower() {
    double totalPower = 0.0;
    for (Compressor comp : compressors) {
      totalPower += comp.getPower("kW");
    }
    return totalPower;
  }

  /**
   * Generates a lift curve table for Eclipse VFP format.
   *
   * <p>
   * Creates a 2D table where each cell contains the maximum flow rate achievable for the given
   * inlet pressure (row) and outlet pressure (column) combination.
   * </p>
   *
   * @param inletPressures array of inlet pressures to evaluate
   * @param outletPressures array of outlet pressures to evaluate
   * @param pressureUnit the pressure unit
   * @return lift curve table with flow rates and power data
   */
  public LiftCurveTable generateLiftCurveTable(double[] inletPressures, double[] outletPressures,
      String pressureUnit) {
    int nInlet = inletPressures.length;
    int nOutlet = outletPressures.length;

    double[][] flowRates = new double[nInlet][nOutlet];
    double[][] powers = new double[nInlet][nOutlet];
    String[][] bottlenecks = new String[nInlet][nOutlet];

    for (int i = 0; i < nInlet; i++) {
      for (int j = 0; j < nOutlet; j++) {
        try {
          OptimizationResult result =
              findMaxFlowRate(inletPressures[i], outletPressures[j], pressureUnit);

          if (result.isFeasible()) {
            flowRates[i][j] = result.getOptimalRate();
            powers[i][j] = result.getDecisionVariables().getOrDefault("totalPower_kW", 0.0);
            bottlenecks[i][j] =
                result.getBottleneck() != null ? result.getBottleneck().getName() : "";
          } else {
            flowRates[i][j] = Double.NaN;
            powers[i][j] = Double.NaN;
            bottlenecks[i][j] = "INFEASIBLE";
          }
        } catch (Exception e) {
          logger.warn("Failed at Pin={}, Pout={}: {}", inletPressures[i], outletPressures[j],
              e.getMessage());
          flowRates[i][j] = Double.NaN;
          powers[i][j] = Double.NaN;
          bottlenecks[i][j] = "ERROR";
        }
      }
    }

    return new LiftCurveTable("LiftCurve", inletPressures, outletPressures, flowRates, powers,
        bottlenecks, pressureUnit, rateUnit);
  }

  /**
   * Generates a capacity curve at fixed inlet pressure.
   *
   * @param inletPressure the inlet pressure
   * @param outletPressures array of outlet pressures to evaluate
   * @param pressureUnit the pressure unit
   * @return array of max flow rates for each outlet pressure
   */
  public double[] generateCapacityCurve(double inletPressure, double[] outletPressures,
      String pressureUnit) {
    double[] flowRates = new double[outletPressures.length];

    for (int i = 0; i < outletPressures.length; i++) {
      try {
        OptimizationResult result =
            findMaxFlowRate(inletPressure, outletPressures[i], pressureUnit);
        flowRates[i] = result.isFeasible() ? result.getOptimalRate() : Double.NaN;
      } catch (Exception e) {
        logger.warn("Failed at Pout={}: {}", outletPressures[i], e.getMessage());
        flowRates[i] = Double.NaN;
      }
    }

    return flowRates;
  }

  /**
   * Finds the operating point that minimizes total compressor power for given pressure boundaries.
   *
   * @param inletPressure the inlet pressure
   * @param targetOutletPressure the target outlet pressure
   * @param pressureUnit the pressure unit
   * @param targetFlowRate the target flow rate to achieve
   * @return optimization result
   */
  public OptimizationResult findMinimumPowerOperatingPoint(double inletPressure,
      double targetOutletPressure, String pressureUnit, double targetFlowRate) {
    // Auto-configure compressors
    if (autoConfigureCompressors && !compressors.isEmpty()) {
      configureCompressorCharts();
    }

    // Create constraints
    List<OptimizationConstraint> constraints = new ArrayList<OptimizationConstraint>();
    constraints
        .add(createOutletPressureConstraint(targetOutletPressure, pressureUnit, pressureTolerance));
    constraints.addAll(createCompressorConstraints());

    // Create power minimization objective
    OptimizationObjective powerObjective = new OptimizationObjective("totalPower",
        proc -> calculateTotalPower(), 1.0, ObjectiveType.MINIMIZE);

    // Flow rate constraint - must achieve target flow
    constraints.add(new OptimizationConstraint("minFlowRate",
        proc -> feedStream.getFlowRate(rateUnit), targetFlowRate, ConstraintDirection.GREATER_THAN,
        ConstraintSeverity.HARD, 10.0, "Flow rate must be >= " + targetFlowRate + " " + rateUnit));

    // Configure optimization - search from target flow to max
    OptimizationConfig config =
        new OptimizationConfig(targetFlowRate, maxFlowRate).rateUnit(rateUnit)
            .tolerance(tolerance * (maxFlowRate - targetFlowRate)).maxIterations(maxIterations)
            .searchMode(SearchMode.GOLDEN_SECTION_SCORE).defaultUtilizationLimit(maxUtilization);

    // Set inlet pressure
    double originalInletPressure = feedStream.getPressure(pressureUnit);
    feedStream.setPressure(inletPressure, pressureUnit);

    try {
      return productionOptimizer.optimize(process, feedStream, config,
          Collections.singletonList(powerObjective), constraints);
    } finally {
      feedStream.setPressure(originalInletPressure, pressureUnit);
    }
  }

  // Setters for configuration

  /**
   * Sets the minimum flow rate for optimization.
   *
   * @param minFlowRate the minimum flow rate in the configured unit
   */
  public void setMinFlowRate(double minFlowRate) {
    this.minFlowRate = minFlowRate;
  }

  /**
   * Sets the maximum flow rate for optimization.
   *
   * @param maxFlowRate the maximum flow rate in the configured unit
   */
  public void setMaxFlowRate(double maxFlowRate) {
    this.maxFlowRate = maxFlowRate;
  }

  /**
   * Sets the optimization tolerance.
   *
   * @param tolerance the relative tolerance
   */
  public void setTolerance(double tolerance) {
    this.tolerance = tolerance;
  }

  /**
   * Sets the maximum number of iterations.
   *
   * @param maxIterations the maximum iterations
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Sets the maximum equipment utilization limit.
   *
   * @param maxUtilization the maximum utilization (1.0 = 100%)
   */
  public void setMaxUtilization(double maxUtilization) {
    this.maxUtilization = maxUtilization;
  }

  /**
   * Sets the rate unit for flow rates.
   *
   * @param rateUnit the rate unit (e.g., "kg/hr", "Sm3/day")
   */
  public void setRateUnit(String rateUnit) {
    this.rateUnit = rateUnit;
  }

  /**
   * Sets whether to automatically configure compressor charts.
   *
   * @param autoConfigureCompressors true to auto-configure
   */
  public void setAutoConfigureCompressors(boolean autoConfigureCompressors) {
    this.autoConfigureCompressors = autoConfigureCompressors;
  }

  /**
   * Sets the minimum surge margin for compressors.
   *
   * @param minSurgeMargin the minimum surge margin (e.g., 0.10 for 10%)
   */
  public void setMinSurgeMargin(double minSurgeMargin) {
    this.minSurgeMargin = minSurgeMargin;
  }

  /**
   * Sets the maximum compressor power limit.
   *
   * @param maxPowerLimit the max power in kW
   */
  public void setMaxPowerLimit(double maxPowerLimit) {
    this.maxPowerLimit = maxPowerLimit;
  }

  /**
   * Sets the compressor speed limits.
   *
   * @param minSpeed minimum speed in RPM
   * @param maxSpeed maximum speed in RPM
   */
  public void setSpeedLimits(double minSpeed, double maxSpeed) {
    this.minSpeedLimit = minSpeed;
    this.maxSpeedLimit = maxSpeed;
  }

  /**
   * Sets the pressure tolerance for outlet pressure matching.
   *
   * @param pressureTolerance the relative tolerance (e.g., 0.02 for 2%)
   */
  public void setPressureTolerance(double pressureTolerance) {
    this.pressureTolerance = pressureTolerance;
  }

  /**
   * Gets the list of compressors in the process.
   *
   * @return list of compressors
   */
  public List<Compressor> getCompressors() {
    return new ArrayList<Compressor>(compressors);
  }

  /**
   * Gets the underlying production optimizer.
   *
   * <p>
   * The production optimizer can be used to:
   * </p>
   * <ul>
   * <li>Set custom equipment utilization limits</li>
   * <li>Add additional optimization objectives</li>
   * <li>Configure advanced search algorithms</li>
   * </ul>
   *
   * @return the production optimizer
   */
  public ProductionOptimizer getProductionOptimizer() {
    return productionOptimizer;
  }

  /**
   * Sets the underlying production optimizer.
   *
   * <p>
   * This allows replacing the production optimizer after construction, useful for:
   * </p>
   * <ul>
   * <li>Reusing a pre-configured optimizer across multiple pressure boundary calculations</li>
   * <li>Applying different optimization strategies for different scenarios</li>
   * </ul>
   *
   * @param productionOptimizer the production optimizer to use
   */
  public void setProductionOptimizer(ProductionOptimizer productionOptimizer) {
    this.productionOptimizer =
        Objects.requireNonNull(productionOptimizer, "Production optimizer is required");
  }

  /**
   * Lift curve table containing maximum flow rates for pressure combinations.
   *
   * <p>
   * This table represents the maximum achievable flow rate for each combination of inlet pressure
   * (row) and outlet pressure (column). It is designed for integration with reservoir simulators
   * like Eclipse, which use VFP (Vertical Flow Performance) tables to couple reservoir and surface
   * network models.
   * </p>
   *
   * <h3>Table Structure</h3>
   * <p>
   * The table is organized as a 2D matrix where:
   * </p>
   * <ul>
   * <li><b>Rows</b> - Inlet pressures (e.g., wellhead or reservoir pressures)</li>
   * <li><b>Columns</b> - Outlet pressures (e.g., export or delivery pressures)</li>
   * <li><b>Cells</b> - Maximum feasible flow rate at that pressure combination</li>
   * </ul>
   *
   * <p>
   * Additionally, the table stores:
   * </p>
   * <ul>
   * <li><b>Power matrix</b> - Total compressor power at each operating point (kW)</li>
   * <li><b>Bottleneck matrix</b> - Name of limiting equipment at each point</li>
   * </ul>
   *
   * <h3>Eclipse VFP Format</h3>
   * <p>
   * The {@link #toEclipseFormat()} method generates output compatible with Eclipse VFPPROD keyword.
   * Infeasible points are marked with "1*" (Eclipse default value marker).
   * </p>
   *
   * <h3>Example Usage</h3>
   * 
   * <pre>
   * // Generate table
   * LiftCurveTable table = optimizer.generateLiftCurveTable(new double[] {50.0, 60.0, 70.0}, // inlet
   *                                                                                          // pressures
   *     new double[] {90.0, 100.0, 110.0}, // outlet pressures
   *     "bara");
   *
   * // Access data
   * double maxFlow = table.getFlowRate(0, 1); // Pin=50, Pout=100
   * double power = table.getPower(0, 1); // Power at that point
   * String bottleneck = table.getBottleneck(0, 1); // Limiting equipment
   *
   * // Export formats
   * System.out.println(table.toEclipseFormat()); // Eclipse VFP format
   * System.out.println(table.toJson()); // JSON format
   *
   * // Statistics
   * int feasible = table.countFeasiblePoints();
   * System.out.println(table); // Summary string
   * </pre>
   *
   * @see PressureBoundaryOptimizer#generateLiftCurveTable(double[], double[], String)
   */
  public static class LiftCurveTable implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String tableName;
    private final double[] inletPressures;
    private final double[] outletPressures;
    private final double[][] flowRates;
    private final double[][] powers;
    private final String[][] bottlenecks;
    private final String pressureUnit;
    private final String rateUnit;

    /**
     * Creates a lift curve table.
     *
     * @param tableName the table name
     * @param inletPressures inlet pressure values
     * @param outletPressures outlet pressure values
     * @param flowRates 2D array of flow rates [inlet][outlet]
     * @param powers 2D array of power values [inlet][outlet]
     * @param bottlenecks 2D array of bottleneck equipment names
     * @param pressureUnit the pressure unit
     * @param rateUnit the rate unit
     */
    public LiftCurveTable(String tableName, double[] inletPressures, double[] outletPressures,
        double[][] flowRates, double[][] powers, String[][] bottlenecks, String pressureUnit,
        String rateUnit) {
      this.tableName = tableName;
      this.inletPressures = Arrays.copyOf(inletPressures, inletPressures.length);
      this.outletPressures = Arrays.copyOf(outletPressures, outletPressures.length);
      this.flowRates = copyMatrix(flowRates);
      this.powers = copyMatrix(powers);
      this.bottlenecks = copyMatrix(bottlenecks);
      this.pressureUnit = pressureUnit;
      this.rateUnit = rateUnit;
    }

    private double[][] copyMatrix(double[][] matrix) {
      double[][] copy = new double[matrix.length][];
      for (int i = 0; i < matrix.length; i++) {
        copy[i] = Arrays.copyOf(matrix[i], matrix[i].length);
      }
      return copy;
    }

    private String[][] copyMatrix(String[][] matrix) {
      String[][] copy = new String[matrix.length][];
      for (int i = 0; i < matrix.length; i++) {
        copy[i] = Arrays.copyOf(matrix[i], matrix[i].length);
      }
      return copy;
    }

    /**
     * Gets the table name.
     *
     * @return the table name
     */
    public String getTableName() {
      return tableName;
    }

    /**
     * Gets the inlet pressures.
     *
     * @return copy of inlet pressure array
     */
    public double[] getInletPressures() {
      return Arrays.copyOf(inletPressures, inletPressures.length);
    }

    /**
     * Gets the outlet pressures.
     *
     * @return copy of outlet pressure array
     */
    public double[] getOutletPressures() {
      return Arrays.copyOf(outletPressures, outletPressures.length);
    }

    /**
     * Gets the flow rate at a specific inlet/outlet index.
     *
     * @param inletIndex the inlet pressure index
     * @param outletIndex the outlet pressure index
     * @return the flow rate
     */
    public double getFlowRate(int inletIndex, int outletIndex) {
      return flowRates[inletIndex][outletIndex];
    }

    /**
     * Gets the power at a specific inlet/outlet index.
     *
     * @param inletIndex the inlet pressure index
     * @param outletIndex the outlet pressure index
     * @return the power in kW
     */
    public double getPower(int inletIndex, int outletIndex) {
      return powers[inletIndex][outletIndex];
    }

    /**
     * Gets the bottleneck equipment at a specific inlet/outlet index.
     *
     * @param inletIndex the inlet pressure index
     * @param outletIndex the outlet pressure index
     * @return the bottleneck equipment name
     */
    public String getBottleneck(int inletIndex, int outletIndex) {
      return bottlenecks[inletIndex][outletIndex];
    }

    /**
     * Counts feasible points in the table.
     *
     * @return number of feasible points (non-NaN flow rates)
     */
    public int countFeasiblePoints() {
      int count = 0;
      for (int i = 0; i < inletPressures.length; i++) {
        for (int j = 0; j < outletPressures.length; j++) {
          if (!Double.isNaN(flowRates[i][j])) {
            count++;
          }
        }
      }
      return count;
    }

    /**
     * Formats the table in Eclipse VFP format.
     *
     * <p>
     * The format follows Eclipse VFP table conventions with inlet pressure as rows and outlet
     * pressure as columns.
     * </p>
     *
     * @return Eclipse format string
     */
    public String toEclipseFormat() {
      StringBuilder sb = new StringBuilder();

      sb.append("-- Lift Curve Table: ").append(tableName).append("\n");
      sb.append("-- Generated by NeqSim PressureBoundaryOptimizer\n");
      sb.append("-- Pressure unit: ").append(pressureUnit).append("\n");
      sb.append("-- Rate unit: ").append(rateUnit).append("\n");
      sb.append("--\n");

      // Header with outlet pressures (THP values)
      sb.append("VFPPROD\n");
      sb.append("-- Table: ").append(tableName).append("\n");
      sb.append("-- THP values (").append(pressureUnit).append("): ");
      for (double p : outletPressures) {
        sb.append(String.format("%.1f ", p));
      }
      sb.append("\n");

      // BHP column header
      sb.append("-- BHP (").append(pressureUnit).append(") / RATE (").append(rateUnit)
          .append(")\n");
      sb.append("-- Pin\\Pout");
      for (double p : outletPressures) {
        sb.append(String.format("%12.1f", p));
      }
      sb.append("\n");

      // Data rows (one per inlet pressure)
      for (int i = 0; i < inletPressures.length; i++) {
        sb.append(String.format("%10.1f", inletPressures[i]));
        for (int j = 0; j < outletPressures.length; j++) {
          if (Double.isNaN(flowRates[i][j])) {
            sb.append(String.format("%12s", "1*"));
          } else {
            sb.append(String.format("%12.0f", flowRates[i][j]));
          }
        }
        sb.append("\n");
      }

      sb.append("/\n");

      // Power table
      sb.append("\n-- Power Table (kW)\n");
      sb.append("-- Pin\\Pout");
      for (double p : outletPressures) {
        sb.append(String.format("%12.1f", p));
      }
      sb.append("\n");

      for (int i = 0; i < inletPressures.length; i++) {
        sb.append(String.format("%10.1f", inletPressures[i]));
        for (int j = 0; j < outletPressures.length; j++) {
          if (Double.isNaN(powers[i][j])) {
            sb.append(String.format("%12s", "-"));
          } else {
            sb.append(String.format("%12.1f", powers[i][j]));
          }
        }
        sb.append("\n");
      }

      return sb.toString();
    }

    /**
     * Converts the table to JSON format.
     *
     * @return JSON string representation
     */
    public String toJson() {
      StringBuilder sb = new StringBuilder();
      sb.append("{\n");
      sb.append("  \"tableName\": \"").append(tableName).append("\",\n");
      sb.append("  \"pressureUnit\": \"").append(pressureUnit).append("\",\n");
      sb.append("  \"rateUnit\": \"").append(rateUnit).append("\",\n");
      sb.append("  \"inletPressures\": ").append(Arrays.toString(inletPressures)).append(",\n");
      sb.append("  \"outletPressures\": ").append(Arrays.toString(outletPressures)).append(",\n");
      sb.append("  \"flowRates\": [\n");
      for (int i = 0; i < flowRates.length; i++) {
        sb.append("    ").append(Arrays.toString(flowRates[i]));
        if (i < flowRates.length - 1) {
          sb.append(",");
        }
        sb.append("\n");
      }
      sb.append("  ],\n");
      sb.append("  \"powers\": [\n");
      for (int i = 0; i < powers.length; i++) {
        sb.append("    ").append(Arrays.toString(powers[i]));
        if (i < powers.length - 1) {
          sb.append(",");
        }
        sb.append("\n");
      }
      sb.append("  ],\n");
      sb.append("  \"feasiblePoints\": ").append(countFeasiblePoints()).append("\n");
      sb.append("}");
      return sb.toString();
    }

    @Override
    public String toString() {
      return "LiftCurveTable{" + "tableName='" + tableName + "'" + ", inletPressures="
          + inletPressures.length + ", outletPressures=" + outletPressures.length
          + ", feasiblePoints=" + countFeasiblePoints() + "/"
          + (inletPressures.length * outletPressures.length) + "}";
    }
  }
}
