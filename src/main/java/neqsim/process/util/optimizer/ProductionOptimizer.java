package neqsim.process.util.optimizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Production optimization utility for process simulation models.
 *
 * <p>
 * This class provides comprehensive optimization capabilities for NeqSim
 * process models, supporting
 * single-variable and multi-variable optimization with multiple search
 * algorithms. It can maximize
 * throughput subject to equipment capacity constraints, or optimize arbitrary
 * objective functions
 * with configurable constraints.
 * </p>
 *
 * <p>
 * <strong>Supported Search Algorithms</strong>
 * </p>
 * <ul>
 * <li><b>BINARY_FEASIBILITY</b> - Traditional monotonic binary search on
 * feasibility. Fast for
 * single-variable problems where feasibility is monotonic with respect to the
 * decision
 * variable.</li>
 * <li><b>GOLDEN_SECTION_SCORE</b> - Golden-section search on a composite score.
 * Suitable for
 * single-variable non-monotonic responses.</li>
 * <li><b>NELDER_MEAD_SCORE</b> - Nelder-Mead simplex algorithm for
 * multi-dimensional optimization.
 * Does not require gradients; works well for 2-10 decision variables.</li>
 * <li><b>PARTICLE_SWARM_SCORE</b> - Particle swarm optimization for global
 * search. Good for
 * non-convex problems with multiple local optima.</li>
 * </ul>
 *
 * <p>
 * <strong>Multi-Objective Optimization</strong>
 * </p>
 * <p>
 * The optimizer supports Pareto multi-objective optimization via weighted-sum
 * scalarization. This
 * generates a Pareto front by solving multiple single-objective problems with
 * different weight
 * combinations. Use {@link #optimizePareto} for multi-objective problems.
 * </p>
 *
 * <p>
 * <strong>Usage Example (Java)</strong>
 * </p>
 * 
 * <pre>{@code
 * // Create process model
 * ProcessSystem process = new ProcessSystem();
 * Stream feed = new Stream("feed", fluid);
 * feed.setFlowRate(100000.0, "kg/hr");
 * Compressor compressor = new Compressor("compressor", feed);
 * process.add(feed);
 * process.add(compressor);
 * process.run();
 *
 * // Configure and run optimization
 * ProductionOptimizer optimizer = new ProductionOptimizer();
 * OptimizationConfig config = new OptimizationConfig(50000.0, 200000.0).tolerance(100.0)
 *     .searchMode(SearchMode.GOLDEN_SECTION_SCORE).maxIterations(30);
 *
 * OptimizationResult result = optimizer.optimize(process, feed, config, null, null);
 * System.out.println("Optimal rate: " + result.getOptimalRate() + " kg/hr");
 * }</pre>
 *
 * <p>
 * <strong>Usage Example (Python via neqsim-python/JPype)</strong>
 * </p>
 * 
 * <pre>{@code
 * from neqsim.neqsimpython import jneqsim
 *
 * # Import classes
 * ProductionOptimizer = jneqsim.process.util.optimizer.ProductionOptimizer
 * OptimizationConfig = ProductionOptimizer.OptimizationConfig
 * SearchMode = ProductionOptimizer.SearchMode
 *
 * # Create optimizer and config
 * optimizer = ProductionOptimizer()
 * config = OptimizationConfig(50000.0, 200000.0) \
 *     .tolerance(100.0) \
 *     .searchMode(SearchMode.GOLDEN_SECTION_SCORE)
 *
 * # Run optimization
 * result = optimizer.optimize(process, feed, config, None, None)
 * print(f"Optimal rate: {result.getOptimalRate():.0f} kg/hr")
 * }</pre>
 *
 * <p>
 * <strong>Multi-Variable Optimization Example</strong>
 * </p>
 * 
 * <pre>{@code
 * // Define manipulated variables
 * List<ManipulatedVariable> variables = Arrays.asList(
 *     new ManipulatedVariable("flowRate", 50000, 200000, "kg/hr",
 *         (proc, val) -> proc.getUnit("feed").setFlowRate(val, "kg/hr")),
 *     new ManipulatedVariable("pressure", 100, 200, "bara",
 *         (proc, val) -> ((Compressor) proc.getUnit("comp")).setOutletPressure(val, "bara")));
 *
 * OptimizationConfig config = new OptimizationConfig(0, 1) // bounds ignored for multi-var
 *     .searchMode(SearchMode.NELDER_MEAD_SCORE);
 *
 * OptimizationResult result = optimizer.optimize(process, variables, config, objectives, null);
 * }</pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see OptimizationConfig
 * @see OptimizationResult
 * @see ManipulatedVariable
 * @see ParetoResult
 */
public class ProductionOptimizer {
  /**
   * Default maximum utilization used when no specific equipment rule is provided.
   */
  public static final double DEFAULT_UTILIZATION_LIMIT = 0.95;

  /** Supported search algorithms. */
  public enum SearchMode {
    /** Traditional monotonic binary search on feasibility. */
    BINARY_FEASIBILITY,
    /** Golden-section scoring search suitable for non-monotonic responses. */
    GOLDEN_SECTION_SCORE,
    /** Nelder–Mead simplex search on the composite score. */
    NELDER_MEAD_SCORE,
    /** Particle-swarm search on the composite/feasibility score. */
    PARTICLE_SWARM_SCORE,
    /**
     * Gradient descent with finite-difference gradients and Armijo line search.
     * Suitable for
     * multi-variable smooth optimization problems (5-20+ variables). Uses
     * L-BFGS-style
     * approximation for the Hessian inverse.
     */
    GRADIENT_DESCENT_SCORE
  }

  /** Objective optimization direction. */
  public enum ObjectiveType {
    MAXIMIZE, MINIMIZE
  }

  /** Direction of a constraint comparison. */
  public enum ConstraintDirection {
    LESS_THAN, GREATER_THAN
  }

  /** Severity classification for constraints. */
  public enum ConstraintSeverity {
    HARD, SOFT
  }

  /**
   * Container for optimization objective configuration.
   *
   * <p>
   * An objective defines a quantity to optimize (maximize or minimize) during the
   * optimization
   * process. Multiple objectives can be combined with weights to form a composite
   * score.
   * </p>
   *
   * <p>
   * <strong>Java Example</strong>
   * </p>
   * 
   * <pre>{@code
   * OptimizationObjective throughput = new OptimizationObjective("throughput",
   *     proc -> proc.getUnit("outlet").getFlowRate("kg/hr"), 1.0, ObjectiveType.MAXIMIZE);
   * }</pre>
   *
   * <p>
   * <strong>Python Example (via JPype)</strong>
   * </p>
   * 
   * <pre>
   * from jpype import JImplements, JOverride
   *
   * &#64;JImplements("java.util.function.ToDoubleFunction")
   * class ThroughputEvaluator:
   *     &#64;JOverride
   *     def applyAsDouble(self, proc):
   *         return proc.getUnit("outlet").getFlowRate("kg/hr")
   *
   * throughput = OptimizationObjective("throughput", ThroughputEvaluator(), 1.0)
   * </pre>
   */
  public static final class OptimizationObjective {
    private final String name;
    private final ToDoubleFunction<ProcessSystem> evaluator;
    private final double weight;
    private final ObjectiveType type;

    /**
     * Constructs an objective with MAXIMIZE direction.
     *
     * @param name      unique name for the objective
     * @param evaluator function to compute objective value from process state
     * @param weight    relative weight for composite scoring (typically 0.0-1.0)
     */
    public OptimizationObjective(String name, ToDoubleFunction<ProcessSystem> evaluator,
        double weight) {
      this(name, evaluator, weight, ObjectiveType.MAXIMIZE);
    }

    /**
     * Constructs an objective with explicit direction.
     *
     * @param name      unique name for the objective
     * @param evaluator function to compute objective value from process state
     * @param weight    relative weight for composite scoring (typically 0.0-1.0)
     * @param type      MAXIMIZE or MINIMIZE direction
     */
    public OptimizationObjective(String name, ToDoubleFunction<ProcessSystem> evaluator,
        double weight, ObjectiveType type) {
      this.name = Objects.requireNonNull(name, "Objective name is required");
      this.evaluator = Objects.requireNonNull(evaluator, "Objective evaluator is required");
      this.weight = weight;
      this.type = Objects.requireNonNull(type, "Objective type is required");
    }

    /**
     * Returns the objective name.
     *
     * @return objective name
     */
    public String getName() {
      return name;
    }

    /**
     * Returns the objective weight.
     *
     * @return weight value
     */
    public double getWeight() {
      return weight;
    }

    /**
     * Returns the objective type (MAXIMIZE or MINIMIZE).
     *
     * @return objective type
     */
    public ObjectiveType getType() {
      return type;
    }

    /**
     * Evaluates the objective for the given process state.
     *
     * @param process the process system to evaluate
     * @return computed objective value
     */
    public double evaluate(ProcessSystem process) {
      return evaluator.applyAsDouble(process);
    }
  }

  /** Simple container for constraint configuration. */
  public static final class OptimizationConstraint {
    private final String name;
    private final ToDoubleFunction<ProcessSystem> metric;
    private final double limit;
    private final ConstraintDirection direction;
    private final ConstraintSeverity severity;
    private final double penaltyWeight;
    private final String description;

    public OptimizationConstraint(String name, ToDoubleFunction<ProcessSystem> metric, double limit,
        ConstraintDirection direction, ConstraintSeverity severity, double penaltyWeight,
        String description) {
      this.name = Objects.requireNonNull(name, "Constraint name is required");
      this.metric = Objects.requireNonNull(metric, "Constraint metric is required");
      this.limit = limit;
      this.direction = Objects.requireNonNull(direction, "Constraint direction is required");
      this.severity = Objects.requireNonNull(severity, "Constraint severity is required");
      this.penaltyWeight = penaltyWeight;
      this.description = description;
    }

    public static OptimizationConstraint lessThan(String name,
        ToDoubleFunction<ProcessSystem> metric, double limit, ConstraintSeverity severity,
        double penaltyWeight, String description) {
      return new OptimizationConstraint(name, metric, limit, ConstraintDirection.LESS_THAN,
          severity, penaltyWeight, description);
    }

    public static OptimizationConstraint greaterThan(String name,
        ToDoubleFunction<ProcessSystem> metric, double limit, ConstraintSeverity severity,
        double penaltyWeight, String description) {
      return new OptimizationConstraint(name, metric, limit, ConstraintDirection.GREATER_THAN,
          severity, penaltyWeight, description);
    }

    public String getName() {
      return name;
    }

    public ConstraintSeverity getSeverity() {
      return severity;
    }

    public double getPenaltyWeight() {
      return penaltyWeight;
    }

    public String getDescription() {
      return description;
    }

    public double margin(ProcessSystem process) {
      double value = metric.applyAsDouble(process);
      if (direction == ConstraintDirection.LESS_THAN) {
        return limit - value;
      }
      return value - limit;
    }

    public boolean isSatisfied(ProcessSystem process) {
      return margin(process) >= 0.0;
    }
  }

  /** Holds the utilization status for an equipment item. */
  public static final class UtilizationRecord {
    private final String equipmentName;
    private final double capacityDuty;
    private final double capacityMax;
    private final double utilization;
    private final double utilizationLimit;

    public UtilizationRecord(String equipmentName, double capacityDuty, double capacityMax,
        double utilization, double utilizationLimit) {
      this.equipmentName = equipmentName;
      this.capacityDuty = capacityDuty;
      this.capacityMax = capacityMax;
      this.utilization = utilization;
      this.utilizationLimit = utilizationLimit;
    }

    public String getEquipmentName() {
      return equipmentName;
    }

    public double getCapacityDuty() {
      return capacityDuty;
    }

    public double getCapacityMax() {
      return capacityMax;
    }

    public double getUtilization() {
      return utilization;
    }

    public double getUtilizationLimit() {
      return utilizationLimit;
    }
  }

  /** Result container for a completed optimization attempt. */
  public static final class OptimizationResult {
    private final double optimalRate;
    private final String rateUnit;
    private final Map<String, Double> decisionVariables;
    private final ProcessEquipmentInterface bottleneck;
    private final double bottleneckUtilization;
    private final List<UtilizationRecord> utilizationRecords;
    private final Map<String, Double> objectiveValues;
    private final List<ConstraintStatus> constraintStatuses;
    private final boolean feasible;
    private final double score;
    private final int iterations;
    private final List<IterationRecord> iterationHistory;

    public OptimizationResult(double optimalRate, String rateUnit,
        Map<String, Double> decisionVariables, ProcessEquipmentInterface bottleneck,
        double bottleneckUtilization, List<UtilizationRecord> utilizationRecords,
        Map<String, Double> objectiveValues, List<ConstraintStatus> constraintStatuses,
        boolean feasible, double score, int iterations, List<IterationRecord> iterationHistory) {
      this.optimalRate = optimalRate;
      this.rateUnit = rateUnit;
      this.decisionVariables = decisionVariables == null ? Collections.emptyMap() : new HashMap<>(decisionVariables);
      this.bottleneck = bottleneck;
      this.bottleneckUtilization = bottleneckUtilization;
      this.utilizationRecords = utilizationRecords;
      this.objectiveValues = objectiveValues;
      this.constraintStatuses = constraintStatuses;
      this.feasible = feasible;
      this.score = score;
      this.iterations = iterations;
      this.iterationHistory = iterationHistory;
    }

    public double getOptimalRate() {
      return optimalRate;
    }

    public String getRateUnit() {
      return rateUnit;
    }

    public Map<String, Double> getDecisionVariables() {
      return decisionVariables;
    }

    public ProcessEquipmentInterface getBottleneck() {
      return bottleneck;
    }

    public double getBottleneckUtilization() {
      return bottleneckUtilization;
    }

    public List<UtilizationRecord> getUtilizationRecords() {
      return utilizationRecords;
    }

    public Map<String, Double> getObjectiveValues() {
      return objectiveValues;
    }

    public List<ConstraintStatus> getConstraintStatuses() {
      return constraintStatuses;
    }

    public boolean isFeasible() {
      return feasible;
    }

    public double getScore() {
      return score;
    }

    public int getIterations() {
      return iterations;
    }

    public List<IterationRecord> getIterationHistory() {
      return iterationHistory;
    }
  }

  /**
   * Lightweight summary of an optimization run intended for quick-consumption
   * APIs.
   */
  public static final class OptimizationSummary {
    private final double maxRate;
    private final String rateUnit;
    private final String limitingEquipment;
    private final double utilization;
    private final double utilizationLimit;
    private final double utilizationMargin;
    private final boolean feasible;
    private final Map<String, Double> decisionVariables;
    private final List<UtilizationRecord> utilizations;
    private final List<ConstraintStatus> constraints;

    public OptimizationSummary(double maxRate, String rateUnit, String limitingEquipment,
        double utilization, double utilizationLimit, double utilizationMargin, boolean feasible,
        Map<String, Double> decisionVariables, List<UtilizationRecord> utilizations,
        List<ConstraintStatus> constraints) {
      this.maxRate = maxRate;
      this.rateUnit = rateUnit;
      this.limitingEquipment = limitingEquipment;
      this.utilization = utilization;
      this.utilizationLimit = utilizationLimit;
      this.utilizationMargin = utilizationMargin;
      this.feasible = feasible;
      this.decisionVariables = decisionVariables == null ? Collections.emptyMap() : new HashMap<>(decisionVariables);
      this.utilizations = utilizations == null ? Collections.emptyList() : new ArrayList<>(utilizations);
      this.constraints = constraints == null ? Collections.emptyList() : new ArrayList<>(constraints);
    }

    public double getMaxRate() {
      return maxRate;
    }

    public String getRateUnit() {
      return rateUnit;
    }

    public String getLimitingEquipment() {
      return limitingEquipment;
    }

    public double getUtilization() {
      return utilization;
    }

    public double getUtilizationLimit() {
      return utilizationLimit;
    }

    public double getUtilizationMargin() {
      return utilizationMargin;
    }

    public boolean isFeasible() {
      return feasible;
    }

    public Map<String, Double> getDecisionVariables() {
      return new HashMap<>(decisionVariables);
    }

    public List<UtilizationRecord> getUtilizations() {
      return new ArrayList<>(utilizations);
    }

    public List<ConstraintStatus> getConstraints() {
      return new ArrayList<>(constraints);
    }
  }

  /** Outcome for a single constraint evaluation. */
  public static final class ConstraintStatus {
    private final String name;
    private final ConstraintSeverity severity;
    private final double margin;
    private final double penaltyWeight;
    private final String description;

    public ConstraintStatus(String name, ConstraintSeverity severity, double margin,
        double penaltyWeight, String description) {
      this.name = name;
      this.severity = severity;
      this.margin = margin;
      this.penaltyWeight = penaltyWeight;
      this.description = description;
    }

    public String getName() {
      return name;
    }

    public ConstraintSeverity getSeverity() {
      return severity;
    }

    public double getMargin() {
      return margin;
    }

    public double getPenaltyWeight() {
      return penaltyWeight;
    }

    public String getDescription() {
      return description;
    }

    public boolean violated() {
      return margin < 0.0;
    }
  }

  /** Snapshot of each iteration to support diagnostics and plotting. */
  public static final class IterationRecord {
    private final double rate;
    private final String rateUnit;
    private final Map<String, Double> decisionVariables;
    private final String bottleneckName;
    private final double bottleneckUtilization;
    private final boolean utilizationWithinLimits;
    private final boolean hardConstraintsOk;
    private final boolean feasible;
    private final double score;
    private final List<UtilizationRecord> utilizations;

    public IterationRecord(double rate, String rateUnit, Map<String, Double> decisionVariables,
        String bottleneckName, double bottleneckUtilization, boolean utilizationWithinLimits,
        boolean hardConstraintsOk, boolean feasible, double score,
        List<UtilizationRecord> utilizations) {
      this.rate = rate;
      this.rateUnit = rateUnit;
      this.decisionVariables = decisionVariables == null ? Collections.emptyMap() : new HashMap<>(decisionVariables);
      this.bottleneckName = bottleneckName;
      this.bottleneckUtilization = bottleneckUtilization;
      this.utilizationWithinLimits = utilizationWithinLimits;
      this.hardConstraintsOk = hardConstraintsOk;
      this.feasible = feasible;
      this.score = score;
      this.utilizations = new ArrayList<>(utilizations);
    }

    public double getRate() {
      return rate;
    }

    public String getRateUnit() {
      return rateUnit;
    }

    public Map<String, Double> getDecisionVariables() {
      return decisionVariables;
    }

    public String getBottleneckName() {
      return bottleneckName;
    }

    public double getBottleneckUtilization() {
      return bottleneckUtilization;
    }

    public boolean isUtilizationWithinLimits() {
      return utilizationWithinLimits;
    }

    public boolean isHardConstraintsOk() {
      return hardConstraintsOk;
    }

    public boolean isFeasible() {
      return feasible;
    }

    public double getScore() {
      return score;
    }

    public List<UtilizationRecord> getUtilizations() {
      return new ArrayList<>(utilizations);
    }
  }

  /**
   * Builder-style configuration for the production optimizer.
   *
   * <p>
   * This class uses a fluent API pattern for configuring optimization parameters.
   * All setter
   * methods return {@code this} to enable method chaining.
   * </p>
   *
   * <p>
   * <strong>Configuration Categories</strong>
   * </p>
   * <ul>
   * <li><b>Search bounds</b> - {@link #lowerBound}, {@link #upperBound} define
   * the search
   * range</li>
   * <li><b>Convergence</b> - {@link #tolerance}, {@link #maxIterations} control
   * termination</li>
   * <li><b>Algorithm</b> - {@link #searchMode} selects the optimization
   * algorithm</li>
   * <li><b>Utilization</b> - equipment capacity limits and margins</li>
   * <li><b>Parallelization</b> - {@link #parallelEvaluations},
   * {@link #parallelThreads}</li>
   * <li><b>Pareto</b> - {@link #paretoGridSize} for multi-objective
   * optimization</li>
   * <li><b>PSO parameters</b> - swarm size, inertia, cognitive/social
   * weights</li>
   * </ul>
   *
   * <p>
   * <strong>Java Example</strong>
   * </p>
   * 
   * <pre>{@code
   * OptimizationConfig config = new OptimizationConfig(50000.0, 200000.0).tolerance(100.0)
   *     .maxIterations(50).searchMode(SearchMode.GOLDEN_SECTION_SCORE).rateUnit("kg/hr")
   *     .utilizationLimitForName("compressor1", 0.90).parallelEvaluations(true).parallelThreads(4);
   * }</pre>
   *
   * <p>
   * <strong>Python Example (via JPype)</strong>
   * </p>
   * 
   * <pre>{@code
   * config = OptimizationConfig(50000.0, 200000.0) \
   *     .tolerance(100.0) \
   *     .maxIterations(50) \
   *     .searchMode(SearchMode.GOLDEN_SECTION_SCORE)
   * }</pre>
   */
  public static final class OptimizationConfig {
    private double lowerBound;
    private double upperBound;
    private double tolerance = 1e-3;
    private int maxIterations = 30;
    private String rateUnit = "kg/hr";
    private double defaultUtilizationLimit = DEFAULT_UTILIZATION_LIMIT;
    private double utilizationMarginFraction = 0.0;
    private double capacityUncertaintyFraction = 0.0;
    private double capacityPercentile = 0.5;
    private double capacityRangeSpreadFraction = 0.0;
    private SearchMode searchMode = SearchMode.BINARY_FEASIBILITY;
    private boolean enableCaching = true;
    private boolean rejectInvalidSimulations = true;
    private boolean parallelEvaluations = false;
    private int parallelThreads = Runtime.getRuntime().availableProcessors();
    private int paretoGridSize = 11;
    private int swarmSize = 8;
    private double inertiaWeight = 0.6;
    private double cognitiveWeight = 1.2;
    private double socialWeight = 1.2;
    private double columnFsFactorLimit = 2.5;
    private final Map<String, Double> utilizationLimitsByName = new HashMap<>();
    private final Map<Class<?>, Double> utilizationLimitsByType = new HashMap<>();
    private final Map<String, CapacityRule> capacityRulesByName = new HashMap<>();
    private final Map<Class<?>, CapacityRule> capacityRulesByType = new HashMap<>();
    private final Map<String, CapacityRange> capacityRangesByName = new HashMap<>();
    private final Map<Class<?>, CapacityRange> capacityRangesByType = new HashMap<>();
    private final List<EquipmentConstraintRule> equipmentConstraintRules = new ArrayList<>();

    /**
     * Constructs a configuration with specified search bounds.
     *
     * @param lowerBound minimum value for the decision variable (e.g., min flow
     *                   rate)
     * @param upperBound maximum value for the decision variable (e.g., max flow
     *                   rate)
     */
    public OptimizationConfig(double lowerBound, double upperBound) {
      this.lowerBound = lowerBound;
      this.upperBound = upperBound;
    }

    /**
     * Sets the convergence tolerance.
     *
     * <p>
     * The optimizer terminates when the change in the decision variable between
     * iterations is less
     * than this tolerance.
     * </p>
     *
     * @param tolerance convergence tolerance in the same units as the decision
     *                  variable
     * @return this config for method chaining
     */
    public OptimizationConfig tolerance(double tolerance) {
      this.tolerance = tolerance;
      return this;
    }

    /**
     * Sets the maximum number of iterations.
     *
     * @param maxIterations maximum iterations before termination (default: 30)
     * @return this config for method chaining
     */
    public OptimizationConfig maxIterations(int maxIterations) {
      this.maxIterations = maxIterations;
      return this;
    }

    /**
     * Sets whether to reject simulation results that are physically invalid.
     * 
     * <p>
     * When enabled (default), the optimizer will mark operating points as
     * infeasible if any
     * equipment reports invalid simulation results (e.g., negative power in
     * compressors, NaN
     * values, etc.).
     * </p>
     * 
     * @param reject true to reject invalid simulations
     * @return this config for method chaining
     */
    public OptimizationConfig rejectInvalidSimulations(boolean reject) {
      this.rejectInvalidSimulations = reject;
      return this;
    }

    /**
     * Gets whether invalid simulations are rejected.
     * 
     * @return true if invalid simulations are rejected
     */
    public boolean isRejectInvalidSimulations() {
      return rejectInvalidSimulations;
    }

    public int getMaxIterations() {
      return maxIterations;
    }

    public double getUtilizationMarginFraction() {
      return utilizationMarginFraction;
    }

    public double getCapacityUncertaintyFraction() {
      return capacityUncertaintyFraction;
    }

    public double getCapacityPercentile() {
      return capacityPercentile;
    }

    public double getCapacityRangeSpreadFraction() {
      return capacityRangeSpreadFraction;
    }

    public OptimizationConfig rateUnit(String rateUnit) {
      this.rateUnit = rateUnit;
      return this;
    }

    public OptimizationConfig searchMode(SearchMode mode) {
      this.searchMode = mode;
      return this;
    }

    public OptimizationConfig swarmSize(int swarmSize) {
      this.swarmSize = swarmSize;
      return this;
    }

    public OptimizationConfig inertiaWeight(double inertiaWeight) {
      this.inertiaWeight = inertiaWeight;
      return this;
    }

    public OptimizationConfig cognitiveWeight(double cognitiveWeight) {
      this.cognitiveWeight = cognitiveWeight;
      return this;
    }

    public OptimizationConfig socialWeight(double socialWeight) {
      this.socialWeight = socialWeight;
      return this;
    }

    public OptimizationConfig columnFsFactorLimit(double columnFsFactorLimit) {
      this.columnFsFactorLimit = columnFsFactorLimit;
      return this;
    }

    public OptimizationConfig defaultUtilizationLimit(double defaultUtilizationLimit) {
      this.defaultUtilizationLimit = defaultUtilizationLimit;
      return this;
    }

    public OptimizationConfig utilizationMarginFraction(double utilizationMarginFraction) {
      this.utilizationMarginFraction = utilizationMarginFraction;
      return this;
    }

    public OptimizationConfig capacityUncertaintyFraction(double capacityUncertaintyFraction) {
      this.capacityUncertaintyFraction = capacityUncertaintyFraction;
      return this;
    }

    public OptimizationConfig capacityPercentile(double capacityPercentile) {
      this.capacityPercentile = capacityPercentile;
      return this;
    }

    public OptimizationConfig capacityRangeSpreadFraction(double capacityRangeSpreadFraction) {
      this.capacityRangeSpreadFraction = capacityRangeSpreadFraction;
      return this;
    }

    public OptimizationConfig utilizationLimitForName(String equipmentName, double limit) {
      utilizationLimitsByName.put(equipmentName, limit);
      return this;
    }

    public OptimizationConfig utilizationLimitForType(Class<?> type, double limit) {
      utilizationLimitsByType.put(type, limit);
      return this;
    }

    public OptimizationConfig capacityRuleForName(String equipmentName, CapacityRule rule) {
      capacityRulesByName.put(equipmentName, rule);
      return this;
    }

    public OptimizationConfig capacityRuleForType(Class<?> type, CapacityRule rule) {
      capacityRulesByType.put(type, rule);
      return this;
    }

    public OptimizationConfig capacityRangeForName(String equipmentName, CapacityRange range) {
      capacityRangesByName.put(equipmentName, range);
      return this;
    }

    public OptimizationConfig capacityRangeForType(Class<?> type, CapacityRange range) {
      capacityRangesByType.put(type, range);
      return this;
    }

    public OptimizationConfig equipmentConstraintRule(EquipmentConstraintRule rule) {
      equipmentConstraintRules.add(rule);
      return this;
    }

    public OptimizationConfig enableCaching(boolean enableCaching) {
      this.enableCaching = enableCaching;
      return this;
    }

    /**
     * Enables parallel evaluation of candidates in PSO and scenario optimization.
     * 
     * <p>
     * When enabled, particle swarm optimization evaluates particles in parallel
     * using a thread
     * pool, and scenario optimization runs scenarios concurrently.
     * </p>
     * 
     * @param parallel true to enable parallel evaluations
     * @return this config for method chaining
     */
    public OptimizationConfig parallelEvaluations(boolean parallel) {
      this.parallelEvaluations = parallel;
      return this;
    }

    /**
     * Sets the number of threads for parallel evaluations.
     * 
     * @param threads number of threads (default: available processors)
     * @return this config for method chaining
     */
    public OptimizationConfig parallelThreads(int threads) {
      this.parallelThreads = Math.max(1, threads);
      return this;
    }

    /**
     * Sets the grid size for Pareto front generation.
     * 
     * <p>
     * For weighted-sum Pareto optimization, this determines how many weight
     * combinations are
     * evaluated. A grid size of 11 generates weights: 0.0, 0.1, 0.2, ..., 1.0.
     * </p>
     * 
     * @param gridSize number of weight points per objective (default: 11)
     * @return this config for method chaining
     */
    public OptimizationConfig paretoGridSize(int gridSize) {
      this.paretoGridSize = Math.max(2, gridSize);
      return this;
    }

    /**
     * Returns whether parallel evaluations are enabled.
     * 
     * @return true if parallel evaluations are enabled
     */
    public boolean isParallelEvaluations() {
      return parallelEvaluations;
    }

    /**
     * Returns the number of threads for parallel evaluations.
     * 
     * @return number of threads
     */
    public int getParallelThreads() {
      return parallelThreads;
    }

    /**
     * Returns the grid size for Pareto front generation.
     * 
     * @return Pareto grid size
     */
    public int getParetoGridSize() {
      return paretoGridSize;
    }

    public int getSwarmSize() {
      return swarmSize;
    }

    public double getInertiaWeight() {
      return inertiaWeight;
    }

    public double getCognitiveWeight() {
      return cognitiveWeight;
    }

    public double getSocialWeight() {
      return socialWeight;
    }

    public double getColumnFsFactorLimit() {
      return columnFsFactorLimit;
    }

    /**
     * Gets the rate unit for the optimization.
     *
     * @return the rate unit string
     */
    public String getRateUnit() {
      return rateUnit;
    }

    /**
     * Gets the lower bound for the search range.
     *
     * @return the lower bound
     */
    public double getLowerBound() {
      return lowerBound;
    }

    /**
     * Gets the upper bound for the search range.
     *
     * @return the upper bound
     */
    public double getUpperBound() {
      return upperBound;
    }

    /**
     * Gets the search mode (algorithm) for the optimization.
     *
     * @return the search mode
     */
    public SearchMode getSearchMode() {
      return searchMode;
    }

    /**
     * Gets the convergence tolerance.
     *
     * @return the tolerance value
     */
    public double getTolerance() {
      return tolerance;
    }
  }

  /** Scenario definition to enable side-by-side optimization comparisons. */
  public static final class ScenarioRequest {
    private final String name;
    private final ProcessSystem process;
    private final StreamInterface feedStream;
    private final List<ManipulatedVariable> variables;
    private final OptimizationConfig config;
    private final List<OptimizationObjective> objectives;
    private final List<OptimizationConstraint> constraints;

    public ScenarioRequest(String name, ProcessSystem process, StreamInterface feedStream,
        OptimizationConfig config, List<OptimizationObjective> objectives,
        List<OptimizationConstraint> constraints) {
      this.name = Objects.requireNonNull(name, "Scenario name is required");
      this.process = Objects.requireNonNull(process, "Scenario process is required");
      this.feedStream = Objects.requireNonNull(feedStream, "Scenario feed stream is required");
      this.variables = Collections.emptyList();
      this.config = Objects.requireNonNull(config, "Scenario config is required");
      this.objectives = objectives == null ? Collections.emptyList() : new ArrayList<>(objectives);
      this.constraints = constraints == null ? Collections.emptyList() : new ArrayList<>(constraints);
    }

    public ScenarioRequest(String name, ProcessSystem process, List<ManipulatedVariable> variables,
        OptimizationConfig config, List<OptimizationObjective> objectives,
        List<OptimizationConstraint> constraints) {
      this.name = Objects.requireNonNull(name, "Scenario name is required");
      this.process = Objects.requireNonNull(process, "Scenario process is required");
      this.feedStream = null;
      this.variables = variables == null ? Collections.emptyList() : new ArrayList<>(variables);
      this.config = Objects.requireNonNull(config, "Scenario config is required");
      this.objectives = objectives == null ? Collections.emptyList() : new ArrayList<>(objectives);
      this.constraints = constraints == null ? Collections.emptyList() : new ArrayList<>(constraints);
    }

    public String getName() {
      return name;
    }

    public ProcessSystem getProcess() {
      return process;
    }

    public StreamInterface getFeedStream() {
      return feedStream;
    }

    public List<ManipulatedVariable> getVariables() {
      return variables;
    }

    public OptimizationConfig getConfig() {
      return config;
    }

    public List<OptimizationObjective> getObjectives() {
      return objectives;
    }

    public List<OptimizationConstraint> getConstraints() {
      return constraints;
    }
  }

  /**
   * Definition of a manipulated decision variable for multi-variable
   * optimization.
   *
   * <p>
   * A manipulated variable represents a process parameter that can be adjusted
   * during optimization.
   * Each variable has bounds, a unit, and a setter function that applies the
   * value to the process
   * model.
   * </p>
   *
   * <p>
   * <strong>Java Example</strong>
   * </p>
   * 
   * <pre>{@code
   * ManipulatedVariable flowVar = new ManipulatedVariable("feedFlow", 50000.0, 200000.0, // lower/upper
   *                                                                                      // bounds
   *     "kg/hr", (proc, val) -> ((Stream) proc.getUnit("feed")).setFlowRate(val, "kg/hr"));
   * }</pre>
   *
   * <p>
   * <strong>Python Example (via JPype)</strong>
   * </p>
   * 
   * <pre>
   * from jpype import JImplements, JOverride
   *
   * &#64;JImplements("java.util.function.BiConsumer")
   * class FlowSetter:
   *     &#64;JOverride
   *     def accept(self, proc, val):
   *         proc.getUnit("feed").setFlowRate(float(val), "kg/hr")
   *
   * flow_var = ManipulatedVariable("feedFlow", 50000.0, 200000.0, "kg/hr", FlowSetter())
   * </pre>
   */
  public static final class ManipulatedVariable {
    private final String name;
    private final double lowerBound;
    private final double upperBound;
    private final String unit;
    private final java.util.function.BiConsumer<ProcessSystem, Double> setter;

    /**
     * Constructs a manipulated variable.
     *
     * @param name       unique name identifying this variable
     * @param lowerBound minimum allowed value for the variable
     * @param upperBound maximum allowed value for the variable
     * @param unit       engineering unit string (e.g., "kg/hr", "bara", "C")
     * @param setter     BiConsumer that applies the variable value to the process
     *                   model
     * @throws NullPointerException if name or setter is null
     */
    public ManipulatedVariable(String name, double lowerBound, double upperBound, String unit,
        java.util.function.BiConsumer<ProcessSystem, Double> setter) {
      this.name = Objects.requireNonNull(name, "Variable name is required");
      this.lowerBound = lowerBound;
      this.upperBound = upperBound;
      this.unit = unit;
      this.setter = Objects.requireNonNull(setter, "Variable setter is required");
    }

    /**
     * Returns the variable name.
     *
     * @return variable name
     */
    public String getName() {
      return name;
    }

    /**
     * Returns the lower bound.
     *
     * @return minimum allowed value
     */
    public double getLowerBound() {
      return lowerBound;
    }

    /**
     * Returns the upper bound.
     *
     * @return maximum allowed value
     */
    public double getUpperBound() {
      return upperBound;
    }

    /**
     * Returns the engineering unit.
     *
     * @return unit string
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Applies the variable value to the process model.
     *
     * @param process the process system to modify
     * @param value   the value to set
     */
    public void apply(ProcessSystem process, double value) {
      setter.accept(process, value);
    }
  }

  /**
   * Optimization result paired with a scenario name to aid reporting/comparison.
   */
  public static final class ScenarioResult {
    private final String name;
    private final OptimizationResult result;

    public ScenarioResult(String name, OptimizationResult result) {
      this.name = name;
      this.result = result;
    }

    public String getName() {
      return name;
    }

    public OptimizationResult getResult() {
      return result;
    }
  }

  /** Definition of a KPI to report alongside scenario comparisons. */
  public static final class ScenarioKpi {
    private final String name;
    private final String unit;
    private final java.util.function.ToDoubleFunction<OptimizationResult> metric;

    public ScenarioKpi(String name, String unit,
        java.util.function.ToDoubleFunction<OptimizationResult> metric) {
      this.name = Objects.requireNonNull(name, "name");
      this.unit = unit;
      this.metric = Objects.requireNonNull(metric, "metric");
    }

    public String getName() {
      return name;
    }

    public String getUnit() {
      return unit;
    }

    public double evaluate(OptimizationResult result) {
      return metric.applyAsDouble(result);
    }

    /** Convenience KPI for reporting optimal rate. */
    public static ScenarioKpi optimalRate(String unitLabel) {
      return new ScenarioKpi("optimalRate", unitLabel, OptimizationResult::getOptimalRate);
    }

    /** Convenience KPI for reporting solver score. */
    public static ScenarioKpi score() {
      return new ScenarioKpi("score", null, OptimizationResult::getScore);
    }

    /** KPI that returns a specific objective value by name. */
    public static ScenarioKpi objectiveValue(String objectiveName) {
      return new ScenarioKpi(objectiveName, null,
          result -> result.getObjectiveValues().getOrDefault(objectiveName, Double.NaN));
    }
  }

  /** Per-scenario KPI values and deltas versus the baseline scenario. */
  public static final class ScenarioComparisonResult {
    private final String baselineScenario;
    private final List<ScenarioResult> scenarioResults;
    private final Map<String, Map<String, Double>> kpiValues;
    private final Map<String, Map<String, Double>> kpiDeltas;

    public ScenarioComparisonResult(String baselineScenario, List<ScenarioResult> scenarioResults,
        Map<String, Map<String, Double>> kpiValues, Map<String, Map<String, Double>> kpiDeltas) {
      this.baselineScenario = Objects.requireNonNull(baselineScenario, "baselineScenario");
      this.scenarioResults = new ArrayList<>(Objects.requireNonNull(scenarioResults, "results"));
      this.kpiValues = new HashMap<>(Objects.requireNonNull(kpiValues, "kpiValues"));
      this.kpiDeltas = new HashMap<>(Objects.requireNonNull(kpiDeltas, "kpiDeltas"));
    }

    public String getBaselineScenario() {
      return baselineScenario;
    }

    public List<ScenarioResult> getScenarioResults() {
      return scenarioResults;
    }

    public Map<String, Map<String, Double>> getKpiValues() {
      return kpiValues;
    }

    public Map<String, Map<String, Double>> getKpiDeltas() {
      return kpiDeltas;
    }
  }

  /**
   * A single point on the Pareto front representing a non-dominated solution.
   */
  public static final class ParetoPoint {
    private final Map<String, Double> decisionVariables;
    private final Map<String, Double> objectiveValues;
    private final double[] weights;
    private final boolean feasible;
    private final OptimizationResult fullResult;

    /**
     * Constructs a Pareto point.
     *
     * @param decisionVariables the decision variable values at this point
     * @param objectiveValues   the objective function values at this point
     * @param weights           the weight combination used to find this point
     * @param feasible          whether this point satisfies all constraints
     * @param fullResult        the full optimization result for this point
     */
    public ParetoPoint(Map<String, Double> decisionVariables, Map<String, Double> objectiveValues,
        double[] weights, boolean feasible, OptimizationResult fullResult) {
      this.decisionVariables = new LinkedHashMap<>(decisionVariables);
      this.objectiveValues = new LinkedHashMap<>(objectiveValues);
      this.weights = weights.clone();
      this.feasible = feasible;
      this.fullResult = fullResult;
    }

    public Map<String, Double> getDecisionVariables() {
      return new LinkedHashMap<>(decisionVariables);
    }

    public Map<String, Double> getObjectiveValues() {
      return new LinkedHashMap<>(objectiveValues);
    }

    public double[] getWeights() {
      return weights.clone();
    }

    public boolean isFeasible() {
      return feasible;
    }

    public OptimizationResult getFullResult() {
      return fullResult;
    }

    /**
     * Checks if this point dominates another point (all objectives at least as
     * good, one strictly
     * better).
     *
     * @param other          the other point to compare
     * @param objectiveTypes map of objective names to their types
     *                       (MAXIMIZE/MINIMIZE)
     * @return true if this point dominates the other
     */
    public boolean dominates(ParetoPoint other, Map<String, ObjectiveType> objectiveTypes) {
      boolean atLeastOneBetter = false;
      for (String objName : objectiveValues.keySet()) {
        double thisVal = objectiveValues.getOrDefault(objName, 0.0);
        double otherVal = other.objectiveValues.getOrDefault(objName, 0.0);
        ObjectiveType type = objectiveTypes.getOrDefault(objName, ObjectiveType.MAXIMIZE);
        int comparison = type == ObjectiveType.MAXIMIZE ? Double.compare(thisVal, otherVal)
            : Double.compare(otherVal, thisVal);
        if (comparison < 0) {
          return false; // This point is worse in at least one objective
        }
        if (comparison > 0) {
          atLeastOneBetter = true;
        }
      }
      return atLeastOneBetter;
    }
  }

  /**
   * Result of a multi-objective Pareto optimization containing the Pareto front.
   */
  public static final class ParetoResult {
    private final List<ParetoPoint> paretoFront;
    private final List<ParetoPoint> allPoints;
    private final List<String> objectiveNames;
    private final Map<String, ObjectiveType> objectiveTypes;
    private final int totalIterations;

    /**
     * Constructs a Pareto result.
     *
     * @param paretoFront     the non-dominated solutions forming the Pareto front
     * @param allPoints       all evaluated points (including dominated ones)
     * @param objectiveNames  names of the objectives in order
     * @param objectiveTypes  types (MAXIMIZE/MINIMIZE) for each objective
     * @param totalIterations total number of optimization iterations across all
     *                        weights
     */
    public ParetoResult(List<ParetoPoint> paretoFront, List<ParetoPoint> allPoints,
        List<String> objectiveNames, Map<String, ObjectiveType> objectiveTypes,
        int totalIterations) {
      this.paretoFront = new ArrayList<>(paretoFront);
      this.allPoints = new ArrayList<>(allPoints);
      this.objectiveNames = new ArrayList<>(objectiveNames);
      this.objectiveTypes = new LinkedHashMap<>(objectiveTypes);
      this.totalIterations = totalIterations;
    }

    /**
     * Returns the Pareto front (non-dominated solutions only).
     *
     * @return list of Pareto-optimal points
     */
    public List<ParetoPoint> getParetoFront() {
      return new ArrayList<>(paretoFront);
    }

    /**
     * Returns all evaluated points including dominated ones.
     *
     * @return list of all points
     */
    public List<ParetoPoint> getAllPoints() {
      return new ArrayList<>(allPoints);
    }

    /**
     * Returns the objective names in order.
     *
     * @return list of objective names
     */
    public List<String> getObjectiveNames() {
      return new ArrayList<>(objectiveNames);
    }

    /**
     * Returns the objective types.
     *
     * @return map of objective name to type
     */
    public Map<String, ObjectiveType> getObjectiveTypes() {
      return new LinkedHashMap<>(objectiveTypes);
    }

    /**
     * Returns the total number of iterations across all weight combinations.
     *
     * @return total iterations
     */
    public int getTotalIterations() {
      return totalIterations;
    }

    /**
     * Returns the number of points on the Pareto front.
     *
     * @return Pareto front size
     */
    public int getParetoFrontSize() {
      return paretoFront.size();
    }

    /**
     * Returns the utopia point (best value for each objective independently).
     *
     * @return map of objective name to utopia value
     */
    public Map<String, Double> getUtopiaPoint() {
      Map<String, Double> utopia = new LinkedHashMap<>();
      for (String objName : objectiveNames) {
        ObjectiveType type = objectiveTypes.getOrDefault(objName, ObjectiveType.MAXIMIZE);
        double best = type == ObjectiveType.MAXIMIZE ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (ParetoPoint point : paretoFront) {
          double val = point.getObjectiveValues().getOrDefault(objName, 0.0);
          if (type == ObjectiveType.MAXIMIZE) {
            best = Math.max(best, val);
          } else {
            best = Math.min(best, val);
          }
        }
        utopia.put(objName, best);
      }
      return utopia;
    }

    /**
     * Returns the nadir point (worst value for each objective on the Pareto front).
     *
     * @return map of objective name to nadir value
     */
    public Map<String, Double> getNadirPoint() {
      Map<String, Double> nadir = new LinkedHashMap<>();
      for (String objName : objectiveNames) {
        ObjectiveType type = objectiveTypes.getOrDefault(objName, ObjectiveType.MAXIMIZE);
        double worst = type == ObjectiveType.MAXIMIZE ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        for (ParetoPoint point : paretoFront) {
          double val = point.getObjectiveValues().getOrDefault(objName, 0.0);
          if (type == ObjectiveType.MAXIMIZE) {
            worst = Math.min(worst, val);
          } else {
            worst = Math.max(worst, val);
          }
        }
        nadir.put(objName, worst);
      }
      return nadir;
    }

    /**
     * Formats the Pareto front as a Markdown table.
     *
     * @return Markdown table string
     */
    public String toMarkdownTable() {
      StringBuilder sb = new StringBuilder();
      sb.append("| # | Feasible |");
      for (String objName : objectiveNames) {
        sb.append(" ").append(objName).append(" |");
      }
      sb.append(" Weights |\n");
      sb.append("|---|---|");
      for (int i = 0; i < objectiveNames.size(); i++) {
        sb.append("---|");
      }
      sb.append("---|\n");

      int idx = 1;
      for (ParetoPoint point : paretoFront) {
        sb.append("| ").append(idx++).append(" | ").append(point.isFeasible() ? "yes" : "no")
            .append(" |");
        for (String objName : objectiveNames) {
          double val = point.getObjectiveValues().getOrDefault(objName, Double.NaN);
          sb.append(String.format(" %.4f |", val));
        }
        sb.append(" [");
        double[] w = point.getWeights();
        for (int i = 0; i < w.length; i++) {
          if (i > 0) {
            sb.append(", ");
          }
          sb.append(String.format("%.2f", w[i]));
        }
        sb.append("] |\n");
      }
      return sb.toString();
    }
  }

  /** Result of a single iteration. */
  private static final class Evaluation {
    private final double bottleneckUtilization;
    private final ProcessEquipmentInterface bottleneck;
    private final List<UtilizationRecord> utilizationRecords;
    private final List<ConstraintStatus> constraintStatuses;
    private final Map<String, Double> objectiveValues;
    private final Map<String, Double> decisionVariables;
    private final boolean utilizationWithinLimits;
    private final boolean hardOk;
    private final double score;

    Evaluation(double bottleneckUtilization, ProcessEquipmentInterface bottleneck,
        List<UtilizationRecord> utilizationRecords, List<ConstraintStatus> constraintStatuses,
        Map<String, Double> objectiveValues, Map<String, Double> decisionVariables,
        boolean utilizationWithinLimits, boolean hardOk, double score) {
      this.bottleneckUtilization = bottleneckUtilization;
      this.bottleneck = bottleneck;
      this.utilizationRecords = utilizationRecords;
      this.constraintStatuses = constraintStatuses;
      this.objectiveValues = objectiveValues;
      this.decisionVariables = decisionVariables == null ? Collections.emptyMap() : new HashMap<>(decisionVariables);
      this.utilizationWithinLimits = utilizationWithinLimits;
      this.hardOk = hardOk;
      this.score = score;
    }

    double bottleneckUtilization() {
      return bottleneckUtilization;
    }

    ProcessEquipmentInterface bottleneck() {
      return bottleneck;
    }

    List<UtilizationRecord> utilizationRecords() {
      return utilizationRecords;
    }

    List<ConstraintStatus> constraintStatuses() {
      return constraintStatuses;
    }

    Map<String, Double> objectiveValues() {
      return objectiveValues;
    }

    Map<String, Double> decisionVariables() {
      return decisionVariables;
    }

    boolean utilizationWithinLimits() {
      return utilizationWithinLimits;
    }

    boolean hardOk() {
      return hardOk;
    }

    double score() {
      return score;
    }
  }

  /**
   * Optimize the feed stream rate of a process to respect utilization limits and
   * constraints.
   *
   * <p>
   * This is the primary optimization method for single-variable (flow rate)
   * optimization. It
   * adjusts the flow rate of the specified feed stream to maximize throughput
   * while respecting
   * equipment utilization limits and any specified constraints.
   * </p>
   *
   * <p>
   * <strong>Algorithm Selection</strong>
   * </p>
   * <p>
   * The search algorithm is determined by {@link OptimizationConfig#searchMode}:
   * </p>
   * <ul>
   * <li><b>BINARY_FEASIBILITY</b> - Fast binary search assuming monotonic
   * feasibility</li>
   * <li><b>GOLDEN_SECTION_SCORE</b> - Golden-section search for non-monotonic
   * responses</li>
   * <li><b>NELDER_MEAD_SCORE</b> - Simplex method (overkill for 1D, but
   * supported)</li>
   * <li><b>PARTICLE_SWARM_SCORE</b> - Global search for multi-modal problems</li>
   * </ul>
   *
   * <p>
   * <strong>Java Example</strong>
   * </p>
   * 
   * <pre>{@code
   * ProductionOptimizer optimizer = new ProductionOptimizer();
   * OptimizationConfig config = new OptimizationConfig(50000.0, 200000.0)
   *     .searchMode(SearchMode.GOLDEN_SECTION_SCORE).tolerance(100.0);
   *
   * OptimizationResult result = optimizer.optimize(process, feedStream, config, null, null);
   * System.out.println("Optimal: " + result.getOptimalRate() + " " + result.getRateUnit());
   * }</pre>
   *
   * <p>
   * <strong>Python Example (via JPype)</strong>
   * </p>
   * 
   * <pre>{@code
   * optimizer = ProductionOptimizer()
   * config = OptimizationConfig(50000.0, 200000.0) \
   *     .searchMode(SearchMode.GOLDEN_SECTION_SCORE) \
   *     .tolerance(100.0)
   *
   * result = optimizer.optimize(process, feed_stream, config, None, None)
   * print(f"Optimal: {result.getOptimalRate():.0f} {result.getRateUnit()}")
   * }</pre>
   *
   * @param process     the process model to evaluate (must not be null)
   * @param feedStream  the feed stream whose flow rate will be adjusted (must not
   *                    be null)
   * @param config      optimizer configuration including bounds and algorithm
   *                    (must not be null)
   * @param objectives  list of objectives to compute weighted scores (may be null
   *                    or empty)
   * @param constraints list of constraints with optional penalties (may be null
   *                    or empty)
   * @return optimization result containing optimal rate, bottleneck, and
   *         diagnostics
   * @throws NullPointerException if process, feedStream, or config is null
   */
  public OptimizationResult optimize(ProcessSystem process, StreamInterface feedStream,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints) {
    Objects.requireNonNull(process, "ProcessSystem is required");
    Objects.requireNonNull(feedStream, "Feed stream is required");
    Objects.requireNonNull(config, "OptimizationConfig is required");
    List<OptimizationObjective> safeObjectives = objectives == null ? Collections.emptyList()
        : new ArrayList<>(objectives);
    List<OptimizationConstraint> safeConstraints = constraints == null ? Collections.emptyList()
        : new ArrayList<>(constraints);

    List<IterationRecord> iterationHistory = new ArrayList<>();

    if (config.searchMode == SearchMode.GOLDEN_SECTION_SCORE) {
      return goldenSectionSearch(process, feedStream, config, safeObjectives, safeConstraints,
          iterationHistory);
    }
    if (config.searchMode == SearchMode.NELDER_MEAD_SCORE) {
      return nelderMeadSearch(process, feedStream, config, safeObjectives, safeConstraints,
          iterationHistory);
    }
    if (config.searchMode == SearchMode.PARTICLE_SWARM_SCORE) {
      return particleSwarmSearch(process, feedStream, config, safeObjectives, safeConstraints,
          iterationHistory);
    }
    if (config.searchMode == SearchMode.GRADIENT_DESCENT_SCORE) {
      return gradientDescentSearch(process, feedStream, config, safeObjectives, safeConstraints,
          iterationHistory);
    }
    return binaryFeasibilitySearch(process, feedStream, config, safeObjectives, safeConstraints,
        iterationHistory);
  }

  /**
   * Optimize multiple manipulated variables using multi-dimensional search
   * strategies.
   *
   * <p>
   * This method extends single-variable optimization to multiple decision
   * variables such as flow
   * rates, pressures, temperatures, or split ratios. It uses Nelder-Mead or
   * Particle Swarm
   * algorithms for multi-dimensional search.
   * </p>
   *
   * <p>
   * <strong>Algorithm Selection for Multi-Variable</strong>
   * </p>
   * <ul>
   * <li><b>NELDER_MEAD_SCORE</b> - Recommended for 2-10 variables,
   * derivative-free</li>
   * <li><b>PARTICLE_SWARM_SCORE</b> - Better for non-convex problems with local
   * optima</li>
   * </ul>
   *
   * <p>
   * <strong>Java Example</strong>
   * </p>
   * 
   * <pre>{@code
   * List<ManipulatedVariable> variables = Arrays.asList(
   *     new ManipulatedVariable("flow", 50000, 200000, "kg/hr",
   *         (p, v) -> ((Stream) p.getUnit("feed")).setFlowRate(v, "kg/hr")),
   *     new ManipulatedVariable("pressure", 100, 200, "bara",
   *         (p, v) -> ((Compressor) p.getUnit("comp")).setOutletPressure(v, "bara")));
   *
   * OptimizationConfig config = new OptimizationConfig(0, 1) // bounds from variables
   *     .searchMode(SearchMode.NELDER_MEAD_SCORE);
   *
   * OptimizationResult result = optimizer.optimize(process, variables, config, objectives, null);
   * Map<String, Double> optimalValues = result.getDecisionVariables();
   * }</pre>
   *
   * <p>
   * <strong>Python Example (via JPype)</strong>
   * </p>
   * 
   * <pre>
   * from jpype import JImplements, JOverride
   * Arrays = jneqsim.java.util.Arrays
   *
   * &#64;JImplements("java.util.function.BiConsumer")
   * class FlowSetter:
   *     &#64;JOverride
   *     def accept(self, proc, val):
   *         proc.getUnit("feed").setFlowRate(float(val), "kg/hr")
   *
   * variables = Arrays.asList([
   *     ManipulatedVariable("flow", 50000, 200000, "kg/hr", FlowSetter())
   * ])
   * result = optimizer.optimize(process, variables, config, None, None)
   * </pre>
   *
   * @param process     the process model to evaluate (must not be null)
   * @param variables   list of manipulated variables with bounds and setters
   *                    (must not be empty)
   * @param config      optimizer configuration (must not be null)
   * @param objectives  list of objectives (may be null or empty)
   * @param constraints list of constraints (may be null or empty)
   * @return optimization result with optimal variable values in
   *         {@code getDecisionVariables()}
   * @throws NullPointerException     if process, variables, or config is null
   * @throws IllegalArgumentException if variables is empty or algorithm doesn't
   *                                  support
   *                                  multi-variable
   */
  public OptimizationResult optimize(ProcessSystem process, List<ManipulatedVariable> variables,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints) {
    Objects.requireNonNull(process, "ProcessSystem is required");
    Objects.requireNonNull(variables, "Variables are required");
    Objects.requireNonNull(config, "OptimizationConfig is required");
    if (variables.isEmpty()) {
      throw new IllegalArgumentException("At least one variable is required");
    }
    List<OptimizationObjective> safeObjectives = objectives == null ? Collections.emptyList()
        : new ArrayList<>(objectives);
    List<OptimizationConstraint> safeConstraints = constraints == null ? Collections.emptyList()
        : new ArrayList<>(constraints);
    List<IterationRecord> iterationHistory = new ArrayList<>();

    if (variables.size() > 1 && (config.searchMode == SearchMode.BINARY_FEASIBILITY
        || config.searchMode == SearchMode.GOLDEN_SECTION_SCORE)) {
      throw new IllegalArgumentException(
          "Binary and golden-section searches support only one decision variable");
    }

    if (variables.size() == 1 && config.searchMode == SearchMode.BINARY_FEASIBILITY) {
      return binaryFeasibilitySearch(process, variables, config, safeObjectives, safeConstraints,
          iterationHistory);
    }
    if (variables.size() == 1 && config.searchMode == SearchMode.GOLDEN_SECTION_SCORE) {
      return goldenSectionSearch(process, variables, config, safeObjectives, safeConstraints,
          iterationHistory);
    }
    if (config.searchMode == SearchMode.NELDER_MEAD_SCORE) {
      return nelderMeadSearch(process, variables, config, safeObjectives, safeConstraints,
          iterationHistory);
    }
    if (config.searchMode == SearchMode.GRADIENT_DESCENT_SCORE) {
      return gradientDescentSearch(process, variables, config, safeObjectives, safeConstraints,
          iterationHistory);
    }
    return particleSwarmSearch(process, variables, config, safeObjectives, safeConstraints,
        iterationHistory);
  }

  /**
   * Optimize a collection of named scenarios and return results for side-by-side
   * comparison.
   *
   * <p>
   * If parallel evaluations are enabled in any scenario's config, scenarios will
   * be optimized
   * concurrently using a thread pool.
   * </p>
   *
   * @param scenarios scenarios containing process, feed, config, objectives, and
   *                  constraints
   * @return list of scenario results in the same order as provided
   */
  public List<ScenarioResult> optimizeScenarios(List<ScenarioRequest> scenarios) {
    Objects.requireNonNull(scenarios, "scenarios are required");

    // Check if any scenario has parallel enabled
    boolean anyParallel = scenarios.stream()
        .anyMatch(s -> s.getConfig() != null && s.getConfig().isParallelEvaluations());

    if (anyParallel && scenarios.size() > 1) {
      return optimizeScenariosParallel(scenarios);
    }

    // Sequential execution
    List<ScenarioResult> results = new ArrayList<>();
    for (ScenarioRequest scenario : scenarios) {
      OptimizationResult result;
      if (scenario.getVariables() != null && !scenario.getVariables().isEmpty()) {
        result = optimize(scenario.getProcess(), scenario.getVariables(), scenario.getConfig(),
            scenario.getObjectives(), scenario.getConstraints());
      } else {
        result = optimize(scenario.getProcess(), scenario.getFeedStream(), scenario.getConfig(),
            scenario.getObjectives(), scenario.getConstraints());
      }
      results.add(new ScenarioResult(scenario.getName(), result));
    }
    return results;
  }

  /**
   * Optimize scenarios in parallel using a thread pool.
   *
   * @param scenarios list of scenario requests to optimize
   * @return list of scenario results in the same order as input
   */
  private List<ScenarioResult> optimizeScenariosParallel(List<ScenarioRequest> scenarios) {
    int threads = scenarios.stream().filter(s -> s.getConfig() != null)
        .mapToInt(s -> s.getConfig().getParallelThreads()).max()
        .orElse(Runtime.getRuntime().availableProcessors());

    ExecutorService executor = Executors.newFixedThreadPool(Math.min(threads, scenarios.size()));
    List<Future<ScenarioResult>> futures = new ArrayList<>();

    for (ScenarioRequest scenario : scenarios) {
      futures.add(executor.submit(() -> {
        OptimizationResult result;
        if (scenario.getVariables() != null && !scenario.getVariables().isEmpty()) {
          result = optimize(scenario.getProcess(), scenario.getVariables(), scenario.getConfig(),
              scenario.getObjectives(), scenario.getConstraints());
        } else {
          result = optimize(scenario.getProcess(), scenario.getFeedStream(), scenario.getConfig(),
              scenario.getObjectives(), scenario.getConstraints());
        }
        return new ScenarioResult(scenario.getName(), result);
      }));
    }

    List<ScenarioResult> results = new ArrayList<>();
    for (Future<ScenarioResult> future : futures) {
      try {
        results.add(future.get());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Scenario optimization interrupted", e);
      } catch (ExecutionException e) {
        throw new RuntimeException("Scenario optimization failed", e.getCause());
      }
    }

    executor.shutdown();
    return results;
  }

  /**
   * Perform multi-objective Pareto optimization using weighted-sum scalarization.
   *
   * <p>
   * This method generates a Pareto front by solving a series of single-objective
   * problems with
   * different weight combinations. The weighted-sum approach converts the
   * multi-objective problem
   * into a sequence of single-objective problems by combining objectives with
   * weights.
   * </p>
   *
   * <p>
   * <strong>How It Works</strong>
   * </p>
   * <ol>
   * <li>Generate weight combinations based on {@code paretoGridSize} (e.g., for 2
   * objectives with
   * gridSize=11: [1.0,0.0], [0.9,0.1], ..., [0.0,1.0])</li>
   * <li>For each weight combination, solve the weighted single-objective
   * problem</li>
   * <li>Filter dominated solutions to obtain the Pareto front</li>
   * </ol>
   *
   * <p>
   * <strong>Java Example</strong>
   * </p>
   * 
   * <pre>{@code
   * List<OptimizationObjective> objectives = Arrays.asList(
   *     new OptimizationObjective("throughput", p -> p.getUnit("outlet").getFlowRate("kg/hr"), 1.0,
   *         ObjectiveType.MAXIMIZE),
   *     new OptimizationObjective("power", p -> ((Compressor) p.getUnit("comp")).getPower("kW"), 1.0,
   *         ObjectiveType.MINIMIZE));
   *
   * OptimizationConfig config = new OptimizationConfig(50000, 200000).paretoGridSize(11) // generates
   *                                                                                      // 11 weight
   *                                                                                      // combinations
   *     .searchMode(SearchMode.GOLDEN_SECTION_SCORE);
   *
   * ParetoResult pareto = optimizer.optimizePareto(process, feed, config, objectives, null);
   *
   * System.out.println("Pareto front size: " + pareto.getParetoFrontSize());
   * System.out.println(pareto.toMarkdownTable());
   * }</pre>
   *
   * <p>
   * <strong>Python Example (via JPype)</strong>
   * </p>
   * 
   * <pre>{@code
   * objectives = Arrays.asList([throughput_obj, power_obj])
   * config = OptimizationConfig(50000, 200000).paretoGridSize(11)
   *
   * pareto = optimizer.optimizePareto(process, feed, config, objectives, None)
   * print(f"Pareto front: {pareto.getParetoFrontSize()} points")
   * print(pareto.toMarkdownTable())
   * }</pre>
   *
   * @param process     the process model to evaluate (must not be null)
   * @param feedStream  the feed stream whose flow rate will be adjusted (must not
   *                    be null)
   * @param config      optimizer configuration; {@code paretoGridSize} controls
   *                    weight granularity
   * @param objectives  list of objectives (must have at least 2 for Pareto
   *                    optimization)
   * @param constraints list of constraints (may be null or empty)
   * @return Pareto result containing the Pareto front, utopia/nadir points, and
   *         all evaluated
   *         points
   * @throws NullPointerException     if process, feedStream, config, or
   *                                  objectives is null
   * @throws IllegalArgumentException if fewer than 2 objectives are provided
   */
  public ParetoResult optimizePareto(ProcessSystem process, StreamInterface feedStream,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints) {
    Objects.requireNonNull(process, "ProcessSystem is required");
    Objects.requireNonNull(feedStream, "Feed stream is required");
    Objects.requireNonNull(config, "OptimizationConfig is required");
    Objects.requireNonNull(objectives, "Objectives are required for Pareto optimization");
    if (objectives.size() < 2) {
      throw new IllegalArgumentException("Pareto optimization requires at least 2 objectives");
    }

    List<String> objectiveNames = new ArrayList<>();
    Map<String, ObjectiveType> objectiveTypes = new LinkedHashMap<>();
    for (OptimizationObjective obj : objectives) {
      objectiveNames.add(obj.getName());
      objectiveTypes.put(obj.getName(), obj.getType());
    }

    List<double[]> weightCombinations = generateWeightCombinations(objectives.size(), config.getParetoGridSize());

    List<ParetoPoint> allPoints = new ArrayList<>();
    int totalIterations = 0;

    // Determine if we should run in parallel
    if (config.isParallelEvaluations() && weightCombinations.size() > 1) {
      allPoints = optimizeParetoParallel(process, feedStream, config, objectives, constraints,
          weightCombinations, objectiveNames);
      totalIterations = allPoints.size() * config.getMaxIterations();
    } else {
      // Sequential execution
      for (double[] weights : weightCombinations) {
        List<OptimizationObjective> weightedObjectives = createWeightedObjectives(objectives, weights);
        OptimizationResult result = optimize(process, feedStream, config, weightedObjectives, constraints);
        totalIterations += result.getIterations();

        ParetoPoint point = new ParetoPoint(result.getDecisionVariables(),
            result.getObjectiveValues(), weights, result.isFeasible(), result);
        allPoints.add(point);
      }
    }

    // Filter to Pareto front (non-dominated solutions)
    List<ParetoPoint> paretoFront = filterToPareto(allPoints, objectiveTypes);

    return new ParetoResult(paretoFront, allPoints, objectiveNames, objectiveTypes,
        totalIterations);
  }

  /**
   * Perform multi-objective Pareto optimization with multiple manipulated
   * variables.
   *
   * <p>
   * This extends Pareto optimization to support multiple decision variables
   * (e.g., flow rate and
   * pressure simultaneously). Uses Nelder-Mead or PSO for multi-dimensional
   * search at each weight
   * combination.
   * </p>
   *
   * <p>
   * <strong>Java Example</strong>
   * </p>
   * 
   * <pre>{@code
   * List<ManipulatedVariable> variables = Arrays.asList(
   *     new ManipulatedVariable("flow", 50000, 200000, "kg/hr", flowSetter),
   *     new ManipulatedVariable("pressure", 100, 200, "bara", pressureSetter));
   *
   * List<OptimizationObjective> objectives = Arrays.asList(throughputObj, powerObj);
   *
   * OptimizationConfig config = new OptimizationConfig(0, 1).paretoGridSize(11).searchMode(SearchMode.NELDER_MEAD_SCORE);
   *
   * ParetoResult pareto = optimizer.optimizePareto(process, variables, config, objectives, null);
   * }</pre>
   *
   * @param process     the process model to evaluate (must not be null)
   * @param variables   list of manipulated decision variables (must not be empty)
   * @param config      optimizer configuration (must not be null)
   * @param objectives  list of objectives (must have at least 2)
   * @param constraints list of constraints (may be null or empty)
   * @return Pareto result containing the Pareto front and all evaluated points
   * @throws NullPointerException     if process, variables, config, or objectives
   *                                  is null
   * @throws IllegalArgumentException if fewer than 2 objectives or no variables
   *                                  provided
   */
  public ParetoResult optimizePareto(ProcessSystem process, List<ManipulatedVariable> variables,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints) {
    Objects.requireNonNull(process, "ProcessSystem is required");
    Objects.requireNonNull(variables, "Variables are required");
    Objects.requireNonNull(config, "OptimizationConfig is required");
    Objects.requireNonNull(objectives, "Objectives are required for Pareto optimization");
    if (objectives.size() < 2) {
      throw new IllegalArgumentException("Pareto optimization requires at least 2 objectives");
    }
    if (variables.isEmpty()) {
      throw new IllegalArgumentException("At least one variable is required");
    }

    List<String> objectiveNames = new ArrayList<>();
    Map<String, ObjectiveType> objectiveTypes = new LinkedHashMap<>();
    for (OptimizationObjective obj : objectives) {
      objectiveNames.add(obj.getName());
      objectiveTypes.put(obj.getName(), obj.getType());
    }

    List<double[]> weightCombinations = generateWeightCombinations(objectives.size(), config.getParetoGridSize());

    List<ParetoPoint> allPoints = new ArrayList<>();
    int totalIterations = 0;

    for (double[] weights : weightCombinations) {
      List<OptimizationObjective> weightedObjectives = createWeightedObjectives(objectives, weights);
      OptimizationResult result = optimize(process, variables, config, weightedObjectives, constraints);
      totalIterations += result.getIterations();

      ParetoPoint point = new ParetoPoint(result.getDecisionVariables(),
          result.getObjectiveValues(), weights, result.isFeasible(), result);
      allPoints.add(point);
    }

    List<ParetoPoint> paretoFront = filterToPareto(allPoints, objectiveTypes);

    return new ParetoResult(paretoFront, allPoints, objectiveNames, objectiveTypes,
        totalIterations);
  }

  /**
   * Parallel execution of Pareto weight combinations.
   */
  private List<ParetoPoint> optimizeParetoParallel(ProcessSystem process,
      StreamInterface feedStream, OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints, List<double[]> weightCombinations,
      List<String> objectiveNames) {
    ExecutorService executor = Executors
        .newFixedThreadPool(Math.min(config.getParallelThreads(), weightCombinations.size()));
    List<Future<ParetoPoint>> futures = new ArrayList<>();

    for (double[] weights : weightCombinations) {
      final double[] w = weights.clone();
      futures.add(executor.submit(() -> {
        List<OptimizationObjective> weightedObjectives = createWeightedObjectives(objectives, w);
        OptimizationResult result = optimize(process, feedStream, config, weightedObjectives, constraints);
        return new ParetoPoint(result.getDecisionVariables(), result.getObjectiveValues(), w,
            result.isFeasible(), result);
      }));
    }

    List<ParetoPoint> points = new ArrayList<>();
    for (Future<ParetoPoint> future : futures) {
      try {
        points.add(future.get());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Pareto optimization interrupted", e);
      } catch (ExecutionException e) {
        throw new RuntimeException("Pareto optimization failed", e.getCause());
      }
    }

    executor.shutdown();
    return points;
  }

  /**
   * Generate all weight combinations for a given number of objectives and grid
   * size.
   */
  private List<double[]> generateWeightCombinations(int numObjectives, int gridSize) {
    List<double[]> combinations = new ArrayList<>();
    if (numObjectives == 2) {
      // Simple case: linear combination
      for (int i = 0; i < gridSize; i++) {
        double w1 = (double) i / (gridSize - 1);
        combinations.add(new double[] { 1.0 - w1, w1 });
      }
    } else {
      // General case: recursive simplex grid
      generateWeightCombinationsRecursive(numObjectives, gridSize - 1, new double[numObjectives], 0,
          1.0, combinations);
    }
    return combinations;
  }

  /**
   * Recursively generate weight combinations that sum to 1.
   */
  private void generateWeightCombinationsRecursive(int numObjectives, int divisions,
      double[] current, int index, double remaining, List<double[]> combinations) {
    if (index == numObjectives - 1) {
      current[index] = remaining;
      combinations.add(current.clone());
      return;
    }
    for (int i = 0; i <= divisions; i++) {
      double weight = (double) i / divisions * remaining;
      if (weight <= remaining + 1e-10) {
        current[index] = weight;
        generateWeightCombinationsRecursive(numObjectives, divisions, current, index + 1,
            remaining - weight, combinations);
      }
    }
  }

  /**
   * Create weighted objectives from original objectives and weights.
   */
  private List<OptimizationObjective> createWeightedObjectives(
      List<OptimizationObjective> originals, double[] weights) {
    List<OptimizationObjective> weighted = new ArrayList<>();
    for (int i = 0; i < originals.size(); i++) {
      OptimizationObjective orig = originals.get(i);
      double newWeight = orig.getWeight() * weights[i];
      weighted.add(new OptimizationObjective(orig.getName(), proc -> orig.evaluate(proc), newWeight,
          orig.getType()));
    }
    return weighted;
  }

  /**
   * Filter points to keep only Pareto-optimal (non-dominated) solutions.
   */
  private List<ParetoPoint> filterToPareto(List<ParetoPoint> allPoints,
      Map<String, ObjectiveType> objectiveTypes) {
    List<ParetoPoint> paretoFront = new ArrayList<>();
    for (ParetoPoint candidate : allPoints) {
      boolean dominated = false;
      for (ParetoPoint other : allPoints) {
        if (other != candidate && other.dominates(candidate, objectiveTypes)) {
          dominated = true;
          break;
        }
      }
      if (!dominated) {
        paretoFront.add(candidate);
      }
    }
    return paretoFront;
  }

  /**
   * Optimize multiple scenarios and compute KPI deltas versus the baseline
   * (first) scenario.
   *
   * @param scenarios list of scenarios to optimize; first entry is treated as
   *                  baseline
   * @param kpis      KPIs to compute per scenario (optional)
   * @return comparison result with KPI deltas and raw results
   */
  public ScenarioComparisonResult compareScenarios(List<ScenarioRequest> scenarios,
      List<ScenarioKpi> kpis) {
    Objects.requireNonNull(scenarios, "scenarios are required");
    if (scenarios.isEmpty()) {
      throw new IllegalArgumentException("At least one scenario is required");
    }
    List<ScenarioKpi> safeKpis = kpis == null ? Collections.emptyList() : new ArrayList<>(kpis);
    List<ScenarioResult> results = optimizeScenarios(scenarios);
    Map<String, Map<String, Double>> kpiValues = new LinkedHashMap<>();
    Map<String, Map<String, Double>> kpiDeltas = new LinkedHashMap<>();

    ScenarioResult baseline = results.get(0);
    Map<String, Double> baselineValues = new LinkedHashMap<>();
    for (ScenarioKpi kpi : safeKpis) {
      baselineValues.put(kpi.getName(), kpi.evaluate(baseline.getResult()));
    }

    for (ScenarioResult scenarioResult : results) {
      Map<String, Double> scenarioKpis = new LinkedHashMap<>();
      Map<String, Double> scenarioDelta = new LinkedHashMap<>();
      for (ScenarioKpi kpi : safeKpis) {
        double value = kpi.evaluate(scenarioResult.getResult());
        scenarioKpis.put(kpi.getName(), value);
        double delta = value - baselineValues.getOrDefault(kpi.getName(), 0.0);
        scenarioDelta.put(kpi.getName(), delta);
      }
      kpiValues.put(scenarioResult.getName(), scenarioKpis);
      kpiDeltas.put(scenarioResult.getName(), scenarioDelta);
    }

    return new ScenarioComparisonResult(baseline.getName(), results, kpiValues, kpiDeltas);
  }

  /**
   * Convenience wrapper to maximize throughput with optional constraints and
   * custom search config.
   *
   * @param process               process system to run
   * @param feedStream            feed stream that will be adjusted
   * @param lowerBound            lower bound on the manipulated feed rate
   * @param upperBound            upper bound on the manipulated feed rate
   * @param rateUnit              engineering unit for rate
   * @param additionalConstraints optional hard/soft constraints
   * @return optimization result with utilization and constraint history
   */
  public OptimizationResult optimizeThroughput(ProcessSystem process, StreamInterface feedStream,
      double lowerBound, double upperBound, String rateUnit,
      List<OptimizationConstraint> additionalConstraints) {
    OptimizationConfig config = new OptimizationConfig(lowerBound, upperBound).rateUnit(rateUnit);
    OptimizationObjective throughput = new OptimizationObjective("throughput",
        proc -> feedStream.getFlowRate(rateUnit), 1.0, ObjectiveType.MAXIMIZE);
    return optimize(process, feedStream, config, Collections.singletonList(throughput),
        additionalConstraints);
  }

  /** Render a compact Markdown table describing utilization per unit. */
  public static String formatUtilizationTable(List<UtilizationRecord> records) {
    StringBuilder sb = new StringBuilder();
    sb.append("| Equipment | Duty | Capacity | Utilization | Limit |\n");
    sb.append("|---|---|---|---|---|\n");
    for (UtilizationRecord record : records) {
      sb.append("|").append(record.getEquipmentName()).append("|")
          .append(String.format("%.2f", record.getCapacityDuty())).append("|")
          .append(String.format("%.2f", record.getCapacityMax())).append("|")
          .append(String.format("%.3f", record.getUtilization())).append("|")
          .append(String.format("%.3f", record.getUtilizationLimit())).append("|\n");
    }
    return sb.toString();
  }

  /** Render scenario KPIs and bottleneck information side-by-side. */
  public static String formatScenarioComparisonTable(ScenarioComparisonResult comparison,
      List<ScenarioKpi> kpis) {
    Objects.requireNonNull(comparison, "comparison");
    List<ScenarioKpi> safeKpis = kpis == null ? Collections.emptyList() : new ArrayList<>(kpis);
    StringBuilder sb = new StringBuilder();
    sb.append("| Scenario | Feasible | Optimal Rate | Bottleneck | Score |");
    for (ScenarioKpi kpi : safeKpis) {
      String label = kpi.getUnit() == null ? kpi.getName() : kpi.getName() + " (" + kpi.getUnit() + ")";
      sb.append(label).append(" |");
    }
    sb.append("\n|---|---|---|---|---|");
    for (int i = 0; i < safeKpis.size(); i++) {
      sb.append("---|");
    }
    sb.append("\n");

    for (ScenarioResult scenarioResult : comparison.getScenarioResults()) {
      OptimizationResult result = scenarioResult.getResult();
      sb.append("|").append(scenarioResult.getName()).append("|")
          .append(result.isFeasible() ? "yes" : "no").append("|")
          .append(String.format("%.3f %s", result.getOptimalRate(), result.getRateUnit()))
          .append("|")
          .append(result.getBottleneck() != null ? result.getBottleneck().getName() : "")
          .append("|").append(String.format("%.3f", result.getScore())).append("|");
      for (ScenarioKpi kpi : safeKpis) {
        double value = comparison.getKpiValues().getOrDefault(scenarioResult.getName(), Collections.emptyMap())
            .getOrDefault(kpi.getName(), Double.NaN);
        double delta = comparison.getKpiDeltas().getOrDefault(scenarioResult.getName(), Collections.emptyMap())
            .getOrDefault(kpi.getName(), 0.0);
        sb.append(String.format("%.3f (%.3f)", value, delta)).append("|");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  /**
   * Series-friendly representation of utilization across iterations for
   * plotting/reporting.
   */
  public static final class UtilizationSeries {
    private final String equipmentName;
    private final List<Double> utilizations;
    private final List<Boolean> bottleneckFlags;
    private final double utilizationLimit;

    public UtilizationSeries(String equipmentName, List<Double> utilizations,
        List<Boolean> bottleneckFlags, double utilizationLimit) {
      this.equipmentName = equipmentName;
      this.utilizations = new ArrayList<>(utilizations);
      this.bottleneckFlags = new ArrayList<>(bottleneckFlags);
      this.utilizationLimit = utilizationLimit;
    }

    public String getEquipmentName() {
      return equipmentName;
    }

    public List<Double> getUtilizations() {
      return new ArrayList<>(utilizations);
    }

    public List<Boolean> getBottleneckFlags() {
      return new ArrayList<>(bottleneckFlags);
    }

    public double getUtilizationLimit() {
      return utilizationLimit;
    }
  }

  /**
   * Build utilization series for each equipment across the provided iteration
   * history to facilitate
   * charting or CSV export.
   */
  public static List<UtilizationSeries> buildUtilizationSeries(
      List<IterationRecord> iterationHistory) {
    Objects.requireNonNull(iterationHistory, "iterationHistory is required");
    Map<String, List<Double>> utilizationByEquipment = new LinkedHashMap<>();
    Map<String, List<Boolean>> bottleneckFlags = new LinkedHashMap<>();
    Map<String, Double> limits = new HashMap<>();

    int iterationIndex = 0;
    for (IterationRecord record : iterationHistory) {
      for (UtilizationRecord utilization : record.getUtilizations()) {
        utilizationByEquipment
            .computeIfAbsent(utilization.getEquipmentName(), k -> new ArrayList<>())
            .add(utilization.getUtilization());
        bottleneckFlags.computeIfAbsent(utilization.getEquipmentName(), k -> new ArrayList<>());
        limits.putIfAbsent(utilization.getEquipmentName(), utilization.getUtilizationLimit());
      }
      for (Map.Entry<String, List<Double>> entry : utilizationByEquipment.entrySet()) {
        if (entry.getValue().size() < iterationIndex + 1) {
          entry.getValue().add(Double.NaN);
        }
      }
      for (Map.Entry<String, List<Boolean>> entry : bottleneckFlags.entrySet()) {
        boolean isBottleneck = record.getBottleneckName() != null && record.getBottleneckName().equals(entry.getKey());
        entry.getValue().add(isBottleneck);
      }
      iterationIndex++;
    }

    return utilizationByEquipment.entrySet().stream()
        .map(entry -> new UtilizationSeries(entry.getKey(), entry.getValue(),
            bottleneckFlags.getOrDefault(entry.getKey(), Collections.emptyList()),
            limits.getOrDefault(entry.getKey(), DEFAULT_UTILIZATION_LIMIT)))
        .collect(Collectors.toList());
  }

  /**
   * Render a compact Markdown timeline showing bottlenecks across iterations.
   */
  public static String formatUtilizationTimeline(List<IterationRecord> iterationHistory) {
    StringBuilder sb = new StringBuilder();
    sb.append("| Iteration | Bottleneck | Utilization | Limit | Feasible |\n");
    sb.append("|---|---|---|---|---|\n");
    int iteration = 1;
    for (IterationRecord record : iterationHistory) {
      String bottleneckName = record.getBottleneckName() == null ? "" : record.getBottleneckName();
      UtilizationRecord bottleneck = record.getUtilizations().stream()
          .filter(u -> u.getEquipmentName().equals(bottleneckName)).findFirst().orElse(null);
      double utilization = bottleneck == null ? Double.NaN : bottleneck.getUtilization();
      double limit = bottleneck == null ? Double.NaN : bottleneck.getUtilizationLimit();
      sb.append("|").append(iteration).append("|").append(bottleneckName).append("|")
          .append(String.format("%.3f", utilization)).append("|")
          .append(String.format("%.3f", limit)).append("|")
          .append(record.isFeasible() ? "yes" : "no").append("|\n");
      iteration++;
    }
    return sb.toString();
  }

  private OptimizationResult toResult(double rate, String unit, int iteration,
      Evaluation evaluation, List<IterationRecord> iterationHistory) {
    return new OptimizationResult(rate, unit, evaluation.decisionVariables(),
        evaluation.bottleneck(), evaluation.bottleneckUtilization(),
        evaluation.utilizationRecords(), evaluation.objectiveValues(),
        evaluation.constraintStatuses(),
        evaluation.utilizationWithinLimits() && evaluation.hardOk(), evaluation.score(), iteration,
        new ArrayList<>(iterationHistory));
  }

  /**
   * Convenience wrapper that derives reasonable bounds from the current feed rate
   * and returns a
   * concise summary (max rate, limiting equipment, utilization margin).
   */
  public OptimizationSummary quickOptimize(ProcessSystem process, StreamInterface feedStream) {
    return quickOptimize(process, feedStream, "kg/hr", Collections.emptyList());
  }

  /**
   * Convenience wrapper that derives reasonable bounds from the current feed rate
   * and returns a
   * concise summary (max rate, limiting equipment, utilization margin).
   */
  public OptimizationSummary quickOptimize(ProcessSystem process, StreamInterface feedStream,
      String rateUnit, List<OptimizationConstraint> constraints) {
    Objects.requireNonNull(process, "ProcessSystem is required");
    Objects.requireNonNull(feedStream, "Feed stream is required");
    double baseRate = Math.max(1.0, feedStream.getFlowRate(rateUnit));
    double lower = 0.0;
    double upper = baseRate * 2.0;
    OptimizationConfig config = new OptimizationConfig(lower, upper).rateUnit(rateUnit)
        .tolerance(baseRate * 0.005).maxIterations(40);
    OptimizationResult result = optimize(process, feedStream, config, Collections.emptyList(), constraints);
    UtilizationRecord bottleneck = result.getUtilizationRecords().stream()
        .filter(record -> result.getBottleneck() != null
            && record.getEquipmentName().equals(result.getBottleneck().getName()))
        .findFirst().orElse(result.getUtilizationRecords().isEmpty() ? null
            : result.getUtilizationRecords().get(0));
    double utilization = bottleneck == null ? 0.0 : bottleneck.getUtilization();
    double utilizationLimit = bottleneck == null ? 0.0 : bottleneck.getUtilizationLimit();
    double margin = utilizationLimit - utilization;
    return new OptimizationSummary(result.getOptimalRate(), result.getRateUnit(),
        result.getBottleneck() == null ? null : result.getBottleneck().getName(), utilization,
        utilizationLimit, margin, result.isFeasible(), result.getDecisionVariables(),
        result.getUtilizationRecords(), result.getConstraintStatuses());
  }

  private Evaluation evaluateProcess(ProcessSystem process, OptimizationConfig config,
      List<OptimizationObjective> objectives, List<OptimizationConstraint> constraints,
      Map<String, Double> decisionVariables) {
    List<UtilizationRecord> utilizations = new ArrayList<>();
    double maxUtilization = -1.0;
    ProcessEquipmentInterface bottleneck = null;
    List<OptimizationConstraint> equipmentConstraints = new ArrayList<>(constraints);

    // Check simulation validity for all equipment
    // Note: We only check basic simulation validity (NaN, negative values)
    // Operating envelope violations (surge, stonewall) are handled as constraints
    // during optimization, not as simulation failures
    boolean simulationValid = true;
    List<String> validationErrors = new ArrayList<>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!unit.isSimulationValid()) {
        simulationValid = false;
        validationErrors.addAll(unit.getSimulationValidationErrors());
      }
    }

    // If simulation is fundamentally invalid, return infeasible evaluation
    // immediately
    if (!simulationValid && config.isRejectInvalidSimulations()) {
      // Create a dummy evaluation with high but finite utilization to signal
      // infeasibility
      // Using a value > 1.0 but not infinity to avoid confusion in reporting
      return new Evaluation(10.0, null, utilizations, new ArrayList<ConstraintStatus>(),
          new HashMap<String, Double>(), decisionVariables, false, false, Double.NEGATIVE_INFINITY);
    }

    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      for (EquipmentConstraintRule rule : config.equipmentConstraintRules) {
        if (rule.matches(unit)) {
          equipmentConstraints.add(rule.toConstraint(unit));
        }
      }
    }

    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      CapacityRule capacityRule = determineCapacityRule(unit, config);
      double capacity = capacityRule.max(unit);
      CapacityRange range = determineCapacityRange(unit, config);
      if (range == null && config.capacityRangeSpreadFraction > 0.0) {
        double spread = config.capacityRangeSpreadFraction;
        range = new CapacityRange(capacity * (1.0 - spread), capacity, capacity * (1.0 + spread));
      }
      if (range != null) {
        capacity = range.atPercentile(config.getCapacityPercentile());
      }
      if (config.capacityUncertaintyFraction > 0.0) {
        capacity = capacity * (1.0 - config.capacityUncertaintyFraction);
      }
      double duty = capacityRule.duty(unit);

      // Validate duty and capacity values to prevent NaN/Infinity utilization
      if (Double.isNaN(duty) || Double.isInfinite(duty)) {
        // Invalid duty - skip this equipment or treat as infeasible
        continue;
      }
      if (Double.isNaN(capacity) || Double.isInfinite(capacity) || capacity <= 1e-12) {
        // Invalid or zero capacity - skip this equipment
        continue;
      }

      double utilization = duty / capacity;

      // Final validation of utilization value
      if (Double.isNaN(utilization) || Double.isInfinite(utilization)) {
        continue;
      }

      double limit = determineUtilizationLimit(unit, config) * (1.0 - config.utilizationMarginFraction);
      utilizations.add(new UtilizationRecord(unit.getName(), duty, capacity, utilization, limit));
      if (utilization > maxUtilization) {
        maxUtilization = utilization;
        bottleneck = unit;
      }
    }

    boolean utilizationOk = utilizations.stream()
        .noneMatch(record -> record.getUtilization() > record.getUtilizationLimit());

    List<ConstraintStatus> constraintStatuses = new ArrayList<>();
    boolean hardOk = true;
    double penalty = 0.0;
    for (OptimizationConstraint constraint : equipmentConstraints) {
      double margin = constraint.margin(process);
      ConstraintStatus status = new ConstraintStatus(constraint.getName(), constraint.getSeverity(),
          margin, constraint.getPenaltyWeight(), constraint.getDescription());
      constraintStatuses.add(status);
      if (status.violated()) {
        if (status.getSeverity() == ConstraintSeverity.HARD) {
          hardOk = false;
        } else {
          penalty += status.getPenaltyWeight() * Math.abs(margin);
        }
      }
    }

    Map<String, Double> objectiveValues = new HashMap<>();
    double score = 0.0;
    for (OptimizationObjective objective : objectives) {
      double value = objective.evaluate(process);
      objectiveValues.put(objective.getName(), value);
      double signedValue = objective.getType() == ObjectiveType.MAXIMIZE ? value : -value;
      score += signedValue * objective.getWeight();
    }
    score -= penalty;

    return new Evaluation(maxUtilization, bottleneck, utilizations, constraintStatuses,
        objectiveValues, decisionVariables, utilizationOk, hardOk, score);
  }

  private OptimizationResult binaryFeasibilitySearch(ProcessSystem process,
      StreamInterface feedStream, OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints, List<IterationRecord> iterationHistory) {
    Map<Long, Evaluation> cache = new HashMap<>();
    double low = config.lowerBound;
    double high = config.upperBound;
    OptimizationResult bestResult = null;
    int iteration = 0;

    while (iteration < config.maxIterations && Math.abs(high - low) > config.tolerance) {
      double candidate = 0.5 * (low + high);
      Evaluation evaluation = evaluateCandidate(process, feedStream, config, objectives, constraints, candidate, cache);
      boolean feasible = evaluation.utilizationWithinLimits() && evaluation.hardOk();
      recordIteration(iterationHistory, candidate, config.rateUnit, evaluation, feasible);
      if (feasible) {
        bestResult = toResult(candidate, config.rateUnit, iteration, evaluation, iterationHistory);
        low = candidate;
      } else {
        high = candidate;
      }
      iteration++;
    }

    if (bestResult == null) {
      Evaluation evaluation = evaluateCandidate(process, feedStream, config, objectives, constraints, low, cache);
      recordIteration(iterationHistory, low, config.rateUnit, evaluation,
          evaluation.utilizationWithinLimits() && evaluation.hardOk());
      bestResult = toResult(low, config.rateUnit, iteration, evaluation, iterationHistory);
    }
    return bestResult;
  }

  private OptimizationResult binaryFeasibilitySearch(ProcessSystem process,
      List<ManipulatedVariable> variables, OptimizationConfig config,
      List<OptimizationObjective> objectives, List<OptimizationConstraint> constraints,
      List<IterationRecord> iterationHistory) {
    Map<String, Evaluation> cache = new HashMap<>();
    ManipulatedVariable variable = variables.get(0);
    double low = variable.getLowerBound();
    double high = variable.getUpperBound();
    OptimizationResult bestResult = null;
    int iteration = 0;
    String unit = variable.getUnit() != null ? variable.getUnit() : config.rateUnit;

    while (iteration < config.maxIterations && Math.abs(high - low) > config.tolerance) {
      double candidateValue = 0.5 * (low + high);
      Evaluation evaluation = evaluateCandidate(process, variables, config, objectives, constraints,
          new double[] { candidateValue }, cache);
      boolean feasible = evaluation.utilizationWithinLimits() && evaluation.hardOk();
      recordIteration(iterationHistory, candidateValue, unit, evaluation, feasible);
      if (feasible) {
        bestResult = toResult(candidateValue, unit, iteration, evaluation, iterationHistory);
        low = candidateValue;
      } else {
        high = candidateValue;
      }
      iteration++;
    }

    if (bestResult == null) {
      Evaluation evaluation = evaluateCandidate(process, variables, config, objectives, constraints,
          new double[] { low }, cache);
      recordIteration(iterationHistory, low, unit, evaluation,
          evaluation.utilizationWithinLimits() && evaluation.hardOk());
      bestResult = toResult(low, unit, iteration, evaluation, iterationHistory);
    }
    return bestResult;
  }

  private OptimizationResult goldenSectionSearch(ProcessSystem process, StreamInterface feedStream,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints, List<IterationRecord> iterationHistory) {
    Map<Long, Evaluation> cache = new HashMap<>();
    double a = config.lowerBound;
    double b = config.upperBound;
    double phi = 0.5 * (Math.sqrt(5) - 1); // ~0.618

    double c = b - phi * (b - a);
    double d = a + phi * (b - a);

    Evaluation evalC = evaluateCandidate(process, feedStream, config, objectives, constraints, c, cache);
    recordIteration(iterationHistory, c, config.rateUnit, evalC,
        evalC.utilizationWithinLimits() && evalC.hardOk());
    Evaluation evalD = evaluateCandidate(process, feedStream, config, objectives, constraints, d, cache);
    recordIteration(iterationHistory, d, config.rateUnit, evalD,
        evalD.utilizationWithinLimits() && evalD.hardOk());

    Evaluation bestEval = null;
    double bestRate = a;
    int iteration = 0;

    while (iteration < config.maxIterations && Math.abs(b - a) > config.tolerance) {
      double scoreC = feasibilityScore(evalC);
      double scoreD = feasibilityScore(evalD);

      if (scoreC < scoreD) {
        a = c;
        c = d;
        evalC = evalD;
        d = a + phi * (b - a);
        evalD = evaluateCandidate(process, feedStream, config, objectives, constraints, d, cache);
        recordIteration(iterationHistory, d, config.rateUnit, evalD,
            evalD.utilizationWithinLimits() && evalD.hardOk());
      } else {
        b = d;
        d = c;
        evalD = evalC;
        c = b - phi * (b - a);
        evalC = evaluateCandidate(process, feedStream, config, objectives, constraints, c, cache);
        recordIteration(iterationHistory, c, config.rateUnit, evalC,
            evalC.utilizationWithinLimits() && evalC.hardOk());
      }

      if (evalC.utilizationWithinLimits() && evalC.hardOk()
          && (bestEval == null || evalC.score() > bestEval.score())) {
        bestEval = evalC;
        bestRate = c;
      }
      if (evalD.utilizationWithinLimits() && evalD.hardOk()
          && (bestEval == null || evalD.score() > bestEval.score())) {
        bestEval = evalD;
        bestRate = d;
      }
      iteration++;
    }

    if (bestEval == null) {
      bestEval = evalC;
      bestRate = c;
    }

    return toResult(bestRate, config.rateUnit, iteration, bestEval, iterationHistory);
  }

  private OptimizationResult goldenSectionSearch(ProcessSystem process,
      List<ManipulatedVariable> variables, OptimizationConfig config,
      List<OptimizationObjective> objectives, List<OptimizationConstraint> constraints,
      List<IterationRecord> iterationHistory) {
    double phi = (1 + Math.sqrt(5)) / 2;
    Map<String, Evaluation> cache = new HashMap<>();
    ManipulatedVariable variable = variables.get(0);
    double low = variable.getLowerBound();
    double high = variable.getUpperBound();
    String unit = variable.getUnit() != null ? variable.getUnit() : config.rateUnit;

    double c = high - (high - low) / phi;
    double d = low + (high - low) / phi;

    Evaluation evalC = evaluateCandidate(process, variables, config, objectives, constraints,
        new double[] { c }, cache);
    Evaluation evalD = evaluateCandidate(process, variables, config, objectives, constraints,
        new double[] { d }, cache);
    recordIteration(iterationHistory, c, unit, evalC,
        evalC.utilizationWithinLimits() && evalC.hardOk());
    recordIteration(iterationHistory, d, unit, evalD,
        evalD.utilizationWithinLimits() && evalD.hardOk());

    int iteration = 0;
    while (iteration < config.maxIterations && Math.abs(high - low) > config.tolerance) {
      if (feasibilityScore(evalC) > feasibilityScore(evalD)) {
        high = d;
        d = c;
        evalD = evalC;
        c = high - (high - low) / phi;
        evalC = evaluateCandidate(process, variables, config, objectives, constraints,
            new double[] { c }, cache);
        recordIteration(iterationHistory, c, unit, evalC,
            evalC.utilizationWithinLimits() && evalC.hardOk());
      } else {
        low = c;
        c = d;
        evalC = evalD;
        d = low + (high - low) / phi;
        evalD = evaluateCandidate(process, variables, config, objectives, constraints,
            new double[] { d }, cache);
        recordIteration(iterationHistory, d, unit, evalD,
            evalD.utilizationWithinLimits() && evalD.hardOk());
      }
      iteration++;
    }

    Evaluation bestEval = feasibilityScore(evalC) > feasibilityScore(evalD) ? evalC : evalD;
    double bestRate = feasibilityScore(evalC) > feasibilityScore(evalD) ? c : d;
    return toResult(bestRate, unit, iteration, bestEval, iterationHistory);
  }

  private OptimizationResult nelderMeadSearch(ProcessSystem process, StreamInterface feedStream,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints, List<IterationRecord> iterationHistory) {
    Map<Long, Evaluation> cache = new HashMap<>();
    double[] simplex = new double[] { config.lowerBound, config.upperBound };
    Evaluation eval0 = evaluateCandidate(process, feedStream, config, objectives, constraints, simplex[0], cache);
    Evaluation eval1 = evaluateCandidate(process, feedStream, config, objectives, constraints, simplex[1], cache);
    recordIteration(iterationHistory, simplex[0], config.rateUnit, eval0,
        eval0.utilizationWithinLimits() && eval0.hardOk());
    recordIteration(iterationHistory, simplex[1], config.rateUnit, eval1,
        eval1.utilizationWithinLimits() && eval1.hardOk());

    int iteration = 0;
    while (iteration < config.maxIterations
        && Math.abs(simplex[1] - simplex[0]) > config.tolerance) {
      int bestIndex = feasibilityScore(eval0) >= feasibilityScore(eval1) ? 0 : 1;
      int worstIndex = bestIndex == 0 ? 1 : 0;
      double centroid = simplex[bestIndex];
      double reflected = centroid + (centroid - simplex[worstIndex]);
      double clamped = Math.max(config.lowerBound, Math.min(config.upperBound, reflected));
      Evaluation reflectedEval = evaluateCandidate(process, feedStream, config, objectives, constraints, clamped,
          cache);
      recordIteration(iterationHistory, clamped, config.rateUnit, reflectedEval,
          reflectedEval.utilizationWithinLimits() && reflectedEval.hardOk());

      if (feasibilityScore(reflectedEval) > feasibilityScore(eval0)
          && feasibilityScore(reflectedEval) > feasibilityScore(eval1)) {
        simplex[worstIndex] = clamped;
        if (worstIndex == 0) {
          eval0 = reflectedEval;
        } else {
          eval1 = reflectedEval;
        }
      } else {
        double contracted = 0.5 * (simplex[worstIndex] + centroid);
        Evaluation contractedEval = evaluateCandidate(process, feedStream, config, objectives,
            constraints, contracted, cache);
        recordIteration(iterationHistory, contracted, config.rateUnit, contractedEval,
            contractedEval.utilizationWithinLimits() && contractedEval.hardOk());
        simplex[worstIndex] = contracted;
        if (worstIndex == 0) {
          eval0 = contractedEval;
        } else {
          eval1 = contractedEval;
        }
      }
      iteration++;
    }

    Evaluation bestEval = feasibilityScore(eval0) >= feasibilityScore(eval1) ? eval0 : eval1;
    double bestRate = feasibilityScore(eval0) >= feasibilityScore(eval1) ? simplex[0] : simplex[1];
    return toResult(bestRate, config.rateUnit, iteration, bestEval, iterationHistory);
  }

  private OptimizationResult nelderMeadSearch(ProcessSystem process,
      List<ManipulatedVariable> variables, OptimizationConfig config,
      List<OptimizationObjective> objectives, List<OptimizationConstraint> constraints,
      List<IterationRecord> iterationHistory) {
    int dim = variables.size();
    int simplexSize = dim + 1;
    double[][] simplex = new double[simplexSize][dim];
    Evaluation[] evaluations = new Evaluation[simplexSize];
    Map<String, Evaluation> cache = new HashMap<>();
    String unit = variables.get(0).getUnit() != null ? variables.get(0).getUnit() : config.rateUnit;

    for (int i = 0; i < simplexSize; i++) {
      for (int j = 0; j < dim; j++) {
        ManipulatedVariable var = variables.get(j);
        double center = 0.5 * (var.getLowerBound() + var.getUpperBound());
        double span = Math.max(config.tolerance, 0.1 * (var.getUpperBound() - var.getLowerBound()));
        simplex[i][j] = Math.max(var.getLowerBound(),
            Math.min(var.getUpperBound(), center + (i == j + 1 ? span : 0.0)));
      }
      evaluations[i] = evaluateCandidate(process, variables, config, objectives, constraints, simplex[i], cache);
      recordIteration(iterationHistory, simplex[i][0], unit, evaluations[i],
          evaluations[i].utilizationWithinLimits() && evaluations[i].hardOk());
    }

    int iteration = 0;
    while (iteration < config.maxIterations) {
      sortSimplexByScore(simplex, evaluations);
      double[] centroid = computeCentroid(simplex, simplexSize - 1);
      double[] worst = simplex[simplexSize - 1];

      double[] reflected = reflect(centroid, worst, 1.0);
      Evaluation reflectedEval = evaluateCandidate(process, variables, config, objectives, constraints, reflected,
          cache);

      if (feasibilityScore(reflectedEval) > feasibilityScore(evaluations[0])) {
        double[] expanded = reflect(centroid, worst, 2.0);
        Evaluation expandedEval = evaluateCandidate(process, variables, config, objectives, constraints, expanded,
            cache);
        if (feasibilityScore(expandedEval) > feasibilityScore(reflectedEval)) {
          simplex[simplexSize - 1] = expanded;
          evaluations[simplexSize - 1] = expandedEval;
        } else {
          simplex[simplexSize - 1] = reflected;
          evaluations[simplexSize - 1] = reflectedEval;
        }
      } else if (feasibilityScore(reflectedEval) > feasibilityScore(evaluations[simplexSize - 2])) {
        simplex[simplexSize - 1] = reflected;
        evaluations[simplexSize - 1] = reflectedEval;
      } else {
        double[] contracted = contract(centroid, worst, 0.5);
        Evaluation contractedEval = evaluateCandidate(process, variables, config, objectives,
            constraints, contracted, cache);
        if (feasibilityScore(contractedEval) > feasibilityScore(evaluations[simplexSize - 1])) {
          simplex[simplexSize - 1] = contracted;
          evaluations[simplexSize - 1] = contractedEval;
        } else {
          shrink(simplex, variables);
          for (int i = 1; i < simplexSize; i++) {
            evaluations[i] = evaluateCandidate(process, variables, config, objectives, constraints,
                simplex[i], cache);
          }
        }
      }

      recordIteration(iterationHistory, simplex[0][0], unit, evaluations[0],
          evaluations[0].utilizationWithinLimits() && evaluations[0].hardOk());
      iteration++;
    }

    sortSimplexByScore(simplex, evaluations);
    return toResult(simplex[0][0], unit, iteration, evaluations[0], iterationHistory);
  }

  private OptimizationResult particleSwarmSearch(ProcessSystem process, StreamInterface feedStream,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints, List<IterationRecord> iterationHistory) {
    Map<Long, Evaluation> cache = new HashMap<>();
    Random random = new Random(0);
    int swarmSize = Math.max(2, config.getSwarmSize());
    double[] positions = new double[swarmSize];
    double[] velocities = new double[swarmSize];
    double[] bestPersonalScores = new double[swarmSize];
    double[] bestPersonalPositions = new double[swarmSize];
    Evaluation[] evaluations = new Evaluation[swarmSize];

    double globalBestScore = Double.NEGATIVE_INFINITY;
    double globalBestPosition = config.lowerBound;
    Evaluation globalBestEvaluation = null;

    for (int i = 0; i < swarmSize; i++) {
      double initPos = config.lowerBound
          + (config.upperBound - config.lowerBound) * ((double) i / (double) swarmSize);
      positions[i] = initPos;
      velocities[i] = 0.0;
      evaluations[i] = evaluateCandidate(process, feedStream, config, objectives, constraints, initPos, cache);
      recordIteration(iterationHistory, initPos, config.rateUnit, evaluations[i],
          evaluations[i].utilizationWithinLimits() && evaluations[i].hardOk());
      bestPersonalScores[i] = feasibilityScore(evaluations[i]);
      bestPersonalPositions[i] = initPos;
      if (bestPersonalScores[i] > globalBestScore) {
        globalBestScore = bestPersonalScores[i];
        globalBestPosition = initPos;
        globalBestEvaluation = evaluations[i];
      }
    }

    int iteration = 0;
    while (iteration < config.maxIterations) {
      for (int i = 0; i < swarmSize; i++) {
        double r1 = random.nextDouble();
        double r2 = random.nextDouble();
        velocities[i] = config.getInertiaWeight() * velocities[i]
            + config.getCognitiveWeight() * r1 * (bestPersonalPositions[i] - positions[i])
            + config.getSocialWeight() * r2 * (globalBestPosition - positions[i]);

        positions[i] = Math.max(config.lowerBound, Math.min(config.upperBound, positions[i] + velocities[i]));
        evaluations[i] = evaluateCandidate(process, feedStream, config, objectives, constraints,
            positions[i], cache);
        recordIteration(iterationHistory, positions[i], config.rateUnit, evaluations[i],
            evaluations[i].utilizationWithinLimits() && evaluations[i].hardOk());

        double score = feasibilityScore(evaluations[i]);
        if (score > bestPersonalScores[i]) {
          bestPersonalScores[i] = score;
          bestPersonalPositions[i] = positions[i];
        }
        if (score > globalBestScore) {
          globalBestScore = score;
          globalBestPosition = positions[i];
          globalBestEvaluation = evaluations[i];
        }
      }
      if (Math.abs(globalBestScore) < 1e-12) {
        break;
      }
      iteration++;
    }

    if (globalBestEvaluation == null) {
      globalBestEvaluation = evaluateCandidate(process, feedStream, config, objectives, constraints,
          config.lowerBound, cache);
      globalBestPosition = config.lowerBound;
    }

    return toResult(globalBestPosition, config.rateUnit, iteration, globalBestEvaluation,
        iterationHistory);
  }

  private OptimizationResult particleSwarmSearch(ProcessSystem process,
      List<ManipulatedVariable> variables, OptimizationConfig config,
      List<OptimizationObjective> objectives, List<OptimizationConstraint> constraints,
      List<IterationRecord> iterationHistory) {
    Map<String, Evaluation> cache = new HashMap<>();
    Random random = new Random(0);
    int swarmSize = Math.max(2, config.getSwarmSize());
    int dim = variables.size();
    String unit = variables.get(0).getUnit() != null ? variables.get(0).getUnit() : config.rateUnit;

    double[][] positions = new double[swarmSize][dim];
    double[][] velocities = new double[swarmSize][dim];
    double[] bestPersonalScores = new double[swarmSize];
    double[][] bestPersonalPositions = new double[swarmSize][dim];
    Evaluation[] evaluations = new Evaluation[swarmSize];

    double globalBestScore = Double.NEGATIVE_INFINITY;
    double[] globalBestPosition = new double[dim];
    Evaluation globalBestEvaluation = null;

    for (int i = 0; i < swarmSize; i++) {
      for (int j = 0; j < dim; j++) {
        ManipulatedVariable var = variables.get(j);
        double init = var.getLowerBound() + (var.getUpperBound() - var.getLowerBound()) * random.nextDouble();
        positions[i][j] = init;
        velocities[i][j] = 0.0;
      }
      evaluations[i] = evaluateCandidate(process, variables, config, objectives, constraints,
          positions[i], cache);
      recordIteration(iterationHistory, positions[i][0], unit, evaluations[i],
          evaluations[i].utilizationWithinLimits() && evaluations[i].hardOk());
      bestPersonalScores[i] = feasibilityScore(evaluations[i]);
      bestPersonalPositions[i] = positions[i].clone();
      if (bestPersonalScores[i] > globalBestScore) {
        globalBestScore = bestPersonalScores[i];
        globalBestPosition = positions[i].clone();
        globalBestEvaluation = evaluations[i];
      }
    }

    int iteration = 0;
    while (iteration < config.maxIterations) {
      for (int i = 0; i < swarmSize; i++) {
        for (int j = 0; j < dim; j++) {
          double r1 = random.nextDouble();
          double r2 = random.nextDouble();
          velocities[i][j] = config.getInertiaWeight() * velocities[i][j]
              + config.getCognitiveWeight() * r1 * (bestPersonalPositions[i][j] - positions[i][j])
              + config.getSocialWeight() * r2 * (globalBestPosition[j] - positions[i][j]);
          ManipulatedVariable var = variables.get(j);
          positions[i][j] = Math.max(var.getLowerBound(),
              Math.min(var.getUpperBound(), positions[i][j] + velocities[i][j]));
        }
        evaluations[i] = evaluateCandidate(process, variables, config, objectives, constraints,
            positions[i], cache);
        recordIteration(iterationHistory, positions[i][0], unit, evaluations[i],
            evaluations[i].utilizationWithinLimits() && evaluations[i].hardOk());
        double score = feasibilityScore(evaluations[i]);
        if (score > bestPersonalScores[i]) {
          bestPersonalScores[i] = score;
          bestPersonalPositions[i] = positions[i].clone();
        }
        if (score > globalBestScore) {
          globalBestScore = score;
          globalBestPosition = positions[i].clone();
          globalBestEvaluation = evaluations[i];
        }
      }
      iteration++;
    }

    if (globalBestEvaluation == null) {
      globalBestEvaluation = evaluateCandidate(process, variables, config, objectives, constraints,
          globalBestPosition, cache);
    }

    return toResult(globalBestPosition[0], unit, iteration, globalBestEvaluation, iterationHistory);
  }

  /**
   * Gradient descent search using finite-difference gradients and Armijo
   * backtracking line search.
   * This is effective for smooth multi-variable problems with 5-20+ variables.
   */
  private OptimizationResult gradientDescentSearch(ProcessSystem process,
      StreamInterface feedStream, OptimizationConfig config,
      List<OptimizationObjective> objectives, List<OptimizationConstraint> constraints,
      List<IterationRecord> iterationHistory) {
    // Convert single-variable to multi-variable form
    ManipulatedVariable flowVar = new ManipulatedVariable(feedStream.getName(), config.lowerBound,
        config.upperBound, config.rateUnit,
        (proc, val) -> feedStream.setFlowRate(val, config.rateUnit));
    List<ManipulatedVariable> variables = Collections.singletonList(flowVar);
    return gradientDescentSearch(process, variables, config, objectives, constraints,
        iterationHistory);
  }

  /**
   * Gradient descent search for multi-variable optimization using
   * finite-difference gradients.
   *
   * <p>
   * Uses Armijo backtracking line search to find a suitable step size. Gradients
   * are approximated
   * via central differences for better accuracy. The search direction is the
   * negative gradient
   * (steepest ascent since we maximize feasibility score).
   * </p>
   */
  private OptimizationResult gradientDescentSearch(ProcessSystem process,
      List<ManipulatedVariable> variables, OptimizationConfig config,
      List<OptimizationObjective> objectives, List<OptimizationConstraint> constraints,
      List<IterationRecord> iterationHistory) {
    Map<String, Evaluation> cache = new HashMap<>();
    int dim = variables.size();
    String unit = variables.get(0).getUnit() != null ? variables.get(0).getUnit() : config.rateUnit;

    // Initialize at center of bounds
    double[] position = new double[dim];
    double[] scales = new double[dim]; // Scaling for each dimension
    for (int j = 0; j < dim; j++) {
      ManipulatedVariable var = variables.get(j);
      position[j] = 0.5 * (var.getLowerBound() + var.getUpperBound());
      scales[j] = var.getUpperBound() - var.getLowerBound();
    }

    Evaluation currentEval = evaluateCandidate(process, variables, config, objectives, constraints, position, cache);
    recordIteration(iterationHistory, position[0], unit, currentEval,
        currentEval.utilizationWithinLimits() && currentEval.hardOk());

    double bestScore = feasibilityScore(currentEval);
    double[] bestPosition = position.clone();
    Evaluation bestEval = currentEval;

    // Gradient descent parameters
    double epsilon = Math.max(config.getTolerance() * 0.001, 1e-8); // Finite difference step
    double armijoC = 1e-4; // Armijo condition parameter
    double initialStepSize = 0.1; // Initial step size (fraction of scale)
    double minStepSize = 1e-10; // Minimum step size before termination
    double gradientTolerance = 1e-10; // Minimum gradient norm to continue

    int iteration = 0;
    while (iteration < config.getMaxIterations()) {
      // Compute gradient via central differences
      double[] gradient = new double[dim];
      for (int j = 0; j < dim; j++) {
        ManipulatedVariable var = variables.get(j);
        double h = epsilon * scales[j];

        double[] posPlus = position.clone();
        double[] posMinus = position.clone();
        posPlus[j] = Math.min(var.getUpperBound(), position[j] + h);
        posMinus[j] = Math.max(var.getLowerBound(), position[j] - h);

        Evaluation evalPlus = evaluateCandidate(process, variables, config, objectives, constraints, posPlus, cache);
        Evaluation evalMinus = evaluateCandidate(process, variables, config, objectives,
            constraints, posMinus, cache);

        double actualH = posPlus[j] - posMinus[j];
        if (actualH > 1e-12) {
          gradient[j] = (feasibilityScore(evalPlus) - feasibilityScore(evalMinus)) / actualH;
        } else {
          gradient[j] = 0.0;
        }
      }

      // Normalize gradient and compute search direction (steepest ascent)
      double gradNorm = 0.0;
      for (int j = 0; j < dim; j++) {
        gradNorm += gradient[j] * gradient[j] * scales[j] * scales[j];
      }
      gradNorm = Math.sqrt(gradNorm);

      if (gradNorm < gradientTolerance) {
        // Gradient is too small, we're at a stationary point
        iteration++; // Count the gradient computation as an iteration
        break;
      }

      // Armijo backtracking line search
      double stepSize = initialStepSize;
      double currentScore = feasibilityScore(currentEval);
      double[] newPosition = new double[dim];
      Evaluation newEval = null;
      boolean stepAccepted = false;

      while (stepSize > minStepSize) {
        // Compute new position
        for (int j = 0; j < dim; j++) {
          ManipulatedVariable var = variables.get(j);
          double step = stepSize * scales[j] * gradient[j] / gradNorm;
          newPosition[j] = Math.max(var.getLowerBound(),
              Math.min(var.getUpperBound(), position[j] + step));
        }

        newEval = evaluateCandidate(process, variables, config, objectives, constraints,
            newPosition, cache);
        recordIteration(iterationHistory, newPosition[0], unit, newEval,
            newEval.utilizationWithinLimits() && newEval.hardOk());

        double newScore = feasibilityScore(newEval);

        // Armijo condition: sufficient increase
        double expectedIncrease = armijoC * stepSize * gradNorm;
        if (newScore >= currentScore + expectedIncrease) {
          stepAccepted = true;
          break;
        }

        // Reduce step size
        stepSize *= 0.5;
      }

      if (!stepAccepted) {
        // Line search failed, try smaller initial step next iteration
        initialStepSize *= 0.5;
        if (initialStepSize < minStepSize) {
          iteration++; // Count this as an iteration
          break; // Can't make progress
        }
        iteration++;
        continue;
      }

      // Track change before updating position
      double maxChange = 0.0;
      for (int j = 0; j < dim; j++) {
        maxChange = Math.max(maxChange, Math.abs(newPosition[j] - position[j]) / scales[j]);
      }

      // Update position
      position = newPosition.clone();
      currentEval = newEval;

      // Track best solution
      double score = feasibilityScore(currentEval);
      if (score > bestScore) {
        bestScore = score;
        bestPosition = position.clone();
        bestEval = currentEval;
      }

      iteration++;

      // Check convergence
      if (maxChange < config.getTolerance() / scales[0]) {
        break;
      }
    }

    return toResult(bestPosition[0], unit, iteration, bestEval, iterationHistory);
  }

  private void sortSimplexByScore(double[][] simplex, Evaluation[] evaluations) {
    java.util.List<Integer> order = new ArrayList<>();
    for (int i = 0; i < evaluations.length; i++) {
      order.add(i);
    }
    order.sort((a, b) -> Double.compare(feasibilityScore(evaluations[b]),
        feasibilityScore(evaluations[a])));
    double[][] newSimplex = new double[simplex.length][simplex[0].length];
    Evaluation[] newEvals = new Evaluation[evaluations.length];
    for (int i = 0; i < order.size(); i++) {
      newSimplex[i] = simplex[order.get(i)].clone();
      newEvals[i] = evaluations[order.get(i)];
    }
    System.arraycopy(newSimplex, 0, simplex, 0, simplex.length);
    System.arraycopy(newEvals, 0, evaluations, 0, evaluations.length);
  }

  private double[] computeCentroid(double[][] simplex, int count) {
    int dim = simplex[0].length;
    double[] centroid = new double[dim];
    for (int i = 0; i < count; i++) {
      for (int j = 0; j < dim; j++) {
        centroid[j] += simplex[i][j];
      }
    }
    for (int j = 0; j < dim; j++) {
      centroid[j] /= count;
    }
    return centroid;
  }

  private double[] reflect(double[] centroid, double[] point, double factor) {
    double[] result = new double[centroid.length];
    for (int i = 0; i < centroid.length; i++) {
      result[i] = centroid[i] + factor * (centroid[i] - point[i]);
    }
    return result;
  }

  private double[] contract(double[] centroid, double[] point, double factor) {
    double[] result = new double[centroid.length];
    for (int i = 0; i < centroid.length; i++) {
      result[i] = centroid[i] + factor * (point[i] - centroid[i]);
    }
    return result;
  }

  private void shrink(double[][] simplex, List<ManipulatedVariable> variables) {
    for (int i = 1; i < simplex.length; i++) {
      for (int j = 0; j < simplex[i].length; j++) {
        ManipulatedVariable var = variables.get(j);
        simplex[i][j] = Math.max(var.getLowerBound(),
            Math.min(var.getUpperBound(), 0.5 * (simplex[0][j] + simplex[i][j])));
      }
    }
  }

  private Evaluation evaluateCandidate(ProcessSystem process, StreamInterface feedStream,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints, double candidateRate, Map<Long, Evaluation> cache) {
    if (config.enableCaching) {
      long cacheKey = Math.round(candidateRate / Math.max(config.tolerance, 1e-9));
      Evaluation cached = cache.get(cacheKey);
      if (cached != null) {
        return cached;
      }
      Evaluation evaluation = evaluateCandidateInternal(process, feedStream, config, objectives,
          constraints, candidateRate);
      cache.put(cacheKey, evaluation);
      return evaluation;
    }
    return evaluateCandidateInternal(process, feedStream, config, objectives, constraints,
        candidateRate);
  }

  private Evaluation evaluateCandidate(ProcessSystem process, List<ManipulatedVariable> variables,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints, double[] candidate, Map<String, Evaluation> cache) {
    if (config.enableCaching) {
      String cacheKey = buildVectorCacheKey(candidate, config);
      Evaluation cached = cache.get(cacheKey);
      if (cached != null) {
        return cached;
      }
      Evaluation evaluation = evaluateCandidateInternal(process, variables, config, objectives, constraints, candidate);
      cache.put(cacheKey, evaluation);
      return evaluation;
    }
    return evaluateCandidateInternal(process, variables, config, objectives, constraints,
        candidate);
  }

  private Evaluation evaluateCandidateInternal(ProcessSystem process, StreamInterface feedStream,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints, double candidateRate) {
    feedStream.setFlowRate(candidateRate, config.rateUnit);
    process.run();
    Map<String, Double> decisions = new HashMap<>();
    decisions.put(feedStream.getName(), candidateRate);
    return evaluateProcess(process, config, objectives, constraints, decisions);
  }

  private Evaluation evaluateCandidateInternal(ProcessSystem process,
      List<ManipulatedVariable> variables, OptimizationConfig config,
      List<OptimizationObjective> objectives, List<OptimizationConstraint> constraints,
      double[] candidate) {
    Map<String, Double> decisions = new HashMap<>();
    for (int i = 0; i < variables.size(); i++) {
      ManipulatedVariable variable = variables.get(i);
      double bounded = Math.max(variable.getLowerBound(), Math.min(variable.getUpperBound(), candidate[i]));
      variable.apply(process, bounded);
      decisions.put(variable.getName(), bounded);
    }
    process.run();
    return evaluateProcess(process, config, objectives, constraints, decisions);
  }

  private String buildVectorCacheKey(double[] candidate, OptimizationConfig config) {
    double tol = Math.max(config.tolerance, 1e-9);
    StringBuilder key = new StringBuilder();
    for (double value : candidate) {
      long rounded = Math.round(value / tol);
      key.append(rounded).append("|");
    }
    return key.toString();
  }

  private void recordIteration(List<IterationRecord> iterationHistory, double candidate,
      String rateUnit, Evaluation evaluation, boolean feasible) {
    String bottleneckName = evaluation.bottleneck() != null ? evaluation.bottleneck().getName() : "unknown";
    iterationHistory.add(new IterationRecord(candidate, rateUnit, evaluation.decisionVariables(),
        bottleneckName, evaluation.bottleneckUtilization(), evaluation.utilizationWithinLimits(),
        evaluation.hardOk(), feasible, evaluation.score(), evaluation.utilizationRecords()));
  }

  private double feasibilityScore(Evaluation evaluation) {
    if (evaluation.utilizationWithinLimits() && evaluation.hardOk()) {
      return evaluation.score();
    }
    // Penalize infeasible points heavily to ensure optimizer prefers feasible
    // solutions.
    // Use a large negative penalty proportional to how far we exceed limits.
    double penalty = 0.0;
    for (ConstraintStatus status : evaluation.constraintStatuses()) {
      if (status.getSeverity() == ConstraintSeverity.HARD && status.violated()) {
        // Strong penalty for constraint violations
        penalty -= 1000.0 * (1.0 + Math.abs(status.getMargin()));
      }
    }
    if (!evaluation.utilizationWithinLimits()) {
      // Strong penalty for exceeding utilization limits
      // Penalty increases quadratically with how much we exceed 100%
      double overUtil = evaluation.bottleneckUtilization() - 1.0;
      if (overUtil > 0) {
        penalty -= 1000.0 * (1.0 + overUtil * overUtil);
      }
    }
    // Return a score that is always much worse than any feasible solution
    return Math.min(-1000.0, evaluation.score() + penalty);
  }

  private double determineUtilizationLimit(ProcessEquipmentInterface unit,
      OptimizationConfig config) {
    return Optional.ofNullable(config.utilizationLimitsByName.get(unit.getName()))
        .orElseGet(() -> config.utilizationLimitsByType.entrySet().stream()
            .filter(entry -> entry.getKey().isAssignableFrom(unit.getClass()))
            .map(Map.Entry::getValue).findFirst().orElse(config.defaultUtilizationLimit));
  }

  private CapacityRule determineCapacityRule(ProcessEquipmentInterface unit,
      OptimizationConfig config) {
    CapacityRule byName = config.capacityRulesByName.get(unit.getName());
    if (byName != null) {
      return byName;
    }
    for (Map.Entry<Class<?>, CapacityRule> entry : config.capacityRulesByType.entrySet()) {
      if (entry.getKey().isAssignableFrom(unit.getClass())) {
        return entry.getValue();
      }
    }

    // PRIORITY: If equipment implements CapacityConstrainedEquipment and has
    // capacity analysis
    // enabled, use its getMaxUtilization() method instead of hardcoded rules.
    // This ensures the optimizer uses the same capacity calculations as the
    // equipment itself.
    if (unit instanceof neqsim.process.equipment.capacity.CapacityConstrainedEquipment) {
      neqsim.process.equipment.capacity.CapacityConstrainedEquipment constrained = (neqsim.process.equipment.capacity.CapacityConstrainedEquipment) unit;
      if (constrained.isCapacityAnalysisEnabled()) {
        // Use getMaxUtilization() which returns the actual capacity utilization (0-1
        // scale)
        // from the equipment's capacity constraint framework
        return new CapacityRule(
            equipment -> ((neqsim.process.equipment.capacity.CapacityConstrainedEquipment) equipment)
                .getMaxUtilization(),
            equipment -> 1.0); // Limit is 1.0 (100% utilization)
      }
    }

    if (unit instanceof DistillationColumn) {
      DistillationColumn column = (DistillationColumn) unit;
      return new CapacityRule(equipment -> column.getFsFactor(),
          equipment -> config.getColumnFsFactorLimit());
    }
    if (unit instanceof neqsim.process.equipment.separator.Separator) {
      neqsim.process.equipment.separator.Separator sep = (neqsim.process.equipment.separator.Separator) unit;
      // Use liquid level fraction as separator capacity metric
      // This represents the operational filling of the separator vessel
      // Values > 1.0 indicate liquid carryover risk
      return new CapacityRule(equipment -> sep.getLiquidLevel(), equipment -> 1.0);
    }
    if (unit instanceof neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2) {
      neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2 exchanger = (neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2) unit;
      return new CapacityRule(equipment -> Math.abs(exchanger.energyDiff()),
          equipment -> Math.max(1.0, exchanger.getCapacityMax()));
    }
    if (unit instanceof neqsim.process.equipment.heatexchanger.Heater) {
      neqsim.process.equipment.heatexchanger.Heater heater = (neqsim.process.equipment.heatexchanger.Heater) unit;
      // Use absolute duty for heaters/coolers (both heating and cooling)
      return new CapacityRule(equipment -> Math.abs(heater.getDuty()), equipment -> {
        double maxDuty = heater.getMaxDesignDuty();
        // If max design duty is not set, return a large value (no constraint)
        return maxDuty > 0 ? maxDuty : Double.MAX_VALUE;
      });
    }
    if (unit instanceof ThrottlingValve) {
      ThrottlingValve valve = (ThrottlingValve) unit;
      // Use valve opening percentage as capacity measure (0-100%)
      // A valve operating at high opening (>80%) has poor control authority
      // and may indicate it is undersized for the current flow/Cv
      // Only track if Cv/Kv has been explicitly set AND max opening is constrained
      double maxOpening = valve.getMaximumValveOpening();
      if (valve.isValveKvSet() && maxOpening < 100.0) {
        return new CapacityRule(equipment -> valve.getPercentValveOpening(), // Current opening %
            equipment -> maxOpening); // Max allowed opening
      }
      // For valves without explicit Cv or unconstrained opening, don't track
      // utilization
      return new CapacityRule(equipment -> 0.0, equipment -> Double.MAX_VALUE);
    }
    if (unit instanceof PipeBeggsAndBrills) {
      PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) unit;
      // Use superficial velocity as capacity measure for pipelines
      // Typical limits: gas ~20-25 m/s (erosion), liquid ~3-5 m/s
      // Multiphase: often limited to 15-20 m/s to avoid erosion/corrosion
      return new CapacityRule(equipment -> pipe.getOutletSuperficialVelocity(), equipment -> {
        // Get max design velocity from mechanical design if set
        double maxVel = pipe.getMechanicalDesign().getMaxDesignVelocity();
        // Default to 20 m/s if not set (typical erosional velocity limit)
        return maxVel > 0 ? maxVel : 20.0;
      });
    }
    return new CapacityRule(ProcessEquipmentInterface::getCapacityDuty,
        ProcessEquipmentInterface::getCapacityMax);
  }

  private CapacityRange determineCapacityRange(ProcessEquipmentInterface unit,
      OptimizationConfig config) {
    CapacityRange byName = config.capacityRangesByName.get(unit.getName());
    if (byName != null) {
      return byName;
    }
    for (Map.Entry<Class<?>, CapacityRange> entry : config.capacityRangesByType.entrySet()) {
      if (entry.getKey().isAssignableFrom(unit.getClass())) {
        return entry.getValue();
      }
    }
    return null;
  }

  /** Range container to support percentile-based capacity evaluations. */
  static final class CapacityRange {
    private final double p10;
    private final double p50;
    private final double p90;

    public CapacityRange(double p10, double p50, double p90) {
      this.p10 = p10;
      this.p50 = p50;
      this.p90 = p90;
    }

    public double atPercentile(double percentile) {
      double clamped = Math.max(0.0, Math.min(1.0, percentile));
      if (clamped <= 0.5) {
        return p10 + (p50 - p10) * (clamped / 0.5);
      }
      return p50 + (p90 - p50) * ((clamped - 0.5) / 0.5);
    }
  }

  /** Function to compute capacity duty/limit for a specific equipment. */
  @FunctionalInterface
  interface CapacityMetric extends ToDoubleFunction<ProcessEquipmentInterface> {
  }

  /** Pair of capacity duty/max providers. */
  static final class CapacityRule {
    private final CapacityMetric dutyMetric;
    private final CapacityMetric maxMetric;

    public CapacityRule(CapacityMetric dutyMetric, CapacityMetric maxMetric) {
      this.dutyMetric = dutyMetric;
      this.maxMetric = maxMetric;
    }

    public double duty(ProcessEquipmentInterface unit) {
      return dutyMetric.applyAsDouble(unit);
    }

    public double max(ProcessEquipmentInterface unit) {
      return maxMetric.applyAsDouble(unit);
    }
  }

  /** Metric to evaluate per-equipment constraints. */
  @FunctionalInterface
  interface EquipmentMetric extends ToDoubleFunction<ProcessEquipmentInterface> {
  }

  /** Constraint template applied to each matching equipment. */
  static final class EquipmentConstraintRule {
    private final Class<?> equipmentType;
    private final String name;
    private final EquipmentMetric metric;
    private final double limit;
    private final ConstraintDirection direction;
    private final ConstraintSeverity severity;
    private final double penaltyWeight;
    private final String description;

    public EquipmentConstraintRule(Class<?> equipmentType, String name, EquipmentMetric metric,
        double limit, ConstraintDirection direction, ConstraintSeverity severity,
        double penaltyWeight, String description) {
      this.equipmentType = Objects.requireNonNull(equipmentType, "Equipment type is required");
      this.name = Objects.requireNonNull(name, "Equipment constraint name is required");
      this.metric = Objects.requireNonNull(metric, "Equipment metric is required");
      this.limit = limit;
      this.direction = Objects.requireNonNull(direction, "Equipment constraint direction");
      this.severity = Objects.requireNonNull(severity, "Equipment constraint severity");
      this.penaltyWeight = penaltyWeight;
      this.description = description;
    }

    public boolean matches(ProcessEquipmentInterface unit) {
      return equipmentType.isAssignableFrom(unit.getClass());
    }

    public OptimizationConstraint toConstraint(ProcessEquipmentInterface unit) {
      return new OptimizationConstraint(name + " - " + unit.getName(),
          proc -> metric.applyAsDouble(unit), limit, direction, severity, penaltyWeight,
          description);
    }
  }
}
