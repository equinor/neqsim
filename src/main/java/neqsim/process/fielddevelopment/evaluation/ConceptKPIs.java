package neqsim.process.fielddevelopment.evaluation;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.fielddevelopment.screening.EconomicsEstimator.EconomicsReport;
import neqsim.process.fielddevelopment.screening.EmissionsTracker.EmissionsReport;
import neqsim.process.fielddevelopment.screening.FlowAssuranceReport;
import neqsim.process.fielddevelopment.screening.FlowAssuranceResult;
import neqsim.process.fielddevelopment.screening.SafetyReport;

/**
 * Key Performance Indicators from concept evaluation.
 *
 * <p>
 * Aggregates all screening results into a single, comparable KPI set for concept ranking and
 * decision support.
 *
 * @author ESOL
 * @version 1.0
 */
public final class ConceptKPIs implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String conceptName;
  private final LocalDateTime evaluationTime;

  // Production KPIs
  private final double plateauRateMsm3d;
  private final double estimatedRecoveryPercent;
  private final double fieldLifeYears;

  // Economic KPIs
  private final double totalCapexMUSD;
  private final double annualOpexMUSD;
  private final double breakEvenOilPriceUSD;
  private final double npv10MUSD;

  // Flow assurance KPIs
  private final FlowAssuranceResult flowAssuranceOverall;
  private final double hydrateMarginC;
  private final double waxMarginC;

  // Safety KPIs
  private final SafetyReport.SafetyLevel safetyLevel;
  private final double blowdownTimeMinutes;
  private final double minMetalTempC;

  // Emissions KPIs
  private final double co2IntensityKgPerBoe;
  private final double annualEmissionsTonnes;
  private final String emissionsClass;

  // Aggregated scores
  private final double technicalScore;
  private final double economicScore;
  private final double environmentalScore;
  private final double overallScore;

  // Full reports
  private final FlowAssuranceReport flowAssuranceReport;
  private final SafetyReport safetyReport;
  private final EmissionsReport emissionsReport;
  private final EconomicsReport economicsReport;

  // Notes and warnings
  private final Map<String, String> notes;
  private final Map<String, String> warnings;

  private ConceptKPIs(Builder builder) {
    this.conceptName = builder.conceptName;
    this.evaluationTime = builder.evaluationTime;
    this.plateauRateMsm3d = builder.plateauRateMsm3d;
    this.estimatedRecoveryPercent = builder.estimatedRecoveryPercent;
    this.fieldLifeYears = builder.fieldLifeYears;
    this.totalCapexMUSD = builder.totalCapexMUSD;
    this.annualOpexMUSD = builder.annualOpexMUSD;
    this.breakEvenOilPriceUSD = builder.breakEvenOilPriceUSD;
    this.npv10MUSD = builder.npv10MUSD;
    this.flowAssuranceOverall = builder.flowAssuranceOverall;
    this.hydrateMarginC = builder.hydrateMarginC;
    this.waxMarginC = builder.waxMarginC;
    this.safetyLevel = builder.safetyLevel;
    this.blowdownTimeMinutes = builder.blowdownTimeMinutes;
    this.minMetalTempC = builder.minMetalTempC;
    this.co2IntensityKgPerBoe = builder.co2IntensityKgPerBoe;
    this.annualEmissionsTonnes = builder.annualEmissionsTonnes;
    this.emissionsClass = builder.emissionsClass;
    this.technicalScore = builder.technicalScore;
    this.economicScore = builder.economicScore;
    this.environmentalScore = builder.environmentalScore;
    this.overallScore = builder.overallScore;
    this.flowAssuranceReport = builder.flowAssuranceReport;
    this.safetyReport = builder.safetyReport;
    this.emissionsReport = builder.emissionsReport;
    this.economicsReport = builder.economicsReport;
    this.notes = new LinkedHashMap<>(builder.notes);
    this.warnings = new LinkedHashMap<>(builder.warnings);
  }

  public static Builder builder(String conceptName) {
    return new Builder(conceptName);
  }

  // Production KPIs
  public String getConceptName() {
    return conceptName;
  }

  public LocalDateTime getEvaluationTime() {
    return evaluationTime;
  }

  public double getPlateauRateMsm3d() {
    return plateauRateMsm3d;
  }

  public double getEstimatedRecoveryPercent() {
    return estimatedRecoveryPercent;
  }

  public double getFieldLifeYears() {
    return fieldLifeYears;
  }

  // Economic KPIs
  public double getTotalCapexMUSD() {
    return totalCapexMUSD;
  }

  public double getAnnualOpexMUSD() {
    return annualOpexMUSD;
  }

  public double getBreakEvenOilPriceUSD() {
    return breakEvenOilPriceUSD;
  }

  public double getNpv10MUSD() {
    return npv10MUSD;
  }

  // Flow assurance KPIs
  public FlowAssuranceResult getFlowAssuranceOverall() {
    return flowAssuranceOverall;
  }

  public double getHydrateMarginC() {
    return hydrateMarginC;
  }

  public double getWaxMarginC() {
    return waxMarginC;
  }

  // Safety KPIs
  public SafetyReport.SafetyLevel getSafetyLevel() {
    return safetyLevel;
  }

  public double getBlowdownTimeMinutes() {
    return blowdownTimeMinutes;
  }

  public double getMinMetalTempC() {
    return minMetalTempC;
  }

  // Emissions KPIs
  public double getCo2IntensityKgPerBoe() {
    return co2IntensityKgPerBoe;
  }

  public double getAnnualEmissionsTonnes() {
    return annualEmissionsTonnes;
  }

  public String getEmissionsClass() {
    return emissionsClass;
  }

  // Scores
  public double getTechnicalScore() {
    return technicalScore;
  }

  public double getEconomicScore() {
    return economicScore;
  }

  public double getEnvironmentalScore() {
    return environmentalScore;
  }

  public double getOverallScore() {
    return overallScore;
  }

  // Full reports
  public FlowAssuranceReport getFlowAssuranceReport() {
    return flowAssuranceReport;
  }

  public SafetyReport getSafetyReport() {
    return safetyReport;
  }

  public EmissionsReport getEmissionsReport() {
    return emissionsReport;
  }

  public EconomicsReport getEconomicsReport() {
    return economicsReport;
  }

  public Map<String, String> getNotes() {
    return new LinkedHashMap<>(notes);
  }

  public Map<String, String> getWarnings() {
    return new LinkedHashMap<>(warnings);
  }

  /**
   * Checks if this concept has any blocking issues.
   *
   * @return true if showstoppers exist
   */
  public boolean hasBlockingIssues() {
    return flowAssuranceOverall == FlowAssuranceResult.FAIL
        || safetyLevel == SafetyReport.SafetyLevel.HIGH;
  }

  /**
   * Gets a quick summary suitable for comparison tables.
   *
   * @return one-line summary
   */
  public String getOneLiner() {
    return String.format("%s: CAPEX=%.0fM, CO2=%.1f kg/boe, FA=%s, Score=%.0f%%", conceptName,
        totalCapexMUSD, co2IntensityKgPerBoe, flowAssuranceOverall.getDisplayName(),
        overallScore * 100);
  }

  /**
   * Gets detailed summary for reporting.
   *
   * @return multi-line summary
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== ").append(conceptName).append(" ===\n");
    sb.append("Evaluated: ").append(evaluationTime).append("\n\n");

    sb.append("PRODUCTION:\n");
    sb.append(String.format("  Plateau rate: %.2f MSm3/d\n", plateauRateMsm3d));
    sb.append(String.format("  Field life: %.0f years\n", fieldLifeYears));

    sb.append("\nECONOMICS:\n");
    sb.append(String.format("  CAPEX: %.0f MUSD\n", totalCapexMUSD));
    sb.append(String.format("  OPEX: %.1f MUSD/year\n", annualOpexMUSD));

    sb.append("\nFLOW ASSURANCE: ").append(flowAssuranceOverall.getDisplayName()).append("\n");
    sb.append(String.format("  Hydrate margin: %.1f°C\n", hydrateMarginC));
    sb.append(String.format("  Wax margin: %.1f°C\n", waxMarginC));

    sb.append("\nSAFETY: ").append(safetyLevel.getDisplayName()).append("\n");
    sb.append(String.format("  Blowdown: %.1f min\n", blowdownTimeMinutes));
    sb.append(String.format("  Min metal temp: %.0f°C\n", minMetalTempC));

    sb.append("\nEMISSIONS: ").append(emissionsClass).append("\n");
    sb.append(String.format("  Intensity: %.1f kg CO2e/boe\n", co2IntensityKgPerBoe));
    sb.append(String.format("  Annual: %.0f tonnes\n", annualEmissionsTonnes));

    sb.append("\nSCORES:\n");
    sb.append(String.format("  Technical: %.0f%%\n", technicalScore * 100));
    sb.append(String.format("  Economic: %.0f%%\n", economicScore * 100));
    sb.append(String.format("  Environmental: %.0f%%\n", environmentalScore * 100));
    sb.append(String.format("  OVERALL: %.0f%%\n", overallScore * 100));

    if (!warnings.isEmpty()) {
      sb.append("\nWARNINGS:\n");
      for (Map.Entry<String, String> entry : warnings.entrySet()) {
        sb.append("  ⚠ ").append(entry.getValue()).append("\n");
      }
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return getOneLiner();
  }

  /**
   * Builder for ConceptKPIs.
   */
  public static final class Builder {
    private final String conceptName;
    private LocalDateTime evaluationTime = LocalDateTime.now();
    private double plateauRateMsm3d;
    private double estimatedRecoveryPercent;
    private double fieldLifeYears = 20.0;
    private double totalCapexMUSD;
    private double annualOpexMUSD;
    private double breakEvenOilPriceUSD;
    private double npv10MUSD;
    private FlowAssuranceResult flowAssuranceOverall = FlowAssuranceResult.PASS;
    private double hydrateMarginC;
    private double waxMarginC;
    private SafetyReport.SafetyLevel safetyLevel = SafetyReport.SafetyLevel.STANDARD;
    private double blowdownTimeMinutes;
    private double minMetalTempC;
    private double co2IntensityKgPerBoe;
    private double annualEmissionsTonnes;
    private String emissionsClass = "MEDIUM";
    private double technicalScore;
    private double economicScore;
    private double environmentalScore;
    private double overallScore;
    private FlowAssuranceReport flowAssuranceReport;
    private SafetyReport safetyReport;
    private EmissionsReport emissionsReport;
    private EconomicsReport economicsReport;
    private final Map<String, String> notes = new LinkedHashMap<>();
    private final Map<String, String> warnings = new LinkedHashMap<>();

    public Builder(String conceptName) {
      this.conceptName = conceptName;
    }

    public Builder evaluationTime(LocalDateTime time) {
      this.evaluationTime = time;
      return this;
    }

    public Builder plateauRate(double msm3d) {
      this.plateauRateMsm3d = msm3d;
      return this;
    }

    public Builder estimatedRecovery(double percent) {
      this.estimatedRecoveryPercent = percent;
      return this;
    }

    public Builder fieldLife(double years) {
      this.fieldLifeYears = years;
      return this;
    }

    public Builder totalCapex(double musd) {
      this.totalCapexMUSD = musd;
      return this;
    }

    public Builder annualOpex(double musd) {
      this.annualOpexMUSD = musd;
      return this;
    }

    public Builder breakEvenPrice(double usd) {
      this.breakEvenOilPriceUSD = usd;
      return this;
    }

    public Builder npv10(double musd) {
      this.npv10MUSD = musd;
      return this;
    }

    public Builder flowAssuranceOverall(FlowAssuranceResult result) {
      this.flowAssuranceOverall = result;
      return this;
    }

    public Builder hydrateMargin(double c) {
      this.hydrateMarginC = c;
      return this;
    }

    public Builder waxMargin(double c) {
      this.waxMarginC = c;
      return this;
    }

    public Builder safetyLevel(SafetyReport.SafetyLevel level) {
      this.safetyLevel = level;
      return this;
    }

    public Builder blowdownTime(double minutes) {
      this.blowdownTimeMinutes = minutes;
      return this;
    }

    public Builder minMetalTemp(double c) {
      this.minMetalTempC = c;
      return this;
    }

    public Builder co2Intensity(double kgPerBoe) {
      this.co2IntensityKgPerBoe = kgPerBoe;
      return this;
    }

    public Builder annualEmissions(double tonnes) {
      this.annualEmissionsTonnes = tonnes;
      return this;
    }

    public Builder emissionsClass(String cls) {
      this.emissionsClass = cls;
      return this;
    }

    public Builder technicalScore(double score) {
      this.technicalScore = score;
      return this;
    }

    public Builder economicScore(double score) {
      this.economicScore = score;
      return this;
    }

    public Builder environmentalScore(double score) {
      this.environmentalScore = score;
      return this;
    }

    public Builder overallScore(double score) {
      this.overallScore = score;
      return this;
    }

    public Builder flowAssuranceReport(FlowAssuranceReport report) {
      this.flowAssuranceReport = report;
      return this;
    }

    public Builder safetyReport(SafetyReport report) {
      this.safetyReport = report;
      return this;
    }

    public Builder emissionsReport(EmissionsReport report) {
      this.emissionsReport = report;
      return this;
    }

    public Builder economicsReport(EconomicsReport report) {
      this.economicsReport = report;
      return this;
    }

    public Builder addNote(String key, String note) {
      this.notes.put(key, note);
      return this;
    }

    public Builder addWarning(String key, String warning) {
      this.warnings.put(key, warning);
      return this;
    }

    public ConceptKPIs build() {
      // Calculate overall score if not set
      if (overallScore == 0
          && (technicalScore > 0 || economicScore > 0 || environmentalScore > 0)) {
        overallScore = (technicalScore + economicScore + environmentalScore) / 3.0;
      }
      return new ConceptKPIs(this);
    }
  }
}
