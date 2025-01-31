package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class PFCTViscosityMethodTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;

  @Test
  void testCalcViscosityHydrogen() {
    double T = 273.15;
    double P = 505.64; //Pressure in bar(a)
    testSystem = new neqsim.thermo.system.SystemSrkEos(T, P);
    testSystem.addComponent("methane", 0.213);
    testSystem.addComponent("hydrogen", 0.787);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("PFCT");
    testSystem.initProperties();
    System.out.println("FINAL VISCOSITY: " + testSystem.getPhase(0).getPhysicalProperties().getViscosity() * Math.pow(10, 6));
  }
}
