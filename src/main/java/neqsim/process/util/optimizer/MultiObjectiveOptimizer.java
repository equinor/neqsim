package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer.ConstraintSeverity;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConstraint;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;

/**
 * Multi-objective optimizer for process systems.
 *
 * <p>
 * This optimizer finds Pareto-optimal solutions when optimizing multiple competing objectives. It
 * supports two main methods:
 * </p>
 * <ul>
 * <li><b>Weighted Sum</b>: Varies weights to find different trade-off points</li>
 * <li><b>Epsilon-Constraint</b>: Optimizes one objective while constraining others</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>{@code
 * List<ObjectiveFunction> objectives =
 *     Arrays.asList(StandardObjective.MAXIMIZE_THROUGHPUT, StandardObjective.MINIMIZE_POWER);
 * 
 * MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer();
 * ParetoFront front = moo.optimizeWeightedSum(process, feedStream, objectives, baseConfig, 20);
 * 
 * ParetoSolution knee = front.findKneePoint();
 * System.out.println("Best trade-off: " + knee);
 * }</pre>
 *
 * @author ASMF
 * @version 1.0
 */
public class MultiObjectiveOptimizer implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Logger for this class. */
  private static final org.apache.logging.log4j.Logger logger =
      org.apache.logging.log4j.LogManager.getLogger(MultiObjectiveOptimizer.class);

  /** Default number of weight combinations to explore. */
  public static final int DEFAULT_WEIGHT_COMBINATIONS = 20;

  /** Default number of grid points for epsilon-constraint method. */
  public static final int DEFAULT_GRID_POINTS = 15;

  /** Minimum number of objectives required. */
  private static final int MIN_OBJECTIVES = 1;

  /** Maximum number of objectives supported efficiently. */
  private static final int MAX_OBJECTIVES_EFFICIENT = 5;

  /** Inner single-objective optimizer. */
  private final ProductionOptimizer singleObjectiveOptimizer;

  /** Whether to include infeasible solutions in the front. */
  private boolean includeInfeasible = false;

  /** Callback for progress reporting. */
  private ProgressCallback progressCallback;

  /**
   * Progress callback interface.
   */
  public interface ProgressCallback {
    /**
     * Called when a new solution is evaluated.
     *
     * @param iteration current iteration number
     * @param totalIterations total planned iterations
     * @param currentSolution current solution (may be null if infeasible)
     */
    void onProgress(int iteration, int totalIterations, ParetoSolution currentSolution);
  }

  /**
   * Create a new multi-objective optimizer.
   */
  public MultiObjectiveOptimizer() {
    this.singleObjectiveOptimizer = new ProductionOptimizer();
  }

  /**
   * Set whether to include infeasible solutions in the Pareto front.
   *
   * @param include true to include infeasible solutions
   * @return this optimizer for chaining
   */
  public MultiObjectiveOptimizer includeInfeasible(boolean include) {
    this.includeInfeasible = include;
    return this;
  }

  /**
   * Set progress callback.
   *
   * @param callback callback to receive progress updates
   * @return this optimizer for chaining
   */
  public MultiObjectiveOptimizer onProgress(ProgressCallback callback) {
    this.progressCallback = callback;
    return this;
  }

  /**
   * Find Pareto front using weighted-sum scalarization.
   *
   * <p>
   * This method varies weights across objectives and solves single-objective problems. It's simple
   * but may miss solutions on non-convex regions of the front.
   * </p>
   *
   * @param process the process system to optimize
   * @param feedStream the feed stream to manipulate
   * @param objectives list of objectives to optimize
   * @param baseConfig base optimization configuration
   * @param numWeightCombinations number of weight combinations to explore
   * @return Pareto front of non-dominated solutions
   */
  public ParetoFront optimizeWeightedSum(ProcessSystem process, StreamInterface feedStream,
      List<ObjectiveFunction> objectives, OptimizationConfig baseConfig,
      int numWeightCombinations) {
    return optimizeWeightedSum(process, feedStream, objectives, baseConfig, numWeightCombinations,
        Collections.emptyList());
  }

  /**
   * Find Pareto front using weighted-sum scalarization with additional constraints.
   *
   * @param process the process system to optimize
   * @param feedStream the feed stream to manipulate
   * @param objectives list of objectives to optimize
   * @param baseConfig base optimization configuration
   * @param numWeightCombinations number of weight combinations to explore
   * @param constraints additional optimization constraints
   * @return Pareto front of non-dominated solutions
   */
  public ParetoFront optimizeWeightedSum(ProcessSystem process, StreamInterface feedStream,
      List<ObjectiveFunction> objectives, OptimizationConfig baseConfig, int numWeightCombinations,
      List<OptimizationConstraint> constraints) {
    Objects.requireNonNull(process, "Process cannot be null");
    Objects.requireNonNull(feedStream, "Feed stream cannot be null");
    Objects.requireNonNull(objectives, "Objectives list cannot be null");
    Objects.requireNonNull(baseConfig, "Optimization config cannot be null");

    if (objectives.isEmpty()) {
      throw new IllegalArgumentException("At least one objective is required");
    }
    if (objectives.size() > MAX_OBJECTIVES_EFFICIENT) {
      logger.warn(
          "Using {} objectives may result in slow computation. "
              + "Consider reducing to {} or fewer for efficient optimization.",
          objectives.size(), MAX_OBJECTIVES_EFFICIENT);
    }
    if (numWeightCombinations < 2) {
      throw new IllegalArgumentException(
          "Number of weight combinations must be at least 2, got: " + numWeightCombinations);
    }

    logger.info("Starting weighted-sum optimization with {} objectives, {} weight combinations",
        objectives.size(), numWeightCombinations);

    if (objectives.size() == 1) {
      // Single objective - just run normal optimization
      logger.debug("Single objective detected, delegating to single-objective optimizer");
      return optimizeSingleObjective(process, feedStream, objectives.get(0), baseConfig,
          constraints);
    }

    ParetoFront front = new ParetoFront(!includeInfeasible);

    // Generate weight combinations
    List<double[]> weights = generateWeights(objectives.size(), numWeightCombinations);
    int iteration = 0;

    for (double[] w : weights) {
      iteration++;

      try {
        ProcessSystem processCopy = process.copy();
        StreamInterface feedCopy = (StreamInterface) processCopy.getUnit(feedStream.getName());

        if (feedCopy == null) {
          // Fallback: try to get the first stream
          for (ProcessEquipmentInterface unit : processCopy.getUnitOperations()) {
            if (unit instanceof StreamInterface) {
              feedCopy = (StreamInterface) unit;
              break;
            }
          }
        }

        if (feedCopy == null) {
          continue; // Can't find feed stream
        }

        // Create weighted objective
        ProductionOptimizer.OptimizationObjective weightedObjective =
            createWeightedObjective(objectives, w);

        // Run single-objective optimization
        OptimizationResult result = singleObjectiveOptimizer.optimize(processCopy, feedCopy,
            baseConfig, Collections.singletonList(weightedObjective), constraints);

        // Set the optimized process state and run
        feedCopy.setFlowRate(result.getOptimalRate(), baseConfig.getRateUnit());
        processCopy.run();

        // Evaluate all objectives at this solution
        double[] rawValues = evaluateObjectives(processCopy, objectives);

        // Create Pareto solution
        Map<String, Double> decisionVars = new HashMap<>();
        decisionVars.put("flowRate", result.getOptimalRate());

        ParetoSolution solution = new ParetoSolution.Builder().objectives(objectives, rawValues)
            .decisionVariables(decisionVars).feasible(result.isFeasible()).build();

        solution.addMetadata("weights", w);
        solution.addMetadata("weightedScore", result.getScore());

        front.add(solution);

        if (progressCallback != null) {
          progressCallback.onProgress(iteration, weights.size(), solution);
        }
      } catch (Exception e) {
        // Skip failed iterations
        if (progressCallback != null) {
          progressCallback.onProgress(iteration, weights.size(), null);
        }
      }
    }

    return front;
  }

  /**
   * Find Pareto front using epsilon-constraint method.
   *
   * <p>
   * This method optimizes the primary objective while constraining other objectives. It can find
   * solutions on non-convex regions of the front.
   * </p>
   *
   * @param process the process system to optimize
   * @param feedStream the feed stream to manipulate
   * @param primaryObjective the objective to optimize
   * @param constrainedObjectives objectives to treat as constraints
   * @param baseConfig base optimization configuration
   * @param gridPoints number of epsilon values to try for each constraint
   * @return Pareto front of non-dominated solutions
   */
  public ParetoFront optimizeEpsilonConstraint(ProcessSystem process, StreamInterface feedStream,
      ObjectiveFunction primaryObjective, List<ObjectiveFunction> constrainedObjectives,
      OptimizationConfig baseConfig, int gridPoints) {
    return optimizeEpsilonConstraint(process, feedStream, primaryObjective, constrainedObjectives,
        baseConfig, gridPoints, Collections.emptyList());
  }

  /**
   * Find Pareto front using epsilon-constraint method with additional constraints.
   *
   * @param process the process system to optimize
   * @param feedStream the feed stream to manipulate
   * @param primaryObjective the objective to optimize
   * @param constrainedObjectives objectives to treat as constraints
   * @param baseConfig base optimization configuration
   * @param gridPoints number of epsilon values to try for each constraint
   * @param additionalConstraints additional hard constraints
   * @return Pareto front of non-dominated solutions
   */
  public ParetoFront optimizeEpsilonConstraint(ProcessSystem process, StreamInterface feedStream,
      ObjectiveFunction primaryObjective, List<ObjectiveFunction> constrainedObjectives,
      OptimizationConfig baseConfig, int gridPoints,
      List<OptimizationConstraint> additionalConstraints) {
    Objects.requireNonNull(process, "Process cannot be null");
    Objects.requireNonNull(feedStream, "Feed stream cannot be null");
    Objects.requireNonNull(primaryObjective, "Primary objective cannot be null");
    Objects.requireNonNull(constrainedObjectives, "Constrained objectives list cannot be null");
    Objects.requireNonNull(baseConfig, "Optimization config cannot be null");

    if (constrainedObjectives.isEmpty()) {
      throw new IllegalArgumentException(
          "At least one constrained objective is required for epsilon-constraint method");
    }
    if (gridPoints < 2) {
      throw new IllegalArgumentException(
          "Number of grid points must be at least 2, got: " + gridPoints);
    }

    int totalObjectives = 1 + constrainedObjectives.size();
    if (totalObjectives > MAX_OBJECTIVES_EFFICIENT) {
      logger.warn("Using {} objectives may result in slow computation. "
          + "Grid size grows exponentially with constrained objectives.", totalObjectives);
    }

    logger.info("Starting epsilon-constraint optimization: primary={}, constrained={}, grid={}",
        primaryObjective.getName(), constrainedObjectives.size(), gridPoints);

    ParetoFront front = new ParetoFront(!includeInfeasible);

    // First, find bounds for each constrained objective
    Map<ObjectiveFunction, double[]> bounds =
        findObjectiveBounds(process, feedStream, constrainedObjectives, baseConfig);

    // Create all objectives list for evaluation
    List<ObjectiveFunction> allObjectives = new ArrayList<>();
    allObjectives.add(primaryObjective);
    allObjectives.addAll(constrainedObjectives);

    // Generate epsilon grid
    List<double[]> epsilonGrid = generateEpsilonGrid(constrainedObjectives, bounds, gridPoints);
    int iteration = 0;
    int totalIterations = epsilonGrid.size();

    for (double[] epsilons : epsilonGrid) {
      iteration++;
      ProcessSystem processCopy = process.copy();
      StreamInterface feedCopy = (StreamInterface) processCopy.getUnit(feedStream.getName());

      // Create epsilon constraints
      List<OptimizationConstraint> allConstraints = new ArrayList<>(additionalConstraints);
      for (int i = 0; i < constrainedObjectives.size(); i++) {
        ObjectiveFunction obj = constrainedObjectives.get(i);
        double epsilon = epsilons[i];

        OptimizationConstraint epsilonConstraint = createEpsilonConstraint(obj, epsilon);
        allConstraints.add(epsilonConstraint);
      }

      // Create primary objective for ProductionOptimizer
      ProductionOptimizer.OptimizationObjective primaryObjConfig =
          objectiveFunctionToConfig(primaryObjective);

      // Run optimization
      OptimizationResult result = singleObjectiveOptimizer.optimize(processCopy, feedCopy,
          baseConfig, Collections.singletonList(primaryObjConfig), allConstraints);

      if (!result.isFeasible() && !includeInfeasible) {
        continue;
      }

      // Set process state and evaluate
      feedCopy.setFlowRate(result.getOptimalRate(), baseConfig.getRateUnit());
      try {
        processCopy.run();
      } catch (Exception e) {
        continue;
      }

      double[] rawValues = evaluateObjectives(processCopy, allObjectives);

      Map<String, Double> decisionVars = new HashMap<>();
      decisionVars.put("flowRate", result.getOptimalRate());

      ParetoSolution solution = new ParetoSolution.Builder().objectives(allObjectives, rawValues)
          .decisionVariables(decisionVars).feasible(result.isFeasible()).build();

      solution.addMetadata("epsilons", epsilons);

      front.add(solution);

      if (progressCallback != null) {
        progressCallback.onProgress(iteration, totalIterations, solution);
      }
    }

    return front;
  }

  /**
   * Optimize a single objective (convenience method).
   */
  private ParetoFront optimizeSingleObjective(ProcessSystem process, StreamInterface feedStream,
      ObjectiveFunction objective, OptimizationConfig baseConfig,
      List<OptimizationConstraint> constraints) {
    ParetoFront front = new ParetoFront(!includeInfeasible);

    ProcessSystem processCopy = process.copy();
    StreamInterface feedCopy = (StreamInterface) processCopy.getUnit(feedStream.getName());

    ProductionOptimizer.OptimizationObjective objConfig = objectiveFunctionToConfig(objective);

    OptimizationResult result = singleObjectiveOptimizer.optimize(processCopy, feedCopy, baseConfig,
        Collections.singletonList(objConfig), constraints);

    feedCopy.setFlowRate(result.getOptimalRate(), baseConfig.getRateUnit());
    try {
      processCopy.run();
    } catch (Exception e) {
      return front;
    }

    double[] rawValues = new double[] {objective.evaluate(processCopy)};

    Map<String, Double> decisionVars = new HashMap<>();
    decisionVars.put("flowRate", result.getOptimalRate());

    ParetoSolution solution =
        new ParetoSolution.Builder().objectives(Collections.singletonList(objective), rawValues)
            .decisionVariables(decisionVars).feasible(result.isFeasible()).build();

    front.add(solution);
    return front;
  }

  /**
   * Generate Pareto front by sampling at fixed flow rates within the feasible range.
   *
   * <p>
   * This method directly samples the decision variable space by evaluating the process at different
   * flow rates. It's useful when objectives are linearly related (e.g., throughput vs power) where
   * weighted-sum methods converge to a single point.
   * </p>
   *
   * @param process the process system to evaluate
   * @param feedStream the feed stream to manipulate
   * @param objectives objectives to evaluate at each sample point
   * @param baseConfig base optimization configuration (defines flow rate range)
   * @param numSamples number of sample points across the flow range
   * @return Pareto front of non-dominated solutions from sampled points
   */
  public ParetoFront sampleParetoFront(ProcessSystem process, StreamInterface feedStream,
      List<ObjectiveFunction> objectives, OptimizationConfig baseConfig, int numSamples) {
    return sampleParetoFront(process, feedStream, objectives, baseConfig, numSamples,
        Collections.emptyList());
  }

  /**
   * Generate Pareto front by sampling at fixed flow rates with constraint checking.
   *
   * @param process the process system to evaluate
   * @param feedStream the feed stream to manipulate
   * @param objectives objectives to evaluate at each sample point
   * @param baseConfig base optimization configuration (defines flow rate range)
   * @param numSamples number of sample points across the flow range
   * @param constraints constraints to check feasibility at each point
   * @return Pareto front of non-dominated solutions from sampled points
   */
  public ParetoFront sampleParetoFront(ProcessSystem process, StreamInterface feedStream,
      List<ObjectiveFunction> objectives, OptimizationConfig baseConfig, int numSamples,
      List<OptimizationConstraint> constraints) {
    ParetoFront front = new ParetoFront(!includeInfeasible);

    double lowerBound = baseConfig.getLowerBound();
    double upperBound = baseConfig.getUpperBound();
    String rateUnit = baseConfig.getRateUnit();

    // Generate sample points
    double step = (upperBound - lowerBound) / Math.max(1, numSamples - 1);

    for (int i = 0; i < numSamples; i++) {
      double flowRate = lowerBound + i * step;

      // Don't exceed upper bound due to floating point
      if (flowRate > upperBound) {
        flowRate = upperBound;
      }

      try {
        ProcessSystem processCopy = process.copy();
        StreamInterface feedCopy = (StreamInterface) processCopy.getUnit(feedStream.getName());

        if (feedCopy == null) {
          continue;
        }

        // Set flow rate and run process
        feedCopy.setFlowRate(flowRate, rateUnit);
        processCopy.run();

        // Check feasibility using constraints and equipment utilization
        boolean feasible = checkFeasibility(processCopy, baseConfig, constraints);

        if (!feasible && !includeInfeasible) {
          if (progressCallback != null) {
            progressCallback.onProgress(i + 1, numSamples, null);
          }
          continue;
        }

        // Evaluate all objectives
        double[] rawValues = evaluateObjectives(processCopy, objectives);

        // Create solution
        Map<String, Double> decisionVars = new HashMap<>();
        decisionVars.put("flowRate", flowRate);

        ParetoSolution solution = new ParetoSolution.Builder().objectives(objectives, rawValues)
            .decisionVariables(decisionVars).feasible(feasible).build();

        front.add(solution);

        if (progressCallback != null) {
          progressCallback.onProgress(i + 1, numSamples, solution);
        }
      } catch (Exception e) {
        // Skip failed samples
        if (progressCallback != null) {
          progressCallback.onProgress(i + 1, numSamples, null);
        }
      }
    }

    return front;
  }

  /**
   * Check feasibility of current process state using equipment utilization and constraints.
   */
  private boolean checkFeasibility(ProcessSystem process, OptimizationConfig config,
      List<OptimizationConstraint> constraints) {
    // Check equipment utilization limits
    // Use the configured default utilization limit (directly access the field)
    double utilizationLimit = ProductionOptimizer.DEFAULT_UTILIZATION_LIMIT;

    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      double duty = unit.getCapacityDuty();
      double max = unit.getCapacityMax();
      if (max > 0 && duty / max > utilizationLimit) {
        return false;
      }
    }

    // Check explicit constraints using the existing API (isSatisfied method)
    for (OptimizationConstraint constraint : constraints) {
      if (constraint.getSeverity() == ConstraintSeverity.HARD) {
        if (!constraint.isSatisfied(process)) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Generate weight combinations for n objectives.
   */
  private List<double[]> generateWeights(int numObjectives, int numCombinations) {
    List<double[]> weights = new ArrayList<>();

    if (numObjectives == 2) {
      // Simple linear interpolation for 2 objectives
      for (int i = 0; i <= numCombinations; i++) {
        double w1 = (double) i / numCombinations;
        weights.add(new double[] {w1, 1.0 - w1});
      }
    } else {
      // For more objectives, use simplex lattice design
      generateSimplexWeights(weights, numObjectives, numCombinations, new double[numObjectives], 0,
          1.0);
    }

    return weights;
  }

  /**
   * Recursive generation of simplex lattice weights.
   */
  private void generateSimplexWeights(List<double[]> weights, int numObjectives, int divisions,
      double[] current, int index, double remaining) {
    if (index == numObjectives - 1) {
      current[index] = remaining;
      weights.add(Arrays.copyOf(current, current.length));
      return;
    }

    for (int i = 0; i <= divisions; i++) {
      double w = (double) i / divisions;
      if (w <= remaining + 1e-9) {
        current[index] = w;
        generateSimplexWeights(weights, numObjectives, divisions, current, index + 1,
            remaining - w);
      }
    }
  }

  /**
   * Create weighted objective for ProductionOptimizer.
   */
  private ProductionOptimizer.OptimizationObjective createWeightedObjective(
      List<ObjectiveFunction> objectives, double[] weights) {
    return new ProductionOptimizer.OptimizationObjective("WeightedSum", process -> {
      double score = 0.0;
      for (int i = 0; i < objectives.size(); i++) {
        ObjectiveFunction obj = objectives.get(i);
        double value = obj.evaluate(process);
        // Normalize direction: always maximize the weighted sum
        if (obj.getDirection() == ObjectiveFunction.Direction.MINIMIZE) {
          value = -value;
        }
        score += weights[i] * value;
      }
      return score;
    }, 1.0, ProductionOptimizer.ObjectiveType.MAXIMIZE);
  }

  /**
   * Convert ObjectiveFunction to ProductionOptimizer.OptimizationObjective.
   */
  private ProductionOptimizer.OptimizationObjective objectiveFunctionToConfig(
      ObjectiveFunction objective) {
    ProductionOptimizer.ObjectiveType type =
        objective.getDirection() == ObjectiveFunction.Direction.MAXIMIZE
            ? ProductionOptimizer.ObjectiveType.MAXIMIZE
            : ProductionOptimizer.ObjectiveType.MINIMIZE;
    return new ProductionOptimizer.OptimizationObjective(objective.getName(), objective::evaluate,
        1.0, type);
  }

  /**
   * Evaluate all objectives for the current process state.
   */
  private double[] evaluateObjectives(ProcessSystem process, List<ObjectiveFunction> objectives) {
    double[] values = new double[objectives.size()];
    for (int i = 0; i < objectives.size(); i++) {
      values[i] = objectives.get(i).evaluate(process);
    }
    return values;
  }

  /**
   * Find bounds for each objective by optimizing individually.
   */
  private Map<ObjectiveFunction, double[]> findObjectiveBounds(ProcessSystem process,
      StreamInterface feedStream, List<ObjectiveFunction> objectives,
      OptimizationConfig baseConfig) {
    Map<ObjectiveFunction, double[]> bounds = new HashMap<>();

    for (ObjectiveFunction obj : objectives) {
      ProcessSystem processCopy = process.copy();
      StreamInterface feedCopy = (StreamInterface) processCopy.getUnit(feedStream.getName());

      // Find minimum
      ProductionOptimizer.OptimizationObjective minObj =
          new ProductionOptimizer.OptimizationObjective(obj.getName() + "_min", obj::evaluate, 1.0,
              ProductionOptimizer.ObjectiveType.MINIMIZE);
      OptimizationResult minResult = singleObjectiveOptimizer.optimize(processCopy, feedCopy,
          baseConfig, Collections.singletonList(minObj), Collections.emptyList());

      feedCopy.setFlowRate(minResult.getOptimalRate(), baseConfig.getRateUnit());
      try {
        processCopy.run();
      } catch (Exception e) {
        // Use config bounds as fallback
      }
      double minValue = obj.evaluate(processCopy);

      // Find maximum
      processCopy = process.copy();
      feedCopy = (StreamInterface) processCopy.getUnit(feedStream.getName());

      ProductionOptimizer.OptimizationObjective maxObj =
          new ProductionOptimizer.OptimizationObjective(obj.getName() + "_max", obj::evaluate, 1.0,
              ProductionOptimizer.ObjectiveType.MAXIMIZE);
      OptimizationResult maxResult = singleObjectiveOptimizer.optimize(processCopy, feedCopy,
          baseConfig, Collections.singletonList(maxObj), Collections.emptyList());

      feedCopy.setFlowRate(maxResult.getOptimalRate(), baseConfig.getRateUnit());
      try {
        processCopy.run();
      } catch (Exception e) {
        // Use config bounds as fallback
      }
      double maxValue = obj.evaluate(processCopy);

      bounds.put(obj, new double[] {Math.min(minValue, maxValue), Math.max(minValue, maxValue)});
    }

    return bounds;
  }

  /**
   * Generate epsilon grid for epsilon-constraint method.
   */
  private List<double[]> generateEpsilonGrid(List<ObjectiveFunction> objectives,
      Map<ObjectiveFunction, double[]> bounds, int gridPoints) {
    List<double[]> grid = new ArrayList<>();

    if (objectives.size() == 1) {
      // Single constrained objective - linear grid
      ObjectiveFunction obj = objectives.get(0);
      double[] bound = bounds.get(obj);
      for (int i = 0; i <= gridPoints; i++) {
        double epsilon = bound[0] + (bound[1] - bound[0]) * i / gridPoints;
        grid.add(new double[] {epsilon});
      }
    } else {
      // Multiple constrained objectives - full grid
      generateGrid(grid, objectives, bounds, gridPoints, new double[objectives.size()], 0);
    }

    return grid;
  }

  /**
   * Recursive grid generation.
   */
  private void generateGrid(List<double[]> grid, List<ObjectiveFunction> objectives,
      Map<ObjectiveFunction, double[]> bounds, int gridPoints, double[] current, int index) {
    if (index == objectives.size()) {
      grid.add(Arrays.copyOf(current, current.length));
      return;
    }

    ObjectiveFunction obj = objectives.get(index);
    double[] bound = bounds.get(obj);
    for (int i = 0; i <= gridPoints; i++) {
      current[index] = bound[0] + (bound[1] - bound[0]) * i / gridPoints;
      generateGrid(grid, objectives, bounds, gridPoints, current, index + 1);
    }
  }

  /**
   * Create epsilon constraint for an objective.
   */
  private OptimizationConstraint createEpsilonConstraint(ObjectiveFunction objective,
      double epsilon) {
    if (objective.getDirection() == ObjectiveFunction.Direction.MINIMIZE) {
      // For minimize objectives, constraint is: value <= epsilon
      return OptimizationConstraint.lessThan("epsilon_" + objective.getName(), objective::evaluate,
          epsilon, ConstraintSeverity.HARD, 0.0, "Epsilon constraint for " + objective.getName());
    } else {
      // For maximize objectives, constraint is: value >= epsilon
      return OptimizationConstraint.greaterThan("epsilon_" + objective.getName(),
          objective::evaluate, epsilon, ConstraintSeverity.HARD, 0.0,
          "Epsilon constraint for " + objective.getName());
    }
  }

  /**
   * Result container for multi-objective optimization.
   */
  public static class MultiObjectiveResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ParetoFront paretoFront;
    private final ParetoSolution kneePoint;
    private final List<ObjectiveFunction> objectives;
    private final String method;
    private final long computationTimeMs;

    /**
     * Constructor for MultiObjectiveResult.
     *
     * @param paretoFront the Pareto front
     * @param objectives list of objectives
     * @param method optimization method used
     * @param computationTimeMs computation time in milliseconds
     */
    public MultiObjectiveResult(ParetoFront paretoFront, List<ObjectiveFunction> objectives,
        String method, long computationTimeMs) {
      this.paretoFront = paretoFront;
      this.kneePoint = paretoFront.findKneePoint();
      this.objectives = objectives;
      this.method = method;
      this.computationTimeMs = computationTimeMs;
    }

    public ParetoFront getParetoFront() {
      return paretoFront;
    }

    public ParetoSolution getKneePoint() {
      return kneePoint;
    }

    public List<ObjectiveFunction> getObjectives() {
      return objectives;
    }

    public String getMethod() {
      return method;
    }

    public long getComputationTimeMs() {
      return computationTimeMs;
    }

    public int getNumSolutions() {
      return paretoFront.size();
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("MultiObjectiveResult{\n");
      sb.append("  method=").append(method).append("\n");
      sb.append("  solutions=").append(paretoFront.size()).append("\n");
      sb.append("  computationTime=").append(computationTimeMs).append("ms\n");
      if (kneePoint != null) {
        sb.append("  kneePoint=").append(kneePoint).append("\n");
      }
      sb.append("}");
      return sb.toString();
    }
  }
}
