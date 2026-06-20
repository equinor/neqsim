package neqsim.process.optimization.valuechain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Evaluates the robustness of operating decisions against parameter uncertainty.
 *
 * <p>
 * A setpoint that is optimal for the nominal case can be fragile: small shifts in feed composition, reservoir pressure
 * or ambient temperature may erode its value or push the plant infeasible. This study couples a Monte-Carlo scenario
 * set with an objective evaluator to report the value distribution (P10/P50/P90, mean) and the fraction of scenarios in
 * which a decision stays feasible. It can also pick, from a set of candidate decisions, the one that maximises the
 * median value subject to a feasibility-confidence requirement — i.e. a chance-constrained selection such as "maximise
 * P50 value while remaining feasible in at least 90% of scenarios".
 * </p>
 *
 * <p>
 * Scenarios may be supplied explicitly (deterministic, reproducible) or drawn from a {@link ScenarioSampler}. Outcomes
 * are produced by a {@link ScenarioEvaluator} that, for a given decision and scenario, returns the objective and
 * whether the plant is feasible (typically by configuring and running a NeqSim flowsheet and pricing it with
 * {@link ValueChainObjective}).
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class RobustOptimizationStudy implements Serializable {

  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Explicit scenarios supplied by the caller. */
  private final List<double[]> scenarios = new ArrayList<double[]>();

  /** Optional sampler used to draw scenarios. */
  private ScenarioSampler sampler;

  /** Number of scenarios to draw from the sampler. */
  private int sampleCount = 500;

  /** Seed for the sampling random generator. */
  private long seed = 42L;

  /** Required feasibility fraction for chance-constrained selection. */
  private double requiredConfidence = 0.90;

  /**
   * Functional sampler that draws an uncertain-parameter scenario.
   */
  public interface ScenarioSampler {
    /**
     * Draws one scenario vector.
     *
     * @param rng the random generator to use
     * @return a scenario vector of uncertain parameters
     */
    double[] sample(Random rng);
  }

  /**
   * Functional evaluator returning the outcome of a decision under a scenario.
   */
  public interface ScenarioEvaluator {
    /**
     * Evaluates a decision under a scenario.
     *
     * @param decision the operating decision vector
     * @param scenario the uncertain-parameter scenario vector
     * @return the outcome (objective and feasibility) of the decision under the scenario
     */
    ScenarioOutcome evaluate(double[] decision, double[] scenario);
  }

  /**
   * Outcome of one decision under one scenario.
   */
  public static class ScenarioOutcome implements Serializable {

    /** Serialization version identifier. */
    private static final long serialVersionUID = 1000L;

    /** The objective value. */
    private final double objective;

    /** Whether the decision is feasible under the scenario. */
    private final boolean feasible;

    /**
     * Creates a scenario outcome.
     *
     * @param objective the objective value
     * @param feasible true if feasible under the scenario
     */
    public ScenarioOutcome(double objective, boolean feasible) {
      this.objective = objective;
      this.feasible = feasible;
    }

    /**
     * Gets the objective value.
     *
     * @return the objective value
     */
    public double getObjective() {
      return objective;
    }

    /**
     * Indicates whether the decision is feasible under the scenario.
     *
     * @return true if feasible
     */
    public boolean isFeasible() {
      return feasible;
    }
  }

  /**
   * Robustness summary of one decision across all scenarios.
   */
  public static class RobustResult implements Serializable {

    /** Serialization version identifier. */
    private static final long serialVersionUID = 1000L;

    /** The decision evaluated. */
    private final double[] decision;

    /** 10th percentile of the objective. */
    private final double p10;

    /** 50th percentile (median) of the objective. */
    private final double p50;

    /** 90th percentile of the objective. */
    private final double p90;

    /** Mean of the objective across feasible scenarios. */
    private final double mean;

    /** Fraction of scenarios in which the decision is feasible. */
    private final double feasibleFraction;

    /**
     * Creates a robustness result.
     *
     * @param decision the decision evaluated
     * @param p10 10th percentile of the objective
     * @param p50 50th percentile of the objective
     * @param p90 90th percentile of the objective
     * @param mean mean of the objective across feasible scenarios
     * @param feasibleFraction fraction of feasible scenarios
     */
    public RobustResult(double[] decision, double p10, double p50, double p90, double mean, double feasibleFraction) {
      this.decision = decision.clone();
      this.p10 = p10;
      this.p50 = p50;
      this.p90 = p90;
      this.mean = mean;
      this.feasibleFraction = feasibleFraction;
    }

    /**
     * Gets the decision evaluated.
     *
     * @return a copy of the decision vector
     */
    public double[] getDecision() {
      return decision.clone();
    }

    /**
     * Gets the 10th percentile of the objective.
     *
     * @return the P10 objective
     */
    public double getP10() {
      return p10;
    }

    /**
     * Gets the 50th percentile (median) of the objective.
     *
     * @return the P50 objective
     */
    public double getP50() {
      return p50;
    }

    /**
     * Gets the 90th percentile of the objective.
     *
     * @return the P90 objective
     */
    public double getP90() {
      return p90;
    }

    /**
     * Gets the mean of the objective across feasible scenarios.
     *
     * @return the mean objective
     */
    public double getMean() {
      return mean;
    }

    /**
     * Gets the fraction of scenarios in which the decision is feasible.
     *
     * @return the feasible fraction in the range [0, 1]
     */
    public double getFeasibleFraction() {
      return feasibleFraction;
    }
  }

  /**
   * Adds an explicit scenario vector.
   *
   * @param scenario the scenario vector (must not be null)
   * @return this study for method chaining
   */
  public RobustOptimizationStudy addScenario(double[] scenario) {
    if (scenario == null) {
      throw new IllegalArgumentException("scenario must not be null");
    }
    scenarios.add(scenario.clone());
    return this;
  }

  /**
   * Sets the sampler used to draw scenarios when no explicit scenarios are supplied.
   *
   * @param sampler the scenario sampler
   * @param count the number of scenarios to draw (must be positive)
   * @return this study for method chaining
   */
  public RobustOptimizationStudy setSampler(ScenarioSampler sampler, int count) {
    if (sampler == null) {
      throw new IllegalArgumentException("sampler must not be null");
    }
    if (count <= 0) {
      throw new IllegalArgumentException("count must be positive");
    }
    this.sampler = sampler;
    this.sampleCount = count;
    return this;
  }

  /**
   * Sets the random seed used for sampling.
   *
   * @param seed the random seed
   * @return this study for method chaining
   */
  public RobustOptimizationStudy setSeed(long seed) {
    this.seed = seed;
    return this;
  }

  /**
   * Sets the required feasibility fraction for chance-constrained selection.
   *
   * @param requiredConfidence the required feasible fraction in the range [0, 1]
   * @return this study for method chaining
   */
  public RobustOptimizationStudy setRequiredConfidence(double requiredConfidence) {
    if (requiredConfidence < 0.0 || requiredConfidence > 1.0) {
      throw new IllegalArgumentException("requiredConfidence must be in [0, 1]");
    }
    this.requiredConfidence = requiredConfidence;
    return this;
  }

  /**
   * Builds the scenario set, drawing from the sampler if no explicit scenarios were supplied.
   *
   * @return the list of scenario vectors to evaluate
   */
  private List<double[]> buildScenarios() {
    if (!scenarios.isEmpty()) {
      return scenarios;
    }
    if (sampler == null) {
      throw new IllegalStateException("No scenarios and no sampler configured");
    }
    List<double[]> drawn = new ArrayList<double[]>();
    Random rng = new Random(seed);
    for (int i = 0; i < sampleCount; i++) {
      drawn.add(sampler.sample(rng));
    }
    return drawn;
  }

  /**
   * Evaluates the robustness of a single decision across all scenarios.
   *
   * @param decision the operating decision vector (must not be null)
   * @param evaluator the scenario evaluator (must not be null)
   * @return the robustness summary of the decision
   */
  public RobustResult evaluateDecision(double[] decision, ScenarioEvaluator evaluator) {
    if (decision == null) {
      throw new IllegalArgumentException("decision must not be null");
    }
    if (evaluator == null) {
      throw new IllegalArgumentException("evaluator must not be null");
    }
    List<double[]> sc = buildScenarios();
    List<Double> objectives = new ArrayList<Double>();
    int feasibleCount = 0;
    double sum = 0.0;
    for (double[] scenario : sc) {
      ScenarioOutcome outcome = evaluator.evaluate(decision, scenario);
      if (outcome.isFeasible()) {
	feasibleCount++;
	objectives.add(outcome.getObjective());
	sum += outcome.getObjective();
      }
    }
    double feasibleFraction = sc.isEmpty() ? 0.0 : (double) feasibleCount / sc.size();
    if (objectives.isEmpty()) {
      return new RobustResult(decision, Double.NaN, Double.NaN, Double.NaN, Double.NaN, feasibleFraction);
    }
    double[] sorted = new double[objectives.size()];
    for (int i = 0; i < objectives.size(); i++) {
      sorted[i] = objectives.get(i);
    }
    Arrays.sort(sorted);
    double mean = sum / objectives.size();
    return new RobustResult(decision, percentile(sorted, 10.0), percentile(sorted, 50.0), percentile(sorted, 90.0),
	mean, feasibleFraction);
  }

  /**
   * Selects, from a set of candidate decisions, the one maximising the median objective while meeting the
   * feasibility-confidence requirement.
   *
   * <p>
   * If no candidate meets the confidence requirement, the candidate with the highest feasible fraction is returned as a
   * fallback.
   * </p>
   *
   * @param candidates the candidate decision vectors (must not be null or empty)
   * @param evaluator the scenario evaluator (must not be null)
   * @return the robustness summary of the selected decision
   */
  public RobustResult selectRobust(List<double[]> candidates, ScenarioEvaluator evaluator) {
    if (candidates == null || candidates.isEmpty()) {
      throw new IllegalArgumentException("candidates must not be null or empty");
    }
    RobustResult best = null;
    RobustResult mostFeasible = null;
    for (double[] decision : candidates) {
      RobustResult r = evaluateDecision(decision, evaluator);
      if (mostFeasible == null || r.getFeasibleFraction() > mostFeasible.getFeasibleFraction()) {
	mostFeasible = r;
      }
      if (r.getFeasibleFraction() >= requiredConfidence) {
	if (best == null || r.getP50() > best.getP50()) {
	  best = r;
	}
      }
    }
    return best != null ? best : mostFeasible;
  }

  /**
   * Computes a percentile of a sorted array using linear interpolation.
   *
   * @param sorted the sorted values (ascending, non-empty)
   * @param p the percentile in the range [0, 100]
   * @return the interpolated percentile value
   */
  private static double percentile(double[] sorted, double p) {
    if (sorted.length == 1) {
      return sorted[0];
    }
    double rank = (p / 100.0) * (sorted.length - 1);
    int lo = (int) Math.floor(rank);
    int hi = (int) Math.ceil(rank);
    if (lo == hi) {
      return sorted[lo];
    }
    double frac = rank - lo;
    return sorted[lo] * (1.0 - frac) + sorted[hi] * frac;
  }
}
