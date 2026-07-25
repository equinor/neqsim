package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class EnergyPortTest {

  @Test
  void testConnectAdoptsPortEnergyType() {
    EnergyPort port = new EnergyPort("shaftPower", EnergyType.SHAFT_WORK, EnergyPortDirection.INPUT,
        EnergyPortMode.SPECIFICATION);
    EnergyStream stream = new EnergyStream("driver");

    port.connect(stream);

    assertTrue(port.isConnected());
    assertSame(stream, port.getEnergyStream());
    assertEquals(EnergyType.SHAFT_WORK, stream.getEnergyType());
  }

  @Test
  void testRejectsIncompatibleEnergyType() {
    EnergyPort heatPort = new EnergyPort("heatDuty", EnergyType.HEAT, EnergyPortDirection.INPUT,
        EnergyPortMode.SPECIFICATION);
    EnergyStream electricalStream = new EnergyStream("electrical", EnergyType.ELECTRICAL);

    assertThrows(IllegalArgumentException.class, () -> heatPort.connect(electricalStream));
  }

  @Test
  void testUnitAwareDutyDelegation() {
    EnergyPort port = new EnergyPort("generator", EnergyType.ELECTRICAL, EnergyPortDirection.OUTPUT,
        EnergyPortMode.CALCULATED);
    port.connect(new EnergyStream("power"));

    port.setDuty(750.0, "kW");

    assertEquals(0.75, port.getDuty("MW"), 1e-12);
  }

  @Test
  void testUnconnectedDutyAccessFailsClearly() {
    EnergyPort port = new EnergyPort("heatDuty", EnergyType.HEAT, EnergyPortDirection.INPUT,
        EnergyPortMode.SPECIFICATION);

    assertThrows(IllegalStateException.class, port::getDuty);
  }
}
