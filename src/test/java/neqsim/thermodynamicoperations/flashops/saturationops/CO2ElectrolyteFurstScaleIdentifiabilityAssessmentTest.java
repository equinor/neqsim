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
 * Source-separated identifiability diagnostic for the complete Furst CO2-ion interaction block.
 *
 * <p>
 * The scan uses only the existing zero, half and production interaction blocks. It does not fit or publish a parameter.
 * The independent Liu low-salinity NaCl family is the calibration discriminator; dense Mousavi-Chapoy-Burgass NaCl and
 * KCl rows are validation families. Mixed brine and hydrate evidence remain outside this producer as frozen holdouts.
 *
 * @author OpenAI
 * @version 1.0
 */
class CO2ElectrolyteFurstScaleIdentifiabilityAssessmentTest {
  private static final Logger logger = LogManager
      .getLogger(CO2ElectrolyteFurstScaleIdentifiabilityAssessmentTest.class);
  private static final String LIU_RESOURCE = "/data/chemistry_benchmarks/co2_water_nacl_solubility_liu2021.csv";
  private static final String MCB_NACL_RESOURCE = "/data/chemistry_benchmarks/co2_water_nacl_solubility_mousavi_chapoy_burgass2024.csv";
  private static final String MCB_KCL_RESOURCE = "/data/chemistry_benchmarks/co2_kcl_solubility_mousavi_chapoy_burgass2024.csv";
  private static final String OUTPUT = "target/co2-electrolyte-furst-scale-identifiability-assessment.csv";
  private static final double[] SCALES = {0.0, 0.5, 1.0};
  private static final double CO2_FEED_MOLES = 10.0;
  private static final double CO2_MOLAR_MASS_KG_PER_MOL = 0.0440095;
  private static final double NACL_MOLAR_MASS_KG_PER_MOL = 0.05844277;

  private static final String[] LIU_POINT_IDS = {"LIU2021-NaCl-S10000-T298-P1020", "LIU2021-NaCl-S10000-T298-P1701",
      "LIU2021-NaCl-S15000-T298-P1020", "LIU2021-NaCl-S15000-T298-P1701", "LIU2021-NaCl-S10000-T373-P1020",
      "LIU2021-NaCl-S10000-T373-P1701", "LIU2021-NaCl-S15000-T373-P1020", "LIU2021-NaCl-S15000-T373-P1701"};
  private static final String[] MCB_NACL_POINT_IDS = {"MCB-NACL10-323-P538", "MCB-NACL10-373-P510",
      "MCB-NACL10-423-P534", "MCB-NACL15-323-P549", "MCB-NACL15-373-P552", "MCB-NACL15-423-P572", "MCB-NACL22-323-P217",
      "MCB-NACL22-373-P531", "MCB-NACL22-423-P545"};
  private static final String[] MCB_KCL_POINT_IDS = {"MCB-KCL10-323-P538", "MCB-KCL10-373-P538", "MCB-KCL10-423-P531",
      "MCB-KCL15-323-P538", "MCB-KCL15-373-P548", "MCB-KCL15-423-P531", "MCB-KCL22-323-P288", "MCB-KCL22-373-P524",
      "MCB-KCL22-423-P497"};

  /**
   * Reports source-separated scale sensitivity without choosing or fitting a candidate.
   *
   * @throws Exception if a retained source fixture cannot be read or the report cannot be written
   */
  @Tag("slow")
  @Test
  void sourceFamiliesReportBoundedFurstBlockSensitivityWithoutFitting() throws Exception {
    List<Datum> data = new ArrayList<Datum>();
    data.addAll(loadLiuRows());
    data.addAll(
        loadMcbRows(MCB_NACL_RESOURCE, MCB_NACL_POINT_IDS, "MCB_NACL", "independent_dense_nacl_validation", "NaCl"));
    data.addAll(loadMcbRows(MCB_KCL_RESOURCE, MCB_KCL_POINT_IDS, "MCB_KCL", "independent_dense_kcl_validation", "KCl"));
    assertEquals(26, data.size());

    List<String> report = new ArrayList<String>();
    report.add("family,role,point_id,salt,salt_mass_fraction_percent,temperature_K,total_pressure_MPa,"
        + "furst_co2_ion_scale,source_observable,source_observable_basis,source_matched_pure_observable,"
        + "model_observable,model_matched_pure_observable,relative_residual,"
        + "source_apparent_salt_effect_kg_per_mol_formula,model_apparent_salt_effect_kg_per_mol_formula,"
        + "model_molecular_co2_mole_percent,matched_pure_water_molecular_co2_mole_percent,"
        + "log_dissolved_co2_ratio,log_co2_fugacity_ratio,log_effective_f_over_x_ratio,"
        + "decomposition_identity_residual,water_activity_proxy,matched_pure_water_water_activity,"
        + "component_balance_residual_mol,molecular_log_fugacity_residual,fluid_phases");

    for (Datum datum : data) {
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference = datum.toModelReference();
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(reference.withoutSalt(), CO2_FEED_MOLES, true);
      double modelPure = datum.modelObservable(pureWater);
      assertTrue(pureWater.componentBalanceResidualMol <= 1.0e-10,
          datum.pointId + " matched pure-water component balance");
      assertTrue(pureWater.molecularLogFugacityResidual < 1.0e-8,
          datum.pointId + " matched pure-water molecular fugacity closure");

      for (double scale : SCALES) {
        KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result result = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
            .calculateWithFurstCo2IonScale(reference, CO2_FEED_MOLES, true, scale);
        double model = datum.modelObservable(result);
        double formulaMoles = "KCl".equals(datum.salt) ? result.kclMoles : result.naclMoles;
        assertTrue(formulaMoles > 0.0, datum.pointId + " positive salt formula inventory");
        assertTrue(Double.isFinite(model) && model > 0.0, datum.pointId + " positive model observable");
        assertTrue(result.componentBalanceResidualMol <= 1.0e-10, datum.pointId + " component balance closure");
        assertTrue(result.molecularLogFugacityResidual < 1.0e-8, datum.pointId + " molecular fugacity closure");
        double decompositionResidual = result.decompositionIdentityResidual(pureWater);
        assertTrue(Math.abs(decompositionResidual) < 1.0e-12, datum.pointId + " dissolved-CO2 decomposition identity");

        double sourceSaltEffect = Double.NaN;
        if (Double.isFinite(datum.sourcePureObservable)) {
          sourceSaltEffect = Math.log(datum.sourcePureObservable / datum.sourceObservable) / formulaMoles;
        }
        double modelSaltEffect = Math.log(modelPure / model) / formulaMoles;
        double dissolvedRatio = result.molecularMolePercent / pureWater.molecularMolePercent;
        double fugacityRatio = result.co2FugacityBara / pureWater.co2FugacityBara;
        double effectiveRatio = result.effectiveFOverXBara() / pureWater.effectiveFOverXBara();

        report.add(String.format(Locale.ROOT,
            "%s,%s,%s,%s,%.8f,%.8f,%.8f,%.2f,%.12g,%s,%.12g,%.12g,%.12g,%.12g,"
                + "%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g," + "%.12g,%.12g,%s",
            datum.family, datum.role, datum.pointId, datum.salt, datum.saltMassFractionPercent, datum.temperatureK,
            datum.totalPressureMpa, scale, datum.sourceObservable, datum.observableBasis, datum.sourcePureObservable,
            model, modelPure, model / datum.sourceObservable - 1.0, sourceSaltEffect, modelSaltEffect,
            result.molecularMolePercent, pureWater.molecularMolePercent, Math.log(dissolvedRatio),
            Math.log(fugacityRatio), Math.log(effectiveRatio), decompositionResidual, result.waterActivity,
            pureWater.waterActivity, result.componentBalanceResidualMol, result.molecularLogFugacityResidual,
            result.phaseTypes));
      }
    }

    assertEquals(79, report.size());
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), report, StandardCharsets.UTF_8);
    for (String row : report) {
      logger.info("CO2_ELECTROLYTE_FURST_SCALE_IDENTIFIABILITY,{}", row);
    }
  }

  private static List<Datum> loadLiuRows() throws Exception {
    Map<String, String[]> rows = readCsv(LIU_RESOURCE,
        "point_id,source_file,salt_type,salinity_ppm,nacl_mass_fraction_percent,temperature_K,"
            + "pressure_atm,total_pressure_MPa,source_co2_mol_per_kg_solution",
        9);
    List<Datum> data = new ArrayList<Datum>();
    for (String pointId : LIU_POINT_IDS) {
      String[] fields = requiredRow(rows, pointId);
      String pureId = pointId.replace("LIU2021-NaCl-S10000", "LIU2021-DISTILLED-S0").replace("LIU2021-NaCl-S15000",
          "LIU2021-DISTILLED-S0");
      String[] pure = requiredRow(rows, pureId);
      data.add(new Datum("LIU_NACL", "calibration_discriminator", pointId, "NaCl", Double.parseDouble(fields[4]),
          Double.parseDouble(fields[5]), Double.parseDouble(fields[7]), Double.parseDouble(fields[8]),
          Double.parseDouble(pure[8]), "mol_per_kg_solution"));
    }
    return data;
  }

  private static List<Datum> loadMcbRows(String resource, String[] pointIds, String family, String role, String salt)
      throws Exception {
    boolean kcl = "KCl".equals(salt);
    String header = kcl
        ? "point_id,source_table,nacl_mass_fraction_percent,kcl_mass_fraction_percent,"
            + "cacl2_mass_fraction_percent,temperature_K,total_pressure_MPa,"
            + "source_co2_mole_percent_salt_free,source_uncertainty_percentage_points"
        : "point_id,source_table,nacl_mass_fraction_percent,temperature_K,total_pressure_MPa,"
            + "source_co2_mole_percent_salt_free,source_uncertainty_percentage_points";
    Map<String, String[]> rows = readCsv(resource, header, kcl ? 9 : 7);
    List<Datum> data = new ArrayList<Datum>();
    for (String pointId : pointIds) {
      String[] fields = requiredRow(rows, pointId);
      data.add(new Datum(family, role, pointId, salt, Double.parseDouble(fields[kcl ? 3 : 2]),
          Double.parseDouble(fields[kcl ? 5 : 3]), Double.parseDouble(fields[kcl ? 6 : 4]),
          Double.parseDouble(fields[kcl ? 7 : 5]), Double.NaN, "salt_free_aqueous_mole_percent"));
    }
    return data;
  }

  private static Map<String, String[]> readCsv(String resource, String expectedHeader, int expectedFields)
      throws Exception {
    InputStream stream = CO2ElectrolyteFurstScaleIdentifiabilityAssessmentTest.class.getResourceAsStream(resource);
    assertNotNull(stream, resource);
    Map<String, String[]> rows = new HashMap<String, String[]>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      assertEquals(expectedHeader, reader.readLine(), resource + " header");
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.trim().isEmpty()) {
          continue;
        }
        String[] fields = line.split(",", -1);
        assertEquals(expectedFields, fields.length, line);
        rows.put(fields[0], fields);
      }
    }
    return rows;
  }

  private static String[] requiredRow(Map<String, String[]> rows, String pointId) {
    String[] row = rows.get(pointId);
    assertNotNull(row, pointId);
    return row;
  }

  private static double modelMolPerKgSolution(KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result result) {
    double solutionMassKg = result.waterMassKg + result.naclMoles * NACL_MOLAR_MASS_KG_PER_MOL
        + result.co2Moles * CO2_MOLAR_MASS_KG_PER_MOL;
    return result.co2Moles / solutionMassKg;
  }

  /** One immutable source state and observable mapping. */
  private static final class Datum {
    private final String family;
    private final String role;
    private final String pointId;
    private final String salt;
    private final double saltMassFractionPercent;
    private final double temperatureK;
    private final double totalPressureMpa;
    private final double sourceObservable;
    private final double sourcePureObservable;
    private final String observableBasis;

    private Datum(String family, String role, String pointId, String salt, double saltMassFractionPercent,
        double temperatureK, double totalPressureMpa, double sourceObservable, double sourcePureObservable,
        String observableBasis) {
      this.family = family;
      this.role = role;
      this.pointId = pointId;
      this.salt = salt;
      this.saltMassFractionPercent = saltMassFractionPercent;
      this.temperatureK = temperatureK;
      this.totalPressureMpa = totalPressureMpa;
      this.sourceObservable = sourceObservable;
      this.sourcePureObservable = sourcePureObservable;
      this.observableBasis = observableBasis;
    }

    private KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference toModelReference() {
      double nacl = "NaCl".equals(salt) ? saltMassFractionPercent : 0.0;
      double kcl = "KCl".equals(salt) ? saltMassFractionPercent : 0.0;
      return new KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference(pointId, family, nacl, kcl, 0.0,
          temperatureK, totalPressureMpa, 0.0, 0.0);
    }

    private double modelObservable(KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result result) {
      if ("mol_per_kg_solution".equals(observableBasis)) {
        return modelMolPerKgSolution(result);
      }
      return result.molecularMolePercent;
    }
  }
}
