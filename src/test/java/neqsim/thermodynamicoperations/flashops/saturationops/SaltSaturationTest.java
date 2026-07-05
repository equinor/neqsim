package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests salt saturation and scale-potential calculations for electrolyte CPA systems.
 *
 * @author ESOL
 * @version $Id$
 */
class SaltSaturationTest {
  private static final double SATURATION_RATIO_TOLERANCE = 1.0e-3;
  private static final double PLUMMER_BUSENBERG_CALCITE_KSP_TOLERANCE = 1.0e-8;

  /**
   * Verifies that scale-potential calculations report activity-based saturation ratios for the mixed chloride brine
   * case.
   *
   * @throws Exception if the thermodynamic operation fails
   */
  @Test
  void checkScalePotentialReportsActivityBasedChlorideSaturation() throws Exception {
    SystemInterface system = createMixedChlorideBrine();
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();
    system.initProperties();

    operations.checkScalePotential(system.getPhaseNumberOfPhase("aqueous"));
    String[][] table = operations.getResultTable();

    double naclSaturationRatio = getSaltSaturationRatio(table, "NaCl");
    double kclSaturationRatio = getSaltSaturationRatio(table, "KCl");
    double cacl2SaturationRatio = getSaltSaturationRatio(table, "CaCl2");
    double mgcl2SaturationRatio = getSaltSaturationRatio(table, "MgCl2");

    assertTrue(naclSaturationRatio > 0.0 && naclSaturationRatio < 1.0,
        "NaCl should remain below activity-based saturation for the mixed brine");
    assertTrue(kclSaturationRatio > 1.0, "KCl should be supersaturated in the activity-based mixed brine screening");
    assertTrue(cacl2SaturationRatio > 0.0 && Double.isFinite(cacl2SaturationRatio),
        "CaCl2 should be reported with a finite activity-based saturation ratio");
    assertTrue(mgcl2SaturationRatio > 0.0 && Double.isFinite(mgcl2SaturationRatio),
        "MgCl2 should be reported with a finite activity-based saturation ratio");
  }

  /**
   * Verifies that the convenience accessor returns the same activity-based saturation ratio as the result table.
   *
   * @throws Exception if the thermodynamic operation fails
   */
  @Test
  void getRelativeScalePotentialReturnsSingleSaltRatio() throws Exception {
    SystemInterface system = createMixedChlorideBrine();
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();
    system.initProperties();

    operations.checkScalePotential(system.getPhaseNumberOfPhase("aqueous"));
    double expectedNaClRatio = getSaltSaturationRatio(operations.getResultTable(), "NaCl");
    double directNaClRatio = operations.getRelativeScalePotential("NaCl");

    assertEquals(expectedNaClRatio, directNaClRatio, 1.0e-12);
  }

  /**
   * Verifies that CaCO3 scale potential can be evaluated in a gas-aqueous CO2 system with carbonate reactions and that
   * the implied calcite solubility product matches Plummer and Busenberg, Geochimica et Cosmochimica Acta 46, 1011-1040
   * (1982).
   *
   * @throws Exception if the thermodynamic operation fails
   */
  @Test
  void getRelativeScalePotentialHandlesCaCO3WithCo2GasReactions() throws Exception {
    SystemInterface system = createCalciumCarbonateCo2GasAqueousSystem();
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();
    system.initProperties();

    assertTrue(system.hasPhaseType("gas"), "CO2-bearing feed should retain a gas phase");
    assertTrue(system.hasPhaseType("aqueous"), "CO2-bearing feed should retain an aqueous phase");

    double bicarbonateMoles = getAqueousComponentMoles(system, "HCO3-");
    double carbonateMoles = getAqueousComponentMoles(system, "CO3--");
    assertTrue(bicarbonateMoles > 0.0 || carbonateMoles > 0.0,
        "CO2 reactions should form bicarbonate or carbonate species in the aqueous phase");

    double caco3ScalePotential = operations.getRelativeScalePotential("CaCO3");
    assertTrue(Double.isFinite(caco3ScalePotential), "CaCO3 scale potential should be finite");
    assertTrue(caco3ScalePotential > 0.0, "CaCO3 scale potential should be positive for Ksp comparison");

    double recoveredCalciteKsp = getRecoveredCalciteKsp(system, caco3ScalePotential);
    double literatureCalciteKsp = calciteKspPlummerBusenberg1982(system.getTemperature());
    assertEquals(literatureCalciteKsp, recoveredCalciteKsp,
        literatureCalciteKsp * PLUMMER_BUSENBERG_CALCITE_KSP_TOLERANCE,
        "Recovered CaCO3 Ksp should match the Plummer-Busenberg calcite correlation");
  }

  /**
   * Verifies that non-reactive produced-water/seawater mixing reports supersaturated sulfate scales for the expected
   * barium-, strontium-, and calcium-sulfate ion pairs.
   *
   * @throws Exception if the thermodynamic operation fails
   */
  @Test
  void getRelativeScalePotentialHandlesProducedWaterSulfateScalesWithoutReactions() throws Exception {
    SystemInterface system = createProducedWaterSeawaterSulfateMix();
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();
    system.initProperties();

    int aqueousPhaseNumber = getAqueousOrWaterPhaseNumber(system);
    operations.checkScalePotential(aqueousPhaseNumber);

    double baso4ScalePotential = operations.getRelativeScalePotential(aqueousPhaseNumber, "BaSO4");
    double srso4ScalePotential = operations.getRelativeScalePotential(aqueousPhaseNumber, "SrSO4");
    double gypsumScalePotential = operations.getRelativeScalePotential(aqueousPhaseNumber, "CaSO4_G");
    double anhydriteScalePotential = operations.getRelativeScalePotential(aqueousPhaseNumber, "CaSO4_A");

    assertTrue(Double.isFinite(baso4ScalePotential) && baso4ScalePotential > 1.0,
        "BaSO4 should be supersaturated when barium-rich produced water meets sulfate-rich seawater");
    assertTrue(Double.isFinite(srso4ScalePotential) && srso4ScalePotential > 1.0,
        "SrSO4 should be supersaturated for the mixed produced-water benchmark");
    assertTrue(Double.isFinite(gypsumScalePotential) && gypsumScalePotential > 1.0,
        "Gypsum should be supersaturated for the calcium/sulfate-rich benchmark");
    assertTrue(Double.isFinite(anhydriteScalePotential) && anhydriteScalePotential > 1.0,
        "Anhydrite should be supersaturated for the calcium/sulfate-rich benchmark");
    assertTrue(baso4ScalePotential > srso4ScalePotential,
        "Barite should be the strongest sulfate-scale driver in the high-barium benchmark");
  }

  /**
   * Verifies that a sour, iron-bearing brine with H2S reactions forms bisulfide and reports finite FeS scale potential.
   *
   * @throws Exception if the thermodynamic operation fails
   */
  @Test
  void getRelativeScalePotentialHandlesFeSWithH2SReactions() throws Exception {
    SystemInterface system = createSourIronBrineWithH2S();
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();
    system.initProperties();

    int aqueousPhaseNumber = getAqueousOrWaterPhaseNumber(system);
    assertTrue(system.getPhase(aqueousPhaseNumber).hasComponent("HS-"),
        "H2S reactions should add bisulfide to the aqueous phase");
    assertTrue(system.getPhase(aqueousPhaseNumber).hasComponent("H3O+"),
        "H2S reactions should add hydronium for FeS scale-potential correction");
    assertTrue(getComponentMoles(system, aqueousPhaseNumber, "HS-") > 0.0,
        "Sour-brine equilibrium should form a finite amount of bisulfide");
    assertTrue(Double.isFinite(system.getPhase(aqueousPhaseNumber).getpH()),
        "Sour-brine aqueous phase should have a finite pH");

    operations.checkScalePotential(aqueousPhaseNumber);
    double fesScalePotential = operations.getRelativeScalePotential(aqueousPhaseNumber, "FeS");

    assertTrue(Double.isFinite(fesScalePotential), "FeS scale potential should be finite");
    assertTrue(fesScalePotential > 0.0, "FeS scale potential should be positive when Fe++ and HS- are present");
  }

  /**
   * Verifies that salt saturation calculations complete for supported chloride salts.
   */
  @Test
  void calcSaltSaturationDoesNotCrashForSupportedChlorideSalt() throws Exception {
    ThermodynamicOperations naclOperations = new ThermodynamicOperations(createWaterSystem());
    naclOperations.calcSaltSaturation("NaCl");

    ThermodynamicOperations kclOperations = new ThermodynamicOperations(createWaterSystem());
    kclOperations.calcSaltSaturation("KCl");

    ThermodynamicOperations cacl2Operations = new ThermodynamicOperations(createWaterSystem());
    cacl2Operations.calcSaltSaturation("CaCl2");

    ThermodynamicOperations mgcl2Operations = new ThermodynamicOperations(createWaterSystem());
    mgcl2Operations.calcSaltSaturation("MgCl2");
  }

  /**
   * Verifies that unsupported salts fail with a clear database-support exception.
   */
  @Test
  void calcSaltSaturationRejectsUnsupportedSaltClearly() throws Exception {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        new org.junit.jupiter.api.function.Executable() {
          @Override
          public void execute() throws Throwable {
            ThermodynamicOperations operations = new ThermodynamicOperations(createWaterSystem());
            operations.calcSaltSaturation("NoSuchSalt");
          }
        });
    assertTrue(exception.getMessage().contains("COMPSALT"));
  }

  /**
   * Verifies that salt saturation converges to an activity-based saturation ratio of one in pure aqueous, gas-aqueous,
   * and gas-oil-aqueous systems.
   *
   * @throws Exception if the thermodynamic operation fails
   */
  @Test
  void calcSaltSaturationIsAccurateAcrossAqueousAndHydrocarbonPhaseSystems() throws Exception {
    assertSaltSaturatesToUnitScalePotential("CaCO3", createCalciumCarbonateCo2GasAqueousSystem(),
        Arrays.asList("gas", "aqueous"));
    assertSaltSaturatesToUnitScalePotential("CaCO3", createHighPressureCalciumCarbonateCo2GasAqueousSystem(),
        Arrays.asList("gas", "aqueous"));
    assertSaltSaturatesToUnitScalePotential("FeCO3", createWaterSystem(), Arrays.asList());

    assertSaltSaturatesToUnitScalePotential("CaCl2", createWaterSystem(), Arrays.asList("aqueous"));
    assertSaltSaturatesToUnitScalePotential("CaCl2", createGasAqueousSystem(), Arrays.asList("gas", "aqueous"));
    assertSaltSaturatesToUnitScalePotential("CaCl2", createGasOilAqueousSystem(),
        Arrays.asList("gas", "oil", "aqueous"));

    assertSaltSaturatesToUnitScalePotential("MgCl2", createWaterSystem(), Arrays.asList("aqueous"));
    assertSaltSaturatesToUnitScalePotential("MgCl2", createGasAqueousSystem(), Arrays.asList("gas", "aqueous"));
    assertSaltSaturatesToUnitScalePotential("MgCl2", createGasOilAqueousSystem(),
        Arrays.asList("gas", "oil", "aqueous"));
  }

  /**
   * Creates the user-requested mixed chloride brine as dissociated ions.
   *
   * @return initialized electrolyte CPA system
   */
  private SystemInterface createMixedChlorideBrine() {
    SystemInterface system = new SystemElectrolyteCPAstatoil(293.15, 1.01325);
    system.addComponent("water", 100000.0 / 18.01528);
    system.addComponent("Na+", 10000.0 / 58.44277);
    system.addComponent("K+", 10000.0 / 74.5513);
    system.addComponent("Mg++", 5000.0 / 95.211);
    system.addComponent("Ca++", 5000.0 / 110.984);
    system.addComponent("Cl-", 10000.0 / 58.44277 + 10000.0 / 74.5513 + 2.0 * 5000.0 / 95.211 + 2.0 * 5000.0 / 110.984);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);
    return system;
  }

  /**
   * Creates a water-rich electrolyte CPA system for salt saturation operations.
   *
   * @return initialized electrolyte CPA system
   */
  private SystemInterface createWaterSystem() throws Exception {
    SystemInterface system = new SystemElectrolyteCPAstatoil(293.15, 1.01325);
    system.addComponent("water", 1.0);
    return initializeElectrolyteSystem(system);
  }

  /**
   * Creates a gas-aqueous electrolyte CPA system.
   *
   * @return initialized electrolyte CPA system
   */
  private SystemInterface createGasAqueousSystem() throws Exception {
    SystemInterface system = new SystemElectrolyteCPAstatoil(293.15, 1.01325);
    system.addComponent("methane", 10.0);
    system.addComponent("water", 1.0);
    return initializeElectrolyteSystem(system);
  }

  /**
   * Creates a gas-oil-aqueous electrolyte CPA system.
   *
   * @return initialized electrolyte CPA system
   */
  private SystemInterface createGasOilAqueousSystem() throws Exception {
    SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 10.0);
    system.addComponent("methane", 1.0);
    system.addComponent("CO2", 0.1);
    system.addComponent("nC10", 0.5);
    system.addComponent("water", 1.0);
    return initializeElectrolyteSystem(system);
  }

  /**
   * Creates a calcium-bearing gas-aqueous electrolyte CPA system with CO2 and alkalinity.
   *
   * @return initialized electrolyte CPA system
   * @throws Exception if chemical-reaction initialization fails
   */
  private SystemInterface createCalciumCarbonateCo2GasAqueousSystem() throws Exception {
    SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    system.addComponent("methane", 5.0);
    system.addComponent("CO2", 0.01);
    system.addComponent("water", 55.5);
    system.addComponent("Ca++", 1.0e-4);
    system.addComponent("Cl-", 2.0e-4);
    system.addComponent("OH-", 1.0e-3);
    return initializeElectrolyteSystem(system);
  }

  /**
   * Creates a calcium-carbonate CO2 gas-aqueous electrolyte CPA system at elevated pressure.
   *
   * @return initialized electrolyte CPA system
   * @throws Exception if chemical-reaction initialization fails
   */
  private SystemInterface createHighPressureCalciumCarbonateCo2GasAqueousSystem() throws Exception {
    SystemInterface system = createCalciumCarbonateCo2GasAqueousSystem();
    system.setPressure(100.0);
    return system;
  }

  /**
   * Creates a non-reactive barium/strontium/calcium-rich produced-water and sulfate-rich seawater blend.
   *
   * @return initialized electrolyte CPA system
   */
  private SystemInterface createProducedWaterSeawaterSulfateMix() {
    SystemInterface system = new SystemElectrolyteCPAstatoil(353.15, 50.0);
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 0.80);
    system.addComponent("Cl-", 1.59);
    system.addComponent("Ca++", 0.06);
    system.addComponent("Ba++", 0.012);
    system.addComponent("Sr++", 0.010);
    system.addComponent("SO4--", 0.045);
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);
    return system;
  }

  /**
   * Creates an iron-bearing sour brine where H2S dissociation supplies bisulfide for FeS scale screening.
   *
   * @return initialized electrolyte CPA system
   * @throws Exception if chemical-reaction initialization fails
   */
  private SystemInterface createSourIronBrineWithH2S() throws Exception {
    SystemInterface system = new SystemElectrolyteCPAstatoil(323.15, 10.0);
    system.addComponent("H2S", 0.02);
    system.addComponent("water", 55.5);
    system.addComponent("Fe++", 1.0e-4);
    system.addComponent("Cl-", 2.0e-4);
    return initializeElectrolyteSystem(system);
  }

  /**
   * Initializes an electrolyte CPA system after all feed components have been added.
   *
   * @param system system to initialize
   * @return initialized electrolyte CPA system
   * @throws Exception if chemical-reaction initialization fails
   */
  private SystemInterface initializeElectrolyteSystem(SystemInterface system) throws Exception {
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);
    return system;
  }

  /**
   * Runs salt saturation and asserts that the resulting scale-potential ratio is one in the aqueous phase.
   *
   * @param saltName salt to saturate
   * @param system thermodynamic system to saturate
   * @param expectedPhaseTypes phase types expected after saturation
   * @throws Exception if the thermodynamic operation fails
   */
  private void assertSaltSaturatesToUnitScalePotential(String saltName, SystemInterface system,
      List<String> expectedPhaseTypes) throws Exception {
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.calcSaltSaturation(saltName);
    operations.TPflash();
    system.initProperties();

    for (String phaseType : expectedPhaseTypes) {
      assertTrue(system.hasPhaseType(phaseType), "Expected " + phaseType + " phase for " + saltName);
    }

    operations.checkScalePotential(getAqueousOrWaterPhaseNumber(system));
    double saturationRatio = getSaltSaturationRatio(operations.getResultTable(), saltName);
    assertEquals(1.0, saturationRatio, SATURATION_RATIO_TOLERANCE,
        saltName + " saturation ratio should be unity after calcSaltSaturation");
  }

  /**
   * Finds the aqueous phase, falling back to the water-containing phase used by single-phase brines.
   *
   * @param system thermodynamic system to inspect
   * @return phase number for the aqueous or water-containing phase
   */
  private int getAqueousOrWaterPhaseNumber(SystemInterface system) {
    int aqueousPhaseNumber = system.getPhaseNumberOfPhase("aqueous");
    if (aqueousPhaseNumber >= 0) {
      return aqueousPhaseNumber;
    }
    for (int phaseNumber = 0; phaseNumber < system.getNumberOfPhases(); phaseNumber++) {
      if (system.getPhase(phaseNumber).hasComponent("water")) {
        return phaseNumber;
      }
    }
    throw new IllegalStateException("No aqueous or water-containing phase found");
  }

  /**
   * Reads a salt saturation ratio from a scale-potential result table.
   *
   * @param table result table from {@link ThermodynamicOperations#getResultTable()}
   * @param saltName salt name to find
   * @return saturation ratio for the salt
   */
  private double getSaltSaturationRatio(String[][] table, String saltName) {
    for (int i = 1; i < table.length; i++) {
      if (saltName.equals(table[i][0])) {
        return Double.parseDouble(table[i][1]);
      }
    }
    throw new IllegalArgumentException("Missing salt in scale-potential table: " + saltName);
  }

  /**
   * Reads moles for a named component in the aqueous phase.
   *
   * @param system thermodynamic system containing an aqueous phase
   * @param componentName component name to read
   * @return component moles in the aqueous phase
   */
  private double getAqueousComponentMoles(SystemInterface system, String componentName) {
    return system.getPhase("aqueous").getComponent(componentName).getNumberOfMolesInPhase();
  }

  /**
   * Reads moles for a named component in a phase.
   *
   * @param system thermodynamic system containing the phase
   * @param phaseNumber phase number to inspect
   * @param componentName component name to read
   * @return component moles in the phase
   */
  private double getComponentMoles(SystemInterface system, int phaseNumber, String componentName) {
    return system.getPhase(phaseNumber).getComponent(componentName).getNumberOfMolesInPhase();
  }

  /**
   * Recovers the calcite solubility product from the activity product and saturation ratio returned by scale-potential
   * screening.
   *
   * @param system thermodynamic system containing an aqueous phase
   * @param caco3ScalePotential CaCO3 saturation ratio from {@link ThermodynamicOperations#getRelativeScalePotential}
   * @return apparent calcite solubility product used by the saturation-ratio calculation
   */
  private double getRecoveredCalciteKsp(SystemInterface system, double caco3ScalePotential) {
    neqsim.thermo.phase.PhaseInterface aqueousPhase = system.getPhase("aqueous");
    int waterComponentNumber = aqueousPhase.getComponent("water").getComponentNumber();
    int calciumComponentNumber = aqueousPhase.getComponent("Ca++").getComponentNumber();
    int carbonateComponentNumber = aqueousPhase.getComponent("CO3--").getComponentNumber();

    double waterDenominator = aqueousPhase.getComponent("water").getx()
        * aqueousPhase.getComponent("water").getMolarMass();
    double calciumMolality = aqueousPhase.getComponent("Ca++").getx() / waterDenominator;
    double carbonateMolality = aqueousPhase.getComponent("CO3--").getx() / waterDenominator;
    double calciumActivityCoefficient = aqueousPhase.getActivityCoefficient(calciumComponentNumber,
        waterComponentNumber);
    double carbonateActivityCoefficient = aqueousPhase.getActivityCoefficient(carbonateComponentNumber,
        waterComponentNumber);
    double ionActivityProduct = calciumActivityCoefficient * calciumMolality * carbonateActivityCoefficient
        * carbonateMolality;
    return ionActivityProduct / caco3ScalePotential;
  }

  /**
   * Calculates the Plummer-Busenberg calcite solubility product correlation used for CaCO3 scale potential.
   *
   * @param temperatureK temperature in Kelvin
   * @return calcite solubility product
   */
  private double calciteKspPlummerBusenberg1982(double temperatureK) {
    double log10Ksp = -171.9065 - 0.077993 * temperatureK + 2839.319 / temperatureK + 71.595 * Math.log10(temperatureK);
    return Math.pow(10.0, log10Ksp);
  }
}
