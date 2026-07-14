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

    double fuelHeat = fuelStream.LCV() * fuelStream.getFlowRate("mole/sec");

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
}
