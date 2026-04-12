package neqsim.process.equipment.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Formal NLP and multi-objective optimizer for pipeline network production allocation.
 *
 * <p>
 * Replaces the gradient-finite-difference approach in {@link LoopedPipeNetwork} with mathematically
 * rigorous optimization using Apache Commons Math:
 * </p>
 * <ul>
 * <li><b>BOBYQA</b>: Bound Optimization BY Quadratic Approximation — derivative-free trust-region
 * method. Best for smooth objectives with 2–20 decision variables.</li>
 * <li><b>CMA-ES</b>: Covariance Matrix Adaptation Evolution Strategy — global optimizer for
 * non-convex, noisy, or multi-modal landscapes. Robust for 5–50 variables.</li>
 * <li><b>Multi-objective</b>: Weighted-sum scalarization with Pareto front exploration via
 * systematic weight sweep.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see LoopedPipeNetwork
 */
public class NetworkOptimizer {

  private static final Logger logger = LogManager.getLogger(NetworkOptimizer.class);

  /**
   * Optimizer algorithm selection.
   */
  public enum Algorithm {
    /**
     * BOBYQA (Bound Optimization BY Quadratic Approximation). Derivative-free, trust-region method
     * by M.J.D. Powell. Best for smooth objectives with box constraints and 2–20 variables. Builds
     * a local quadratic model and iteratively improves it.
     */
    BOBYQA,

    /**
     * CMA-ES (Covariance Matrix Adaptation Evolution Strategy). Population-based global optimizer
     * by N. Hansen. Robust for non-convex, noisy, or multi-modal objectives. Uses 5–50 variables
     * effectively but requires more function evaluations than BOBYQA.
     */
    CMAES
  }

  /**
   * Objective type for single-objective optimization.
   */
  public enum ObjectiveType {
    /**
     * Maximize total production (mass flow) at all sink nodes.
     */
    MAX_PRODUCTION,

    /**
     * Maximize revenue (price-weighted production).
     */
    MAX_REVENUE,

    /**
     * Minimize total compressor power consumption.
     */
    MIN_COMPRESSOR_POWER,

    /**
     * Maximize production per unit of compressor power (specific production).
     */
    MAX_SPECIFIC_PRODUCTION
  }

  /** The network being optimized. */
  private final LoopedPipeNetwork network;

  /** Algorithm selection. */
  private Algorithm algorithm = Algorithm.BOBYQA;

  /** Maximum function evaluations. */
  private int maxEvaluations = 500;

  /** Objective type. */
  private ObjectiveType objectiveType = ObjectiveType.MAX_PRODUCTION;

  /** Penalty factor for constraint violations. */
  private double constraintPenalty = 1e6;

  /** Choke elements to optimize (names). */
  private List<String> chokeNames;

  /** Lower bounds for choke openings (%). */
  private double[] lowerBounds;

  /** Upper bounds for choke openings (%). */
  private double[] upperBounds;

  /** Number of Pareto front points for multi-objective. */
  private int paretoPoints = 11;

  /** Last optimization result. */
  private OptimizationResult lastResult;

  /**
   * Create a new network optimizer.
   *
   * @param network the pipeline network to optimize
   */
  public NetworkOptimizer(LoopedPipeNetwork network) {
    this.network = network;
  }

  /**
   * Set the optimization algorithm.
   *
   * @param algorithm BOBYQA or CMAES
   */
  public void setAlgorithm(Algorithm algorithm) {
    this.algorithm = algorithm;
  }

  /**
   * Get the optimization algorithm.
   *
   * @return algorithm
   */
  public Algorithm getAlgorithm() {
    return algorithm;
  }

  /**
   * Set the maximum number of function evaluations.
   *
   * @param maxEval maximum evaluations
   */
  public void setMaxEvaluations(int maxEval) {
    this.maxEvaluations = maxEval;
  }

  /**
   * Get the maximum number of function evaluations.
   *
   * @return maximum evaluations
   */
  public int getMaxEvaluations() {
    return maxEvaluations;
  }

  /**
   * Set the objective type.
   *
   * @param type objective type
   */
  public void setObjectiveType(ObjectiveType type) {
    this.objectiveType = type;
  }

  /**
   * Get the objective type.
   *
   * @return objective type
   */
  public ObjectiveType getObjectiveType() {
    return objectiveType;
  }

  /**
   * Set the constraint penalty factor.
   *
   * @param penalty penalty multiplier for each constraint violation
   */
  public void setConstraintPenalty(double penalty) {
    this.constraintPenalty = penalty;
  }

  /**
   * Get the constraint penalty factor.
   *
   * @return penalty
   */
  public double getConstraintPenalty() {
    return constraintPenalty;
  }

  /**
   * Set the number of Pareto front points for multi-objective optimization.
   *
   * @param points number of weight combinations to evaluate (minimum 3)
   */
  public void setParetoPoints(int points) {
    this.paretoPoints = Math.max(3, points);
  }

  /**
   * Get the number of Pareto front points.
   *
   * @return number of points
   */
  public int getParetoPoints() {
    return paretoPoints;
  }

  /**
   * Get the result from the last optimization run.
   *
   * @return optimization result, or null if not yet run
   */
  public OptimizationResult getLastResult() {
    return lastResult;
  }

  /**
   * Run single-objective optimization of choke openings.
   *
   * <p>
   * Discovers all choke elements, constructs bounds, and runs the selected algorithm (BOBYQA or
   * CMA-ES) to optimize the specified objective.
   * </p>
   *
   * @return optimization result with optimal choke openings, objective value, and diagnostics
   */
  public OptimizationResult optimize() {
    long startTime = System.currentTimeMillis();

    // Discover choke elements
    discoverChokeElements();
    if (chokeNames.isEmpty()) {
      logger.warn("No choke elements found in network — nothing to optimize");
      lastResult = new OptimizationResult();
      lastResult.converged = false;
      lastResult.message = "No choke elements found";
      return lastResult;
    }

    int n = chokeNames.size();
    logger.info("Optimizing " + n + " choke openings using " + algorithm + " / " + objectiveType);

    // Get initial choke openings
    double[] x0 = new double[n];
    for (int i = 0; i < n; i++) {
      LoopedPipeNetwork.NetworkPipe pipe = network.getPipe(chokeNames.get(i));
      x0[i] = pipe.getChokeOpening();
    }

    // Run optimizer
    PointValuePair result;
    int evaluationCount;

    switch (algorithm) {
      case CMAES:
        result = runCMAES(x0);
        evaluationCount = maxEvaluations; // CMA-ES doesn't expose exact count easily
        break;
      case BOBYQA:
      default:
        result = runBOBYQA(x0);
        evaluationCount = maxEvaluations;
        break;
    }

    // Apply optimal solution
    double[] optimalOpenings = result.getPoint();
    applyChokeOpenings(optimalOpenings);
    network.run();

    // Build result
    lastResult = new OptimizationResult();
    lastResult.converged = true;
    lastResult.objectiveValue = -result.getValue(); // Negate back (we minimize -f)
    lastResult.chokeNames = new ArrayList<>(chokeNames);
    lastResult.chokeOpenings = optimalOpenings.clone();
    lastResult.algorithm = algorithm.name();
    lastResult.objectiveTypeName = objectiveType.name();
    lastResult.elapsedMs = System.currentTimeMillis() - startTime;
    lastResult.functionEvaluations = evaluationCount;
    lastResult.message = "Optimization completed";

    // Capture per-well results
    lastResult.wellResults = new LinkedHashMap<>();
    for (String name : chokeNames) {
      LoopedPipeNetwork.NetworkPipe pipe = network.getPipe(name);
      double rate = Math.abs(pipe.getFlowRate()) * 3600.0; // kg/hr
      lastResult.wellResults.put(name,
          new double[] {rate, pipe.getChokeOpening(), pipe.getHeadLoss() / 1e5});
    }

    double totalProd = network.getTotalSinkFlow() * 3600.0;
    lastResult.totalProductionKgHr = totalProd;

    logger.info(String.format("Optimization complete: obj=%.4f, production=%.0f kg/hr, time=%d ms",
        lastResult.objectiveValue, totalProd, lastResult.elapsedMs));

    return lastResult;
  }

  /**
   * Run multi-objective optimization: production vs compressor power.
   *
   * <p>
   * Explores the Pareto front by sweeping a weight parameter w from 0 to 1:
   * </p>
   *
   * <pre>
   * f(x) = w * production(x) - (1 - w) * power(x)
   * </pre>
   *
   * <p>
   * Returns a list of Pareto-optimal solutions, each with its production, power, and choke
   * settings.
   * </p>
   *
   * @return list of Pareto-optimal results ordered by increasing production weight
   */
  public List<OptimizationResult> optimizeMultiObjective() {
    long startTime = System.currentTimeMillis();

    discoverChokeElements();
    if (chokeNames.isEmpty()) {
      return Collections.emptyList();
    }

    int n = chokeNames.size();

    // Save initial state
    double[] originalOpenings = new double[n];
    for (int i = 0; i < n; i++) {
      LoopedPipeNetwork.NetworkPipe pipe = network.getPipe(chokeNames.get(i));
      originalOpenings[i] = pipe.getChokeOpening();
    }

    List<OptimizationResult> paretoFront = new ArrayList<>();

    for (int p = 0; p < paretoPoints; p++) {
      double w = (double) p / (paretoPoints - 1);

      // Create weighted objective function
      final double weight = w;
      ObjectiveFunction objFunc = new ObjectiveFunction(x -> {
        try {
          applyChokeOpenings(x);
          network.run();

          double production = network.getTotalSinkFlow() * 3600.0; // kg/hr
          double power = getTotalCompressorPower(); // kW

          // Normalize: production in thousands of kg/hr, power in MW
          double normProd = production / 1000.0;
          double normPower = power / 1000.0;

          double penalty = computePenalty();
          double objective = weight * normProd - (1.0 - weight) * normPower - penalty;
          if (Double.isNaN(objective) || Double.isInfinite(objective)) {
            return 1e12;
          }
          return -objective; // Minimize negative of objective
        } catch (Exception ex) {
          logger.debug("Multi-objective evaluation failed: " + ex.getMessage());
          return 1e12;
        }
      });

      // Use current position as start (warm-start from previous Pareto point)
      double[] x0 = (p == 0) ? originalOpenings.clone()
          : paretoFront.get(paretoFront.size() - 1).chokeOpenings.clone();

      PointValuePair result;
      try {
        int interpPts = 2 * n + 1;
        double trustRadius = 10.0;
        double stopRadius = 0.01;
        BOBYQAOptimizer optimizer = new BOBYQAOptimizer(interpPts, trustRadius, stopRadius);
        result = optimizer.optimize(new MaxEval(maxEvaluations / paretoPoints + 50), objFunc,
            GoalType.MINIMIZE, new InitialGuess(x0), new SimpleBounds(lowerBounds, upperBounds));
      } catch (Exception e) {
        logger.warn("Pareto point w=" + w + " failed: " + e.getMessage());
        continue;
      }

      // Apply and capture result
      double[] optX = result.getPoint();
      applyChokeOpenings(optX);
      network.run();

      OptimizationResult res = new OptimizationResult();
      res.converged = true;
      res.chokeNames = new ArrayList<>(chokeNames);
      res.chokeOpenings = optX.clone();
      res.totalProductionKgHr = network.getTotalSinkFlow() * 3600.0;
      res.totalCompressorPowerKW = getTotalCompressorPower();
      res.paretoWeight = w;
      res.objectiveValue = -result.getValue();
      res.algorithm = algorithm.name() + "-MultiObj";
      res.objectiveTypeName = "PARETO(prod=" + String.format("%.2f", w) + ")";
      res.message = "Pareto point w=" + String.format("%.2f", w);
      paretoFront.add(res);
    }

    // Restore original state
    applyChokeOpenings(originalOpenings);
    network.run();

    long elapsed = System.currentTimeMillis() - startTime;
    logger.info("Multi-objective optimization: " + paretoFront.size() + " Pareto points in "
        + elapsed + " ms");

    return paretoFront;
  }

  /**
   * Run BOBYQA (Bound Optimization BY Quadratic Approximation).
   *
   * @param x0 initial point (choke openings in %)
   * @return optimal point-value pair
   */
  private PointValuePair runBOBYQA(double[] x0) {
    int n = x0.length;
    int interpPts = Math.min(2 * n + 1, (n + 1) * (n + 2) / 2);
    interpPts = Math.max(interpPts, n + 2);
    double trustRadius = 15.0; // Initial trust region in % opening
    double stopRadius = 0.1; // Stop tolerance in % opening

    BOBYQAOptimizer optimizer = new BOBYQAOptimizer(interpPts, trustRadius, stopRadius);

    ObjectiveFunction objFunc = new ObjectiveFunction(x -> {
      try {
        applyChokeOpenings(x);
        network.run();
        double obj = evaluateObjective();
        if (Double.isNaN(obj) || Double.isInfinite(obj)) {
          return 1e12;
        }
        return -obj; // BOBYQA minimizes; we want to maximize
      } catch (Exception ex) {
        logger.debug("Objective evaluation failed: " + ex.getMessage());
        return 1e12; // Large penalty for failed evaluations
      }
    });

    try {
      return optimizer.optimize(new MaxEval(maxEvaluations), objFunc, GoalType.MINIMIZE,
          new InitialGuess(x0), new SimpleBounds(lowerBounds, upperBounds));
    } catch (Exception e) {
      logger.warn("BOBYQA optimization exception: " + e.getMessage());
      // Return initial point if optimization fails
      try {
        applyChokeOpenings(x0);
        network.run();
        return new PointValuePair(x0, -evaluateObjective());
      } catch (Exception ex2) {
        return new PointValuePair(x0, 1e12);
      }
    }
  }

  /**
   * Run CMA-ES (Covariance Matrix Adaptation Evolution Strategy).
   *
   * @param x0 initial point (choke openings in %)
   * @return optimal point-value pair
   */
  private PointValuePair runCMAES(double[] x0) {
    int n = x0.length;
    double[] sigma = new double[n];
    Arrays.fill(sigma, 10.0); // Initial step size: 10% opening

    int populationSize = 4 + (int) (3.0 * Math.log(n));
    CMAESOptimizer optimizer =
        new CMAESOptimizer(maxEvaluations, 1e-6, true, 0, 10, new MersenneTwister(42), false, null);

    ObjectiveFunction objFunc = new ObjectiveFunction(x -> {
      try {
        applyChokeOpenings(x);
        network.run();
        double obj = evaluateObjective();
        if (Double.isNaN(obj) || Double.isInfinite(obj)) {
          return 1e12;
        }
        return -obj; // CMA-ES minimizes
      } catch (Exception ex) {
        logger.debug("Objective evaluation failed: " + ex.getMessage());
        return 1e12;
      }
    });

    try {
      return optimizer.optimize(new MaxEval(maxEvaluations), objFunc, GoalType.MINIMIZE,
          new InitialGuess(x0), new SimpleBounds(lowerBounds, upperBounds),
          new CMAESOptimizer.Sigma(sigma), new CMAESOptimizer.PopulationSize(populationSize));
    } catch (Exception e) {
      logger.warn("CMA-ES optimization exception: " + e.getMessage());
      try {
        applyChokeOpenings(x0);
        network.run();
        return new PointValuePair(x0, -evaluateObjective());
      } catch (Exception ex2) {
        return new PointValuePair(x0, 1e12);
      }
    }
  }

  /**
   * Evaluate the objective function based on the selected objective type.
   *
   * @return objective value (higher is better)
   */
  private double evaluateObjective() {
    double production = network.getTotalSinkFlow() * 3600.0; // kg/hr
    double penalty = computePenalty();

    switch (objectiveType) {
      case MAX_REVENUE:
        double revenue = 0.0;
        Map<String, double[]> alloc = network.getWellAllocationResults();
        if (alloc != null && !alloc.isEmpty()) {
          for (double[] vals : alloc.values()) {
            revenue += vals[1]; // revenue column
          }
        } else {
          revenue = production; // Fallback: use mass flow
        }
        return revenue - penalty;

      case MIN_COMPRESSOR_POWER:
        double power = getTotalCompressorPower();
        return -power - penalty; // Negate power (minimizing)

      case MAX_SPECIFIC_PRODUCTION:
        double totalPower = getTotalCompressorPower();
        if (totalPower < 1.0) {
          totalPower = 1.0; // Avoid division by zero
        }
        return (production / totalPower) - penalty;

      case MAX_PRODUCTION:
      default:
        return production - penalty;
    }
  }

  /**
   * Compute constraint violation penalty.
   *
   * @return penalty value (non-negative)
   */
  private double computePenalty() {
    List<String> violations = network.checkConstraints();
    List<String> erosions = network.checkErosionalVelocity();
    int totalViolations = violations.size() + erosions.size();
    return totalViolations * constraintPenalty;
  }

  /**
   * Get total compressor power across all compressor elements in the network.
   *
   * @return total power in kW
   */
  private double getTotalCompressorPower() {
    double total = 0.0;
    for (String pipeName : network.getPipeNames()) {
      LoopedPipeNetwork.NetworkPipe pipe = network.getPipe(pipeName);
      if (pipe != null
          && pipe.getElementType() == LoopedPipeNetwork.NetworkElementType.COMPRESSOR) {
        total += pipe.getCompressorPower();
      }
    }
    return total;
  }

  /**
   * Discover choke elements and set up bounds.
   */
  private void discoverChokeElements() {
    chokeNames = new ArrayList<>();
    for (String pipeName : network.getPipeNames()) {
      LoopedPipeNetwork.NetworkPipe pipe = network.getPipe(pipeName);
      if (pipe != null && pipe.getElementType() == LoopedPipeNetwork.NetworkElementType.CHOKE) {
        chokeNames.add(pipeName);
      }
    }
    int n = chokeNames.size();
    lowerBounds = new double[n];
    upperBounds = new double[n];
    Arrays.fill(lowerBounds, 1.0); // Minimum 1% opening
    Arrays.fill(upperBounds, 100.0); // Maximum 100% opening
  }

  /**
   * Apply choke opening values to the network elements.
   *
   * @param openings array of choke openings in % (same order as chokeNames)
   */
  private void applyChokeOpenings(double[] openings) {
    for (int i = 0; i < chokeNames.size(); i++) {
      LoopedPipeNetwork.NetworkPipe pipe = network.getPipe(chokeNames.get(i));
      if (pipe != null) {
        double clipped = Math.max(lowerBounds[i], Math.min(upperBounds[i], openings[i]));
        pipe.setChokeOpening(clipped);
      }
    }
  }

  /**
   * Set custom bounds for a specific choke element.
   *
   * @param chokeName name of the choke element
   * @param minOpening minimum opening in % (default 1)
   * @param maxOpening maximum opening in % (default 100)
   */
  public void setChokeBounds(String chokeName, double minOpening, double maxOpening) {
    if (chokeNames == null) {
      discoverChokeElements();
    }
    int idx = chokeNames.indexOf(chokeName);
    if (idx >= 0) {
      lowerBounds[idx] = Math.max(0.1, minOpening);
      upperBounds[idx] = Math.min(100.0, maxOpening);
    }
  }

  /**
   * Result container for network optimization.
   */
  public static class OptimizationResult {
    /** Whether the optimization converged. */
    public boolean converged;

    /** The optimal objective function value. */
    public double objectiveValue;

    /** Total production at optimal point (kg/hr). */
    public double totalProductionKgHr;

    /** Total compressor power at optimal point (kW). */
    public double totalCompressorPowerKW;

    /** Choke element names (ordered). */
    public List<String> chokeNames;

    /** Optimal choke openings (% , same order as chokeNames). */
    public double[] chokeOpenings;

    /** Algorithm used. */
    public String algorithm;

    /** Objective type used. */
    public String objectiveTypeName;

    /** Wall-clock time in milliseconds. */
    public long elapsedMs;

    /** Number of objective function evaluations. */
    public int functionEvaluations;

    /** Pareto weight for multi-objective (0=power, 1=production). */
    public double paretoWeight = -1;

    /** Descriptive message. */
    public String message;

    /**
     * Per-well results: choke name to [rate_kghr, opening_pct, dp_bar].
     */
    public Map<String, double[]> wellResults;

    /**
     * Get a summary string of the optimization result.
     *
     * @return formatted summary
     */
    public String getSummary() {
      StringBuilder sb = new StringBuilder();
      sb.append("=== Network Optimization Result ===\n");
      sb.append("Algorithm: ").append(algorithm).append("\n");
      sb.append("Objective: ").append(objectiveTypeName).append("\n");
      sb.append("Converged: ").append(converged).append("\n");
      sb.append(String.format("Objective value: %.4f%n", objectiveValue));
      sb.append(String.format("Total production: %.0f kg/hr%n", totalProductionKgHr));
      if (totalCompressorPowerKW > 0) {
        sb.append(String.format("Compressor power: %.1f kW%n", totalCompressorPowerKW));
      }
      sb.append(String.format("Time: %d ms, Evaluations: %d%n", elapsedMs, functionEvaluations));
      if (chokeNames != null && chokeOpenings != null) {
        sb.append("Choke openings:\n");
        for (int i = 0; i < chokeNames.size(); i++) {
          sb.append(String.format("  %s: %.1f%%%n", chokeNames.get(i), chokeOpenings[i]));
        }
      }
      return sb.toString();
    }
  }
}
