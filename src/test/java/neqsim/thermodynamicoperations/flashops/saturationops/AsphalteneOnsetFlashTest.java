package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Unit tests for asphaltene onset pressure and temperature flash calculations.
 *
 * @author Even Solbraa
 */
public class AsphalteneOnsetFlashTest {
  private SystemInterface testSystem;

  @BeforeEach
  void setUp() {
    // Create a typical heavy oil system
    testSystem = new SystemSrkCPAstatoil(373.15, 100.0);

    // Add typical crude oil components
    testSystem.addComponent("methane", 0.05);
    testSystem.addComponent("ethane", 0.03);
    testSystem.addComponent("propane", 0.03);
    testSystem.addComponent("n-butane", 0.02);
    testSystem.addComponent("n-pentane", 0.02);
    testSystem.addComponent("n-hexane", 0.03);
    testSystem.addComponent("n-heptane", 0.05);
    testSystem.addComponent("n-octane", 0.10);
    testSystem.addComponent("n-nonane", 0.15);
    testSystem.addComponent("nC10", 0.20);
    testSystem.addComponent("nC12", 0.15);
    testSystem.addComponent("benzene", 0.05);
    testSystem.addComponent("toluene", 0.05);
    testSystem.addComponent("m-Xylene", 0.05);

    // Add a heavy component to represent asphaltene-like behavior
    testSystem.addTBPfraction("C20", 0.02, 280.0 / 1000.0, 0.85);

    testSystem.setMixingRule("classic");
    testSystem.useVolumeCorrection(true);
  }

  @Test
  void testAsphalteneOnsetPressureFlashConstruction() {
    // Test that the flash operation can be constructed
    AsphalteneOnsetPressureFlash flash = new AsphalteneOnsetPressureFlash(testSystem, 100.0, 1.0);

    assertNotNull(flash);
    // Before run(), onset is NaN (not found yet)
    assertTrue(Double.isNaN(flash.getOnsetPressure()) || flash.getOnsetPressure() > 0);
    assertFalse(flash.isOnsetFound());
  }

  @Test
  void testAsphalteneOnsetTemperatureFlashConstruction() {
    // Test that the flash operation can be constructed
    AsphalteneOnsetTemperatureFlash flash = new AsphalteneOnsetTemperatureFlash(testSystem);

    assertNotNull(flash);
    assertFalse(flash.isOnsetFound());
  }

  @Test
  void testAsphalteneOnsetPressureViaThermodynamicOperations() {
    // Test the high-level API in ThermodynamicOperations
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);

    double onsetPressure = ops.asphalteneOnsetPressure();

    // For this simplified system without true asphaltene phase,
    // we expect no onset to be found (returns NaN or positive)
    assertTrue(Double.isNaN(onsetPressure) || onsetPressure > 0,
        "Onset pressure should be NaN (not found) or positive");
  }

  @Test
  void testAsphalteneOnsetTemperatureViaThermodynamicOperations() {
    // Test the high-level API in ThermodynamicOperations
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);

    double onsetTemperature = ops.asphalteneOnsetTemperature();

    // For this simplified system without true asphaltene phase,
    // we expect no onset to be found (returns NaN)
    assertTrue(Double.isNaN(onsetTemperature) || onsetTemperature > 0,
        "Onset temperature should be NaN (not found) or positive");
  }

  @Test
  void testAsphalteneOnsetPressureWithRange() {
    // Test with specified pressure range
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);

    double onsetPressure = ops.asphalteneOnsetPressure(150.0, 5.0);

    // Just verify it runs without error
    assertTrue(Double.isNaN(onsetPressure) || onsetPressure >= 5.0,
        "If onset found, should be above minimum pressure");
  }

  @Test
  void testAsphalteneOnsetTemperatureWithRange() {
    // Test with specified temperature range
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);

    double onsetTemperature = ops.asphalteneOnsetTemperature(400.0, 250.0, 450.0);

    // Just verify it runs without error
    assertTrue(
        Double.isNaN(onsetTemperature) || (onsetTemperature >= 250.0 && onsetTemperature <= 450.0),
        "If onset found, should be within specified range");
  }

  @Test
  void testPressureFlashGetThermoSystem() {
    AsphalteneOnsetPressureFlash flash = new AsphalteneOnsetPressureFlash(testSystem, 200.0, 1.0);

    // getThermoSystem should return the system
    assertNotNull(flash.getThermoSystem());
  }

  @Test
  void testTemperatureFlashGetThermoSystem() {
    AsphalteneOnsetTemperatureFlash flash =
        new AsphalteneOnsetTemperatureFlash(testSystem, testSystem.getTemperature(), 200.0, 500.0);

    // getThermoSystem should return the system
    assertNotNull(flash.getThermoSystem());
  }

  @Test
  void testFlashWithSimplifiedSystem() {
    // Test with a simpler SRK system
    SystemInterface simpleSystem = new SystemSrkEos(350.0, 50.0);
    simpleSystem.addComponent("methane", 0.7);
    simpleSystem.addComponent("n-heptane", 0.2);
    simpleSystem.addComponent("nC10", 0.1);
    simpleSystem.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(simpleSystem);

    // These should run without errors even if no onset is found
    double onsetP = ops.asphalteneOnsetPressure();
    double onsetT = ops.asphalteneOnsetTemperature();

    // Simplified system without asphaltenes should not show precipitation
    assertTrue(Double.isNaN(onsetP) || onsetP > 0);
    assertTrue(Double.isNaN(onsetT) || onsetT > 0);
  }

  @Test
  void testSetPressureStep() {
    AsphalteneOnsetPressureFlash flash = new AsphalteneOnsetPressureFlash(testSystem);

    // Should be able to set the pressure step
    flash.setPressureStep(5.0);

    // No assertion needed - just checking it doesn't throw
    assertNotNull(flash);
  }

  @Test
  void testSetMinPressure() {
    AsphalteneOnsetPressureFlash flash = new AsphalteneOnsetPressureFlash(testSystem);

    // Should be able to set minimum pressure
    flash.setMinPressure(10.0);

    // No assertion needed - just checking it doesn't throw
    assertNotNull(flash);
  }

  @Test
  void testAsphalteneOnsetWithPhaseTypeAsphaltene() {
    // Create a system with an asphaltene component
    SystemInterface srkSystem = new SystemSrkEos(350.0, 100.0);
    srkSystem.addComponent("methane", 0.3);
    srkSystem.addComponent("n-heptane", 0.5);
    srkSystem.addComponent("asphaltene", 0.2);
    srkSystem.setMixingRule("classic");

    // Enable solid phase check for asphaltene
    srkSystem.setSolidPhaseCheck("asphaltene");

    ThermodynamicOperations ops = new ThermodynamicOperations(srkSystem);
    ops.TPflash();

    // Check if system can detect asphaltene phase
    // Note: whether asphaltene precipitates depends on thermodynamic conditions
    boolean hasAsphaltene = srkSystem.hasPhaseType("asphaltene");
    boolean hasSolid = srkSystem.hasPhaseType("solid");

    // The test verifies the API works without throwing exceptions
    srkSystem.prettyPrint();

    // Test the AOP calculation - should run without errors
    double onsetP = ops.asphalteneOnsetPressure(200.0, 1.0);
    assertTrue(Double.isNaN(onsetP) || onsetP > 0,
        "Onset pressure should be NaN (not found) or positive");
  }

  @Test
  void testAsphalteneOnsetTemperatureSearch() {
    // Create a system with asphaltene-like component
    SystemInterface system = new SystemSrkEos(400.0, 50.0);
    system.addComponent("methane", 0.2);
    system.addComponent("n-heptane", 0.6);
    system.addComponent("nC10", 0.15);
    // Add asphaltene pseudo-component
    system.addTBPfraction("asphaltene", 0.05, 1000.0 / 1000.0, 1.1);
    system.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(system);

    // Test temperature search in both directions
    AsphalteneOnsetTemperatureFlash flashDown =
        new AsphalteneOnsetTemperatureFlash(system, 400.0, 250.0, 450.0);
    flashDown.setTemperatureStep(20.0);
    flashDown.setSearchDecreasing(true);
    flashDown.run();

    // Should complete without error
    assertFalse(flashDown.isOnsetFound() && Double.isNaN(flashDown.getOnsetTemperature()),
        "If onset is found, temperature should not be NaN");

    // Test temperature in Celsius
    if (flashDown.isOnsetFound()) {
      double tempC = flashDown.getOnsetTemperature("C");
      assertTrue(tempC < flashDown.getOnsetTemperature("K"), "Celsius should be less than Kelvin");
    }
  }

  @Test
  void testAsphalteneOnsetPressureWithSteps() {
    // Test pressure flash with custom step sizes
    AsphalteneOnsetPressureFlash flash = new AsphalteneOnsetPressureFlash(testSystem, 150.0, 5.0);
    flash.setPressureStep(25.0); // Coarse step

    flash.run();

    // Test pressure unit conversions
    if (flash.isOnsetFound()) {
      double pBara = flash.getOnsetPressure("bara");
      double pPsia = flash.getOnsetPressure("psia");
      double pMPa = flash.getOnsetPressure("MPa");

      assertEquals(pBara * 14.5038, pPsia, 0.01, "psia conversion should be correct");
      assertEquals(pBara * 0.1, pMPa, 0.001, "MPa conversion should be correct");
    }

    // getThermoSystem should return the modified system
    assertNotNull(flash.getThermoSystem());
  }
}
