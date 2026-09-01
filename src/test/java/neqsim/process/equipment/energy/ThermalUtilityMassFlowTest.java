package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.UtilityLevel;

class ThermalUtilityMassFlowTest {

  @Test
  void testSteamNetworkReportsPhysicalMassFlows() {
    ThermalUtilityState steamSupply = new ThermalUtilityState(425.0, 4.0e5, 2.8e6);
    ThermalUtilityState condensateReturn = new ThermalUtilityState(383.0, 4.0e5, 0.6e6);
    UtilityEnergyBus steam = new UtilityEnergyBus("LP steam", UtilityLevel.LOW_PRESSURE_STEAM, steamSupply,
        condensateReturn);

    ThermalUtilitySource source = new ThermalUtilitySource("boiler", UtilityLevel.LOW_PRESSURE_STEAM);
    ThermalUtilityConsumer consumer = new ThermalUtilityConsumer("reboiler", UtilityLevel.LOW_PRESSURE_STEAM);
    source.connectEnergyStream(ThermalUtilitySource.OUTPUT_PORT, steam, EnergyPortMode.CALCULATED);
    consumer.connectEnergyStream(ThermalUtilityConsumer.INPUT_PORT, steam, EnergyPortMode.SPECIFICATION);
    source.setAvailablePower(2.0e6);
    consumer.setRequestedPower(1.5e6);

    source.run();
    steam.solveBalance();
    consumer.run();

    assertTrue(steam.hasThermodynamicStates());
    assertEquals(2.2e6, steam.getSpecificDuty(), 1.0e-9);
    assertEquals(2.0e6 / 2.2e6, steam.getOfferedMassFlow(), 1.0e-12);
    assertEquals(1.5e6 / 2.2e6, steam.getAcceptedMassFlow(), 1.0e-12);
    assertEquals(1.5e6 / 2.2e6, steam.getRequestedMassFlow(), 1.0e-12);
    assertEquals(1.5e6 / 2.2e6, steam.getServedMassFlow(), 1.0e-12);
    assertEquals(0.0, steam.getUnmetMassFlow(), 1.0e-12);
    assertEquals(0.5e6 / 2.2e6, steam.getCurtailedMassFlow(), 1.0e-12);
    assertEquals(425.0, steam.getSupplyTemperature(), 1.0e-12);
    assertEquals(4.0e5, steam.getSupplyPressure(), 1.0e-12);
    assertEquals(383.0, steam.getReturnTemperature(), 1.0e-12);
  }

  @Test
  void testCoolingWaterDutyUsesReturnMinusSupplyEnthalpy() {
    ThermalUtilityState coolingWaterSupply = new ThermalUtilityState(293.15, 3.0e5, 84.0e3);
    ThermalUtilityState coolingWaterReturn = new ThermalUtilityState(313.15, 2.5e5, 168.0e3);
    UtilityEnergyBus coolingWater = new UtilityEnergyBus("cooling water", UtilityLevel.COOLING_WATER,
        coolingWaterSupply, coolingWaterReturn);

    assertEquals(84.0e3, coolingWater.getSpecificDuty(), 1.0e-12);
    assertEquals(50.0, coolingWater.getMassFlowForDuty(4.2e6), 1.0e-12);
    assertEquals(3600.0, coolingWater.getMassFlowForDuty(84.0, "kW", "kg/hr"), 1.0e-12);
    assertEquals(3.6, coolingWater.getMassFlowForDuty(84.0, "kW", "ton/hr"), 1.0e-12);
  }

  @Test
  void testUtilityEnthalpyDirectionIsValidatedByGrade() {
    ThermalUtilityState highEnthalpy = new ThermalUtilityState(425.0, 4.0e5, 2.8e6);
    ThermalUtilityState lowEnthalpy = new ThermalUtilityState(383.0, 4.0e5, 0.6e6);

    assertThrows(IllegalArgumentException.class,
        () -> new UtilityEnergyBus("invalid steam", UtilityLevel.LOW_PRESSURE_STEAM, lowEnthalpy, highEnthalpy));
    assertThrows(IllegalArgumentException.class,
        () -> new UtilityEnergyBus("invalid cooling", UtilityLevel.COOLING_WATER, highEnthalpy, lowEnthalpy));
    assertThrows(IllegalArgumentException.class,
        () -> new UtilityEnergyBus("zero duty", UtilityLevel.HOT_OIL, highEnthalpy, highEnthalpy));
  }

  @Test
  void testThermalUtilityStateAndMassFlowPreconditionsAreValidated() {
    assertThrows(IllegalArgumentException.class, () -> new ThermalUtilityState(0.0, 1.0e5, 0.0));
    assertThrows(IllegalArgumentException.class, () -> new ThermalUtilityState(300.0, 0.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> new ThermalUtilityState(300.0, 1.0e5, Double.NaN));

    UtilityEnergyBus steam = new UtilityEnergyBus("unconfigured steam", UtilityLevel.LOW_PRESSURE_STEAM);
    assertThrows(IllegalStateException.class, steam::getSpecificDuty);
    assertThrows(IllegalStateException.class, steam::getServedMassFlow);

    steam.setThermodynamicStates(new ThermalUtilityState(425.0, 4.0e5, 2.8e6),
        new ThermalUtilityState(383.0, 4.0e5, 0.6e6));
    assertThrows(IllegalArgumentException.class, () -> steam.getMassFlowForDuty(-1.0));
    assertThrows(IllegalArgumentException.class, () -> steam.getMassFlowForDuty(1.0, "MW", "lb/hr"));
  }

  @Test
  void testTemperatureAndPressureUpdatesKeepExplicitStateCoherent() {
    UtilityEnergyBus hotOil = new UtilityEnergyBus("hot oil", UtilityLevel.HOT_OIL,
        new ThermalUtilityState(500.0, 5.0e5, 1.0e6), new ThermalUtilityState(400.0, 4.5e5, 0.5e6));

    hotOil.setSupplyTemperature(510.0);
    hotOil.setSupplyPressure(5.5e5);
    hotOil.setReturnTemperature(405.0);

    assertEquals(510.0, hotOil.getSupplyState().getTemperature(), 1.0e-12);
    assertEquals(5.5e5, hotOil.getSupplyState().getPressure(), 1.0e-12);
    assertEquals(405.0, hotOil.getReturnState().getTemperature(), 1.0e-12);
    assertEquals(0.5e6, hotOil.getSpecificDuty(), 1.0e-12);
  }
}
