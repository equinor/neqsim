package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Tests for CoolingWaterSystem.
 */
public class CoolingWaterSystemTest {

  @Test
  public void testBasicSizing() {
    CoolingWaterSystem cws = new CoolingWaterSystem();
    cws.addCoolingRequirement("After-Cooler", 5000.0, 40.0, 10.0);
    cws.addCoolingRequirement("Condenser", 3000.0, 55.0, 15.0);
    cws.setCoolingWaterSupplyTemperature(25.0);
    cws.setCoolingWaterReturnTemperature(35.0);
    cws.setPumpEfficiency(0.75);
    cws.setSystemPressureDrop(3.0);

    cws.calculate();

    // Total duty should be 8000 kW
    assertEquals(8000.0, cws.getTotalDuty(), 0.1);

    // CW flow rate: Q = 8000 kW / (4.186 kJ/(kg*K) * 10 K) = 191.1 kg/s = 688 m3/hr
    double expectedFlow = (8000.0 / (4.186 * 10.0)) / 1000.0 * 3600.0;
    assertEquals(expectedFlow, cws.getTotalCWFlowRate(), expectedFlow * 0.01);

    // Pump power > 0
    double pumpPower = cws.getPumpPower();
    assertTrue(pumpPower > 0);

    // Total power > pump power (includes tower fan)
    assertTrue(cws.getTotalElectricalPower() > pumpPower);
  }

  @Test
  public void testOperatingCost() {
    CoolingWaterSystem cws = new CoolingWaterSystem();
    cws.addCoolingRequirement("Cooler 1", 1000.0, 45.0, 10.0);
    cws.setCoolingWaterSupplyTemperature(20.0);
    cws.setCoolingWaterReturnTemperature(30.0);
    cws.setElectricityCost(0.10);
    cws.setAnnualOperatingHours(8000.0);

    cws.calculate();

    double annualCost = cws.getAnnualOperatingCost();
    assertTrue(annualCost > 0, "Annual operating cost should be positive");

    // Cost = totalPower * hours * electricityCost
    double expectedCost = cws.getTotalElectricalPower() * 8000.0 * 0.10;
    assertEquals(expectedCost, annualCost, expectedCost * 0.01);
  }

  @Test
  public void testSingleCooler() {
    CoolingWaterSystem cws = new CoolingWaterSystem();
    cws.addCoolingRequirement("Single", 500.0, 35.0, 5.0);
    cws.setCoolingWaterSupplyTemperature(25.0);
    cws.setCoolingWaterReturnTemperature(35.0);

    cws.calculate();

    assertEquals(500.0, cws.getTotalDuty(), 0.1);
    assertTrue(cws.getTotalCWFlowRate() > 0);
    assertTrue(cws.getPumpPower() > 0);
    assertTrue(cws.getTowerFanPower() > 0);
  }

  @Test
  public void testJsonOutput() {
    CoolingWaterSystem cws = new CoolingWaterSystem();
    cws.addCoolingRequirement("AC-1", 2000.0, 40.0, 10.0);
    cws.addCoolingRequirement("AC-2", 1500.0, 50.0, 15.0);
    cws.calculate();

    String json = cws.toJson();
    assertNotNull(json);
    assertTrue(json.contains("Cooling Water System"));
    assertTrue(json.contains("coolingRequirements"));
    assertTrue(json.contains("totalCWFlowRate"));
    assertTrue(json.contains("pumpPower_kW"));
    assertTrue(json.contains("annualOperatingCost"));
  }

  @Test
  public void testEmptySystem() {
    CoolingWaterSystem cws = new CoolingWaterSystem();
    cws.calculate();

    assertEquals(0.0, cws.getTotalDuty(), 1e-6);
    assertEquals(0.0, cws.getTotalCWFlowRate(), 1e-6);
    assertEquals(0.0, cws.getAnnualOperatingCost(), 1e-6);
  }
}
