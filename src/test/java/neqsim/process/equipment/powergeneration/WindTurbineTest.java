package neqsim.process.equipment.powergeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.EquipmentFactory;

public class WindTurbineTest extends neqsim.NeqSimTest {
  @Test
  public void testPowerCalculation() {
    WindTurbine turbine = new WindTurbine("turbine");
    turbine.setWindSpeed(10.0);
    turbine.setRotorArea(100.0);
    turbine.setPowerCoefficient(0.4);
    turbine.run();

    assertEquals(24500.0, turbine.getPower(), 1e-6);
    assertEquals(-turbine.getPower(), turbine.getEnergyStream().getDuty(), 1e-6);
  }

  @Test
  public void testEquipmentFactory() {
    WindTurbine turbine = (WindTurbine) EquipmentFactory.createEquipment("turb", "windturbine");
    assertTrue(turbine instanceof WindTurbine);
  }
}

