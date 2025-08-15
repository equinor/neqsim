package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class MuznyModViscosityMethodTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;

  @Test
  void testCalcViscosity() {
    double T = 273.15;
    double P = 20; // Pressure in MPa
    testSystem = new neqsim.thermo.system.SystemSrkEos(T, P * 10);
    testSystem.addComponent("hydrogen", 1.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("Muzny_mod");
    testSystem.initProperties();
    assertEquals(8.792308805913915E-6,
        testSystem.getPhase(0).getPhysicalProperties().getViscosity(), 0.5e-10);
    // double viscosity = testSystem.getPhase(0).getPhysicalProperties().getViscosity();
    // System.out.println("Viscosity_Muzny: " + viscosity);
  }
}
