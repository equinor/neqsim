package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

class EnergyBusAllocationTest {

  @Test
  void testEqualPriorityShortageIsAllocatedProportionally() {
    EnergyBus bus = new EnergyBus("power bus", EnergyType.ELECTRICAL);
    EnergyPort producer = port("producer", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyPort first = port("first", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    EnergyPort second = port("second", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    producer.setDuty(100.0);
    first.setRequestedPower(100.0);
    second.setRequestedPower(50.0);

    EnergyNetworkReport report = bus.solveBalance();

    assertEquals(-100.0 * 2.0 / 3.0, bus.getAllocation(first.getParticipantId()), 1.0e-12);
    assertEquals(-100.0 / 3.0, bus.getAllocation(second.getParticipantId()), 1.0e-12);
    assertEquals(-100.0 * 2.0 / 3.0, first.getDuty(), 1.0e-12);
    assertEquals(100.0 * 2.0 / 3.0, first.getPowerMagnitude(), 1.0e-12);
    assertEquals(50.0, report.getUnmetDemand(), 1.0e-12);
    assertEquals(100.0, report.getServedDemand(), 1.0e-12);

    first.setDuty(50.0);
    assertEquals(100.0 / 3.0, second.getPowerMagnitude(), 1.0e-12);
    assertTrue(bus.hasSolution());

    EnergyNetworkReport updated = bus.solveBalance();
    assertEquals(-50.0, bus.getAllocation(first.getParticipantId()), 1.0e-12);
    assertEquals(-50.0, bus.getAllocation(second.getParticipantId()), 1.0e-12);
    assertEquals(0.0, updated.getUnmetDemand(), 1.0e-12);
  }

  @Test
  void testPriorityDispatchAndStableIdentity() throws Exception {
    EnergyBus bus = new EnergyBus("priority bus", EnergyType.ELECTRICAL);
    EnergyPort producer = port("producer", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyPort essential = port("essential", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    EnergyPort flexible = port("flexible", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    essential.setPriority(10);
    flexible.setPriority(20);
    essential.setRequestedPower(80.0);
    flexible.setRequestedPower(80.0);
    producer.setDuty(100.0);

    bus.solveBalance();

    assertEquals(-80.0, bus.getAllocation(essential.getParticipantId()), 1.0e-12);
    assertEquals(-20.0, bus.getAllocation(flexible.getParticipantId()), 1.0e-12);
    String participantId = flexible.getParticipantId();
    flexible.setOwnerName("renamed equipment");
    assertEquals(-20.0, bus.getAllocation(participantId), 1.0e-12);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(flexible);
    }
    EnergyPort restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (EnergyPort) input.readObject();
    }
    assertEquals(participantId, restored.getParticipantId());

    restored.connect(bus);
    assertNotEquals(participantId, restored.getParticipantId());
    assertTrue(bus.getRegisteredPorts().containsKey(restored.getParticipantId()));
  }

  @Test
  void testBalancePortCoversShortageAndAbsorbsSurplus() {
    EnergyBus bus = new EnergyBus("balanced bus", EnergyType.ELECTRICAL);
    EnergyPort producer = port("producer", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyPort consumer = port("consumer", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    EnergyPort storage = port("storage", EnergyPortDirection.BIDIRECTIONAL, EnergyPortMode.BALANCE, bus);
    storage.setBalanceLimits(60.0, 30.0);
    consumer.setRequestedPower(100.0);
    producer.setDuty(50.0);

    EnergyNetworkReport shortage = bus.solveBalance();

    assertEquals(50.0, bus.getAllocation(storage.getParticipantId()), 1.0e-12);
    assertEquals(50.0, storage.getDuty(), 1.0e-12);
    assertEquals(110.0, shortage.getOfferedSupply(), 1.0e-12);
    assertEquals(0.0, shortage.getUnmetDemand(), 1.0e-12);

    producer.setDuty(150.0);
    EnergyNetworkReport surplus = bus.solveBalance();

    assertEquals(-30.0, bus.getAllocation(storage.getParticipantId()), 1.0e-12);
    assertEquals(20.0, surplus.getCurtailedSupply(), 1.0e-12);
  }

  @Test
  void testCostEmissionAndEfficiencyReporting() {
    EnergyBus bus = new EnergyBus("report bus", EnergyType.ELECTRICAL);
    EnergyPort producer = port("grid", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyPort consumer = port("load", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    producer.setEnergyPricePerMWh(100.0);
    producer.setEmissionFactorKgPerMWh(400.0);
    producer.setDuty(1.0e6);
    consumer.setRequestedPower(1.0e6);

    EnergyNetworkReport report = bus.solveBalance();

    assertEquals(100.0, report.getOperatingCostPerHour(), 1.0e-12);
    assertEquals(400.0, report.getCo2EmissionRate(), 1.0e-12);
    assertEquals(1.0, report.getEfficiency(), 1.0e-12);
  }

  @Test
  void testChemicalBusReportsFuelEnergyRate() {
    EnergyBus fuelBus = new EnergyBus("fuel gas energy", EnergyType.CHEMICAL);
    EnergyPort fuelSupply = port("fuel supply", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, fuelBus);
    EnergyPort primeMover = port("prime mover", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, fuelBus);
    fuelSupply.setDuty(10.0e6);
    primeMover.setRequestedPower(8.0e6);

    EnergyNetworkReport report = fuelBus.solveBalance();

    assertEquals(8.0, report.getFuelEnergyRate("MW"), 1.0e-12);
  }

  @Test
  void testIncompatibleUtilityQualityIsRejected() {
    EnergyBus steam = new EnergyBus("steam", EnergyType.HEAT);
    steam.getQuality().setUtilityLevel(UtilityLevel.LOW_PRESSURE_STEAM);
    EnergyPort consumer = new EnergyPort("heat", EnergyType.HEAT, EnergyPortDirection.INPUT,
        EnergyPortMode.SPECIFICATION);
    consumer.setRequiredQuality(new EnergyQuality(UtilityLevel.HIGH_PRESSURE_STEAM));

    assertThrows(IllegalArgumentException.class, () -> consumer.connect(steam));
  }

  private static EnergyPort port(String owner, EnergyPortDirection direction, EnergyPortMode mode, EnergyBus bus) {
    EnergyPort port = new EnergyPort("power", bus.getEnergyType(), direction, mode);
    port.setOwnerName(owner);
    port.connect(bus);
    return port;
  }
}
