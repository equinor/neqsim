package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Tests the frozen He-Morse residual law against independent Kamps KCl data. */
class KClCO2Kamps2007IndependentTransferAssessmentTest {
  private static final String RESOURCE = "/data/chemistry_benchmarks/co2_kcl_solubility_kamps2007_thermoml.csv";
  private static final String OUTPUT = "target/co2-kcl-kamps2007-independent-transfer-assessment.csv";
  private static final double KCL_MOLAR_MASS_KG_PER_MOL = 0.0745513;
  private static final double REFERENCE_TEMPERATURE_K = 298.15;
  private static final double LAMBDA_REFERENCE = -0.0097562362;
  private static final double LAMBDA_REFERENCE_STANDARD_ERROR = 0.0022933499;
  private static final double LAMBDA_INVERSE_TEMPERATURE = 0.4328598992;
  private static final double LAMBDA_INVERSE_TEMPERATURE_STANDARD_ERROR = 0.0486611208;
  private static final double ZETA_INVERSE_TEMPERATURE = -0.0922848363;
  private static final double ZETA_INVERSE_TEMPERATURE_STANDARD_ERROR = 0.0118243769;

  /**
   * Transfers the source-independent candidate without refit to the frozen Kamps states.
   *
   * @throws Exception if the source fixture, model state or report cannot be read or written
   */
  @Tag("slow")
  @Test
  void independentTransferExposesHighMolalityOvercorrection() throws Exception {
    List<SourcePoint> source = loadSource();
    List<String> rows = new ArrayList<String>();
    rows.add(header());
    rows.add(parameterRow());
    Summary low = new Summary();
    Summary high = new Summary();
    Summary aggregate = new Summary();
    int selected = 0;
    int baselineUnderpredicted = 0;
    double maximumDecompositionIdentityResidual = 0.0;
    double absoluteGuestFugacityContribution = 0.0;
    double absoluteEffectiveFOverXContribution = 0.0;
    int waterActivityDecreases = 0;
    for (SourcePoint point : source) {
      if (!point.transferHoldout) {
        continue;
      }
      selected++;
      double saltMassKg = point.kclMolality * KCL_MOLAR_MASS_KG_PER_MOL;
      double kclMassPercent = 100.0 * saltMassKg / (1.0 + saltMassKg);
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference = new KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference(
          point.pointId, "KAMPS2007-THERMOML-DATASET-1", 0.0, kclMassPercent, 0.0, 0.0, 0.0, point.kclMolality, 0.0,
          0.0, point.temperatureK, point.totalPressureKpa / 1000.0, 0.0, 0.0);
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result model = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(reference, 10.0);
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(reference.withoutSalt(), model.co2FeedMoles);
      double correction = correctionAt(point.temperatureK, point.kclMolality);
      double baselineResidual = Math.log(model.co2Molality / point.co2Molality);
      double candidateResidual = baselineResidual - correction;
      double candidateMolality = model.co2Molality * Math.exp(-correction);
      double logGuestFugacityRatio = Math.log(model.co2FugacityBara / pureWater.co2FugacityBara);
      double logEffectiveFOverXRatio = Math.log(model.effectiveFOverXBara() / pureWater.effectiveFOverXBara());
      if (baselineResidual < 0.0) {
        baselineUnderpredicted++;
      }
      Summary family = "LOW".equals(point.kclLevel) ? low : high;
      family.add(baselineResidual, candidateResidual);
      aggregate.add(baselineResidual, candidateResidual);
      rows.add(pointRow(point, model.co2Molality, candidateMolality, model.waterActivity, baselineResidual, correction,
          candidateResidual, pureWater.co2Molality, pureWater.waterActivity,
          Math.log(model.molecularMolePercent / pureWater.molecularMolePercent), logGuestFugacityRatio,
          logEffectiveFOverXRatio, model.decompositionIdentityResidual(pureWater),
          model.waterActivity - pureWater.waterActivity));
      maximumDecompositionIdentityResidual = Math.max(maximumDecompositionIdentityResidual,
          Math.abs(model.decompositionIdentityResidual(pureWater)));
      absoluteGuestFugacityContribution += Math.abs(logGuestFugacityRatio);
      absoluteEffectiveFOverXContribution += Math.abs(logEffectiveFOverXRatio);
      if (model.waterActivity < pureWater.waterActivity) {
        waterActivityDecreases++;
      }
    }
    rows.add(summaryRow("SUMMARY-LOW-KCL", low, "all_low_kcl_states_improve"));
    rows.add(summaryRow("SUMMARY-HIGH-KCL", high, "high_kcl_temperature_curvature_overcorrects_blocks_activation"));
    rows.add(summaryRow("SUMMARY-ALL", aggregate, "aggregate_improves_but_family_failure_blocks_activation"));

    assertEquals(102, source.size(), "complete ThermoML KCl dataset");
    assertEquals(14, selected, "one frozen median-positive-loading state per temperature and salt family");
    assertEquals(7, low.count, "seven low-KCl temperature blocks");
    assertEquals(7, high.count, "seven high-KCl temperature blocks");
    assertEquals(12, baselineUnderpredicted, "baseline underpredicts twelve of fourteen transfer states");
    assertEquals(7, low.improvedCount, "candidate improves every low-KCl state");
    assertEquals(2, high.improvedCount, "candidate improves high KCl only at 313 and 333 K");
    assertEquals(9, aggregate.improvedCount, "nine of fourteen independent transfer states improve");
    assertTrue(maximumDecompositionIdentityResidual < 1.0e-12, "matched pure-water decomposition identity");
    assertEquals(14, waterActivityDecreases, "KCl lowers water activity in every selected state");
    assertTrue(absoluteGuestFugacityContribution / absoluteEffectiveFOverXContribution < 0.05,
        "aqueous effective-f/x response dominates the matched guest-fugacity contribution");
    assertTrue(low.candidateRmse() < low.baselineRmse(), "low-KCl transfer RMSE improves");
    assertTrue(high.candidateRmse() > high.baselineRmse(), "high-KCl transfer RMSE worsens");
    assertTrue(aggregate.candidateRmse() < aggregate.baselineRmse(), "aggregate transfer RMSE improves");

    int columns = rows.get(0).split(",", -1).length;
    for (String row : rows) {
      assertEquals(columns, row.split(",", -1).length, "stable evidence schema");
    }
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), rows, StandardCharsets.UTF_8);
  }

  /** Loads all 102 source-pinned ThermoML KCl records. */
  static List<SourcePoint> loadSource() throws Exception {
    InputStream stream = KClCO2Kamps2007IndependentTransferAssessmentTest.class.getResourceAsStream(RESOURCE);
    assertNotNull(stream, RESOURCE);
    List<SourcePoint> points = new ArrayList<SourcePoint>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("point_id") || line.trim().isEmpty()) {
          continue;
        }
        String[] fields = line.split(",", -1);
        points.add(new SourcePoint(fields[0], Double.parseDouble(fields[1]), Double.parseDouble(fields[2]),
            Double.parseDouble(fields[3]), Double.parseDouble(fields[4]), Double.parseDouble(fields[5]),
            Double.parseDouble(fields[6]), fields[7], "1".equals(fields[8])));
      }
    }
    return points;
  }

  /** Evaluates the frozen source-minus-model log-activity candidate. */
  static double correctionAt(double temperatureK, double molality) {
    double q = REFERENCE_TEMPERATURE_K / temperatureK - 1.0;
    return (LAMBDA_REFERENCE + LAMBDA_INVERSE_TEMPERATURE * q) * molality
        + ZETA_INVERSE_TEMPERATURE * q * molality * molality;
  }

  /** Returns the stable evidence header. */
  private static String header() {
    return "record_type,point_id,nominal_temperature_K,temperature_K,kcl_level,"
        + "kcl_molality_mol_per_kg_water,source_co2_molality_mol_per_kg_water,total_pressure_kPa,"
        + "expanded_pressure_uncertainty_95_kPa,model_co2_molality_mol_per_kg_water,"
        + "candidate_co2_molality_mol_per_kg_water,water_activity,baseline_log_residual,"
        + "candidate_correction,candidate_log_residual,improved,summary_count,summary_improved_count,"
        + "summary_baseline_bias,summary_baseline_rmse,summary_candidate_bias,summary_candidate_rmse,"
        + "summary_candidate_max_abs,lambda_ref,lambda_ref_se,lambda_invT,lambda_invT_se,zeta_invT,"
        + "zeta_invT_se,matched_pure_water_co2_molality,matched_pure_water_activity,log_dissolved_ratio,"
        + "log_guest_fugacity_ratio,log_effective_f_over_x_ratio,decomposition_identity_residual,"
        + "water_activity_delta,disposition";
  }

  /** Formats the immutable He-Morse fit provenance. */
  private static String parameterRow() {
    return row("PARAMETER_SOURCE", "HE-MORSE-FIT", null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, LAMBDA_REFERENCE, LAMBDA_REFERENCE_STANDARD_ERROR,
        LAMBDA_INVERSE_TEMPERATURE, LAMBDA_INVERSE_TEMPERATURE_STANDARD_ERROR, ZETA_INVERSE_TEMPERATURE,
        ZETA_INVERSE_TEMPERATURE_STANDARD_ERROR, null, null, null, null, null, null, null,
        "fixed_from_he_morse_no_kamps_refit");
  }

  /** Formats one independent Kamps transfer state. */
  private static String pointRow(SourcePoint point, double modelMolality, double candidateMolality,
      double waterActivity, double baselineResidual, double correction, double candidateResidual,
      double pureWaterMolality, double pureWaterActivity, double logDissolvedRatio, double logGuestFugacityRatio,
      double logEffectiveFOverXRatio, double decompositionIdentityResidual, double waterActivityDelta) {
    return row("HOLDOUT", point.pointId, point.nominalTemperatureK, point.temperatureK, point.kclLevel,
        point.kclMolality, point.co2Molality, point.totalPressureKpa, point.pressureUncertaintyKpa, modelMolality,
        candidateMolality, waterActivity, baselineResidual, correction, candidateResidual,
        Math.abs(candidateResidual) < Math.abs(baselineResidual), null, null, null, null, null, null, null, null, null,
        null, null, null, null, pureWaterMolality, pureWaterActivity, logDissolvedRatio, logGuestFugacityRatio,
        logEffectiveFOverXRatio, decompositionIdentityResidual, waterActivityDelta,
        "LOW".equals(point.kclLevel) ? "independent_low_kcl_holdout" : "independent_high_kcl_holdout");
  }

  /** Formats one family summary. */
  private static String summaryRow(String pointId, Summary summary, String disposition) {
    return row("SUMMARY", pointId, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        summary.count, summary.improvedCount, summary.baselineBias(), summary.baselineRmse(), summary.candidateBias(),
        summary.candidateRmse(), summary.candidateMaxAbsoluteResidual, null, null, null, null, null, null, null, null,
        null, null, null, null, null, disposition);
  }

  /** Converts values to one locale-independent CSV row. */
  private static String row(Object... values) {
    List<String> fields = new ArrayList<String>();
    for (Object value : values) {
      fields.add(value == null ? "" : String.format(Locale.ROOT, "%s", value));
    }
    return String.join(",", fields);
  }

  /** One source record and its frozen source-only selection metadata. */
  static final class SourcePoint {
    final String pointId;
    final double temperatureK;
    final double kclMolality;
    final double co2Molality;
    final double totalPressureKpa;
    final double pressureUncertaintyKpa;
    final double nominalTemperatureK;
    final String kclLevel;
    final boolean transferHoldout;

    /** Creates an immutable source record. */
    private SourcePoint(String pointId, double temperatureK, double kclMolality, double co2Molality,
        double totalPressureKpa, double pressureUncertaintyKpa, double nominalTemperatureK, String kclLevel,
        boolean transferHoldout) {
      this.pointId = pointId;
      this.temperatureK = temperatureK;
      this.kclMolality = kclMolality;
      this.co2Molality = co2Molality;
      this.totalPressureKpa = totalPressureKpa;
      this.pressureUncertaintyKpa = pressureUncertaintyKpa;
      this.nominalTemperatureK = nominalTemperatureK;
      this.kclLevel = kclLevel;
      this.transferHoldout = transferHoldout;
    }
  }

  /** Accumulates baseline and candidate transfer residuals. */
  private static final class Summary {
    private int count;
    private int improvedCount;
    private double baselineSum;
    private double baselineSquareSum;
    private double candidateSum;
    private double candidateSquareSum;
    private double candidateMaxAbsoluteResidual;

    /** Adds one transfer comparison. */
    private void add(double baselineResidual, double candidateResidual) {
      count++;
      if (Math.abs(candidateResidual) < Math.abs(baselineResidual)) {
        improvedCount++;
      }
      baselineSum += baselineResidual;
      baselineSquareSum += baselineResidual * baselineResidual;
      candidateSum += candidateResidual;
      candidateSquareSum += candidateResidual * candidateResidual;
      candidateMaxAbsoluteResidual = Math.max(candidateMaxAbsoluteResidual, Math.abs(candidateResidual));
    }

    /** Returns baseline signed bias. */
    private double baselineBias() {
      return baselineSum / count;
    }

    /** Returns baseline root-mean-square residual. */
    private double baselineRmse() {
      return Math.sqrt(baselineSquareSum / count);
    }

    /** Returns candidate signed bias. */
    private double candidateBias() {
      return candidateSum / count;
    }

    /** Returns candidate root-mean-square residual. */
    private double candidateRmse() {
      return Math.sqrt(candidateSquareSum / count);
    }
  }
}
