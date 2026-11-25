package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/**
 * Composition-derivative audit for a neutral-solute/cation/anion salt-pair Helmholtz kernel.
 *
 * <p>
 * The unit-response kernel is {@code B = n0*nPlus*nMinus/(Mw*nWater)^2}. Multiplying it by a sourced or fitted
 * salt-pair coefficient would give the reduced excess Helmholtz contribution. The kernel is the extensive
 * molality-coordinate counterpart of a Pitzer neutral-cation-anion zeta topology. This assessment supplies no physical
 * coefficient, does not activate the term in electrolyte CPA, and does not use any retained holdout.
 * </p>
 *
 * @author OpenAI
 * @version 1.0
 */
class CO2SaltPairHelmholtzCompositionAssessmentTest {
  private static final Logger logger = LogManager.getLogger(CO2SaltPairHelmholtzCompositionAssessmentTest.class);
  private static final String OUTPUT = "target/co2-salt-pair-helmholtz-composition-assessment.csv";
  private static final double WATER_MOLAR_MASS_KG_PER_MOL = 0.01801528;
  private static final int NEUTRAL = 0;
  private static final int CATION = 1;
  private static final int ANION = 2;
  private static final int WATER = 3;

  /**
   * Verifies extensivity, analytic first/second derivatives, and the quadratic salt-pair response.
   *
   * @throws Exception if the retained diagnostic output cannot be written
   */
  @Tag("slow")
  @Test
  void unitKernelIsExtensiveAndHasConsistentCompositionDerivatives() throws Exception {
    double[] amounts = {0.08, 0.60, 0.60, 55.508};
    double value = value(amounts);
    double[] gradient = gradient(amounts);
    double[][] hessian = hessian(amounts);

    double scale = 3.25;
    double scaledValue = value(scale(amounts, scale));
    double extensivityResidual = Math.abs(scaledValue - scale * value);
    assertEquals(scale * value, scaledValue, 1.0e-14);

    double euler = 0.0;
    for (int component = 0; component < amounts.length; component++) {
      euler += amounts[component] * gradient[component];
    }
    double eulerResidual = Math.abs(euler - value);
    assertEquals(value, euler, 1.0e-14);

    double gradientResidual = maximumGradientResidual(amounts, gradient);
    assertTrue(gradientResidual < 2.0e-9, "first composition derivatives must match centered finite differences");

    double hessianResidual = maximumHessianResidual(amounts, hessian);
    assertTrue(hessianResidual < 2.0e-7, "second composition derivatives must match centered finite differences");

    double symmetryResidual = maximumSymmetryResidual(hessian);
    assertEquals(0.0, symmetryResidual, 0.0);

    double homogeneityResidual = maximumHessianHomogeneityResidual(amounts, hessian);
    assertEquals(0.0, homogeneityResidual, 2.0e-15);

    double[] doubledSalt = amounts.clone();
    doubledSalt[CATION] *= 2.0;
    doubledSalt[ANION] *= 2.0;
    double curvatureRatio = gradient(doubledSalt)[NEUTRAL] / gradient[NEUTRAL];
    assertEquals(4.0, curvatureRatio, 1.0e-14,
        "neutral chemical-potential response must be quadratic in a 1:1 salt amount");

    double topologyResidual = 0.0;
    for (int missing = NEUTRAL; missing <= ANION; missing++) {
      double[] incomplete = amounts.clone();
      incomplete[missing] = 0.0;
      topologyResidual = Math.max(topologyResidual, Math.abs(value(incomplete)));
    }
    assertEquals(0.0, topologyResidual, 0.0);

    List<String> report = new ArrayList<String>();
    report.add("check,calculated,reference,absolute_residual,disposition");
    report.add(row("EXTENSIVITY", scaledValue, scale * value, extensivityResidual,
        "unit_kernel_only_no_coefficient_selected"));
    report.add(row("EULER_IDENTITY", euler, value, eulerResidual, "thermodynamically_consistent_composition_basis"));
    report.add(row("FIRST_DERIVATIVES", gradientResidual, 0.0, gradientResidual, "centered_finite_difference_pass"));
    report.add(row("SECOND_DERIVATIVES", hessianResidual, 0.0, hessianResidual, "centered_finite_difference_pass"));
    report.add(row("HESSIAN_SYMMETRY", symmetryResidual, 0.0, symmetryResidual, "exact_symmetry_pass"));
    report
        .add(row("HESSIAN_HOMOGENEITY", homogeneityResidual, 0.0, homogeneityResidual, "degree_one_homogeneity_pass"));
    report.add(row("SALT_PAIR_CURVATURE", curvatureRatio, 4.0, Math.abs(curvatureRatio - 4.0),
        "quadratic_shape_only_parameter_not_adopted"));
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), report, StandardCharsets.UTF_8);
    for (String reportRow : report) {
      logger.info("CO2_SALT_PAIR_HELMHOLTZ_COMPOSITION,{}", reportRow);
    }
  }

  /**
   * Evaluates the unit-response reduced Helmholtz composition kernel.
   *
   * @param amounts neutral, cation, anion, and water amounts in mol
   * @return unit-response kernel in mol cubed per kg squared
   */
  private static double value(double[] amounts) {
    double solventMass = WATER_MOLAR_MASS_KG_PER_MOL * amounts[WATER];
    return amounts[NEUTRAL] * amounts[CATION] * amounts[ANION] / (solventMass * solventMass);
  }

  /**
   * Evaluates the analytic first composition derivatives of the unit kernel.
   *
   * @param amounts neutral, cation, anion, and water amounts in mol
   * @return derivatives ordered as neutral, cation, anion, and water
   */
  private static double[] gradient(double[] amounts) {
    double kernel = value(amounts);
    return new double[] {kernel / amounts[NEUTRAL], kernel / amounts[CATION], kernel / amounts[ANION],
        -2.0 * kernel / amounts[WATER]};
  }

  /**
   * Evaluates the analytic composition Hessian of the unit kernel.
   *
   * @param amounts neutral, cation, anion, and water amounts in mol
   * @return symmetric Hessian ordered as neutral, cation, anion, and water
   */
  private static double[][] hessian(double[] amounts) {
    double kernel = value(amounts);
    double[][] result = new double[4][4];
    result[NEUTRAL][CATION] = kernel / (amounts[NEUTRAL] * amounts[CATION]);
    result[CATION][NEUTRAL] = result[NEUTRAL][CATION];
    result[NEUTRAL][ANION] = kernel / (amounts[NEUTRAL] * amounts[ANION]);
    result[ANION][NEUTRAL] = result[NEUTRAL][ANION];
    result[CATION][ANION] = kernel / (amounts[CATION] * amounts[ANION]);
    result[ANION][CATION] = result[CATION][ANION];
    double[] first = gradient(amounts);
    for (int solute = NEUTRAL; solute <= ANION; solute++) {
      result[solute][WATER] = -2.0 * first[solute] / amounts[WATER];
      result[WATER][solute] = result[solute][WATER];
    }
    result[WATER][WATER] = 6.0 * kernel / (amounts[WATER] * amounts[WATER]);
    return result;
  }

  /**
   * Finds the maximum absolute first-derivative finite-difference residual.
   *
   * @param amounts composition amounts
   * @param analytic analytic gradient
   * @return maximum absolute residual
   */
  private static double maximumGradientResidual(double[] amounts, double[] analytic) {
    double maximum = 0.0;
    for (int component = 0; component < amounts.length; component++) {
      double step = 1.0e-6 * amounts[component];
      double[] plus = amounts.clone();
      double[] minus = amounts.clone();
      plus[component] += step;
      minus[component] -= step;
      double numerical = (value(plus) - value(minus)) / (2.0 * step);
      maximum = Math.max(maximum, Math.abs(numerical - analytic[component]));
    }
    return maximum;
  }

  /**
   * Finds the maximum absolute Hessian finite-difference residual.
   *
   * @param amounts composition amounts
   * @param analytic analytic Hessian
   * @return maximum absolute residual
   */
  private static double maximumHessianResidual(double[] amounts, double[][] analytic) {
    double maximum = 0.0;
    for (int column = 0; column < amounts.length; column++) {
      double step = 1.0e-5 * amounts[column];
      double[] plus = amounts.clone();
      double[] minus = amounts.clone();
      plus[column] += step;
      minus[column] -= step;
      double[] plusGradient = gradient(plus);
      double[] minusGradient = gradient(minus);
      for (int row = 0; row < amounts.length; row++) {
        double numerical = (plusGradient[row] - minusGradient[row]) / (2.0 * step);
        maximum = Math.max(maximum, Math.abs(numerical - analytic[row][column]));
      }
    }
    return maximum;
  }

  /**
   * Finds the maximum Hessian symmetry residual.
   *
   * @param hessian analytic Hessian
   * @return maximum absolute antisymmetric entry
   */
  private static double maximumSymmetryResidual(double[][] hessian) {
    double maximum = 0.0;
    for (int row = 0; row < hessian.length; row++) {
      for (int column = 0; column < hessian.length; column++) {
        maximum = Math.max(maximum, Math.abs(hessian[row][column] - hessian[column][row]));
      }
    }
    return maximum;
  }

  /**
   * Finds the maximum residual of the degree-one Hessian homogeneity identity.
   *
   * @param amounts composition amounts
   * @param hessian analytic Hessian
   * @return maximum absolute entry of the Hessian-times-amount vector
   */
  private static double maximumHessianHomogeneityResidual(double[] amounts, double[][] hessian) {
    double maximum = 0.0;
    for (int row = 0; row < hessian.length; row++) {
      double residual = 0.0;
      for (int column = 0; column < hessian.length; column++) {
        residual += hessian[row][column] * amounts[column];
      }
      maximum = Math.max(maximum, Math.abs(residual));
    }
    return maximum;
  }

  /**
   * Scales every amount by the same factor.
   *
   * @param amounts original amounts
   * @param factor scale factor
   * @return independently allocated scaled amounts
   */
  private static double[] scale(double[] amounts, double factor) {
    double[] scaled = amounts.clone();
    for (int component = 0; component < scaled.length; component++) {
      scaled[component] *= factor;
    }
    return scaled;
  }

  /**
   * Formats one retained diagnostic row.
   *
   * @param check check identifier
   * @param calculated calculated value
   * @param reference analytic reference value
   * @param residual absolute residual
   * @param disposition scientific disposition
   * @return comma-separated row
   */
  private static String row(String check, double calculated, double reference, double residual, String disposition) {
    return String.format(Locale.ROOT, "%s,%.16g,%.16g,%.16g,%s", check, calculated, reference, residual, disposition);
  }
}
