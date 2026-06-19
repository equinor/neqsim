package neqsim.process.automation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Closed-loop, derivative-free optimizer that drives a process simulation through the string-addressable
 * {@link ProcessAutomation#evaluate(Map, String, java.util.List)} primitive.
 *
 * <p>
 * This class is purpose-built for <strong>machine-learning and agentic workflows</strong>. Unlike the classic optimizer
 * classes in {@code neqsim.process.util.optimizer} (which take a {@code Function<double[], Double>} over an opaque
 * {@link neqsim.process.processmodel.ProcessSystem ProcessSystem}), {@code AgenticProcessOptimizer} works entirely in
 * terms of:
 * </p>
 * <ul>
 * <li><strong>Stable string addresses</strong> &mdash; decision variables, the objective, and constraints are all named
 * with dot-notation paths (or area-qualified paths for a {@link neqsim.process.processmodel.ProcessModel
 * ProcessModel}). No Java object navigation is required, so an LLM agent can construct a problem purely from
 * {@link ProcessAutomation#getAdjustableParametersJson()} output.</li>
 * <li><strong>A never-throwing, schema-versioned JSON contract</strong> &mdash; every trial is one gated
 * {@code evaluate()} call (apply setpoints &rarr; run to convergence &rarr; gate feasibility &rarr; read back
 * objectives). A malformed candidate degrades a single trial instead of crashing the loop, and {@link #optimize()}
 * itself never throws.</li>
 * <li><strong>A full, replayable trajectory</strong> &mdash; every evaluated point is logged with its setpoints,
 * read-backs, raw objective, penalty, feasibility, and minimized score. This is the (state, action, reward) tape that
 * offline reinforcement learning, surrogate-model fitting, and agent post-mortems consume.</li>
 * </ul>
 *
 * <p>
 * <strong>Algorithm.</strong> A bounded Nelder&ndash;Mead simplex search with deterministic random seeding. A process
 * flowsheet exposed through {@code evaluate()} is a noisy, feasibility-gated black box with no usable analytic
 * gradient, so a derivative-free method is the appropriate choice. Hard constraints are folded into the scalar score as
 * quadratic penalties, and any infeasible run (failed convergence or a failed unit) is pushed to the back of the
 * ordering with a large additive penalty while still being logged.
 * </p>
 *
 * <p>
 * <strong>ML / agentic readiness.</strong> Call {@link #getReadinessJson()} for a machine-readable self-assessment of
 * how this optimizer fits ML and agentic pipelines (deterministic seeding, never-throw guarantee, bounded action space,
 * reward shaping, trajectory logging, JSON I/O).
 * </p>
 *
 * <p>
 * <strong>Example.</strong>
 * </p>
 *
 * <pre>
 * ProcessAutomation auto = plant.getAutomation();
 * AgenticProcessOptimizer opt = auto.newOptimizer();
 * opt.addVariable("Separation::Oil Heater.outletTemperature", 45.0, 95.0, "C");
 * opt.minimize("Compression::Export Compressor.power", "kW");
 * opt.addConstraintLessOrEqual("Export Oil.RVP", 0.90, "bara", 1.0e4);
 * opt.setSeed(42).setMaxEvaluations(80);
 * AgenticProcessOptimizer.OptimizationResult result = opt.optimize();
 * String json = result.toJson();
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class AgenticProcessOptimizer implements Serializable {

  /** Serialization id. */
  private static final long serialVersionUID = 1L;

  /** Stable schema version for JSON produced by this optimizer. */
  public static final String SCHEMA_VERSION = "1.0";

  /** Additive score penalty applied to any infeasible trial (failed run or failed unit). */
  private static final double INFEASIBLE_PENALTY = 1.0e9;

  /** Additive score penalty applied when an objective or constraint read-back is unavailable. */
  private static final double MISSING_READBACK_PENALTY = 1.0e8;

  /** Nelder&ndash;Mead reflection coefficient. */
  private static final double ALPHA = 1.0;

  /** Nelder&ndash;Mead expansion coefficient. */
  private static final double GAMMA = 2.0;

  /** Nelder&ndash;Mead contraction coefficient. */
  private static final double RHO = 0.5;

  /** Nelder&ndash;Mead shrink coefficient. */
  private static final double SIGMA = 0.5;

  /** Optimization sense for an address-based objective. */
  public enum Sense {
    /** Minimize the objective read-back. */
    MINIMIZE,
    /** Maximize the objective read-back. */
    MAXIMIZE
  }

  /** Comparison direction for a hard inequality constraint. */
  public enum ConstraintType {
    /** Constrain the read-back to be less than or equal to the limit. */
    LESS_OR_EQUAL,
    /** Constrain the read-back to be greater than or equal to the limit. */
    GREATER_OR_EQUAL
  }

  /** The automation facade that backs every trial. */
  private final transient ProcessAutomation automation;

  /** Decision variables (degrees of freedom) in registration order. */
  private final List<DecisionVariable> variables = new ArrayList<DecisionVariable>();

  /** Hard inequality constraints folded into the score as penalties. */
  private final List<Constraint> constraints = new ArrayList<Constraint>();

  /** Extra addresses (with units) read every trial and exposed to custom objective functions. */
  private final Map<String, String> watches = new LinkedHashMap<String, String>();

  /** Address of the objective read-back (mode A); {@code null} when a custom function is used. */
  private String objectiveAddress = null;

  /** Unit for the objective read-back. */
  private String objectiveUnit = null;

  /** Sense applied to the objective read-back. */
  private Sense objectiveSense = Sense.MINIMIZE;

  /**
   * Custom objective function (mode B): receives a map of {@code address -> value} (decision variables, constraint
   * read-backs and watches) and returns a scalar cost to MINIMIZE. When set, it overrides the address-based objective.
   */
  private transient Function<Map<String, Double>, Double> objectiveFunction = null;

  /** Approximate maximum number of {@code evaluate()} calls. */
  private int maxEvaluations = 100;

  /** Inner convergence iteration cap passed to {@code evaluate()}. */
  private int innerMaxIterations = 30;

  /** Inner convergence tolerance passed to {@code evaluate()}. */
  private double innerTolerance = 5.0e-3;

  /** Relative simplex-size tolerance used to declare convergence of the outer search. */
  private double convergenceTolerance = 1.0e-3;

  /** Seed for the deterministic random number generator used for seeding the simplex. */
  private long seed = 42L;

  /** Running evaluation counter. */
  private transient int evaluations = 0;

  /** Best feasible trial found so far (or null if none feasible). */
  private transient Trial bestFeasible = null;

  /** Best (lowest-score) trial found so far regardless of feasibility. */
  private transient Trial bestOverall = null;

  /** Recorded trajectory of every evaluated point. */
  private transient List<Trial> trajectory = new ArrayList<Trial>();

  /**
   * Creates an optimizer bound to an automation facade.
   *
   * @param automation the automation facade wrapping the process to optimize; must not be null
   * @throws IllegalArgumentException if {@code automation} is null
   */
  public AgenticProcessOptimizer(ProcessAutomation automation) {
    if (automation == null) {
      throw new IllegalArgumentException("automation must not be null");
    }
    this.automation = automation;
  }

  /**
   * Adds a bounded decision variable (degree of freedom).
   *
   * @param address dot-notation address of a writable input variable
   * @param lowerBound inclusive lower bound, in {@code unit}
   * @param upperBound inclusive upper bound, in {@code unit}; must be strictly greater than {@code lowerBound}
   * @param unit unit applied when writing this variable, or null for the variable's default unit
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code address} is null/empty or {@code upperBound <= lowerBound} or either
   * bound is non-finite
   */
  public AgenticProcessOptimizer addVariable(String address, double lowerBound, double upperBound, String unit) {
    if (address == null || address.trim().isEmpty()) {
      throw new IllegalArgumentException("variable address must not be null or empty");
    }
    if (Double.isNaN(lowerBound) || Double.isInfinite(lowerBound) || Double.isNaN(upperBound)
	|| Double.isInfinite(upperBound)) {
      throw new IllegalArgumentException("variable bounds must be finite");
    }
    if (upperBound <= lowerBound) {
      throw new IllegalArgumentException(
	  "upperBound (" + upperBound + ") must be greater than lowerBound (" + lowerBound + ")");
    }
    variables.add(new DecisionVariable(address, lowerBound, upperBound, unit));
    return this;
  }

  /**
   * Adds every bounded adjustable parameter reported by {@link ProcessAutomation#getAdjustableParameters()} as a
   * decision variable. Parameters without both a finite lower and upper bound are skipped (an unbounded decision space
   * cannot be searched by simplex). This is the one-call way for an agent to populate the action space.
   *
   * @return the number of decision variables added
   */
  public int useAdjustableParameters() {
    int added = 0;
    for (AdjustableParameter p : automation.getAdjustableParameters()) {
      Double lo = p.getLowerBound();
      Double hi = p.getUpperBound();
      if (lo == null || hi == null || hi.doubleValue() <= lo.doubleValue()) {
	continue;
      }
      variables.add(new DecisionVariable(p.getAddress(), lo.doubleValue(), hi.doubleValue(), p.getUnit()));
      added++;
    }
    return added;
  }

  /**
   * Sets an address-based objective to minimize.
   *
   * @param address dot-notation address of the objective read-back
   * @param unit unit for the objective read-back, or null for the default unit
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code address} is null or empty
   */
  public AgenticProcessOptimizer minimize(String address, String unit) {
    return setObjective(address, Sense.MINIMIZE, unit);
  }

  /**
   * Sets an address-based objective to maximize.
   *
   * @param address dot-notation address of the objective read-back
   * @param unit unit for the objective read-back, or null for the default unit
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code address} is null or empty
   */
  public AgenticProcessOptimizer maximize(String address, String unit) {
    return setObjective(address, Sense.MAXIMIZE, unit);
  }

  /**
   * Sets an address-based objective with an explicit sense.
   *
   * @param address dot-notation address of the objective read-back
   * @param sense whether to minimize or maximize; must not be null
   * @param unit unit for the objective read-back, or null for the default unit
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code address} is null/empty or {@code sense} is null
   */
  public AgenticProcessOptimizer setObjective(String address, Sense sense, String unit) {
    if (address == null || address.trim().isEmpty()) {
      throw new IllegalArgumentException("objective address must not be null or empty");
    }
    if (sense == null) {
      throw new IllegalArgumentException("sense must not be null");
    }
    this.objectiveAddress = address;
    this.objectiveSense = sense;
    this.objectiveUnit = unit;
    this.objectiveFunction = null;
    return this;
  }

  /**
   * Sets a custom objective function (ML reward shaping). The function receives a map of {@code address -> value}
   * containing every decision variable (by address), every constraint read-back, and every watch read-back, and returns
   * a scalar <em>cost to minimize</em>. When set, this overrides any address-based objective. Constraint penalties are
   * still added on top of the returned cost.
   *
   * @param objectiveFunction the cost function to minimize; must not be null
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code objectiveFunction} is null
   */
  public AgenticProcessOptimizer setObjectiveFunction(Function<Map<String, Double>, Double> objectiveFunction) {
    if (objectiveFunction == null) {
      throw new IllegalArgumentException("objectiveFunction must not be null");
    }
    this.objectiveFunction = objectiveFunction;
    this.objectiveAddress = null;
    return this;
  }

  /**
   * Adds a {@code value <= limit} hard constraint folded into the score as a quadratic penalty.
   *
   * @param address dot-notation address of the constrained read-back
   * @param limit upper limit, in {@code unit}
   * @param unit unit for the read-back, or null for the default unit
   * @param penaltyWeight non-negative weight multiplying the squared violation
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code address} is null/empty, {@code limit} is non-finite, or
   * {@code penaltyWeight} is negative
   */
  public AgenticProcessOptimizer addConstraintLessOrEqual(String address, double limit, String unit,
      double penaltyWeight) {
    return addConstraint(address, ConstraintType.LESS_OR_EQUAL, limit, unit, penaltyWeight);
  }

  /**
   * Adds a {@code value >= limit} hard constraint folded into the score as a quadratic penalty.
   *
   * @param address dot-notation address of the constrained read-back
   * @param limit lower limit, in {@code unit}
   * @param unit unit for the read-back, or null for the default unit
   * @param penaltyWeight non-negative weight multiplying the squared violation
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code address} is null/empty, {@code limit} is non-finite, or
   * {@code penaltyWeight} is negative
   */
  public AgenticProcessOptimizer addConstraintGreaterOrEqual(String address, double limit, String unit,
      double penaltyWeight) {
    return addConstraint(address, ConstraintType.GREATER_OR_EQUAL, limit, unit, penaltyWeight);
  }

  /**
   * Adds a hard inequality constraint folded into the score as a quadratic penalty.
   *
   * @param address dot-notation address of the constrained read-back
   * @param type comparison direction; must not be null
   * @param limit the limit value, in {@code unit}
   * @param unit unit for the read-back, or null for the default unit
   * @param penaltyWeight non-negative weight multiplying the squared violation
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code address} is null/empty, {@code type} is null, {@code limit} is
   * non-finite, or {@code penaltyWeight} is negative
   */
  public AgenticProcessOptimizer addConstraint(String address, ConstraintType type, double limit, String unit,
      double penaltyWeight) {
    if (address == null || address.trim().isEmpty()) {
      throw new IllegalArgumentException("constraint address must not be null or empty");
    }
    if (type == null) {
      throw new IllegalArgumentException("constraint type must not be null");
    }
    if (Double.isNaN(limit) || Double.isInfinite(limit)) {
      throw new IllegalArgumentException("constraint limit must be finite");
    }
    if (penaltyWeight < 0.0 || Double.isNaN(penaltyWeight)) {
      throw new IllegalArgumentException("penaltyWeight must be non-negative");
    }
    constraints.add(new Constraint(address, type, limit, unit, penaltyWeight));
    return this;
  }

  /**
   * Registers an additional address to read every trial and expose to a custom objective function. Watches do not
   * affect the score on their own; they are convenience inputs for {@link #setObjectiveFunction(Function)} reward
   * shaping.
   *
   * @param address dot-notation address to read each trial
   * @param unit unit for the read-back, or null for the default unit
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code address} is null or empty
   */
  public AgenticProcessOptimizer addWatch(String address, String unit) {
    if (address == null || address.trim().isEmpty()) {
      throw new IllegalArgumentException("watch address must not be null or empty");
    }
    watches.put(address, unit);
    return this;
  }

  /**
   * Sets the approximate maximum number of {@code evaluate()} calls (objective evaluations). The search may overrun
   * this cap by at most one Nelder&ndash;Mead iteration.
   *
   * @param maxEvaluations the evaluation budget; must be at least 2
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code maxEvaluations < 2}
   */
  public AgenticProcessOptimizer setMaxEvaluations(int maxEvaluations) {
    if (maxEvaluations < 2) {
      throw new IllegalArgumentException("maxEvaluations must be at least 2");
    }
    this.maxEvaluations = maxEvaluations;
    return this;
  }

  /**
   * Sets the inner convergence settings passed to every {@code evaluate()} call.
   *
   * @param innerMaxIterations maximum outer iterations for the convergence run; must be at least 1
   * @param innerTolerance relative convergence tolerance; must be finite and positive
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code innerMaxIterations < 1} or {@code innerTolerance} is not finite and
   * positive
   */
  public AgenticProcessOptimizer setInnerConvergence(int innerMaxIterations, double innerTolerance) {
    if (innerMaxIterations < 1) {
      throw new IllegalArgumentException("innerMaxIterations must be at least 1");
    }
    if (Double.isNaN(innerTolerance) || Double.isInfinite(innerTolerance) || innerTolerance <= 0.0) {
      throw new IllegalArgumentException("innerTolerance must be a finite positive number");
    }
    this.innerMaxIterations = innerMaxIterations;
    this.innerTolerance = innerTolerance;
    return this;
  }

  /**
   * Sets the relative simplex-size tolerance used to declare convergence of the outer search.
   *
   * @param convergenceTolerance relative tolerance; must be finite and positive
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code convergenceTolerance} is not finite and positive
   */
  public AgenticProcessOptimizer setConvergenceTolerance(double convergenceTolerance) {
    if (Double.isNaN(convergenceTolerance) || Double.isInfinite(convergenceTolerance) || convergenceTolerance <= 0.0) {
      throw new IllegalArgumentException("convergenceTolerance must be a finite positive number");
    }
    this.convergenceTolerance = convergenceTolerance;
    return this;
  }

  /**
   * Sets the seed for the deterministic random number generator used to seed the simplex. Equal seeds with equal
   * problem definitions yield identical trajectories, which is essential for reproducible ML experiments.
   *
   * @param seed the random seed
   * @return this optimizer, for chaining
   */
  public AgenticProcessOptimizer setSeed(long seed) {
    this.seed = seed;
    return this;
  }

  /**
   * Runs the closed-loop optimization. This method never throws &mdash; any unexpected error is captured in the
   * returned result's message and {@code success} flag.
   *
   * @return the optimization result, including the best point and the full trajectory
   */
  public OptimizationResult optimize() {
    this.evaluations = 0;
    this.bestFeasible = null;
    this.bestOverall = null;
    this.trajectory = new ArrayList<Trial>();

    OptimizationResult result = new OptimizationResult();
    if (variables.isEmpty()) {
      result.success = false;
      result.message = "no decision variables defined";
      return result;
    }
    if (objectiveFunction == null && objectiveAddress == null) {
      result.success = false;
      result.message = "no objective defined (call minimize/maximize or setObjectiveFunction)";
      return result;
    }

    try {
      runNelderMead();
    } catch (RuntimeException ex) {
      result.success = false;
      result.message = "optimizer aborted: " + ex.getMessage();
      result.evaluations = evaluations;
      result.trajectory = trajectory;
      return result;
    }

    Trial best = bestFeasible != null ? bestFeasible : bestOverall;
    result.evaluations = evaluations;
    result.trajectory = trajectory;
    result.feasible = bestFeasible != null;
    if (best == null) {
      result.success = false;
      result.message = "no point was successfully evaluated";
      return result;
    }
    result.success = true;
    result.message = bestFeasible != null ? "converged to a feasible optimum"
	: "no feasible point found; returning least-penalized point";
    result.bestSetpoints = best.setpoints;
    result.bestObjective = best.objective;
    result.bestScore = best.score;
    result.bestReadbacks = best.readbacks;
    return result;
  }

  /**
   * Runs the optimization and returns the result as schema-versioned JSON. Never throws.
   *
   * @return JSON produced by {@link OptimizationResult#toJson()}
   */
  public String optimizeToJson() {
    return optimize().toJson();
  }

  /**
   * Executes the bounded Nelder&ndash;Mead search. Mutates {@link #bestFeasible}, {@link #bestOverall},
   * {@link #trajectory} and {@link #evaluations}.
   */
  private void runNelderMead() {
    int n = variables.size();
    double[] lo = new double[n];
    double[] hi = new double[n];
    for (int i = 0; i < n; i++) {
      lo[i] = variables.get(i).lowerBound;
      hi[i] = variables.get(i).upperBound;
    }

    Random rng = new Random(seed);

    // Random seeding: sample a fraction of the budget, keep the best start.
    int nSeeds = Math.max(1, Math.min(2 * n + 1, maxEvaluations / 3));
    double[] start = new double[n];
    for (int i = 0; i < n; i++) {
      start[i] = 0.5 * (lo[i] + hi[i]);
    }
    double startScore = score(start);
    for (int s = 1; s < nSeeds && evaluations < maxEvaluations; s++) {
      double[] cand = new double[n];
      for (int i = 0; i < n; i++) {
	cand[i] = lo[i] + rng.nextDouble() * (hi[i] - lo[i]);
      }
      double candScore = score(cand);
      if (candScore < startScore) {
	startScore = candScore;
	start = cand;
      }
    }

    // Build the initial simplex (n + 1 vertices).
    double[][] simplex = new double[n + 1][n];
    double[] fvals = new double[n + 1];
    simplex[0] = start.clone();
    fvals[0] = startScore;
    for (int i = 0; i < n; i++) {
      double[] v = start.clone();
      double range = hi[i] - lo[i];
      double step = 0.05 * range;
      if (v[i] + step <= hi[i]) {
	v[i] = v[i] + step;
      } else {
	v[i] = v[i] - step;
      }
      clamp(v, lo, hi);
      simplex[i + 1] = v;
      fvals[i + 1] = evaluations < maxEvaluations ? score(v) : startScore;
    }

    // Outer Nelder-Mead loop.
    while (evaluations < maxEvaluations) {
      sortSimplex(simplex, fvals);

      if (simplexSize(simplex, lo, hi) < convergenceTolerance) {
	break;
      }

      double[] centroid = centroidExcludingWorst(simplex);
      double[] worst = simplex[n];
      double fWorst = fvals[n];
      double fSecondWorst = fvals[n - 1];
      double fBest = fvals[0];

      // Reflection
      double[] xr = combine(centroid, worst, ALPHA);
      clamp(xr, lo, hi);
      double fr = score(xr);

      if (fr < fBest) {
	// Expansion
	if (evaluations < maxEvaluations) {
	  double[] xe = combine(centroid, xr, GAMMA - 1.0);
	  clamp(xe, lo, hi);
	  double fe = score(xe);
	  if (fe < fr) {
	    simplex[n] = xe;
	    fvals[n] = fe;
	  } else {
	    simplex[n] = xr;
	    fvals[n] = fr;
	  }
	} else {
	  simplex[n] = xr;
	  fvals[n] = fr;
	}
      } else if (fr < fSecondWorst) {
	simplex[n] = xr;
	fvals[n] = fr;
      } else {
	// Contraction toward the better of (worst, reflection)
	boolean outside = fr < fWorst;
	double[] target = outside ? xr : worst;
	double[] xc = combine(centroid, target, RHO);
	clamp(xc, lo, hi);
	double fc = evaluations < maxEvaluations ? score(xc) : fWorst;
	double fCompare = outside ? fr : fWorst;
	if (fc < fCompare) {
	  simplex[n] = xc;
	  fvals[n] = fc;
	} else {
	  // Shrink toward the best vertex.
	  double[] xBest = simplex[0];
	  for (int i = 1; i <= n && evaluations < maxEvaluations; i++) {
	    double[] v = new double[n];
	    for (int j = 0; j < n; j++) {
	      v[j] = xBest[j] + SIGMA * (simplex[i][j] - xBest[j]);
	    }
	    clamp(v, lo, hi);
	    simplex[i] = v;
	    fvals[i] = score(v);
	  }
	}
      }
    }
  }

  /**
   * Evaluates one candidate point: applies setpoints, runs the gated convergence step, reads back the objective and
   * constraints, computes the minimized score, logs a {@link Trial}, and updates the best-feasible / best-overall
   * trackers. Never throws.
   *
   * @param x the candidate decision-variable vector (assumed already within bounds)
   * @return the minimized scalar score for {@code x}
   */
  private double score(double[] x) {
    evaluations++;
    Trial trial = new Trial();
    trial.index = evaluations;

    // 1) Apply decision variables, each in its own unit; track rejections.
    boolean anyRejected = false;
    Map<String, Double> decisionMap = new LinkedHashMap<String, Double>();
    for (int i = 0; i < variables.size(); i++) {
      DecisionVariable dv = variables.get(i);
      trial.setpoints.put(dv.address, x[i]);
      decisionMap.put(dv.address, x[i]);
      try {
	automation.setVariableValue(dv.address, x[i], dv.unit);
      } catch (RuntimeException ex) {
	anyRejected = true;
	trial.rejected.put(dv.address, ex.getMessage());
      }
    }

    // 2) Gated run (apply no extra setpoints, no bulk read-back here).
    boolean runFeasible;
    try {
      String runJson = automation.evaluate(EMPTY_SETPOINTS, null, EMPTY_READBACKS, null, innerMaxIterations,
	  innerTolerance);
      JsonObject root = JsonParser.parseString(runJson).getAsJsonObject();
      runFeasible = root.has("feasible") && root.get("feasible").getAsBoolean();
      if (root.has("iterations") && !root.get("iterations").isJsonNull()) {
	trial.iterations = root.get("iterations").getAsInt();
      }
    } catch (RuntimeException ex) {
      runFeasible = false;
      trial.rejected.put("__run__", ex.getMessage());
    }
    trial.runFeasible = runFeasible && !anyRejected;

    // 3) Read objective and constraint values, each in its own unit.
    double penalty = 0.0;
    boolean missingReadback = false;
    boolean constraintsOk = true;

    Map<String, Double> readMap = new LinkedHashMap<String, Double>(decisionMap);

    for (Constraint c : constraints) {
      Double v = readSafe(c.address, c.unit);
      if (v == null) {
	missingReadback = true;
	constraintsOk = false;
	penalty += MISSING_READBACK_PENALTY;
	continue;
      }
      trial.readbacks.put(c.address, v);
      readMap.put(c.address, v);
      double violation = c.violation(v.doubleValue());
      if (violation > 0.0) {
	constraintsOk = false;
	penalty += c.penaltyWeight * violation * violation;
      }
    }

    for (Map.Entry<String, String> w : watches.entrySet()) {
      Double v = readSafe(w.getKey(), w.getValue());
      if (v != null) {
	trial.readbacks.put(w.getKey(), v);
	readMap.put(w.getKey(), v);
      }
    }

    // 4) Objective.
    double objective;
    double signedObjective;
    if (objectiveFunction != null) {
      Double fnVal;
      try {
	fnVal = objectiveFunction.apply(readMap);
      } catch (RuntimeException ex) {
	fnVal = null;
      }
      if (fnVal == null || fnVal.isNaN() || fnVal.isInfinite()) {
	objective = Double.NaN;
	signedObjective = MISSING_READBACK_PENALTY;
	missingReadback = true;
      } else {
	objective = fnVal.doubleValue();
	signedObjective = objective;
      }
    } else {
      Double v = readSafe(objectiveAddress, objectiveUnit);
      if (v == null) {
	objective = Double.NaN;
	signedObjective = MISSING_READBACK_PENALTY;
	missingReadback = true;
      } else {
	trial.readbacks.put(objectiveAddress, v);
	objective = v.doubleValue();
	signedObjective = objectiveSense == Sense.MAXIMIZE ? -objective : objective;
      }
    }

    double score = signedObjective + penalty;
    if (!trial.runFeasible) {
      score += INFEASIBLE_PENALTY;
    }
    if (missingReadback) {
      score += MISSING_READBACK_PENALTY;
    }

    trial.objective = objective;
    trial.penalty = penalty;
    trial.score = score;
    trial.feasible = trial.runFeasible && constraintsOk && !missingReadback;
    trajectory.add(trial);

    if (bestOverall == null || trial.score < bestOverall.score) {
      bestOverall = trial;
    }
    if (trial.feasible
	&& (bestFeasible == null || trial.objective < bestFeasible.objective || (objectiveSense == Sense.MAXIMIZE
	    && objectiveFunction == null && trial.objective > bestFeasible.objective))) {
      // For MAXIMIZE the lower score already corresponds to higher objective, so compare scores.
      if (bestFeasible == null || trial.score < bestFeasible.score) {
	bestFeasible = trial;
      }
    }
    return score;
  }

  /**
   * Reads a variable, returning {@code null} instead of throwing on any failure.
   *
   * @param address the address to read
   * @param unit the unit, or null for the default
   * @return the value, or null if the read failed or was non-finite
   */
  private Double readSafe(String address, String unit) {
    try {
      double v = automation.getVariableValue(address, unit);
      if (Double.isNaN(v) || Double.isInfinite(v)) {
	return null;
      }
      return Double.valueOf(v);
    } catch (RuntimeException ex) {
      return null;
    }
  }

  /** Empty setpoint map reused by {@link #score(double[])}. */
  private static final Map<String, Double> EMPTY_SETPOINTS = java.util.Collections
      .unmodifiableMap(new LinkedHashMap<String, Double>());

  /** Empty read-back list reused by {@link #score(double[])}. */
  private static final List<String> EMPTY_READBACKS = java.util.Collections.unmodifiableList(new ArrayList<String>());

  /**
   * Clamps each component of {@code x} to its bounds in place.
   *
   * @param x the vector to clamp
   * @param lo lower bounds
   * @param hi upper bounds
   */
  private static void clamp(double[] x, double[] lo, double[] hi) {
    for (int i = 0; i < x.length; i++) {
      if (x[i] < lo[i]) {
	x[i] = lo[i];
      } else if (x[i] > hi[i]) {
	x[i] = hi[i];
      }
    }
  }

  /**
   * Returns {@code base + coeff * (base - other)} component-wise (reflection/contraction helper).
   *
   * @param base the base vector (typically the centroid)
   * @param other the vector reflected/contracted relative to {@code base}
   * @param coeff the coefficient
   * @return a new combined vector
   */
  private static double[] combine(double[] base, double[] other, double coeff) {
    double[] out = new double[base.length];
    for (int i = 0; i < base.length; i++) {
      out[i] = base[i] + coeff * (base[i] - other[i]);
    }
    return out;
  }

  /**
   * Sorts simplex vertices (and their scores) in ascending score order using a simple insertion sort, which is adequate
   * for the small {@code n + 1} vertex count.
   *
   * @param simplex the simplex vertices, mutated in place
   * @param fvals the matching scores, mutated in place
   */
  private static void sortSimplex(double[][] simplex, double[] fvals) {
    for (int i = 1; i < fvals.length; i++) {
      double key = fvals[i];
      double[] keyV = simplex[i];
      int j = i - 1;
      while (j >= 0 && fvals[j] > key) {
	fvals[j + 1] = fvals[j];
	simplex[j + 1] = simplex[j];
	j--;
      }
      fvals[j + 1] = key;
      simplex[j + 1] = keyV;
    }
  }

  /**
   * Computes the centroid of all simplex vertices except the worst (last) one.
   *
   * @param simplex the sorted simplex
   * @return the centroid vector
   */
  private static double[] centroidExcludingWorst(double[][] simplex) {
    int n = simplex[0].length;
    double[] c = new double[n];
    for (int i = 0; i < simplex.length - 1; i++) {
      for (int j = 0; j < n; j++) {
	c[j] += simplex[i][j];
      }
    }
    for (int j = 0; j < n; j++) {
      c[j] /= (simplex.length - 1);
    }
    return c;
  }

  /**
   * Computes a relative measure of simplex size: the maximum per-dimension vertex spread divided by the per-dimension
   * bound range, maximized over dimensions.
   *
   * @param simplex the simplex vertices
   * @param lo lower bounds
   * @param hi upper bounds
   * @return the relative simplex size
   */
  private static double simplexSize(double[][] simplex, double[] lo, double[] hi) {
    int n = simplex[0].length;
    double maxRel = 0.0;
    for (int j = 0; j < n; j++) {
      double min = simplex[0][j];
      double max = simplex[0][j];
      for (int i = 1; i < simplex.length; i++) {
	if (simplex[i][j] < min) {
	  min = simplex[i][j];
	}
	if (simplex[i][j] > max) {
	  max = simplex[i][j];
	}
      }
      double range = hi[j] - lo[j];
      double rel = range > 0.0 ? (max - min) / range : 0.0;
      if (rel > maxRel) {
	maxRel = rel;
      }
    }
    return maxRel;
  }

  /**
   * Returns a machine-readable self-assessment of this optimizer's fit for ML and agentic workflows. Each capability is
   * rated {@code "full"}, {@code "partial"} or {@code "none"} with a short rationale, so an agent or experiment harness
   * can decide whether to use it.
   *
   * @return schema-versioned JSON describing ML/agentic readiness
   */
  public String getReadinessJson() {
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("optimizer", "AgenticProcessOptimizer");
    root.addProperty("algorithm", "bounded Nelder-Mead simplex with deterministic random seeding");

    JsonArray caps = new JsonArray();
    caps.add(rating("never_throws", "full",
	"optimize() and every trial are exception-safe; failures degrade a single trial"));
    caps.add(rating("deterministic", "full",
	"fixed seed + fixed problem yields an identical trajectory for reproducible experiments"));
    caps.add(rating("bounded_action_space", "full",
	"decision variables carry finite [lower, upper] bounds; useAdjustableParameters() auto-fills"));
    caps.add(rating("json_io", "full", "schema-versioned JSON for results, trajectory and this readiness report"));
    caps.add(rating("reward_shaping", "full",
	"address-based min/max objective or a custom cost function over read-backs and decisions"));
    caps.add(rating("constraint_handling", "full",
	"hard inequality constraints folded in as quadratic penalties with per-constraint weights"));
    caps.add(rating("trajectory_logging", "full",
	"full (setpoints, read-backs, objective, penalty, feasibility, score) tape for offline RL"));
    caps.add(rating("feasibility_gating", "full",
	"each trial is gated by evaluate(): run success + convergence + no failed unit"));
    caps.add(rating("gradient_based", "none", "flowsheet is a noisy gated black box; no analytic gradient is used"));
    caps.add(rating("global_optimum_guarantee", "partial",
	"random seeding mitigates but does not eliminate local-minimum risk for multimodal problems"));
    caps.add(rating("parallel_evaluation", "none",
	"trials are sequential; use ProcessSystem.copy() sweeps for parallelism"));
    root.add("capabilities", caps);

    root.addProperty("recommendedUse",
	"closed-loop setpoint optimization, RL environment stepping, and agent-driven what-if search "
	    + "over a converged NeqSim flowsheet");
    return root.toString();
  }

  /**
   * Builds a single capability rating JSON object.
   *
   * @param name the capability name
   * @param level the rating level ("full", "partial" or "none")
   * @param note a short rationale
   * @return the rating JSON object
   */
  private static JsonObject rating(String name, String level, String note) {
    JsonObject o = new JsonObject();
    o.addProperty("capability", name);
    o.addProperty("level", level);
    o.addProperty("note", note);
    return o;
  }

  /**
   * Immutable descriptor of a bounded decision variable.
   */
  public static final class DecisionVariable implements Serializable {
    /** Serialization id. */
    private static final long serialVersionUID = 1L;
    /** Dot-notation address of the writable input. */
    private final String address;
    /** Inclusive lower bound. */
    private final double lowerBound;
    /** Inclusive upper bound. */
    private final double upperBound;
    /** Unit applied when writing, or null for the default. */
    private final String unit;

    /**
     * Creates a decision variable.
     *
     * @param address dot-notation address
     * @param lowerBound inclusive lower bound
     * @param upperBound inclusive upper bound
     * @param unit unit or null
     */
    DecisionVariable(String address, double lowerBound, double upperBound, String unit) {
      this.address = address;
      this.lowerBound = lowerBound;
      this.upperBound = upperBound;
      this.unit = unit;
    }

    /**
     * Returns the address.
     *
     * @return the dot-notation address
     */
    public String getAddress() {
      return address;
    }

    /**
     * Returns the lower bound.
     *
     * @return the inclusive lower bound
     */
    public double getLowerBound() {
      return lowerBound;
    }

    /**
     * Returns the upper bound.
     *
     * @return the inclusive upper bound
     */
    public double getUpperBound() {
      return upperBound;
    }

    /**
     * Returns the unit.
     *
     * @return the unit, or null for the default
     */
    public String getUnit() {
      return unit;
    }
  }

  /**
   * Immutable descriptor of a hard inequality constraint.
   */
  public static final class Constraint implements Serializable {
    /** Serialization id. */
    private static final long serialVersionUID = 1L;
    /** Dot-notation address of the constrained read-back. */
    private final String address;
    /** Comparison direction. */
    private final ConstraintType type;
    /** The limit value. */
    private final double limit;
    /** Unit for the read-back, or null for the default. */
    private final String unit;
    /** Non-negative penalty weight multiplying the squared violation. */
    private final double penaltyWeight;

    /**
     * Creates a constraint.
     *
     * @param address dot-notation address
     * @param type comparison direction
     * @param limit the limit value
     * @param unit unit or null
     * @param penaltyWeight non-negative penalty weight
     */
    Constraint(String address, ConstraintType type, double limit, String unit, double penaltyWeight) {
      this.address = address;
      this.type = type;
      this.limit = limit;
      this.unit = unit;
      this.penaltyWeight = penaltyWeight;
    }

    /**
     * Computes the constraint violation magnitude (zero if satisfied).
     *
     * @param value the read-back value
     * @return the non-negative violation magnitude
     */
    double violation(double value) {
      if (type == ConstraintType.LESS_OR_EQUAL) {
	return Math.max(0.0, value - limit);
      }
      return Math.max(0.0, limit - value);
    }

    /**
     * Returns the address.
     *
     * @return the dot-notation address
     */
    public String getAddress() {
      return address;
    }

    /**
     * Returns the comparison direction.
     *
     * @return the constraint type
     */
    public ConstraintType getType() {
      return type;
    }

    /**
     * Returns the limit value.
     *
     * @return the limit
     */
    public double getLimit() {
      return limit;
    }

    /**
     * Returns the unit.
     *
     * @return the unit, or null
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Returns the penalty weight.
     *
     * @return the non-negative penalty weight
     */
    public double getPenaltyWeight() {
      return penaltyWeight;
    }
  }

  /**
   * Record of one evaluated candidate point: the (state, action, reward) tuple consumed by ML and agentic
   * post-processing.
   */
  public static final class Trial implements Serializable {
    /** Serialization id. */
    private static final long serialVersionUID = 1L;
    /** 1-based evaluation index. */
    private int index;
    /** Applied decision-variable setpoints (address &rarr; value). */
    private final Map<String, Double> setpoints = new LinkedHashMap<String, Double>();
    /** Read-back values (address &rarr; value). */
    private final Map<String, Double> readbacks = new LinkedHashMap<String, Double>();
    /** Per-address rejection reasons, if any. */
    private final Map<String, String> rejected = new LinkedHashMap<String, String>();
    /** Raw objective value (sense not applied). */
    private double objective = Double.NaN;
    /** Total constraint penalty added to the score. */
    private double penalty = 0.0;
    /** Minimized scalar score. */
    private double score = Double.NaN;
    /** True when the gated run converged and no setpoint was rejected. */
    private boolean runFeasible;
    /** True when the run is feasible and all constraints are satisfied. */
    private boolean feasible;
    /** Inner convergence iterations reported by evaluate(). */
    private int iterations = -1;

    /**
     * Returns the 1-based evaluation index.
     *
     * @return the index
     */
    public int getIndex() {
      return index;
    }

    /**
     * Returns the applied setpoints.
     *
     * @return an address &rarr; value map
     */
    public Map<String, Double> getSetpoints() {
      return setpoints;
    }

    /**
     * Returns the read-back values.
     *
     * @return an address &rarr; value map
     */
    public Map<String, Double> getReadbacks() {
      return readbacks;
    }

    /**
     * Returns the raw objective value.
     *
     * @return the objective
     */
    public double getObjective() {
      return objective;
    }

    /**
     * Returns the minimized score.
     *
     * @return the score
     */
    public double getScore() {
      return score;
    }

    /**
     * Returns whether this trial is feasible (run converged and all constraints satisfied).
     *
     * @return true if feasible
     */
    public boolean isFeasible() {
      return feasible;
    }

    /**
     * Serializes this trial to a JSON object.
     *
     * @return the JSON object
     */
    JsonObject toJsonObject() {
      JsonObject o = new JsonObject();
      o.addProperty("index", index);
      o.add("setpoints", mapToJson(setpoints));
      o.add("readbacks", mapToJson(readbacks));
      if (!rejected.isEmpty()) {
	JsonObject r = new JsonObject();
	for (Map.Entry<String, String> e : rejected.entrySet()) {
	  r.addProperty(e.getKey(), e.getValue());
	}
	o.add("rejected", r);
      }
      addNumber(o, "objective", objective);
      o.addProperty("penalty", penalty);
      addNumber(o, "score", score);
      o.addProperty("runFeasible", runFeasible);
      o.addProperty("feasible", feasible);
      if (iterations >= 0) {
	o.addProperty("iterations", iterations);
      }
      return o;
    }
  }

  /**
   * Outcome of an optimization run, including the best point and the full trajectory.
   */
  public static final class OptimizationResult implements Serializable {
    /** Serialization id. */
    private static final long serialVersionUID = 1L;
    /** True when at least one point was evaluated successfully. */
    private boolean success;
    /** True when a feasible optimum was found. */
    private boolean feasible;
    /** Human-readable status message. */
    private String message = "";
    /** Number of objective evaluations performed. */
    private int evaluations;
    /** Best decision-variable setpoints (address &rarr; value). */
    private Map<String, Double> bestSetpoints = new LinkedHashMap<String, Double>();
    /** Read-backs at the best point. */
    private Map<String, Double> bestReadbacks = new LinkedHashMap<String, Double>();
    /** Raw objective value at the best point. */
    private double bestObjective = Double.NaN;
    /** Minimized score at the best point. */
    private double bestScore = Double.NaN;
    /** Full trajectory of evaluated points. */
    private List<Trial> trajectory = new ArrayList<Trial>();

    /**
     * Returns whether the run produced at least one evaluation.
     *
     * @return true on success
     */
    public boolean isSuccess() {
      return success;
    }

    /**
     * Returns whether a feasible optimum was found.
     *
     * @return true if feasible
     */
    public boolean isFeasible() {
      return feasible;
    }

    /**
     * Returns the status message.
     *
     * @return the message
     */
    public String getMessage() {
      return message;
    }

    /**
     * Returns the number of objective evaluations performed.
     *
     * @return the evaluation count
     */
    public int getEvaluations() {
      return evaluations;
    }

    /**
     * Returns the best decision-variable setpoints.
     *
     * @return an address &rarr; value map
     */
    public Map<String, Double> getBestSetpoints() {
      return bestSetpoints;
    }

    /**
     * Returns the read-backs at the best point.
     *
     * @return an address &rarr; value map
     */
    public Map<String, Double> getBestReadbacks() {
      return bestReadbacks;
    }

    /**
     * Returns the raw objective value at the best point.
     *
     * @return the best objective
     */
    public double getBestObjective() {
      return bestObjective;
    }

    /**
     * Returns the minimized score at the best point.
     *
     * @return the best score
     */
    public double getBestScore() {
      return bestScore;
    }

    /**
     * Returns the full trajectory of evaluated points.
     *
     * @return the trajectory list
     */
    public List<Trial> getTrajectory() {
      return trajectory;
    }

    /**
     * Serializes this result to schema-versioned JSON.
     *
     * @return the JSON string
     */
    public String toJson() {
      JsonObject root = new JsonObject();
      root.addProperty("schemaVersion", SCHEMA_VERSION);
      root.addProperty("success", success);
      root.addProperty("feasible", feasible);
      root.addProperty("message", message);
      root.addProperty("evaluations", evaluations);
      addNumber(root, "bestObjective", bestObjective);
      addNumber(root, "bestScore", bestScore);
      root.add("bestSetpoints", mapToJson(bestSetpoints));
      root.add("bestReadbacks", mapToJson(bestReadbacks));
      JsonArray traj = new JsonArray();
      for (Trial t : trajectory) {
	traj.add(t.toJsonObject());
      }
      root.add("trajectory", traj);
      return root.toString();
    }
  }

  /**
   * Adds a numeric property, writing JSON null for non-finite values.
   *
   * @param o the target object
   * @param key the property name
   * @param value the value
   */
  private static void addNumber(JsonObject o, String key, double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      o.add(key, com.google.gson.JsonNull.INSTANCE);
    } else {
      o.addProperty(key, value);
    }
  }

  /**
   * Serializes a string-to-double map to a JSON object, writing JSON null for non-finite values.
   *
   * @param map the map to serialize
   * @return the JSON object
   */
  private static JsonObject mapToJson(Map<String, Double> map) {
    JsonObject o = new JsonObject();
    for (Map.Entry<String, Double> e : map.entrySet()) {
      addNumber(o, e.getKey(), e.getValue() == null ? Double.NaN : e.getValue().doubleValue());
    }
    return o;
  }

  /**
   * Returns the set of unique read-back addresses (objective, constraints and watches). Useful for agents that want to
   * pre-validate addresses with {@link ProcessAutomation#validateAddress(String)} before optimizing.
   *
   * @return an ordered set of read-back addresses
   */
  public Set<String> getReadbackAddresses() {
    Set<String> addrs = new LinkedHashSet<String>();
    if (objectiveAddress != null) {
      addrs.add(objectiveAddress);
    }
    for (Constraint c : constraints) {
      addrs.add(c.address);
    }
    addrs.addAll(watches.keySet());
    return addrs;
  }

  /**
   * Returns the registered decision variables.
   *
   * @return an unmodifiable list of decision variables
   */
  public List<DecisionVariable> getVariables() {
    return java.util.Collections.unmodifiableList(new ArrayList<DecisionVariable>(variables));
  }

  /**
   * Returns the registered constraints.
   *
   * @return an unmodifiable list of constraints
   */
  public List<Constraint> getConstraints() {
    return java.util.Collections.unmodifiableList(new ArrayList<Constraint>(constraints));
  }

  /**
   * Returns the variable addresses as a list, for diagnostics.
   *
   * @return the decision-variable addresses in registration order
   */
  public List<String> getVariableAddresses() {
    List<String> out = new ArrayList<String>();
    for (DecisionVariable dv : variables) {
      out.add(dv.address);
    }
    return Arrays.asList(out.toArray(new String[0]));
  }
}
