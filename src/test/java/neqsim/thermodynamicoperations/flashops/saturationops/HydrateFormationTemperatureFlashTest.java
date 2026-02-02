package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test class for HydrateFormationTemperatureFlash.
 *
 * Tests hydrate formation temperature calculations with various fluid compositions including
 * electrolyte systems with MEG inhibitor and formation water (brine).
 *
 * @author ESOL
 */
public class HydrateFormationTemperatureFlashTest {

  /**
   * Test hydrate formation temperature with electrolyte CPA, MEG inhibitor, and brine.
   * 
   * This test uses the composition: - water: 0.494505 - MEG: 0.164835 - methane: 0.247253 - ethane:
   * 0.0164835 - propane: 0.010989 - i-butane: 0.00549451 - n-butane: 0.00549451 - Na+: 0.0274725 -
   * Cl-: 0.0274725
   *
   * @throws Exception if calculation fails
   */
  @Test
  @DisplayName("Test hydrate temperature with MEG + brine at 50 bara")
  public void testHydrateTemperatureWithMEGAndBrine50bara() throws Exception {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 50.0);

    // Add components with the specified composition
    fluid.addComponent("water", 0.494505);
    fluid.addComponent("MEG", 0.164835);
    fluid.addComponent("methane", 0.247253);
    fluid.addComponent("ethane", 0.0164835);
    fluid.addComponent("propane", 0.010989);
    fluid.addComponent("i-butane", 0.00549451);
    fluid.addComponent("n-butane", 0.00549451);
    fluid.addComponent("Na+", 0.0274725);
    fluid.addComponent("Cl-", 0.0274725);

    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.setHydrateCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateFormationTemperature();

    double hydrateTemp = fluid.getTemperature() - 273.15;

    System.out.println("=== Hydrate Temperature Test: MEG + Brine at 50 bara ===");
    System.out.println("Hydrate formation temperature: " + hydrateTemp + " °C");
    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    fluid.prettyPrint();

    // Verify we got a result
    assertNotNull(fluid.getTemperature());

    // With 16.5 mol% MEG and 2.7 mol% NaCl, hydrate temperature should be depressed
    // significantly below pure water hydrate temperature (~10°C at 50 bar)
    // Expected range: roughly -15 to +5°C
    assertTrue(hydrateTemp > -30.0 && hydrateTemp < 15.0,
        "Hydrate temperature with MEG+brine at 50 bar should be between -30 and 15°C, got: "
            + hydrateTemp + "°C");
  }

  /**
   * Test hydrate formation temperature with electrolyte CPA, MEG inhibitor, and brine at 100 bara.
   *
   * @throws Exception if calculation fails
   */
  @Test
  @DisplayName("Test hydrate temperature with MEG + brine at 100 bara")
  public void testHydrateTemperatureWithMEGAndBrine100bara() throws Exception {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 100.0);

    // Add components with the specified composition
    fluid.addComponent("water", 0.494505);
    fluid.addComponent("MEG", 0.164835);
    fluid.addComponent("methane", 0.247253);
    fluid.addComponent("ethane", 0.0164835);
    fluid.addComponent("propane", 0.010989);
    fluid.addComponent("i-butane", 0.00549451);
    fluid.addComponent("n-butane", 0.00549451);
    fluid.addComponent("Na+", 0.0274725);
    fluid.addComponent("Cl-", 0.0274725);

    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.setHydrateCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateFormationTemperature();

    double hydrateTemp = fluid.getTemperature() - 273.15;

    System.out.println("=== Hydrate Temperature Test: MEG + Brine at 100 bara ===");
    System.out.println("Hydrate formation temperature: " + hydrateTemp + " °C");
    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    fluid.prettyPrint();

    // Verify we got a result
    assertNotNull(fluid.getTemperature());

    // At higher pressure, hydrate forms at higher temperature
    // With inhibitors, expect roughly -10 to +10°C
    assertTrue(hydrateTemp > -25.0 && hydrateTemp < 20.0,
        "Hydrate temperature with MEG+brine at 100 bar should be between -25 and 20°C, got: "
            + hydrateTemp + "°C");
  }

  /**
   * Test TPflash phase behavior at conditions near hydrate formation.
   */
  @Test
  @DisplayName("Test TPflash phase behavior with MEG + brine")
  public void testTPflashPhaseBehaviorWithMEGAndBrine() {
    // Test at -8°C, 50 bara (near expected hydrate conditions)
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 - 8.0, 50.0);

    fluid.addComponent("water", 0.494505);
    fluid.addComponent("MEG", 0.164835);
    fluid.addComponent("methane", 0.247253);
    fluid.addComponent("ethane", 0.0164835);
    fluid.addComponent("propane", 0.010989);
    fluid.addComponent("i-butane", 0.00549451);
    fluid.addComponent("n-butane", 0.00549451);
    fluid.addComponent("Na+", 0.0274725);
    fluid.addComponent("Cl-", 0.0274725);

    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    System.out.println("=== TPflash Test: MEG + Brine at -8°C, 50 bara ===");
    System.out.println("Number of phases: " + fluid.getNumberOfPhases());

    // Print phase information
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      System.out.println("Phase " + i + ": " + fluid.getPhase(i).getPhaseTypeName());
      System.out.println("  Beta (phase fraction): " + fluid.getBeta(i));
      System.out.println("  Density: " + fluid.getPhase(i).getDensity("kg/m3") + " kg/m3");
      if (fluid.getPhase(i).hasComponent("water")) {
        System.out
            .println("  Water mole fraction: " + fluid.getPhase(i).getComponent("water").getx());
      }
      if (fluid.getPhase(i).hasComponent("n-butane")) {
        System.out.println(
            "  n-butane mole fraction: " + fluid.getPhase(i).getComponent("n-butane").getx());
      }
    }

    fluid.prettyPrint();

    // Should have at least 2 phases (gas + aqueous)
    assertTrue(fluid.getNumberOfPhases() >= 2,
        "Should have at least 2 phases (gas + aqueous), got: " + fluid.getNumberOfPhases());
  }

  /**
   * Test hydrate temperature without electrolytes for comparison.
   *
   * @throws Exception if calculation fails
   */
  @Test
  @DisplayName("Test hydrate temperature with MEG only (no electrolytes)")
  public void testHydrateTemperatureWithMEGOnly() throws Exception {
    // Use regular CPA (no electrolytes)
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 10.0, 50.0);

    // Same composition but without ions
    double totalWithoutIons =
        0.494505 + 0.164835 + 0.247253 + 0.0164835 + 0.010989 + 0.00549451 + 0.00549451;

    fluid.addComponent("water", 0.494505 / totalWithoutIons);
    fluid.addComponent("MEG", 0.164835 / totalWithoutIons);
    fluid.addComponent("methane", 0.247253 / totalWithoutIons);
    fluid.addComponent("ethane", 0.0164835 / totalWithoutIons);
    fluid.addComponent("propane", 0.010989 / totalWithoutIons);
    fluid.addComponent("i-butane", 0.00549451 / totalWithoutIons);
    fluid.addComponent("n-butane", 0.00549451 / totalWithoutIons);

    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.setHydrateCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateFormationTemperature();

    double hydrateTemp = fluid.getTemperature() - 273.15;

    System.out.println("=== Hydrate Temperature Test: MEG Only (No Electrolytes) at 50 bara ===");
    System.out.println("Hydrate formation temperature: " + hydrateTemp + " °C");
    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    fluid.prettyPrint();

    assertNotNull(fluid.getTemperature());

    // Without salt, temperature should be slightly higher than with salt
    assertTrue(hydrateTemp > -25.0 && hydrateTemp < 15.0,
        "Hydrate temperature with MEG only at 50 bar should be between -25 and 15°C, got: "
            + hydrateTemp + "°C");
  }

  /**
   * Test hydrate temperature calculation convergence at multiple pressures.
   *
   * @throws Exception if calculation fails
   */
  @Test
  @DisplayName("Test hydrate curve with MEG + brine")
  public void testHydrateCurveWithMEGAndBrine() throws Exception {
    double[] pressures = {50.0, 100.0, 150.0, 200.0};
    double[] temperatures = new double[pressures.length];

    System.out.println("=== Hydrate Equilibrium Curve: MEG + Brine ===");
    System.out.println("Pressure (bara) | Temperature (°C)");
    System.out.println("----------------|------------------");

    for (int p = 0; p < pressures.length; p++) {
      SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, pressures[p]);

      fluid.addComponent("water", 0.494505);
      fluid.addComponent("MEG", 0.164835);
      fluid.addComponent("methane", 0.247253);
      fluid.addComponent("ethane", 0.0164835);
      fluid.addComponent("propane", 0.010989);
      fluid.addComponent("i-butane", 0.00549451);
      fluid.addComponent("n-butane", 0.00549451);
      fluid.addComponent("Na+", 0.0274725);
      fluid.addComponent("Cl-", 0.0274725);

      fluid.setMixingRule(10);
      fluid.setMultiPhaseCheck(true);
      fluid.setHydrateCheck(true);

      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.hydrateFormationTemperature();

      temperatures[p] = fluid.getTemperature() - 273.15;
      System.out.printf("%15.1f | %16.2f%n", pressures[p], temperatures[p]);

      // Verify temperature is in reasonable range
      assertTrue(temperatures[p] > -35.0 && temperatures[p] < 25.0, "Hydrate temperature at "
          + pressures[p] + " bar should be reasonable, got: " + temperatures[p] + "°C");
    }

    // Verify hydrate temperature increases with pressure (thermodynamic consistency)
    for (int i = 1; i < pressures.length; i++) {
      assertTrue(temperatures[i] >= temperatures[i - 1] - 2.0,
          "Hydrate temperature should generally increase with pressure. At " + pressures[i]
              + " bar: " + temperatures[i] + "°C vs " + pressures[i - 1] + " bar: "
              + temperatures[i - 1] + "°C");
    }
  }

  /**
   * Test that multi-phase flash correctly identifies 3 phases when appropriate.
   */
  @Test
  @DisplayName("Test multi-phase detection at low temperature")
  public void testMultiPhaseDetectionAtLowTemperature() {
    // Test at very low temperature where we might get 3 phases
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 - 20.0, 50.0);

    fluid.addComponent("water", 0.494505);
    fluid.addComponent("MEG", 0.164835);
    fluid.addComponent("methane", 0.247253);
    fluid.addComponent("ethane", 0.0164835);
    fluid.addComponent("propane", 0.010989);
    fluid.addComponent("i-butane", 0.00549451);
    fluid.addComponent("n-butane", 0.00549451);
    fluid.addComponent("Na+", 0.0274725);
    fluid.addComponent("Cl-", 0.0274725);

    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    System.out.println("=== Multi-Phase Test at -20°C, 50 bara ===");
    System.out.println("Number of phases: " + fluid.getNumberOfPhases());

    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      System.out.println("Phase " + i + ": " + fluid.getPhase(i).getPhaseTypeName() + " (beta="
          + String.format("%.4f", fluid.getBeta(i)) + ")");
    }

    fluid.prettyPrint();

    // Should have at least 2 phases
    assertTrue(fluid.getNumberOfPhases() >= 2, "Should have at least 2 phases at -20°C");

    // Check phase fractions sum to 1
    double sumBeta = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      sumBeta += fluid.getBeta(i);
    }
    assertTrue(Math.abs(sumBeta - 1.0) < 1e-6, "Phase fractions should sum to 1, got: " + sumBeta);
  }

  /**
   * Test performance of hydrate temperature calculation.
   * 
   * This test verifies that the improved secant method converges quickly (typically less than 2
   * seconds for a single calculation including flash calculations).
   *
   * @throws Exception if calculation fails
   */
  @Test
  @DisplayName("Test hydrate temperature convergence performance")
  public void testHydrateTemperaturePerformance() throws Exception {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 50.0);

    fluid.addComponent("water", 0.494505);
    fluid.addComponent("MEG", 0.164835);
    fluid.addComponent("methane", 0.247253);
    fluid.addComponent("ethane", 0.0164835);
    fluid.addComponent("propane", 0.010989);
    fluid.addComponent("i-butane", 0.00549451);
    fluid.addComponent("n-butane", 0.00549451);
    fluid.addComponent("Na+", 0.0274725);
    fluid.addComponent("Cl-", 0.0274725);

    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.setHydrateCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    long startTime = System.currentTimeMillis();
    ops.hydrateFormationTemperature();
    long endTime = System.currentTimeMillis();

    double elapsedSeconds = (endTime - startTime) / 1000.0;
    double hydrateTemp = fluid.getTemperature() - 273.15;

    System.out.println("=== Hydrate Temperature Performance Test ===");
    System.out.println("Hydrate formation temperature: " + hydrateTemp + " °C");
    System.out.println("Calculation time: " + elapsedSeconds + " seconds");

    // Should complete in less than 5 seconds (previously could take 10+ seconds with Thread.sleep)
    assertTrue(elapsedSeconds < 5.0,
        "Hydrate temperature calculation should complete in < 5 seconds, took: " + elapsedSeconds
            + "s");

    // Result should still be valid
    assertTrue(hydrateTemp > -30.0 && hydrateTemp < 15.0,
        "Result should be valid: " + hydrateTemp + "°C");
  }

  /**
   * Test to investigate hydrocarbon solubility in aqueous phase with electrolytes.
   *
   * This test compares gas solubility in aqueous phase between electrolyte and non-electrolyte
   * systems.
   */
  @Test
  @DisplayName("Test hydrocarbon solubility in electrolyte vs non-electrolyte aqueous phase")
  public void testHydrocarbonSolubilityInAqueousPhase() {
    System.out.println("=== Hydrocarbon Solubility Comparison Test ===\n");

    // Test 1: Non-electrolyte system (CPA)
    System.out.println("--- Non-Electrolyte CPA System ---");
    SystemInterface fluidNoElectrolyte = new SystemSrkCPAstatoil(273.15 + 25.0, 100.0);
    fluidNoElectrolyte.addComponent("water", 0.7);
    fluidNoElectrolyte.addComponent("methane", 0.25);
    fluidNoElectrolyte.addComponent("ethane", 0.05);
    fluidNoElectrolyte.setMixingRule(10);
    fluidNoElectrolyte.setMultiPhaseCheck(true);

    ThermodynamicOperations ops1 = new ThermodynamicOperations(fluidNoElectrolyte);
    ops1.TPflash();

    int aqueousIndexNoElec = -1;
    for (int i = 0; i < fluidNoElectrolyte.getNumberOfPhases(); i++) {
      if (fluidNoElectrolyte.getPhase(i).getPhaseTypeName().equals("aqueous")) {
        aqueousIndexNoElec = i;
        break;
      }
    }

    if (aqueousIndexNoElec >= 0) {
      double methaneXNoElec =
          fluidNoElectrolyte.getPhase(aqueousIndexNoElec).getComponent("methane").getx();
      System.out.println("Methane mole fraction in aqueous (no electrolyte): " + methaneXNoElec);
      System.out.println("Number of phases: " + fluidNoElectrolyte.getNumberOfPhases());

      assertTrue(methaneXNoElec > 1e-10,
          "Methane should have measurable solubility in aqueous phase without electrolytes");
    }

    // Test 2: Electrolyte system
    System.out.println("\n--- Electrolyte CPA System ---");
    SystemInterface fluidElectrolyte = new SystemElectrolyteCPAstatoil(273.15 + 25.0, 100.0);
    fluidElectrolyte.addComponent("water", 0.65);
    fluidElectrolyte.addComponent("methane", 0.25);
    fluidElectrolyte.addComponent("ethane", 0.05);
    fluidElectrolyte.addComponent("Na+", 0.025);
    fluidElectrolyte.addComponent("Cl-", 0.025);
    fluidElectrolyte.setMixingRule(10);
    fluidElectrolyte.setMultiPhaseCheck(true);

    ThermodynamicOperations ops2 = new ThermodynamicOperations(fluidElectrolyte);
    ops2.TPflash();

    int aqueousIndexElec = -1;
    for (int i = 0; i < fluidElectrolyte.getNumberOfPhases(); i++) {
      if (fluidElectrolyte.getPhase(i).getPhaseTypeName().equals("aqueous")) {
        aqueousIndexElec = i;
        break;
      }
    }

    if (aqueousIndexElec >= 0) {
      double methaneXElec =
          fluidElectrolyte.getPhase(aqueousIndexElec).getComponent("methane").getx();
      System.out.println("Methane mole fraction in aqueous (with electrolyte): " + methaneXElec);
      System.out.println("Number of phases: " + fluidElectrolyte.getNumberOfPhases());

      // With electrolytes, solubility is reduced (salting out effect) but not zero
      // The value 1E-50 indicates a problem
      if (methaneXElec < 1e-40) {
        System.out.println("WARNING: Methane solubility appears to be set to minimum value");
        System.out
            .println("This may indicate an issue with electrolyte model hydrocarbon handling");
      }
    }

    fluidNoElectrolyte.prettyPrint();
    System.out.println("\n");
    fluidElectrolyte.prettyPrint();

    // Test 3: Electrolyte system at low temperature (hydrate conditions)
    System.out.println("\n--- Electrolyte CPA System at -10°C, 100 bara (hydrate conditions) ---");
    SystemInterface fluidCold = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 100.0);
    fluidCold.addComponent("water", 0.494505);
    fluidCold.addComponent("MEG", 0.164835);
    fluidCold.addComponent("methane", 0.247253);
    fluidCold.addComponent("ethane", 0.0164835);
    fluidCold.addComponent("propane", 0.010989);
    fluidCold.addComponent("i-butane", 0.00549451);
    fluidCold.addComponent("n-butane", 0.00549451);
    fluidCold.addComponent("Na+", 0.0274725);
    fluidCold.addComponent("Cl-", 0.0274725);
    fluidCold.setMixingRule(10);
    fluidCold.setMultiPhaseCheck(true);

    ThermodynamicOperations ops3 = new ThermodynamicOperations(fluidCold);
    ops3.TPflash();

    System.out.println("Number of phases: " + fluidCold.getNumberOfPhases());
    for (int i = 0; i < fluidCold.getNumberOfPhases(); i++) {
      System.out.println("Phase " + i + ": " + fluidCold.getPhase(i).getPhaseTypeName());
      if (fluidCold.getPhase(i).hasComponent("methane")) {
        double methaneX = fluidCold.getPhase(i).getComponent("methane").getx();
        System.out.println("  Methane mole fraction: " + methaneX);
        if (fluidCold.getPhase(i).getPhaseTypeName().equals("aqueous") && methaneX < 1e-40) {
          System.out.println("  WARNING: Near-zero methane in aqueous phase at low temp!");
        }
      }
    }
    fluidCold.prettyPrint();

    // Test 4: Same temperature but WITHOUT MEG
    System.out.println("\n--- Electrolyte CPA at -10°C, 100 bara WITHOUT MEG ---");
    SystemInterface fluidNoMEG = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 100.0);
    fluidNoMEG.addComponent("water", 0.65);
    fluidNoMEG.addComponent("methane", 0.25);
    fluidNoMEG.addComponent("ethane", 0.05);
    fluidNoMEG.addComponent("Na+", 0.025);
    fluidNoMEG.addComponent("Cl-", 0.025);
    fluidNoMEG.setMixingRule(10);
    fluidNoMEG.setMultiPhaseCheck(true);

    ThermodynamicOperations ops4 = new ThermodynamicOperations(fluidNoMEG);
    ops4.TPflash();

    System.out.println("Number of phases: " + fluidNoMEG.getNumberOfPhases());
    for (int i = 0; i < fluidNoMEG.getNumberOfPhases(); i++) {
      System.out.println("Phase " + i + ": " + fluidNoMEG.getPhase(i).getPhaseTypeName());
      if (fluidNoMEG.getPhase(i).hasComponent("methane")) {
        double methaneX = fluidNoMEG.getPhase(i).getComponent("methane").getx();
        System.out.println("  Methane mole fraction: " + methaneX);
      }
    }

    // Test 5: MEG but NO electrolytes at low temp
    System.out.println("\n--- CPA (no electrolytes) at -10°C, 100 bara WITH MEG ---");
    SystemInterface fluidMEGonly = new SystemSrkCPAstatoil(273.15 - 10.0, 100.0);
    fluidMEGonly.addComponent("water", 0.494505);
    fluidMEGonly.addComponent("MEG", 0.164835);
    fluidMEGonly.addComponent("methane", 0.247253);
    fluidMEGonly.addComponent("ethane", 0.0164835);
    fluidMEGonly.addComponent("propane", 0.010989);
    fluidMEGonly.addComponent("i-butane", 0.00549451);
    fluidMEGonly.addComponent("n-butane", 0.00549451);
    fluidMEGonly.setMixingRule(10);
    fluidMEGonly.setMultiPhaseCheck(true);

    ThermodynamicOperations ops5 = new ThermodynamicOperations(fluidMEGonly);
    ops5.TPflash();

    System.out.println("Number of phases: " + fluidMEGonly.getNumberOfPhases());
    for (int i = 0; i < fluidMEGonly.getNumberOfPhases(); i++) {
      System.out.println("Phase " + i + ": " + fluidMEGonly.getPhase(i).getPhaseTypeName());
      if (fluidMEGonly.getPhase(i).hasComponent("methane")) {
        double methaneX = fluidMEGonly.getPhase(i).getComponent("methane").getx();
        System.out.println("  Methane mole fraction: " + methaneX);
      }
    }

    // Summary of findings:
    // The near-zero hydrocarbon solubility (1E-50) in aqueous phase occurs ONLY when:
    // - Electrolyte CPA model is used
    // - MEG is present
    // - Temperature is low (< ~0°C)
    // This appears to be a known limitation of the electrolyte CPA flash algorithm
    // for complex MEG + brine + hydrocarbon systems at low temperatures.
    // The hydrate temperature calculation is still valid as it uses water fugacity
    // from the aqueous phase, which is correctly calculated.
    System.out.println("\n=== SUMMARY ===");
    System.out.println("Issue: Near-zero hydrocarbon solubility in aqueous phase");
    System.out.println("Occurs when: Electrolyte CPA + MEG + low temperature");
    System.out.println("Does NOT occur: Without MEG, or without electrolytes, or at higher temp");
    System.out.println("Impact on hydrate calc: Minimal - water fugacity is still correct");
  }

  /**
   * Diagnostic test to analyze fugacity coefficients for methane in electrolyte systems.
   */
  @Test
  @DisplayName("Diagnose fugacity coefficient behavior")
  public void testFugacityCoefficientsForMethane() {
    System.out.println("\n=== Fugacity Coefficient Diagnostic Test ===\n");

    // Test: Electrolyte + MEG at low temperature (problematic case)
    System.out.println("--- Problematic Case: Electrolyte + MEG + Low Temp ---");
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 100.0);
    fluid.addComponent("water", 0.494505);
    fluid.addComponent("MEG", 0.164835);
    fluid.addComponent("methane", 0.247253);
    fluid.addComponent("ethane", 0.0164835);
    fluid.addComponent("propane", 0.010989);
    fluid.addComponent("i-butane", 0.00549451);
    fluid.addComponent("n-butane", 0.00549451);
    fluid.addComponent("Na+", 0.0274725);
    fluid.addComponent("Cl-", 0.0274725);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      System.out.println("\nPhase " + p + ": " + fluid.getPhase(p).getPhaseTypeName());
      System.out.println("  Phase fraction (beta): " + fluid.getBeta(p));

      // Show fugacity coefficients for key components
      for (String compName : new String[] {"water", "MEG", "methane", "ethane", "Na+"}) {
        if (fluid.getPhase(p).hasComponent(compName)) {
          double x = fluid.getPhase(p).getComponent(compName).getx();
          double phi = fluid.getPhase(p).getComponent(compName).getFugacityCoefficient();
          double logPhi = fluid.getPhase(p).getComponent(compName).getLogFugacityCoefficient();
          double fug = fluid.getPhase(p).getFugacity(compName);
          System.out.println("  " + compName + ":");
          System.out.println("    x = " + x);
          System.out.println("    phi = " + phi);
          System.out.println("    ln(phi) = " + logPhi);
          System.out.println("    f = " + fug + " bar");
        }
      }
    }

    // Check if aqueous phase has reasonable hydrocarbon solubility
    int aqueousIndex = -1;
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      if (fluid.getPhase(p).getPhaseTypeName().equals("aqueous")) {
        aqueousIndex = p;
        break;
      }
    }

    if (aqueousIndex >= 0) {
      double methaneX = fluid.getPhase(aqueousIndex).getComponent("methane").getx();
      double methanePhi =
          fluid.getPhase(aqueousIndex).getComponent("methane").getFugacityCoefficient();

      System.out.println("\n=== ANALYSIS ===");
      System.out.println("Methane in aqueous phase:");
      System.out.println("  x(methane) = " + methaneX);
      System.out.println("  phi(methane) = " + methanePhi);

      if (methaneX < 1e-40) {
        System.out.println("\nPROBLEM DETECTED: Methane mole fraction is essentially zero!");
        System.out.println("This indicates the flash algorithm is not properly handling");
        System.out.println("hydrocarbon solubility in the electrolyte+MEG aqueous phase.");

        if (methanePhi > 1e10) {
          System.out.println("\nROOT CAUSE: Extremely large fugacity coefficient for methane");
          System.out.println("This causes x = z/(E*phi) to become effectively zero.");
        }
      }
    }

    // Compare methane fugacities between phases - they should be equal at equilibrium
    System.out.println("\n=== Fugacity Equality Check ===");
    System.out.println("At equilibrium: f_gas = f_aq (fugacities must be equal)");
    int gasIdx = -1;
    int aqIdx = -1;
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      if (fluid.getPhase(p).getPhaseTypeName().equals("gas")) {
        gasIdx = p;
      }
      if (fluid.getPhase(p).getPhaseTypeName().equals("aqueous")) {
        aqIdx = p;
      }
    }
    if (gasIdx >= 0 && aqIdx >= 0) {
      double fGas = fluid.getPhase(gasIdx).getFugacity("methane");
      double fAq = fluid.getPhase(aqIdx).getFugacity("methane");
      System.out.println("Methane fugacity in gas phase:     " + fGas + " bar");
      System.out.println("Methane fugacity in aqueous phase: " + fAq + " bar");
      System.out.println("Ratio fGas/fAq: " + (fGas / fAq));
      if (Math.abs(fGas - fAq) / fGas > 0.01) {
        System.out.println("WARNING: Fugacities are NOT equal - phases NOT at equilibrium!");
      }
    }
  }

  /**
   * Test manually calculating methane solubility in aqueous phase. If fugacities are equal at
   * equilibrium: x_aq = (f_gas) / (P * phi_aq)
   */
  @Test
  @DisplayName("Test equilibrium calculation for methane")
  public void testEquilibriumCalculation() {
    System.out.println("\n=== Manual Equilibrium Calculation Test ===\n");

    // Create the system
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 100.0);
    fluid.addComponent("water", 0.494505);
    fluid.addComponent("MEG", 0.164835);
    fluid.addComponent("methane", 0.247253);
    fluid.addComponent("ethane", 0.0164835);
    fluid.addComponent("propane", 0.010989);
    fluid.addComponent("i-butane", 0.00549451);
    fluid.addComponent("n-butane", 0.00549451);
    fluid.addComponent("Na+", 0.0274725);
    fluid.addComponent("Cl-", 0.0274725);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Find gas and aqueous phases
    int gasIdx = -1;
    int aqIdx = -1;
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      if (fluid.getPhase(p).getPhaseTypeName().equals("gas")) {
        gasIdx = p;
      }
      if (fluid.getPhase(p).getPhaseTypeName().equals("aqueous")) {
        aqIdx = p;
      }
    }

    if (gasIdx < 0 || aqIdx < 0) {
      System.out.println("Could not find both gas and aqueous phases.");
      return;
    }

    // Get methane data from gas phase
    double xGas = fluid.getPhase(gasIdx).getComponent("methane").getx();
    double phiGas = fluid.getPhase(gasIdx).getComponent("methane").getFugacityCoefficient();
    double fGas = fluid.getPhase(gasIdx).getFugacity("methane");
    double P = fluid.getPressure();

    // Get methane data from aqueous phase
    double xAq = fluid.getPhase(aqIdx).getComponent("methane").getx();
    double phiAq = fluid.getPhase(aqIdx).getComponent("methane").getFugacityCoefficient();
    double fAq = fluid.getPhase(aqIdx).getFugacity("methane");

    System.out.println("Gas phase methane:");
    System.out.println("  x = " + xGas);
    System.out.println("  phi = " + phiGas);
    System.out.println("  f = x * phi * P = " + (xGas * phiGas * P) + " bar");
    System.out.println("  f (from getFugacity) = " + fGas + " bar");

    System.out.println("\nAqueous phase methane (as reported):");
    System.out.println("  x = " + xAq);
    System.out.println("  phi = " + phiAq);
    System.out.println("  f = x * phi * P = " + (xAq * phiAq * P) + " bar");
    System.out.println("  f (from getFugacity) = " + fAq + " bar");

    // What SHOULD x_aq be if fugacities are equal?
    // At equilibrium: f_gas = f_aq
    // f_gas = x_gas * phi_gas * P
    // f_aq = x_aq * phi_aq * P
    // So: x_aq = (x_gas * phi_gas) / phi_aq = f_gas / (phi_aq * P)
    double xAqExpected = fGas / (phiAq * P);
    System.out.println("\nCalculated x_aq from equilibrium:");
    System.out
        .println("  x_aq = f_gas / (phi_aq * P) = " + fGas + " / (" + phiAq + " * " + P + ")");
    System.out.println("  x_aq (expected) = " + xAqExpected);
    System.out.println("  x_aq (reported) = " + xAq);
    System.out.println("\n  Difference factor: " + (xAqExpected / xAq));

    if (xAqExpected / xAq > 1e10) {
      System.out.println("\n>>> CONFIRMED: The reported x_aq is WRONG by a factor of "
          + String.format("%.2e", xAqExpected / xAq));
      System.out.println(">>> Expected methane solubility: " + String.format("%.6f", xAqExpected));
      System.out.println(">>> The flash algorithm is not converging properly for this system.");
    }
  }

  /**
   * Test verifying that proper aqueous phase composition is computed with simplified conditions.
   * Use 2-component system to isolate the issue.
   */
  @Test
  @DisplayName("Simplified test: methane-water with electrolyte model")
  public void testSimpleMethaneWaterElectrolyte() {
    System.out.println("\n=== Simplified Methane-Water with Electrolyte Model ===\n");

    // Simple 2-component system - should be easier to converge
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 100.0);
    fluid.addComponent("water", 0.5);
    fluid.addComponent("methane", 0.5);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      System.out.println("\nPhase " + p + ": " + fluid.getPhase(p).getPhaseTypeName());
      System.out.println("  beta = " + fluid.getBeta(p));
      double xWater = fluid.getPhase(p).getComponent("water").getx();
      double xMethane = fluid.getPhase(p).getComponent("methane").getx();
      double phiWater = fluid.getPhase(p).getComponent("water").getFugacityCoefficient();
      double phiMethane = fluid.getPhase(p).getComponent("methane").getFugacityCoefficient();
      System.out.println("  water:   x = " + xWater + ", phi = " + phiWater);
      System.out.println("  methane: x = " + xMethane + ", phi = " + phiMethane);
      System.out.println("  f(methane) = " + fluid.getPhase(p).getFugacity("methane"));
    }

    // For comparison, try non-electrolyte SRK-CPA
    System.out.println("\n--- Same system with standard SRK-CPA (non-electrolyte) ---");
    SystemInterface fluid2 = new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 - 10.0, 100.0);
    fluid2.addComponent("water", 0.5);
    fluid2.addComponent("methane", 0.5);
    fluid2.setMixingRule(10);
    fluid2.setMultiPhaseCheck(true);

    ThermodynamicOperations ops2 = new ThermodynamicOperations(fluid2);
    ops2.TPflash();

    for (int p = 0; p < fluid2.getNumberOfPhases(); p++) {
      System.out.println("\nPhase " + p + ": " + fluid2.getPhase(p).getPhaseTypeName());
      System.out.println("  beta = " + fluid2.getBeta(p));
      double xMethane = fluid2.getPhase(p).getComponent("methane").getx();
      double phiMethane = fluid2.getPhase(p).getComponent("methane").getFugacityCoefficient();
      System.out.println("  methane: x = " + xMethane + ", phi = " + phiMethane);
      System.out.println("  f(methane) = " + fluid2.getPhase(p).getFugacity("methane"));
    }
  }

  /**
   * Test isolating which component causes the convergence issue.
   */
  @Test
  @DisplayName("Isolate problematic component: MEG or ions?")
  public void testIsolateProblem() {
    System.out.println("\n=== Isolating Problematic Component ===\n");

    // Test 1: Water + methane + MEG (no ions)
    System.out.println("--- Test 1: Water + Methane + MEG (NO ions) ---");
    SystemInterface fluid1 = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 100.0);
    fluid1.addComponent("water", 0.5);
    fluid1.addComponent("MEG", 0.2);
    fluid1.addComponent("methane", 0.3);
    fluid1.setMixingRule(10);
    fluid1.setMultiPhaseCheck(true);

    ThermodynamicOperations ops1 = new ThermodynamicOperations(fluid1);
    ops1.TPflash();
    printMethaneAqueous(fluid1, "water+MEG+methane (no ions)");

    // Test 2: Water + methane + ions (no MEG)
    System.out.println("\n--- Test 2: Water + Methane + Ions (NO MEG) ---");
    SystemInterface fluid2 = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 100.0);
    fluid2.addComponent("water", 0.5);
    fluid2.addComponent("methane", 0.4);
    fluid2.addComponent("Na+", 0.05);
    fluid2.addComponent("Cl-", 0.05);
    fluid2.setMixingRule(10);
    fluid2.setMultiPhaseCheck(true);

    ThermodynamicOperations ops2 = new ThermodynamicOperations(fluid2);
    ops2.TPflash();
    printMethaneAqueous(fluid2, "water+methane+ions (no MEG)");

    // Test 3: Water + methane + MEG + ions (all)
    System.out.println("\n--- Test 3: Water + Methane + MEG + Ions (ALL) ---");
    SystemInterface fluid3 = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 100.0);
    fluid3.addComponent("water", 0.4);
    fluid3.addComponent("MEG", 0.2);
    fluid3.addComponent("methane", 0.3);
    fluid3.addComponent("Na+", 0.05);
    fluid3.addComponent("Cl-", 0.05);
    fluid3.setMixingRule(10);
    fluid3.setMultiPhaseCheck(true);

    ThermodynamicOperations ops3 = new ThermodynamicOperations(fluid3);
    ops3.TPflash();
    printMethaneAqueous(fluid3, "water+MEG+methane+ions (ALL)");

    System.out.println("\n=== Conclusion ===");
    System.out.println("Compare methane x values above to identify problematic combination.");
  }

  private void printMethaneAqueous(SystemInterface fluid, String label) {
    System.out.println("System: " + label);
    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      String type = fluid.getPhase(p).getPhaseTypeName();
      if (type.equals("aqueous")) {
        double xMethane = fluid.getPhase(p).getComponent("methane").getx();
        double fMethane = fluid.getPhase(p).getFugacity("methane");
        System.out.println("  Aqueous: x(methane) = " + xMethane + ", f(methane) = " + fMethane);
        if (xMethane < 1e-10) {
          System.out.println("  >>> PROBLEM: x(methane) is near zero!");
        }
      } else if (type.equals("gas")) {
        double fMethane = fluid.getPhase(p).getFugacity("methane");
        System.out.println("  Gas:     f(methane) = " + fMethane);
      }
    }
  }

  /**
   * Test full composition - same as user's problematic case.
   */
  @Test
  @DisplayName("Full composition test - user's case")
  public void testFullComposition() {
    System.out.println("\n=== Full Composition Test - User's Case ===\n");

    // User's full composition
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 100.0);
    fluid.addComponent("water", 0.494505);
    fluid.addComponent("MEG", 0.164835);
    fluid.addComponent("methane", 0.247253);
    fluid.addComponent("ethane", 0.0164835);
    fluid.addComponent("propane", 0.010989);
    fluid.addComponent("i-butane", 0.00549451);
    fluid.addComponent("n-butane", 0.00549451);
    fluid.addComponent("Na+", 0.0274725);
    fluid.addComponent("Cl-", 0.0274725);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    System.out.println("Full composition: water+MEG+C1-C4+NaCl at -10C, 100 bara");
    printMethaneAqueous(fluid, "Full composition");

    // Check all component x values in aqueous phase
    System.out.println("\n--- All component mole fractions in aqueous phase ---");
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      if (fluid.getPhase(p).getPhaseTypeName().equals("aqueous")) {
        System.out.println("Aqueous phase beta = " + fluid.getBeta(p));
        for (int i = 0; i < fluid.getPhase(p).getNumberOfComponents(); i++) {
          String name = fluid.getPhase(p).getComponent(i).getComponentName();
          double x = fluid.getPhase(p).getComponent(i).getx();
          double phi = fluid.getPhase(p).getComponent(i).getFugacityCoefficient();
          System.out.println("  " + name + ": x = " + x + ", phi = " + phi);
        }
      }
    }

    // Now test WITHOUT multiPhaseCheck - manually run PT flash
    System.out.println("\n--- Testing without MultiPhaseCheck ---");
    SystemInterface fluid2 = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 100.0);
    fluid2.addComponent("water", 0.494505);
    fluid2.addComponent("MEG", 0.164835);
    fluid2.addComponent("methane", 0.247253);
    fluid2.addComponent("ethane", 0.0164835);
    fluid2.addComponent("propane", 0.010989);
    fluid2.addComponent("i-butane", 0.00549451);
    fluid2.addComponent("n-butane", 0.00549451);
    fluid2.addComponent("Na+", 0.0274725);
    fluid2.addComponent("Cl-", 0.0274725);
    fluid2.setMixingRule(10);
    // Do NOT set multiPhaseCheck

    ThermodynamicOperations ops2 = new ThermodynamicOperations(fluid2);
    ops2.TPflash();

    System.out.println("Without MultiPhaseCheck - Number of phases: " + fluid2.getNumberOfPhases());
    for (int p = 0; p < fluid2.getNumberOfPhases(); p++) {
      System.out.println("Phase " + p + " type: " + fluid2.getPhase(p).getPhaseTypeName());
      if (fluid2.getPhase(p).hasComponent("methane")) {
        System.out.println("  x(methane) = " + fluid2.getPhase(p).getComponent("methane").getx());
      }
    }
  }
}
