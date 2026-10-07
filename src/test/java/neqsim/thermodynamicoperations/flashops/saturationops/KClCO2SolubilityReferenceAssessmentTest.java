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
  private static final double TEMPERATURE_UNCERTAINTY_K = 0.1;

  /**
   * Executes the 273.15 K NIST series at its reported CO2 partial pressure without fitting.
   *
   * @throws Exception if the reference fixture or generated diagnostic cannot be read or written
   */
  @Tag("slow")
  @Test
  void nistKClSeriesReportsLikeForLikeAqueousCo2Molality() throws Exception {
    List<Reference> references = loadReferences();
    List<String> report = new ArrayList<String>();
    report.add("point_id,temperature_K,target_pco2_bara,actual_pco2_bara,kcl_molality_mol_per_kg_water,"
        + "experimental_co2_molality_mol_per_kg_water,model_co2_molality_mol_per_kg_water,model_minus_experiment,"
        + "relative_residual,experimental_relative_uncertainty,temperature_uncertainty_K,total_pressure_bara,"
        + "aqueous_x_co2,fluid_phases");

    double previousExperimentalMolality = Double.POSITIVE_INFINITY;
    double previousModelMolality = Double.NEGATIVE_INFINITY;
    for (Reference reference : references) {
      Result result = calculate(reference);
      assertTrue(reference.experimentalCo2Molality < previousExperimentalMolality,
          "NIST 273.15 K series must retain monotonic KCl salting-out ordering");
      assertEquals(reference.targetCo2PartialPressureBara, result.actualCo2PartialPressureBara, 1.0e-7,
          reference.pointId + " CO2 partial-pressure iteration did not converge");
      assertTrue(Double.isFinite(result.modelCo2Molality) && result.modelCo2Molality > 0.0,
          reference.pointId + " model CO2 molality must be finite and positive");
      assertTrue(result.modelCo2Molality > previousModelMolality,
          reference.pointId + " must retain the diagnosed model salting-in sequence opposite to experiment");
      report.add(result.toCsv(reference));
      previousExperimentalMolality = reference.experimentalCo2Molality;
      previousModelMolality = result.modelCo2Molality;
    }

    assertEquals(7, report.size());
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get("target/kcl-co2-solubility-he-morse1993-assessment.csv"), report, StandardCharsets.UTF_8);
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
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(reference.temperatureK,
        reference.targetCo2PartialPressureBara);
    fluid.addComponent("CO2", 10.0);
    fluid.addComponent("water", 1.0 / WATER_MOLAR_MASS_KG_PER_MOL);
    fluid.addComponent("K+", reference.kclMolality);
    fluid.addComponent("Cl-", reference.kclMolality);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);

    double actualPartialPressureBara = Double.NaN;
    for (int iteration = 0; iteration < 20; iteration++) {
      operations.TPflash();
      fluid.init(3);
      PhaseInterface gas = fluid.getPhase("gas");
      actualPartialPressureBara = gas.getComponent("CO2").getx() * fluid.getPressure();
      if (Math.abs(actualPartialPressureBara - reference.targetCo2PartialPressureBara) < 1.0e-10) {
        break;
      }
      fluid.setPressure(fluid.getPressure() * reference.targetCo2PartialPressureBara / actualPartialPressureBara);
    }
    operations.TPflash();
    fluid.init(3);

    PhaseInterface gas = fluid.getPhase("gas");
    PhaseInterface aqueous = findIonRichPhase(fluid);
    actualPartialPressureBara = gas.getComponent("CO2").getx() * fluid.getPressure();
    double modelCo2Molality = aqueous.getComponent("CO2").getMolality(aqueous);
    double aqueousCo2MoleFraction = aqueous.getComponent("CO2").getx();
    assertEquals(reference.kclMolality, aqueous.getComponent("K+").getNumberOfMolesInPhase(), 1.0e-12);
    assertEquals(reference.kclMolality, aqueous.getComponent("Cl-").getNumberOfMolesInPhase(), 1.0e-12);

    List<String> phaseTypes = new ArrayList<String>();
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      phaseTypes.add(fluid.getPhase(phaseIndex).getType().toString());
    }
    return new Result(actualPartialPressureBara, fluid.getPressure(), modelCo2Molality, aqueousCo2MoleFraction,
        String.join("+", phaseTypes));
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
   * Loads the source-pinned 273.15 K experimental series.
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
            Double.parseDouble(fields[4]), Double.parseDouble(fields[5])));
      }
    }
    assertEquals(6, references.size());
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
    private final double targetCo2PartialPressureBara;
    private final double experimentalCo2Molality;
    private final double kclMolality;

    /**
     * Creates an experimental state.
     *
     * @param pointId stable fixture identifier
     * @param temperatureK temperature in kelvin
     * @param co2PartialPressureAtm CO2 partial pressure in atmospheres
     * @param experimentalCo2Molality experimental CO2 molality
     * @param kclMolality KCl molality
     */
    private Reference(String pointId, double temperatureK, double co2PartialPressureAtm, double experimentalCo2Molality,
        double kclMolality) {
      this.pointId = pointId;
      this.temperatureK = temperatureK;
      this.targetCo2PartialPressureBara = co2PartialPressureAtm * ATM_TO_BAR;
      this.experimentalCo2Molality = experimentalCo2Molality;
      this.kclMolality = kclMolality;
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
    private final double aqueousCo2MoleFraction;
    private final String phaseTypes;

    /**
     * Creates calculated diagnostics.
     *
     * @param actualCo2PartialPressureBara actual CO2 partial pressure in bara
     * @param totalPressureBara total pressure in bara
     * @param modelCo2Molality calculated aqueous CO2 molality
     * @param aqueousCo2MoleFraction calculated aqueous CO2 mole fraction
     * @param phaseTypes phase topology
     */
    private Result(double actualCo2PartialPressureBara, double totalPressureBara, double modelCo2Molality,
        double aqueousCo2MoleFraction, String phaseTypes) {
      this.actualCo2PartialPressureBara = actualCo2PartialPressureBara;
      this.totalPressureBara = totalPressureBara;
      this.modelCo2Molality = modelCo2Molality;
      this.aqueousCo2MoleFraction = aqueousCo2MoleFraction;
      this.phaseTypes = phaseTypes;
    }

    /**
     * Serializes a deterministic diagnostic row.
     *
     * @param reference experimental state
     * @return CSV row
     */
    private String toCsv(Reference reference) {
      double residual = modelCo2Molality - reference.experimentalCo2Molality;
      return reference.pointId + "," + reference.temperatureK + "," + reference.targetCo2PartialPressureBara + ","
          + actualCo2PartialPressureBara + "," + reference.kclMolality + "," + reference.experimentalCo2Molality + ","
          + modelCo2Molality + "," + residual + "," + residual / reference.experimentalCo2Molality + ","
          + RELATIVE_SOLUBILITY_UNCERTAINTY + "," + TEMPERATURE_UNCERTAINTY_K + "," + totalPressureBara + ","
          + aqueousCo2MoleFraction + "," + phaseTypes;
    }
  }
}
