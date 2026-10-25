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
import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.mixingrule.CPAMixingRuleHandler;
import neqsim.thermo.mixingrule.EosMixingRuleHandler;
import neqsim.thermo.phase.PhaseCPAInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * No-fit salt-free CO2 assessment against the NIST evaluation of Carroll et al. (1991).
 *
 * <p>
 * Numerical gates do not qualify physical accuracy. Explicit zero ions preserve the diagnosed electrolyte-CPA
 * construction; ion-omitted precision failures remain tracked in issue 4234.
 * </p>
 *
 * @author OpenAI
 * @version 1.0
 */
class CO2WaterBaselineReferenceAssessmentTest {
  private static final Logger logger = LogManager.getLogger(CO2WaterBaselineReferenceAssessmentTest.class);
  private static final String RESOURCE = "/data/chemistry_benchmarks/co2_water_carroll1991_evaluation.csv";
  private static final double WATER_MOLAR_MASS = 0.01801528;

  /**
   * Freezes the evaluated reference column, not the inconsistent displayed web correlation.
   *
   * @throws Exception if the fixture cannot be read
   */
  @Test
  void referencePreservesPartialPressureAndEvaluatedColumn() throws Exception {
    List<Reference> references = loadReferences();
    double[] values = {0.001355, 0.001136, 0.000963, 0.000825, 0.000713, 0.000622, 0.000548, 0.000487, 0.000437,
        0.000395, 0.000359, 0.000329, 0.000304, 0.000283, 0.000264, 0.000249, 0.000236, 0.000224, 0.000214};
    for (int index = 0; index < references.size(); index++) {
      Reference reference = references.get(index);
      assertEquals(273.15 + 5.0 * index, reference.temperature, 1.0e-12);
      assertEquals(1.01325, reference.partialPressure, 0.0);
      assertEquals(values[index], reference.moleFraction, 0.0);
    }
  }

  /**
   * Retains all 57 setup variants before asserting numerical gates, without an accuracy pass band.
   *
   * @throws Exception if references, calculations or output cannot be processed
   */
  @Tag("slow")
  @Test
  void saltFreeBaselineRetainsPhysicalResidualsAndRuntimePath() throws Exception {
    List<String> rows = new ArrayList<String>();
    rows.add("point_id,construction,temperature_K,target_pco2_bara,total_pressure_bara,actual_pco2_bara,"
        + "evaluated_reference_x_co2,model_x_co2,relative_x_residual,guest_fugacity_bara,"
        + "apparent_f_over_x_bara,molecular_log_fugacity_residual,component_balance_residual,"
        + "repeat_x_residual,phase_normalization_residual,charge_residual_mol,phase_count,"
        + "fluid_phases,numerical_checks_passed,eos_rule_class,k0,kT,k_effective,"
        + "co2_scheme,co2_sites,water_scheme,water_sites,cross_beta,cross_energy_J_per_mol,"
        + "co2_unbonded_fraction,temporary_tables");
    List<Boolean> dispositions = new ArrayList<Boolean>();
    for (Reference reference : loadReferences()) {
      for (int construction = 0; construction < 3; construction++) {
        dispositions.add(calculate(reference, construction, rows));
      }
    }
    assertEquals(58, rows.size());
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get("target/co2-water-baseline-carroll1991-assessment.csv"), rows, StandardCharsets.UTF_8);
    for (String row : rows) {
      logger.info("CO2_WATER_BASELINE_DIAGNOSTIC,{}", row);
    }
    assertTrue(dispositions.stream().allMatch(Boolean::booleanValue),
        "Numerical gate failure: every completed row is retained; physical accuracy is not assessed");
  }

  /**
   * Calculates a source-matched state and reports unchanged strict numerical gates.
   *
   * @param reference evaluated state on the CO2 partial-pressure basis
   * @param construction zero for baseline, one for database reset, two for reversed insertion
   * @param rows output records retained before the final assertion
   * @return whether the numerical gates pass, independently of the reference residual
   */
  private static boolean calculate(Reference reference, int construction, List<String> rows) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(reference.temperature, reference.partialPressure);
    if (construction == 2) {
      fluid.addComponent("water", 1.0 / WATER_MOLAR_MASS);
      fluid.addComponent("CO2", 10.0);
    } else {
      fluid.addComponent("CO2", 10.0);
      fluid.addComponent("water", 1.0 / WATER_MOLAR_MASS);
    }
    fluid.addComponent("K+", 0.0);
    fluid.addComponent("Cl-", 0.0);
    if (construction == 1) {
      fluid.createDatabase(true);
    }
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    for (int iteration = 0; iteration < 20; iteration++) {
      operations.TPflash();
      fluid.init(3);
      double actual = fluid.getPhase("gas").getComponent("CO2").getx() * fluid.getPressure();
      if (Math.abs(actual - reference.partialPressure) < 1.0e-10) {
        break;
      }
      fluid.setPressure(fluid.getPressure() * reference.partialPressure / actual);
    }
    operations.TPflash();
    fluid.init(3);
    PhaseInterface gas = fluid.getPhase("gas");
    PhaseInterface aqueous = fluid.getPhase("aqueous");
    double actual = gas.getComponent("CO2").getx() * fluid.getPressure();
    double x = aqueous.getComponent("CO2").getx();
    double fugacity = actual * gas.getComponent("CO2").getFugacityCoefficient();
    double logFugacity = 0.0;
    double balance = 0.0;
    double normalization = 0.0;
    double betaSum = 0.0;
    double charge = 0.0;
    StringBuilder phases = new StringBuilder();
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      if (phaseIndex > 0) {
        phases.append("+");
      }
      phases.append(phase.getType());
      betaSum += phase.getBeta();
      double sum = 0.0;
      for (int component = 0; component < phase.getNumberOfComponents(); component++) {
        double fraction = phase.getComponent(component).getx();
        if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
          normalization = Double.POSITIVE_INFINITY;
        }
        sum += fraction;
        charge += phase.getComponent(component).getNumberOfMolesInPhase()
            * phase.getComponent(component).getIonicCharge();
      }
      normalization = Math.max(normalization, Math.abs(sum - 1.0));
      if (!Double.isFinite(phase.getBeta()) || phase.getBeta() < 0.0 || phase.getBeta() > 1.0) {
        normalization = Double.POSITIVE_INFINITY;
      }
    }
    normalization = Math.max(normalization, Math.abs(betaSum - 1.0));
    for (String species : new String[] {"CO2", "water", "K+", "Cl-"}) {
      double original = "CO2".equals(species) ? 10.0 : "water".equals(species) ? 1.0 / WATER_MOLAR_MASS : 0.0;
      double sum = 0.0;
      for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
        sum += fluid.getPhase(phaseIndex).getComponent(species).getNumberOfMolesInPhase();
      }
      balance = Math.max(balance, Math.abs(sum - original) / Math.max(1.0, original));
      if (original > 0.0) {
        double gasF = gas.getComponent(species).getx() * gas.getComponent(species).getFugacityCoefficient();
        double aqF = aqueous.getComponent(species).getx() * aqueous.getComponent(species).getFugacityCoefficient();
        logFugacity = Math.max(logFugacity, Math.abs(Math.log(aqF / gasF)));
      }
    }
    operations.TPflash();
    fluid.init(3);
    double repeat = Math.abs(fluid.getPhase("aqueous").getComponent("CO2").getx() - x);
    boolean passed = fluid.getNumberOfPhases() == 2 && "GAS+AQUEOUS".equals(phases.toString()) && Double.isFinite(x)
        && x > 0.0 && x < 1.0 && Double.isFinite(fugacity) && fugacity > 0.0
        && Math.abs(actual - reference.partialPressure) <= 1.0e-7 && Double.isFinite(logFugacity)
        && logFugacity <= 1.0e-8 && balance <= 1.0e-12 && normalization <= 1.0e-12 && Math.abs(charge) <= 1.0e-12
        && repeat <= 1.0e-10;
    int co2 = aqueous.getComponent("CO2").getComponentNumber();
    int water = aqueous.getComponent("water").getComponentNumber();
    PhaseCPAInterface cpa = (PhaseCPAInterface) aqueous;
    EosMixingRuleHandler.ClassicSRKT2 eos = (EosMixingRuleHandler.ClassicSRKT2) cpa.getEosMixingRule();
    CPAMixingRuleHandler.CPA_Radoch association = (CPAMixingRuleHandler.CPA_Radoch) cpa.getCpaMixingRule();
    String configuration = construction == 0 ? "BASELINE" : construction == 1 ? "DATABASE_RESET" : "WATER_FIRST";
    rows.add(reference.id + "," + configuration + "," + reference.temperature + "," + reference.partialPressure + ","
        + fluid.getPressure() + "," + actual + "," + reference.moleFraction + "," + x + ","
        + (x / reference.moleFraction - 1.0) + "," + fugacity + "," + fugacity / x + "," + logFugacity + "," + balance
        + "," + repeat + "," + normalization + "," + charge + "," + fluid.getNumberOfPhases() + "," + phases + ","
        + passed + "," + eos.getClass().getSimpleName() + "," + eos.getBinaryInteractionParameter(co2, water) + ","
        + eos.getBinaryInteractionParameterT1(co2, water) + "," + eos.getkij(reference.temperature, co2, water) + ","
        + aqueous.getComponent(co2).getAssociationScheme() + ","
        + aqueous.getComponent(co2).getNumberOfAssociationSites() + ","
        + aqueous.getComponent(water).getAssociationScheme() + ","
        + aqueous.getComponent(water).getNumberOfAssociationSites() + ","
        + association.getCrossAssociationVolume(co2, water, aqueous, reference.temperature, fluid.getPressure(),
            fluid.getNumberOfComponents())
        + ","
        + association.getCrossAssociationEnergy(co2, water, aqueous, reference.temperature, fluid.getPressure(),
            fluid.getNumberOfComponents())
        + "," + ((ComponentCPAInterface) aqueous.getComponent(co2)).getXsite()[0] + ","
        + neqsim.util.database.NeqSimDataBase.createTemporaryTables());
    return passed;
  }

  /**
   * Loads the nineteen explicitly tabulated evaluated model values.
   *
   * @return source states, not a newly fitted correlation
   * @throws Exception if the fixture cannot be read or parsed
   */
  private static List<Reference> loadReferences() throws Exception {
    InputStream stream = CO2WaterBaselineReferenceAssessmentTest.class.getResourceAsStream(RESOURCE);
    assertNotNull(stream, RESOURCE);
    List<Reference> references = new ArrayList<Reference>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("#") || line.startsWith("point_id") || line.trim().isEmpty()) {
          continue;
        }
        String[] fields = line.split(",");
        assertEquals(4, fields.length);
        references.add(new Reference(fields[0], Double.parseDouble(fields[1]), Double.parseDouble(fields[2]),
            Double.parseDouble(fields[3])));
      }
    }
    assertEquals(19, references.size());
    return references;
  }

  /**
   * Immutable source state.
   *
   * @author OpenAI
   * @version 1.0
   */
  private static final class Reference {
    private final String id;
    private final double temperature;
    private final double partialPressure;
    private final double moleFraction;

    /**
     * Creates a reference without rescaling pressure or concentration.
     *
     * @param id source row identifier
     * @param temperature temperature in kelvin
     * @param partialPressure CO2 partial pressure in bara
     * @param moleFraction evaluated liquid CO2 mole fraction
     */
    private Reference(String id, double temperature, double partialPressure, double moleFraction) {
      this.id = id;
      this.temperature = temperature;
      this.partialPressure = partialPressure;
      this.moleFraction = moleFraction;
    }
  }
}
