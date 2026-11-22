package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * No-fit factorial isolation of the complete Furst CO2-ion block and the K+/CO2 CPA layer.
 *
 * @author OpenAI
 * @version 1.0
 */
class CO2KClInteractionLayerIsolationAssessmentTest {
  private static final Logger logger = LogManager.getLogger(CO2KClInteractionLayerIsolationAssessmentTest.class);
  private static final String OUTPUT = "target/co2-kcl-interaction-layer-isolation-assessment.csv";
  private static final double CO2_FEED_MOLES = 10.0;
  private static final double[] FURST_ENDPOINTS = {0.0, 1.0};
  private static final boolean[] CPA_ENDPOINTS = {false, true};
  private static final Set<String> POINT_IDS = new HashSet<String>(Arrays.asList("MCB-KCL10-323-P538",
      "MCB-KCL10-373-P538", "MCB-KCL10-423-P531", "MCB-KCL15-323-P538", "MCB-KCL15-373-P548", "MCB-KCL15-423-P531",
      "MCB-KCL22-323-P288", "MCB-KCL22-373-P524", "MCB-KCL22-423-P497"));

  /**
   * Reports the four endpoint combinations without choosing or fitting a candidate parameter.
   *
   * @throws Exception if the retained source fixture cannot be read or the report cannot be written
   */
  @Tag("slow")
  @Test
  void denseKclStatesSeparateFurstAndCpaLayerEndpointsWithoutFitting() throws Exception {
    List<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference> references = selectedReferences();
    List<String> report = new ArrayList<String>();
    report.add("point_id,role,kcl_mass_fraction_percent,temperature_K,total_pressure_MPa,"
        + "source_co2_mole_percent_salt_free,source_uncertainty_percentage_points,scenario,"
        + "furst_co2_ion_scale,cpa_k_co2_enabled,model_co2_mole_percent,relative_residual,"
        + "matched_pure_water_co2_mole_percent,log_dissolved_co2_ratio,log_co2_fugacity_ratio,"
        + "log_effective_f_over_x_ratio,water_activity_proxy,matched_pure_water_water_activity,"
        + "factorial_log_dissolved_furst_at_cpa_off,factorial_log_dissolved_cpa_at_furst_off,"
        + "factorial_log_dissolved_cpa_at_furst_on,"
        + "factorial_log_dissolved_coupling,factorial_log_effective_f_over_x_coupling,"
        + "decomposition_identity_residual,component_balance_residual_mol,molecular_log_fugacity_residual,"
        + "fluid_phases");
    boolean furstEffectObserved = false;
    double maxAbsoluteCpaEffect = 0.0;

    for (KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference : references) {
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(reference.withoutSalt(), CO2_FEED_MOLES, true);
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result baseline = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(reference, CO2_FEED_MOLES, true);
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result[][] results = new KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result[2][2];

      for (int furstIndex = 0; furstIndex < FURST_ENDPOINTS.length; furstIndex++) {
        for (int cpaIndex = 0; cpaIndex < CPA_ENDPOINTS.length; cpaIndex++) {
          results[furstIndex][cpaIndex] = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
              .calculateWithInteractionLayerEndpoints(reference, CO2_FEED_MOLES, true, FURST_ENDPOINTS[furstIndex],
                  CPA_ENDPOINTS[cpaIndex]);
        }
      }

      assertEquals(baseline.molecularMolePercent, results[1][1].molecularMolePercent, 0.0,
          reference.pointId + " unchanged production endpoint");
      assertEquals(baseline.co2FugacityBara, results[1][1].co2FugacityBara, 0.0,
          reference.pointId + " unchanged production fugacity");
      assertEquals(baseline.waterActivity, results[1][1].waterActivity, 0.0,
          reference.pointId + " unchanged production water activity");
      assertEquals(baseline.phaseTypes, results[1][1].phaseTypes, reference.pointId + " unchanged production topology");
      furstEffectObserved |= Double.compare(results[1][1].molecularMolePercent,
          results[0][1].molecularMolePercent) != 0;

      double[][] logDissolved = new double[2][2];
      double[][] logEffective = new double[2][2];
      for (int furstIndex = 0; furstIndex < FURST_ENDPOINTS.length; furstIndex++) {
        for (int cpaIndex = 0; cpaIndex < CPA_ENDPOINTS.length; cpaIndex++) {
          logDissolved[furstIndex][cpaIndex] = logDissolvedRatio(results[furstIndex][cpaIndex], pureWater);
          logEffective[furstIndex][cpaIndex] = logEffectiveRatio(results[furstIndex][cpaIndex], pureWater);
        }
      }
      double furstEffectAtCpaOff = logDissolved[1][0] - logDissolved[0][0];
      double cpaEffectAtFurstOff = logDissolved[0][1] - logDissolved[0][0];
      double cpaEffectAtFurstOn = logDissolved[1][1] - logDissolved[1][0];
      maxAbsoluteCpaEffect = Math.max(maxAbsoluteCpaEffect,
          Math.max(Math.abs(cpaEffectAtFurstOff), Math.abs(cpaEffectAtFurstOn)));
      double dissolvedCoupling = logDissolved[1][1] - logDissolved[1][0] - logDissolved[0][1] + logDissolved[0][0];
      double effectiveCoupling = logEffective[1][1] - logEffective[1][0] - logEffective[0][1] + logEffective[0][0];

      for (int furstIndex = 0; furstIndex < FURST_ENDPOINTS.length; furstIndex++) {
        for (int cpaIndex = 0; cpaIndex < CPA_ENDPOINTS.length; cpaIndex++) {
          KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result result = results[furstIndex][cpaIndex];
          assertTrue(result.componentBalanceResidualMol <= 1.0e-10, reference.pointId + " component balance closure");
          assertTrue(result.molecularLogFugacityResidual < 1.0e-8, reference.pointId + " molecular fugacity closure");
          double decompositionResidual = result.decompositionIdentityResidual(pureWater);
          assertTrue(Math.abs(decompositionResidual) < 1.0e-12,
              reference.pointId + " dissolved-CO2 decomposition identity");
          double logFugacity = Math.log(result.co2FugacityBara / pureWater.co2FugacityBara);
          report.add(String.format(Locale.ROOT,
              "%s,layer_discriminator,%.8f,%.8f,%.8f,%.17g,%.17g,%s,%.1f,%s,%.17g,%.17g,%.17g,"
                  + "%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%s",
              reference.pointId, reference.kclMassFractionPercent, reference.temperatureK, reference.totalPressureMpa,
              reference.sourceCo2MolePercent, reference.sourceUncertaintyPercentagePoints,
              scenarioName(FURST_ENDPOINTS[furstIndex], CPA_ENDPOINTS[cpaIndex]), FURST_ENDPOINTS[furstIndex],
              Boolean.toString(CPA_ENDPOINTS[cpaIndex]), result.molecularMolePercent,
              result.molecularMolePercent / reference.sourceCo2MolePercent - 1.0, pureWater.molecularMolePercent,
              logDissolved[furstIndex][cpaIndex], logFugacity, logEffective[furstIndex][cpaIndex], result.waterActivity,
              pureWater.waterActivity, furstEffectAtCpaOff, cpaEffectAtFurstOff, cpaEffectAtFurstOn, dissolvedCoupling,
              effectiveCoupling, decompositionResidual, result.componentBalanceResidualMol,
              result.molecularLogFugacityResidual, result.phaseTypes));
        }
      }
    }

    assertTrue(maxAbsoluteCpaEffect < 1.0e-12,
        "legacy K+/CO2 CPA row must remain numerically inactive in the frozen grid");
    assertTrue(furstEffectObserved, "Furst ablation must change dissolved CO2 somewhere in the frozen grid");
    assertEquals(37, report.size());
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), report, StandardCharsets.UTF_8);
    for (String row : report) {
      logger.info("CO2_KCL_INTERACTION_LAYER_ISOLATION,{}", row);
    }
  }

  /**
   * Selects the nine predeclared dense KCl layer-discriminator states.
   *
   * @return source states in fixture order
   * @throws Exception if the source fixture cannot be read
   */
  private static List<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference> selectedReferences()
      throws Exception {
    List<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference> selected = new ArrayList<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference>();
    for (KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference : KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
        .loadReferences()) {
      if (POINT_IDS.contains(reference.pointId)) {
        selected.add(reference);
      }
    }
    assertEquals(POINT_IDS.size(), selected.size());
    return selected;
  }

  /**
   * Creates the stable endpoint label.
   *
   * @param furstScale complete Furst block scale
   * @param cpaEnabled whether the K+/CO2 CPA layer remains enabled
   * @return endpoint label
   */
  private static String scenarioName(double furstScale, boolean cpaEnabled) {
    return "F" + (furstScale == 1.0 ? "1" : "0") + "_C" + (cpaEnabled ? "1" : "0");
  }

  /**
   * Computes the log dissolved-CO2 response relative to matched pure water.
   *
   * @param result brine result
   * @param pureWater matched pure-water result
   * @return log dissolved-CO2 ratio
   */
  private static double logDissolvedRatio(KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result result,
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater) {
    return Math.log(result.molecularMolePercent / pureWater.molecularMolePercent);
  }

  /**
   * Computes the log effective aqueous f/x response relative to matched pure water.
   *
   * @param result brine result
   * @param pureWater matched pure-water result
   * @return log effective f/x ratio
   */
  private static double logEffectiveRatio(KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result result,
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater) {
    return Math.log(result.effectiveFOverXBara() / pureWater.effectiveFOverXBara());
  }
}
