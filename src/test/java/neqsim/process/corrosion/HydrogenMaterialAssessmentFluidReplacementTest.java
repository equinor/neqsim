package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression coverage for replacing the fluid used in material exposure screening.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class HydrogenMaterialAssessmentFluidReplacementTest {
  /**
   * Verifies invalidated serialized inputs can be exported with current high-temperature results.
   *
   * @throws Exception if the serialization round trip fails
   */
  @Test
  void serializedAssessmentCanEvaluateDuringExport() throws Exception {
    SystemInterface current = fluid(313.15, 10.0, "CO2", 0.02, 0.01, true);
    HydrogenMaterialAssessment original = configured();
    original.setFluid(current);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }
    HydrogenMaterialAssessment restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (HydrogenMaterialAssessment) input.readObject();
    }
    assertMatchesFresh(restored, current);
  }

  /** Verifies absent components clear both pressure and composition after an evaluated sour case. */
  @Test
  void absentHydrogenAndSulfideClearPreviousExposure() {
    SystemInterface pure = fluid(313.15, 10.0, "CO2", 0.0, 0.0, false);
    HydrogenMaterialAssessment reused = configured();
    reused.setFluid(fluid(313.15, 10.0, "CO2", 0.02, 0.01, true));
    reused.evaluate();
    assertTrue(reused.getH2PartialPressureBar() > 0.0);
    reused.setFluid(pure);
    assertFalse(reused.isEvaluated());
    assertEquals(0.0, reused.getH2PartialPressureBar(), 0.0);
    assertEquals(0.0, reused.getH2MoleFractionGas(), 0.0);
    assertMatchesFresh(reused, pure);
  }

  /** Verifies present components with zero amounts do not inherit nonzero exposure. */
  @Test
  void zeroCompositionClearsPreviousExposure() {
    SystemInterface zero = fluid(313.15, 10.0, "CO2", 0.0, 0.0, true);
    HydrogenMaterialAssessment reused = configured();
    reused.setFluid(fluid(313.15, 10.0, "CO2", 0.02, 0.01, true));
    reused.setFluid(zero);
    assertEquals(0.0, reused.getH2PartialPressureBar(), 0.0);
    assertMatchesFresh(reused, zero);
  }

  /** Verifies the legacy bulk-H2 liquid fallback and removal of gas-derived H2S pressure. */
  @Test
  void gasDisappearanceReplacesGasExposureWithCurrentLiquidBasis() {
    SystemInterface liquid = fluid(300.0, 50.0, "n-decane", 0.0001, 0.0001, true);
    assertFalse(liquid.hasPhaseType("gas"));
    HydrogenMaterialAssessment reused = configured();
    reused.setFluid(fluid(313.15, 10.0, "CO2", 0.02, 0.01, true));
    reused.evaluate();
    reused.setFluid(liquid);
    assertEquals(liquid.getPhase(0).getComponent("hydrogen").getz() * 50.0, reused.getH2PartialPressureBar(), 1.0e-12);
    assertMatchesFresh(reused, liquid);
  }

  /** Verifies disappearance of H2S alone resets sour exposure while retaining the new hydrogen input. */
  @Test
  void sulfideDisappearanceDoesNotRetainSourRisk() {
    SystemInterface hydrogenOnly = fluid(313.15, 10.0, "CO2", 0.01, 0.0, false);
    HydrogenMaterialAssessment reused = configured();
    reused.setFluid(fluid(313.15, 10.0, "CO2", 0.02, 0.01, true));
    reused.setFluid(hydrogenOnly);
    assertMatchesFresh(reused, hydrogenOnly);
    assertEquals("Not applicable (no H2S)", reused.getHICRisk());
  }

  /** Verifies a liquid without either tracked component clears all earlier exposure. */
  @Test
  void hydrogenAndGasPhaseDisappearanceClearExposure() {
    SystemInterface liquid = fluid(300.0, 50.0, "n-decane", 0.0, 0.0, false);
    assertFalse(liquid.hasPhaseType("gas"));
    HydrogenMaterialAssessment reused = configured();
    reused.setFluid(fluid(313.15, 10.0, "CO2", 0.02, 0.01, true));
    reused.setFluid(liquid);
    assertEquals(0.0, reused.getH2PartialPressureBar(), 0.0);
    assertMatchesFresh(reused, liquid);
  }

  /**
   * Creates a flashed synthetic replacement state.
   *
   * @param temperature temperature in K
   * @param pressure absolute pressure in bar
   * @param carrier carrier component
   * @param hydrogen hydrogen mole amount
   * @param sulfide hydrogen sulfide mole amount
   * @param includeZeroComponents whether to retain zero-amount component entries
   * @return flashed fluid
   */
  private SystemInterface fluid(double temperature, double pressure, String carrier, double hydrogen, double sulfide,
      boolean includeZeroComponents) {
    SystemInterface result = new SystemPrEos(temperature, pressure);
    result.addComponent(carrier, 1.0 - hydrogen - sulfide);
    if (hydrogen > 0.0 || includeZeroComponents) {
      result.addComponent("hydrogen", hydrogen);
    }
    if (sulfide > 0.0 || includeZeroComponents) {
      result.addComponent("H2S", sulfide);
    }
    result.setMixingRule("classic");
    new ThermodynamicOperations(result).TPflash();
    return result;
  }

  /**
   * Creates an assessment with explicit user settings that fluid replacement must preserve.
   *
   * @return configured assessment
   */
  private HydrogenMaterialAssessment configured() {
    HydrogenMaterialAssessment result = new HydrogenMaterialAssessment();
    result.setMaterialGrade("X65");
    result.setHardnessHRC(24.0);
    result.setWallThicknessMm(20.0);
    result.setPwhtApplied(true);
    result.setFreeWaterPresent(true);
    result.setChlorideMgL(500.0);
    result.setMaxOperatingTemperatureC(250.0);
    result.setCyclicService(true);
    result.setDesignLifeYears(40.0);
    return result;
  }

  /**
   * Compares all exported inputs and evaluated risks with a fresh, identically configured assessment.
   *
   * @param reused assessment whose fluid was replaced
   * @param replacement current flashed fluid
   */
  private void assertMatchesFresh(HydrogenMaterialAssessment reused, SystemInterface replacement) {
    HydrogenMaterialAssessment fresh = configured();
    fresh.setFluid(replacement);
    fresh.evaluate();
    // Export must invalidate old evaluated results as well as replace the input fields.
    assertEquals(fresh.toJson(), reused.toJson());
    assertEquals(fresh.toMap(), reused.toMap());
    assertTrue(reused.isEvaluated());
    assertEquals(40.0, reused.getDesignLifeYears(), 0.0);
  }
}
