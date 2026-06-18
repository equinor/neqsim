package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MuznyViscosityMethodTest {
  private static final Logger logger = LogManager.getLogger(MuznyViscosityMethodTest.class);

  static neqsim.thermo.system.SystemInterface testSystem = null;

  @Test
  void testCalcViscosity() {
    double T = 273.15;
    double P = 20; // Pressure in MPa
    testSystem = new neqsim.thermo.system.SystemSrkEos(T, P * 10);
    testSystem.addComponent("hydrogen", 1.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("Muzny");
    testSystem.initProperties();
    assertEquals(9.084162247540838E-6,
        testSystem.getPhase(0).getPhysicalProperties().getViscosity(), 1e-10);
    // double viscosity = testSystem.getPhase(0).getPhysicalProperties().getViscosity();
    // logger.info("Viscosity_Muzny: " + viscosity);
  }
}
