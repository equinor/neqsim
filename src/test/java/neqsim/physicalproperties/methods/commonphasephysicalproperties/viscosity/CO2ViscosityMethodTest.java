package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class CO2ViscosityMethodTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;

  @Test
  void testCalcViscosity() {
    double T = 300.0;
    double P = 1.0; // Pressure in bar
    testSystem = new neqsim.thermo.system.SystemSrkEos(T, P);
    testSystem.addComponent("CO2", 1.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();
    testSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("CO2Model");
    testSystem.initProperties();
    assertEquals(1.4993960815994241E-5,
        testSystem.getPhase(0).getPhysicalProperties().getViscosity(), 1e-9);
  }
}
