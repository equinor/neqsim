package neqsim.process.optimization.valuechain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-period (life-of-field) optimizer that schedules capital investments in time.
 *
 * <p>
 * Single-period optimization tunes setpoints for one operating point; field development decisions are intrinsically
 * multi-period — <em>when</em> to install compression, drill an infill well, or debottleneck a flowline changes the
 * discounted value of the whole field. This optimizer searches over the installation <em>year</em> of each candidate
 * investment (including the option never to make it) to maximise the discounted net present value of the field over its
 * life.
 * </p>
 *
 * <p>
 * The per-period economics are supplied by a {@link LifeOfFieldEvaluator}: given a year and the set of investments
 * active in that year, it returns the net annual cash flow (typically obtained by configuring the corresponding NeqSim
 * flowsheet, advancing the reservoir with {@code SimpleReservoir.runTransient}, running it to convergence, and pricing
 * the result with {@link ValueChainObjective}). Keeping the evaluator functional makes the search deterministic and
 * unit-testable without running a flowsheet.
 * </p>
 *
 * <p>
 * The investment timeline is found by bounded enumeration: each investment may be installed in any year from its
 * earliest feasible year to the end of field life, or skipped entirely. A guard caps the number of evaluated
 * combinations so the search stays tractable for a handful of strategic decisions.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class LifeOfFieldOptimizer implements Serializable {

  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Default cap on the number of investment-timing combinations evaluated. */
  public static final long DEFAULT_MAX_COMBINATIONS = 2_000_000L;

  /** Number of years in the field life (inclusive of year 0). */
  private final int nYears;

  /** The economic parameters supplying the discount rate. */
  private final EconomicParameters econ;

  /** The candidate investments to schedule. */
  private final List<Investment> investments = new ArrayList<Investment>();

  /** Cap on the number of evaluated investment-timing combinations. */
  private long maxCombinations = DEFAULT_MAX_COMBINATIONS;

  /**
   * Functional evaluator returning the net annual cash flow for one period.
   */
  public interface LifeOfFieldEvaluator {
    /**
     * Returns the net annual cash flow for a given year and active-investment set.
     *
     * @param year the year index (0 = first year of production)
     * @param active a flag per registered investment, true when active in this year
     * @return the net annual cash flow for the year in the configured currency
     */
    double annualNetValueNok(int year, boolean[] active);
  }

  /**
   * A candidate capital investment that may be installed in a chosen year.
   */
  public static class Investment implements Serializable {

    /** Serialization version identifier. */
    private static final long serialVersionUID = 1000L;

    /** Descriptive name of the investment. */
    private final String name;

    /** Capital cost of the investment in the configured currency. */
    private final double capexNok;

    /** Earliest year the investment can be installed. */
    private final int earliestYear;

    /**
     * Creates an investment.
     *
     * @param name descriptive name
     * @param capexNok capital cost in the configured currency (non-negative)
     * @param earliestYear earliest year the investment can be installed (>= 0)
     */
    public Investment(String name, double capexNok, int earliestYear) {
      this.name = name;
      this.capexNok = capexNok;
      this.earliestYear = earliestYear;
    }

    /**
     * Gets the descriptive name.
     *
     * @return the investment name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the capital cost.
     *
     * @return capital cost in the configured currency
     */
    public double getCapexNok() {
      return capexNok;
    }

    /**
     * Gets the earliest installation year.
     *
     * @return the earliest year the investment can be installed
     */
    public int getEarliestYear() {
      return earliestYear;
    }
  }

  /**
   * Result of a life-of-field optimization.
   */
  public static class LifeOfFieldResult implements Serializable {

    /** Serialization version identifier. */
    private static final long serialVersionUID = 1000L;

    /** Installation year per investment, or -1 if the investment is skipped. */
    private final int[] installYears;

    /** Net present value of the optimal timeline in the configured currency. */
    private final double npvNok;

    /** Net annual cash flow per year for the optimal timeline. */
    private final double[] annualCashFlow;

    /**
     * Creates a life-of-field result.
     *
     * @param installYears installation year per investment (-1 = skipped)
     * @param npvNok net present value in the configured currency
     * @param annualCashFlow net annual cash flow per year
     */
    public LifeOfFieldResult(int[] installYears, double npvNok, double[] annualCashFlow) {
      this.installYears = installYears.clone();
      this.npvNok = npvNok;
      this.annualCashFlow = annualCashFlow.clone();
    }

    /**
     * Gets the optimal installation year per investment.
     *
     * @return an array of installation years (-1 indicates the investment is skipped)
     */
    public int[] getInstallYears() {
      return installYears.clone();
    }

    /**
     * Gets the net present value of the optimal timeline.
     *
     * @return net present value in the configured currency
     */
    public double getNpvNok() {
      return npvNok;
    }

    /**
     * Gets the net annual cash flow per year for the optimal timeline.
     *
     * @return an array of annual cash flows
     */
    public double[] getAnnualCashFlow() {
      return annualCashFlow.clone();
    }
  }

  /**
   * Creates a life-of-field optimizer.
   *
   * @param nYears number of production years (year indices 0..nYears-1); must be positive
   * @param econ the economic parameters supplying the discount rate (must not be null)
   */
  public LifeOfFieldOptimizer(int nYears, EconomicParameters econ) {
    if (nYears <= 0) {
      throw new IllegalArgumentException("nYears must be positive");
    }
    if (econ == null) {
      throw new IllegalArgumentException("EconomicParameters must not be null");
    }
    this.nYears = nYears;
    this.econ = econ;
  }

  /**
   * Registers a candidate investment.
   *
   * @param investment the investment to add (must not be null)
   * @return this optimizer for method chaining
   */
  public LifeOfFieldOptimizer addInvestment(Investment investment) {
    if (investment == null) {
      throw new IllegalArgumentException("Investment must not be null");
    }
    investments.add(investment);
    return this;
  }

  /**
   * Sets the cap on the number of investment-timing combinations evaluated.
   *
   * @param maxCombinations the maximum number of combinations (must be positive)
   * @return this optimizer for method chaining
   */
  public LifeOfFieldOptimizer setMaxCombinations(long maxCombinations) {
    if (maxCombinations <= 0) {
      throw new IllegalArgumentException("maxCombinations must be positive");
    }
    this.maxCombinations = maxCombinations;
    return this;
  }

  /**
   * Optimizes the investment timeline to maximise discounted net present value.
   *
   * @param evaluator the per-period economic evaluator (must not be null)
   * @return the optimal timeline and its net present value
   */
  public LifeOfFieldResult optimize(LifeOfFieldEvaluator evaluator) {
    if (evaluator == null) {
      throw new IllegalArgumentException("Evaluator must not be null");
    }
    int n = investments.size();
    // Each investment has (nYears - earliest + 1) install options plus a "never" option.
    long combos = 1L;
    int[] optionCount = new int[n];
    for (int i = 0; i < n; i++) {
      int feasibleYears = Math.max(0, nYears - investments.get(i).getEarliestYear());
      optionCount[i] = feasibleYears + 1; // +1 for "never"
      combos *= optionCount[i];
      if (combos > maxCombinations) {
	throw new IllegalStateException("Investment-timing combinations (" + combos + ") exceed the configured cap ("
	    + maxCombinations + "); reduce investments or raise the cap.");
      }
    }

    int[] best = new int[n];
    int[] current = new int[n];
    double[] bestCashFlow = new double[nYears];
    double[] bestNpv = new double[] { Double.NEGATIVE_INFINITY };
    enumerate(0, current, best, bestNpv, bestCashFlow, evaluator);
    return new LifeOfFieldResult(best, bestNpv[0], bestCashFlow);
  }

  /**
   * Recursively enumerates investment-timing combinations and retains the best by net present value.
   *
   * @param index the investment index currently being assigned
   * @param current the working install-year assignment
   * @param best the best install-year assignment found so far (updated in place)
   * @param bestNpv a single-element array holding the best net present value found so far
   * @param bestCashFlow the cash-flow schedule of the best timeline (updated in place)
   * @param evaluator the per-period economic evaluator
   */
  private void enumerate(int index, int[] current, int[] best, double[] bestNpv, double[] bestCashFlow,
      LifeOfFieldEvaluator evaluator) {
    int n = investments.size();
    if (index == n) {
      double[] cashFlow = new double[nYears];
      double npv = computeNpv(current, cashFlow, evaluator);
      if (npv > bestNpv[0]) {
	bestNpv[0] = npv;
	System.arraycopy(current, 0, best, 0, n);
	System.arraycopy(cashFlow, 0, bestCashFlow, 0, nYears);
      }
      return;
    }
    int earliest = investments.get(index).getEarliestYear();
    // "never" option encoded as -1
    current[index] = -1;
    enumerate(index + 1, current, best, bestNpv, bestCashFlow, evaluator);
    for (int year = earliest; year < nYears; year++) {
      current[index] = year;
      enumerate(index + 1, current, best, bestNpv, bestCashFlow, evaluator);
    }
  }

  /**
   * Computes the net present value of a fully assigned investment timeline.
   *
   * @param installYears install year per investment (-1 = skipped)
   * @param cashFlowOut output array receiving the net annual cash flow per year
   * @param evaluator the per-period economic evaluator
   * @return the discounted net present value of the timeline
   */
  private double computeNpv(int[] installYears, double[] cashFlowOut, LifeOfFieldEvaluator evaluator) {
    int n = investments.size();
    double npv = 0.0;
    for (int year = 0; year < nYears; year++) {
      boolean[] active = new boolean[n];
      for (int i = 0; i < n; i++) {
	active[i] = installYears[i] >= 0 && year >= installYears[i];
      }
      double value = evaluator.annualNetValueNok(year, active);
      cashFlowOut[year] = value;
      npv += value * econ.discountFactor(year);
    }
    for (int i = 0; i < n; i++) {
      if (installYears[i] >= 0) {
	npv -= investments.get(i).getCapexNok() * econ.discountFactor(installYears[i]);
      }
    }
    return npv;
  }
}
