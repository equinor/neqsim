package neqsim.process.equipment.pump;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for verifying NPSH (Net Positive Suction Head) calculations and cavitation detection.
 *
 * @author NeqSim
 */
public class PumpNPSHTest extends neqsim.NeqSimTest {

  private SystemInterface testFluid;
  private Stream feedStream;
  private Pump pump;

  @BeforeEach
  void setUp() {
    // Create test fluid (water)
    testFluid = new SystemSrkEos(298.15, 1.5);
    testFluid.addComponent("water", 1.0);
    testFluid.init(0);
    testFluid.initPhysicalProperties();

    feedStream = new Stream("Feed", testFluid);
    feedStream.run();

    pump = new Pump("TestPump", feedStream);
    pump.setOutletPressure(5.0, "bara");
    pump.setIsentropicEfficiency(0.75);
  }

  @Test
  void testNPSHCalculationWithGoodConditions() {
    // At 60°C and 1.5 bara, water should have adequate NPSH
    feedStream.run();
    double npsha = pump.getNPSHAvailable();

    // NPSHa should be positive and reasonable
    Assertions.assertTrue(npsha > 0.0, "NPSH available should be positive");
    Assertions.assertTrue(npsha < 100.0, "NPSH available should be reasonable (< 100 m)");
  }

  @Test
  void testNPSHDecreaseWithHigherTemperature() {
    // NPSHa should decrease as temperature increases (higher vapor pressure)
    feedStream.setTemperature(60.0, "C");
    feedStream.run();
    double npsha1 = pump.getNPSHAvailable();

    feedStream.setTemperature(80.0, "C");
    feedStream.run();
    double npsha2 = pump.getNPSHAvailable();

    Assertions.assertTrue(npsha2 < npsha1,
        "NPSH available should decrease with higher temperature");
  }

  @Test
  void testNPSHIncreaseWithHigherPressure() {
    // NPSHa should increase with higher suction pressure
    feedStream.setPressure(1.5, "bara");
    feedStream.run();
    double npsha1 = pump.getNPSHAvailable();

    feedStream.setPressure(2.5, "bara");
    feedStream.run();
    double npsha2 = pump.getNPSHAvailable();

    Assertions.assertTrue(npsha2 > npsha1,
        "NPSH available should increase with higher suction pressure");
  }

  @Test
  void testCavitationDetection() {
    // Enable NPSH checking
    pump.setCheckNPSH(true);

    // Set conditions that should not cause cavitation
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(2.0, "bara");
    feedStream.run();

    boolean cavitating = pump.isCavitating();
    // At 25°C and 2 bara, water should not cavitate
    Assertions.assertFalse(cavitating, "Pump should not cavitate under good suction conditions");
  }

  @Test
  void testNPSHRequiredEstimate() {
    // Test that NPSH required returns reasonable values
    double npshr = pump.getNPSHRequired();

    Assertions.assertTrue(npshr > 0.0, "NPSH required should be positive");
    Assertions.assertTrue(npshr < 20.0,
        "NPSH required should be reasonable for typical pumps (< 20 m)");
  }

  @Test
  void testNPSHMarginSetting() {
    // Test NPSH margin setting and getting
    pump.setNPSHMargin(1.5);
    Assertions.assertEquals(1.5, pump.getNPSHMargin(), 0.01, "NPSH margin should be set correctly");
  }

  @Test
  void testPumpOperationWithLowNPSH() {
    // Set conditions near boiling to create low NPSH situation
    feedStream.setTemperature(95.0, "C");
    feedStream.setPressure(1.05, "bara"); // Just above atmospheric
    feedStream.run();

    pump.setCheckNPSH(true);
    pump.run();

    // Pump should still run but may log warnings
    Assertions.assertNotNull(pump.getOutletStream(), "Pump should produce outlet stream");
  }

  @Test
  void testNPSHWithHydrocarbon() {
    // Test with a hydrocarbon fluid (different vapor pressure behavior)
    SystemInterface hydrocarbon = new SystemSrkEos(298.15, 2.0);
    hydrocarbon.addComponent("n-hexane", 1.0);
    hydrocarbon.init(0);
    hydrocarbon.initPhysicalProperties();

    Stream hcStream = new Stream("HC Feed", hydrocarbon);
    hcStream.run();

    Pump hcPump = new Pump("HC Pump", hcStream);
    hcPump.setOutletPressure(10.0, "bara");

    double npsha = hcPump.getNPSHAvailable();

    Assertions.assertTrue(npsha > 0.0, "NPSH available should be positive for hydrocarbon fluid");
  }
}
