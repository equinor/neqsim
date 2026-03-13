package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.TPflash;

class SystemThermoTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;

  /**
   * <p>
   * setUp.
   * </p>
   */
  @BeforeAll
  public static void setUp() {
    testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.68);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
  }

  /**
   * <p>
   * setUp.
   * </p>
   */
  @Test
  public void testCp() {
    neqsim.thermo.system.SystemPrEos testSystem =
        new neqsim.thermo.system.SystemPrEos(273.15 + 40.0, 1.0);
    testSystem.addComponent("methane", 10.01);
    testSystem.addTBPfraction("C20", 10.68, 0.3, 0.85);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    assertEquals(2.00406932521, testSystem.getPhase(1).getCp("kJ/kgK"), 1e-6);
  }

  /**
   * <p>
   * test setPressure
   * </p>
   */
  @Test
  @DisplayName("test setPressure")
  public void testTPflash2() {
    assertEquals(10.0, testSystem.getPressure("bara"));
    testSystem.setPressure(110000.0, "Pa");
    assertEquals(1.1, testSystem.getPressure());
  }

  /**
   * <p>
   * testAddFluids_Flash
   * </p>
   */
  @Test
  @DisplayName("test addFluids input order")
  public void testAddFluids_Flash() {
    neqsim.thermo.system.SystemPrEos fluid1 = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    fluid1.addComponent("propane", 1.0);
    fluid1.addComponent("N2", 2.0);

    neqsim.thermo.system.SystemPrEos fluid2 = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    fluid2.addComponent("methane", 1.0);
    fluid2.addComponent("ethane", 3.0);

    assertEquals(fluid1.clone().addFluid(fluid2), fluid2.clone().addFluid(fluid1));

    TPflash flash1 = new TPflash(fluid1.clone().addFluid(fluid2));
    TPflash flash2 = new TPflash(fluid2.clone().addFluid(fluid1));

    assertEquals(flash1, flash2);
  }

  /**
   * <p>
   * testAddFluids
   * </p>
   */
  @Test
  @DisplayName("test addFluids with pseudo component")
  public void testAddFluids() {
    neqsim.thermo.system.SystemPrEos fluid1 = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    fluid1.addComponent("methane", 1.0);
    fluid1.addTBPfraction("C7", 1.0, 0.09, 0.81);

    neqsim.thermo.system.SystemPrEos fluid2 = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    fluid2.addComponent("methane", 1.0);
    fluid2.addTBPfraction("C7", 1.0, 0.09, 0.81);

    fluid1.addFluid(fluid2);

    assertEquals(2.0, fluid1.getComponent(0).getNumberOfmoles());
    assertEquals(2.0, fluid1.getComponent(1).getNumberOfmoles());

    assertEquals(2.0, fluid1.getComponent("methane").getNumberOfmoles());
    assertEquals(2.0, fluid1.getComponent("C7_PC").getNumberOfmoles());

    neqsim.thermo.system.SystemPrEos fluid3 = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    fluid3.addComponent("nitrogen", 1.0);
    fluid3.addTBPfraction("C8", 1.0, 0.092, 0.82);

    fluid1.addFluid(fluid3);

    assertEquals(2.0, fluid1.getComponent("methane").getNumberOfmoles());
    assertEquals(1.0, fluid1.getComponent("nitrogen").getNumberOfmoles());
    assertEquals(1.0, fluid1.getComponent("C8_PC").getNumberOfmoles());
  }

  /**
   * <p>
   * testSetPressure
   * </p>
   */
  @Test
  public void testSetPressure() {
    neqsim.thermo.system.SystemPrEos fluid = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    fluid.addComponent("nitrogen", 1.0);
    fluid.setPressure(0.0, "barg");

    assertEquals(ThermodynamicConstantsInterface.referencePressure, fluid.getPressure("bara"),
        1e-4);
    assertEquals(0.0, fluid.getPressure("barg"), 1e-4);
  }

  @Test
  @Disabled
  void testDisplay() {
    testSystem.display();

    SystemEos s = new SystemPrEos();
    s.display();
  }

  @Test
  void TESTsetForceSinglePhase() {
    testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.68);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double density = testSystem.getDensity("kg/m3");

    testSystem.setForceSinglePhase(PhaseType.GAS);
    testSystem.initProperties();

    assertEquals(density, testSystem.getDensity("kg/m3"), 1e-4);

    testSystem.setForceSinglePhase("GAS");
    testOps.TPflash();
    testSystem.initProperties();

    assertEquals(density, testSystem.getDensity("kg/m3"), 1e-4);
  }

  @Test
  @DisplayName("getDensity() uses volume-fraction weighting for multiphase mixtures")
  void testGetDensityVolumeWeighting() {
    // Methane + n-heptane at 280 K, 30 bar produces a two-phase system.
    // The correct mixture density is total_mass / total_volume, which equals
    // the volume-fraction-weighted average of phase densities.
    SystemInterface sys = new SystemSrkEos(280.0, 30.0);
    sys.addComponent("methane", 0.50);
    sys.addComponent("n-heptane", 0.50);
    sys.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();

    assertEquals(2, sys.getNumberOfPhases(), "Expected two phases");

    // Compute expected density as total_mass / total_volume
    double totalMass = 0.0;
    double totalVolume = 0.0;
    for (int i = 0; i < sys.getNumberOfPhases(); i++) {
      double phaseVolume = sys.getPhase(i).getVolume();
      double phaseDensity = sys.getPhase(i).getDensity();
      totalMass += phaseDensity * phaseVolume;
      totalVolume += phaseVolume;
    }
    double expectedDensity = totalMass / totalVolume;

    double actualDensity = sys.getDensity();
    assertEquals(expectedDensity, actualDensity, 1e-6,
        "getDensity() should equal total_mass / total_volume");

    // The density must lie between the lightest and densest phase densities
    double gasRho = sys.getPhase(0).getDensity();
    double liqRho = sys.getPhase(1).getDensity();
    double minRho = Math.min(gasRho, liqRho);
    double maxRho = Math.max(gasRho, liqRho);
    assertTrue(actualDensity >= minRho && actualDensity <= maxRho,
        "Mixture density must be between phase densities");
  }

  @Test
  @DisplayName("getDensity(kg/m3) uses Peneloux-shifted volumes consistently")
  void testGetDensityWithUnitPenelouxConsistency() {
    // Methane + n-heptane at 280 K, 30 bar — two-phase system with Peneloux active.
    // getDensity("kg/m3") must equal total_mass / total_shifted_volume.
    SystemInterface sys = new SystemSrkEos(280.0, 30.0);
    sys.addComponent("methane", 0.50);
    sys.addComponent("n-heptane", 0.50);
    sys.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initProperties();

    assertEquals(2, sys.getNumberOfPhases(), "Expected two phases");

    // Compute expected density from per-phase shifted volumes and masses
    double totalMass = 0.0;
    double totalShiftedVolume = 0.0;
    for (int i = 0; i < sys.getNumberOfPhases(); i++) {
      double phaseMass = sys.getPhase(i).getMolarMass() * sys.getPhase(i).getNumberOfMolesInPhase();
      double phaseShiftedDensity = sys.getPhase(i).getPhysicalProperties().getDensity();
      totalMass += phaseMass;
      totalShiftedVolume += phaseMass / phaseShiftedDensity;
    }
    double expectedDensity = totalMass / totalShiftedVolume;

    double actualDensity = sys.getDensity("kg/m3");
    assertEquals(expectedDensity, actualDensity, 1e-6,
        "getDensity(kg/m3) should equal total_mass / total_shifted_volume");

    // Verify Mw/getMolarVolume("m3/mol") is consistent with getDensity("kg/m3")
    double densityFromMolarVolume = sys.getMolarMass() / sys.getMolarVolume("m3/mol");
    assertEquals(actualDensity, densityFromMolarVolume, actualDensity * 0.001,
        "getDensity(kg/m3) and Mw/getMolarVolume(m3/mol) should be consistent");
  }

  @SuppressWarnings("deprecation")
  @Test
  void TestMixingRuleTypes() {
    EosMixingRuleType[] mrNum = EosMixingRuleType.values();
    for (EosMixingRuleType mixingRule : mrNum) {
      testSystem.setMixingRule(mixingRule.getValue());
      assertEquals(mixingRule, testSystem.getMixingRule());
    }

    for (EosMixingRuleType mixingRule : mrNum) {
      testSystem.setMixingRule(mixingRule);
      assertEquals(mixingRule, testSystem.getMixingRule());
    }

    for (EosMixingRuleType mixingRule : mrNum) {
      testSystem.setMixingRule(mixingRule.name());
      assertEquals(mixingRule, testSystem.getMixingRule());
    }
  }

  @Test
  void waterNaClTest() {
    neqsim.thermo.system.SystemSrkEos testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.15, 1.0);
    testSystem.addComponent("methane", 0.01);
    testSystem.addComponent("water", 0.99);
    testSystem.addComponent("NaCl", 0.05);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    // testSystem.prettyPrint();
    // double density = testSystem.getDensity("kg/m3");

    assertEquals(1109.7640, testSystem.getPhase(PhaseType.AQUEOUS).getDensity("kg/m3"), 1e-2);
    assertEquals(1099.66150816, testSystem.getPhase(PhaseType.AQUEOUS).getWaterDensity("kg/m3"),
        1e-2);
  }

  @Test
  void waterMegMixtureWaterDensityTest() {
    neqsim.thermo.system.SystemSrkEos testSystem =
        new neqsim.thermo.system.SystemSrkEos(293.15, 1.0);
    testSystem.addComponent("MEG", 49.0);
    testSystem.addComponent("water", 49.0);
    testSystem.addComponent("NaCl", 2.0);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double waterDensity = testSystem.getPhase(PhaseType.AQUEOUS).getWaterDensity("kg/m3");

    assertTrue(Double.isFinite(waterDensity));
    assertEquals(1109.3, waterDensity, 2.0);
  }

  @Test
  void waterMethanolMixtureWaterDensityTest() {
    neqsim.thermo.system.SystemSrkEos testSystem =
        new neqsim.thermo.system.SystemSrkEos(293.15, 1.0);
    testSystem.addComponent("methanol", 49.0);
    testSystem.addComponent("water", 49.0);
    testSystem.addComponent("NaCl", 2.0);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double waterDensity = testSystem.getPhase(PhaseType.AQUEOUS).getWaterDensity("kg/m3");

    assertTrue(Double.isFinite(waterDensity));
    assertEquals(889.0, waterDensity, 5.0);
  }

  @Test
  void waterEthanolMixtureWaterDensityTest() {
    neqsim.thermo.system.SystemSrkEos testSystem =
        new neqsim.thermo.system.SystemSrkEos(293.15, 1.0);
    testSystem.addComponent("ethanol", 49.0);
    testSystem.addComponent("water", 49.0);
    testSystem.addComponent("NaCl", 2.0);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double waterDensity = testSystem.getPhase(PhaseType.AQUEOUS).getWaterDensity("kg/m3");

    assertTrue(Double.isFinite(waterDensity));
    assertEquals(806.9378326487334, waterDensity, 5.0);
  }
}
