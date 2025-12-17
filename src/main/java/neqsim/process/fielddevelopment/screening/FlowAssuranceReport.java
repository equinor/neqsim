package neqsim.process.fielddevelopment.screening;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Detailed results from flow assurance screening.
 *
 * <p>
 * Contains individual pass/marginal/fail assessments for each flow assurance concern along with
 * margins and recommended mitigations.
 *
 * @author ESOL
 * @version 1.0
 */
public final class FlowAssuranceReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final FlowAssuranceResult hydrateResult;
  private final FlowAssuranceResult waxResult;
  private final FlowAssuranceResult asphalteneResult;
  private final FlowAssuranceResult corrosionResult;
  private final FlowAssuranceResult scalingResult;
  private final FlowAssuranceResult erosionResult;

  private final double hydrateMarginC;
  private final double waxMarginC;
  private final double minOperatingTempC;
  private final double hydrateFormationTempC;
  private final double waxAppearanceTempC;

  private final Map<String, String> recommendations;
  private final Map<String, String> mitigationOptions;

  private FlowAssuranceReport(Builder builder) {
    this.hydrateResult = builder.hydrateResult;
    this.waxResult = builder.waxResult;
    this.asphalteneResult = builder.asphalteneResult;
    this.corrosionResult = builder.corrosionResult;
    this.scalingResult = builder.scalingResult;
    this.erosionResult = builder.erosionResult;
    this.hydrateMarginC = builder.hydrateMarginC;
    this.waxMarginC = builder.waxMarginC;
    this.minOperatingTempC = builder.minOperatingTempC;
    this.hydrateFormationTempC = builder.hydrateFormationTempC;
    this.waxAppearanceTempC = builder.waxAppearanceTempC;
    this.recommendations = new LinkedHashMap<>(builder.recommendations);
    this.mitigationOptions = new LinkedHashMap<>(builder.mitigationOptions);
  }

  /**
   * Creates a new builder.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Gets the overall combined result (worst case of all parameters).
   *
   * @return overall result
   */
  public FlowAssuranceResult getOverallResult() {
    FlowAssuranceResult result = hydrateResult;
    result = result.combine(waxResult);
    result = result.combine(asphalteneResult);
    result = result.combine(corrosionResult);
    result = result.combine(scalingResult);
    result = result.combine(erosionResult);
    return result;
  }

  /**
   * Checks if all flow assurance parameters pass.
   *
   * @return true if all pass
   */
  public boolean allPass() {
    return getOverallResult() == FlowAssuranceResult.PASS;
  }

  /**
   * Checks if any parameter fails.
   *
   * @return true if any fails
   */
  public boolean anyFail() {
    return getOverallResult() == FlowAssuranceResult.FAIL;
  }

  // Individual results
  public FlowAssuranceResult getHydrateResult() {
    return hydrateResult;
  }

  public FlowAssuranceResult getWaxResult() {
    return waxResult;
  }

  public FlowAssuranceResult getAsphalteneResult() {
    return asphalteneResult;
  }

  public FlowAssuranceResult getCorrosionResult() {
    return corrosionResult;
  }

  public FlowAssuranceResult getScalingResult() {
    return scalingResult;
  }

  public FlowAssuranceResult getErosionResult() {
    return erosionResult;
  }

  // Margins and temperatures
  public double getHydrateMarginC() {
    return hydrateMarginC;
  }

  public double getWaxMarginC() {
    return waxMarginC;
  }

  public double getMinOperatingTempC() {
    return minOperatingTempC;
  }

  public double getHydrateFormationTempC() {
    return hydrateFormationTempC;
  }

  public double getWaxAppearanceTempC() {
    return waxAppearanceTempC;
  }

  public Map<String, String> getRecommendations() {
    return new LinkedHashMap<>(recommendations);
  }

  public Map<String, String> getMitigationOptions() {
    return new LinkedHashMap<>(mitigationOptions);
  }

  /**
   * Gets a summary suitable for reporting.
   *
   * @return summary string
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Flow Assurance Assessment: ").append(getOverallResult().getDisplayName())
        .append("\n");
    sb.append("  Hydrate: ").append(hydrateResult.getDisplayName());
    sb.append(" (margin: ").append(String.format("%.1f", hydrateMarginC)).append("°C)\n");
    sb.append("  Wax: ").append(waxResult.getDisplayName());
    sb.append(" (margin: ").append(String.format("%.1f", waxMarginC)).append("°C)\n");
    sb.append("  Asphaltene: ").append(asphalteneResult.getDisplayName()).append("\n");
    sb.append("  Corrosion: ").append(corrosionResult.getDisplayName()).append("\n");
    sb.append("  Scaling: ").append(scalingResult.getDisplayName()).append("\n");
    sb.append("  Erosion: ").append(erosionResult.getDisplayName()).append("\n");
    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("FlowAssuranceReport[overall=%s, hydrate=%s(%.1f°C), wax=%s(%.1f°C)]",
        getOverallResult(), hydrateResult, hydrateMarginC, waxResult, waxMarginC);
  }

  /**
   * Builder for FlowAssuranceReport.
   */
  public static final class Builder {
    private FlowAssuranceResult hydrateResult = FlowAssuranceResult.PASS;
    private FlowAssuranceResult waxResult = FlowAssuranceResult.PASS;
    private FlowAssuranceResult asphalteneResult = FlowAssuranceResult.PASS;
    private FlowAssuranceResult corrosionResult = FlowAssuranceResult.PASS;
    private FlowAssuranceResult scalingResult = FlowAssuranceResult.PASS;
    private FlowAssuranceResult erosionResult = FlowAssuranceResult.PASS;
    private double hydrateMarginC = Double.NaN;
    private double waxMarginC = Double.NaN;
    private double minOperatingTempC = Double.NaN;
    private double hydrateFormationTempC = Double.NaN;
    private double waxAppearanceTempC = Double.NaN;
    private final Map<String, String> recommendations = new LinkedHashMap<>();
    private final Map<String, String> mitigationOptions = new LinkedHashMap<>();

    public Builder hydrateResult(FlowAssuranceResult result) {
      this.hydrateResult = result;
      return this;
    }

    public Builder waxResult(FlowAssuranceResult result) {
      this.waxResult = result;
      return this;
    }

    public Builder asphalteneResult(FlowAssuranceResult result) {
      this.asphalteneResult = result;
      return this;
    }

    public Builder corrosionResult(FlowAssuranceResult result) {
      this.corrosionResult = result;
      return this;
    }

    public Builder scalingResult(FlowAssuranceResult result) {
      this.scalingResult = result;
      return this;
    }

    public Builder erosionResult(FlowAssuranceResult result) {
      this.erosionResult = result;
      return this;
    }

    public Builder hydrateMargin(double marginC) {
      this.hydrateMarginC = marginC;
      return this;
    }

    public Builder waxMargin(double marginC) {
      this.waxMarginC = marginC;
      return this;
    }

    public Builder minOperatingTemp(double tempC) {
      this.minOperatingTempC = tempC;
      return this;
    }

    public Builder hydrateFormationTemp(double tempC) {
      this.hydrateFormationTempC = tempC;
      return this;
    }

    public Builder waxAppearanceTemp(double tempC) {
      this.waxAppearanceTempC = tempC;
      return this;
    }

    public Builder addRecommendation(String key, String recommendation) {
      this.recommendations.put(key, recommendation);
      return this;
    }

    public Builder addMitigationOption(String key, String option) {
      this.mitigationOptions.put(key, option);
      return this;
    }

    public FlowAssuranceReport build() {
      return new FlowAssuranceReport(this);
    }
  }
}
