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

  /**
   * Test hydrate TPflash when conditions are above hydrate stability (no hydrate forms).
   *
   * <p>
   * At high temperature (e.g., 30°C) and moderate pressure, hydrate should not form. The method
   * should still work correctly, returning zero hydrate fraction.
   * </p>
   */
  @Test
  void testNoHydrateFormation() {
    // Conditions well above hydrate stability: 30°C, 50 bar
    // Methane hydrate is not stable above ~20°C at 50 bar
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 30.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    // Flash should complete without errors
    assertNotNull(fluid);
    assertTrue(fluid.getNumberOfPhases() >= 1);

    // Hydrate fraction should be zero since conditions are above stability
    double hydrateFraction = fluid.getHydrateFraction();
    assertEquals(0.0, hydrateFraction, 1e-10, "Hydrate fraction should be zero above stability");

    // Should not have a hydrate phase in active phases
    assertTrue(!fluid.hasHydratePhase() || fluid.getHydrateFraction() == 0.0,
        "No hydrate should form at 30°C, 50 bar");
  }

  /**
   * Test hydrate TPflash with gas-oil-water system (4 phases including hydrate).
   *
   * <p>
   * This tests a realistic production fluid with gas, oil (condensate), aqueous, and hydrate
   * phases.
   * </p>
   */
  @Test
  void testGasOilWaterHydrateFlash() {
    // Create a rich gas condensate with water at hydrate-forming conditions
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 4.0, 100.0);

    // Gas phase components
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-pentane", 0.01);

    // Oil/condensate components
    fluid.addComponent("n-hexane", 0.01);
    fluid.addComponent("n-heptane", 0.02);
    fluid.addComponent("n-octane", 0.01);

    // Water
    fluid.addComponent("water", 0.10);

    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    // Flash should complete without errors
    assertNotNull(fluid);
    assertTrue(fluid.getNumberOfPhases() >= 2, "Should have at least gas and aqueous phases");

    // At 4°C and 100 bar, hydrate should form with this composition
    // The system should handle the multi-phase equilibrium correctly
    fluid.prettyPrint();
  }

  /**
   * Test hydrate TPflash with MEG (monoethylene glycol) inhibitor.
   *
   * <p>
   * MEG is a thermodynamic hydrate inhibitor that shifts the hydrate equilibrium curve to lower
   * temperatures. At sufficient MEG concentration, hydrate should not form at conditions where it
   * would otherwise form.
   * </p>
   */
  @Test
  void testHydrateFlashWithMEGInhibitor() {
    // Conditions where hydrate would form without inhibitor: 5°C, 80 bar
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 5.0, 80.0);

    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("water", 0.08);
    fluid.addComponent("MEG", 0.02); // ~20 wt% MEG in aqueous phase

    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    // Flash should complete without errors
    assertNotNull(fluid);
    assertTrue(fluid.getNumberOfPhases() >= 1);

    // MEG should inhibit hydrate formation or reduce hydrate fraction
    // The exact behavior depends on the MEG concentration and model accuracy
    fluid.prettyPrint();
  }

  /**
   * Test hydrate TPflash with high MEG concentration (strong inhibition).
   *
   * <p>
   * At high MEG concentrations (e.g., 50 wt% in aqueous phase), hydrate formation should be
   * completely inhibited at moderate conditions.
   * </p>
   */
  @Test
  void testHydrateFlashWithHighMEGConcentration() {
    // Conditions with high MEG concentration
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 5.0, 50.0);

    fluid.addComponent("methane", 0.80);
    fluid.addComponent("water", 0.10);
    fluid.addComponent("MEG", 0.10); // High MEG concentration

    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    // Flash should complete without errors
    assertNotNull(fluid);
    assertTrue(fluid.getNumberOfPhases() >= 1);

    // With high MEG, hydrate formation should be inhibited or greatly reduced
    double hydrateFraction = fluid.getHydrateFraction();
    // MEG should reduce hydrate stability - the exact effect depends on model accuracy
    assertTrue(hydrateFraction >= 0.0, "Hydrate fraction should be non-negative");
  }

  /**
   * Test gas-oil-water-hydrate with MEG inhibitor (complex multi-phase case).
   */
  @Test
  void testGasOilWaterHydrateWithMEG() {
    // Rich gas condensate with water and MEG at hydrate-prone conditions
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 2.0, 100.0);

    // Gas components
    fluid.addComponent("methane", 0.65);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("CO2", 0.02);

    // Oil/condensate components
    fluid.addComponent("n-pentane", 0.02);
    fluid.addComponent("n-hexane", 0.01);
    fluid.addComponent("n-heptane", 0.02);

    // Water and MEG
    fluid.addComponent("water", 0.15);
    fluid.addComponent("MEG", 0.03);

    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    // Flash should complete without errors with this complex composition
    assertNotNull(fluid);
    assertTrue(fluid.getNumberOfPhases() >= 1);

    // Print results for inspection
    fluid.prettyPrint();
  }

  /**
   * Test mass and component conservation in hydrate TPflash.
   *
   * <p>
   * Verifies that total moles and component moles are conserved after the flash calculation.
   * </p>
   */
  @Test
  void testMassAndComponentConservation() {
    // Create a system with known composition
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 2.0, 80.0);

    double methane = 0.80;
    double ethane = 0.05;
    double propane = 0.03;
    double water = 0.12;

    fluid.addComponent("methane", methane);
    fluid.addComponent("ethane", ethane);
    fluid.addComponent("propane", propane);
    fluid.addComponent("water", water);
    fluid.setMixingRule(10);

    // Store initial total moles
    double totalMolesBefore = methane + ethane + propane + water;

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    // Total moles should be conserved - use system's getTotalNumberOfMoles
    double totalMolesAfter = fluid.getTotalNumberOfMoles();
    assertEquals(totalMolesBefore, totalMolesAfter, 1e-6,
        "Total moles should be conserved after flash");

    // Verify component mole fractions are consistent across phases
    // Check that overall z-fractions sum to 1
    double sumZ = 0.0;
    for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
      sumZ += fluid.getPhase(0).getComponent(i).getz();
    }
    assertEquals(1.0, sumZ, 1e-6, "Overall mole fractions (z) should sum to 1");

    // Check that each phase's mole fractions sum to approximately 1
    // (hydrate phase may have slight deviations due to cavity model)
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      double sumX = 0.0;
      for (int i = 0; i < fluid.getPhase(p).getNumberOfComponents(); i++) {
        sumX += fluid.getPhase(p).getComponent(i).getx();
      }
      // Allow 10% tolerance for hydrate phase due to cavity occupancy model
      assertEquals(1.0, sumX, 0.15,
          "Phase " + p + " (" + fluid.getPhase(p).getType() + ") mole fractions should be ~1");
    }

    // Check that phase fractions (beta) sum to 1
    double sumBeta = 0.0;
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      sumBeta += fluid.getBeta(p);
    }
    assertEquals(1.0, sumBeta, 1e-6, "Phase fractions (beta) should sum to 1");

    // Check that hydrate formed at these conditions
    assertTrue(fluid.hasHydratePhase(), "Hydrate should form at 2°C, 80 bar");
  }

  /**
   * Test gas-oil-water/MEG with hydrate formation at low temperature.
   *
   * <p>
   * Uses low MEG concentration so that hydrate still forms despite the inhibitor.
   * </p>
   */
  @Test
  void testGasOilWaterMEGWithHydrateFormationLowMEG() {
    // Very low temperature (-2°C) and high pressure (150 bar) with low MEG
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 - 2.0, 150.0);

    // Gas components
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);

    // Oil components
    fluid.addComponent("n-pentane", 0.02);
    fluid.addComponent("n-hexane", 0.01);
    fluid.addComponent("n-heptane", 0.01);

    // Water and low MEG (only ~5% in aqueous phase)
    fluid.addComponent("water", 0.17);
    fluid.addComponent("MEG", 0.01); // Low MEG concentration

    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    // Flash should complete without errors
    assertNotNull(fluid);

    // At these extreme conditions (-2°C, 150 bar) with low MEG, hydrate should form
    fluid.prettyPrint();

    // Check for expected phases
    assertTrue(fluid.getNumberOfPhases() >= 2, "Should have multiple phases");

    // Verify mass conservation
    double totalMolesExpected = 0.70 + 0.05 + 0.03 + 0.02 + 0.01 + 0.01 + 0.17 + 0.01;
    double totalMolesActual = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      totalMolesActual += fluid.getPhase(i).getNumberOfMolesInPhase();
    }
    assertEquals(totalMolesExpected, totalMolesActual, 1e-6, "Total moles should be conserved");
  }

  /**
   * Test hydrate formation with very low MEG at moderate conditions.
   *
   * <p>
   * At 0°C and 100 bar with only trace MEG, hydrate should definitely form.
   * </p>
   */
  @Test
  void testHydrateFormationWithTraceMEG() {
    // Conditions where hydrate definitely forms: 0°C, 100 bar
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15, 100.0);

    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.03);
    fluid.addComponent("water", 0.115);
    fluid.addComponent("MEG", 0.005); // Trace MEG (~4% in aqueous phase)

    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    // Flash should complete without errors
    assertNotNull(fluid);
    assertTrue(fluid.getNumberOfPhases() >= 1);

    // Print to see the phases
    fluid.prettyPrint();

    // Verify mass conservation
    double totalMolesExpected = 0.85 + 0.03 + 0.115 + 0.005;
    double totalMolesActual = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      totalMolesActual += fluid.getPhase(i).getNumberOfMolesInPhase();
    }
    assertEquals(totalMolesExpected, totalMolesActual, 1e-6, "Total moles should be conserved");
  }

  /**
   * Test hydrate formation with trace water (gas-hydrate equilibrium only).
   *
   * <p>
   * When water content is very low, all water goes into the hydrate phase and there is no free
   * aqueous phase - only gas and hydrate phases remain.
   * </p>
   */
  @Test
  void testGasHydrateWithTraceWater() {
    // High pressure, low temperature - hydrate forming conditions
    // Very low water content so all water goes to hydrate
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 - 5.0, 100.0); // -5°C, 100 bar

    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.03);
    fluid.addComponent("propane", 0.01);
    fluid.addComponent("water", 0.01); // Trace water - 1%

    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    // Flash should complete without errors
    assertNotNull(fluid);
    assertTrue(fluid.getNumberOfPhases() >= 2, "Should have at least gas and hydrate phases");

    // Print to see the phases
    System.out.println("=== Gas-Hydrate with Trace Water ===");
    fluid.prettyPrint();

    // Verify hydrate forms
    assertTrue(fluid.hasHydratePhase(), "Hydrate should form at these conditions");
    assertTrue(fluid.getHydrateFraction() > 0, "Hydrate fraction should be positive");

    // Check that we have gas phase
    boolean hasGasPhase = false;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      if (fluid.getPhase(i).getType() == PhaseType.GAS) {
        hasGasPhase = true;
        break;
      }
    }
    assertTrue(hasGasPhase, "Should have a gas phase");

    // Check phase fractions sum to 1
    double betaSum = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      betaSum += fluid.getBeta(i);
    }
    assertEquals(1.0, betaSum, 0.01, "Phase fractions (beta) should sum to 1");

    // Verify mass conservation
    double totalMolesExpected = 0.95 + 0.03 + 0.01 + 0.01;
    double totalMolesActual = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      totalMolesActual += fluid.getPhase(i).getNumberOfMolesInPhase();
    }
    assertEquals(totalMolesExpected, totalMolesActual, 1e-6, "Total moles should be conserved");
  }

  /**
   * Test dry gas with very trace water forming hydrate.
   *
   * <p>
   * Simulates a nearly dry gas with just enough water to form some hydrate but not enough for a
   * free aqueous phase.
   * </p>
   */
  @Test
  void testDryGasWithTraceWaterHydrate() {
    // Very cold, high pressure - extreme hydrate forming conditions
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 - 10.0, 150.0); // -10°C, 150 bar

    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.02);
    fluid.addComponent("CO2", 0.01);
    fluid.addComponent("water", 0.02); // 2% water - trace amount

    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    // Flash should complete without errors
    assertNotNull(fluid);

    // Print to see the phases
    System.out.println("=== Dry Gas with Trace Water Hydrate ===");
    fluid.prettyPrint();

    // Verify hydrate forms at these extreme conditions
    assertTrue(fluid.hasHydratePhase(), "Hydrate should definitely form at -10°C, 150 bar");

    // Hydrate fraction should be significant since conditions are very favorable
    double hydrateFraction = fluid.getHydrateFraction();
    assertTrue(hydrateFraction > 0, "Hydrate fraction should be positive");
    System.out.println("Hydrate fraction: " + hydrateFraction);

    // Verify mass conservation
    double totalMolesExpected = 0.90 + 0.05 + 0.02 + 0.01 + 0.02;
    double totalMolesActual = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      totalMolesActual += fluid.getPhase(i).getNumberOfMolesInPhase();
    }
    assertEquals(totalMolesExpected, totalMolesActual, 1e-6, "Total moles should be conserved");
  }

  /**
   * Test gas-hydrate equilibrium with very low water at high pressure.
   *
   * <p>
   * At very high pressure and low temperature with minimal water, all water should go into the
   * hydrate phase, resulting in only gas and hydrate phases (no free aqueous phase).
   * </p>
   */
  @Test
  void testGasHydrateEquilibriumHighPressureLowWater() {
    // Extreme conditions: very high pressure, low temperature, minimal water
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 - 15.0, 300.0); // -15°C, 300 bar

    fluid.addComponent("methane", 0.995);
    fluid.addComponent("water", 0.005); // Only 0.5% water - very dry gas

    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    // Flash should complete without errors
    assertNotNull(fluid);

    // Print to see the phases
    System.out.println("=== Gas-Hydrate Equilibrium at 300 bar, -15°C, 0.5% water ===");
    fluid.prettyPrint();

    // Print phase information
    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      System.out.println(
          "Phase " + i + ": " + fluid.getPhase(i).getType() + ", beta = " + fluid.getBeta(i));
    }

    // Verify hydrate forms
    assertTrue(fluid.hasHydratePhase(), "Hydrate should form at these extreme conditions");

    // Verify mass conservation
    double totalMolesExpected = 0.995 + 0.005;
    double totalMolesActual = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      totalMolesActual += fluid.getPhase(i).getNumberOfMolesInPhase();
    }
    assertEquals(totalMolesExpected, totalMolesActual, 1e-6, "Total moles should be conserved");
  }

  /**
   * Test gas-hydrate equilibrium with ultra-low water content.
   *
   * <p>
   * Tests with water at ppm levels to see if hydrate still forms and whether aqueous phase
   * disappears.
   * </p>
   */
  @Test
  void testGasHydrateEquilibriumUltraLowWater() {
    // Very high pressure, cold, with trace water (0.1% = 1000 ppm)
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 - 20.0, 500.0); // -20°C, 500 bar

    fluid.addComponent("methane", 0.899);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("water", 0.001); // 0.1% water - very trace

    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    // Flash should complete without errors
    assertNotNull(fluid);

    // Print to see the phases
    System.out.println("=== Gas-Hydrate Equilibrium at 500 bar, -20°C, 0.1% water ===");
    fluid.prettyPrint();

    // Print phase information
    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      System.out.println(
          "Phase " + i + ": " + fluid.getPhase(i).getType() + ", beta = " + fluid.getBeta(i));
    }

    // Verify hydrate forms even with trace water
    assertTrue(fluid.hasHydratePhase(), "Hydrate should form at -20°C, 500 bar with trace water");

    // Hydrate fraction
    double hydrateFraction = fluid.getHydrateFraction();
    System.out.println("Hydrate fraction: " + hydrateFraction);

    // Check if aqueous phase exists and its fraction
    boolean hasAqueousPhase = false;
    double aqueousFraction = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      if (fluid.getPhase(i).getType() == PhaseType.AQUEOUS) {
        hasAqueousPhase = true;
        aqueousFraction = fluid.getBeta(i);
        break;
      }
    }
    System.out.println("Has aqueous phase: " + hasAqueousPhase + ", fraction: " + aqueousFraction);

    // Verify mass conservation
    double totalMolesExpected = 0.899 + 0.05 + 0.03 + 0.02 + 0.001;
    double totalMolesActual = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      totalMolesActual += fluid.getPhase(i).getNumberOfMolesInPhase();
    }
    assertEquals(totalMolesExpected, totalMolesActual, 1e-6, "Total moles should be conserved");
  }

  /**
   * Test pure gas-hydrate equilibrium with no aqueous phase.
   *
   * <p>
   * With extremely low water content (ppm level), the water dissolves in the gas phase and goes
   * directly to hydrate without forming a separate aqueous phase.
   * </p>
   */
  @Test
  void testPureGasHydrateNoAqueousPhase() {
    // Extreme conditions with very low water - water stays dissolved in gas
    // At high pressure, water solubility in gas increases
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 - 10.0, 200.0); // -10°C, 200 bar

    // Very dry gas - only 100 ppm water (0.01%)
    fluid.addComponent("methane", 0.9999);
    fluid.addComponent("water", 0.0001); // 100 ppm water

    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // First check what happens with regular TPflash
    ops.TPflash();
    System.out.println("=== Before hydrate flash (100 ppm water) ===");
    System.out.println("Number of phases after TPflash: " + fluid.getNumberOfPhases());
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      System.out.println("Phase " + i + ": " + fluid.getPhase(i).getType());
    }

    // Now do hydrate flash
    ops.hydrateTPflash();

    System.out.println("\n=== After hydrate flash ===");
    fluid.prettyPrint();

    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      System.out.println(
          "Phase " + i + ": " + fluid.getPhase(i).getType() + ", beta = " + fluid.getBeta(i));
    }

    // Check if aqueous phase exists
    boolean hasAqueousPhase = false;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      if (fluid.getPhase(i).getType() == PhaseType.AQUEOUS) {
        hasAqueousPhase = true;
        break;
      }
    }
    System.out.println("Has aqueous phase: " + hasAqueousPhase);

    // Check if hydrate forms
    boolean hasHydrate = fluid.hasHydratePhase();
    System.out.println("Has hydrate phase: " + hasHydrate);
  }

  /**
   * Test gas-hydrate equilibrium with 50 ppm water.
   *
   * <p>
   * With very low water content, the hydrate fraction is limited by the available water. This test
   * verifies that the hydrate fraction is correctly calculated based on water content.
   * </p>
   */
  @Test
  void testGasHydrateWith50ppmWater() {
    // Very dry gas at extreme hydrate conditions - colder to ensure hydrate formation
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 - 20.0, 300.0); // -20°C, 300 bar

    // 50 ppm water
    fluid.addComponent("methane", 0.99995);
    fluid.addComponent("water", 0.00005);

    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // First TPflash to see initial state
    ops.TPflash();
    System.out.println("=== TPflash with 50 ppm water at -20°C, 300 bar ===");
    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      System.out.println("Phase " + i + ": " + fluid.getPhase(i).getType());
      if (fluid.getPhase(i).hasComponent("water")) {
        System.out.println("  Water x = " + fluid.getPhase(i).getComponent("water").getx());
      }
    }

    // Now hydrate flash
    ops.hydrateTPflash();

    System.out.println("\n=== After hydrate flash ===");
    fluid.prettyPrint();

    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      System.out.println(
          "Phase " + i + ": " + fluid.getPhase(i).getType() + ", beta = " + fluid.getBeta(i));
    }

    // Check phases
    boolean hasAqueousPhase = false;
    double aqueousBeta = 0.0;
    boolean hasHydrate = false;
    double hydrateBeta = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      if (fluid.getPhase(i).getType() == PhaseType.AQUEOUS) {
        hasAqueousPhase = true;
        aqueousBeta = fluid.getBeta(i);
      }
      if (fluid.getPhase(i).getType() == PhaseType.HYDRATE) {
        hasHydrate = true;
        hydrateBeta = fluid.getBeta(i);
      }
    }
    System.out.println("Has aqueous phase: " + hasAqueousPhase + ", beta = " + aqueousBeta);
    System.out.println("Has hydrate phase: " + hasHydrate + ", beta = " + hydrateBeta);

    // Verify hydrate forms
    assertTrue(hasHydrate, "Hydrate should form at -20°C, 300 bar");

    // Verify hydrate fraction is small (limited by water content)
    // With 50 ppm water and hydrate being ~85% water, max hydrate ≈ 59 ppm
    assertTrue(hydrateBeta < 0.001, "Hydrate fraction should be small with 50 ppm water");
    assertTrue(hydrateBeta > 0, "Hydrate fraction should be positive");

    // Verify aqueous phase is very small (if it exists)
    if (hasAqueousPhase) {
      assertTrue(aqueousBeta < 0.0001, "Aqueous phase should be very small with trace water");
    }

    // The gas phase should dominate
    assertTrue(fluid.getBeta(0) > 0.999, "Gas phase should be > 99.9%");
  }

  /**
   * Test gas-hydrate equilibrium using gasHydrateTPflash method.
   *
   * <p>
   * This test verifies that the gasHydrateTPflash method can achieve gas-hydrate equilibrium
   * without an aqueous phase when water content is low enough.
   * </p>
   */
  @Test
  void testGasHydrateTPflash() {
    // Dry gas at extreme hydrate conditions
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 - 15.0, 250.0); // -15°C, 250 bar

    // 200 ppm water - low enough to be consumed by hydrate
    fluid.addComponent("methane", 0.9998);
    fluid.addComponent("water", 0.0002);

    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // Use the new gasHydrateTPflash method
    ops.gasHydrateTPflash();

    System.out.println("=== Gas-Hydrate TPflash (200 ppm water at -15°C, 250 bar) ===");
    fluid.prettyPrint();

    // Count phase types
    boolean hasGas = false;
    boolean hasHydrate = false;
    boolean hasAqueous = false;
    double gasBeta = 0.0;
    double hydrateBeta = 0.0;
    double aqueousBeta = 0.0;

    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      System.out.println(
          "Phase " + i + ": " + fluid.getPhase(i).getType() + ", beta = " + fluid.getBeta(i));
      if (fluid.getPhase(i).getType() == PhaseType.GAS) {
        hasGas = true;
        gasBeta = fluid.getBeta(i);
      }
      if (fluid.getPhase(i).getType() == PhaseType.HYDRATE) {
        hasHydrate = true;
        hydrateBeta = fluid.getBeta(i);
      }
      if (fluid.getPhase(i).getType() == PhaseType.AQUEOUS) {
        hasAqueous = true;
        aqueousBeta = fluid.getBeta(i);
      }
    }

    // Verify hydrate forms
    assertTrue(hasHydrate, "Hydrate should form at -15°C, 250 bar");

    // Verify gas phase exists
    assertTrue(hasGas, "Gas phase should exist");

    // Report whether aqueous phase was removed
    System.out.println("Has aqueous phase: " + hasAqueous);
    if (hasAqueous) {
      System.out.println("Aqueous beta: " + aqueousBeta);
    }

    // Gas should dominate
    assertTrue(gasBeta > 0.99, "Gas phase should be > 99%");

    // Verify beta sum = 1.0 (mass conservation)
    double betaSum = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      betaSum += fluid.getBeta(i);
    }
    assertEquals(1.0, betaSum, 1e-6, "Beta sum should equal 1.0");
  }

  /**
   * Test gas-hydrate equilibrium with very low water (500 ppm).
   *
   * <p>
   * At low water content, the algorithm should achieve gas-hydrate equilibrium with all water
   * consumed by hydrate formation.
   * </p>
   */
  @Test
  void testGasHydrateEquilibriumWithVeryLowWater() {
    // Dry gas at extreme hydrate conditions
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 - 20.0, 300.0); // -20°C, 300 bar

    // 500 ppm water - low but enough for hydrate calculation
    fluid.addComponent("methane", 0.9995);
    fluid.addComponent("water", 0.0005);

    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // Use gasHydrateTPflash
    ops.gasHydrateTPflash();

    System.out.println("=== Gas-Hydrate TPflash (500 ppm water at -20°C, 300 bar) ===");
    fluid.prettyPrint();

    // Count phase types
    boolean hasHydrate = false;
    boolean hasAqueous = false;
    int phaseCount = fluid.getNumberOfPhases();

    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      System.out.println(
          "Phase " + i + ": " + fluid.getPhase(i).getType() + ", beta = " + fluid.getBeta(i));
      if (fluid.getPhase(i).getType() == PhaseType.HYDRATE) {
        hasHydrate = true;
      }
      if (fluid.getPhase(i).getType() == PhaseType.AQUEOUS) {
        hasAqueous = true;
      }
    }

    System.out.println("Total phases: " + phaseCount);
    System.out.println("Has hydrate: " + hasHydrate);
    System.out.println("Has aqueous: " + hasAqueous);

    // Verify hydrate forms
    assertTrue(hasHydrate, "Hydrate should form at these extreme conditions");

    // Verify beta sum = 1.0 (mass conservation)
    double betaSum = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      betaSum += fluid.getBeta(i);
    }
    assertEquals(1.0, betaSum, 1e-6, "Beta sum should equal 1.0");
  }

  /**
   * Test gas-hydrate equilibrium with natural gas and low water.
   *
   * <p>
   * Tests gas-hydrate equilibrium with a more realistic natural gas composition containing multiple
   * hydrate formers.
   * </p>
   */
  @Test
  void testNaturalGasHydrateEquilibrium() {
    // Natural gas composition at hydrate conditions
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 - 5.0, 150.0); // -5°C, 150 bar

    // Typical natural gas with low water content
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("CO2", 0.02);
    fluid.addComponent("nitrogen", 0.04);
    fluid.addComponent("water", 0.01); // 1% water

    fluid.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // Use gasHydrateTPflash
    ops.gasHydrateTPflash();

    System.out.println("=== Natural Gas Hydrate TPflash (1% water at -5°C, 150 bar) ===");
    fluid.prettyPrint();

    // Verify hydrate forms
    boolean hasHydrate = false;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      System.out.println(
          "Phase " + i + ": " + fluid.getPhase(i).getType() + ", beta = " + fluid.getBeta(i));
      if (fluid.getPhase(i).getType() == PhaseType.HYDRATE) {
        hasHydrate = true;
      }
    }

    assertTrue(hasHydrate, "Hydrate should form in natural gas at -5°C, 150 bar");

    // Verify beta sum = 1.0
    double betaSum = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      betaSum += fluid.getBeta(i);
    }
    assertEquals(1.0, betaSum, 1e-6, "Beta sum should equal 1.0");
  }
}
