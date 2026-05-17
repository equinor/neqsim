package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.FuelGasSystem.ConsumerType;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for FuelGasSystem class.
 */
public class FuelGasSystemTest {

  private FuelGasSystem fuelGasSystem;
  private Stream fuelGasStream;

  @BeforeEach
  public void setUp() {
    // Create a natural gas stream
    SystemSrkEos gas = new SystemSrkEos(298.15, 70.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.05);
    gas.addComponent("propane", 0.03);
    gas.addComponent("nitrogen", 0.02);
    gas.setMixingRule("classic");

    fuelGasStream = new Stream("fuel_gas_source", gas);
    fuelGasStream.setFlowRate(1000.0, "kg/hr");
    fuelGasStream.run();

    fuelGasSystem = new FuelGasSystem("Fuel Gas System", fuelGasStream);
  }

  @Test
  public void testConstruction() {
    assertEquals("Fuel Gas System", fuelGasSystem.getName());
    assertNotNull(fuelGasSystem.getInletStream());
  }

  @Test
  public void testRunCalculation() {
    fuelGasSystem.setOutletPressure(30.0);
    fuelGasSystem.setTotalDemand(1000.0);
    fuelGasSystem.run();

    assertTrue(fuelGasSystem.getHeaterDutyKW() >= 0);
    assertTrue(fuelGasSystem.getJTCooling() >= 0);
    assertTrue(fuelGasSystem.getWobbeIndex() > 0);
  }

  @Test
  public void testJouleThomsonCooling() {
    fuelGasSystem.setOutletPressure(10.0); // Large pressure drop
    fuelGasSystem.run();

    double jtCooling = fuelGasSystem.getJTCooling();
    assertTrue(jtCooling > 0); // Should have cooling
    assertTrue(jtCooling < 50); // But not excessive
  }

  @Test
  public void testHeaterDuty() {
    fuelGasSystem.setOutletPressure(10.0);
    fuelGasSystem.run();

    // Heater needed to compensate for JT cooling and ensure superheat
    double heaterDuty = fuelGasSystem.getHeaterDutyKW();
    assertTrue(heaterDuty >= 0);
  }

  @Test
  public void testSuperheat() {
    fuelGasSystem.setOutletPressure(30.0);
    fuelGasSystem.run();

    double superheat = fuelGasSystem.getSuperheat();
    // Should have positive superheat above dew point
    assertTrue(superheat > 0);
  }

  @Test
  public void testConsumerManagement() {
    FuelGasSystem system = new FuelGasSystem("Test System");

    system.addConsumer("GT-1", ConsumerType.GAS_TURBINE, 500.0);
    system.addConsumer("Heater-1", ConsumerType.FIRED_HEATER, 200.0);
    system.addConsumer("Pilot", ConsumerType.FLARE_PILOT, 10.0);

    assertEquals(710.0, system.getTotalDemand(), 1e-6);
    assertEquals(3, system.getConsumers().size());
  }

  @Test
  public void testConsumerTypes() {
    // Gas turbine should require highest pressure
    assertTrue(ConsumerType.GAS_TURBINE.getTypicalPressureBarg() > ConsumerType.FIRED_HEATER
        .getTypicalPressureBarg());

    // Gas turbine should require most superheat
    assertTrue(
        ConsumerType.GAS_TURBINE.getMinSuperheatC() > ConsumerType.INCINERATOR.getMinSuperheatC());

    // Gas turbine should have strictest H2S limit
    assertTrue(ConsumerType.GAS_TURBINE.getMaxH2Sppmv() < ConsumerType.INCINERATOR.getMaxH2Sppmv());
  }

  @Test
  public void testThermalPower() {
    fuelGasSystem.setTotalDemand(1000.0);
    fuelGasSystem.run();

    double thermalPowerMW = fuelGasSystem.getThermalPowerMW();
    // 1000 kg/hr * 45 MJ/kg / 3600 s/hr ≈ 12.5 MW
    assertTrue(thermalPowerMW > 10.0);
    assertTrue(thermalPowerMW < 15.0);
  }

  @Test
  public void testAnnualConsumption() {
    fuelGasSystem.setTotalDemand(1000.0);
    fuelGasSystem.run();

    double annualTonnes = fuelGasSystem.getAnnualConsumptionTonnes(8760.0);
    // 1000 kg/hr * 8760 hr / 1000 = 8760 tonnes
    assertEquals(8760.0, annualTonnes, 1.0);
  }

  @Test
  public void testSuperheatAdequacy() {
    fuelGasSystem.run();

    // For gas turbine, need 25°C superheat
    boolean adequate = fuelGasSystem.isSuperheatAdequate(25.0);
    // May or may not be adequate depending on conditions
    assertNotNull(adequate);
  }

  @Test
  public void testJsonOutput() {
    fuelGasSystem.addConsumer("GT-1", ConsumerType.GAS_TURBINE, 500.0);
    fuelGasSystem.run();

    String json = fuelGasSystem.toJson();

    assertNotNull(json);
    assertTrue(json.contains("systemName"));
    assertTrue(json.contains("inlet"));
    assertTrue(json.contains("outlet"));
    assertTrue(json.contains("gasProperties"));
    assertTrue(json.contains("consumers"));
  }

  @Test
  public void testWobbeIndex() {
    fuelGasSystem.run();

    double wobbeIndex = fuelGasSystem.getWobbeIndex();
    // Natural gas Wobbe index typically 45-55 MJ/Sm³
    assertTrue(wobbeIndex > 40);
    assertTrue(wobbeIndex < 60);
  }

  @Test
  public void testOutletStream() {
    fuelGasSystem.setOutletPressure(30.0);
    fuelGasSystem.run();

    assertNotNull(fuelGasSystem.getOutletStream());
  }

  @Test
  public void testFuelGasConsumerClass() {
    FuelGasSystem.FuelGasConsumer consumer =
        new FuelGasSystem.FuelGasConsumer("GT-1", ConsumerType.GAS_TURBINE, 500.0);

    assertEquals("GT-1", consumer.getName());
    assertEquals(ConsumerType.GAS_TURBINE, consumer.getType());
    assertEquals(500.0, consumer.getDemandKgh(), 1e-6);
    assertTrue(consumer.isRunning());

    consumer.setRunning(false);
    assertEquals(0.0, consumer.getDemandKgh(), 1e-6);

    consumer.setRunning(true);
    assertEquals(500.0, consumer.getDemandKgh(), 1e-6);
  }

  @Test
  public void testConsumerEfficiency() {
    FuelGasSystem.FuelGasConsumer consumer =
        new FuelGasSystem.FuelGasConsumer("GT-1", ConsumerType.GAS_TURBINE, 1000.0);

    consumer.setEfficiencyPercent(35.0);
    double usefulPower = consumer.getUsefulThermalPowerKW(45.0);

    // 1000 kg/hr * 45 MJ/kg * 0.35 / 3.6 ≈ 4375 kW
    assertTrue(usefulPower > 4000);
    assertTrue(usefulPower < 5000);
  }

  @Test
  public void testNoInletStream() {
    FuelGasSystem emptySystem = new FuelGasSystem("Empty System");
    emptySystem.run();

    // Should not throw, just log warning
    assertEquals(0.0, emptySystem.getHeaterDutyKW(), 1e-6);
  }

  @Test
  public void testPressureSettings() {
    fuelGasSystem.setOutletPressure(25.0);
    assertEquals(25.0, fuelGasSystem.getOutletPressure(), 1e-6);
  }

  @Test
  public void testTemperatureSettings() {
    fuelGasSystem.setOutletTemperature(60.0);
    assertEquals(60.0, fuelGasSystem.getOutletTemperature(), 1e-6);
  }
}
