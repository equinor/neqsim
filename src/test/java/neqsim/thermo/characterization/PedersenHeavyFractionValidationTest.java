package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for the unsupported heavy Pedersen coefficient branch reported in issue 4534.
 *
 * @author ESOL
 * @version 1.0
 */
class PedersenHeavyFractionValidationTest {
  private static final String[] AFFECTED_MODELS = {"PedersenSRK", "PedersenPR", "PedersenPR2", "PedersenSRKHeavyOil",
      "PedersenPRHeavyOil", "RiaziDaubert"};

  /**
   * Prevent direct property requests and inherited estimators from returning invalid heavy properties.
   */
  @Test
  void rejectsUnsupportedHeavyPropertiesInEveryPedersenVariant() {
    for (String modelName : AFFECTED_MODELS) {
      TBPModelInterface model = new TBPfractionModel().getModel(modelName);
      for (double molarMass : new double[] {1120.0, 1500.0, 2000.0}) {
        for (double density : new double[] {0.95, 1.0, 1.05}) {
          assertThrows(UnsupportedOperationException.class, () -> model.calcTC(molarMass, density), modelName);
          assertThrows(UnsupportedOperationException.class, () -> model.calcPC(molarMass, density), modelName);
          assertThrows(UnsupportedOperationException.class, () -> model.calcm(molarMass, density), modelName);
          assertThrows(UnsupportedOperationException.class, () -> model.calcAcentricFactor(molarMass, density),
              modelName);
          assertThrows(UnsupportedOperationException.class, () -> model.calcCriticalVolume(molarMass, density),
              modelName);
        }
      }
    }
  }

  /**
   * Preserve existing standard correlation results immediately below the heavy-branch threshold.
   */
  @Test
  void preservesSupportedPropertiesBelowThreshold() {
    TBPModelInterface srkModel = new TBPfractionModel().getModel("PedersenSRK");
    assertEquals(1252.0289528423816, srkModel.calcTC(1119.0, 1.0), 1.0e-9);
    assertEquals(12.989964223038845, srkModel.calcPC(1119.0, 1.0), 1.0e-11);
    assertEquals(1.4815870376, srkModel.calcm(1119.0, 1.0), 1.0e-11);
    assertTrue(srkModel.calcTC(1119.0, 1.0) > srkModel.calcTB(1119.0, 1.0));

    TBPModelInterface prModel = new TBPfractionModel().getModel("PedersenPR");
    assertEquals(1447.3976049207397, prModel.calcTC(1119.0, 1.0), 1.0e-9);
    assertEquals(11.217288423528355, prModel.calcPC(1119.0, 1.0), 1.0e-11);
    assertEquals(0.35811122111, prModel.calcm(1119.0, 1.0), 1.0e-11);
    assertTrue(prModel.calcTC(1119.0, 1.0) > prModel.calcTB(1119.0, 1.0));
  }

  /**
   * Do not recommend disabled coefficient sets or unsupported heavy automatic characterization.
   */
  @Test
  void recommendationsStayWithinSupportedCoefficientSets() {
    TBPfractionModel factory = new TBPfractionModel();
    assertEquals("PedersenSRK", factory.recommendTBPModel(0.6, 0.95, "SRK"));
    assertEquals("PedersenPR", factory.recommendTBPModel(0.6, 0.95, "PR"));
    assertThrows(UnsupportedOperationException.class, () -> factory.recommendTBPModel(1.120, 0.95, "SRK"));
    assertThrows(UnsupportedOperationException.class, () -> factory.recommendTBPModel(1.5, 1.05, "PR"));
  }

  /**
   * Refuse unsupported automatic characterization before changing new or existing components.
   */
  @Test
  void rejectedAutomaticFractionsDoNotChangeFluidComposition() {
    for (String modelName : AFFECTED_MODELS) {
      SystemInterface fluid = newFluid(modelName);
      fluid.addComponent("methane", 2.0);
      fluid.addTBPfraction("existing", 1.0, 0.2, 0.85, 750.0, 20.0, 0.7);
      double originalTemperature = fluid.getComponent("existing_PC").getTC();
      double originalPressure = fluid.getComponent("existing_PC").getPC();
      int originalComponents = fluid.getNumberOfComponents();
      double originalMoles = fluid.getTotalNumberOfMoles();

      assertThrows(UnsupportedOperationException.class, () -> fluid.addTBPfraction("new", 0.5, 1.12, 0.95), modelName);
      assertEquals(originalComponents, fluid.getNumberOfComponents(), modelName);
      assertEquals(originalMoles, fluid.getTotalNumberOfMoles(), 1.0e-12, modelName);
      assertFalse(fluid.hasComponent("new_PC"), modelName);

      assertThrows(UnsupportedOperationException.class, () -> fluid.addTBPfraction("existing", 0.5, 1.5, 1.05),
          modelName);
      assertEquals(originalComponents, fluid.getNumberOfComponents(), modelName);
      assertEquals(originalMoles, fluid.getTotalNumberOfMoles(), 1.0e-12, modelName);
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        assertEquals(1.0, fluid.getPhase(phase).getComponent("existing_PC").getNumberOfmoles(), 1.0e-12, modelName);
        assertEquals(originalTemperature, fluid.getPhase(phase).getComponent("existing_PC").getTC(), 1.0e-12,
            modelName);
        assertEquals(originalPressure, fluid.getPhase(phase).getComponent("existing_PC").getPC(), 1.0e-12, modelName);
      }
    }
  }

  /**
   * Keep the explicit-property overload usable for heavy fractions with independently validated properties.
   */
  @Test
  void suppliedHeavyCriticalPropertiesRemainAvailable() {
    double expectedCriticalVolume = (0.2918 - 0.0928 * 1.2) * ThermodynamicConstantsInterface.R * 1400.0 / 5.0 * 10.0;
    double expectedCriticalViscosity = 7.94830 * Math.sqrt(1500.0) * Math.pow(5.0, 2.0 / 3.0)
        / Math.pow(1400.0, 1.0 / 6.0) * 1e-7;
    for (String modelName : AFFECTED_MODELS) {
      SystemInterface fluid = newFluid(modelName);
      fluid.addComponent("methane", 2.0);
      fluid.addTBPfraction("heavy", 1.0, 1.5, 1.05, 1400.0, 5.0, 1.2);
      fluid.setMixingRule(2);
      fluid.init(0);
      fluid.init(1);

      assertEquals(2, fluid.getNumberOfComponents(), modelName);
      assertEquals(3.0, fluid.getTotalNumberOfMoles(), 1.0e-12, modelName);
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        assertEquals(1400.0, fluid.getPhase(phase).getComponent("heavy_PC").getTC(), 1.0e-12, modelName);
        assertEquals(5.0, fluid.getPhase(phase).getComponent("heavy_PC").getPC(), 1.0e-12, modelName);
        assertEquals(1.2, fluid.getPhase(phase).getComponent("heavy_PC").getAcentricFactor(), 1.0e-12, modelName);
        assertEquals(1.5, fluid.getPhase(phase).getComponent("heavy_PC").getMolarMass(), 1.0e-12, modelName);
        assertTrue(Double.isFinite(fluid.getPhase(phase).getComponent("heavy_PC").getCriticalVolume()), modelName);
        assertTrue(fluid.getPhase(phase).getComponent("heavy_PC").getCriticalVolume() > 0.0, modelName);
        assertEquals(expectedCriticalVolume, fluid.getPhase(phase).getComponent("heavy_PC").getCriticalVolume(), 1.0e-9,
            modelName);
        assertEquals(expectedCriticalViscosity, fluid.getPhase(phase).getComponent("heavy_PC").getCriticalViscosity(),
            1.0e-14, modelName);
        assertTrue(Double.isFinite(fluid.getPhase(phase).getComponent("heavy_PC").getCriticalViscosity()), modelName);
        assertTrue(fluid.getPhase(phase).getComponent("heavy_PC").getCriticalViscosity() > 0.0, modelName);
        assertTrue(fluid.getPhase(phase).getComponent("heavy_PC").getTC() > fluid.getPhase(phase)
            .getComponent("heavy_PC").getNormalBoilingPoint(), modelName);
      }
    }
  }

  /**
   * Create a fluid with the equation of state matching the named correlation.
   *
   * @param modelName TBP characterization model
   * @return empty fluid configured with the requested model
   */
  private SystemInterface newFluid(String modelName) {
    SystemInterface fluid = modelName.contains("PR") ? new SystemPrEos(298.15, 10.0) : new SystemSrkEos(298.15, 10.0);
    fluid.getCharacterization().setTBPModel(modelName);
    return fluid;
  }
}
