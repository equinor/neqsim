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
 * No-fit assessment of independent low-salinity CaCl2 and MgCl2 CO2 solubility data.
 *
 * @author OpenAI
 * @version 1.0
 */
class LowSalinityDivalentBrineCO2SolubilityLiu2021AssessmentTest {
  private static final Logger logger = LogManager
      .getLogger(LowSalinityDivalentBrineCO2SolubilityLiu2021AssessmentTest.class);
  private static final String RESOURCE = "/data/chemistry_benchmarks/co2_water_cacl2_mgcl2_solubility_liu2021.csv";
  private static final String OUTPUT = "target/co2-water-cacl2-mgcl2-solubility-liu2021-assessment.csv";
  private static final double CO2_FEED_MOLES = 10.0;
  private static final double CO2_MOLAR_MASS_KG_PER_MOL = 0.0440095;
  private static final double CACL2_MOLAR_MASS_KG_PER_MOL = 0.11098;
  private static final double MGCL2_MOLAR_MASS_KG_PER_MOL = 0.095211;

  /**
   * Reports matched single-salt responses and numerical checks without fitting a coefficient.
   *
   * @throws Exception if the source fixture or generated report cannot be read or written
   */
  @Tag("slow")
  @Test
  void independentDivalentSeriesReportsIdentifiabilityWithoutFitting() throws Exception {
    List<SourceReference> references = loadReferences();
    Map<String, Double> sourcePureByState = new HashMap<String, Double>();
    for (SourceReference reference : references) {
      if (reference.isPureWater()) {
        sourcePureByState.put(reference.stateKey(), reference.sourceCo2MolPerKgSolution);
      }
    }

    Map<String, KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result> pureWaterByState = new HashMap<String, KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result>();
    List<String> report = new ArrayList<String>();
    report.add("point_id,salt_type,evidence_role,salinity_ppm,salt_mass_fraction_percent,temperature_K,"
        + "pressure_atm,total_pressure_MPa,source_co2_mol_per_kg_solution,"
        + "model_co2_mol_per_kg_solution,relative_residual," + "matched_pure_water_source_co2_mol_per_kg_solution,"
        + "matched_pure_water_model_co2_mol_per_kg_solution,"
        + "source_apparent_salt_effect_kg_water_per_mol_formula_unit,"
        + "model_apparent_salt_effect_kg_water_per_mol_formula_unit,source_salt_direction,"
        + "model_co2_fugacity_bara,matched_pure_water_co2_fugacity_bara,"
        + "model_water_activity,matched_pure_water_water_activity,"
        + "model_molecular_co2_mole_percent,matched_pure_water_model_molecular_co2_mole_percent,"
        + "log_dissolved_co2_molecular_ratio,log_guest_fugacity_ratio,"
        + "model_effective_f_over_x_bara,matched_pure_water_effective_f_over_x_bara," + "log_effective_f_over_x_ratio,"
        + "decomposition_identity_residual,component_balance_residual_mol,"
        + "molecular_log_fugacity_residual,phase_types," + "constrained_model_co2_mol_per_kg_solution,"
        + "constrained_molecular_log_fugacity_residual,"
        + "ablation_model_co2_mol_per_kg_solution,"
        + "ablation_apparent_salt_effect_kg_water_per_mol_formula_unit,"
        + "ablation_log_dissolved_co2_molecular_ratio,ablation_log_guest_fugacity_ratio,"
        + "ablation_log_effective_f_over_x_ratio,ablation_water_activity,"
        + "ablation_component_balance_residual_mol,ablation_molecular_log_fugacity_residual,"
        + "ablation_phase_types,ablation_minus_baseline_model_co2_mol_per_kg_solution");

    int pureCount = 0;
    int cacl2Count = 0;
    int mgcl2Count = 0;
    int constrainedCount = 0;
    int sourceSaltingOutCount = 0;
    int ablationCount = 0;
    for (SourceReference reference : references) {
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result result = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
          .calculate(reference.toModelReference(), CO2_FEED_MOLES);
      double model = modelMolPerKgSolution(result);
      assertTrue(Double.isFinite(model) && model > 0.0, reference.pointId + " finite positive model result");
      assertTrue(result.componentBalanceResidualMol <= 1.0e-10, reference.pointId + " component balance closure");
      assertTrue(Math.abs(result.molecularLogFugacityResidual) < 1.0e-8,
          reference.pointId + " default molecular fugacity closure");

      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result pureWater;
      if (reference.isPureWater()) {
        pureWater = result;
        pureWaterByState.put(reference.stateKey(), result);
        pureCount++;
      } else {
        pureWater = pureWaterByState.get(reference.stateKey());
        assertNotNull(pureWater, reference.pointId + " matched pure-water model state");
        if ("CaCl2".equals(reference.saltType)) {
          cacl2Count++;
        } else {
          assertEquals("MgCl2", reference.saltType);
          mgcl2Count++;
        }
      }
      Double sourcePure = sourcePureByState.get(reference.stateKey());
      assertNotNull(sourcePure, reference.pointId + " matched pure-water source state");
      assertEquals(pureWater.phaseTypes, result.phaseTypes, reference.pointId + " matched pure-water phase set");
      double modelPure = modelMolPerKgSolution(pureWater);
      double logDissolvedCo2MolecularRatio = Math.log(result.molecularMolePercent / pureWater.molecularMolePercent);
      double logGuestFugacityRatio = Math.log(result.co2FugacityBara / pureWater.co2FugacityBara);
      double effectiveFOverXBara = result.effectiveFOverXBara();
      double pureWaterEffectiveFOverXBara = pureWater.effectiveFOverXBara();
      double logEffectiveFOverXRatio = Math.log(effectiveFOverXBara / pureWaterEffectiveFOverXBara);
      double decompositionResidual = result.decompositionIdentityResidual(pureWater);
      assertTrue(Math.abs(decompositionResidual) < 1.0e-12,
          reference.pointId + " dissolved-CO2 decomposition identity");

      double sourceSaltEffect = Double.NaN;
      double modelSaltEffect = Double.NaN;
      String sourceSaltDirection = "PURE_WATER";
      if (!reference.isPureWater()) {
        double saltMoles = saltFormulaMoles(result, reference.saltType);
        assertTrue(saltMoles > 0.0, reference.pointId + " positive salt formula inventory");
        sourceSaltEffect = Math.log(sourcePure.doubleValue() / reference.sourceCo2MolPerKgSolution) / saltMoles;
        modelSaltEffect = Math.log(modelPure / model) / saltMoles;
        sourceSaltDirection = sourceSaltEffect > 0.0 ? "SALTING_OUT" : "SALTING_IN_OR_EQUAL";
        if (sourceSaltEffect > 0.0) {
          sourceSaltingOutCount++;
        }
      }

      double constrainedModel = Double.NaN;
      double constrainedResidual = Double.NaN;
      if (!reference.isPureWater() && reference.salinityPpm == 15000
          && Math.abs(reference.pressureAtm - 170.1) < 1.0e-12) {
        KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result constrained = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
            .calculate(reference.toModelReference(), CO2_FEED_MOLES, true);
        constrainedModel = modelMolPerKgSolution(constrained);
        constrainedResidual = constrained.molecularLogFugacityResidual;
        assertTrue(constrainedResidual < 1.0e-8, reference.pointId + " constrained molecular fugacity closure");
        constrainedCount++;
      }

      double ablationModel = Double.NaN;
      double ablationSaltEffect = Double.NaN;
      double ablationLogDissolvedRatio = Double.NaN;
      double ablationLogGuestFugacityRatio = Double.NaN;
      double ablationLogEffectiveFOverXRatio = Double.NaN;
      double ablationWaterActivity = Double.NaN;
      double ablationComponentResidual = Double.NaN;
      double ablationFugacityResidual = Double.NaN;
      String ablationPhaseTypes = "";
      double ablationMinusBaselineModel = Double.NaN;
      if (isAblationState(reference)) {
        KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result ablated =
            KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
                .calculateWithoutFurstCo2IonInteractions(reference.toModelReference(), CO2_FEED_MOLES);
        ablationModel = modelMolPerKgSolution(ablated);
        double saltMoles = saltFormulaMoles(ablated, reference.saltType);
        ablationSaltEffect = Math.log(modelPure / ablationModel) / saltMoles;
        ablationLogDissolvedRatio = Math.log(ablated.molecularMolePercent / pureWater.molecularMolePercent);
        ablationLogGuestFugacityRatio = Math.log(ablated.co2FugacityBara / pureWater.co2FugacityBara);
        ablationLogEffectiveFOverXRatio =
            Math.log(ablated.effectiveFOverXBara() / pureWaterEffectiveFOverXBara);
        ablationWaterActivity = ablated.waterActivity;
        ablationComponentResidual = ablated.componentBalanceResidualMol;
        ablationFugacityResidual = ablated.molecularLogFugacityResidual;
        ablationPhaseTypes = ablated.phaseTypes;
        ablationMinusBaselineModel = ablationModel - model;
        assertTrue(ablationComponentResidual <= 1.0e-10,
            reference.pointId + " ablation component balance closure");
        assertTrue(ablationFugacityResidual < 1.0e-8,
            reference.pointId + " ablation molecular fugacity closure");
        assertEquals(result.phaseTypes, ablationPhaseTypes, reference.pointId + " ablation phase set");
        ablationCount++;
      }

      report.add(String.format(Locale.ROOT,
          "%s,%s,%s,%d,%.8f,%.1f,%.1f,%.8f,%.12g,%.12g,%.12g,%.12g,%.12g,"
              + "%.12g,%.12g,%s,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,"
              + "%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%s,%.12g,%.12g,"
              + "%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%s,%.12g",
          reference.pointId, reference.saltType, reference.isPureWater() ? "REFERENCE" : "IDENTIFIABILITY_SCREEN",
          reference.salinityPpm, reference.saltMassFractionPercent, reference.temperatureK, reference.pressureAtm,
          reference.totalPressureMpa, reference.sourceCo2MolPerKgSolution, model,
          (model - reference.sourceCo2MolPerKgSolution) / reference.sourceCo2MolPerKgSolution, sourcePure.doubleValue(),
          modelPure, sourceSaltEffect, modelSaltEffect, sourceSaltDirection, result.co2FugacityBara,
          pureWater.co2FugacityBara, result.waterActivity, pureWater.waterActivity, result.molecularMolePercent,
          pureWater.molecularMolePercent, logDissolvedCo2MolecularRatio, logGuestFugacityRatio, effectiveFOverXBara,
          pureWaterEffectiveFOverXBara, logEffectiveFOverXRatio, decompositionResidual,
          result.componentBalanceResidualMol, result.molecularLogFugacityResidual, result.phaseTypes, constrainedModel,
          constrainedResidual, ablationModel, ablationSaltEffect, ablationLogDissolvedRatio,
          ablationLogGuestFugacityRatio, ablationLogEffectiveFOverXRatio, ablationWaterActivity,
          ablationComponentResidual, ablationFugacityResidual, ablationPhaseTypes, ablationMinusBaselineModel));
    }

    assertEquals(84, references.size());
    assertEquals(12, pureCount);
    assertEquals(36, cacl2Count);
    assertEquals(36, mgcl2Count);
    assertEquals(8, constrainedCount);
    assertEquals(16, ablationCount);
    assertEquals(85, report.size());
    assertEquals(65, sourceSaltingOutCount,
        "retain the seven source rows that contradict the aggregate salting-out trend");

    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), report, StandardCharsets.UTF_8);
    for (String row : report) {
      logger.info("CO2_DIVALENT_LIU2021_DIAGNOSTIC,{}", row);
    }
  }

  /**
   * Converts one model result to the source mol-per-kilogram-of-solution observable.
   *
   * @param result model result on a final one-kilogram aqueous-water basis
   * @return dissolved CO2 moles per kilogram of aqueous solution
   */
  private static double modelMolPerKgSolution(KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result result) {
    double saltMassKg = result.cacl2Moles * CACL2_MOLAR_MASS_KG_PER_MOL
        + result.mgcl2Moles * MGCL2_MOLAR_MASS_KG_PER_MOL;
    double solutionMassKg = result.waterMassKg + saltMassKg + result.co2Moles * CO2_MOLAR_MASS_KG_PER_MOL;
    return result.co2Moles / solutionMassKg;
  }

  /**
   * Returns the salt formula-unit inventory for the selected source family.
   *
   * @param result model result
   * @param saltType source salt label
   * @return salt formula-unit moles
   */
  private static double saltFormulaMoles(KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result result,
      String saltType) {
    if ("CaCl2".equals(saltType)) {
      return result.cacl2Moles;
    }
    if ("MgCl2".equals(saltType)) {
      return result.mgcl2Moles;
    }
    return 0.0;
  }

  /**
   * Selects the predeclared layer-ablation matrix: both divalent salts at 15 000 ppm,
   * the lowest and highest source pressures, and all four source temperatures.
   *
   * @param reference source state
   * @return whether the state belongs to the ablation matrix
   */
  private static boolean isAblationState(SourceReference reference) {
    return !reference.isPureWater() && reference.salinityPpm == 15000
        && (Math.abs(reference.pressureAtm - 20.4) < 1.0e-12
            || Math.abs(reference.pressureAtm - 170.1) < 1.0e-12);
  }

  /**
   * Loads the predeclared source subset.
   *
   * @return source references in fixture order
   * @throws Exception if the fixture cannot be read or parsed
   */
  private static List<SourceReference> loadReferences() throws Exception {
    InputStream stream = LowSalinityDivalentBrineCO2SolubilityLiu2021AssessmentTest.class.getResourceAsStream(RESOURCE);
    assertNotNull(stream, RESOURCE);
    List<SourceReference> references = new ArrayList<SourceReference>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String header = reader.readLine();
      assertEquals("point_id,source_file,salt_type,salinity_ppm,salt_mass_fraction_percent,"
          + "temperature_K,pressure_atm,total_pressure_MPa," + "source_co2_mol_per_kg_solution", header);
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.trim().isEmpty()) {
          continue;
        }
        String[] fields = line.split(",", -1);
        assertEquals(9, fields.length, line);
        assertEquals("energies-1349243-supplementary.csv", fields[1], line);
        references.add(new SourceReference(fields[0], fields[2], Integer.parseInt(fields[3]),
            Double.parseDouble(fields[4]), Double.parseDouble(fields[5]), Double.parseDouble(fields[6]),
            Double.parseDouble(fields[7]), Double.parseDouble(fields[8])));
      }
    }
    assertEquals(84, references.size());
    return references;
  }

  /** Source row and model-state mapping. */
  private static final class SourceReference {
    private final String pointId;
    private final String saltType;
    private final int salinityPpm;
    private final double saltMassFractionPercent;
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
     * @param saltMassFractionPercent salt mass percent in the source brine
     * @param temperatureK source temperature in K
     * @param pressureAtm source total pressure in atm
     * @param totalPressureMpa source total pressure converted to MPa
     * @param sourceCo2MolPerKgSolution source dissolved CO2 in mol/kg solution
     */
    private SourceReference(String pointId, String saltType, int salinityPpm, double saltMassFractionPercent,
        double temperatureK, double pressureAtm, double totalPressureMpa, double sourceCo2MolPerKgSolution) {
      this.pointId = pointId;
      this.saltType = saltType;
      this.salinityPpm = salinityPpm;
      this.saltMassFractionPercent = saltMassFractionPercent;
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
      double cacl2MassPercent = "CaCl2".equals(saltType) ? saltMassFractionPercent : 0.0;
      double mgcl2MassPercent = "MgCl2".equals(saltType) ? saltMassFractionPercent : 0.0;
      return new KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference(pointId, "Liu2021", 0.0, 0.0,
          cacl2MassPercent, mgcl2MassPercent, temperatureK, totalPressureMpa, 0.0, 0.0);
    }

    /**
     * Tests whether this is a distilled-water reference row.
     *
     * @return true for the salt-free reference
     */
    private boolean isPureWater() {
      return salinityPpm == 0;
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
