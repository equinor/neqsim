package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Tests loading dependence across the complete positive-loading Kamps KCl series. */
class KClCO2Kamps2007LoadingTrendAssessmentTest {
  private static final double KCL_MOLAR_MASS_KG_PER_MOL = 0.0745513;
  private static final String OUTPUT = "target/co2-kcl-kamps2007-loading-trend-assessment.csv";

  /**
   * Evaluates every positive-loading source state without fitting any Kamps observation.
   *
   * @throws Exception if the source fixture, model state or report cannot be read or written
   */
  @Tag("slow")
  @Test
  void completeSeriesIdentifiesLoadingDependentResidualStructure() throws Exception {
    List<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint> source = KClCO2Kamps2007IndependentTransferAssessmentTest
        .loadSource();
    Map<String, BlockSummary> blocks = new LinkedHashMap<String, BlockSummary>();
    List<String> rows = new ArrayList<String>();
    rows.add(header());
    int positiveLoading = 0;
    int zeroLoadingControls = 0;
    for (KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint point : source) {
      if (point.co2Molality == 0.0) {
        zeroLoadingControls++;
        rows.add(controlRow(point));
        continue;
      }
      positiveLoading++;
      double saltMassKg = point.kclMolality * KCL_MOLAR_MASS_KG_PER_MOL;
      double kclMassPercent = 100.0 * saltMassKg / (1.0 + saltMassKg);
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference = new KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference(
          point.pointId, "KAMPS2007-THERMOML-DATASET-1", 0.0, kclMassPercent, 0.0, 0.0, 0.0, point.kclMolality, 0.0,
          0.0, point.temperatureK, point.totalPressureKpa / 1000.0, 0.0, 0.0);
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result model = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(reference, 10.0);
      double correction = KClCO2Kamps2007IndependentTransferAssessmentTest.correctionAt(point.temperatureK,
          point.kclMolality);
      double baselineResidual = Math.log(model.co2Molality / point.co2Molality);
      double candidateResidual = baselineResidual - correction;
      double fugacityCoefficient = model.co2FugacityBara / (point.totalPressureKpa / 100.0);
      String blockKey = String.format(Locale.ROOT, "%.2f-%s", point.nominalTemperatureK, point.kclLevel);
      BlockSummary block = blocks.get(blockKey);
      if (block == null) {
        block = new BlockSummary(point.nominalTemperatureK, point.kclLevel);
        blocks.put(blockKey, block);
      }
      block.add(point.co2Molality, baselineResidual, candidateResidual, model.waterActivity, fugacityCoefficient);
      rows.add(pointRow(point, model, fugacityCoefficient, baselineResidual, correction, candidateResidual));
    }

    int significantBaselineSlopes = 0;
    int significantCandidateSlopes = 0;
    for (BlockSummary block : blocks.values()) {
      if (block.baselineSlopeT() >= 2.0) {
        significantBaselineSlopes++;
      }
      if (block.candidateSlopeT() >= 2.0) {
        significantCandidateSlopes++;
      }
      rows.add(summaryRow(block));
    }

    assertEquals(102, source.size(), "complete ThermoML source");
    assertEquals(98, positiveLoading, "all positive-loading source states");
    assertEquals(4, zeroLoadingControls, "source vapor-pressure controls");
    assertEquals(14, blocks.size(), "seven temperatures and two KCl families");
    assertTrue(significantBaselineSlopes > 0, "at least one block exposes loading-dependent baseline residuals");
    assertTrue(significantCandidateSlopes > 0,
        "salt-only candidate cannot remove every loading-dependent residual trend");

    int columns = rows.get(0).split(",", -1).length;
    for (String row : rows) {
      assertEquals(columns, row.split(",", -1).length, "stable evidence schema");
    }
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), rows, StandardCharsets.UTF_8);
  }

  /** Returns the stable evidence header. */
  private static String header() {
    return "record_type,point_id,nominal_temperature_K,temperature_K,kcl_level,"
        + "kcl_molality_mol_per_kg_water,source_co2_molality_mol_per_kg_water,total_pressure_kPa,"
        + "model_co2_molality_mol_per_kg_water,water_activity,co2_fugacity_bara,co2_fugacity_over_total_pressure,"
        + "baseline_log_residual,candidate_correction,candidate_log_residual,block_count,baseline_slope,"
        + "baseline_slope_se,baseline_slope_t,candidate_slope,candidate_slope_se,candidate_slope_t,"
        + "baseline_residual_span,candidate_residual_span,water_activity_span,fugacity_coefficient_span,"
        + "baseline_rmse,candidate_rmse,improved_count,disposition";
  }

  /** Formats one zero-CO2 source control without evaluating a logarithmic residual. */
  private static String controlRow(KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint point) {
    return row("SOURCE_CONTROL", point.pointId, point.nominalTemperatureK, point.temperatureK, point.kclLevel,
        point.kclMolality, point.co2Molality, point.totalPressureKpa, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null,
        "zero_co2_vapor_pressure_control_excluded_from_log_residual");
  }

  /** Formats one complete-series model state. */
  private static String pointRow(KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint point,
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result model, double fugacityCoefficient,
      double baselineResidual, double correction, double candidateResidual) {
    return row("SOURCE_STATE", point.pointId, point.nominalTemperatureK, point.temperatureK, point.kclLevel,
        point.kclMolality, point.co2Molality, point.totalPressureKpa, model.co2Molality, model.waterActivity,
        model.co2FugacityBara, fugacityCoefficient, baselineResidual, correction, candidateResidual, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        Math.abs(candidateResidual) < Math.abs(baselineResidual) ? "candidate_improves" : "candidate_regresses");
  }

  /** Formats one temperature and salt-family loading summary. */
  private static String summaryRow(BlockSummary block) {
    return row("BLOCK_SUMMARY", String.format(Locale.ROOT, "%.2f-%s", block.nominalTemperatureK, block.kclLevel),
        block.nominalTemperatureK, null, block.kclLevel, null, null, null, null, null, null, null, null, null, null,
        block.count(), block.baselineSlope(), block.baselineSlopeStandardError(), block.baselineSlopeT(),
        block.candidateSlope(), block.candidateSlopeStandardError(), block.candidateSlopeT(), block.baselineSpan(),
        block.candidateSpan(), block.waterActivitySpan(), block.fugacityCoefficientSpan(), block.baselineRmse(),
        block.candidateRmse(), block.improvedCount(),
        block.candidateSlopeT() >= 2.0 ? "loading_dependence_blocks_salt_only_activation"
            : "no_resolved_loading_trend");
  }

  /** Converts values to one locale-independent CSV row. */
  private static String row(Object... values) {
    List<String> fields = new ArrayList<String>();
    for (Object value : values) {
      fields.add(value == null ? "" : String.format(Locale.ROOT, "%s", value));
    }
    return String.join(",", fields);
  }

  /** Holds one fixed-temperature, fixed-salt-family loading series. */
  private static final class BlockSummary {
    private final double nominalTemperatureK;
    private final String kclLevel;
    private final List<Double> loading = new ArrayList<Double>();
    private final List<Double> baseline = new ArrayList<Double>();
    private final List<Double> candidate = new ArrayList<Double>();
    private final List<Double> waterActivity = new ArrayList<Double>();
    private final List<Double> fugacityCoefficient = new ArrayList<Double>();

    /** Creates one block accumulator. */
    private BlockSummary(double nominalTemperatureK, String kclLevel) {
      this.nominalTemperatureK = nominalTemperatureK;
      this.kclLevel = kclLevel;
    }

    /** Adds one source/model state. */
    private void add(double sourceLoading, double baselineResidual, double candidateResidual, double modelWaterActivity,
        double modelFugacityCoefficient) {
      loading.add(sourceLoading);
      baseline.add(baselineResidual);
      candidate.add(candidateResidual);
      waterActivity.add(modelWaterActivity);
      fugacityCoefficient.add(modelFugacityCoefficient);
    }

    /** Returns the number of positive-loading states. */
    private int count() {
      return loading.size();
    }

    /** Returns the baseline residual slope against source CO2 molality. */
    private double baselineSlope() {
      return regression(baseline)[0];
    }

    /** Returns the baseline slope standard error. */
    private double baselineSlopeStandardError() {
      return regression(baseline)[1];
    }

    /** Returns the absolute baseline slope t statistic. */
    private double baselineSlopeT() {
      double[] fit = regression(baseline);
      return Math.abs(fit[0] / fit[1]);
    }

    /** Returns the candidate residual slope against source CO2 molality. */
    private double candidateSlope() {
      return regression(candidate)[0];
    }

    /** Returns the candidate slope standard error. */
    private double candidateSlopeStandardError() {
      return regression(candidate)[1];
    }

    /** Returns the absolute candidate slope t statistic. */
    private double candidateSlopeT() {
      double[] fit = regression(candidate);
      return Math.abs(fit[0] / fit[1]);
    }

    /** Returns baseline residual span. */
    private double baselineSpan() {
      return span(baseline);
    }

    /** Returns candidate residual span. */
    private double candidateSpan() {
      return span(candidate);
    }

    /** Returns model water-activity span. */
    private double waterActivitySpan() {
      return span(waterActivity);
    }

    /** Returns model CO2 fugacity-coefficient span. */
    private double fugacityCoefficientSpan() {
      return span(fugacityCoefficient);
    }

    /** Returns baseline residual RMSE. */
    private double baselineRmse() {
      return rmse(baseline);
    }

    /** Returns candidate residual RMSE. */
    private double candidateRmse() {
      return rmse(candidate);
    }

    /** Returns the number of source states improved by the candidate. */
    private int improvedCount() {
      int improved = 0;
      for (int index = 0; index < baseline.size(); index++) {
        if (Math.abs(candidate.get(index)) < Math.abs(baseline.get(index))) {
          improved++;
        }
      }
      return improved;
    }

    /** Returns slope and slope standard error for one response. */
    private double[] regression(List<Double> response) {
      double meanX = mean(loading);
      double meanY = mean(response);
      double sxx = 0.0;
      double sxy = 0.0;
      for (int index = 0; index < loading.size(); index++) {
        double dx = loading.get(index) - meanX;
        sxx += dx * dx;
        sxy += dx * (response.get(index) - meanY);
      }
      double slope = sxy / sxx;
      double intercept = meanY - slope * meanX;
      double sumSquares = 0.0;
      for (int index = 0; index < loading.size(); index++) {
        double residual = response.get(index) - intercept - slope * loading.get(index);
        sumSquares += residual * residual;
      }
      double slopeStandardError = Math.sqrt(sumSquares / (loading.size() - 2) / sxx);
      return new double[] {slope, slopeStandardError};
    }

    /** Returns the arithmetic mean of one series. */
    private static double mean(List<Double> values) {
      double sum = 0.0;
      for (double value : values) {
        sum += value;
      }
      return sum / values.size();
    }

    /** Returns the maximum-minus-minimum span. */
    private static double span(List<Double> values) {
      double minimum = Double.POSITIVE_INFINITY;
      double maximum = Double.NEGATIVE_INFINITY;
      for (double value : values) {
        minimum = Math.min(minimum, value);
        maximum = Math.max(maximum, value);
      }
      return maximum - minimum;
    }

    /** Returns root-mean-square response. */
    private static double rmse(List<Double> values) {
      double sumSquares = 0.0;
      for (double value : values) {
        sumSquares += value * value;
      }
      return Math.sqrt(sumSquares / values.size());
    }
  }
}
