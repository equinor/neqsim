package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Tests loading dependence across the complete positive-loading Kamps KCl series. */
class KClCO2Kamps2007LoadingTrendAssessmentTest {
  private static final double KCL_MOLAR_MASS_KG_PER_MOL = 0.0745513;
  private static final double REFERENCE_TEMPERATURE_K = 298.15;
  private static final int EVIDENCE_COLUMNS = 48;
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
      block.add(point.kclMolality, point.co2Molality, baselineResidual, candidateResidual, model.waterActivity,
          fugacityCoefficient);
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

    List<KernelFitResult> kernelFits = fitLoadingKernels(blocks.values());
    for (KernelFitResult fit : kernelFits) {
      rows.add(kernelRow(fit));
    }
    assertKernelDisposition(kernelFits);

    assertEquals(102, source.size(), "complete ThermoML source");
    assertEquals(98, positiveLoading, "all positive-loading source states");
    assertEquals(4, zeroLoadingControls, "source vapor-pressure controls");
    assertEquals(14, blocks.size(), "seven temperatures and two KCl families");
    assertEquals(8, kernelFits.size(), "two topologies, two temperature laws and two family splits");
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
        + "baseline_rmse,candidate_rmse,improved_count,disposition,kernel_topology,kernel_salt_power,"
        + "calibration_family,holdout_family,parameter_count,coefficient_ref,coefficient_ref_standard_error,"
        + "coefficient_inv_t,coefficient_inv_t_standard_error,design_rank,condition_number,"
        + "calibration_centered_rmse_before,calibration_centered_rmse_after,calibration_aicc,"
        + "holdout_centered_rmse_before,holdout_centered_rmse_after,holdout_improved_count,holdout_point_count";
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
    while (fields.size() < EVIDENCE_COLUMNS) {
      fields.add("");
    }
    assertEquals(EVIDENCE_COLUMNS, fields.size(), "evidence row cannot exceed the stable schema");
    return String.join(",", fields);
  }

  /**
   * Fits two degree-one excess-Helmholtz loading kernels with source-family holdouts.
   *
   * <p>
   * The linear-salt kernel is {@code n0^2*(nPlus+nMinus)/(2*(Mw*nWater)^2)}; its neutral chemical-potential derivative
   * is {@code 2*m0*mSalt} for a 1:1 salt. The salt-pair kernel is {@code n0^2*nPlus*nMinus/(Mw*nWater)^3}; its
   * derivative is {@code 2*m0*mSalt^2}. Both vanish at neutral-solute infinite dilution and are degree-one homogeneous.
   * Block centering removes one nuisance offset per temperature and salt family, so only the independently observed
   * loading shape is fitted.
   * </p>
   *
   * @param blocks complete Kamps temperature and salt-family blocks
   * @return eight fits spanning two kernels, two temperature laws and reciprocal family splits
   */
  private static List<KernelFitResult> fitLoadingKernels(Collection<BlockSummary> blocks) {
    List<KernelFitResult> fits = new ArrayList<KernelFitResult>();
    for (int saltPower = 1; saltPower <= 2; saltPower++) {
      for (boolean inverseTemperature : new boolean[] {false, true}) {
        fits.add(fitLoadingKernel(blocks, "LOW", saltPower, inverseTemperature));
        fits.add(fitLoadingKernel(blocks, "HIGH", saltPower, inverseTemperature));
      }
    }
    return fits;
  }

  /**
   * Fits one loading kernel on one KCl family and transfers it without refit to the other family.
   *
   * @param blocks complete Kamps blocks
   * @param calibrationFamily LOW or HIGH KCl source family
   * @param saltPower one for the ion-mean kernel or two for the salt-pair kernel
   * @param inverseTemperature whether to add the {@code 298.15/T-1} coefficient
   * @return fit, covariance and frozen-family transfer statistics
   */
  private static KernelFitResult fitLoadingKernel(Collection<BlockSummary> blocks, String calibrationFamily,
      int saltPower, boolean inverseTemperature) {
    String holdoutFamily = "LOW".equals(calibrationFamily) ? "HIGH" : "LOW";
    KernelData calibration = centeredKernelData(blocks, calibrationFamily, saltPower);
    KernelData holdout = centeredKernelData(blocks, holdoutFamily, saltPower);
    int parameterCount = inverseTemperature ? 2 : 1;

    double s00 = 0.0;
    double s01 = 0.0;
    double s11 = 0.0;
    double z0 = 0.0;
    double z1 = 0.0;
    for (int index = 0; index < calibration.size(); index++) {
      s00 += calibration.x0.get(index) * calibration.x0.get(index);
      s01 += calibration.x0.get(index) * calibration.x1.get(index);
      s11 += calibration.x1.get(index) * calibration.x1.get(index);
      z0 += calibration.x0.get(index) * calibration.y.get(index);
      z1 += calibration.x1.get(index) * calibration.y.get(index);
    }
    double coefficientReference;
    double coefficientInverseTemperature = 0.0;
    double inverse00;
    double inverse11 = 0.0;
    double conditionNumber = 1.0;
    if (inverseTemperature) {
      double determinant = s00 * s11 - s01 * s01;
      assertTrue(determinant > 0.0, "inverse-temperature loading design must be full rank");
      coefficientReference = (z0 * s11 - z1 * s01) / determinant;
      coefficientInverseTemperature = (s00 * z1 - s01 * z0) / determinant;
      inverse00 = s11 / determinant;
      inverse11 = s00 / determinant;
      double trace = s00 + s11;
      double discriminant = Math.sqrt((s00 - s11) * (s00 - s11) + 4.0 * s01 * s01);
      double maximumEigenvalue = 0.5 * (trace + discriminant);
      double minimumEigenvalue = 0.5 * (trace - discriminant);
      conditionNumber = Math.sqrt(maximumEigenvalue / minimumEigenvalue);
    } else {
      assertTrue(s00 > 0.0, "constant loading design must have nonzero response");
      coefficientReference = z0 / s00;
      inverse00 = 1.0 / s00;
    }

    double calibrationBaselineSquares = calibration.sumSquares(null, false);
    double calibrationCandidateSquares = calibration
        .sumSquares(new double[] {coefficientReference, coefficientInverseTemperature}, inverseTemperature);
    int residualDegreesOfFreedom = calibration.size() - calibration.blockCount() - parameterCount;
    assertTrue(residualDegreesOfFreedom > 0, "loading fit must retain positive residual degrees of freedom");
    double residualVariance = calibrationCandidateSquares / residualDegreesOfFreedom;
    double coefficientReferenceStandardError = Math.sqrt(residualVariance * inverse00);
    double coefficientInverseTemperatureStandardError = inverseTemperature ? Math.sqrt(residualVariance * inverse11)
        : Double.NaN;
    double calibrationAicc = correctedAkaike(calibration.size(), calibration.blockCount() + parameterCount,
        calibrationCandidateSquares);
    double holdoutBaselineSquares = holdout.sumSquares(null, false);
    double holdoutCandidateSquares = holdout
        .sumSquares(new double[] {coefficientReference, coefficientInverseTemperature}, inverseTemperature);

    return new KernelFitResult(saltPower == 1 ? "NEUTRAL_NEUTRAL_ION_MEAN" : "NEUTRAL_NEUTRAL_SALT_PAIR", saltPower,
        calibrationFamily, holdoutFamily, parameterCount, coefficientReference, coefficientReferenceStandardError,
        coefficientInverseTemperature, coefficientInverseTemperatureStandardError, parameterCount,
        calibration.blockCount(), conditionNumber, Math.sqrt(calibrationBaselineSquares / calibration.size()),
        Math.sqrt(calibrationCandidateSquares / calibration.size()), calibrationAicc,
        Math.sqrt(holdoutBaselineSquares / holdout.size()), Math.sqrt(holdoutCandidateSquares / holdout.size()),
        holdout.improvedCount(new double[] {coefficientReference, coefficientInverseTemperature}, inverseTemperature),
        holdout.size());
  }

  /**
   * Builds block-centered loading-response data for one salt family.
   *
   * @param blocks complete Kamps blocks
   * @param family LOW or HIGH KCl source family
   * @param saltPower salt-molality exponent in the neutral chemical-potential response
   * @return centered predictors and current-model residuals
   */
  private static KernelData centeredKernelData(Collection<BlockSummary> blocks, String family, int saltPower) {
    KernelData data = new KernelData();
    for (BlockSummary block : blocks) {
      if (!family.equals(block.kclLevel)) {
        continue;
      }
      data.startBlock();
      List<Double> rawX0 = new ArrayList<Double>();
      List<Double> rawX1 = new ArrayList<Double>();
      double temperatureCoordinate = REFERENCE_TEMPERATURE_K / block.nominalTemperatureK - 1.0;
      for (int index = 0; index < block.loading.size(); index++) {
        double predictor = 2.0 * Math.pow(block.saltMolality.get(index), saltPower) * block.loading.get(index);
        rawX0.add(predictor);
        rawX1.add(predictor * temperatureCoordinate);
      }
      double meanX0 = BlockSummary.mean(rawX0);
      double meanX1 = BlockSummary.mean(rawX1);
      double meanY = BlockSummary.mean(block.baseline);
      for (int index = 0; index < block.loading.size(); index++) {
        data.add(rawX0.get(index) - meanX0, rawX1.get(index) - meanX1, block.baseline.get(index) - meanY);
      }
    }
    assertTrue(data.size() > 2, "each source family must retain centered loading observations");
    return data;
  }

  /**
   * Calculates corrected Akaike information criterion for a Gaussian least-squares model.
   *
   * @param observations observation count
   * @param parameters fitted parameter count
   * @param sumSquares residual sum of squares
   * @return AICc value up to the common Gaussian constant
   */
  private static double correctedAkaike(int observations, int parameters, double sumSquares) {
    double aic = observations * Math.log(sumSquares / observations) + 2.0 * parameters;
    return aic + 2.0 * parameters * (parameters + 1.0) / (observations - parameters - 1.0);
  }

  /**
   * Enforces the source-family scientific disposition without selecting a production coefficient.
   *
   * @param fits complete kernel comparison
   */
  private static void assertKernelDisposition(List<KernelFitResult> fits) {
    KernelFitResult linearLow = findFit(fits, "NEUTRAL_NEUTRAL_ION_MEAN", "LOW", 1);
    KernelFitResult linearHigh = findFit(fits, "NEUTRAL_NEUTRAL_ION_MEAN", "HIGH", 1);
    KernelFitResult linearLowTemperature = findFit(fits, "NEUTRAL_NEUTRAL_ION_MEAN", "LOW", 2);
    KernelFitResult linearHighTemperature = findFit(fits, "NEUTRAL_NEUTRAL_ION_MEAN", "HIGH", 2);
    KernelFitResult pairLow = findFit(fits, "NEUTRAL_NEUTRAL_SALT_PAIR", "LOW", 1);

    assertTrue(linearLow.calibrationRmseAfter < linearLow.calibrationRmseBefore);
    assertTrue(linearHigh.calibrationRmseAfter < linearHigh.calibrationRmseBefore);
    assertTrue(linearLow.holdoutRmseAfter < linearLow.holdoutRmseBefore,
        "linear-salt kernel must retain at least marginal low-to-high transfer");
    assertTrue(linearHigh.holdoutRmseAfter < linearHigh.holdoutRmseBefore,
        "linear-salt kernel must retain high-to-low transfer");
    double familyDifferenceStandardError = Math
        .sqrt(linearLow.coefficientReferenceStandardError * linearLow.coefficientReferenceStandardError
            + linearHigh.coefficientReferenceStandardError * linearHigh.coefficientReferenceStandardError);
    assertTrue(
        Math.abs(linearLow.coefficientReference - linearHigh.coefficientReference) > 2.0
            * familyDifferenceStandardError,
        "family coefficients must remain incompatibly separated before any production parameter is selected");
    assertTrue(linearLow.calibrationAicc < linearLowTemperature.calibrationAicc);
    assertTrue(linearHigh.calibrationAicc < linearHighTemperature.calibrationAicc);
    assertTrue(Math.abs(
        linearLowTemperature.coefficientInverseTemperature) < linearLowTemperature.coefficientInverseTemperatureStandardError);
    assertTrue(Math.abs(
        linearHighTemperature.coefficientInverseTemperature) < linearHighTemperature.coefficientInverseTemperatureStandardError);
    assertTrue(pairLow.holdoutRmseAfter > pairLow.holdoutRmseBefore,
        "quadratic salt-pair loading kernel must retain its high-family regression");
    assertTrue(linearLow.holdoutRmseAfter < pairLow.holdoutRmseAfter,
        "linear-salt loading topology must outperform quadratic salt-pair transfer");
  }

  /**
   * Finds one kernel fit by topology, calibration family and parameter count.
   *
   * @param fits complete fit list
   * @param topology kernel topology identifier
   * @param family calibration family
   * @param parameterCount fitted parameter count
   * @return matching fit
   */
  private static KernelFitResult findFit(List<KernelFitResult> fits, String topology, String family,
      int parameterCount) {
    for (KernelFitResult fit : fits) {
      if (topology.equals(fit.topology) && family.equals(fit.calibrationFamily)
          && parameterCount == fit.parameterCount) {
        return fit;
      }
    }
    throw new IllegalStateException("Missing loading-kernel fit: " + topology + "/" + family + "/" + parameterCount);
  }

  /** Formats one source-family loading-kernel fit row. */
  private static String kernelRow(KernelFitResult fit) {
    List<String> fields = new ArrayList<String>();
    for (int index = 0; index < EVIDENCE_COLUMNS; index++) {
      fields.add("");
    }
    fields.set(0, "KERNEL_MODEL");
    fields.set(1, fit.topology + "-" + fit.calibrationFamily + "-P" + fit.parameterCount);
    fields.set(15, String.valueOf(fit.calibrationBlockCount));
    fields.set(29, "diagnostic_only_family_transfer_blocks_activation");
    fields.set(30, fit.topology);
    fields.set(31, String.valueOf(fit.saltPower));
    fields.set(32, fit.calibrationFamily);
    fields.set(33, fit.holdoutFamily);
    fields.set(34, String.valueOf(fit.parameterCount));
    fields.set(35, format(fit.coefficientReference));
    fields.set(36, format(fit.coefficientReferenceStandardError));
    fields.set(37, fit.parameterCount == 2 ? format(fit.coefficientInverseTemperature) : "");
    fields.set(38, fit.parameterCount == 2 ? format(fit.coefficientInverseTemperatureStandardError) : "");
    fields.set(39, String.valueOf(fit.rank));
    fields.set(40, format(fit.conditionNumber));
    fields.set(41, format(fit.calibrationRmseBefore));
    fields.set(42, format(fit.calibrationRmseAfter));
    fields.set(43, format(fit.calibrationAicc));
    fields.set(44, format(fit.holdoutRmseBefore));
    fields.set(45, format(fit.holdoutRmseAfter));
    fields.set(46, String.valueOf(fit.holdoutImprovedCount));
    fields.set(47, String.valueOf(fit.holdoutPointCount));
    return String.join(",", fields);
  }

  /** Formats one finite floating-point evidence value. */
  private static String format(double value) {
    return String.format(Locale.ROOT, "%.15g", value);
  }

  /** Holds block-centered predictors and residual responses. */
  private static final class KernelData {
    private final List<Double> x0 = new ArrayList<Double>();
    private final List<Double> x1 = new ArrayList<Double>();
    private final List<Double> y = new ArrayList<Double>();
    private int blockCount;

    /** Records one source temperature/salt block and its fitted nuisance intercept. */
    private void startBlock() {
      blockCount++;
    }

    /** Adds one centered observation. */
    private void add(double predictorReference, double predictorInverseTemperature, double response) {
      x0.add(predictorReference);
      x1.add(predictorInverseTemperature);
      y.add(response);
    }

    /** Returns observation count. */
    private int size() {
      return y.size();
    }

    /** Returns the count of source blocks whose centered means consume degrees of freedom. */
    private int blockCount() {
      return blockCount;
    }

    /** Returns response sum of squares before or after applying a fitted kernel. */
    private double sumSquares(double[] coefficients, boolean inverseTemperature) {
      double sum = 0.0;
      for (int index = 0; index < size(); index++) {
        double prediction = coefficients == null ? 0.0
            : coefficients[0] * x0.get(index) + (inverseTemperature ? coefficients[1] * x1.get(index) : 0.0);
        double residual = y.get(index) - prediction;
        sum += residual * residual;
      }
      return sum;
    }

    /** Returns the count whose absolute centered residual improves after transfer. */
    private int improvedCount(double[] coefficients, boolean inverseTemperature) {
      int improved = 0;
      for (int index = 0; index < size(); index++) {
        double prediction = coefficients[0] * x0.get(index)
            + (inverseTemperature ? coefficients[1] * x1.get(index) : 0.0);
        if (Math.abs(y.get(index) - prediction) < Math.abs(y.get(index))) {
          improved++;
        }
      }
      return improved;
    }
  }

  /** Immutable loading-kernel fit and frozen-family transfer result. */
  private static final class KernelFitResult {
    private final String topology;
    private final int saltPower;
    private final String calibrationFamily;
    private final String holdoutFamily;
    private final int parameterCount;
    private final double coefficientReference;
    private final double coefficientReferenceStandardError;
    private final double coefficientInverseTemperature;
    private final double coefficientInverseTemperatureStandardError;
    private final int rank;
    private final int calibrationBlockCount;
    private final double conditionNumber;
    private final double calibrationRmseBefore;
    private final double calibrationRmseAfter;
    private final double calibrationAicc;
    private final double holdoutRmseBefore;
    private final double holdoutRmseAfter;
    private final int holdoutImprovedCount;
    private final int holdoutPointCount;

    /** Creates one immutable kernel result. */
    private KernelFitResult(String topology, int saltPower, String calibrationFamily, String holdoutFamily,
        int parameterCount, double coefficientReference, double coefficientReferenceStandardError,
        double coefficientInverseTemperature, double coefficientInverseTemperatureStandardError, int rank,
        int calibrationBlockCount, double conditionNumber, double calibrationRmseBefore, double calibrationRmseAfter,
        double calibrationAicc, double holdoutRmseBefore, double holdoutRmseAfter, int holdoutImprovedCount,
        int holdoutPointCount) {
      this.topology = topology;
      this.saltPower = saltPower;
      this.calibrationFamily = calibrationFamily;
      this.holdoutFamily = holdoutFamily;
      this.parameterCount = parameterCount;
      this.coefficientReference = coefficientReference;
      this.coefficientReferenceStandardError = coefficientReferenceStandardError;
      this.coefficientInverseTemperature = coefficientInverseTemperature;
      this.coefficientInverseTemperatureStandardError = coefficientInverseTemperatureStandardError;
      this.rank = rank;
      this.calibrationBlockCount = calibrationBlockCount;
      this.conditionNumber = conditionNumber;
      this.calibrationRmseBefore = calibrationRmseBefore;
      this.calibrationRmseAfter = calibrationRmseAfter;
      this.calibrationAicc = calibrationAicc;
      this.holdoutRmseBefore = holdoutRmseBefore;
      this.holdoutRmseAfter = holdoutRmseAfter;
      this.holdoutImprovedCount = holdoutImprovedCount;
      this.holdoutPointCount = holdoutPointCount;
    }
  }

  /** Holds one fixed-temperature, fixed-salt-family loading series. */
  private static final class BlockSummary {
    private final double nominalTemperatureK;
    private final String kclLevel;
    private final List<Double> saltMolality = new ArrayList<Double>();
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
    private void add(double sourceSaltMolality, double sourceLoading, double baselineResidual, double candidateResidual,
        double modelWaterActivity, double modelFugacityCoefficient) {
      saltMolality.add(sourceSaltMolality);
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
