package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for FiredHeater.
 */
public class FiredHeaterTest {

  @Test
  public void testBasicHeating() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");

    FiredHeater heater = new FiredHeater("Crude Heater", feed);
    heater.setOutTemperature(273.15 + 150.0);
    heater.setThermalEfficiency(0.85);
    heater.setFuelLHV(48.0e6);
    heater.setFuelCO2Factor(2.75);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);
    process.run();

    // Outlet temperature should be ~150 C
    double outTemp = heater.getOutletStream().getTemperature() - 273.15;
    assertEquals(150.0, outTemp, 1.0);

    // Fired duty should be greater than absorbed duty
    double absorbedDuty = heater.getAbsorbedDuty("kW");
    double firedDuty = heater.getFiredDuty("kW");
    assertTrue(absorbedDuty > 0);
    assertTrue(firedDuty > absorbedDuty);

    // Fired duty = absorbed / efficiency
    assertEquals(absorbedDuty / 0.85, firedDuty, firedDuty * 0.01);

    // Fuel consumption should be positive
    double fuelRate = heater.getFuelConsumption("kg/hr");
    assertTrue(fuelRate > 0);

    // CO2 emissions
    double co2 = heater.getCO2Emissions("kg/hr");
    assertEquals(fuelRate * 2.75, co2, co2 * 0.01);
  }

  @Test
  public void testStackLoss() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 20.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");

    FiredHeater heater = new FiredHeater("Test Heater", feed);
    heater.setOutTemperature(273.15 + 100.0);
    heater.setThermalEfficiency(0.90);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);
    process.run();

    double stackLoss = heater.getStackLoss("kW");
    double firedDuty = heater.getFiredDuty("kW");
    double absorbedDuty = heater.getAbsorbedDuty("kW");

    // Stack loss = fired - absorbed
    assertEquals(firedDuty - absorbedDuty, stackLoss, 1.0);

    // Stack loss should be 10% of fired duty
    assertEquals(0.10 * firedDuty, stackLoss, firedDuty * 0.01);
  }

  @Test
  public void testNOxEmissions() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 30.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");

    FiredHeater heater = new FiredHeater("Heater");
    heater.setInletStream(feed);
    heater.setOutTemperature(273.15 + 200.0);
    heater.setNoxFactor(0.08);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);
    process.run();

    double nox = heater.getNOxEmissions("kg/hr");
    assertTrue(nox > 0, "NOx emissions should be positive");
  }

  @Test
  public void testJsonOutput() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 40.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");

    FiredHeater heater = new FiredHeater("JSON Heater", feed);
    heater.setOutTemperature(273.15 + 80.0);
    heater.setThermalEfficiency(0.88);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);
    process.run();

    String json = heater.toJson();
    assertNotNull(json);
    assertTrue(json.contains("FiredHeater"));
    assertTrue(json.contains("thermalEfficiency"));
    assertTrue(json.contains("absorbedDuty_kW"));
    assertTrue(json.contains("firedDuty_kW"));
    assertTrue(json.contains("CO2_kgPerHr"));
  }
}
