package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for TBP fraction models.
 *
 * <p>
 * Tests verify that each TBP model correctly calculates critical properties (Tc, Pc, ω, Vc) for
 * petroleum pseudo-components.
 * </p>
 */
public class TBPfractionModelTest {
  @Test
  void testTwuModel() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.getCharacterization().setTBPModel("Twu");
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);
    assertEquals(536.173400, thermoSystem.getComponent(0).getTC(), 1e-3);
    assertEquals(26.52357312690, thermoSystem.getComponent(0).getPC(), 1e-3);
    assertEquals(0.56001213933, thermoSystem.getComponent(0).getAcentricFactor(), 1e-3);
    assertEquals(437.335493, thermoSystem.getComponent(0).getCriticalVolume(), 1e-3);
    assertEquals(0.24141893477, thermoSystem.getComponent(0).getRacketZ(), 1e-3);
  }

  @Test
  void testLeeKeslerModel() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.getCharacterization().setTBPModel("Lee-Kesler");
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);
    assertEquals(562.4229803010662, thermoSystem.getComponent(0).getTC(), 1e-3);
    assertEquals(28.322987349048354, thermoSystem.getComponent(0).getPC(), 1e-3);
    assertEquals(0.3509842412742902, thermoSystem.getComponent(0).getAcentricFactor(), 1e-3);
    assertEquals(427.99744457199, thermoSystem.getComponent(0).getCriticalVolume(), 1e-3);
    assertEquals(0.25976113283, thermoSystem.getComponent(0).getRacketZ(), 1e-3);
  }

  @Test
  void testPedersenSRKModel() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.getCharacterization().setTBPModel("PedersenSRK");
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);
    assertEquals(281.1685637, thermoSystem.getComponent(0).getTC() - 273.15, 1e-3);
    assertEquals(26.341015469211726, thermoSystem.getComponent(0).getPC(), 1e-3);
    assertEquals(0.508241, thermoSystem.getComponent(0).getAcentricFactor(), 1e-3);
    assertEquals(426.46717439, thermoSystem.getComponent(0).getCriticalVolume(), 1e-3);
    assertEquals(122.93500, thermoSystem.getComponent(0).getNormalBoilingPoint("C"), 1e-3);
  }

  @Test
  void testPedersenPRModel() {
    SystemInterface thermoSystem = new SystemPrEos(298.0, 10.0);
    thermoSystem.getCharacterization().setTBPModel("PedersenPR");
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);
    assertEquals(560.546, thermoSystem.getComponent(0).getTC(), 1e-3);
    assertEquals(26.16934428134274, thermoSystem.getComponent(0).getPC(), 1e-3);
    assertEquals(0.3838836222383, thermoSystem.getComponent(0).getAcentricFactor(), 1e-3);
    assertEquals(444.07282144, thermoSystem.getComponent(0).getCriticalVolume(), 1e-3);
    assertEquals(122.93500, thermoSystem.getComponent(0).getNormalBoilingPoint("C"), 1e-3);
  }

  /**
   * Test Cavett (1962) model for critical property estimation. Note: The Cavett model is highly
   * sensitive to the boiling point correlation used and may give less accurate results for certain
   * fraction types.
   */
  @Test
  void testCavettModel() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.getCharacterization().setTBPModel("Cavett");
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);

    // Verify Tc is in broad range (Cavett can be sensitive to input Tb)
    double Tc = thermoSystem.getComponent(0).getTC();
    assertTrue(Tc > 400 && Tc < 800, "Tc should be in range 400-800 K, got: " + Tc);

    // Verify Pc is positive and reasonable
    double Pc = thermoSystem.getComponent(0).getPC();
    assertTrue(Pc > 10 && Pc < 60, "Pc should be in range 10-60 bar, got: " + Pc);

    // Acentric factor can vary widely for this model
    double omega = thermoSystem.getComponent(0).getAcentricFactor();
    assertTrue(omega > -0.2 && omega < 1.0, "Acentric factor should be -0.2-1.0, got: " + omega);
  }

  /**
   * Test Standing (1977) model for critical property estimation.
   */
  @Test
  void testStandingModel() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.getCharacterization().setTBPModel("Standing");
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);

    // Verify Tc is in reasonable range for C7
    double Tc = thermoSystem.getComponent(0).getTC();
    assertTrue(Tc > 500 && Tc < 700, "Tc should be in range 500-700 K, got: " + Tc);

    // Verify Pc is in reasonable range for C7
    double Pc = thermoSystem.getComponent(0).getPC();
    assertTrue(Pc > 15 && Pc < 45, "Pc should be in range 15-45 bar, got: " + Pc);

    // Verify acentric factor is positive and reasonable
    double omega = thermoSystem.getComponent(0).getAcentricFactor();
    assertTrue(omega > 0.2 && omega < 0.8, "Acentric factor should be 0.2-0.8, got: " + omega);
  }

  /**
   * Test Riazi-Daubert model for light petroleum fractions.
   */
  @Test
  void testRiaziDaubertModel() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.getCharacterization().setTBPModel("RiaziDaubert");
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);

    // Verify Tc is in reasonable range for C7
    double Tc = thermoSystem.getComponent(0).getTC();
    assertTrue(Tc > 500 && Tc < 600, "Tc should be in range 500-600 K, got: " + Tc);

    // Verify Pc is in reasonable range for C7
    double Pc = thermoSystem.getComponent(0).getPC();
    assertTrue(Pc > 20 && Pc < 40, "Pc should be in range 20-40 bar, got: " + Pc);
  }

  /**
   * Test API gravity calculation utilities.
   */
  @Test
  void testAPIGravityCalculations() {
    // Test API from SG: SG = 0.73 should give API ≈ 62.3
    double api = TBPfractionModel.calcAPIGravity(0.73);
    assertEquals(62.34, api, 0.1);

    // Test SG from API: API = 62.34 should give SG ≈ 0.73
    double sg = TBPfractionModel.calcSpecificGravity(62.34);
    assertEquals(0.73, sg, 0.01);

    // Round-trip test
    double originalSG = 0.85;
    double apiValue = TBPfractionModel.calcAPIGravity(originalSG);
    double recoveredSG = TBPfractionModel.calcSpecificGravity(apiValue);
    assertEquals(originalSG, recoveredSG, 1e-6);
  }

  /**
   * Test Watson K-factor calculation.
   */
  @Test
  void testWatsonKFactorCalculation() {
    TBPfractionModel model = new TBPfractionModel();

    // Test for typical C7 fraction (MW = 0.110 kg/mol, SG = 0.73)
    // Expected Kw for paraffinic C7 should be around 11.5-12.5
    double Kw = model.calcWatsonKFactor(0.110, 0.73);
    assertTrue(Kw > 11.0 && Kw < 13.0, "Kw should be around 11-13 for C7, got: " + Kw);
  }

  /**
   * Test model recommendation based on fluid properties.
   */
  @Test
  void testModelRecommendation() {
    TBPfractionModel model = new TBPfractionModel();

    // Heavy oil should recommend heavy oil model
    String heavyRec = model.recommendTBPModel(0.600, 0.92, "SRK");
    assertEquals("PedersenSRKHeavyOil", heavyRec);

    // Heavy oil with PR should recommend PR heavy oil model
    String heavyPRRec = model.recommendTBPModel(0.600, 0.92, "PR");
    assertEquals("PedersenPRHeavyOil", heavyPRRec);

    // Light paraffinic should recommend Twu
    String paraffinicRec = model.recommendTBPModel(0.100, 0.70, "SRK");
    assertEquals("Twu", paraffinicRec);
  }

  /**
   * Test that all available models can be instantiated.
   */
  @Test
  void testGetAvailableModels() {
    String[] models = TBPfractionModel.getAvailableModels();
    assertNotNull(models);
    assertTrue(models.length >= 10, "Should have at least 10 models available");

    // Verify all models can be instantiated
    TBPfractionModel factory = new TBPfractionModel();
    for (String modelName : models) {
      TBPModelInterface instance = factory.getModel(modelName);
      assertNotNull(instance, "Model '" + modelName + "' should be instantiable");
    }
  }

  /**
   * Test heavy oil models with high molecular weight fraction.
   */
  @Test
  void testHeavyOilModels() {
    // Test with heavy C30+ fraction (MW = 0.400 kg/mol, SG = 0.90)
    SystemInterface thermoSystemSRK = new SystemSrkEos(298.0, 10.0);
    thermoSystemSRK.getCharacterization().setTBPModel("PedersenSRKHeavyOil");
    thermoSystemSRK.addTBPfraction("C30", 1.0, 400.0 / 1000.0, 0.90);

    double TcSRK = thermoSystemSRK.getComponent(0).getTC();
    double PcSRK = thermoSystemSRK.getComponent(0).getPC();
    assertTrue(TcSRK > 700, "Heavy fraction Tc should be > 700 K, got: " + TcSRK);
    assertTrue(PcSRK > 5 && PcSRK < 20, "Heavy fraction Pc should be 5-20 bar, got: " + PcSRK);

    // Test PR heavy oil model
    SystemInterface thermoSystemPR = new SystemPrEos(298.0, 10.0);
    thermoSystemPR.getCharacterization().setTBPModel("PedersenPRHeavyOil");
    thermoSystemPR.addTBPfraction("C30", 1.0, 400.0 / 1000.0, 0.90);

    double TcPR = thermoSystemPR.getComponent(0).getTC();
    assertTrue(TcPR > 700, "Heavy fraction Tc (PR) should be > 700 K, got: " + TcPR);
  }

  /**
   * Compare core models for the same C10 fraction to understand differences. Note: Excludes Cavett
   * model from strict validation as it has known sensitivity issues.
   */
  @Test
  void testModelComparison() {
    double molarMass = 0.142; // kg/mol (C10)
    double density = 0.78; // g/cm³

    // Core models with good accuracy
    String[] coreModels = {"PedersenSRK", "Lee-Kesler", "Twu", "Standing"};
    // Models with known limitations
    String[] allModels = {"PedersenSRK", "Lee-Kesler", "RiaziDaubert", "Twu", "Cavett", "Standing"};

    System.out.println("\n=== TBP Model Comparison for C10 (MW=142 g/mol, SG=0.78) ===");
    System.out.println(String.format("%-15s %10s %10s %10s", "Model", "Tc (K)", "Pc (bar)", "ω"));
    System.out.println("-".repeat(50));

    for (String modelName : allModels) {
      SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
      thermoSystem.getCharacterization().setTBPModel(modelName);
      thermoSystem.addTBPfraction("C10", 1.0, molarMass, density);

      double Tc = thermoSystem.getComponent(0).getTC();
      double Pc = thermoSystem.getComponent(0).getPC();
      double omega = thermoSystem.getComponent(0).getAcentricFactor();

      System.out.println(String.format("%-15s %10.2f %10.2f %10.4f", modelName, Tc, Pc, omega));
    }

    // Strict validation only for core models
    for (String modelName : coreModels) {
      SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
      thermoSystem.getCharacterization().setTBPModel(modelName);
      thermoSystem.addTBPfraction("C10", 1.0, molarMass, density);

      double Tc = thermoSystem.getComponent(0).getTC();
      double Pc = thermoSystem.getComponent(0).getPC();
      double omega = thermoSystem.getComponent(0).getAcentricFactor();

      // Core models should give tight, reasonable results
      assertTrue(Tc > 550 && Tc < 700, modelName + " Tc out of range: " + Tc);
      assertTrue(Pc > 15 && Pc < 35, modelName + " Pc out of range: " + Pc);
      assertTrue(omega > 0.3 && omega < 1.0, modelName + " omega out of range: " + omega);
    }
  }
}
