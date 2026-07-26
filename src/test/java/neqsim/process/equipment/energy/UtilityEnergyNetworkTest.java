package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.UtilityLevel;

class UtilityEnergyNetworkTest {

  @Test
  void testTypedSteamUtilityAllocation() {
    UtilityEnergyBus steam = new UtilityEnergyBus("LP steam", UtilityLevel.LOW_PRESSURE_STEAM, 425.0, 383.0);
    ThermalUtilitySource source = new ThermalUtilitySource("boiler", UtilityLevel.LOW_PRESSURE_STEAM);
    ThermalUtilityConsumer consumer = new ThermalUtilityConsumer("reboiler", UtilityLevel.LOW_PRESSURE_STEAM);
    source.connectEnergyStream(ThermalUtilitySource.OUTPUT_PORT, steam, EnergyPortMode.CALCULATED);
    consumer.connectEnergyStream(ThermalUtilityConsumer.INPUT_PORT, steam, EnergyPortMode.SPECIFICATION);
    source.setAvailablePower(2.0e6);
    consumer.setRequestedPower(1.5e6);

    source.run();
    steam.solveBalance();
    consumer.run();

    assertEquals(1.5e6, consumer.getAllocatedPower(), 1.0e-9);
    assertEquals(0.5e6, steam.getLastReport().getCurtailedSupply(), 1.0e-9);
    assertEquals(UtilityLevel.LOW_PRESSURE_STEAM, steam.getUtilityLevel());
  }
}
