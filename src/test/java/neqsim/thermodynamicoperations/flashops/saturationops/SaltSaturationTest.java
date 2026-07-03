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

    operations.checkScalePotential(system.getPhaseNumberOfPhase("aqueous"));
    double saturationRatio = getSaltSaturationRatio(operations.getResultTable(), saltName);
    assertEquals(1.0, saturationRatio, SATURATION_RATIO_TOLERANCE,
        saltName + " saturation ratio should be unity after calcSaltSaturation");
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
}
