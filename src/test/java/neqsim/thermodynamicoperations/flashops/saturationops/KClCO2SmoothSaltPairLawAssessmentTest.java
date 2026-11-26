package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.mathlib.linearalgebra.EjmlLinearAlgebra;
import neqsim.mathlib.linearalgebra.LinearAlgebraOperations;

/**
 * Tests a parsimonious temperature-dependent KCl/CO2 residual activity law.
 *
 * <p>
 * The fitted source-minus-model response is {@code g(T,m) = [lambdaRef + lambdaInvT q(T)] m + zetaInvT q(T) m^2}, with
 * {@code q(T) = 298.15/T - 1}. The zero reference-temperature residual curvature is supported by the fitted
 * seven-parameter comparator rather than imposed as a source coefficient. He-Morse data calibrate the law; the
 * source-matched Mousavi-Chapoy-Burgass KCl series is retained as an independent holdout. This diagnostic changes no
 * production equation or parameter.
 * </p>
 *
 * @author OpenAI
 * @version 1.0
 */
class KClCO2SmoothSaltPairLawAssessmentTest {
  private static final double REFERENCE_TEMPERATURE_K = 298.15;
  private static final double CO2_FEED_MOLES = 10.0;
  private static final double[] TEMPERATURES_K = {273.15, 298.15, 323.15, 348.15, 363.15};
  private static final String OUTPUT = "target/co2-kcl-smooth-salt-pair-law-assessment.csv";

  /**
   * Fits the reduced law and transfers it without refit to the frozen dense KCl series.
   *
   * @throws Exception if a source fixture, model state or diagnostic output cannot be read or written
   */
  @Tag("slow")
  @Test
  void smoothResidualLawIsParsimoniousButHighTemperatureTransferOvercorrects() throws Exception {
    LinearAlgebraOperations algebra = new EjmlLinearAlgebra();
    List<KClCO2SolubilityReferenceAssessmentTest.Reference> calibration = KClCO2SolubilityReferenceAssessmentTest
        .loadReferences();
    Map<Double, Double> pureByTemperature = new HashMap<Double, Double>();
    double[][] candidateDesign = new double[calibration.size()][3];
    double[][] smoothFourDesign = new double[calibration.size()][4];
    double[][] establishedDesign = new double[calibration.size()][7];
    double[] observed = new double[calibration.size()];
    List<String> rows = new ArrayList<String>();
    rows.add(header());
    for (int row = 0; row < calibration.size(); row++) {
      KClCO2SolubilityReferenceAssessmentTest.Reference reference = calibration.get(row);
      Double pure = pureByTemperature.get(reference.temperatureK);
      if (pure == null) {
        pure = KClCO2SolubilityReferenceAssessmentTest.calculate(reference.withoutSalt()).modelCo2Molality;
        pureByTemperature.put(reference.temperatureK, pure);
      }
      double brine = KClCO2SolubilityReferenceAssessmentTest.calculate(reference).modelCo2Molality;
      double molality = reference.kclMolality;
      double q = inverseTemperature(reference.temperatureK);
      candidateDesign[row][0] = molality;
      candidateDesign[row][1] = molality * q;
      candidateDesign[row][2] = molality * molality * q;
      smoothFourDesign[row][0] = molality;
      smoothFourDesign[row][1] = molality * q;
      smoothFourDesign[row][2] = molality * molality;
      smoothFourDesign[row][3] = molality * molality * q;
      establishedDesign[row][temperatureIndex(reference.temperatureK)] = molality;
      establishedDesign[row][5] = molality * molality;
      establishedDesign[row][6] = molality * molality * q;
      observed[row] = Math.log(reference.sourceCo2ActivityCoefficient) - Math.log(pure / brine);
      rows.add(calibrationRow(reference, observed[row]));
    }

    Fit candidate = fit(candidateDesign, observed, algebra);
    Fit smoothFour = fit(smoothFourDesign, observed, algebra);
    Fit established = fit(establishedDesign, observed, algebra);
    rows.add(fitRow("CANDIDATE_THREE_PARAMETER", candidate));
    rows.add(fitRow("SMOOTH_FOUR_PARAMETER", smoothFour));
    rows.add(fitRow("ESTABLISHED_SEVEN_PARAMETER", established));

    assertEquals(3, candidate.rank, "three identifiable residual coefficients");
    assertTrue(candidate.conditionNumber < 40.0, "candidate design remains numerically identifiable");
    assertTrue(candidate.aicc < smoothFour.aicc, "unsupported reference-temperature curvature is rejected");
    assertTrue(candidate.aicc < established.aicc, "temperature-specific linear nuisance terms are not retained");
    assertTrue(candidate.maxAbsoluteCorrelation() < 0.95, "candidate covariance remains bounded");
    for (int parameter = 0; parameter < candidate.parameters.length; parameter++) {
      assertTrue(Math.abs(candidate.parameters[parameter]) > 3.0 * candidate.standardError(parameter),
          "candidate coefficient " + parameter + " must exceed three standard errors");
    }

    List<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference> kclReferences = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
        .loadReferences();
    List<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference> waterReferences = WaterNaClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
        .loadReferences();
    Summary at323 = new Summary();
    Summary at373 = new Summary();
    int selected = 0;
    for (KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference : kclReferences) {
      KClCO2SourceMatchedSaltEffectAssessmentTest.SourceInterpolation sourceWater = KClCO2SourceMatchedSaltEffectAssessmentTest
          .interpolateSourceWater(reference, waterReferences);
      if (sourceWater == null) {
        continue;
      }
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result brine = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(reference, CO2_FEED_MOLES);
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(reference.withoutSalt(), brine.co2FeedMoles);
      double sourceSalt = Math.log(sourceWater.linearMolePercent / reference.sourceCo2MolePercent);
      double modelSalt = Math.log(pureWater.molecularMolePercent / brine.molecularMolePercent);
      double baselineResidual = modelSalt - sourceSalt;
      double molality = KClCO2SourceMatchedSaltEffectAssessmentTest.kclMolality(reference.kclMassFractionPercent);
      double correction = candidate.correctionAt(reference.temperatureK, molality);
      double correctionStandardError = candidate.standardErrorAt(reference.temperatureK, molality);
      double candidateResidual = baselineResidual + correction;
      Summary summary = reference.temperatureK == 323.15 ? at323 : at373;
      summary.add(baselineResidual, candidateResidual);
      rows.add(
          holdoutRow(reference, molality, baselineResidual, correction, correctionStandardError, candidateResidual));
      selected++;
    }
    rows.add(summaryRow("SUMMARY-323.15K", at323));
    rows.add(summaryRow("SUMMARY-373.15K", at373));

    assertEquals(13, selected, "same-source KCl holdout states");
    assertEquals(7, at323.count, "323.15 K holdout states");
    assertEquals(6, at373.count, "373.15 K extrapolative holdout states");
    assertEquals(7, at323.improvedCount, "candidate improves every in-range 323.15 K holdout");
    assertEquals(4, at373.improvedCount, "373.15 K transfer exposes two local overcorrections");
    assertTrue(at323.candidateRmse() < at323.baselineRmse(), "candidate improves in-range holdout RMSE");
    assertTrue(at373.candidateRmse() < at373.baselineRmse(), "candidate improves extrapolative aggregate RMSE");
    assertTrue(at323.candidateBias() > 0.0, "323.15 K candidate remains an under-correction");
    assertTrue(at373.candidateBias() < 0.0, "373.15 K candidate crosses to aggregate over-correction");

    int columns = rows.get(0).split(",", -1).length;
    for (String row : rows) {
      assertEquals(columns, row.split(",", -1).length, "stable evidence schema");
    }
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), rows, StandardCharsets.UTF_8);
  }

  /** Fits one ordinary least-squares design and its covariance. */
  private static Fit fit(double[][] design, double[] observed, LinearAlgebraOperations algebra) {
    double[] parameters = algebra.solveLeastSquares(design, observed);
    double[] calculated = algebra.multiply(design, parameters);
    double sumSquares = 0.0;
    double maxAbsoluteResidual = 0.0;
    for (int row = 0; row < observed.length; row++) {
      double residual = calculated[row] - observed[row];
      sumSquares += residual * residual;
      maxAbsoluteResidual = Math.max(maxAbsoluteResidual, Math.abs(residual));
    }
    int rank = algebra.rank(design);
    double variance = sumSquares / (observed.length - rank);
    double[][] covariance = algebra.scale(algebra.invert(algebra.multiply(algebra.transpose(design), design)),
        variance);
    double rmse = Math.sqrt(sumSquares / observed.length);
    double aicc = observed.length * Math.log(sumSquares / observed.length) + 2.0 * rank
        + 2.0 * rank * (rank + 1.0) / (observed.length - rank - 1.0);
    return new Fit(parameters, covariance, rank, algebra.conditionNumber(design), rmse, maxAbsoluteResidual, aicc);
  }

  /** Returns the inverse-temperature coordinate. */
  private static double inverseTemperature(double temperatureK) {
    return REFERENCE_TEMPERATURE_K / temperatureK - 1.0;
  }

  /** Finds one retained He-Morse temperature block. */
  private static int temperatureIndex(double temperatureK) {
    for (int index = 0; index < TEMPERATURES_K.length; index++) {
      if (temperatureK == TEMPERATURES_K[index]) {
        return index;
      }
    }
    throw new IllegalArgumentException("Unexpected temperature " + temperatureK);
  }

  /** Returns the stable evidence header. */
  private static String header() {
    return "record_type,point_id,temperature_K,kcl_molality_mol_per_kg_water,calibration_log_residual,model,"
        + "points,parameters,rank,condition_number,rmse,max_abs_residual,aicc,lambda_ref,lambda_ref_se,"
        + "lambda_invT,lambda_invT_se,zeta_ref,zeta_ref_se,zeta_invT,zeta_invT_se,max_abs_correlation,"
        + "baseline_log_residual,candidate_correction,candidate_correction_se,candidate_log_residual,"
        + "summary_count,summary_improved_count,summary_baseline_bias,summary_baseline_rmse,"
        + "summary_candidate_bias,summary_candidate_rmse,summary_candidate_max_abs,disposition";
  }

  /** Formats one He-Morse calibration row. */
  private static String calibrationRow(KClCO2SolubilityReferenceAssessmentTest.Reference reference, double residual) {
    return row("CALIBRATION", reference.pointId, reference.temperatureK, reference.kclMolality, residual, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, "he_morse_calibration_source_minus_current_model");
  }

  /** Formats one model-fit summary row. */
  private static String fitRow(String model, Fit fit) {
    Double zetaRef = 0.0;
    Double zetaRefSe = null;
    if (fit.parameters.length == 4) {
      zetaRef = fit.parameters[2];
      zetaRefSe = fit.standardError(2);
    } else if (fit.parameters.length == 7) {
      zetaRef = fit.parameters[5];
      zetaRefSe = fit.standardError(5);
    }
    double zetaInv = fit.parameters[fit.parameters.length - 1];
    double zetaInvSe = fit.standardError(fit.parameters.length - 1);
    return row("FIT", model, null, null, null, model, 26, fit.parameters.length, fit.rank, fit.conditionNumber,
        fit.rmse, fit.maxAbsoluteResidual, fit.aicc, fit.parameters[0], fit.standardError(0),
        fit.parameters.length <= 4 ? fit.parameters[1] : null, fit.parameters.length <= 4 ? fit.standardError(1) : null,
        zetaRef, zetaRefSe, zetaInv, zetaInvSe, fit.maxAbsoluteCorrelation(), null, null, null, null, null, null, null,
        null, null, null, null, "candidate_selection_only_no_activation");
  }

  /** Formats one independent source-matched holdout row. */
  private static String holdoutRow(KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference,
      double molality, double baselineResidual, double correction, double correctionStandardError,
      double candidateResidual) {
    return row("HOLDOUT", reference.pointId, reference.temperatureK, molality, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, baselineResidual, correction,
        correctionStandardError, candidateResidual, null, null, null, null, null, null, null,
        reference.temperatureK <= 363.15 ? "independent_in_range_holdout" : "extrapolative_holdout_no_activation");
  }

  /** Formats one holdout summary row. */
  private static String summaryRow(String pointId, Summary summary) {
    return row("SUMMARY", pointId, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, summary.count, summary.improvedCount,
        summary.baselineBias(), summary.baselineRmse(), summary.candidateBias(), summary.candidateRmse(),
        summary.candidateMaxAbsoluteResidual,
        summary.candidateBias() < 0.0 ? "aggregate_overcorrection_blocks_activation" : "under_correction");
  }

  /** Converts values to one locale-independent CSV row. */
  private static String row(Object... values) {
    List<String> fields = new ArrayList<String>();
    for (Object value : values) {
      fields.add(value == null ? "" : String.format(Locale.ROOT, "%s", value));
    }
    return String.join(",", fields);
  }

  /** Holds one fit and its covariance diagnostics. */
  private static final class Fit {
    private final double[] parameters;
    private final double[][] covariance;
    private final int rank;
    private final double conditionNumber;
    private final double rmse;
    private final double maxAbsoluteResidual;
    private final double aicc;

    /** Creates an immutable fit result. */
    private Fit(double[] parameters, double[][] covariance, int rank, double conditionNumber, double rmse,
        double maxAbsoluteResidual, double aicc) {
      this.parameters = parameters;
      this.covariance = covariance;
      this.rank = rank;
      this.conditionNumber = conditionNumber;
      this.rmse = rmse;
      this.maxAbsoluteResidual = maxAbsoluteResidual;
      this.aicc = aicc;
    }

    /** Returns one parameter standard error. */
    private double standardError(int parameter) {
      return Math.sqrt(covariance[parameter][parameter]);
    }

    /** Returns the largest absolute off-diagonal parameter correlation. */
    private double maxAbsoluteCorrelation() {
      double maximum = 0.0;
      for (int row = 0; row < parameters.length; row++) {
        for (int column = row + 1; column < parameters.length; column++) {
          double correlation = covariance[row][column] / Math.sqrt(covariance[row][row] * covariance[column][column]);
          maximum = Math.max(maximum, Math.abs(correlation));
        }
      }
      return maximum;
    }

    /** Evaluates the selected three-parameter correction. */
    private double correctionAt(double temperatureK, double molality) {
      double q = inverseTemperature(temperatureK);
      return parameters[0] * molality + parameters[1] * molality * q + parameters[2] * molality * molality * q;
    }

    /** Propagates fitted covariance to one candidate correction. */
    private double standardErrorAt(double temperatureK, double molality) {
      double q = inverseTemperature(temperatureK);
      double[] coordinate = {molality, molality * q, molality * molality * q};
      double variance = 0.0;
      for (int row = 0; row < coordinate.length; row++) {
        for (int column = 0; column < coordinate.length; column++) {
          variance += coordinate[row] * covariance[row][column] * coordinate[column];
        }
      }
      return Math.sqrt(variance);
    }
  }

  /** Accumulates baseline and candidate holdout residuals. */
  private static final class Summary {
    private int count;
    private int improvedCount;
    private double baselineSum;
    private double baselineSquareSum;
    private double candidateSum;
    private double candidateSquareSum;
    private double candidateMaxAbsoluteResidual;

    /** Adds one holdout comparison. */
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
