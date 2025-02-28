package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

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
    testSystem = new neqsim.thermo.system.SystemSrkEos(298.15, 1.0);
    testSystem.addComponent("methane", 0.01);
    testSystem.addComponent("water", 0.99);
    testSystem.addComponent("NaCl", 0.01);
    testSystem.setMixingRule("HV");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    testSystem.prettyPrint();
    double density = testSystem.getDensity("kg/m3");

    assertEquals(971.4723812, testSystem.getPhase(PhaseType.AQUEOUS).getDensity("kg/m3"), 1e-2);
    assertEquals(1000.0, testSystem.getPhase(PhaseType.AQUEOUS).getWaterDensity("kg/m3"), 1e-2);


  }
}
