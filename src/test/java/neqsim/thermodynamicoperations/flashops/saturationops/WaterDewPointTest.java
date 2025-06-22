package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.WaterDewPointAnalyser;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class WaterDewPointTest {
  @Test
  void testWaterDewPointCalculation() {
    // Create a new thermodynamic system
    double ppmWater = 35.0;

    SystemInterface testSystem = new SystemSrkEos(270.15, 70.0);
    testSystem.addComponent("CO2", 0.02);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("methane", 0.9 - ppmWater * 1e-6);
    testSystem.addComponent("ethane", 0.05);
    testSystem.addComponent("propane", 0.01);
    testSystem.addComponent("i-butane", 0.005);
    testSystem.addComponent("n-butane", 0.005);
    testSystem.addComponent("water", ppmWater * 1e-6);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    // Perform the water dew point calculation
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    try {
      ops.waterDewPointTemperatureFlash();
    } catch (Exception e) {
      e.printStackTrace();
    }

    // Get the calculated dew point temperature
    double dewPointTemperature = testSystem.getTemperature("C");

    // Assert the expected value (this value should be adjusted based on expected results)
    assertEquals(-12.0953140, dewPointTemperature, .1, "Dew point temperature is not as expected");
  }

  @Test
  void testWaterDewPointCalculationCPA() {
    // Create a new thermodynamic system
    double ppmWater = 22.0;

    SystemInterface testSystem = new SystemSrkCPAstatoil(260.15, 70.0);
    testSystem.addComponent("CO2", 0.02);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("methane", 0.9 - ppmWater * 1e-6);
    testSystem.addComponent("ethane", 0.05);
    testSystem.addComponent("propane", 0.01);
    testSystem.addComponent("i-butane", 0.005);
    testSystem.addComponent("n-butane", 0.005);
    testSystem.addComponent("water", ppmWater * 1e-6);
    testSystem.setMixingRule(10);
    testSystem.setMultiPhaseCheck(true);

    // Perform the water dew point calculation
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    try {
      ops.waterDewPointTemperatureMultiphaseFlash();
    } catch (Exception e) {
      e.printStackTrace();
    }

    // Get the calculated dew point temperature
    double dewPointTemperature = testSystem.getTemperature("C");

    // Assert the expected value (this value should be adjusted based on expected results)
    assertEquals(-24.71094269, dewPointTemperature, 0.1,
        "Dew point temperature is not as expected");
  }

  @Test
  void testWaterDewPointCalculationCPAhydrate() {
    // Create a new thermodynamic system
    double ppmWater = 22.0;

    SystemInterface testSystem = new SystemSrkCPAstatoil(260.15, 70.0);
    testSystem.addComponent("CO2", 0.02);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("methane", 0.9 - ppmWater * 1e-6);
    testSystem.addComponent("ethane", 0.05);
    testSystem.addComponent("propane", 0.01);
    testSystem.addComponent("i-butane", 0.005);
    testSystem.addComponent("n-butane", 0.005);
    testSystem.addComponent("water", ppmWater * 1e-6);
    testSystem.setMixingRule(10);
    testSystem.setHydrateCheck(true);

    // Perform the water dew point calculation
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    try {
      ops.hydrateFormationTemperature();
    } catch (Exception e) {
      e.printStackTrace();
    }

    // Get the calculated dew point temperature
    double dewPointTemperature = testSystem.getTemperature("C");

    // Assert the expected value (this value should be adjusted based on expected results)
    assertEquals(-17.6585425817, dewPointTemperature, 0.1,
        "Dew point temperature is not as expected");
  }

  @Test
  void testWaterDewPointCalculationGERGwater() {
    // Create a new thermodynamic system
    double ppmWater = 22.0;

    SystemInterface testSystem = new SystemGERGwaterEos(260.15, 70.0);
    testSystem.addComponent("CO2", 0.02);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("methane", 0.9 - ppmWater * 1e-6);
    testSystem.addComponent("ethane", 0.05);
    testSystem.addComponent("propane", 0.01);
    testSystem.addComponent("i-butane", 0.005);
    testSystem.addComponent("n-butane", 0.005);
    testSystem.addComponent("water", ppmWater * 1e-6);
    testSystem.setMixingRule(8);

    // Perform the water dew point calculation
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    try {
      ops.waterDewPointTemperatureFlash();
    } catch (Exception e) {
      e.printStackTrace();
    }

    // Get the calculated dew point temperature
    double dewPointTemperature = testSystem.getTemperature("C");

    // Assert the expected value (this value should be adjusted based on expected results)
    assertEquals(-15.3075727, dewPointTemperature, 0.1, "Dew point temperature is not as expected");
  }

  @Test
  void testWaterDewAnalyser() {
    // Create a new thermodynamic system
    double ppmWater = 22.0;

    SystemInterface testSystem = new SystemPrEos(260.15, 70.0);
    testSystem.addComponent("CO2", 0.02);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("methane", 0.9 - ppmWater * 1e-6);
    testSystem.addComponent("ethane", 0.05);
    testSystem.addComponent("propane", 0.01);
    testSystem.addComponent("i-butane", 0.005);
    testSystem.addComponent("n-butane", 0.005);
    testSystem.addComponent("water", ppmWater * 1e-6);
    testSystem.setMixingRule(2);

    Stream stream = new Stream("testStream", testSystem);
    stream.run();
    WaterDewPointAnalyser analyser = new WaterDewPointAnalyser("wdp", stream);
    analyser.setReferencePressure(70.0);

    analyser.setMethod("GERG-water-EOS");
    // Get the calculated dew point temperature
    double dewPointTemperature = analyser.getMeasuredValue("C");

    // Assert the expected value (this value should be adjusted based on expected results)
    assertEquals(-15.307572714, dewPointTemperature, 0.1,
        "Dew point temperature is not as expected");

    analyser.setMethod("CPA");
    // Get the calculated dew point temperature
    dewPointTemperature = analyser.getMeasuredValue("C");

    // Assert the expected value (this value should be adjusted based on expected results)
    assertEquals(-24.7109426, dewPointTemperature, 0.1, "Dew point temperature is not as expected");

    analyser.setMethod("CPA hydrate");
    // Get the calculated dew point temperature
    dewPointTemperature = analyser.getMeasuredValue("C");

    // Assert the expected value (this value should be adjusted based on expected results)
    assertEquals(-17.658542, dewPointTemperature, 0.1, "Dew point temperature is not as expected");

    analyser.setMethod("Bukacek");
    // Get the calculated dew point temperature
    dewPointTemperature = analyser.getMeasuredValue("C");

    // Assert the expected value (this value should be adjusted based on expected results)
    assertEquals(-23.5090125, dewPointTemperature, 0.1, "Dew point temperature is not as expected");
  }

  @Test
  void testSaturation() {
    double ppmWater = 22.0;

    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 - 24.710, 70.0);
    testSystem.addComponent("CO2", 0.02);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("methane", 0.9 - ppmWater * 1e-6);
    testSystem.addComponent("ethane", 0.05);
    testSystem.addComponent("propane", 0.01);
    testSystem.addComponent("i-butane", 0.005);
    testSystem.addComponent("n-butane", 0.005);
    testSystem.addComponent("water", ppmWater * 1e-6);
    testSystem.setMixingRule(10);

    // Perform the water dew point calculation
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    try {
      ops.saturateWithWater();
    } catch (Exception e) {
      e.printStackTrace();
    }
    double waterContent = testSystem.getPhase("gas").getComponent("water").getx() * 1e6;
    assertEquals(22.0, waterContent, 0.1, "Water content is not as expected");
  }
}
