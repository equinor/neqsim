package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

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
  void testRejectedReconnectPreservesBusConnectionAndContribution() {
    EnergyPort heatPort = new EnergyPort("heatDuty", EnergyType.HEAT, EnergyPortDirection.OUTPUT,
        EnergyPortMode.CALCULATED);
    heatPort.setOwnerName("heater");
    EnergyBus heatBus = new EnergyBus("heat bus", EnergyType.HEAT);
    heatPort.connect(heatBus);
    heatPort.setDuty(100.0);

    EnergyStream electricalStream = new EnergyStream("electrical", EnergyType.ELECTRICAL);

    assertThrows(IllegalArgumentException.class, () -> heatPort.connect(electricalStream));
    assertSame(heatBus, heatPort.getEnergyStream());
    assertEquals(100.0, heatBus.getContribution("heater.heatDuty"), 1e-12);
  }

  @Test
  void testRejectedLegacySetPreservesInternalConnection() {
    TestEquipment equipment = new TestEquipment("heater");
    EnergyStream internalStream = equipment.getEnergyStream();
    EnergyStream electricalStream = new EnergyStream("electrical", EnergyType.ELECTRICAL);

    assertThrows(IllegalArgumentException.class, () -> equipment.setEnergyStream(electricalStream));

    assertSame(internalStream, equipment.getEnergyStream());
    assertSame(internalStream, equipment.getEnergyPort("heatDuty").getEnergyStream());
    assertFalse(equipment.isSetEnergyStream());
  }

  @Test
  void testDisconnectRestoresInternalLegacyStream() {
    TestEquipment equipment = new TestEquipment("heater");
    EnergyStream externalStream = new EnergyStream("external heat", EnergyType.HEAT);
    externalStream.setDuty(42.0);

    equipment.connectEnergyStream("heatDuty", externalStream, EnergyPortMode.SPECIFICATION);
    assertTrue(equipment.isSetEnergyStream());
    assertSame(externalStream, equipment.getEnergyStream());

    equipment.disconnectEnergyStream("heatDuty");

    assertFalse(equipment.isSetEnergyStream());
    assertNotSame(externalStream, equipment.getEnergyStream());
    assertSame(equipment.getEnergyStream(), equipment.getEnergyPort("heatDuty").getEnergyStream());
    assertEquals(EnergyType.HEAT, equipment.getEnergyStream().getEnergyType());

    equipment.getEnergyPort("heatDuty").setDuty(10.0);
    assertEquals(42.0, externalStream.getDuty(), 1e-12);
  }

  @Test
  void testCalculatedExternalConnectionIsNotSpecification() {
    TestEquipment equipment = new TestEquipment("heater");
    EnergyStream externalStream = new EnergyStream("external heat", EnergyType.HEAT);

    equipment.connectEnergyStream("heatDuty", externalStream, EnergyPortMode.CALCULATED);

    assertFalse(equipment.isSetEnergyStream());
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

  private static final class TestEquipment extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;

    private TestEquipment(String name) {
      super(name);
      registerEnergyPort("heatDuty", EnergyType.HEAT, EnergyPortDirection.BIDIRECTIONAL, EnergyPortMode.CALCULATED);
    }

    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }
  }
}
