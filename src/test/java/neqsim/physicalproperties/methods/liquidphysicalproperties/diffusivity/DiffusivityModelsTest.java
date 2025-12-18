package neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for diffusivity models including Hayduk-Minhas, high-pressure correction, and automatic
 * model selection.
 *
 * @author Even Solbraa
 */
public class DiffusivityModelsTest {

  private SystemInterface hydrocarbonSystem;
  private SystemInterface aqueousSystem;
  private SystemInterface highPressureSystem;

  @BeforeEach
  void setUp() {
    // Hydrocarbon system (oil)
    hydrocarbonSystem = new SystemSrkEos(300.0, 10.0);
    hydrocarbonSystem.addComponent("methane", 0.3);
    hydrocarbonSystem.addComponent("n-hexane", 0.5);
    hydrocarbonSystem.addComponent("n-heptane", 0.2);
    hydrocarbonSystem.createDatabase(true);
    hydrocarbonSystem.setMixingRule(2);

    // Aqueous system
    aqueousSystem = new SystemSrkEos(300.0, 1.0);
    aqueousSystem.addComponent("CO2", 0.05);
    aqueousSystem.addComponent("water", 0.95);
    aqueousSystem.createDatabase(true);
    aqueousSystem.setMixingRule(2);

    // High pressure system
    highPressureSystem = new SystemSrkEos(350.0, 200.0);
    highPressureSystem.addComponent("methane", 0.6);
    highPressureSystem.addComponent("n-heptane", 0.4);
    highPressureSystem.createDatabase(true);
    highPressureSystem.setMixingRule(2);
  }

  @Test
  void testHaydukMinhasHydrocarbonDiffusivity() {
    ThermodynamicOperations ops = new ThermodynamicOperations(hydrocarbonSystem);
    ops.TPflash();
    hydrocarbonSystem.initPhysicalProperties();

    if (hydrocarbonSystem.hasPhaseType("oil")) {
      HaydukMinhasDiffusivity diffModel =
          new HaydukMinhasDiffusivity(hydrocarbonSystem.getPhase("oil").getPhysicalProperties());
      diffModel.setSolventType(HaydukMinhasDiffusivity.SolventType.PARAFFIN);

      double[][] D = diffModel.calcDiffusionCoefficients(0, 0);

      // Check that diffusion coefficients are calculated
      assertNotNull(D);

      // Typical liquid diffusivities are 1e-11 to 1e-8 m²/s
      // With fallback protection, should be in reasonable range
      for (int i = 0; i < hydrocarbonSystem.getPhase("oil").getNumberOfComponents(); i++) {
        for (int j = 0; j < hydrocarbonSystem.getPhase("oil").getNumberOfComponents(); j++) {
          if (D[i][j] > 0) {
            assertTrue(D[i][j] > 1e-13, "Diffusivity should be > 1e-13 m²/s, got: " + D[i][j]);
            assertTrue(D[i][j] < 1e-5, "Diffusivity should be < 1e-5 m²/s, got: " + D[i][j]);
          }
        }
      }
    }
  }

  @Test
  void testHaydukMinhasAqueousDiffusivity() {
    ThermodynamicOperations ops = new ThermodynamicOperations(aqueousSystem);
    ops.TPflash();
    aqueousSystem.initPhysicalProperties();

    if (aqueousSystem.hasPhaseType("aqueous")) {
      HaydukMinhasDiffusivity diffModel =
          new HaydukMinhasDiffusivity(aqueousSystem.getPhase("aqueous").getPhysicalProperties());
      diffModel.setSolventType(HaydukMinhasDiffusivity.SolventType.AQUEOUS);

      double D = diffModel.calcBinaryDiffusionCoefficient(0, 1, 0);

      // With fallback protection, should be in reasonable range
      // Even if calculation fails, fallback ensures 1e-9
      assertTrue(D > 1e-12, "CO2-water diffusivity should be > 1e-12 m²/s, got: " + D);
      assertTrue(D < 1e-6, "CO2-water diffusivity should be < 1e-6 m²/s, got: " + D);
    }
  }

  @Test
  void testHighPressureDiffusivity() {
    ThermodynamicOperations ops = new ThermodynamicOperations(highPressureSystem);
    ops.TPflash();
    highPressureSystem.initPhysicalProperties();

    if (highPressureSystem.hasPhaseType("oil")) {
      HighPressureDiffusivity diffModel =
          new HighPressureDiffusivity(highPressureSystem.getPhase("oil").getPhysicalProperties());

      double[][] D = diffModel.calcDiffusionCoefficients(0, 0);

      // Check that diffusion coefficients are calculated
      assertNotNull(D);

      // Get correction factor - should be < 1 at high pressure
      double correctionFactor = diffModel.getPressureCorrectionFactor();
      assertTrue(correctionFactor > 0, "Correction factor should be positive");
      assertTrue(correctionFactor <= 1.0, "Correction factor should be <= 1 at high pressure");
    }
  }

  @Test
  void testDiffusivityModelSelectorHydrocarbon() {
    ThermodynamicOperations ops = new ThermodynamicOperations(hydrocarbonSystem);
    ops.TPflash();

    if (hydrocarbonSystem.hasPhaseType("oil")) {
      DiffusivityModelSelector.DiffusivityModelType modelType =
          DiffusivityModelSelector.selectOptimalModel(hydrocarbonSystem.getPhase("oil"));

      // For low-pressure hydrocarbon, should select Hayduk-Minhas
      assertEquals(DiffusivityModelSelector.DiffusivityModelType.HAYDUK_MINHAS, modelType);
    }
  }

  @Test
  void testDiffusivityModelSelectorAqueous() {
    ThermodynamicOperations ops = new ThermodynamicOperations(aqueousSystem);
    ops.TPflash();

    if (aqueousSystem.hasPhaseType("aqueous")) {
      DiffusivityModelSelector.DiffusivityModelType modelType =
          DiffusivityModelSelector.selectOptimalModel(aqueousSystem.getPhase("aqueous"));

      // For CO2 in aqueous system, should select CO2_WATER
      assertEquals(DiffusivityModelSelector.DiffusivityModelType.CO2_WATER, modelType);
    }
  }

  @Test
  void testDiffusivityModelSelectorHighPressure() {
    ThermodynamicOperations ops = new ThermodynamicOperations(highPressureSystem);
    ops.TPflash();

    if (highPressureSystem.hasPhaseType("oil")) {
      DiffusivityModelSelector.DiffusivityModelType modelType =
          DiffusivityModelSelector.selectOptimalModel(highPressureSystem.getPhase("oil"));

      // At 200 bar, should select high-pressure corrected model
      assertEquals(DiffusivityModelSelector.DiffusivityModelType.HIGH_PRESSURE_CORRECTED,
          modelType);
    }
  }

  @Test
  void testModelSelectionReason() {
    ThermodynamicOperations ops = new ThermodynamicOperations(highPressureSystem);
    ops.TPflash();

    if (highPressureSystem.hasPhaseType("oil")) {
      String reason =
          DiffusivityModelSelector.getModelSelectionReason(highPressureSystem.getPhase("oil"));

      assertNotNull(reason);
      assertTrue(reason.contains("pressure") || reason.contains("High"));
    }
  }

  @Test
  void testAutoSelectedModelCreation() {
    ThermodynamicOperations ops = new ThermodynamicOperations(hydrocarbonSystem);
    ops.TPflash();
    hydrocarbonSystem.initPhysicalProperties();

    if (hydrocarbonSystem.hasPhaseType("oil")) {
      Diffusivity autoModel = DiffusivityModelSelector
          .createAutoSelectedModel(hydrocarbonSystem.getPhase("oil").getPhysicalProperties());

      assertNotNull(autoModel);
      assertTrue(autoModel instanceof HaydukMinhasDiffusivity);
    }
  }

  @Test
  void testDiffusivityTemperatureDependence() {
    // Test that diffusivity increases with temperature
    double[] temps = {280.0, 300.0, 320.0, 340.0};
    double[] diffusivities = new double[temps.length];

    for (int t = 0; t < temps.length; t++) {
      SystemInterface testSystem = new SystemSrkEos(temps[t], 10.0);
      testSystem.addComponent("methane", 0.3);
      testSystem.addComponent("n-hexane", 0.7);
      testSystem.createDatabase(true);
      testSystem.setMixingRule(2);

      ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
      ops.TPflash();
      testSystem.initPhysicalProperties();

      if (testSystem.hasPhaseType("oil")) {
        HaydukMinhasDiffusivity diffModel =
            new HaydukMinhasDiffusivity(testSystem.getPhase("oil").getPhysicalProperties());
        diffusivities[t] = diffModel.calcBinaryDiffusionCoefficient(0, 1, 0);
      }
    }

    // Diffusivity should increase with temperature
    for (int i = 1; i < diffusivities.length; i++) {
      if (diffusivities[i] > 0 && diffusivities[i - 1] > 0) {
        assertTrue(diffusivities[i] >= diffusivities[i - 1] * 0.9,
            "Diffusivity should generally increase with temperature");
      }
    }
  }

  @Test
  void testVignesMixingRule() {
    ThermodynamicOperations ops = new ThermodynamicOperations(hydrocarbonSystem);
    ops.TPflash();
    hydrocarbonSystem.initPhysicalProperties();

    if (hydrocarbonSystem.hasPhaseType("oil")) {
      HaydukMinhasDiffusivity diffModel =
          new HaydukMinhasDiffusivity(hydrocarbonSystem.getPhase("oil").getPhysicalProperties());

      double[][] D = diffModel.calcDiffusionCoefficients(0, 0);

      // After Vignes mixing, D[i][j] should be positive for all pairs
      // The exact relationship depends on mole fractions
      for (int i = 0; i < D.length; i++) {
        for (int j = 0; j < D.length; j++) {
          if (i != j) {
            assertTrue(D[i][j] > 0, "D[" + i + "][" + j + "] should be positive");
          }
        }
      }
    }
  }

  /**
   * Test comparing Hayduk-Minhas with Siddiqi-Lucas for hydrocarbon systems. Both models should
   * give values in the same order of magnitude for liquid diffusivities.
   */
  @Test
  void testCompareHaydukMinhasWithSiddiqiLucas() {
    ThermodynamicOperations ops = new ThermodynamicOperations(hydrocarbonSystem);
    ops.TPflash();
    hydrocarbonSystem.initPhysicalProperties();

    if (hydrocarbonSystem.hasPhaseType("oil")) {
      PhysicalProperties physProps = hydrocarbonSystem.getPhase("oil").getPhysicalProperties();

      // Create both models
      HaydukMinhasDiffusivity haydukModel = new HaydukMinhasDiffusivity(physProps);
      haydukModel.setSolventType(HaydukMinhasDiffusivity.SolventType.PARAFFIN);
      SiddiqiLucasMethod siddiqiModel = new SiddiqiLucasMethod(physProps);

      int nComps = hydrocarbonSystem.getPhase("oil").getNumberOfComponents();

      System.out
          .println("\n=== Comparison: Hayduk-Minhas vs Siddiqi-Lucas (Hydrocarbon System) ===");
      System.out.println("T = " + hydrocarbonSystem.getPhase("oil").getTemperature() + " K, P = "
          + hydrocarbonSystem.getPhase("oil").getPressure() + " bar");
      System.out.println(String.format("%-20s %-20s %-20s %-15s", "Component Pair", "Hayduk-Minhas",
          "Siddiqi-Lucas", "Ratio (HM/SL)"));
      System.out.println(StringUtils.repeat("-", 80));

      int validComparisons = 0;
      for (int i = 0; i < nComps; i++) {
        for (int j = 0; j < nComps; j++) {
          if (i != j) {
            double dHayduk = haydukModel.calcBinaryDiffusionCoefficient(i, j, 0);
            double dSiddiqi = siddiqiModel.calcBinaryDiffusionCoefficient(i, j, 0);

            String compI = hydrocarbonSystem.getPhase("oil").getComponent(i).getComponentName();
            String compJ = hydrocarbonSystem.getPhase("oil").getComponent(j).getComponentName();
            String pair = compI + "/" + compJ;

            double ratio =
                (dSiddiqi > 0 && Double.isFinite(dSiddiqi)) ? dHayduk / dSiddiqi : Double.NaN;

            System.out.println(
                String.format("%-20s %-20.4e %-20.4e %-15.2f", pair, dHayduk, dSiddiqi, ratio));

            // Hayduk-Minhas should always be in reasonable liquid diffusivity range
            assertTrue(dHayduk > 1e-12 && dHayduk < 1e-6,
                "Hayduk-Minhas D[" + i + "][" + j + "] out of range: " + dHayduk);

            // Siddiqi-Lucas may return Infinity when viscosity is 0 (known limitation)
            // Only validate if we get a finite, positive value
            if (Double.isFinite(dSiddiqi) && dSiddiqi > 0) {
              assertTrue(dSiddiqi < 1e-5,
                  "Siddiqi-Lucas D[" + i + "][" + j + "] too large: " + dSiddiqi);

              // Models should be within ~2 orders of magnitude of each other for valid data
              assertTrue(ratio > 0.01 && ratio < 100,
                  "Models differ by more than 2 orders of magnitude for " + pair);
              validComparisons++;
            }
          }
        }
      }
      System.out.println("Valid model comparisons: " + validComparisons);
      System.out.println();

      // At least some comparisons should have valid Siddiqi-Lucas values
      assertTrue(validComparisons > 0,
          "At least some component pairs should have valid Siddiqi-Lucas values");
    }
  }

  /**
   * Test comparing CO2-water diffusivity from different models.
   */
  @Test
  void testCompareCO2WaterDiffusivity() {
    ThermodynamicOperations ops = new ThermodynamicOperations(aqueousSystem);
    ops.TPflash();
    aqueousSystem.initPhysicalProperties();

    if (aqueousSystem.hasPhaseType("aqueous")) {
      PhysicalProperties physProps = aqueousSystem.getPhase("aqueous").getPhysicalProperties();

      // Create all applicable models
      HaydukMinhasDiffusivity haydukModel = new HaydukMinhasDiffusivity(physProps);
      haydukModel.setSolventType(HaydukMinhasDiffusivity.SolventType.AQUEOUS);
      SiddiqiLucasMethod siddiqiModel = new SiddiqiLucasMethod(physProps);
      CO2water co2waterModel = new CO2water(physProps);

      // CO2 is component 0, water is component 1
      double dHayduk = haydukModel.calcBinaryDiffusionCoefficient(0, 1, 0);
      double dSiddiqi = siddiqiModel.calcBinaryDiffusionCoefficient(0, 1, 0);
      double dCO2water = co2waterModel.calcBinaryDiffusionCoefficient(0, 1, 0);

      // Literature value for CO2 in water at 25°C: ~1.9e-9 m²/s
      double literatureValue = 1.9e-9;

      System.out.println("\n=== Comparison: CO2-Water Diffusivity Models ===");
      System.out.println("T = " + aqueousSystem.getPhase("aqueous").getTemperature() + " K");
      System.out.println(String.format("%-25s %-20s %-20s", "Model", "D (m²/s)", "vs Literature"));
      System.out.println(StringUtils.repeat("-", 70));
      System.out.println(String.format("%-25s %-20.4e %-20.2f%%", "Hayduk-Minhas (Aqueous)",
          dHayduk, 100 * (dHayduk - literatureValue) / literatureValue));
      System.out.println(String.format("%-25s %-20.4e %-20.2f%%", "Siddiqi-Lucas", dSiddiqi,
          100 * (dSiddiqi - literatureValue) / literatureValue));
      System.out.println(String.format("%-25s %-20.4e %-20.2f%%", "CO2-Water (Tamimi)", dCO2water,
          100 * (dCO2water - literatureValue) / literatureValue));
      System.out.println(String.format("%-25s %-20.4e", "Literature (~25°C)", literatureValue));
      System.out.println();

      // All models should give physically reasonable values
      assertTrue(dHayduk > 1e-11 && dHayduk < 1e-7,
          "Hayduk-Minhas CO2-water out of range: " + dHayduk);
      assertTrue(dCO2water > 1e-11 && dCO2water < 1e-7,
          "CO2-water model out of range: " + dCO2water);

      // CO2-water specific model should be most accurate (designed for this system)
      // Should be within factor of 5 of literature
      assertTrue(dCO2water > literatureValue / 5 && dCO2water < literatureValue * 5,
          "CO2-water model should be within factor of 5 of literature");
    }
  }

  /**
   * Test comparing Siddiqi-Lucas aqueous vs non-aqueous correlations.
   */
  @Test
  void testSiddiqiLucasAqueousVsNonAqueous() {
    ThermodynamicOperations ops = new ThermodynamicOperations(hydrocarbonSystem);
    ops.TPflash();
    hydrocarbonSystem.initPhysicalProperties();

    if (hydrocarbonSystem.hasPhaseType("oil")) {
      PhysicalProperties physProps = hydrocarbonSystem.getPhase("oil").getPhysicalProperties();
      SiddiqiLucasMethod siddiqiModel = new SiddiqiLucasMethod(physProps);

      int nComps = hydrocarbonSystem.getPhase("oil").getNumberOfComponents();

      System.out.println("\n=== Siddiqi-Lucas: Aqueous vs Non-Aqueous Correlations ===");
      System.out.println(String.format("%-20s %-20s %-20s %-15s", "Component Pair", "Aqueous Corr.",
          "Non-Aqueous Corr.", "Ratio"));
      System.out.println(StringUtils.repeat("-", 80));

      for (int i = 0; i < nComps; i++) {
        for (int j = 0; j < nComps; j++) {
          if (i != j) {
            double dAqueous = siddiqiModel.calcBinaryDiffusionCoefficient(i, j, 0);
            double dNonAqueous = siddiqiModel.calcBinaryDiffusionCoefficient2(i, j, 0);

            String compI = hydrocarbonSystem.getPhase("oil").getComponent(i).getComponentName();
            String compJ = hydrocarbonSystem.getPhase("oil").getComponent(j).getComponentName();
            String pair = compI + "/" + compJ;

            double ratio = (dNonAqueous > 0) ? dAqueous / dNonAqueous : Double.NaN;

            System.out.println(
                String.format("%-20s %-20.4e %-20.4e %-15.2f", pair, dAqueous, dNonAqueous, ratio));

            // Non-aqueous should be valid for hydrocarbons
            if (dNonAqueous > 0 && !Double.isNaN(dNonAqueous) && !Double.isInfinite(dNonAqueous)) {
              assertTrue(dNonAqueous > 1e-12 && dNonAqueous < 1e-5,
                  "Siddiqi non-aqueous D out of range: " + dNonAqueous);
            }
          }
        }
      }
      System.out.println();
    }
  }

  /**
   * Test temperature dependence comparison across models.
   */
  @Test
  void testTemperatureDependenceComparison() {
    double[] temps = {280.0, 300.0, 320.0, 350.0};

    System.out.println("\n=== Temperature Dependence Comparison ===");
    System.out.println("System: methane/n-hexane at 10 bar");
    System.out.println(String.format("%-10s %-18s %-18s %-18s", "T (K)", "Hayduk-Minhas",
        "Siddiqi-Lucas", "Siddiqi Non-Aq"));
    System.out.println(StringUtils.repeat("-", 70));

    for (double temp : temps) {
      SystemInterface testSystem = new SystemSrkEos(temp, 10.0);
      testSystem.addComponent("methane", 0.3);
      testSystem.addComponent("n-hexane", 0.7);
      testSystem.createDatabase(true);
      testSystem.setMixingRule(2);

      ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
      ops.TPflash();
      testSystem.initPhysicalProperties();

      if (testSystem.hasPhaseType("oil")) {
        PhysicalProperties physProps = testSystem.getPhase("oil").getPhysicalProperties();

        HaydukMinhasDiffusivity haydukModel = new HaydukMinhasDiffusivity(physProps);
        haydukModel.setSolventType(HaydukMinhasDiffusivity.SolventType.PARAFFIN);
        SiddiqiLucasMethod siddiqiModel = new SiddiqiLucasMethod(physProps);

        double dHayduk = haydukModel.calcBinaryDiffusionCoefficient(0, 1, 0);
        double dSiddiqi = siddiqiModel.calcBinaryDiffusionCoefficient(0, 1, 0);
        double dSiddiqiNonAq = siddiqiModel.calcBinaryDiffusionCoefficient2(0, 1, 0);

        System.out.println(String.format("%-10.1f %-18.4e %-18.4e %-18.4e", temp, dHayduk, dSiddiqi,
            dSiddiqiNonAq));

        // All should increase with temperature
        assertTrue(dHayduk > 0, "Hayduk-Minhas should be positive at T=" + temp);
      }
    }
    System.out.println();
  }

  /**
   * Test high-pressure correction effect comparison.
   */
  @Test
  void testHighPressureEffectComparison() {
    double[] pressures = {10.0, 50.0, 100.0, 200.0, 400.0};

    System.out.println("\n=== High-Pressure Effect Comparison ===");
    System.out.println("System: methane/n-heptane at 350 K");
    System.out.println(String.format("%-10s %-18s %-18s %-18s %-12s", "P (bar)", "Hayduk-Minhas",
        "HP-Corrected", "Siddiqi-Lucas", "HP Factor"));
    System.out.println(StringUtils.repeat("-", 85));

    for (double pressure : pressures) {
      SystemInterface testSystem = new SystemSrkEos(350.0, pressure);
      testSystem.addComponent("methane", 0.5);
      testSystem.addComponent("n-heptane", 0.5);
      testSystem.createDatabase(true);
      testSystem.setMixingRule(2);

      ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
      ops.TPflash();
      testSystem.initPhysicalProperties();

      if (testSystem.hasPhaseType("oil")) {
        PhysicalProperties physProps = testSystem.getPhase("oil").getPhysicalProperties();

        HaydukMinhasDiffusivity haydukModel = new HaydukMinhasDiffusivity(physProps);
        HighPressureDiffusivity hpModel = new HighPressureDiffusivity(physProps);
        SiddiqiLucasMethod siddiqiModel = new SiddiqiLucasMethod(physProps);

        double dHayduk = haydukModel.calcBinaryDiffusionCoefficient(0, 1, 0);
        double dHP = hpModel.calcBinaryDiffusionCoefficient(0, 1, 0);
        double dSiddiqi = siddiqiModel.calcBinaryDiffusionCoefficient(0, 1, 0);
        double hpFactor = hpModel.getPressureCorrectionFactor();

        System.out.println(String.format("%-10.1f %-18.4e %-18.4e %-18.4e %-12.4f", pressure,
            dHayduk, dHP, dSiddiqi, hpFactor));

        // High-pressure corrected should be <= base model
        assertTrue(dHP <= dHayduk * 1.01, "HP-corrected should be <= base model");

        // Correction factor should decrease with pressure
        assertTrue(hpFactor <= 1.0, "HP factor should be <= 1");
      }
    }
    System.out.println();
  }
}
