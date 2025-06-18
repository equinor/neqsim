package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SoreideWhitsonSystemTest {
  @Test
  public void testSoreideWhitsonSetup() {
    // Create a Soreide-Whitson system

    //SystemPrEos testSystem = new neqsim.thermo.system.SystemPrEos1978(298.0, 20.0);
    SystemSoreideWhitson testSystem = new SystemSoreideWhitson(298.0, 20.0);
    testSystem.addComponent("methane", 0.8);
    testSystem.addComponent("water", 0.2);
    testSystem.addSalinity(100,"mole/sec" );
    testSystem.setMixingRule(11); // Set mixing rule to 2

    

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    System.out.println(testSystem.getPhase(0).getSalinityConcentration());



    // Add methane, CO2, and water (mole fractions: 0.4, 0.3, 0.3 for example)
    testSystem.addComponent("methane", 0.4);
    testSystem.addComponent("CO2", 0.3);
    testSystem.addComponent("water", 0.3);
    // Check that the system and phases are set up correctly
    assertEquals(298.0, testSystem.getTemperature(), 1e-6);
    assertEquals(20.0, testSystem.getPressure(), 1e-6);
    assertEquals("methane", testSystem.getPhase(0).getComponent(0).getComponentName());
  }
}
