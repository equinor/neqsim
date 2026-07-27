package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.battery.BatteryStorage;
import neqsim.process.equipment.powergeneration.SolarPanel;

class EnergyBusTest {

  @Test
  void testAggregatesNamedContributionsAndBalanceDuty() {
    EnergyBus bus = new EnergyBus("main electrical bus", EnergyType.ELECTRICAL);
    bus.setDuty(25.0, "kW");
    bus.setContribution("solar", 1.0, "MW");
    bus.setContribution("electrolyzer", -250.0, "kW");

    assertEquals(775.0, bus.getNetPower("kW"), 1.0e-12);
    assertEquals(-250.0, bus.getContribution("electrolyzer", "kW"), 1.0e-12);
  }

  @Test
  void testCloneHasIndependentContributionMap() {
    EnergyBus bus = new EnergyBus("bus", EnergyType.HEAT);
    bus.setContribution("heater", 100.0);

    EnergyBus clone = bus.clone();
    clone.setContribution("heater", 200.0);

    assertNotSame(bus, clone);
    assertEquals(100.0, bus.getContribution("heater"), 1.0e-12);
    assertEquals(200.0, clone.getContribution("heater"), 1.0e-12);
  }

  @Test
  void testSpecificationConsumerExcludesItsPreviousWithdrawal() {
    EnergyBus bus = new EnergyBus("electrical bus", EnergyType.ELECTRICAL);
    EnergyPort generator = new EnergyPort("power", EnergyType.ELECTRICAL, EnergyPortDirection.OUTPUT,
        EnergyPortMode.CALCULATED);
    generator.setOwnerName("generator");
    generator.connect(bus);
    generator.setDuty(500.0);

    EnergyPort consumer = new EnergyPort("power", EnergyType.ELECTRICAL, EnergyPortDirection.INPUT,
        EnergyPortMode.SPECIFICATION);
    consumer.setOwnerName("consumer");
    consumer.connect(bus);

    assertEquals(500.0, consumer.getPowerMagnitude(), 1.0e-12);
    consumer.setDuty(500.0);
    assertEquals(0.0, bus.getNetPower(), 1.0e-12);
    assertEquals(500.0, consumer.getPowerMagnitude(), 1.0e-12);
  }

  @Test
  void testBidirectionalSpecificationRecordsHeatWithdrawal() {
    EnergyBus bus = new EnergyBus("heat recovery", EnergyType.HEAT);
    EnergyPort condenser = new EnergyPort("heatDuty", EnergyType.HEAT, EnergyPortDirection.OUTPUT,
        EnergyPortMode.CALCULATED);
    condenser.setOwnerName("condenser");
    condenser.connect(bus);
    condenser.setDuty(-1000.0);

    EnergyPort heater = new EnergyPort("heatDuty", EnergyType.HEAT, EnergyPortDirection.BIDIRECTIONAL,
        EnergyPortMode.SPECIFICATION);
    heater.setOwnerName("heater");
    heater.connect(bus);
    heater.setDuty(heater.getPowerMagnitude());

    assertEquals(1000.0, bus.getContribution("condenser.heatDuty"), 1.0e-12);
    assertEquals(-1000.0, bus.getContribution("heater.heatDuty"), 1.0e-12);
    assertEquals(0.0, bus.getNetPower(), 1.0e-12);
  }

  @Test
  void testSerializationPreservesSharedBusIdentityAndMetadata() throws Exception {
    EnergyBus bus = new EnergyBus("serialized bus", EnergyType.ELECTRICAL);
    EnergyPort producer = new EnergyPort("power", EnergyType.ELECTRICAL, EnergyPortDirection.OUTPUT,
        EnergyPortMode.CALCULATED);
    producer.setOwnerName("producer");
    producer.connect(bus);
    producer.setDuty(250.0);
    EnergyPort consumer = new EnergyPort("power", EnergyType.ELECTRICAL, EnergyPortDirection.INPUT,
        EnergyPortMode.SPECIFICATION);
    consumer.setOwnerName("consumer");
    consumer.connect(bus);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(Arrays.asList(producer, consumer));
    }

    List<?> restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (List<?>) input.readObject();
    }

    EnergyPort restoredProducer = (EnergyPort) restored.get(0);
    EnergyPort restoredConsumer = (EnergyPort) restored.get(1);
    assertSame(restoredProducer.getEnergyStream(), restoredConsumer.getEnergyStream());
    assertEquals(EnergyType.ELECTRICAL, restoredProducer.getEnergyType());
    assertEquals(EnergyPortMode.SPECIFICATION, restoredConsumer.getMode());
    assertEquals(250.0, restoredConsumer.getPowerMagnitude(), 1.0e-12);
  }

  @Test
  void testMechanicalShaftBalancesGenerationAndLoad() {
    MechanicalShaft shaft = new MechanicalShaft("compressor train");
    shaft.setMechanicalEfficiency(0.98);
    shaft.setGeneratedPower("expander", 10.0e6);
    shaft.setConsumedPower("compressor", 8.5e6);
    shaft.setConsumedPower("oil pump", 0.5e6);

    assertEquals(0.8, shaft.getNetPower("MW"), 1.0e-12);
    assertEquals(EnergyType.SHAFT_WORK, shaft.getEnergyType());
  }

  @Test
  void testEquipmentPortsPublishDirectedBusContributions() {
    EnergyBus bus = new EnergyBus("electrical bus", EnergyType.ELECTRICAL);
    SolarPanel solar = new SolarPanel("solar", 1000.0, 10.0, 0.20);
    solar.connectEnergyStream("electricalPower", bus, EnergyPortMode.CALCULATED);
    solar.run();

    BatteryStorage battery = new BatteryStorage("battery", 10.0e6);
    battery.connectEnergyStream("electricalPower", bus, EnergyPortMode.CALCULATED);
    battery.charge(1000.0, 1.0);
    battery.run();

    assertEquals(2000.0, bus.getContribution("solar.electricalPower"), 1.0e-12);
    assertEquals(-1000.0, bus.getContribution("battery.electricalPower"), 1.0e-12);
    assertEquals(1000.0, bus.getNetPower(), 1.0e-12);

    battery.discharge(500.0, 1.0);
    battery.run();

    assertEquals(500.0, bus.getContribution("battery.electricalPower"), 1.0e-12);
    assertEquals(2500.0, bus.getNetPower(), 1.0e-12);
  }

}
