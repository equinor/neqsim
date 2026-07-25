package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import org.junit.jupiter.api.Test;

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
  void testMechanicalShaftBalancesGenerationAndLoad() {
    MechanicalShaft shaft = new MechanicalShaft("compressor train");
    shaft.setMechanicalEfficiency(0.98);
    shaft.setGeneratedPower("expander", 10.0e6);
    shaft.setConsumedPower("compressor", 8.5e6);
    shaft.setConsumedPower("oil pump", 0.5e6);

    assertEquals(0.8, shaft.getNetPower("MW"), 1.0e-12);
    assertEquals(EnergyType.SHAFT_WORK, shaft.getEnergyType());
  }
}
