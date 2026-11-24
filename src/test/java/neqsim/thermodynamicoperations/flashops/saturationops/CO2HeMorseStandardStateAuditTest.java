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
 * Audits the standard-state and temperature-law identifiability of the He-Morse CO2/KCl source.
 *
 * <p>
 * For each temperature, the reported molality-scale activity coefficient implies
 * {@code H0 = pCO2 / (mCO2 * gammaCO2)}. A common value within a temperature block verifies the
 * internal convention of the compiled source, but not the independent provenance of the adopted
 * infinite-dilution anchor. The five-term He-Morse temperature basis is also rank-audited without
 * fitting or adopting a production parameter.
 * </p>
 *
 * @author OpenAI
 * @version 1.0
 */
class CO2HeMorseStandardStateAuditTest {
  private static final Logger logger =
      LogManager.getLogger(CO2HeMorseStandardStateAuditTest.class);
  private static final String RESOURCE =
      "/data/chemistry_benchmarks/co2_kcl_solubility_he_morse1993.csv";
  private static final String OUTPUT = "target/co2-he-morse-standard-state-audit.csv";
  private static final double REPORTED_GAMMA_RELATIVE_UNCERTAINTY = 0.04;
  private static final double[] TEMPERATURES = {273.15, 298.15, 323.15, 348.15, 363.15};

  /**
   * Reconstructs the source convention and proves that its temperature covariance is unresolved.
   *
   * @throws Exception if the retained source cannot be read or the evidence cannot be written
   */
  @Tag("slow")
  @Test
  void sourceConventionIsInternallyConsistentButTemperatureCovarianceIsUnidentified()
      throws Exception {
    List<SourcePoint> points = loadSource();
    assertEquals(26, points.size());

    List<String> report = new ArrayList<String>();
    report.add("family,temperature_K,points,partial_pressure_atm,"
        + "implied_h0_atm_kg_per_mol_geometric_mean,implied_h0_minimum,"
        + "implied_h0_maximum,max_abs_log_residual,reported_gamma_relative_uncertainty,"
        + "implied_pure_water_co2_molality_mol_per_kg,temperature_basis_rank,"
        + "temperature_observations,residual_degrees_of_freedom,disposition");

    for (double temperature : TEMPERATURES) {
      List<SourcePoint> block = selectTemperature(points, temperature);
      StandardStateAudit audit = auditStandardState(block);
      assertTrue(audit.maxAbsLogResidual <= Math.log1p(REPORTED_GAMMA_RELATIVE_UNCERTAINTY),
          temperature + " K source block must share one H0 within reported gamma uncertainty");
      report.add(String.format(Locale.ROOT,
          "HE_MORSE_KCL_STANDARD_STATE,%.2f,%d,%.12g,%.12g,%.12g,%.12g,%.12g,"
              + "%.12g,%.12g,NaN,NaN,NaN,"
              + "internally_reconstructable_anchor_independent_provenance_not_verified",
          temperature, block.size(), audit.partialPressure, audit.geometricMean,
          audit.minimum, audit.maximum, audit.maxAbsLogResidual,
          REPORTED_GAMMA_RELATIVE_UNCERTAINTY,
          audit.partialPressure / audit.geometricMean));
    }

    LinearAlgebraOperations algebra = new EjmlLinearAlgebra();
    int rank = algebra.rank(temperatureDesign());
    int residualDegreesOfFreedom = TEMPERATURES.length - rank;
    assertEquals(5, rank, "five He-Morse temperature functions are independent on five blocks");
    assertEquals(0, residualDegreesOfFreedom,
        "five observations cannot estimate covariance for a five-parameter temperature law");
    report.add(String.format(Locale.ROOT,
        "HE_MORSE_TEMPERATURE_LAW,NaN,5,NaN,NaN,NaN,NaN,NaN,NaN,NaN,%d,%d,%d,"
            + "five_parameter_law_zero_residual_dof_covariance_not_identifiable",
        rank, TEMPERATURES.length, residualDegreesOfFreedom));

    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), report, StandardCharsets.UTF_8);
    for (String row : report) {
      logger.info("CO2_HE_MORSE_STANDARD_STATE_AUDIT,{}", row);
    }
  }

  /** Returns the five-column He-Morse temperature basis {@code 1,T,T^2,1/T,ln(T)}. */
  private static double[][] temperatureDesign() {
    double[][] design = new double[TEMPERATURES.length][5];
    for (int index = 0; index < TEMPERATURES.length; index++) {
      double temperature = TEMPERATURES[index];
      design[index] = new double[] {1.0, temperature, temperature * temperature,
          1.0 / temperature, Math.log(temperature)};
    }
    return design;
  }

  /** Audits a single temperature block without estimating a production parameter. */
  private static StandardStateAudit auditStandardState(List<SourcePoint> points) {
    double partialPressure = points.get(0).partialPressure;
    double logSum = 0.0;
    double minimum = Double.POSITIVE_INFINITY;
    double maximum = Double.NEGATIVE_INFINITY;
    double[] implied = new double[points.size()];
    for (int index = 0; index < points.size(); index++) {
      SourcePoint point = points.get(index);
      assertEquals(partialPressure, point.partialPressure, 0.0,
          "one temperature block must use one CO2 partial pressure");
      implied[index] = point.partialPressure / (point.co2Molality * point.activityCoefficient);
      assertTrue(Double.isFinite(implied[index]) && implied[index] > 0.0);
      logSum += Math.log(implied[index]);
      minimum = Math.min(minimum, implied[index]);
      maximum = Math.max(maximum, implied[index]);
    }
    double geometricMean = Math.exp(logSum / points.size());
    double maxAbsLogResidual = 0.0;
    for (double value : implied) {
      maxAbsLogResidual = Math.max(maxAbsLogResidual, Math.abs(Math.log(value / geometricMean)));
    }
    return new StandardStateAudit(partialPressure, geometricMean, minimum, maximum,
        maxAbsLogResidual);
  }

  /** Selects one retained temperature block. */
  private static List<SourcePoint> selectTemperature(List<SourcePoint> points,
      double temperature) {
    List<SourcePoint> selected = new ArrayList<SourcePoint>();
    for (SourcePoint point : points) {
      if (point.temperature == temperature) {
        selected.add(point);
      }
    }
    assertTrue(selected.size() >= 4, temperature + " K source block");
    return selected;
  }

  /** Loads the retained He-Morse/NIST compilation. */
  private static List<SourcePoint> loadSource() throws Exception {
    InputStream stream = CO2HeMorseStandardStateAuditTest.class.getResourceAsStream(RESOURCE);
    assertNotNull(stream, RESOURCE);
    List<SourcePoint> points = new ArrayList<SourcePoint>();
    boolean headerSeen = false;
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
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
        points.add(new SourcePoint(Double.parseDouble(fields[1]),
            Double.parseDouble(fields[2]), Double.parseDouble(fields[4]),
            Double.parseDouble(fields[6])));
      }
    }
    assertTrue(headerSeen, "He-Morse fixture header");
    return points;
  }

  /** One retained source point. */
  private static final class SourcePoint {
    private final double temperature;
    private final double partialPressure;
    private final double co2Molality;
    private final double activityCoefficient;

    private SourcePoint(double temperature, double partialPressure, double co2Molality,
        double activityCoefficient) {
      this.temperature = temperature;
      this.partialPressure = partialPressure;
      this.co2Molality = co2Molality;
      this.activityCoefficient = activityCoefficient;
    }
  }

  /** Immutable audit result for one temperature block. */
  private static final class StandardStateAudit {
    private final double partialPressure;
    private final double geometricMean;
    private final double minimum;
    private final double maximum;
    private final double maxAbsLogResidual;

    private StandardStateAudit(double partialPressure, double geometricMean, double minimum,
        double maximum, double maxAbsLogResidual) {
      this.partialPressure = partialPressure;
      this.geometricMean = geometricMean;
      this.minimum = minimum;
      this.maximum = maximum;
      this.maxAbsLogResidual = maxAbsLogResidual;
    }
  }
}
