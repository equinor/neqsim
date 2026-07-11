package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.fielddevelopment.integrated.ChokeAndGasLiftAllocationOptimizer.AllocationResult;
import neqsim.process.fielddevelopment.integrated.ChokeAndGasLiftAllocationOptimizer.WellAllocation;

/**
 * Builds the operator-facing "strupe/&oslash;ke" (choke-back / open-up) recommendation list (NIP-4).
 *
 * <p>
 * A {@link ChokeAndGasLiftAllocationOptimizer} produces an optimal choke-and-lift set-point for a well fleet. This
 * class compares that optimum against each well's current setting and turns it into the ranked action list an operator
 * actually uses: per well the recommended choke opening, whether to open up / choke back / shut / leave unchanged, the
 * expected oil uplift versus the current setting, the binding constraint, and any operational lock reason. The list is
 * sorted by expected uplift so the highest-value moves are at the top.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see ChokeAndGasLiftAllocationOptimizer
 * @see ChokeableGasLiftWell
 */
public class StrupeOkeReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Recommended operator action for a well. */
  public enum Action {
    /** Open the choke further (increase rate). */
    OPEN,
    /** Choke the well back (reduce rate). */
    CHOKE_BACK,
    /** Keep the well shut (operational lock). */
    SHUT,
    /** No change from the current setting. */
    NO_CHANGE
  }

  private final List<Recommendation> recommendations = new ArrayList<Recommendation>();
  private double chokeTolerance = 0.02;

  /**
   * Builds a strupe/&oslash;ke report from a well fleet and an allocation result.
   *
   * @param wells the well fleet, carrying current choke and lift settings
   * @param result the optimiser result to compare against
   * @return a populated report (never null)
   */
  public static StrupeOkeReport build(List<ChokeableGasLiftWell> wells, AllocationResult result) {
    StrupeOkeReport report = new StrupeOkeReport();
    report.compute(wells, result);
    return report;
  }

  /**
   * Sets the choke tolerance below which a change is treated as no change.
   *
   * @param chokeTolerance absolute choke-fraction tolerance (default 0.02)
   * @return this report for chaining
   */
  public StrupeOkeReport setChokeTolerance(double chokeTolerance) {
    this.chokeTolerance = Math.max(0.0, chokeTolerance);
    return this;
  }

  /**
   * Computes the recommendations.
   *
   * @param wells the well fleet
   * @param result the optimiser result
   */
  private void compute(List<ChokeableGasLiftWell> wells, AllocationResult result) {
    recommendations.clear();
    if (wells == null || result == null) {
      return;
    }
    for (ChokeableGasLiftWell w : wells) {
      String name = w.getName();
      WellAllocation alloc = result.getWells().get(name);
      double recommendedChoke = alloc == null ? 0.0 : alloc.getChokeFraction();
      double recommendedLift = alloc == null ? 0.0 : alloc.getLiftRate();
      double optimizedOil = alloc == null ? 0.0 : alloc.getOilRate();
      String binding = alloc == null ? "none" : alloc.getBindingConstraint();
      double currentOil = w.oilRate(w.getCurrentChokeFraction(), w.getCurrentLiftRate());
      double uplift = optimizedOil - currentOil;

      Action action;
      if (w.isForcedShut()) {
        action = Action.SHUT;
      } else if (recommendedChoke > w.getCurrentChokeFraction() + chokeTolerance) {
        action = Action.OPEN;
      } else if (recommendedChoke < w.getCurrentChokeFraction() - chokeTolerance) {
        action = Action.CHOKE_BACK;
      } else {
        action = Action.NO_CHANGE;
      }

      recommendations.add(new Recommendation(name, action, w.getCurrentChokeFraction(), recommendedChoke,
          recommendedLift, currentOil, optimizedOil, uplift, binding, w.getShutReason()));
    }
    Collections.sort(recommendations, new Comparator<Recommendation>() {
      @Override
      public int compare(Recommendation a, Recommendation b) {
        return Double.compare(b.getExpectedUplift(), a.getExpectedUplift());
      }
    });
  }

  /**
   * Returns the ranked recommendations (highest uplift first).
   *
   * @return list of recommendations
   */
  public List<Recommendation> getRecommendations() {
    return recommendations;
  }

  /**
   * Returns the total expected oil uplift across all wells versus their current settings.
   *
   * @return total uplift in Sm3/day
   */
  public double getTotalExpectedUplift() {
    double total = 0.0;
    for (Recommendation r : recommendations) {
      total += r.getExpectedUplift();
    }
    return total;
  }

  /**
   * Returns a plain-text table of the recommendations.
   *
   * @return formatted table string
   */
  public String toTable() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format(java.util.Locale.ROOT, "%-10s %-11s %8s %8s %10s %10s  %s%n", "Well", "Action", "Cur%",
        "Rec%", "Uplift", "Bind", "Reason"));
    for (Recommendation r : recommendations) {
      sb.append(String.format(java.util.Locale.ROOT, "%-10s %-11s %7.0f%% %7.0f%% %10.1f %10s  %s%n", r.getWellName(),
          r.getAction().name(), 100.0 * r.getCurrentChokeFraction(), 100.0 * r.getRecommendedChokeFraction(),
          r.getExpectedUplift(), r.getBindingConstraint(), r.getLockReason()));
    }
    return sb.toString();
  }

  /**
   * Returns a schema-versioned JSON representation of the report.
   *
   * @return JSON string
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"schemaVersion\":\"").append(ChokeAndGasLiftAllocationOptimizer.SCHEMA_VERSION).append("\",");
    sb.append("\"totalExpectedUplift\":").append(fmt(getTotalExpectedUplift())).append(",");
    sb.append("\"recommendations\":[");
    boolean first = true;
    for (Recommendation r : recommendations) {
      if (!first) {
        sb.append(",");
      }
      first = false;
      sb.append("{\"well\":\"").append(r.getWellName()).append("\",");
      sb.append("\"action\":\"").append(r.getAction().name()).append("\",");
      sb.append("\"currentChoke\":").append(fmt(r.getCurrentChokeFraction())).append(",");
      sb.append("\"recommendedChoke\":").append(fmt(r.getRecommendedChokeFraction())).append(",");
      sb.append("\"recommendedLift\":").append(fmt(r.getRecommendedLiftRate())).append(",");
      sb.append("\"currentOil\":").append(fmt(r.getCurrentOil())).append(",");
      sb.append("\"optimizedOil\":").append(fmt(r.getOptimizedOil())).append(",");
      sb.append("\"expectedUplift\":").append(fmt(r.getExpectedUplift())).append(",");
      sb.append("\"bindingConstraint\":\"").append(r.getBindingConstraint()).append("\",");
      sb.append("\"lockReason\":\"").append(escape(r.getLockReason())).append("\"}");
    }
    sb.append("]}");
    return sb.toString();
  }

  /**
   * Returns a summary of the recommendations grouped by action.
   *
   * @return map of action name to well count
   */
  public Map<String, Integer> getActionSummary() {
    Map<String, Integer> summary = new LinkedHashMap<String, Integer>();
    for (Action a : Action.values()) {
      summary.put(a.name(), 0);
    }
    for (Recommendation r : recommendations) {
      summary.put(r.getAction().name(), summary.get(r.getAction().name()) + 1);
    }
    return summary;
  }

  /**
   * Formats a double for JSON output.
   *
   * @param value the value to format
   * @return formatted string
   */
  private static String fmt(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return "null";
    }
    return String.format(java.util.Locale.ROOT, "%.4f", value);
  }

  /**
   * Escapes a string for JSON output.
   *
   * @param value the value to escape
   * @return escaped string
   */
  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /**
   * A single per-well strupe/&oslash;ke recommendation.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class Recommendation implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String wellName;
    private final Action action;
    private final double currentChokeFraction;
    private final double recommendedChokeFraction;
    private final double recommendedLiftRate;
    private final double currentOil;
    private final double optimizedOil;
    private final double expectedUplift;
    private final String bindingConstraint;
    private final String lockReason;

    /**
     * Creates a recommendation.
     *
     * @param wellName well name
     * @param action recommended action
     * @param currentChokeFraction current choke opening in [0, 1]
     * @param recommendedChokeFraction recommended choke opening in [0, 1]
     * @param recommendedLiftRate recommended lift-gas rate in Sm3/day
     * @param currentOil current oil rate in Sm3/day
     * @param optimizedOil optimised oil rate in Sm3/day
     * @param expectedUplift expected oil uplift in Sm3/day
     * @param bindingConstraint binding constraint label
     * @param lockReason operational lock reason (may be empty)
     */
    public Recommendation(String wellName, Action action, double currentChokeFraction, double recommendedChokeFraction,
        double recommendedLiftRate, double currentOil, double optimizedOil, double expectedUplift,
        String bindingConstraint, String lockReason) {
      this.wellName = wellName;
      this.action = action;
      this.currentChokeFraction = currentChokeFraction;
      this.recommendedChokeFraction = recommendedChokeFraction;
      this.recommendedLiftRate = recommendedLiftRate;
      this.currentOil = currentOil;
      this.optimizedOil = optimizedOil;
      this.expectedUplift = expectedUplift;
      this.bindingConstraint = bindingConstraint;
      this.lockReason = lockReason;
    }

    /**
     * Returns the well name.
     *
     * @return well name
     */
    public String getWellName() {
      return wellName;
    }

    /**
     * Returns the recommended action.
     *
     * @return recommended action
     */
    public Action getAction() {
      return action;
    }

    /**
     * Returns the current choke opening.
     *
     * @return current opening in [0, 1]
     */
    public double getCurrentChokeFraction() {
      return currentChokeFraction;
    }

    /**
     * Returns the recommended choke opening.
     *
     * @return recommended opening in [0, 1]
     */
    public double getRecommendedChokeFraction() {
      return recommendedChokeFraction;
    }

    /**
     * Returns the recommended lift-gas rate.
     *
     * @return recommended lift-gas rate in Sm3/day
     */
    public double getRecommendedLiftRate() {
      return recommendedLiftRate;
    }

    /**
     * Returns the current oil rate.
     *
     * @return current oil rate in Sm3/day
     */
    public double getCurrentOil() {
      return currentOil;
    }

    /**
     * Returns the optimised oil rate.
     *
     * @return optimised oil rate in Sm3/day
     */
    public double getOptimizedOil() {
      return optimizedOil;
    }

    /**
     * Returns the expected oil uplift versus the current setting.
     *
     * @return expected uplift in Sm3/day
     */
    public double getExpectedUplift() {
      return expectedUplift;
    }

    /**
     * Returns the binding constraint label.
     *
     * @return binding constraint
     */
    public String getBindingConstraint() {
      return bindingConstraint;
    }

    /**
     * Returns the operational lock reason.
     *
     * @return lock reason, or an empty string
     */
    public String getLockReason() {
      return lockReason;
    }
  }
}
