package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for PedersenAsphalteneCharacterization class.
 *
 * <p>
 * Validates the implementation of Pedersen's method for asphaltene characterization and
 * precipitation modeling using classical cubic EOS (SRK).
 * </p>
 */
public class PedersenAsphalteneCharacterizationTest {
  private PedersenAsphalteneCharacterization asphChar;

  @BeforeEach
  void setUp() {
    asphChar = new PedersenAsphalteneCharacterization();
  }

  @Test
  @DisplayName("Default constructor creates valid object")
  void testDefaultConstructor() {
    assertNotNull(asphChar);
    assertEquals(PedersenAsphalteneCharacterization.DEFAULT_ASPHALTENE_MW,
        asphChar.getAsphalteneMW(), 1e-6);
    assertEquals(PedersenAsphalteneCharacterization.DEFAULT_ASPHALTENE_DENSITY,
        asphChar.getAsphalteneDensity(), 1e-6);
    assertFalse(asphChar.isCharacterized());
  }

  @Test
  @DisplayName("Constructor with properties sets values correctly")
  void testPropertiesConstructor() {
    PedersenAsphalteneCharacterization char2 =
        new PedersenAsphalteneCharacterization(1000.0, 1.15, 0.08);
    assertEquals(1000.0, char2.getAsphalteneMW(), 1e-6);
    assertEquals(1.15, char2.getAsphalteneDensity(), 1e-6);
    assertEquals(0.08, char2.getAsphalteneWeightFraction(), 1e-6);
  }

  @Test
  @DisplayName("Characterization calculates reasonable critical properties")
  void testCharacterization() {
    asphChar.setAsphalteneMW(750.0);
    asphChar.setAsphalteneDensity(1.10);
    asphChar.characterize();

    assertTrue(asphChar.isCharacterized());

    // Check critical temperature is reasonable (typically 800-1200 K for heavy components)
    double Tc = asphChar.getCriticalTemperature();
    assertTrue(Tc > 700.0, "Critical temperature should be > 700 K, got " + Tc);
    assertTrue(Tc < 1500.0, "Critical temperature should be < 1500 K, got " + Tc);

    // Check critical pressure is reasonable (typically 5-50 bar for heavy components)
    double Pc = asphChar.getCriticalPressure();
    assertTrue(Pc > 5.0, "Critical pressure should be > 5 bar, got " + Pc);
    assertTrue(Pc < 50.0, "Critical pressure should be < 50 bar, got " + Pc);

    // Check acentric factor is reasonable (typically 0.8-2.5 for heavy aromatics)
    double omega = asphChar.getAcentricFactor();
    assertTrue(omega > 0.5, "Acentric factor should be > 0.5, got " + omega);
    assertTrue(omega < 3.0, "Acentric factor should be < 3.0, got " + omega);

    // Check boiling point is reasonable
    double Tb = asphChar.getBoilingPoint();
    assertTrue(Tb > 400.0, "Boiling point should be > 400 K, got " + Tb);
    assertTrue(Tb < Tc, "Boiling point should be < critical temperature");

    System.out.println("Asphaltene characterization results:");
    System.out.println(asphChar.toString());
  }

  @Test
  @DisplayName("MW affects critical properties")
  void testMWEffectOnProperties() {
    // Lower MW asphaltene
    PedersenAsphalteneCharacterization light = new PedersenAsphalteneCharacterization();
    light.setAsphalteneMW(500.0);
    light.setAsphalteneDensity(1.05);
    light.characterize();

    // Higher MW asphaltene
    PedersenAsphalteneCharacterization heavy = new PedersenAsphalteneCharacterization();
    heavy.setAsphalteneMW(1500.0);
    heavy.setAsphalteneDensity(1.20);
    heavy.characterize();

    // Critical temperature should increase with MW
    assertTrue(heavy.getCriticalTemperature() >= light.getCriticalTemperature(),
        "Higher MW should have higher Tc");

    // Critical pressure should decrease with MW
    assertTrue(heavy.getCriticalPressure() <= light.getCriticalPressure(),
        "Higher MW should have lower Pc");

    // Acentric factor should increase with MW
    assertTrue(heavy.getAcentricFactor() >= light.getAcentricFactor(),
        "Higher MW should have higher omega");

    System.out.println("\nLight asphaltene (MW=500):");
    System.out.println(light.toString());
    System.out.println("Heavy asphaltene (MW=1500):");
    System.out.println(heavy.toString());
  }

  @Test
  @DisplayName("MW validation enforces limits")
  void testMWValidation() {
    // Below minimum
    asphChar.setAsphalteneMW(100.0);
    assertEquals(PedersenAsphalteneCharacterization.MIN_ASPHALTENE_MW, asphChar.getAsphalteneMW());

    // Above maximum
    asphChar.setAsphalteneMW(10000.0);
    assertEquals(PedersenAsphalteneCharacterization.MAX_ASPHALTENE_MW, asphChar.getAsphalteneMW());
  }

  @Test
  @DisplayName("Weight fraction validation throws for invalid values")
  void testWeightFractionValidation() {
    assertThrows(IllegalArgumentException.class, () -> asphChar.setAsphalteneWeightFraction(-0.1));
    assertThrows(IllegalArgumentException.class, () -> asphChar.setAsphalteneWeightFraction(1.5));
  }

  @Test
  @DisplayName("Add asphaltene to SRK system")
  void testAddAsphalteneToSystem() {
    SystemInterface system = new SystemSrkEos(373.15, 200.0);
    system.addComponent("methane", 0.50);
    system.addComponent("propane", 0.10);
    system.addComponent("n-heptane", 0.35);

    asphChar.setAsphalteneMW(750.0);
    asphChar.setAsphalteneDensity(1.10);
    String compName = asphChar.addAsphalteneToSystem(system, 0.05);

    // Set mixing rule AFTER adding all components
    system.setMixingRule("classic");
    system.init(0);

    // Verify asphaltene was added (name has _PC suffix from TBP fraction)
    assertEquals(4, system.getNumberOfComponents());
    assertEquals("Asphaltene_PC", compName);

    // Verify component exists by index (last component added)
    int aspIndex = system.getNumberOfComponents() - 1;
    double Tc = system.getPhase(0).getComponent(aspIndex).getTC();
    double Pc = system.getPhase(0).getComponent(aspIndex).getPC();

    assertTrue(Tc > 700.0, "Asphaltene Tc should be set correctly, got " + Tc);
    assertTrue(Pc > 5.0, "Asphaltene Pc should be set correctly, got " + Pc);

    System.out.println("\nSystem with asphaltene:");
    system.prettyPrint();
  }

  @Test
  @DisplayName("Add distributed asphaltene components")
  void testAddDistributedAsphaltene() {
    SystemInterface system = new SystemSrkEos(373.15, 200.0);
    system.addComponent("methane", 0.50);
    system.addComponent("n-heptane", 0.45);

    asphChar.setAsphalteneMW(1000.0);
    asphChar.setAsphalteneDensity(1.15);
    asphChar.addDistributedAsphaltene(system, 0.05, 3);

    // Set mixing rule AFTER adding all components
    system.setMixingRule("classic");
    system.init(0);

    // Verify 3 asphaltene pseudo-components were added
    assertEquals(5, system.getNumberOfComponents()); // methane + heptane + 3 asphaltene

    // Check components by index (they have _PC suffix)
    String comp3Name = system.getPhase(0).getComponent(2).getComponentName();
    String comp4Name = system.getPhase(0).getComponent(3).getComponentName();
    String comp5Name = system.getPhase(0).getComponent(4).getComponentName();

    assertTrue(comp3Name.startsWith("Asph_"), "Component 3 should be Asph_1, got: " + comp3Name);
    assertTrue(comp4Name.startsWith("Asph_"), "Component 4 should be Asph_2, got: " + comp4Name);
    assertTrue(comp5Name.startsWith("Asph_"), "Component 5 should be Asph_3, got: " + comp5Name);

    System.out.println("\nSystem with distributed asphaltene:");
    system.prettyPrint();
  }

  @Test
  @DisplayName("Flash calculation with asphaltene component")
  void testFlashWithAsphaltene() {
    SystemInterface system = new SystemSrkEos(373.15, 200.0);
    system.addComponent("methane", 0.40);
    system.addComponent("ethane", 0.05);
    system.addComponent("propane", 0.05);
    system.addComponent("n-heptane", 0.30);
    system.addComponent("nC10", 0.15);

    // Add asphaltene BEFORE setting mixing rule
    asphChar.setAsphalteneMW(750.0);
    asphChar.setAsphalteneDensity(1.10);
    asphChar.addAsphalteneToSystem(system, 0.05);

    // Set mixing rule AFTER adding all components
    system.setMixingRule("classic");
    system.init(0);

    // Run flash calculation
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    // Should complete without error
    assertTrue(system.getNumberOfPhases() >= 1);

    System.out.println("\nFlash results with asphaltene:");
    System.out.println("Number of phases: " + system.getNumberOfPhases());
    System.out.println("Gas fraction: " + system.getBeta());
    system.prettyPrint();
  }

  @Test
  @DisplayName("Solubility parameter calculation")
  void testSolubilityParameter() {
    asphChar.setAsphalteneMW(750.0);
    asphChar.setAsphalteneDensity(1.10);

    // At 25°C
    double delta25 = asphChar.calculateSolubilityParameter(298.15);
    assertTrue(delta25 > 18.0 && delta25 < 22.0,
        "Asphaltene solubility parameter should be ~20 (MPa)^0.5");

    // At higher temperature
    double delta100 = asphChar.calculateSolubilityParameter(373.15);
    assertTrue(delta100 < delta25, "Solubility parameter should decrease with temperature");

    System.out.println("\nSolubility parameters:");
    System.out.println("  At 25°C: " + delta25 + " (MPa)^0.5");
    System.out.println("  At 100°C: " + delta100 + " (MPa)^0.5");
  }

  @Test
  @DisplayName("Stability assessment based on solubility parameter")
  void testStabilityAssessment() {
    asphChar.setAsphalteneMW(750.0);
    asphChar.setAsphalteneDensity(1.10);
    asphChar.characterize();

    // Stable case - aromatic-rich oil
    String stableAssessment = asphChar.assessStability(19.0);
    assertTrue(stableAssessment.contains("STABLE"));

    // Unstable case - paraffinic oil
    String unstableAssessment = asphChar.assessStability(15.0);
    assertTrue(unstableAssessment.contains("UNSTABLE"));

    System.out.println("\nStability assessments:");
    System.out.println("Aromatic oil (δ=19):");
    System.out.println(stableAssessment);
    System.out.println("Paraffinic oil (δ=15):");
    System.out.println(unstableAssessment);
  }

  @Test
  @DisplayName("toString provides complete characterization summary")
  void testToString() {
    asphChar.setAsphalteneMW(1000.0);
    asphChar.setAsphalteneDensity(1.12);

    String summary = asphChar.toString();

    assertTrue(summary.contains("Molecular Weight"));
    assertTrue(summary.contains("Density"));
    assertTrue(summary.contains("Critical Temperature"));
    assertTrue(summary.contains("Critical Pressure"));
    assertTrue(summary.contains("Acentric Factor"));
    assertTrue(summary.contains("Boiling Point"));

    System.out.println(summary);
  }

  @Test
  @DisplayName("Compare with default TBP characterization")
  void testCompareWithTBPCharacterization() {
    // Create system with TBP fraction at same MW
    SystemInterface systemTBP = new SystemSrkEos(373.15, 100.0);
    systemTBP.getCharacterization().setTBPModel("PedersenSRK");
    systemTBP.addTBPfraction("Heavy", 1.0, 750.0 / 1000.0, 1.10);
    systemTBP.setMixingRule("classic");
    systemTBP.init(0);

    // Get TBP characterized properties
    int compIndexTBP = systemTBP.getPhase(0).getNumberOfComponents() - 1;
    double TcTBP = systemTBP.getPhase(0).getComponent(compIndexTBP).getTC();
    double PcTBP = systemTBP.getPhase(0).getComponent(compIndexTBP).getPC();
    double omegaTBP = systemTBP.getPhase(0).getComponent(compIndexTBP).getAcentricFactor();

    // Get Pedersen asphaltene characterized properties
    asphChar.setAsphalteneMW(750.0);
    asphChar.setAsphalteneDensity(1.10);
    asphChar.characterize();

    System.out.println("\nComparison: TBP vs Pedersen Asphaltene Characterization");
    System.out.println("(MW=750 g/mol, ρ=1.10 g/cm³)");
    System.out.println(
        String.format("  Tc: TBP=%.1f K, Asph=%.1f K", TcTBP, asphChar.getCriticalTemperature()));
    System.out.println(
        String.format("  Pc: TBP=%.2f bar, Asph=%.2f bar", PcTBP, asphChar.getCriticalPressure()));
    System.out
        .println(String.format("  ω: TBP=%.4f, Asph=%.4f", omegaTBP, asphChar.getAcentricFactor()));

    // Properties should be in similar range (using same underlying correlations)
    assertTrue(Math.abs(TcTBP - asphChar.getCriticalTemperature()) < 200.0,
        "Critical temperatures should be similar");
  }

  @Test
  @DisplayName("Pressure depletion study with asphaltene")
  void testPressureDepletionStudy() {
    // Create system simulating reservoir oil
    SystemInterface system = new SystemSrkEos(373.15, 300.0);
    system.addComponent("methane", 0.35);
    system.addComponent("ethane", 0.08);
    system.addComponent("propane", 0.05);
    system.addComponent("n-heptane", 0.25);
    system.addComponent("nC10", 0.15);
    system.addComponent("nC20", 0.07);

    // Add asphaltene BEFORE setting mixing rule
    asphChar.setAsphalteneMW(750.0);
    asphChar.setAsphalteneDensity(1.10);
    asphChar.addAsphalteneToSystem(system, 0.05);

    // Set mixing rule AFTER adding all components
    system.setMixingRule("classic");
    system.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);

    System.out.println("\nPressure Depletion Study (T=100°C):");
    System.out.println(String.format("%-12s | %-8s | %-12s", "P [bar]", "Phases", "Gas frac"));
    System.out.println(StringUtils.repeat("-", 40));

    double[] pressures = {300, 250, 200, 150, 100, 50, 20};
    for (double p : pressures) {
      system.setPressure(p);
      try {
        ops.TPflash();
        System.out.println(String.format("%12.0f | %8d | %12.4f", p, system.getNumberOfPhases(),
            system.getBeta()));
      } catch (Exception e) {
        System.out.println(String.format("%12.0f | Error: %s", p, e.getMessage()));
      }
    }
  }

  // === TBP Fraction Examples ===

  @Test
  @DisplayName("TBP characterized oil with asphaltene")
  void testTBPFractionsWithAsphaltene() {
    // Example: Black oil with C7+ characterized using Pedersen method
    // This demonstrates the workflow for a complete oil characterization

    // Create system and set Pedersen TBP model
    SystemInterface oil = new SystemSrkEos(373.15, 200.0);
    oil.getCharacterization().setTBPModel("PedersenSRK");

    // Add light components (defined compounds)
    oil.addComponent("nitrogen", 0.005);
    oil.addComponent("CO2", 0.02);
    oil.addComponent("methane", 0.35);
    oil.addComponent("ethane", 0.08);
    oil.addComponent("propane", 0.05);
    oil.addComponent("i-butane", 0.01);
    oil.addComponent("n-butane", 0.02);
    oil.addComponent("i-pentane", 0.015);
    oil.addComponent("n-pentane", 0.015);
    oil.addComponent("n-hexane", 0.02);

    // Add C7+ fractions as TBP pseudo-components
    // Parameters: name, moles, MW (kg/mol), density (g/cm³)
    oil.addTBPfraction("C7", 0.10, 96.0 / 1000.0, 0.738);
    oil.addTBPfraction("C8", 0.08, 107.0 / 1000.0, 0.765);
    oil.addTBPfraction("C9", 0.06, 121.0 / 1000.0, 0.781);
    oil.addTBPfraction("C10", 0.04, 134.0 / 1000.0, 0.792);
    oil.addTBPfraction("C11-C15", 0.06, 180.0 / 1000.0, 0.825);
    oil.addTBPfraction("C16-C20", 0.03, 260.0 / 1000.0, 0.865);
    oil.addTBPfraction("C21+", 0.02, 450.0 / 1000.0, 0.920);

    // Add asphaltene using Pedersen characterization
    asphChar.setAsphalteneMW(850.0);
    asphChar.setAsphalteneDensity(1.12);
    asphChar.addAsphalteneToSystem(oil, 0.015);

    // Set mixing rule and initialize
    oil.setMixingRule("classic");
    oil.init(0);

    // Perform flash calculation
    ThermodynamicOperations ops = new ThermodynamicOperations(oil);
    ops.TPflash();

    System.out.println("\n=== TBP Characterized Oil with Asphaltene ===");
    System.out.println("Number of components: " + oil.getPhase(0).getNumberOfComponents());
    System.out.println("Number of phases: " + oil.getNumberOfPhases());
    oil.prettyPrint();

    // Verify that characterization worked
    assertTrue(oil.getPhase(0).getNumberOfComponents() >= 18,
        "Should have at least 18 components (10 defined + 7 TBP + 1 asphaltene)");
  }

  @Test
  @DisplayName("Heavy oil with multiple TBP cuts and distributed asphaltene")
  void testHeavyOilWithDistributedAsphaltene() {
    // Example: Heavy oil with extended TBP characterization and distributed asphaltene

    SystemInterface heavyOil = new SystemSrkEos(353.15, 100.0);
    heavyOil.getCharacterization().setTBPModel("PedersenSRK");

    // Lighter components
    heavyOil.addComponent("methane", 0.15);
    heavyOil.addComponent("ethane", 0.05);
    heavyOil.addComponent("propane", 0.04);
    heavyOil.addComponent("n-butane", 0.03);
    heavyOil.addComponent("n-pentane", 0.03);

    // Heavy oil TBP fractions - representing heavy crude
    heavyOil.addTBPfraction("C6", 0.04, 86.0 / 1000.0, 0.685);
    heavyOil.addTBPfraction("C7-C10", 0.15, 115.0 / 1000.0, 0.770);
    heavyOil.addTBPfraction("C11-C15", 0.15, 180.0 / 1000.0, 0.825);
    heavyOil.addTBPfraction("C16-C20", 0.12, 260.0 / 1000.0, 0.865);
    heavyOil.addTBPfraction("C21-C30", 0.10, 380.0 / 1000.0, 0.900);
    heavyOil.addTBPfraction("C31-C40", 0.06, 520.0 / 1000.0, 0.940);

    // Add distributed asphaltene (multiple pseudo-components)
    asphChar.setAsphalteneMW(1000.0);
    asphChar.setAsphalteneDensity(1.15);
    asphChar.addDistributedAsphaltene(heavyOil, 0.08, 3);

    // Set mixing rule and initialize
    heavyOil.setMixingRule("classic");
    heavyOil.init(0);

    // Perform flash calculation
    ThermodynamicOperations ops = new ThermodynamicOperations(heavyOil);
    ops.TPflash();

    System.out.println("\n=== Heavy Oil with Distributed Asphaltene ===");
    System.out.println("Number of components: " + heavyOil.getPhase(0).getNumberOfComponents());
    System.out.println("Total asphaltene pseudo-components: 3");
    heavyOil.prettyPrint();

    // Verify distributed asphaltene was added
    assertTrue(heavyOil.getPhase(0).getNumberOfComponents() >= 14,
        "Should have at least 14 components");
  }

  @Test
  @DisplayName("Volatile oil with light asphaltene")
  void testVolatileOilWithAsphaltene() {
    // Example: Volatile oil (high GOR) with small asphaltene content

    SystemInterface volatileOil = new SystemSrkEos(393.15, 350.0);
    volatileOil.getCharacterization().setTBPModel("PedersenSRK");

    // High gas content typical of volatile oil
    volatileOil.addComponent("nitrogen", 0.01);
    volatileOil.addComponent("CO2", 0.03);
    volatileOil.addComponent("methane", 0.55);
    volatileOil.addComponent("ethane", 0.10);
    volatileOil.addComponent("propane", 0.06);
    volatileOil.addComponent("n-butane", 0.03);
    volatileOil.addComponent("n-pentane", 0.02);

    // Smaller liquid fraction
    volatileOil.addTBPfraction("C6", 0.03, 86.0 / 1000.0, 0.685);
    volatileOil.addTBPfraction("C7-C12", 0.10, 130.0 / 1000.0, 0.780);
    volatileOil.addTBPfraction("C13-C20", 0.05, 220.0 / 1000.0, 0.840);
    volatileOil.addTBPfraction("C21+", 0.01, 400.0 / 1000.0, 0.900);

    // Light asphaltene with lower MW (volatile oil typically has less asphaltene)
    asphChar.setAsphalteneMW(650.0);
    asphChar.setAsphalteneDensity(1.08);
    asphChar.addAsphalteneToSystem(volatileOil, 0.005);

    // Set mixing rule and initialize
    volatileOil.setMixingRule("classic");
    volatileOil.init(0);

    // Perform flash at high pressure
    ThermodynamicOperations ops = new ThermodynamicOperations(volatileOil);
    ops.TPflash();

    System.out.println("\n=== Volatile Oil with Light Asphaltene ===");
    System.out.println("Pressure: 350 bar, Temperature: 120°C");
    System.out.println("Number of phases: " + volatileOil.getNumberOfPhases());
    System.out.println("Gas fraction: " + volatileOil.getBeta());

    assertTrue(volatileOil.getBeta() > 0.3, "Volatile oil should have significant gas fraction");
  }

  // === Tuning Parameter Tests ===

  @Test
  @DisplayName("Tuning parameters affect characterization")
  void testTuningParametersEffect() {
    // Test that tuning parameters properly affect calculated properties

    // Get baseline properties
    asphChar.setAsphalteneMW(750.0);
    asphChar.setAsphalteneDensity(1.10);
    asphChar.characterize();
    double baseTc = asphChar.getCriticalTemperature();
    double basePc = asphChar.getCriticalPressure();
    double baseOmega = asphChar.getAcentricFactor();

    // Apply tuning multipliers
    asphChar.setTcMultiplier(1.05);
    asphChar.setPcMultiplier(0.95);
    asphChar.setOmegaMultiplier(1.10);
    asphChar.characterize();

    double tunedTc = asphChar.getCriticalTemperature();
    double tunedPc = asphChar.getCriticalPressure();
    double tunedOmega = asphChar.getAcentricFactor();

    // Verify tuning effects
    assertEquals(baseTc * 1.05, tunedTc, baseTc * 0.001, "Tc should be multiplied by 1.05");
    assertEquals(basePc * 0.95, tunedPc, basePc * 0.001, "Pc should be multiplied by 0.95");
    assertEquals(baseOmega * 1.10, tunedOmega, baseOmega * 0.001,
        "Omega should be multiplied by 1.10");

    System.out.println("\n=== Tuning Parameter Effects ===");
    System.out.println(String.format("Tc: base=%.1f K, tuned=%.1f K (x1.05)", baseTc, tunedTc));
    System.out.println(String.format("Pc: base=%.2f bar, tuned=%.2f bar (x0.95)", basePc, tunedPc));
    System.out.println(String.format("ω: base=%.4f, tuned=%.4f (x1.10)", baseOmega, tunedOmega));

    // Reset and verify
    asphChar.resetTuningParameters();
    asphChar.characterize();
    assertEquals(baseTc, asphChar.getCriticalTemperature(), baseTc * 0.001,
        "Tc should return to base after reset");
  }

  @Test
  @DisplayName("Convenience method for setting tuning parameters")
  void testSetTuningParametersConvenience() {
    asphChar.setAsphalteneMW(750.0);
    asphChar.setAsphalteneDensity(1.10);
    asphChar.characterize();
    double baseTc = asphChar.getCriticalTemperature();

    // Use convenience method
    asphChar.setTuningParameters(1.02, 0.98, 1.05);
    asphChar.characterize();

    assertEquals(1.02, asphChar.getTcMultiplier(), 0.001);
    assertEquals(0.98, asphChar.getPcMultiplier(), 0.001);
    assertEquals(1.05, asphChar.getOmegaMultiplier(), 0.001);

    assertEquals(baseTc * 1.02, asphChar.getCriticalTemperature(), baseTc * 0.001);
  }

  @Test
  @DisplayName("Tuning applied to system component properties")
  void testTunedAsphalteneInSystem() {
    // Verify tuning parameters are applied when adding asphaltene to system

    SystemInterface system = new SystemSrkEos(373.15, 100.0);
    system.addComponent("methane", 0.5);
    system.addComponent("n-heptane", 0.3);

    // Create tuned characterization
    asphChar.setAsphalteneMW(800.0);
    asphChar.setAsphalteneDensity(1.12);
    asphChar.setTcMultiplier(1.03);
    asphChar.setPcMultiplier(0.97);
    asphChar.addAsphalteneToSystem(system, 0.1);

    system.setMixingRule("classic");
    system.init(0);

    // Get asphaltene component from system
    int aspIndex = system.getPhase(0).getNumberOfComponents() - 1;
    double systemTc = system.getPhase(0).getComponent(aspIndex).getTC();
    double systemPc = system.getPhase(0).getComponent(aspIndex).getPC();

    // Verify tuned values were applied
    assertEquals(asphChar.getCriticalTemperature(), systemTc, 0.1, "System should have tuned Tc");
    assertEquals(asphChar.getCriticalPressure(), systemPc, 0.01, "System should have tuned Pc");

    System.out.println("\n=== Tuned Asphaltene in System ===");
    System.out.println("Tuning: Tc x1.03, Pc x0.97");
    System.out.println(String.format("Applied Tc: %.1f K", systemTc));
    System.out.println(String.format("Applied Pc: %.2f bar", systemPc));
  }

  // === Paper Validation Tests ===

  @Test
  @DisplayName("Validation: Pedersen correlation consistency")
  void testPedersenCorrelationConsistency() {
    // Verify that our implementation produces consistent results for
    // typical asphaltene properties as described in SPE-224534-MS

    // Test case: Heavy asphaltene MW=1000, density=1.15 g/cm³
    asphChar.setAsphalteneMW(1000.0);
    asphChar.setAsphalteneDensity(1.15);
    asphChar.characterize();

    // Per Pedersen paper, heavy asphaltenes should have:
    // - High critical temperature (900-1100 K typical)
    // - Low critical pressure (5-15 bar typical)
    // - High acentric factor (>1.0)

    double Tc = asphChar.getCriticalTemperature();
    double Pc = asphChar.getCriticalPressure();
    double omega = asphChar.getAcentricFactor();

    System.out.println("\n=== Pedersen Correlation Validation (MW=1000, ρ=1.15) ===");
    System.out.println(String.format("Tc = %.1f K (expected: 900-1100 K)", Tc));
    System.out.println(String.format("Pc = %.2f bar (expected: 5-15 bar)", Pc));
    System.out.println(String.format("ω = %.4f (expected: >1.0)", omega));

    assertTrue(Tc > 850.0 && Tc < 1200.0, "Tc should be in expected range for heavy asphaltene");
    assertTrue(Pc > 3.0 && Pc < 20.0, "Pc should be in expected range for heavy asphaltene");
    assertTrue(omega > 0.8, "Acentric factor should be high for heavy aromatic component");
  }

  @Test
  @DisplayName("Validation: MW sensitivity matches expected trends")
  void testMWSensitivityTrends() {
    // Per Pedersen paper, as MW increases:
    // - Tc increases (but not linearly)
    // - Pc decreases
    // - Acentric factor increases

    double[] mwValues = {600, 800, 1000, 1500, 2000};
    double[] tcValues = new double[mwValues.length];
    double[] pcValues = new double[mwValues.length];
    double[] omegaValues = new double[mwValues.length];

    System.out.println("\n=== MW Sensitivity Analysis ===");
    System.out.println(
        String.format("%-8s | %-10s | %-10s | %-10s", "MW", "Tc [K]", "Pc [bar]", "omega"));
    System.out.println(StringUtils.repeat("-", 45));

    for (int i = 0; i < mwValues.length; i++) {
      asphChar.setAsphalteneMW(mwValues[i]);
      asphChar.setAsphalteneDensity(1.10);
      asphChar.characterize();

      tcValues[i] = asphChar.getCriticalTemperature();
      pcValues[i] = asphChar.getCriticalPressure();
      omegaValues[i] = asphChar.getAcentricFactor();

      System.out.println(String.format("%8.0f | %10.1f | %10.2f | %10.4f", mwValues[i], tcValues[i],
          pcValues[i], omegaValues[i]));
    }

    // Verify trends
    for (int i = 1; i < mwValues.length; i++) {
      assertTrue(tcValues[i] >= tcValues[i - 1] - 10, "Tc should generally increase with MW");
      assertTrue(pcValues[i] <= pcValues[i - 1] + 1, "Pc should generally decrease with MW");
      assertTrue(omegaValues[i] >= omegaValues[i - 1] - 0.05,
          "Omega should generally increase with MW");
    }
  }

  @Test
  @DisplayName("Validation: Density effect on properties")
  void testDensityEffectOnProperties() {
    // Higher density (more aromatic) should result in:
    // - Higher critical temperature
    // - Different acentric factor behavior

    double[] densities = {1.00, 1.05, 1.10, 1.15, 1.20};
    double fixedMW = 800.0;

    System.out.println("\n=== Density Effect Analysis (MW=800) ===");
    System.out.println(
        String.format("%-10s | %-10s | %-10s | %-10s", "Density", "Tc [K]", "Pc [bar]", "omega"));
    System.out.println(StringUtils.repeat("-", 50));

    double prevTc = 0;
    for (double density : densities) {
      asphChar.setAsphalteneMW(fixedMW);
      asphChar.setAsphalteneDensity(density);
      asphChar.characterize();

      double Tc = asphChar.getCriticalTemperature();
      double Pc = asphChar.getCriticalPressure();
      double omega = asphChar.getAcentricFactor();

      System.out
          .println(String.format("%10.2f | %10.1f | %10.2f | %10.4f", density, Tc, Pc, omega));

      // Tc should increase with density (more aromatic character)
      if (prevTc > 0) {
        assertTrue(Tc >= prevTc - 20, "Tc should generally increase with density");
      }
      prevTc = Tc;
    }
  }
}
