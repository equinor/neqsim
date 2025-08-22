package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class CO2ConductivityMethodTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;

  @Test
  void testCalcConductivity() {
    double T = 300.0;
    double P = 1.0; // Pressure in bar
    testSystem = new neqsim.thermo.system.SystemSrkEos(T, P);
    testSystem.addComponent("CO2", 1.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();
    testSystem.getPhase("gas").getPhysicalProperties().setConductivityModel("CO2Model");
    testSystem.initProperties();
    assertEquals(0.016728951577544077,
        testSystem.getPhase(0).getPhysicalProperties().getConductivity(), 1e-6);
  }
}
