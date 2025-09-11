package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
      double viscosity = testSystem.getPhase(0).getPhysicalProperties().getViscosity();
      assertEquals(2.153886699791477E-5, viscosity, 1e-10);
      System.out.println("Viscosity_LBC: " + viscosity * Math.pow(10, 6) + "[µPa*s]");
    }

    @Test
    void testLiquidNHeptaneViscosity() {
      double T = 298.15;
      double P = 0.1; // Pressure in MPa
      testSystem = new neqsim.thermo.system.SystemSrkEos(T, P*10);
      testSystem.addComponent("n-heptane", 1.0);
      ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
      testOps.TPflash();
      testSystem.getPhase(0).getPhysicalProperties().setViscosityModel("LBC");
      testSystem.initProperties();
      double viscosity = testSystem.getPhase(0).getPhysicalProperties().getViscosity();
      assertEquals(4.8297881398477884E-4, viscosity, 1e-13);
      System.out.println("Viscosity_LBC_nHeptane: " + viscosity * Math.pow(10, 3) + "[mPa*s]");
    }
}
