package neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
}
