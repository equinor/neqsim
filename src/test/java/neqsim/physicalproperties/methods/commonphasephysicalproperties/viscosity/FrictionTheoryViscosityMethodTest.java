package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class FrictionTheoryViscosityMethodTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;

  @BeforeAll
  public static void setUp() {
    testSystem = new neqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 42.0);
    testSystem.addComponent("methane", 0.5);
    testSystem.addComponent("ethane", 0.5);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  @Test
  void testCalcViscosity() {
    testSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("friction theory");
    testSystem.initProperties();
    double expected = 1.11212E-5;
    double actual = testSystem.getPhase("gas").getViscosity("kg/msec");
    assertEquals(expected, actual, 1e-6);
  }

  @Test
  void testPrEosSelection() {
    SystemInterface prSystem = new neqsim.thermo.system.SystemPrEos(298.15, 50.0);
    prSystem.addComponent("methane", 0.5);
    prSystem.addComponent("ethane", 0.5);
    prSystem.setMixingRule("classic");
    new ThermodynamicOperations(prSystem).TPflash();
    prSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("friction theory");
    prSystem.initProperties();

    SystemInterface srkSystem = new neqsim.thermo.system.SystemSrkEos(298.15, 50.0);
    srkSystem.addComponent("methane", 0.5);
    srkSystem.addComponent("ethane", 0.5);
    srkSystem.setMixingRule("classic");
    new ThermodynamicOperations(srkSystem).TPflash();
    srkSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("friction theory");
    srkSystem.initProperties();

    double prVisc = prSystem.getPhase("gas").getViscosity("kg/msec");
    double srkVisc = srkSystem.getPhase("gas").getViscosity("kg/msec");
    assertNotEquals(srkVisc, prVisc, 1e-9);
  }

  @Test
  void testPlusFractionTbpcorrection() {
    SystemInterface plusSystem = new neqsim.thermo.system.SystemSrkEos(298.15, 20.0);
    plusSystem.addComponent("methane", 0.8);
    plusSystem.addPlusFraction("C20", 0.2, 381.0 / 1000.0, 0.88);
    plusSystem.setMixingRule("classic");
    new ThermodynamicOperations(plusSystem).TPflash();
    plusSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("friction theory");
    plusSystem.initProperties();
    double baseVisc = plusSystem.getPhase("gas").getViscosity("kg/msec");

    ((FrictionTheoryViscosityMethod) plusSystem.getPhase("gas").getPhysicalProperties()
        .getViscosityModel()).setTBPviscosityCorrection(1.2);
    plusSystem.initProperties();
    double corrected = plusSystem.getPhase("gas").getViscosity("kg/msec");
    assertTrue(corrected > baseVisc);
  }

  @Test
  void testPolarMixture() {
    SystemInterface polarSystem = new neqsim.thermo.system.SystemSrkEos(298.15, 5.0);
    polarSystem.addComponent("methane", 0.5);
    polarSystem.addComponent("water", 0.5);
    polarSystem.setMixingRule("classic");
    new ThermodynamicOperations(polarSystem).TPflash();
    polarSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("friction theory");
    polarSystem.initProperties();
    double visc = polarSystem.getPhase("gas").getViscosity("kg/msec");
    assertTrue(visc > 0.0);
  }

  @Test
  void testOilViscosity() {
    SystemInterface oilSystem = new neqsim.thermo.system.SystemSrkEos(298.15, 50.0);
    oilSystem.addComponent("n-heptane", 0.5);
    oilSystem.addComponent("nC10", 0.5);
    oilSystem.setMixingRule("classic");
    new ThermodynamicOperations(oilSystem).TPflash();
    oilSystem.getPhase("oil").getPhysicalProperties().setViscosityModel("friction theory");
    oilSystem.initProperties();
    double expected = 6.37961E-4;
    double actual = oilSystem.getPhase("oil").getViscosity("kg/msec");
    assertEquals(expected, actual, 1e-6);
  }
}
