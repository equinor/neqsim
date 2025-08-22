package neqsim.process.equipment.powergeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class SolarPanelTest extends neqsim.NeqSimTest {
  @Test
  void testPowerCalculation() {
    SolarPanel panel = new SolarPanel("panel");
    panel.setIrradiance(800.0);
    panel.setPanelArea(2.0);
    panel.setEfficiency(0.2);
    panel.run();
    assertEquals(320.0, panel.getPower(), 1e-6);
    assertEquals(-320.0, panel.getEnergyStream().getDuty(), 1e-6);
  }
}
