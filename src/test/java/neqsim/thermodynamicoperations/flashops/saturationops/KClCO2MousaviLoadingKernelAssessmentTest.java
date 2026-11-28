package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Discriminates two degree-one excess-Helmholtz CO2-loading kernels with a frozen
 * Mousavi-Chapoy-Burgass concentration-family holdout.
 *
 * @author OpenAI
 * @version 1.0
 */
class KClCO2MousaviLoadingKernelAssessmentTest {
  private static final String OUTPUT =
      "target/co2-kcl-mousavi-loading-kernel-assessment.csv";
  private static final double WATER_MOLAR_MASS_KG_PER_MOL = 0.01801528;
  private static final double KCL_MOLAR_MASS_KG_PER_MOL = 0.0745513;
  private static final double CO2_FEED_MOLES = 10.0;

  /**
   * Fits A11 and A13 only, then transfers without refit to the frozen A12 family.
   *
   * <p>The response and predictors are centered within source-table/temperature
   * blocks before fitting. This removes one block intercept without treating it as
   * measured loading shape. The two tested chemical-potential increments are
   * 2 L m0 ms and 2 Z m0 ms^2. No coefficient is activated in the production model.
   *
   * @throws Exception if source data, model evaluation, or evidence writing fails
   */
  @Tag("slow")
  @Test
  void a11AndA13CalibrationDiscriminatesA12HoldoutTransfer() throws Exception {
    List<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference> references =
        KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.loadReferences();
    List<State> states = new ArrayList<State>();

    // The source-family partition is fixed before any model evaluation.
    for (KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference :
        references) {
      boolean calibration = "A11".equals(reference.sourceTable)
          || "A13".equals(reference.sourceTable);
      assertTrue(calibration || "A12".equals(reference.sourceTable),
          reference.pointId + " unexpected source table");
      states.add(evaluate(reference, calibration));
    }

    centerWithinBlocks(states, true);
    centerWithinBlocks(states, false);
    Fit linear = fit(states, false);
    Fit linearPlusQuadratic = fit(states, true);

    assertEquals(26, states.size());
    assertEquals(17, count(states, true));
    assertEquals(9, count(states, false));
    assertEquals(6, linear.calibrationBlocks);
    assertEquals(3, blockCount(states, false));
    assertTrue(linear.degreesOfFreedom > 0);
    assertTrue(linearPlusQuadratic.degreesOfFreedom > 0);
    assertTrue(linearPlusQuadratic.conditionNumber < 1.0e12,
        "two-kernel calibration design must be full rank");
    assertFinite(linear);
    assertFinite(linearPlusQuadratic);

    List<String> report = new ArrayList<String>();
    report.add("row_type,point_id,partition,source_table,temperature_K,"
        + "kcl_mass_fraction_percent,total_pressure_MPa,source_co2_molality_mol_per_kg_water,"
        + "model_co2_molality_mol_per_kg_water,pure_water_model_co2_molality_mol_per_kg_water,"
        + "model_log_residual,centered_log_residual,centered_linear_predictor,"
        + "centered_quadratic_predictor,linear_candidate_centered_residual,"
        + "linear_plus_quadratic_candidate_centered_residual,water_activity,"
        + "pure_water_activity,co2_fugacity_bara,pure_water_co2_fugacity_bara,"
        + "model_name,calibration_states,calibration_blocks,parameter_count,"
        + "degrees_of_freedom,linear_coefficient,linear_standard_error,"
        + "quadratic_coefficient,quadratic_standard_error,parameter_correlation,"
        + "condition_number,calibration_baseline_rmse,calibration_candidate_rmse,"
        + "holdout_baseline_rmse,holdout_candidate_rmse,holdout_improved_states,"
        + "aicc,comparison");
    for (State state : states) {
      report.add(stateRow(state, linear, linearPlusQuadratic));
    }
    report.add(fitRow(linear));
    report.add(fitRow(linearPlusQuadratic));
    report.add(comparisonRow(linear, linearPlusQuadratic));

    assertEquals(30, report.size());
    Files.createDirectories(Paths.get("target"));
    Path output = Paths.get(OUTPUT);
    Files.write(output, report, StandardCharsets.UTF_8);
  }

  private static State evaluate(
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference,
      boolean calibration) {
    KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result model =
        KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.calculate(
            reference, CO2_FEED_MOLES);
    KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater =
        KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.calculate(
            reference.withoutSalt(), model.co2FeedMoles);
    double sourceMoleFraction = reference.sourceCo2MolePercent / 100.0;
    double sourceMolality = sourceMoleFraction
        / (1.0 - sourceMoleFraction) / WATER_MOLAR_MASS_KG_PER_MOL;
    double kclMolality = reference.kclMassFractionPercent
        / (100.0 - reference.kclMassFractionPercent) / KCL_MOLAR_MASS_KG_PER_MOL;
    assertTrue(sourceMolality > 0.0, reference.pointId + " positive source loading");
    assertTrue(model.co2Molality > 0.0, reference.pointId + " positive model loading");
    assertTrue(pureWater.co2Molality > 0.0,
        reference.pointId + " positive matched pure-water loading");
    assertTrue(model.componentBalanceResidualMol <= 1.0e-10,
        reference.pointId + " component closure");
    assertTrue(model.molecularLogFugacityResidual < 1.0e-8,
        reference.pointId + " fugacity closure");
    assertTrue(pureWater.componentBalanceResidualMol <= 1.0e-10,
        reference.pointId + " pure-water component closure");
    assertTrue(pureWater.molecularLogFugacityResidual < 1.0e-8,
        reference.pointId + " pure-water fugacity closure");
    return new State(reference, calibration, sourceMolality, kclMolality, model,
        pureWater);
  }

  private static void centerWithinBlocks(List<State> states, boolean calibration) {
    Map<String, List<State>> blocks = new LinkedHashMap<String, List<State>>();
    for (State state : states) {
      if (state.calibration == calibration) {
        blocks.computeIfAbsent(state.block, unused -> new ArrayList<State>()).add(state);
      }
    }
    for (List<State> block : blocks.values()) {
      double meanResponse = mean(block, 0);
      double meanLinear = mean(block, 1);
      double meanQuadratic = mean(block, 2);
      for (State state : block) {
        state.centeredResponse = state.response - meanResponse;
        state.centeredLinear = state.linearPredictor - meanLinear;
        state.centeredQuadratic = state.quadraticPredictor - meanQuadratic;
      }
    }
  }

  private static double mean(List<State> states, int selector) {
    double sum = 0.0;
    for (State state : states) {
      if (selector == 0) {
        sum += state.response;
      } else if (selector == 1) {
        sum += state.linearPredictor;
      } else {
        sum += state.quadraticPredictor;
      }
    }
    return sum / states.size();
  }

  private static Fit fit(List<State> states, boolean quadratic) {
    double xx = 0.0;
    double xz = 0.0;
    double zz = 0.0;
    double xy = 0.0;
    double zy = 0.0;
    for (State state : states) {
      if (state.calibration) {
        xx += state.centeredLinear * state.centeredLinear;
        xz += state.centeredLinear * state.centeredQuadratic;
        zz += state.centeredQuadratic * state.centeredQuadratic;
        xy += state.centeredLinear * state.centeredResponse;
        zy += state.centeredQuadratic * state.centeredResponse;
      }
    }
    double linearCoefficient;
    double quadraticCoefficient = 0.0;
    double inverse00;
    double inverse11 = Double.NaN;
    double inverse01 = Double.NaN;
    double conditionNumber = 1.0;
    if (quadratic) {
      double determinant = xx * zz - xz * xz;
      assertTrue(determinant > 0.0, "two-kernel normal matrix must be positive definite");
      inverse00 = zz / determinant;
      inverse11 = xx / determinant;
      inverse01 = -xz / determinant;
      linearCoefficient = inverse00 * xy + inverse01 * zy;
      quadraticCoefficient = inverse01 * xy + inverse11 * zy;
      double trace = xx + zz;
      double discriminant = Math.sqrt((xx - zz) * (xx - zz) + 4.0 * xz * xz);
      double largest = 0.5 * (trace + discriminant);
      double smallest = 0.5 * (trace - discriminant);
      conditionNumber = Math.sqrt(largest / smallest);
    } else {
      assertTrue(xx > 0.0, "linear-kernel design must have nonzero loading span");
      inverse00 = 1.0 / xx;
      linearCoefficient = xy / xx;
    }

    int calibrationStates = count(states, true);
    int calibrationBlocks = blockCount(states, true);
    int parameterCount = quadratic ? 2 : 1;
    int degreesOfFreedom = calibrationStates - calibrationBlocks - parameterCount;
    double calibrationSse = 0.0;
    double calibrationBaselineSse = 0.0;
    double holdoutSse = 0.0;
    double holdoutBaselineSse = 0.0;
    int holdoutImproved = 0;
    for (State state : states) {
      double candidate = candidateResidual(state, linearCoefficient,
          quadraticCoefficient);
      if (state.calibration) {
        calibrationSse += candidate * candidate;
        calibrationBaselineSse += state.centeredResponse * state.centeredResponse;
      } else {
        holdoutSse += candidate * candidate;
        holdoutBaselineSse += state.centeredResponse * state.centeredResponse;
        if (Math.abs(candidate) < Math.abs(state.centeredResponse)) {
          holdoutImproved++;
        }
      }
    }
    double variance = calibrationSse / degreesOfFreedom;
    double linearStandardError = Math.sqrt(variance * inverse00);
    double quadraticStandardError = quadratic ? Math.sqrt(variance * inverse11)
        : Double.NaN;
    double correlation = quadratic
        ? inverse01 / Math.sqrt(inverse00 * inverse11) : Double.NaN;
    int aiccParameters = calibrationBlocks + parameterCount;
    double aicc = calibrationStates * Math.log(calibrationSse / calibrationStates)
        + 2.0 * aiccParameters
        + 2.0 * aiccParameters * (aiccParameters + 1.0)
            / (calibrationStates - aiccParameters - 1.0);
    return new Fit(quadratic ? "LINEAR_PLUS_QUADRATIC" : "LINEAR_SALT",
        calibrationStates, calibrationBlocks, parameterCount, degreesOfFreedom,
        linearCoefficient, linearStandardError, quadraticCoefficient,
        quadraticStandardError, correlation, conditionNumber,
        Math.sqrt(calibrationBaselineSse / calibrationStates),
        Math.sqrt(calibrationSse / calibrationStates),
        Math.sqrt(holdoutBaselineSse / count(states, false)),
        Math.sqrt(holdoutSse / count(states, false)), holdoutImproved, aicc);
  }

  private static double candidateResidual(State state, double linearCoefficient,
      double quadraticCoefficient) {
    return state.centeredResponse - linearCoefficient * state.centeredLinear
        - quadraticCoefficient * state.centeredQuadratic;
  }

  private static int count(List<State> states, boolean calibration) {
    int count = 0;
    for (State state : states) {
      if (state.calibration == calibration) {
        count++;
      }
    }
    return count;
  }

  private static int blockCount(List<State> states, boolean calibration) {
    Map<String, Boolean> blocks = new LinkedHashMap<String, Boolean>();
    for (State state : states) {
      if (state.calibration == calibration) {
        blocks.put(state.block, Boolean.TRUE);
      }
    }
    return blocks.size();
  }

  private static void assertFinite(Fit fit) {
    assertTrue(Double.isFinite(fit.linearCoefficient));
    assertTrue(Double.isFinite(fit.linearStandardError));
    assertTrue(Double.isFinite(fit.conditionNumber));
    assertTrue(Double.isFinite(fit.calibrationCandidateRmse));
    assertTrue(Double.isFinite(fit.holdoutCandidateRmse));
    assertTrue(Double.isFinite(fit.aicc));
    if (fit.parameterCount == 2) {
      assertTrue(Double.isFinite(fit.quadraticCoefficient));
      assertTrue(Double.isFinite(fit.quadraticStandardError));
      assertTrue(Double.isFinite(fit.parameterCorrelation));
    }
  }

  private static String stateRow(State state, Fit linear, Fit combined) {
    Object[] row = emptyRow();
    row[0] = "STATE";
    row[1] = state.reference.pointId;
    row[2] = state.calibration ? "CALIBRATION_A11_A13" : "HOLDOUT_A12";
    row[3] = state.reference.sourceTable;
    row[4] = state.reference.temperatureK;
    row[5] = state.reference.kclMassFractionPercent;
    row[6] = state.reference.totalPressureMpa;
    row[7] = state.sourceMolality;
    row[8] = state.model.co2Molality;
    row[9] = state.pureWater.co2Molality;
    row[10] = state.response;
    row[11] = state.centeredResponse;
    row[12] = state.centeredLinear;
    row[13] = state.centeredQuadratic;
    row[14] = candidateResidual(state, linear.linearCoefficient, 0.0);
    row[15] = candidateResidual(state, combined.linearCoefficient,
        combined.quadraticCoefficient);
    row[16] = state.model.waterActivity;
    row[17] = state.pureWater.waterActivity;
    row[18] = state.model.co2FugacityBara;
    row[19] = state.pureWater.co2FugacityBara;
    return csv(row);
  }

  private static String fitRow(Fit fit) {
    Object[] row = emptyRow();
    row[0] = "FIT";
    row[2] = "CALIBRATION_A11_A13_HOLDOUT_A12";
    row[20] = fit.name;
    row[21] = fit.calibrationStates;
    row[22] = fit.calibrationBlocks;
    row[23] = fit.parameterCount;
    row[24] = fit.degreesOfFreedom;
    row[25] = fit.linearCoefficient;
    row[26] = fit.linearStandardError;
    if (fit.parameterCount == 2) {
      row[27] = fit.quadraticCoefficient;
      row[28] = fit.quadraticStandardError;
      row[29] = fit.parameterCorrelation;
    }
    row[30] = fit.conditionNumber;
    row[31] = fit.calibrationBaselineRmse;
    row[32] = fit.calibrationCandidateRmse;
    row[33] = fit.holdoutBaselineRmse;
    row[34] = fit.holdoutCandidateRmse;
    row[35] = fit.holdoutImproved;
    row[36] = fit.aicc;
    return csv(row);
  }

  private static String comparisonRow(Fit linear, Fit combined) {
    String comparison = String.format(Locale.ROOT,
        "delta_AICc_combined_minus_linear=%.12g;holdout_RMSE_linear=%.12g;"
            + "holdout_RMSE_combined=%.12g;scientific_disposition_pending_exact_head_artifact",
        combined.aicc - linear.aicc, linear.holdoutCandidateRmse,
        combined.holdoutCandidateRmse);
    Object[] row = emptyRow();
    row[0] = "COMPARISON";
    row[2] = "FROZEN_A12_HOLDOUT";
    row[37] = comparison;
    return csv(row);
  }

  private static Object[] emptyRow() {
    Object[] row = new Object[38];
    Arrays.fill(row, "");
    return row;
  }

  private static String csv(Object... values) {
    StringBuilder row = new StringBuilder();
    for (Object value : values) {
      if (row.length() > 0) {
        row.append(',');
      }
      row.append(value == null ? "" : value.toString());
    }
    return row.toString();
  }

  private static final class State {
    private final KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference;
    private final boolean calibration;
    private final String block;
    private final double sourceMolality;
    private final KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result model;
    private final KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater;
    private final double response;
    private final double linearPredictor;
    private final double quadraticPredictor;
    private double centeredResponse;
    private double centeredLinear;
    private double centeredQuadratic;

    private State(
        KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference,
        boolean calibration, double sourceMolality, double kclMolality,
        KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result model,
        KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater) {
      this.reference = reference;
      this.calibration = calibration;
      this.block = reference.sourceTable + "@" + reference.temperatureK;
      this.sourceMolality = sourceMolality;
      this.model = model;
      this.pureWater = pureWater;
      this.response = Math.log(model.co2Molality / sourceMolality);
      this.linearPredictor = 2.0 * sourceMolality * kclMolality;
      this.quadraticPredictor = this.linearPredictor * kclMolality;
    }
  }

  private static final class Fit {
    private final String name;
    private final int calibrationStates;
    private final int calibrationBlocks;
    private final int parameterCount;
    private final int degreesOfFreedom;
    private final double linearCoefficient;
    private final double linearStandardError;
    private final double quadraticCoefficient;
    private final double quadraticStandardError;
    private final double parameterCorrelation;
    private final double conditionNumber;
    private final double calibrationBaselineRmse;
    private final double calibrationCandidateRmse;
    private final double holdoutBaselineRmse;
    private final double holdoutCandidateRmse;
    private final int holdoutImproved;
    private final double aicc;

    private Fit(String name, int calibrationStates, int calibrationBlocks,
        int parameterCount, int degreesOfFreedom, double linearCoefficient,
        double linearStandardError, double quadraticCoefficient,
        double quadraticStandardError, double parameterCorrelation,
        double conditionNumber, double calibrationBaselineRmse,
        double calibrationCandidateRmse, double holdoutBaselineRmse,
        double holdoutCandidateRmse, int holdoutImproved, double aicc) {
      this.name = name;
      this.calibrationStates = calibrationStates;
      this.calibrationBlocks = calibrationBlocks;
      this.parameterCount = parameterCount;
      this.degreesOfFreedom = degreesOfFreedom;
      this.linearCoefficient = linearCoefficient;
      this.linearStandardError = linearStandardError;
      this.quadraticCoefficient = quadraticCoefficient;
      this.quadraticStandardError = quadraticStandardError;
      this.parameterCorrelation = parameterCorrelation;
      this.conditionNumber = conditionNumber;
      this.calibrationBaselineRmse = calibrationBaselineRmse;
      this.calibrationCandidateRmse = calibrationCandidateRmse;
      this.holdoutBaselineRmse = holdoutBaselineRmse;
      this.holdoutCandidateRmse = holdoutCandidateRmse;
      this.holdoutImproved = holdoutImproved;
      this.aicc = aicc;
    }
  }
}
