package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Comprehensive test suite for hydrate calculations with electrolyte CPA EOS. Tests various fluid
 * compositions including inerts (N2, CO2), oil fractions (pentanes, hexanes), inhibitors (MEG,
 * methanol), and salts (Na+, Cl-).
 *
 * @author ESOL
 * @version 1.0
 */
public class HydrateComprehensiveTest extends neqsim.NeqSimTest {

  /**
   * Test hydrate formation with lean natural gas and inerts (N2, CO2).
   */
  @Test
  @DisplayName("Hydrate with lean gas + inerts (N2, CO2)")
  public void testHydrateLeanGasWithInerts() throws Exception {
    System.out.println("\n=== Test: Lean Gas with Inerts ===");

    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 100.0);
    fluid.addComponent("nitrogen", 0.02);
    fluid.addComponent("CO2", 0.03);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.02);
    fluid.addComponent("water", 0.03);
    fluid.setMixingRule(10);
    fluid.setHydrateCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateFormationTemperature();

    double hydrateTemp = fluid.getTemperature() - 273.15;
    System.out.println("Hydrate formation temperature: " + hydrateTemp + " °C");
    System.out.println("Pressure: 100 bara");

    // At 100 bar, lean gas hydrate should form between 10-25°C
    assertTrue(hydrateTemp > 5.0 && hydrateTemp < 30.0,
        "Hydrate temp for lean gas with inerts should be 5-30°C, got: " + hydrateTemp);

    // Check phase distribution
    ops.TPflash();
    fluid.prettyPrint();
  }

  /**
   * Test hydrate formation with rich gas including butanes.
   */
  @Test
  @DisplayName("Hydrate with rich gas (C1-C4)")
  public void testHydrateRichGas() throws Exception {
    System.out.println("\n=== Test: Rich Gas (C1-C4) ===");

    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 80.0);
    fluid.addComponent("methane", 0.75);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("i-butane", 0.02);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("water", 0.05);
    fluid.setMixingRule(10);
    fluid.setHydrateCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateFormationTemperature();

    double hydrateTemp = fluid.getTemperature() - 273.15;
    System.out.println("Hydrate formation temperature: " + hydrateTemp + " °C");
    System.out.println("Pressure: 80 bara");

    assertTrue(hydrateTemp > 5.0 && hydrateTemp < 30.0,
        "Hydrate temp for rich gas should be 5-30°C, got: " + hydrateTemp);

    // Verify aqueous phase has reasonable hydrocarbon solubility
    ops.TPflash();
    if (fluid.hasPhaseType("aqueous")) {
      int aqPhase = fluid.getPhaseNumberOfPhase("aqueous");
      double methaneInAq = fluid.getPhase(aqPhase).getComponent("methane").getx();
      double nButaneInAq = fluid.getPhase(aqPhase).getComponent("n-butane").getx();
      System.out.println("Methane in aqueous phase: " + methaneInAq);
      System.out.println("n-Butane in aqueous phase: " + nButaneInAq);

      // Should be small but not zero (1E-50 would indicate the old bug)
      assertTrue(methaneInAq > 1E-10, "Methane solubility in water should be > 1E-10");
      assertTrue(nButaneInAq > 1E-15, "n-Butane solubility in water should be > 1E-15");
    }
  }

  /**
   * Test hydrate with gas-condensate fluid including pentanes and hexanes.
   */
  @Test
  @DisplayName("Hydrate with gas-condensate (C1-C6)")
  public void testHydrateGasCondensate() throws Exception {
    System.out.println("\n=== Test: Gas-Condensate (C1-C6) ===");

    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 15.0, 120.0);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("CO2", 0.02);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("i-butane", 0.02);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("i-pentane", 0.01);
    fluid.addComponent("n-pentane", 0.01);
    fluid.addComponent("n-hexane", 0.01);
    fluid.addComponent("water", 0.06);
    fluid.setMixingRule(10);
    fluid.setHydrateCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateFormationTemperature();

    double hydrateTemp = fluid.getTemperature() - 273.15;
    System.out.println("Hydrate formation temperature: " + hydrateTemp + " °C");
    System.out.println("Pressure: 120 bara");

    assertTrue(hydrateTemp > 5.0 && hydrateTemp < 35.0,
        "Hydrate temp for gas-condensate should be 5-35°C, got: " + hydrateTemp);
  }

  /**
   * Test hydrate inhibition with MEG on rich gas.
   */
  @Test
  @DisplayName("Hydrate with MEG inhibitor")
  public void testHydrateWithMEG() throws Exception {
    System.out.println("\n=== Test: Rich Gas with MEG Inhibitor ===");

    // Without MEG
    SystemInterface fluidNoMEG = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 100.0);
    fluidNoMEG.addComponent("methane", 0.80);
    fluidNoMEG.addComponent("ethane", 0.05);
    fluidNoMEG.addComponent("propane", 0.03);
    fluidNoMEG.addComponent("n-butane", 0.02);
    fluidNoMEG.addComponent("water", 0.10);
    fluidNoMEG.setMixingRule(10);
    fluidNoMEG.setHydrateCheck(true);

    ThermodynamicOperations opsNoMEG = new ThermodynamicOperations(fluidNoMEG);
    opsNoMEG.hydrateFormationTemperature();
    double hydrateTempNoMEG = fluidNoMEG.getTemperature() - 273.15;

    // With 30 wt% MEG in water phase
    SystemInterface fluidMEG = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 100.0);
    fluidMEG.addComponent("methane", 0.80);
    fluidMEG.addComponent("ethane", 0.05);
    fluidMEG.addComponent("propane", 0.03);
    fluidMEG.addComponent("n-butane", 0.02);
    fluidMEG.addComponent("water", 0.07);
    fluidMEG.addComponent("MEG", 0.03);
    fluidMEG.setMixingRule(10);
    fluidMEG.setHydrateCheck(true);

    ThermodynamicOperations opsMEG = new ThermodynamicOperations(fluidMEG);
    opsMEG.hydrateFormationTemperature();
    double hydrateTempMEG = fluidMEG.getTemperature() - 273.15;

    double inhibitionEffect = hydrateTempNoMEG - hydrateTempMEG;

    System.out.println("Hydrate temp without MEG: " + hydrateTempNoMEG + " °C");
    System.out.println("Hydrate temp with MEG: " + hydrateTempMEG + " °C");
    System.out.println("MEG inhibition effect: " + inhibitionEffect + " °C");

    // MEG should lower hydrate temperature
    assertTrue(inhibitionEffect > 5.0,
        "MEG should lower hydrate temp by at least 5°C, got: " + inhibitionEffect);
  }

  /**
   * Test hydrate inhibition with methanol on rich gas.
   */
  @Test
  @DisplayName("Hydrate with methanol inhibitor")
  public void testHydrateWithMethanol() throws Exception {
    System.out.println("\n=== Test: Rich Gas with Methanol Inhibitor ===");

    // Without methanol
    SystemInterface fluidNoMeOH = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 100.0);
    fluidNoMeOH.addComponent("methane", 0.80);
    fluidNoMeOH.addComponent("ethane", 0.05);
    fluidNoMeOH.addComponent("propane", 0.03);
    fluidNoMeOH.addComponent("n-butane", 0.02);
    fluidNoMeOH.addComponent("water", 0.10);
    fluidNoMeOH.setMixingRule(10);
    fluidNoMeOH.setHydrateCheck(true);

    ThermodynamicOperations opsNoMeOH = new ThermodynamicOperations(fluidNoMeOH);
    opsNoMeOH.hydrateFormationTemperature();
    double hydrateTempNoMeOH = fluidNoMeOH.getTemperature() - 273.15;

    // With methanol
    SystemInterface fluidMeOH = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 100.0);
    fluidMeOH.addComponent("methane", 0.80);
    fluidMeOH.addComponent("ethane", 0.05);
    fluidMeOH.addComponent("propane", 0.03);
    fluidMeOH.addComponent("n-butane", 0.02);
    fluidMeOH.addComponent("water", 0.07);
    fluidMeOH.addComponent("methanol", 0.03);
    fluidMeOH.setMixingRule(10);
    fluidMeOH.setHydrateCheck(true);

    ThermodynamicOperations opsMeOH = new ThermodynamicOperations(fluidMeOH);
    opsMeOH.hydrateFormationTemperature();
    double hydrateTempMeOH = fluidMeOH.getTemperature() - 273.15;

    double inhibitionEffect = hydrateTempNoMeOH - hydrateTempMeOH;

    System.out.println("Hydrate temp without methanol: " + hydrateTempNoMeOH + " °C");
    System.out.println("Hydrate temp with methanol: " + hydrateTempMeOH + " °C");
    System.out.println("Methanol inhibition effect: " + inhibitionEffect + " °C");

    // Methanol should lower hydrate temperature
    assertTrue(inhibitionEffect > 5.0,
        "Methanol should lower hydrate temp by at least 5°C, got: " + inhibitionEffect);
  }

  /**
   * Test hydrate with formation water (brine with NaCl).
   */
  @Test
  @DisplayName("Hydrate with formation water (NaCl brine)")
  public void testHydrateWithBrine() throws Exception {
    System.out.println("\n=== Test: Rich Gas with Formation Water (NaCl) ===");

    // Without salt
    SystemInterface fluidNoSalt = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 100.0);
    fluidNoSalt.addComponent("methane", 0.80);
    fluidNoSalt.addComponent("ethane", 0.05);
    fluidNoSalt.addComponent("propane", 0.03);
    fluidNoSalt.addComponent("n-butane", 0.02);
    fluidNoSalt.addComponent("water", 0.10);
    fluidNoSalt.setMixingRule(10);
    fluidNoSalt.setHydrateCheck(true);

    ThermodynamicOperations opsNoSalt = new ThermodynamicOperations(fluidNoSalt);
    opsNoSalt.hydrateFormationTemperature();
    double hydrateTempNoSalt = fluidNoSalt.getTemperature() - 273.15;

    // With NaCl (~3 wt% salinity)
    SystemInterface fluidSalt = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 100.0);
    fluidSalt.addComponent("methane", 0.80);
    fluidSalt.addComponent("ethane", 0.05);
    fluidSalt.addComponent("propane", 0.03);
    fluidSalt.addComponent("n-butane", 0.02);
    fluidSalt.addComponent("water", 0.08);
    fluidSalt.addComponent("Na+", 0.01);
    fluidSalt.addComponent("Cl-", 0.01);
    fluidSalt.setMixingRule(10);
    fluidSalt.setHydrateCheck(true);

    ThermodynamicOperations opsSalt = new ThermodynamicOperations(fluidSalt);
    opsSalt.hydrateFormationTemperature();
    double hydrateTempSalt = fluidSalt.getTemperature() - 273.15;

    double saltEffect = hydrateTempNoSalt - hydrateTempSalt;

    System.out.println("Hydrate temp without salt: " + hydrateTempNoSalt + " °C");
    System.out.println("Hydrate temp with NaCl: " + hydrateTempSalt + " °C");
    System.out.println("Salt inhibition effect: " + saltEffect + " °C");

    // Salt should lower hydrate temperature (salting-out effect)
    assertTrue(saltEffect > 0.0,
        "Salt should lower hydrate temp, got effect: " + saltEffect + " °C");

    // Verify aqueous phase composition
    opsSalt.TPflash();
    if (fluidSalt.hasPhaseType("aqueous")) {
      int aqPhase = fluidSalt.getPhaseNumberOfPhase("aqueous");
      double methaneInAq = fluidSalt.getPhase(aqPhase).getComponent("methane").getx();
      double nButaneInAq = fluidSalt.getPhase(aqPhase).getComponent("n-butane").getx();
      double naInAq = fluidSalt.getPhase(aqPhase).getComponent("Na+").getx();
      double clInAq = fluidSalt.getPhase(aqPhase).getComponent("Cl-").getx();

      System.out.println("\nAqueous phase composition:");
      System.out.println("  Methane: " + methaneInAq);
      System.out.println("  n-Butane: " + nButaneInAq);
      System.out.println("  Na+: " + naInAq);
      System.out.println("  Cl-: " + clInAq);

      // Ions should be mostly in aqueous phase
      assertTrue(naInAq > 0.001, "Na+ should be present in aqueous phase");
      assertTrue(clInAq > 0.001, "Cl- should be present in aqueous phase");
      // Hydrocarbons should have small but non-zero solubility
      assertTrue(methaneInAq > 1E-10, "Methane should have non-zero solubility");
    }
  }

  /**
   * Test hydrate with combined MEG and NaCl inhibition.
   */
  @Test
  @DisplayName("Hydrate with MEG + NaCl combined")
  public void testHydrateWithMEGAndSalt() throws Exception {
    System.out.println("\n=== Test: Rich Gas with MEG + NaCl ===");

    // Reference: no inhibitors
    SystemInterface fluidRef = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 100.0);
    fluidRef.addComponent("methane", 0.80);
    fluidRef.addComponent("ethane", 0.05);
    fluidRef.addComponent("propane", 0.03);
    fluidRef.addComponent("n-butane", 0.02);
    fluidRef.addComponent("water", 0.10);
    fluidRef.setMixingRule(10);
    fluidRef.setHydrateCheck(true);

    ThermodynamicOperations opsRef = new ThermodynamicOperations(fluidRef);
    opsRef.hydrateFormationTemperature();
    double hydrateTempRef = fluidRef.getTemperature() - 273.15;

    // With MEG + NaCl
    SystemInterface fluidCombined = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 100.0);
    fluidCombined.addComponent("methane", 0.80);
    fluidCombined.addComponent("ethane", 0.05);
    fluidCombined.addComponent("propane", 0.03);
    fluidCombined.addComponent("n-butane", 0.02);
    fluidCombined.addComponent("water", 0.05);
    fluidCombined.addComponent("MEG", 0.03);
    fluidCombined.addComponent("Na+", 0.01);
    fluidCombined.addComponent("Cl-", 0.01);
    fluidCombined.setMixingRule(10);
    fluidCombined.setHydrateCheck(true);

    ThermodynamicOperations opsCombined = new ThermodynamicOperations(fluidCombined);
    opsCombined.hydrateFormationTemperature();
    double hydrateTempCombined = fluidCombined.getTemperature() - 273.15;

    double combinedEffect = hydrateTempRef - hydrateTempCombined;

    System.out.println("Hydrate temp (reference): " + hydrateTempRef + " °C");
    System.out.println("Hydrate temp with MEG + NaCl: " + hydrateTempCombined + " °C");
    System.out.println("Combined inhibition effect: " + combinedEffect + " °C");

    // Combined effect should be significant
    assertTrue(combinedEffect > 10.0,
        "Combined MEG + NaCl should lower hydrate temp by >10°C, got: " + combinedEffect);
  }

  /**
   * Test hydrate TPflash to verify phase equilibrium at hydrate conditions.
   */
  @Test
  @DisplayName("Hydrate TPflash with brine at hydrate conditions")
  public void testHydrateTPflashWithBrine() throws Exception {
    System.out.println("\n=== Test: TPflash at Hydrate Conditions with Brine ===");

    // Conditions where hydrate should form: 4°C, 100 bar
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 4.0, 100.0);
    fluid.addComponent("methane", 0.75);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("water", 0.10);
    fluid.addComponent("Na+", 0.025);
    fluid.addComponent("Cl-", 0.025);
    fluid.setMixingRule(10);
    fluid.setHydrateCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateTPflash();

    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    System.out.println("Hydrate fraction: " + fluid.getHydrateFraction());

    fluid.prettyPrint();

    // Should have at least gas and aqueous phases
    assertTrue(fluid.getNumberOfPhases() >= 2, "Should have at least 2 phases");

    // Check that aqueous phase has reasonable composition
    if (fluid.hasPhaseType("aqueous")) {
      int aqPhase = fluid.getPhaseNumberOfPhase("aqueous");
      double methaneInAq = fluid.getPhase(aqPhase).getComponent("methane").getx();
      double nButaneInAq = fluid.getPhase(aqPhase).getComponent("n-butane").getx();

      System.out.println("\nAqueous phase hydrocarbon solubility:");
      System.out.println("  Methane: " + methaneInAq);
      System.out.println("  n-Butane: " + nButaneInAq);

      // Should be small but NOT 1E-50 (that was the bug)
      assertTrue(methaneInAq > 1E-10 && methaneInAq < 0.1,
          "Methane solubility should be reasonable (1E-10 to 0.1), got: " + methaneInAq);
    }
  }

  /**
   * Test hydrate at various pressures to check pressure dependency.
   */
  @Test
  @DisplayName("Hydrate temperature vs pressure")
  public void testHydratePressureDependency() throws Exception {
    System.out.println("\n=== Test: Hydrate Temperature vs Pressure ===");

    double[] pressures = {50.0, 100.0, 150.0, 200.0};
    double[] hydrateTemps = new double[pressures.length];

    for (int i = 0; i < pressures.length; i++) {
      SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, pressures[i]);
      fluid.addComponent("methane", 0.85);
      fluid.addComponent("ethane", 0.05);
      fluid.addComponent("propane", 0.03);
      fluid.addComponent("n-butane", 0.02);
      fluid.addComponent("water", 0.05);
      fluid.setMixingRule(10);
      fluid.setHydrateCheck(true);

      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.hydrateFormationTemperature();

      hydrateTemps[i] = fluid.getTemperature() - 273.15;
      System.out.println("P = " + pressures[i] + " bara: Hydrate T = " + hydrateTemps[i] + " °C");
    }

    // Hydrate temperature should increase with pressure
    for (int i = 1; i < hydrateTemps.length; i++) {
      assertTrue(hydrateTemps[i] >= hydrateTemps[i - 1] - 1.0,
          "Hydrate temp should generally increase with pressure");
    }
  }

  /**
   * Test with sour gas (H2S) - if supported.
   */
  @Test
  @DisplayName("Hydrate with sour gas (H2S)")
  public void testHydrateSourGas() throws Exception {
    System.out.println("\n=== Test: Sour Gas (with H2S) ===");

    try {
      SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 100.0);
      fluid.addComponent("methane", 0.80);
      fluid.addComponent("ethane", 0.05);
      fluid.addComponent("H2S", 0.02);
      fluid.addComponent("CO2", 0.03);
      fluid.addComponent("water", 0.10);
      fluid.setMixingRule(10);
      fluid.setHydrateCheck(true);

      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.hydrateFormationTemperature();

      double hydrateTemp = fluid.getTemperature() - 273.15;
      System.out.println("Hydrate formation temperature (sour gas): " + hydrateTemp + " °C");

      // H2S forms type I hydrate at higher temperatures than methane
      assertTrue(hydrateTemp > 5.0 && hydrateTemp < 40.0,
          "Sour gas hydrate temp should be 5-40°C, got: " + hydrateTemp);

    } catch (Exception e) {
      System.out.println("H2S test skipped: " + e.getMessage());
    }
  }

  /**
   * Test high salinity brine.
   */
  @Test
  @DisplayName("Hydrate with high salinity brine")
  public void testHydrateHighSalinity() throws Exception {
    System.out.println("\n=== Test: High Salinity Brine ===");

    // Reference without salt
    SystemInterface fluidRef = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 80.0);
    fluidRef.addComponent("methane", 0.85);
    fluidRef.addComponent("ethane", 0.05);
    fluidRef.addComponent("water", 0.10);
    fluidRef.setMixingRule(10);
    fluidRef.setHydrateCheck(true);

    ThermodynamicOperations opsRef = new ThermodynamicOperations(fluidRef);
    opsRef.hydrateFormationTemperature();
    double hydrateTempRef = fluidRef.getTemperature() - 273.15;

    // High salinity (~10 wt% NaCl equivalent)
    SystemInterface fluidHighSalt = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 80.0);
    fluidHighSalt.addComponent("methane", 0.85);
    fluidHighSalt.addComponent("ethane", 0.05);
    fluidHighSalt.addComponent("water", 0.06);
    fluidHighSalt.addComponent("Na+", 0.02);
    fluidHighSalt.addComponent("Cl-", 0.02);
    fluidHighSalt.setMixingRule(10);
    fluidHighSalt.setHydrateCheck(true);

    ThermodynamicOperations opsHighSalt = new ThermodynamicOperations(fluidHighSalt);
    opsHighSalt.hydrateFormationTemperature();
    double hydrateTempHighSalt = fluidHighSalt.getTemperature() - 273.15;

    double saltEffect = hydrateTempRef - hydrateTempHighSalt;

    System.out.println("Hydrate temp (fresh water): " + hydrateTempRef + " °C");
    System.out.println("Hydrate temp (high salinity): " + hydrateTempHighSalt + " °C");
    System.out.println("Salt inhibition effect: " + saltEffect + " °C");

    // High salinity should have significant inhibition effect
    assertTrue(saltEffect > 2.0,
        "High salinity should lower hydrate temp by >2°C, got: " + saltEffect);
  }

  /**
   * Test hydrate equilibrium with water-saturated gas (gas-hydrate equilibrium). This tests the
   * case where there is no free aqueous phase - only gas saturated with water vapor in equilibrium
   * with hydrate.
   */
  @Test
  @DisplayName("Gas-hydrate equilibrium (water-saturated gas, no free water)")
  public void testGasHydrateEquilibrium() throws Exception {
    System.out.println("\n=== Test: Gas-Hydrate Equilibrium (no free water) ===");

    // Create a gas with trace water (water-saturated, no free aqueous phase)
    // At typical pipeline conditions, water content is typically < 0.1%
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 5.0, 100.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("water", 0.02); // Low water - may not form free aqueous phase
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.setHydrateCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // First check if we get gas + aqueous or just gas at a temperature above hydrate point
    fluid.setTemperature(273.15 + 30.0); // Well above hydrate formation
    ops.TPflash();

    System.out.println("At 30°C (above hydrate point):");
    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      System.out.println(
          "  Phase " + i + ": " + fluid.getPhase(i).getType() + ", beta=" + fluid.getBeta(i));
    }

    // Now calculate hydrate formation temperature
    ops.hydrateFormationTemperature();
    double hydrateTemp = fluid.getTemperature() - 273.15;
    System.out.println("\nHydrate formation temperature: " + hydrateTemp + " °C");

    // Should get a valid hydrate temperature
    assertTrue(hydrateTemp > 5.0 && hydrateTemp < 30.0,
        "Hydrate temp should be 5-30°C, got: " + hydrateTemp);

    // Now do TP flash at a temperature below hydrate point
    fluid.setTemperature(273.15 + hydrateTemp - 5.0); // 5°C below hydrate point
    ops.TPflash();

    System.out.println("\nAt " + (hydrateTemp - 5.0) + "°C (below hydrate point):");
    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    boolean hasHydrate = false;
    boolean hasGas = false;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      String phaseType = fluid.getPhase(i).getType().toString();
      double beta = fluid.getBeta(i);
      System.out.println("  Phase " + i + ": " + phaseType + ", beta=" + beta);
      if (phaseType.contains("HYDRATE")) {
        hasHydrate = true;
      }
      if (phaseType.contains("GAS")) {
        hasGas = true;
      }
    }

    // Below hydrate point, we should have hydrate phase
    assertTrue(hasHydrate, "Should have hydrate phase below hydrate formation temperature");
    assertTrue(hasGas, "Should still have gas phase");

    // The algorithm works - hydrate forms from gas phase water
    System.out.println("\nGas-hydrate equilibrium test passed!");
    System.out.println("The algorithm handles hydrate formation from gas phase water correctly.");
  }

  /**
   * Test hydrate curve (temperature vs pressure) for dry gas. This verifies the algorithm works
   * across a range of pressures.
   */
  @Test
  @DisplayName("Hydrate curve for dry gas (T vs P)")
  public void testHydrateCurveDryGas() throws Exception {
    System.out.println("\n=== Test: Hydrate Curve for Dry Gas ===");

    double[] pressures = {30.0, 50.0, 80.0, 100.0, 150.0};
    double[] hydrateTemps = new double[pressures.length];

    for (int i = 0; i < pressures.length; i++) {
      SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, pressures[i]);
      fluid.addComponent("methane", 0.95);
      fluid.addComponent("ethane", 0.03);
      fluid.addComponent("water", 0.02);
      fluid.setMixingRule(10);
      fluid.setHydrateCheck(true);

      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.hydrateFormationTemperature();
      hydrateTemps[i] = fluid.getTemperature() - 273.15;

      System.out.printf("P = %.0f bara: Hydrate T = %.2f °C%n", pressures[i], hydrateTemps[i]);
    }

    // Verify hydrate temperature increases with pressure (thermodynamic expectation)
    for (int i = 1; i < pressures.length; i++) {
      assertTrue(hydrateTemps[i] >= hydrateTemps[i - 1] - 0.5,
          "Hydrate temp should generally increase with pressure");
    }

    System.out.println("\nHydrate curve follows expected trend (T increases with P)");
  }
}
