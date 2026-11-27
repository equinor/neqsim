package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Tests whether the existing Furst CO2-ion block can explain Kamps loading trends. */
class KClCO2Kamps2007FurstLoadingSensitivityAssessmentTest {
  private static final double KCL_MOLAR_MASS_KG_PER_MOL = 0.0745513;
  private static final double[] FURST_SCALES = {0.0, 0.5, 1.0};
  private static final String OUTPUT = "target/co2-kcl-kamps2007-furst-loading-sensitivity-assessment.csv";

  /**
   * Evaluates a source-only low/median/high loading design at three Furst block scales.
   *
   * <p>
   * Scale one is the Onda-anchored production default. Scales zero and one-half are form-sensitivity controls only;
   * they are not candidate parameters because they do not preserve that independent reference.
   * </p>
   *
   * @throws Exception if the source fixture, model state or report cannot be read or written
   */
  @Tag("slow")
  @Test
  void existingFurstBlockCannotBeRescaledWithoutTestingItsLoadingShape() throws Exception {
    List<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint> source = KClCO2Kamps2007IndependentTransferAssessmentTest
        .loadSource();
    Map<String, List<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint>> sourceBlocks = group(source);
    List<String> rows = new ArrayList<String>();
    rows.add(header());
    int selectedStates = 0;
    int attemptedModelStates = 0;
    int convergedBaselineStates = 0;
    int convergedSensitivityStates = 0;
    int failedSensitivityStates = 0;
    int retainedControls = 0;
    int finiteZeroSlopeScales = 0;
    int boundedZeroSlopeScales = 0;
    int boundedLowFamilyZeroSlopeScales = 0;
    for (KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint point : source) {
      if (point.co2Molality == 0.0) {
        retainedControls++;
        rows.add(controlRow(point));
      }
    }
    for (Map.Entry<String, List<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint>> entry : sourceBlocks
        .entrySet()) {
      List<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint> design = selectDesign(entry.getValue());
      BlockSummary block = new BlockSummary(entry.getKey(), design.get(0).nominalTemperatureK, design.get(0).kclLevel);
      for (KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint point : design) {
        selectedStates++;
        double saltMassKg = point.kclMolality * KCL_MOLAR_MASS_KG_PER_MOL;
        double kclMassPercent = 100.0 * saltMassKg / (1.0 + saltMassKg);
        KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference = new KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference(
            point.pointId, "KAMPS2007-THERMOML-DATASET-1", 0.0, kclMassPercent, 0.0, 0.0, 0.0, point.kclMolality, 0.0,
            0.0, point.temperatureK, point.totalPressureKpa / 1000.0, 0.0, 0.0);
        for (double scale : FURST_SCALES) {
          attemptedModelStates++;
          try {
            KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result model = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
                .calculateWithFurstCo2IonScale(reference, 10.0, scale);
            double residual = Math.log(model.co2Molality / point.co2Molality);
            block.add(scale, point.co2Molality, residual);
            rows.add(pointRow(point, scale, model, residual));
            if (scale == 1.0) {
              convergedBaselineStates++;
            } else {
              convergedSensitivityStates++;
            }
          } catch (RuntimeException | AssertionError failure) {
            block.addFailure(scale);
            rows.add(failureRow(point, scale, failure));
            if (scale == 1.0) {
              throw failure;
            }
            failedSensitivityStates++;
          }
        }
      }
      double zeroSlopeScale = block.zeroSlopeScale();
      if (Double.isFinite(zeroSlopeScale)) {
        finiteZeroSlopeScales++;
        if (zeroSlopeScale >= 0.0 && zeroSlopeScale <= 1.0) {
          boundedZeroSlopeScales++;
          if ("LOW".equals(block.kclLevel)) {
            boundedLowFamilyZeroSlopeScales++;
          }
        }
      }
      rows.add(summaryRow(block));
    }

    assertEquals(102, source.size(), "complete ThermoML source");
    assertEquals(14, sourceBlocks.size(), "seven temperatures and two KCl families");
    assertEquals(42, selectedStates, "source-only low/median/high design");
    assertEquals(126, attemptedModelStates, "three Furst scales per selected state");
    assertEquals(42, convergedBaselineStates, "every Onda-anchored baseline state");
    assertTrue(convergedSensitivityStates > 0, "at least one ablated sensitivity state must converge");
    assertEquals(4, retainedControls, "zero-CO2 vapor-pressure controls");
    assertEquals(14, finiteZeroSlopeScales, "every block must respond to Furst scaling");
    assertTrue(boundedZeroSlopeScales < sourceBlocks.size(),
        "one bounded Furst scale cannot remove every selected loading trend");
    assertEquals(0, boundedLowFamilyZeroSlopeScales,
        "every low-KCl block requires extrapolation beyond the anchored Furst scale");
    assertEquals(84, convergedSensitivityStates + failedSensitivityStates,
        "every non-default sensitivity state has a disposition");

    int columns = rows.get(0).split(",", -1).length;
    for (String row : rows) {
      assertEquals(columns, row.split(",", -1).length, "stable evidence schema");
    }
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), rows, StandardCharsets.UTF_8);
  }

  /** Groups positive-loading rows by nominal temperature and salt family. */
  private static Map<String, List<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint>> group(
      List<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint> source) {
    Map<String, List<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint>> blocks = new LinkedHashMap<String, List<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint>>();
    for (KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint point : source) {
      if (point.co2Molality > 0.0) {
        String key = String.format(Locale.ROOT, "%.2f-%s", point.nominalTemperatureK, point.kclLevel);
        if (!blocks.containsKey(key)) {
          blocks.put(key, new ArrayList<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint>());
        }
        blocks.get(key).add(point);
      }
    }
    return blocks;
  }

  /** Selects the lowest, median and highest CO2 loading before model evaluation. */
  private static List<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint> selectDesign(
      List<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint> sourceBlock) {
    List<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint> sorted = new ArrayList<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint>(
        sourceBlock);
    Collections.sort(sorted, new Comparator<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint>() {
      @Override
      public int compare(KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint left,
          KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint right) {
        return Double.compare(left.co2Molality, right.co2Molality);
      }
    });
    assertTrue(sorted.size() >= 3, "each Kamps block must contain at least three positive loadings");
    List<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint> selected = new ArrayList<KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint>();
    selected.add(sorted.get(0));
    selected.add(sorted.get(sorted.size() / 2));
    selected.add(sorted.get(sorted.size() - 1));
    return selected;
  }

  /** Returns the stable evidence header. */
  private static String header() {
    return "record_type,point_id,nominal_temperature_K,temperature_K,kcl_level,"
        + "kcl_molality_mol_per_kg_water,source_co2_molality_mol_per_kg_water,total_pressure_kPa,"
        + "furst_scale,model_co2_molality_mol_per_kg_water,water_activity,co2_fugacity_bara,log_residual,"
        + "converged,failure_class,failure_message,"
        + "scale_zero_slope,scale_half_slope,scale_one_slope,zero_slope_scale,midpoint_slope_nonlinearity,"
        + "scale_zero_rmse,scale_half_rmse,scale_one_rmse,scale_zero_failures,scale_half_failures,"
        + "scale_one_failures,disposition";
  }

  /** Formats one retained source control. */
  private static String controlRow(KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint point) {
    return row("SOURCE_CONTROL", point.pointId, point.nominalTemperatureK, point.temperatureK, point.kclLevel,
        point.kclMolality, point.co2Molality, point.totalPressureKpa, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, "zero_co2_vapor_pressure_control");
  }

  /** Formats one source/model sensitivity state. */
  private static String pointRow(KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint point, double scale,
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result model, double residual) {
    return row("SENSITIVITY_STATE", point.pointId, point.nominalTemperatureK, point.temperatureK, point.kclLevel,
        point.kclMolality, point.co2Molality, point.totalPressureKpa, scale, model.co2Molality, model.waterActivity,
        model.co2FugacityBara, residual, true, null, null, null, null, null, null, null, null, null, null, null, null,
        null, scale == 1.0 ? "onda_anchored_default" : "form_sensitivity_only_breaks_onda_anchor");
  }

  /** Formats one failed non-default sensitivity state without weakening a conservation gate. */
  private static String failureRow(KClCO2Kamps2007IndependentTransferAssessmentTest.SourcePoint point, double scale,
      Throwable failure) {
    String message = failure.getMessage() == null ? "" : failure.getMessage().replace(',', ';').replace('\n', ' ');
    return row("SENSITIVITY_FAILURE", point.pointId, point.nominalTemperatureK, point.temperatureK, point.kclLevel,
        point.kclMolality, point.co2Molality, point.totalPressureKpa, scale, null, null, null, null, false,
        failure.getClass().getSimpleName(), message, null, null, null, null, null, null, null, null, null, null, null,
        "nondefault_furst_scale_rejected_by_existing_physical_or_numerical_gate");
  }

  /** Formats one block-level loading-slope sensitivity summary. */
  private static String summaryRow(BlockSummary block) {
    double zeroSlopeScale = block.zeroSlopeScale();
    String disposition = zeroSlopeScale >= 0.0 && zeroSlopeScale <= 1.0
        ? "bounded_scale_can_zero_selected_loading_slope_but_breaks_onda_anchor"
        : "no_bounded_common_block_scale_zeroes_selected_loading_slope";
    return row("BLOCK_SUMMARY", block.key, block.nominalTemperatureK, null, block.kclLevel, null, null, null, null,
        null, null, null, null, null, null, null, block.slope(0.0), block.slope(0.5), block.slope(1.0), zeroSlopeScale,
        block.midpointSlopeNonlinearity(), block.rmse(0.0), block.rmse(0.5), block.rmse(1.0), block.failures(0.0),
        block.failures(0.5), block.failures(1.0), disposition);
  }

  /** Converts values to one locale-independent CSV row. */
  private static String row(Object... values) {
    List<String> fields = new ArrayList<String>();
    for (Object value : values) {
      fields.add(value == null ? "" : String.format(Locale.ROOT, "%s", value));
    }
    return String.join(",", fields);
  }

  /** Holds the three-point response for one temperature/salt block. */
  private static final class BlockSummary {
    private final String key;
    private final double nominalTemperatureK;
    private final String kclLevel;
    private final Map<Double, List<Double>> loading = new LinkedHashMap<Double, List<Double>>();
    private final Map<Double, List<Double>> residual = new LinkedHashMap<Double, List<Double>>();
    private final Map<Double, Integer> failures = new LinkedHashMap<Double, Integer>();

    /** Creates one block accumulator. */
    private BlockSummary(String key, double nominalTemperatureK, String kclLevel) {
      this.key = key;
      this.nominalTemperatureK = nominalTemperatureK;
      this.kclLevel = kclLevel;
      for (double scale : FURST_SCALES) {
        loading.put(scale, new ArrayList<Double>());
        residual.put(scale, new ArrayList<Double>());
        failures.put(scale, 0);
      }
    }

    /** Adds one evaluated state. */
    private void add(double scale, double sourceLoading, double logResidual) {
      loading.get(scale).add(sourceLoading);
      residual.get(scale).add(logResidual);
    }

    /** Records one rejected sensitivity state. */
    private void addFailure(double scale) {
      failures.put(scale, failures.get(scale) + 1);
    }

    /** Returns the rejected-state count at one scale. */
    private int failures(double scale) {
      return failures.get(scale);
    }

    /** Returns the least-squares residual slope against source CO2 loading. */
    private double slope(double scale) {
      List<Double> x = loading.get(scale);
      List<Double> y = residual.get(scale);
      if (x.size() < 2) {
        return Double.NaN;
      }
      double meanX = mean(x);
      double meanY = mean(y);
      double sxx = 0.0;
      double sxy = 0.0;
      for (int index = 0; index < x.size(); index++) {
        double dx = x.get(index) - meanX;
        sxx += dx * dx;
        sxy += dx * (y.get(index) - meanY);
      }
      return sxy / sxx;
    }

    /** Returns the endpoint-interpolated scale whose selected loading slope is zero. */
    private double zeroSlopeScale() {
      double slopeZero = slope(0.0);
      double slopeOne = slope(1.0);
      double difference = slopeOne - slopeZero;
      return !Double.isFinite(slopeZero) || !Double.isFinite(slopeOne) || difference == 0.0 ? Double.NaN
          : -slopeZero / difference;
    }

    /** Returns deviation of the midpoint slope from endpoint-linear interpolation. */
    private double midpointSlopeNonlinearity() {
      double slopeHalf = slope(0.5);
      double endpointMean = 0.5 * (slope(0.0) + slope(1.0));
      return Double.isFinite(slopeHalf) && Double.isFinite(endpointMean) ? slopeHalf - endpointMean : Double.NaN;
    }

    /** Returns root-mean-square source residual at one scale. */
    private double rmse(double scale) {
      double sumSquares = 0.0;
      for (double value : residual.get(scale)) {
        sumSquares += value * value;
      }
      return residual.get(scale).isEmpty() ? Double.NaN : Math.sqrt(sumSquares / residual.get(scale).size());
    }

    /** Returns the arithmetic mean. */
    private static double mean(List<Double> values) {
      double sum = 0.0;
      for (double value : values) {
        sum += value;
      }
      return sum / values.size();
    }
  }
}
