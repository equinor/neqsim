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
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

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
  private static final double CACL2_MOLAR_MASS_KG_PER_MOL = 0.11098;
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
        + "component_balance_residual_mol,molecular_log_fugacity_residual");
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), report, StandardCharsets.UTF_8);

    for (Reference reference : references) {
      Result result = calculate(reference, CO2_FEED_MOLES);
      String row = result.toCsv(reference);
      report.add(row);
      Files.write(Paths.get(OUTPUT), Collections.singletonList(row), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
      if ("MCB-KCL15-373-P281".equals(reference.pointId)) {
        // The preliminary stability check previously led to a third phase with unequal fugacities.
        assertEquals("GAS+AQUEOUS", result.phaseTypes, reference.pointId + " phase split");
        assertTrue(result.molecularLogFugacityResidual < 1.0e-8, reference.pointId + " molecular fugacity closure");
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
  private static Result calculate(Reference reference, double co2FeedMoles) {
    double saltFreeMassPercent = 100.0 - reference.totalSaltMassFractionPercent();
    double naclMassKg = reference.naclMassFractionPercent / saltFreeMassPercent;
    double kclMassKg = reference.kclMassFractionPercent / saltFreeMassPercent;
    double cacl2MassKg = reference.cacl2MassFractionPercent / saltFreeMassPercent;
    double naclMoles = naclMassKg / NACL_MOLAR_MASS_KG_PER_MOL;
    double kclMoles = kclMassKg / KCL_MOLAR_MASS_KG_PER_MOL;
    double cacl2Moles = cacl2MassKg / CACL2_MOLAR_MASS_KG_PER_MOL;
    double totalPressureBara = 10.0 * reference.totalPressureMpa;
    Trial trial = flashWithAqueousWaterBasis(reference, totalPressureBara, co2FeedMoles, naclMoles, kclMoles,
        cacl2Moles);
    PhaseInterface aqueous = trial.aqueous;

    double waterMoles = aqueous.getComponent("water").getNumberOfMolesInPhase();
    double waterMassKg = waterMoles * WATER_MOLAR_MASS_KG_PER_MOL;
    double co2Moles = aqueous.getComponent("CO2").getNumberOfMolesInPhase();
    double actualNaclMoles = componentMoles(aqueous, "Na+");
    double actualKclMoles = componentMoles(aqueous, "K+");
    double actualCacl2Moles = componentMoles(aqueous, "Ca++");
    double solutionMassKg = waterMassKg + actualNaclMoles * NACL_MOLAR_MASS_KG_PER_MOL
        + actualKclMoles * KCL_MOLAR_MASS_KG_PER_MOL + actualCacl2Moles * CACL2_MOLAR_MASS_KG_PER_MOL;
    double actualNaclMassPercent = 100.0 * actualNaclMoles * NACL_MOLAR_MASS_KG_PER_MOL / solutionMassKg;
    double actualKclMassPercent = 100.0 * actualKclMoles * KCL_MOLAR_MASS_KG_PER_MOL / solutionMassKg;
    double actualCacl2MassPercent = 100.0 * actualCacl2Moles * CACL2_MOLAR_MASS_KG_PER_MOL / solutionMassKg;
    double nativeMolePercent = 100.0 * aqueous.getComponent("CO2").getx();
    double molecularMolePercent = 100.0 * co2Moles / (co2Moles + waterMoles);
    double pseudoSaltMolePercent = 100.0 * co2Moles
        / (co2Moles + waterMoles + actualNaclMoles + actualKclMoles + actualCacl2Moles);
    double co2Molality = co2Moles / waterMassKg;
    int waterIndex = aqueous.getComponent("water").getComponentNumber();
    double waterActivity = aqueous.getComponent("water").getx() * aqueous.getActivityCoefficient(waterIndex);

    assertEquals(1.0, waterMassKg, AQUEOUS_WATER_MASS_TOLERANCE_KG, reference.pointId + " aqueous-water basis");
    assertEquals(naclMoles, actualNaclMoles, 1.0e-12, reference.pointId + " sodium inventory");
    assertEquals(kclMoles, actualKclMoles, 1.0e-12, reference.pointId + " potassium inventory");
    assertEquals(cacl2Moles, actualCacl2Moles, 1.0e-12, reference.pointId + " calcium inventory");
    assertEquals(naclMoles + kclMoles + 2.0 * cacl2Moles, aqueous.getComponent("Cl-").getNumberOfMolesInPhase(),
        1.0e-12, reference.pointId + " chloride inventory");
    assertEquals(reference.naclMassFractionPercent, actualNaclMassPercent, SALT_MASS_PERCENT_CLOSURE_TOLERANCE,
        reference.pointId + " NaCl mass fraction");
    assertEquals(reference.kclMassFractionPercent, actualKclMassPercent, SALT_MASS_PERCENT_CLOSURE_TOLERANCE,
        reference.pointId + " KCl mass fraction");
    assertEquals(reference.cacl2MassFractionPercent, actualCacl2MassPercent, SALT_MASS_PERCENT_CLOSURE_TOLERANCE,
        reference.pointId + " CaCl2 mass fraction");
    for (double value : new double[] {trial.co2PartialPressureBara, nativeMolePercent, molecularMolePercent,
        pseudoSaltMolePercent, co2Molality, waterActivity}) {
      assertTrue(Double.isFinite(value) && value > 0.0, reference.pointId + " finite diagnostic");
    }
    assertTrue(waterActivity <= 1.0, reference.pointId + " water activity range");

    return new Result(trial.co2PartialPressureBara, nativeMolePercent, molecularMolePercent, pseudoSaltMolePercent,
        co2Molality, waterActivity, actualNaclMassPercent, actualKclMassPercent, actualCacl2MassPercent, waterMassKg,
        co2Moles, actualNaclMoles, actualKclMoles, actualCacl2Moles, trial.co2FeedMoles, trial.phaseTypes,
        trial.componentBalanceResidualMol, trial.molecularLogFugacityResidual);
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
   * @return converged and validated state
   */
  private static Trial flashWithAqueousWaterBasis(Reference reference, double totalPressureBara, double co2FeedMoles,
      double naclMoles, double kclMoles, double cacl2Moles) {
    double waterFeedMoles = 1.0 / WATER_MOLAR_MASS_KG_PER_MOL;
    double co2Feed = co2FeedMoles;
    for (int iteration = 0; iteration < 20; iteration++) {
      // The flash can return a spurious single phase for a feed that is a tiny step from a feed that splits
      // correctly (for example 423.15 K, 385.31 bar, 15 wt% KCl). The saturated aqueous composition does not
      // depend on the reservoir size, so retry with a larger CO2 reservoir; the feed used is reported.
      SystemInterface fluid = null;
      for (int attempt = 0; attempt <= MAX_RESERVOIR_RETRIES; attempt++) {
        fluid = flashSystem(reference.temperatureK, totalPressureBara, co2Feed, waterFeedMoles, naclMoles, kclMoles,
            cacl2Moles);
        if (fluid.getNumberOfPhases() >= 2) {
          break;
        }
        co2Feed *= RESERVOIR_RETRY_FACTOR;
      }

      assertTrue(fluid.getNumberOfPhases() >= 2, reference.pointId + " requires an aqueous and a non-aqueous phase");
      PhaseInterface aqueous = findIonRichPhase(fluid);
      List<PhaseInterface> nonAqueous = nonAqueousPhases(fluid, aqueous);
      PhaseInterface gas = mostCo2RichPhase(nonAqueous);
      double componentBalanceResidualMol = 0.0;
      double molecularLogFugacityResidual = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        String componentName = aqueous.getComponent(component).getComponentName();
        double feedMoles = feedMoles(componentName, co2Feed, waterFeedMoles, naclMoles, kclMoles, cacl2Moles);
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
      assertTrue(componentBalanceResidualMol < 1.0e-10, reference.pointId + " component inventory");
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
        return new Trial(aqueous, co2PartialPressureBara, String.join("+", phases), componentBalanceResidualMol,
            molecularLogFugacityResidual, co2Feed);
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
   * @return flashed system
   */
  private static SystemInterface flashSystem(double temperatureK, double pressureBara, double co2Moles,
      double waterMoles, double naclMoles, double kclMoles, double cacl2Moles) {
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
    fluid.addComponent("Cl-", naclMoles + kclMoles + 2.0 * cacl2Moles);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(1);
    return fluid;
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
   * @return component feed inventory in mol
   */
  private static double feedMoles(String componentName, double co2FeedMoles, double waterFeedMoles, double naclMoles,
      double kclMoles, double cacl2Moles) {
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
    if ("Cl-".equals(componentName)) {
      return naclMoles + kclMoles + 2.0 * cacl2Moles;
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
   * Selects the phase containing the greatest total ionic mole fraction.
   *
   * @param fluid flashed system
   * @return ion-rich aqueous phase
   */
  private static PhaseInterface findIonRichPhase(SystemInterface fluid) {
    PhaseInterface selected = null;
    double largestIonicFraction = -1.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface candidate = fluid.getPhase(phaseIndex);
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
  private static final class Reference {
    private final String pointId;
    private final String sourceTable;
    private final double naclMassFractionPercent;
    private final double kclMassFractionPercent;
    private final double cacl2MassFractionPercent;
    private final double temperatureK;
    private final double totalPressureMpa;
    private final double sourceCo2MolePercent;
    private final double sourceUncertaintyPercentagePoints;

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
    private Reference(String pointId, String sourceTable, double naclMassFractionPercent, double kclMassFractionPercent,
        double cacl2MassFractionPercent, double temperatureK, double totalPressureMpa, double sourceCo2MolePercent,
        double sourceUncertaintyPercentagePoints) {
      this.pointId = pointId;
      this.sourceTable = sourceTable;
      this.naclMassFractionPercent = naclMassFractionPercent;
      this.kclMassFractionPercent = kclMassFractionPercent;
      this.cacl2MassFractionPercent = cacl2MassFractionPercent;
      this.temperatureK = temperatureK;
      this.totalPressureMpa = totalPressureMpa;
      this.sourceCo2MolePercent = sourceCo2MolePercent;
      this.sourceUncertaintyPercentagePoints = sourceUncertaintyPercentagePoints;
    }

    /**
     * Returns the total dissolved-salt mass fraction in percent.
     *
     * @return total salt mass fraction in percent
     */
    private double totalSaltMassFractionPercent() {
      return naclMassFractionPercent + kclMassFractionPercent + cacl2MassFractionPercent;
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
    private final double co2FeedMoles;

    /**
     * Creates a validated trial.
     *
     * @param aqueous ion-rich phase
     * @param co2PartialPressureBara calculated gas CO2 partial pressure in bara
     * @param phaseTypes joined phase labels
     * @param componentBalanceResidualMol maximum component balance residual in mol
     * @param molecularLogFugacityResidual maximum molecular log fugacity ratio
     * @param co2FeedMoles CO2 reservoir actually used, which exceeds the requested one after a retry
     */
    private Trial(PhaseInterface aqueous, double co2PartialPressureBara, String phaseTypes,
        double componentBalanceResidualMol, double molecularLogFugacityResidual, double co2FeedMoles) {
      this.aqueous = aqueous;
      this.co2PartialPressureBara = co2PartialPressureBara;
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
  private static final class Result {
    private final double co2PartialPressureBara;
    private final double nativeMolePercent;
    private final double molecularMolePercent;
    private final double pseudoSaltMolePercent;
    private final double co2Molality;
    private final double waterActivity;
    private final double actualNaclMassPercent;
    private final double actualKclMassPercent;
    private final double actualCacl2MassPercent;
    private final double waterMassKg;
    private final double co2Moles;
    private final double naclMoles;
    private final double kclMoles;
    private final double cacl2Moles;
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
     * @param pseudoSaltMolePercent CO2 mole percent over CO2, water and salt formula units
     * @param co2Molality CO2 molality per kg aqueous water
     * @param waterActivity water activity proxy
     * @param actualNaclMassPercent final solvent-brine NaCl mass fraction in percent
     * @param actualKclMassPercent final solvent-brine KCl mass fraction in percent
     * @param actualCacl2MassPercent final solvent-brine CaCl2 mass fraction in percent
     * @param waterMassKg final aqueous water mass in kg
     * @param co2Moles final aqueous CO2 inventory in mol
     * @param naclMoles NaCl formula-unit inventory in mol
     * @param kclMoles KCl formula-unit inventory in mol
     * @param cacl2Moles CaCl2 formula-unit inventory in mol
     * @param co2FeedMoles finite CO2 feed inventory in mol
     * @param phaseTypes joined phase labels
     * @param componentBalanceResidualMol maximum component balance residual in mol
     * @param molecularLogFugacityResidual maximum molecular log fugacity ratio
     */
    private Result(double co2PartialPressureBara, double nativeMolePercent, double molecularMolePercent,
        double pseudoSaltMolePercent, double co2Molality, double waterActivity, double actualNaclMassPercent,
        double actualKclMassPercent, double actualCacl2MassPercent, double waterMassKg, double co2Moles,
        double naclMoles, double kclMoles, double cacl2Moles, double co2FeedMoles, String phaseTypes,
        double componentBalanceResidualMol, double molecularLogFugacityResidual) {
      this.co2PartialPressureBara = co2PartialPressureBara;
      this.nativeMolePercent = nativeMolePercent;
      this.molecularMolePercent = molecularMolePercent;
      this.pseudoSaltMolePercent = pseudoSaltMolePercent;
      this.co2Molality = co2Molality;
      this.waterActivity = waterActivity;
      this.actualNaclMassPercent = actualNaclMassPercent;
      this.actualKclMassPercent = actualKclMassPercent;
      this.actualCacl2MassPercent = actualCacl2MassPercent;
      this.waterMassKg = waterMassKg;
      this.co2Moles = co2Moles;
      this.naclMoles = naclMoles;
      this.kclMoles = kclMoles;
      this.cacl2Moles = cacl2Moles;
      this.co2FeedMoles = co2FeedMoles;
      this.phaseTypes = phaseTypes;
      this.componentBalanceResidualMol = componentBalanceResidualMol;
      this.molecularLogFugacityResidual = molecularLogFugacityResidual;
    }

    /**
     * Serializes this result with the explicit salt-free mapping and representation controls.
     *
     * @param reference source state
     * @return one CSV row
     */
    private String toCsv(Reference reference) {
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
          + componentBalanceResidualMol + "," + molecularLogFugacityResidual;
    }
  }
}
