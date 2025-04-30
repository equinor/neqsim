package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class LBCViscosityMethodTest {
    static neqsim.thermo.system.SystemInterface testSystem = null;

    @Test
    void testCalcViscosity() {
      double T = 273.15;
      double P = 20; //Pressure in MPa
      testSystem = new neqsim.thermo.system.SystemSrkEos(T, P*10);
      testSystem.addComponent("methane", 1.0);
      ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
      testOps.TPflash();
      testSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("LBC");
      testSystem.initProperties();
      //assertEquals(8.792308805913915E-6, testSystem.getPhase(0).getPhysicalProperties().getViscosity(), 0.5e-10);
      double viscosity = testSystem.getPhase(0).getPhysicalProperties().getViscosity();
      System.out.println("Viscosity_LBC: " + viscosity*Math.pow(10, 6) + "[µPa*s]");
    }
}
