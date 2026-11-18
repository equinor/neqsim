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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * No-fit assessment of independent low-salinity water/NaCl CO2 solubility data from Liu et al. (2021).
 *
 * @author OpenAI
 * @version 1.0
 */
class LowSalinityNaClCO2SolubilityLiu2021AssessmentTest {
  private static final Logger logger = LogManager.getLogger(LowSalinityNaClCO2SolubilityLiu2021AssessmentTest.class);
  private static final String RESOURCE = "/data/chemistry_benchmarks/co2_water_nacl_solubility_liu2021.csv";
  private static final String OUTPUT = "target/co2-water-nacl-solubility-liu2021-assessment.csv";
  private static final double CO2_FEED_MOLES = 10.0;
  private static final double CO2_MOLAR_MASS_KG_PER_MOL = 0.0440095;
  private static final double NACL_MOLAR_MASS_KG_PER_MOL = 0.05844277;

  /**
   * Reports the source observable, matched pure-water response and apparent salt effects without fitting.
   *
   * @throws Exception if the source fixture or generated report cannot be read or written
   */
  @Tag("slow")
  @Test
  void independentLowSalinitySeriesReportsWithoutFitting() throws Exception {
    List<SourceReference> references = loadReferences();
    Map<String, Double> sourcePureByState = new HashMap<String, Double>();
    for (SourceReference reference : references) {
      if (reference.salinityPpm == 0) {
        sourcePureByState.put(reference.stateKey(), reference.sourceCo2MolPerKgSolution);
      }
    }

    Map<String, KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result> pureWaterByState =
        new HashMap<String, KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result>();
    Map<Integer, Double> sourceSums = new HashMap<Integer, Double>();
    Map<Integer, Integer> sourceCounts = new HashMap<Integer, Integer>();
    List<String> report = new ArrayList<String>();
    report.add("point_id,salt_type,salinity_ppm,nacl_mass_fraction_percent,temperature_K,pressure_atm,"
        + "total_pressure_MPa,source_co2_mol_per_kg_solution,model_co2_mol_per_kg_solution,"
        + "relative_residual,matched_pure_water_source_co2_mol_per_kg_solution,"
        + "matched_pure_water_model_co2_mol_per_kg_solution,source_apparent_salt_effect_kg_per_mol,"
        + "model_apparent_salt_effect_kg_per_mol,model_co2_fugacity_bara,matched_pure_water_co2_fugacity_bara,"
        + "model_water_activity,matched_pure_water_water_activity,decomposition_identity_residual,"
        + "component_balance_residual_mol,molecular_log_fugacity_residual,phase_types,"
        + "constrained_model_co2_mol_per_kg_solution,constrained_molecular_log_fugacity_residual");

    int pureCount = 0;
    int saltCount = 0;
    int constrainedCount = 0;
    for (SourceReference reference : references) {
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result result =
          KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.calculate(reference.toModelReference(),
              CO2_FEED_MOLES);
      double model = modelMolPerKgSolution(result);
      assertTrue(Double.isFinite(model) && model > 0.0, reference.pointId + " finite positive model result");
      assertTrue(result.componentBalanceResidualMol <= 1.0e-10,
          reference.pointId + " component balance closure");
      assertTrue(Double.isFinite(result.molecularLogFugacityResidual),
          reference.pointId + " finite default fugacity diagnostic");

      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater;
      if (reference.salinityPpm == 0) {
        pureWater = result;
        pureWaterByState.put(reference.stateKey(), result);
        pureCount++;
      } else {
        pureWater = pureWaterByState.get(reference.stateKey());
        assertNotNull(pureWater, reference.pointId + " matched pure-water model state");
        saltCount++;
      }
      Double sourcePure = sourcePureByState.get(reference.stateKey());
      assertNotNull(sourcePure, reference.pointId + " matched pure-water source state");
      double modelPure = modelMolPerKgSolution(pureWater);
      double decompositionResidual = result.decompositionIdentityResidual(pureWater);
      assertTrue(Math.abs(decompositionResidual) < 1.0e-12,
          reference.pointId + " dissolved-CO2 decomposition identity");

      double sourceSaltEffect = Double.NaN;
      double modelSaltEffect = Double.NaN;
      if (reference.salinityPpm > 0) {
        assertTrue(result.naclMoles > 0.0, reference.pointId + " positive NaCl formula inventory");
        sourceSaltEffect = Math.log(sourcePure.doubleValue() / reference.sourceCo2MolPerKgSolution)
            / result.naclMoles;
        modelSaltEffect = Math.log(modelPure / model) / result.naclMoles;
      }

      double constrainedModel = Double.NaN;
      double constrainedResidual = Double.NaN;
      if (reference.salinityPpm == 15000 && Math.abs(reference.pressureAtm - 170.1) < 1.0e-12) {
        KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result constrained =
            KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.calculate(reference.toModelReference(),
                CO2_FEED_MOLES, true);
        constrainedModel = modelMolPerKgSolution(constrained);
        constrainedResidual = constrained.molecularLogFugacityResidual;
        assertTrue(constrainedResidual < 1.0e-8,
            reference.pointId + " constrained molecular fugacity closure");
        constrainedCount++;
      }

      report.add(String.format(Locale.ROOT,
          "%s,%s,%d,%.8f,%.1f,%.1f,%.8f,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,"
              + "%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%s,%.12g,%.12g",
          reference.pointId, reference.saltType, reference.salinityPpm, reference.naclMassFractionPercent,
          reference.temperatureK, reference.pressureAtm, reference.totalPressureMpa,
          reference.sourceCo2MolPerKgSolution, model,
          (model - reference.sourceCo2MolPerKgSolution) / reference.sourceCo2MolPerKgSolution,
          sourcePure.doubleValue(), modelPure, sourceSaltEffect, modelSaltEffect, result.co2FugacityBara,
          pureWater.co2FugacityBara, result.waterActivity, pureWater.waterActivity, decompositionResidual,
          result.componentBalanceResidualMol, result.molecularLogFugacityResidual, result.phaseTypes,
          constrainedModel, constrainedResidual));
      sourceSums.put(reference.salinityPpm,
          sourceSums.containsKey(reference.salinityPpm)
              ? sourceSums.get(reference.salinityPpm) + reference.sourceCo2MolPerKgSolution
              : reference.sourceCo2MolPerKgSolution);
      sourceCounts.put(reference.salinityPpm,
          sourceCounts.containsKey(reference.salinityPpm) ? sourceCounts.get(reference.salinityPpm) + 1 : 1);
    }

    assertEquals(48, references.size());
    assertEquals(12, pureCount);
    assertEquals(36, saltCount);
    assertEquals(4, constrainedCount);
    assertEquals(49, report.size());
    assertTrue(sourceMean(sourceSums, sourceCounts, 0) > sourceMean(sourceSums, sourceCounts, 1000));
    assertTrue(sourceMean(sourceSums, sourceCounts, 1000) > sourceMean(sourceSums, sourceCounts, 10000));
    assertTrue(sourceMean(sourceSums, sourceCounts, 10000) > sourceMean(sourceSums, sourceCounts, 15000));

    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), report, StandardCharsets.UTF_8);
    for (String row : report) {
      logger.info("CO2_WATER_NACL_LIU2021_DIAGNOSTIC,{}", row);
    }
  }

  /**
   * Converts a model aqueous result to the source's mol-per-kilogram-of-solution observable.
   *
   * @param result model result on a final one-kilogram aqueous-water basis
   * @return dissolved CO2 moles per kilogram of aqueous solution including dissolved CO2 and salt
   */
  private static double modelMolPerKgSolution(
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result result) {
    double solutionMassKg = result.waterMassKg + result.naclMoles * NACL_MOLAR_MASS_KG_PER_MOL
        + result.co2Moles * CO2_MOLAR_MASS_KG_PER_MOL;
    return result.co2Moles / solutionMassKg;
  }

  /**
   * Returns the source mean for one salinity, used only as a fixture-integrity trend check.
   *
   * @param sums source-value sums by salinity
   * @param counts source-value counts by salinity
   * @param salinityPpm selected salinity in ppm
   * @return arithmetic mean source solubility
   */
  private static double sourceMean(Map<Integer, Double> sums, Map<Integer, Integer> counts, int salinityPpm) {
    assertTrue(sums.containsKey(salinityPpm) && counts.containsKey(salinityPpm));
    return sums.get(salinityPpm) / counts.get(salinityPpm);
  }

  /**
   * Loads the predeclared source subset.
   *
   * @return source references in fixture order
   * @throws Exception if the fixture cannot be read or parsed
   */
  private static List<SourceReference> loadReferences() throws Exception {
    InputStream stream = LowSalinityNaClCO2SolubilityLiu2021AssessmentTest.class.getResourceAsStream(RESOURCE);
    assertNotNull(stream, RESOURCE);
    List<SourceReference> references = new ArrayList<SourceReference>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String header = reader.readLine();
      assertEquals("point_id,source_file,salt_type,salinity_ppm,nacl_mass_fraction_percent,temperature_K,"
          + "pressure_atm,total_pressure_MPa,source_co2_mol_per_kg_solution", header);
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.trim().isEmpty()) {
          continue;
        }
        String[] fields = line.split(",", -1);
        assertEquals(9, fields.length, line);
        references.add(new SourceReference(fields[0], fields[2], Integer.parseInt(fields[3]),
            Double.parseDouble(fields[4]), Double.parseDouble(fields[5]), Double.parseDouble(fields[6]),
            Double.parseDouble(fields[7]), Double.parseDouble(fields[8])));
      }
    }
    assertEquals(48, references.size());
    return references;
  }

  /** Source row and model-state mapping. */
  private static final class SourceReference {
    private final String pointId;
    private final String saltType;
    private final int salinityPpm;
    private final double naclMassFractionPercent;
    private final double temperatureK;
    private final double pressureAtm;
    private final double totalPressureMpa;
    private final double sourceCo2MolPerKgSolution;

    /**
     * Creates one immutable source reference.
     *
     * @param pointId stable source-row identifier
     * @param saltType source salt label
     * @param salinityPpm source salinity in mass ppm
     * @param naclMassFractionPercent NaCl mass percent in the source brine
     * @param temperatureK source temperature in K
     * @param pressureAtm source total pressure in atm
     * @param totalPressureMpa source total pressure converted to MPa
     * @param sourceCo2MolPerKgSolution source dissolved CO2 in mol/kg solution
     */
    private SourceReference(String pointId, String saltType, int salinityPpm, double naclMassFractionPercent,
        double temperatureK, double pressureAtm, double totalPressureMpa,
        double sourceCo2MolPerKgSolution) {
      this.pointId = pointId;
      this.saltType = saltType;
      this.salinityPpm = salinityPpm;
      this.naclMassFractionPercent = naclMassFractionPercent;
      this.temperatureK = temperatureK;
      this.pressureAtm = pressureAtm;
      this.totalPressureMpa = totalPressureMpa;
      this.sourceCo2MolPerKgSolution = sourceCo2MolPerKgSolution;
    }

    /**
     * Builds the shared explicit-ion Electrolyte-CPA source state.
     *
     * @return model reference with no experimental-accuracy field populated
     */
    private KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference toModelReference() {
      return new KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference(pointId, "Liu2021",
          naclMassFractionPercent, 0.0, 0.0, temperatureK, totalPressureMpa, 0.0, 0.0);
    }

    /**
     * Returns the exact matched temperature-pressure key.
     *
     * @return state key
     */
    private String stateKey() {
      return Double.toString(temperatureK) + "|" + Double.toString(totalPressureMpa);
    }
  }
}
