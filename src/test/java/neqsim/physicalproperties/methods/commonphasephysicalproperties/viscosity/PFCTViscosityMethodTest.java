package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for the PFCT viscosity model ensuring gas and liquid results are within 10% of a reference
 * implementation.
 */
public class PFCTViscosityMethodTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;

  /** Verify PFCT gas viscosity deviates less than 10% from the methane reference model. */
  @Test
  void testGasViscosityWithinTenPercent() {
    double T = 300.0; // K
    double P = 10.0; // bar(a)
    testSystem = new neqsim.thermo.system.SystemSrkEos(T, P);
    testSystem.addComponent("methane", 1.0);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();
    testSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("MethaneModel");
    testSystem.initProperties();
    double reference = testSystem.getPhase(0).getPhysicalProperties().getViscosity();
    testSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("PFCT");
    testSystem.initProperties();
    double calculated = testSystem.getPhase(0).getPhysicalProperties().getViscosity();
    assertEquals(reference, calculated, 0.10 * reference);
  }

  /** Verify PFCT-Heavy-Oil liquid viscosity deviates less than 10% from an LBC reference. */
  @Test
  void testLiquidViscosityWithinTenPercent() {
    double T = 300.0; // K
    double P = 1.0; // bar(a)
    testSystem = new neqsim.thermo.system.SystemSrkEos(T, P);
    testSystem.addComponent("n-heptane", 1.0);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();
    testSystem.getPhase("oil").getPhysicalProperties().setViscosityModel("LBC");
    testSystem.initProperties();
    double reference = testSystem.getPhase(0).getPhysicalProperties().getViscosity();
    testSystem.getPhase("oil").getPhysicalProperties().setViscosityModel("PFCT-Heavy-Oil");
    testSystem.initProperties();
    double calculated = testSystem.getPhase(0).getPhysicalProperties().getViscosity();
    assertEquals(reference, calculated, 0.10 * reference);
  }
}

