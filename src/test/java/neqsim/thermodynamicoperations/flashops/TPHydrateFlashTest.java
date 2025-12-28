package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test class for TPHydrateFlash.
 *
 * <p>
 * Tests the hydrate TPflash functionality that calculates hydrate phase fraction and composition at
 * given temperature and pressure conditions.
 * </p>
 */
public class TPHydrateFlashTest {

  /**
   * Test basic hydrate TPflash with methane and water using CPA EOS.
   */
  @Test
  void testMethaneWaterHydrateFlash() {
    // Create a fluid with methane and water at conditions where hydrate forms
    // Methane hydrate forms below ~15°C at 50 bar
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 5.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule(10); // CPA mixing rule

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    // Verify the flash ran without errors
    assertNotNull(fluid);

    // Check if system has proper phase types
    assertTrue(fluid.getNumberOfPhases() >= 1);
  }

  /**
   * Test hydrate formation temperature calculation to verify hydrate check works.
   *
   * @throws Exception if calculation fails
   */
  @Test
  void testHydrateFormationTemperature() throws Exception {
    // At 50 bar, methane hydrate forms around 10-15°C
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 10.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule(10);
    fluid.setHydrateCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateFormationTemperature();

    // Should find a hydrate formation temperature
    assertNotNull(fluid);
    assertTrue(fluid.getTemperature() > 273.15, "Hydrate formation temp should be above 0°C");
  }

  /**
   * Test hydrate TPflash with natural gas composition.
   */
  @Test
  void testNaturalGasHydrateFlash() {
    // Natural gas composition at hydrate forming conditions
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 2.0, 80.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-butane", 0.01);
    fluid.addComponent("CO2", 0.01);
    fluid.addComponent("water", 0.10);
    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    // Verify system state
    assertNotNull(fluid);
    assertTrue(fluid.getNumberOfPhases() >= 1);

    // Verify prettyPrint works with hydrate phase
    fluid.prettyPrint();
  }

  /**
   * Test hydrate methods on SystemInterface.
   */
  @Test
  void testHydrateSystemMethods() {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15, 100.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule(10);

    // Run TPflash first without hydrate check
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Methods should work and return sensible values
    double fraction = fluid.getHydrateFraction();
    assertTrue(fraction >= 0.0);
  }

  /**
   * Test PhaseHydrate methods for cavity occupancy.
   */
  @Test
  void testPhaseHydrateMethods() {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 2.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule(10);
    fluid.setHydrateCheck(true);
    fluid.init(0);
    fluid.init(1);

    // Get the hydrate phase (phase index 4)
    PhaseHydrate hydratePhase = (PhaseHydrate) fluid.getPhase(4);

    // Test structure method
    int structure = hydratePhase.getStableHydrateStructure();
    assertTrue(structure == 1 || structure == 2, "Structure should be 1 or 2");

    // Test cavity occupancy methods
    double smallOcc = hydratePhase.getSmallCavityOccupancy(structure);
    double largeOcc = hydratePhase.getLargeCavityOccupancy(structure);
    assertTrue(smallOcc >= 0.0 && smallOcc <= 1.0);
    assertTrue(largeOcc >= 0.0 && largeOcc <= 1.0);

    // Test hydration number
    double hydrationNumber = hydratePhase.getHydrationNumber();
    assertTrue(hydrationNumber > 0);
  }

  /**
   * Test that HYDRATE phase type shows correctly.
   */
  @Test
  void testHydratePhaseTypeDisplay() {
    // Verify the PhaseType description was updated
    assertEquals("gas hydrate", PhaseType.HYDRATE.getDesc());
  }

  /**
   * Test hydrate flash with CO2.
   */
  @Test
  void testCO2HydrateFlash() {
    // CO2 hydrate forms at different conditions than methane
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 3.0, 30.0);
    fluid.addComponent("CO2", 0.85);
    fluid.addComponent("water", 0.15);
    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    assertNotNull(fluid);
    assertTrue(fluid.getNumberOfPhases() >= 1);
  }

  /**
   * Test hydrate flash with H2S.
   */
  @Test
  void testH2SHydrateFlash() {
    // H2S is a strong hydrate former
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 10.0, 20.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("H2S", 0.05);
    fluid.addComponent("water", 0.15);
    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    assertNotNull(fluid);
    assertTrue(fluid.getNumberOfPhases() >= 1);
  }

  /**
   * Test that hydrateTPflash enables hydrate check automatically.
   */
  @Test
  void testAutoEnableHydrateCheck() {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 5.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    // Hydrate check should now be enabled
    assertTrue(fluid.getHydrateCheck());
  }

  /**
   * Test the overloaded hydrateTPflash with solid check.
   */
  @Test
  void testHydrateTPflashWithSolidCheck() {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 - 5.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("water", 0.15);
    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash(true); // Enable solid check (for ice, etc.)

    assertNotNull(fluid);
    assertTrue(fluid.getNumberOfPhases() >= 1);
  }
}
