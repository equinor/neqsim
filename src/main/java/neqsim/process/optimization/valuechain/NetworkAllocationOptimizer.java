package neqsim.process.optimization.valuechain;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Allocates a fixed shared total across competing legs to maximise a coupled objective.
 *
 * <p>
 * Many production problems are allocation problems: split a fixed gas-lift budget across wells, apportion a plateau
 * rate across fields sharing a host, or divide duty across parallel trains that feed shared compression. A naive
 * parameter sweep scales poorly with the number of legs; this class treats allocation as a constrained optimization
 * that conserves the shared total exactly.
 * </p>
 *
 * <p>
 * The objective is supplied by a functional {@link AllocationEvaluator} that scores a candidate split (typically by
 * setting per-leg rates on a coupled {@code ProcessModel}, running it to convergence and pricing the result with
 * {@link ValueChainObjective}). The optimizer uses a transfer-based pattern search: it repeatedly moves a shrinking
 * quantity between pairs of legs, accepting any move that improves the objective while respecting per-leg bounds.
 * Because every accepted move transfers from one leg to another, the sum of the allocation is preserved at the
 * configured total throughout.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class NetworkAllocationOptimizer implements Serializable {

  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** The fixed shared total to be allocated across legs. */
  private final double total;

  /** The number of legs the total is split across. */
  private final int nLegs;

  /** Lower bound per leg. */
  private final double[] lowerBound;

  /** Upper bound per leg. */
  private final double[] upperBound;

  /** Optional caller-supplied initial allocation (null = equal split). */
  private double[] initialAllocation;

  /** Maximum number of pattern-search iterations. */
  private int maxIterations = 200;

  /** Initial transfer step as a fraction of the total. */
  private double initialStepFraction = 0.25;

  /** Relative convergence tolerance on the transfer step. */
  private double tolerance = 1e-4;

  /**
   * Functional evaluator that scores a candidate allocation.
   */
  public interface AllocationEvaluator {
    /**
     * Scores a candidate allocation.
     *
     * @param allocation the per-leg allocation (sums to the configured total)
     * @return the score of the candidate allocation
     */
    AllocationResult evaluate(double[] allocation);
  }

  /**
   * Result of scoring or optimizing an allocation.
   */
  public static class AllocationResult implements Serializable {

    /** Serialization version identifier. */
    private static final long serialVersionUID = 1000L;

    /** The per-leg allocation. */
    private final double[] allocation;

    /** The objective value (higher is better). */
    private final double objective;

    /** Whether the allocation is feasible. */
    private final boolean feasible;

    /**
     * Creates an allocation result.
     *
     * @param allocation the per-leg allocation
     * @param objective the objective value (higher is better)
     * @param feasible true if the allocation is feasible
     */
    public AllocationResult(double[] allocation, double objective, boolean feasible) {
      this.allocation = allocation.clone();
      this.objective = objective;
      this.feasible = feasible;
    }

    /**
     * Gets the per-leg allocation.
     *
     * @return a copy of the allocation array
     */
    public double[] getAllocation() {
      return allocation.clone();
    }

    /**
     * Gets the objective value.
     *
     * @return the objective value (higher is better)
     */
    public double getObjective() {
      return objective;
    }

    /**
     * Indicates whether the allocation is feasible.
     *
     * @return true if feasible
     */
    public boolean isFeasible() {
      return feasible;
    }
  }

  /**
   * Creates a network allocation optimizer with default per-leg bounds of {@code [0, total]}.
   *
   * @param total the fixed shared total to allocate (must be positive)
   * @param nLegs the number of legs (must be at least 2)
   */
  public NetworkAllocationOptimizer(double total, int nLegs) {
    if (total <= 0.0) {
      throw new IllegalArgumentException("total must be positive");
    }
    if (nLegs < 2) {
      throw new IllegalArgumentException("nLegs must be at least 2");
    }
    this.total = total;
    this.nLegs = nLegs;
    this.lowerBound = new double[nLegs];
    this.upperBound = new double[nLegs];
    Arrays.fill(this.lowerBound, 0.0);
    Arrays.fill(this.upperBound, total);
  }

  /**
   * Sets the lower and upper bound for one leg.
   *
   * @param leg the leg index (0-based)
   * @param lower the lower bound (non-negative)
   * @param upper the upper bound (greater than or equal to the lower bound)
   * @return this optimizer for method chaining
   */
  public NetworkAllocationOptimizer setBounds(int leg, double lower, double upper) {
    if (leg < 0 || leg >= nLegs) {
      throw new IllegalArgumentException("leg index out of range: " + leg);
    }
    if (upper < lower) {
      throw new IllegalArgumentException("upper bound must be >= lower bound");
    }
    this.lowerBound[leg] = lower;
    this.upperBound[leg] = upper;
    return this;
  }

  /**
   * Sets the initial allocation used to seed the search.
   *
   * @param allocation the initial per-leg allocation (length must equal the leg count)
   * @return this optimizer for method chaining
   */
  public NetworkAllocationOptimizer setInitialAllocation(double[] allocation) {
    if (allocation == null || allocation.length != nLegs) {
      throw new IllegalArgumentException("initial allocation length must equal the leg count");
    }
    this.initialAllocation = allocation.clone();
    return this;
  }

  /**
   * Sets the maximum number of pattern-search iterations.
   *
   * @param maxIterations the maximum number of iterations (must be positive)
   * @return this optimizer for method chaining
   */
  public NetworkAllocationOptimizer setMaxIterations(int maxIterations) {
    if (maxIterations <= 0) {
      throw new IllegalArgumentException("maxIterations must be positive");
    }
    this.maxIterations = maxIterations;
    return this;
  }

  /**
   * Sets the initial transfer step as a fraction of the total.
   *
   * @param fraction the initial step fraction in the range (0, 1]
   * @return this optimizer for method chaining
   */
  public NetworkAllocationOptimizer setInitialStepFraction(double fraction) {
    if (fraction <= 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException("step fraction must be in (0, 1]");
    }
    this.initialStepFraction = fraction;
    return this;
  }

  /**
   * Sets the relative convergence tolerance on the transfer step.
   *
   * @param tolerance the relative tolerance (must be positive)
   * @return this optimizer for method chaining
   */
  public NetworkAllocationOptimizer setTolerance(double tolerance) {
    if (tolerance <= 0.0) {
      throw new IllegalArgumentException("tolerance must be positive");
    }
    this.tolerance = tolerance;
    return this;
  }

  /**
   * Optimizes the allocation of the shared total to maximise the evaluator's objective.
   *
   * @param evaluator the functional evaluator scoring candidate allocations (must not be null)
   * @return the best feasible allocation found and its objective value
   */
  public AllocationResult optimize(AllocationEvaluator evaluator) {
    if (evaluator == null) {
      throw new IllegalArgumentException("Evaluator must not be null");
    }
    double[] alloc = seedAllocation();
    AllocationResult current = evaluator.evaluate(alloc);
    double bestObj = current.isFeasible() ? current.getObjective() : Double.NEGATIVE_INFINITY;
    double[] best = alloc.clone();
    boolean haveFeasible = current.isFeasible();

    double step = initialStepFraction * total;
    int iter = 0;
    while (step > tolerance * total && iter < maxIterations) {
      boolean improved = false;
      for (int i = 0; i < nLegs; i++) {
        for (int j = 0; j < nLegs; j++) {
          if (i == j) {
            continue;
          }
          if (best[i] + step > upperBound[i] || best[j] - step < lowerBound[j]) {
            continue;
          }
          double[] trial = best.clone();
          trial[i] += step;
          trial[j] -= step;
          AllocationResult r = evaluator.evaluate(trial);
          iter++;
          if (r.isFeasible() && (!haveFeasible || r.getObjective() > bestObj + 1e-12)) {
            best = trial;
            bestObj = r.getObjective();
            haveFeasible = true;
            improved = true;
          }
          if (iter >= maxIterations) {
            break;
          }
        }
        if (iter >= maxIterations) {
          break;
        }
      }
      if (!improved) {
        step *= 0.5;
      }
    }
    return new AllocationResult(best, bestObj, haveFeasible);
  }

  /**
   * Builds the seed allocation, honouring bounds and the configured total.
   *
   * @return a feasible-as-possible initial allocation summing to the total
   */
  private double[] seedAllocation() {
    double[] alloc;
    if (initialAllocation != null) {
      alloc = initialAllocation.clone();
    } else {
      alloc = new double[nLegs];
      Arrays.fill(alloc, total / nLegs);
    }
    // Clamp to bounds then re-normalise to preserve the total.
    for (int i = 0; i < nLegs; i++) {
      alloc[i] = Math.max(lowerBound[i], Math.min(upperBound[i], alloc[i]));
    }
    double sum = 0.0;
    for (int i = 0; i < nLegs; i++) {
      sum += alloc[i];
    }
    double diff = total - sum;
    // Distribute the residual across legs that still have headroom in the needed direction.
    if (Math.abs(diff) > 1e-12) {
      for (int i = 0; i < nLegs && Math.abs(diff) > 1e-12; i++) {
        if (diff > 0) {
          double headroom = upperBound[i] - alloc[i];
          double add = Math.min(headroom, diff);
          alloc[i] += add;
          diff -= add;
        } else {
          double headroom = alloc[i] - lowerBound[i];
          double sub = Math.min(headroom, -diff);
          alloc[i] -= sub;
          diff += sub;
        }
      }
    }
    return alloc;
  }
}
