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
 * Separates source, existing-model and residual KCl curvature before independent transfer.
 *
 * <p>
 * The He-Morse source response and current eCPA response are fitted to
 * {@code ln(gamma_CO2) = a_T m_KCl + zeta(T) m_KCl^2}, where {@code zeta(T) = zetaRef + zetaInvT*(298.15/T - 1)}. Model
 * activity uses a matched pure-water state at the same temperature and CO2 partial pressure. The independently
 * transferable increment is therefore {@code ln(gamma_source/gamma_model)}, not the full source coefficient. The
 * full-source and residual corrections are both tested against independent Mousavi-Chapoy-Burgass 323.15 K KCl states.
 * A concentration-intercept pressure discriminator then tests whether the remaining dense-source residual supports a
 * common Poynting-like pressure term. This first-order diagnostic changes no production parameter or flash equation.
 * </p>
 *
 * @author OpenAI
 * @version 1.0
 */
class KClCO2SaltPairTransferAssessmentTest {
  private static final double REFERENCE_TEMPERATURE_K = 298.15;
  private static final double KCL_MOLAR_MASS_KG_PER_MOL = 0.0745513;
  private static final double GAS_CONSTANT_J_PER_MOL_K = 8.314462618;
  private static final double TRANSFER_TEMPERATURE_K = 323.15;
  private static final double[] TEMPERATURES_K = {273.15, 298.15, 323.15, 348.15, 363.15};
  private static final String OUTPUT = "target/co2-kcl-salt-pair-transfer-assessment.csv";

  /**
   * Fits the incremental response and transfers both competing corrections to independent states.
   *
   * @throws Exception if a source fixture, model state or diagnostic output cannot be read or written
   */
  @Tag("slow")
  @Test
  void residualCurvaturePreventsDoubleCountingButDoesNotTransfer() throws Exception {
    ActivityFits fits = fitActivityCurvatures(new EjmlLinearAlgebra());
    double sourceZeta = fits.source.zetaAt(TRANSFER_TEMPERATURE_K);
    double modelZeta = fits.model.zetaAt(TRANSFER_TEMPERATURE_K);
    double residualZeta = fits.residual.zetaAt(TRANSFER_TEMPERATURE_K);
    double sourceZetaSe = fits.source.zetaStandardErrorAt(TRANSFER_TEMPERATURE_K);
    double modelZetaSe = fits.model.zetaStandardErrorAt(TRANSFER_TEMPERATURE_K);
    double residualZetaSe = fits.residual.zetaStandardErrorAt(TRANSFER_TEMPERATURE_K);
    assertTrue(sourceZeta < 0.0, "He-Morse total curvature remains negative");
    assertTrue(modelZeta < sourceZeta, "eCPA already has more negative curvature than the source");
    assertTrue(residualZeta > 3.0 * residualZetaSe, "incremental residual curvature is significantly positive");
    assertEquals(sourceZeta - modelZeta, residualZeta, 1.0e-12, "residual must not double count model curvature");
    assertTrue(fits.source.candidateRmse < fits.source.linearRmse);
    assertTrue(fits.model.candidateRmse < fits.model.linearRmse);
    assertTrue(fits.residual.candidateRmse < fits.residual.linearRmse);

    List<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference> references = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
        .loadReferences();
    List<String> rows = new ArrayList<String>();
    rows.add(header());
    double[][] groups = new double[3][10];
    List<PressurePoint> pressurePoints = new ArrayList<PressurePoint>();
    int selected = 0;
    for (KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference : references) {
      if (reference.temperatureK != TRANSFER_TEMPERATURE_K || reference.kclMassFractionPercent <= 0.0
          || reference.naclMassFractionPercent != 0.0 || reference.cacl2MassFractionPercent != 0.0
          || reference.mgcl2MassFractionPercent != 0.0) {
        continue;
      }
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result baseline = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(reference, 10.0);
      double molality = kclMolality(reference.kclMassFractionPercent);
      double sourceCandidate = baseline.molecularMolePercent * Math.exp(-sourceZeta * molality * molality);
      double residualCandidate = baseline.molecularMolePercent * Math.exp(-residualZeta * molality * molality);
      double baselineLog = Math.log(baseline.molecularMolePercent / reference.sourceCo2MolePercent);
      double sourceLog = Math.log(sourceCandidate / reference.sourceCo2MolePercent);
      double residualLog = Math.log(residualCandidate / reference.sourceCo2MolePercent);
      double baselineError = relativeError(baseline.molecularMolePercent, reference.sourceCo2MolePercent);
      double sourceError = relativeError(sourceCandidate, reference.sourceCo2MolePercent);
      double residualError = relativeError(residualCandidate, reference.sourceCo2MolePercent);
      accumulate(groups[concentrationGroup(reference.kclMassFractionPercent)], baselineLog, sourceLog, residualLog,
          baselineError, sourceError, residualError);
      pressurePoints.add(new PressurePoint(concentrationGroup(reference.kclMassFractionPercent),
          reference.totalPressureMpa, baselineLog));
      rows.add(transferRow(reference, molality, baseline.molecularMolePercent, sourceCandidate, residualCandidate,
          baselineLog, sourceLog, residualLog, baselineError, sourceError, residualError, fits, sourceZeta,
          sourceZetaSe, modelZeta, modelZetaSe, residualZeta, residualZetaSe));
      selected++;
    }
    assertEquals(9, selected, "independent 323.15 K KCl states");
    PressureDiagnostics pressure = fitPressureDiagnostics(pressurePoints, new EjmlLinearAlgebra());
    assertTrue(Math.abs(pressure.commonSlopeTStatistic) < 2.0,
        "common pressure slope must remain statistically unresolved");
    assertTrue(pressure.commonSlope.aicc > pressure.interceptOnly.aicc,
        "common pressure slope must not outperform concentration intercepts by AICc");
    assertTrue(pressure.groupSlopes[0] > 0.0 && pressure.groupSlopes[1] < 0.0 && pressure.groupSlopes[2] < 0.0,
        "retained concentration families must preserve their inconsistent pressure-slope signs");

    double[] aggregate = new double[10];
    for (int group = 0; group < groups.length; group++) {
      merge(aggregate, groups[group]);
      rows.add(summaryRow("SUMMARY-KCL" + (group == 0 ? 10 : group == 1 ? 15 : 22), groups[group], fits, sourceZeta,
          sourceZetaSe, modelZeta, modelZetaSe, residualZeta, residualZetaSe, pressure, group));
    }
    rows.add(summaryRow("SUMMARY", aggregate, fits, sourceZeta, sourceZetaSe, modelZeta, modelZetaSe, residualZeta,
        residualZetaSe, pressure, null));
    assertEquals(14, rows.size());
    int columns = rows.get(0).split(",", -1).length;
    for (String row : rows) {
      assertEquals(columns, row.split(",", -1).length, "stable evidence schema");
    }
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), rows, StandardCharsets.UTF_8);

    assertTrue(rmse(aggregate, 2) < rmse(aggregate, 1), "full-source correction retains aggregate improvement");
    assertTrue(rmse(aggregate, 3) > rmse(aggregate, 1), "identified residual fails independent transfer");
    for (double[] group : groups) {
      assertTrue(group[3] > group[1], "residual correction worsens every concentration family");
    }
    assertTrue(groups[2][2] > groups[2][1], "full-source correction retains the 22 wt% breakdown");
    assertTrue(groups[2][4] < 0.0 && groups[2][5] > 0.0,
        "full-source correction retains the dense-family residual-sign reversal");
  }

  /** Fits source, current-model and residual activity responses on an identical design matrix. */
  private static ActivityFits fitActivityCurvatures(LinearAlgebraOperations algebra) throws Exception {
    List<KClCO2SolubilityReferenceAssessmentTest.Reference> references = KClCO2SolubilityReferenceAssessmentTest
        .loadReferences();
    assertEquals(26, references.size());
    Map<Double, Double> pureByTemperature = new HashMap<Double, Double>();
    double[][] design = new double[references.size()][7];
    double[] source = new double[references.size()];
    double[] model = new double[references.size()];
    double[] residual = new double[references.size()];
    for (int row = 0; row < references.size(); row++) {
      KClCO2SolubilityReferenceAssessmentTest.Reference reference = references.get(row);
      Double pure = pureByTemperature.get(reference.temperatureK);
      if (pure == null) {
        pure = KClCO2SolubilityReferenceAssessmentTest.calculate(reference.withoutSalt()).modelCo2Molality;
        pureByTemperature.put(reference.temperatureK, pure);
      }
      double brine = KClCO2SolubilityReferenceAssessmentTest.calculate(reference).modelCo2Molality;
      design[row][temperatureIndex(reference.temperatureK)] = reference.kclMolality;
      design[row][5] = reference.kclMolality * reference.kclMolality;
      design[row][6] = design[row][5] * (REFERENCE_TEMPERATURE_K / reference.temperatureK - 1.0);
      source[row] = Math.log(reference.sourceCo2ActivityCoefficient);
      model[row] = Math.log(pure / brine);
      residual[row] = source[row] - model[row];
    }
    return new ActivityFits(fit(design, source, algebra), fit(design, model, algebra), fit(design, residual, algebra));
  }

  /**
   * Returns the complete fitted He-Morse source-minus-model KCl salt-pair response.
   *
   * @return residual linear-plus-quadratic law on the retained molality basis
   * @throws Exception if a source fixture or matched model state cannot be evaluated
   */
  static ResidualSaltPairLaw fitResidualSaltPairLaw() throws Exception {
    FitStatistics residual = fitActivityCurvatures(new EjmlLinearAlgebra()).residual;
    return new ResidualSaltPairLaw(residual.parameters, residual.covariance);
  }

  /** Fits the reduced law and its five-linear-coefficient comparator. */
  private static FitStatistics fit(double[][] design, double[] observed, LinearAlgebraOperations algebra) {
    double[][] linearDesign = new double[design.length][5];
    for (int row = 0; row < design.length; row++) {
      System.arraycopy(design[row], 0, linearDesign[row], 0, 5);
    }
    Regression linear = regression(linearDesign, observed, algebra);
    Regression candidate = regression(design, observed, algebra);
    double variance = candidate.sumSquares / (observed.length - candidate.rank);
    double[][] covariance = algebra.scale(algebra.invert(algebra.multiply(algebra.transpose(design), design)),
        variance);
    return new FitStatistics(candidate.parameters, covariance, linear.rmse, candidate.rmse, linear.aicc,
        candidate.aicc);
  }

  /** Calculates one ordinary least-squares regression. */
  private static Regression regression(double[][] design, double[] observed, LinearAlgebraOperations algebra) {
    double[] parameters = algebra.solveLeastSquares(design, observed);
    double[] calculated = algebra.multiply(design, parameters);
    double sumSquares = 0.0;
    for (int row = 0; row < observed.length; row++) {
      double difference = calculated[row] - observed[row];
      sumSquares += difference * difference;
    }
    int rank = algebra.rank(design);
    double rmse = Math.sqrt(sumSquares / observed.length);
    double aicc = observed.length * Math.log(sumSquares / observed.length) + 2.0 * rank
        + 2.0 * rank * (rank + 1.0) / (observed.length - rank - 1.0);
    return new Regression(parameters, sumSquares, rank, rmse, aicc);
  }

  /** Fits concentration intercepts with and without one common pressure slope. */
  private static PressureDiagnostics fitPressureDiagnostics(List<PressurePoint> points,
      LinearAlgebraOperations algebra) {
    double[][] interceptDesign = new double[points.size()][3];
    double[][] pressureDesign = new double[points.size()][4];
    double[] observed = new double[points.size()];
    for (int row = 0; row < points.size(); row++) {
      PressurePoint point = points.get(row);
      interceptDesign[row][point.group] = 1.0;
      System.arraycopy(interceptDesign[row], 0, pressureDesign[row], 0, 3);
      pressureDesign[row][3] = point.pressureMpa;
      observed[row] = point.logResidual;
    }
    Regression interceptOnly = regression(interceptDesign, observed, algebra);
    Regression commonSlope = regression(pressureDesign, observed, algebra);
    double variance = commonSlope.sumSquares / (observed.length - commonSlope.rank);
    double[][] covariance = algebra
        .scale(algebra.invert(algebra.multiply(algebra.transpose(pressureDesign), pressureDesign)), variance);
    double slopeStandardError = Math.sqrt(covariance[3][3]);
    double[] groupSlopes = new double[3];
    for (int group = 0; group < groupSlopes.length; group++) {
      int count = 0;
      for (PressurePoint point : points) {
        if (point.group == group) {
          count++;
        }
      }
      double[][] groupDesign = new double[count][2];
      double[] groupObserved = new double[count];
      int row = 0;
      for (PressurePoint point : points) {
        if (point.group == group) {
          groupDesign[row][0] = 1.0;
          groupDesign[row][1] = point.pressureMpa;
          groupObserved[row] = point.logResidual;
          row++;
        }
      }
      groupSlopes[group] = algebra.solveLeastSquares(groupDesign, groupObserved)[1];
    }
    return new PressureDiagnostics(interceptOnly, commonSlope, slopeStandardError, groupSlopes);
  }

  /** Returns the stable evidence header. */
  private static String header() {
    return "record_type,point_id,kcl_mass_fraction_percent,kcl_molality_mol_per_kg_water,"
        + "source_co2_mole_percent_salt_free,baseline_co2_mole_percent_salt_free,"
        + "source_candidate_co2_mole_percent_salt_free,residual_candidate_co2_mole_percent_salt_free,"
        + "baseline_log_residual,source_candidate_log_residual,residual_candidate_log_residual,"
        + "baseline_relative_error_percent,source_candidate_relative_error_percent,"
        + "residual_candidate_relative_error_percent,source_zeta_323,source_zeta_323_standard_error,"
        + "model_zeta_323,model_zeta_323_standard_error,residual_zeta_323,residual_zeta_323_standard_error,"
        + "source_linear_rmse,source_candidate_rmse,source_linear_aicc,source_candidate_aicc,"
        + "model_linear_rmse,model_candidate_rmse,model_linear_aicc,model_candidate_aicc,"
        + "residual_linear_rmse,residual_candidate_rmse,residual_linear_aicc,residual_candidate_aicc,"
        + "baseline_log_rmse,source_candidate_log_rmse,residual_candidate_log_rmse,baseline_mape,"
        + "source_candidate_mape,residual_candidate_mape,pressure_group_slope_per_mpa,"
        + "pressure_intercept_only_log_rmse,pressure_intercept_only_aicc,pressure_common_slope_per_mpa,"
        + "pressure_common_slope_standard_error_per_mpa,pressure_common_slope_t_statistic,"
        + "pressure_common_slope_log_rmse,pressure_common_slope_aicc,"
        + "apparent_delta_partial_molar_volume_cm3_per_mol,disposition";
  }

  /** Formats one independent transfer row. */
  private static String transferRow(KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference,
      double molality, double baseline, double sourceCandidate, double residualCandidate, double baselineLog,
      double sourceLog, double residualLog, double baselineError, double sourceError, double residualError,
      ActivityFits fits, double sourceZeta, double sourceZetaSe, double modelZeta, double modelZetaSe,
      double residualZeta, double residualZetaSe) {
    return row("TRANSFER", reference.pointId, reference.kclMassFractionPercent, molality,
        reference.sourceCo2MolePercent, baseline, sourceCandidate, residualCandidate, baselineLog, sourceLog,
        residualLog, baselineError, sourceError, residualError, sourceZeta, sourceZetaSe, modelZeta, modelZetaSe,
        residualZeta, residualZetaSe, fits.source.linearRmse, fits.source.candidateRmse, fits.source.linearAicc,
        fits.source.candidateAicc, fits.model.linearRmse, fits.model.candidateRmse, fits.model.linearAicc,
        fits.model.candidateAicc, fits.residual.linearRmse, fits.residual.candidateRmse, fits.residual.linearAicc,
        fits.residual.candidateAicc, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, "diagnostic_only_no_activation");
  }

  /** Formats one concentration-family or aggregate summary row. */
  private static String summaryRow(String label, double[] statistics, ActivityFits fits, double sourceZeta,
      double sourceZetaSe, double modelZeta, double modelZetaSe, double residualZeta, double residualZetaSe,
      PressureDiagnostics pressure, Integer pressureGroup) {
    boolean aggregate = pressureGroup == null;
    return row("SUMMARY", label, null, null, null, null, null, null, null, null, null, null, null, null, sourceZeta,
        sourceZetaSe, modelZeta, modelZetaSe, residualZeta, residualZetaSe, fits.source.linearRmse,
        fits.source.candidateRmse, fits.source.linearAicc, fits.source.candidateAicc, fits.model.linearRmse,
        fits.model.candidateRmse, fits.model.linearAicc, fits.model.candidateAicc, fits.residual.linearRmse,
        fits.residual.candidateRmse, fits.residual.linearAicc, fits.residual.candidateAicc, rmse(statistics, 1),
        rmse(statistics, 2), rmse(statistics, 3), mape(statistics, 7), mape(statistics, 8), mape(statistics, 9),
        aggregate ? null : pressure.groupSlopes[pressureGroup], aggregate ? pressure.interceptOnly.rmse : null,
        aggregate ? pressure.interceptOnly.aicc : null, aggregate ? pressure.commonSlope.parameters[3] : null,
        aggregate ? pressure.commonSlopeStandardError : null, aggregate ? pressure.commonSlopeTStatistic : null,
        aggregate ? pressure.commonSlope.rmse : null, aggregate ? pressure.commonSlope.aicc : null,
        aggregate ? pressure.apparentDeltaPartialMolarVolumeCm3PerMol : null,
        aggregate ? "residual_curvature_not_transferable_pressure_not_identifiable"
            : "pressure_group_slope_diagnostic_only");
  }

  /** Converts values to one locale-independent CSV row. */
  private static String row(Object... values) {
    List<String> fields = new ArrayList<String>();
    for (Object value : values) {
      fields.add(value == null ? "" : String.format(Locale.ROOT, "%s", value));
    }
    return String.join(",", fields);
  }

  /** Accumulates squared and signed log residuals plus absolute relative errors. */
  private static void accumulate(double[] stats, double baselineLog, double sourceLog, double residualLog,
      double baselineError, double sourceError, double residualError) {
    stats[0]++;
    stats[1] += baselineLog * baselineLog;
    stats[2] += sourceLog * sourceLog;
    stats[3] += residualLog * residualLog;
    stats[4] += baselineLog;
    stats[5] += sourceLog;
    stats[6] += residualLog;
    stats[7] += Math.abs(baselineError);
    stats[8] += Math.abs(sourceError);
    stats[9] += Math.abs(residualError);
  }

  /** Merges a concentration-family accumulator into the aggregate. */
  private static void merge(double[] aggregate, double[] group) {
    for (int index = 0; index < aggregate.length; index++) {
      aggregate[index] += group[index];
    }
  }

  /** Returns log-RMSE from a squared-residual accumulator. */
  private static double rmse(double[] statistics, int index) {
    return Math.sqrt(statistics[index] / statistics[0]);
  }

  /** Returns MAPE from an absolute-relative-error accumulator. */
  private static double mape(double[] statistics, int index) {
    return statistics[index] / statistics[0];
  }

  /** Returns signed relative error in percent. */
  private static double relativeError(double calculated, double source) {
    return 100.0 * (calculated / source - 1.0);
  }

  /** Converts anhydrous KCl mass fraction to source-basis molality. */
  private static double kclMolality(double massFractionPercent) {
    return massFractionPercent / (100.0 - massFractionPercent) / KCL_MOLAR_MASS_KG_PER_MOL;
  }

  /** Returns the retained-temperature column index. */
  private static int temperatureIndex(double temperatureK) {
    for (int index = 0; index < TEMPERATURES_K.length; index++) {
      if (temperatureK == TEMPERATURES_K[index]) {
        return index;
      }
    }
    throw new IllegalArgumentException("Unexpected temperature " + temperatureK);
  }

  /** Maps 10, 15 and 22 wt% KCl to stable summary indices. */
  private static int concentrationGroup(double massFractionPercent) {
    if (massFractionPercent == 10.0) {
      return 0;
    }
    if (massFractionPercent == 15.0) {
      return 1;
    }
    if (massFractionPercent == 22.0) {
      return 2;
    }
    throw new IllegalArgumentException("Unexpected KCl mass fraction " + massFractionPercent);
  }

  /** Complete source-minus-model KCl activity response retained for independent transfer tests. */
  static final class ResidualSaltPairLaw {
    private final double[] parameters;
    private final double[][] covariance;

    /** Creates an immutable fitted response. */
    private ResidualSaltPairLaw(double[] parameters, double[][] covariance) {
      this.parameters = parameters.clone();
      this.covariance = new double[covariance.length][];
      for (int row = 0; row < covariance.length; row++) {
        this.covariance[row] = covariance[row].clone();
      }
    }

    /**
     * Evaluates the complete linear-plus-quadratic log-activity correction.
     *
     * @param temperatureK retained He-Morse source temperature in K
     * @param molality KCl molality in mol/kg water
     * @return source-minus-model log-activity correction
     */
    double correctionAt(double temperatureK, double molality) {
      double squared = molality * molality;
      double inverseTemperature = REFERENCE_TEMPERATURE_K / temperatureK - 1.0;
      return parameters[temperatureIndex(temperatureK)] * molality + parameters[5] * squared
          + parameters[6] * squared * inverseTemperature;
    }

    /**
     * Propagates fitted covariance to one complete correction.
     *
     * @param temperatureK retained He-Morse source temperature in K
     * @param molality KCl molality in mol/kg water
     * @return standard error of the log-activity correction
     */
    double standardErrorAt(double temperatureK, double molality) {
      double[] coordinate = new double[7];
      double squared = molality * molality;
      coordinate[temperatureIndex(temperatureK)] = molality;
      coordinate[5] = squared;
      coordinate[6] = squared * (REFERENCE_TEMPERATURE_K / temperatureK - 1.0);
      double variance = 0.0;
      for (int row = 0; row < coordinate.length; row++) {
        for (int column = 0; column < coordinate.length; column++) {
          variance += coordinate[row] * covariance[row][column] * coordinate[column];
        }
      }
      return Math.sqrt(variance);
    }
  }

  /** Holds source, current-model and residual fits. */
  private static final class ActivityFits {
    private final FitStatistics source;
    private final FitStatistics model;
    private final FitStatistics residual;

    /** Creates a comparable three-fit result. */
    private ActivityFits(FitStatistics source, FitStatistics model, FitStatistics residual) {
      this.source = source;
      this.model = model;
      this.residual = residual;
    }
  }

  /** Holds one retained dense-source pressure and residual coordinate. */
  private static final class PressurePoint {
    private final int group;
    private final double pressureMpa;
    private final double logResidual;

    /** Creates one immutable pressure point. */
    private PressurePoint(int group, double pressureMpa, double logResidual) {
      this.group = group;
      this.pressureMpa = pressureMpa;
      this.logResidual = logResidual;
    }
  }

  /** Holds nested pressure-model diagnostics without defining a production pressure law. */
  private static final class PressureDiagnostics {
    private final Regression interceptOnly;
    private final Regression commonSlope;
    private final double commonSlopeStandardError;
    private final double commonSlopeTStatistic;
    private final double apparentDeltaPartialMolarVolumeCm3PerMol;
    private final double[] groupSlopes;

    /** Creates immutable pressure diagnostics and the explicitly signed apparent-volume mapping. */
    private PressureDiagnostics(Regression interceptOnly, Regression commonSlope, double commonSlopeStandardError,
        double[] groupSlopes) {
      this.interceptOnly = interceptOnly;
      this.commonSlope = commonSlope;
      this.commonSlopeStandardError = commonSlopeStandardError;
      this.commonSlopeTStatistic = commonSlope.parameters[3] / commonSlopeStandardError;
      this.apparentDeltaPartialMolarVolumeCm3PerMol = -GAS_CONSTANT_J_PER_MOL_K * TRANSFER_TEMPERATURE_K
          * commonSlope.parameters[3];
      this.groupSlopes = groupSlopes;
    }
  }

  /** Holds one candidate fit, comparator diagnostics and covariance. */
  private static final class FitStatistics {
    private final double[] parameters;
    private final double[][] covariance;
    private final double linearRmse;
    private final double candidateRmse;
    private final double linearAicc;
    private final double candidateAicc;

    /** Creates immutable fit diagnostics. */
    private FitStatistics(double[] parameters, double[][] covariance, double linearRmse, double candidateRmse,
        double linearAicc, double candidateAicc) {
      this.parameters = parameters;
      this.covariance = covariance;
      this.linearRmse = linearRmse;
      this.candidateRmse = candidateRmse;
      this.linearAicc = linearAicc;
      this.candidateAicc = candidateAicc;
    }

    /** Evaluates fitted curvature at one temperature. */
    private double zetaAt(double temperatureK) {
      return parameters[5] + parameters[6] * (REFERENCE_TEMPERATURE_K / temperatureK - 1.0);
    }

    /** Propagates covariance to curvature uncertainty at one temperature. */
    private double zetaStandardErrorAt(double temperatureK) {
      double coordinate = REFERENCE_TEMPERATURE_K / temperatureK - 1.0;
      double variance = covariance[5][5] + 2.0 * coordinate * covariance[5][6]
          + coordinate * coordinate * covariance[6][6];
      return Math.sqrt(variance);
    }
  }

  /** Holds scalar ordinary-least-squares diagnostics. */
  private static final class Regression {
    private final double[] parameters;
    private final double sumSquares;
    private final int rank;
    private final double rmse;
    private final double aicc;

    /** Creates immutable regression diagnostics. */
    private Regression(double[] parameters, double sumSquares, int rank, double rmse, double aicc) {
      this.parameters = parameters;
      this.sumSquares = sumSquares;
      this.rank = rank;
      this.rmse = rmse;
      this.aicc = aicc;
    }
  }
}
