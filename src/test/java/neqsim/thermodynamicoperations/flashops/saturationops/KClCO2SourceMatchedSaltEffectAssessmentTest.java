package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Compares same-source KCl salting effects against matched model pure-water states.
 *
 * <p>
 * Mousavi-Chapoy-Burgass Table A1 pure-water values are interpolated only between bracketing source pressures at the
 * same temperature. Linear mole-fraction interpolation defines the primary source ratio; log interpolation reports form
 * sensitivity. The model salt response is decomposed exactly into guest-fugacity and effective aqueous f/x
 * contributions. This diagnostic fits no parameter and changes no production equation.
 * </p>
 *
 * @author OpenAI
 * @version 1.0
 */
class KClCO2SourceMatchedSaltEffectAssessmentTest {
  private static final double KCL_MOLAR_MASS_KG_PER_MOL = 0.0745513;
  private static final double CO2_FEED_MOLES = 10.0;
  private static final String OUTPUT = "target/co2-kcl-source-matched-salt-effect-assessment.csv";

  /**
   * Builds source-matched salt ratios and preserves the model contribution identity.
   *
   * @throws Exception if a source fixture, model state or diagnostic output cannot be read or written
   */
  @Tag("slow")
  @Test
  void sameStudyPureWaterSeparatesSaltEffectFromAbsoluteBaseline() throws Exception {
    List<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference> kclReferences = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
        .loadReferences();
    List<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference> waterReferences = WaterNaClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
        .loadReferences();
    List<String> rows = new ArrayList<String>();
    rows.add(header());
    KClCO2SaltPairTransferAssessmentTest.ResidualSaltPairLaw residualLaw = KClCO2SaltPairTransferAssessmentTest
        .fitResidualSaltPairLaw();
    Summary at323 = new Summary();
    Summary at373 = new Summary();
    Summary aggregate = new Summary();
    int selected = 0;
    for (KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference : kclReferences) {
      SourceInterpolation sourceWater = interpolateSourceWater(reference, waterReferences);
      if (sourceWater == null) {
        continue;
      }
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result brine = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(reference, CO2_FEED_MOLES);
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(reference.withoutSalt(), brine.co2FeedMoles);
      assertTrue(brine.componentBalanceResidualMol <= 1.0e-10, reference.pointId + " brine component balance");
      assertTrue(pureWater.componentBalanceResidualMol <= 1.0e-10, reference.pointId + " pure-water component balance");
      assertTrue(brine.molecularLogFugacityResidual < 1.0e-8, reference.pointId + " brine molecular fugacity closure");
      assertTrue(pureWater.molecularLogFugacityResidual < 1.0e-8,
          reference.pointId + " pure-water molecular fugacity closure");

      double sourceSalt = Math.log(sourceWater.linearMolePercent / reference.sourceCo2MolePercent);
      double sourceSaltLogInterpolation = Math.log(sourceWater.logMolePercent / reference.sourceCo2MolePercent);
      double modelSalt = Math.log(pureWater.molecularMolePercent / brine.molecularMolePercent);
      double fugacityRatio = brine.co2FugacityBara / pureWater.co2FugacityBara;
      double effectiveRatio = brine.effectiveFOverXBara() / pureWater.effectiveFOverXBara();
      double guestContribution = -Math.log(fugacityRatio);
      double aqueousContribution = Math.log(effectiveRatio);
      double decompositionResidual = modelSalt - guestContribution - aqueousContribution;
      double residual = modelSalt - sourceSalt;
      double molality = kclMolality(reference.kclMassFractionPercent);
      double candidateCorrection = reference.temperatureK == 323.15
          ? residualLaw.correctionAt(reference.temperatureK, molality)
          : Double.NaN;
      double candidateCorrectionStandardError = reference.temperatureK == 323.15
          ? residualLaw.standardErrorAt(reference.temperatureK, molality)
          : Double.NaN;
      double candidateSalt = modelSalt + candidateCorrection;
      double candidateResidual = candidateSalt - sourceSalt;
      assertTrue(sourceSalt > 0.0, reference.pointId + " same-source KCl salting-out direction");
      assertTrue(Math.abs(decompositionResidual) < 1.0e-12, reference.pointId + " contribution identity");
      if (Double.isFinite(candidateResidual)) {
        assertTrue(Math.abs(candidateResidual) < Math.abs(residual),
            reference.pointId + " complete residual-law transfer improves source-matched salt effect");
        assertTrue(candidateResidual > 0.0, reference.pointId + " transfer remains an under-correction");
        assertTrue(Math.abs(candidateCorrection) > 3.0 * candidateCorrectionStandardError,
            reference.pointId + " transferred correction exceeds three propagated standard errors");
      }

      Summary temperatureSummary = reference.temperatureK == 323.15 ? at323 : at373;
      temperatureSummary.add(residual, sourceWater.logSensitivity, guestContribution, aqueousContribution);
      aggregate.add(residual, sourceWater.logSensitivity, guestContribution, aqueousContribution);
      if (Double.isFinite(candidateResidual)) {
        temperatureSummary.addCandidate(candidateResidual);
        aggregate.addCandidate(candidateResidual);
      }
      rows.add(dataRow(reference, sourceWater, brine, pureWater, sourceSalt, sourceSaltLogInterpolation, modelSalt,
          residual, guestContribution, aqueousContribution, decompositionResidual, candidateCorrection,
          candidateCorrectionStandardError, candidateSalt, candidateResidual));
      selected++;
    }
    assertEquals(13, selected, "same-temperature source-water brackets without extrapolation");
    assertEquals(7, at323.count, "323.15 K bracketed states");
    assertEquals(6, at373.count, "373.15 K bracketed states");
    assertTrue(aggregate.maxInterpolationSensitivity < 0.025,
        "source interpolation-form sensitivity remains below 0.025 log units");
    assertEquals(7, at323.candidateCount, "323.15 K complete residual-law transfers");
    assertTrue(at323.candidateRmse() < at323.rmse(), "complete residual law improves the 323.15 K holdout RMSE");
    assertTrue(at323.candidateBias() > 0.0, "complete residual law does not close the dense-source discrepancy");
    rows.add(summaryRow("SUMMARY-323.15K", at323));
    rows.add(summaryRow("SUMMARY-373.15K", at373));
    rows.add(summaryRow("SUMMARY", aggregate));
    assertEquals(17, rows.size());
    int columns = rows.get(0).split(",", -1).length;
    for (String row : rows) {
      assertEquals(columns, row.split(",", -1).length, "stable evidence schema");
    }
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), rows, StandardCharsets.UTF_8);
  }

  /** Returns the stable evidence header. */
  private static String header() {
    return "record_type,point_id,temperature_K,total_pressure_MPa,kcl_mass_fraction_percent,"
        + "kcl_molality_mol_per_kg_water,source_water_lower_point,source_water_upper_point,"
        + "source_interpolation_fraction,source_water_co2_mole_percent_linear,"
        + "source_water_co2_mole_percent_log,source_interpolation_log_sensitivity,"
        + "source_brine_co2_mole_percent,source_log_salt_effect_linear,source_log_salt_effect_log_interpolation,"
        + "model_water_co2_mole_percent,model_brine_co2_mole_percent,model_log_salt_effect,"
        + "model_minus_source_log_salt_residual,he_morse_full_residual_correction,"
        + "he_morse_full_residual_correction_standard_error,candidate_log_salt_effect,"
        + "candidate_model_minus_source_log_salt_residual,brine_co2_fugacity_bara,pure_water_co2_fugacity_bara,"
        + "guest_fugacity_contribution,brine_effective_f_over_x_bara,pure_water_effective_f_over_x_bara,"
        + "aqueous_effective_f_over_x_contribution,decomposition_identity_residual,brine_water_activity,"
        + "pure_water_water_activity,brine_component_balance_residual_mol,brine_molecular_log_fugacity_residual,"
        + "pure_water_component_balance_residual_mol,pure_water_molecular_log_fugacity_residual,summary_count,"
        + "summary_residual_bias,summary_residual_rmse,summary_residual_max_abs,"
        + "summary_max_interpolation_log_sensitivity,summary_max_abs_guest_contribution,"
        + "summary_min_abs_aqueous_contribution,summary_max_guest_fraction,summary_candidate_count,"
        + "summary_candidate_residual_bias,"
        + "summary_candidate_residual_rmse,summary_candidate_residual_max_abs,disposition";
  }

  /** Formats one source-matched model row. */
  private static String dataRow(KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference,
      SourceInterpolation sourceWater, KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result brine,
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater, double sourceSalt,
      double sourceSaltLogInterpolation, double modelSalt, double residual, double guestContribution,
      double aqueousContribution, double decompositionResidual, double candidateCorrection,
      double candidateCorrectionStandardError, double candidateSalt, double candidateResidual) {
    return row("DATA", reference.pointId, reference.temperatureK, reference.totalPressureMpa,
        reference.kclMassFractionPercent, kclMolality(reference.kclMassFractionPercent), sourceWater.lowerPointId,
        sourceWater.upperPointId, sourceWater.fraction, sourceWater.linearMolePercent, sourceWater.logMolePercent,
        sourceWater.logSensitivity, reference.sourceCo2MolePercent, sourceSalt, sourceSaltLogInterpolation,
        pureWater.molecularMolePercent, brine.molecularMolePercent, modelSalt, residual, finite(candidateCorrection),
        finite(candidateCorrectionStandardError), finite(candidateSalt), finite(candidateResidual),
        brine.co2FugacityBara, pureWater.co2FugacityBara, guestContribution, brine.effectiveFOverXBara(),
        pureWater.effectiveFOverXBara(), aqueousContribution, decompositionResidual, brine.waterActivity,
        pureWater.waterActivity, brine.componentBalanceResidualMol, brine.molecularLogFugacityResidual,
        pureWater.componentBalanceResidualMol, pureWater.molecularLogFugacityResidual, null, null, null, null, null,
        null, null, null, null, null, null, null, "same_source_diagnostic_only_no_fit");
  }

  /** Formats one temperature or aggregate summary row. */
  private static String summaryRow(String label, Summary summary) {
    return row("SUMMARY", label, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, summary.count, summary.bias(), summary.rmse(), summary.maxAbsoluteResidual,
        summary.maxInterpolationSensitivity, summary.maxAbsoluteGuestContribution,
        summary.minAbsoluteAqueousContribution, summary.maxGuestFraction, summary.candidateCount,
        finite(summary.candidateBias()), finite(summary.candidateRmse()), finite(summary.candidateMax()),
        "source_matched_salt_effect_not_a_parameter_fit");
  }

  /** Returns a finite number or an empty CSV field marker. */
  private static Double finite(double value) {
    return Double.isFinite(value) ? value : null;
  }

  /** Finds a same-temperature pure-water source bracket without extrapolation. */
  private static SourceInterpolation interpolateSourceWater(
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference target,
      List<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference> candidates) {
    KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference lower = null;
    KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference upper = null;
    for (KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference candidate : candidates) {
      if (candidate.naclMassFractionPercent != 0.0 || candidate.temperatureK != target.temperatureK) {
        continue;
      }
      if (candidate.totalPressureMpa <= target.totalPressureMpa
          && (lower == null || candidate.totalPressureMpa > lower.totalPressureMpa)) {
        lower = candidate;
      }
      if (candidate.totalPressureMpa >= target.totalPressureMpa
          && (upper == null || candidate.totalPressureMpa < upper.totalPressureMpa)) {
        upper = candidate;
      }
    }
    if (lower == null || upper == null || upper.totalPressureMpa == lower.totalPressureMpa) {
      return null;
    }
    double fraction = (target.totalPressureMpa - lower.totalPressureMpa)
        / (upper.totalPressureMpa - lower.totalPressureMpa);
    double linear = lower.sourceCo2MolePercent + fraction * (upper.sourceCo2MolePercent - lower.sourceCo2MolePercent);
    double log = Math.exp(Math.log(lower.sourceCo2MolePercent)
        + fraction * (Math.log(upper.sourceCo2MolePercent) - Math.log(lower.sourceCo2MolePercent)));
    return new SourceInterpolation(lower.pointId, upper.pointId, fraction, linear, log,
        Math.abs(Math.log(linear / log)));
  }

  /** Converts anhydrous KCl mass fraction to source-basis molality. */
  private static double kclMolality(double massFractionPercent) {
    return massFractionPercent / (100.0 - massFractionPercent) / KCL_MOLAR_MASS_KG_PER_MOL;
  }

  /** Converts values to one locale-independent CSV row. */
  private static String row(Object... values) {
    List<String> fields = new ArrayList<String>();
    for (Object value : values) {
      fields.add(value == null ? "" : String.format(Locale.ROOT, "%s", value));
    }
    return String.join(",", fields);
  }

  /** Holds one source-water interpolation and its form sensitivity. */
  private static final class SourceInterpolation {
    private final String lowerPointId;
    private final String upperPointId;
    private final double fraction;
    private final double linearMolePercent;
    private final double logMolePercent;
    private final double logSensitivity;

    /** Creates one immutable source interpolation. */
    private SourceInterpolation(String lowerPointId, String upperPointId, double fraction, double linearMolePercent,
        double logMolePercent, double logSensitivity) {
      this.lowerPointId = lowerPointId;
      this.upperPointId = upperPointId;
      this.fraction = fraction;
      this.linearMolePercent = linearMolePercent;
      this.logMolePercent = logMolePercent;
      this.logSensitivity = logSensitivity;
    }
  }

  /** Accumulates source-matched residual and model-contribution diagnostics. */
  private static final class Summary {
    private int count;
    private double residualSum;
    private double residualSquareSum;
    private double maxAbsoluteResidual;
    private double maxInterpolationSensitivity;
    private double maxAbsoluteGuestContribution;
    private double minAbsoluteAqueousContribution = Double.POSITIVE_INFINITY;
    private double maxGuestFraction;
    private int candidateCount;
    private double candidateResidualSum;
    private double candidateResidualSquareSum;
    private double candidateMaxAbsoluteResidual;

    /** Adds one diagnostic row. */
    private void add(double residual, double interpolationSensitivity, double guestContribution,
        double aqueousContribution) {
      count++;
      residualSum += residual;
      residualSquareSum += residual * residual;
      maxAbsoluteResidual = Math.max(maxAbsoluteResidual, Math.abs(residual));
      maxInterpolationSensitivity = Math.max(maxInterpolationSensitivity, interpolationSensitivity);
      maxAbsoluteGuestContribution = Math.max(maxAbsoluteGuestContribution, Math.abs(guestContribution));
      minAbsoluteAqueousContribution = Math.min(minAbsoluteAqueousContribution, Math.abs(aqueousContribution));
      double contributionMagnitude = Math.abs(guestContribution) + Math.abs(aqueousContribution);
      maxGuestFraction = Math.max(maxGuestFraction, Math.abs(guestContribution) / contributionMagnitude);
    }

    /** Adds one independent candidate-transfer residual. */
    private void addCandidate(double candidateResidual) {
      candidateCount++;
      candidateResidualSum += candidateResidual;
      candidateResidualSquareSum += candidateResidual * candidateResidual;
      candidateMaxAbsoluteResidual = Math.max(candidateMaxAbsoluteResidual, Math.abs(candidateResidual));
    }

    /** Returns signed mean model-minus-source salt-effect residual. */
    private double bias() {
      return residualSum / count;
    }

    /** Returns root-mean-square model-minus-source salt-effect residual. */
    private double rmse() {
      return Math.sqrt(residualSquareSum / count);
    }

    /** Returns signed mean candidate model-minus-source residual, or NaN when not evaluated. */
    private double candidateBias() {
      return candidateCount == 0 ? Double.NaN : candidateResidualSum / candidateCount;
    }

    /** Returns candidate RMSE, or NaN when not evaluated. */
    private double candidateRmse() {
      return candidateCount == 0 ? Double.NaN : Math.sqrt(candidateResidualSquareSum / candidateCount);
    }

    /** Returns maximum absolute candidate residual, or NaN when not evaluated. */
    private double candidateMax() {
      return candidateCount == 0 ? Double.NaN : candidateMaxAbsoluteResidual;
    }
  }
}
