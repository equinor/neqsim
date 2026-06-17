package neqsim.process.equipment.powergeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.battery.BatteryStorage;

/**
 * Tests for OffshoreEnergySystem class.
 */
public class OffshoreEnergySystemTest extends neqsim.NeqSimTest {

  @Test
  public void testWindOnlyDispatch() {
    OffshoreEnergySystem system = new OffshoreEnergySystem("Test Platform");

    WindFarm wind = new WindFarm("Wind", 10);
    wind.setRatedPowerPerTurbine(15.0e6);
    wind.setWakeLossFactor(0.0);
    wind.setAvailabilityFactor(1.0);
    wind.setElectricalLossFactor(0.0);
    wind.setWindSpeed(12.0);
    system.setWindFarm(wind);

    system.setTotalPowerDemand(100.0e6);
    system.setGasTurbineCapacity(0.0); // No gas backup
    system.run();

    // Wind farm produces 150 MW, demand is 100 MW
    assertEquals(100.0e6, system.getWindPowerDelivered(), 1e3);
    assertEquals(50.0e6, system.getWindPowerCurtailed(), 1e3);
    assertEquals(0.0, system.getGasTurbinePowerDelivered(), 1e-6);
    assertEquals(1.0, system.getWindPowerFraction(), 0.01);
  }

  @Test
  public void testGasTurbineBackup() {
    OffshoreEnergySystem system = new OffshoreEnergySystem("Test Platform");

    WindFarm wind = new WindFarm("Wind", 2);
    wind.setRatedPowerPerTurbine(10.0e6);
    wind.setWakeLossFactor(0.0);
    wind.setAvailabilityFactor(1.0);
    wind.setElectricalLossFactor(0.0);
    wind.setWindSpeed(12.0); // At rated
    system.setWindFarm(wind);

    system.setTotalPowerDemand(50.0e6); // 50 MW demand
    system.setGasTurbineCapacity(50.0e6);
    system.setGasTurbineMinLoad(0.0);
    system.run();

    // Wind delivers 20 MW, gas covers 30 MW
    assertEquals(20.0e6, system.getWindPowerDelivered(), 1e3);
    assertEquals(30.0e6, system.getGasTurbinePowerDelivered(), 1e3);
    assertTrue(system.getCO2Emissions() > 0.0);
  }

  @Test
  public void testBatteryDispatch() {
    OffshoreEnergySystem system = new OffshoreEnergySystem("BESS Platform");

    // No wind
    system.setTotalPowerDemand(10.0e6);
    system.setGasTurbineCapacity(0.0);

    // Pre-charged battery (100 MWh)
    BatteryStorage battery = new BatteryStorage("BESS", 100.0e6 * 3600.0);
    battery.setStateOfCharge(100.0e6 * 3600.0); // Full
    system.setBatteryStorage(battery);
    system.setTimeStepHours(1.0);

    system.run();

    // Battery should deliver all 10 MW
    assertTrue(system.getBatteryPowerDelivered() > 9.0e6);
    assertEquals(0.0, system.getGasTurbinePowerDelivered(), 1e-6);
  }

  @Test
  public void testCO2AvoidanceCalculation() {
    OffshoreEnergySystem system = new OffshoreEnergySystem("CO2 Test");

    WindFarm wind = new WindFarm("Wind", 10);
    wind.setRatedPowerPerTurbine(15.0e6);
    wind.setWindSpeed(12.0);
    wind.setWakeLossFactor(0.0);
    wind.setAvailabilityFactor(1.0);
    wind.setElectricalLossFactor(0.0);
    system.setWindFarm(wind);

    system.setTotalPowerDemand(100.0e6);
    system.setGasTurbineCapacity(0.0);
    system.setCO2EmissionFactor(0.55);
    system.run();

    // Full wind power, all CO2 avoided
    double expectedAvoidance = 100.0e3 * 0.55; // 100 MW = 100,000 kW * 0.55
    assertEquals(expectedAvoidance, system.getCO2Avoided(), 1.0);
  }

  @Test
  public void testHourlyDispatch() {
    OffshoreEnergySystem system = new OffshoreEnergySystem("Hourly Test");

    WindFarm wind = new WindFarm("Wind", 5);
    wind.setRatedPowerPerTurbine(10.0e6);
    wind.setWakeLossFactor(0.0);
    wind.setAvailabilityFactor(1.0);
    wind.setElectricalLossFactor(0.0);
    system.setWindFarm(wind);

    system.setTotalPowerDemand(30.0e6);
    system.setGasTurbineCapacity(30.0e6);
    system.setGasTurbineMinLoad(0.0);

    double[] windSpeeds = {0.0, 5.0, 10.0, 12.0, 20.0, 3.0};
    system.runHourlyDispatch(windSpeeds);

    assertEquals(windSpeeds.length, system.getDispatchHistory().size());
  }

  @Test
  public void testEquipmentFactory() {
    OffshoreEnergySystem system = (OffshoreEnergySystem) neqsim.process.equipment.EquipmentFactory
        .createEquipment("test", "offshoreenergysystem");
    assertTrue(system instanceof OffshoreEnergySystem);
  }
}
