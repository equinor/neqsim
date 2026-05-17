package neqsim.process.equipment.powergeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for WindFarm class.
 */
public class WindFarmTest extends neqsim.NeqSimTest {

  @Test
  public void testBasicPowerCalculation() {
    WindFarm farm = new WindFarm("Test Farm", 10);
    farm.setRatedPowerPerTurbine(15.0e6);
    farm.setRotorDiameter(236.0);
    farm.setWindSpeed(12.0); // At rated speed
    farm.run();

    // At rated speed, should produce close to rated power (minus losses)
    double expected = 15.0e6 * 10 * (1.0 - 0.10) * 0.95 * (1.0 - 0.03);
    assertEquals(expected, farm.getPower(), expected * 0.01);
    assertTrue(farm.getCapacityFactor() > 0.8);
  }

  @Test
  public void testBelowCutIn() {
    WindFarm farm = new WindFarm("Test Farm", 5);
    farm.setRatedPowerPerTurbine(10.0e6);
    farm.setWindSpeed(2.0); // Below cut-in of 3 m/s
    farm.run();

    assertEquals(0.0, farm.getPower(), 1e-6);
    assertEquals(0.0, farm.getCapacityFactor(), 1e-6);
  }

  @Test
  public void testAboveCutOut() {
    WindFarm farm = new WindFarm("Test Farm", 5);
    farm.setRatedPowerPerTurbine(10.0e6);
    farm.setWindSpeed(30.0); // Above cut-out of 25 m/s
    farm.run();

    assertEquals(0.0, farm.getPower(), 1e-6);
  }

  @Test
  public void testCubicPowerCurve() {
    WindFarm farm = new WindFarm("Test Farm", 1);
    farm.setRatedPowerPerTurbine(10.0e6);
    farm.setCutInSpeed(3.0);
    farm.setRatedSpeed(12.0);
    farm.setWakeLossFactor(0.0);
    farm.setAvailabilityFactor(1.0);
    farm.setElectricalLossFactor(0.0);

    // At midpoint between cut-in and rated: (7.5-3)/(12-3) = 0.5
    farm.setWindSpeed(7.5);
    double midPower = farm.calculateTurbinePower(7.5);
    assertEquals(10.0e6 * 0.125, midPower, 1e-3); // 0.5^3 = 0.125
  }

  @Test
  public void testAEPCalculation() {
    WindFarm farm = new WindFarm("AEP Farm", 100);
    farm.setRatedPowerPerTurbine(15.0e6);
    farm.setWeibullShape(2.0);
    farm.setWeibullScale(10.0);

    double aep = farm.calculateAEP();
    assertTrue(aep > 0, "AEP should be positive");
    // 100 turbines * 15 MW * 8760h * ~0.45 CF ~ 5.9 TWh
    assertTrue(aep > 1.0e12, "AEP should be > 1 TWh for large farm");
  }

  @Test
  public void testPowerUnits() {
    WindFarm farm = new WindFarm("Unit Farm", 1);
    farm.setRatedPowerPerTurbine(15.0e6);
    farm.setWindSpeed(12.0);
    farm.setWakeLossFactor(0.0);
    farm.setAvailabilityFactor(1.0);
    farm.setElectricalLossFactor(0.0);
    farm.run();

    assertEquals(farm.getPower() / 1.0e6, farm.getPower("MW"), 1e-6);
    assertEquals(farm.getPower() / 1.0e3, farm.getPower("kW"), 1e-3);
  }

  @Test
  public void testTimeSeries() {
    WindFarm farm = new WindFarm("TS Farm", 10);
    farm.setRatedPowerPerTurbine(10.0e6);
    double[] speeds = {0.0, 3.0, 7.0, 12.0, 15.0, 25.0, 30.0};
    farm.setWindSpeedTimeSeries(speeds);
    farm.runTimeSeries();

    double[] powers = farm.getPowerTimeSeries();
    assertEquals(speeds.length, powers.length);
    assertEquals(0.0, powers[0], 1e-6); // Below cut-in
    assertTrue(powers[3] > powers[2]);   // More wind = more power
    assertEquals(0.0, powers[6], 1e-6); // Above cut-out
  }

  @Test
  public void testAirDensityCorrection() {
    WindFarm farm = new WindFarm("Density Farm", 1);
    farm.setAirTemperature(15.0);
    farm.setAtmosphericPressure(101325.0);

    double rho = farm.getCorrectedAirDensity();
    assertEquals(1.225, rho, 0.005); // Standard conditions
  }

  @Test
  public void testEquipmentFactory() {
    WindFarm farm = (WindFarm) neqsim.process.equipment.EquipmentFactory
        .createEquipment("farm", "windfarm");
    assertTrue(farm instanceof WindFarm);
  }
}
