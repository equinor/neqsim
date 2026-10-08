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
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * No-fit comparison with the Yasunishi-Yoshida CO2-in-KCl Ostwald data compiled by NIST SRD 106.
 *
 * @author OpenAI
 * @version 1.0
 */
class KClCO2OstwaldReferenceAssessmentTest {
  private static final Logger logger = LogManager.getLogger(KClCO2OstwaldReferenceAssessmentTest.class);
  private static final String RESOURCE = "/data/chemistry_benchmarks/co2_kcl_ostwald_yasunishi_yoshida1979.csv";
  private static final double WATER_MOLAR_MASS_KG_PER_MOL = 0.01801528;

  /**
   * Executes the unambiguous 298.15 K NIST series in its source-reported Ostwald observable.
   *
   * @throws Exception if the reference fixture or generated diagnostic cannot be read or written
   */
  @Tag("slow")
  @Test
  void nistKClSeriesReportsLikeForLikeOstwaldCoefficient() throws Exception {
    List<Reference> references = loadReferences();
    List<String> report = new ArrayList<String>();
    report.add("point_id,temperature_K,total_pressure_bara,target_kcl_mol_per_L,"
        + "actual_kcl_mol_per_L,state_matched,state_note,experimental_ostwald_coefficient,"
        + "model_ostwald_coefficient,model_minus_experiment,relative_residual,experimental_relative_uncertainty,"
        + "temperature_uncertainty_K,aqueous_co2_mol_per_L,gas_co2_mol_per_L," + "aqueous_water_mass_kg,fluid_phases");

    double previousExperimentalOstwald = Double.POSITIVE_INFINITY;
    int matchedStates = 0;
    for (Reference reference : references) {
      Result result = calculate(reference);
      assertTrue(reference.experimentalOstwald < previousExperimentalOstwald,
          "NIST 298.15 K series must retain monotonic KCl salting-out ordering");
      if (result.stateMatched) {
        assertEquals(reference.kclConcentrationMolPerL, result.actualKclConcentrationMolPerL, 1.0e-8,
            reference.pointId + " KCl concentration iteration did not converge");
        matchedStates++;
      }
      assertTrue(Double.isFinite(result.modelOstwald) && result.modelOstwald > 0.0,
          reference.pointId + " model Ostwald coefficient must be finite and positive");
      assertTrue(result.aqueousCo2ConcentrationMolPerL > 0.0,
          reference.pointId + " aqueous CO2 concentration must be positive");
      assertTrue(result.gasCo2ConcentrationMolPerL > 0.0,
          reference.pointId + " gas CO2 concentration must be positive");
      report.add(result.toCsv(reference));
      previousExperimentalOstwald = reference.experimentalOstwald;
    }

    assertTrue(matchedStates >= 6, "at least six independent source states must be constructed exactly");
    assertEquals(8, report.size());
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get("target/kcl-co2-ostwald-yasunishi-yoshida1979-assessment.csv"), report,
        StandardCharsets.UTF_8);
    for (String row : report) {
      logger.info("KCL_CO2_OSTWALD_DIAGNOSTIC,{}", row);
    }
  }

  /**
   * Calculates one experimental state and iterates KCl moles to the source concentration basis.
   *
   * @param reference experimental state
   * @return calculated diagnostics
   */
  private static Result calculate(Reference reference) {
    double kclMoles = reference.kclConcentrationMolPerL;
    Result bestResult = null;
    double bestError = Double.POSITIVE_INFINITY;
    for (int iteration = 0; iteration < 40; iteration++) {
      Result result;
      try {
        result = calculateAtKclMoles(reference, kclMoles);
      } catch (RuntimeException exception) {
        break;
      }
      double concentrationError = Math.abs(result.actualKclConcentrationMolPerL - reference.kclConcentrationMolPerL);
      if (concentrationError < bestError) {
        bestResult = result;
        bestError = concentrationError;
      }
      if (reference.kclConcentrationMolPerL == 0.0 || concentrationError < 1.0e-10) {
        return result.withStateMatch(true, "MATCHED_SOURCE_CONCENTRATION");
      }
      if (result.actualKclConcentrationMolPerL < 1.0e-12) {
        break;
      }
      double scale = reference.kclConcentrationMolPerL / result.actualKclConcentrationMolPerL;
      scale = Math.max(0.97, Math.min(1.03, scale));
      kclMoles *= scale;
    }
    assertNotNull(bestResult);
    return bestResult.withStateMatch(false, "TARGET_CONCENTRATION_NOT_REACHED");
  }

  /**
   * Calculates one state for a trial KCl inventory.
   *
   * @param reference experimental state
   * @param kclMoles trial moles of both potassium and chloride
   * @return calculated diagnostics
   */
  private static Result calculateAtKclMoles(Reference reference, double kclMoles) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(reference.temperatureK, reference.totalPressureBara);
    fluid.addComponent("CO2", 10.0);
    fluid.addComponent("water", 1.0 / WATER_MOLAR_MASS_KG_PER_MOL);
    if (kclMoles > 0.0) {
      fluid.addComponent("K+", kclMoles);
      fluid.addComponent("Cl-", kclMoles);
    }
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.init(3);

    PhaseInterface gas = fluid.getPhase("gas");
    PhaseInterface aqueous = findWaterRichPhase(fluid);
    double aqueousVolumeLitres = aqueous.getVolume("litre");
    double gasVolumeLitres = gas.getVolume("litre");
    assertTrue(aqueousVolumeLitres > 0.0, "aqueous phase volume must be positive");
    assertTrue(gasVolumeLitres > 0.0, "gas phase volume must be positive");

    double actualKclConcentrationMolPerL = kclMoles == 0.0 ? 0.0
        : aqueous.getComponent("K+").getNumberOfMolesInPhase() / aqueousVolumeLitres;
    double aqueousCo2ConcentrationMolPerL = aqueous.getComponent("CO2").getNumberOfMolesInPhase() / aqueousVolumeLitres;
    double gasCo2ConcentrationMolPerL = gas.getComponent("CO2").getNumberOfMolesInPhase() / gasVolumeLitres;
    double modelOstwald = aqueousCo2ConcentrationMolPerL / gasCo2ConcentrationMolPerL;
    double aqueousWaterMassKg = aqueous.getComponent("water").getNumberOfMolesInPhase() * WATER_MOLAR_MASS_KG_PER_MOL;

    if (kclMoles > 0.0) {
      assertEquals(aqueous.getComponent("K+").getNumberOfMolesInPhase(),
          aqueous.getComponent("Cl-").getNumberOfMolesInPhase(), 1.0e-12);
    }
    List<String> phaseTypes = new ArrayList<String>();
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      phaseTypes.add(fluid.getPhase(phaseIndex).getType().toString());
    }
    return new Result(actualKclConcentrationMolPerL, modelOstwald, aqueousCo2ConcentrationMolPerL,
        gasCo2ConcentrationMolPerL, aqueousWaterMassKg, String.join("+", phaseTypes), false, "UNCLASSIFIED");
  }

  /**
   * Selects the phase with the greatest water mole fraction.
   *
   * @param fluid flashed system
   * @return water-rich aqueous phase
   */
  private static PhaseInterface findWaterRichPhase(SystemInterface fluid) {
    PhaseInterface selected = null;
    double largestWaterFraction = -1.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface candidate = fluid.getPhase(phaseIndex);
      if (candidate.hasComponent("water") && candidate.getComponent("water").getx() > largestWaterFraction) {
        selected = candidate;
        largestWaterFraction = candidate.getComponent("water").getx();
      }
    }
    assertNotNull(selected);
    return selected;
  }

  /**
   * Loads the source-pinned 298.15 K experimental series.
   *
   * @return experimental rows
   * @throws Exception if the fixture cannot be read or parsed
   */
  private static List<Reference> loadReferences() throws Exception {
    InputStream stream = KClCO2OstwaldReferenceAssessmentTest.class.getResourceAsStream(RESOURCE);
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
    assertEquals(7, references.size());
    return references;
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
    private final double totalPressureBara;
    private final double kclConcentrationMolPerL;
    private final double experimentalOstwald;
    private final double relativeSolubilityUncertainty;
    private final double temperatureUncertaintyK;

    /**
     * Creates an experimental state.
     *
     * @param pointId stable fixture identifier
     * @param temperatureK temperature in kelvin
     * @param totalPressureKpa total pressure in kilopascals
     * @param kclConcentrationMolPerL KCl concentration in moles per litre of solution
     * @param experimentalOstwald experimental Ostwald coefficient
     * @param relativeSolubilityUncertainty relative solubility uncertainty
     * @param temperatureUncertaintyK temperature uncertainty in kelvin
     */
    private Reference(String pointId, double temperatureK, double totalPressureKpa, double kclConcentrationMolPerL,
        double experimentalOstwald, double relativeSolubilityUncertainty, double temperatureUncertaintyK) {
      this.pointId = pointId;
      this.temperatureK = temperatureK;
      this.totalPressureBara = totalPressureKpa / 100.0;
      this.kclConcentrationMolPerL = kclConcentrationMolPerL;
      this.experimentalOstwald = experimentalOstwald;
      this.relativeSolubilityUncertainty = relativeSolubilityUncertainty;
      this.temperatureUncertaintyK = temperatureUncertaintyK;
    }
  }

  /**
   * Calculated state.
   *
   * @author OpenAI
   * @version 1.0
   */
  private static final class Result {
    private final double actualKclConcentrationMolPerL;
    private final double modelOstwald;
    private final double aqueousCo2ConcentrationMolPerL;
    private final double gasCo2ConcentrationMolPerL;
    private final double aqueousWaterMassKg;
    private final String phaseTypes;
    private final boolean stateMatched;
    private final String stateNote;

    /**
     * Creates calculated diagnostics.
     *
     * @param actualKclConcentrationMolPerL calculated KCl concentration
     * @param modelOstwald calculated Ostwald coefficient
     * @param aqueousCo2ConcentrationMolPerL aqueous molecular CO2 concentration
     * @param gasCo2ConcentrationMolPerL gas-phase molecular CO2 concentration
     * @param aqueousWaterMassKg aqueous water mass in kilograms
     * @param phaseTypes phase topology
     * @param stateMatched whether the source KCl concentration was matched
     * @param stateNote state-construction classification
     */
    private Result(double actualKclConcentrationMolPerL, double modelOstwald, double aqueousCo2ConcentrationMolPerL,
        double gasCo2ConcentrationMolPerL, double aqueousWaterMassKg, String phaseTypes, boolean stateMatched,
        String stateNote) {
      this.actualKclConcentrationMolPerL = actualKclConcentrationMolPerL;
      this.modelOstwald = modelOstwald;
      this.aqueousCo2ConcentrationMolPerL = aqueousCo2ConcentrationMolPerL;
      this.gasCo2ConcentrationMolPerL = gasCo2ConcentrationMolPerL;
      this.aqueousWaterMassKg = aqueousWaterMassKg;
      this.phaseTypes = phaseTypes;
      this.stateMatched = stateMatched;
      this.stateNote = stateNote;
    }

    /**
     * Returns this numerical state with its source-coordinate classification.
     *
     * @param matched whether the source KCl concentration was matched
     * @param note state-construction classification
     * @return classified result
     */
    private Result withStateMatch(boolean matched, String note) {
      return new Result(actualKclConcentrationMolPerL, modelOstwald, aqueousCo2ConcentrationMolPerL,
          gasCo2ConcentrationMolPerL, aqueousWaterMassKg, phaseTypes, matched, note);
    }

    /**
     * Serializes a deterministic diagnostic row.
     *
     * @param reference experimental state
     * @return CSV row
     */
    private String toCsv(Reference reference) {
      double residual = stateMatched ? modelOstwald - reference.experimentalOstwald : Double.NaN;
      return reference.pointId + "," + reference.temperatureK + "," + reference.totalPressureBara + ","
          + reference.kclConcentrationMolPerL + "," + actualKclConcentrationMolPerL + "," + stateMatched + ","
          + stateNote + "," + reference.experimentalOstwald + "," + modelOstwald + "," + residual + ","
          + (stateMatched ? residual / reference.experimentalOstwald : Double.NaN) + ","
          + reference.relativeSolubilityUncertainty + "," + reference.temperatureUncertaintyK + ","
          + aqueousCo2ConcentrationMolPerL + "," + gasCo2ConcentrationMolPerL + "," + aqueousWaterMassKg + ","
          + phaseTypes;
    }
  }
}
