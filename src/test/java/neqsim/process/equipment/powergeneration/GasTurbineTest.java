package neqsim.process.equipment.powergeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

public class GasTurbineTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(GasTurbineTest.class);

  /** Logger object for class. */

  static neqsim.thermo.system.SystemInterface testSystem;
  static Stream gasStream;

  @BeforeAll
  static void setUp() {
    testSystem = new SystemSrkEos(298.15, 1.0);
    testSystem.addComponent("methane", 1.0);

    gasStream = new Stream("turbine stream", testSystem);
    gasStream.setFlowRate(1.0, "MSm3/day");
    gasStream.setTemperature(50.0, "C");
    gasStream.setPressure(2.0, "bara");
  }

  @Test
  void testSetInletStream() {
    GasTurbine gasturb = new GasTurbine("turbine");
    gasturb.setInletStream(gasStream);

    Assertions.assertEquals(new GasTurbine("turbine", gasStream), gasturb);
  }

  @Test
  void testStreamConstructorInitializesAirSide() {
    GasTurbine gasturb = new GasTurbine("turbine", gasStream);

    assertNotNull(gasturb.airStream);
    assertNotNull(gasturb.airCompressor);
  }

  @Test
  void testGetMechanicalDesign() {
  }

  @Test
  void testRun() {
    gasStream.run();
    GasTurbine gasturb = new GasTurbine("turbine", gasStream);

    gasturb.run();

    logger.info("power generated " + gasturb.getPower() / 1.0e6);
    logger.info("heat generated " + gasturb.getHeat() / 1.0e6);

    // The Brayton cycle must produce a finite, non-zero net shaft power and reject heat.
    Assertions.assertTrue(Double.isFinite(gasturb.getPower()), "power must be finite");
    Assertions.assertTrue(gasturb.getPower() > 0.0, "net power must be positive");
    Assertions.assertTrue(Double.isFinite(gasturb.getHeat()), "heat must be finite");
  }

  @Test
  void testIdealAiFuelRatio() {
    testSystem = new SystemSrkEos(298.15, 1.0);
    testSystem.addComponent("nitrogen", 1.0);
    testSystem.addComponent("CO2", 2.0);
    testSystem.addComponent("methane", 92.0);
    testSystem.addComponent("ethane", 4.0);
    testSystem.addComponent("propane", 2.0);
    testSystem.addComponent("i-butane", 0.5);
    testSystem.addComponent("n-butane", 0.5);
    testSystem.addComponent("n-pentane", 0.01);
    testSystem.addComponent("i-pentane", 0.01);
    testSystem.addComponent("n-hexane", 0.001);

    gasStream = new Stream("turbine stream", testSystem);
    gasStream.setFlowRate(1.0, "MSm3/day");
    gasStream.setTemperature(50.0, "C");
    gasStream.setPressure(2.0, "bara");

    GasTurbine gasturb = new GasTurbine("turbine");
    gasturb.setInletStream(gasStream);
    double AFR = gasturb.calcIdealAirFuelRatio();
    assertEquals(15.8430086719654, AFR, 0.0001);
  }

  @Test
  void testSimpleCycleThermalEfficiencyPower() {
    SystemSrkEos fuel = new SystemSrkEos(298.15, 20.0);
    fuel.addComponent("methane", 0.9);
    fuel.addComponent("ethane", 0.1);
    fuel.setMixingRule("classic");
    Stream fuelStream = new Stream("fuel", fuel);
    fuelStream.setFlowRate(5000.0, "kg/hr");
    fuelStream.setTemperature(25.0, "C");
    fuelStream.setPressure(20.0, "bara");
    fuelStream.run();

    double fuelHeat = fuelStream.LCV() * fuelStream.getFlowRate("Sm3/sec");

    GasTurbine gasturb = new GasTurbine("turbine", fuelStream);
    gasturb.setThermalEfficiency(0.35);
    gasturb.run();

    // Net shaft power must equal efficiency x fuel LHV, and exhaust heat the remainder.
    assertEquals(0.35 * fuelHeat, gasturb.getPower(), Math.abs(0.35 * fuelHeat) * 1e-6);
    assertEquals(fuelHeat - gasturb.getPower(), gasturb.getHeat(), Math.abs(fuelHeat) * 1e-6);
    // Realistic simple-cycle efficiency, unlike the very low detailed-cycle default at 2.5 bara.
    Assertions.assertTrue(gasturb.getPower() / fuelHeat > 0.30, "simple-cycle efficiency must be realistic");
  }

  @Test
  void testHeatRateMapsToEfficiency() {
    GasTurbine gasturb = new GasTurbine("turbine");
    gasturb.setHeatRate(10286.0);
    assertEquals(0.35, gasturb.getThermalEfficiency(), 1e-3);
    assertEquals(10286.0, gasturb.getHeatRate(), 1.0);
  }

  @Test
  void testThermalEfficiencyValidation() {
    GasTurbine gasturb = new GasTurbine("turbine");
    Assertions.assertThrows(IllegalArgumentException.class, () -> gasturb.setThermalEfficiency(1.5));
    Assertions.assertThrows(IllegalArgumentException.class, () -> gasturb.setThermalEfficiency(-0.1));
    Assertions.assertThrows(IllegalArgumentException.class, () -> gasturb.setHeatRate(0.0));
  }

  @Test
  void testPowerDemandModeSizesFuel() {
    SystemSrkEos fuel = new SystemSrkEos(298.15, 20.0);
    fuel.addComponent("methane", 0.9);
    fuel.addComponent("ethane", 0.1);
    fuel.setMixingRule("classic");
    Stream fuelStream = new Stream("fuel", fuel);
    fuelStream.setFlowRate(1000.0, "kg/hr");
    fuelStream.setTemperature(25.0, "C");
    fuelStream.setPressure(20.0, "bara");
    fuelStream.run();

    double efficiency = 0.34;
    double requiredPowerW = 30.0e6; // 30 MW
    GasTurbine gasturb = new GasTurbine("turbine", fuelStream);
    gasturb.setThermalEfficiency(efficiency);
    gasturb.setRequiredPower(30.0, "MW");
    Assertions.assertTrue(gasturb.isPowerDemandMode(), "turbine must be in power-demand mode");
    gasturb.run();

    // The turbine must deliver exactly the required power.
    assertEquals(requiredPowerW, gasturb.getPower(), requiredPowerW * 1e-6);
    // The sized fuel flow must close the energy balance: power = efficiency x fuel LHV.
    double fuelHeat = fuelStream.LCV() * gasturb.getFuelFlowRate("Sm3/sec");
    assertEquals(requiredPowerW, efficiency * fuelHeat, requiredPowerW * 1e-4);
    // Fuel flow must be positive and finite.
    Assertions.assertTrue(gasturb.getFuelFlowRate("mole/sec") > 0.0, "fuel flow must be positive");
    Assertions.assertTrue(Double.isFinite(gasturb.getFuelFlowRate("Sm3/day")), "fuel flow finite");

    // Doubling the required power must roughly double the fuel demand.
    double fuelAt30 = gasturb.getFuelFlowRate("Sm3/sec");
    gasturb.setRequiredPower(60.0, "MW");
    gasturb.run();
    assertEquals(2.0 * fuelAt30, gasturb.getFuelFlowRate("Sm3/sec"), 2.0 * fuelAt30 * 1e-3);
  }

  @Test
  void testPowerDemandModeRequiresEfficiency() {
    SystemSrkEos fuel = new SystemSrkEos(298.15, 20.0);
    fuel.addComponent("methane", 1.0);
    fuel.setMixingRule("classic");
    Stream fuelStream = new Stream("fuel", fuel);
    fuelStream.setFlowRate(1000.0, "kg/hr");
    fuelStream.run();

    GasTurbine gasturb = new GasTurbine("turbine", fuelStream);
    gasturb.setRequiredPower(10.0, "MW"); // no thermal efficiency set
    Assertions.assertThrows(RuntimeException.class, () -> gasturb.run());
  }

  @Test
  void testDrivenLoadAutoSizesFuel() {
    // A compressor as the driven shaft load.
    SystemSrkEos process = new SystemSrkEos(298.15, 20.0);
    process.addComponent("methane", 0.9);
    process.addComponent("ethane", 0.1);
    process.setMixingRule("classic");
    Stream procStream = new Stream("process gas", process);
    procStream.setFlowRate(50000.0, "kg/hr");
    procStream.setTemperature(30.0, "C");
    procStream.setPressure(20.0, "bara");
    procStream.run();
    neqsim.process.equipment.compressor.Compressor comp = new neqsim.process.equipment.compressor.Compressor(
        "load compressor", procStream);
    comp.setOutletPressure(60.0, "bara");
    comp.run();
    double loadPowerW = comp.getPower();
    Assertions.assertTrue(loadPowerW > 0.0, "load compressor power must be positive");

    // Fuel gas for the turbine.
    SystemSrkEos fuel = new SystemSrkEos(298.15, 20.0);
    fuel.addComponent("methane", 0.9);
    fuel.addComponent("ethane", 0.1);
    fuel.setMixingRule("classic");
    Stream fuelStream = new Stream("fuel", fuel);
    fuelStream.setFlowRate(1000.0, "kg/hr");
    fuelStream.run();

    double efficiency = 0.34;
    double auxMW = 5.0;
    GasTurbine gasturb = new GasTurbine("turbine", fuelStream);
    gasturb.setThermalEfficiency(efficiency);
    gasturb.addDrivenLoad(comp);
    gasturb.setAuxiliaryLoad(auxMW, "MW");
    Assertions.assertTrue(gasturb.isPowerDemandMode(), "turbine must be in power-demand mode");
    gasturb.run();

    double expectedPower = loadPowerW + auxMW * 1.0e6;
    assertEquals(expectedPower, gasturb.getAggregatedLoadPowerW(), expectedPower * 1e-6);
    // The turbine delivers exactly the aggregated driven power.
    assertEquals(expectedPower, gasturb.getPower(), expectedPower * 1e-6);
    // The fuel flow closes the energy balance: aggregated power = efficiency x fuel LHV.
    double fuelHeat = fuelStream.LCV() * gasturb.getFuelFlowRate("Sm3/sec");
    assertEquals(expectedPower, efficiency * fuelHeat, expectedPower * 1e-4);
    Assertions.assertEquals(1, gasturb.getDrivenLoads().size());
  }
}
