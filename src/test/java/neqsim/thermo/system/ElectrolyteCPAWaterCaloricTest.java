package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.NeqSimTest;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.component.attractiveeosterm.AttractiveTermCPAWaterCaloric;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.util.steam.Iapws_if97;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Checks conditions, model selection and reference data for the electrolyte-CPA caloric water alpha.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ElectrolyteCPAWaterCaloricTest extends NeqSimTest {
  private static final double ATMOSPHERIC_PRESSURE = 1.01325;
  private static final double WATER_KG_PER_MOL = 0.01801528;
  private static final double NACL_KG_PER_MOL = 0.05844277;
  private static final double KCL_KG_PER_MOL = 0.0745513;

  /**
   * Exercise the documented setup and toggle the alpha after caching reference phases on the original.
   */
  @Test
  void selectionSurvivesSaltAdditionAndCloneToggle() {
    SystemElectrolyteCPAstatoil original = flashBrine(298.15, ATMOSPHERIC_PRESSURE, "Na+", 1.0, false);
    PhaseInterface aqueous = original.getPhase("aqueous");
    double originalCp = aqueous.getCp("J/kgK");
    double originalActivity = waterActivity(aqueous);
    int waterIndex = aqueous.getComponent("water").getComponentNumber();
    assertFalse(original.isUsingCaloricWaterAlpha());

    SystemElectrolyteCPAstatoil copy = original.clone();
    copy.setUseCaloricWaterAlpha(true);
    flash(copy);
    PhaseInterface changed = copy.getPhase("aqueous");
    assertTrue(copy.isUsingCaloricWaterAlpha());
    assertTrue(changed.getCp("J/kgK") > originalCp);
    assertEquals(originalActivity, waterActivity(changed), 1.0e-4);
    assertEquals(23, changed.getRefPhase(waterIndex).getComponent(0).getAttractiveTermNumber());
    assertEquals(15, aqueous.getRefPhase(waterIndex).getComponent(0).getAttractiveTermNumber());
    assertFalse(original.isUsingCaloricWaterAlpha());

    copy.setUseCaloricWaterAlpha(false);
    flash(copy);
    assertFalse(copy.isUsingCaloricWaterAlpha());
    assertEquals(originalCp, copy.getPhase("aqueous").getCp("J/kgK"), 1.0e-5);

    SystemElectrolyteCPAstatoil documented = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    documented.setUseCaloricWaterAlpha(true);
    documented.addComponent("water", 55.508);
    documented.addComponent("Na+", 1.0);
    documented.addComponent("Cl-", 1.0);
    documented.createDatabase(true);
    documented.setMixingRule(10);
    flash(documented);
    PhaseInterface brine = documented.getPhase("aqueous");
    assertTrue(documented.isUsingCaloricWaterAlpha());
    assertTrue(brine.getComponent("water").getAttractiveTerm() instanceof AttractiveTermCPAWaterCaloric);
    assertFalse(brine.getComponent("Na+").getAttractiveTerm() instanceof AttractiveTermCPAWaterCaloric);
    assertEquals(aqueous.getComponent("Na+").getAttractiveTerm().aT(298.15),
        brine.getComponent("Na+").getAttractiveTerm().aT(298.15), 0.0);
    assertEquals(3887.46, brine.getCp("J/kgK"), 1.0);
    assertEquals(1036.15, brine.getWaterDensity("kg/m3"), 0.2);
  }

  /**
   * Exercise late addition of reaction species on a deserialized system with the caloric mode selected.
   *
   * @throws Exception if serialization or deserialization fails
   */
  @Test
  void serializedSelectionSurvivesReactionInitialization() throws Exception {
    SystemElectrolyteCPAstatoil fluid = new SystemElectrolyteCPAstatoil(323.15, 50.8);
    fluid.addComponent("water", 55.508);
    fluid.setUseCaloricWaterAlpha(true);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(fluid);
    }

    SystemElectrolyteCPAstatoil restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (SystemElectrolyteCPAstatoil) input.readObject();
    }
    restored.addComponent("Na+", 1.0);
    restored.addComponent("Cl-", 1.0);
    restored.addComponent("CO2", 10.0);
    restored.chemicalReactionInit();
    restored.createDatabase(true);
    restored.setMixingRule(10);
    restored.setMultiPhaseCheck(true);
    flash(restored);
    assertTrue(restored.isUsingCaloricWaterAlpha());
    PhaseInterface aqueous = restored.getPhase("aqueous");
    assertTrue(aqueous.hasComponent("H3O+"));
    assertTrue(aqueous.getComponent("water").getAttractiveTerm() instanceof AttractiveTermCPAWaterCaloric);
    assertTrue(Double.isFinite(aqueous.getpH()));
  }

  /**
   * Check Phillips et al. (LBL-12810, Appendix I, pp. 43 and 45) NaCl reference values at 25 and 50 degrees C.
   *
   * @param weightFraction NaCl mass fraction of solution
   * @param referenceCp25 reference heat capacity at 25 degrees C in J/(kg K)
   * @param referenceCp50 reference heat capacity at 50 degrees C in J/(kg K)
   * @param referenceEnthalpyRise reference change in specific enthalpy from 25 to 50 degrees C in J/kg
   */
  @ParameterizedTest
  @CsvSource({"0.02,4074.8,4081.3,101970", "0.05,3934.8,3946.9,98600", "0.10,3732.2,3748.4,93590"})
  void sodiumChlorideCaloricReference(double weightFraction, double referenceCp25, double referenceCp50,
      double referenceEnthalpyRise) {
    double molality = weightFraction / ((1.0 - weightFraction) * NACL_KG_PER_MOL);
    PhaseInterface cool = flashBrine(298.15, ATMOSPHERIC_PRESSURE, "Na+", molality, true).getPhase("aqueous");
    PhaseInterface warm = flashBrine(323.15, ATMOSPHERIC_PRESSURE, "Na+", molality, true).getPhase("aqueous");
    PhaseInterface coolLegacy = flashBrine(298.15, ATMOSPHERIC_PRESSURE, "Na+", molality, false).getPhase("aqueous");
    PhaseInterface warmLegacy = flashBrine(323.15, ATMOSPHERIC_PRESSURE, "Na+", molality, false).getPhase("aqueous");
    assertImproved(referenceCp25, cool.getCp("J/kgK"), coolLegacy.getCp("J/kgK"), 0.03);
    assertImproved(referenceCp50, warm.getCp("J/kgK"), warmLegacy.getCp("J/kgK"), 0.03);
    double enthalpyRise = warm.getEnthalpy("J/kg") - cool.getEnthalpy("J/kg");
    double legacyRise = warmLegacy.getEnthalpy("J/kg") - coolLegacy.getEnthalpy("J/kg");
    assertImproved(referenceEnthalpyRise, enthalpyRise, legacyRise, 0.03);
  }

  /**
   * Check KCl solution heat capacity using Partanen et al. (2019), Table 1 and Equation 6.
   *
   * @param molality mol KCl per kg water
   * @param apparentCp apparent molar salt heat capacity in J/(mol K)
   * @param referenceDensity literature solution density in kg/m3
   */
  @ParameterizedTest
  @CsvSource({"0.5,-87.7,1019.87", "1.0,-71.9,1041.45"})
  void potassiumChlorideCaloricReference(double molality, double apparentCp, double referenceDensity) {
    double reference = (4181.315 + molality * apparentCp) / (1.0 + molality * KCL_KG_PER_MOL);
    PhaseInterface legacy = flashBrine(298.15, ATMOSPHERIC_PRESSURE, "K+", molality, false).getPhase("aqueous");
    PhaseInterface improved = flashBrine(298.15, ATMOSPHERIC_PRESSURE, "K+", molality, true).getPhase("aqueous");
    assertImproved(reference, improved.getCp("J/kgK"), legacy.getCp("J/kgK"), 0.02);
    assertEquals(referenceDensity, improved.getWaterDensity("kg/m3"), referenceDensity * 0.0015);
  }

  /**
   * Check the sensible enthalpy increment of dilute KCl from solution calorimetry and pure-component references.
   */
  @Test
  void potassiumChlorideEnthalpyReference() {
    double molality = 0.2;
    double lowTemperature = 298.15;
    double highTemperature = 313.15;
    double pressureMpa = ATMOSPHERIC_PRESSURE * 0.1;
    double waterRise = 1000.0
        * (Iapws_if97.h_pt(pressureMpa, highTemperature) - Iapws_if97.h_pt(pressureMpa, lowTemperature));
    // Partanen et al. (2019), Part 1, Table 7 (ref. 49), and Tables 8-9 for the finite-molality terms.
    double solutionRise = (14803.0 + 599.0) - (17195.0 + 350.0);
    // NIST-JANAF Cl-036, J/(mol K). The solid reference contribution is small over this 15 K interval.
    double crystalRise = 51.287 * (highTemperature - lowTemperature);
    double referenceRise = (waterRise + molality * (crystalRise + solutionRise)) / (1.0 + molality * KCL_KG_PER_MOL);

    SystemInterface cool = flashBrine(lowTemperature, ATMOSPHERIC_PRESSURE, "K+", molality, true);
    SystemInterface warm = flashBrine(highTemperature, ATMOSPHERIC_PRESSURE, "K+", molality, true);
    SystemInterface coolLegacy = flashBrine(lowTemperature, ATMOSPHERIC_PRESSURE, "K+", molality, false);
    SystemInterface warmLegacy = flashBrine(highTemperature, ATMOSPHERIC_PRESSURE, "K+", molality, false);
    assertImproved(referenceRise, warm.getEnthalpy("J/kg") - cool.getEnthalpy("J/kg"),
        warmLegacy.getEnthalpy("J/kg") - coolLegacy.getEnthalpy("J/kg"), 0.02);
  }

  /**
   * Verify that finite differences of the enthalpy reproduce the fixed-composition liquid heat capacity.
   *
   * @param cation salt cation
   * @param pressure pressure in bara
   */
  @ParameterizedTest
  @CsvSource({"Na+,1.01325", "Na+,44.0", "K+,1.01325", "K+,44.0"})
  void brineHeatCapacityClosesWithEnthalpy(String cation, double pressure) {
    double temperature = 323.15;
    double step = 0.01;
    PhaseInterface centre = flashBrine(temperature, pressure, cation, 1.0, true).getPhase("aqueous");
    PhaseInterface plus = flashBrine(temperature + step, pressure, cation, 1.0, true).getPhase("aqueous");
    PhaseInterface minus = flashBrine(temperature - step, pressure, cation, 1.0, true).getPhase("aqueous");
    double hDerivative = (plus.getEnthalpy("J/kg") - minus.getEnthalpy("J/kg")) / (2.0 * step);
    assertEquals(centre.getCp("J/kgK"), hDerivative, 0.005);
  }

  /**
   * Compare water activity with Partanen and Partanen (2020) and density with the Advanced Thermodynamics NaCl table.
   *
   * @param molality mol NaCl per kg water
   * @param osmoticCoefficient literature molality-scale osmotic coefficient
   * @param referenceDensity reference density at 25 degrees C and 1 bar in kg/m3
   */
  @ParameterizedTest
  @CsvSource({"1.0,0.940,1036.21", "2.0,0.989,1072.27", "3.0,1.047,1105.79"})
  void saltWaterActivityAndDensityReference(double molality, double osmoticCoefficient, double referenceDensity) {
    double expectedActivity = Math.exp(-2.0 * molality * WATER_KG_PER_MOL * osmoticCoefficient);
    PhaseInterface legacy = flashBrine(298.15, ATMOSPHERIC_PRESSURE, "Na+", molality, false).getPhase("aqueous");
    PhaseInterface improved = flashBrine(298.15, ATMOSPHERIC_PRESSURE, "Na+", molality, true).getPhase("aqueous");
    assertEquals(expectedActivity, waterActivity(improved), 0.004);
    assertEquals(waterActivity(legacy), waterActivity(improved), 1.0e-4);
    assertEquals(referenceDensity, improved.getWaterDensity("kg/m3"), referenceDensity * 0.0015);
    assertEquals(legacy.getDensity("kg/m3"), improved.getDensity("kg/m3"), 0.15);
  }

  /**
   * Compare dissolved molecular CO2 with Messabeb et al. (2016) and retain a PH closure check on the same two phases.
   */
  @Test
  void carbonDioxideSolubilityAndPhFlashReference() {
    SystemElectrolyteCPAstatoil fluid = flashReactiveBrine(50.3, 1.0, true);
    assertEquals(2, fluid.getNumberOfPhases());
    PhaseInterface aqueous = fluid.getPhase("aqueous");
    double co2Molality = aqueous.getComponent("CO2").getMolality(aqueous);
    // NIST ThermoML row: 323.15 K, 5030 kPa, 1 mol NaCl/kg water, 0.650 +/- 0.058 mol CO2/kg water.
    assertEquals(0.650, co2Molality, 0.058);
    double enthalpy = fluid.getEnthalpy();
    fluid.setTemperature(325.15);
    new ThermodynamicOperations(fluid).PHflash(enthalpy);
    fluid.initProperties();
    assertEquals(323.15, fluid.getTemperature(), 1.0e-5);
    assertEquals(enthalpy, fluid.getEnthalpy(), 0.01);
  }

  /**
   * Quantify the change in default reported pH against the measured CO2-saturated brine states in Haghi et al. Table 5.
   *
   * @param pressure pressure in bara
   * @param molality mol NaCl per kg water
   * @param measuredPH reported spectroscopic pH
   */
  @ParameterizedTest
  @CsvSource({"50.8,1.0,3.17", "44.5,2.0,3.18"})
  void reactiveCarbonDioxideBrinePHComparison(double pressure, double molality, double measuredPH) {
    double legacy = flashReactiveBrine(pressure, molality, false).getPhase("aqueous").getpH();
    double caloric = flashReactiveBrine(pressure, molality, true).getPhase("aqueous").getpH();
    assertEquals(legacy - measuredPH, caloric - measuredPH, 0.01,
        "The alpha change should preserve the measured comparison within 0.01 pH units");
    assertTrue(Double.isFinite(caloric));
  }

  /**
   * Assert that the new result meets its reference tolerance and improves on the default.
   *
   * @param reference independent value
   * @param caloric result with caloric alpha
   * @param legacy result with legacy alpha
   * @param relativeTolerance allowed error as a fraction of the reference
   */
  private static void assertImproved(double reference, double caloric, double legacy, double relativeTolerance) {
    assertEquals(reference, caloric, reference * relativeTolerance);
    assertTrue(Math.abs(caloric - reference) < Math.abs(legacy - reference));
  }

  /**
   * Compute the solvent activity relative to pure liquid water at the phase temperature and pressure.
   *
   * @param phase initialized brine phase
   * @return water activity
   */
  private static double waterActivity(PhaseInterface phase) {
    ComponentInterface water = phase.getComponent("water");
    return water.getx() * phase.getActivityCoefficientSymetric(water.getComponentNumber());
  }

  /**
   * Build and flash a binary brine.
   *
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @param cation Na+ or K+
   * @param molality salt amount in mol per kg water
   * @param caloric whether to use the new alpha
   * @return initialized system
   */
  private static SystemElectrolyteCPAstatoil flashBrine(double temperature, double pressure, String cation,
      double molality, boolean caloric) {
    SystemElectrolyteCPAstatoil fluid = new SystemElectrolyteCPAstatoil(temperature, pressure);
    fluid.setUseCaloricWaterAlpha(caloric);
    fluid.addComponent("water", 1.0 / WATER_KG_PER_MOL);
    fluid.addComponent(cation, molality);
    fluid.addComponent("Cl-", molality);
    fluid.setMixingRule(10);
    flash(fluid);
    assertEquals(1, fluid.getNumberOfPhases());
    return fluid;
  }

  /**
   * Build and flash sodium chloride brine in contact with excess carbon dioxide.
   *
   * @param pressure pressure in bara
   * @param molality salt amount in mol per kg water
   * @param caloric whether to use the new alpha
   * @return initialized reactive system
   */
  private static SystemElectrolyteCPAstatoil flashReactiveBrine(double pressure, double molality, boolean caloric) {
    SystemElectrolyteCPAstatoil fluid = new SystemElectrolyteCPAstatoil(323.15, pressure);
    fluid.setUseCaloricWaterAlpha(caloric);
    fluid.addComponent("water", 1.0 / WATER_KG_PER_MOL);
    fluid.addComponent("Na+", molality);
    fluid.addComponent("Cl-", molality);
    fluid.addComponent("CO2", 10.0);
    fluid.chemicalReactionInit();
    fluid.createDatabase(true);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    flash(fluid);
    return fluid;
  }

  /**
   * Flash at the system temperature and pressure and initialize property derivatives.
   *
   * @param fluid configured fluid
   */
  private static void flash(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
  }
}
