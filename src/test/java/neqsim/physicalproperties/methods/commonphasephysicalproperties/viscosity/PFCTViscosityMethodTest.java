package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class PFCTViscosityMethodTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;

  @Test
  void testCalcViscosityHydrogen() {
    double T = 298.15;
    double P = 42.00; //Pressure in bar(a)
    testSystem = new neqsim.thermo.system.SystemSrkEos(T, P);
    testSystem.addComponent("hydrogen", 1.0);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("PFCT");
    testSystem.initProperties();
    assertEquals(7.8e-6, testSystem.getPhase(0).getPhysicalProperties().getViscosity(), 1e-6);
  }
}
