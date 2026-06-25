package neqsim.process.optimization.valuechain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import neqsim.process.equipment.capacity.CapacityConstraint;

/**
 * Ranks candidate debottlenecking investments by their incremental net present value.
 *
 * <p>
 * Once a {@code BottleneckTracker} migration trajectory has revealed <em>which</em> constraint binds the plant and
 * <em>when</em>, the economic question becomes <em>which upgrade is worth making, and in which year</em>. The advisor
 * answers that by discounting the incremental value each candidate upgrade unlocks (extra export, deferred decline)
 * against its capital cost, producing a ranked shortlist with NPV, benefit-cost ratio and simple payback. It is the
 * economic companion to the physical bottleneck analysis.
 * </p>
 *
 * <p>
 * The advisor is a pure ranking engine: the incremental annual value of each candidate is supplied by the caller
 * (typically obtained by relaxing the binding constraint in NeqSim, re-running, and pricing the extra production with
 * {@link ValueChainObjective}). This keeps the advisor fast and deterministic. When a candidate carries a live
 * {@link CapacityConstraint}, {@link #applyShadowPrices()} writes the per-year incremental value back onto the
 * constraint as its shadow price.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DebottleneckingAdvisor implements Serializable {

  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** The economic parameters supplying the discount rate. */
  private final EconomicParameters econ;

  /** The candidate upgrades to be ranked. */
  private final List<DebottleneckCandidate> candidates = new ArrayList<DebottleneckCandidate>();

  /**
   * A candidate debottlenecking investment.
   */
  public static class DebottleneckCandidate implements Serializable {

    /** Serialization version identifier. */
    private static final long serialVersionUID = 1000L;

    /** Descriptive name of the upgrade (e.g. "Larger tubing on Well-1"). */
    private final String name;

    /** Name of the equipment the upgrade targets. */
    private final String targetEquipment;

    /** Capital cost of the upgrade in the configured currency. */
    private final double capexNok;

    /** First year (inclusive) the upgrade starts unlocking value. */
    private final int firstYear;

    /** Last year (inclusive) the upgrade continues unlocking value. */
    private final int lastYear;

    /** Incremental value unlocked by the upgrade, in currency per year. */
    private final double annualIncrementalValueNok;

    /** Optional live constraint the upgrade relaxes (may be null). */
    private final CapacityConstraint constraint;

    /**
     * Creates a debottlenecking candidate.
     *
     * @param name descriptive name of the upgrade
     * @param targetEquipment name of the equipment the upgrade targets
     * @param capexNok capital cost in the configured currency (non-negative)
     * @param firstYear first year (inclusive) the upgrade starts unlocking value (>= 0)
     * @param lastYear last year (inclusive) the upgrade continues unlocking value (>= firstYear)
     * @param annualIncrementalValueNok incremental value unlocked in currency per year
     * @param constraint optional live constraint the upgrade relaxes; may be null
     */
    public DebottleneckCandidate(String name, String targetEquipment, double capexNok, int firstYear, int lastYear,
        double annualIncrementalValueNok, CapacityConstraint constraint) {
      this.name = name;
      this.targetEquipment = targetEquipment;
      this.capexNok = capexNok;
      this.firstYear = firstYear;
      this.lastYear = lastYear;
      this.annualIncrementalValueNok = annualIncrementalValueNok;
      this.constraint = constraint;
    }

    /**
     * Gets the descriptive name of the upgrade.
     *
     * @return the upgrade name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the target equipment name.
     *
     * @return the target equipment name
     */
    public String getTargetEquipment() {
      return targetEquipment;
    }

    /**
     * Gets the capital cost of the upgrade.
     *
     * @return capital cost in the configured currency
     */
    public double getCapexNok() {
      return capexNok;
    }

    /**
     * Gets the first year the upgrade unlocks value.
     *
     * @return the first year (inclusive)
     */
    public int getFirstYear() {
      return firstYear;
    }

    /**
     * Gets the last year the upgrade unlocks value.
     *
     * @return the last year (inclusive)
     */
    public int getLastYear() {
      return lastYear;
    }

    /**
     * Gets the incremental annual value unlocked by the upgrade.
     *
     * @return incremental value in currency per year
     */
    public double getAnnualIncrementalValueNok() {
      return annualIncrementalValueNok;
    }

    /**
     * Gets the live constraint the upgrade relaxes.
     *
     * @return the constraint, or null if none was supplied
     */
    public CapacityConstraint getConstraint() {
      return constraint;
    }
  }

  /**
   * A ranked recommendation for one candidate upgrade.
   */
  public static class Recommendation implements Serializable {

    /** Serialization version identifier. */
    private static final long serialVersionUID = 1000L;

    /** The candidate the recommendation refers to. */
    private final DebottleneckCandidate candidate;

    /** Net present value of the upgrade in the configured currency. */
    private final double npvNok;

    /** Present value of the benefits in the configured currency. */
    private final double pvBenefitsNok;

    /** Benefit-cost ratio (present value of benefits divided by capex). */
    private final double benefitCostRatio;

    /** Simple payback in years, or {@link Double#POSITIVE_INFINITY} if never paid back. */
    private final double paybackYears;

    /**
     * Creates a recommendation.
     *
     * @param candidate the candidate the recommendation refers to
     * @param npvNok net present value in the configured currency
     * @param pvBenefitsNok present value of benefits in the configured currency
     * @param benefitCostRatio benefit-cost ratio
     * @param paybackYears simple payback in years
     */
    public Recommendation(DebottleneckCandidate candidate, double npvNok, double pvBenefitsNok, double benefitCostRatio,
        double paybackYears) {
      this.candidate = candidate;
      this.npvNok = npvNok;
      this.pvBenefitsNok = pvBenefitsNok;
      this.benefitCostRatio = benefitCostRatio;
      this.paybackYears = paybackYears;
    }

    /**
     * Gets the candidate this recommendation refers to.
     *
     * @return the candidate
     */
    public DebottleneckCandidate getCandidate() {
      return candidate;
    }

    /**
     * Gets the net present value of the upgrade.
     *
     * @return net present value in the configured currency
     */
    public double getNpvNok() {
      return npvNok;
    }

    /**
     * Gets the present value of the benefits.
     *
     * @return present value of benefits in the configured currency
     */
    public double getPvBenefitsNok() {
      return pvBenefitsNok;
    }

    /**
     * Gets the benefit-cost ratio.
     *
     * @return benefit-cost ratio
     */
    public double getBenefitCostRatio() {
      return benefitCostRatio;
    }

    /**
     * Gets the simple payback in years.
     *
     * @return payback in years, or positive infinity if never paid back
     */
    public double getPaybackYears() {
      return paybackYears;
    }

    /**
     * Indicates whether the upgrade is economically attractive (positive NPV).
     *
     * @return true if the net present value is positive
     */
    public boolean isAttractive() {
      return npvNok > 0.0;
    }
  }

  /**
   * Creates a debottlenecking advisor.
   *
   * @param econ the economic parameters supplying the discount rate (must not be null)
   */
  public DebottleneckingAdvisor(EconomicParameters econ) {
    if (econ == null) {
      throw new IllegalArgumentException("EconomicParameters must not be null");
    }
    this.econ = econ;
  }

  /**
   * Adds a candidate upgrade to be evaluated.
   *
   * @param candidate the candidate to add (must not be null)
   * @return this advisor for method chaining
   */
  public DebottleneckingAdvisor addCandidate(DebottleneckCandidate candidate) {
    if (candidate == null) {
      throw new IllegalArgumentException("Candidate must not be null");
    }
    candidates.add(candidate);
    return this;
  }

  /**
   * Gets the candidates currently registered with the advisor.
   *
   * @return an unmodifiable list of candidates
   */
  public List<DebottleneckCandidate> getCandidates() {
    return Collections.unmodifiableList(candidates);
  }

  /**
   * Evaluates and ranks all candidate upgrades by net present value, highest first.
   *
   * <p>
   * For each candidate the present value of the incremental annual cash flow is discounted across its active years and
   * the capital cost is discounted to its installation year. Net present value is the difference. Ties are broken by
   * benefit-cost ratio.
   * </p>
   *
   * @return a list of recommendations sorted by descending net present value
   */
  public List<Recommendation> evaluate() {
    List<Recommendation> out = new ArrayList<Recommendation>();
    for (DebottleneckCandidate c : candidates) {
      double pvBenefits = 0.0;
      for (int y = c.getFirstYear(); y <= c.getLastYear(); y++) {
        pvBenefits += c.getAnnualIncrementalValueNok() * econ.discountFactor(y);
      }
      double pvCapex = c.getCapexNok() * econ.discountFactor(c.getFirstYear());
      double npv = pvBenefits - pvCapex;
      double bcr = pvCapex > 0.0 ? pvBenefits / pvCapex : Double.POSITIVE_INFINITY;
      double payback = computePayback(c);
      out.add(new Recommendation(c, npv, pvBenefits, bcr, payback));
    }
    Collections.sort(out, new Comparator<Recommendation>() {
      @Override
      public int compare(Recommendation a, Recommendation b) {
        int byNpv = Double.compare(b.getNpvNok(), a.getNpvNok());
        if (byNpv != 0) {
          return byNpv;
        }
        return Double.compare(b.getBenefitCostRatio(), a.getBenefitCostRatio());
      }
    });
    return out;
  }

  /**
   * Computes the simple (undiscounted) payback of a candidate in years from its installation year.
   *
   * @param c the candidate
   * @return the payback in years, or positive infinity if the benefits never cover the capex
   */
  private double computePayback(DebottleneckCandidate c) {
    if (c.getAnnualIncrementalValueNok() <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    double years = c.getCapexNok() / c.getAnnualIncrementalValueNok();
    int activeYears = c.getLastYear() - c.getFirstYear() + 1;
    if (years > activeYears) {
      return Double.POSITIVE_INFINITY;
    }
    return years;
  }

  /**
   * Writes each candidate's incremental annual value back onto its live constraint as a shadow price.
   *
   * <p>
   * Candidates without a live {@link CapacityConstraint} are skipped. After this call the binding constraints carry the
   * marginal value of relaxing them, queryable via {@link CapacityConstraint#getShadowPrice()}.
   * </p>
   *
   * @return the number of constraints updated
   */
  public int applyShadowPrices() {
    int updated = 0;
    for (DebottleneckCandidate c : candidates) {
      if (c.getConstraint() != null) {
        c.getConstraint().setShadowPrice(c.getAnnualIncrementalValueNok());
        updated++;
      }
    }
    return updated;
  }

  /**
   * Renders the ranked recommendations as a JSON array.
   *
   * @return a JSON string describing the ranked recommendations
   */
  public String toJson() {
    List<Recommendation> recs = evaluate();
    StringBuilder sb = new StringBuilder();
    sb.append("{\"recommendations\":[");
    for (int i = 0; i < recs.size(); i++) {
      Recommendation r = recs.get(i);
      if (i > 0) {
        sb.append(",");
      }
      sb.append("{");
      sb.append("\"name\":\"").append(esc(r.getCandidate().getName())).append("\",");
      sb.append("\"targetEquipment\":\"").append(esc(r.getCandidate().getTargetEquipment())).append("\",");
      sb.append("\"npvNok\":").append(fmt(r.getNpvNok())).append(",");
      sb.append("\"benefitCostRatio\":").append(fmt(r.getBenefitCostRatio())).append(",");
      sb.append("\"paybackYears\":").append(fmt(r.getPaybackYears())).append(",");
      sb.append("\"attractive\":").append(r.isAttractive());
      sb.append("}");
    }
    sb.append("]}");
    return sb.toString();
  }

  /**
   * Formats a double for JSON output, mapping non-finite values to {@code null}.
   *
   * @param value the value to format
   * @return the formatted string or {@code null}
   */
  private static String fmt(double value) {
    if (Double.isInfinite(value) || Double.isNaN(value)) {
      return "null";
    }
    return String.format(Locale.US, "%.6g", value);
  }

  /**
   * Escapes a string for inclusion in JSON output.
   *
   * @param value the value to escape (may be null)
   * @return the escaped string, or an empty string if null
   */
  private static String esc(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
