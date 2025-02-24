package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class KTAViscosityMethodTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;

  @Test
  void testCalcViscosity() {
    double T = 273.15;
    double P = 20.265; //Pressure in bar(a)
    testSystem = new neqsim.thermo.system.SystemSrkEos(T, P);
    testSystem.addComponent("helium", 1.0);
    //testSystem.addComponent("hydrogen", 0.6);
    testSystem.setMixingRule(2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("KTA");
    testSystem.initProperties();
    assertEquals(18.647e-6, testSystem.getPhase(0).getPhysicalProperties().getViscosity(), 1e-9);
    //double viscosity = testSystem.getPhase(0).getPhysicalProperties().getViscosity() * Math.pow(10, 6);
    //System.out.println("Viscosity: " + viscosity);
  }
}
