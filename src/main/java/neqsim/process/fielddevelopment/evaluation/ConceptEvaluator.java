package neqsim.process.fielddevelopment.evaluation;

import java.util.Map;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.concept.ReservoirInput;
import neqsim.process.fielddevelopment.facility.FacilityBuilder;
import neqsim.process.fielddevelopment.facility.FacilityConfig;
import neqsim.process.fielddevelopment.economics.ProductionProfileGenerator;
import neqsim.process.fielddevelopment.economics.ProductionProfileGenerator.DeclineType;
import neqsim.process.fielddevelopment.screening.EconomicsEstimator;
import neqsim.process.fielddevelopment.screening.EconomicsEstimator.EconomicsReport;
import neqsim.process.fielddevelopment.screening.EmissionsTracker;
import neqsim.process.fielddevelopment.screening.EmissionsTracker.EmissionsReport;
import neqsim.process.fielddevelopment.screening.FlowAssuranceReport;
import neqsim.process.fielddevelopment.screening.FlowAssuranceResult;
import neqsim.process.fielddevelopment.screening.FlowAssuranceScreener;
import neqsim.process.fielddevelopment.screening.SafetyReport;
import neqsim.process.fielddevelopment.screening.SafetyScreener;

/**
 * Main orchestrator for field development concept evaluation.
 *
 * <p>
 * This class is the primary entry point for the Field Development Engine. It coordinates all
 * screening analyses (flow assurance, safety, emissions, economics) and aggregates results into a
 * comprehensive {@link ConceptKPIs} object for decision support.
 * </p>
 *
 * <h2>Evaluation Workflow</h2>
 * <p>
 * The evaluator runs the following analyses:
 * </p>
 * <ol>
 * <li><b>Production KPIs</b>: Calculate plateau rate, field life, and recovery estimates</li>
 * <li><b>Flow Assurance</b>: Screen hydrate, wax, corrosion, and other risks</li>
 * <li><b>Safety</b>: Assess blowdown requirements, ESD complexity, and H2S considerations</li>
 * <li><b>Emissions</b>: Calculate CO2 emissions and intensity</li>
 * <li><b>Economics</b>: Estimate CAPEX, OPEX, and unit costs</li>
 * <li><b>Scoring</b>: Generate technical, economic, and environmental scores</li>
 * </ol>
 *
 * <h2>Scoring System</h2>
 * <p>
 * The evaluator calculates normalized scores (0-1) for concept comparison:
 * </p>
 * <ul>
 * <li><b>Technical Score (35%)</b>: Based on flow assurance and safety outcomes</li>
 * <li><b>Economic Score (40%)</b>: Based on CAPEX relative to baseline</li>
 * <li><b>Environmental Score (25%)</b>: Based on CO2 intensity</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Full Evaluation</h3>
 *
 * <pre>
 * ConceptEvaluator evaluator = new ConceptEvaluator();
 * ConceptKPIs kpis = evaluator.evaluate(concept);
 *
 * // Access individual reports
 * FlowAssuranceReport fa = kpis.getFlowAssuranceReport();
 * EconomicsEstimator.EconomicsReport econ = kpis.getEconomicsReport();
 *
 * // Check warnings
 * kpis.getWarnings().forEach((category, message) -&gt; {
 *   System.out.println("WARNING: " + message);
 * });
 *
 * // Get overall score
 * System.out.println("Overall Score: " + kpis.getOverallScore());
 * </pre>
 *
 * <h3>Evaluation with Custom Facility</h3>
 *
 * <pre>
 * FacilityConfig facility =
 *     FacilityBuilder.builder().addBlock(BlockConfig.of(BlockType.INLET_SEPARATION))
 *         .addBlock(BlockConfig.of(BlockType.TEG_DEHYDRATION)).build();
 *
 * ConceptKPIs kpis = evaluator.evaluate(concept, facility);
 * </pre>
 *
 * <h3>Quick Screening (Reduced Fidelity)</h3>
 *
 * <pre>
 * ConceptKPIs quickKpis = evaluator.quickScreen(concept);
 * // Note: Safety assessment not included in quick screen
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is stateless and thread-safe. Multiple concepts can be evaluated concurrently using
 * the same evaluator instance, though thermodynamic database operations may require
 * synchronization.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see ConceptKPIs
 * @see BatchConceptRunner
 */
public class ConceptEvaluator {

  /** Flow assurance screener for hydrate, wax, and corrosion assessment. */
  private final FlowAssuranceScreener flowAssuranceScreener;

  /** Safety screener for blowdown, ESD, and H2S assessment. */
  private final SafetyScreener safetyScreener;

  /** Emissions tracker for CO2 calculations. */
  private final EmissionsTracker emissionsTracker;

  /** Economics estimator for CAPEX/OPEX calculations. */
  private final EconomicsEstimator economicsEstimator;

  /**
   * Creates a new concept evaluator with default screeners.
   *
   * <p>
   * This is the recommended constructor for typical usage. Each screener is instantiated with
   * default parameters suitable for screening-level analysis.
   * </p>
   */
  public ConceptEvaluator() {
    this.flowAssuranceScreener = new FlowAssuranceScreener();
    this.safetyScreener = new SafetyScreener();
    this.emissionsTracker = new EmissionsTracker();
    this.economicsEstimator = new EconomicsEstimator();
  }

  /**
   * Creates a concept evaluator with custom screeners.
   *
   * <p>
   * Use this constructor when you need to customize screener behavior, such as using different cost
   * factors or risk thresholds.
   * </p>
   *
   * @param flowAssuranceScreener custom flow assurance screener
   * @param safetyScreener custom safety screener
   * @param emissionsTracker custom emissions tracker
   * @param economicsEstimator custom economics estimator
   */
  public ConceptEvaluator(FlowAssuranceScreener flowAssuranceScreener,
      SafetyScreener safetyScreener, EmissionsTracker emissionsTracker,
      EconomicsEstimator economicsEstimator) {
    this.flowAssuranceScreener = flowAssuranceScreener;
    this.safetyScreener = safetyScreener;
    this.emissionsTracker = emissionsTracker;
    this.economicsEstimator = economicsEstimator;
  }

  /**
   * Evaluates a concept with auto-generated facility configuration.
   *
   * <p>
   * The facility configuration is automatically generated based on the concept's reservoir
   * properties and processing requirements. This is suitable for initial screening when detailed
   * facility design is not yet available.
   * </p>
   *
   * @param concept field concept to evaluate (must not be null)
   * @return comprehensive concept KPIs including all screening results
   * @see FacilityBuilder#autoGenerate(FieldConcept)
   */
  public ConceptKPIs evaluate(FieldConcept concept) {
    // Auto-generate facility configuration
    FacilityConfig facilityConfig = FacilityBuilder.autoGenerate(concept).build();
    return evaluate(concept, facilityConfig);
  }

  /**
   * Evaluates a concept with a provided facility configuration.
   *
   * <p>
   * Use this method when you have a specific facility design to evaluate, such as when comparing
   * different processing configurations for the same reservoir.
   * </p>
   *
   * @param concept field concept to evaluate (must not be null)
   * @param facilityConfig facility configuration defining process blocks
   * @return comprehensive concept KPIs including all screening results
   */
  public ConceptKPIs evaluate(FieldConcept concept, FacilityConfig facilityConfig) {
    ConceptKPIs.Builder builder = ConceptKPIs.builder(concept.getName());

    // Production estimates
    calculateProductionKPIs(builder, concept);

    // Run flow assurance screening
    FlowAssuranceReport faReport = flowAssuranceScreener.quickScreen(concept);
    builder.flowAssuranceReport(faReport);
    builder.flowAssuranceOverall(faReport.getOverallResult());
    builder.hydrateMargin(faReport.getHydrateMarginC());
    builder.waxMargin(faReport.getWaxMarginC());

    // Run safety screening
    SafetyReport safetyReport = safetyScreener.screen(concept, facilityConfig);
    builder.safetyReport(safetyReport);
    builder.safetyLevel(safetyReport.getOverallLevel());
    builder.blowdownTime(safetyReport.getEstimatedBlowdownTimeMinutes());
    builder.minMetalTemp(safetyReport.getMinimumMetalTempC());

    // Run emissions tracking
    EmissionsReport emissionsReport = emissionsTracker.estimate(concept, facilityConfig);
    builder.emissionsReport(emissionsReport);
    builder.co2Intensity(emissionsReport.getIntensityKgCO2PerBoe());
    builder.annualEmissions(emissionsReport.getTotalEmissionsTonnesPerYear());
    builder.emissionsClass(emissionsReport.getIntensityClass());

    // Run economics estimation
    EconomicsReport economicsReport = economicsEstimator.estimate(concept, facilityConfig);
    builder.economicsReport(economicsReport);
    builder.totalCapex(economicsReport.getTotalCapexMUSD());
    builder.annualOpex(economicsReport.getAnnualOpexMUSD());

    // Calculate scores
    calculateScores(builder, faReport, safetyReport, emissionsReport, economicsReport);

    // Add warnings for issues
    addWarnings(builder, faReport, safetyReport, emissionsReport);

    return builder.build();
  }

  /**
   * Performs quick screening without full facility evaluation.
   *
   * <p>
   * This method provides a faster, lower-fidelity evaluation that skips safety screening and uses
   * simplified facility assumptions. It's useful for:
   * </p>
   * <ul>
   * <li>Initial concept filtering before detailed analysis</li>
   * <li>Rapid sensitivity studies with many variations</li>
   * <li>Early-phase feasibility checks</li>
   * </ul>
   *
   * <p>
   * <b>Note:</b> The results are marked with a "fidelity" note indicating reduced accuracy. Safety
   * reports are not included in quick screening.
   * </p>
   *
   * @param concept field concept to screen
   * @return concept KPIs with reduced fidelity (no safety assessment)
   */
  public ConceptKPIs quickScreen(FieldConcept concept) {
    ConceptKPIs.Builder builder = ConceptKPIs.builder(concept.getName());

    // Production estimates
    calculateProductionKPIs(builder, concept);

    // Quick screens
    FlowAssuranceReport faReport = flowAssuranceScreener.quickScreen(concept);
    builder.flowAssuranceReport(faReport);
    builder.flowAssuranceOverall(faReport.getOverallResult());
    builder.hydrateMargin(faReport.getHydrateMarginC());

    EmissionsReport emissionsReport = emissionsTracker.quickEstimate(concept);
    builder.emissionsReport(emissionsReport);
    builder.co2Intensity(emissionsReport.getIntensityKgCO2PerBoe());
    builder.emissionsClass(emissionsReport.getIntensityClass());

    EconomicsReport economicsReport = economicsEstimator.quickEstimate(concept);
    builder.economicsReport(economicsReport);
    builder.totalCapex(economicsReport.getTotalCapexMUSD());

    builder.addNote("fidelity", "Quick screening - reduced fidelity");

    return builder.build();
  }

  /**
   * Calculates production KPIs from a ramp-up, plateau, and Arps decline forecast.
   *
   * @param builder KPI builder to update
   * @param concept concept to evaluate
   */
  private void calculateProductionKPIs(ConceptKPIs.Builder builder, FieldConcept concept) {
    ReservoirInput reservoir = concept.getReservoir();
    boolean gasConcept = isGasConcept(reservoir);
    double peakRatePerDay = getPeakRatePerDay(concept, gasConcept);
    if (peakRatePerDay <= 0.0) {
      builder.fieldLife(0.0);
      builder.estimatedRecovery(0.0);
      builder.addWarning("production", "No producer rate specified for production forecast");
      return;
    }

    double plateauRateMsm3d =
        gasConcept ? peakRatePerDay / 1.0e6 : peakRatePerDay * 0.158987 / 1.0e6;
    builder.plateauRate(plateauRateMsm3d);

    ProductionProfileGenerator generator = new ProductionProfileGenerator();
    Map<Integer, Double> profile =
        generator.generateFullProfile(peakRatePerDay, 2, 5, gasConcept ? 0.12 : 0.15, 0.5,
            gasConcept ? DeclineType.EXPONENTIAL : DeclineType.HYPERBOLIC, 2026, 30,
            peakRatePerDay * 0.05);

    double targetRecoverable = reservoir != null ? reservoir.getRecoverableResourceEstimate() : 0.0;
    String resourceUnit = reservoir != null ? reservoir.getResourceUnit() : "";
    double cumulativeInResourceUnit =
        calculateCumulativeInResourceUnit(profile, gasConcept, resourceUnit, targetRecoverable);
    double resourceEstimate = reservoir != null ? reservoir.getResourceEstimate() : 0.0;
    double recoveryPercent = resourceEstimate > 0.0
        ? Math.min(cumulativeInResourceUnit / resourceEstimate * 100.0, 100.0)
        : defaultRecoveryFactor(reservoir) * 100.0;

    builder.fieldLife(estimateFieldLifeYears(profile, gasConcept, resourceUnit, targetRecoverable));
    builder.estimatedRecovery(recoveryPercent);
    builder.addNote("production_forecast",
        String.format("Arps forecast with 2-year ramp-up, 5-year plateau, %.0f%% recovery target",
            recoveryPercent));
  }

  /**
   * Checks whether a concept should be treated as gas-dominated.
   *
   * @param reservoir reservoir input, or null
   * @return true if the concept is gas-dominated
   */
  private boolean isGasConcept(ReservoirInput reservoir) {
    if (reservoir == null) {
      return true;
    }
    return reservoir.getFluidType() == ReservoirInput.FluidType.LEAN_GAS
        || reservoir.getFluidType() == ReservoirInput.FluidType.RICH_GAS
        || reservoir.getFluidType() == ReservoirInput.FluidType.GAS_CONDENSATE;
  }

  /**
   * Gets the peak production rate in profile units.
   *
   * @param concept field concept
   * @param gasConcept true for gas concepts
   * @return gas rate in Sm3/d or oil rate in bbl/d
   */
  private double getPeakRatePerDay(FieldConcept concept, boolean gasConcept) {
    if (concept.getWells() == null) {
      return 0.0;
    }
    double ratePerWell = concept.getWells().getRatePerWell();
    String unit = concept.getWells().getRateUnit();
    double totalRate = ratePerWell * concept.getWells().getProducerCount();
    if (gasConcept) {
      if (unit != null && unit.toLowerCase().contains("msm3")) {
        return totalRate * 1.0e6;
      }
      return concept.getWells().getRatePerWellSm3d() * concept.getWells().getProducerCount();
    }
    if (unit != null
        && (unit.toLowerCase().contains("bbl") || unit.toLowerCase().contains("bopd"))) {
      return totalRate;
    }
    return totalRate / 0.158987;
  }

  /**
   * Calculates cumulative production in the resource unit.
   *
   * @param profile annual production profile
   * @param gasConcept true for gas concepts
   * @param resourceUnit resource unit
   * @param targetRecoverable target recoverable resource in the resource unit
   * @return cumulative production in the resource unit
   */
  private double calculateCumulativeInResourceUnit(Map<Integer, Double> profile, boolean gasConcept,
      String resourceUnit, double targetRecoverable) {
    double cumulative = 0.0;
    for (Double annualVolume : profile.values()) {
      cumulative += convertAnnualVolumeToResourceUnit(annualVolume, gasConcept, resourceUnit);
      if (targetRecoverable > 0.0 && cumulative >= targetRecoverable) {
        return targetRecoverable;
      }
    }
    return cumulative;
  }

  /**
   * Estimates field life from the profile and recoverable-resource target.
   *
   * @param profile annual production profile
   * @param gasConcept true for gas concepts
   * @param resourceUnit resource unit
   * @param targetRecoverable target recoverable resource in the resource unit
   * @return field life in years
   */
  private double estimateFieldLifeYears(Map<Integer, Double> profile, boolean gasConcept,
      String resourceUnit, double targetRecoverable) {
    if (targetRecoverable <= 0.0) {
      return profile.size();
    }
    double cumulative = 0.0;
    int years = 0;
    for (Double annualVolume : profile.values()) {
      cumulative += convertAnnualVolumeToResourceUnit(annualVolume, gasConcept, resourceUnit);
      years++;
      if (cumulative >= targetRecoverable) {
        return years;
      }
    }
    return years;
  }

  /**
   * Converts an annual profile volume to the reservoir resource unit.
   *
   * @param annualVolume annual volume in Sm3 for gas or bbl for oil
   * @param gasConcept true for gas concepts
   * @param resourceUnit resource unit
   * @return annual volume in the resource unit
   */
  private double convertAnnualVolumeToResourceUnit(double annualVolume, boolean gasConcept,
      String resourceUnit) {
    if (resourceUnit == null) {
      return annualVolume;
    }
    String normalizedUnit = resourceUnit.toLowerCase();
    if (gasConcept) {
      if (normalizedUnit.contains("gsm3")) {
        return annualVolume / 1.0e9;
      }
      if (normalizedUnit.contains("msm3")) {
        return annualVolume / 1.0e6;
      }
      if (normalizedUnit.contains("mmboe")) {
        return annualVolume / 1000.0 / 1.0e6;
      }
      return annualVolume;
    }
    if (normalizedUnit.contains("mmbbl")) {
      return annualVolume / 1.0e6;
    }
    if (normalizedUnit.contains("mmboe")) {
      return annualVolume / 1.0e6;
    }
    if (normalizedUnit.contains("sm3") || normalizedUnit.contains("m3")) {
      return annualVolume * 0.158987;
    }
    return annualVolume;
  }

  /**
   * Gets a default recovery factor when no explicit resource estimate exists.
   *
   * @param reservoir reservoir input, or null
   * @return default recovery factor as a fraction from 0 to 1
   */
  private double defaultRecoveryFactor(ReservoirInput reservoir) {
    if (reservoir == null) {
      return 0.60;
    }
    switch (reservoir.getFluidType()) {
      case LEAN_GAS:
      case RICH_GAS:
        return 0.75;
      case GAS_CONDENSATE:
        return 0.65;
      case BLACK_OIL:
      case VOLATILE_OIL:
        return 0.45;
      case HEAVY_OIL:
        return 0.25;
      default:
        return 0.55;
    }
  }

  private void calculateScores(ConceptKPIs.Builder builder, FlowAssuranceReport faReport,
      SafetyReport safetyReport, EmissionsReport emissionsReport, EconomicsReport economicsReport) {
    // Technical score (0-1) based on flow assurance and safety
    double technicalScore = 1.0;
    switch (faReport.getOverallResult()) {
      case FAIL:
        technicalScore -= 0.4;
        break;
      case MARGINAL:
        technicalScore -= 0.15;
        break;
      default:
        break;
    }
    switch (safetyReport.getOverallLevel()) {
      case HIGH:
        technicalScore -= 0.3;
        break;
      case ENHANCED:
        technicalScore -= 0.1;
        break;
      default:
        break;
    }
    builder.technicalScore(Math.max(0, technicalScore));

    // Economic score based on CAPEX (lower is better, normalized)
    // Assume 500 MUSD is baseline, score decreases as CAPEX increases
    double capex = economicsReport.getTotalCapexMUSD();
    double economicScore = Math.max(0, 1.0 - (capex - 500) / 2000.0);
    economicScore = Math.min(1.0, economicScore);
    builder.economicScore(economicScore);

    // Environmental score based on emissions intensity
    double intensity = emissionsReport.getIntensityKgCO2PerBoe();
    double envScore;
    if (intensity < 10) {
      envScore = 1.0;
    } else if (intensity < 20) {
      envScore = 0.8;
    } else if (intensity < 30) {
      envScore = 0.6;
    } else {
      envScore = 0.4;
    }
    builder.environmentalScore(envScore);

    // Overall weighted score
    double overall = 0.35 * technicalScore + 0.40 * economicScore + 0.25 * envScore;
    builder.overallScore(overall);
  }

  private void addWarnings(ConceptKPIs.Builder builder, FlowAssuranceReport faReport,
      SafetyReport safetyReport, EmissionsReport emissionsReport) {
    if (faReport.getOverallResult() == FlowAssuranceResult.FAIL) {
      builder.addWarning("flow_assurance", "Flow assurance FAIL - mitigation measures mandatory");
    } else if (faReport.getOverallResult() == FlowAssuranceResult.MARGINAL) {
      builder.addWarning("flow_assurance", "Flow assurance marginal - detailed study recommended");
    }

    if (safetyReport.getOverallLevel() == SafetyReport.SafetyLevel.HIGH) {
      builder.addWarning("safety", "High safety complexity - comprehensive safety case required");
    }

    if (safetyReport.isH2sPresent()) {
      builder.addWarning("h2s", "H2S present - toxic gas design requirements apply");
    }

    if (!safetyReport.meetsBlowdownTarget()) {
      builder.addWarning("blowdown",
          "Blowdown time exceeds 15-minute target - segmentation may be needed");
    }

    if (emissionsReport.getIntensityKgCO2PerBoe() > 25) {
      builder.addWarning("emissions", "High CO2 intensity - consider power from shore or CCS");
    }
  }
}
