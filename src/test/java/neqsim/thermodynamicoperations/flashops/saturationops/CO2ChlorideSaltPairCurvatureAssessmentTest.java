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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.mathlib.linearalgebra.EjmlLinearAlgebra;
import neqsim.mathlib.linearalgebra.LinearAlgebraOperations;

/**
 * No-production-change curvature and rank diagnostic for CO2 in chloride brines.
 *
 * <p>
 * The candidate basis follows NeqSim's existing Pitzer neutral-solute semantics. For a 1:1 salt MCl,
 * {@code ln(gamma_CO2) = 2 m lambda(CO2,M) + 2 m lambda(CO2,Cl) + m^2 zeta(CO2,M,Cl)}. This test compares
 * linear and linear-plus-quadratic source-space shapes and audits the composition Jacobian. It does not select a
 * coefficient, choose a single-ion gauge, transfer Pitzer parameters into electrolyte CPA, or change production code.
 * </p>
 *
 * @author OpenAI
 * @version 1.0
 */
class CO2ChlorideSaltPairCurvatureAssessmentTest {
  private static final Logger logger = LogManager.getLogger(CO2ChlorideSaltPairCurvatureAssessmentTest.class);
  private static final String HE_MORSE_RESOURCE =
      "/data/chemistry_benchmarks/co2_kcl_solubility_he_morse1993.csv";
  private static final String YASUNISHI_YOSHIDA_RESOURCE =
      "/data/chemistry_benchmarks/co2_kcl_ostwald_yasunishi_yoshida1979.csv";
  private static final String LIU_NACL_RESOURCE =
      "/data/chemistry_benchmarks/co2_water_nacl_solubility_liu2021.csv";
  private static final String OUTPUT = "target/co2-chloride-salt-pair-curvature-assessment.csv";
  private static final double NACL_MOLAR_MASS_KG_PER_MOL = 0.05844277;

  /**
   * Reports source-separated curvature and the unresolved individual-ion gauge without fitting the model.
   *
   * @throws Exception if a retained fixture cannot be read or the evidence file cannot be written
   */
  @Tag("slow")
  @Test
  void independentSingleSaltSourcesDiscriminateCurvatureWithoutSelectingAParameter() throws Exception {
    LinearAlgebraOperations algebra = new EjmlLinearAlgebra();
    List<HeMorsePoint> heMorse = loadHeMorse();
    List<YasunishiPoint> yasunishi = loadYasunishiYoshida();
    List<Double> liuNaclMolalities = loadLiuNaclMolalities();
    assertEquals(26, heMorse.size());
    assertEquals(7, yasunishi.size());
    assertEquals(36, liuNaclMolalities.size());

    List<String> report = new ArrayList<String>();
    report.add("family,role,temperature_K,concentration_basis,points,linear_coefficient,quadratic_linear_coefficient,"
        + "quadratic_coefficient,linear_rmse,quadratic_rmse,reported_relative_uncertainty,disposition");
    double[] temperatures = {273.15, 298.15, 323.15, 348.15, 363.15};
    for (double temperature : temperatures) {
      List<HeMorsePoint> block = selectTemperature(heMorse, temperature);
      FitResult fit = fitHeMorse(block, algebra);
      assertTrue(fit.quadraticRmse < fit.linearRmse, temperature + " K must retain source curvature discrimination");
      assertTrue(fit.quadraticCoefficient < 0.0, temperature + " K must retain diminishing salting-out curvature");
      report.add(String.format(Locale.ROOT,
          "HE_MORSE_KCL,source_shape_discriminator_no_parameter_selection,%.2f,mol_per_kg_water,%d,"
              + "%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,quadratic_shape_supported_parameter_not_adopted",
          temperature, block.size(), fit.linearCoefficient, fit.quadraticLinearCoefficient,
          fit.quadraticCoefficient, fit.linearRmse, fit.quadraticRmse, 0.04));
    }

    FitResult yasunishiFit = fitYasunishiYoshida(yasunishi, algebra);
    assertTrue(yasunishiFit.quadraticRmse < yasunishiFit.linearRmse,
        "independent molarity source must retain curvature discrimination");
    assertTrue(yasunishiFit.quadraticCoefficient < 0.0,
        "independent molarity source must retain diminishing salting-out curvature");
    report.add(String.format(Locale.ROOT,
        "YASUNISHI_YOSHIDA_KCL,independent_source_shape_validation,298.15,mol_per_L_solution,%d,"
            + "%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,basis_specific_not_transferable_to_molality_parameter",
        yasunishi.size(), yasunishiFit.linearCoefficient, yasunishiFit.quadraticLinearCoefficient,
        yasunishiFit.quadraticCoefficient, yasunishiFit.linearRmse, yasunishiFit.quadraticRmse, 0.01));

    double[][] individualDesign = individualIonDesign(heMorse, liuNaclMolalities);
    double[][] saltLevelDesign = saltLevelDesign(heMorse, liuNaclMolalities);
    assertEquals(4, algebra.rank(individualDesign), "five individual-ion terms retain one electroneutral gauge");
    assertEquals(4, algebra.rank(saltLevelDesign), "four salt-level lambda/zeta combinations are identifiable");
    double[] nullVector = {1.0, 1.0, -1.0, 0.0, 0.0};
    double maxNullResidual = maximumAbsolute(algebra.multiply(individualDesign, nullVector));
    assertEquals(0.0, maxNullResidual, 1.0e-12, "NaCl/KCl data cannot select an individual-ion allocation");
    report.add(String.format(Locale.ROOT,
        "NACL_KCL_COMPOSITION_JACOBIAN,no_response_fit,NaN,mol_per_kg_water,%d,NaN,NaN,NaN,NaN,NaN,NaN,"
            + "individual_rank_%d_of_5_salt_level_rank_%d_of_4_null_1_1_minus1_0_0_residual_%.12g",
        individualDesign.length, algebra.rank(individualDesign), algebra.rank(saltLevelDesign), maxNullResidual));

    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), report, StandardCharsets.UTF_8);
    for (String row : report) {
      logger.info("CO2_CHLORIDE_SALT_PAIR_CURVATURE,{}", row);
    }
  }

  /**
   * Fits one He-Morse temperature block in its reported activity-coefficient space.
   *
   * @param points one temperature block
   * @param algebra numerical backend
   * @return linear and quadratic no-intercept diagnostics
   */
  private static FitResult fitHeMorse(List<HeMorsePoint> points, LinearAlgebraOperations algebra) {
    double[] concentration = new double[points.size()];
    double[] response = new double[points.size()];
    for (int index = 0; index < points.size(); index++) {
      concentration[index] = points.get(index).kclMolality;
      response[index] = Math.log(points.get(index).co2ActivityCoefficient);
    }
    return fitLinearAndQuadratic(concentration, response, algebra);
  }

  /**
   * Fits the independent Yasunishi-Yoshida source in its reported molarity/Ostwald space.
   *
   * @param points retained 298.15 K source points
   * @param algebra numerical backend
   * @return basis-specific linear and quadratic diagnostics
   */
  private static FitResult fitYasunishiYoshida(List<YasunishiPoint> points, LinearAlgebraOperations algebra) {
    double pureOstwald = points.get(0).ostwaldCoefficient;
    assertEquals(0.0, points.get(0).kclMolarity, 0.0);
    double[] concentration = new double[points.size() - 1];
    double[] response = new double[points.size() - 1];
    for (int index = 1; index < points.size(); index++) {
      concentration[index - 1] = points.get(index).kclMolarity;
      response[index - 1] = Math.log(pureOstwald / points.get(index).ostwaldCoefficient);
    }
    return fitLinearAndQuadratic(concentration, response, algebra);
  }

  /**
   * Performs no-intercept least-squares fits for {@code y=a*m} and {@code y=a*m+b*m^2}.
   *
   * @param concentration salt concentrations on one declared source basis
   * @param response source log response
   * @param algebra numerical backend
   * @return fit diagnostics
   */
  private static FitResult fitLinearAndQuadratic(double[] concentration, double[] response,
      LinearAlgebraOperations algebra) {
    double[][] linear = new double[concentration.length][1];
    double[][] quadratic = new double[concentration.length][2];
    for (int index = 0; index < concentration.length; index++) {
      linear[index][0] = concentration[index];
      quadratic[index][0] = concentration[index];
      quadratic[index][1] = concentration[index] * concentration[index];
    }
    double[] linearParameters = algebra.solveLeastSquares(linear, response);
    double[] quadraticParameters = algebra.solveLeastSquares(quadratic, response);
    return new FitResult(linearParameters[0], quadraticParameters[0], quadraticParameters[1],
        rmse(response, algebra.multiply(linear, linearParameters)),
        rmse(response, algebra.multiply(quadratic, quadraticParameters)));
  }

  /**
   * Calculates root-mean-square residual.
   *
   * @param observed observed values
   * @param calculated calculated values
   * @return root-mean-square residual
   */
  private static double rmse(double[] observed, double[] calculated) {
    assertEquals(observed.length, calculated.length);
    double sumSquares = 0.0;
    for (int index = 0; index < observed.length; index++) {
      double residual = calculated[index] - observed[index];
      sumSquares += residual * residual;
    }
    return Math.sqrt(sumSquares / observed.length);
  }

  /**
   * Builds the five-column individual-ion composition Jacobian.
   *
   * @param kclPoints direct-molality KCl states
   * @param naclMolalities NaCl states converted from reported mass fraction
   * @return columns lambda-Na, lambda-K, lambda-Cl, zeta-NaCl and zeta-KCl
   */
  private static double[][] individualIonDesign(List<HeMorsePoint> kclPoints, List<Double> naclMolalities) {
    double[][] matrix = new double[kclPoints.size() + naclMolalities.size()][5];
    int row = 0;
    for (double molality : naclMolalities) {
      matrix[row++] = new double[] {2.0 * molality, 0.0, 2.0 * molality, molality * molality, 0.0};
    }
    for (HeMorsePoint point : kclPoints) {
      double molality = point.kclMolality;
      matrix[row++] = new double[] {0.0, 2.0 * molality, 2.0 * molality, 0.0, molality * molality};
    }
    return matrix;
  }

  /**
   * Builds the gauge-free four-column salt-level composition Jacobian.
   *
   * @param kclPoints direct-molality KCl states
   * @param naclMolalities NaCl states converted from reported mass fraction
   * @return columns lambda-NaCl, lambda-KCl, zeta-NaCl and zeta-KCl
   */
  private static double[][] saltLevelDesign(List<HeMorsePoint> kclPoints, List<Double> naclMolalities) {
    double[][] matrix = new double[kclPoints.size() + naclMolalities.size()][4];
    int row = 0;
    for (double molality : naclMolalities) {
      matrix[row++] = new double[] {2.0 * molality, 0.0, molality * molality, 0.0};
    }
    for (HeMorsePoint point : kclPoints) {
      double molality = point.kclMolality;
      matrix[row++] = new double[] {0.0, 2.0 * molality, 0.0, molality * molality};
    }
    return matrix;
  }

  /**
   * Selects one He-Morse temperature block.
   *
   * @param points all retained states
   * @param temperature target temperature in K
   * @return states at the requested temperature
   */
  private static List<HeMorsePoint> selectTemperature(List<HeMorsePoint> points, double temperature) {
    List<HeMorsePoint> selected = new ArrayList<HeMorsePoint>();
    for (HeMorsePoint point : points) {
      if (point.temperature == temperature) {
        selected.add(point);
      }
    }
    assertTrue(selected.size() >= 4, temperature + " K source block");
    return selected;
  }

  /**
   * Loads retained He-Morse states.
   *
   * @return source points
   * @throws Exception if the fixture cannot be read
   */
  private static List<HeMorsePoint> loadHeMorse() throws Exception {
    List<HeMorsePoint> points = new ArrayList<HeMorsePoint>();
    for (String[] fields : readRows(HE_MORSE_RESOURCE)) {
      points.add(new HeMorsePoint(Double.parseDouble(fields[1]), Double.parseDouble(fields[5]),
          Double.parseDouble(fields[6])));
    }
    return points;
  }

  /**
   * Loads the unambiguous 298.15 K Yasunishi-Yoshida block.
   *
   * @return source points
   * @throws Exception if the fixture cannot be read
   */
  private static List<YasunishiPoint> loadYasunishiYoshida() throws Exception {
    List<YasunishiPoint> points = new ArrayList<YasunishiPoint>();
    for (String[] fields : readRows(YASUNISHI_YOSHIDA_RESOURCE)) {
      points.add(new YasunishiPoint(Double.parseDouble(fields[3]), Double.parseDouble(fields[4])));
    }
    return points;
  }

  /**
   * Loads nonzero Liu NaCl mass fractions and converts only their composition coordinate to molality.
   *
   * @return NaCl molalities in mol/kg water
   * @throws Exception if the fixture cannot be read
   */
  private static List<Double> loadLiuNaclMolalities() throws Exception {
    List<Double> molalities = new ArrayList<Double>();
    for (String[] fields : readRows(LIU_NACL_RESOURCE)) {
      if (!"NaCl".equals(fields[2])) {
        continue;
      }
      double massFraction = Double.parseDouble(fields[4]) / 100.0;
      molalities.add(massFraction / ((1.0 - massFraction) * NACL_MOLAR_MASS_KG_PER_MOL));
    }
    return molalities;
  }

  /**
   * Reads non-comment CSV rows after the first header.
   *
   * @param resource classpath resource
   * @return parsed data rows
   * @throws Exception if the resource cannot be read
   */
  private static List<String[]> readRows(String resource) throws Exception {
    InputStream stream = CO2ChlorideSaltPairCurvatureAssessmentTest.class.getResourceAsStream(resource);
    assertNotNull(stream, resource);
    List<String[]> rows = new ArrayList<String[]>();
    boolean headerSeen = false;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.trim().isEmpty() || line.startsWith("#")) {
          continue;
        }
        if (!headerSeen) {
          headerSeen = true;
          continue;
        }
        rows.add(line.split(",", -1));
      }
    }
    assertTrue(headerSeen, resource + " header");
    return rows;
  }

  /**
   * Returns the largest absolute vector entry.
   *
   * @param values vector
   * @return largest absolute value
   */
  private static double maximumAbsolute(double[] values) {
    double maximum = 0.0;
    for (double value : values) {
      maximum = Math.max(maximum, Math.abs(value));
    }
    return maximum;
  }

  /** One source point from the He-Morse molality/activity-coefficient series. */
  private static final class HeMorsePoint {
    private final double temperature;
    private final double kclMolality;
    private final double co2ActivityCoefficient;

    /**
     * Creates a He-Morse point.
     *
     * @param temperature temperature in K
     * @param kclMolality KCl molality in mol/kg water
     * @param co2ActivityCoefficient reported CO2 activity coefficient
     */
    private HeMorsePoint(double temperature, double kclMolality, double co2ActivityCoefficient) {
      this.temperature = temperature;
      this.kclMolality = kclMolality;
      this.co2ActivityCoefficient = co2ActivityCoefficient;
    }
  }

  /** One source point from the Yasunishi-Yoshida molarity/Ostwald series. */
  private static final class YasunishiPoint {
    private final double kclMolarity;
    private final double ostwaldCoefficient;

    /**
     * Creates a Yasunishi-Yoshida point.
     *
     * @param kclMolarity KCl molarity in mol/L solution
     * @param ostwaldCoefficient reported Ostwald coefficient
     */
    private YasunishiPoint(double kclMolarity, double ostwaldCoefficient) {
      this.kclMolarity = kclMolarity;
      this.ostwaldCoefficient = ostwaldCoefficient;
    }
  }

  /** Immutable source-space regression result with no adopted parameter semantics. */
  private static final class FitResult {
    private final double linearCoefficient;
    private final double quadraticLinearCoefficient;
    private final double quadraticCoefficient;
    private final double linearRmse;
    private final double quadraticRmse;

    /**
     * Creates one regression result.
     *
     * @param linearCoefficient coefficient from the linear-only basis
     * @param quadraticLinearCoefficient linear coefficient from the two-term basis
     * @param quadraticCoefficient quadratic coefficient from the two-term basis
     * @param linearRmse linear-basis RMSE
     * @param quadraticRmse two-term-basis RMSE
     */
    private FitResult(double linearCoefficient, double quadraticLinearCoefficient, double quadraticCoefficient,
        double linearRmse, double quadraticRmse) {
      this.linearCoefficient = linearCoefficient;
      this.quadraticLinearCoefficient = quadraticLinearCoefficient;
      this.quadraticCoefficient = quadraticCoefficient;
      this.linearRmse = linearRmse;
      this.quadraticRmse = quadraticRmse;
    }
  }
}
