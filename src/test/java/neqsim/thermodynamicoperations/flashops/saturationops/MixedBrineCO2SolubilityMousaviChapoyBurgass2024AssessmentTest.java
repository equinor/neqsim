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
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * No-fit CO2 diagnostic for the Mousavi-Chapoy-Burgass four-salt Table A24 brine.
 *
 * @author OpenAI
 * @version 1.0
 */
class MixedBrineCO2SolubilityMousaviChapoyBurgass2024AssessmentTest {
  private static final Logger logger = LogManager
      .getLogger(MixedBrineCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.class);
  private static final String RESOURCE = "/data/chemistry_benchmarks/co2_mixed_brine_solubility_mousavi_chapoy_burgass2024_table_a24.csv";
  private static final String OUTPUT = "target/co2-mixed-brine-solubility-mousavi-chapoy-burgass2024-table-a24-assessment.csv";
  private static final double CO2_FEED_MOLES = 10.0;
  private static final double WATER_MOLAR_MASS_KG_PER_MOL = 0.01801528;
  private static final double NACL_MOLAR_MASS_KG_PER_MOL = 0.05844277;
  private static final double KCL_MOLAR_MASS_KG_PER_MOL = 0.0745513;
  private static final double CACL2_MOLAR_MASS_KG_PER_MOL = 0.110984;
  private static final double MGCL2_MOLAR_MASS_KG_PER_MOL = 0.095211;

  /**
   * Reports Table A24 with exact preparation, rounded-recipe, root and pure-water controls.
   *
   * @throws Exception if the reference fixture or generated diagnostic cannot be read or written
   */
  @Tag("slow")
  @Test
  void tableA24ReportsHydratedPreparationAndContributionControls() throws Exception {
    PreparedBrine exactRecipe = PreparedBrine.fromSourcePreparation();
    exactRecipe.assertSourceReconciliation();
    List<ReferenceRow> rows = loadReferences();
    List<String> report = new ArrayList<String>();
    report.add("point_id,source_table,temperature_K,total_pressure_bara,"
        + "source_co2_mole_fraction_salt_free,source_uncertainty_mole_fraction,"
        + "model_co2_mole_fraction_including_ions,model_co2_mole_fraction_molecular_only,"
        + "residual_molecular_mole_fraction,model_co2_molality_mol_per_kg_water,water_activity_proxy,"
        + "fluid_phases,component_balance_residual_mol,molecular_log_fugacity_residual,"
        + "constrained_fluid_phases,constrained_model_co2_mole_fraction_molecular_only,"
        + "constrained_residual_molecular_mole_fraction,constrained_minus_default_molecular_mole_fraction,"
        + "constrained_water_activity_proxy,"
        + "constrained_minus_default_water_activity,constrained_molecular_log_fugacity_residual,"
        + "constrained_co2_fugacity_bara,constrained_co2_fugacity_ratio,"
        + "constrained_effective_f_over_x_bara,constrained_effective_f_over_x_ratio,"
        + "constrained_log_dissolved_co2_ratio,constrained_log_co2_fugacity_ratio,"
        + "constrained_log_effective_f_over_x_ratio,constrained_decomposition_identity_residual,"
        + "constrained_brine_minus_pure_water_water_activity,"
        + "rounded_recipe_model_co2_mole_fraction_molecular_only,exact_minus_rounded_molecular_mole_fraction,"
        + "rounded_recipe_water_activity_proxy,exact_minus_rounded_water_activity,"
        + "matched_pure_water_co2_mole_fraction,brine_to_pure_water_co2_ratio,brine_co2_fugacity_bara,"
        + "pure_water_co2_fugacity_bara,co2_fugacity_ratio,brine_effective_f_over_x_bara,"
        + "pure_water_effective_f_over_x_bara,effective_f_over_x_ratio,log_dissolved_co2_ratio,"
        + "log_co2_fugacity_ratio,log_effective_f_over_x_ratio,decomposition_identity_residual,"
        + "matched_pure_water_water_activity,brine_minus_pure_water_water_activity,"
        + "nacl_molality_mol_per_kg_final_water,kcl_molality_mol_per_kg_final_water,"
        + "cacl2_molality_mol_per_kg_final_water,mgcl2_molality_mol_per_kg_final_water,"
        + "chloride_and_positive_charge_mol_per_kg_final_water,ionic_strength_mol_per_kg_final_water,"
        + "actual_nacl_mass_fraction_percent,actual_kcl_mass_fraction_percent,"
        + "actual_cacl2_mass_fraction_percent,actual_mgcl2_mass_fraction_percent,aqueous_water_mass_kg,"
        + "co2_feed_moles,pure_water_component_balance_residual_mol,pure_water_molecular_log_fugacity_residual");
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), report, StandardCharsets.UTF_8);

    for (ReferenceRow row : rows) {
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference exactReference = row
          .toExplicitReference(exactRecipe);
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference roundedReference = row.toRoundedReference();
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result result = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(exactReference, CO2_FEED_MOLES);
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result constrained = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(exactReference, CO2_FEED_MOLES, true);
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result rounded = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(roundedReference, CO2_FEED_MOLES);
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(exactReference.withoutSalt(), result.co2FeedMoles);

      assertEquals(result.co2FeedMoles, pureWater.co2FeedMoles, 0.0, row.pointId + " matched pure-water CO2 reservoir");
      assertEquals(result.co2FeedMoles, constrained.co2FeedMoles, 0.0,
          row.pointId + " matched constrained-root CO2 reservoir");
      assertTrue(constrained.molecularLogFugacityResidual < 1.0e-8,
          row.pointId + " constrained molecular fugacity closure");
      assertTrue(pureWater.componentBalanceResidualMol <= 1.0e-10,
          row.pointId + " matched pure-water component balance");
      assertTrue(pureWater.molecularLogFugacityResidual < 1.0e-8,
          row.pointId + " matched pure-water molecular fugacity closure");
      assertTrue(Math.abs(result.decompositionIdentityResidual(pureWater)) < 1.0e-12,
          row.pointId + " dissolved-CO2 decomposition identity");
      assertTrue(Math.abs(constrained.decompositionIdentityResidual(pureWater)) < 1.0e-12,
          row.pointId + " constrained dissolved-CO2 decomposition identity");

      String outputRow = toCsv(row, exactRecipe, result, constrained, rounded, pureWater);
      report.add(outputRow);
      Files.write(Paths.get(OUTPUT), Collections.singletonList(outputRow), StandardCharsets.UTF_8,
          StandardOpenOption.APPEND);
    }

    assertEquals(18, rows.size());
    assertEquals(19, report.size());
    for (String row : report) {
      logger.info("CO2_MIXED_BRINE_MOUSAVI_CHAPOY_BURGASS_TABLE_A24_DIAGNOSTIC,{}", row);
    }
  }

  /**
   * Serializes one source row and its exact, numerical, composition and pure-water controls.
   *
   * @param row source state
   * @param recipe exact hydrated-reagent preparation
   * @param result default exact-recipe result
   * @param constrained exact-recipe result from explicit gas/oil root candidates
   * @param rounded result from the published one-decimal anhydrous mass fractions
   * @param pureWater matched no-salt result
   * @return one CSV row
   */
  private static String toCsv(ReferenceRow row, PreparedBrine recipe,
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result result,
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result constrained,
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result rounded,
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater) {
    double modelMolecularFraction = result.molecularMolePercent / 100.0;
    double constrainedMolecularFraction = constrained.molecularMolePercent / 100.0;
    double roundedMolecularFraction = rounded.molecularMolePercent / 100.0;
    double pureWaterMolecularFraction = pureWater.molecularMolePercent / 100.0;
    double dissolvedRatio = result.molecularMolePercent / pureWater.molecularMolePercent;
    double fugacityRatio = result.co2FugacityBara / pureWater.co2FugacityBara;
    double effectiveRatio = result.effectiveFOverXBara() / pureWater.effectiveFOverXBara();
    double constrainedDissolvedRatio = constrained.molecularMolePercent / pureWater.molecularMolePercent;
    double constrainedFugacityRatio = constrained.co2FugacityBara / pureWater.co2FugacityBara;
    double constrainedEffectiveRatio = constrained.effectiveFOverXBara() / pureWater.effectiveFOverXBara();
    return row.pointId + "," + row.sourceTable + "," + row.temperatureK + "," + row.totalPressureBara + ","
        + row.sourceCo2MoleFraction + "," + row.sourceUncertaintyMoleFraction + "," + result.nativeMolePercent / 100.0
        + "," + modelMolecularFraction + "," + (modelMolecularFraction - row.sourceCo2MoleFraction) + ","
        + result.co2Molality + "," + result.waterActivity + "," + result.phaseTypes + ","
        + result.componentBalanceResidualMol + "," + result.molecularLogFugacityResidual + "," + constrained.phaseTypes
        + "," + constrainedMolecularFraction + "," + (constrainedMolecularFraction - row.sourceCo2MoleFraction) + ","
        + (constrainedMolecularFraction - modelMolecularFraction) + "," + constrained.waterActivity + ","
        + (constrained.waterActivity - result.waterActivity) + "," + constrained.molecularLogFugacityResidual + ","
        + constrained.co2FugacityBara + "," + constrainedFugacityRatio + "," + constrained.effectiveFOverXBara() + ","
        + constrainedEffectiveRatio + "," + Math.log(constrainedDissolvedRatio) + ","
        + Math.log(constrainedFugacityRatio) + "," + Math.log(constrainedEffectiveRatio) + ","
        + constrained.decompositionIdentityResidual(pureWater) + ","
        + (constrained.waterActivity - pureWater.waterActivity) + "," + roundedMolecularFraction + ","
        + (modelMolecularFraction - roundedMolecularFraction) + "," + rounded.waterActivity + ","
        + (result.waterActivity - rounded.waterActivity) + "," + pureWaterMolecularFraction + "," + dissolvedRatio + ","
        + result.co2FugacityBara + "," + pureWater.co2FugacityBara + "," + fugacityRatio + ","
        + result.effectiveFOverXBara() + "," + pureWater.effectiveFOverXBara() + "," + effectiveRatio + ","
        + Math.log(dissolvedRatio) + "," + Math.log(fugacityRatio) + "," + Math.log(effectiveRatio) + ","
        + result.decompositionIdentityResidual(pureWater) + "," + pureWater.waterActivity + ","
        + (result.waterActivity - pureWater.waterActivity) + "," + recipe.naclMolality + "," + recipe.kclMolality + ","
        + recipe.cacl2Molality + "," + recipe.mgcl2Molality + "," + recipe.chargeMolality + ","
        + recipe.ionicStrengthMolality + "," + result.actualNaclMassPercent + "," + result.actualKclMassPercent + ","
        + result.actualCacl2MassPercent + "," + result.actualMgcl2MassPercent + "," + result.waterMassKg + ","
        + result.co2FeedMoles + "," + pureWater.componentBalanceResidualMol + ","
        + pureWater.molecularLogFugacityResidual;
  }

  /**
   * Loads the eighteen source-pinned Table A24 states.
   *
   * @return source states
   * @throws Exception if the fixture cannot be read or parsed
   */
  private static List<ReferenceRow> loadReferences() throws Exception {
    InputStream stream = MixedBrineCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.class
        .getResourceAsStream(RESOURCE);
    assertNotNull(stream, RESOURCE);
    List<ReferenceRow> rows = new ArrayList<ReferenceRow>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("point_id") || line.trim().isEmpty()) {
          continue;
        }
        String[] fields = line.split(",");
        assertEquals(6, fields.length, fields[0] + " fixture width");
        assertEquals("A24", fields[1], fields[0] + " source table");
        rows.add(new ReferenceRow(fields[0], fields[1], Double.parseDouble(fields[2]), Double.parseDouble(fields[3]),
            Double.parseDouble(fields[4]), Double.parseDouble(fields[5])));
      }
    }
    assertEquals(18, rows.size());
    return rows;
  }

  /**
   * One factual Table A24 row.
   *
   * @author OpenAI
   * @version 1.0
   */
  private static final class ReferenceRow {
    private final String pointId;
    private final String sourceTable;
    private final double temperatureK;
    private final double totalPressureBara;
    private final double sourceCo2MoleFraction;
    private final double sourceUncertaintyMoleFraction;

    /**
     * Creates one source row.
     *
     * @param pointId stable point identifier
     * @param sourceTable source table identifier
     * @param temperatureK temperature in K
     * @param totalPressureBara total pressure in bara
     * @param sourceCo2MoleFraction salt-free aqueous CO2 mole fraction
     * @param sourceUncertaintyMoleFraction displayed source uncertainty
     */
    private ReferenceRow(String pointId, String sourceTable, double temperatureK, double totalPressureBara,
        double sourceCo2MoleFraction, double sourceUncertaintyMoleFraction) {
      this.pointId = pointId;
      this.sourceTable = sourceTable;
      this.temperatureK = temperatureK;
      this.totalPressureBara = totalPressureBara;
      this.sourceCo2MoleFraction = sourceCo2MoleFraction;
      this.sourceUncertaintyMoleFraction = sourceUncertaintyMoleFraction;
    }

    /**
     * Creates the exact hydrated-preparation reference.
     *
     * @param recipe source preparation reduced to final-water molalities
     * @return explicit-molality reference
     */
    private KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference toExplicitReference(PreparedBrine recipe) {
      return new KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference(pointId, sourceTable,
          recipe.naclMassPercent, recipe.kclMassPercent, recipe.cacl2MassPercent, recipe.mgcl2MassPercent,
          recipe.naclMolality, recipe.kclMolality, recipe.cacl2Molality, recipe.mgcl2Molality, temperatureK,
          totalPressureBara / 10.0, 100.0 * sourceCo2MoleFraction, 100.0 * sourceUncertaintyMoleFraction);
    }

    /**
     * Creates the published rounded-mass-fraction sensitivity reference.
     *
     * @return rounded-composition reference
     */
    private KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference toRoundedReference() {
      return new KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference(pointId + "-ROUNDED", sourceTable,
          19.4, 0.3, 2.3, 1.1, temperatureK, totalPressureBara / 10.0, 100.0 * sourceCo2MoleFraction,
          100.0 * sourceUncertaintyMoleFraction);
    }
  }

  /**
   * Exact hydrated-reagent preparation reduced to source-equivalent anhydrous salt quantities.
   *
   * @author OpenAI
   * @version 1.0
   */
  private static final class PreparedBrine {
    private final double finalWaterKg;
    private final double naclMolality;
    private final double kclMolality;
    private final double cacl2Molality;
    private final double mgcl2Molality;
    private final double chargeMolality;
    private final double ionicStrengthMolality;
    private final double naclMassPercent;
    private final double kclMassPercent;
    private final double cacl2MassPercent;
    private final double mgcl2MassPercent;

    /**
     * Creates the reduced preparation.
     *
     * @param finalWaterKg final solvent water, including crystal water, in kg
     * @param naclMolality NaCl formula-unit molality
     * @param kclMolality KCl formula-unit molality
     * @param cacl2Molality CaCl2 formula-unit molality
     * @param mgcl2Molality MgCl2 formula-unit molality
     * @param naclMassPercent source-equivalent NaCl mass fraction in percent
     * @param kclMassPercent source-equivalent KCl mass fraction in percent
     * @param cacl2MassPercent source-equivalent CaCl2 mass fraction in percent
     * @param mgcl2MassPercent source-equivalent MgCl2 mass fraction in percent
     */
    private PreparedBrine(double finalWaterKg, double naclMolality, double kclMolality, double cacl2Molality,
        double mgcl2Molality, double naclMassPercent, double kclMassPercent, double cacl2MassPercent,
        double mgcl2MassPercent) {
      this.finalWaterKg = finalWaterKg;
      this.naclMolality = naclMolality;
      this.kclMolality = kclMolality;
      this.cacl2Molality = cacl2Molality;
      this.mgcl2Molality = mgcl2Molality;
      this.chargeMolality = naclMolality + kclMolality + 2.0 * cacl2Molality + 2.0 * mgcl2Molality;
      this.ionicStrengthMolality = 0.5
          * (naclMolality + kclMolality + 4.0 * cacl2Molality + 4.0 * mgcl2Molality + chargeMolality);
      this.naclMassPercent = naclMassPercent;
      this.kclMassPercent = kclMassPercent;
      this.cacl2MassPercent = cacl2MassPercent;
      this.mgcl2MassPercent = mgcl2MassPercent;
    }

    /**
     * Reduces the Table 5 gram recipe, treating calcium and magnesium chloride as the documented hydrates.
     *
     * @return exact preparation on a final aqueous-water basis
     */
    private static PreparedBrine fromSourcePreparation() {
      double naclMoles = 0.25813 / NACL_MOLAR_MASS_KG_PER_MOL;
      double kclMoles = 0.00348 / KCL_MOLAR_MASS_KG_PER_MOL;
      double cacl2Moles = 0.04009 / (CACL2_MOLAR_MASS_KG_PER_MOL + 2.0 * WATER_MOLAR_MASS_KG_PER_MOL);
      double mgcl2Moles = 0.03105 / (MGCL2_MOLAR_MASS_KG_PER_MOL + 6.0 * WATER_MOLAR_MASS_KG_PER_MOL);
      double finalWaterKg = 1.0 + 2.0 * cacl2Moles * WATER_MOLAR_MASS_KG_PER_MOL
          + 6.0 * mgcl2Moles * WATER_MOLAR_MASS_KG_PER_MOL;
      double totalPreparationKg = 1.0 + 0.25813 + 0.00348 + 0.04009 + 0.03105;
      return new PreparedBrine(finalWaterKg, naclMoles / finalWaterKg, kclMoles / finalWaterKg,
          cacl2Moles / finalWaterKg, mgcl2Moles / finalWaterKg, 100.0 * 0.25813 / totalPreparationKg,
          100.0 * 0.00348 / totalPreparationKg, 100.0 * cacl2Moles * CACL2_MOLAR_MASS_KG_PER_MOL / totalPreparationKg,
          100.0 * mgcl2Moles * MGCL2_MOLAR_MASS_KG_PER_MOL / totalPreparationKg);
    }

    /**
     * Verifies the arithmetic that disambiguates the hydrated preparation from the rounded table.
     */
    private void assertSourceReconciliation() {
      assertEquals(1.0263339402421, finalWaterKg, 1.0e-12, "final preparation water");
      assertEquals(4.303472167068, naclMolality, 1.0e-12, "NaCl molality");
      assertEquals(0.045481557865, kclMolality, 1.0e-12, "KCl molality");
      assertEquals(0.265697223919, cacl2Molality, 1.0e-12, "CaCl2 molality");
      assertEquals(0.148809209564, mgcl2Molality, 1.0e-12, "MgCl2 molality");
      assertEquals(5.177966591898, chargeMolality, 1.0e-12, "charge and chloride molality");
      assertEquals(5.592473025381, ionicStrengthMolality, 1.0e-12, "ionic strength");
      assertEquals(19.4, roundOneDecimal(naclMassPercent), 0.0, "published NaCl mass fraction");
      assertEquals(0.3, roundOneDecimal(kclMassPercent), 0.0, "published KCl mass fraction");
      assertEquals(2.3, roundOneDecimal(cacl2MassPercent), 0.0, "published CaCl2 mass fraction");
      assertEquals(1.1, roundOneDecimal(mgcl2MassPercent), 0.0, "published MgCl2 mass fraction");
      assertTrue(
          Math.abs((naclMassPercent + kclMassPercent + cacl2MassPercent + mgcl2MassPercent) - 22.9912631595) < 1.0e-9,
          "exact anhydrous salt mass fraction");
    }

    /**
     * Rounds a percentage to the source table's one-decimal precision.
     *
     * @param value percentage to round
     * @return percentage rounded to one decimal place
     */
    private static double roundOneDecimal(double value) {
      return Math.round(10.0 * value) / 10.0;
    }
  }
}
