package neqsim.process.equipment.battery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class BatteryStorageTest extends neqsim.NeqSimTest {
  @Test
  void testChargeDischargeCycle() {
    BatteryStorage battery = new BatteryStorage("battery", 1000.0);

    // Charge for 1 hour at 100 W
    battery.charge(100.0, 1.0);
    battery.run();
    assertEquals(95.0, battery.getStateOfCharge(), 1e-6);
    assertEquals(100.0, battery.getEnergyStream().getDuty(), 1e-6);

    // Discharge for 1 hour at 50 W
    battery.discharge(50.0, 1.0);
    battery.run();
    double expectedSoc = 95.0 - 50.0 / 0.95;
    assertEquals(expectedSoc, battery.getStateOfCharge(), 1e-6);
    assertEquals(-50.0, battery.getEnergyStream().getDuty(), 1e-6);
  }
}

