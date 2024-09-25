package neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class PFCTViscosityMethodTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;

  @Test
  void testCalcViscosityHydrogen() {
    testSystem = new neqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 42.0);
    testSystem.addComponent("hydrogen", 0.5);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    double expected = 7.8e-6;
    double actual = testSystem.getPhase("gas").getViscosity("kg/msec");
    assertEquals(expected, actual, 1e-6);
  }
}
