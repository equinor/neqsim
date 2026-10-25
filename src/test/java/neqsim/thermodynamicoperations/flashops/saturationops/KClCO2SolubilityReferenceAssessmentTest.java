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
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * No-fit comparison with the He-Morse CO2-in-KCl solubility data compiled by NIST SRD 106.
 *
 * @author OpenAI
 * @version 1.0
 */
class KClCO2SolubilityReferenceAssessmentTest {
  private static final Logger logger = LogManager.getLogger(KClCO2SolubilityReferenceAssessmentTest.class);
  private static final String RESOURCE = "/data/chemistry_benchmarks/co2_kcl_solubility_he_morse1993.csv";
  private static final double ATM_TO_BAR = 1.01325;
  private static final double WATER_MOLAR_MASS_KG_PER_MOL = 0.01801528;
  private static final double RELATIVE_SOLUBILITY_UNCERTAINTY = 0.012;
  private static final double CO2_ACTIVITY_COEFFICIENT_UNCERTAINTY = 0.04;
  private static final double TEMPERATURE_UNCERTAINTY_K = 0.1;
  private static final double PARTIAL_PRESSURE_CONVERGENCE_TOLERANCE_BAR = 1.0e-10;
  private static final int MAX_PARTIAL_PRESSURE_ITERATIONS = 80;
  private static final double CO2_FEED_MOLES = 1.0;
  private static final double AQUEOUS_WATER_MASS_TOLERANCE_KG = 1.0e-10;

  /**
   * Executes the complete five-temperature NIST series at reported CO2 partial pressure without fitting.
   *
   * @throws Exception if the reference fixture or generated diagnostic cannot be read or written
   */
  @Tag("slow")
  @Test
  void nistKClSeriesReportsLikeForLikeAqueousCo2Molality() throws Exception {
    List<Reference> references = loadReferences();
    List<String> report = new ArrayList<String>();
    report.add("point_id,temperature_K,target_pco2_bara,actual_pco2_bara,kcl_molality_mol_per_kg_water,"
        + "source_solvent_density_g_per_cm3,experimental_co2_molality_mol_per_kg_water,"
        + "source_co2_activity_coefficient,model_co2_molality_mol_per_kg_water,"
        + "model_delta_from_previous_molality,model_trend_from_previous,aqueous_water_mass_kg,"
        + "model_minus_experiment,relative_residual,experimental_relative_uncertainty,"
        + "source_activity_coefficient_relative_uncertainty,temperature_uncertainty_K,total_pressure_bara,"
        + "aqueous_x_co2,fluid_phases,actual_kcl_molality_mol_per_kg_water,"
        + "co2_feed_moles,component_balance_residual_mol,molecular_log_fugacity_residual");
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get("target/kcl-co2-solubility-he-morse1993-assessment.csv"), report, StandardCharsets.UTF_8);

    double previousTemperature = Double.NaN;
    double previousExperimentalMolality = Double.POSITIVE_INFINITY;
    double previousModelMolality = Double.POSITIVE_INFINITY;
    int temperatureBlocks = 0;
    for (Reference reference : references) {
      if (reference.temperatureK != previousTemperature) {
        previousExperimentalMolality = Double.POSITIVE_INFINITY;
        previousModelMolality = Double.POSITIVE_INFINITY;
        previousTemperature = reference.temperatureK;
        temperatureBlocks++;
      }
      Result result = calculate(reference);
      assertTrue(reference.experimentalCo2Molality < previousExperimentalMolality,
          reference.pointId + " must retain the source salting-out ordering");
      assertEquals(reference.targetCo2PartialPressureBara, result.actualCo2PartialPressureBara, 1.0e-7,
          reference.pointId + " CO2 partial-pressure iteration did not converge");
      assertTrue(Double.isFinite(result.modelCo2Molality) && result.modelCo2Molality > 0.0,
          reference.pointId + " model CO2 molality must be finite and positive");
      String row = result.toCsv(reference, previousModelMolality);
      report.add(row);
      Files.write(Paths.get("target/kcl-co2-solubility-he-morse1993-assessment.csv"),
          java.util.Collections.singletonList(row), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
      previousExperimentalMolality = reference.experimentalCo2Molality;
      previousModelMolality = result.modelCo2Molality;
    }

    assertEquals(5, temperatureBlocks);
    assertEquals(27, report.size());
    for (String row : report) {
      logger.info("KCL_CO2_SOLUBILITY_DIAGNOSTIC,{}", row);
    }
  }

  /**
   * Calculates one experimental state while iterating total pressure to the reported CO2 partial pressure.
   *
   * @param reference experimental state
   * @return calculated diagnostics
   */
  private static Result calculate(Reference reference) {
    return calculate(reference, CO2_FEED_MOLES);
  }

  /**
   * Verifies the source state is independent of the finite gas-reservoir size.
   */
  @Tag("slow")
  @Test
  void hotConcentratedStateKeepsSourceMolalityWithEitherGasInventory() {
    Reference reference = new Reference("HM363-KCL3000", 363.15, 0.301, 1.0841, 0.0031, 3.0, 1.12);
    Result ordinary = calculate(reference, CO2_FEED_MOLES);
    Result smallerReservoir = calculate(reference, 0.5 * CO2_FEED_MOLES);
    assertEquals(ordinary.modelCo2Molality, smallerReservoir.modelCo2Molality, 1.0e-10);
    assertEquals(ordinary.totalPressureBara, smallerReservoir.totalPressureBara, 1.0e-9);
  }

  /**
   * Matches gas partial pressure and final aqueous salt molality, including evaporated feed water.
   *
   * @param reference experimental state
   * @param co2FeedMoles finite CO2 reservoir in mol, exceeding the dissolved inventory
   * @return calculated diagnostics
   */
  private static Result calculate(Reference reference, double co2FeedMoles) {
    PressureTrial upper = flashAtTotalPressure(reference, reference.targetCo2PartialPressureBara + 1.0, co2FeedMoles);
    for (int expansion = 0; expansion < 10
        && upper.actualCo2PartialPressureBara < reference.targetCo2PartialPressureBara; expansion++) {
      upper = flashAtTotalPressure(reference, 2.0 * upper.totalPressureBara, co2FeedMoles);
    }
    assertTrue(upper.actualCo2PartialPressureBara >= reference.targetCo2PartialPressureBara,
        reference.pointId + " could not bracket the target CO2 partial pressure");

    // Descend from a valid liquid-bearing state. Starting at pCO2 alone can boil away the
    // aqueous phase; a salt-depleted single gas phase is not a valid root-function evaluation.
    PressureTrial lower = upper;
    for (int contraction = 0; contraction < 80
        && lower.actualCo2PartialPressureBara > reference.targetCo2PartialPressureBara; contraction++) {
      double lowerPressure = Math.max(reference.targetCo2PartialPressureBara, 0.9 * lower.totalPressureBara);
      lower = flashAtTotalPressure(reference, lowerPressure, co2FeedMoles);
    }
    assertTrue(lower.actualCo2PartialPressureBara <= reference.targetCo2PartialPressureBara,
        reference.pointId + " could not find a conserved lower pressure trial");

    PressureTrial selected = lower;
    for (int iteration = 0; iteration < MAX_PARTIAL_PRESSURE_ITERATIONS
        && Math.abs(selected.actualCo2PartialPressureBara
            - reference.targetCo2PartialPressureBara) >= PARTIAL_PRESSURE_CONVERGENCE_TOLERANCE_BAR; iteration++) {
      double lowerResidual = lower.actualCo2PartialPressureBara - reference.targetCo2PartialPressureBara;
      double upperResidual = upper.actualCo2PartialPressureBara - reference.targetCo2PartialPressureBara;
      double bracketWidth = upper.totalPressureBara - lower.totalPressureBara;
      double candidatePressure = lower.totalPressureBara
          - lowerResidual * bracketWidth / (upperResidual - lowerResidual);
      if (!Double.isFinite(candidatePressure) || candidatePressure <= lower.totalPressureBara
          || candidatePressure >= upper.totalPressureBara || iteration % 4 == 3) {
        candidatePressure = 0.5 * (lower.totalPressureBara + upper.totalPressureBara);
      }

      selected = flashAtTotalPressure(reference, candidatePressure, co2FeedMoles);
      double selectedResidual = selected.actualCo2PartialPressureBara - reference.targetCo2PartialPressureBara;
      if (Math.abs(selectedResidual) < PARTIAL_PRESSURE_CONVERGENCE_TOLERANCE_BAR) {
        break;
      }
      if (selectedResidual < 0.0) {
        lower = selected;
      } else {
        upper = selected;
      }
    }

    assertEquals(reference.targetCo2PartialPressureBara, selected.actualCo2PartialPressureBara,
        PARTIAL_PRESSURE_CONVERGENCE_TOLERANCE_BAR, reference.pointId + " pressure root exhausted its iteration limit");
    SystemInterface fluid = selected.fluid;
    PhaseInterface aqueous = findIonRichPhase(fluid);
    double aqueousWaterMassKg = aqueous.getComponent("water").getNumberOfMolesInPhase() * WATER_MOLAR_MASS_KG_PER_MOL;
    assertEquals(1.0, aqueousWaterMassKg, AQUEOUS_WATER_MASS_TOLERANCE_KG,
        reference.pointId + " must use the source kilogram-water basis");
    double actualKclMolality = aqueous.getComponent("K+").getNumberOfMolesInPhase() / aqueousWaterMassKg;
    assertEquals(reference.kclMolality, actualKclMolality, 1.0e-9, reference.pointId + " aqueous KCl molality");
    double modelCo2Molality = aqueous.getComponent("CO2").getNumberOfMolesInPhase() / aqueousWaterMassKg;
    double aqueousCo2MoleFraction = aqueous.getComponent("CO2").getx();
    assertEquals(reference.kclMolality, aqueous.getComponent("K+").getNumberOfMolesInPhase(), 1.0e-12);
    assertEquals(reference.kclMolality, aqueous.getComponent("Cl-").getNumberOfMolesInPhase(), 1.0e-12);

    List<String> phaseTypes = new ArrayList<String>();
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      phaseTypes.add(fluid.getPhase(phaseIndex).getType().toString());
    }
    return new Result(selected.actualCo2PartialPressureBara, selected.totalPressureBara, modelCo2Molality,
        aqueousWaterMassKg, aqueousCo2MoleFraction, String.join("+", phaseTypes), actualKclMolality, co2FeedMoles,
        selected.componentBalanceResidualMol, selected.molecularLogFugacityResidual);
  }

  /**
   * Flashes independent states while compensating evaporated water to retain one aqueous kilogram.
   *
   * @param reference experimental state
   * @param totalPressureBara trial total pressure in bara
   * @param co2FeedMoles finite CO2 reservoir in mol
   * @return independently validated pressure trial
   */
  private static PressureTrial flashAtTotalPressure(Reference reference, double totalPressureBara,
      double co2FeedMoles) {
    double waterFeedMoles = 1.0 / WATER_MOLAR_MASS_KG_PER_MOL;
    for (int iteration = 0; iteration < 20; iteration++) {
      SystemInterface fluid = new SystemElectrolyteCPAstatoil(reference.temperatureK, totalPressureBara);
      fluid.addComponent("CO2", co2FeedMoles);
      fluid.addComponent("water", waterFeedMoles);
      fluid.addComponent("K+", reference.kclMolality);
      fluid.addComponent("Cl-", reference.kclMolality);
      fluid.setMixingRule(10);
      fluid.setMultiPhaseCheck(true);
      new ThermodynamicOperations(fluid).TPflash();
      fluid.init(1);

      assertEquals(2, fluid.getNumberOfPhases(), reference.pointId + " requires gas and aqueous phases");
      assertTrue(fluid.hasPhaseType(PhaseType.GAS) && fluid.hasPhaseType(PhaseType.AQUEOUS), reference.pointId);
      PhaseInterface gas = fluid.getPhase("gas");
      PhaseInterface aqueous = fluid.getPhase("aqueous");
      double componentBalanceResidualMol = 0.0;
      double molecularLogFugacityResidual = 0.0;
      double[] feedMoles = {co2FeedMoles, waterFeedMoles, reference.kclMolality, reference.kclMolality};
      for (int component = 0; component < feedMoles.length; component++) {
        double gasMoles = gas.getComponent(component).getNumberOfMolesInPhase();
        double aqueousMoles = aqueous.getComponent(component).getNumberOfMolesInPhase();
        componentBalanceResidualMol = Math.max(componentBalanceResidualMol,
            Math.abs(gasMoles + aqueousMoles - feedMoles[component]));
        if (gas.getComponent(component).getIonicCharge() == 0) {
          double fugacityRatio = gas.getFugacity(component) / aqueous.getFugacity(component);
          molecularLogFugacityResidual = Math.max(molecularLogFugacityResidual, Math.abs(Math.log(fugacityRatio)));
        } else {
          assertEquals(0.0, gasMoles, 1.0e-40, reference.pointId + " ions must remain aqueous");
          assertEquals(reference.kclMolality, aqueousMoles, 1.0e-12, reference.pointId + " ion inventory");
        }
      }
      assertTrue(componentBalanceResidualMol < 1.0e-10, reference.pointId + " component inventory");
      assertTrue(molecularLogFugacityResidual < 1.0e-7, reference.pointId + " molecular fugacity equilibrium");
      double aqueousWaterMoles = aqueous.getComponent("water").getNumberOfMolesInPhase();
      double waterCorrectionMoles = 1.0 / WATER_MOLAR_MASS_KG_PER_MOL - aqueousWaterMoles;
      if (Math.abs(waterCorrectionMoles) * WATER_MOLAR_MASS_KG_PER_MOL < AQUEOUS_WATER_MASS_TOLERANCE_KG) {
        double actualCo2PartialPressureBara = gas.getComponent("CO2").getx() * fluid.getPressure();
        return new PressureTrial(fluid, fluid.getPressure(), actualCo2PartialPressureBara, componentBalanceResidualMol,
            molecularLogFugacityResidual);
      }
      waterFeedMoles += waterCorrectionMoles;
    }
    throw new AssertionError(reference.pointId + " aqueous-water inventory iteration did not converge");
  }

  /**
   * Selects the phase that contains the greatest potassium mole fraction.
   *
   * @param fluid flashed system
   * @return ion-rich aqueous phase
   */
  private static PhaseInterface findIonRichPhase(SystemInterface fluid) {
    PhaseInterface selected = null;
    double largestPotassiumFraction = -1.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface candidate = fluid.getPhase(phaseIndex);
      if (candidate.hasComponent("K+") && candidate.getComponent("K+").getx() > largestPotassiumFraction) {
        selected = candidate;
        largestPotassiumFraction = candidate.getComponent("K+").getx();
      }
    }
    assertNotNull(selected);
    return selected;
  }

  /**
   * Loads the source-pinned 273.15-363.15 K experimental series.
   *
   * @return experimental rows
   * @throws Exception if the fixture cannot be read or parsed
   */
  private static List<Reference> loadReferences() throws Exception {
    InputStream stream = KClCO2SolubilityReferenceAssessmentTest.class.getResourceAsStream(RESOURCE);
    assertNotNull(stream, RESOURCE);
    List<Reference> references = new ArrayList<Reference>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("#") || line.startsWith("point_id") || line.trim().isEmpty()) {
          continue;
        }
        String[] fields = line.split(",");
        references.add(new Reference(fields[0], Double.parseDouble(fields[1]), Double.parseDouble(fields[2]),
            Double.parseDouble(fields[3]), Double.parseDouble(fields[4]), Double.parseDouble(fields[5]),
            Double.parseDouble(fields[6])));
      }
    }
    assertEquals(26, references.size());
    return references;
  }

  /**
   * Independent trial state for the CO2 partial-pressure root.
   *
   * @author OpenAI
   * @version 1.0
   */
  private static final class PressureTrial {
    private final SystemInterface fluid;
    private final double totalPressureBara;
    private final double actualCo2PartialPressureBara;
    private final double componentBalanceResidualMol;
    private final double molecularLogFugacityResidual;

    /**
     * Creates a pressure trial.
     *
     * @param fluid independently flashed system
     * @param totalPressureBara total pressure in bara
     * @param actualCo2PartialPressureBara calculated CO2 partial pressure in bara
     * @param componentBalanceResidualMol maximum component balance residual in mol
     * @param molecularLogFugacityResidual maximum molecular log fugacity ratio
     */
    private PressureTrial(SystemInterface fluid, double totalPressureBara, double actualCo2PartialPressureBara,
        double componentBalanceResidualMol, double molecularLogFugacityResidual) {
      this.fluid = fluid;
      this.totalPressureBara = totalPressureBara;
      this.actualCo2PartialPressureBara = actualCo2PartialPressureBara;
      this.componentBalanceResidualMol = componentBalanceResidualMol;
      this.molecularLogFugacityResidual = molecularLogFugacityResidual;
    }
  }

  /**
   * Experimental state.
   *
   * @author OpenAI
   * @version 1.0
   */
  private static final class Reference {
    private final String pointId;
    private final double temperatureK;
    private final double targetCo2PartialPressureBara;
    private final double sourceSolventDensity;
    private final double experimentalCo2Molality;
    private final double kclMolality;
    private final double sourceCo2ActivityCoefficient;

    /**
     * Creates an experimental state.
     *
     * @param pointId stable fixture identifier
     * @param temperatureK temperature in kelvin
     * @param co2PartialPressureAtm CO2 partial pressure in atmospheres
     * @param sourceSolventDensity source solvent density in g/cm3
     * @param experimentalCo2Molality experimental CO2 molality
     * @param kclMolality KCl molality
     * @param sourceCo2ActivityCoefficient source CO2 activity coefficient
     */
    private Reference(String pointId, double temperatureK, double co2PartialPressureAtm, double sourceSolventDensity,
        double experimentalCo2Molality, double kclMolality, double sourceCo2ActivityCoefficient) {
      this.pointId = pointId;
      this.temperatureK = temperatureK;
      this.targetCo2PartialPressureBara = co2PartialPressureAtm * ATM_TO_BAR;
      this.sourceSolventDensity = sourceSolventDensity;
      this.experimentalCo2Molality = experimentalCo2Molality;
      this.kclMolality = kclMolality;
      this.sourceCo2ActivityCoefficient = sourceCo2ActivityCoefficient;
    }
  }

  /**
   * Calculated state.
   *
   * @author OpenAI
   * @version 1.0
   */
  private static final class Result {
    private final double actualCo2PartialPressureBara;
    private final double totalPressureBara;
    private final double modelCo2Molality;
    private final double aqueousWaterMassKg;
    private final double aqueousCo2MoleFraction;
    private final String phaseTypes;
    private final double actualKclMolality;
    private final double co2FeedMoles;
    private final double componentBalanceResidualMol;
    private final double molecularLogFugacityResidual;

    /**
     * Creates calculated diagnostics.
     *
     * @param actualCo2PartialPressureBara actual CO2 partial pressure in bara
     * @param totalPressureBara total pressure in bara
     * @param modelCo2Molality calculated aqueous CO2 molality
     * @param aqueousWaterMassKg water mass in the selected aqueous phase in kilograms
     * @param aqueousCo2MoleFraction calculated aqueous CO2 mole fraction
     * @param phaseTypes phase topology
     * @param actualKclMolality final aqueous KCl molality in mol/kg water
     * @param co2FeedMoles finite CO2 feed inventory in mol
     * @param componentBalanceResidualMol maximum component balance residual in mol
     * @param molecularLogFugacityResidual maximum molecular log fugacity ratio
     */
    private Result(double actualCo2PartialPressureBara, double totalPressureBara, double modelCo2Molality,
        double aqueousWaterMassKg, double aqueousCo2MoleFraction, String phaseTypes, double actualKclMolality,
        double co2FeedMoles, double componentBalanceResidualMol, double molecularLogFugacityResidual) {
      this.actualCo2PartialPressureBara = actualCo2PartialPressureBara;
      this.totalPressureBara = totalPressureBara;
      this.modelCo2Molality = modelCo2Molality;
      this.aqueousWaterMassKg = aqueousWaterMassKg;
      this.aqueousCo2MoleFraction = aqueousCo2MoleFraction;
      this.phaseTypes = phaseTypes;
      this.actualKclMolality = actualKclMolality;
      this.co2FeedMoles = co2FeedMoles;
      this.componentBalanceResidualMol = componentBalanceResidualMol;
      this.molecularLogFugacityResidual = molecularLogFugacityResidual;
    }

    /**
     * Serializes a deterministic diagnostic row.
     *
     * @param reference experimental state
     * @param previousModelMolality preceding model value in this temperature block
     * @return CSV row
     */
    private String toCsv(Reference reference, double previousModelMolality) {
      double residual = modelCo2Molality - reference.experimentalCo2Molality;
      double modelDelta = Double.isFinite(previousModelMolality) ? modelCo2Molality - previousModelMolality
          : Double.NaN;
      String modelTrend = "not_applicable";
      if (Double.isFinite(modelDelta)) {
        if (modelDelta < 0.0) {
          modelTrend = "salting_out";
        } else if (modelDelta > 0.0) {
          modelTrend = "salting_in";
        } else {
          modelTrend = "flat";
        }
      }
      return reference.pointId + "," + reference.temperatureK + "," + reference.targetCo2PartialPressureBara + ","
          + actualCo2PartialPressureBara + "," + reference.kclMolality + "," + reference.sourceSolventDensity + ","
          + reference.experimentalCo2Molality + "," + reference.sourceCo2ActivityCoefficient + "," + modelCo2Molality
          + "," + modelDelta + "," + modelTrend + "," + aqueousWaterMassKg + "," + residual + ","
          + residual / reference.experimentalCo2Molality + "," + RELATIVE_SOLUBILITY_UNCERTAINTY + ","
          + CO2_ACTIVITY_COEFFICIENT_UNCERTAINTY + "," + TEMPERATURE_UNCERTAINTY_K + "," + totalPressureBara + ","
          + aqueousCo2MoleFraction + "," + phaseTypes + "," + actualKclMolality + "," + co2FeedMoles + ","
          + componentBalanceResidualMol + "," + molecularLogFugacityResidual;
    }
  }
}
