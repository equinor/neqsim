package neqsim.process.util.optimizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChart;
import neqsim.process.equipment.compressor.CompressorChartInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer.ManipulatedVariable;
import neqsim.process.util.optimizer.ProductionOptimizer.ObjectiveType;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConstraint;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.process.util.optimizer.ProductionOptimizer.SearchMode;
import neqsim.process.util.optimizer.ProductionOptimizer.ConstraintSeverity;

/**
 * Helper class for compressor-specific production optimization.
 *
 * <p>
 * This class provides utilities for extracting optimization bounds from
 * compressor charts, creating
 * compressor-specific objectives and constraints, and performing two-stage
 * optimization for
 * multi-train compressor systems.
 * </p>
 *
 * <p>
 * <strong>Key Features:</strong>
 * </p>
 * <ul>
 * <li>Extract adaptive bounds from compressor performance charts (speed, flow
 * ranges)</li>
 * <li>Create standard compressor objectives (minimize power, maximize
 * efficiency, maximize surge
 * margin)</li>
 * <li>Two-stage optimization: first balance loads across parallel trains, then
 * maximize total
 * throughput</li>
 * <li>Support for VFD (variable frequency drive) and multi-speed
 * compressors</li>
 * </ul>
 *
 * <p>
 * <strong>Two-Stage Optimization Pattern:</strong>
 * </p>
 * <p>
 * For facilities with multiple parallel compressor trains:
 * </p>
 * <ol>
 * <li><b>Stage 1 - Load Balancing:</b> At fixed total flow, optimize train
 * split fractions to
 * balance loads (minimize max utilization across trains)</li>
 * <li><b>Stage 2 - Throughput Maximization:</b> With optimal splits, maximize
 * total flow subject to
 * constraints</li>
 * </ol>
 *
 * <p>
 * <strong>Usage Example (Java):</strong>
 * </p>
 * 
 * <pre>{@code
 * // Extract bounds from compressor chart
 * CompressorBounds bounds = CompressorOptimizationHelper.extractBounds(compressor);
 *
 * // Create speed variable with chart-derived bounds
 * ManipulatedVariable speedVar = CompressorOptimizationHelper.createSpeedVariable(compressor,
 *     bounds.getMinSpeed(), bounds.getMaxSpeed());
 *
 * // Two-stage optimization
 * TwoStageResult result = CompressorOptimizationHelper.optimizeTwoStage(process, feedStream,
 *     Arrays.asList(comp1, comp2, comp3), flowBounds, config);
 * }</pre>
 *
 * <p>
 * <strong>Usage Example (Python via JPype):</strong>
 * </p>
 * 
 * <pre>{@code
 * from neqsim.neqsimpython import jneqsim
 *
 * Helper = jneqsim.process.util.optimizer.CompressorOptimizationHelper
 *
 * # Extract bounds from compressor chart
 * bounds = Helper.extractBounds(compressor)
 * print(f"Speed range: {bounds.getMinSpeed():.0f} - {bounds.getMaxSpeed():.0f} RPM")
 *
 * # Run two-stage optimization
 * result = Helper.optimizeTwoStage(process, feed, [comp1, comp2], flow_bounds, config)
 * print(f"Optimal flow: {result.getTotalFlow():.0f} kg/hr")
 * }</pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see ProductionOptimizer
 */
public class CompressorOptimizationHelper {

  /**
   * Container for compressor operating bounds extracted from performance charts.
   */
  public static final class CompressorBounds {
    private final double minSpeed;
    private final double maxSpeed;
    private final double minFlow;
    private final double maxFlow;
    private final double surgeFlow;
    private final double stoneWallFlow;
    private final String flowUnit;

    /**
     * Constructs compressor bounds.
     *
     * @param minSpeed      minimum operating speed (RPM)
     * @param maxSpeed      maximum operating speed (RPM)
     * @param minFlow       minimum flow at minimum speed
     * @param maxFlow       maximum flow at maximum speed
     * @param surgeFlow     surge line flow at reference speed
     * @param stoneWallFlow stone wall (choke) flow at reference speed
     * @param flowUnit      flow rate unit
     */
    public CompressorBounds(double minSpeed, double maxSpeed, double minFlow, double maxFlow,
        double surgeFlow, double stoneWallFlow, String flowUnit) {
      this.minSpeed = minSpeed;
      this.maxSpeed = maxSpeed;
      this.minFlow = minFlow;
      this.maxFlow = maxFlow;
      this.surgeFlow = surgeFlow;
      this.stoneWallFlow = stoneWallFlow;
      this.flowUnit = flowUnit;
    }

    /**
     * Returns minimum operating speed in RPM.
     *
     * @return minimum speed
     */
    public double getMinSpeed() {
      return minSpeed;
    }

    /**
     * Returns maximum operating speed in RPM.
     *
     * @return maximum speed
     */
    public double getMaxSpeed() {
      return maxSpeed;
    }

    /**
     * Returns minimum flow at minimum speed.
     *
     * @return minimum flow
     */
    public double getMinFlow() {
      return minFlow;
    }

    /**
     * Returns maximum flow at maximum speed.
     *
     * @return maximum flow
     */
    public double getMaxFlow() {
      return maxFlow;
    }

    /**
     * Returns surge line flow at reference speed.
     *
     * @return surge flow
     */
    public double getSurgeFlow() {
      return surgeFlow;
    }

    /**
     * Returns stone wall (choke) flow at reference speed.
     *
     * @return stone wall flow
     */
    public double getStoneWallFlow() {
      return stoneWallFlow;
    }

    /**
     * Returns the flow unit.
     *
     * @return flow unit string
     */
    public String getFlowUnit() {
      return flowUnit;
    }

    /**
     * Returns the recommended operating range as a fraction of max flow.
     *
     * @param marginFraction safety margin (e.g., 0.1 for 10%)
     * @return array of [minRecommended, maxRecommended] flows
     */
    public double[] getRecommendedRange(double marginFraction) {
      double minRecommended = surgeFlow * (1.0 + marginFraction);
      double maxRecommended = stoneWallFlow * (1.0 - marginFraction);
      return new double[] { minRecommended, maxRecommended };
    }

    @Override
    public String toString() {
      return String.format(
          "CompressorBounds[speed=%.0f-%.0f RPM, flow=%.1f-%.1f %s, surge=%.1f, stonewall=%.1f]",
          minSpeed, maxSpeed, minFlow, maxFlow, flowUnit, surgeFlow, stoneWallFlow);
    }
  }

  /**
   * Result of two-stage optimization containing final flow, splits, and per-train
   * data.
   */
  public static final class TwoStageResult {
    private final double totalFlow;
    private final String flowUnit;
    private final Map<String, Double> trainSplits;
    private final Map<String, Double> trainFlows;
    private final Map<String, Double> trainUtilizations;
    private final Map<String, Double> trainPowers;
    private final Map<String, Double> trainSurgeMargins;
    private final OptimizationResult stage1Result;
    private final OptimizationResult stage2Result;

    /**
     * Constructs a two-stage result.
     *
     * @param totalFlow         optimal total flow rate
     * @param flowUnit          flow rate unit
     * @param trainSplits       split fractions per train
     * @param trainFlows        flow rates per train
     * @param trainUtilizations utilization per train
     * @param trainPowers       power consumption per train (kW)
     * @param trainSurgeMargins surge margin per train
     * @param stage1Result      result from load balancing stage
     * @param stage2Result      result from throughput maximization stage
     */
    public TwoStageResult(double totalFlow, String flowUnit, Map<String, Double> trainSplits,
        Map<String, Double> trainFlows, Map<String, Double> trainUtilizations,
        Map<String, Double> trainPowers, Map<String, Double> trainSurgeMargins,
        OptimizationResult stage1Result, OptimizationResult stage2Result) {
      this.totalFlow = totalFlow;
      this.flowUnit = flowUnit;
      this.trainSplits = new HashMap<>(trainSplits);
      this.trainFlows = new HashMap<>(trainFlows);
      this.trainUtilizations = new HashMap<>(trainUtilizations);
      this.trainPowers = new HashMap<>(trainPowers);
      this.trainSurgeMargins = new HashMap<>(trainSurgeMargins);
      this.stage1Result = stage1Result;
      this.stage2Result = stage2Result;
    }

    /**
     * Returns the optimal total flow rate.
     *
     * @return total flow
     */
    public double getTotalFlow() {
      return totalFlow;
    }

    /**
     * Returns the flow unit.
     *
     * @return flow unit string
     */
    public String getFlowUnit() {
      return flowUnit;
    }

    /**
     * Returns split fractions per train.
     *
     * @return map of train name to split fraction
     */
    public Map<String, Double> getTrainSplits() {
      return new HashMap<>(trainSplits);
    }

    /**
     * Returns flow rates per train.
     *
     * @return map of train name to flow rate
     */
    public Map<String, Double> getTrainFlows() {
      return new HashMap<>(trainFlows);
    }

    /**
     * Returns utilization per train.
     *
     * @return map of train name to utilization
     */
    public Map<String, Double> getTrainUtilizations() {
      return new HashMap<>(trainUtilizations);
    }

    /**
     * Returns power consumption per train in kW.
     *
     * @return map of train name to power
     */
    public Map<String, Double> getTrainPowers() {
      return new HashMap<>(trainPowers);
    }

    /**
     * Returns surge margin per train.
     *
     * @return map of train name to surge margin
     */
    public Map<String, Double> getTrainSurgeMargins() {
      return new HashMap<>(trainSurgeMargins);
    }

    /**
     * Returns the Stage 1 (load balancing) optimization result.
     *
     * @return stage 1 result
     */
    public OptimizationResult getStage1Result() {
      return stage1Result;
    }

    /**
     * Returns the Stage 2 (throughput maximization) optimization result.
     *
     * @return stage 2 result
     */
    public OptimizationResult getStage2Result() {
      return stage2Result;
    }

    /**
     * Returns total power consumption across all trains.
     *
     * @return total power in kW
     */
    public double getTotalPower() {
      return trainPowers.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /**
     * Returns the minimum surge margin across all trains.
     *
     * @return minimum surge margin
     */
    public double getMinSurgeMargin() {
      return trainSurgeMargins.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
    }

    /**
     * Formats the result as a summary string.
     *
     * @return formatted summary
     */
    public String toSummary() {
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("Two-Stage Optimization Result%n"));
      sb.append(String.format("  Total Flow: %.1f %s%n", totalFlow, flowUnit));
      sb.append(String.format("  Total Power: %.1f kW%n", getTotalPower()));
      sb.append(String.format("  Min Surge Margin: %.1f%%%n", getMinSurgeMargin() * 100));
      sb.append(String.format("  Train Details:%n"));
      for (String train : trainSplits.keySet()) {
        sb.append(String.format("    %s: split=%.1f%%, flow=%.1f %s, util=%.1f%%, power=%.1f kW%n",
            train, trainSplits.get(train) * 100, trainFlows.get(train), flowUnit,
            trainUtilizations.get(train) * 100, trainPowers.get(train)));
      }
      return sb.toString();
    }
  }

  /**
   * Extract operating bounds from a compressor's performance chart.
   *
   * <p>
   * This method analyzes the compressor chart to determine:
   * </p>
   * <ul>
   * <li>Speed range (min/max RPM from chart curves)</li>
   * <li>Flow range at each speed curve</li>
   * <li>Surge and stone wall lines</li>
   * </ul>
   *
   * @param compressor the compressor to analyze
   * @return bounds extracted from chart, or default bounds if no chart available
   */
  public static CompressorBounds extractBounds(Compressor compressor) {
    Objects.requireNonNull(compressor, "Compressor is required");

    CompressorChartInterface chart = compressor.getCompressorChart();

    // Default bounds if no chart available
    double defaultMinSpeed = compressor.getSpeed() * 0.7;
    double defaultMaxSpeed = compressor.getSpeed() * 1.1;
    double defaultMinFlow = 0.0;
    double defaultMaxFlow = Double.MAX_VALUE;
    double defaultSurgeFlow = 0.0;
    double defaultStoneWall = Double.MAX_VALUE;

    if (chart == null || !(chart instanceof CompressorChart)) {
      return new CompressorBounds(defaultMinSpeed, defaultMaxSpeed, defaultMinFlow, defaultMaxFlow,
          defaultSurgeFlow, defaultStoneWall, "Am3/hr");
    }

    CompressorChart fullChart = (CompressorChart) chart;

    // Extract speed curves
    double[] speeds = fullChart.getSpeeds();
    double minSpeed = defaultMinSpeed;
    double maxSpeed = defaultMaxSpeed;
    if (speeds != null && speeds.length > 0) {
      minSpeed = speeds[0];
      maxSpeed = speeds[speeds.length - 1];
      for (double speed : speeds) {
        if (speed < minSpeed) {
          minSpeed = speed;
        }
        if (speed > maxSpeed) {
          maxSpeed = speed;
        }
      }
    }

    // Extract flow limits from surge and stone wall lines - handle null curves and
    // null flows
    double[] surgeFlows = null;
    if (fullChart.getSurgeCurve() != null) {
      surgeFlows = fullChart.getSurgeCurve().getFlow();
    }
    if (surgeFlows == null || surgeFlows.length == 0) {
      surgeFlows = new double[] { defaultSurgeFlow };
    }

    double[] stoneWallFlows = null;
    if (fullChart.getStoneWallCurve() != null) {
      stoneWallFlows = fullChart.getStoneWallCurve().getFlow();
    }
    if (stoneWallFlows == null || stoneWallFlows.length == 0) {
      stoneWallFlows = new double[] { defaultStoneWall };
    }

    double surgeFlow = surgeFlows[surgeFlows.length / 2];
    double stoneWallFlow = stoneWallFlows[stoneWallFlows.length / 2];

    // Overall flow range
    double minFlow = surgeFlow;
    double maxFlow = stoneWallFlow;

    return new CompressorBounds(minSpeed, maxSpeed, minFlow, maxFlow, surgeFlow, stoneWallFlow,
        fullChart.getHeadUnit() != null ? "Am3/hr" : "Am3/hr");
  }

  /**
   * Create a manipulated variable for compressor speed.
   *
   * @param compressor the compressor to control
   * @param minSpeed   minimum speed in RPM
   * @param maxSpeed   maximum speed in RPM
   * @return manipulated variable for speed optimization
   */
  public static ManipulatedVariable createSpeedVariable(Compressor compressor, double minSpeed,
      double maxSpeed) {
    Objects.requireNonNull(compressor, "Compressor is required");
    String name = compressor.getName() + ".speed";
    BiConsumer<ProcessSystem, Double> setter = (proc, val) -> compressor.setSpeed(val);
    return new ManipulatedVariable(name, minSpeed, maxSpeed, "RPM", setter);
  }

  /**
   * Create a manipulated variable for compressor outlet pressure.
   *
   * @param compressor  the compressor to control
   * @param minPressure minimum outlet pressure in bara
   * @param maxPressure maximum outlet pressure in bara
   * @return manipulated variable for pressure optimization
   */
  public static ManipulatedVariable createOutletPressureVariable(Compressor compressor,
      double minPressure, double maxPressure) {
    Objects.requireNonNull(compressor, "Compressor is required");
    String name = compressor.getName() + ".outletPressure";
    BiConsumer<ProcessSystem, Double> setter = (proc, val) -> compressor.setOutletPressure(val, "bara");
    return new ManipulatedVariable(name, minPressure, maxPressure, "bara", setter);
  }

  /**
   * Create an objective to minimize total compressor power.
   *
   * @param compressors list of compressors to include
   * @param weight      objective weight (typically 1.0)
   * @return optimization objective for power minimization
   */
  public static OptimizationObjective createPowerObjective(List<Compressor> compressors,
      double weight) {
    Objects.requireNonNull(compressors, "Compressors list is required");
    return new OptimizationObjective("totalPower", proc -> {
      double totalPower = 0.0;
      for (Compressor comp : compressors) {
        totalPower += comp.getPower("kW");
      }
      return totalPower;
    }, weight, ObjectiveType.MINIMIZE);
  }

  /**
   * Create an objective to maximize minimum surge margin across compressors.
   *
   * @param compressors list of compressors to include
   * @param weight      objective weight (typically 1.0)
   * @return optimization objective for surge margin maximization
   */
  public static OptimizationObjective createSurgeMarginObjective(List<Compressor> compressors,
      double weight) {
    Objects.requireNonNull(compressors, "Compressors list is required");
    return new OptimizationObjective("minSurgeMargin", proc -> {
      double minMargin = Double.MAX_VALUE;
      for (Compressor comp : compressors) {
        double margin = comp.getDistanceToSurge();
        if (margin < minMargin) {
          minMargin = margin;
        }
      }
      return minMargin == Double.MAX_VALUE ? 0.0 : minMargin;
    }, weight, ObjectiveType.MAXIMIZE);
  }

  /**
   * Create an objective to maximize average polytropic efficiency.
   *
   * @param compressors list of compressors to include
   * @param weight      objective weight (typically 1.0)
   * @return optimization objective for efficiency maximization
   */
  public static OptimizationObjective createEfficiencyObjective(List<Compressor> compressors,
      double weight) {
    Objects.requireNonNull(compressors, "Compressors list is required");
    return new OptimizationObjective("avgEfficiency", proc -> {
      double totalEff = 0.0;
      int count = 0;
      for (Compressor comp : compressors) {
        double eff = comp.getPolytropicEfficiency();
        if (eff > 0 && eff <= 1.0) {
          totalEff += eff;
          count++;
        }
      }
      return count > 0 ? totalEff / count : 0.0;
    }, weight, ObjectiveType.MAXIMIZE);
  }

  /**
   * Create a constraint requiring minimum surge margin on all compressors.
   *
   * @param compressors list of compressors to constrain
   * @param minMargin   minimum required surge margin (e.g., 0.10 for 10%)
   * @param severity    constraint severity (HARD or SOFT)
   * @return optimization constraint for surge margin
   */
  public static OptimizationConstraint createSurgeMarginConstraint(List<Compressor> compressors,
      double minMargin, ConstraintSeverity severity) {
    Objects.requireNonNull(compressors, "Compressors list is required");
    return OptimizationConstraint.greaterThan("minSurgeMargin", proc -> {
      double minActual = Double.MAX_VALUE;
      for (Compressor comp : compressors) {
        double margin = comp.getDistanceToSurge();
        if (margin < minActual) {
          minActual = margin;
        }
      }
      return minActual == Double.MAX_VALUE ? 0.0 : minActual;
    }, minMargin, severity, 100.0, "Minimum surge margin across all compressors");
  }

  /**
   * Create a constraint requiring all compressor simulations to be valid.
   *
   * @param compressors list of compressors to validate
   * @return optimization constraint for simulation validity
   */
  public static OptimizationConstraint createValidityConstraint(List<Compressor> compressors) {
    Objects.requireNonNull(compressors, "Compressors list is required");
    return OptimizationConstraint.greaterThan("compressorValidity", proc -> {
      for (Compressor comp : compressors) {
        if (!comp.isSimulationValid()) {
          return -1.0; // Invalid
        }
      }
      return 1.0; // All valid
    }, 0.0, ConstraintSeverity.HARD, 1000.0, "All compressor simulations must be valid");
  }

  /**
   * Perform two-stage optimization for multi-train compressor systems.
   *
   * <p>
   * Stage 1: At fixed total flow, optimize split fractions to balance loads
   * across trains. Stage 2:
   * With optimal splits, maximize total flow subject to constraints.
   * </p>
   *
   * @param process            the process system
   * @param feedStream         the main feed stream
   * @param compressors        list of parallel compressors (one per train)
   * @param trainStreamSetters functions to set each train's flow fraction
   * @param flowLowerBound     minimum total flow
   * @param flowUpperBound     maximum total flow
   * @param config             optimization configuration
   * @return two-stage optimization result
   */
  public static TwoStageResult optimizeTwoStage(ProcessSystem process, StreamInterface feedStream,
      List<Compressor> compressors,
      List<BiConsumer<ProcessSystem, Double>> trainStreamSetters, double flowLowerBound,
      double flowUpperBound, OptimizationConfig config) {
    Objects.requireNonNull(process, "Process is required");
    Objects.requireNonNull(feedStream, "Feed stream is required");
    Objects.requireNonNull(compressors, "Compressors list is required");
    Objects.requireNonNull(trainStreamSetters, "Train stream setters are required");

    if (compressors.size() != trainStreamSetters.size()) {
      throw new IllegalArgumentException(
          "Number of compressors must match number of train setters");
    }

    int numTrains = compressors.size();
    ProductionOptimizer optimizer = new ProductionOptimizer();

    // --- Stage 1: Balance loads at current flow ---
    // Create split variables (N-1 independent splits)
    List<ManipulatedVariable> splitVariables = new ArrayList<>();
    double equalSplit = 1.0 / numTrains;

    for (int i = 0; i < numTrains - 1; i++) {
      final int trainIndex = i;
      String name = "split_" + compressors.get(i).getName();
      BiConsumer<ProcessSystem, Double> setter = (proc, val) -> {
        trainStreamSetters.get(trainIndex).accept(proc, val);
      };
      // Allow splits from 10% to 50% per train
      splitVariables.add(new ManipulatedVariable(name, 0.1, 0.5, "fraction", setter));
    }

    // Objective: minimize maximum utilization (balance loads)
    List<OptimizationObjective> balanceObjectives = Collections
        .singletonList(new OptimizationObjective("maxUtilization", proc -> {
          double maxUtil = 0.0;
          for (Compressor comp : compressors) {
            double util = comp.getCapacityDuty() / Math.max(1e-6, comp.getCapacityMax());
            if (util > maxUtil) {
              maxUtil = util;
            }
          }
          return maxUtil;
        }, 1.0, ObjectiveType.MINIMIZE));

    // Constraints for Stage 1
    List<OptimizationConstraint> balanceConstraints = new ArrayList<>();
    balanceConstraints.add(createValidityConstraint(compressors));
    balanceConstraints.add(createSurgeMarginConstraint(compressors, 0.10, ConstraintSeverity.HARD));

    OptimizationConfig stage1Config = new OptimizationConfig(0, 1)
        .searchMode(SearchMode.NELDER_MEAD_SCORE).maxIterations(config.getMaxIterations() / 2)
        .tolerance(0.01);

    OptimizationResult stage1Result = optimizer.optimize(process, splitVariables, stage1Config, balanceObjectives,
        balanceConstraints);

    // Extract optimal splits
    Map<String, Double> optimalSplits = new HashMap<>();
    double sumSplits = 0.0;
    for (int i = 0; i < numTrains - 1; i++) {
      String varName = "split_" + compressors.get(i).getName();
      double split = stage1Result.getDecisionVariables().getOrDefault(varName, equalSplit);
      optimalSplits.put(compressors.get(i).getName(), split);
      sumSplits += split;
    }
    // Last train gets remainder
    optimalSplits.put(compressors.get(numTrains - 1).getName(), 1.0 - sumSplits);

    // --- Stage 2: Maximize throughput with optimal splits ---
    // Apply optimal splits via setters
    for (int i = 0; i < numTrains - 1; i++) {
      trainStreamSetters.get(i).accept(process, optimalSplits.get(compressors.get(i).getName()));
    }

    // Throughput objective
    List<OptimizationObjective> throughputObjectives = Collections.singletonList(new OptimizationObjective("totalFlow",
        proc -> feedStream.getFlowRate(config.getRateUnit()), 1.0, ObjectiveType.MAXIMIZE));

    OptimizationConfig stage2Config = new OptimizationConfig(flowLowerBound, flowUpperBound)
        .searchMode(config.getSearchMode())
        .maxIterations(config.getMaxIterations()).tolerance(config.getTolerance())
        .rateUnit(config.getRateUnit());

    List<OptimizationConstraint> throughputConstraints = new ArrayList<>();
    throughputConstraints.add(createValidityConstraint(compressors));
    throughputConstraints
        .add(createSurgeMarginConstraint(compressors, 0.10, ConstraintSeverity.HARD));

    OptimizationResult stage2Result = optimizer.optimize(process, feedStream, stage2Config,
        throughputObjectives, throughputConstraints);

    // Collect final train data
    Map<String, Double> trainFlows = new HashMap<>();
    Map<String, Double> trainUtilizations = new HashMap<>();
    Map<String, Double> trainPowers = new HashMap<>();
    Map<String, Double> trainSurgeMargins = new HashMap<>();

    double totalFlow = stage2Result.getOptimalRate();
    for (int i = 0; i < numTrains; i++) {
      Compressor comp = compressors.get(i);
      String name = comp.getName();
      double split = optimalSplits.get(name);
      trainFlows.put(name, totalFlow * split);
      trainUtilizations.put(name, comp.getCapacityDuty() / Math.max(1e-6, comp.getCapacityMax()));
      trainPowers.put(name, comp.getPower("kW"));
      trainSurgeMargins.put(name, comp.getDistanceToSurge());
    }

    return new TwoStageResult(totalFlow, config.getRateUnit(), optimalSplits, trainFlows,
        trainUtilizations, trainPowers, trainSurgeMargins, stage1Result, stage2Result);
  }

  /**
   * Create a list of compressor speed variables with chart-derived bounds.
   *
   * @param compressors list of compressors
   * @return list of speed manipulated variables
   */
  public static List<ManipulatedVariable> createSpeedVariables(List<Compressor> compressors) {
    List<ManipulatedVariable> variables = new ArrayList<>();
    for (Compressor comp : compressors) {
      CompressorBounds bounds = extractBounds(comp);
      variables.add(createSpeedVariable(comp, bounds.getMinSpeed(), bounds.getMaxSpeed()));
    }
    return variables;
  }

  /**
   * Create standard objectives for compressor optimization.
   *
   * <p>
   * Returns a list with three objectives:
   * </p>
   * <ul>
   * <li>Minimize total power (weight 0.4)</li>
   * <li>Maximize minimum surge margin (weight 0.3)</li>
   * <li>Maximize average efficiency (weight 0.3)</li>
   * </ul>
   *
   * @param compressors list of compressors
   * @return list of standard objectives
   */
  public static List<OptimizationObjective> createStandardObjectives(List<Compressor> compressors) {
    return Arrays.asList(createPowerObjective(compressors, 0.4),
        createSurgeMarginObjective(compressors, 0.3), createEfficiencyObjective(compressors, 0.3));
  }

  /**
   * Create standard constraints for compressor optimization.
   *
   * <p>
   * Returns constraints for:
   * </p>
   * <ul>
   * <li>Simulation validity (HARD)</li>
   * <li>Minimum 10% surge margin (HARD)</li>
   * </ul>
   *
   * @param compressors list of compressors
   * @return list of standard constraints
   */
  public static List<OptimizationConstraint> createStandardConstraints(
      List<Compressor> compressors) {
    return Arrays.asList(createValidityConstraint(compressors),
        createSurgeMarginConstraint(compressors, 0.10, ConstraintSeverity.HARD));
  }
}
