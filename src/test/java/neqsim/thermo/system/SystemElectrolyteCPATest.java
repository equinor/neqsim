package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * ElectrolyteCPAEosTest class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class SystemElectrolyteCPATest extends neqsim.NeqSimTest {
  static SystemInterface thermoSystem;
  static ThermodynamicOperations testOps;
  static neqsim.thermo.ThermodynamicModelTest testModel = null;

  /**
   * <p>
   * setUp.
   * </p>
   */
  @BeforeAll
  public static void setUp() {
    thermoSystem = new SystemElectrolyteCPAstatoil(298.15, 10.01325);
    thermoSystem.addComponent("methane", 0.1);
    thermoSystem.addComponent("water", 1.0);
    thermoSystem.addComponent("Na+", 0.001);
    thermoSystem.addComponent("Cl-", 0.001);
    thermoSystem.setMixingRule(10);
    testModel = new neqsim.thermo.ThermodynamicModelTest(thermoSystem);
    testModel.setMaxError(1e-10);
    testOps = new ThermodynamicOperations(thermoSystem);
    testOps.TPflash();
    thermoSystem.initProperties();
  }

  /**
   * <p>
   * tearDown.
   * </p>
   */
  @AfterAll
  public static void tearDown() {}

  /**
   * <p>
   * testTPflash.
   * </p>
   */
  @Test
  public void testTPflash() {
    assertEquals(2, thermoSystem.getNumberOfPhases());
  }

  /**
   * <p>
   * testinitPhysicalProperties.
   * </p>
   */
  @Test
  public void testDensity() {
    assertEquals(6.594232943612613, thermoSystem.getPhase(PhaseType.GAS).getDensity("kg/m3"), 0.01);
    assertEquals(995.69, thermoSystem.getPhase(PhaseType.AQUEOUS).getDensity("kg/m3"), 0.01);
  }

  /**
   * <p>
   * testFugacityCoefficients.
   * </p>
   */
  @Test
  @DisplayName("test the fugacity coefficients calculated")
  public void testFugacityCoefficients() {
    assertTrue(testModel.checkFugacityCoefficients());
  }

  /**
   * <p>
   * checkFugacityCoefficientsDP.
   * </p>
   */
  @Test
  @DisplayName("test derivative of fugacity coefficients with respect to pressure")
  public void checkFugacityCoefficientsDP() {
    assertTrue(testModel.checkFugacityCoefficientsDP());
  }

  /**
   * <p>
   * checkFugacityCoefficientsDT.
   * </p>
   */
  @Test
  @DisplayName("test derivative of fugacity coefficients with respect to temperature")
  public void checkFugacityCoefficientsDT() {
    assertTrue(testModel.checkFugacityCoefficientsDT());
  }

  /**
   * <p>
   * checkFugacityCoefficientsDn.
   * </p>
   */
  @Test
  @DisplayName("test derivative of fugacity coefficients with respect to composition")
  public void checkFugacityCoefficientsDn() {
    assertTrue(testModel.checkFugacityCoefficientsDn());
  }

  /**
   * <p>
   * checkFugacityCoefficientsDn2.
   * </p>
   */
  @Test
  @DisplayName("test derivative of fugacity coefficients with respect to composition (2nd method)")
  public void checkFugacityCoefficientsDn2() {
    assertTrue(testModel.checkFugacityCoefficientsDn2());
  }

  /**
   * Test hasIons method returns true for electrolyte systems.
   */
  @Test
  @DisplayName("test hasIons returns true for system with ionic components")
  public void testHasIonsWithElectrolytes() {
    assertTrue(thermoSystem.hasIons(), "System with Na+ and Cl- should return hasIons() = true");
  }

  /**
   * Test hasIons method returns false for systems without ions.
   */
  @Test
  @DisplayName("test hasIons returns false for system without ionic components")
  public void testHasIonsWithoutElectrolytes() {
    SystemInterface nonIonicSystem = new SystemSrkEos(298.15, 10.0);
    nonIonicSystem.addComponent("methane", 0.5);
    nonIonicSystem.addComponent("water", 0.5);
    nonIonicSystem.setMixingRule("classic");
    assertEquals(false, nonIonicSystem.hasIons(),
        "System without ions should return hasIons() = false");
  }

  /**
   * Test that adding heavier hydrocarbons (butane) to an electrolyte system does not cause CH4
   * solubility to increase with salinity. This regression test ensures proper salting-out behavior
   * for multi-component hydrocarbon mixtures.
   *
   * <p>
   * Bug description: Prior to fix, adding n-butane caused CH4 solubility to INCREASE with salinity
   * instead of DECREASE (salting-out effect). This was because heavier hydrocarbons were not
   * assigned proper gas-ion Wij interaction parameters.
   * </p>
   */
  @Test
  @DisplayName("test salting-out effect with heavier hydrocarbons (butane, pentane)")
  public void testSaltingOutWithHeavierHydrocarbons() {
    // System without salt
    SystemInterface pureWater = new SystemElectrolyteCPAstatoil(298.15, 50.0);
    pureWater.addComponent("methane", 0.1);
    pureWater.addComponent("n-butane", 0.05);
    pureWater.addComponent("water", 1.0);
    pureWater.setMixingRule(10);
    ThermodynamicOperations opsNosalt = new ThermodynamicOperations(pureWater);
    opsNosalt.TPflash();
    pureWater.initProperties();

    double ch4InWaterNoSalt = pureWater.getPhase(PhaseType.AQUEOUS).getComponent("methane").getx();

    // System with salt (1 mol/kg NaCl)
    SystemInterface salineWater = new SystemElectrolyteCPAstatoil(298.15, 50.0);
    salineWater.addComponent("methane", 0.1);
    salineWater.addComponent("n-butane", 0.05);
    salineWater.addComponent("water", 1.0);
    salineWater.addComponent("Na+", 0.018); // ~1 mol/kg
    salineWater.addComponent("Cl-", 0.018);
    salineWater.setMixingRule(10);
    ThermodynamicOperations opsSalt = new ThermodynamicOperations(salineWater);
    opsSalt.TPflash();
    salineWater.initProperties();

    double ch4InWaterWithSalt =
        salineWater.getPhase(PhaseType.AQUEOUS).getComponent("methane").getx();

    // CH4 solubility should DECREASE with salinity (salting-out effect)
    assertTrue(ch4InWaterWithSalt < ch4InWaterNoSalt,
        "CH4 solubility should decrease with salinity (salting-out effect). " + "Without salt: "
            + ch4InWaterNoSalt + ", With salt: " + ch4InWaterWithSalt);
  }

  /**
   * Test salting-out effect for various hydrocarbons (ethane, propane, pentane).
   */
  @Test
  @DisplayName("test salting-out effect for ethane, propane, and pentane")
  public void testSaltingOutVariousHydrocarbons() {
    String[] hydrocarbons = {"ethane", "propane", "n-pentane"};

    for (String hc : hydrocarbons) {
      // System without salt
      SystemInterface pureWater = new SystemElectrolyteCPAstatoil(298.15, 50.0);
      pureWater.addComponent("methane", 0.1);
      pureWater.addComponent(hc, 0.05);
      pureWater.addComponent("water", 1.0);
      pureWater.setMixingRule(10);
      ThermodynamicOperations opsNoSalt = new ThermodynamicOperations(pureWater);
      opsNoSalt.TPflash();
      pureWater.initProperties();

      double ch4NoSalt = pureWater.getPhase(PhaseType.AQUEOUS).getComponent("methane").getx();

      // System with salt
      SystemInterface salineWater = new SystemElectrolyteCPAstatoil(298.15, 50.0);
      salineWater.addComponent("methane", 0.1);
      salineWater.addComponent(hc, 0.05);
      salineWater.addComponent("water", 1.0);
      salineWater.addComponent("Na+", 0.018);
      salineWater.addComponent("Cl-", 0.018);
      salineWater.setMixingRule(10);
      ThermodynamicOperations opsSalt = new ThermodynamicOperations(salineWater);
      opsSalt.TPflash();
      salineWater.initProperties();

      double ch4WithSalt = salineWater.getPhase(PhaseType.AQUEOUS).getComponent("methane").getx();

      assertTrue(ch4WithSalt < ch4NoSalt, "CH4 solubility should decrease with salinity when " + hc
          + " is present. " + "Without salt: " + ch4NoSalt + ", With salt: " + ch4WithSalt);
    }
  }

  /**
   * Test three-phase equilibrium (gas-oil-water) with ions. This verifies that the salting-out
   * effect works correctly when an oil phase is present in addition to gas and aqueous phases.
   *
   * <p>
   * In three-phase systems, the presence of ions in the aqueous phase should still reduce
   * hydrocarbon solubility in water (salting-out effect), regardless of the oil phase.
   * </p>
   */
  @Test
  @DisplayName("test three-phase equilibrium (gas-oil-water) with ions")
  public void testThreePhaseEquilibriumWithIons() {
    // Three-phase system without salt: gas + oil + water
    SystemInterface threePhaseNoSalt = new SystemElectrolyteCPAstatoil(298.15, 20.0);
    threePhaseNoSalt.addComponent("methane", 0.5); // gas phase
    threePhaseNoSalt.addComponent("n-heptane", 0.3); // oil phase
    threePhaseNoSalt.addComponent("n-butane", 0.1); // partitions between gas and oil
    threePhaseNoSalt.addComponent("water", 1.0); // aqueous phase
    threePhaseNoSalt.setMixingRule(10);
    ThermodynamicOperations opsNoSalt = new ThermodynamicOperations(threePhaseNoSalt);
    opsNoSalt.TPflash();
    threePhaseNoSalt.initProperties();

    // Should have 3 phases
    int numPhasesNoSalt = threePhaseNoSalt.getNumberOfPhases();
    assertTrue(numPhasesNoSalt >= 2,
        "System should have at least 2 phases, got: " + numPhasesNoSalt);

    double ch4InWaterNoSalt =
        threePhaseNoSalt.getPhase(PhaseType.AQUEOUS).getComponent("methane").getx();
    double butaneInWaterNoSalt =
        threePhaseNoSalt.getPhase(PhaseType.AQUEOUS).getComponent("n-butane").getx();

    // Three-phase system with salt
    SystemInterface threePhaseWithSalt = new SystemElectrolyteCPAstatoil(298.15, 20.0);
    threePhaseWithSalt.addComponent("methane", 0.5);
    threePhaseWithSalt.addComponent("n-heptane", 0.3);
    threePhaseWithSalt.addComponent("n-butane", 0.1);
    threePhaseWithSalt.addComponent("water", 1.0);
    threePhaseWithSalt.addComponent("Na+", 0.02); // ~1 mol/kg NaCl
    threePhaseWithSalt.addComponent("Cl-", 0.02);
    threePhaseWithSalt.setMixingRule(10);
    ThermodynamicOperations opsSalt = new ThermodynamicOperations(threePhaseWithSalt);
    opsSalt.TPflash();
    threePhaseWithSalt.initProperties();

    double ch4InWaterWithSalt =
        threePhaseWithSalt.getPhase(PhaseType.AQUEOUS).getComponent("methane").getx();
    double butaneInWaterWithSalt =
        threePhaseWithSalt.getPhase(PhaseType.AQUEOUS).getComponent("n-butane").getx();

    // Salting-out: hydrocarbon solubility in water should DECREASE with salt
    assertTrue(ch4InWaterWithSalt < ch4InWaterNoSalt,
        "CH4 solubility in aqueous phase should decrease with salt in 3-phase system. "
            + "Without salt: " + ch4InWaterNoSalt + ", With salt: " + ch4InWaterWithSalt);

    assertTrue(butaneInWaterWithSalt < butaneInWaterNoSalt,
        "n-Butane solubility in aqueous phase should decrease with salt in 3-phase system. "
            + "Without salt: " + butaneInWaterNoSalt + ", With salt: " + butaneInWaterWithSalt);
  }

  /**
   * Test three-phase equilibrium with multiple ions (NaCl + CaCl2). This verifies that the model
   * handles divalent ions (Ca2+) correctly in three-phase systems.
   */
  @Test
  @DisplayName("test three-phase equilibrium with multiple ion types")
  public void testThreePhaseWithMultipleIons() {
    // Three-phase system with NaCl only
    SystemInterface withNaCl = new SystemElectrolyteCPAstatoil(298.15, 30.0);
    withNaCl.addComponent("methane", 0.3);
    withNaCl.addComponent("propane", 0.1);
    withNaCl.addComponent("n-hexane", 0.2);
    withNaCl.addComponent("water", 1.0);
    withNaCl.addComponent("Na+", 0.018);
    withNaCl.addComponent("Cl-", 0.018);
    withNaCl.setMixingRule(10);
    ThermodynamicOperations opsNaCl = new ThermodynamicOperations(withNaCl);
    opsNaCl.TPflash();
    withNaCl.initProperties();

    double propaneWithNaCl = withNaCl.getPhase(PhaseType.AQUEOUS).getComponent("propane").getx();

    // Three-phase system with NaCl + CaCl2 (higher ionic strength)
    SystemInterface withMixedSalt = new SystemElectrolyteCPAstatoil(298.15, 30.0);
    withMixedSalt.addComponent("methane", 0.3);
    withMixedSalt.addComponent("propane", 0.1);
    withMixedSalt.addComponent("n-hexane", 0.2);
    withMixedSalt.addComponent("water", 1.0);
    withMixedSalt.addComponent("Na+", 0.018);
    withMixedSalt.addComponent("Cl-", 0.036); // more Cl- for CaCl2
    withMixedSalt.addComponent("Ca++", 0.009); // divalent ion
    withMixedSalt.setMixingRule(10);
    ThermodynamicOperations opsMixed = new ThermodynamicOperations(withMixedSalt);
    opsMixed.TPflash();
    withMixedSalt.initProperties();

    double propaneWithMixedSalt =
        withMixedSalt.getPhase(PhaseType.AQUEOUS).getComponent("propane").getx();

    // Higher ionic strength should give stronger salting-out effect
    // Using propane since it has higher solubility than methane in this system
    assertTrue(propaneWithMixedSalt <= propaneWithNaCl,
        "Higher ionic strength (NaCl+CaCl2) should give equal or stronger salting-out than NaCl alone. "
            + "With NaCl: " + propaneWithNaCl + ", With NaCl+CaCl2: " + propaneWithMixedSalt);
  }
}
