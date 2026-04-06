package neqsim.process.equipment.powergeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the SteamTurbine class.
 */
class SteamTurbineTest {

  private SystemSrkEos steamFluid;
  private Stream steamStream;

  @BeforeEach
  void setUp() {
    // Create a water/steam fluid at high temperature and pressure
    steamFluid = new SystemSrkEos(273.15 + 400.0, 40.0);
    steamFluid.addComponent("water", 1.0);
    steamFluid.setMixingRule("classic");

    steamStream = new Stream("HP steam", steamFluid);
    steamStream.setFlowRate(10.0, "kg/sec");
    steamStream.setTemperature(400.0, "C");
    steamStream.setPressure(40.0, "bara");
  }

  @Test
  void testSteamTurbineProducesPower() {
    ProcessSystem process = new ProcessSystem();
    process.add(steamStream);

    SteamTurbine turbine = new SteamTurbine("ST-100", steamStream);
    turbine.setOutletPressure(1.0);
    turbine.setIsentropicEfficiency(0.85);
    process.add(turbine);

    process.run();

    // Power should be positive (turbine produces power)
    double power = turbine.getPower();
    assertTrue(power > 0, "Steam turbine should produce positive power, got: " + power);
  }

  @Test
  void testPowerUnits() {
    ProcessSystem process = new ProcessSystem();
    process.add(steamStream);

    SteamTurbine turbine = new SteamTurbine("ST", steamStream);
    turbine.setOutletPressure(1.0);
    turbine.setIsentropicEfficiency(0.85);
    process.add(turbine);
    process.run();

    double powerW = turbine.getPower("W");
    double powerkW = turbine.getPower("kW");
    double powerMW = turbine.getPower("MW");

    assertEquals(powerW / 1000.0, powerkW, 0.001);
    assertEquals(powerW / 1.0e6, powerMW, 0.000001);
  }

  @Test
  void testOutletTemperatureIsLower() {
    ProcessSystem process = new ProcessSystem();
    process.add(steamStream);

    SteamTurbine turbine = new SteamTurbine("ST", steamStream);
    turbine.setOutletPressure(5.0);
    turbine.setIsentropicEfficiency(0.85);
    process.add(turbine);
    process.run();

    double inletTemp = steamStream.getTemperature("C");
    double outletTemp = turbine.getOutletStream().getTemperature("C");
    assertTrue(outletTemp < inletTemp, "Outlet temperature (" + outletTemp
        + " C) should be less than inlet (" + inletTemp + " C)");
  }

  @Test
  void testPressureSettings() {
    SteamTurbine turbine = new SteamTurbine("ST");
    turbine.setOutletPressure(5.0, "bara");
    // Just verify it doesn't throw

    turbine.setOutletPressure(50.0, "psi");
    // psi to bara conversion

    turbine.setOutletPressure(4.0, "barg");
    // barg to bara conversion
  }

  @Test
  void testIsentropicEfficiencyAccessors() {
    SteamTurbine turbine = new SteamTurbine("ST");
    turbine.setIsentropicEfficiency(0.90);
    assertEquals(0.90, turbine.getIsentropicEfficiency(), 0.001);
  }

  @Test
  void testNumberOfStages() {
    SteamTurbine turbine = new SteamTurbine("ST");
    turbine.setNumberOfStages(3);
    assertEquals(3, turbine.getNumberOfStages());
  }
}
