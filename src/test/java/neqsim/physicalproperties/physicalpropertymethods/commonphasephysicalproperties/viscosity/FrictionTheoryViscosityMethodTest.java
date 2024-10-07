package neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
}
