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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * No-fit dense-CO2 benchmark for the Mousavi-Chapoy-Burgass pure-water and NaCl series.
 *
 * @author OpenAI
 * @version 1.0
 */
class WaterNaClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest {
  private static final Logger logger = LogManager
      .getLogger(WaterNaClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.class);
  private static final String RESOURCE = "/data/chemistry_benchmarks/co2_water_nacl_solubility_mousavi_chapoy_burgass2024.csv";
  private static final String OUTPUT = "target/co2-water-nacl-solubility-mousavi-chapoy-burgass2024-assessment.csv";
  private static final double CO2_FEED_MOLES = 10.0;

  /**
   * Retains source residuals and separates the NaCl response from matched pure water without fitting.
   *
   * @throws Exception if the source fixture or generated evidence cannot be read or written
   */
  @Tag("slow")
  @Test
  void denseWaterAndNaClSeriesReportWithoutFitting() throws Exception {
    List<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference> references = loadReferences();
    List<String> report = new ArrayList<String>();
    report.add("point_id,source_table,nacl_mass_fraction_percent,temperature_K,total_pressure_MPa,"
        + "source_co2_mole_percent_salt_free,source_uncertainty_percentage_points,"
        + "model_co2_mole_percent_molecular_only,relative_residual,constrained_model_co2_mole_percent,"
        + "constrained_minus_default_percentage_points,matched_pure_water_co2_mole_percent,"
        + "log_dissolved_co2_ratio,log_co2_fugacity_ratio,log_effective_f_over_x_ratio,"
        + "decomposition_identity_residual,water_activity_proxy,matched_pure_water_water_activity,"
        + "component_balance_residual_mol,molecular_log_fugacity_residual,fluid_phases,default_solver_status");

    int pureWaterRows = 0;
    int naclRows = 0;
    int defaultSuccesses = 0;
    for (KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference : references) {
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result result = null;
      String defaultSolverStatus = "converged";
      try {
        result = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.calculate(reference, CO2_FEED_MOLES);
        defaultSuccesses++;
      } catch (AssertionError error) {
        defaultSolverStatus = "unavailable:" + error.getMessage().replace(',', ';');
      }
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result constrained = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(reference, CO2_FEED_MOLES, true);
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result selected = result == null ? constrained : result;
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater;
      if (reference.naclMassFractionPercent == 0.0) {
        pureWater = selected;
        pureWaterRows++;
      } else {
        pureWater = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.calculate(reference.withoutSalt(),
            selected.co2FeedMoles, true);
        naclRows++;
      }

      assertTrue(selected.componentBalanceResidualMol < 1.0e-10, reference.pointId + " component balance");
      assertTrue(constrained.molecularLogFugacityResidual < 1.0e-8,
          reference.pointId + " constrained fugacity closure");
      assertTrue(Math.abs(selected.decompositionIdentityResidual(pureWater)) < 1.0e-12,
          reference.pointId + " contribution identity");
      assertTrue(Double.isFinite(selected.molecularMolePercent) && selected.molecularMolePercent > 0.0,
          reference.pointId + " dissolved CO2");

      double dissolvedRatio = selected.molecularMolePercent / pureWater.molecularMolePercent;
      double fugacityRatio = selected.co2FugacityBara / pureWater.co2FugacityBara;
      double effectiveRatio = selected.effectiveFOverXBara() / pureWater.effectiveFOverXBara();
      double defaultMolePercent = result == null ? Double.NaN : result.molecularMolePercent;
      report.add(reference.pointId + "," + reference.sourceTable + "," + reference.naclMassFractionPercent + ","
          + reference.temperatureK + "," + reference.totalPressureMpa + "," + reference.sourceCo2MolePercent + ","
          + reference.sourceUncertaintyPercentagePoints + "," + defaultMolePercent + ","
          + (defaultMolePercent / reference.sourceCo2MolePercent - 1.0) + "," + constrained.molecularMolePercent + ","
          + (constrained.molecularMolePercent - defaultMolePercent) + "," + pureWater.molecularMolePercent + ","
          + Math.log(dissolvedRatio) + "," + Math.log(fugacityRatio) + "," + Math.log(effectiveRatio) + ","
          + selected.decompositionIdentityResidual(pureWater) + "," + selected.waterActivity + ","
          + pureWater.waterActivity + "," + selected.componentBalanceResidualMol + ","
          + selected.molecularLogFugacityResidual + "," + selected.phaseTypes + "," + defaultSolverStatus);
    }

    assertEquals(34, references.size());
    assertEquals(8, pureWaterRows);
    assertEquals(26, naclRows);
    assertTrue(defaultSuccesses >= 33, "default solver reproduced fewer than 33 source states");
    assertEquals(35, report.size());
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), report, StandardCharsets.UTF_8);
    for (String row : report) {
      logger.info("CO2_WATER_NACL_MOUSAVI_CHAPOY_BURGASS_DIAGNOSTIC,{}", row);
    }
  }

  /** Loads the exact source rows without interpreting the reported uncertainty as a tolerance. */
  static List<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference> loadReferences()
      throws Exception {
    InputStream stream = WaterNaClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.class
        .getResourceAsStream(RESOURCE);
    assertNotNull(stream, RESOURCE);
    List<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference> references = new ArrayList<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("point_id") || line.trim().isEmpty()) {
          continue;
        }
        String[] fields = line.split(",");
        references.add(new KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference(fields[0], fields[1],
            Double.parseDouble(fields[2]), 0.0, 0.0, Double.parseDouble(fields[3]), Double.parseDouble(fields[4]),
            Double.parseDouble(fields[5]), Double.parseDouble(fields[6])));
      }
    }
    return references;
  }
}
