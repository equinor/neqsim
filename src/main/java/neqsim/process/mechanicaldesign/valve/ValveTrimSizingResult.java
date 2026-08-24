package neqsim.process.mechanicaldesign.valve;

import java.io.Serializable;

/**
 * Result of comparing a required valve Cv with explicitly supplied trim capacity options.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ValveTrimSizingResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Assessment status. */
  public enum Status {
    /** No trim catalog or valid required Cv was available. */
    NOT_EVALUATED,
    /** A trim satisfying the configured utilization limit was selected. */
    FEASIBLE,
    /** No supplied trim satisfies the configured utilization limit. */
    NO_FEASIBLE_TRIM
  }

  private final Status status;
  private final double requiredCv;
  private final ValveTrimOption selectedTrimOption;
  private final ValveTrimOption limitingTrimOption;
  private final double maximumAvailableCv;
  private final double utilization;
  private final double capacityMarginCv;
  private final double maximumAllowedUtilization;
  private final String recommendation;

  private ValveTrimSizingResult(Status status, double requiredCv, ValveTrimOption selectedTrimOption,
      ValveTrimOption limitingTrimOption, double maximumAvailableCv, double utilization, double capacityMarginCv,
      double maximumAllowedUtilization, String recommendation) {
    this.status = status;
    this.requiredCv = requiredCv;
    this.selectedTrimOption = selectedTrimOption;
    this.limitingTrimOption = limitingTrimOption;
    this.maximumAvailableCv = maximumAvailableCv;
    this.utilization = utilization;
    this.capacityMarginCv = capacityMarginCv;
    this.maximumAllowedUtilization = maximumAllowedUtilization;
    this.recommendation = recommendation;
  }

  /**
   * Creates a result for a case that could not be evaluated.
   *
   * @param requiredCv calculated required Cv, possibly not finite
   * @param recommendation reason why the assessment was not performed
   * @return not-evaluated result
   */
  public static ValveTrimSizingResult notEvaluated(double requiredCv, String recommendation) {
    return notEvaluated(requiredCv, recommendation, 1.0);
  }

  /**
   * Creates a result for a case that could not be evaluated while retaining the configured utilization criterion.
   *
   * @param requiredCv calculated required Cv, possibly not finite
   * @param recommendation reason why the assessment was not performed
   * @param maximumAllowedUtilization configured utilization limit
   * @return not-evaluated result
   */
  public static ValveTrimSizingResult notEvaluated(double requiredCv, String recommendation,
      double maximumAllowedUtilization) {
    return new ValveTrimSizingResult(Status.NOT_EVALUATED, requiredCv, null, null, 0.0, 0.0, 0.0,
        maximumAllowedUtilization, recommendation);
  }

  /**
   * Creates a feasible trim selection result.
   *
   * @param requiredCv calculated required Cv
   * @param selectedTrimOption selected trim option
   * @param maximumAvailableCv largest Cv in the supplied catalog
   * @param maximumAllowedUtilization configured utilization limit
   * @return feasible result
   */
  public static ValveTrimSizingResult feasible(double requiredCv, ValveTrimOption selectedTrimOption,
      double maximumAvailableCv, double maximumAllowedUtilization) {
    double utilization = requiredCv / selectedTrimOption.getMaximumDesignCv();
    double margin = selectedTrimOption.getMaximumDesignCv() - requiredCv;
    String recommendation = "Selected the smallest supplied trim satisfying the configured Cv utilization limit";
    return new ValveTrimSizingResult(Status.FEASIBLE, requiredCv, selectedTrimOption, selectedTrimOption,
        maximumAvailableCv, utilization, margin, maximumAllowedUtilization, recommendation);
  }

  /**
   * Creates an infeasible result referenced to the largest supplied trim.
   *
   * @param requiredCv calculated required Cv
   * @param largestTrimOption largest supplied trim option
   * @param maximumAllowedUtilization configured utilization limit
   * @return infeasible result
   */
  public static ValveTrimSizingResult infeasible(double requiredCv, ValveTrimOption largestTrimOption,
      double maximumAllowedUtilization) {
    double utilization = requiredCv / largestTrimOption.getMaximumDesignCv();
    double margin = largestTrimOption.getMaximumDesignCv() - requiredCv;
    String recommendation = "No supplied trim satisfies the configured Cv utilization limit; "
        + "review the body/trim catalog or process design case";
    return new ValveTrimSizingResult(Status.NO_FEASIBLE_TRIM, requiredCv, null, largestTrimOption,
        largestTrimOption.getMaximumDesignCv(), utilization, margin, maximumAllowedUtilization, recommendation);
  }

  /**
   * Gets the assessment status.
   *
   * @return assessment status
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Gets the calculated required Cv.
   *
   * @return required Cv
   */
  public double getRequiredCv() {
    return requiredCv;
  }

  /**
   * Gets the automatically selected trim.
   *
   * @return selected trim, or {@code null} when no feasible trim exists
   */
  public ValveTrimOption getSelectedTrimOption() {
    return selectedTrimOption;
  }

  /**
   * Gets the option used to calculate the reported utilization and margin.
   *
   * <p>
   * This is the selected option for a feasible result and the largest available option for an infeasible result.
   * </p>
   *
   * @return limiting trim option, or {@code null} when not evaluated
   */
  public ValveTrimOption getLimitingTrimOption() {
    return limitingTrimOption;
  }

  /**
   * Gets the largest maximum design Cv in the supplied catalog.
   *
   * @return maximum available Cv, or zero when not evaluated
   */
  public double getMaximumAvailableCv() {
    return maximumAvailableCv;
  }

  /**
   * Gets Cv utilization relative to the limiting trim option.
   *
   * @return required Cv divided by the limiting trim maximum Cv
   */
  public double getUtilization() {
    return utilization;
  }

  /**
   * Gets the remaining Cv capacity of the limiting option.
   *
   * @return limiting maximum Cv minus required Cv; negative means overload
   */
  public double getCapacityMarginCv() {
    return capacityMarginCv;
  }

  /**
   * Gets the configured maximum allowed utilization used for selection.
   *
   * @return allowed utilization fraction
   */
  public double getMaximumAllowedUtilization() {
    return maximumAllowedUtilization;
  }

  /**
   * Gets the engineering recommendation.
   *
   * @return recommendation text
   */
  public String getRecommendation() {
    return recommendation;
  }

  /**
   * Checks whether a feasible trim was selected.
   *
   * @return {@code true} when the result is feasible
   */
  public boolean isFeasible() {
    return status == Status.FEASIBLE;
  }

  /**
   * Checks whether a trim catalog was evaluated.
   *
   * @return {@code true} for feasible and infeasible catalog assessments
   */
  public boolean isEvaluated() {
    return status != Status.NOT_EVALUATED;
  }
}
