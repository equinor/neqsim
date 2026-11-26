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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.mathlib.linearalgebra.EjmlLinearAlgebra;
import neqsim.mathlib.linearalgebra.LinearAlgebraOperations;

/**
 * Source-space identifiability audit for a reduced CO2/KCl salt-pair temperature law.
 *
 * <p>
 * The candidate curvature is {@code zeta(T) = zetaRef + zetaInvT*(298.15/T - 1)} in the He-Morse molality convention.
 * Five temperature-specific linear coefficients are nuisance parameters. The assessment compares this seven-parameter
 * model with a five-parameter linear model and a ten-parameter saturated-curvature model, reports covariance and
 * leave-one-temperature sensitivity, and retains the independent Yasunishi-Yoshida curvature sign on its own molarity
 * basis. It does not transfer a coefficient into electrolyte CPA, select pressure dependence, or consume mixed-brine or
 * hydrate holdouts.
 * </p>
 *
 * @author OpenAI
 * @version 1.0
 */
class CO2SaltPairTemperatureLawAssessmentTest {
  private static final Logger logger = LogManager.getLogger(CO2SaltPairTemperatureLawAssessmentTest.class);
  private static final String HE_MORSE_RESOURCE = "/data/chemistry_benchmarks/co2_kcl_solubility_he_morse1993.csv";
  private static final String YASUNISHI_RESOURCE = "/data/chemistry_benchmarks/co2_kcl_ostwald_yasunishi_yoshida1979.csv";
  private static final String OUTPUT = "target/co2-salt-pair-temperature-law-assessment.csv";
  private static final double REFERENCE_TEMPERATURE_K = 298.15;
  private static final double[] TEMPERATURES_K = {273.15, 298.15, 323.15, 348.15, 363.15};

  /**
   * Audits a reduced inverse-temperature curvature law without activating a model parameter.
   *
   * @throws Exception if a retained source cannot be read or evidence cannot be written
   */
  @Tag("slow")
  @Test
  void reducedTemperatureLawHasResidualDegreesOfFreedomButPressureRemainsUnidentified() throws Exception {
    LinearAlgebraOperations algebra = new EjmlLinearAlgebra();
    List<HeMorsePoint> heMorse = loadHeMorse();
    List<YasunishiPoint> yasunishi = loadYasunishi();
    assertEquals(26, heMorse.size());
    assertEquals(7, yasunishi.size());

    FitResult linear = fit(buildLinearDesign(heMorse), response(heMorse), algebra);
    FitResult candidate = fit(buildCandidateDesign(heMorse), response(heMorse), algebra);
    FitResult saturated = fit(buildSaturatedDesign(heMorse), response(heMorse), algebra);

    assertEquals(5, linear.rank);
    assertEquals(7, candidate.rank);
    assertEquals(10, saturated.rank);
    assertEquals(19, candidate.residualDegreesOfFreedom);
    assertTrue(candidate.rmse < linear.rmse, "salt-pair curvature must improve the source fit");
    assertTrue(candidate.aicc < linear.aicc,
        "reduced curvature must improve corrected information criterion over the linear model");
    assertTrue(candidate.aicc < saturated.aicc,
        "source evidence must prefer the reduced law over saturated per-temperature curvature");
    assertTrue(candidate.parameters[5] < 0.0, "298.15 K curvature must retain its source sign");
    assertTrue(candidate.parameters[6] < 0.0, "inverse-temperature coefficient must retain its fitted source sign");

    double[][] candidateCovariance = covariance(buildCandidateDesign(heMorse), candidate, algebra);
    double zetaReferenceStandardError = Math.sqrt(candidateCovariance[5][5]);
    double zetaInvTemperatureStandardError = Math.sqrt(candidateCovariance[6][6]);
    double zetaCorrelation = candidateCovariance[5][6]
        / Math.sqrt(candidateCovariance[5][5] * candidateCovariance[6][6]);
    assertTrue(Double.isFinite(zetaCorrelation) && Math.abs(zetaCorrelation) < 1.0);
    assertTrue(candidate.conditionNumber < 100.0, "reduced source design must remain numerically identifiable");

    List<HoldoutResult> holdouts = new ArrayList<HoldoutResult>();
    for (int heldIndex = 0; heldIndex < TEMPERATURES_K.length; heldIndex++) {
      holdouts.add(leaveOneTemperatureOut(heMorse, heldIndex, algebra));
    }
    double holdoutRmse = aggregateRmse(holdouts);
    double holdoutMaximum = aggregateMaximum(holdouts);
    assertTrue(Double.isFinite(holdoutRmse) && Double.isFinite(holdoutMaximum));

    double highTemperatureZeta = zetaAt(candidate.parameters, TEMPERATURES_K[4]);
    double highTemperatureStandardError = zetaStandardErrorAt(candidateCovariance, TEMPERATURES_K[4]);
    assertTrue(highTemperatureZeta - 2.0 * highTemperatureStandardError < 0.0);
    assertTrue(highTemperatureZeta + 2.0 * highTemperatureStandardError > 0.0,
        "high-temperature curvature sign must remain an explicit uncertainty blocker");

    SourceShapeResult independentShape = fitYasunishi(yasunishi, algebra);
    assertTrue(independentShape.quadraticRmse < independentShape.linearRmse,
        "independent molarity source must prefer curvature");
    assertTrue(independentShape.quadraticCoefficient < 0.0,
        "independent source must retain diminishing salting-out curvature");

    int maximumWithinTemperaturePressureCount = maximumWithinTemperaturePressureCount(heMorse);
    assertEquals(1, maximumWithinTemperaturePressureCount,
        "He-Morse cannot identify pressure response within a temperature block");

    List<String> report = new ArrayList<String>();
    report.add("record_type,family,role,temperature_K,model,points,parameters,"
        + "residual_degrees_of_freedom,rank,condition_number,rmse,max_abs_residual,aicc,"
        + "aicc_delta_to_candidate,zeta_ref_kg2_per_mol2,zeta_ref_standard_error,"
        + "zeta_invT_kg2_per_mol2,zeta_invT_standard_error,zeta_at_temperature_kg2_per_mol2,"
        + "zeta_at_temperature_standard_error,zeta_parameter_correlation,quadratic_coefficient,"
        + "holdout_aggregate_rmse,holdout_aggregate_max_abs_residual,disposition");
    report.add(modelRow("HE_MORSE_MODEL", "source_baseline", "LINEAR_PER_TEMPERATURE", linear, candidate, Double.NaN,
        Double.NaN, Double.NaN, Double.NaN, Double.NaN, "no_salt_pair_curvature"));
    report.add(modelRow("HE_MORSE_MODEL", "candidate_temperature_law", "TWO_PARAMETER_INVERSE_T_ZETA", candidate,
        candidate, candidate.parameters[5], zetaReferenceStandardError, candidate.parameters[6],
        zetaInvTemperatureStandardError, zetaCorrelation,
        "source_space_candidate_only_pressure_and_production_mapping_unqualified"));
    report.add(modelRow("HE_MORSE_MODEL", "upper_flexibility_comparator", "SATURATED_ZETA_PER_TEMPERATURE", saturated,
        candidate, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
        "extra_curvature_flexibility_not_supported_by_aicc"));
    for (HoldoutResult holdout : holdouts) {
      report.add(holdoutRow(holdout, holdoutRmse, holdoutMaximum));
    }
    report.add(independentShapeRow(independentShape));
    report.add(String.format(Locale.ROOT,
        "PRESSURE_IDENTIFIABILITY,HE_MORSE_KCL,source_design_audit,NaN,WITHIN_T_PRESSURE,"
            + "%d,0,NaN,NaN,NaN,NaN,NaN,NaN,NaN,NaN,NaN,NaN,NaN,NaN,NaN,NaN,NaN,%.12g,%.12g,"
            + "one_pressure_per_temperature_block_pressure_law_unidentified",
        heMorse.size(), holdoutRmse, holdoutMaximum));

    assertEquals(11, report.size());
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), report, StandardCharsets.UTF_8);
    for (String row : report) {
      logger.info("CO2_SALT_PAIR_TEMPERATURE_LAW,{}", row);
    }
  }

  /**
   * Fits one full-rank least-squares model and its information diagnostics.
   *
   * @param design regression design matrix
   * @param observed observed log activity coefficients
   * @param algebra numerical backend
   * @return immutable fit result
   */
  private static FitResult fit(double[][] design, double[] observed, LinearAlgebraOperations algebra) {
    double[] parameters = algebra.solveLeastSquares(design, observed);
    double[] calculated = algebra.multiply(design, parameters);
    double sumSquares = 0.0;
    double maximum = 0.0;
    for (int index = 0; index < observed.length; index++) {
      double residual = calculated[index] - observed[index];
      sumSquares += residual * residual;
      maximum = Math.max(maximum, Math.abs(residual));
    }
    int rank = algebra.rank(design);
    int residualDegreesOfFreedom = observed.length - rank;
    double rmse = Math.sqrt(sumSquares / observed.length);
    double aicc = correctedAkaike(observed.length, rank, sumSquares);
    return new FitResult(parameters, sumSquares, rmse, maximum, aicc, rank, residualDegreesOfFreedom,
        algebra.conditionNumber(design));
  }

  /**
   * Calculates the ordinary least-squares covariance matrix.
   *
   * @param design regression design matrix
   * @param fit fitted model
   * @param algebra numerical backend
   * @return covariance matrix using residual variance and inverse normal matrix
   */
  private static double[][] covariance(double[][] design, FitResult fit, LinearAlgebraOperations algebra) {
    double[][] normal = algebra.multiply(algebra.transpose(design), design);
    double variance = fit.sumSquares / fit.residualDegreesOfFreedom;
    return algebra.scale(algebra.invert(normal), variance);
  }

  /**
   * Builds the five-column no-curvature source model.
   *
   * @param points retained He-Morse points
   * @return design with one linear coefficient per temperature
   */
  private static double[][] buildLinearDesign(List<HeMorsePoint> points) {
    double[][] design = new double[points.size()][TEMPERATURES_K.length];
    for (int row = 0; row < points.size(); row++) {
      HeMorsePoint point = points.get(row);
      design[row][temperatureIndex(point.temperatureK)] = point.kclMolality;
    }
    return design;
  }

  /**
   * Builds the seven-column reduced salt-pair model.
   *
   * @param points retained He-Morse points
   * @return design with five linear nuisance terms and two curvature terms
   */
  private static double[][] buildCandidateDesign(List<HeMorsePoint> points) {
    double[][] design = new double[points.size()][TEMPERATURES_K.length + 2];
    for (int row = 0; row < points.size(); row++) {
      HeMorsePoint point = points.get(row);
      design[row][temperatureIndex(point.temperatureK)] = point.kclMolality;
      design[row][5] = point.kclMolality * point.kclMolality;
      design[row][6] = design[row][5] * (REFERENCE_TEMPERATURE_K / point.temperatureK - 1.0);
    }
    return design;
  }

  /**
   * Builds the ten-column saturated-curvature comparator.
   *
   * @param points retained He-Morse points
   * @return design with independent linear and quadratic terms at each temperature
   */
  private static double[][] buildSaturatedDesign(List<HeMorsePoint> points) {
    double[][] design = new double[points.size()][2 * TEMPERATURES_K.length];
    for (int row = 0; row < points.size(); row++) {
      HeMorsePoint point = points.get(row);
      int index = temperatureIndex(point.temperatureK);
      design[row][index] = point.kclMolality;
      design[row][TEMPERATURES_K.length + index] = point.kclMolality * point.kclMolality;
    }
    return design;
  }

  /**
   * Performs one leave-one-temperature-out shape check.
   *
   * <p>
   * The two curvature coefficients are fitted on the other four temperature blocks. The held block contributes only its
   * linear nuisance coefficient, so its curvature is a prediction.
   * </p>
   *
   * @param points retained He-Morse points
   * @param heldIndex index of the held temperature
   * @param algebra numerical backend
   * @return held-block prediction result
   */
  private static HoldoutResult leaveOneTemperatureOut(List<HeMorsePoint> points, int heldIndex,
      LinearAlgebraOperations algebra) {
    List<HeMorsePoint> training = new ArrayList<HeMorsePoint>();
    List<HeMorsePoint> held = new ArrayList<HeMorsePoint>();
    for (HeMorsePoint point : points) {
      if (temperatureIndex(point.temperatureK) == heldIndex) {
        held.add(point);
      } else {
        training.add(point);
      }
    }
    double[][] design = new double[training.size()][6];
    double[] observed = response(training);
    for (int row = 0; row < training.size(); row++) {
      HeMorsePoint point = training.get(row);
      int fullIndex = temperatureIndex(point.temperatureK);
      int compactIndex = fullIndex < heldIndex ? fullIndex : fullIndex - 1;
      design[row][compactIndex] = point.kclMolality;
      design[row][4] = point.kclMolality * point.kclMolality;
      design[row][5] = design[row][4] * (REFERENCE_TEMPERATURE_K / point.temperatureK - 1.0);
    }
    FitResult trainingFit = fit(design, observed, algebra);
    double zeta = trainingFit.parameters[4]
        + trainingFit.parameters[5] * (REFERENCE_TEMPERATURE_K / TEMPERATURES_K[heldIndex] - 1.0);
    double numerator = 0.0;
    double denominator = 0.0;
    for (HeMorsePoint point : held) {
      double concentration = point.kclMolality;
      numerator += concentration * (Math.log(point.activityCoefficient) - zeta * concentration * concentration);
      denominator += concentration * concentration;
    }
    double linearCoefficient = numerator / denominator;
    double sumSquares = 0.0;
    double maximum = 0.0;
    for (HeMorsePoint point : held) {
      double calculated = linearCoefficient * point.kclMolality + zeta * point.kclMolality * point.kclMolality;
      double residual = calculated - Math.log(point.activityCoefficient);
      sumSquares += residual * residual;
      maximum = Math.max(maximum, Math.abs(residual));
    }
    return new HoldoutResult(TEMPERATURES_K[heldIndex], held.size(), zeta, sumSquares, maximum);
  }

  /**
   * Fits linear and quadratic no-intercept shapes to the independent molarity source.
   *
   * @param points Yasunishi-Yoshida points including the salt-free reference
   * @param algebra numerical backend
   * @return independent source-shape result
   */
  private static SourceShapeResult fitYasunishi(List<YasunishiPoint> points, LinearAlgebraOperations algebra) {
    double pure = points.get(0).ostwaldCoefficient;
    assertEquals(0.0, points.get(0).kclMolarity, 0.0);
    double[][] linear = new double[points.size() - 1][1];
    double[][] quadratic = new double[points.size() - 1][2];
    double[] observed = new double[points.size() - 1];
    for (int index = 1; index < points.size(); index++) {
      double concentration = points.get(index).kclMolarity;
      linear[index - 1][0] = concentration;
      quadratic[index - 1][0] = concentration;
      quadratic[index - 1][1] = concentration * concentration;
      observed[index - 1] = Math.log(pure / points.get(index).ostwaldCoefficient);
    }
    FitResult linearFit = fit(linear, observed, algebra);
    FitResult quadraticFit = fit(quadratic, observed, algebra);
    return new SourceShapeResult(linearFit.rmse, quadraticFit.rmse, quadraticFit.parameters[1]);
  }

  /**
   * Calculates corrected Akaike information criterion for a Gaussian residual model.
   *
   * @param observations observation count
   * @param parameters fitted parameter count
   * @param sumSquares residual sum of squares
   * @return corrected Akaike information criterion
   */
  private static double correctedAkaike(int observations, int parameters, double sumSquares) {
    return observations * Math.log(sumSquares / observations) + 2.0 * parameters
        + 2.0 * parameters * (parameters + 1.0) / (observations - parameters - 1.0);
  }

  /**
   * Returns the source response vector.
   *
   * @param points retained source points
   * @return natural logarithm of reported activity coefficients
   */
  private static double[] response(List<HeMorsePoint> points) {
    double[] result = new double[points.size()];
    for (int index = 0; index < points.size(); index++) {
      result[index] = Math.log(points.get(index).activityCoefficient);
    }
    return result;
  }

  /**
   * Evaluates the reduced curvature law at one temperature.
   *
   * @param parameters candidate parameter vector
   * @param temperatureK temperature in K
   * @return source-space salt-pair curvature in kg squared per mol squared
   */
  private static double zetaAt(double[] parameters, double temperatureK) {
    return parameters[5] + parameters[6] * (REFERENCE_TEMPERATURE_K / temperatureK - 1.0);
  }

  /**
   * Propagates candidate covariance to one temperature.
   *
   * @param covariance candidate covariance matrix
   * @param temperatureK temperature in K
   * @return one-standard-error uncertainty of source-space curvature
   */
  private static double zetaStandardErrorAt(double[][] covariance, double temperatureK) {
    double coordinate = REFERENCE_TEMPERATURE_K / temperatureK - 1.0;
    double variance = covariance[5][5] + 2.0 * coordinate * covariance[5][6]
        + coordinate * coordinate * covariance[6][6];
    return Math.sqrt(variance);
  }

  /**
   * Calculates aggregate held-temperature RMSE.
   *
   * @param holdouts held-temperature results
   * @return observation-weighted RMSE
   */
  private static double aggregateRmse(List<HoldoutResult> holdouts) {
    double sumSquares = 0.0;
    int points = 0;
    for (HoldoutResult holdout : holdouts) {
      sumSquares += holdout.sumSquares;
      points += holdout.points;
    }
    return Math.sqrt(sumSquares / points);
  }

  /**
   * Finds the maximum held-temperature absolute residual.
   *
   * @param holdouts held-temperature results
   * @return maximum absolute residual
   */
  private static double aggregateMaximum(List<HoldoutResult> holdouts) {
    double maximum = 0.0;
    for (HoldoutResult holdout : holdouts) {
      maximum = Math.max(maximum, holdout.maximumAbsoluteResidual);
    }
    return maximum;
  }

  /**
   * Audits the maximum distinct pressure count within a temperature block.
   *
   * @param points retained He-Morse source points
   * @return maximum count of distinct partial pressures at one temperature
   */
  private static int maximumWithinTemperaturePressureCount(List<HeMorsePoint> points) {
    int maximum = 0;
    for (double temperature : TEMPERATURES_K) {
      Set<Double> pressures = new HashSet<Double>();
      for (HeMorsePoint point : points) {
        if (point.temperatureK == temperature) {
          pressures.add(point.partialPressureAtm);
        }
      }
      maximum = Math.max(maximum, pressures.size());
    }
    return maximum;
  }

  /**
   * Returns the index of a retained temperature.
   *
   * @param temperatureK temperature in K
   * @return retained temperature index
   * @throws IllegalArgumentException if the temperature is not retained
   */
  private static int temperatureIndex(double temperatureK) {
    for (int index = 0; index < TEMPERATURES_K.length; index++) {
      if (temperatureK == TEMPERATURES_K[index]) {
        return index;
      }
    }
    throw new IllegalArgumentException("Unexpected retained temperature " + temperatureK);
  }

  /**
   * Loads the retained He-Morse source.
   *
   * @return source points
   * @throws Exception if the fixture cannot be read
   */
  private static List<HeMorsePoint> loadHeMorse() throws Exception {
    InputStream stream = CO2SaltPairTemperatureLawAssessmentTest.class.getResourceAsStream(HE_MORSE_RESOURCE);
    assertNotNull(stream, HE_MORSE_RESOURCE);
    List<HeMorsePoint> points = new ArrayList<HeMorsePoint>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      boolean headerSeen = false;
      while ((line = reader.readLine()) != null) {
        if (line.trim().isEmpty() || line.startsWith("#")) {
          continue;
        }
        if (!headerSeen) {
          headerSeen = true;
          continue;
        }
        String[] fields = line.split(",", -1);
        assertEquals(7, fields.length);
        points.add(new HeMorsePoint(Double.parseDouble(fields[1]), Double.parseDouble(fields[2]),
            Double.parseDouble(fields[5]), Double.parseDouble(fields[6])));
      }
      assertTrue(headerSeen, "He-Morse header");
    }
    return points;
  }

  /**
   * Loads the retained unambiguous 298.15 K Yasunishi-Yoshida source block.
   *
   * @return source points
   * @throws Exception if the fixture cannot be read
   */
  private static List<YasunishiPoint> loadYasunishi() throws Exception {
    InputStream stream = CO2SaltPairTemperatureLawAssessmentTest.class.getResourceAsStream(YASUNISHI_RESOURCE);
    assertNotNull(stream, YASUNISHI_RESOURCE);
    List<YasunishiPoint> points = new ArrayList<YasunishiPoint>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      boolean headerSeen = false;
      while ((line = reader.readLine()) != null) {
        if (line.trim().isEmpty() || line.startsWith("#")) {
          continue;
        }
        if (!headerSeen) {
          headerSeen = true;
          continue;
        }
        String[] fields = line.split(",", -1);
        assertEquals(7, fields.length);
        points.add(new YasunishiPoint(Double.parseDouble(fields[3]), Double.parseDouble(fields[4])));
      }
      assertTrue(headerSeen, "Yasunishi-Yoshida header");
    }
    return points;
  }

  /**
   * Formats one model-comparison row.
   *
   * @param recordType record category
   * @param role evidence role
   * @param model model label
   * @param fit fitted model
   * @param candidate candidate fit for AICc comparison
   * @param zetaReference reference-temperature curvature
   * @param zetaReferenceStandardError reference-temperature standard error
   * @param zetaInvTemperature inverse-temperature curvature coefficient
   * @param zetaInvTemperatureStandardError inverse-temperature standard error
   * @param zetaCorrelation curvature-parameter correlation
   * @param disposition scientific disposition
   * @return CSV row
   */
  private static String modelRow(String recordType, String role, String model, FitResult fit, FitResult candidate,
      double zetaReference, double zetaReferenceStandardError, double zetaInvTemperature,
      double zetaInvTemperatureStandardError, double zetaCorrelation, String disposition) {
    return String.format(Locale.ROOT,
        "%s,HE_MORSE_KCL,%s,NaN,%s,26,%d,%d,%d,%.12g,%.12g,%.12g,%.12g,%.12g,"
            + "%.12g,%.12g,%.12g,%.12g,NaN,NaN,%.12g,NaN,NaN,NaN,%s",
        recordType, role, model, fit.parameters.length, fit.residualDegreesOfFreedom, fit.rank, fit.conditionNumber,
        fit.rmse, fit.maximumAbsoluteResidual, fit.aicc, fit.aicc - candidate.aicc, zetaReference,
        zetaReferenceStandardError, zetaInvTemperature, zetaInvTemperatureStandardError, zetaCorrelation, disposition);
  }

  /**
   * Formats one held-temperature row.
   *
   * @param holdout held-temperature result
   * @param aggregateRmse observation-weighted aggregate RMSE
   * @param aggregateMaximum aggregate maximum absolute residual
   * @return CSV row
   */
  private static String holdoutRow(HoldoutResult holdout, double aggregateRmse, double aggregateMaximum) {
    return String.format(Locale.ROOT,
        "LEAVE_ONE_TEMPERATURE_OUT,HE_MORSE_KCL,source_shape_holdout,%.2f,"
            + "TWO_PARAMETER_INVERSE_T_ZETA,%d,6,NaN,6,NaN,%.12g,%.12g,NaN,NaN,NaN,"
            + "NaN,NaN,NaN,%.12g,NaN,NaN,NaN,%.12g,%.12g," + "held_block_linear_nuisance_refit_curvature_predicted",
        holdout.temperatureK, holdout.points, holdout.rmse(), holdout.maximumAbsoluteResidual, holdout.zeta,
        aggregateRmse, aggregateMaximum);
  }

  /**
   * Formats the independent source-shape validation row.
   *
   * @param shape independent source-shape result
   * @return CSV row
   */
  private static String independentShapeRow(SourceShapeResult shape) {
    return String.format(Locale.ROOT, "INDEPENDENT_SHAPE,YASUNISHI_YOSHIDA_KCL,independent_basis_holdout,298.15,"
        + "LINEAR_PLUS_QUADRATIC_MOLARITY,6,2,4,2,NaN,%.12g,NaN,NaN,NaN,NaN,NaN," + "NaN,NaN,NaN,NaN,NaN,%.12g,NaN,NaN,"
        + "negative_curvature_supported_molarity_coefficient_not_transferable", shape.quadraticRmse,
        shape.quadraticCoefficient);
  }

  /** Immutable He-Morse source point. */
  private static final class HeMorsePoint {
    private final double temperatureK;
    private final double partialPressureAtm;
    private final double kclMolality;
    private final double activityCoefficient;

    /**
     * Creates one source point.
     *
     * @param temperatureK temperature in K
     * @param partialPressureAtm CO2 partial pressure in atm
     * @param kclMolality KCl molality in mol/kg water
     * @param activityCoefficient molality-scale CO2 activity coefficient
     */
    private HeMorsePoint(double temperatureK, double partialPressureAtm, double kclMolality,
        double activityCoefficient) {
      this.temperatureK = temperatureK;
      this.partialPressureAtm = partialPressureAtm;
      this.kclMolality = kclMolality;
      this.activityCoefficient = activityCoefficient;
    }
  }

  /** Immutable Yasunishi-Yoshida source point. */
  private static final class YasunishiPoint {
    private final double kclMolarity;
    private final double ostwaldCoefficient;

    /**
     * Creates one independent source point.
     *
     * @param kclMolarity KCl molarity in mol/L solution
     * @param ostwaldCoefficient reported Ostwald coefficient
     */
    private YasunishiPoint(double kclMolarity, double ostwaldCoefficient) {
      this.kclMolarity = kclMolarity;
      this.ostwaldCoefficient = ostwaldCoefficient;
    }
  }

  /** Immutable least-squares result. */
  private static final class FitResult {
    private final double[] parameters;
    private final double sumSquares;
    private final double rmse;
    private final double maximumAbsoluteResidual;
    private final double aicc;
    private final int rank;
    private final int residualDegreesOfFreedom;
    private final double conditionNumber;

    /**
     * Creates one fit result.
     *
     * @param parameters fitted parameters
     * @param sumSquares residual sum of squares
     * @param rmse root-mean-square residual
     * @param maximumAbsoluteResidual maximum absolute residual
     * @param aicc corrected Akaike information criterion
     * @param rank numerical design rank
     * @param residualDegreesOfFreedom residual degrees of freedom
     * @param conditionNumber two-norm design condition number
     */
    private FitResult(double[] parameters, double sumSquares, double rmse, double maximumAbsoluteResidual, double aicc,
        int rank, int residualDegreesOfFreedom, double conditionNumber) {
      this.parameters = parameters;
      this.sumSquares = sumSquares;
      this.rmse = rmse;
      this.maximumAbsoluteResidual = maximumAbsoluteResidual;
      this.aicc = aicc;
      this.rank = rank;
      this.residualDegreesOfFreedom = residualDegreesOfFreedom;
      this.conditionNumber = conditionNumber;
    }
  }

  /** Immutable held-temperature result. */
  private static final class HoldoutResult {
    private final double temperatureK;
    private final int points;
    private final double zeta;
    private final double sumSquares;
    private final double maximumAbsoluteResidual;

    /**
     * Creates one held-temperature result.
     *
     * @param temperatureK held temperature in K
     * @param points held observation count
     * @param zeta curvature predicted from other temperature blocks
     * @param sumSquares held residual sum of squares
     * @param maximumAbsoluteResidual held maximum absolute residual
     */
    private HoldoutResult(double temperatureK, int points, double zeta, double sumSquares,
        double maximumAbsoluteResidual) {
      this.temperatureK = temperatureK;
      this.points = points;
      this.zeta = zeta;
      this.sumSquares = sumSquares;
      this.maximumAbsoluteResidual = maximumAbsoluteResidual;
    }

    /**
     * Returns held-block RMSE.
     *
     * @return held-block RMSE
     */
    private double rmse() {
      return Math.sqrt(sumSquares / points);
    }
  }

  /** Immutable independent source-shape result. */
  private static final class SourceShapeResult {
    private final double linearRmse;
    private final double quadraticRmse;
    private final double quadraticCoefficient;

    /**
     * Creates one independent source-shape result.
     *
     * @param linearRmse linear-model RMSE
     * @param quadraticRmse quadratic-model RMSE
     * @param quadraticCoefficient fitted quadratic coefficient
     */
    private SourceShapeResult(double linearRmse, double quadraticRmse, double quadraticCoefficient) {
      this.linearRmse = linearRmse;
      this.quadraticRmse = quadraticRmse;
      this.quadraticCoefficient = quadraticCoefficient;
    }
  }
}
