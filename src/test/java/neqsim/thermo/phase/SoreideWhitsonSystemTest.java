package neqsim.thermo.system;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SoreideWhitsonSystemTest {
  @Test
  public void testSoreideWhitsonSetup() {
    // Create a Soreide-Whitson system
    SystemSoreideWhitson system = new SystemSoreideWhitson(293.15, 20.0);
    // Set salinity for both phases
    system.getPhase(0).setSalinity(1.4);
    system.getPhase(1).setSalinity(1.4);
    // Add methane, CO2, and water (mole fractions: 0.4, 0.3, 0.3 for example)
    system.addComponent("methane", 0.4);
    system.addComponent("CO2", 0.3);
    system.addComponent("water", 0.3);
    // Check that the system and phases are set up correctly
    assertEquals(293.15, system.getTemperature(), 1e-6);
    assertEquals(20.0, system.getPressure(), 1e-6);
    assertEquals(1.4, system.getPhase(0).getSalinity(), 1e-6);
    assertEquals(1.4, system.getPhase(1).getSalinity(), 1e-6);
    assertEquals("methane", system.getPhase(0).getComponent(0).getComponentName());
    assertEquals("CO2", system.getPhase(0).getComponent(1).getComponentName());
    assertEquals("water", system.getPhase(0).getComponent(2).getComponentName());
  }
}
