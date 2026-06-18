package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class KTAViscosityMethodModTest {
  private static final Logger logger = LogManager.getLogger(KTAViscosityMethodModTest.class);

  static neqsim.thermo.system.SystemInterface testSystem = null;

  @Test
  void testCalcViscosity() {
    double T = 273.15;
    double P = 20.265; // Pressure in MPa
    testSystem = new neqsim.thermo.system.SystemSrkEos(T, P);
    testSystem.addComponent("helium", 1.0);
    // testSystem.addComponent("hydrogen", 0.6);
    testSystem.setMixingRule(2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("KTA_mod");
    testSystem.initProperties();
    assertEquals(18.878e-6, testSystem.getPhase(0).getPhysicalProperties().getViscosity(), 1e-9);
    // double viscosity = testSystem.getPhase(0).getPhysicalProperties().getViscosity() *
    // Math.pow(10, 6);
    // logger.info("Viscosity: " + viscosity);
  }
}
