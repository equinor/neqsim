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
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * No-fit low-temperature CO2-in-KCl diagnostic from Tohidi's Burgass comparison.
 *
 * @author OpenAI
 * @version 1.0
 */
class KClCO2LowTemperatureSolubilityAssessmentTest {
  private static final Logger logger = LogManager.getLogger(KClCO2LowTemperatureSolubilityAssessmentTest.class);
  private static final String RESOURCE = "/data/chemistry_benchmarks/co2_kcl_solubility_burgass_tohidi1995.csv";
  private static final String OUTPUT = "target/kcl-co2-solubility-burgass-tohidi1995-assessment.csv";
  private static final double WATER_MOLAR_MASS_KG_PER_MOL = 0.01801528;
  private static final double KCL_MOLAR_MASS_KG_PER_MOL = 0.0745513;
  private static final double CO2_FEED_MOLES = 10.0;
  private static final double AQUEOUS_WATER_MASS_TOLERANCE_KG = 1.0e-10;
  private static final double KCL_MASS_PERCENT_CLOSURE_TOLERANCE = 2.0e-9;

  /**
   * Reports all non-equivalent mole-percent mappings without selecting an unsupported source basis.
   *
   * @throws Exception if the reference fixture or generated diagnostic cannot be read or written
   */
  @Tag("slow")
  @Test
  void burgassTohidiSeriesReportsObservableMappingsWithoutFitting() throws Exception {
    List<Reference> references = loadReferences();
    List<String> report = new ArrayList<String>();
    report.add("point_id,kcl_mass_fraction_percent,temperature_K,total_pressure_MPa,"
        + "source_co2_mole_percent,actual_total_pressure_bara,actual_co2_partial_pressure_bara,"
        + "model_co2_mole_percent_including_ions,residual_including_ions_percentage_points,"
        + "model_co2_mole_percent_molecular_only,residual_molecular_only_percentage_points,"
        + "model_co2_mole_percent_pseudo_kcl,residual_pseudo_kcl_percentage_points,"
        + "model_co2_molality_mol_per_kg_water,water_activity_proxy,actual_kcl_mass_fraction_percent,"
        + "aqueous_water_mass_kg,aqueous_co2_moles,kcl_formula_moles,co2_feed_moles,fluid_phases,"
        + "component_balance_residual_mol,molecular_log_fugacity_residual");
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), report, StandardCharsets.UTF_8);

    for (Reference reference : references) {
      Result result = calculate(reference, CO2_FEED_MOLES);
      String row = result.toCsv(reference);
      report.add(row);
      Files.write(Paths.get(OUTPUT), Collections.singletonList(row), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    assertEquals(4, references.size());
    assertEquals(5, report.size());
    for (String row : report) {
      logger.info("KCL_CO2_LOW_TEMPERATURE_DIAGNOSTIC,{}", row);
    }
  }

  /**
   * Checks that the finite gas reservoir does not control the cold concentrated result.
   */
  @Tag("slow")
  @Test
  void coldConcentratedStateIsIndependentOfGasInventory() {
    Reference reference = new Reference("BT-KCL14-266", 14.0, 266.5, 1.172, 1.26);
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
  private static Result calculate(Reference reference, double co2FeedMoles) {
    double kclMassKg = reference.kclMassFractionPercent / (100.0 - reference.kclMassFractionPercent);
    double kclMoles = kclMassKg / KCL_MOLAR_MASS_KG_PER_MOL;
    double totalPressureBara = 10.0 * reference.totalPressureMpa;
    Trial trial = flashWithAqueousWaterBasis(reference, totalPressureBara, co2FeedMoles, kclMoles);
    PhaseInterface aqueous = trial.aqueous;

    double waterMoles = aqueous.getComponent("water").getNumberOfMolesInPhase();
    double waterMassKg = waterMoles * WATER_MOLAR_MASS_KG_PER_MOL;
    double co2Moles = aqueous.getComponent("CO2").getNumberOfMolesInPhase();
    double actualKclMoles = aqueous.getComponent("K+").getNumberOfMolesInPhase();
    double actualKclMassPercent = 100.0 * actualKclMoles * KCL_MOLAR_MASS_KG_PER_MOL
        / (waterMassKg + actualKclMoles * KCL_MOLAR_MASS_KG_PER_MOL);
    double nativeMolePercent = 100.0 * aqueous.getComponent("CO2").getx();
    double molecularMolePercent = 100.0 * co2Moles / (co2Moles + waterMoles);
    double pseudoKclMolePercent = 100.0 * co2Moles / (co2Moles + waterMoles + actualKclMoles);
    double co2Molality = co2Moles / waterMassKg;
    int waterIndex = aqueous.getComponent("water").getComponentNumber();
    double waterActivity = aqueous.getComponent("water").getx() * aqueous.getActivityCoefficient(waterIndex);

    assertEquals(1.0, waterMassKg, AQUEOUS_WATER_MASS_TOLERANCE_KG, reference.pointId + " aqueous-water basis");
    assertEquals(kclMoles, actualKclMoles, 1.0e-12, reference.pointId + " potassium inventory");
    assertEquals(kclMoles, aqueous.getComponent("Cl-").getNumberOfMolesInPhase(), 1.0e-12,
        reference.pointId + " chloride inventory");
    assertEquals(reference.kclMassFractionPercent, actualKclMassPercent, KCL_MASS_PERCENT_CLOSURE_TOLERANCE,
        reference.pointId + " KCl mass fraction");
    for (double value : new double[] {trial.co2PartialPressureBara, nativeMolePercent, molecularMolePercent,
        pseudoKclMolePercent, co2Molality, waterActivity}) {
      assertTrue(Double.isFinite(value) && value > 0.0, reference.pointId + " finite diagnostic");
    }
    assertTrue(waterActivity <= 1.0, reference.pointId + " water activity range");

    return new Result(trial.co2PartialPressureBara, nativeMolePercent, molecularMolePercent, pseudoKclMolePercent,
        co2Molality, waterActivity, actualKclMassPercent, waterMassKg, co2Moles, actualKclMoles, co2FeedMoles,
        trial.phaseTypes, trial.componentBalanceResidualMol, trial.molecularLogFugacityResidual);
  }

  /**
   * Flashes a source pressure while compensating evaporated water.
   *
   * @param reference source state
   * @param totalPressureBara source total pressure in bara
   * @param co2FeedMoles finite CO2 reservoir in mol
   * @param kclMoles KCl formula-unit inventory in mol
   * @return converged and validated state
   */
  private static Trial flashWithAqueousWaterBasis(Reference reference, double totalPressureBara, double co2FeedMoles,
      double kclMoles) {
    double waterFeedMoles = 1.0 / WATER_MOLAR_MASS_KG_PER_MOL;
    for (int iteration = 0; iteration < 20; iteration++) {
      SystemInterface fluid = new SystemElectrolyteCPAstatoil(reference.temperatureK, totalPressureBara);
      fluid.addComponent("CO2", co2FeedMoles);
      fluid.addComponent("water", waterFeedMoles);
      fluid.addComponent("K+", kclMoles);
      fluid.addComponent("Cl-", kclMoles);
      fluid.setMixingRule(10);
      fluid.setMultiPhaseCheck(true);
      new ThermodynamicOperations(fluid).TPflash();
      fluid.init(1);

      assertTrue(fluid.hasPhaseType(PhaseType.GAS) && fluid.hasPhaseType(PhaseType.AQUEOUS),
          reference.pointId + " requires gas and aqueous phases");
      PhaseInterface gas = fluid.getPhase("gas");
      PhaseInterface aqueous = findIonRichPhase(fluid);
      double[] feedMoles = {co2FeedMoles, waterFeedMoles, kclMoles, kclMoles};
      double componentBalanceResidualMol = 0.0;
      double molecularLogFugacityResidual = 0.0;
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
        }
      }
      assertTrue(componentBalanceResidualMol < 1.0e-10, reference.pointId + " component inventory");
      assertTrue(molecularLogFugacityResidual < 1.0e-7, reference.pointId + " molecular fugacity equilibrium");

      double aqueousWaterMoles = aqueous.getComponent("water").getNumberOfMolesInPhase();
      double waterCorrectionMoles = 1.0 / WATER_MOLAR_MASS_KG_PER_MOL - aqueousWaterMoles;
      if (Math.abs(waterCorrectionMoles) * WATER_MOLAR_MASS_KG_PER_MOL < AQUEOUS_WATER_MASS_TOLERANCE_KG) {
        List<String> phases = new ArrayList<String>();
        for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
          phases.add(fluid.getPhase(phaseIndex).getType().toString());
        }
        double co2PartialPressureBara = gas.getComponent("CO2").getx() * fluid.getPressure();
        return new Trial(aqueous, co2PartialPressureBara, String.join("+", phases), componentBalanceResidualMol,
            molecularLogFugacityResidual);
      }
      waterFeedMoles += waterCorrectionMoles;
    }
    throw new AssertionError(reference.pointId + " aqueous-water inventory iteration did not converge");
  }

  /**
   * Selects the phase containing the greatest potassium mole fraction.
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
   * Loads the four source-pinned states.
   *
   * @return source states
   * @throws Exception if the fixture cannot be read or parsed
   */
  private static List<Reference> loadReferences() throws Exception {
    InputStream stream = KClCO2LowTemperatureSolubilityAssessmentTest.class.getResourceAsStream(RESOURCE);
    assertNotNull(stream, RESOURCE);
    List<Reference> references = new ArrayList<Reference>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("point_id") || line.trim().isEmpty()) {
          continue;
        }
        String[] fields = line.split(",");
        references.add(new Reference(fields[0], Double.parseDouble(fields[1]), Double.parseDouble(fields[2]),
            Double.parseDouble(fields[3]), Double.parseDouble(fields[4])));
      }
    }
    assertEquals(4, references.size());
    return references;
  }

  /**
   * Source state retained without interpretation of the reported CO2 mole-percent denominator.
   *
   * @author OpenAI
   * @version 1.0
   */
  private static final class Reference {
    private final String pointId;
    private final double kclMassFractionPercent;
    private final double temperatureK;
    private final double totalPressureMpa;
    private final double sourceCo2MolePercent;

    /**
     * Creates one source state.
     *
     * @param pointId stable point identifier
     * @param kclMassFractionPercent KCl mass fraction in percent
     * @param temperatureK temperature in K
     * @param totalPressureMpa total pressure in MPa
     * @param sourceCo2MolePercent source-reported CO2 mole percent
     */
    private Reference(String pointId, double kclMassFractionPercent, double temperatureK, double totalPressureMpa,
        double sourceCo2MolePercent) {
      this.pointId = pointId;
      this.kclMassFractionPercent = kclMassFractionPercent;
      this.temperatureK = temperatureK;
      this.totalPressureMpa = totalPressureMpa;
      this.sourceCo2MolePercent = sourceCo2MolePercent;
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
    private final String phaseTypes;
    private final double componentBalanceResidualMol;
    private final double molecularLogFugacityResidual;

    /**
     * Creates a validated trial.
     *
     * @param aqueous ion-rich phase
     * @param co2PartialPressureBara calculated gas CO2 partial pressure in bara
     * @param phaseTypes joined phase labels
     * @param componentBalanceResidualMol maximum component balance residual in mol
     * @param molecularLogFugacityResidual maximum molecular log fugacity ratio
     */
    private Trial(PhaseInterface aqueous, double co2PartialPressureBara, String phaseTypes,
        double componentBalanceResidualMol, double molecularLogFugacityResidual) {
      this.aqueous = aqueous;
      this.co2PartialPressureBara = co2PartialPressureBara;
      this.phaseTypes = phaseTypes;
      this.componentBalanceResidualMol = componentBalanceResidualMol;
      this.molecularLogFugacityResidual = molecularLogFugacityResidual;
    }
  }

  /**
   * Calculated observable mappings and numerical diagnostics.
   *
   * @author OpenAI
   * @version 1.0
   */
  private static final class Result {
    private final double co2PartialPressureBara;
    private final double nativeMolePercent;
    private final double molecularMolePercent;
    private final double pseudoKclMolePercent;
    private final double co2Molality;
    private final double waterActivity;
    private final double actualKclMassPercent;
    private final double waterMassKg;
    private final double co2Moles;
    private final double kclMoles;
    private final double co2FeedMoles;
    private final String phaseTypes;
    private final double componentBalanceResidualMol;
    private final double molecularLogFugacityResidual;

    /**
     * Creates one result.
     *
     * @param co2PartialPressureBara calculated gas CO2 partial pressure in bara
     * @param nativeMolePercent aqueous CO2 mole percent including ions
     * @param molecularMolePercent CO2 mole percent over CO2 plus water
     * @param pseudoKclMolePercent CO2 mole percent over CO2, water and one KCl formula unit
     * @param co2Molality CO2 molality per kg aqueous water
     * @param waterActivity water activity proxy
     * @param actualKclMassPercent final solvent-brine KCl mass fraction in percent
     * @param waterMassKg final aqueous water mass in kg
     * @param co2Moles final aqueous CO2 inventory in mol
     * @param kclMoles KCl formula-unit inventory in mol
     * @param co2FeedMoles finite CO2 feed inventory in mol
     * @param phaseTypes joined phase labels
     * @param componentBalanceResidualMol maximum component balance residual in mol
     * @param molecularLogFugacityResidual maximum molecular log fugacity ratio
     */
    private Result(double co2PartialPressureBara, double nativeMolePercent, double molecularMolePercent,
        double pseudoKclMolePercent, double co2Molality, double waterActivity, double actualKclMassPercent,
        double waterMassKg, double co2Moles, double kclMoles, double co2FeedMoles, String phaseTypes,
        double componentBalanceResidualMol, double molecularLogFugacityResidual) {
      this.co2PartialPressureBara = co2PartialPressureBara;
      this.nativeMolePercent = nativeMolePercent;
      this.molecularMolePercent = molecularMolePercent;
      this.pseudoKclMolePercent = pseudoKclMolePercent;
      this.co2Molality = co2Molality;
      this.waterActivity = waterActivity;
      this.actualKclMassPercent = actualKclMassPercent;
      this.waterMassKg = waterMassKg;
      this.co2Moles = co2Moles;
      this.kclMoles = kclMoles;
      this.co2FeedMoles = co2FeedMoles;
      this.phaseTypes = phaseTypes;
      this.componentBalanceResidualMol = componentBalanceResidualMol;
      this.molecularLogFugacityResidual = molecularLogFugacityResidual;
    }

    /**
     * Serializes this result without selecting one source-observable interpretation.
     *
     * @param reference source state
     * @return one CSV row
     */
    private String toCsv(Reference reference) {
      return reference.pointId + "," + reference.kclMassFractionPercent + "," + reference.temperatureK + ","
          + reference.totalPressureMpa + "," + reference.sourceCo2MolePercent + ","
          + (10.0 * reference.totalPressureMpa) + "," + co2PartialPressureBara + "," + nativeMolePercent + ","
          + (nativeMolePercent - reference.sourceCo2MolePercent) + "," + molecularMolePercent + ","
          + (molecularMolePercent - reference.sourceCo2MolePercent) + "," + pseudoKclMolePercent + ","
          + (pseudoKclMolePercent - reference.sourceCo2MolePercent) + "," + co2Molality + "," + waterActivity + ","
          + actualKclMassPercent + "," + waterMassKg + "," + co2Moles + "," + kclMoles + "," + co2FeedMoles + ","
          + phaseTypes + "," + componentBalanceResidualMol + "," + molecularLogFugacityResidual;
    }
  }
}
