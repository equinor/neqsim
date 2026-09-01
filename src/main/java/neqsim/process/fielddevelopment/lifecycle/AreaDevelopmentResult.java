package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.fielddevelopment.lifecycle.FieldProductSpecifications.ViolationAction;

/** Ranked technical/economic results for an area-development portfolio. */
public final class AreaDevelopmentResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Evaluated route with its full lifecycle result. */
  public static final class OptionResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final AreaDevelopmentOption option;
    private final FieldLifecycleResult lifecycleResult;

    OptionResult(AreaDevelopmentOption option, FieldLifecycleResult lifecycleResult) {
      this.option = option;
      this.lifecycleResult = lifecycleResult;
    }

    /** Returns option definition and route metadata. */
    public AreaDevelopmentOption getOption() {
      return option;
    }

    /** Returns full reservoir-to-economics lifecycle result. */
    public FieldLifecycleResult getLifecycleResult() {
      return lifecycleResult;
    }

    /** Returns whether the option remains eligible under its product-specification policy. */
    public boolean isEligible() {
      FieldProductSpecifications specifications = option.getLifecycleConcept().getConfiguration()
          .getProductSpecifications();
      return specifications == null || specifications.getViolationAction() != ViolationAction.REJECT_OPTION
          || lifecycleResult.areAllProductSpecificationsMet();
    }
  }

  private final String areaName;
  private final List<OptionResult> rankedOptions;

  AreaDevelopmentResult(String areaName, List<OptionResult> rankedOptions) {
    this.areaName = areaName;
    this.rankedOptions = Collections.unmodifiableList(new ArrayList<OptionResult>(rankedOptions));
  }

  /** Returns area or discovery name. */
  public String getAreaName() {
    return areaName;
  }

  /** Returns options ranked by eligibility and NPV. */
  public List<OptionResult> getRankedOptions() {
    return rankedOptions;
  }

  /** Returns highest-NPV eligible option, or null when every option is rejected. */
  public OptionResult getRecommendedOption() {
    for (OptionResult result : rankedOptions) {
      if (result.isEligible()) {
        return result;
      }
    }
    return null;
  }

  /** Returns a Markdown comparison including routing, economics, capacity, and quality compliance. */
  public String toMarkdownTable() {
    StringBuilder table = new StringBuilder();
    table.append("| Option | Route | Receiving asset | Eligible | NPV (MUSD) | Break-even oil (USD/bbl) ");
    table.append("| Oil (MSm3) | Deferred oil (MSm3) | Peak operating utilization (%) ");
    table.append("| Peak requested utilization (%) | Off-spec years |\n");
    table.append("|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
    for (OptionResult optionResult : rankedOptions) {
      AreaDevelopmentOption option = optionResult.getOption();
      FieldLifecycleResult result = optionResult.getLifecycleResult();
      table.append(
          String.format("| %s | %s | %s | %s | %.0f | %.1f | %.1f | %.1f | %.1f | %.1f | %d |%n", option.getName(),
              option.getRouteType(), option.getReceivingAssetName(), optionResult.isEligible() ? "yes" : "no",
              result.getNpvMusd(), result.getBreakevenOilPriceUsdPerBbl(), result.getCumulativeOilSm3() / 1.0e6,
              result.getCumulativeDeferredOilSm3() / 1.0e6, result.getPeakFacilityUtilization() * 100.0,
              result.getPeakUnconstrainedFacilityUtilization() * 100.0, result.getOffSpecificationYears()));
    }
    return table.toString();
  }
}
