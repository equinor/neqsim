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
import neqsim.thermo.mixingrule.ElectrolyteMixingRulesInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.CO2BrinePhaseEquilibrium;

/**
 * No-fit CO2-in-KCl diagnostic from the explicit salt-free Mousavi-Chapoy-Burgass measurements.
 *
 * @author OpenAI
 * @version 1.0
 */
class KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest {
  private static final Logger logger = LogManager
      .getLogger(KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.class);
  private static final String RESOURCE = "/data/chemistry_benchmarks/co2_kcl_solubility_mousavi_chapoy_burgass2024.csv";
  private static final String OUTPUT = "target/co2-kcl-solubility-mousavi-chapoy-burgass2024-assessment.csv";
  private static final double WATER_MOLAR_MASS_KG_PER_MOL = 0.01801528;
  private static final double NACL_MOLAR_MASS_KG_PER_MOL = 0.05844277;
  private static final double KCL_MOLAR_MASS_KG_PER_MOL = 0.0745513;
  private static final double CACL2_MOLAR_MASS_KG_PER_MOL = 0.110984;
  private static final double MGCL2_MOLAR_MASS_KG_PER_MOL = 0.095211;
  private static final double CO2_FEED_MOLES = 10.0;
  private static final int MAX_RESERVOIR_RETRIES = 5;
  private static final double RESERVOIR_RETRY_FACTOR = 1.1;
  private static final double AQUEOUS_WATER_MASS_TOLERANCE_KG = 1.0e-10;
  private static final double SALT_MASS_PERCENT_CLOSURE_TOLERANCE = 2.0e-9;

  /**
   * Reports the source-defined salt-free mole fraction and representation controls without fitting.
   *
   * @throws Exception if the reference fixture or generated diagnostic cannot be read or written
   */
  @Tag("slow")
  @Test
  void explicitSaltFreeKclMeasurementsReportWithoutFitting() throws Exception {
    List<Reference> references = loadReferences();
    List<String> report = new ArrayList<String>();
    report.add("point_id,source_table,nacl_mass_fraction_percent,kcl_mass_fraction_percent,"
        + "cacl2_mass_fraction_percent,temperature_K,total_pressure_MPa,"
        + "source_co2_mole_percent_salt_free,source_uncertainty_percentage_points,"
        + "actual_total_pressure_bara,actual_co2_partial_pressure_bara,"
        + "model_co2_mole_percent_including_ions,residual_including_ions_percentage_points,"
        + "model_co2_mole_percent_molecular_only,residual_molecular_only_percentage_points,"
        + "model_co2_mole_percent_pseudo_salt,residual_pseudo_salt_percentage_points,"
        + "model_co2_molality_mol_per_kg_water,water_activity_proxy,actual_nacl_mass_fraction_percent,"
        + "actual_kcl_mass_fraction_percent,actual_cacl2_mass_fraction_percent,aqueous_water_mass_kg,"
        + "aqueous_co2_moles,nacl_formula_moles,kcl_formula_moles,cacl2_formula_moles,co2_feed_moles,fluid_phases,"
        + "component_balance_residual_mol,molecular_log_fugacity_residual,"
        + "constrained_fluid_phases,constrained_model_co2_mole_percent_molecular_only,"
        + "constrained_minus_default_molecular_percentage_points,constrained_water_activity_proxy,"
        + "constrained_minus_default_water_activity,constrained_molecular_log_fugacity_residual,"
        + "matched_pure_water_co2_mole_percent,brine_to_pure_water_co2_ratio,brine_co2_fugacity_bara,"
        + "pure_water_co2_fugacity_bara,co2_fugacity_ratio,brine_effective_f_over_x_bara,"
        + "pure_water_effective_f_over_x_bara,effective_f_over_x_ratio,log_dissolved_co2_ratio,"
        + "log_co2_fugacity_ratio,log_effective_f_over_x_ratio,decomposition_identity_residual,"
        + "matched_pure_water_water_activity,brine_minus_pure_water_water_activity,"
        + "pure_water_component_balance_residual_mol,pure_water_molecular_log_fugacity_residual");
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), report, StandardCharsets.UTF_8);

    for (Reference reference : references) {
      Result result = calculate(reference, CO2_FEED_MOLES);
      Result constrained = calculate(reference, CO2_FEED_MOLES, true);
      Result pureWater = calculate(reference.withoutSalt(), result.co2FeedMoles);
      assertEquals(result.co2FeedMoles, pureWater.co2FeedMoles, 0.0,
          reference.pointId + " matched pure-water CO2 reservoir");
      assertTrue(pureWater.componentBalanceResidualMol <= 1.0e-10,
          reference.pointId + " matched pure-water component balance");
      assertTrue(pureWater.molecularLogFugacityResidual < 1.0e-8,
          reference.pointId + " matched pure-water molecular fugacity closure");
      assertTrue(Math.abs(result.decompositionIdentityResidual(pureWater)) < 1.0e-12,
          reference.pointId + " dissolved-CO2 decomposition identity");
      String row = result.toCsv(reference, constrained, pureWater);
      report.add(row);
      Files.write(Paths.get(OUTPUT), Collections.singletonList(row), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
      if ("MCB-KCL15-373-P281".equals(reference.pointId)) {
        // The preliminary stability check previously led to a third phase with unequal fugacities.
        assertEquals("GAS+AQUEOUS", result.phaseTypes, reference.pointId + " phase split");
        assertTrue(result.molecularLogFugacityResidual < 1.0e-8, reference.pointId + " molecular fugacity closure");
      }
      assertTrue(constrained.molecularLogFugacityResidual < 1.0e-8,
          reference.pointId + " constrained molecular fugacity closure");
      if ("MCB-KCL10-323-P400".equals(reference.pointId) || "MCB-KCL10-323-P538".equals(reference.pointId)
          || "MCB-KCL15-323-P538".equals(reference.pointId)) {
        assertTrue(result.componentBalanceResidualMol <= 1.0e-10, reference.pointId + " default component balance");
        assertTrue(result.molecularLogFugacityResidual < 1.0e-8,
            reference.pointId + " default molecular fugacity closure");
        assertEquals(constrained.phaseTypes, result.phaseTypes, reference.pointId + " phase identity");
        assertEquals(constrained.molecularMolePercent, result.molecularMolePercent, 1.0e-8,
            reference.pointId + " default/constrained dissolved CO2");
        assertEquals(constrained.waterActivity, result.waterActivity, 1.0e-10,
            reference.pointId + " default/constrained water activity");
      }
    }

    assertEquals(26, references.size());
    assertEquals(27, report.size());
    for (String row : report) {
      logger.info("CO2_KCL_MOUSAVI_CHAPOY_BURGASS_DIAGNOSTIC,{}", row);
    }
  }

  /**
   * Checks that the finite gas reservoir does not control a concentrated high-pressure result.
   */
  @Tag("slow")
  @Test
  void concentratedHighPressureStateIsIndependentOfGasInventory() {
    Reference reference = new Reference("MCB-KCL22-423-P497", "A13", 0.0, 22.0, 0.0, 423.15, 49.724, 1.88, 0.01);
    Result ordinary = calculate(reference, CO2_FEED_MOLES);
    Result smallerReservoir = calculate(reference, 0.5 * CO2_FEED_MOLES);
    assertEquals(ordinary.co2Molality, smallerReservoir.co2Molality, 1.0e-10);
    assertEquals(ordinary.co2PartialPressureBara, smallerReservoir.co2PartialPressureBara, 1.0e-9);
  }

  /**
   * Calculates one source state at reported total pressure and one final aqueous kilogram of water.
   *
   * @param reference source state
   * @param co2FeedMoles finite CO2 reservoir in mol, exceeding the dissolved inventory
   * @return calculated diagnostics
   */
  static Result calculate(Reference reference, double co2FeedMoles) {
    return calculate(reference, co2FeedMoles, false, false);
  }

  /**
   * Calculates one source state after removing only the Furst CO2-ion interaction terms.
   *
   * <p>This diagnostic preserves the EOS, association, CPA binary interactions, phase solver, and
   * all non-CO2 electrolyte terms. It is an ablation, not a fitted parameterization.
   *
   * @param reference source state
   * @param co2FeedMoles finite CO2 reservoir in mol, exceeding the dissolved inventory
   * @return calculated diagnostics
   */
  static Result calculateWithoutFurstCo2IonInteractions(Reference reference, double co2FeedMoles) {
    return calculate(reference, co2FeedMoles, false, true);
  }

  /**
   * Calculates one source state with either the default or constrained CO2/brine phase solver.
   *
   * @param reference source state
   * @param co2FeedMoles finite CO2 reservoir in mol, exceeding the dissolved inventory
   * @param constrainedPhaseSolver whether to compare the explicit GAS/OIL root candidates
   * @return calculated diagnostics
   */
  static Result calculate(Reference reference, double co2FeedMoles, boolean constrainedPhaseSolver) {
    return calculate(reference, co2FeedMoles, constrainedPhaseSolver, false);
  }

  /**
   * Calculates one source state under the requested numerical solver and interaction ablation.
   *
   * @param reference source state
   * @param co2FeedMoles finite CO2 reservoir in mol, exceeding the dissolved inventory
   * @param constrainedPhaseSolver whether to compare the explicit GAS/OIL root candidates
   * @param ablateFurstCo2IonInteractions whether to zero only CO2-ion Furst W0/W1/W2 terms
   * @return calculated diagnostics
   */
  private static Result calculate(Reference reference, double co2FeedMoles, boolean constrainedPhaseSolver,
      boolean ablateFurstCo2IonInteractions) {
    double saltFreeMassPercent = 100.0 - reference.totalSaltMassFractionPercent();
    double naclMoles = reference.naclMolalityMolPerKgWater;
    double kclMoles = reference.kclMolalityMolPerKgWater;
    double cacl2Moles = reference.cacl2MolalityMolPerKgWater;
    double mgcl2Moles = reference.mgcl2MolalityMolPerKgWater;
    if (!reference.hasExplicitMolalities()) {
      naclMoles = reference.naclMassFractionPercent / saltFreeMassPercent / NACL_MOLAR_MASS_KG_PER_MOL;
      kclMoles = reference.kclMassFractionPercent / saltFreeMassPercent / KCL_MOLAR_MASS_KG_PER_MOL;
      cacl2Moles = reference.cacl2MassFractionPercent / saltFreeMassPercent / CACL2_MOLAR_MASS_KG_PER_MOL;
      mgcl2Moles = reference.mgcl2MassFractionPercent / saltFreeMassPercent / MGCL2_MOLAR_MASS_KG_PER_MOL;
    }
    double totalPressureBara = 10.0 * reference.totalPressureMpa;
    Trial trial = flashWithAqueousWaterBasis(reference, totalPressureBara, co2FeedMoles, naclMoles, kclMoles,
        cacl2Moles, mgcl2Moles, constrainedPhaseSolver, ablateFurstCo2IonInteractions);
    PhaseInterface aqueous = trial.aqueous;

    double waterMoles = aqueous.getComponent("water").getNumberOfMolesInPhase();
    double waterMassKg = waterMoles * WATER_MOLAR_MASS_KG_PER_MOL;
    double co2Moles = aqueous.getComponent("CO2").getNumberOfMolesInPhase();
    double actualNaclMoles = componentMoles(aqueous, "Na+");
    double actualKclMoles = componentMoles(aqueous, "K+");
    double actualCacl2Moles = componentMoles(aqueous, "Ca++");
    double actualMgcl2Moles = componentMoles(aqueous, "Mg++");
    double solutionMassKg = waterMassKg + actualNaclMoles * NACL_MOLAR_MASS_KG_PER_MOL
        + actualKclMoles * KCL_MOLAR_MASS_KG_PER_MOL + actualCacl2Moles * CACL2_MOLAR_MASS_KG_PER_MOL
        + actualMgcl2Moles * MGCL2_MOLAR_MASS_KG_PER_MOL;
    double actualNaclMassPercent = 100.0 * actualNaclMoles * NACL_MOLAR_MASS_KG_PER_MOL / solutionMassKg;
    double actualKclMassPercent = 100.0 * actualKclMoles * KCL_MOLAR_MASS_KG_PER_MOL / solutionMassKg;
    double actualCacl2MassPercent = 100.0 * actualCacl2Moles * CACL2_MOLAR_MASS_KG_PER_MOL / solutionMassKg;
    double actualMgcl2MassPercent = 100.0 * actualMgcl2Moles * MGCL2_MOLAR_MASS_KG_PER_MOL / solutionMassKg;
    double nativeMolePercent = 100.0 * aqueous.getComponent("CO2").getx();
    double molecularMolePercent = 100.0 * co2Moles / (co2Moles + waterMoles);
    double pseudoSaltMolePercent = 100.0 * co2Moles
        / (co2Moles + waterMoles + actualNaclMoles + actualKclMoles + actualCacl2Moles + actualMgcl2Moles);
    double co2Molality = co2Moles / waterMassKg;
    int waterIndex = aqueous.getComponent("water").getComponentNumber();
    double waterActivity = aqueous.getComponent("water").getx() * aqueous.getActivityCoefficient(waterIndex);

    assertEquals(1.0, waterMassKg, AQUEOUS_WATER_MASS_TOLERANCE_KG, reference.pointId + " aqueous-water basis");
    assertEquals(naclMoles, actualNaclMoles, 1.0e-12, reference.pointId + " sodium inventory");
    assertEquals(kclMoles, actualKclMoles, 1.0e-12, reference.pointId + " potassium inventory");
    assertEquals(cacl2Moles, actualCacl2Moles, 1.0e-12, reference.pointId + " calcium inventory");
    assertEquals(mgcl2Moles, actualMgcl2Moles, 1.0e-12, reference.pointId + " magnesium inventory");
    assertEquals(naclMoles + kclMoles + 2.0 * cacl2Moles + 2.0 * mgcl2Moles, componentMoles(aqueous, "Cl-"), 1.0e-12,
        reference.pointId + " chloride inventory");
    assertEquals(reference.naclMassFractionPercent, actualNaclMassPercent, SALT_MASS_PERCENT_CLOSURE_TOLERANCE,
        reference.pointId + " NaCl mass fraction");
    assertEquals(reference.kclMassFractionPercent, actualKclMassPercent, SALT_MASS_PERCENT_CLOSURE_TOLERANCE,
        reference.pointId + " KCl mass fraction");
    assertEquals(reference.cacl2MassFractionPercent, actualCacl2MassPercent, SALT_MASS_PERCENT_CLOSURE_TOLERANCE,
        reference.pointId + " CaCl2 mass fraction");
    assertEquals(reference.mgcl2MassFractionPercent, actualMgcl2MassPercent, SALT_MASS_PERCENT_CLOSURE_TOLERANCE,
        reference.pointId + " MgCl2 mass fraction");
    for (double value : new double[] {trial.co2PartialPressureBara, trial.co2FugacityBara, nativeMolePercent,
        molecularMolePercent, pseudoSaltMolePercent, co2Molality, waterActivity}) {
      assertTrue(Double.isFinite(value) && value > 0.0, reference.pointId + " finite diagnostic");
    }
    assertTrue(waterActivity <= 1.0, reference.pointId + " water activity range");

    return new Result(trial.co2PartialPressureBara, trial.co2FugacityBara, nativeMolePercent, molecularMolePercent,
        pseudoSaltMolePercent, co2Molality, waterActivity, actualNaclMassPercent, actualKclMassPercent,
        actualCacl2MassPercent, actualMgcl2MassPercent, waterMassKg, co2Moles, actualNaclMoles, actualKclMoles,
        actualCacl2Moles, actualMgcl2Moles, trial.co2FeedMoles, trial.phaseTypes, trial.componentBalanceResidualMol,
        trial.molecularLogFugacityResidual);
  }

  /**
   * Flashes a source pressure while compensating evaporated water.
   *
   * @param reference source state
   * @param totalPressureBara source total pressure in bara
   * @param co2FeedMoles finite CO2 reservoir in mol
   * @param naclMoles NaCl formula-unit inventory in mol
   * @param kclMoles KCl formula-unit inventory in mol
   * @param cacl2Moles CaCl2 formula-unit inventory in mol
   * @param mgcl2Moles MgCl2 formula-unit inventory in mol
   * @param constrainedPhaseSolver whether to compare the explicit GAS/OIL root candidates
   * @param ablateFurstCo2IonInteractions whether to zero only CO2-ion Furst W0/W1/W2 terms
   * @return converged and validated state
   */
  private static Trial flashWithAqueousWaterBasis(Reference reference, double totalPressureBara, double co2FeedMoles,
      double naclMoles, double kclMoles, double cacl2Moles, double mgcl2Moles, boolean constrainedPhaseSolver,
      boolean ablateFurstCo2IonInteractions) {
    double waterFeedMoles = 1.0 / WATER_MOLAR_MASS_KG_PER_MOL;
    double co2Feed = co2FeedMoles;
    for (int iteration = 0; iteration < 20; iteration++) {
      // The flash can return a spurious single phase for a feed that is a tiny step from a feed that splits
      // correctly (for example 423.15 K, 385.31 bar, 15 wt% KCl). The saturated aqueous composition does not
      // depend on the reservoir size, so retry with a larger CO2 reservoir; the feed used is reported.
      SystemInterface fluid = null;
      for (int attempt = 0; attempt <= MAX_RESERVOIR_RETRIES; attempt++) {
        fluid = flashSystem(reference.temperatureK, totalPressureBara, co2Feed, waterFeedMoles, naclMoles, kclMoles,
            cacl2Moles, mgcl2Moles, constrainedPhaseSolver, ablateFurstCo2IonInteractions);
        if (fluid.getNumberOfPhases() >= 2) {
          break;
        }
        co2Feed *= RESERVOIR_RETRY_FACTOR;
      }

      assertTrue(fluid.getNumberOfPhases() >= 2, reference.pointId + " requires an aqueous and a non-aqueous phase");
      PhaseInterface aqueous = findAqueousPhase(fluid);
      List<PhaseInterface> nonAqueous = nonAqueousPhases(fluid, aqueous);
      PhaseInterface gas = mostCo2RichPhase(nonAqueous);
      double componentBalanceResidualMol = 0.0;
      double molecularLogFugacityResidual = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        String componentName = aqueous.getComponent(component).getComponentName();
        double feedMoles = feedMoles(componentName, co2Feed, waterFeedMoles, naclMoles, kclMoles, cacl2Moles,
            mgcl2Moles);
        double aqueousMoles = aqueous.getComponent(component).getNumberOfMolesInPhase();
        double nonAqueousMoles = 0.0;
        for (PhaseInterface phase : nonAqueous) {
          double phaseMoles = phase.getComponent(component).getNumberOfMolesInPhase();
          nonAqueousMoles += phaseMoles;
          if (phase.getComponent(component).getIonicCharge() == 0) {
            double fugacityRatio = phase.getFugacity(component) / aqueous.getFugacity(component);
            molecularLogFugacityResidual = Math.max(molecularLogFugacityResidual, Math.abs(Math.log(fugacityRatio)));
          } else {
            assertEquals(0.0, phaseMoles, 1.0e-40, reference.pointId + " ions must remain aqueous");
          }
        }
        componentBalanceResidualMol = Math.max(componentBalanceResidualMol,
            Math.abs(nonAqueousMoles + aqueousMoles - feedMoles));
      }
      assertTrue(componentBalanceResidualMol < 1.0e-10,
          reference.pointId + " component inventory residual=" + componentBalanceResidualMol);
      // Reported, not gated: the molecular fugacity mismatch is itself a model diagnostic at high pressure.
      assertTrue(Double.isFinite(molecularLogFugacityResidual), reference.pointId + " finite fugacity residual");

      double aqueousWaterMoles = aqueous.getComponent("water").getNumberOfMolesInPhase();
      double waterCorrectionMoles = 1.0 / WATER_MOLAR_MASS_KG_PER_MOL - aqueousWaterMoles;
      if (Math.abs(waterCorrectionMoles) * WATER_MOLAR_MASS_KG_PER_MOL < AQUEOUS_WATER_MASS_TOLERANCE_KG) {
        List<String> phases = new ArrayList<String>();
        for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
          phases.add(fluid.getPhase(phaseIndex).getType().toString());
        }
        double co2PartialPressureBara = gas.getComponent("CO2").getx() * fluid.getPressure();
        double co2FugacityBara = gas.getFugacity("CO2");
        return new Trial(aqueous, co2PartialPressureBara, co2FugacityBara, String.join("+", phases),
            componentBalanceResidualMol, molecularLogFugacityResidual, co2Feed);
      }
      waterFeedMoles += waterCorrectionMoles;
    }
    throw new AssertionError(reference.pointId + " aqueous-water inventory iteration did not converge");
  }

  /**
   * Builds and flashes one electrolyte system.
   *
   * @param temperatureK temperature in K
   * @param pressureBara pressure in bara
   * @param co2Moles CO2 inventory in mol
   * @param waterMoles water inventory in mol
   * @param naclMoles NaCl formula-unit inventory in mol
   * @param kclMoles KCl formula-unit inventory in mol
   * @param cacl2Moles CaCl2 formula-unit inventory in mol
   * @param mgcl2Moles MgCl2 formula-unit inventory in mol
   * @param constrainedPhaseSolver whether to compare the explicit GAS/OIL root candidates
   * @param ablateFurstCo2IonInteractions whether to zero only CO2-ion Furst W0/W1/W2 terms
   * @return flashed system
   */
  private static SystemInterface flashSystem(double temperatureK, double pressureBara, double co2Moles,
      double waterMoles, double naclMoles, double kclMoles, double cacl2Moles, double mgcl2Moles,
      boolean constrainedPhaseSolver, boolean ablateFurstCo2IonInteractions) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(temperatureK, pressureBara);
    fluid.addComponent("CO2", co2Moles);
    fluid.addComponent("water", waterMoles);
    if (naclMoles > 0.0) {
      fluid.addComponent("Na+", naclMoles);
    }
    if (kclMoles > 0.0) {
      fluid.addComponent("K+", kclMoles);
    }
    if (cacl2Moles > 0.0) {
      fluid.addComponent("Ca++", cacl2Moles);
    }
    if (mgcl2Moles > 0.0) {
      fluid.addComponent("Mg++", mgcl2Moles);
    }
    double chlorideMoles = naclMoles + kclMoles + 2.0 * cacl2Moles + 2.0 * mgcl2Moles;
    if (chlorideMoles > 0.0) {
      fluid.addComponent("Cl-", chlorideMoles);
    }
    fluid.setMixingRule(10);
    if (ablateFurstCo2IonInteractions) {
      ablateFurstCo2IonInteractions(fluid);
    }
    fluid.setMultiPhaseCheck(true);
    if (constrainedPhaseSolver) {
      new CO2BrinePhaseEquilibrium(fluid).run();
    } else {
      new ThermodynamicOperations(fluid).TPflash();
    }
    fluid.init(1);
    return fluid;
  }

  /**
   * Zeros only the Furst CO2-ion W0/W1/W2 interactions in every phase.
   *
   * <p>Ion-specific attribution remains gauge-dependent; therefore this diagnostic removes the
   * complete CO2-ion layer as a block and does not interpret individual cation or chloride terms.
   *
   * @param fluid initialized electrolyte system
   */
  private static void ablateFurstCo2IonInteractions(SystemInterface fluid) {
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      if (!(phase instanceof PhaseModifiedFurstElectrolyteEos)) {
        throw new AssertionError("mixing rule 10 did not create a modified Furst electrolyte phase");
      }
      ElectrolyteMixingRulesInterface rule =
          ((PhaseModifiedFurstElectrolyteEos) phase).getElectrolyteMixingRule();
      int co2Index = phase.getComponent("CO2").getComponentNumber();
      for (String ion : new String[] {"Na+", "K+", "Ca++", "Mg++", "Cl-"}) {
        if (phase.hasComponent(ion)) {
          int ionIndex = phase.getComponent(ion).getComponentNumber();
          rule.setWijParameter(co2Index, ionIndex, 0.0);
          rule.setWijT1Parameter(co2Index, ionIndex, 0.0);
          rule.setWijT2Parameter(co2Index, ionIndex, 0.0);
        }
      }
    }
  }

  /**
   * Maps a component name to the corresponding feed inventory.
   *
   * @param componentName NeqSim component name
   * @param co2FeedMoles CO2 inventory in mol
   * @param waterFeedMoles water inventory in mol
   * @param naclMoles NaCl formula-unit inventory in mol
   * @param kclMoles KCl formula-unit inventory in mol
   * @param cacl2Moles CaCl2 formula-unit inventory in mol
   * @param mgcl2Moles MgCl2 formula-unit inventory in mol
   * @return component feed inventory in mol
   */
  private static double feedMoles(String componentName, double co2FeedMoles, double waterFeedMoles, double naclMoles,
      double kclMoles, double cacl2Moles, double mgcl2Moles) {
    if ("CO2".equals(componentName)) {
      return co2FeedMoles;
    }
    if ("water".equals(componentName)) {
      return waterFeedMoles;
    }
    if ("Na+".equals(componentName)) {
      return naclMoles;
    }
    if ("K+".equals(componentName)) {
      return kclMoles;
    }
    if ("Ca++".equals(componentName)) {
      return cacl2Moles;
    }
    if ("Mg++".equals(componentName)) {
      return mgcl2Moles;
    }
    if ("Cl-".equals(componentName)) {
      return naclMoles + kclMoles + 2.0 * cacl2Moles + 2.0 * mgcl2Moles;
    }
    throw new IllegalArgumentException("Unexpected benchmark component " + componentName);
  }

  /**
   * Returns component moles when present, otherwise zero.
   *
   * @param phase phase to inspect
   * @param componentName component name
   * @return component inventory in mol
   */
  private static double componentMoles(PhaseInterface phase, String componentName) {
    return phase.hasComponent(componentName) ? phase.getComponent(componentName).getNumberOfMolesInPhase() : 0.0;
  }

  /**
   * Returns every phase other than the aqueous one. NeqSim may label the CO2-rich phase as oil or liquid rather than
   * gas at high pressure, and a third phase can appear, so phases are identified by position instead of by type label.
   *
   * @param fluid flashed system
   * @param aqueous ion-rich aqueous phase
   * @return non-aqueous phases, never empty
   */
  private static List<PhaseInterface> nonAqueousPhases(SystemInterface fluid, PhaseInterface aqueous) {
    List<PhaseInterface> phases = new ArrayList<PhaseInterface>();
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface candidate = fluid.getPhase(phaseIndex);
      if (candidate != aqueous) {
        phases.add(candidate);
      }
    }
    if (phases.isEmpty()) {
      throw new AssertionError("no non-aqueous phase found");
    }
    return phases;
  }

  /**
   * Selects the non-aqueous phase with the greatest CO2 mole fraction, used for the partial pressure.
   *
   * @param phases non-aqueous phases, never empty
   * @return most CO2-rich phase
   */
  private static PhaseInterface mostCo2RichPhase(List<PhaseInterface> phases) {
    PhaseInterface selected = phases.get(0);
    for (PhaseInterface candidate : phases) {
      if (candidate.getComponent("CO2").getx() > selected.getComponent("CO2").getx()) {
        selected = candidate;
      }
    }
    return selected;
  }

  /**
   * Selects the aqueous phase by type, with ionic content as a defensive fallback.
   *
   * @param fluid flashed system
   * @return aqueous phase
   */
  private static PhaseInterface findAqueousPhase(SystemInterface fluid) {
    PhaseInterface selected = null;
    double largestIonicFraction = -1.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface candidate = fluid.getPhase(phaseIndex);
      if (candidate.getType() == PhaseType.AQUEOUS) {
        return candidate;
      }
      double ionicFraction = 0.0;
      for (int component = 0; component < candidate.getNumberOfComponents(); component++) {
        if (candidate.getComponent(component).getIonicCharge() != 0) {
          ionicFraction += candidate.getComponent(component).getx();
        }
      }
      if (ionicFraction > largestIonicFraction) {
        selected = candidate;
        largestIonicFraction = ionicFraction;
      }
    }
    assertNotNull(selected);
    return selected;
  }

  /**
   * Loads the twenty-six source-pinned states.
   *
   * @return source states
   * @throws Exception if the fixture cannot be read or parsed
   */
  private static List<Reference> loadReferences() throws Exception {
    InputStream stream = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.class.getResourceAsStream(RESOURCE);
    assertNotNull(stream, RESOURCE);
    List<Reference> references = new ArrayList<Reference>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("point_id") || line.trim().isEmpty()) {
          continue;
        }
        String[] fields = line.split(",");
        references.add(new Reference(fields[0], fields[1], Double.parseDouble(fields[2]), Double.parseDouble(fields[3]),
            Double.parseDouble(fields[4]), Double.parseDouble(fields[5]), Double.parseDouble(fields[6]),
            Double.parseDouble(fields[7]), Double.parseDouble(fields[8])));
      }
    }
    assertEquals(26, references.size());
    return references;
  }

  /**
   * Source state retained on the explicitly reported salt-free aqueous-phase basis.
   *
   * @author OpenAI
   * @version 1.0
   */
  static final class Reference {
    final String pointId;
    final String sourceTable;
    final double naclMassFractionPercent;
    final double kclMassFractionPercent;
    final double cacl2MassFractionPercent;
    final double mgcl2MassFractionPercent;
    final double naclMolalityMolPerKgWater;
    final double kclMolalityMolPerKgWater;
    final double cacl2MolalityMolPerKgWater;
    final double mgcl2MolalityMolPerKgWater;
    final double temperatureK;
    final double totalPressureMpa;
    final double sourceCo2MolePercent;
    final double sourceUncertaintyPercentagePoints;

    /**
     * Creates one source state.
     *
     * @param pointId stable point identifier
     * @param sourceTable source table number
     * @param naclMassFractionPercent NaCl mass fraction in percent
     * @param kclMassFractionPercent KCl mass fraction in percent
     * @param cacl2MassFractionPercent CaCl2 mass fraction in percent
     * @param temperatureK temperature in K
     * @param totalPressureMpa total pressure in MPa
     * @param sourceCo2MolePercent source-reported salt-free CO2 mole percent
     * @param sourceUncertaintyPercentagePoints source-reported uncertainty in percentage points
     */
    Reference(String pointId, String sourceTable, double naclMassFractionPercent, double kclMassFractionPercent,
        double cacl2MassFractionPercent, double temperatureK, double totalPressureMpa, double sourceCo2MolePercent,
        double sourceUncertaintyPercentagePoints) {
      this(pointId, sourceTable, naclMassFractionPercent, kclMassFractionPercent, cacl2MassFractionPercent, 0.0,
          Double.NaN, Double.NaN, Double.NaN, Double.NaN, temperatureK, totalPressureMpa, sourceCo2MolePercent,
          sourceUncertaintyPercentagePoints);
    }

    /**
     * Creates one source state from four rounded anhydrous salt mass fractions.
     *
     * @param pointId stable point identifier
     * @param sourceTable source table number
     * @param naclMassFractionPercent NaCl mass fraction in percent
     * @param kclMassFractionPercent KCl mass fraction in percent
     * @param cacl2MassFractionPercent CaCl2 mass fraction in percent
     * @param mgcl2MassFractionPercent MgCl2 mass fraction in percent
     * @param temperatureK temperature in K
     * @param totalPressureMpa total pressure in MPa
     * @param sourceCo2MolePercent source-reported salt-free CO2 mole percent
     * @param sourceUncertaintyPercentagePoints source-reported uncertainty in percentage points
     */
    Reference(String pointId, String sourceTable, double naclMassFractionPercent, double kclMassFractionPercent,
        double cacl2MassFractionPercent, double mgcl2MassFractionPercent, double temperatureK, double totalPressureMpa,
        double sourceCo2MolePercent, double sourceUncertaintyPercentagePoints) {
      this(pointId, sourceTable, naclMassFractionPercent, kclMassFractionPercent, cacl2MassFractionPercent,
          mgcl2MassFractionPercent, Double.NaN, Double.NaN, Double.NaN, Double.NaN, temperatureK, totalPressureMpa,
          sourceCo2MolePercent, sourceUncertaintyPercentagePoints);
    }

    /**
     * Creates one state with explicit final-aqueous-water molalities.
     *
     * @param pointId stable point identifier
     * @param sourceTable source table number
     * @param naclMassFractionPercent source-equivalent NaCl mass fraction in percent
     * @param kclMassFractionPercent source-equivalent KCl mass fraction in percent
     * @param cacl2MassFractionPercent source-equivalent CaCl2 mass fraction in percent
     * @param mgcl2MassFractionPercent source-equivalent MgCl2 mass fraction in percent
     * @param naclMolalityMolPerKgWater NaCl formula-unit molality in mol/kg final aqueous water
     * @param kclMolalityMolPerKgWater KCl formula-unit molality in mol/kg final aqueous water
     * @param cacl2MolalityMolPerKgWater CaCl2 formula-unit molality in mol/kg final aqueous water
     * @param mgcl2MolalityMolPerKgWater MgCl2 formula-unit molality in mol/kg final aqueous water
     * @param temperatureK temperature in K
     * @param totalPressureMpa total pressure in MPa
     * @param sourceCo2MolePercent source-reported salt-free CO2 mole percent
     * @param sourceUncertaintyPercentagePoints source-reported uncertainty in percentage points
     */
    Reference(String pointId, String sourceTable, double naclMassFractionPercent, double kclMassFractionPercent,
        double cacl2MassFractionPercent, double mgcl2MassFractionPercent, double naclMolalityMolPerKgWater,
        double kclMolalityMolPerKgWater, double cacl2MolalityMolPerKgWater, double mgcl2MolalityMolPerKgWater,
        double temperatureK, double totalPressureMpa, double sourceCo2MolePercent,
        double sourceUncertaintyPercentagePoints) {
      this.pointId = pointId;
      this.sourceTable = sourceTable;
      this.naclMassFractionPercent = naclMassFractionPercent;
      this.kclMassFractionPercent = kclMassFractionPercent;
      this.cacl2MassFractionPercent = cacl2MassFractionPercent;
      this.mgcl2MassFractionPercent = mgcl2MassFractionPercent;
      this.naclMolalityMolPerKgWater = naclMolalityMolPerKgWater;
      this.kclMolalityMolPerKgWater = kclMolalityMolPerKgWater;
      this.cacl2MolalityMolPerKgWater = cacl2MolalityMolPerKgWater;
      this.mgcl2MolalityMolPerKgWater = mgcl2MolalityMolPerKgWater;
      this.temperatureK = temperatureK;
      this.totalPressureMpa = totalPressureMpa;
      this.sourceCo2MolePercent = sourceCo2MolePercent;
      this.sourceUncertaintyPercentagePoints = sourceUncertaintyPercentagePoints;
    }

    /**
     * Creates the matched no-salt state at the same temperature and total pressure.
     *
     * @return matched pure-water reference state used only for model decomposition
     */
    Reference withoutSalt() {
      return new Reference(pointId + "-PURE-WATER", sourceTable, 0.0, 0.0, 0.0, temperatureK, totalPressureMpa,
          sourceCo2MolePercent, sourceUncertaintyPercentagePoints);
    }

    /**
     * Reports whether the state supplies formula-unit molalities rather than rounded mass fractions.
     *
     * @return true when all four formula-unit molalities are finite
     */
    private boolean hasExplicitMolalities() {
      return Double.isFinite(naclMolalityMolPerKgWater) && Double.isFinite(kclMolalityMolPerKgWater)
          && Double.isFinite(cacl2MolalityMolPerKgWater) && Double.isFinite(mgcl2MolalityMolPerKgWater);
    }

    /**
     * Returns the total dissolved-salt mass fraction in percent.
     *
     * @return total salt mass fraction in percent
     */
    private double totalSaltMassFractionPercent() {
      return naclMassFractionPercent + kclMassFractionPercent + cacl2MassFractionPercent + mgcl2MassFractionPercent;
    }
  }

  /**
   * Validated flashed state before observable mapping.
   *
   * @author OpenAI
   * @version 1.0
   */
  private static final class Trial {
    private final PhaseInterface aqueous;
    private final double co2PartialPressureBara;
    private final double co2FugacityBara;
    private final String phaseTypes;
    private final double componentBalanceResidualMol;
    private final double molecularLogFugacityResidual;
    private final double co2FeedMoles;

    /**
     * Creates a validated trial.
     *
     * @param aqueous ion-rich phase
     * @param co2PartialPressureBara calculated gas CO2 partial pressure in bara
     * @param co2FugacityBara CO2 fugacity in the most CO2-rich phase in bara
     * @param phaseTypes joined phase labels
     * @param componentBalanceResidualMol maximum component balance residual in mol
     * @param molecularLogFugacityResidual maximum molecular log fugacity ratio
     * @param co2FeedMoles CO2 reservoir actually used, which exceeds the requested one after a retry
     */
    private Trial(PhaseInterface aqueous, double co2PartialPressureBara, double co2FugacityBara, String phaseTypes,
        double componentBalanceResidualMol, double molecularLogFugacityResidual, double co2FeedMoles) {
      this.aqueous = aqueous;
      this.co2PartialPressureBara = co2PartialPressureBara;
      this.co2FugacityBara = co2FugacityBara;
      this.phaseTypes = phaseTypes;
      this.componentBalanceResidualMol = componentBalanceResidualMol;
      this.molecularLogFugacityResidual = molecularLogFugacityResidual;
      this.co2FeedMoles = co2FeedMoles;
    }
  }

  /**
   * Calculated observable mappings and numerical diagnostics.
   *
   * @author OpenAI
   * @version 1.0
   */
  static final class Result {
    final double co2PartialPressureBara;
    final double co2FugacityBara;
    final double nativeMolePercent;
    final double molecularMolePercent;
    final double pseudoSaltMolePercent;
    final double co2Molality;
    final double waterActivity;
    final double actualNaclMassPercent;
    final double actualKclMassPercent;
    final double actualCacl2MassPercent;
    final double actualMgcl2MassPercent;
    final double waterMassKg;
    final double co2Moles;
    final double naclMoles;
    final double kclMoles;
    final double cacl2Moles;
    final double mgcl2Moles;
    final double co2FeedMoles;
    final String phaseTypes;
    final double componentBalanceResidualMol;
    final double molecularLogFugacityResidual;

    /**
     * Creates one result.
     *
     * @param co2PartialPressureBara calculated gas CO2 partial pressure in bara
     * @param co2FugacityBara CO2 fugacity in the most CO2-rich phase in bara
     * @param nativeMolePercent aqueous CO2 mole percent including ions
     * @param molecularMolePercent CO2 mole percent over CO2 plus water
     * @param pseudoSaltMolePercent CO2 mole percent over CO2, water and salt formula units
     * @param co2Molality CO2 molality per kg aqueous water
     * @param waterActivity water activity proxy
     * @param actualNaclMassPercent final solvent-brine NaCl mass fraction in percent
     * @param actualKclMassPercent final solvent-brine KCl mass fraction in percent
     * @param actualCacl2MassPercent final solvent-brine CaCl2 mass fraction in percent
     * @param actualMgcl2MassPercent final solvent-brine MgCl2 mass fraction in percent
     * @param waterMassKg final aqueous water mass in kg
     * @param co2Moles final aqueous CO2 inventory in mol
     * @param naclMoles NaCl formula-unit inventory in mol
     * @param kclMoles KCl formula-unit inventory in mol
     * @param cacl2Moles CaCl2 formula-unit inventory in mol
     * @param mgcl2Moles MgCl2 formula-unit inventory in mol
     * @param co2FeedMoles finite CO2 feed inventory in mol
     * @param phaseTypes joined phase labels
     * @param componentBalanceResidualMol maximum component balance residual in mol
     * @param molecularLogFugacityResidual maximum molecular log fugacity ratio
     */
    private Result(double co2PartialPressureBara, double co2FugacityBara, double nativeMolePercent,
        double molecularMolePercent, double pseudoSaltMolePercent, double co2Molality, double waterActivity,
        double actualNaclMassPercent, double actualKclMassPercent, double actualCacl2MassPercent,
        double actualMgcl2MassPercent, double waterMassKg, double co2Moles, double naclMoles, double kclMoles,
        double cacl2Moles, double mgcl2Moles, double co2FeedMoles, String phaseTypes,
        double componentBalanceResidualMol, double molecularLogFugacityResidual) {
      this.co2PartialPressureBara = co2PartialPressureBara;
      this.co2FugacityBara = co2FugacityBara;
      this.nativeMolePercent = nativeMolePercent;
      this.molecularMolePercent = molecularMolePercent;
      this.pseudoSaltMolePercent = pseudoSaltMolePercent;
      this.co2Molality = co2Molality;
      this.waterActivity = waterActivity;
      this.actualNaclMassPercent = actualNaclMassPercent;
      this.actualKclMassPercent = actualKclMassPercent;
      this.actualCacl2MassPercent = actualCacl2MassPercent;
      this.actualMgcl2MassPercent = actualMgcl2MassPercent;
      this.waterMassKg = waterMassKg;
      this.co2Moles = co2Moles;
      this.naclMoles = naclMoles;
      this.kclMoles = kclMoles;
      this.cacl2Moles = cacl2Moles;
      this.mgcl2Moles = mgcl2Moles;
      this.co2FeedMoles = co2FeedMoles;
      this.phaseTypes = phaseTypes;
      this.componentBalanceResidualMol = componentBalanceResidualMol;
      this.molecularLogFugacityResidual = molecularLogFugacityResidual;
    }

    /**
     * Returns the effective CO2 fugacity divided by the salt-free aqueous CO2 mole fraction.
     *
     * <p>
     * This model diagnostic has units of bara and is not represented as a dilute-limit Henry constant at the dense
     * source states.
     *
     * @return effective fugacity over mole fraction in bara
     */
    double effectiveFOverXBara() {
      return co2FugacityBara / (molecularMolePercent / 100.0);
    }

    /**
     * Checks the exact log-space decomposition of dissolved CO2 into fugacity exposure and the effective aqueous
     * response.
     *
     * @param pureWater matched no-salt model state
     * @return log identity residual, ideally zero
     */
    double decompositionIdentityResidual(Result pureWater) {
      double dissolvedRatio = molecularMolePercent / pureWater.molecularMolePercent;
      double fugacityRatio = co2FugacityBara / pureWater.co2FugacityBara;
      double effectiveRatio = effectiveFOverXBara() / pureWater.effectiveFOverXBara();
      return Math.log(dissolvedRatio) - Math.log(fugacityRatio) + Math.log(effectiveRatio);
    }

    /**
     * Serializes this result with explicit observable, root and matched pure-water controls.
     *
     * @param reference source state
     * @param constrained same feed evaluated with explicit GAS/OIL root candidates
     * @param pureWater matched no-salt model state at the same temperature and pressure
     * @return one CSV row
     */
    private String toCsv(Reference reference, Result constrained, Result pureWater) {
      double dissolvedRatio = molecularMolePercent / pureWater.molecularMolePercent;
      double fugacityRatio = co2FugacityBara / pureWater.co2FugacityBara;
      double effectiveRatio = effectiveFOverXBara() / pureWater.effectiveFOverXBara();
      return reference.pointId + "," + reference.sourceTable + "," + reference.naclMassFractionPercent + ","
          + reference.kclMassFractionPercent + "," + reference.cacl2MassFractionPercent + "," + reference.temperatureK
          + "," + reference.totalPressureMpa + "," + reference.sourceCo2MolePercent + ","
          + reference.sourceUncertaintyPercentagePoints + "," + (10.0 * reference.totalPressureMpa) + ","
          + co2PartialPressureBara + "," + nativeMolePercent + ","
          + (nativeMolePercent - reference.sourceCo2MolePercent) + "," + molecularMolePercent + ","
          + (molecularMolePercent - reference.sourceCo2MolePercent) + "," + pseudoSaltMolePercent + ","
          + (pseudoSaltMolePercent - reference.sourceCo2MolePercent) + "," + co2Molality + "," + waterActivity + ","
          + actualNaclMassPercent + "," + actualKclMassPercent + "," + actualCacl2MassPercent + "," + waterMassKg + ","
          + co2Moles + "," + naclMoles + "," + kclMoles + "," + cacl2Moles + "," + co2FeedMoles + "," + phaseTypes + ","
          + componentBalanceResidualMol + "," + molecularLogFugacityResidual + "," + constrained.phaseTypes + ","
          + constrained.molecularMolePercent + "," + (constrained.molecularMolePercent - molecularMolePercent) + ","
          + constrained.waterActivity + "," + (constrained.waterActivity - waterActivity) + ","
          + constrained.molecularLogFugacityResidual + "," + pureWater.molecularMolePercent + "," + dissolvedRatio + ","
          + co2FugacityBara + "," + pureWater.co2FugacityBara + "," + fugacityRatio + "," + effectiveFOverXBara() + ","
          + pureWater.effectiveFOverXBara() + "," + effectiveRatio + "," + Math.log(dissolvedRatio) + ","
          + Math.log(fugacityRatio) + "," + Math.log(effectiveRatio) + "," + decompositionIdentityResidual(pureWater)
          + "," + pureWater.waterActivity + "," + (waterActivity - pureWater.waterActivity) + ","
          + pureWater.componentBalanceResidualMol + "," + pureWater.molecularLogFugacityResidual;
    }
  }
}
